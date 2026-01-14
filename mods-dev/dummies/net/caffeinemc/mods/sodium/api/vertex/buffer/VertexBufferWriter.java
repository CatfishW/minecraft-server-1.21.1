package net.caffeinemc.mods.sodium.api.vertex.buffer;

import net.minecraft.class_293;
import net.minecraft.class_4588;
import org.lwjgl.system.MemoryStack;

public interface VertexBufferWriter {
    static VertexBufferWriter of(class_4588 consumer) { return null; }
    void push(MemoryStack stack, long ptr, int count, class_293 format);
}
