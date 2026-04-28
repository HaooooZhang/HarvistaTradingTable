package ink.myumoon.tradingtable.registry;

import ink.myumoon.tradingtable.HarvistasTradingTable;
import ink.myumoon.tradingtable.menu.TradingTableInitMenu;
import ink.myumoon.tradingtable.menu.TradingTableTradeMenu;
import ink.myumoon.tradingtable.menu.TradingTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TTMenuTypes {
    private TTMenuTypes() {
    }

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, HarvistasTradingTable.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TradingTableInitMenu>> TRADING_TABLE_INIT =
            MENU_TYPES.register("trading_table_init", () -> new MenuType<>(TradingTableInitMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<TradingTableTradeMenu>> TRADING_TABLE_TRADE =
            MENU_TYPES.register("trading_table_trade", () -> new MenuType<>(TradingTableTradeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<TradingTableMenu>> TRADING_TABLE_MANAGE =
            MENU_TYPES.register("trading_table_manage", () -> new MenuType<>(TradingTableMenu::new, FeatureFlags.DEFAULT_FLAGS));
}

