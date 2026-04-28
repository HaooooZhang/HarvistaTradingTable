package ink.myumoon.tradingtable.registry;

import ink.myumoon.tradingtable.HarvistasTradingTable;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TTBlockEntities {
    private TTBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HarvistasTradingTable.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TradingTableBlockEntity>> TRADING_TABLE =
            BLOCK_ENTITY_TYPES.register("trading_table", () -> BlockEntityType.Builder
                    .of(TradingTableBlockEntity::new, TTBlocks.TRADING_TABLE.get())
                    .build(null));
}

