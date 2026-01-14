package com.warmpixel.optimizer.render.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class VertexRecorder implements VertexConsumer {
    private final VertexConsumer delegate;
    private final ByteBuffer buffer;
    
    // Captured vertex state
    private float x, y, z;
    private int color = 0xFFFFFFFF;
    private float u, v;
    private int u1, v1; // overlay
    private int u2, v2; // light
    private float nx, ny, nz;
    
    public VertexRecorder(VertexConsumer delegate) {
        this.delegate = delegate;
        this.buffer = ByteBuffer.allocateDirect(1024 * 1024).order(ByteOrder.nativeOrder()); // 1MB buffer
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        this.color = ((alpha & 0xFF) << 24) | ((blue & 0xFF) << 16) | ((green & 0xFF) << 8) | (red & 0xFF);
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.u = u;
        this.v = v;
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.u1 = u;
        this.v1 = v;
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.u2 = u;
        this.v2 = v;
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        this.nx = x;
        this.ny = y;
        this.nz = z;
        delegate.setNormal(x, y, z);
        endVertex(); 
        return this;
    }
    
    public void endVertex() {
        // Write to buffer
        buffer.putFloat(x).putFloat(y).putFloat(z);
        buffer.putInt(color);
        buffer.putFloat(u).putFloat(v);
        buffer.putInt(packUV(u1, v1));
        buffer.putInt(packUV(u2, v2));
        buffer.putInt(packNormal(nx, ny, nz));
    }
    
    private int packUV(int u, int v) {
        return (u & 0xFFFF) | ((v & 0xFFFF) << 16);
    }
    
    private int packNormal(float x, float y, float z) {
        int xb = (int)(x * 127.0f) & 0xFF;
        int yb = (int)(y * 127.0f) & 0xFF;
        int zb = (int)(z * 127.0f) & 0xFF;
        return xb | (yb << 8) | (zb << 16);
    }
    
    public ByteBuffer getBuffer() {
        return buffer;
    }
    
    public int getVertexCount() {
        return buffer.position() / 36;
    }
}
