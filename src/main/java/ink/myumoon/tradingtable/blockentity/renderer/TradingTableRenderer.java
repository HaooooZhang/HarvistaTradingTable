package ink.myumoon.tradingtable.blockentity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.NotNull;

public class TradingTableRenderer implements BlockEntityRenderer<TradingTableBlockEntity> {
    private static final float FLOAT_PERIOD_TICKS = 80.0F; // 4 seconds (20 tps)
    private static final float FLOAT_AMPLITUDE = 0.06F;

    public TradingTableRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(@NotNull TradingTableBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {

        if (!blockEntity.isEnabled() || !blockEntity.isInitialized()){
            return;
        }

        Item tradingItem = blockEntity.getTradeItem();
        if(tradingItem == null){
            return;
        }

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        Level level = blockEntity.getLevel();

        float animTicks = (level == null ? 0.0F : level.getGameTime() + partialTick);
        float floatOffset = Mth.sin((float) (animTicks * (Math.PI * 2.0D) / FLOAT_PERIOD_TICKS)) * FLOAT_AMPLITUDE;

        poseStack.pushPose();
        poseStack.translate(0.5, 1.5 + floatOffset, 0.5);
        float cameraYaw = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - cameraYaw));
        poseStack.scale(0.5F, 0.5F, 0.5F);

        int sampledLight = getLightLevel(level, blockEntity.getBlockPos().above());
        int lightLevel = mergeLight(packedLight, sampledLight);
        itemRenderer.renderStatic(new ItemStack(tradingItem), ItemDisplayContext.FIXED, lightLevel, OverlayTexture.NO_OVERLAY, poseStack, buffer, level, 0);
        poseStack.popPose();
    }

    // 光照，来自 Mattias150784/Pedestals
    private int getLightLevel(Level level, BlockPos pos) {
        if (level == null) {
            return LightTexture.pack(0, 0);
        }
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        return LightTexture.pack(blockLight, skyLight);
    }

    private int mergeLight(int a, int b) {
        int blockLight = Math.max(LightTexture.block(a), LightTexture.block(b));
        int skyLight = Math.max(LightTexture.sky(a), LightTexture.sky(b));
        return LightTexture.pack(blockLight, skyLight);
    }
}

