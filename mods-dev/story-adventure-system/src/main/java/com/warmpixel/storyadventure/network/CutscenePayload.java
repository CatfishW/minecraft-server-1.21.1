package com.warmpixel.storyadventure.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client payload for cutscene synchronization.
 * Allows the server to start, stop, or skip cutscenes on clients.
 */
public record CutscenePayload(
    String action,           // "START", "STOP", "SKIP"
    String instanceId,       // Instance ID for reference
    String cameraPathJson,   // JSON data for camera path (only for START)
    boolean skippable,       // Whether the cutscene can be skipped
    boolean letterbox,       // Enable letterbox bars
    int fadeInTicks,         // Fade in duration
    int fadeOutTicks,        // Fade out duration
    String voiceover,        // Optional voiceover path
    String subtitlesJson     // JSON data for subtitles
) implements CustomPacketPayload {
    
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("storyadventure", "cutscene");
    public static final CustomPacketPayload.Type<CutscenePayload> TYPE = new CustomPacketPayload.Type<>(ID);
    
    private static final Gson GSON = new Gson();
    
    public static final StreamCodec<FriendlyByteBuf, CutscenePayload> STREAM_CODEC = StreamCodec.of(
        CutscenePayload::write, CutscenePayload::read
    );
    
    public static void write(FriendlyByteBuf buf, CutscenePayload payload) {
        buf.writeUtf(payload.action());
        buf.writeUtf(payload.instanceId());
        buf.writeUtf(payload.cameraPathJson());
        buf.writeBoolean(payload.skippable());
        buf.writeBoolean(payload.letterbox());
        buf.writeVarInt(payload.fadeInTicks());
        buf.writeVarInt(payload.fadeOutTicks());
        buf.writeUtf(payload.voiceover() != null ? payload.voiceover() : "");
        buf.writeUtf(payload.subtitlesJson() != null ? payload.subtitlesJson() : "[]");
    }
    
    public static CutscenePayload read(FriendlyByteBuf buf) {
        String action = buf.readUtf();
        String instanceId = buf.readUtf();
        String cameraPathJson = buf.readUtf();
        boolean skippable = buf.readBoolean();
        boolean letterbox = buf.readBoolean();
        int fadeInTicks = buf.readVarInt();
        int fadeOutTicks = buf.readVarInt();
        String voiceover = buf.readUtf();
        String subtitlesJson = buf.readUtf();
        return new CutscenePayload(action, instanceId, cameraPathJson, skippable, letterbox, fadeInTicks, fadeOutTicks, voiceover, subtitlesJson);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    // ==================== Factory Methods ====================
    
    /**
     * Create a START cutscene payload.
     */
    public static CutscenePayload start(String instanceId, JsonObject cameraPath, 
                                         boolean skippable, boolean letterbox,
                                         int fadeInTicks, int fadeOutTicks, String voiceover,
                                         com.google.gson.JsonArray subtitles) {
        String pathJson = cameraPath != null ? GSON.toJson(cameraPath) : "{}";
        String subtitlesJson = subtitles != null ? GSON.toJson(subtitles) : "[]";
        return new CutscenePayload("START", instanceId, pathJson, skippable, letterbox, fadeInTicks, fadeOutTicks, voiceover, subtitlesJson);
    }
    
    /**
     * Create a STOP cutscene payload.
     */
    public static CutscenePayload stop(String instanceId) {
        return new CutscenePayload("STOP", instanceId, "", true, false, 0, 0, "", "[]");
    }
    
    /**
     * Create a SKIP cutscene payload.
     */
    public static CutscenePayload skip(String instanceId) {
        return new CutscenePayload("SKIP", instanceId, "", true, false, 0, 0, "", "[]");
    }
    
    /**
     * Parse camera path JSON to JsonObject.
     */
    public JsonObject getCameraPathAsJson() {
        if (cameraPathJson == null || cameraPathJson.isEmpty()) {
            return new JsonObject();
        }
        try {
            return GSON.fromJson(cameraPathJson, JsonObject.class);
        } catch (Exception e) {
            return new JsonObject();
        }
    }
    
    /**
     * Parse subtitles JSON to JsonArray.
     */
    public com.google.gson.JsonArray getSubtitlesAsJson() {
        if (subtitlesJson == null || subtitlesJson.isEmpty()) {
            return new com.google.gson.JsonArray();
        }
        try {
            return GSON.fromJson(subtitlesJson, com.google.gson.JsonArray.class);
        } catch (Exception e) {
            return new com.google.gson.JsonArray();
        }
    }
}
