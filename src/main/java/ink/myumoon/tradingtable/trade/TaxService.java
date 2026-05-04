package ink.myumoon.tradingtable.trade;

import ink.myumoon.tradingtable.config.Config;

public final class TaxService {

    private TaxService() {
    }

    public static double calculateTax(double baseAmount) {
        if (baseAmount <= 0.0D) {
            return 0.0D;
        }
        return Math.floor(baseAmount * Config.getTaxRate());
    }
}

