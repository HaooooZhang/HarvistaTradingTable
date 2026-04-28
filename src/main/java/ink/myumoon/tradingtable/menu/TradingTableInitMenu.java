package ink.myumoon.tradingtable.menu;

import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import ink.myumoon.tradingtable.registry.TTBlocks;
import ink.myumoon.tradingtable.registry.TTMenuTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class TradingTableInitMenu extends AbstractContainerMenu {
    public static final int BUTTON_INITIALIZE = 0;
    public static final int BUTTON_SET_HELD_TRADE_ITEM = 1;
    public static final int BUTTON_TOGGLE_TYPE = 2;
    public static final int BUTTON_MIN_PLUS = 3;
    public static final int BUTTON_MIN_MINUS = 4;
    public static final int BUTTON_PRICE_PLUS = 5;
    public static final int BUTTON_PRICE_MINUS = 6;
    public static final int BUTTON_MIN_PLUS_8 = 7;
    public static final int BUTTON_MIN_PLUS_32 = 8;
    public static final int BUTTON_PRICE_PLUS_8 = 9;
    public static final int BUTTON_PRICE_PLUS_32 = 10;
    public static final int BUTTON_MIN_MINUS_8 = 11;
    public static final int BUTTON_MIN_MINUS_32 = 12;
    public static final int BUTTON_PRICE_MINUS_8 = 13;
    public static final int BUTTON_PRICE_MINUS_32 = 14;
    public static final int BUTTON_NAME_CLEAR = 2000;
    public static final int BUTTON_NAME_APPEND_HIGH_BASE = 3000;
    public static final int BUTTON_NAME_APPEND_HIGH_MAX = BUTTON_NAME_APPEND_HIGH_BASE + 255;
    public static final int BUTTON_NAME_APPEND_LOW_BASE = 4000;
    public static final int BUTTON_NAME_APPEND_LOW_MAX = BUTTON_NAME_APPEND_LOW_BASE + 255;

    private final ContainerLevelAccess access;
    private final ItemStackHandler inventory;
    private int cachedMin = 1;
    private int cachedPrice = 1;
    private int cachedType = 0;
    private int cachedInitialized = 0;
    private String cachedTableName = "";
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
                case 3 -> cachedInitialized;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> cachedMin = value;
                case 1 -> cachedPrice = value;
                case 2 -> cachedType = value;
                case 3 -> cachedInitialized = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public TradingTableInitMenu(int containerId, @NotNull Inventory playerInventory) {
        this(containerId, playerInventory, new ItemStackHandler(TradingTableMenu.INVENTORY_SLOTS), ContainerLevelAccess.NULL);
    }

    public TradingTableInitMenu(int containerId, @NotNull Inventory playerInventory, @NotNull ItemStackHandler inventory, @NotNull ContainerLevelAccess access) {
        super(TTMenuTypes.TRADING_TABLE_INIT.get(), containerId);
        this.access = access;
        this.inventory = inventory;
        this.addDataSlots(this.viewData);

        // Trade item config slot (mapped to block inventory slot 0).
        this.addSlot(new SlotItemHandler(inventory, 0, 16, 59));

        // Player inventory + hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;
                this.addSlot(new Slot(playerInventory, index, 8 + col * 18, 108 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 166));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return AbstractContainerMenu.stillValid(this.access, player, TTBlocks.TRADING_TABLE.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int quickMovedSlotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
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

            if (id == BUTTON_INITIALIZE) {
                if (table.isInitialized()) {
                    player.displayClientMessage(Component.translatable("message.trading_table.init_already_done"), true);
                    return false;
                }
                table.setTableName(TradingTableBlockEntity.sanitizeTableName(this.pendingTableName.toString()));
                if (!table.canFinalizeInitialization(player)) {
                    return false;
                }
                if (table.finalizeInitialization(player)) {
                    this.pendingTableName.setLength(0);
                    this.pendingNameHighByte = -1;
                    player.displayClientMessage(Component.translatable("message.trading_table.init_success"), true);
                    return true;
                }
                player.displayClientMessage(Component.translatable("message.trading_table.invalid_trade_item"), true);
                return false;
            }

            if (table.isInitialized()) {
                player.displayClientMessage(Component.translatable("message.trading_table.init_locked"), true);
                return false;
            }


            if (id == BUTTON_TOGGLE_TYPE) {
                table.setBuyOrder(!table.isBuyOrder());
                player.displayClientMessage(Component.translatable("message.trading_table.toggled_trade_type"), true);
                return true;
            }

            if (id == BUTTON_MIN_PLUS || id == BUTTON_MIN_PLUS_8 || id == BUTTON_MIN_PLUS_32) {
                table.setMinTradeAmount(table.getMinTradeAmount() + getStep(id));
                player.displayClientMessage(Component.translatable("message.trading_table.updated_min_trade"), true);
                return true;
            }

            if (id == BUTTON_MIN_MINUS || id == BUTTON_MIN_MINUS_8 || id == BUTTON_MIN_MINUS_32) {
                table.setMinTradeAmount(Math.max(1, table.getMinTradeAmount() - getStep(id)));
                player.displayClientMessage(Component.translatable("message.trading_table.updated_min_trade"), true);
                return true;
            }

            if (id == BUTTON_PRICE_PLUS || id == BUTTON_PRICE_PLUS_8 || id == BUTTON_PRICE_PLUS_32) {
                table.setUnitPrice(table.getUnitPrice() + (long) getStep(id));
                player.displayClientMessage(Component.translatable("message.trading_table.updated_price"), true);
                return true;
            }

            if (id == BUTTON_PRICE_MINUS || id == BUTTON_PRICE_MINUS_8 || id == BUTTON_PRICE_MINUS_32) {
                table.setUnitPrice(Math.max(1L, table.getUnitPrice() - (long) getStep(id)));
                player.displayClientMessage(Component.translatable("message.trading_table.updated_price"), true);
                return true;
            }

            return false;
        }, false);
    }

    public ItemStack getConfiguredTradeItem() {
        return this.inventory.getStackInSlot(0);
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

    public boolean isInitialized() {
        return this.viewData.get(3) == 1;
    }

    public String getTableName() {
        this.refreshFromBlockEntity();
        return this.cachedTableName;
    }

    private void refreshFromBlockEntity() {
        this.access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof TradingTableBlockEntity table) {
                this.cachedMin = table.getMinTradeAmount();
                this.cachedPrice = (int) Math.min(Integer.MAX_VALUE, table.getUnitPrice());
                this.cachedType = table.isBuyOrder() ? 1 : 0;
                this.cachedInitialized = table.isInitialized() ? 1 : 0;
                this.cachedTableName = table.getTableName();
            }
        });
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







