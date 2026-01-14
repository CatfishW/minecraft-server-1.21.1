package com.warmpixel.storyadventure.core.node;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for TASK nodes.
 * Supports fetch, investigate, escort, and stealth objectives.
 */
public class TaskNodeHandler implements NodeHandler {
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String taskType = node.getString("task_type", "FETCH");
        int timeLimitSeconds = node.getInt("time_limit_seconds", 0);
        boolean stealthRequired = node.getBoolean("stealth_required", false);
        
        StoryAdventureMod.LOGGER.info("[TaskNodeHandler] onEnter: instance={}, node={}, type={}, timeLimit={}s, stealth={}", 
            instance.getInstanceId(), node.getId(), taskType, timeLimitSeconds, stealthRequired);
        
        // Start timer if time limit is set
        if (timeLimitSeconds > 0) {
            long durationMs = timeLimitSeconds * 1000L;
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Starting task timer for {}ms", durationMs);
            instance.getState().startTimer("task_timer", durationMs);
        }
        
        // Parse and track objectives
        List<TaskObjective> objectives = parseObjectives(node);
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Parsed {} objectives for task {}", objectives.size(), node.getId());
        for (int i = 0; i < objectives.size(); i++) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Objective [{}]: type={}, data={}", i, objectives.get(i).type(), objectives.get(i).data());
        }
        
        // Store objectives in instance state for tracking
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Check timer expiration
        var timer = instance.getState().getTimer("task_timer");
        if (timer != null && timer.isExpired()) {
            StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] Task timer expired for instance {}", instance.getInstanceId());
            instance.getState().setNodeResult("failed");
            instance.evaluateAutoTransitions();
            return;
        }
        
        // Check stealth detection
        if (node.getBoolean("stealth_required", false)) {
            // TODO: Check if players are detected
            // StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Stealth check tick..."); 
        }
        
        // Check objective completion
        // TODO: Implement objective tracking
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] onAction: player={}, action={}, data={}", 
            player != null ? player.getName().getString() : "null", action, data);
            
        switch (action) {
            case "objective_complete" -> {
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Objective completed via action");
                // Mark specific objective complete
                // Check if all objectives are done
                // If so, set result to success
            }
            case "item_collected" -> {
                 StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Item collected action received");
                // Handle item collection
            }
            case "location_reached" -> {
                 StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Location reached action received");
                // Handle location objectives
            }
            default -> StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] Unknown action: {}", action);
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] onExit: Stopping timers and cleanup");
        // Stop timer
        instance.getState().stopTimer("task_timer");
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        String result = instance.getState().getLastDialogueChoice();
        return "success".equals(result) || "failed".equals(result);
    }
    
    private List<TaskObjective> parseObjectives(StageNode node) {
        List<TaskObjective> objectives = new ArrayList<>();
        
        JsonObject data = node.getData();
        if (data.has("objectives")) {
            JsonArray objectivesArray = data.getAsJsonArray("objectives");
            for (JsonElement elem : objectivesArray) {
                JsonObject obj = elem.getAsJsonObject();
                String type = obj.get("type").getAsString();
                objectives.add(new TaskObjective(type, obj));
            }
        }
        
        return objectives;
    }
    
    public record TaskObjective(String type, JsonObject data) {}
}
