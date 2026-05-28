package ink.myumoon.tradingtable.block;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.HarvistasTradingTable;
import ink.myumoon.tradingtable.blockentity.OpenMenuMode;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import ink.myumoon.tradingtable.registries.TTBlocks;
import ink.myumoon.tradingtable.trade.ConversionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.NonNull;

@EventBusSubscriber(modid = HarvistasTradingTable.MODID)
public class BlockTradingTable extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty INITIALIZED = BooleanProperty.create("initialized");
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    public BlockTradingTable(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(INITIALIZED, Boolean.FALSE)
                .setValue(ENABLED, Boolean.FALSE));
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new TradingTableBlockEntity(pos, state);
    }

    @Override
    public @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult){
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer){
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TradingTableBlockEntity tradingTableBlockEntity) {
                if (!tradingTableBlockEntity.isInitialized()) {
                    tradingTableBlockEntity.setOwnerIfAbsent(player);
                    tradingTableBlockEntity.setOpenMenuMode(OpenMenuMode.INIT);
                    serverPlayer.openMenu(tradingTableBlockEntity);
                    return InteractionResult.SUCCESS;
                }

                boolean wantsManage = player.isShiftKeyDown() && tradingTableBlockEntity.canManage(player);


                if (!tradingTableBlockEntity.isEnabled() && !wantsManage) {
                    if (!level.isClientSide()) {
                        player.sendOverlayMessage(Component.translatable("message.trading_table.trade_disabled"));
                    }
                    return InteractionResult.SUCCESS;
                }

                if (wantsManage) {
                    tradingTableBlockEntity.setOpenMenuMode(OpenMenuMode.MANAGE);
                    serverPlayer.openMenu(tradingTableBlockEntity);
                } else {
                    tradingTableBlockEntity.setOpenMenuMode(OpenMenuMode.TRADE);
                    serverPlayer.openMenu(tradingTableBlockEntity);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INITIALIZED, ENABLED);
    }

    // 破坏方块验证
    @Override
    public boolean onDestroyedByPlayer(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull ItemStack toolStack, boolean willHarvest, @NonNull FluidState fluid) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TradingTableBlockEntity tradingTableBlockEntity
                && tradingTableBlockEntity.isInitialized()
                && !tradingTableBlockEntity.canManage(player)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.trading_table.no_permission_break"));
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
    }

    // 貌似 BlockBehaviour#onRemove 没有了，所以用了更加...神秘的方法。
    @SubscribeEvent
    public static void onBlockRemove(BreakBlockEvent event) {
        if(!event.getState().is(TTBlocks.TRADING_TABLE)){
            return;
        }

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TradingTableBlockEntity tradingTableBlockEntity) {
            for (int i = 0; i < tradingTableBlockEntity.getInventoryHandler().size(); i++) {
                ItemStack stack = tradingTableBlockEntity.getInventoryHandler().copyToList().get(i);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }

            long balance = (long) Math.floor(tradingTableBlockEntity.getCurrencyBalance());
            if (balance > 0L) {
                if (ConversionService.isEnabled()) {
                    for (ItemStack stack : ConversionService.convertBalanceToStacks(balance)) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                } else {
                    ItemStack template = new ItemStack(Config.getCurrencyItem());
                    int maxStackSize = template.getMaxStackSize();
                    while (balance > 0L) {
                        int drop = (int) Math.min(balance, maxStackSize);
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(template.getItem(), drop));
                        balance -= drop;
                    }
                }
            }
        }
    }
}
