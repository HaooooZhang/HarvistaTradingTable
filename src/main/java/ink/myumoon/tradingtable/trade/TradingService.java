package ink.myumoon.tradingtable.trade;

import ink.myumoon.tradingtable.Config;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

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
        long gross = table.getUnitPrice() * tradeUnits;
        long tax = Config.roundTax(gross);
        long net = Math.max(0L, gross - tax);

        if (table.isBuyOrder()) {
            return executeBuyOrder(player, table, tradeItem, amount, minAmount, gross, net);
        }
        return executeSellOrder(player, table, tradeItem, amount, minAmount, gross, net);
    }

    // 售出处理
    private static TradeResult executeSellOrder(Player player, TradingTableBlockEntity table, Item tradeItem, int amount,
                                                int minAmount, long gross, long net) {
        // 检查库存
        int stock = countInHandler(table.getInventoryHandler(), tradeItem);
        if (stock < minAmount) {
            return TradeResult.fail("message.trading_table.stock_too_low", true);
        }
        if (stock < amount) {
            return TradeResult.fail("message.trading_table.stock_not_enough_for_request", false);
        }

        // 检查玩家余额
        Item currency = Config.getVanillaCurrencyItem();
        int playerCurrency = countInPlayer(player, currency);
        long minimumGross = table.getUnitPrice();
        if (playerCurrency < minimumGross) {
            return TradeResult.fail("message.trading_table.player_currency_too_low", false);
        }
        if (playerCurrency < gross) {
            return TradeResult.fail("message.trading_table.player_currency_too_low", false);
        }

        //执行交易
        if (!removeFromHandler(table.getInventoryHandler(), tradeItem, amount)) {
            return TradeResult.fail("message.trading_table.stock_too_low", true);
        }
        if (!removeFromPlayer(player, currency, gross)) {
            return TradeResult.fail("message.trading_table.player_currency_too_low", false);
        }

        giveToPlayer(player, new ItemStack(tradeItem, amount));
        table.depositCurrency(net);
        return TradeResult.success("message.trading_table.trade_success");
    }

    // 购买处理
    private static TradeResult executeBuyOrder(Player player, TradingTableBlockEntity table, Item tradeItem, int amount,
                                               int minAmount, long gross, long net) {
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

        //检查交易站货币余额
        long minimumGross = table.getUnitPrice();
        if (table.getCurrencyBalance() < minimumGross) {
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

        giveCurrencyToPlayer(player, Config.getVanillaCurrencyItem(), net);
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

    private static boolean removeFromPlayer(Player player, Item item, long amount) {
        long remaining = amount;
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

    private static void giveCurrencyToPlayer(Player player, Item currencyItem, long amount) {
        if (amount <= 0L) {
            return;
        }

        int maxStack = currencyItem.getDefaultMaxStackSize();
        long remaining = amount;
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
