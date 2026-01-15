package com.warmpixel.storyadventure.instance;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageGraph;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.core.graph.StageEdge;
import com.warmpixel.storyadventure.core.graph.NodeType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

import com.warmpixel.storyadventure.core.waypoint.Waypoint;
import com.warmpixel.storyadventure.core.waypoint.TriggerBox;
import com.warmpixel.storyadventure.core.action.NodeAction;

/**
 * Represents a single running instance of a story.
 * Each instance tracks its own state, party, and current position in the graph.
 */
public class Instance {
    private final UUID instanceId;
    private final String storyId;
    private final StageGraph graph;
    private final Party party;
    private final InstanceState state;
    
    private String currentNodeId;
    private InstanceStatus status;
    private long startTimeMillis;
    private long lastUpdateMillis;
    
    // Waypoints and triggers
    private final Map<String, Waypoint> activeWaypoints = new HashMap<>();
    private final Map<String, TriggerBox> activeTriggers = new HashMap<>();
    private MinecraftServer server;
    
    public enum InstanceStatus {
        CREATED, RUNNING, PAUSED, COMPLETED, FAILED
    }
    
    public Instance(UUID instanceId, StageGraph graph, Party party) {
        this.instanceId = instanceId;
        this.storyId = graph.getStoryId();
        this.graph = graph;
        this.party = party;
        this.state = new InstanceState(this);
        this.currentNodeId = graph.getEntryNodeId();
        this.status = InstanceStatus.CREATED;
        this.startTimeMillis = System.currentTimeMillis();
        this.lastUpdateMillis = startTimeMillis;
        
        // Initialize flags with defaults from graph
        for (var flag : graph.getAllFlags()) {
            state.setFlag(flag.id(), flag.defaultValue());
        }
    }
    
    // Getters
    public UUID getInstanceId() { return instanceId; }
    public String getStoryId() { return storyId; }
    public StageGraph getGraph() { return graph; }
    public Party getParty() { return party; }
    public InstanceState getState() { return state; }
    public String getCurrentNodeId() { return currentNodeId; }
    public InstanceStatus getStatus() { return status; }
    public long getStartTimeMillis() { return startTimeMillis; }
    public long getElapsedMillis() { return System.currentTimeMillis() - startTimeMillis; }
    public MinecraftServer getServer() { return server; }
    
    public StageNode getCurrentNode() {
        return graph.getNode(currentNodeId);
    }
    
    /**
     * Start the instance, transitioning to the entry node.
     */
    public void start(MinecraftServer server) {
        StoryAdventureMod.LOGGER.debug("[Instance.start] Called for instanceId={}, storyId={}, partySize={}", 
            instanceId, storyId, party.getMemberCount());

        if (status != InstanceStatus.CREATED) {
            StoryAdventureMod.LOGGER.error("[Instance.start] FAILED: Invalid status for start. Expected CREATED, got {}", status);
            throw new IllegalStateException("Cannot start instance in status: " + status);
        }
        
        status = InstanceStatus.RUNNING;
        this.server = server;
        startTimeMillis = System.currentTimeMillis();
        
        StoryAdventureMod.LOGGER.info("[Instance.start] Status set to RUNNING. Start time: {}", startTimeMillis);
            
        // Teleport players to start location if defined
        var startLoc = graph.getSpecialLocation("start");
        StoryAdventureMod.LOGGER.debug("[Instance.start] Checking for 'start' location in graph. Result: {}", startLoc);

        if (startLoc != null) {
            try {
                StoryAdventureMod.LOGGER.debug("[Instance.start] Attempting to parse dimension: {}", startLoc.dimension());
                ResourceLocation dimRl = ResourceLocation.parse(startLoc.dimension());
                ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimRl);
                
                StoryAdventureMod.LOGGER.debug("[Instance.start] Looking up level for key: {}", dimKey);
                ServerLevel level = server.getLevel(dimKey);
                
                if (level != null) {
                    StoryAdventureMod.LOGGER.info("[Instance.start] Target level found: {} ({})", level.dimension().location(), level);
                    
                    for (UUID memberId : party.getMembers()) {
                        StoryAdventureMod.LOGGER.debug("[Instance.start] Processing party member: {}", memberId);
                        ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                        
                        if (player != null) {
                            StoryAdventureMod.LOGGER.info("[Instance.start] Teleporting player '{}' ({}) to {} {} {} in {}", 
                                player.getName().getString(), memberId, startLoc.x(), startLoc.y(), startLoc.z(), startLoc.dimension());
                            
                            player.teleportTo(level, startLoc.x(), startLoc.y(), startLoc.z(), startLoc.yaw(), startLoc.pitch());
                        } else {
                            StoryAdventureMod.LOGGER.warn("[Instance.start] Player {} is offline or not found, skipping teleport.", memberId);
                        }
                    }
                    StoryAdventureMod.LOGGER.info("[Instance.start] Teleportation logic completed.");
                } else {
                    StoryAdventureMod.LOGGER.error("[Instance.start] CRITICAL: Start dimension '{}' not found/loaded on server.", startLoc.dimension());
                    throw new IllegalStateException("Start dimension not found: " + startLoc.dimension());
                }
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.error("[Instance.start] Exception occurred during teleportation sequence.", e);
                if (e instanceof IllegalStateException) throw (IllegalStateException)e;
                throw new RuntimeException("Failed to start instance due to teleportation error", e);
            }
        } else {
            StoryAdventureMod.LOGGER.info("[Instance.start] No 'start' location defined. Players will remain at current position.");
        }
        
        // Show HUD for all players
        for (UUID memberId : party.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                // Build HUD data JSON
                String hudData = String.format("{\"title\":\"%s\",\"chapter\":\"%s\"}", 
                    escapeJson(graph.getName()), "第一章");
                com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                    player, 
                    com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_SHOW, 
                    hudData
                );
            }
        }
        
        // Enter the entry node
        StoryAdventureMod.LOGGER.debug("[Instance.start] Transitioning to entry node: {}", currentNodeId);
        enterNode(currentNodeId);
    }

    public void tick() {
        if (status != InstanceStatus.RUNNING) return;

        StageNode current = getCurrentNode();
        if (current != null) {
            var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(current.getType());
            if (handler != null) {
                handler.onTick(this, current);
            }
        }
        
        // Check triggers for all party members
        checkPlayerTriggers();
        
        lastUpdateMillis = System.currentTimeMillis();
    }
    
    /**
     * Check all active triggers for player enter/exit events.
     */
    private void checkPlayerTriggers() {
        if (server == null || activeTriggers.isEmpty()) return;
        
        for (UUID memberId : party.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player == null) continue;
            
            var pos = player.position();
            
            for (TriggerBox trigger : activeTriggers.values()) {
                TriggerBox.TriggerEvent event = trigger.checkPlayer(memberId, pos);
                
                if (event == TriggerBox.TriggerEvent.ENTER) {
                    StoryAdventureMod.LOGGER.debug("[Instance] Player {} entered trigger {}", player.getName().getString(), trigger.getId());
                    
                    for (NodeAction action : trigger.getOnEnterActions()) {
                        action.execute(List.of(player));
                    }
                    
                    // If linked to a node, trigger transition
                    if (trigger.getLinkedNodeId() != null) {
                        transitionTo(trigger.getLinkedNodeId(), player);
                    }
                    
                } else if (event == TriggerBox.TriggerEvent.EXIT) {
                    StoryAdventureMod.LOGGER.debug("[Instance] Player {} exited trigger {}", player.getName().getString(), trigger.getId());
                    
                    for (NodeAction action : trigger.getOnExitActions()) {
                        action.execute(List.of(player));
                    }
                }
            }
        }
    }
    
    /**
     * Pause the instance (e.g., when all players disconnect).
     */
    public void pause() {
        if (status == InstanceStatus.RUNNING) {
            status = InstanceStatus.PAUSED;
            StoryAdventureMod.LOGGER.info("Instance {} paused", instanceId);
        }
    }
    
    /**
     * Resume a paused instance.
     */
    public void resume() {
        if (status == InstanceStatus.PAUSED) {
            status = InstanceStatus.RUNNING;
            StoryAdventureMod.LOGGER.info("Instance {} resumed", instanceId);
        }
    }
    
    /**
     * Complete the instance successfully.
     */
    public void complete() {
        status = InstanceStatus.COMPLETED;
        long elapsedMs = getElapsedMillis();
        StoryAdventureMod.LOGGER.info("Instance {} completed successfully in {}ms",
            instanceId, elapsedMs);
        
        // Send victory screen to all party members
        if (server != null) {
            // Build victory data JSON
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");
            jsonBuilder.append("\"storyName\":\"").append(escapeJson(graph.getName())).append("\",");
            jsonBuilder.append("\"completionTime\":").append(elapsedMs).append(",");
            jsonBuilder.append("\"rewards\":[");
            
            // Get rewards from current node if it has any
            StageNode currentNode = getCurrentNode();
            if (currentNode != null && currentNode.getData().has("rewards")) {
                var rewardsArray = currentNode.getData().getAsJsonArray("rewards");
                boolean first = true;
                for (var rewardElem : rewardsArray) {
                    if (!first) jsonBuilder.append(",");
                    first = false;
                    
                    var reward = rewardElem.getAsJsonObject();
                    String type = reward.has("type") ? reward.get("type").getAsString() : "ITEM";
                    
                    jsonBuilder.append("{");
                    jsonBuilder.append("\"type\":\"").append(type).append("\",");
                    
                    if ("EXPERIENCE".equals(type)) {
                        int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 0;
                        jsonBuilder.append("\"amount\":").append(amount);
                    } else if ("ITEM".equals(type)) {
                        String item = reward.has("item") ? reward.get("item").getAsString() : "minecraft:diamond";
                        int count = reward.has("count") ? reward.get("count").getAsInt() : 1;
                        jsonBuilder.append("\"item\":\"").append(escapeJson(item)).append("\",");
                        jsonBuilder.append("\"amount\":").append(count);
                    }
                    
                    jsonBuilder.append("}");
                }
            }
            
            jsonBuilder.append("]}");
            String victoryJson = jsonBuilder.toString();
            
            // Send to all party members
            for (UUID memberId : party.getMembers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player != null) {
                    com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                        player, 
                        com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_VICTORY, 
                        victoryJson
                    );
                }
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
    
    /**
     * Mark the instance as failed.
     */
    public void fail() {
        status = InstanceStatus.FAILED;
        StoryAdventureMod.LOGGER.info("Instance {} failed after {}ms",
            instanceId, getElapsedMillis());
    }
    
    /**
     * Get available outgoing edges from the current node.
     */
    public List<StageEdge> getAvailableEdges(ServerPlayer player) {
        StageNode current = getCurrentNode();
        if (current == null) return List.of();
        
        return current.getEdges().stream()
            .filter(edge -> edge.canTransition(this, player))
            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
            .collect(Collectors.toList());
    }
    
    /**
     * Attempt to transition to a target node.
     * 
     * @return true if the transition was successful
     */
    public boolean transitionTo(String targetNodeId, ServerPlayer initiator) {
        StoryAdventureMod.LOGGER.debug("[Instance.transitionTo] Request: targetNodeId={}, initiator={}", 
            targetNodeId, initiator != null ? initiator.getName().getString() : "null");
        
        if (status != InstanceStatus.RUNNING) {
            StoryAdventureMod.LOGGER.warn("[Instance.transitionTo] FAILED: Instance not running. Status={}", status);
            return false;
        }
        
        StageNode current = getCurrentNode();
        if (current == null) {
            StoryAdventureMod.LOGGER.error("[Instance.transitionTo] FAILED: Current node is null.");
            return false;
        }
        
        StoryAdventureMod.LOGGER.debug("[Instance.transitionTo] Current node: {}", current.getId());
        
        // Find a valid edge to the target
        for (StageEdge edge : current.getEdges()) {
            boolean canTransition = edge.canTransition(this, initiator);
            StoryAdventureMod.LOGGER.debug("[Instance.transitionTo] Checking edge to {}. canTransition={}", 
                edge.getTargetNodeId(), canTransition);
            
            if (edge.getTargetNodeId().equals(targetNodeId) && canTransition) {
                // Exit current node
                exitNode(currentNodeId);
                
                // Update current node
                String previousNodeId = currentNodeId;
                currentNodeId = targetNodeId;
                lastUpdateMillis = System.currentTimeMillis();
                
                StoryAdventureMod.LOGGER.info("[Instance.transitionTo] Transitioning: {} -> {}", previousNodeId, currentNodeId);
                
                // Enter new node
                enterNode(targetNodeId);
                
                return true;
            }
        }
        
        StoryAdventureMod.LOGGER.debug("[Instance.transitionTo] FAILED: No valid edge found to target {}", targetNodeId);
        return false;
    }
    
    /**
     * Evaluate auto-transitions (unconditional edges or edges that became valid).
     */
    public void evaluateAutoTransitions() {
        if (status != InstanceStatus.RUNNING) return;
        
        StageNode current = getCurrentNode();
        if (current == null) return;
        
        // Check for single unconditional edge (auto-advance)
        List<StageEdge> edges = current.getEdges();
        if (edges.size() == 1 && edges.get(0).isUnconditional()) {
            transitionTo(edges.get(0).getTargetNodeId(), null);
        }
    }
    
    /**
     * Rewind to a checkpoint.
     */
    public boolean rewindToCheckpoint(String checkpointNodeId) {
        StageNode checkpoint = graph.getNode(checkpointNodeId);
        if (checkpoint == null || checkpoint.getType() != NodeType.CHECKPOINT) {
            return false;
        }
        
        // Check if this checkpoint was reached
        if (!state.hasReachedCheckpoint(checkpointNodeId)) {
            return false;
        }
        
        // Restore state from checkpoint
        state.restoreFromCheckpoint(checkpointNodeId);
        currentNodeId = checkpointNodeId;
        status = InstanceStatus.RUNNING;
        
        StoryAdventureMod.LOGGER.info("Instance {} rewound to checkpoint {}", instanceId, checkpointNodeId);
        
        // Process the checkpoint node again
        evaluateAutoTransitions();
        
        return true;
    }
    
    private void enterNode(String nodeId) {
        StoryAdventureMod.LOGGER.debug("[Instance.enterNode] Entering node: {}", nodeId);
        
        StageNode node = graph.getNode(nodeId);
        if (node == null) {
            StoryAdventureMod.LOGGER.error("[Instance.enterNode] FAILED: Node {} not found in graph.", nodeId);
            return;
        }
        
        state.clearNodeResult();
        state.recordNodeEntry(nodeId);
        
        // Execute on_enter actions for all party members
        JsonObject nodeData = node.getData();
        if (nodeData.has("on_enter") && nodeData.get("on_enter").isJsonArray()) {
            var actionsArray = nodeData.getAsJsonArray("on_enter");
            
            // Get all online party members
            java.util.List<net.minecraft.server.level.ServerPlayer> onlinePlayers = new java.util.ArrayList<>();
            for (UUID memberId : party.getMembers()) {
                net.minecraft.server.level.ServerPlayer p = server.getPlayerList().getPlayer(memberId);
                if (p != null) {
                    onlinePlayers.add(p);
                }
            }
            
            // Execute each action for all players
            for (var actionElem : actionsArray) {
                if (actionElem.isJsonObject()) {
                    var actionJson = actionElem.getAsJsonObject();
                    var action = com.warmpixel.storyadventure.core.action.ActionFactory.fromJson(actionJson);
                    if (action != null) {
                        try {
                            action.execute(onlinePlayers);
                            StoryAdventureMod.LOGGER.debug("[Instance.enterNode] Executed action: {} for {} players", 
                                actionJson.get("type").getAsString(), onlinePlayers.size());
                        } catch (Exception e) {
                            StoryAdventureMod.LOGGER.error("[Instance.enterNode] Failed to execute action", e);
                        }
                    }
                }
            }
        }
        
        // Call NodeHandler
        NodeType type = node.getType();
        var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(type);
        
        StoryAdventureMod.LOGGER.debug("[Instance.enterNode] Node Type: {}, Handler: {}", type, handler != null ? handler.getClass().getSimpleName() : "null");
        
        if (handler != null) {
            try {
                handler.onEnter(this, node);
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.error("[Instance.enterNode] Exception in handler.onEnter for node " + nodeId, e);
            }
        } else {
             StoryAdventureMod.LOGGER.warn("[Instance.enterNode] No handler found for node type: {}", type);
        }

        // Load triggers defined in node data
        loadTriggersFromNode(node);
    }

    private void loadTriggersFromNode(StageNode node) {
        activeTriggers.clear();
        JsonObject data = node.getData();
        if (data.has("triggers") && data.get("triggers").isJsonArray()) {
            JsonArray triggersArray = data.getAsJsonArray("triggers");
            for (JsonElement elem : triggersArray) {
                if (elem.isJsonObject()) {
                    JsonObject trigJson = elem.getAsJsonObject();
                    String id = trigJson.has("id") ? trigJson.get("id").getAsString() : "trig_" + UUID.randomUUID().toString().substring(0, 8);
                    TriggerBox box = TriggerBox.fromJson(id, trigJson);
                    if (box != null) {
                        addTrigger(box);
                        StoryAdventureMod.LOGGER.debug("[Instance] Loaded trigger {} for node {}", id, node.getId());
                    }
                }
            }
        }
    }
    
    private void exitNode(String nodeId) {
        StoryAdventureMod.LOGGER.debug("[Instance.exitNode] Exiting node: {}", nodeId);
        
        StageNode node = graph.getNode(nodeId);
        if (node != null) {
            var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(node.getType());
            if (handler != null) {
                try {
                    handler.onExit(this, node);
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("[Instance.exitNode] Exception in handler.onExit for node " + nodeId, e);
                }
            }
        } else {
             StoryAdventureMod.LOGGER.warn("[Instance.exitNode] Node {} not found in graph during exit.", nodeId);
        }
        state.recordNodeExit(nodeId);
    }
    
    /**
     * Check if a player is part of this instance.
     */
    public boolean hasPlayer(UUID playerId) {
        return party.hasMember(playerId);
    }
    
    // ==================== Waypoint & Trigger API ====================
    
    /**
     * Add a waypoint to this instance.
     */
    public void addWaypoint(Waypoint waypoint) {
        activeWaypoints.put(waypoint.getId(), waypoint);
        syncWaypointsToParty();
    }
    
    /**
     * Remove a waypoint from this instance.
     */
    public void removeWaypoint(String waypointId) {
        activeWaypoints.remove(waypointId);
        syncWaypointsToParty();
    }
    
    /**
     * Add a trigger box to this instance.
     */
    public void addTrigger(TriggerBox trigger) {
        activeTriggers.put(trigger.getId(), trigger);
    }
    
    /**
     * Remove a trigger box from this instance.
     */
    public void removeTrigger(String triggerId) {
        activeTriggers.remove(triggerId);
    }
    
    /**
     * Get all active waypoints.
     */
    public Map<String, Waypoint> getActiveWaypoints() {
        return activeWaypoints;
    }
    
    /**
     * Get all active triggers.
     */
    public Map<String, TriggerBox> getActiveTriggers() {
        return activeTriggers;
    }
    
    /**
     * Clear all waypoints and triggers (e.g., on node transition).
     */
    public void clearWaypointsAndTriggers() {
        activeWaypoints.clear();
        activeTriggers.clear();
        syncWaypointsToParty();
    }
    
    /**
     * Sync waypoints to all party members via network.
     */
    private void syncWaypointsToParty() {
        if (server == null) return;
        // Network sync will be implemented when we add the payload
        // For now, this is a placeholder
    }
    
    /**
     * Set the server reference (called during start).
     */
    public void setServer(MinecraftServer server) {
        this.server = server;
    }
    
    @Override
    public String toString() {
        return String.format("Instance{id=%s, story='%s', node='%s', status=%s, players=%d}",
            instanceId, storyId, currentNodeId, status, party.getMemberCount());
    }
}
