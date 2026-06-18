package ink.myumoon.tradingtable;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.economy.MystiasIzakayaEconomyBackend;
import ink.myumoon.tradingtable.economy.MystiasIzakayaPendingBalance;
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

        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
    }

    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // NMI 模式：结算离线待处理余额，并入 TradeNoticeService 统一通知
            if (Config.isMystiasIzakayaMode()) {
                MystiasIzakayaPendingBalance pending = MystiasIzakayaPendingBalance.get(player.level().getServer());
                int netDelta = pending.drain(player.getUUID());
                if (netDelta != 0) {
                    TradeNoticeService notice = TradeNoticeService.get(player.level().getServer());
                    if (netDelta > 0) {
                        MystiasIzakayaEconomyBackend.addBalance(player, netDelta);
                        // netDelta > 0: 入账 → 收入
                        notice.accumulateForNotice(player.getUUID(), false, netDelta);
                    } else {
                        MystiasIzakayaEconomyBackend.subtractBalance(player, -netDelta);
                        // netDelta < 0: 出账 → 支出
                        notice.accumulateForNotice(player.getUUID(), true, -netDelta);
                    }
                }
            }

            TradeNoticeService.onPlayerLogin(player);
        }
    }
}
