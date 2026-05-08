package ink.myumoon.tradingtable.menu;

import ink.myumoon.tradingtable.blockentity.SystemTradingTableBlockEntity;
import ink.myumoon.tradingtable.registry.TTBlocks;
import ink.myumoon.tradingtable.registry.TTMenuTypes;
import ink.myumoon.tradingtable.trade.SystemTradingService;
import ink.myumoon.tradingtable.trade.TradingService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class SystemTradingTableTradeMenu extends AbstractContainerMenu {
    public static final int INVENTORY_SLOTS = 1;
    public static final int BUTTON_EXECUTE = 0;
    public static final int BUTTON_TOGGLE_TYPE = 1;
    public static final int BUTTON_AMOUNT_PLUS = 2;
    public static final int BUTTON_AMOUNT_MINUS = 3;
    public static final int BUTTON_AMOUNT_PLUS_8 = 4;
    public static final int BUTTON_AMOUNT_PLUS_32 = 5;
    public static final int BUTTON_AMOUNT_MINUS_8 = 6;
    public static final int BUTTON_AMOUNT_MINUS_32 = 7;

    private final ContainerLevelAccess access;
    private int requestedAmount = 1;
    private int cachedMin = 1;
    private int cachedPrice = 1;
    private int cachedType = 0;
    private int cachedStock = Integer.MAX_VALUE;
    private int cachedTradeItemId = -1;
    private int cachedTradeEventId = 0;
    private int cachedTradeResult = 0;
    private final ContainerData viewData = new ContainerData() {
        @Override
        public int get(int index) {
            refreshFromBlockEntity();
            return switch (index) {
                case 0 -> requestedAmount;
                case 1 -> cachedMin;
                case 2 -> cachedPrice;
                case 3 -> cachedType;
                case 4 -> cachedStock;
                case 5 -> cachedTradeItemId;
                case 6 -> cachedTradeEventId;
                case 7 -> cachedTradeResult;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> requestedAmount = Math.max(1, value);
                case 1 -> cachedMin = value;
                case 2 -> cachedPrice = value;
                case 3 -> cachedType = value;
                case 4 -> cachedStock = value;
                case 5 -> cachedTradeItemId = value;
                case 6 -> cachedTradeEventId = Math.max(0, value);
                case 7 -> cachedTradeResult = value > 0 ? 1 : 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 8;
        }
    };

    public SystemTradingTableTradeMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new ItemStackHandler(INVENTORY_SLOTS), ContainerLevelAccess.NULL);
    }

    public SystemTradingTableTradeMenu(int containerId, Inventory playerInventory, ItemStackHandler inventory,
                                       ContainerLevelAccess access) {
        super(TTMenuTypes.SYSTEM_TRADING_TABLE_TRADE.get(), containerId);
        if (inventory.getSlots() != INVENTORY_SLOTS) {
            throw new IllegalStateException("Unexpected handler size for SystemTradingTableTradeMenu");
        }

        this.access = access;
        this.addDataSlots(this.viewData);

        // Player inventory (3x9).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;
                this.addSlot(new Slot(playerInventory, index, 8 + col * 18, 108 + row * 18));
            }
        }

        // Hotbar.
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 166));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(this.access, player, TTBlocks.SYSTEM_TRADING_TABLE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int quickMovedSlotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return this.access.evaluate((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof SystemTradingTableBlockEntity table)) {
                return false;
            }

            int minAmount = Math.max(1, table.getMinTradeAmount());
            if (this.requestedAmount < minAmount) {
                this.requestedAmount = minAmount;
            }

            if (id == BUTTON_EXECUTE) {
                TradingService.TradeResult result = SystemTradingService.executeTrade(player, table, this.requestedAmount);
                this.cachedTradeEventId = this.cachedTradeEventId == Integer.MAX_VALUE ? 1 : this.cachedTradeEventId + 1;
                this.cachedTradeResult = result.success() ? 1 : 0;
                result.sendTo(player);
                return true;
            }

            if (id == BUTTON_TOGGLE_TYPE) {
                if (!table.canManage(player)) {
                    player.displayClientMessage(Component.translatable("message.trading_table.no_permission_manage"), true);
                    return false;
                }
                table.setBuyOrder(!table.isBuyOrder());
                player.displayClientMessage(Component.translatable("message.trading_table.toggled_trade_type"), true);
                return true;
            }

            if (id == BUTTON_AMOUNT_PLUS || id == BUTTON_AMOUNT_PLUS_8 || id == BUTTON_AMOUNT_PLUS_32) {
                int step = getStep(id);
                long nextAmount = (long) this.requestedAmount + (long) minAmount * step;
                this.requestedAmount = (int) Math.min(Integer.MAX_VALUE, nextAmount);
                return true;
            }

            if (id == BUTTON_AMOUNT_MINUS || id == BUTTON_AMOUNT_MINUS_8 || id == BUTTON_AMOUNT_MINUS_32) {
                int step = getStep(id);
                long nextAmount = (long) this.requestedAmount - (long) minAmount * step;
                this.requestedAmount = (int) Math.max(minAmount, nextAmount);
                return true;
            }

            return false;
        }, false);
    }

    public int getRequestedAmount() {
        return this.viewData.get(0);
    }

    public int getMinTradeAmount() {
        return this.viewData.get(1);
    }

    public int getUnitPrice() {
        return this.viewData.get(2);
    }

    public boolean isBuyOrder() {
        return this.viewData.get(3) == 1;
    }

    public int getStockCount() {
        return this.viewData.get(4);
    }

    public net.minecraft.world.item.Item getTradeItem() {
        int tradeItemId = this.viewData.get(5);
        return tradeItemId < 0 ? null : BuiltInRegistries.ITEM.byId(tradeItemId);
    }

    public int getTradeEventId() {
        return this.viewData.get(6);
    }

    public boolean wasLastTradeSuccessful() {
        return this.viewData.get(7) == 1;
    }

    private void refreshFromBlockEntity() {
        this.access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof SystemTradingTableBlockEntity table) {
                this.cachedMin = table.getMinTradeAmount();
                this.cachedPrice = (int) Math.min(Integer.MAX_VALUE, table.getUnitPrice());
                this.cachedType = table.isBuyOrder() ? 1 : 0;
                this.cachedTradeItemId = table.getTradeItem() == null ? -1 : BuiltInRegistries.ITEM.getId(table.getTradeItem());

                if (this.requestedAmount < this.cachedMin) {
                    this.requestedAmount = this.cachedMin;
                }
            }
        });
    }

    private static int getStep(int id) {
        if (id == BUTTON_AMOUNT_PLUS_32 || id == BUTTON_AMOUNT_MINUS_32) {
            return 32;
        }
        if (id == BUTTON_AMOUNT_PLUS_8 || id == BUTTON_AMOUNT_MINUS_8) {
            return 8;
        }
        return 1;
    }
}
