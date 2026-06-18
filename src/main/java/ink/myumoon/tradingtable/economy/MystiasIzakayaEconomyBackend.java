package ink.myumoon.tradingtable.economy;

import com.mojang.logging.LogUtils;
import ink.myumoon.tradingtable.HarvistasTradingTable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * NeoMystiasIzakaya 模组的经济系统后端。
 * <p>
 * 通过反射调用 NMICommonBalanceUtil（静态工具类），无编译依赖。
 * 在线玩家直接通过 NMI API 操作余额；离线玩家读取 NBT 文件获取基准余额，
 * 所有离线期间的变更记录在 {@link MystiasIzakayaPendingBalance} SavedData 中，
 * 玩家上线时一次性结算。
 */
public final class MystiasIzakayaEconomyBackend {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MystiasIzakayaEconomyBackend() {
    }

    // --- public API ---

    /**
     * 查询玩家余额（通过 UUID）。
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
            return (double) getBalance(player);
        }
        // 离线：NBT 基准 + SavedData 净变化量
        long nbtBalance = readBalanceFromNbt(server, uuid);
        int netDelta = MystiasIzakayaPendingBalance.get(server).getNetDelta(uuid);
        return (double) (nbtBalance + netDelta);
    }

    /**
     * 直接获取在线玩家余额（已有 Player 实例时使用，性能更好）。
     */
    public static long getBalance(Player player) {
        if (!isAvailable() || player == null) {
            return 0L;
        }
        try {
            Object result = getEnMethod().invoke(null, player);
            return result instanceof Number n ? n.longValue() : 0L;
        } catch (Exception e) {
            logError("getBalance", player.getUUID(), e);
            return 0L;
        }
    }

    /**
     * 增加玩家余额（通过 UUID）。
     *
     * @param uuid   玩家 UUID
     * @param amount 金额（正数，向下取整为 int）
     * @return 是否成功
     */
    public static boolean addBalance(UUID uuid, double amount) {
        if (!isAvailable() || uuid == null || amount <= 0.0D) {
            return false;
        }
        int intAmount = (int) Math.floor(amount);
        if (intAmount <= 0) {
            return false;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return addBalance(player, intAmount);
        }
        // 离线：写入 SavedData 净变化量
        MystiasIzakayaPendingBalance.get(server).addNetDelta(uuid, intAmount);
        return true;
    }

    /**
     * 直接增加在线玩家余额（已有 Player 实例时使用）。
     * 绕过 NMI 的 insertEn/extractEn（存在事务 bug），直接操作 NMIBalance 对象。
     */
    public static boolean addBalance(Player player, int amount) {
        if (!isAvailable() || player == null || amount <= 0) {
            return false;
        }
        try {
            long current = getBalance(player);
            long target = current + amount;
            setBalanceDirect(player, target);
            return true;
        } catch (Exception e) {
            logError("addBalance", player.getUUID(), e);
            return false;
        }
    }

    /**
     * 直接减少在线玩家余额（已有 Player 实例时使用）。
     * 绕过 NMI 的 insertEn/extractEn（存在事务 bug），直接操作 NMIBalance 对象。
     */
    public static boolean subtractBalance(Player player, int amount) {
        if (!isAvailable() || player == null || amount <= 0) {
            return false;
        }
        try {
            long current = getBalance(player);
            if (current < amount) {
                return false;
            }
            long target = current - amount;
            setBalanceDirect(player, target);
            return true;
        } catch (Exception e) {
            logError("subtractBalance", player.getUUID(), e);
            return false;
        }
    }

    /**
     * 直接设置玩家余额为指定值。
     * 通过 get(player) 获取副本 → 修改 EN entry count → set(player, balance) 写回。
     * 完全绕过 NMI 的 insert/extract 事务逻辑。
     */
    private static void setBalanceDirect(Player player, long target) throws Exception {
        // 1. 获取余额副本
        Object balanceCopy = getBalanceCopyMethod().invoke(null, player);

        // 2. 遍历 entries，找到 EN 条目并修改 count
        int size = (int) sizeMethod().invoke(balanceCopy);
        boolean found = false;
        for (int i = 0; i < size; i++) {
            Object entry = getResourceMethod().invoke(balanceCopy, i);
            Identifier item = (Identifier) getItemMethod().invoke(entry);
            if (EN_UNIT_ID.equals(item.toString())) {
                setCountMethod().invoke(entry, target);
                found = true;
                break;
            }
        }

        // 3. 如果没有 EN 条目，新建一个并用它替换 EMPTY 槽位
        if (!found) {
            Object enIdentifier = identifierOf(REASON_NAMESPACE, "en");
            Object newEntry = createEntryConstructor().newInstance(enIdentifier, target);
            @SuppressWarnings("unchecked")
            java.util.List<Object> list = (java.util.List<Object>) getEntriesMethod().invoke(balanceCopy);
            int lastIdx = list.size() - 1;
            if (lastIdx >= 0) {
                list.set(lastIdx, newEntry);
            } else {
                list.add(newEntry);
            }
            // 确保始终有一个 EMPTY 槽位
            Object emptyIdentifier = identifierOf(REASON_NAMESPACE, "empty");
            Object emptyEntry = createEntryConstructor().newInstance(emptyIdentifier, 0L);
            list.add(emptyEntry);
        }

        // 4. 写回玩家数据
        setBalanceMethod().invoke(null, player, balanceCopy);
    }

    /**
     * 减少玩家余额（通过 UUID）。调用前需自行检查余额是否足够。
     *
     * @param uuid   玩家 UUID
     * @param amount 金额（正数，向下取整为 int）
     * @return 是否成功
     */
    public static boolean subtractBalance(UUID uuid, double amount) {
        if (!isAvailable() || uuid == null || amount <= 0.0D) {
            return false;
        }
        int intAmount = (int) Math.floor(amount);
        if (intAmount <= 0) {
            return false;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return subtractBalance(player, intAmount);
        }
        // 离线：先检查余额是否足够（NBT + netDelta），再记录负的净变化量
        long nbtBalance = readBalanceFromNbt(server, uuid);
        int netDelta = MystiasIzakayaPendingBalance.get(server).getNetDelta(uuid);
        if (nbtBalance + netDelta < intAmount) {
            return false;
        }
        MystiasIzakayaPendingBalance.get(server).addNetDelta(uuid, -intAmount);
        return true;
    }

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
     * 检查玩家余额是否足够。
     */
    public static boolean hasBalance(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    /**
     * 转账：from 扣款，to 存款（非原子操作）。
     */
    public static boolean transfer(UUID from, UUID to, double amount) {
        if (!hasBalance(from, amount)) {
            return false;
        }
        if (!subtractBalance(from, amount)) {
            return false;
        }
        if (!addBalance(to, amount)) {
            // 回滚
            addBalance(from, amount);
            return false;
        }
        return true;
    }

    /**
     * 获取货币显示文本（翻译键对应的原文或兜底 "EN"）。
     */
    public static String getCurrencySymbol() {
        if (!isAvailable()) {
            return "?";
        }
        return "EN";
    }

    // --- internal: NBT reading ---

    /**
     * 从玩家 .dat 文件中读取 NMI EN 余额。
     * NBT 路径：neoforge:attachments."neo_mystias_izakaya:balance" → entries[] → {item, count}
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
                if (EN_UNIT_ID.equals(entry.getString("item"))) {
                    return entry.getLongOr("count", 0L);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read NMI balance from NBT for player {}: {}", uuid, e.getMessage());
        }
        return 0L;
    }

    // --- internal: reflection ---

    /** NMICommonBalanceUtil 类名 */
    private static final String BALANCE_UTIL_CLASS = "icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonBalanceUtil";
    /** NMIBalance 类名 */
    private static final String BALANCE_CLASS = "icu.gensoukyo.neo_mystias_izakaya.content.economy.balance.NMIBalance";
    /** NMIBalanceEntry 类名 */
    private static final String BALANCE_ENTRY_CLASS = "icu.gensoukyo.neo_mystias_izakaya.content.economy.balance.NMIBalanceEntry";

    /** NMI Attachment NBT key */
    private static final String ATTACHMENT_KEY = "neo_mystias_izakaya:balance";

    /** EN 货币单位的 identifier 字符串 */
    private static final String EN_UNIT_ID = "neo_mystias_izakaya:en";

    /** 交易原因的 namespace */
    private static final String REASON_NAMESPACE = "neo_mystias_izakaya";

    /** 标记 NMI 是否可用 */
    private static volatile Boolean available;

    /** 缓存的反射类与方法 */
    private static volatile Class<?> balanceUtilClass;
    private static volatile Class<?> balanceClass;
    private static volatile Class<?> balanceEntryClass;
    private static volatile Method getEnMethod;
    private static volatile Method getBalanceCopyMethod;
    private static volatile Method setBalanceMethod;
    private static volatile Method sizeMethod;
    private static volatile Method getResourceMethod;
    private static volatile Method getItemMethod;
    private static volatile Method setCountMethod;
    private static volatile Method getEntriesMethod;
    private static volatile Constructor<?> createEntryConstructor;

    private static boolean isAvailable() {
        if (available == null) {
            synchronized (MystiasIzakayaEconomyBackend.class) {
                if (available == null) {
                    try {
                        balanceUtilClass = Class.forName(BALANCE_UTIL_CLASS, false,
                                MystiasIzakayaEconomyBackend.class.getClassLoader());
                        balanceClass = Class.forName(BALANCE_CLASS, false,
                                MystiasIzakayaEconomyBackend.class.getClassLoader());
                        balanceEntryClass = Class.forName(BALANCE_ENTRY_CLASS, false,
                                MystiasIzakayaEconomyBackend.class.getClassLoader());
                        // NMICommonBalanceUtil 方法
                        getEnMethod = balanceUtilClass.getMethod("getEn", Player.class);
                        getBalanceCopyMethod = balanceUtilClass.getMethod("get", Player.class);
                        setBalanceMethod = balanceUtilClass.getMethod("set", Player.class, balanceClass);
                        // NMIBalance 方法
                        sizeMethod = balanceClass.getMethod("size");
                        getResourceMethod = balanceClass.getMethod("getResource", int.class);
                        getEntriesMethod = balanceClass.getMethod("getEntries");
                        // NMIBalanceEntry 方法
                        getItemMethod = balanceEntryClass.getMethod("getItem");
                        setCountMethod = balanceEntryClass.getMethod("setCount", long.class);
                        createEntryConstructor = balanceEntryClass.getConstructor(Identifier.class, long.class);
                        available = true;
                        HarvistasTradingTable.LOGGER.info("NeoMystiasIzakaya economy backend detected and available.");
                    } catch (ClassNotFoundException e) {
                        available = false;
                        HarvistasTradingTable.LOGGER.warn(
                                "NeoMystiasIzakaya not found. MYSTIAS_IZAKAYA currency backend will be unavailable.");
                    } catch (NoSuchMethodException e) {
                        available = false;
                        HarvistasTradingTable.LOGGER.warn(
                                "NeoMystiasIzakaya API mismatch: {}", e.getMessage());
                    }
                }
            }
        }
        return available;
    }

    private static Method getEnMethod() throws Exception {
        if (getEnMethod == null) {
            getEnMethod = balanceUtilClass().getMethod("getEn", Player.class);
        }
        return getEnMethod;
    }

    private static Method getBalanceCopyMethod() throws Exception {
        if (getBalanceCopyMethod == null) {
            getBalanceCopyMethod = balanceUtilClass().getMethod("get", Player.class);
        }
        return getBalanceCopyMethod;
    }

    private static Method setBalanceMethod() throws Exception {
        if (setBalanceMethod == null) {
            setBalanceMethod = balanceUtilClass().getMethod("set", Player.class, balanceClass());
        }
        return setBalanceMethod;
    }

    private static Method sizeMethod() throws Exception {
        if (sizeMethod == null) {
            sizeMethod = balanceClass().getMethod("size");
        }
        return sizeMethod;
    }

    private static Method getResourceMethod() throws Exception {
        if (getResourceMethod == null) {
            getResourceMethod = balanceClass().getMethod("getResource", int.class);
        }
        return getResourceMethod;
    }

    private static Method getItemMethod() throws Exception {
        if (getItemMethod == null) {
            getItemMethod = balanceEntryClass().getMethod("getItem");
        }
        return getItemMethod;
    }

    private static Method setCountMethod() throws Exception {
        if (setCountMethod == null) {
            setCountMethod = balanceEntryClass().getMethod("setCount", long.class);
        }
        return setCountMethod;
    }

    private static Method getEntriesMethod() throws Exception {
        if (getEntriesMethod == null) {
            getEntriesMethod = balanceClass().getMethod("getEntries");
        }
        return getEntriesMethod;
    }

    private static Constructor<?> createEntryConstructor() throws Exception {
        if (createEntryConstructor == null) {
            createEntryConstructor = balanceEntryClass().getConstructor(Identifier.class, long.class);
        }
        return createEntryConstructor;
    }

    private static Class<?> balanceUtilClass() throws ClassNotFoundException {
        if (balanceUtilClass == null) {
            balanceUtilClass = Class.forName(BALANCE_UTIL_CLASS);
        }
        return balanceUtilClass;
    }

    private static Class<?> balanceClass() throws ClassNotFoundException {
        if (balanceClass == null) {
            balanceClass = Class.forName(BALANCE_CLASS);
        }
        return balanceClass;
    }

    private static Class<?> balanceEntryClass() throws ClassNotFoundException {
        if (balanceEntryClass == null) {
            balanceEntryClass = Class.forName(BALANCE_ENTRY_CLASS);
        }
        return balanceEntryClass;
    }

    /** 创建 Identifier，避免重复 tryParse */
    private static Identifier identifierOf(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static void logError(String method, UUID uuid, Exception e) {
        LOGGER.error("MystiasIzakayaEconomyBackend.{} failed for player {}: {}",
                method, uuid, e.toString());
        Throwable cause = e.getCause();
        if (cause != null) {
            LOGGER.error("  Caused by: {}", cause.toString());
        }
    }
}
