package ink.myumoon.tradingtable.registry;

import net.neoforged.bus.api.IEventBus;

public final class TTRegistries {
    private TTRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        TTBlocks.BLOCKS.register(modEventBus);
        TTItems.ITEMS.register(modEventBus);
        TTCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        TTBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        TTMenuTypes.MENU_TYPES.register(modEventBus);
    }
}

