package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action to control background music (BGM).
 */
public class BGMAction implements NodeAction {
    
    private final String sound;
    private final float volume;
    private final boolean loop;
    private final boolean stop;
    private final int fade;

    public BGMAction(String sound, float volume, boolean loop, boolean stop, int fade) {
        this.sound = sound;
        this.volume = volume;
        this.loop = loop;
        this.stop = stop;
        this.fade = fade;
    }

    public static BGMAction fromJson(JsonObject json) {
        String sound = json.has("sound") ? json.get("sound").getAsString() : "";
        float volume = json.has("volume") ? json.get("volume").getAsFloat() : 1.0f;
        boolean loop = !json.has("loop") || json.get("loop").getAsBoolean();
        boolean stop = json.has("stop") && json.get("stop").getAsBoolean();
        int fade = json.has("fade") ? json.get("fade").getAsInt() : 20;
        
        return new BGMAction(sound, volume, loop, stop, fade);
    }

    @Override
    public String getType() {
        return "BGM";
    }

    @Override
    public void execute(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (stop) {
                NetworkHandler.sendBGMStop(player, fade);
            } else if (!sound.isEmpty()) {
                NetworkHandler.sendBGM(player, sound, volume, loop, fade);
            }
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "BGM");
        obj.addProperty("sound", sound);
        obj.addProperty("volume", volume);
        obj.addProperty("loop", loop);
        obj.addProperty("stop", stop);
        obj.addProperty("fade", fade);
        return obj;
    }

    @Override
    public String getSummary() {
        if (stop) return "停止背景音乐 (淡出: " + fade + "t)";
        return "播放背景音乐: " + sound + " (音量: " + volume + ")";
    }
}
