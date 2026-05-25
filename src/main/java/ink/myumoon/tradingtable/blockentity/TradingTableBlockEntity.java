package ink.myumoon.tradingtable.blockentity;

import ink.myumoon.tradingtable.HarvistasTradingTable;
import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.block.BlockTradingTable;
import ink.myumoon.tradingtable.config.CurrencyBackend;
import ink.myumoon.tradingtable.economy.NeoEssentialsEconomyBackend;
import ink.myumoon.tradingtable.menu.TradingTableInitMenu;
import ink.myumoon.tradingtable.menu.TradingTableMenu;
import ink.myumoon.tradingtable.menu.TradingTableTradeMenu;
import ink.myumoon.tradingtable.registries.TTBlockEntities;
import ink.myumoon.tradingtable.registries.TTBlocks;
import ink.myumoon.tradingtable.trade.ConversionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

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
    private long lastConversionTick = Long.MIN_VALUE;
    private long convertedSlotsMask;

    private final ResourceHandler<ItemResource> backInputHandler = new InventoryAutomationView(true, false);
    private final ResourceHandler<ItemResource> downOutputHandler = new InventoryAutomationView(false, true);

    private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(INVENTORY_SIZE){
        @Override
        protected void onContentsChanged(int slot, @NonNull ItemStack previousContents){
            convertCurrencyStacksToBalance();
            setChanged();
        }
    };

    public TradingTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(TTBlockEntities.TRADING_TABLE.get(), pos, blockState);
    }

    public ItemStacksResourceHandler getInventoryHandler() {
        return this.inventory;
    }

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

    // 26.1.2: player.permissions() 返回 PermissionSet，不再返回 int
    public boolean isAdmin(Player player) {
        PermissionSet perms = player.permissions();
        if (perms instanceof LevelBasedPermissionSet lbs) {
            PermissionLevel required = PermissionLevel.byId(Config.getAdminPermissionLevel());
            return lbs.level().isEqualOrHigherThan(required);
        }
        return false;
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
            return NeoEssentialsEconomyBackend.getBalance(this.owner);
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
            double current = NeoEssentialsEconomyBackend.getBalance(this.owner);
            if (current + 1.0E-9D < amount) {
                return false;
            }
            boolean ok = NeoEssentialsEconomyBackend.subtractBalance(this.owner, amount);
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
                NeoEssentialsEconomyBackend.addBalance(this.owner, amount);
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
        for (int i = 0; i < this.inventory.size(); i++) {
            ItemStack stack = this.inventory.copyToList().get(i);
            if (stack.is(this.tradeItem)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public boolean initializeDefaultsFrom(Player player) {
        ItemStack configured = this.inventory.copyToList().getFirst();
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
            this.tableName = Component.translatable(TTBlocks.TRADING_TABLE.get().getDescriptionId()).getString();
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

        // 新 tick 时重置槽位追踪
        long currentTick = this.level != null ? this.level.getGameTime() : Long.MIN_VALUE;
        if (currentTick != this.lastConversionTick) {
            this.lastConversionTick = currentTick;
            this.convertedSlotsMask = 0L;
        }

        if (ConversionService.isEnabled()) {
            int size = this.inventory.size();
            // Phase 1：只统计尚未转换的槽位
            long totalValue = 0L;
            for (int i = 0; i < size; i++) {
                if ((this.convertedSlotsMask & (1L << i)) != 0) {
                    continue;
                }
                ItemStack stack = this.inventory.copyToList().get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                long value = ConversionService.getValue(stack.getItem());
                if (value <= 0L) {
                    continue;
                }
                totalValue += value * stack.getCount();
            }

            this.convertingCurrencyDeposit = true;
            try {
                // Phase 2：清除所有货币物品（包括已被重新插入的）
                for (int i = 0; i < size; i++) {
                    ItemStack stack = this.inventory.copyToList().get(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    if (ConversionService.getValue(stack.getItem()) <= 0L) {
                        continue;
                    }
                    this.inventory.set(i, ItemResource.EMPTY, 0);
                    this.convertedSlotsMask |= (1L << i);
                }
                if (totalValue > 0L) {
                    this.currencyBalance = Math.min(Double.MAX_VALUE, this.currencyBalance + totalValue);
                }
            } finally {
                this.convertingCurrencyDeposit = false;
            }
            return;
        }

        Item currencyItem = Config.getCurrencyItem();
        int size = this.inventory.size();

        // Phase 1：只统计尚未转换的槽位
        int totalCurrencyItems = 0;
        for (int i = 0; i < size; i++) {
            if ((this.convertedSlotsMask & (1L << i)) != 0) {
                continue;
            }
            ItemStack stack = this.inventory.copyToList().get(i);
            if (stack.is(currencyItem)) {
                totalCurrencyItems += stack.getCount();
            }
        }

        this.convertingCurrencyDeposit = true;
        try {
            // Phase 2：清除所有货币物品（包括已被重新插入的）
            for (int i = 0; i < size; i++) {
                ItemStack stack = this.inventory.copyToList().get(i);
                if (!stack.is(currencyItem)) {
                    continue;
                }
                this.inventory.set(i, ItemResource.EMPTY, 0);
                this.convertedSlotsMask |= (1L << i);
            }
            if (totalCurrencyItems > 0) {
                this.currencyBalance = Math.min(Double.MAX_VALUE, this.currencyBalance + totalCurrencyItems);
            }
        } finally {
            this.convertingCurrencyDeposit = false;
        }
    }

    // 迁移，其实还没有实现
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
        boolean ok = NeoEssentialsEconomyBackend.addBalance(this.owner, toMigrate);
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
    public ResourceHandler<ItemResource> getItemHandlerForSide(@Nullable Direction side) {
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

    /**
     * 自动化视图：根据 canInsert/canExtract 限制对 inventory 的访问。
     * 26.1.2: 纯 ResourceHandler<ItemResource> API，不再混用 IItemHandler。
     */
    private class InventoryAutomationView implements ResourceHandler<ItemResource> {
        private final boolean canInsert;
        private final boolean canExtract;

        private InventoryAutomationView(boolean canInsert, boolean canExtract) {
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }

        @Override
        public int size() {
            return inventory.size();
        }

        @Override
        public ItemResource getResource(int index) {
            return inventory.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return inventory.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return inventory.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return inventory.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (!this.canInsert) {
                return 0;
            }
            return inventory.insert(index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (!this.canExtract) {
                return 0;
            }
            return inventory.extract(index, resource, amount, transaction);
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

    // saveAdditional/loadAdditional 改用 ValueOutput/ValueInput
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        // UUID 存储为字符串
        String ownerStr = input.getStringOr(TAG_OWNER, "");
        if (!ownerStr.isEmpty()) {
            try {
                this.owner = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException e) {
                this.owner = null;
            }
        } else {
            this.owner = null;
        }

        this.initialized = input.getBooleanOr(TAG_IS_INITIALIZED, false);
        this.enabled = input.getBooleanOr(TAG_IS_ENABLED, false);
        this.tableName = sanitizeTableName(input.getStringOr(TAG_TABLE_NAME, ""));
        this.buyOrder = input.getBooleanOr(TAG_IS_BUY_ORDER, false);
        this.minTradeAmount = Math.max(1, input.getIntOr(TAG_MIN_TRADE_AMOUNT, 1));
        this.unitPrice = Math.max(1L, input.getLongOr(TAG_UNIT_PRICE, 1L));
        this.currencyBalance = Math.max(0.0D, input.getDoubleOr(TAG_CURRENCY_BALANCE, 0.0D));
        this.currencyMigrated = input.getBooleanOr(TAG_CURRENCY_MIGRATED, false);

        String tradeItemStr = input.getStringOr(TAG_TRADE_ITEM, "");
        Identifier tradeItemId = Identifier.tryParse(tradeItemStr);
        this.tradeItem = tradeItemId != null
                ? BuiltInRegistries.ITEM.get(tradeItemId).map(Holder.Reference::value).orElse(null)
                : null;

        // ValueInputExtension.readChild 直接处理 ValueIOSerializable
        input.readChild(TAG_INVENTORY, this.inventory);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        if (this.owner != null) {
            output.putString(TAG_OWNER, this.owner.toString());
        }
        output.putBoolean(TAG_IS_INITIALIZED, this.initialized);
        output.putBoolean(TAG_IS_ENABLED, this.enabled);
        output.putString(TAG_TABLE_NAME, this.tableName);
        if (this.tradeItem != null) {
            output.putString(TAG_TRADE_ITEM, BuiltInRegistries.ITEM.getKey(this.tradeItem).toString());
        }
        output.putBoolean(TAG_IS_BUY_ORDER, this.buyOrder);
        output.putInt(TAG_MIN_TRADE_AMOUNT, this.minTradeAmount);
        output.putLong(TAG_UNIT_PRICE, this.unitPrice);
        output.putDouble(TAG_CURRENCY_BALANCE, this.currencyBalance);
        output.putBoolean(TAG_CURRENCY_MIGRATED, this.currencyMigrated);

        // ValueOutputExtension.putChild 直接处理 ValueIOSerializable
        output.putChild(TAG_INVENTORY, this.inventory);
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
