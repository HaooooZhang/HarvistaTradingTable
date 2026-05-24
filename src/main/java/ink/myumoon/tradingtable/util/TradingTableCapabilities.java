package ink.myumoon.tradingtable.util;

import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import ink.myumoon.tradingtable.registries.TTBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class TradingTableCapabilities {
    private TradingTableCapabilities() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                TTBlockEntities.TRADING_TABLE.get(),
                TradingTableBlockEntity::getItemHandlerForSide
        );
    }
}


