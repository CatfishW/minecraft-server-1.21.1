package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/** Condition for successful puzzle completion. */
public class PuzzleSolvedCondition implements EdgeCondition {
    public static final String TYPE = "PUZZLE_SOLVED";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("solved");
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
    public String getDescription() { return "Puzzle solved"; }
    
    public static PuzzleSolvedCondition fromJson(JsonObject json) {
        return new PuzzleSolvedCondition();
    }
}
