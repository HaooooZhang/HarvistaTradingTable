package ink.myumoon.tradingtable.economy;

import com.mojang.logging.LogUtils;
import ink.myumoon.tradingtable.HarvistasTradingTable;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * NeoEssentials 经济系统服务封装。
 * <p>
 * 通过 {@code com.zerog.neoessentials.managers.EconomyManager#getInstance()} 单例
 * 直接操作玩家余额。若 NeoEssentials 未安装，所有方法返回安全默认值。
 * <p>
 * <b>所有方法必须在服务端调用。</b>
 */
public final class NeoEssentialsEconomyBackend {
    private static final Logger LOGGER = LogUtils.getLogger();

    private NeoEssentialsEconomyBackend() {
    }

    // --- public API ---

    /**
     * 查询玩家余额。
     *
     * @param uuid 玩家 UUID
     * @return 余额；NeoEssentials 不可用时返回 0
     */
    public static double getBalance(UUID uuid) {
        if (!isAvailable() || uuid == null) {
            return 0.0D;
        }
        try {
            Object manager = getInstance();
            Object result = manager.getClass()
                    .getMethod("getBalance", UUID.class)
                    .invoke(manager, uuid);
            if (result instanceof Number number) {
                return number.doubleValue();
            }
            LOGGER.warn("NeoEssentialsEconomyService.getBalance returned unexpected type: {}",
                    result != null ? result.getClass().getName() : "null");
            return 0.0D;
        } catch (Exception e) {
            logError("getBalance", uuid, e);
            return 0.0D;
        }
    }

    /**
     * 增加玩家余额（存款）。
     *
     * @param uuid   玩家 UUID
     * @param amount 金额（正数）
     * @return 是否成功
     */
    public static boolean addBalance(UUID uuid, double amount) {
        if (!isAvailable() || uuid == null || amount <= 0.0D) {
            return false;
        }
        try {
            Object manager = getInstance();
            Object bd = toBigDecimal(amount);
            Object result = manager.getClass()
                    .getMethod("addBalance", UUID.class, BigDecimalClass())
                    .invoke(manager, uuid, bd);
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            logError("addBalance", uuid, e);
            return false;
        }
    }

    /**
     * 减少玩家余额（取款）。调用前需自行检查余额是否足够。
     *
     * @param uuid   玩家 UUID
     * @param amount 金额（正数）
     * @return 是否成功
     */
    public static boolean subtractBalance(UUID uuid, double amount) {
        if (!isAvailable() || uuid == null || amount <= 0.0D) {
            return false;
        }
        try {
            Object manager = getInstance();
            Object bd = toBigDecimal(amount);
            Object result = manager.getClass()
                    .getMethod("subtractBalance", UUID.class, BigDecimalClass())
                    .invoke(manager, uuid, bd);
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            logError("subtractBalance", uuid, e);
            return false;
        }
    }

    /**
     * 设置玩家余额到指定值。
     *
     * @param uuid   玩家 UUID
     * @param amount 新余额
     * @return 是否成功
     */
    public static boolean setBalance(UUID uuid, double amount) {
        if (!isAvailable() || uuid == null) {
            return false;
        }
        try {
            Object manager = getInstance();
            Object bd = toBigDecimal(amount);
            manager.getClass()
                    .getMethod("setBalance", UUID.class, BigDecimalClass())
                    .invoke(manager, uuid, bd);
            return true;
        } catch (Exception e) {
            logError("setBalance", uuid, e);
            return false;
        }
    }

    /**
     * 转账：from 扣款，to 存款。
     * 使用 NeoEssentials EconomyAPI.payPlayer（原子操作）。
     *
     * @param from   付款方 UUID
     * @param to     收款方 UUID
     * @param amount 金额（正数）
     * @return 是否成功
     */
    public static boolean transfer(UUID from, UUID to, double amount) {
        if (!isAvailable() || from == null || to == null || amount <= 0.0D) {
            return false;
        }
        try {
            Object bd = toBigDecimal(amount);
            Object result = economyApiClass()
                    .getMethod("payPlayer", UUID.class, UUID.class, BigDecimalClass())
                    .invoke(null, from, to, bd);
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            logError("transfer(payPlayer)", from, e);
            return false;
        }
    }

    /**
     * 检查玩家余额是否足够（新版 API 无 hasBalance，自行比较）。
     */
    public static boolean hasBalance(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    /**
     * 获取货币符号（如 $）。NeoEssentials 不可用时返回 "?"。
     */
    public static String getCurrencySymbol() {
        if (!isAvailable()) {
            return "?";
        }
        try {
            Object manager = getInstance();
            Object result = manager.getClass()
                    .getMethod("getCurrencySymbol")
                    .invoke(manager);
            return result != null ? result.toString() : "$";
        } catch (Exception e) {
            return "$";
        }
    }

    // --- internal ---

    /** EconomyManager 类名 */
    private static final String ECONOMY_MANAGER_CLASS = "com.zerog.neoessentials.economy.managers.EconomyManager";
    /** EconomyAPI 类名（静态工具类） */
    private static final String ECONOMY_API_CLASS = "com.zerog.neoessentials.api.EconomyAPI";

    /** 标记 NeoEssentials 是否可用 */
    private static volatile Boolean available;

    /** BigDecimal.valueOf(double) MethodHandle 缓存 */
    private static volatile java.lang.reflect.Method bigDecimalValueOf;
    /** BigDecimal.class 缓存 */
    private static volatile Class<?> bigDecimalClass;
    /** EconomyAPI.class 缓存 */
    private static volatile Class<?> economyApiClass;

    private static boolean isAvailable() {
        if (available == null) {
            synchronized (NeoEssentialsEconomyBackend.class) {
                if (available == null) {
                    try {
                        Class.forName(ECONOMY_MANAGER_CLASS, false,
                                NeoEssentialsEconomyBackend.class.getClassLoader());
                        // 预热缓存
                        bigDecimalClass = Class.forName("java.math.BigDecimal");
                        bigDecimalValueOf = bigDecimalClass.getMethod("valueOf", double.class);
                        economyApiClass = Class.forName(ECONOMY_API_CLASS);
                        available = true;
                        HarvistasTradingTable.LOGGER.info("NeoEssentials economy backend v1.0.2.5+ detected and available.");
                    } catch (ClassNotFoundException | NoSuchMethodException e) {
                        available = false;
                        HarvistasTradingTable.LOGGER.warn(
                                "NeoEssentials not found or incompatible version. NEO_ESSENTIALS currency backend will be unavailable.");
                    }
                }
            }
        }
        return available;
    }

    private static Object getInstance() throws Exception {
        return Class.forName(ECONOMY_MANAGER_CLASS).getMethod("getInstance").invoke(null);
    }

    private static Class<?> BigDecimalClass() throws ClassNotFoundException {
        if (bigDecimalClass == null) {
            bigDecimalClass = Class.forName("java.math.BigDecimal");
        }
        return bigDecimalClass;
    }

    private static Class<?> economyApiClass() throws ClassNotFoundException {
        if (economyApiClass == null) {
            economyApiClass = Class.forName(ECONOMY_API_CLASS);
        }
        return economyApiClass;
    }

    private static Object toBigDecimal(double value) throws Exception {
        if (bigDecimalValueOf == null) {
            bigDecimalValueOf = BigDecimalClass().getMethod("valueOf", double.class);
        }
        return bigDecimalValueOf.invoke(null, value);
    }

    private static void logError(String method, UUID uuid, Exception e) {
        LOGGER.error("NeoEssentialsEconomyService.{} failed for player {}: {}",
                method, uuid, e.toString());
        Throwable cause = e.getCause();
        if (cause != null) {
            LOGGER.error("  Caused by: {}", cause.toString());
        }
    }
}
