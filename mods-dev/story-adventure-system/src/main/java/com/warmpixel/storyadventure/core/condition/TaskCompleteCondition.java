package com.warmpixel.storyadventure.core.condition;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks if the current task node was completed successfully.
 */
public class TaskCompleteCondition implements EdgeCondition {
    public static final String TYPE = "TASK_COMPLETE";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        boolean result = instance.getState().isCurrentNodeCompleteWith("success");
        com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.debug("[TaskCompleteCondition] Evaluating. Result={}", result);
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
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Task completed successfully";
    }
    
    public static TaskCompleteCondition fromJson(JsonObject json) {
        return new TaskCompleteCondition();
    }
}
