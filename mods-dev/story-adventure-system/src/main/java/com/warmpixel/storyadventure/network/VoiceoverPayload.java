package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for sending voiceover audio to clients.
 * Triggers playback of pre-recorded OGG files for dialogue and narrator lines.
 */
public record VoiceoverPayload(
    String instanceId,
    String soundPath,
    float volume,
    float pitch,
    String characterId
) implements CustomPacketPayload {
    
    public static final Type<VoiceoverPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "voiceover"));
    
    public static final StreamCodec<FriendlyByteBuf, VoiceoverPayload> STREAM_CODEC = 
        StreamCodec.of(VoiceoverPayload::write, VoiceoverPayload::read);
    
    public static void write(FriendlyByteBuf buf, VoiceoverPayload payload) {
        buf.writeUtf(payload.instanceId);
        buf.writeUtf(payload.soundPath);
        buf.writeFloat(payload.volume);
        buf.writeFloat(payload.pitch);
        buf.writeUtf(payload.characterId != null ? payload.characterId : "");
    }
    
    public static VoiceoverPayload read(FriendlyByteBuf buf) {
        return new VoiceoverPayload(
            buf.readUtf(),
            buf.readUtf(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readUtf()
        );
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * Create a voiceover payload for the narrator.
     */
    public static VoiceoverPayload narrator(String instanceId, String soundPath) {
        return new VoiceoverPayload(instanceId, soundPath, 1.0f, 1.0f, "narrator");
    }
    
    /**
     * Create a voiceover payload for a character.
     */
    public static VoiceoverPayload character(String instanceId, String soundPath, String characterId) {
        return new VoiceoverPayload(instanceId, soundPath, 1.0f, 1.0f, characterId);
    }
    
    /**
     * Create a voiceover payload with custom volume and pitch.
     */
    public static VoiceoverPayload custom(String instanceId, String soundPath, float volume, float pitch, String characterId) {
        return new VoiceoverPayload(instanceId, soundPath, volume, pitch, characterId);
    }
}
