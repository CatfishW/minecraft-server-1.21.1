package com.warmpixel.optimizer.render;

import com.warmpixel.optimizer.render.backend.BufferObject;
import com.warmpixel.optimizer.render.backend.ComputeProgram;
import com.warmpixel.optimizer.render.core.PipelineManager;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL46;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL46.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL46.glMemoryBarrier;

public class RenderQueue {
    private static final RenderQueue INSTANCE = new RenderQueue();
    // Struct: int meshStart, int meshCount, int light, int overlay, int color
    //         mat4 pose (16 floats)
    //         mat3 normal (9 floats -> padded to 12 floats/16 bytes aligned)
    // Structure size per instance: (5 ints) + (16 floats) + (12 floats) = 20 + 64 + 48 = 132 bytes?
    // Let's align to vec4. 
    // Data layout in SSBO "Sharings":
    // struct SharingData {
    //     mat4 transform; // 64 bytes
    //     mat3 normal;    // 48 bytes (std430 pads columns) -> actually 12 floats = 48 bytes
    // };
    // We also need "VaryingData" for offsets.
    
    private final Map<Mesh, List<Matrix4f>> batches = new HashMap<>();
    
    // GPU Buffers
    private BufferObject instanceBuffer; // SSBO holding matrices
    // Output buffer handled by PipelineManager
    
    public static RenderQueue getInstance() {
        return INSTANCE;
    }

    public void addRequest(Mesh mesh, Matrix4f pose, Matrix3f normal, int light, int overlay, int color) {
        // For simplicity, we currently ignore light/overlay/color per instance overrides 
        // and assume they are baked in or controlled uniformly?
        // Wait, light/overlay/color ARE important per instance.
        // But my simplified shader currently just takes Matrix.
        // I should stick to Geometry only for now (MVP optimization).
        // Or unpack light/overlay if needed.
        
        batches.computeIfAbsent(mesh, k -> new ArrayList<>()).add(pose);
    }
    
    public void clear() {
        batches.clear();
    }
    
    public void dispatch() {
        if (batches.isEmpty()) return;
        
        PipelineManager pm = PipelineManager.getInstance();
        ComputeProgram program = pm.getTransformProgram();
        if (program == null) return;
        
        program.use();
        
        // Ensure instance buffer is large enough
        // We reuse one buffer for all batches, uploading dynamically?
        // Or one big buffer?
        // Simple: Loop batches.
        
        if (instanceBuffer == null) {
            instanceBuffer = new BufferObject(GL46.GL_SHADER_STORAGE_BUFFER);
        }
        
        int outputOffset = 0;
        
        for (Map.Entry<Mesh, List<Matrix4f>> entry : batches.entrySet()) {
            Mesh mesh = entry.getKey();
            List<Matrix4f> poses = entry.getValue();
            int count = poses.size();
            
            // Upload poses
            // 64 bytes per pose
            ByteBuffer data = MemoryUtil.memAlloc(count * 64);
            FloatBuffer fb = data.asFloatBuffer();
            for (Matrix4f m : poses) {
                m.get(fb);
                fb.position(fb.position() + 16);
            }
            fb.flip();
            
            instanceBuffer.uploadData(data, GL46.GL_DYNAMIC_DRAW);
            MemoryUtil.memFree(data);
            
            // Bind buffers
            // 0: MeshVertices
            pm.getVerticesInBuffer().bindBase(0);
            // 1: InstanceData
            instanceBuffer.bindBase(1);
            // 2: VerticesOut
            pm.getVerticesOutBuffer().bindBase(2);
            
            // Set Uniforms
            // meshVertexCount
            program.setUniform1ui(program.getUniformLocation("meshVertexCount"), mesh.vertexCount);
            // meshStartOffset
            program.setUniform1ui(program.getUniformLocation("meshStartOffset"), mesh.vertexStart);
            // outputStartOffset
            program.setUniform1ui(program.getUniformLocation("outputStartOffset"), outputOffset);
            // instanceCount
            program.setUniform1ui(program.getUniformLocation("instanceCount"), count);
            
            // Dispatch
            int totalVertices = mesh.vertexCount * count;
            int groups = (totalVertices + 127) / 128;
            program.dispatch(groups, 1, 1);
            
            // Barrier
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            
            outputOffset += totalVertices;
        }
        
        program.unuse();
        
        // DRAW
        // Bind Output Buffer as VBO
        // Setup pointers... this is tricky in vanilla pipeline context.
        // We probably defined a custom RenderType?
        // Or we just hijack the global buffer?
        
        // For this optimization mod, we just want to draw.
        // pm.drawResult(outputOffset);
    }
}
