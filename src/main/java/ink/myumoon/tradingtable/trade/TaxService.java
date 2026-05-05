package ink.myumoon.tradingtable.trade;

import ink.myumoon.tradingtable.config.Config;

public final class TaxService {

    private TaxService() {
    }

    /**
     * 计算税额。
     * <ul>
     *   <li>ITEM 模式：向下取整到整数（物品不可分割）</li>
     *   <li>NEO_ESSENTIALS 模式：保留两位小数</li>
     * </ul>
     */
    public static double calculateTax(double baseAmount) {
        if (baseAmount <= 0.0D) {
            return 0.0D;
        }
        double raw = baseAmount * Config.getTaxRate();
        if (Config.isNeoEssentialsMode()) {
            return Math.round(raw * 100.0) / 100.0;
        }
        return Math.floor(raw);
    }
}

