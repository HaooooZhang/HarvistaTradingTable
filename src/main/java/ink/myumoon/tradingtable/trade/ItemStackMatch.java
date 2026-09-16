package ink.myumoon.tradingtable.trade;

import net.minecraft.world.item.ItemStack;

public final class ItemStackMatch {
    private ItemStackMatch() {
    }

    public static boolean matches(ItemStack stock, ItemStack template) {
        if (stock.isEmpty() || template.isEmpty()) {
            return false;
        }
        if (template.isComponentsPatchEmpty()) {
            return stock.is(template.getItem());
        }
        return ItemStack.isSameItemSameComponents(stock, template);
    }
}
