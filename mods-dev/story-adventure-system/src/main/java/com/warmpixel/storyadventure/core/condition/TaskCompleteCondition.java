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
        // ✅ FIX: Check multiple indicators of task completion
        
        // Check 1: Standard node result
        boolean nodeResult = instance.getState().isCurrentNodeCompleteWith("success");
        
        // Check 2: Explicit task_complete metadata flag
        boolean taskCompleteFlag = false;
        if (instance.getState().getMetadata().has("task_complete")) {
            try {
                taskCompleteFlag = instance.getState().getMetadata().get("task_complete").getAsBoolean();
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.warn("[TaskCompleteCondition] Failed to read task_complete flag: {}", e.getMessage());
            }
        }
        
        // Check 3: All objectives completed (fallback check)
        boolean allObjectivesComplete = false;
        int completed = 0;
        int total = 0;
        
        try {
            if (instance.getState().getMetadata().has("completed_objectives")) {
                completed = instance.getState().getMetadata().get("completed_objectives").getAsInt();
            }
            if (instance.getState().getMetadata().has("total_objectives")) {
                total = instance.getState().getMetadata().get("total_objectives").getAsInt();
            }
            if (total > 0 && completed >= total) {
                allObjectivesComplete = true;
            }
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.warn("[TaskCompleteCondition] Failed to read objective counts: {}", e.getMessage());
        }
        
        // Any of the three conditions passing means task is complete
        boolean result = nodeResult || taskCompleteFlag || allObjectivesComplete;
        
        StoryAdventureMod.LOGGER.info("[TaskCompleteCondition] Evaluating: nodeResult={}, taskCompleteFlag={}, objectives={}/{}, allComplete={} => RESULT: {}", 
            nodeResult, taskCompleteFlag, completed, total, allObjectivesComplete, result);
        
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