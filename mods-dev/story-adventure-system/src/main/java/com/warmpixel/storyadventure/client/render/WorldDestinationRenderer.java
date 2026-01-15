package com.warmpixel.storyadventure.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders 3D indicators at target destinations (e.g., rotating circles).
 */
public class WorldDestinationRenderer {

    private static final List<Vec3> destinations = new ArrayList<>();
    private static boolean enabled = true;

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldDestinationRenderer::onWorldRender);
    }

    public static void setDestinations(List<Vec3> points) {
        synchronized (destinations) {
            destinations.clear();
            destinations.addAll(points);
        }
    }

    public static void clearDestinations() {
        synchronized (destinations) {
            destinations.clear();
        }
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled || destinations.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 cameraPos = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) return;
        
        // Use a transparent layer
        VertexConsumer consumer = context.consumers().getBuffer(RenderType.lines());

        float time = (mc.level.getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(true)) / 20.0f;
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        Matrix4f matrix = poseStack.last().pose();
        
        synchronized (destinations) {
            Vec3 playerPos = mc.player.position();
            for (Vec3 pos : destinations) {
                renderRotatingCircle(consumer, matrix, pos, time);
                renderPillar(consumer, matrix, pos, time);
                
                // Breadcrumbs trail leading to destination
                renderBreadcrumbs(consumer, matrix, playerPos, pos, time);
            }
        }
        
        poseStack.popPose();
    }

    private static void renderBreadcrumbs(VertexConsumer consumer, Matrix4f matrix, Vec3 start, Vec3 end, float time) {
        double dist = start.distanceTo(end);
        if (dist < 5.0) return; // Hide when very close to avoid clutter
        
        // Direction vector
        Vec3 dir = end.subtract(start).normalize();
        
        // Stranger Things Neon Red color for path
        int r = 255, g = 20, b = 20, a = 200;
        
        // Render dots every 2 blocks instead of 4 for a smoother path
        // Increase render distance for breadcrumbs to 64 blocks
        double maxTrailDist = Math.min(dist - 3.0, 64.0);
        
        for (double d = 6.0; d < maxTrailDist; d += 2.0) {
            Vec3 point = start.add(dir.scale(d));
            
            // Pulsing animation
            float pulse = (float) Math.sin(time * 4.0f + d * 0.8f);
            
            // Neon flicker effect
            float flicker = (Minecraft.getInstance().level.getGameTime() + (int)d) % 15 == 0 ? 0.3f : 1.0f;
            int currentAlpha = (int)(a * flicker);
            
            float scale = 0.08f + 0.04f * pulse;
            float yOffset = 1.2f + 0.3f * pulse; 
            
            // Add a small "float" motion
            double ox = Math.cos(time * 2.5f + d) * 0.15;
            double oz = Math.sin(time * 2.5f + d) * 0.15;
            
            drawStar(consumer, matrix, point.x + ox, point.y + yOffset, point.z + oz, scale, r, g, b, currentAlpha, time + (float)d);
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
        int segments = 32; // More segments for smoother look
        float radius = 2.0f;
        float rotation = time * 2.0f;
        
        // Stranger Things Neon Red
        int r = 255, g = 10, b = 10, a = 255;
        
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) i / segments * Mth.TWO_PI + rotation;
            float angle2 = (float) (i + 1) / segments * Mth.TWO_PI + rotation;

            float x1 = (float) (pos.x + Mth.cos(angle1) * radius);
            float z1 = (float) (pos.z + Mth.sin(angle1) * radius);
            float y1 = (float) (pos.y + 0.1f + Mth.sin(time * 3.0f + angle1 * 3) * 0.15f);

            float x2 = (float) (pos.x + Mth.cos(angle2) * radius);
            float z2 = (float) (pos.z + Mth.sin(angle2) * radius);
            float y2 = (float) (pos.y + 0.1f + Mth.sin(time * 3.0f + angle2 * 3) * 0.15f);

            // Exterior glow (Red)
            consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(0, 1, 0);
            consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(0, 1, 0);
            
            // Interior glow (White/Pink for neon effect)
            consumer.addVertex(matrix, x1, y1 + 0.05f, z1).setColor(255, 200, 200, 200).setNormal(0, 1, 0);
            consumer.addVertex(matrix, x2, y2 + 0.05f, z2).setColor(255, 200, 200, 200).setNormal(0, 1, 0);
        }
    }

    private static void renderPillar(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float time) {
        float height = 20.0f; // Taller pillar for easier sighting
        int r = 255, g = 50, b = 50, a = 80;

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
}
