package ink.myumoon.tradingtable.trade;

import ink.myumoon.tradingtable.config.Config;

public final class TaxService {

    private TaxService() {
    }

    public static long calculateTax(double baseAmount) {
        if (baseAmount <= 0) {
            return 0L;
        }
        return (long) Math.floor(baseAmount * Config.getTaxRate());
    }
}

