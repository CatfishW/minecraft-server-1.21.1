package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * Action that plays a sound effect to players.
 */
public class PlaySoundAction implements NodeAction {
    
    private final String sound;
    private final float volume;
    private final float pitch;
    
    public PlaySoundAction(String sound) {
        this(sound, 1.0f, 1.0f);
    }
    
    public PlaySoundAction(String sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }
    
    @Override
    public String getType() {
        return "PLAY_SOUND";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            try {
                ResourceLocation soundLoc = ResourceLocation.tryParse(sound);
                if (soundLoc != null) {
                    SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLoc);
                    player.playNotifySound(soundEvent, SoundSource.MASTER, volume, pitch);
                }
            } catch (Exception e) {
                // Invalid sound, silently ignore
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "PLAY_SOUND");
        obj.addProperty("sound", sound);
        if (volume != 1.0f) obj.addProperty("volume", volume);
        if (pitch != 1.0f) obj.addProperty("pitch", pitch);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return "播放: " + sound;
    }
    
    public static PlaySoundAction fromJson(JsonObject obj) {
        String sound = obj.has("sound") ? obj.get("sound").getAsString() : "minecraft:entity.experience_orb.pickup";
        float volume = obj.has("volume") ? obj.get("volume").getAsFloat() : 1.0f;
        float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 1.0f;
        return new PlaySoundAction(sound, volume, pitch);
    }
}
