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
    int fadeOutTicks         // Fade out duration
) implements CustomPacketPayload {
    
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("storyadventure", "cutscene");
    public static final CustomPacketPayload.Type<CutscenePayload> TYPE = new CustomPacketPayload.Type<>(ID);
    
    private static final Gson GSON = new Gson();
    
    public static final StreamCodec<FriendlyByteBuf, CutscenePayload> CODEC = new StreamCodec<>() {
        @Override
        public CutscenePayload decode(FriendlyByteBuf buf) {
            String action = buf.readUtf();
            String instanceId = buf.readUtf();
            String cameraPathJson = buf.readUtf();
            boolean skippable = buf.readBoolean();
            boolean letterbox = buf.readBoolean();
            int fadeInTicks = buf.readVarInt();
            int fadeOutTicks = buf.readVarInt();
            return new CutscenePayload(action, instanceId, cameraPathJson, skippable, letterbox, fadeInTicks, fadeOutTicks);
        }
        
        @Override
        public void encode(FriendlyByteBuf buf, CutscenePayload payload) {
            buf.writeUtf(payload.action());
            buf.writeUtf(payload.instanceId());
            buf.writeUtf(payload.cameraPathJson());
            buf.writeBoolean(payload.skippable());
            buf.writeBoolean(payload.letterbox());
            buf.writeVarInt(payload.fadeInTicks());
            buf.writeVarInt(payload.fadeOutTicks());
        }
    };
    
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
                                         int fadeInTicks, int fadeOutTicks) {
        String pathJson = cameraPath != null ? GSON.toJson(cameraPath) : "{}";
        return new CutscenePayload("START", instanceId, pathJson, skippable, letterbox, fadeInTicks, fadeOutTicks);
    }
    
    /**
     * Create a STOP cutscene payload.
     */
    public static CutscenePayload stop(String instanceId) {
        return new CutscenePayload("STOP", instanceId, "", true, false, 0, 0);
    }
    
    /**
     * Create a SKIP cutscene payload.
     */
    public static CutscenePayload skip(String instanceId) {
        return new CutscenePayload("SKIP", instanceId, "", true, false, 0, 0);
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
}
