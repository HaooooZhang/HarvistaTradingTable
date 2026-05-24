package ink.myumoon.tradingtable;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.registries.TTRegistries;
import ink.myumoon.tradingtable.trade.TradeNoticeService;
import ink.myumoon.tradingtable.util.TradingTableCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

@Mod(HarvistasTradingTable.MODID)
public class HarvistasTradingTable {
    public static final String MODID = "trading_table";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HarvistasTradingTable(IEventBus modEventBus, ModContainer modContainer) {
        TTRegistries.register(modEventBus);
        modEventBus.addListener(Config::onLoad);
        modEventBus.addListener(TradingTableCapabilities::registerCapabilities);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 玩家上线事件：发送离线累计通知（SavedData 自动管理加载/保存）
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
    }

    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TradeNoticeService.onPlayerLogin(player);
        }
    }
}
