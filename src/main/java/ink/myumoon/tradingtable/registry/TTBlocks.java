package ink.myumoon.tradingtable.registry;

import ink.myumoon.tradingtable.HarvistasTradingTable;
import ink.myumoon.tradingtable.block.BlockTradingTable;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TTBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HarvistasTradingTable.MODID);

    public static final DeferredBlock<BlockTradingTable> TRADING_TABLE = BLOCKS.register(
            "trading_table", () -> new BlockTradingTable(BlockBehaviour.Properties.of()
                    .destroyTime(1.0f)
                    .explosionResistance(1200f)
                    .lightLevel(state -> state.getValue(BlockTradingTable.INITIALIZED) ? 7 : 0)
                    .sound(SoundType.COPPER)
            ));
}
