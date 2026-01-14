package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks if a clue has been discovered.
 */
public class ClueCondition implements EdgeCondition {
    public static final String TYPE = "CLUE";
    
    private final String clueId;
    private final boolean discovered;
    
    public ClueCondition(String clueId, boolean discovered) {
        this.clueId = clueId;
        this.discovered = discovered;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().hasDiscoveredClue(clueId) == discovered;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("clue", clueId);
        json.addProperty("discovered", discovered);
        return json;
    }
    
    @Override
    public String getDescription() {
        return discovered ? "Discovered clue '" + clueId + "'" : "Has not discovered '" + clueId + "'";
    }
    
    public static ClueCondition fromJson(JsonObject json) {
        String clueId = json.get("clue").getAsString();
        boolean discovered = !json.has("discovered") || json.get("discovered").getAsBoolean();
        return new ClueCondition(clueId, discovered);
    }
}
