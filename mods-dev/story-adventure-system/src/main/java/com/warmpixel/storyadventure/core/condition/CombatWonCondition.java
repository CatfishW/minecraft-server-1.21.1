package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/** Condition for combat victory. */
public class CombatWonCondition implements EdgeCondition {
    public static final String TYPE = "COMBAT_WON";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("victory");
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
    public String getDescription() { return "Combat won"; }
    
    public static CombatWonCondition fromJson(JsonObject json) {
        return new CombatWonCondition();
    }
}
