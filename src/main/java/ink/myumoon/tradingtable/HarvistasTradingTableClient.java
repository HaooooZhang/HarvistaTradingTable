package ink.myumoon.tradingtable;

import ink.myumoon.tradingtable.blockentity.renderer.TradingTableRenderer;
import ink.myumoon.tradingtable.client.screen.TradingTableInitScreen;
import ink.myumoon.tradingtable.client.screen.TradingTableScreen;
import ink.myumoon.tradingtable.client.screen.TradingTableTradeScreen;
import ink.myumoon.tradingtable.registries.TTBlockEntities;
import ink.myumoon.tradingtable.registries.TTMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = HarvistasTradingTable.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = HarvistasTradingTable.MODID, value = Dist.CLIENT)
public class HarvistasTradingTableClient {
    public HarvistasTradingTableClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {

    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(TTMenuTypes.TRADING_TABLE_INIT.get(), TradingTableInitScreen::new);
        event.register(TTMenuTypes.TRADING_TABLE_TRADE.get(), TradingTableTradeScreen::new);
        event.register(TTMenuTypes.TRADING_TABLE_MANAGE.get(), TradingTableScreen::new);
        // event.register(TTMenuTypes.SYSTEM_TRADING_TABLE_INIT.get(), SystemTradingTableInitScreen::new);
        // event.register(TTMenuTypes.SYSTEM_TRADING_TABLE_TRADE.get(), SystemTradingTableTradeScreen::new);
        // event.register(TTMenuTypes.SYSTEM_TRADING_TABLE_MANAGE.get(), SystemTradingTableScreen::new);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                TTBlockEntities.TRADING_TABLE.get(),
                TradingTableRenderer::new
        );
    }
}
