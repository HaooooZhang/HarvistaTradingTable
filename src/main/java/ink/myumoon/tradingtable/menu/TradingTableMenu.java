package ink.myumoon.tradingtable.menu;

import ink.myumoon.tradingtable.Config;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import ink.myumoon.tradingtable.registry.TTBlocks;
import ink.myumoon.tradingtable.registry.TTMenuTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class TradingTableMenu extends AbstractContainerMenu {
    public static final int INVENTORY_SLOTS = 27;
    public static final int PLAYER_INV_SLOTS = 27;
    public static final int HOTBAR_SLOTS = 9;

    public static final int BUTTON_TOGGLE_ENABLED = 0;
    public static final int BUTTON_SET_TYPE_SELL = 1;
    public static final int BUTTON_SET_TYPE_BUY = 2;
    public static final int BUTTON_MIN_PLUS = 3;
    public static final int BUTTON_MIN_MINUS = 4;
    public static final int BUTTON_PRICE_PLUS = 5;
    public static final int BUTTON_PRICE_MINUS = 6;
    public static final int BUTTON_SAVE = 7;
    public static final int BUTTON_MIN_PLUS_8 = 8;
    public static final int BUTTON_MIN_PLUS_32 = 9;
    public static final int BUTTON_PRICE_PLUS_8 = 10;
    public static final int BUTTON_PRICE_PLUS_32 = 11;
    public static final int BUTTON_EXTRACT = 12;
    public static final int BUTTON_EXTRACT_STACK = 13;
    public static final int BUTTON_EXTRACT_ALL = 14;
    public static final int BUTTON_MIN_MINUS_8 = 15;
    public static final int BUTTON_MIN_MINUS_32 = 16;
    public static final int BUTTON_PRICE_MINUS_8 = 17;
    public static final int BUTTON_PRICE_MINUS_32 = 18;
    public static final int BUTTON_CONFIRM_TRADE_ITEM = 19;
    public static final int BUTTON_NAME_CLEAR = 2000;
    public static final int BUTTON_NAME_APPEND_HIGH_BASE = 3000;
    public static final int BUTTON_NAME_APPEND_HIGH_MAX = BUTTON_NAME_APPEND_HIGH_BASE + 255;
    public static final int BUTTON_NAME_APPEND_LOW_BASE = 4000;
    public static final int BUTTON_NAME_APPEND_LOW_MAX = BUTTON_NAME_APPEND_LOW_BASE + 255;


    private static final int INVENTORY_START = 0;
    private static final int INVENTORY_END = INVENTORY_START + INVENTORY_SLOTS;
    private static final int TRADE_ITEM_ALIAS_START = INVENTORY_END;
    private static final int TRADE_ITEM_ALIAS_END = TRADE_ITEM_ALIAS_START + 1;
    private static final int PLAYER_INV_START = TRADE_ITEM_ALIAS_END;
    private static final int PLAYER_INV_END = PLAYER_INV_START + PLAYER_INV_SLOTS;
    private static final int HOTBAR_START = PLAYER_INV_END;
    private static final int HOTBAR_END = HOTBAR_START + HOTBAR_SLOTS;

    private static final int TABLE_INV_X = 88;
    private static final int TABLE_INV_Y = 18;
    private static final int PLAYER_INV_X = 88;
    private static final int PLAYER_INV_Y = 84;
    private static final int HOTBAR_X = 88;
    private static final int HOTBAR_Y = 142;
    private static final int TRADE_ITEM_ALIAS_X = 262;
    private static final int TRADE_ITEM_ALIAS_Y = 60;

    private final ItemStackHandler inventory;
    private final ContainerLevelAccess access;
    private final boolean allowManage;
    private int cachedMin = 1;
    private int cachedPrice = 1;
    private int cachedType = 0;
    private int cachedEnabled = 0;
    private int cachedCashierWhole = 0;
    private int cachedCashierFractional = 0;
    private int cachedTradeItemId = -1;
    private boolean cacheInitialized;
    private boolean hasPendingManageChanges;
    private final StringBuilder pendingTableName = new StringBuilder();
    private int pendingNameHighByte = -1;
    private final ContainerData viewData = new ContainerData() {
        @Override
        public int get(int index) {
            refreshFromBlockEntity();
            return switch (index) {
                case 0 -> cachedMin;
                case 1 -> cachedPrice;
                case 2 -> cachedType;
                case 3 -> cachedEnabled;
                case 4 -> cachedCashierWhole;
                case 5 -> cachedCashierFractional;
                case 6 -> cachedTradeItemId;
                case 7 -> hasPendingManageChanges ? 1 : 0;
                default -> 0;
            };
        }

        //todo 逻辑有问题，要保存才可以有库存更新
        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> cachedMin = value;
                case 1 -> cachedPrice = value;
                case 2 -> cachedType = value;
                case 3 -> cachedEnabled = value;
                case 4 -> cachedCashierWhole = value;
                case 5 -> cachedCashierFractional = value;
                case 6 -> cachedTradeItemId = value;
                case 7 -> hasPendingManageChanges = value > 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 8;
        }
    };

    public TradingTableMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new ItemStackHandler(INVENTORY_SLOTS), ContainerLevelAccess.NULL, true);
    }

    public TradingTableMenu(int containerId, Inventory playerInventory, ItemStackHandler inventory,
                            ContainerLevelAccess access, boolean allowManage) {
        super(TTMenuTypes.TRADING_TABLE_MANAGE.get(), containerId);
        if (inventory.getSlots() != INVENTORY_SLOTS) {
            throw new IllegalStateException("Unexpected handler size for TradingTableMenu");
        }

        this.inventory = inventory;
        this.access = access;
        this.allowManage = allowManage;
        this.addDataSlots(this.viewData);

        // Trading table inventory (3x9) in middle panel.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                this.addSlot(new SlotItemHandler(inventory, index, TABLE_INV_X + col * 18, TABLE_INV_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return TradingTableMenu.this.allowManage;
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return TradingTableMenu.this.allowManage;
                    }
                });
            }
        }

        // Right-panel full-function alias for inventory slot[0].
        this.addSlot(new SlotItemHandler(inventory, 0, TRADE_ITEM_ALIAS_X, TRADE_ITEM_ALIAS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TradingTableMenu.this.allowManage;
            }

            @Override
            public boolean mayPickup(Player player) {
                return TradingTableMenu.this.allowManage;
            }
        });

        // Player inventory (3x9).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;
                this.addSlot(new Slot(playerInventory, index, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // Hotbar.
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, HOTBAR_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(this.access, player, TTBlocks.TRADING_TABLE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int quickMovedSlotIndex) {
        if (!this.allowManage) {
            return ItemStack.EMPTY;
        }

        ItemStack quickMovedStack = ItemStack.EMPTY;
        Slot quickMovedSlot = this.slots.get(quickMovedSlotIndex);

        if (quickMovedSlot != null && quickMovedSlot.hasItem()) {
            ItemStack rawStack = quickMovedSlot.getItem();
            quickMovedStack = rawStack.copy();

            if (quickMovedSlotIndex < PLAYER_INV_START) {
                if (!this.moveItemStackTo(rawStack, PLAYER_INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(rawStack, INVENTORY_START, INVENTORY_END, false)) {
                    if (quickMovedSlotIndex < PLAYER_INV_END) {
                        if (!this.moveItemStackTo(rawStack, HOTBAR_START, HOTBAR_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(rawStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (rawStack.isEmpty()) {
                quickMovedSlot.set(ItemStack.EMPTY);
            } else {
                quickMovedSlot.setChanged();
            }

            quickMovedSlot.onTake(player, rawStack);
        }

        return quickMovedStack;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!this.allowManage) {
            player.displayClientMessage(Component.translatable("message.trading_table.no_permission_manage"), true);
            return false;
        }

        return this.access.evaluate((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof TradingTableBlockEntity table)) {
                return false;
            }

            if (id == BUTTON_NAME_CLEAR) {
                this.pendingTableName.setLength(0);
                this.pendingNameHighByte = -1;
                return true;
            }

            if (id >= BUTTON_NAME_APPEND_HIGH_BASE && id <= BUTTON_NAME_APPEND_HIGH_MAX) {
                this.pendingNameHighByte = id - BUTTON_NAME_APPEND_HIGH_BASE;
                return true;
            }

            if (id >= BUTTON_NAME_APPEND_LOW_BASE && id <= BUTTON_NAME_APPEND_LOW_MAX) {
                if (this.pendingNameHighByte < 0) {
                    return false;
                }
                if (this.pendingTableName.length() < TradingTableBlockEntity.MAX_TABLE_NAME_LENGTH * 2) {
                    int lowByte = id - BUTTON_NAME_APPEND_LOW_BASE;
                    char decoded = (char) ((this.pendingNameHighByte << 8) | lowByte);
                    this.pendingTableName.append(decoded);
                }
                this.pendingNameHighByte = -1;
                return true;
            }

            this.ensureCacheInitialized(table);

            if (id == BUTTON_TOGGLE_ENABLED) {
                this.cachedEnabled = this.cachedEnabled == 1 ? 0 : 1;
                this.hasPendingManageChanges = true;
                return true;
            }

            if (id == BUTTON_SET_TYPE_SELL) {
                this.cachedType = 0;
                this.hasPendingManageChanges = true;
                return true;
            }

            if (id == BUTTON_SET_TYPE_BUY) {
                this.cachedType = 1;
                this.hasPendingManageChanges = true;
                return true;
            }

            if (id == BUTTON_MIN_PLUS || id == BUTTON_MIN_PLUS_8 || id == BUTTON_MIN_PLUS_32) {
                this.cachedMin = Math.max(1, this.cachedMin + getStep(id));
                this.hasPendingManageChanges = true;
                return true;
            }

            if (id == BUTTON_MIN_MINUS || id == BUTTON_MIN_MINUS_8 || id == BUTTON_MIN_MINUS_32) {
                this.cachedMin = Math.max(1, this.cachedMin - getStep(id));
                this.hasPendingManageChanges = true;
                return true;
            }

            if (id == BUTTON_PRICE_PLUS || id == BUTTON_PRICE_PLUS_8 || id == BUTTON_PRICE_PLUS_32) {
                this.cachedPrice = (int) Math.min(Integer.MAX_VALUE, (long) this.cachedPrice + getStep(id));
                this.hasPendingManageChanges = true;
                return true;
            }

            if (id == BUTTON_PRICE_MINUS || id == BUTTON_PRICE_MINUS_8 || id == BUTTON_PRICE_MINUS_32) {
                this.cachedPrice = Math.max(1, this.cachedPrice - getStep(id));
                this.hasPendingManageChanges = true;
                return true;
            }

            if (id == BUTTON_SAVE) {
                table.setTableName(TradingTableBlockEntity.sanitizeTableName(this.pendingTableName.toString()));
                table.setEnabled(this.cachedEnabled == 1);
                table.setBuyOrder(this.cachedType == 1);
                table.setMinTradeAmount(this.cachedMin);
                table.setUnitPrice(this.cachedPrice);
                this.hasPendingManageChanges = false;
                this.pendingTableName.setLength(0);
                this.pendingNameHighByte = -1;
                if (!table.canFinalizeInitialization()) {
                    player.displayClientMessage(Component.translatable("message.trading_table.invalid_trade_item"), true);
                    return false;
                }
                player.displayClientMessage(Component.translatable("message.trading_table.manage_saved"), true);
                return true;
            }

            if (id == BUTTON_CONFIRM_TRADE_ITEM) {
                ItemStack configured = this.inventory.getStackInSlot(0);
                if (configured.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.trading_table.invalid_trade_item"), true);
                    return false;
                }
                table.setTradeItem(configured.getItem());
                player.displayClientMessage(Component.translatable("message.trading_table.trade_item_saved"), true);
                return true;
            }

            if (id == BUTTON_EXTRACT || id == BUTTON_EXTRACT_STACK || id == BUTTON_EXTRACT_ALL) {
                return this.handleCashierExtract(player, table, id);
            }

            return false;
        }, false);
    }

    public ItemStackHandler getInventoryHandler() {
        return this.inventory;
    }

    public boolean isAllowManage() {
        return this.allowManage;
    }

    public int getMinTradeAmount() {
        return this.viewData.get(0);
    }

    public int getUnitPrice() {
        return this.viewData.get(1);
    }

    public boolean isBuyOrder() {
        return this.viewData.get(2) == 1;
    }

    public boolean isEnabled() {
        return this.viewData.get(3) == 1;
    }

    public double getCashierBalance() {
        int whole = this.viewData.get(4);
        int fractional = this.viewData.get(5);
        return whole + (fractional / 1000.0D);
    }

    public boolean hasUnsavedManageChanges() {
        return this.viewData.get(7) == 1;
    }

    public boolean isTradeItemSelectionDirty() {
        int savedTradeItemId = this.viewData.get(6);
        ItemStack configured = this.inventory.getStackInSlot(0);
        if (configured.isEmpty()) {
            return savedTradeItemId != -1;
        }
        return BuiltInRegistries.ITEM.getId(configured.getItem()) != savedTradeItemId;
    }

    private void refreshFromBlockEntity() {
        this.access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof TradingTableBlockEntity table) {
                if (!this.cacheInitialized || !this.hasPendingManageChanges) {
                    this.cachedMin = table.getMinTradeAmount();
                    this.cachedPrice = (int) Math.min(Integer.MAX_VALUE, table.getUnitPrice());
                    this.cachedType = table.isBuyOrder() ? 1 : 0;
                    this.cachedEnabled = table.isEnabled() ? 1 : 0;
                    this.cacheInitialized = true;
                }
                double balance = Math.max(0.0D, table.getCurrencyBalance());
                int whole = (int) Math.min(Integer.MAX_VALUE, Math.floor(balance));
                int fractional = (int) Math.min(999, Math.floor((balance - whole) * 1000.0D + 1.0E-6D));
                this.cachedCashierWhole = whole;
                this.cachedCashierFractional = fractional;
                this.cachedTradeItemId = table.getTradeItem() == null ? -1 : BuiltInRegistries.ITEM.getId(table.getTradeItem());
            }
        });
    }

    private void ensureCacheInitialized(TradingTableBlockEntity table) {
        if (this.cacheInitialized) {
            return;
        }
        this.cachedMin = table.getMinTradeAmount();
        this.cachedPrice = (int) Math.min(Integer.MAX_VALUE, table.getUnitPrice());
        this.cachedType = table.isBuyOrder() ? 1 : 0;
        this.cachedEnabled = table.isEnabled() ? 1 : 0;
        this.cacheInitialized = true;
    }

    private boolean handleCashierExtract(Player player, TradingTableBlockEntity table, int buttonId) {
        Item currencyItem = Config.getVanillaCurrencyItem();
        int maxStack = currencyItem.getDefaultMaxStackSize();
        long available = (long) Math.floor(Math.max(0.0D, table.getCurrencyBalance()));
        if (available <= 0L) {
            return false;
        }

        long desired;
        boolean dropOverflow;
        if (buttonId == BUTTON_EXTRACT_ALL) {
            desired = available;
            dropOverflow = true;
        } else if (buttonId == BUTTON_EXTRACT_STACK) {
            desired = Math.min(available, maxStack);
            dropOverflow = true;
        } else {
            int fit = countFitInInventory(player, currencyItem);
            if (fit <= 0) {
                return false;
            }
            desired = Math.min(available, fit);
            dropOverflow = false;
        }

        if (desired <= 0L || !table.tryWithdrawCurrency(desired)) {
            return false;
        }

        long overflow = giveCurrencyToPlayer(player, currencyItem, desired);
        if (overflow > 0L) {
            if (dropOverflow) {
                dropCurrencyNearPlayer(player, currencyItem, overflow);
            } else {
                table.depositCurrency(overflow);
            }
        }
        return true;
    }

    private static int countFitInInventory(Player player, Item item) {
        int fit = 0;
        int maxStack = item.getDefaultMaxStackSize();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                fit += maxStack;
            } else if (stack.is(item)) {
                fit += Math.max(0, Math.min(maxStack, stack.getMaxStackSize()) - stack.getCount());
            }
        }
        return fit;
    }

    private static long giveCurrencyToPlayer(Player player, Item item, long amount) {
        if (amount <= 0L) {
            return 0L;
        }

        int maxStack = item.getDefaultMaxStackSize();
        long remaining = amount;
        while (remaining > 0L) {
            int toGive = (int) Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, toGive);
            boolean fullyInserted = player.getInventory().add(stack);
            long insertedCount = toGive - stack.getCount();
            if (insertedCount <= 0L) {
                break;
            }
            remaining -= insertedCount;
            if (!fullyInserted) {
                break;
            }
        }
        return Math.max(0L, remaining);
    }

    private static void dropCurrencyNearPlayer(Player player, Item item, long amount) {
        int maxStack = item.getDefaultMaxStackSize();
        long remaining = amount;
        while (remaining > 0L) {
            int drop = (int) Math.min(remaining, maxStack);
            ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), new ItemStack(item, drop));
            entity.setPickUpDelay(0);
            player.level().addFreshEntity(entity);
            remaining -= drop;
        }
    }

    private static int getStep(int id) {
        if (id == BUTTON_MIN_PLUS_32 || id == BUTTON_PRICE_PLUS_32 || id == BUTTON_MIN_MINUS_32 || id == BUTTON_PRICE_MINUS_32) {
            return 32;
        }
        if (id == BUTTON_MIN_PLUS_8 || id == BUTTON_PRICE_PLUS_8 || id == BUTTON_MIN_MINUS_8 || id == BUTTON_PRICE_MINUS_8) {
            return 8;
        }
        return 1;
    }
}

