package net.diebuddies.compat;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ParticleVertex;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.diebuddies.physics.BlockEntityVertexConsumer;
import net.diebuddies.physics.DummyVertexConsumer;
import net.diebuddies.physics.StarterClient;
import net.diebuddies.physics.settings.mobs.BoundingBoxGetter;
import net.minecraft.class_1058;
import net.minecraft.class_293;
import net.minecraft.class_296;
import net.minecraft.class_4588;
import net.minecraft.class_761;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

public class Sodium {
    public static void markSpriteActive(class_1058 sprite) {
        if (StarterClient.sodium) {
            try {
                SpriteUtil.markSpriteActive(sprite);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void scheduleChunkRebuild(class_761 renderer, int x, int y, int z, boolean important) {
        if (StarterClient.sodium) {
            try {
                SodiumWorldRenderer swr = SodiumWorldRenderer.instance();
                if (swr != null) {
                    swr.scheduleRebuildForChunk(x, y, z, important);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static BlockEntityVertexConsumer getNewBlockConsumer() {
        return new BlockEntityVertexConsumerSodium();
    }

    public static DummyVertexConsumer getNewDummyConsumer() {
        return new DummyVertexConsumerSodium();
    }

    public static BoundingBoxGetter getNewBoundingBoxConsumer() {
        return new BoundingBoxGetterSodium();
    }

    public static long getTextureElementOffset(Object format) {
        return ((class_293)format).method_60835(class_296.field_52109);
    }

    public static long getStride(Object format) {
        return ((class_293)format).method_1362();
    }

    public static void renderParticle(class_4588 vertexConsumer, Vector3f tmp0, Vector3f tmp1, Vector3f tmp2, Vector3f tmp3, float currentX, float currentY, float currentZ, float u0, float v0, float u1, float v1, int color, int light) {
        VertexBufferWriter writer = VertexBufferWriter.of(vertexConsumer);
        try (MemoryStack stack = MemoryStack.stackPush()){
            long buffer;
            long ptr = buffer = stack.nmalloc(112);
            ParticleVertex.put(ptr, tmp0.x + currentX, tmp0.y + currentY, tmp0.z + currentZ, u1, v1, color, light);
            ParticleVertex.put(ptr += 28L, tmp1.x + currentX, tmp1.y + currentY, tmp1.z + currentZ, u1, v0, color, light);
            ParticleVertex.put(ptr += 28L, tmp2.x + currentX, tmp2.y + currentY, tmp2.z + currentZ, u0, v0, color, light);
            ParticleVertex.put(ptr += 28L, tmp3.x + currentX, tmp3.y + currentY, tmp3.z + currentZ, u0, v1, color, light);
            writer.push(stack, buffer, 4, ParticleVertex.FORMAT);
        }
    }
}


