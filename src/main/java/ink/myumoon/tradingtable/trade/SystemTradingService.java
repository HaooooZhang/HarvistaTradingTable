package ink.myumoon.tradingtable.trade;

import ink.myumoon.tradingtable.blockentity.SystemTradingTableBlockEntity;
import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.config.CurrencyBackend;
import ink.myumoon.tradingtable.economy.MystiasIzakayaEconomyBackend;
import ink.myumoon.tradingtable.economy.NeoEssentialsEconomyBackend;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class SystemTradingService {
    private SystemTradingService() {
    }

    public static TradingService.TradeResult executeTrade(Player player, SystemTradingTableBlockEntity table, int amount) {
        if (!table.isInitialized()) {
            return TradingService.TradeResult.fail("message.trading_table.not_initialized", false);
        }
        if (!table.isEnabled()) {
            return TradingService.TradeResult.fail("message.trading_table.trade_disabled", false);
        }

        Item tradeItem = table.getTradeItem();
        if (tradeItem == null) {
            return TradingService.TradeResult.fail("message.trading_table.invalid_trade_item", true);
        }

        int minAmount = table.getMinTradeAmount();
        if (amount < minAmount || amount % minAmount != 0) {
            return TradingService.TradeResult.fail("message.trading_table.invalid_trade_amount", false);
        }

        long tradeUnits = amount / minAmount;
        double gross = (double) table.getUnitPrice() * tradeUnits;
        double tax = TaxService.calculateTax(gross);
        double net = Math.max(0.0D, gross - tax);

        if (table.isBuyOrder()) {
            return executeBuyOrder(player, table, tradeItem, amount, minAmount, gross, net);
        }
        return executeSellOrder(player, table, tradeItem, amount, minAmount, gross);
    }

    /**
     * 系统售出物品给玩家（Sell Order）。
     * 系统拥有无限库存，无需检查库存；无需税收，无需更新余额。
     * 玩家支付 gross，系统直接生成物品给玩家。
     */
    private static TradingService.TradeResult executeSellOrder(Player player, SystemTradingTableBlockEntity table,
                                                                Item tradeItem, int amount, int minAmount, double gross) {
        // MystiasIzakaya 模式：先原子扣款（失败不部分扣），再发物品；扣款失败时不生成物品
        if (Config.getCurrencyBackend() == CurrencyBackend.MYSTIAS_IZAKAYA) {
            int intGross = (int) Math.floor(gross);
            MystiasIzakayaEconomyBackend.ChangeResult paid =
                    MystiasIzakayaEconomyBackend.subtractBalanceDetailed(player, intGross);
            if (!paid.fullyApplied()) {
                return TradingService.TradeResult.fail("message.trading_table.player_currency_too_low", false);
            }
            TradingService.giveToPlayer(player, new ItemStack(tradeItem, amount));
            TradingService.grantTradeAdvancement(player);
            return TradingService.TradeResult.success("message.trading_table.trade_success");
        }

        // NeoEssentials 模式：通过 API 检查并扣除玩家余额
        if (Config.getCurrencyBackend() == CurrencyBackend.NEO_ESSENTIALS) {
            double playerBalance = NeoEssentialsEconomyBackend.getBalance(player.getUUID());
            if (playerBalance + 1.0E-9D < gross) {
                return TradingService.TradeResult.fail("message.trading_table.player_currency_too_low", false);
            }
            if (!NeoEssentialsEconomyBackend.subtractBalance(player.getUUID(), gross)) {
                return TradingService.TradeResult.fail("message.trading_table.player_currency_too_low", false);
            }
            TradingService.giveToPlayer(player, new ItemStack(tradeItem, amount));
            TradingService.grantTradeAdvancement(player);
            return TradingService.TradeResult.success("message.trading_table.trade_success");
        }

        // ITEM 模式
        Item currency = Config.getCurrencyItem();
        long playerCurrency = ConversionService.isEnabled()
                ? ConversionService.totalValue(player.getInventory().getNonEquipmentItems())
                : TradingService.countInPlayer(player, currency);
        if ((double) playerCurrency < gross) {
            return TradingService.TradeResult.fail("message.trading_table.player_currency_too_low", false);
        }

        boolean removed = ConversionService.isEnabled()
                ? TradingService.removeMixedCurrencyFromPlayer(player, gross)
                : TradingService.removeFromPlayer(player, currency, gross);
        if (!removed) {
            return TradingService.TradeResult.fail("message.trading_table.player_currency_too_low", false);
        }

        TradingService.giveToPlayer(player, new ItemStack(tradeItem, amount));
        TradingService.grantTradeAdvancement(player);
        return TradingService.TradeResult.success("message.trading_table.trade_success");
    }

    /**
     * 系统从玩家收购物品（Buy Order）。
     * 系统拥有无限余额和无限库存容量，物品直接消失。
     * 税收适用：玩家获得 net = gross - tax（税收沉没）。
     */
    private static TradingService.TradeResult executeBuyOrder(Player player, SystemTradingTableBlockEntity table,
                                                               Item tradeItem, int amount, int minAmount,
                                                               double gross, double net) {
        // 检查玩家库存
        int playerItems = TradingService.countInPlayer(player, tradeItem);
        if (playerItems < minAmount) {
            return TradingService.TradeResult.fail("message.trading_table.player_item_too_low", false);
        }
        if (playerItems < amount) {
            return TradingService.TradeResult.fail("message.trading_table.player_item_too_low", false);
        }

        // MystiasIzakaya 模式：先扣玩家物品，成功后原子入账；入账失败要退回玩家物品
        if (Config.getCurrencyBackend() == CurrencyBackend.MYSTIAS_IZAKAYA) {
            if (!TradingService.removeFromPlayer(player, tradeItem, amount)) {
                return TradingService.TradeResult.fail("message.trading_table.player_item_too_low", false);
            }
            int intNet = (int) Math.floor(net);
            MystiasIzakayaEconomyBackend.ChangeResult credited =
                    MystiasIzakayaEconomyBackend.addBalanceDetailed(player, intNet);
            if (!credited.fullyApplied()) {
                // 入账失败，退还已扣的物品
                TradingService.giveToPlayer(player, new ItemStack(tradeItem, amount));
                return TradingService.TradeResult.fail("message.trading_table.player_currency_too_low", false);
            }
            TradingService.grantTradeAdvancement(player);
            return TradingService.TradeResult.success("message.trading_table.trade_success");
        }

        // NeoEssentials 模式
        if (Config.getCurrencyBackend() == CurrencyBackend.NEO_ESSENTIALS) {
            if (!TradingService.removeFromPlayer(player, tradeItem, amount)) {
                return TradingService.TradeResult.fail("message.trading_table.player_item_too_low", false);
            }
            NeoEssentialsEconomyBackend.addBalance(player.getUUID(), net);
            TradingService.grantTradeAdvancement(player);
            return TradingService.TradeResult.success("message.trading_table.trade_success");
        }

        // ITEM 模式
        if (!TradingService.removeFromPlayer(player, tradeItem, amount)) {
            return TradingService.TradeResult.fail("message.trading_table.player_item_too_low", false);
        }

        TradingService.giveCurrencyToPlayer(player, Config.getCurrencyItem(), net);
        TradingService.grantTradeAdvancement(player);
        return TradingService.TradeResult.success("message.trading_table.trade_success");
    }
}
