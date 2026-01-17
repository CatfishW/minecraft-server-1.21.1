package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action that sends a message to all players.
 */
public class MessageAction implements NodeAction {
    
    private final String text;
    private final boolean actionBar;
    private final String voiceover;
    
    public MessageAction(String text) {
        this(text, false, null);
    }
    
    public MessageAction(String text, boolean actionBar, String voiceover) {
        this.text = text;
        this.actionBar = actionBar;
        this.voiceover = voiceover;
    }
    
    @Override
    public String getType() {
        return "MESSAGE";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        // Play voiceover if specified
        if (voiceover != null && !voiceover.isEmpty() && !players.isEmpty()) {
            new PlayVoiceoverAction(voiceover, "narrator").execute(players);
        }

        for (ServerPlayer player : players) {
            String processed = text.replace("{player}", player.getName().getString());
            
            if (actionBar) {
                player.displayClientMessage(Component.literal(processed), true);
            } else {
                player.sendSystemMessage(Component.literal(processed));
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "MESSAGE");
        obj.addProperty("text", text);
        if (actionBar) obj.addProperty("action_bar", true);
        if (voiceover != null && !voiceover.isEmpty()) obj.addProperty("voiceover", voiceover);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return (voiceover != null && !voiceover.isEmpty() ? "🔊 " : "消息: ") + (text.length() > 25 ? text.substring(0, 23) + ".." : text);
    }
    
    public static MessageAction fromJson(JsonObject obj) {
        String text = obj.has("text") ? obj.get("text").getAsString() : "";
        boolean actionBar = obj.has("action_bar") && obj.get("action_bar").getAsBoolean();
        String voiceover = obj.has("voiceover") ? obj.get("voiceover").getAsString() : null;
        return new MessageAction(text, actionBar, voiceover);
    }
}
