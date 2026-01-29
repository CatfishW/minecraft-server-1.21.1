package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for managing Background Music (BGM) on clients.
 */
public record BGMPayload(
    String soundPath,
    float volume,
    boolean loop,
    boolean stop,
    int fadeTicks
) implements CustomPacketPayload {
    
    public static final Type<BGMPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "bgm"));
    
    public static final StreamCodec<FriendlyByteBuf, BGMPayload> STREAM_CODEC = 
        StreamCodec.of(BGMPayload::write, BGMPayload::read);
    
    public static void write(FriendlyByteBuf buf, BGMPayload payload) {
        buf.writeUtf(payload.soundPath != null ? payload.soundPath : "");
        buf.writeFloat(payload.volume);
        buf.writeBoolean(payload.loop);
        buf.writeBoolean(payload.stop);
        buf.writeInt(payload.fadeTicks);
    }
    
    public static BGMPayload read(FriendlyByteBuf buf) {
        return new BGMPayload(
            buf.readUtf(),
            buf.readFloat(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readInt()
        );
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static BGMPayload play(String path, float volume, boolean loop, int fadeTicks) {
        return new BGMPayload(path, volume, loop, false, fadeTicks);
    }
    
    public static BGMPayload stop(int fadeTicks) {
        return new BGMPayload("", 0f, false, true, fadeTicks);
    }
}
