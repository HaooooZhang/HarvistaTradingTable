package ink.myumoon.tradingtable.economy;

import net.minecraft.world.item.Item;

public interface CurrencyBackend {
    String id();

    Item currencyItem();

    long roundTax(long grossAmount);
}

