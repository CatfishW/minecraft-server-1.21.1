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
import java.util.UUID;

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
        
        // Clear previous task state to ensure fresh start
        clearTaskState(instance, node);
        
        // Start timer if time limit is set
        if (timeLimitSeconds > 0) {
            long durationMs = timeLimitSeconds * 1000L;
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Starting task timer for {}ms", durationMs);
            instance.getState().startTimer("task_timer", durationMs);
        }
        
        // Parse and track objectives
        List<TaskObjective> objectives = parseObjectives(node);
        StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Parsed {} objectives for task {}", objectives.size(), node.getId());
        for (int i = 0; i < objectives.size(); i++) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Objective [{}]: type={}, data={}", 
                i, objectives.get(i).type(), objectives.get(i).data());
        }
        
        // Store objectives in instance state for tracking
        instance.getState().getMetadata().addProperty("total_objectives", objectives.size());
        instance.getState().getMetadata().addProperty("completed_objectives", 0);
        instance.getState().getMetadata().addProperty("task_complete", false);
        instance.getState().getMetadata().addProperty("task_failed", false);
        
        // Create waypoint from node data if defined
        createWaypointFromNodeData(instance, node);
        
        // Sync HUD with task title and objectives
        syncHudToParty(instance, node, objectives, timeLimitSeconds);
    }
    
    /**
     * Clears all previous task state for a fresh start
     */
    private void clearTaskState(Instance instance, StageNode node) {
        instance.getState().getMetadata().remove("task_complete");
        instance.getState().getMetadata().remove("task_failed");
        instance.getState().getMetadata().remove("total_objectives");
        instance.getState().getMetadata().remove("completed_objectives");
        
        // Clear individual objective flags
        List<TaskObjective> objectives = parseObjectives(node);
        for (int i = 0; i < objectives.size(); i++) {
            instance.getState().getMetadata().remove("objective_" + i + "_complete");
        }
        
        // Clear any previous node result
        try {
            instance.getState().clearNodeResult();
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] clearNodeResult not available, skipping");
        }
        
        // Clear existing waypoints
        instance.getActiveWaypoints().clear();
    }
    
    /**
     * Creates waypoint from node data if defined
     */
    private void createWaypointFromNodeData(Instance instance, StageNode node) {
        JsonObject data = node.getData();
        if (!data.has("waypoint")) {
            return;
        }
        
        JsonObject wpData = data.getAsJsonObject("waypoint");
        String wpId = wpData.has("id") ? wpData.get("id").getAsString() : "task_waypoint";
        double x = wpData.has("x") ? wpData.get("x").getAsDouble() : 0;
        double y = wpData.has("y") ? wpData.get("y").getAsDouble() : 64;
        double z = wpData.has("z") ? wpData.get("z").getAsDouble() : 0;
        String label = wpData.has("label") ? wpData.get("label").getAsString() : "目标";
        String icon = wpData.has("icon") ? wpData.get("icon").getAsString() : "objective";
        int color = 0xFFFFCC00; // Default gold
        
        if (wpData.has("color")) {
            String colorStr = wpData.get("color").getAsString();
            try {
                color = (int) Long.parseLong(colorStr.replace("0x", ""), 16);
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] Failed to parse waypoint color: {}", colorStr);
            }
        }
        
        var waypoint = new com.warmpixel.storyadventure.core.waypoint.Waypoint(wpId, 
            new net.minecraft.world.phys.Vec3(x, y, z));
        waypoint.setLabel(label);
        waypoint.setIcon(com.warmpixel.storyadventure.core.waypoint.Waypoint.WaypointIcon.fromId(icon));
        waypoint.setColor(color);
        waypoint.setShowDistance(true);
        
        instance.addWaypoint(waypoint);
        StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Created waypoint '{}' at ({}, {}, {})", wpId, x, y, z);
        
        // Sync waypoints to all party members
        syncWaypointsToParty(instance);
    }
    
    /**
     * Syncs HUD data to all party members
     */
    private void syncHudToParty(Instance instance, StageNode node, List<TaskObjective> objectives, int timeLimitSeconds) {
        String taskTitle = node.getString("title", "任务");
        String taskDescription = node.getString("description", "完成目标");
        
        // Build HUD data JSON with objectives from the task
        StringBuilder hudJson = new StringBuilder();
        hudJson.append("{");
        hudJson.append("\"title\":\"").append(escapeJson(instance.getGraph().getName())).append("\",");
        hudJson.append("\"chapter\":\"").append(escapeJson(taskTitle)).append("\",");
        hudJson.append("\"objectives\":[");
        
        int currentObjIndex = -1;
        for (int i = 0; i < objectives.size(); i++) {
            if (!isObjectiveComplete(instance, "objective_" + i + "_complete")) {
                currentObjIndex = i;
                break;
            }
        }
        
        for (int i = 0; i < objectives.size(); i++) {
            if (i > 0) hudJson.append(",");
            String objDesc = objectives.get(i).data().has("description") ? 
                objectives.get(i).data().get("description").getAsString() : taskDescription;
            
            // Add progress if it's a kill objective
            if ("KILL_ENTITY".equals(objectives.get(i).type())) {
                int currentKills = instance.getState().getMetadata().has("objective_" + i + "_kills") ? 
                    instance.getState().getMetadata().get("objective_" + i + "_kills").getAsInt() : 0;
                int required = objectives.get(i).data().has("count") ? objectives.get(i).data().get("count").getAsInt() : 1;
                if (!isObjectiveComplete(instance, "objective_" + i + "_complete")) {
                    objDesc += String.format(" (%d/%d)", currentKills, required);
                }
            }
            
            boolean isComplete = isObjectiveComplete(instance, "objective_" + i + "_complete");
            boolean isCurrent = (i == currentObjIndex) || (currentObjIndex == -1 && i == objectives.size() - 1);
            
            hudJson.append("{");
            hudJson.append("\"text\":\"").append(escapeJson(objDesc)).append("\",");
            hudJson.append("\"complete\":").append(isComplete).append(",");
            hudJson.append("\"current\":").append(isCurrent);
            hudJson.append("}");
        }
        
        hudJson.append("]");
        if (timeLimitSeconds > 0) {
            hudJson.append(",\"timer\":").append(timeLimitSeconds * 1000L);
        }
        hudJson.append("}");
        
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                    p,
                    com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_SHOW,
                    hudJson.toString()
                );
            }
        }
    }
    
    /**
     * Escapes special characters for JSON string
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Syncs waypoints to all party members
     */
    private void syncWaypointsToParty(Instance instance) {
        if (instance.getServer() == null) return;
        
        var waypoints = instance.getActiveWaypoints();
        java.util.List<com.warmpixel.storyadventure.network.SyncWaypointsPayload.WaypointData> wpList = new java.util.ArrayList<>();
        
        for (var wp : waypoints.values()) {
            wpList.add(new com.warmpixel.storyadventure.network.SyncWaypointsPayload.WaypointData(
                wp.getId(), wp.getLabel(),
                wp.getPosition().x, wp.getPosition().y, wp.getPosition().z,
                wp.getIcon().getId(), wp.getColor(), wp.showsDistance()
            ));
        }
        
        var payload = new com.warmpixel.storyadventure.network.SyncWaypointsPayload(wpList);
        
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            }
        }
        
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Synced {} waypoints to party", wpList.size());
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Debug log every second
        if (instance.getServer().getTickCount() % 200 == 0) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] onTick: node={}, complete={}, failed={}", 
                node.getId(), isTaskAlreadyComplete(instance), isTaskAlreadyFailed(instance));
        }

        // Check timer expiration
        var timer = instance.getState().getTimer("task_timer");
        if (timer != null && timer.isExpired()) {
            StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] Task timer expired for instance {}", instance.getInstanceId());
            markTaskFailed(instance, node, "Time expired");
            return;
        }
        
        // Check if task is already complete
        if (isTaskAlreadyComplete(instance)) {
            // Already complete - try to transition if we haven't yet
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Task already marked complete, ensuring transition");
            instance.evaluateAutoTransitions();
            return;
        }
        
        // Check if task is already failed
        if (isTaskAlreadyFailed(instance)) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Task already marked failed, ensuring transition");
            instance.evaluateAutoTransitions();
            return;
        }

        // Get current progress
        int completed = getCompletedObjectivesCount(instance);
        int total = getTotalObjectivesCount(instance);
        
        // Handle edge case: no objectives defined - complete immediately
        if (total == 0) {
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] No objectives defined, completing task immediately");
            markTaskComplete(instance, node);
            return;
        }
        
        // All objectives already done (shouldn't happen, but safety check)
        if (completed >= total) {
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] All {} objectives already complete, triggering transition", total);
            markTaskComplete(instance, node);
            return;
        }
        
        // Check each objective
        List<TaskObjective> objectives = parseObjectives(node);
        boolean madeProgress = false;
        
        for (int i = 0; i < objectives.size(); i++) {
            TaskObjective obj = objectives.get(i);
            String objKey = "objective_" + i + "_complete";
            
            // Skip if already completed
            if (isObjectiveComplete(instance, objKey)) {
                continue;
            }
            
            // Check objective based on type
            boolean objectiveCompleted = false;
            if (instance.getServer().getTickCount() % 100 == 0) {
                StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Checking objective {}: type={}", i, obj.type());
            }
            switch (obj.type()) {
                case "REACH_LOCATION" -> objectiveCompleted = checkReachLocationObjective(instance, node, obj, i);
                case "COLLECT_ITEM" -> objectiveCompleted = checkCollectItemObjective(instance, node, obj, i);
                case "KILL_ENTITY" -> objectiveCompleted = checkKillEntityObjective(instance, node, obj, i);
                case "INTERACT" -> objectiveCompleted = checkInteractObjective(instance, node, obj, i);
                default -> StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] Unknown objective type: {}", obj.type());
            }
            
            if (objectiveCompleted) {
                // Mark objective complete
                instance.getState().getMetadata().addProperty(objKey, true);
                completed++;
                instance.getState().getMetadata().addProperty("completed_objectives", completed);
                madeProgress = true;
                
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] ✓ Objective {} complete. Progress: {}/{}", i, completed, total);
                
                // Clear waypoints on objective completion
                instance.getActiveWaypoints().clear();
                syncWaypointsToParty(instance);
                
                // Notify party of progress
                notifyPartyProgress(instance, node, completed, total);
                
                // Check if all objectives are now complete
                if (completed >= total) {
                    markTaskComplete(instance, node);
                    return;
                }
                
                // Only process one objective per tick to avoid race conditions
                break;
            }
        }
        
        // Debug logging once per second
        if (instance.getServer().getTickCount() % 20 == 0 && !madeProgress) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Tick: node={}, progress={}/{}", 
                node.getId(), completed, total);
        }
    }
    
    /**
     * Checks if task is already marked as complete
     */
    private boolean isTaskAlreadyComplete(Instance instance) {
        if (instance.getState().getMetadata().has("task_complete")) {
            try {
                return instance.getState().getMetadata().get("task_complete").getAsBoolean();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * Checks if task is already marked as failed
     */
    private boolean isTaskAlreadyFailed(Instance instance) {
        if (instance.getState().getMetadata().has("task_failed")) {
            try {
                return instance.getState().getMetadata().get("task_failed").getAsBoolean();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * Gets the number of completed objectives
     */
    private int getCompletedObjectivesCount(Instance instance) {
        if (instance.getState().getMetadata().has("completed_objectives")) {
            try {
                return instance.getState().getMetadata().get("completed_objectives").getAsInt();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Gets the total number of objectives
     */
    private int getTotalObjectivesCount(Instance instance) {
        if (instance.getState().getMetadata().has("total_objectives")) {
            try {
                return instance.getState().getMetadata().get("total_objectives").getAsInt();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Checks if a specific objective is complete
     */
    private boolean isObjectiveComplete(Instance instance, String objKey) {
        if (instance.getState().getMetadata().has(objKey)) {
            try {
                return instance.getState().getMetadata().get(objKey).getAsBoolean();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * Checks REACH_LOCATION objective
     */
    private boolean checkReachLocationObjective(Instance instance, StageNode node, TaskObjective obj, int objectiveIndex) {
        double targetX = obj.data().has("target_x") ? obj.data().get("target_x").getAsDouble() : 0;
        double targetY = obj.data().has("target_y") ? obj.data().get("target_y").getAsDouble() : 64;
        double targetZ = obj.data().has("target_z") ? obj.data().get("target_z").getAsDouble() : 0;
        double radius = obj.data().has("radius") ? obj.data().get("radius").getAsDouble() : 5.0;

        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player == null) continue;
            
            double dx = player.getX() - targetX;
            double dy = player.getY() - targetY;
            double dz = player.getZ() - targetZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            double distance = Math.sqrt(distanceSq);
            
            // Check if within 3D spherical range
            double effectiveRadius = radius + 0.25; 
            
            if (distanceSq <= effectiveRadius * effectiveRadius) {
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] ✓ Player {} reached location for objective {}", 
                    player.getName().getString(), objectiveIndex);
                
                // Notify player
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[任务] 已到达目标位置！"));
                
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks COLLECT_ITEM objective (placeholder - implement based on your inventory system)
     */
    private boolean checkCollectItemObjective(Instance instance, StageNode node, TaskObjective obj, int objectiveIndex) {
        // This would check player inventory for specific items
        // Implement based on your item tracking system
        String itemId = obj.data().has("item_id") ? obj.data().get("item_id").getAsString() : "";
        int requiredCount = obj.data().has("count") ? obj.data().get("count").getAsInt() : 1;
        
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] COLLECT_ITEM check: item={}, count={}", itemId, requiredCount);
        
        // TODO: Implement item collection check
        return false;
    }
    
    /**
     * Checks KILL_ENTITY objective (placeholder - implement based on your combat tracking)
     */
    private boolean checkKillEntityObjective(Instance instance, StageNode node, TaskObjective obj, int objectiveIndex) {
        // This would check if required entities have been killed
        // Implement based on your combat/entity tracking system
        String entityType = obj.data().has("entity_type") ? obj.data().get("entity_type").getAsString() : "";
        int requiredKills = obj.data().has("count") ? obj.data().get("count").getAsInt() : 1;
        
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] KILL_ENTITY check: entity={}, count={}", entityType, requiredKills);
        
        // TODO: Implement kill tracking check
        return false;
    }
    
    /**
     * Checks INTERACT objective (placeholder - implement based on your interaction system)
     */
    private boolean checkInteractObjective(Instance instance, StageNode node, TaskObjective obj, int objectiveIndex) {
        // This would check if player has interacted with specific object/NPC
        String targetId = obj.data().has("target_id") ? obj.data().get("target_id").getAsString() : "";
        
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] INTERACT check: target={}", targetId);
        
        // TODO: Implement interaction check
        return false;
    }
    
    /**
     * Notifies party of progress and updates HUD
     */
    private void notifyPartyProgress(Instance instance, StageNode node, int completed, int total) {
        String msg = String.format("§a[任务] 进度: %d/%d", completed, total);
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
            }
        }
        
        // Update HUD for everyone
        List<TaskObjective> objectives = parseObjectives(node);
        int timeLimitSeconds = node.getInt("time_limit_seconds", 0);
        syncHudToParty(instance, node, objectives, timeLimitSeconds);
    }
    
    /**
     * Marks the task as complete and triggers transition
     */
    private void markTaskComplete(Instance instance, StageNode node) {
        // Prevent double-completion
        if (isTaskAlreadyComplete(instance)) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Task already marked complete, just triggering transition");
            instance.evaluateAutoTransitions();
            return;
        }
        
        int total = getTotalObjectivesCount(instance);
        StoryAdventureMod.LOGGER.info("[TaskNodeHandler] ★★★ TASK COMPLETE ★★★ Node: {} | Objectives: {}", node.getId(), total);
        
        // Set ALL completion flags for maximum compatibility
        instance.getState().getMetadata().addProperty("task_complete", true);
        instance.getState().getMetadata().addProperty("task_failed", false);
        instance.getState().setNodeResult("success");
        
        // Stop any active timers
        instance.getState().stopTimer("task_timer");
        
        // Clear waypoints
        instance.getActiveWaypoints().clear();
        syncWaypointsToParty(instance);
        
        // Notify party of completion
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a§l[任务完成] §r§a所有目标已完成！"));
            }
        }
        
        // Trigger transition using deferred execution to ensure state is saved
        instance.getServer().execute(() -> {
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] >>> Executing deferred transition for node {} <<<", node.getId());
            instance.evaluateAutoTransitions();
        });
    }
    
    /**
     * Marks the task as failed and triggers transition
     */
    private void markTaskFailed(Instance instance, StageNode node, String reason) {
        // Prevent double-failure
        if (isTaskAlreadyFailed(instance)) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Task already marked failed, just triggering transition");
            instance.evaluateAutoTransitions();
            return;
        }
        
        StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] ✗✗✗ TASK FAILED ✗✗✗ Node: {} | Reason: {}", node.getId(), reason);
        
        // Set failure flags
        instance.getState().getMetadata().addProperty("task_complete", false);
        instance.getState().getMetadata().addProperty("task_failed", true);
        instance.getState().setNodeResult("failed");
        
        // Stop any active timers
        instance.getState().stopTimer("task_timer");
        
        // Clear waypoints
        instance.getActiveWaypoints().clear();
        syncWaypointsToParty(instance);
        
        // Notify party of failure
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c§l[任务失败] §r§c" + reason));
            }
        }
        
        // Trigger transition
        instance.getServer().execute(() -> {
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] >>> Executing deferred transition for failed node {} <<<", node.getId());
            instance.evaluateAutoTransitions();
        });
    }

    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] onAction: player={}, action={}, data={}", 
            player != null ? player.getName().getString() : "null", action, data);
        
        int completed = getCompletedObjectivesCount(instance);
        int total = getTotalObjectivesCount(instance);

        switch (action) {
            case "enemy_killed" -> {
                StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Enemy killed action received for node {}", node.getId());
                // Data is the LivingEntity that died
                if (data instanceof net.minecraft.world.entity.LivingEntity entity) {
                    java.util.List<TaskObjective> objectives = parseObjectives(node);
                    boolean anyUpdate = false;
                    
                    for (int i = 0; i < objectives.size(); i++) {
                        TaskObjective obj = objectives.get(i);
                        if ("KILL_ENTITY".equals(obj.type()) && !isObjectiveComplete(instance, "objective_" + i + "_complete")) {
                            String targetType = obj.data().has("entity_type") ? obj.data().get("entity_type").getAsString() : "minecraft:zombie";
                            int required = obj.data().has("count") ? obj.data().get("count").getAsInt() : 1;
                            
                            // Check if it matches (fuzzy match for now)
                            String diedType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                            if (diedType.equals(targetType)) {
                                int currentKills = instance.getState().getMetadata().has("objective_" + i + "_kills") ? 
                                    instance.getState().getMetadata().get("objective_" + i + "_kills").getAsInt() : 0;
                                currentKills++;
                                instance.getState().getMetadata().addProperty("objective_" + i + "_kills", currentKills);
                                
                                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Kill recorded for objective {}: {}/{}", i, currentKills, required);
                                
                                if (currentKills >= required) {
                                    instance.getState().getMetadata().addProperty("objective_" + i + "_complete", true);
                                    completed++;
                                    instance.getState().getMetadata().addProperty("completed_objectives", completed);
                                    anyUpdate = true;
                                } else {
                                    // Just update HUD for kill progress
                                    notifyPartyProgress(instance, node, completed, total);
                                }
                            }
                        }
                    }
                    
                    if (anyUpdate) {
                        notifyPartyProgress(instance, node, completed, total);
                        if (completed >= total) {
                            markTaskComplete(instance, node);
                        }
                    }
                }
            }
            case "objective_complete" -> {
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Objective completed via action for node {}", node.getId());
                completed++;
                instance.getState().getMetadata().addProperty("completed_objectives", completed);
                
                // Notify party of progress
                notifyPartyProgress(instance, node, completed, total);

                // Check for completion
                if (completed >= total) {
                    markTaskComplete(instance, node);
                }
            }
            
            case "item_collected" -> {
                StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Item collected action received");
                onAction(instance, node, player, "objective_complete", data);
            }
            
            case "location_reached" -> {
                StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Location reached action received");
                onAction(instance, node, player, "objective_complete", data);
            }
            
            case "entity_killed" -> {
                StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Entity killed action received");
                onAction(instance, node, player, "objective_complete", data);
            }
            
            case "force_complete" -> {
                // Debug action to force task completion
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Force completing task for node {}", node.getId());
                instance.getState().getMetadata().addProperty("completed_objectives", total);
                markTaskComplete(instance, node);
            }
            
            case "force_fail" -> {
                // Debug action to force task failure
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Force failing task for node {}", node.getId());
                markTaskFailed(instance, node, "Forced failure");
            }
            
            case "skip" -> {
                // Skip task without marking as complete or failed
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Skipping task for node {}", node.getId());
                instance.getState().setNodeResult("skipped");
                instance.evaluateAutoTransitions();
            }
            
            default -> StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] Unknown action: {}", action);
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.info("[TaskNodeHandler] onExit: Leaving node {}", node.getId());
        
        // Stop any active timers
        instance.getState().stopTimer("task_timer");
        
        // Clear waypoints when leaving task node
        instance.getActiveWaypoints().clear();
        syncWaypointsToParty(instance);
        
        // Hide task HUD (optional - depends on your UI system)
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                // Optionally hide the HUD
                // com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                //     p,
                //     com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_HIDE,
                //     "{}"
                // );
            }
        }
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        // Check multiple indicators of task completion
        boolean taskCompleteFlag = isTaskAlreadyComplete(instance);
        boolean taskFailedFlag = isTaskAlreadyFailed(instance);
        boolean successResult = instance.getState().isCurrentNodeCompleteWith("success");
        boolean failedResult = instance.getState().isCurrentNodeCompleteWith("failed");
        
        boolean canComplete = taskCompleteFlag || taskFailedFlag || successResult || failedResult;
        
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] canComplete: taskComplete={}, taskFailed={}, successResult={}, failedResult={} => {}", 
            taskCompleteFlag, taskFailedFlag, successResult, failedResult, canComplete);
        
        return canComplete;
    }
    
    /**
     * Static method to check if task is complete (for use by condition handlers)
     */
    public static boolean isTaskComplete(Instance instance) {
        // Check explicit flag
        if (instance.getState().getMetadata().has("task_complete")) {
            try {
                if (instance.getState().getMetadata().get("task_complete").getAsBoolean()) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        // Check node result
        if (instance.getState().isCurrentNodeCompleteWith("success")) {
            return true;
        }
        
        // Check objective counts
        int completed = 0;
        int total = 0;
        
        try {
            if (instance.getState().getMetadata().has("completed_objectives")) {
                completed = instance.getState().getMetadata().get("completed_objectives").getAsInt();
            }
            if (instance.getState().getMetadata().has("total_objectives")) {
                total = instance.getState().getMetadata().get("total_objectives").getAsInt();
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return total > 0 && completed >= total;
    }
    
    /**
     * Static method to check if task is failed (for use by condition handlers)
     */
    public static boolean isTaskFailed(Instance instance) {
        // Check explicit flag
        if (instance.getState().getMetadata().has("task_failed")) {
            try {
                if (instance.getState().getMetadata().get("task_failed").getAsBoolean()) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        // Check node result
        return instance.getState().isCurrentNodeCompleteWith("failed");
    }
    
    /**
     * Parses objectives from node data
     */
    private List<TaskObjective> parseObjectives(StageNode node) {
        List<TaskObjective> objectives = new ArrayList<>();
        
        JsonObject data = node.getData();
        if (data.has("objectives")) {
            JsonArray objectivesArray = data.getAsJsonArray("objectives");
            for (JsonElement elem : objectivesArray) {
                JsonObject obj = elem.getAsJsonObject();
                String type = obj.has("type") ? obj.get("type").getAsString() : "UNKNOWN";
                objectives.add(new TaskObjective(type, obj));
            }
        }
        
        return objectives;
    }
    
    /**
     * Record for task objective data
     */
    public record TaskObjective(String type, JsonObject data) {}
}