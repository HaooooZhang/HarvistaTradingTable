package ink.myumoon.tradingtable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();


    //todo 改成enum
    public static final ModConfigSpec.ConfigValue<String> CURRENCY_BACKEND = BUILDER
            .comment("Currency backend. Supported values: VANILLA_ITEM, NEO_ESSENTIALS, CURRENCY_OVERHAUL")
            .define("currencyBackend", "VANILLA_ITEM");

    public static final ModConfigSpec.ConfigValue<String> VANILLA_CURRENCY_ITEM = BUILDER
            .comment("Currency item used when currencyBackend is VANILLA_ITEM")
            .define("vanillaCurrencyItem", "minecraft:emerald");

    public static final ModConfigSpec.DoubleValue TAX_RATE = BUILDER
            .comment("Tax rate in [0, 1]")
            .defineInRange("taxRate", 0.0D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue ADMIN_PERMISSION_LEVEL = BUILDER
            .comment("Permission level that counts as admin for management/breaking checks")
            .defineInRange("adminPermissionLevel", 2, 0, 4);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static String resolvedCurrencyBackend = "VANILLA_ITEM";
    private static Item resolvedVanillaCurrencyItem = Items.EMERALD;
    private static double resolvedTaxRate = 0.0D;
    private static int resolvedAdminPermissionLevel = 2;

    private Config() {
    }

    public static String getCurrencyBackend() {
        return resolvedCurrencyBackend;
    }

    public static Item getVanillaCurrencyItem() {
        return resolvedVanillaCurrencyItem;
    }

    public static double getTaxRate() {
        return resolvedTaxRate;
    }

    public static int getAdminPermissionLevel() {
        return resolvedAdminPermissionLevel;
    }

    public static long roundTax(long baseAmount) {
        if (baseAmount <= 0) {
            return 0L;
        }
        return Math.round(baseAmount * resolvedTaxRate);
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        resolvedCurrencyBackend = CURRENCY_BACKEND.get().trim().toUpperCase();
        resolvedTaxRate = TAX_RATE.get();
        resolvedAdminPermissionLevel = ADMIN_PERMISSION_LEVEL.get();

        ResourceLocation itemId = ResourceLocation.tryParse(VANILLA_CURRENCY_ITEM.get().trim());
        if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
            resolvedVanillaCurrencyItem = BuiltInRegistries.ITEM.get(itemId);
        } else {
            resolvedVanillaCurrencyItem = Items.EMERALD;
        }
    }
}
