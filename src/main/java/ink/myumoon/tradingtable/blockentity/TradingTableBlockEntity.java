package ink.myumoon.tradingtable.blockentity;

import ink.myumoon.tradingtable.HarvistasTradingTable;
import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.config.CurrencyBackend;
import ink.myumoon.tradingtable.block.BlockTradingTable;
import ink.myumoon.tradingtable.economy.NeoEssentialsEconomyService;
import ink.myumoon.tradingtable.menu.TradingTableInitMenu;
import ink.myumoon.tradingtable.menu.TradingTableTradeMenu;
import ink.myumoon.tradingtable.menu.TradingTableMenu;
import ink.myumoon.tradingtable.trade.ConversionService;
import ink.myumoon.tradingtable.registry.TTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.UUID;

public class TradingTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INVENTORY_SIZE = 27;
    public static final int MAX_TABLE_NAME_LENGTH = 48;

    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_IS_INITIALIZED = "IsInitialized";
    private static final String TAG_IS_ENABLED = "IsEnabled";
    private static final String TAG_TABLE_NAME = "TableName";
    private static final String TAG_TRADE_ITEM = "TradeItem";
    private static final String TAG_IS_BUY_ORDER = "IsBuyOrder";
    private static final String TAG_MIN_TRADE_AMOUNT = "MinTradeAmount";
    private static final String TAG_UNIT_PRICE = "UnitPrice";
    private static final String TAG_CURRENCY_BALANCE = "CurrencyBalance";
    private static final String TAG_CURRENCY_MIGRATED = "CurrencyMigrated";

    @Nullable
    private UUID owner;
    private boolean initialized;
    private boolean enabled;
    private String tableName = "";
    private Item tradeItem = null;
    private boolean buyOrder;
    private int minTradeAmount = 1;
    private long unitPrice = 1L;
    private double currencyBalance;
    private OpenMenuMode openMenuMode = OpenMenuMode.TRADE;
    private boolean pendingClientSync;
    private boolean syncTaskScheduled;
    private int syncBatchDepth;
    private boolean convertingCurrencyDeposit;
    private boolean currencyMigrated;

    private final IItemHandler backInputHandler = new InventoryAutomationView(true, false);
    private final IItemHandler downOutputHandler = new InventoryAutomationView(false, true);

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            convertCurrencyStacksToBalance();
            setChanged();
        }
    };

    public TradingTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(TTBlockEntities.TRADING_TABLE.get(), pos, blockState);
    }

    public ItemStackHandler getInventoryHandler() {
        return this.inventory;
    }

    @Nullable
    public UUID getOwner() {
        return this.owner;
    }

    public boolean setOwnerIfAbsent(Player player) {
        if (this.owner != null) {
            return false;
        }
        this.owner = player.getUUID();
        this.setChanged();
        return true;
    }

    public boolean isOwner(Player player) {
        return this.owner != null && this.owner.equals(player.getUUID());
    }

    public boolean isAdmin(Player player) {
        return player.hasPermissions(Config.getAdminPermissionLevel());
    }

    public boolean canManage(Player player) {
        return this.isOwner(player) || this.isAdmin(player);
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

    public double getCurrencyBalance() {
        if (Config.getCurrencyBackend() == CurrencyBackend.NEO_ESSENTIALS) {
            if (this.owner == null || this.level == null || this.level.isClientSide()) {
                return 0.0D;
            }
            return NeoEssentialsEconomyService.getBalance(this.owner);
        }
        return this.currencyBalance;
    }

    public boolean tryWithdrawCurrency(double amount) {
        if (amount <= 0.0D) {
            return false;
        }
        if (Config.getCurrencyBackend() == CurrencyBackend.NEO_ESSENTIALS) {
            if (this.owner == null) {
                return false;
            }
            double current = NeoEssentialsEconomyService.getBalance(this.owner);
            if (current + 1.0E-9D < amount) {
                return false;
            }
            boolean ok = NeoEssentialsEconomyService.subtractBalance(this.owner, amount);
            if (ok) {
                this.setChanged();
            }
            return ok;
        }
        if (this.currencyBalance + 1.0E-9D < amount) {
            return false;
        }
        this.currencyBalance = Math.max(0.0D, this.currencyBalance - amount);
        this.setChanged();
        return true;
    }

    public void depositCurrency(double amount) {
        if (amount <= 0.0D) {
            return;
        }
        if (Config.getCurrencyBackend() == CurrencyBackend.NEO_ESSENTIALS) {
            if (this.owner != null) {
                NeoEssentialsEconomyService.addBalance(this.owner, amount);
            }
            this.setChanged();
            return;
        }
        this.currencyBalance = Math.min(Double.MAX_VALUE, this.currencyBalance + amount);
        this.setChanged();
    }

    public void setOpenMenuMode(OpenMenuMode openMenuMode) {
        this.openMenuMode = openMenuMode;
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

    public int getTradeStockCount() {
        if (this.tradeItem == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < this.inventory.getSlots(); i++) {
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (stack.is(this.tradeItem)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    //需要修改，部分内容没有设置不让保存
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
            this.tableName = "";
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
            this.setOwnerIfAbsent(player);
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
        this.tryMigrateStoredCurrency();
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

    private void convertCurrencyStacksToBalance() {
        if (this.convertingCurrencyDeposit) {
            return;
        }
        if (Config.getCurrencyBackend() == CurrencyBackend.NEO_ESSENTIALS) {
            return;
        }
        if (ConversionService.isEnabled()) {
            long totalValue = 0L;
            this.convertingCurrencyDeposit = true;
            try {
                for (int i = 0; i < this.inventory.getSlots(); i++) {
                    ItemStack stack = this.inventory.getStackInSlot(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    long value = ConversionService.getValue(stack.getItem());
                    if (value <= 0L) {
                        continue;
                    }
                    totalValue += value * stack.getCount();
                    this.inventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            } finally {
                this.convertingCurrencyDeposit = false;
            }

            if (totalValue > 0L) {
                this.currencyBalance = Math.min(Double.MAX_VALUE, this.currencyBalance + totalValue);
            }
            return;
        }

        Item currencyItem = Config.getCurrencyItem();
        int totalCurrencyItems = 0;
        for (int i = 0; i < this.inventory.getSlots(); i++) {
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (stack.is(currencyItem)) {
                totalCurrencyItems += stack.getCount();
            }
        }
        if (totalCurrencyItems <= 0) {
            return;
        }

        this.convertingCurrencyDeposit = true;
        try {
            for (int i = 0; i < this.inventory.getSlots(); i++) {
                ItemStack stack = this.inventory.getStackInSlot(i);
                if (!stack.is(currencyItem)) {
                    continue;
                }
                this.inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        } finally {
            this.convertingCurrencyDeposit = false;
        }

        this.currencyBalance = Math.min(Double.MAX_VALUE, this.currencyBalance + totalCurrencyItems);
    }

    /**
     * 一次性迁移：将 NBT 中残留的 currencyBalance 转入 NeoEssentials 经济系统。
     * 仅在服务端、NeoEssentials 模式、有余额、未迁移、有 owner 时执行。
     */
    private void tryMigrateStoredCurrency() {
        if (this.currencyMigrated) {
            return;
        }
        if (Config.getCurrencyBackend() != CurrencyBackend.NEO_ESSENTIALS) {
            this.currencyMigrated = true;
            return;
        }
        if (this.currencyBalance <= 0.0D) {
            this.currencyMigrated = true;
            return;
        }
        if (this.owner == null) {
            return;
        }
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (!(this.level instanceof ServerLevel)) {
            return;
        }

        double toMigrate = this.currencyBalance;
        boolean ok = NeoEssentialsEconomyService.addBalance(this.owner, toMigrate);
        if (ok) {
            this.currencyBalance = 0.0D;
            this.currencyMigrated = true;
            this.setChanged();
            HarvistasTradingTable.LOGGER.info(
                    "Migrated {} stored currency to NeoEssentials for owner {} at {}",
                    toMigrate, this.owner, this.worldPosition);
        }
        // 失败则下次再试
    }

    public Direction getBackInputSide() {
        if (this.level == null) {
            return Direction.SOUTH;
        }
        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!state.hasProperty(BlockTradingTable.FACING)) {
            return Direction.SOUTH;
        }
        return state.getValue(BlockTradingTable.FACING).getOpposite();
    }

    @Nullable
    public IItemHandler getItemHandlerForSide(@Nullable Direction side) {
        if (side == null) {
            return this.inventory;
        }
        if (side == Direction.DOWN) {
            return this.downOutputHandler;
        }
        if (side == this.getBackInputSide()) {
            return this.backInputHandler;
        }
        return null;
    }

    private void syncStateToBlock() {
        if (this.level == null) {
            return;
        }
        BlockState current = this.level.getBlockState(this.worldPosition);
        if (!(current.getBlock() instanceof BlockTradingTable)) {
            return;
        }
        BlockState updated = current
                .setValue(BlockTradingTable.INITIALIZED, this.initialized)
                .setValue(BlockTradingTable.ENABLED, this.enabled);
        if (updated != current) {
            this.level.setBlock(this.worldPosition, updated, 3);
        }
    }

    private class InventoryAutomationView implements IItemHandler {
        private final boolean canInsert;
        private final boolean canExtract;

        private InventoryAutomationView(boolean canInsert, boolean canExtract) {
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }

        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!this.canInsert) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!this.canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return this.canInsert && inventory.isItemValid(slot, stack);
        }
    }

    @Override
    public Component getDisplayName() {
        Component baseName = this.tableName.isBlank()
                ? Component.translatable("block.trading_table.trading_table")
                : Component.literal(this.tableName);
        return switch (this.openMenuMode) {
            case INIT, TRADE, MANAGE -> baseName;
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        ContainerLevelAccess access = this.level == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(this.level, this.worldPosition);
        OpenMenuMode openingMode = this.openMenuMode;
        this.openMenuMode = OpenMenuMode.TRADE;

        return switch (openingMode) {
            case INIT -> new TradingTableInitMenu(containerId, playerInventory, this.inventory, access);
            case TRADE -> new TradingTableTradeMenu(containerId, playerInventory, this.inventory, access);
            case MANAGE -> new TradingTableMenu(containerId, playerInventory, this.inventory, access, this.canManage(player));
        };
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));

        if (tag.hasUUID(TAG_OWNER)) {
            this.owner = tag.getUUID(TAG_OWNER);
        } else {
            this.owner = null;
        }

        this.initialized = tag.getBoolean(TAG_IS_INITIALIZED);
        this.enabled = tag.getBoolean(TAG_IS_ENABLED);
        this.tableName = sanitizeTableName(tag.getString(TAG_TABLE_NAME));
        this.buyOrder = tag.getBoolean(TAG_IS_BUY_ORDER);
        this.minTradeAmount = Math.max(1, tag.getInt(TAG_MIN_TRADE_AMOUNT));
        this.unitPrice = Math.max(1L, tag.getLong(TAG_UNIT_PRICE));
        if (tag.contains(TAG_CURRENCY_BALANCE, Tag.TAG_DOUBLE)) {
            this.currencyBalance = Math.max(0.0D, tag.getDouble(TAG_CURRENCY_BALANCE));
        } else {
            this.currencyBalance = Math.max(0.0D, tag.getLong(TAG_CURRENCY_BALANCE));
        }
        this.currencyMigrated = tag.getBoolean(TAG_CURRENCY_MIGRATED);

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
        if (this.owner != null) {
            tag.putUUID(TAG_OWNER, this.owner);
        }
        tag.putBoolean(TAG_IS_INITIALIZED, this.initialized);
        tag.putBoolean(TAG_IS_ENABLED, this.enabled);
        tag.putString(TAG_TABLE_NAME, this.tableName);
        if (this.tradeItem != null) {
            tag.putString(TAG_TRADE_ITEM, BuiltInRegistries.ITEM.getKey(this.tradeItem).toString());
        }
        tag.putBoolean(TAG_IS_BUY_ORDER, this.buyOrder);
        tag.putInt(TAG_MIN_TRADE_AMOUNT, this.minTradeAmount);
        tag.putLong(TAG_UNIT_PRICE, this.unitPrice);
        tag.putDouble(TAG_CURRENCY_BALANCE, this.currencyBalance);
        tag.putBoolean(TAG_CURRENCY_MIGRATED, this.currencyMigrated);
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
