package ink.myumoon.tradingtable.economy;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.trade.TaxService;
import net.minecraft.world.item.Item;

public final class ItemCurrencyBackend implements CurrencyBackend {
    public static final ItemCurrencyBackend INSTANCE = new ItemCurrencyBackend();

    private ItemCurrencyBackend() {
    }

    @Override
    public String id() {
        return "ITEM";
    }

    @Override
    public Item currencyItem() {
        return Config.getCurrencyItem();
    }

    @Override
    public long roundTax(long grossAmount) {
        return (long) TaxService.calculateTax(grossAmount);
    }
}
