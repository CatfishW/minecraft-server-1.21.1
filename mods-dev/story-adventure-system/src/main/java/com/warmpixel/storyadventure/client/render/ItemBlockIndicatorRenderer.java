package com.warmpixel.storyadventure.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Renders visual indicators around items or blocks in the world.
 * Features:
 * - A rotating circle circling around the target position
 * - An animated arrow pointing down at the target
 * - Glowing effects and pulsing animations
 * 
 * Used by the story adventure system to highlight objectives, pickups, or interactables.
 */
public class ItemBlockIndicatorRenderer {

    private static final ResourceLocation CIRCLE_TEXTURE = ResourceLocation.fromNamespaceAndPath("storyadventure", "textures/gui/indicators/circle_glow.png");
    private static final ResourceLocation ARROW_TEXTURE = ResourceLocation.fromNamespaceAndPath("storyadventure", "textures/gui/indicators/arrow_glow.png");

    // Active indicators
    private static final List<ItemBlockIndicator> activeIndicators = new CopyOnWriteArrayList<>();
    private static boolean enabled = true;

    /**
     * Data class representing a single indicator.
     */
    public static class ItemBlockIndicator {
        public final String id;
        public final Vec3 position;
        public final int color;
        public final String label;
        public final float circleRadius;
        public final boolean showArrow;
        public final boolean showCircle;
        public final float createdTime;

        public ItemBlockIndicator(String id, Vec3 position, int color, String label, 
                                  float circleRadius, boolean showArrow, boolean showCircle) {
            this.id = id;
            this.position = position;
            this.color = color;
            this.label = label;
            this.circleRadius = circleRadius;
            this.showArrow = showArrow;
            this.showCircle = showCircle;
            this.createdTime = System.currentTimeMillis() / 1000.0f;
        }

        public ItemBlockIndicator(String id, Vec3 position) {
            this(id, position, 0xFF00FFFF, "", 1.0f, true, true);
        }
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ItemBlockIndicatorRenderer::onWorldRender);
        StoryAdventureMod.LOGGER.info("[ItemBlockIndicatorRenderer] Registered world render event");
    }

    // ==================== Public API ====================

    public static void addIndicator(ItemBlockIndicator indicator) {
        // Remove existing indicator with same id
        activeIndicators.removeIf(i -> i.id.equals(indicator.id));
        activeIndicators.add(indicator);
    }

    public static void addIndicator(String id, Vec3 position, int color, String label) {
        addIndicator(new ItemBlockIndicator(id, position, color, label, 1.0f, true, true));
    }

    public static void addIndicator(String id, Vec3 position) {
        addIndicator(new ItemBlockIndicator(id, position));
    }

    public static void removeIndicator(String id) {
        activeIndicators.removeIf(i -> i.id.equals(id));
    }

    public static void clearIndicators() {
        activeIndicators.clear();
    }

    public static void setEnabled(boolean enabled) {
        ItemBlockIndicatorRenderer.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static List<ItemBlockIndicator> getActiveIndicators() {
        return new ArrayList<>(activeIndicators);
    }

    // ==================== Rendering ====================

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled || activeIndicators.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) return;

        Vec3 cameraPos = context.camera().getPosition();
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        float time = (mc.level.getGameTime() + partialTick) / 20.0f;

        // Setup render state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        // Render indicators using Tesselator
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        int renderedCount = 0;
        for (ItemBlockIndicator indicator : activeIndicators) {
            float indicatorTime = time - indicator.createdTime;
            
            if (indicator.showCircle) {
                renderRotatingCircle(buffer, matrix, indicator.position, indicator.circleRadius, 
                                    indicator.color, time, indicatorTime);
            }
            if (indicator.showArrow) {
                renderDownArrow(buffer, matrix, indicator.position, indicator.color, time, indicatorTime);
            }
            renderedCount++;
        }

        // Complete rendering
        if (renderedCount > 0) {
            MeshData meshData = buffer.build();
            if (meshData != null) {
                BufferUploader.drawWithShader(meshData);
            }
        }

        // Render labels with font (using MultiBufferSource from context)
        MultiBufferSource buffers = context.consumers();
        if (buffers != null) {
            for (ItemBlockIndicator indicator : activeIndicators) {
                if (indicator.label != null && !indicator.label.isEmpty()) {
                    renderLabel(mc, poseStack, buffers, context, indicator, time);
                }
            }
        }

        poseStack.popPose();

        // Restore render state
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Renders a rotating circle around the target position (on the XZ plane).
     */
    private static void renderRotatingCircle(BufferBuilder buffer, Matrix4f matrix, Vec3 pos, 
                                              float baseRadius, int color, float time, float indicatorTime) {
        int segments = 48;
        float rotation = time * 2.0f; // Rotation speed
        
        // Extract color components
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // Pulsing effect
        float pulse = 0.9f + 0.1f * (float)Math.sin(time * 3.0f);
        float radius = baseRadius * pulse;
        
        // Fade in effect
        float fadeIn = Math.min(1.0f, indicatorTime * 2.0f);
        int alpha = (int)(220 * fadeIn);
        
        // Height above ground - raised significantly to avoid clipping
        float circleY = (float)pos.y + 0.15f;
        
        // Draw outer glow ring (thicker)
        float glowRadius = radius * 1.2f;
        int glowAlpha = (int)(100 * fadeIn);
        drawCircleRingAtY(buffer, matrix, pos, glowRadius, 0.35f, segments, rotation, r, g, b, glowAlpha, circleY);
        
        // Draw main circle ring (much thicker - 0.25 instead of 0.05)
        drawCircleRingAtY(buffer, matrix, pos, radius, 0.25f, segments, rotation, r, g, b, alpha, circleY);
        
        // Draw inner bright ring
        drawCircleRingAtY(buffer, matrix, pos, radius * 0.85f, 0.12f, segments, rotation, 255, 255, 255, (int)(alpha * 0.6f), circleY + 0.02f);
        
        // Draw larger orbiting dots (4 dots)
        float dotRadius = radius * 0.18f;
        for (int i = 0; i < 4; i++) {
            float angle = rotation + (i * Mth.PI / 2);
            float dotX = (float)pos.x + Mth.cos(angle) * radius;
            float dotZ = (float)pos.z + Mth.sin(angle) * radius;
            float dotY = circleY + 0.05f;
            
            // Outer glow of dot
            drawFilledCircle(buffer, matrix, dotX, dotY, dotZ, dotRadius * 1.5f, 12, r, g, b, glowAlpha);
            // Main dot
            drawFilledCircle(buffer, matrix, dotX, dotY, dotZ, dotRadius, 12, r, g, b, alpha);
            // Bright core
            drawFilledCircle(buffer, matrix, dotX, dotY + 0.01f, dotZ, dotRadius * 0.5f, 8, 255, 255, 255, alpha);
        }
    }

    /**
     * Draws a ring (circle outline) using triangles at a specific Y height.
     */
    private static void drawCircleRingAtY(BufferBuilder buffer, Matrix4f matrix, Vec3 center,
                                          float radius, float thickness, int segments, float rotation,
                                          int r, int g, int b, int a, float y) {
        float innerRadius = radius - thickness;
        float outerRadius = radius + thickness;
        
        for (int i = 0; i < segments; i++) {
            float angle1 = (float)i / segments * Mth.TWO_PI + rotation;
            float angle2 = (float)(i + 1) / segments * Mth.TWO_PI + rotation;
            
            float cos1 = Mth.cos(angle1);
            float sin1 = Mth.sin(angle1);
            float cos2 = Mth.cos(angle2);
            float sin2 = Mth.sin(angle2);
            
            float ix1 = (float)center.x + cos1 * innerRadius;
            float iz1 = (float)center.z + sin1 * innerRadius;
            float ox1 = (float)center.x + cos1 * outerRadius;
            float oz1 = (float)center.z + sin1 * outerRadius;
            
            float ix2 = (float)center.x + cos2 * innerRadius;
            float iz2 = (float)center.z + sin2 * innerRadius;
            float ox2 = (float)center.x + cos2 * outerRadius;
            float oz2 = (float)center.z + sin2 * outerRadius;
            
            // Two triangles per segment to form a quad
            buffer.addVertex(matrix, ix1, y, iz1).setColor(r, g, b, a);
            buffer.addVertex(matrix, ox1, y, oz1).setColor(r, g, b, a);
            buffer.addVertex(matrix, ix2, y, iz2).setColor(r, g, b, a);
            
            buffer.addVertex(matrix, ox1, y, oz1).setColor(r, g, b, a);
            buffer.addVertex(matrix, ox2, y, oz2).setColor(r, g, b, a);
            buffer.addVertex(matrix, ix2, y, iz2).setColor(r, g, b, a);
        }
    }

    /**
     * Draws a ring (circle outline) using triangles.
     */
    private static void drawCircleRing(BufferBuilder buffer, Matrix4f matrix, Vec3 center,
                                       float radius, float thickness, int segments, float rotation,
                                       int r, int g, int b, int a) {
        float innerRadius = radius - thickness;
        float outerRadius = radius + thickness;
        float y = (float)center.y + 0.05f; // Slightly above ground
        
        for (int i = 0; i < segments; i++) {
            float angle1 = (float)i / segments * Mth.TWO_PI + rotation;
            float angle2 = (float)(i + 1) / segments * Mth.TWO_PI + rotation;
            
            float cos1 = Mth.cos(angle1);
            float sin1 = Mth.sin(angle1);
            float cos2 = Mth.cos(angle2);
            float sin2 = Mth.sin(angle2);
            
            float ix1 = (float)center.x + cos1 * innerRadius;
            float iz1 = (float)center.z + sin1 * innerRadius;
            float ox1 = (float)center.x + cos1 * outerRadius;
            float oz1 = (float)center.z + sin1 * outerRadius;
            
            float ix2 = (float)center.x + cos2 * innerRadius;
            float iz2 = (float)center.z + sin2 * innerRadius;
            float ox2 = (float)center.x + cos2 * outerRadius;
            float oz2 = (float)center.z + sin2 * outerRadius;
            
            // Two triangles per segment to form a quad
            buffer.addVertex(matrix, ix1, y, iz1).setColor(r, g, b, a);
            buffer.addVertex(matrix, ox1, y, oz1).setColor(r, g, b, a);
            buffer.addVertex(matrix, ix2, y, iz2).setColor(r, g, b, a);
            
            buffer.addVertex(matrix, ox1, y, oz1).setColor(r, g, b, a);
            buffer.addVertex(matrix, ox2, y, oz2).setColor(r, g, b, a);
            buffer.addVertex(matrix, ix2, y, iz2).setColor(r, g, b, a);
        }
    }

    /**
     * Draws a filled circle (for dots).
     */
    private static void drawFilledCircle(BufferBuilder buffer, Matrix4f matrix,
                                         float cx, float cy, float cz, float radius,
                                         int segments, int r, int g, int b, int a) {
        for (int i = 0; i < segments; i++) {
            float angle1 = (float)i / segments * Mth.TWO_PI;
            float angle2 = (float)(i + 1) / segments * Mth.TWO_PI;
            
            float x1 = cx + Mth.cos(angle1) * radius;
            float z1 = cz + Mth.sin(angle1) * radius;
            float x2 = cx + Mth.cos(angle2) * radius;
            float z2 = cz + Mth.sin(angle2) * radius;
            
            buffer.addVertex(matrix, cx, cy, cz).setColor(r, g, b, a);
            buffer.addVertex(matrix, x1, cy, z1).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2, cy, z2).setColor(r, g, b, a);
        }
    }

    /**
     * Renders a downward-pointing arrow above the target position.
     */
    private static void renderDownArrow(BufferBuilder buffer, Matrix4f matrix, Vec3 pos,
                                         int color, float time, float indicatorTime) {
        // Extract color components
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // Bob animation - moves up and down
        float bob = (float)Math.sin(time * 4.0f) * 0.15f;
        float baseY = (float)pos.y + 2.5f + bob;
        
        // Fade in effect
        float fadeIn = Math.min(1.0f, indicatorTime * 2.0f);
        int alpha = (int)(220 * fadeIn);
        int glowAlpha = (int)(80 * fadeIn);
        
        // Arrow dimensions
        float arrowWidth = 0.35f;
        float arrowHeight = 0.6f;
        float arrowThickness = 0.15f;
        
        float cx = (float)pos.x;
        float cz = (float)pos.z;
        
        // Draw glow (larger, more transparent)
        drawArrowShape(buffer, matrix, cx, baseY + 0.05f, cz, 
                      arrowWidth * 1.3f, arrowHeight * 1.2f, arrowThickness * 1.5f,
                      r, g, b, glowAlpha);
        
        // Draw main arrow
        drawArrowShape(buffer, matrix, cx, baseY, cz, 
                      arrowWidth, arrowHeight, arrowThickness,
                      r, g, b, alpha);
        
        // Draw bright core
        drawArrowShape(buffer, matrix, cx, baseY - 0.02f, cz, 
                      arrowWidth * 0.6f, arrowHeight * 0.8f, arrowThickness * 0.7f,
                      255, 255, 255, (int)(alpha * 0.7f));
    }

    /**
     * Draws a 3D arrow shape pointing downward with complete solid faces.
     */
    private static void drawArrowShape(BufferBuilder buffer, Matrix4f matrix,
                                       float cx, float topY, float cz,
                                       float width, float height, float thickness,
                                       int r, int g, int b, int a) {
        float bottomY = topY - height;
        float midY = topY - height * 0.4f;
        float stemWidth = width * 0.35f;
        
        // Front and back Z positions
        float frontZ = cz - thickness;
        float backZ = cz + thickness;
        
        // Wing edge positions
        float leftX = cx - width;
        float rightX = cx + width;
        
        // ====== FRONT FACE (negative Z) ======
        // Left wing triangle
        addTriangle(buffer, matrix, cx, bottomY, frontZ, leftX, midY, frontZ, cx, midY, frontZ, r, g, b, a);
        // Right wing triangle
        addTriangle(buffer, matrix, cx, bottomY, frontZ, cx, midY, frontZ, rightX, midY, frontZ, r, g, b, a);
        // Stem (two triangles for quad)
        addTriangle(buffer, matrix, cx - stemWidth, midY, frontZ, cx + stemWidth, midY, frontZ, cx + stemWidth, topY, frontZ, r, g, b, a);
        addTriangle(buffer, matrix, cx - stemWidth, midY, frontZ, cx + stemWidth, topY, frontZ, cx - stemWidth, topY, frontZ, r, g, b, a);
        
        // ====== BACK FACE (positive Z) - reversed winding ======
        addTriangle(buffer, matrix, cx, bottomY, backZ, cx, midY, backZ, leftX, midY, backZ, r, g, b, a);
        addTriangle(buffer, matrix, cx, bottomY, backZ, rightX, midY, backZ, cx, midY, backZ, r, g, b, a);
        addTriangle(buffer, matrix, cx - stemWidth, midY, backZ, cx + stemWidth, topY, backZ, cx + stemWidth, midY, backZ, r, g, b, a);
        addTriangle(buffer, matrix, cx - stemWidth, midY, backZ, cx - stemWidth, topY, backZ, cx + stemWidth, topY, backZ, r, g, b, a);
        
        // ====== LEFT WING EDGE (from tip to left corner) ======
        addTriangle(buffer, matrix, cx, bottomY, frontZ, cx, bottomY, backZ, leftX, midY, frontZ, r, g, b, a);
        addTriangle(buffer, matrix, leftX, midY, frontZ, cx, bottomY, backZ, leftX, midY, backZ, r, g, b, a);
        
        // ====== RIGHT WING EDGE (from tip to right corner) ======
        addTriangle(buffer, matrix, cx, bottomY, frontZ, rightX, midY, frontZ, cx, bottomY, backZ, r, g, b, a);
        addTriangle(buffer, matrix, rightX, midY, frontZ, rightX, midY, backZ, cx, bottomY, backZ, r, g, b, a);
        
        // ====== LEFT WING TOP EDGE (from left corner to stem) ======
        addTriangle(buffer, matrix, leftX, midY, frontZ, leftX, midY, backZ, cx - stemWidth, midY, frontZ, r, g, b, a);
        addTriangle(buffer, matrix, cx - stemWidth, midY, frontZ, leftX, midY, backZ, cx - stemWidth, midY, backZ, r, g, b, a);
        
        // ====== RIGHT WING TOP EDGE (from right corner to stem) ======
        addTriangle(buffer, matrix, rightX, midY, frontZ, cx + stemWidth, midY, frontZ, rightX, midY, backZ, r, g, b, a);
        addTriangle(buffer, matrix, cx + stemWidth, midY, frontZ, cx + stemWidth, midY, backZ, rightX, midY, backZ, r, g, b, a);
        
        // ====== STEM LEFT SIDE ======
        addTriangle(buffer, matrix, cx - stemWidth, midY, frontZ, cx - stemWidth, midY, backZ, cx - stemWidth, topY, frontZ, r, g, b, a);
        addTriangle(buffer, matrix, cx - stemWidth, topY, frontZ, cx - stemWidth, midY, backZ, cx - stemWidth, topY, backZ, r, g, b, a);
        
        // ====== STEM RIGHT SIDE ======
        addTriangle(buffer, matrix, cx + stemWidth, midY, frontZ, cx + stemWidth, topY, frontZ, cx + stemWidth, midY, backZ, r, g, b, a);
        addTriangle(buffer, matrix, cx + stemWidth, topY, frontZ, cx + stemWidth, topY, backZ, cx + stemWidth, midY, backZ, r, g, b, a);
        
        // ====== STEM TOP ======
        addTriangle(buffer, matrix, cx - stemWidth, topY, frontZ, cx - stemWidth, topY, backZ, cx + stemWidth, topY, frontZ, r, g, b, a);
        addTriangle(buffer, matrix, cx + stemWidth, topY, frontZ, cx - stemWidth, topY, backZ, cx + stemWidth, topY, backZ, r, g, b, a);
    }
    
    /**
     * Helper to add a triangle with 3 vertices.
     */
    private static void addTriangle(BufferBuilder buffer, Matrix4f matrix,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3,
                                    int r, int g, int b, int a) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
    }

    /**
     * Renders a text label above the indicator.
     */
    private static void renderLabel(Minecraft mc, PoseStack poseStack, MultiBufferSource buffers,
                                    WorldRenderContext context, ItemBlockIndicator indicator, float time) {
        String label = indicator.label;
        if (label == null || label.isEmpty()) return;
        
        int labelWidth = mc.font.width(label);
        float bob = (float)Math.sin(time * 2.0f) * 0.1f;
        
        poseStack.pushPose();
        poseStack.translate(indicator.position.x, indicator.position.y + 3.5f + bob, indicator.position.z);
        poseStack.mulPose(context.camera().rotation());
        poseStack.scale(-0.03f, -0.03f, 0.03f);
        
        Matrix4f mat = poseStack.last().pose();
        
        // Background
        int bgColor = 0x88000000;
        // Text color from indicator
        int textColor = indicator.color | 0xFF000000;
        
        mc.font.drawInBatch(label, -labelWidth / 2.0f, 0, textColor, false, mat, buffers, 
                           net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, bgColor, 0xF000F0);
        
        poseStack.popPose();
    }
}
