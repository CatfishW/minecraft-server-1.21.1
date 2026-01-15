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
import java.util.Map;
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
        instance.getState().getMetadata().addProperty("total_objectives", objectives.size());
        instance.getState().getMetadata().addProperty("completed_objectives", 0);
        
        // Create waypoint from node data if defined
        JsonObject data = node.getData();
        if (data.has("waypoint")) {
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
                    StoryAdventureMod.LOGGER.warn("Failed to parse waypoint color: {}", colorStr);
                }
            }
            
            var waypoint = new com.warmpixel.storyadventure.core.waypoint.Waypoint(wpId, 
                new net.minecraft.world.phys.Vec3(x, y, z));
            waypoint.setLabel(label);
            waypoint.setIcon(com.warmpixel.storyadventure.core.waypoint.Waypoint.WaypointIcon.fromId(icon));
            waypoint.setColor(color);
            waypoint.setShowDistance(true);
            
            instance.addWaypoint(waypoint);
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Created waypoint '{}' at {}, {}, {}", wpId, x, y, z);
            
            // Sync waypoints to all party members
            syncWaypointsToParty(instance);
        }
        
        // Sync HUD with task title and objectives from JSON
        String taskTitle = node.getString("title", "任务");
        String taskDescription = node.getString("description", "完成目标");
        
        // Build HUD data JSON with objectives from the task
        StringBuilder hudJson = new StringBuilder();
        hudJson.append("{");
        hudJson.append("\"title\":\"").append(escapeJson(instance.getGraph().getName())).append("\",");
        hudJson.append("\"chapter\":\"").append(escapeJson(taskTitle)).append("\",");
        hudJson.append("\"objectives\":[");
        
        for (int i = 0; i < objectives.size(); i++) {
            if (i > 0) hudJson.append(",");
            String objDesc = objectives.get(i).data().has("description") ? 
                objectives.get(i).data().get("description").getAsString() : taskDescription;
            hudJson.append("{");
            hudJson.append("\"text\":\"").append(escapeJson(objDesc)).append("\",");
            hudJson.append("\"complete\":false,");
            hudJson.append("\"current\":").append(i == 0 ? "true" : "false");
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
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
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
            // Placeholder for stealth logic
        }
        
        // Check REACH_LOCATION objectives by monitoring player positions
        int completed = instance.getState().getMetadata().get("completed_objectives").getAsInt();
        int total = instance.getState().getMetadata().get("total_objectives").getAsInt();
        
        if (completed < total) {
            List<TaskObjective> objectives = parseObjectives(node);
            
            for (int i = 0; i < objectives.size(); i++) {
                TaskObjective obj = objectives.get(i);
                String objKey = "objective_" + i + "_complete";
                
                // Skip if already completed
                if (instance.getState().getMetadata().has(objKey) && 
                    instance.getState().getMetadata().get(objKey).getAsBoolean()) {
                    continue;
                }
                
                if ("REACH_LOCATION".equals(obj.type())) {
                    double targetX = obj.data().has("target_x") ? obj.data().get("target_x").getAsDouble() : 0;
                    double targetY = obj.data().has("target_y") ? obj.data().get("target_y").getAsDouble() : 64;
                    double targetZ = obj.data().has("target_z") ? obj.data().get("target_z").getAsDouble() : 0;
                    double radius = obj.data().has("radius") ? obj.data().get("radius").getAsDouble() : 3.0;
                    
                    // Check if any party member is within radius
                    for (UUID memberId : instance.getParty().getMembers()) {
                        ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
                        if (player != null) {
                            double dx = player.getX() - targetX;
                            double dy = player.getY() - targetY;
                            double dz = player.getZ() - targetZ;
                            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                            
                            if (distance <= radius) {
                                // Location reached!
                                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Player {} reached location objective {}", 
                                    player.getName().getString(), i);
                                
                                instance.getState().getMetadata().addProperty(objKey, true);
                                int newCompleted = instance.getState().getMetadata().get("completed_objectives").getAsInt() + 1;
                                instance.getState().getMetadata().addProperty("completed_objectives", newCompleted);
                                
                                // Clear waypoint on completion
                                instance.getActiveWaypoints().clear();
                                syncWaypointsToParty(instance);
                                
                                // Notify player
                                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[任务] 已到达目标位置！"));
                                
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        // Re-check completion status
        completed = instance.getState().getMetadata().get("completed_objectives").getAsInt();
        
        if (total > 0 && completed >= total) {
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] All objectives completed for node {}", node.getId());
            instance.getState().setNodeResult("success");
            instance.evaluateAutoTransitions();
        }
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] onAction: player={}, action={}, data={}", 
            player != null ? player.getName().getString() : "null", action, data);
            
        switch (action) {
            case "objective_complete" -> {
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Objective completed via action");
                int completed = instance.getState().getMetadata().get("completed_objectives").getAsInt();
                instance.getState().getMetadata().addProperty("completed_objectives", completed + 1);
                
                // Update HUD for party
                int total = instance.getState().getMetadata().get("total_objectives").getAsInt();
                String msg = String.format("Progress: %d/%d", completed + 1, total);
                for (UUID memberId : instance.getParty().getMembers()) {
                    ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
                    if (p != null) {
                        p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a" + msg));
                    }
                }
            }
            case "item_collected" -> {
                 StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Item collected action received");
                // Auto-complete objective if it matches
                onAction(instance, node, player, "objective_complete", data);
            }
            case "location_reached" -> {
                 StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Location reached action received");
                // Auto-complete objective if it matches
                onAction(instance, node, player, "objective_complete", data);
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
