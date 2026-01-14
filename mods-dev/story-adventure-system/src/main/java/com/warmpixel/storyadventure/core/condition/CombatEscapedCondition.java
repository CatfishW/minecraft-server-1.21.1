package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/** Condition for combat escape. */
public class CombatEscapedCondition implements EdgeCondition {
    public static final String TYPE = "COMBAT_ESCAPED";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("escaped");
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
    public String getDescription() { return "Escaped combat"; }
    
    public static CombatEscapedCondition fromJson(JsonObject json) {
        return new CombatEscapedCondition();
    }
}
