package ink.myumoon.tradingtable.economy;

import ink.myumoon.tradingtable.Config;
import net.minecraft.world.item.Item;

public final class VanillaItemCurrencyBackend implements CurrencyBackend {
    public static final VanillaItemCurrencyBackend INSTANCE = new VanillaItemCurrencyBackend();

    private VanillaItemCurrencyBackend() {
    }

    @Override
    public String id() {
        return "VANILLA_ITEM";
    }

    @Override
    public Item currencyItem() {
        return Config.getVanillaCurrencyItem();
    }

    @Override
    public long roundTax(long grossAmount) {
        return Config.roundTax(grossAmount);
    }
}

