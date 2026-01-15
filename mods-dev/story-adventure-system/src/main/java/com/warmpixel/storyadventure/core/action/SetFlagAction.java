package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action that sets a story flag in the instance state.
 */
public class SetFlagAction implements NodeAction {
    
    private final String flag;
    private final boolean value;
    
    public SetFlagAction(String flag, boolean value) {
        this.flag = flag;
        this.value = value;
    }
    
    @Override
    public String getType() {
        return "SET_FLAG";
    }

    @Override
    public String getSummary() {
        return "Set Flag: " + flag + " = " + value;
    }

    @Override
    public void execute(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        
        // This action needs the instance context to work properly
        // In the current architecture, NodeAction.execute only takes players.
        // We might need to find the instance from the player.
        
        for (ServerPlayer player : players) {
            Instance instance = StoryAdventureMod.getInstance().getInstanceManager().getPlayerInstance(player.getUUID());
            if (instance != null) {
                instance.getState().setFlag(flag, value);
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "SET_FLAG");
        json.addProperty("flag", flag);
        json.addProperty("value", value);
        return json;
    }
    
    public static SetFlagAction fromJson(JsonObject json) {
        String flag = json.has("flag") ? json.get("flag").getAsString() : "unknown";
        boolean value = json.has("value") && json.get("value").getAsBoolean();
        return new SetFlagAction(flag, value);
    }
}
