package com.warmpixel.optimizer.render.core;

import com.warmpixel.optimizer.render.backend.BufferObject;
import com.warmpixel.optimizer.render.backend.ComputeProgram;
import com.warmpixel.optimizer.render.backend.ComputeShader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL46;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.lwjgl.opengl.GL46.*;

public class PipelineManager {
    private static PipelineManager INSTANCE;
    private ComputeProgram transformProgram;
    private BufferObject verticesInBuffer;
    private BufferObject verticesOutBuffer;
    private int vaoHandle;

    public static PipelineManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PipelineManager();
        }
        return INSTANCE;
    }

    public void init() {
        if (transformProgram != null) return;

        try {
            String source = loadShaderSource(ResourceLocation.fromNamespaceAndPath("warmpixel-performance-optimizer", "shaders/core/transform/entity_vertex_transform_shader.compute"));
            ComputeShader shader = new ComputeShader();
            shader.setShaderSource(source);
            if (!shader.compileShader()) {
                throw new RuntimeException("Failed to compile shader");
            }
            
            transformProgram = new ComputeProgram();
            transformProgram.attachShader(shader);
            if (!transformProgram.link()) {
                throw new RuntimeException("Failed to link program");
            }
            
            shader.delete();
            
            // Initialize buffers 
            verticesInBuffer = new BufferObject(GL46.GL_SHADER_STORAGE_BUFFER);
            // Pre-allocate decent size (e.g. 16MB)
            verticesInBuffer.uploadData(16 * 1024 * 1024, GL46.GL_DYNAMIC_DRAW);
            
            verticesOutBuffer = new BufferObject(GL46.GL_SHADER_STORAGE_BUFFER);
            verticesOutBuffer.uploadData(16 * 1024 * 1024, GL46.GL_DYNAMIC_DRAW);
            
            // Init VAO for drawing
            vaoHandle = glCreateVertexArrays();
            glBindVertexArray(vaoHandle);
            
            // Bind output buffer as ARRAY_BUFFER for VAO setup
            glBindBuffer(GL_ARRAY_BUFFER, verticesOutBuffer.getHandle());
            
            int stride = 36;
            
            // Pos
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            
            // Color
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, stride, 12);
            
            // UV0
            glEnableVertexAttribArray(2);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 16);
            
            // UV1 (Overlay)
            // Packed as 2 shorts in an int
            glEnableVertexAttribArray(3);
            glVertexAttribPointer(3, 2, GL_UNSIGNED_SHORT, false, stride, 24); // 16+8=24
            
            // UV2 (Light)
            glEnableVertexAttribArray(4);
            glVertexAttribPointer(4, 2, GL_UNSIGNED_SHORT, false, stride, 28); // 24+4=28
            
            // Normal
            // Packed as 3 bytes in int (x | y<<8 | z<<16)
            // GL_BYTE normalized?
            glEnableVertexAttribArray(5);
            glVertexAttribPointer(5, 4, GL_BYTE, true, stride, 32); // 28+4=32
            
            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private String loadShaderSource(ResourceLocation location) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isPresent()) {
            try (InputStream is = resource.get().open()) {
                return IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Shader not found: " + location);
    }
    
    public void uploadStaticVertices(long offset, ByteBuffer data, int length) {
        // Enlarge if needed? For now assume it fits or user handles.
        verticesInBuffer.uploadSubData(offset, data);
    }
    
    public BufferObject getVerticesInBuffer() { return verticesInBuffer; }
    public BufferObject getVerticesOutBuffer() { return verticesOutBuffer; }
    public ComputeProgram getTransformProgram() { return transformProgram; }
    
    public void drawResult(int vertexCount) {
        if (vaoHandle == 0 || vertexCount == 0) return;
        
        glBindVertexArray(vaoHandle);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }
}
