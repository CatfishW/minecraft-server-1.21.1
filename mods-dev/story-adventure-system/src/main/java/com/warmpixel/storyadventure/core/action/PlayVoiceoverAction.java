package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.network.NetworkHandler;
import com.warmpixel.storyadventure.network.VoiceoverPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action that plays a voiceover audio file.
 * Voiceover files are stored in config/storyadventure/voiceovers/<story_id>/<sound_id>.ogg
 */
public class PlayVoiceoverAction implements NodeAction {
    
    private final String soundPath;
    private final float volume;
    private final float pitch;
    private final String characterId;
    
    public PlayVoiceoverAction(String soundPath, String characterId) {
        this(soundPath, 1.0f, 1.0f, characterId);
    }
    
    public PlayVoiceoverAction(String soundPath, float volume, float pitch, String characterId) {
        this.soundPath = soundPath;
        this.volume = volume;
        this.pitch = pitch;
        this.characterId = characterId;
    }
    
    @Override
    public String getType() {
        return "PLAY_VOICEOVER";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        // Get instance ID from first player (they should all be in the same instance)
        String instanceId = "";
        if (!players.isEmpty()) {
            var instance = StoryAdventureMod.getInstance().getInstanceManager().getPlayerInstance(players.get(0).getUUID());
            if (instance != null) {
                instanceId = instance.getInstanceId().toString();
            }
        }

        
        // Send voiceover payload to all players
        VoiceoverPayload payload = VoiceoverPayload.custom(instanceId, soundPath, volume, pitch, characterId);
        
        for (ServerPlayer player : players) {
            try {
                ServerPlayNetworking.send(player, payload);
                StoryAdventureMod.LOGGER.debug("[PlayVoiceoverAction] Sent voiceover {} to player {}", soundPath, player.getName().getString());
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.warn("[PlayVoiceoverAction] Failed to send voiceover to {}: {}", player.getName().getString(), e.getMessage());
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "PLAY_VOICEOVER");
        obj.addProperty("sound", soundPath);
        if (volume != 1.0f) obj.addProperty("volume", volume);
        if (pitch != 1.0f) obj.addProperty("pitch", pitch);
        if (characterId != null && !characterId.isEmpty()) obj.addProperty("character", characterId);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return "语音: " + soundPath + (characterId.isEmpty() ? "" : " (" + characterId + ")");
    }
    
    public static PlayVoiceoverAction fromJson(JsonObject obj) {
        String sound = obj.has("sound") ? obj.get("sound").getAsString() : "";
        float volume = obj.has("volume") ? obj.get("volume").getAsFloat() : 1.0f;
        float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 1.0f;
        String character = obj.has("character") ? obj.get("character").getAsString() : "narrator";
        return new PlayVoiceoverAction(sound, volume, pitch, character);
    }
}
