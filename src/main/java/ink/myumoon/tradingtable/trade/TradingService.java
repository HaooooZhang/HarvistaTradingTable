package ink.myumoon.tradingtable.trade;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.config.CurrencyBackend;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import ink.myumoon.tradingtable.economy.NeoEssentialsEconomyService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TradingService {
    private TradingService() {
    }

    // 最简单交易模块，目前没有使用
    public static TradeResult executeMinimumTrade(Player player, TradingTableBlockEntity table) {
        return executeTrade(player, table, table.getMinTradeAmount());
    }

    // 交易执行
    public static TradeResult executeTrade(Player player, TradingTableBlockEntity table, int amount) {
        if (!table.isInitialized()) {
            return TradeResult.fail("message.trading_table.not_initialized", false);
        }
        if (!table.isEnabled()) {
            return TradeResult.fail("message.trading_table.trade_disabled", false);
        }

        Item tradeItem = table.getTradeItem();
        if (tradeItem == null) {
            return TradeResult.fail("message.trading_table.invalid_trade_item", true);
        }

        int minAmount = table.getMinTradeAmount();
        if (amount < minAmount || amount % minAmount != 0) {
            return TradeResult.fail("message.trading_table.invalid_trade_amount", false);
        }

        long tradeUnits = amount / minAmount;
        double gross = (double) table.getUnitPrice() * tradeUnits;
        double tax = TaxService.calculateTax(gross);
        double net = Math.max(0.0D, gross - tax);

        if (table.isBuyOrder()) {
            return executeBuyOrder(player, table, tradeItem, amount, minAmount, gross, net);
        }
        return executeSellOrder(player, table, tradeItem, amount, minAmount, gross, net);
    }

    // 售出处理
    private static TradeResult executeSellOrder(Player player, TradingTableBlockEntity table, Item tradeItem, int amount,
                                                int minAmount, double gross, double net) {
        // 检查库存
        int stock = countInHandler(table.getInventoryHandler(), tradeItem);
        if (stock < minAmount) {
            return TradeResult.fail("message.trading_table.stock_too_low", true);
        }
        if (stock < amount) {
            return TradeResult.fail("message.trading_table.stock_not_enough_for_request", false);
        }

        // NeoEssentials 模式：通过 API 检查并扣除玩家余额
        if (Config.getCurrencyBackend() == CurrencyBackend.NEO_ESSENTIALS) {
            double playerBalance = NeoEssentialsEconomyService.getBalance(player.getUUID());
            if (playerBalance + 1.0E-9D < gross) {
                return TradeResult.fail("message.trading_table.player_currency_too_low", false);
            }
            if (!removeFromHandler(table.getInventoryHandler(), tradeItem, amount)) {
                return TradeResult.fail("message.trading_table.stock_too_low", true);
            }
            if (!NeoEssentialsEconomyService.subtractBalance(player.getUUID(), gross)) {
                return TradeResult.fail("message.trading_table.player_currency_too_low", false);
            }
            giveToPlayer(player, new ItemStack(tradeItem, amount));
            table.depositCurrency(net);
            return TradeResult.success("message.trading_table.trade_success");
        }

        // ITEM 模式
        // 检查玩家余额
        Item currency = Config.getCurrencyItem();
        long playerCurrency = ConversionService.isEnabled()
                ? ConversionService.totalValue(player.getInventory().items)
                : countInPlayer(player, currency);
        if ((double) playerCurrency < gross) {
            return TradeResult.fail("message.trading_table.player_currency_too_low", false);
        }

        //执行交易
        if (!removeFromHandler(table.getInventoryHandler(), tradeItem, amount)) {
            return TradeResult.fail("message.trading_table.stock_too_low", true);
        }
        boolean removed = ConversionService.isEnabled()
                ? removeMixedCurrencyFromPlayer(player, gross)
                : removeFromPlayer(player, currency, gross);
        if (!removed) {
            return TradeResult.fail("message.trading_table.player_currency_too_low", false);
        }

        giveToPlayer(player, new ItemStack(tradeItem, amount));
        table.depositCurrency(net);
        return TradeResult.success("message.trading_table.trade_success");
    }

    // 购买处理
    private static TradeResult executeBuyOrder(Player player, TradingTableBlockEntity table, Item tradeItem, int amount,
                                               int minAmount, double gross, double net) {
        //检查玩家库存
        int playerItems = countInPlayer(player, tradeItem);
        if (playerItems < minAmount) {
            return TradeResult.fail("message.trading_table.player_item_too_low", false);
        }
        if (playerItems < amount) {
            return TradeResult.fail("message.trading_table.player_item_too_low", false);
        }

        // 检查交易站库存容量（先模拟，避免部分写入）
        ItemStack simulatedRemainder = insertIntoHandler(table.getInventoryHandler(), new ItemStack(tradeItem, amount), true);
        if (!simulatedRemainder.isEmpty()) {
            return TradeResult.fail("message.trading_table.stock_full", false);
        }

        // NeoEssentials 模式：通过 API 检查余额、扣款、转账
        if (Config.getCurrencyBackend() == CurrencyBackend.NEO_ESSENTIALS) {
            if (table.getCurrencyBalance() + 1.0E-9D < (double) table.getUnitPrice()) {
                return TradeResult.fail("message.trading_table.owner_currency_too_low", true);
            }
            if (table.getCurrencyBalance() + 1.0E-9D < gross) {
                return TradeResult.fail("message.trading_table.owner_currency_not_enough_for_request", false);
            }

            ItemStack remainder = insertIntoHandler(table.getInventoryHandler(), new ItemStack(tradeItem, amount), false);
            if (!remainder.isEmpty()) {
                return TradeResult.fail("message.trading_table.stock_full", false);
            }
            if (!removeFromPlayer(player, tradeItem, amount)) {
                return TradeResult.fail("message.trading_table.player_item_too_low", false);
            }
            if (!table.tryWithdrawCurrency(gross)) {
                return TradeResult.fail("message.trading_table.owner_currency_too_low", true);
            }
            NeoEssentialsEconomyService.addBalance(player.getUUID(), net);
            return TradeResult.success("message.trading_table.trade_success");
        }

        // ITEM 模式
        //检查交易站货币余额
        if (table.getCurrencyBalance() < (double) table.getUnitPrice()) {
            return TradeResult.fail("message.trading_table.owner_currency_too_low", true);
        }
        if (table.getCurrencyBalance() < gross) {
            return TradeResult.fail("message.trading_table.owner_currency_not_enough_for_request", false);
        }

        // 执行交易
        ItemStack remainder = insertIntoHandler(table.getInventoryHandler(), new ItemStack(tradeItem, amount), false);
        if (!remainder.isEmpty()) {
            return TradeResult.fail("message.trading_table.stock_full", false);
        }

        if (!removeFromPlayer(player, tradeItem, amount)) {
            return TradeResult.fail("message.trading_table.player_item_too_low", false);
        }
        if (!table.tryWithdrawCurrency(gross)) {
            return TradeResult.fail("message.trading_table.owner_currency_too_low", true);
        }

        giveCurrencyToPlayer(player, Config.getCurrencyItem(), net);
        return TradeResult.success("message.trading_table.trade_success");
    }

    private static int countInPlayer(Player player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int countInHandler(ItemStackHandler handler, Item item) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean removeFromPlayer(Player player, Item item, double amount) {
        long wholeAmount = (long) Math.floor(amount);
        if (wholeAmount <= 0L) {
            return false;
        }
        long remaining = wholeAmount;
        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.is(item)) {
                continue;
            }
            int remove = (int) Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            remaining -= remove;
        }
        return remaining == 0L;
    }

    private static boolean removeMixedCurrencyFromPlayer(Player player, double amount) {
        long wholeAmount = (long) Math.floor(amount);
        if (wholeAmount <= 0L) {
            return false;
        }
        Map<Item, Integer> available = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (ConversionService.isRegistered(item)) {
                available.put(item, available.getOrDefault(item, 0) + stack.getCount());
            }
        }

        Optional<List<ItemStack>> payment = ConversionService.tryFillPayment(wholeAmount, available);
        if (payment.isEmpty()) {
            return false;
        }

        List<ItemStack> toConsume = payment.get();

        // 计算实际支付的总价值（可能大于 wholeAmount）
        long totalPaymentValue = 0L;
        for (ItemStack stack : toConsume) {
            long itemValue = ConversionService.getValue(stack.getItem());
            totalPaymentValue += itemValue * stack.getCount();
        }

        // 计算找零
        long changeValue = totalPaymentValue - wholeAmount;

        // 从玩家背包扣除支付的货币
        for (ItemStack need : toConsume) {
            int remaining = need.getCount();
            for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
                ItemStack stack = player.getInventory().items.get(i);
                if (!stack.is(need.getItem())) {
                    continue;
                }
                int remove = Math.min(remaining, stack.getCount());
                stack.shrink(remove);
                remaining -= remove;
            }
            if (remaining > 0) {
                return false;
            }
        }

        // 如果有找零，转换为物品并返还给玩家
        if (changeValue > 0L) {
            List<ItemStack> changeStacks = ConversionService.convertBalanceToStacks(changeValue);
            for (ItemStack changeStack : changeStacks) {
                ItemStack remaining = changeStack.copy();
                if (!player.getInventory().add(remaining)) {
                    // 背包放不下，掉落在地上
                    ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), remaining);
                    drop.setPickUpDelay(0);
                    player.level().addFreshEntity(drop);
                }
            }
        }

        return true;
    }

    private static boolean removeFromHandler(ItemStackHandler handler, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.is(item)) {
                continue;
            }
            int remove = Math.min(remaining, stack.getCount());
            handler.extractItem(i, remove, false);
            remaining -= remove;
        }
        return remaining == 0;
    }

    private static ItemStack insertIntoHandler(ItemStackHandler handler, ItemStack toInsert, boolean simulate) {
        ItemStack remainder = toInsert;
        for (int i = 0; i < handler.getSlots() && !remainder.isEmpty(); i++) {
            remainder = handler.insertItem(i, remainder, simulate);
        }
        return remainder;
    }

    private static void giveToPlayer(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remaining = stack.copy();
        if (player.getInventory().add(remaining)) {
            return;
        }

        ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), remaining);
        drop.setPickUpDelay(0);
        player.level().addFreshEntity(drop);
    }

    private static void giveCurrencyToPlayer(Player player, Item currencyItem, double amount) {
        long wholeAmount = (long) Math.floor(amount);
        if (wholeAmount <= 0L) {
            return;
        }

        if (ConversionService.isEnabled()) {
            for (ItemStack stack : ConversionService.convertBalanceToStacks(wholeAmount)) {
                giveToPlayer(player, stack);
            }
            return;
        }

        int maxStack = currencyItem.getDefaultMaxStackSize();
        long remaining = wholeAmount;
        while (remaining > 0L) {
            int toGive = (int) Math.min(remaining, maxStack);
            giveToPlayer(player, new ItemStack(currencyItem, toGive));
            remaining -= toGive;
        }
    }

    public record TradeResult(boolean success, boolean disableTable, String messageKey) {
        public static TradeResult success(String messageKey) {
            return new TradeResult(true, false, messageKey);
        }

        public static TradeResult fail(String messageKey, boolean disableTable) {
            return new TradeResult(false, disableTable, messageKey);
        }

        public void sendTo(Player player) {
            player.displayClientMessage(Component.translatable(this.messageKey), true);
        }
    }
}
