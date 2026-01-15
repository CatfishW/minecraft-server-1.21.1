package com.warmpixel.storyadventure.client.render;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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
        StoryAdventureMod.LOGGER.info("[EnemyIndicatorRenderer] Registered world render event");
    }

    public static void setEnabled(boolean enabled) {
        EnemyIndicatorRenderer.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) return;

        Vec3 cameraPos = context.camera().getPosition();
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        float time = (mc.level.getGameTime() + partialTick) / 20.0f;

        // Count and collect enemies first to avoid issues with empty buffers
        int count = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getTags().contains("story_enemy") && entity.isAlive()) {
                count++;
            }
        }

        if (count == 0) return;
        
        // Debug logging: check first 5 entities for tags
        if (mc.level.getGameTime() % 100 == 0) {
             int logged = 0;
             for (Entity entity : mc.level.entitiesForRendering()) {
                 if (logged++ < 5) {
                     StoryAdventureMod.LOGGER.info("[EnemyIndicatorRenderer] Entity: {} (ID: {}), Tags: {}", 
                         entity.getType().toShortString(), entity.getId(), entity.getTags());
                 }
                 if (entity.getTags().contains("story_enemy")) {
                      StoryAdventureMod.LOGGER.info("[EnemyIndicatorRenderer] FOUND TARGET: {} (ID: {})", 
                         entity.getType().toShortString(), entity.getId());
                 }
             }
        }

        // Save current render state
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        // Configure render state for our custom rendering
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Use Tesselator for direct triangle rendering
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        int renderedCount = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getTags().contains("story_enemy") && entity.isAlive()) {
                double x = Mth.lerp(partialTick, entity.xo, entity.getX());
                double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() + 0.8;
                double z = Mth.lerp(partialTick, entity.zo, entity.getZ());

                renderEnemyIndicator(buffer, matrix, x, y, z, time, mc.player.distanceToSqr(x, y, z));
                renderedCount++;
            }
        }

        // Build and draw the mesh
        if (renderedCount > 0) {
            MeshData meshData = buffer.build();
            if (meshData != null) {
                BufferUploader.drawWithShader(meshData);
            }
        }

        // Restore render state
        poseStack.popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // Debug logging
        if (renderedCount > 0 && mc.level.getGameTime() % 100 == 0) {
            StoryAdventureMod.LOGGER.debug("[EnemyIndicatorRenderer] Rendered {} story enemy indicators", renderedCount);
        }
    }

    private static void renderEnemyIndicator(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z, float time, double distSq) {
        // Neon Red color
        int r = 255, g = 20, b = 20, a = 255;

        // Floating animation
        float bob = (float) Math.sin(time * 3.0f) * 0.15f;
        double currentY = y + bob;

        // Rotating diamond shape
        float size = 0.25f;
        float rot = time * 4.0f;

        // Main diamond
        renderDiamond(buffer, matrix, x, currentY, z, size, rot, r, g, b, a);

        // Secondary outer glow (slightly larger, counter-rotating, semi-transparent)
        renderDiamond(buffer, matrix, x, currentY, z, size * 1.3f, -rot * 0.5f, r, g, b, 80);

        // Vertical pointer line when far away (more than 10 blocks)
        if (distSq > 100) {
            float lineAlpha = Math.min(1.0f, (float) (distSq - 100) / 400.0f);
            int lineA = (int) (lineAlpha * 150);
            renderPointerLine(buffer, matrix, x, currentY - 0.3, z, 0.03f, 1.0f, r, g, b, lineA);
        }
    }

    private static void renderDiamond(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z, float s, float rot, int r, int g, int b, int a) {
        float cos = (float) Math.cos(rot) * s;
        float sin = (float) Math.sin(rot) * s;

        // Four corner points at the middle of the diamond
        float px1 = (float) x + cos;
        float pz1 = (float) z + sin;
        float px2 = (float) x + sin;
        float pz2 = (float) z - cos;
        float px3 = (float) x - cos;
        float pz3 = (float) z - sin;
        float px4 = (float) x - sin;
        float pz4 = (float) z + cos;

        float topY = (float) y + s;
        float midY = (float) y;
        float botY = (float) y - s;

        // Top pyramid (4 triangles)
        addTriangle(buffer, matrix, (float) x, topY, (float) z, px1, midY, pz1, px2, midY, pz2, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, topY, (float) z, px2, midY, pz2, px3, midY, pz3, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, topY, (float) z, px3, midY, pz3, px4, midY, pz4, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, topY, (float) z, px4, midY, pz4, px1, midY, pz1, r, g, b, a);

        // Bottom pyramid (4 triangles) - reversed winding for correct face culling
        addTriangle(buffer, matrix, (float) x, botY, (float) z, px2, midY, pz2, px1, midY, pz1, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, botY, (float) z, px3, midY, pz3, px2, midY, pz2, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, botY, (float) z, px4, midY, pz4, px3, midY, pz3, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, botY, (float) z, px1, midY, pz1, px4, midY, pz4, r, g, b, a);
    }

    private static void renderPointerLine(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z, float width, float length, int r, int g, int b, int a) {
        // Draw a thin triangular pointer pointing down
        float halfWidth = width;
        float topY = (float) y;
        float bottomY = (float) y - length;

        // Front face
        addTriangle(buffer, matrix,
                (float) x - halfWidth, topY, (float) z,
                (float) x + halfWidth, topY, (float) z,
                (float) x, bottomY, (float) z,
                r, g, b, a);

        // Back face
        addTriangle(buffer, matrix,
                (float) x + halfWidth, topY, (float) z,
                (float) x - halfWidth, topY, (float) z,
                (float) x, bottomY, (float) z,
                r, g, b, a);

        // Side faces
        addTriangle(buffer, matrix,
                (float) x, topY, (float) z - halfWidth,
                (float) x, topY, (float) z + halfWidth,
                (float) x, bottomY, (float) z,
                r, g, b, a);

        addTriangle(buffer, matrix,
                (float) x, topY, (float) z + halfWidth,
                (float) x, topY, (float) z - halfWidth,
                (float) x, bottomY, (float) z,
                r, g, b, a);
    }

    private static void addTriangle(BufferBuilder buffer, Matrix4f matrix,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3,
                                    int r, int g, int b, int a) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
    }
}