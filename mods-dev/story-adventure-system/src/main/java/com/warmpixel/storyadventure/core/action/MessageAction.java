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
    
    public MessageAction(String text) {
        this(text, false);
    }
    
    public MessageAction(String text, boolean actionBar) {
        this.text = text;
        this.actionBar = actionBar;
    }
    
    @Override
    public String getType() {
        return "MESSAGE";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
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
        return obj;
    }
    
    @Override
    public String getSummary() {
        return "消息: " + (text.length() > 25 ? text.substring(0, 23) + ".." : text);
    }
    
    public static MessageAction fromJson(JsonObject obj) {
        String text = obj.has("text") ? obj.get("text").getAsString() : "";
        boolean actionBar = obj.has("action_bar") && obj.get("action_bar").getAsBoolean();
        return new MessageAction(text, actionBar);
    }
}
