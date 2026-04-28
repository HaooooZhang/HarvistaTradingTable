package ink.myumoon.tradingtable.registry;

import ink.myumoon.tradingtable.HarvistasTradingTable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TTCreativeModeTabs {
    private TTCreativeModeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HarvistasTradingTable.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TRADING_TABLE = CREATIVE_MODE_TABS.register("trading_table", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.trading_table"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> TTItems.TRADING_TABLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(
                    TTItems.TRADING_TABLE_ITEM.get()))
            .build());
}

