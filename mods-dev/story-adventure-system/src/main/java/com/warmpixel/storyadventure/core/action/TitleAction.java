package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action that displays a title/subtitle to players.
 */
public class TitleAction implements NodeAction {
    
    private final String title;
    private final String subtitle;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;
    
    public TitleAction(String title, String subtitle) {
        this(title, subtitle, 10, 70, 20);
    }
    
    public TitleAction(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        this.title = title;
        this.subtitle = subtitle;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }
    
    @Override
    public String getType() {
        return "TITLE";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            // Set timing
            player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            
            // Set subtitle first (order matters)
            if (subtitle != null && !subtitle.isEmpty()) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal(subtitle.replace("{player}", player.getName().getString()))
                ));
            }
            
            // Set title
            if (title != null && !title.isEmpty()) {
                player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal(title.replace("{player}", player.getName().getString()))
                ));
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "TITLE");
        obj.addProperty("title", title);
        if (subtitle != null && !subtitle.isEmpty()) {
            obj.addProperty("subtitle", subtitle);
        }
        obj.addProperty("fade_in", fadeIn);
        obj.addProperty("stay", stay);
        obj.addProperty("fade_out", fadeOut);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return "标题: " + (title != null && title.length() > 20 ? title.substring(0, 18) + ".." : title);
    }
    
    public static TitleAction fromJson(JsonObject obj) {
        String title = obj.has("title") ? obj.get("title").getAsString() : "";
        String subtitle = obj.has("subtitle") ? obj.get("subtitle").getAsString() : "";
        int fadeIn = obj.has("fade_in") ? obj.get("fade_in").getAsInt() : 10;
        int stay = obj.has("stay") ? obj.get("stay").getAsInt() : 70;
        int fadeOut = obj.has("fade_out") ? obj.get("fade_out").getAsInt() : 20;
        return new TitleAction(title, subtitle, fadeIn, stay, fadeOut);
    }
}
