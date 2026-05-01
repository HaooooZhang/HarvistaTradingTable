package ink.myumoon.tradingtable;

import com.mojang.logging.LogUtils;
import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.registry.TTRegistries;
import ink.myumoon.tradingtable.util.TradingTableCapabilities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(HarvistasTradingTable.MODID)
public class HarvistasTradingTable {
    public static final String MODID = "trading_table";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HarvistasTradingTable(IEventBus modEventBus, ModContainer modContainer) {
        TTRegistries.register(modEventBus);
        modEventBus.addListener(Config::onLoad);
        modEventBus.addListener(TradingTableCapabilities::registerCapabilities);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
