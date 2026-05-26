package ink.myumoon.tradingtable.registries;

import java.util.Set;

import ink.myumoon.tradingtable.HarvistasTradingTable;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import ink.myumoon.tradingtable.blockentity.SystemTradingTableBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TTBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HarvistasTradingTable.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TradingTableBlockEntity>> TRADING_TABLE =
            BLOCK_ENTITY_TYPES.register("trading_table", () -> new BlockEntityType<>(TradingTableBlockEntity::new, Set.of(TTBlocks.TRADING_TABLE.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SystemTradingTableBlockEntity>> SYSTEM_TRADING_TABLE =
            BLOCK_ENTITY_TYPES.register("system_trading_table", () -> new BlockEntityType<>(SystemTradingTableBlockEntity::new, Set.of(TTBlocks.SYSTEM_TRADING_TABLE.get())));
}
