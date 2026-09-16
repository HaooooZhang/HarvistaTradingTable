package ink.myumoon.tradingtable.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

final class TradeItemSyncContainer implements Container {
    private final @Nullable Supplier<ItemStack> source;
    private ItemStack clientValue = ItemStack.EMPTY;

    TradeItemSyncContainer(@Nullable Supplier<ItemStack> source) {
        this.source = source;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.getItem(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.source != null ? this.source.get() : this.clientValue;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        this.clientValue = itemStack == null ? ItemStack.EMPTY : itemStack.copy();
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.clientValue = ItemStack.EMPTY;
    }
}
