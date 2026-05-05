package ink.myumoon.tradingtable.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    //todo 本地化与改进
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<CurrencyBackend> CURRENCY_BACKEND = BUILDER
            .comment("Currency backend")
            .defineEnum("currencyBackend", CurrencyBackend.ITEM);

    public static final ModConfigSpec.ConfigValue<String> CURRENCY_ITEM = BUILDER
            .comment("Currency item used when currencyBackend is ITEM")
            .define("CurrencyItem", "minecraft:emerald");

    public static final ModConfigSpec.ConfigValue<Boolean> COMPATIBILITY_MODE = BUILDER
            .comment("Compatibility with Item Currency from other mods")
            .define("CompatibilityMode", true);

    public static final ModConfigSpec.DoubleValue TAX_RATE = BUILDER
            .comment("Tax rate in [0, 1]")
            .defineInRange("taxRate", 0.0D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue ADMIN_PERMISSION_LEVEL = BUILDER
            .comment("Permission level that counts as admin for management/breaking checks")
            .defineInRange("adminPermissionLevel", 2, 0, 4);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static CurrencyBackend resolvedCurrencyBackend = CurrencyBackend.ITEM;
    private static Item resolvedCurrencyItem = Items.EMERALD;
    private static Boolean resolvedCompatibilityMode = true;
    private static double resolvedTaxRate = 0.0D;
    private static int resolvedAdminPermissionLevel = 2;

    private Config() {
    }

    public static CurrencyBackend getCurrencyBackend() {
        return resolvedCurrencyBackend;
    }

    public static boolean isNeoEssentialsMode() {
        return resolvedCurrencyBackend == CurrencyBackend.NEO_ESSENTIALS;
    }

    public static Item getCurrencyItem() {
        return resolvedCurrencyItem;
    }

    public static Boolean getCompatibilityMode(){
        return resolvedCompatibilityMode;
    }

    public static double getTaxRate() {
        return resolvedTaxRate;
    }

    public static int getAdminPermissionLevel() {
        return resolvedAdminPermissionLevel;
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        resolvedCurrencyBackend = CURRENCY_BACKEND.get();
        resolvedCompatibilityMode = COMPATIBILITY_MODE.get();
        resolvedTaxRate = TAX_RATE.get();
        resolvedAdminPermissionLevel = ADMIN_PERMISSION_LEVEL.get();


        ResourceLocation itemId = ResourceLocation.tryParse(CURRENCY_ITEM.get().trim());
        if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
            resolvedCurrencyItem = BuiltInRegistries.ITEM.get(itemId);
        } else {
            resolvedCurrencyItem = Items.EMERALD;
        }
    }
}
