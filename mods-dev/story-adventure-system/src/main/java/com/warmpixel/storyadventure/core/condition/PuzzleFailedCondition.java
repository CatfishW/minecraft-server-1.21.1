package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/** Condition for puzzle failure (max attempts exceeded). */
public class PuzzleFailedCondition implements EdgeCondition {
    public static final String TYPE = "PUZZLE_FAILED";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("failed");
    }
    
    @Override
    public String getType() { return TYPE; }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }
    
    @Override
    public String getDescription() { return "Puzzle failed"; }
    
    public static PuzzleFailedCondition fromJson(JsonObject json) {
        return new PuzzleFailedCondition();
    }
}
