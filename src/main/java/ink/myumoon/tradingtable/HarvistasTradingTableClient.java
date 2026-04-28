package ink.myumoon.tradingtable;

import ink.myumoon.tradingtable.blockentity.renderer.TradingTableRenderer;
import ink.myumoon.tradingtable.client.screen.TradingTableInitScreen;
import ink.myumoon.tradingtable.client.screen.TradingTableScreen;
import ink.myumoon.tradingtable.client.screen.TradingTableTradeScreen;
import ink.myumoon.tradingtable.registry.TTBlockEntities;
import ink.myumoon.tradingtable.registry.TTMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = HarvistasTradingTable.MODID, dist = Dist.CLIENT)
public class HarvistasTradingTableClient {
    public HarvistasTradingTableClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(this::registerMenuScreens);
        modEventBus.addListener(this::registerRenderers);
    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(TTMenuTypes.TRADING_TABLE_INIT.get(), TradingTableInitScreen::new);
        event.register(TTMenuTypes.TRADING_TABLE_TRADE.get(), TradingTableTradeScreen::new);
        event.register(TTMenuTypes.TRADING_TABLE_MANAGE.get(), TradingTableScreen::new);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TTBlockEntities.TRADING_TABLE.get(), TradingTableRenderer::new);
    }
}
