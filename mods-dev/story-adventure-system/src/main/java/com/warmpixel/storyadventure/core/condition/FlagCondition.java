package com.warmpixel.storyadventure.core.condition;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks if a story flag is set to a specific value.
 */
public class FlagCondition implements EdgeCondition {
    public static final String TYPE = "FLAG";
    
    private final String flagId;
    private final boolean expectedValue;
    
    public FlagCondition(String flagId, boolean expectedValue) {
        this.flagId = flagId;
        this.expectedValue = expectedValue;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        boolean actualValue = instance.getState().getFlag(flagId);
        boolean result = actualValue == expectedValue;
        com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.debug("[FlagCondition] Evaluating: flag='{}', expected={}, actual={}. Result={}", 
            flagId, expectedValue, actualValue, result);
        return result;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("flag", flagId);
        json.addProperty("value", expectedValue);
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Flag '" + flagId + "' is " + expectedValue;
    }
    
    public static FlagCondition fromJson(JsonObject json) {
        String flagId = json.get("flag").getAsString();
        boolean value = !json.has("value") || json.get("value").getAsBoolean();
        return new FlagCondition(flagId, value);
    }
}
