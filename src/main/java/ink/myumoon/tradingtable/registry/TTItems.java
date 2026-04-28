package ink.myumoon.tradingtable.registry;

import ink.myumoon.tradingtable.HarvistasTradingTable;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TTItems {
    private TTItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HarvistasTradingTable.MODID);

    public static final DeferredItem<BlockItem> TRADING_TABLE_ITEM =
            ITEMS.registerSimpleBlockItem("trading_table", TTBlocks.TRADING_TABLE);
}
