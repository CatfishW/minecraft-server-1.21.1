package com.warmpixel.optimizer.render.backend;

import static org.lwjgl.opengl.GL46.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class BufferObject {
    private final int bufferHandle;
    private final int target; // e.g. GL_SHADER_STORAGE_BUFFER

    public BufferObject(int target) {
        this.target = target;
        this.bufferHandle = glCreateBuffers();
    }

    public void bind() {
        glBindBuffer(target, bufferHandle);
    }
    
    public void bindBase(int index) {
        glBindBufferBase(target, index, bufferHandle);
    }

    public void unbind() {
        glBindBuffer(target, 0);
    }

    public void uploadData(ByteBuffer data, int usage) {
        bind();
        glBufferData(target, data, usage);
        unbind();
    }
    
    public void uploadData(long size, int usage) {
        bind();
        glBufferData(target, size, usage);
        unbind();
    }
    
    public void uploadSubData(long offset, ByteBuffer data) {
        bind();
        glBufferSubData(target, offset, data);
        unbind();
    }

    public void delete() {
        glDeleteBuffers(bufferHandle);
    }
    
    public int getHandle() {
        return bufferHandle;
    }
}
