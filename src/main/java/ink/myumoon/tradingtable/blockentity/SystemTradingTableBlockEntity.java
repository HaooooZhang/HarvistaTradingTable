package ink.myumoon.tradingtable.blockentity;

import ink.myumoon.tradingtable.block.BlockSystemTradingTable;
import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.menu.SystemTradingTableInitMenu;
import ink.myumoon.tradingtable.menu.SystemTradingTableTradeMenu;
import ink.myumoon.tradingtable.menu.SystemTradingTableMenu;
import ink.myumoon.tradingtable.registry.TTBlockEntities;
import ink.myumoon.tradingtable.registry.TTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class SystemTradingTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INVENTORY_SIZE = 1;
    public static final int MAX_TABLE_NAME_LENGTH = 48;

    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_IS_INITIALIZED = "IsInitialized";
    private static final String TAG_IS_ENABLED = "IsEnabled";
    private static final String TAG_TABLE_NAME = "TableName";
    private static final String TAG_TRADE_ITEM = "TradeItem";
    private static final String TAG_IS_BUY_ORDER = "IsBuyOrder";
    private static final String TAG_MIN_TRADE_AMOUNT = "MinTradeAmount";
    private static final String TAG_UNIT_PRICE = "UnitPrice";

    private boolean initialized;
    private boolean enabled;
    private String tableName = "";
    private Item tradeItem = null;
    private boolean buyOrder;
    private int minTradeAmount = 1;
    private long unitPrice = 1L;
    private OpenMenuMode openMenuMode = OpenMenuMode.TRADE;
    private boolean pendingClientSync;
    private boolean syncTaskScheduled;
    private int syncBatchDepth;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public SystemTradingTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(TTBlockEntities.SYSTEM_TRADING_TABLE.get(), pos, blockState);
    }

    public ItemStackHandler getInventoryHandler() {
        return this.inventory;
    }

    public boolean isAdmin(Player player) {
        return player.hasPermissions(Config.getAdminPermissionLevel());
    }

    public boolean canManage(Player player) {
        return this.isAdmin(player);
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
        this.syncStateToBlock();
        this.setChanged();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.syncStateToBlock();
        this.setChanged();
    }

    public boolean isBuyOrder() {
        return this.buyOrder;
    }

    public void setBuyOrder(boolean buyOrder) {
        this.buyOrder = buyOrder;
        this.setChanged();
    }

    public int getMinTradeAmount() {
        return this.minTradeAmount;
    }

    public void setMinTradeAmount(int minTradeAmount) {
        this.minTradeAmount = Math.max(1, minTradeAmount);
        this.setChanged();
    }

    public long getUnitPrice() {
        return this.unitPrice;
    }

    public void setUnitPrice(long unitPrice) {
        this.unitPrice = Math.max(1L, unitPrice);
        this.setChanged();
    }

    @Nullable
    public Item getTradeItem() {
        return this.tradeItem;
    }

    public void setTradeItem(@Nullable Item tradeItem) {
        this.tradeItem = tradeItem;
        this.setChanged();
    }

    public String getTableName() {
        return this.tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = sanitizeTableName(tableName);
        this.setChanged();
    }

    public static String sanitizeTableName(@Nullable String tableName) {
        if (tableName == null) {
            return "";
        }
        String sanitized = tableName.strip();
        if (sanitized.length() > MAX_TABLE_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_TABLE_NAME_LENGTH);
        }
        return sanitized;
    }

    public void setOpenMenuMode(OpenMenuMode openMenuMode) {
        this.openMenuMode = openMenuMode;
    }

    public boolean initializeDefaultsFrom(Player player) {
        ItemStack configured = this.inventory.getStackInSlot(0);
        if (!configured.isEmpty()) {
            this.tradeItem = configured.getItem();
        } else if (this.tradeItem == null && !player.getMainHandItem().isEmpty()) {
            this.tradeItem = player.getMainHandItem().getItem();
        }
        if (this.minTradeAmount <= 0) {
            this.minTradeAmount = 1;
        }
        if (this.unitPrice <= 0) {
            this.unitPrice = 1L;
        }
        if (this.tableName.isBlank()) {
            this.tableName = Component.translatable(TTBlocks.SYSTEM_TRADING_TABLE.get().getDescriptionId()).getString();
        }
        this.setChanged();
        return true;
    }

    public boolean canFinalizeInitialization() {
        return this.canFinalizeInitialization(null);
    }

    public boolean canFinalizeInitialization(@Nullable Player player) {
        ItemStack configured = this.inventory.getStackInSlot(0);
        if (!configured.isEmpty()) {
            this.tradeItem = configured.getItem();
        } else if (this.tradeItem == null && player != null && !player.getMainHandItem().isEmpty()) {
            this.tradeItem = player.getMainHandItem().getItem();
        }
        return this.tradeItem != null && this.minTradeAmount > 0 && this.unitPrice > 0;
    }

    public boolean finalizeInitialization(Player player) {
        if (this.initialized) {
            return false;
        }
        this.beginSyncBatch();
        try {
            this.initializeDefaultsFrom(player);
            if (!this.canFinalizeInitialization(player)) {
                return false;
            }
            this.initialized = true;
            this.enabled = true;
            this.syncStateToBlock();
            this.setChanged();
            return true;
        } finally {
            this.endSyncBatch();
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        this.queueClientSync();
    }

    private void beginSyncBatch() {
        this.syncBatchDepth++;
    }

    private void endSyncBatch() {
        if (this.syncBatchDepth <= 0) {
            return;
        }
        this.syncBatchDepth--;
        if (this.syncBatchDepth == 0 && this.pendingClientSync) {
            this.scheduleSyncTask();
        }
    }

    private void queueClientSync() {
        if (!(this.level instanceof ServerLevel)) {
            return;
        }
        this.pendingClientSync = true;
        if (this.syncBatchDepth > 0) {
            return;
        }
        this.scheduleSyncTask();
    }

    private void scheduleSyncTask() {
        if (this.syncTaskScheduled || !(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        this.syncTaskScheduled = true;
        serverLevel.getServer().execute(this::flushClientSync);
    }

    private void flushClientSync() {
        this.syncTaskScheduled = false;
        if (!(this.level instanceof ServerLevel) || this.isRemoved() || !this.pendingClientSync) {
            return;
        }
        this.pendingClientSync = false;
        BlockState state = this.getBlockState();
        this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
    }

    private void syncStateToBlock() {
        if (this.level == null) {
            return;
        }
        BlockState current = this.level.getBlockState(this.worldPosition);
        if (!(current.getBlock() instanceof BlockSystemTradingTable)) {
            return;
        }
        BlockState updated = current
                .setValue(BlockSystemTradingTable.INITIALIZED, this.initialized)
                .setValue(BlockSystemTradingTable.ENABLED, this.enabled);
        if (updated != current) {
            this.level.setBlock(this.worldPosition, updated, 3);
        }
    }

    @Override
    public Component getDisplayName() {
        return this.tableName.isBlank()
                ? Component.translatable("block.trading_table.system_trading_table")
                : Component.literal(this.tableName);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        ContainerLevelAccess access = this.level == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(this.level, this.worldPosition);
        OpenMenuMode openingMode = this.openMenuMode;
        this.openMenuMode = OpenMenuMode.TRADE;

        return switch (openingMode) {
            case INIT -> new SystemTradingTableInitMenu(containerId, playerInventory, this.inventory, access);
            case TRADE -> new SystemTradingTableTradeMenu(containerId, playerInventory, this.inventory, access);
            case MANAGE -> new SystemTradingTableMenu(containerId, playerInventory, this.inventory, access, this.canManage(player));
        };
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));

        this.initialized = tag.getBoolean(TAG_IS_INITIALIZED);
        this.enabled = tag.getBoolean(TAG_IS_ENABLED);
        this.tableName = sanitizeTableName(tag.getString(TAG_TABLE_NAME));
        this.buyOrder = tag.getBoolean(TAG_IS_BUY_ORDER);
        this.minTradeAmount = Math.max(1, tag.getInt(TAG_MIN_TRADE_AMOUNT));
        this.unitPrice = Math.max(1L, tag.getLong(TAG_UNIT_PRICE));

        ResourceLocation tradeItemId = ResourceLocation.tryParse(tag.getString(TAG_TRADE_ITEM));
        if (tradeItemId != null && BuiltInRegistries.ITEM.containsKey(tradeItemId)) {
            this.tradeItem = BuiltInRegistries.ITEM.get(tradeItemId);
        } else {
            this.tradeItem = null;
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, this.inventory.serializeNBT(registries));
        tag.putBoolean(TAG_IS_INITIALIZED, this.initialized);
        tag.putBoolean(TAG_IS_ENABLED, this.enabled);
        tag.putString(TAG_TABLE_NAME, this.tableName);
        if (this.tradeItem != null) {
            tag.putString(TAG_TRADE_ITEM, BuiltInRegistries.ITEM.getKey(this.tradeItem).toString());
        }
        tag.putBoolean(TAG_IS_BUY_ORDER, this.buyOrder);
        tag.putInt(TAG_MIN_TRADE_AMOUNT, this.minTradeAmount);
        tag.putLong(TAG_UNIT_PRICE, this.unitPrice);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        this.loadAdditional(tag, registries);
    }
}
