package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks if the current task node failed.
 */
public class TaskFailedCondition implements EdgeCondition {
    public static final String TYPE = "TASK_FAILED";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("failed");
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Task failed";
    }
    
    public static TaskFailedCondition fromJson(JsonObject json) {
        return new TaskFailedCondition();
    }
}
