package com.warmpixel.imagedisplayer.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.warmpixel.imagedisplayer.block.BillboardBlock;
import com.warmpixel.imagedisplayer.block.entity.BillboardBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import com.mojang.math.Axis;

public class BillboardBlockEntityRenderer implements BlockEntityRenderer<BillboardBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("imagedisplayer", "gui/commands.png");

    public BillboardBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BillboardBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction direction = entity.getBlockState().getValue(BillboardBlock.FACING);
        
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        
        // Rotate to match facing direction
        switch (direction) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(0));
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(270));
        }
        
        // Move slightly forward from the block center to avoid z-fighting with the block model
        poseStack.translate(0, 0, -0.501);
        
        poseStack.scale(1.0f, 1.0f, 1.0f);

        Matrix4f matrix4f = poseStack.last().pose();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));

        // Render a quad
        float size = 0.5f;
        
        // Top-left
        vertexConsumer.addVertex(matrix4f, -size, size, 0).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);
        // Top-right
        vertexConsumer.addVertex(matrix4f, size, size, 0).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);
        // Bottom-right
        vertexConsumer.addVertex(matrix4f, size, -size, 0).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);
        // Bottom-left
        vertexConsumer.addVertex(matrix4f, -size, -size, 0).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, -1);

        poseStack.popPose();
    }
}
