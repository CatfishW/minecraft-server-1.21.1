package com.warmpixel.storyadventure.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.warmpixel.storyadventure.core.waypoint.TriggerBox;
import com.warmpixel.storyadventure.item.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders trigger box gizmos in the world.
 * Only visible when the player is holding the Admin Wand.
 */
public class TriggerBoxGizmoRenderer {
    
    private static TriggerBoxGizmoRenderer instance;
    private List<TriggerBox> triggerBoxes = new ArrayList<>();
    
    public static void register() {
        instance = new TriggerBoxGizmoRenderer();
        WorldRenderEvents.AFTER_TRANSLUCENT.register(instance::render);
    }
    
    public static TriggerBoxGizmoRenderer getInstance() {
        return instance;
    }
    
    private void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        // Only render if player is holding admin wand
        boolean holdingWand = mc.player.getMainHandItem().getItem() == ModItems.ADMIN_WAND ||
                              mc.player.getOffhandItem().getItem() == ModItems.ADMIN_WAND;
        
        if (!holdingWand || triggerBoxes.isEmpty()) return;
        
        Vec3 cameraPos = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        MultiBufferSource bufferSource = context.consumers();
        
        if (poseStack == null || bufferSource == null) return;
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        for (TriggerBox box : triggerBoxes) {
            if (box.getRadius() > 0) {
                renderSphere(poseStack, bufferSource, box);
            } else {
                renderBox(poseStack, bufferSource, box);
            }
        }
        
        poseStack.popPose();
    }
    
    private void renderSphere(PoseStack poseStack, MultiBufferSource bufferSource, TriggerBox box) {
        Vec3 center = box.getCenter();
        double radius = box.getRadius();
        if (center == null) return;

        float r = 0.2f, g = 1.0f, b = 0.3f, a = 0.6f;
        if (!box.getPlayersInside().isEmpty()) {
            r = 1.0f; g = 0.8f; b = 0.0f;
        }

        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        // Draw 3 primary circles to represent the sphere
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) i / segments * ((float) Math.PI * 2);
            float angle2 = (float) (i + 1) / segments * ((float) Math.PI * 2);

            float cos1 = (float) Math.cos(angle1) * (float) radius;
            float sin1 = (float) Math.sin(angle1) * (float) radius;
            float cos2 = (float) Math.cos(angle2) * (float) radius;
            float sin2 = (float) Math.sin(angle2) * (float) radius;

            // XY circle
            drawLine(lineConsumer, matrix, (float)center.x + cos1, (float)center.y + sin1, (float)center.z, 
                     (float)center.x + cos2, (float)center.y + sin2, (float)center.z, r, g, b, a);
            // XZ circle
            drawLine(lineConsumer, matrix, (float)center.x + cos1, (float)center.y, (float)center.z + sin1, 
                     (float)center.x + cos2, (float)center.y, (float)center.z + sin2, r, g, b, a);
            // YZ circle
            drawLine(lineConsumer, matrix, (float)center.x, (float)center.y + cos1, (float)center.z + sin1, 
                     (float)center.x, (float)center.y + cos2, (float)center.z + sin2, r, g, b, a);
        }
    }

    private void renderBox(PoseStack poseStack, MultiBufferSource bufferSource, TriggerBox box) {
        AABB bounds = box.getBounds();
        
        // Colors based on box state
        float r = 0.2f, g = 1.0f, b = 0.3f, a = 0.6f;
        if (!box.getPlayersInside().isEmpty()) {
            // Highlight when players inside
            r = 1.0f; g = 0.8f; b = 0.0f;
        }
        
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();
        
        float minX = (float) bounds.minX;
        float minY = (float) bounds.minY;
        float minZ = (float) bounds.minZ;
        float maxX = (float) bounds.maxX;
        float maxY = (float) bounds.maxY;
        float maxZ = (float) bounds.maxZ;
        
        // Bottom face
        drawLine(lineConsumer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(lineConsumer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        
        // Top face
        drawLine(lineConsumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(lineConsumer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        
        // Vertical edges
        drawLine(lineConsumer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(lineConsumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }
    
    private void drawLine(VertexConsumer consumer, Matrix4f matrix,
                          float x1, float y1, float z1, float x2, float y2, float z2,
                          float r, float g, float b, float a) {
        // Calculate normal for line direction
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        
        consumer.addVertex(matrix, x1, y1, z1)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz);
    }
    
    // Public API
    public void setTriggerBoxes(List<TriggerBox> boxes) {
        this.triggerBoxes = new ArrayList<>(boxes);
    }
    
    public void addTriggerBox(TriggerBox box) {
        triggerBoxes.add(box);
    }
    
    public void removeTriggerBox(String id) {
        triggerBoxes.removeIf(b -> b.getId().equals(id));
    }
    
    public void clear() {
        triggerBoxes.clear();
    }
}
