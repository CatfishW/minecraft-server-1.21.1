package com.warmpixel.optimizer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.warmpixel.optimizer.render.core.PipelineManager;
import com.warmpixel.optimizer.render.core.VertexRecorder;
// Import TACZ classes using reflection or provided dependency
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class RenderOptimizer {
    public static final RenderOptimizer INSTANCE = new RenderOptimizer();
    
    // Map object (ModelPart/BedrockPart) to its Mesh
    private final Map<Object, Mesh> meshCache = new HashMap<>();
    
    // Tracks current vertex offset in the global static buffer
    private int currentVertexOffset = 0;
    
    public RenderOptimizer() {
    }

    public boolean render(Object part, PoseStack.Pose pose, int light, int overlay, int color) {
        Mesh mesh = meshCache.get(part);
        
        if (mesh == null) {
             // Build mesh on the fly
             mesh = buildMesh(part);
             if (mesh != null) {
                 meshCache.put(part, mesh);
             } else {
                 return false; // Failed to build (e.g. no cubes), fallback to vanilla
             }
        }
        
        // Queue render request
        // Transform pose matrix to match what shader expects? 
        // Shader expects ModelMatrix.
        RenderQueue.getInstance().addRequest(mesh, pose.pose(), pose.normal(), light, overlay, color);
        return true;
    }

    private Mesh buildMesh(Object part) {
        if (part instanceof BedrockPart bedrockPart) {
            if (bedrockPart.cubes.isEmpty()) return null;
            
            // Record vertices
            // We use a dummy VertexConsumer that records to a buffer
            // We must use Identity Pose because we want Local Space vertices
            PoseStack identityStack = new PoseStack();
            PoseStack.Pose identityPose = identityStack.last();
            
            // To record, we need a VertexRecorder
            // Since we don't have a delegate (we are CREATING the mesh), we pass null or dummy listener?
            // VertexRecorder currently takes a delegate. We should modify it to allow null (no-op delegate).
            // But wait, VertexRecorder delegates calls.
            // Let's create a "NoOpVertexConsumer" or allow null.
            
            VertexRecorder recorder = new VertexRecorder(new com.mojang.blaze3d.vertex.VertexConsumer() {
                public com.mojang.blaze3d.vertex.VertexConsumer addVertex(double x, double y, double z) { return this; }
                public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
                public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { return this; }
                public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
                public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
                public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { return this; }
                public void defaultColor(int r, int g, int b, int a) {}
                public void unsetDefaultColor() {}
                public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) { return this; }
            });
            
            for (BedrockCube cube : bedrockPart.cubes) {
                // Compile cube into recorder
                // 1F, 1F, 1F, 1F for RGBA (white), simpler than passing color?
                // compile uses floats. 
                cube.compile(identityPose, recorder, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
            }
            
            // Now recorder has the buffer
            ByteBuffer data = recorder.getBuffer();
            int vertexCount = recorder.getVertexCount();
            
            if (vertexCount == 0) return null;
            
            // Upload to Global Buffer
            uploadMesh(data, vertexCount);
            
            Mesh mesh = new Mesh(currentVertexOffset, vertexCount);
            currentVertexOffset += vertexCount;
            return mesh;
        }
        return null;
    }
    
    private void uploadMesh(ByteBuffer data, int count) {
        // Upload to PipelineManager's verticesInBuffer
        // We need to append.
        // For simplicity, let's assume PipelineManager exposes a method "uploadStaticVertices(offset, data)"
        PipelineManager.getInstance().uploadStaticVertices(currentVertexOffset * 36L, data, count * 36);
    }

    public void startFrame() {
        // Reset dynamic requests? No, Queue clears them.
        RenderQueue.getInstance().clear();
    }
    
    public void endFrame() {
        // Dispatch Compute Shader
        RenderQueue.getInstance().dispatch();
    }
}
