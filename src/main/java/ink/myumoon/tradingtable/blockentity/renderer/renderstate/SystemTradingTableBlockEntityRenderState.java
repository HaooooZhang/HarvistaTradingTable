package ink.myumoon.tradingtable.blockentity.renderer.renderstate;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class SystemTradingTableBlockEntityRenderState extends BlockEntityRenderState {
    public Item tradeItem;
    public boolean enabled;
    public boolean initialized;
    public Level level;
    public BlockPos blockPos;
    public float floatOffset;
    public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
}
