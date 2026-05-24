package ink.myumoon.tradingtable.registries;

import ink.myumoon.tradingtable.HarvistasTradingTable;
import ink.myumoon.tradingtable.block.BlockTradingTable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TTBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HarvistasTradingTable.MODID);

    public static final DeferredBlock<BlockTradingTable> TRADING_TABLE = BLOCKS.register(
            "trading_table", () -> new BlockTradingTable(BlockBehaviour.Properties
                    .ofFullCopy(Blocks.COPPER_BLOCK)
                    .strength(2F)
                    .explosionResistance(1200f)
                    .lightLevel(state -> state.getValue(BlockTradingTable.INITIALIZED) ? 7 : 0)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops()
            ));

//    public static final DeferredBlock<BlockSystemTradingTable> SYSTEM_TRADING_TABLE = BLOCKS.register(
//        "system_trading_table",() -> new BlockSystemTradingTable(BlockBehaviour.Properties
//                .ofFullCopy(Blocks.COPPER_BLOCK)
//                .strength(2F)
//                .explosionResistance(1200f)
//                .lightLevel(state -> state.getValue(BlockSystemTradingTable.INITIALIZED) ? 7 : 0)
//                .sound(SoundType.COPPER)
//                .requiresCorrectToolForDrops()
//        ));
}
