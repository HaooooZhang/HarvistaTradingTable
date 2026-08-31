package ink.myumoon.tradingtable.blockentity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ink.myumoon.tradingtable.blockentity.SystemTradingTableBlockEntity;
import ink.myumoon.tradingtable.blockentity.renderer.renderstate.SystemTradingTableBlockEntityRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class SystemTradingTableRenderer implements BlockEntityRenderer<SystemTradingTableBlockEntity, SystemTradingTableBlockEntityRenderState> {
    private static final float FLOAT_PERIOD_TICKS = 80.0F;
    private static final float FLOAT_AMPLITUDE = 0.06F;

    private final ItemModelResolver itemModelResolver;

    public SystemTradingTableRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public void extractRenderState(@NonNull SystemTradingTableBlockEntity blockEntity, SystemTradingTableBlockEntityRenderState state,
                                   float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        state.tradeItem = blockEntity.getTradeItem();
        state.enabled = blockEntity.isEnabled();
        state.initialized = blockEntity.isInitialized();
        state.level = blockEntity.getLevel();
        state.blockPos = blockEntity.getBlockPos();

        if (state.level != null) {
            long cycleTicks = state.level.getGameTime() % (long) FLOAT_PERIOD_TICKS;
            float phase = (cycleTicks + partialTicks) / FLOAT_PERIOD_TICKS;
            state.floatOffset = Mth.sin(phase * ((float) (Math.PI * 2.0D))) * FLOAT_AMPLITUDE;
        } else {
            state.floatOffset = 0.0F;
        }

        state.itemRenderState.clear();
        if (state.tradeItem != null && state.enabled && state.initialized) {
            itemModelResolver.updateForNonLiving(state.itemRenderState,
                    new ItemStack(state.tradeItem), ItemDisplayContext.FIXED, Minecraft.getInstance().player);
        }
    }

    @Override
    public SystemTradingTableBlockEntityRenderState createRenderState() {
        return new SystemTradingTableBlockEntityRenderState();
    }

    @Override
    public void submit(SystemTradingTableBlockEntityRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (!state.enabled || !state.initialized || state.tradeItem == null || state.itemRenderState.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 1.5 + state.floatOffset, 0.5);

        double dx = cameraState.pos.x - state.blockPos.getX() - 0.5;
        double dz = cameraState.pos.z - state.blockPos.getZ() - 0.5;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));

        // 方块物品在 FIXED 上下文中朝向相反，补 180° 翻转
        if (state.tradeItem instanceof BlockItem) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }

        poseStack.scale(0.5F, 0.5F, 0.5F);

        int packedLight = state.level != null
                ? LevelRenderer.getLightCoords(state.level, state.blockPos.above())
                : 0xF000F0;

        state.itemRenderState.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }
}
