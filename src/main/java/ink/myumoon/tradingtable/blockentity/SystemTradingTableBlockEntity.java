package ink.myumoon.tradingtable.blockentity;

import ink.myumoon.tradingtable.block.BlockSystemTradingTable;
import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.menu.SystemTradingTableInitMenu;
import ink.myumoon.tradingtable.menu.SystemTradingTableTradeMenu;
import ink.myumoon.tradingtable.menu.SystemTradingTableMenu;
import ink.myumoon.tradingtable.registries.TTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;

public class SystemTradingTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INVENTORY_SIZE = 1;
    public static final int MAX_TABLE_NAME_LENGTH = 48;

    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_IS_INITIALIZED = "IsInitialized";
    private static final String TAG_IS_ENABLED = "IsEnabled";
    private static final String TAG_TABLE_NAME = "TableName";
    private static final String TAG_TRADE_ITEM = "TradeItem";
    private static final String TAG_TRADE_ITEM_STACK = "TradeItemStack";
    private static final String TAG_IS_BUY_ORDER = "IsBuyOrder";
    private static final String TAG_MIN_TRADE_AMOUNT = "MinTradeAmount";
    private static final String TAG_UNIT_PRICE = "UnitPrice";

    private boolean initialized;
    private boolean enabled;
    private String tableName = "";
    private ItemStack tradeItemStack = ItemStack.EMPTY;
    private boolean buyOrder;
    private int minTradeAmount = 1;
    private long unitPrice = 1L;
    private OpenMenuMode openMenuMode = OpenMenuMode.TRADE;
    private boolean pendingClientSync;
    private boolean syncTaskScheduled;
    private int syncBatchDepth;

    private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot, @NonNull ItemStack previousContents) {
            setChanged();
        }
    };

    public SystemTradingTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(TTBlockEntities.SYSTEM_TRADING_TABLE.get(), pos, blockState);
    }

    public ItemStacksResourceHandler getInventoryHandler() {
        return this.inventory;
    }

    public boolean isAdmin(Player player) {
        PermissionSet perms = player.permissions();
        if (perms instanceof LevelBasedPermissionSet lbs) {
            PermissionLevel required = PermissionLevel.byId(Config.getAdminPermissionLevel());
            return lbs.level().isEqualOrHigherThan(required);
        }
        return false;
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

    public ItemStack getTradeItemStack() {
        return this.tradeItemStack;
    }

    public void setTradeItemStack(@Nullable ItemStack tradeItemStack) {
        this.tradeItemStack = (tradeItemStack == null || tradeItemStack.isEmpty())
                ? ItemStack.EMPTY
                : tradeItemStack.copyWithCount(1);
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
        ItemStack configured = this.inventory.copyToList().getFirst();
        if (!configured.isEmpty()) {
            this.tradeItemStack = configured.copyWithCount(1);
        } else if (this.tradeItemStack.isEmpty() && !player.getMainHandItem().isEmpty()) {
            this.tradeItemStack = player.getMainHandItem().copyWithCount(1);
        }
        if (this.minTradeAmount <= 0) {
            this.minTradeAmount = 1;
        }
        if (this.unitPrice <= 0) {
            this.unitPrice = 1L;
        }
        this.setChanged();
        return true;
    }

    public boolean canFinalizeInitialization() {
        return this.canFinalizeInitialization(null);
    }

    public boolean canFinalizeInitialization(@Nullable Player player) {
        ItemStack configured = this.inventory.copyToList().getFirst();
        if (!configured.isEmpty()) {
            this.tradeItemStack = configured.copyWithCount(1);
        } else if (this.tradeItemStack.isEmpty() && player != null && !player.getMainHandItem().isEmpty()) {
            this.tradeItemStack = player.getMainHandItem().copyWithCount(1);
        }
        return !this.tradeItemStack.isEmpty() && this.minTradeAmount > 0 && this.unitPrice > 0;
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
            case TRADE -> new SystemTradingTableTradeMenu(containerId, playerInventory, this.inventory, access, this::getTradeItemStack);
            case MANAGE -> new SystemTradingTableMenu(containerId, playerInventory, this.inventory, access, this.canManage(player), this::getTradeItemStack);
        };
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        this.initialized = input.getBooleanOr(TAG_IS_INITIALIZED, false);
        this.enabled = input.getBooleanOr(TAG_IS_ENABLED,false);
        this.tableName = sanitizeTableName(input.getStringOr(TAG_TABLE_NAME,""));
        this.buyOrder = input.getBooleanOr(TAG_IS_BUY_ORDER,false);
        this.minTradeAmount = input.getIntOr(TAG_MIN_TRADE_AMOUNT,1);
        this.unitPrice = input.getLongOr(TAG_UNIT_PRICE, 1L);

        this.tradeItemStack = readTradeItemStack(input);

        input.readChild(TAG_INVENTORY, this.inventory);
    }

    @Override
    public void saveAdditional(ValueOutput  output) {
        super.saveAdditional(output);

        output.putBoolean(TAG_IS_INITIALIZED, this.initialized);
        output.putBoolean(TAG_IS_ENABLED, this.enabled);
        output.putString(TAG_TABLE_NAME, this.tableName);
        if (!this.tradeItemStack.isEmpty()) {
            output.store(TAG_TRADE_ITEM_STACK, ItemStack.CODEC, this.tradeItemStack.copyWithCount(1));
        }
        output.putBoolean(TAG_IS_BUY_ORDER, this.buyOrder);
        output.putInt(TAG_MIN_TRADE_AMOUNT, this.minTradeAmount);
        output.putLong(TAG_UNIT_PRICE, this.unitPrice);

        output.putChild(TAG_INVENTORY, this.inventory);
    }

    // 优先读取完整物品堆栈；旧存档仅存有物品注册名，回退构造默认组件堆栈
    private static ItemStack readTradeItemStack(ValueInput input) {
        ItemStack stored = input.read(TAG_TRADE_ITEM_STACK, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        if (!stored.isEmpty()) {
            return stored.copyWithCount(1);
        }
        Identifier legacyId = Identifier.tryParse(input.getStringOr(TAG_TRADE_ITEM, ""));
        if (legacyId == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.get(legacyId)
                .map(holder -> new ItemStack(holder.value()))
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
