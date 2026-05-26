package ink.myumoon.tradingtable.registries;

import ink.myumoon.tradingtable.HarvistasTradingTable;
import ink.myumoon.tradingtable.menu.*;
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

    public static final DeferredHolder<MenuType<?>, MenuType<SystemTradingTableInitMenu>> SYSTEM_TRADING_TABLE_INIT =
            MENU_TYPES.register("system_trading_table_init", () -> new MenuType<>(SystemTradingTableInitMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<SystemTradingTableTradeMenu>> SYSTEM_TRADING_TABLE_TRADE =
            MENU_TYPES.register("system_trading_table_trade", () -> new MenuType<>(SystemTradingTableTradeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<SystemTradingTableMenu>> SYSTEM_TRADING_TABLE_MANAGE =
            MENU_TYPES.register("system_trading_table_manage", () -> new MenuType<>(SystemTradingTableMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
