package ink.myumoon.tradingtable.block;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.blockentity.OpenMenuMode;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

public class BlockTradingTable extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty INITIALIZED = BooleanProperty.create("initialized");
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    // todo 检查一下怎么写方块方向
    public BlockTradingTable(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(INITIALIZED, Boolean.FALSE)
                .setValue(ENABLED, Boolean.FALSE));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TradingTableBlockEntity(pos, state);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TradingTableBlockEntity tradingTableBlockEntity) {
                if (!tradingTableBlockEntity.isInitialized()) {
                    tradingTableBlockEntity.setOwnerIfAbsent(player);
                    tradingTableBlockEntity.setOpenMenuMode(OpenMenuMode.INIT);
                    serverPlayer.openMenu(tradingTableBlockEntity);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }

                boolean wantsManage = player.isShiftKeyDown() && tradingTableBlockEntity.canManage(player);

                // Disabled tables block trade UI, but managers can still open the manage UI.
                if (!tradingTableBlockEntity.isEnabled() && !wantsManage) {
                    if (!level.isClientSide) {
                        player.displayClientMessage(Component.translatable("message.trading_table.trade_disabled"), true);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
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
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INITIALIZED, ENABLED);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TradingTableBlockEntity tradingTableBlockEntity
                && tradingTableBlockEntity.isInitialized()
                && !tradingTableBlockEntity.canManage(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.trading_table.no_permission_break"), true);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TradingTableBlockEntity tradingTableBlockEntity) {
                for (int i = 0; i < tradingTableBlockEntity.getInventoryHandler().getSlots(); i++) {
                    ItemStack stack = tradingTableBlockEntity.getInventoryHandler().getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }

                long balance = (long) Math.floor(tradingTableBlockEntity.getCurrencyBalance());
                if (balance > 0L) {
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
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
