package com.warmpixel.storyadventure.client.render;
 
import com.warmpixel.storyadventure.StoryAdventureMod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders 3D indicators above enemies spawned by the story system.
 * This helps players find enemies easily in combat nodes.
 */
public class EnemyIndicatorRenderer {

    private static boolean enabled = true;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(EnemyIndicatorRenderer::onWorldRender);
    }

    public static void setEnabled(boolean enabled) {
        EnemyIndicatorRenderer.enabled = enabled;
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 cameraPos = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) return;

        // Use a glowing line render type. 
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        // Use a more robust render type that works well with shaders and transparency.
        VertexConsumer consumer = context.consumers().getBuffer(RenderType.entityTranslucentEmissive(net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/white.png")));
        
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        float time = (mc.level.getGameTime() + partialTick) / 20.0f;

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        Matrix4f matrix = poseStack.last().pose();
        
        int count = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            // Check for story_enemy tag.
            if (entity.getTags().contains("story_enemy") && entity.isAlive()) {
                double x = Mth.lerp(partialTick, entity.xo, entity.getX());
                double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() + 0.8;
                double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
                
                renderEnemyIndicator(consumer, matrix, x, y, z, time);
                count++;
            }
        }
        
        if (count > 0 && mc.level.getGameTime() % 100 == 0) {
            StoryAdventureMod.LOGGER.debug("[EnemyIndicatorRenderer] Rendered {} story enemy indicators", count);
        }
        
        poseStack.popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderEnemyIndicator(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, float time) {
        // Neon Red color
        int r = 255, g = 20, b = 20, a = 255;
        
        // Floating animation
        float bob = (float) Math.sin(time * 3.0f) * 0.15f;
        double currentY = y + bob;
        
        // Rotating diamond shape
        float size = 0.25f;
        float rot = time * 4.0f;
        
        renderDiamond(consumer, matrix, x, currentY, z, size, rot, r, g, b, a);
        
        // Secondary outer glow
        renderDiamond(consumer, matrix, x, currentY, z, size * 1.2f, -rot * 0.5f, r, g, b, 100);
        
        // Vertical line to ground if far away
        Minecraft mc = Minecraft.getInstance();
        double distSq = mc.player.distanceToSqr(x, y, z);
        if (distSq > 100) { // More than 10 blocks away
            float lineAlpha = Math.min(1.0f, (float)(distSq - 100) / 400.0f) * 150;
            float thickness = 0.02f;
            drawTriangle(consumer, matrix, x - thickness, currentY - 0.2f, z, x + thickness, currentY - 0.2f, z, x, currentY - 1.5f, z, r, g, b, (int)lineAlpha);
        }
    }

    private static void renderDiamond(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, float s, float rot, int r, int g, int b, int a) {
        float cos = (float) Math.cos(rot) * s;
        float sin = (float) Math.sin(rot) * s;
        
        // Render 8 triangles to form the diamond
        // Top pyramid
        drawTriangle(consumer, matrix, x, y + s, z, x + cos, y, z + sin, x + sin, y, z - cos, r, g, b, a);
        drawTriangle(consumer, matrix, x, y + s, z, x + sin, y, z - cos, x - cos, y, z - sin, r, g, b, a);
        drawTriangle(consumer, matrix, x, y + s, z, x - cos, y, z - sin, x - sin, y, z + cos, r, g, b, a);
        drawTriangle(consumer, matrix, x, y + s, z, x - sin, y, z + cos, x + cos, y, z + sin, r, g, b, a);
        
        // Bottom pyramid
        drawTriangle(consumer, matrix, x, y - s, z, x + sin, y, z - cos, x + cos, y, z + sin, r, g, b, a);
        drawTriangle(consumer, matrix, x, y - s, z, x - cos, y, z - sin, x + sin, y, z - cos, r, g, b, a);
        drawTriangle(consumer, matrix, x, y - s, z, x - sin, y, z + cos, x - cos, y, z - sin, r, g, b, a);
        drawTriangle(consumer, matrix, x, y - s, z, x + cos, y, z + sin, x - sin, y, z + cos, r, g, b, a);
    }

    private static void drawTriangle(VertexConsumer consumer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int r, int g, int b, int a) {
        // Position, Color, Texture, Light, Overlay, Normal
        consumer.addVertex(matrix, (float)x1, (float)y1, (float)z1).setColor(r, g, b, a).setUv(0, 0).setLight(240).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setNormal(0, 1, 0);
        consumer.addVertex(matrix, (float)x2, (float)y2, (float)z2).setColor(r, g, b, a).setUv(0, 1).setLight(240).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setNormal(0, 1, 0);
        consumer.addVertex(matrix, (float)x3, (float)y3, (float)z3).setColor(r, g, b, a).setUv(1, 1).setLight(240).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setNormal(0, 1, 0);
        consumer.addVertex(matrix, (float)x3, (float)y3, (float)z3).setColor(r, g, b, a).setUv(1, 1).setLight(240).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setNormal(0, 1, 0); // Duplicate for safety if it expects quads
    }

    private static void drawLine(VertexConsumer consumer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, int r, int g, int b, int a) {
        // Not used now, but kept for compatibility or fallback
    }
}
