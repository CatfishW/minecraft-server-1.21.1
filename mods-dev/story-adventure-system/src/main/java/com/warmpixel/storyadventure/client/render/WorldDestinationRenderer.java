package com.warmpixel.storyadventure.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders 3D indicators at target destinations (e.g., rotating circles).
 */
public class WorldDestinationRenderer {

    private static final List<Vec3> destinations = new ArrayList<>();
    private static final Map<Vec3, String> destinationLabels = new HashMap<>();
    private static final Map<Integer, Boolean> outlineStates = new HashMap<>();
    private static boolean enabled = true;
    private static final double OUTLINE_DISTANCE = 64.0;
    private static final int COLOR_NEON_RED = 0xFFFF2222;
    private static final int COLOR_NEON_TEAL = 0xFF3BB6A6;
    private static final int COLOR_TEXT = 0xFFE6F2FF;

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldDestinationRenderer::onWorldRender);
    }

    public static void setDestinations(List<Vec3> points) {
        synchronized (destinations) {
            destinations.clear();
            destinations.addAll(points);
        }
    }
    
    public static void setDestinationWithLabel(Vec3 point, String label) {
        synchronized (destinations) {
            destinations.clear();
            destinations.add(point);
            destinationLabels.clear();
            if (label != null && !label.isEmpty()) {
                destinationLabels.put(point, label);
            }
        }
    }

    public static void clearDestinations() {
        synchronized (destinations) {
            destinations.clear();
            destinationLabels.clear();
        }
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled || destinations.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 cameraPos = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) return;
        
        updateEntityOutlines(mc, mc.player.position());

        // Use a transparent layer
        VertexConsumer consumer = context.consumers().getBuffer(RenderType.lines());
        MultiBufferSource buffers = context.consumers();

        float time = (mc.level.getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(true)) / 20.0f;
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        Matrix4f matrix = poseStack.last().pose();
        
        synchronized (destinations) {
            Vec3 playerPos = mc.player.position();
            for (Vec3 originalPos : destinations) {
                // Better ground snapping: search down from originalPos to find Mike's basement floor
                Vec3 pos = snapToGround(mc.level, originalPos);
                
                double distance = playerPos.distanceTo(pos);
                renderRotatingCircle(consumer, matrix, pos, time);
                renderUpperRing(consumer, matrix, pos, time);
                renderPillar(consumer, matrix, pos, time);
                renderDistanceLabel(mc, poseStack, buffers, context, pos, distance, time);
                
                // Breadcrumbs trail leading to destination
                renderBreadcrumbs(mc.level, consumer, matrix, playerPos, pos, time);
            }
        }
        
        poseStack.popPose();
    }

    private static Vec3 snapToGround(net.minecraft.world.level.Level level, Vec3 pos) {
        net.minecraft.core.BlockPos bpos = net.minecraft.core.BlockPos.containing(pos);
        // Start from the block containing the position and looking down up to 16 blocks
        for (int i = 0; i < 16; i++) {
            net.minecraft.core.BlockPos check = bpos.below(i);
            if (level.getBlockState(check).blocksMotion()) {
                return new Vec3(pos.x, check.getY() + 1.05, pos.z);
            }
        }
        // If not found, check if we are already inside a block?
        if (level.getBlockState(bpos).blocksMotion()) {
            return new Vec3(pos.x, bpos.getY() + 1.05, pos.z);
        }
        return pos;
    }

    private static void renderBreadcrumbs(net.minecraft.world.level.Level level, VertexConsumer consumer, Matrix4f matrix, Vec3 start, Vec3 end, float time) {
        double dist = start.distanceTo(end);
        if (dist < 5.0) return; 
        
        // Direction vector (horizontal only for ground trail)
        Vec3 diff = end.subtract(start);
        Vec3 dir = new Vec3(diff.x, 0, diff.z);
        if (dir.lengthSqr() < 0.001) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        
        int r = 255, g = 60, b = 60, a = 200; 
        double maxTrailDist = Math.min(dist - 3.0, 64.0);
        
        for (double d = 4.0; d < maxTrailDist; d += 2.0) {
            Vec3 point = start.add(dir.scale(d));
            // Snap breadcrumbs to terrain
            Vec3 groundPoint = snapToGround(level, point);
            
            float pulse = (float) Math.sin(time * 3.0f + d * 0.4f);
            int currentAlpha = (int)(a * (0.7f + 0.3f * pulse));
            float scale = 0.12f + 0.03f * pulse;
            
            drawStar(consumer, matrix, groundPoint.x, groundPoint.y + 0.2 + (pulse * 0.05), groundPoint.z, scale, r, g, b, currentAlpha, time + (float)d);
        }
    }

    private static void drawStar(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, float s, int r, int g, int b, int a, float time) {
        // Draw a rotating cross/star shape
        float rot = time * 2.0f;
        float cos = (float) Math.cos(rot) * s;
        float sin = (float) Math.sin(rot) * s;
        
        // Main vertical line
        consumer.addVertex(matrix, (float)x, (float)y - s*1.5f, (float)z).setColor(r, g, b, a).setNormal(0, 1, 0);
        consumer.addVertex(matrix, (float)x, (float)y + s*1.5f, (float)z).setColor(r, g, b, a).setNormal(0, 1, 0);
        
        // Rotating horizontal cross 1
        consumer.addVertex(matrix, (float)x - cos, (float)y, (float)z - sin).setColor(r, g, b, a).setNormal(0, 1, 0);
        consumer.addVertex(matrix, (float)x + cos, (float)y, (float)z + sin).setColor(r, g, b, a).setNormal(0, 1, 0);
        
        // Rotating horizontal cross 2 (perpendicular)
        consumer.addVertex(matrix, (float)x + sin, (float)y, (float)z - cos).setColor(r, g, b, a).setNormal(0, 1, 0);
        consumer.addVertex(matrix, (float)x - sin, (float)y, (float)z + cos).setColor(r, g, b, a).setNormal(0, 1, 0);
    }

    private static void renderRotatingCircle(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float time) {
        int segments = 48; // Smoother
        float radius = 2.0f;
        float rotation = time * 1.5f;
        
        // Pulse alpha for "premium" feel
        int a = (int)(180 + 75 * Math.sin(time * 2.5f));
        int r = 255, g = 20, b = 20;
        
        // Draw two concentric circles for thickness
        for (int j = 0; j < 2; j++) {
            float rad = radius + j * 0.05f;
            int alpha = a / (j + 1);
            
            for (int i = 0; i < segments; i++) {
                float angle1 = (float) i / segments * Mth.TWO_PI + rotation;
                float angle2 = (float) (i + 1) / segments * Mth.TWO_PI + rotation;

                float x1 = (float) (pos.x + Mth.cos(angle1) * rad);
                float z1 = (float) (pos.z + Mth.sin(angle1) * rad);
                float x2 = (float) (pos.x + Mth.cos(angle2) * rad);
                float z2 = (float) (pos.z + Mth.sin(angle2) * rad);
                
                // Flat on ground, slight overall bob to prevent z-fighting
                float yOffset = 0.05f + (float)Math.sin(time * 2.0f) * 0.05f;
                float y = (float)pos.y + yOffset;

                consumer.addVertex(matrix, x1, y, z1).setColor(r, g, b, alpha).setNormal(0, 1, 0);
                consumer.addVertex(matrix, x2, y, z2).setColor(r, g, b, alpha).setNormal(0, 1, 0);
            }
        }
    }

    private static void renderUpperRing(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float time) {
        int segments = 28;
        float radius = 1.1f + (float)Math.sin(time * 2.2f) * 0.1f;
        float rotation = -time * 1.6f;
        int r = 80, g = 220, b = 200, a = 180;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) i / segments * Mth.TWO_PI + rotation;
            float angle2 = (float) (i + 1) / segments * Mth.TWO_PI + rotation;

            float x1 = (float) (pos.x + Mth.cos(angle1) * radius);
            float z1 = (float) (pos.z + Mth.sin(angle1) * radius);
            float y1 = (float) (pos.y + 1.2f);

            float x2 = (float) (pos.x + Mth.cos(angle2) * radius);
            float z2 = (float) (pos.z + Mth.sin(angle2) * radius);
            float y2 = (float) (pos.y + 1.2f);

            consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(0, 1, 0);
            consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(0, 1, 0);
        }
    }

    private static void renderPillar(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float time) {
        float height = 50.0f; // Very tall pillar for maximum visibility
        int r = 255, g = 50, b = 50, a = 120;

        // Central vertical beam (more detailed)
        for (int i = 0; i < 8; i++) {
            float angle = (i / 8.0f) * Mth.TWO_PI + time;
            float offset = 0.05f + (float)Math.sin(time * 2.0 + i) * 0.02f;
            float ox = Mth.cos(angle) * offset;
            float oz = Mth.sin(angle) * offset;
            
            consumer.addVertex(matrix, (float)pos.x + ox, (float)pos.y, (float)pos.z + oz).setColor(r, g, b, a).setNormal(0, 1, 0);
            consumer.addVertex(matrix, (float)pos.x + ox, (float)pos.y + height, (float)pos.z + oz).setColor(r, g, b, 0).setNormal(0, 1, 0);
        }
    }

    private static void renderDistanceLabel(Minecraft mc, PoseStack poseStack, MultiBufferSource buffers, WorldRenderContext context,
                                            Vec3 pos, double distance, float time) {
        String title = destinationLabels.getOrDefault(pos, "目标");
        String meters = String.format("%.0fm", distance);
        int titleWidth = mc.font.width(title);
        int metersWidth = mc.font.width(meters);
        
        int color = distance < 20.0 ? COLOR_NEON_TEAL : COLOR_TEXT;
        // Semi-transparent background for high contrast
        int bgColor = 0x88000000; 

        float bob = (float)Math.sin(time * 2.0f) * 0.15f;

        poseStack.pushPose();
        // Position label significantly above ground to avoid line/pillar overlap
        poseStack.translate(pos.x, pos.y + 3.2f + bob, pos.z);
        poseStack.mulPose(context.camera().rotation());
        poseStack.scale(-0.035f, -0.035f, 0.035f); // Slightly larger for readability

        Matrix4f mat = poseStack.last().pose();
        
        // Use Font.DisplayMode.SEE_THROUGH to ensure visibility through terrain/walls (basements)
        mc.font.drawInBatch(title, -titleWidth / 2.0f, -10, color, false, mat, buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, bgColor, 0xF000F0);
        mc.font.drawInBatch(meters, -metersWidth / 2.0f, 2, COLOR_NEON_RED, false, mat, buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, bgColor, 0xF000F0);

        poseStack.popPose();
    }


    private static void updateEntityOutlines(Minecraft mc, Vec3 playerPos) {
        if (mc.level == null) return;
        double maxDistanceSq = OUTLINE_DISTANCE * OUTLINE_DISTANCE;
        java.util.Set<Integer> stillOutlined = new java.util.HashSet<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!entity.isAlive()) continue;
            boolean isStoryEntity = entity.getTags().contains("story_entity") || entity.getTags().contains("story_enemy");
            if (!isStoryEntity) continue;

            double distSq = entity.distanceToSqr(playerPos);
            if (distSq <= maxDistanceSq) {
                int id = entity.getId();
                if (!outlineStates.containsKey(id)) {
                    outlineStates.put(id, entity.isCurrentlyGlowing());
                }
                entity.setGlowingTag(true);
                stillOutlined.add(id);
            }
        }

        outlineStates.keySet().removeIf(id -> {
            if (stillOutlined.contains(id)) {
                return false;
            }
            Entity entity = mc.level.getEntity(id);
            boolean previousState = outlineStates.getOrDefault(id, false);
            if (entity != null) {
                entity.setGlowingTag(previousState);
            }
            return true;
        });
    }
}
