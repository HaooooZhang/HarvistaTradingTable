package ink.myumoon.tradingtable.economy;

import com.mojang.logging.LogUtils;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonBalanceUtil;
import ink.myumoon.tradingtable.HarvistasTradingTable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.File;
import java.util.UUID;

/**
 * NeoMystiasIzakaya 模组的经济系统后端。
 * <p>
 * 直接调用 NMI 新版货币 API（{@link NMICommonBalanceUtil}），通过 compileOnly 依赖，
 * 运行时通过 mods.toml 声明 optional 依赖。当 NMI 不存在时，所有方法返回安全默认值
 * 且不会抛出 ClassNotFoundException（懒加载检测，见 {@link #isAvailable()}）。
 * <p>
 * 在线玩家直接走 NMI API；离线玩家将变更记录到 {@link MystiasIzakayaPendingBalance}
 * SavedData 中，玩家上线时一次性结算（{@code HarvistasTradingTable#onPlayerLoggedIn}）。
 * <p>
 * 离线余额查询读取玩家 .dat 文件中的 NeoForge Attachment 数据（NMI 的持久化格式），
 * 叠加 PendingBalance 中的净变化量。<b>注意：NBT 快照可能滞后数分钟（取决于自动保存间隔），
 * 因此离线预检结果只能作为参考，真正扣款/入账需检查返回的 {@link ChangeResult}。</b>
 */
public final class MystiasIzakayaEconomyBackend {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MystiasIzakayaEconomyBackend() {
    }

    // --- 常量 ---

    /** NMI Attachment NBT key（BALANCE 附件持久化到玩家 .dat 的路径段） */
    private static final String ATTACHMENT_KEY = "neo_mystias_izakaya:balance";

    /** EN 货币单位的 identifier 字符串（仅用作离线 NBT 读取时的比对） */
    private static final String EN_UNIT_ID = "neo_mystias_izakaya:en";

    /** 货币显示用的 NMI 翻译键（与 NMI 自身的交易日志显示一致） */
    public static final String EN_TRANSLATION_KEY = "unit.neo_mystias_izakaya.en";

    /** 标记 NMI 是否可用（懒加载）。任何 NMI 类被链接访问前都会先检查此标记。 */
    private static volatile Boolean available;

    // --- 内部 Result 类型 ---

    /**
     * 扣款 / 入账的执行结果。
     * <p>
     * 由于 NMI 的 insert/extract 在事件被取消或竞争时可能只完成部分操作，
     * 调用方应同时检查 {@link #applied()}（实际生效的金额）与 {@link #requested()}，
     * 若 {@code applied < requested} 表示部分操作未生效，需自行回滚。
     */
    public record ChangeResult(boolean success, int requested, int applied) {
        static ChangeResult fullSuccess(int amount) {
            return new ChangeResult(true, amount, amount);
        }

        static ChangeResult fullFailure(int amount) {
            return new ChangeResult(false, amount, 0);
        }

        /**
         * 在请求金额 = 应用金额时返回 true。调用方在 {@code success=true} 时可信任完全扣款；
         * 在 {@code success=false} 时若 {@code applied>0}，表示部分扣款已发生，需要回滚补偿。
         */
        public boolean fullyApplied() {
            return applied == requested;
        }
    }

    // --- public API: 余额查询 ---

    /**
     * 查询玩家余额（通过 UUID）。
     * <p>
     * 在线玩家返回实时值；离线玩家返回 NBT 快照 + SavedData 净变化量（可能略有滞后，
     * 因此只用于 UI 显示，不要仅凭此判断交易可执行性）。
     *
     * @param uuid 玩家 UUID
     * @return 余额（EN 数量），NMI 不可用或玩家不存在返回 0
     */
    public static double getBalance(UUID uuid) {
        if (!isAvailable() || uuid == null) {
            return 0.0D;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return 0.0D;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return getBalance(player);
        }
        long nbtBalance = readBalanceFromNbt(server, uuid);
        int netDelta = MystiasIzakayaPendingBalance.get(server).getNetDelta(uuid);
        return (double) (nbtBalance + netDelta);
    }

    /**
     * 直接获取在线玩家余额（已有 Player 实例时使用，性能更好且实时）。
     */
    public static long getBalance(Player player) {
        if (!isAvailable() || player == null) {
            return 0L;
        }
        try {
            long en = NMICommonBalanceUtil.getEn(player);
            LOGGER.debug("NMI getEn({}) = {}", player.getName().getString(), en);
            return en;
        } catch (LinkageError e) {
            markUnavailableDueToLinkageError(e);
            return 0L;
        } catch (Throwable e) {
            logError("getBalance", player.getUUID(), e);
            return 0L;
        }
    }

    /**
     * 检查玩家余额是否足够（基于 {@link #getBalance(UUID)}）。
     */
    public static boolean hasBalance(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    // --- public API: 余额变更（UUID，调度在线/离线） ---

    /**
     * 增加玩家余额（通过 UUID）。
     *
     * @param uuid   玩家 UUID
     * @param amount 金额（正数，向下取整为 int）
     * @return 是否完全成功
     */
    public static boolean addBalance(UUID uuid, double amount) {
        return addBalanceDetailed(uuid, amount).fullyApplied();
    }

    /**
     * 与 {@link #addBalance(UUID, double)} 相同，但返回详细结果以便上层回滚部分应用。
     */
    public static ChangeResult addBalanceDetailed(UUID uuid, double amount) {
        if (!isAvailable() || uuid == null || amount <= 0.0D) {
            return ChangeResult.fullFailure((int) Math.floor(amount));
        }
        int intAmount = (int) Math.floor(amount);
        if (intAmount <= 0) {
            return ChangeResult.fullFailure(intAmount);
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return ChangeResult.fullFailure(intAmount);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return addBalanceDetailed(player, intAmount);
        }
        // 离线：写入 SavedData 净变化量（操作可逆，玩家上线时结算）
        MystiasIzakayaPendingBalance.get(server).addNetDelta(uuid, intAmount);
        return ChangeResult.fullSuccess(intAmount);
    }

    /**
     * 减少玩家余额（通过 UUID）。调用前需自行检查余额是否足够。
     */
    public static boolean subtractBalance(UUID uuid, double amount) {
        return subtractBalanceDetailed(uuid, amount).fullyApplied();
    }

    /**
     * 与 {@link #subtractBalance(UUID, double)} 相同，但返回详细结果以便上层回滚部分应用。
     */
    public static ChangeResult subtractBalanceDetailed(UUID uuid, double amount) {
        if (!isAvailable() || uuid == null || amount <= 0.0D) {
            return ChangeResult.fullFailure((int) Math.floor(amount));
        }
        int intAmount = (int) Math.floor(amount);
        if (intAmount <= 0) {
            return ChangeResult.fullFailure(intAmount);
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return ChangeResult.fullFailure(intAmount);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return subtractBalanceDetailed(player, intAmount);
        }
        // 离线：先检查余额是否足够（NBT + netDelta），再记录负的净变化量
        long nbtBalance = readBalanceFromNbt(server, uuid);
        int netDelta = MystiasIzakayaPendingBalance.get(server).getNetDelta(uuid);
        if (nbtBalance + netDelta < intAmount) {
            return ChangeResult.fullFailure(intAmount);
        }
        MystiasIzakayaPendingBalance.get(server).addNetDelta(uuid, -intAmount);
        return ChangeResult.fullSuccess(intAmount);
    }

    // --- public API: 余额变更（在线 Player，原子提交） ---

    /**
     * 直接增加在线玩家余额（已有 Player 实例时使用）。
    * 调用 NMI 26.1.7 官方 {@link NMICommonBalanceUtil#insertEn} API，实际写入玩家 Attachment。
     *
     * @param player 在线玩家
     * @param amount 金额（正数）
     * @return 是否成功插入全部金额
     */
    public static boolean addBalance(Player player, int amount) {
        return addBalanceDetailed(player, amount).fullyApplied();
    }

    /**
    * 同 {@link #addBalance(Player, int)}，但返回详细结果以便上层回滚部分应用。
     */
    public static ChangeResult addBalanceDetailed(Player player, int amount) {
        if (!isAvailable() || player == null || amount <= 0) {
            return ChangeResult.fullFailure(amount);
        }
        try {
            int applied = NMICommonBalanceUtil.insertEn(player, amount, false);
            LOGGER.debug("NMI addBalance: player={} requested={} applied={}",
                    player.getName().getString(), amount, applied);
            return new ChangeResult(applied == amount, amount, applied);
        } catch (LinkageError e) {
            markUnavailableDueToLinkageError(e);
            return ChangeResult.fullFailure(amount);
        } catch (Throwable e) {
            logError("addBalance", player.getUUID(), e);
            return ChangeResult.fullFailure(amount);
        }
    }

    /**
     * 直接减少在线玩家余额（已有 Player 实例时使用）。
    * 调用 NMI 26.1.7 官方 {@link NMICommonBalanceUtil#extractEn} API，实际写入玩家 Attachment。
     * <p>
     * <b>注意：调用前应先用 {@link #getBalance(Player)} 检查余额；但即使预检通过，
     * 在异步事件 / 并发扣款下仍可能失败。本方法在余额不足时不会扣任何钱。</b>
     *
     * @param player 在线玩家
     * @param amount 金额（正数）
     * @return 是否成功扣除全部金额
     */
    public static boolean subtractBalance(Player player, int amount) {
        return subtractBalanceDetailed(player, amount).fullyApplied();
    }

    /**
    * 同 {@link #subtractBalance(Player, int)}，但返回详细结果以便上层回滚部分应用。
     */
    public static ChangeResult subtractBalanceDetailed(Player player, int amount) {
        if (!isAvailable() || player == null || amount <= 0) {
            return ChangeResult.fullFailure(amount);
        }
        try {
            long current = NMICommonBalanceUtil.getEn(player);
            LOGGER.debug("NMI subtractBalance: player={} currentEN={} requested={}",
                    player.getName().getString(), current, amount);
            if (current < amount) {
                LOGGER.debug("NMI subtractBalance FAILED: insufficient EN (have {}, need {})", current, amount);
                return ChangeResult.fullFailure(amount);
            }

            int applied = NMICommonBalanceUtil.extractEn(player, amount, false);
            LOGGER.debug("NMI subtractBalance: player={} requested={} applied={}",
                    player.getName().getString(), amount, applied);
            return new ChangeResult(applied == amount, amount, applied);
        } catch (LinkageError e) {
            markUnavailableDueToLinkageError(e);
            return ChangeResult.fullFailure(amount);
        } catch (Throwable e) {
            logError("subtractBalance", player.getUUID(), e);
            return ChangeResult.fullFailure(amount);
        }
    }

    // --- public API: 其他 ---

    /**
     * 设置玩家余额到指定值（通过 UUID）。
     */
    public static boolean setBalance(UUID uuid, double amount) {
        double current = getBalance(uuid);
        double diff = amount - current;
        if (diff > 0) {
            return addBalance(uuid, diff);
        } else if (diff < 0) {
            return subtractBalance(uuid, -diff);
        }
        return true;
    }

    /**
     * 转账：from 扣款，to 存款。
     * <p>
     * 先预扣款（失败立即返回 false），再尝试入账；入账失败时回滚扣款。
     */
    public static boolean transfer(UUID from, UUID to, double amount) {
        ChangeResult deduct = subtractBalanceDetailed(from, amount);
        if (!deduct.fullyApplied()) {
            return false;
        }
        ChangeResult credit = addBalanceDetailed(to, amount);
        if (!credit.fullyApplied()) {
            // 回滚扣款
            addBalance(from, deduct.applied());
            return false;
        }
        return true;
    }

    /**
     * 获取货币显示文本（兜底 "EN"，调用方应优先使用 {@link #EN_TRANSLATION_KEY} 做本地化显示）。
     */
    public static String getCurrencySymbol() {
        if (!isAvailable()) {
            return "?";
        }
        return "EN";
    }

    /**
     * 暴露 NMI 是否可用，供上层在配置不一致时给出提示。
     */
    public static boolean available() {
        return isAvailable();
    }

    // --- internal: 可用性检测 ---

    /**
     * 检测 NMI 是否在运行时存在。
     * <p>
     * 使用 {@link net.neoforged.fml.ModList#isLoaded} 检测而非 {@code Class.forName}，
     * 因为独立服务器上 NeoForge 会为每个 Mod 做 ClassLoader 隔离——
     * 我们自己的 ClassLoader 看不到 NMI 的类，但 ModList 可以正确判断 Mod 是否安装。
     * <p>
     * 如果 NMI 安装了但类链接失败（ClassLoader 隔离），会在首次调用 NMI API 时
     * 动态将 available 置为 false，避免重复尝试。
     */
    private static boolean isAvailable() {
        if (available != null && !available) {
            return false; // 已确认不可用，快速短路
        }
        if (available == null) {
            synchronized (MystiasIzakayaEconomyBackend.class) {
                if (available == null) {
                    available = net.neoforged.fml.ModList.get().isLoaded("neo_mystias_izakaya");
                    if (available) {
                        HarvistasTradingTable.LOGGER.info(
                                "NeoMystiasIzakaya economy backend detected and available.");
                    } else {
                        HarvistasTradingTable.LOGGER.warn(
                                "NeoMystiasIzakaya not found. MYSTIAS_IZAKAYA currency backend will be unavailable.");
                    }
                }
            }
        }
        return available;
    }

    /**
     * 当链接 NMI 类失败时（ClassLoader 隔离），将 available 置为 false 并记录告警。
     * 之后所有方法会走 isAvailable() 的快速短路分支返回安全默认值。
     */
    private static void markUnavailableDueToLinkageError(LinkageError e) {
        available = false;
        HarvistasTradingTable.LOGGER.error(
                "NMI classes could not be linked (ClassLoader isolation?). "
                        + "MYSTIAS_IZAKAYA currency backend disabled. "
                        + "Error: {}", e.toString());
    }

    // --- internal: 离线 NBT 读取 ---

    /**
     * 从玩家 .dat 文件中读取 NMI EN 余额。
     * NBT 路径：{@code neoforge:attachments}."neo_mystias_izakaya:balance" → entries[] → {item, count}
     * <p>
    * 该结构对应 NMI BALANCE Attachment 的序列化格式。
     * <p>
     * <b>注意：NBT 反映的是玩家最后一次保存（退出或自动保存）时的状态，可能滞后真实余额数分钟，
     * 仅适合作为离线预检参考。</b>
     */
    private static long readBalanceFromNbt(MinecraftServer server, UUID uuid) {
        try {
            File playerFile = new File(
                    server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile(),
                    uuid.toString() + ".dat"
            );
            if (!playerFile.exists()) {
                return 0L;
            }
            CompoundTag root = NbtIo.readCompressed(playerFile.toPath(), NbtAccounter.unlimitedHeap());
            CompoundTag attachments = root.getCompoundOrEmpty("neoforge:attachments");
            CompoundTag balanceTag = attachments.getCompoundOrEmpty(ATTACHMENT_KEY);
            ListTag entries = balanceTag.getListOrEmpty("entries");
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompoundOrEmpty(i);
                if (EN_UNIT_ID.equals(entry.getStringOr("item", ""))) {
                    return entry.getLongOr("count", 0L);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read NMI balance from NBT for player {}: {}", uuid, e.getMessage());
        }
        return 0L;
    }

    private static void logError(String method, UUID uuid, Throwable e) {
        LOGGER.error("MystiasIzakayaEconomyBackend.{} failed for player {}: {}",
                method, uuid, e.toString());
        Throwable cause = e.getCause();
        if (cause != null) {
            LOGGER.error("  Caused by: {}", cause.toString());
        }
    }
}
