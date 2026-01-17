package com.warmpixel.storyadventure.instance;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageGraph;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.core.graph.StageEdge;
import com.warmpixel.storyadventure.core.graph.NodeType;
import com.warmpixel.storyadventure.core.waypoint.TriggerBox;
import com.warmpixel.storyadventure.core.waypoint.Waypoint;
import com.warmpixel.storyadventure.core.action.NodeAction;
import net.minecraft.world.phys.Vec3;
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
        
        // Initialize death tracking
        state.getMetadata().addProperty("team_deaths", 0);
        state.getMetadata().addProperty("max_team_deaths", graph.getMaxTeamDeaths());
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
     * Get the current team death count.
     */
    public int getTeamDeaths() {
        return state.getMetadata().has("team_deaths") ? 
            state.getMetadata().get("team_deaths").getAsInt() : 0;
    }
    
    /**
     * Get the maximum allowed team deaths before failure.
     */
    public int getMaxTeamDeaths() {
        return state.getMetadata().has("max_team_deaths") ? 
            state.getMetadata().get("max_team_deaths").getAsInt() : 15;
    }
    
    /**
     * Get remaining lives (max - current deaths).
     */
    public int getRemainingLives() {
        return Math.max(0, getMaxTeamDeaths() - getTeamDeaths());
    }
    
    /**
     * Increment the team death counter and check for failure.
     * 
     * @return true if the instance has now failed due to exceeding death limit
     */
    public boolean incrementDeathCount() {
        int currentDeaths = getTeamDeaths();
        int maxDeaths = getMaxTeamDeaths();
        currentDeaths++;
        state.getMetadata().addProperty("team_deaths", currentDeaths);
        
        StoryAdventureMod.LOGGER.info("[Instance] Team death #{} / {} for instance {}", 
            currentDeaths, maxDeaths, instanceId);
        
        if (currentDeaths >= maxDeaths) {
            StoryAdventureMod.LOGGER.warn("[Instance] Instance {} has exceeded death limit ({}/{}), triggering failure!",
 instanceId, currentDeaths, maxDeaths);
            fail();
            return true;
        }
        
        return false;
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
        
        // Remove entities from previous instances that no longer exist.
        cleanupOrphanedInstanceEntities(server);
        
        // Clean up any lingering NPCs from previous runs
        try {
            String instanceTag = "instance_" + instanceId.toString();
            String npcDeleteCmd = String.format("easy_npc delete @e[tag=%s]", instanceTag);
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                npcDeleteCmd
            );
            StoryAdventureMod.LOGGER.info("[Instance.start] Cleaned up lingering NPCs for instance {}", instanceId);
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.warn("[Instance.start] Failed to cleanup lingering NPCs: {}", e.getMessage());
        }
            
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
                            
                            // Clear law status for the player
                            String clearLawCmd = "law clear " + player.getName().getString();
                            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), clearLawCmd);
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
        // Show HUD for all players - DISABLED explicit auto-show.
        // HUD should be controlled by story nodes (e.g. on_enter actions)
        /*
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
        */
        
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
                // Debug log every 10 seconds for active node ticking
                if (server.getTickCount() % 200 == 0) {
                    StoryAdventureMod.LOGGER.debug("[Instance] Ticking node: {} (type: {}) for instance {}", 
                        currentNodeId, current.getType(), instanceId);
                }
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
            
            // Find target reference point for distance triggers (first waypoint in the node)
            Vec3 targetRef = null;
            if (!activeWaypoints.isEmpty()) {
                targetRef = activeWaypoints.values().iterator().next().getPosition();
            }
            
            for (TriggerBox trigger : activeTriggers.values()) {
                TriggerBox.TriggerEvent event = trigger.checkPlayer(memberId, pos, targetRef);
                
                if (event == TriggerBox.TriggerEvent.ENTER) {
                    StoryAdventureMod.LOGGER.info("[Instance] Trigger activated: player={}, trigger={}", player.getName().getString(), trigger.getId());
                    
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

    private void cleanupOrphanedInstanceEntities(MinecraftServer server) {
        var instanceIds = com.warmpixel.storyadventure.StoryAdventureMod.getInstance()
            .getInstanceManager()
            .getAllInstances()
            .stream()
            .filter(inst -> inst.getStatus() == InstanceStatus.RUNNING || inst.getStatus() == InstanceStatus.PAUSED)
            .map(Instance::getInstanceId)
            .collect(java.util.stream.Collectors.toSet());
        
        int removedCount = 0;
        for (ServerLevel level : server.getAllLevels()) {
            java.util.List<net.minecraft.world.entity.Entity> entitiesToRemove = new java.util.ArrayList<>();
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                boolean hasStoryEntityTag = entity.getTags().contains("story_entity");
                boolean hasStoryVehicleTag = entity.getTags().contains("story_vehicle");
                
                if (!hasStoryEntityTag && !hasStoryVehicleTag) {
                    continue;
                }
                
                java.util.Optional<java.util.UUID> entityInstanceId = entity.getTags().stream()
                    .filter(tag -> tag.startsWith("instance_"))
                    .map(tag -> tag.substring("instance_".length()))
                    .map(id -> {
                        try {
                            return java.util.UUID.fromString(id);
                        } catch (IllegalArgumentException ex) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .findFirst();
                
                if (entityInstanceId.isEmpty() || !instanceIds.contains(entityInstanceId.get())) {
                    entitiesToRemove.add(entity);
                }
            }
            
            for (net.minecraft.world.entity.Entity entity : entitiesToRemove) {
                try {
                    entity.ejectPassengers();
                    entity.discard();
                    removedCount++;
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.warn("[Instance.start] Failed to discard orphaned entity {}: {}", entity, e.getMessage());
                }
            }
        }
        
        if (removedCount > 0) {
            StoryAdventureMod.LOGGER.info("[Instance.start] Removed {} orphaned story entities before starting instance {}", 
                removedCount, instanceId);
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
        
        // Clean up entities
        cleanupEntities();
        cleanupOrphanedInstanceEntities(server);
        
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
                    } else if ("COIN".equals(type)) {
                        int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 1;
                        jsonBuilder.append("\"amount\":").append(amount).append(",");
                        jsonBuilder.append("\"item\":\"").append(escapeJson("numismatic-overhaul:gold_coin")).append("\"");
                    }
                    
                    jsonBuilder.append("}");
                }
            }
            
            jsonBuilder.append("]}");
            String victoryJson = jsonBuilder.toString();
            
            // Clear waypoints
            activeWaypoints.clear();
            
            // Send to all party members
            for (UUID memberId : party.getMembers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player != null) {
                    // Hide HUD first
                    com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                        player, 
                        com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_HIDE, 
                        ""
                    );
                    
                    // Clear waypoint indicators
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
                        new com.warmpixel.storyadventure.network.SyncWaypointsPayload(java.util.List.of()));
                    
                    // Give actual rewards
                    giveRewards(player);

                    // Show victory screen
                    com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                        player, 
                        com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_VICTORY, 
                        victoryJson
                    );
                }
            }
        }
    }
    
    private void giveRewards(ServerPlayer player) {
        StageNode currentNode = getCurrentNode();
        if (currentNode == null || !currentNode.getData().has("rewards")) return;
        
        var rewardsArray = currentNode.getData().getAsJsonArray("rewards");
        for (var rewardElem : rewardsArray) {
            var reward = rewardElem.getAsJsonObject();
            String type = reward.has("type") ? reward.get("type").getAsString() : "ITEM";
            
            if ("EXPERIENCE".equals(type)) {
                int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 0;
                player.giveExperiencePoints(amount);
            } else if ("ITEM".equals(type) || "COIN".equals(type)) {
                String itemId = "minecraft:diamond";
                int count = 1;
                
                if ("COIN".equals(type)) {
                    itemId = "numismatic-overhaul:gold_coin";
                    count = reward.has("amount") ? reward.get("amount").getAsInt() : 1;
                } else {
                    itemId = reward.has("item") ? reward.get("item").getAsString() : "minecraft:diamond";
                    count = reward.has("count") ? reward.get("count").getAsInt() : 1;
                }
                
                try {
                    net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(itemId);
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, count);
                        if (!player.getInventory().add(stack)) {
                            player.drop(stack, false);
                        }
                    } else {
                         StoryAdventureMod.LOGGER.error("Item not found or is AIR: " + itemId);
                    }
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("Failed to give item reward: " + itemId, e);
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
        if (status == InstanceStatus.FAILED || status == InstanceStatus.COMPLETED) {
            // Already ended, don't double-process
            return;
        }
        
        status = InstanceStatus.FAILED;
        long elapsedMs = getElapsedMillis();
        StoryAdventureMod.LOGGER.info("Instance {} failed after {}ms",
            instanceId, elapsedMs);
            
        // Clean up entities
        cleanupEntities();
        cleanupOrphanedInstanceEntities(server);
        
        // Send defeat screen to all party members
        if (server != null) {
            // Build defeat data JSON
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");
            jsonBuilder.append("\"storyName\":\"").append(escapeJson(graph.getName())).append("\",");
            jsonBuilder.append("\"reason\":\"团队死亡次数超过限制\",");
            jsonBuilder.append("\"deathCount\":").append(getTeamDeaths()).append(",");
            jsonBuilder.append("\"maxDeaths\":").append(getMaxTeamDeaths()).append(",");
            jsonBuilder.append("\"rewards\":[");
            
            // Get failure rewards from graph
            JsonArray failureRewards = graph.getFailureRewards();
            if (failureRewards != null && !failureRewards.isEmpty()) {
                boolean first = true;
                for (var rewardElem : failureRewards) {
                    if (!first) jsonBuilder.append(",");
                    first = false;
                    
                    var reward = rewardElem.getAsJsonObject();
                    String type = reward.has("type") ? reward.get("type").getAsString() : "EXPERIENCE";
                    
                    jsonBuilder.append("{");
                    jsonBuilder.append("\"type\":\"").append(type).append("\",");
                    
                    if ("EXPERIENCE".equals(type)) {
                        int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 0;
                        jsonBuilder.append("\"amount\":").append(amount);
                    }
                    
                    jsonBuilder.append("}");
                }
            }
            
            jsonBuilder.append("]}");
            String defeatJson = jsonBuilder.toString();
            
            // Clear waypoints
            activeWaypoints.clear();
            
            // Get spawn location
            var spawnLoc = graph.getSpecialLocation("spawn");
            
            // Send to all party members
            for (UUID memberId : party.getMembers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player != null) {
                    // Hide HUD first
                    com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                        player, 
                        com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_HIDE, 
                        ""
                    );
                    
                    // Clear waypoint indicators
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
                        new com.warmpixel.storyadventure.network.SyncWaypointsPayload(java.util.List.of()));
                    
                    // Give failure rewards
                    giveFailureRewards(player);
                    
                    // Show defeat screen
                    com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                        player, 
                        com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_DEFEAT, 
                        defeatJson
                    );
                    
                    // Teleport to spawn after a delay (3 seconds for UI to show)
                    if (spawnLoc != null) {
                        server.execute(() -> {
                            try {
                                Thread.sleep(3000);
                                ServerPlayer p = server.getPlayerList().getPlayer(memberId);
                                if (p != null) {
                                    ResourceLocation dimRl = ResourceLocation.parse(spawnLoc.dimension());
                                    ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimRl);
                                    ServerLevel level = server.getLevel(dimKey);
                                    if (level != null) {
                                        p.teleportTo(level, spawnLoc.x(), spawnLoc.y(), spawnLoc.z(), spawnLoc.yaw(), spawnLoc.pitch());
                                    }
                                }
                            } catch (Exception e) {
                                StoryAdventureMod.LOGGER.error("[Instance] Failed to teleport player to spawn after defeat", e);
                            }
                        });
                    }
                }
            }
            
            // Ensure instance is terminated and mappings are cleared after defeat
            server.execute(() -> {
                try {
                    Thread.sleep(3500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                StoryAdventureMod.getInstance().getInstanceManager().cleanupInstance(instanceId);
            });
        }
    }
    
    private void giveFailureRewards(ServerPlayer player) {
        JsonArray failureRewards = graph.getFailureRewards();
        if (failureRewards == null || failureRewards.isEmpty()) return;
        
        for (var rewardElem : failureRewards) {
            var reward = rewardElem.getAsJsonObject();
            String type = reward.has("type") ? reward.get("type").getAsString() : "EXPERIENCE";
            
            if ("EXPERIENCE".equals(type)) {
                int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 0;
                player.giveExperiencePoints(amount);
                StoryAdventureMod.LOGGER.info("[Instance] Gave {} XP to {} as failure reward", amount, player.getName().getString());
            }
        }
    }
    
    /**
     * Clean up any entities spawned by this instance (tagged with instance_ID).
     */
    public void cleanupEntities() {
        if (server == null) return;
        
        String instanceTag = "instance_" + instanceId.toString();
        StoryAdventureMod.LOGGER.info("[Instance] Cleaning up entities for instance {} (tag: {})", instanceId, instanceTag);
        
        // Use Easy NPC delete command for NPCs
        String npcDeleteCmd = String.format("easy_npc delete @e[tag=%s]", instanceTag);
        try {
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput().withPermission(2),
                npcDeleteCmd
            );
            StoryAdventureMod.LOGGER.info("[Instance] Executed NPC cleanup command: {}", npcDeleteCmd);
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.warn("[Instance] Failed to execute easy_npc delete command: {}", e.getMessage());
        }
        
        // Also remove via entity discard as fallback
        for (ServerLevel level : server.getAllLevels()) {
            java.util.List<net.minecraft.world.entity.Entity> entitiesToRemove = new java.util.ArrayList<>();
            // Iterable<Entity> getAllEntities()
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity.getTags().contains(instanceTag)) {
                    entitiesToRemove.add(entity);
                }
            }
            
            for (net.minecraft.world.entity.Entity entity : entitiesToRemove) {
                try {
                    entity.ejectPassengers();
                    entity.discard(); // remove without death events
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.warn("[Instance] Failed to discard entity {}: {}", entity, e.getMessage());
                }
            }
            
            if (!entitiesToRemove.isEmpty()) {
                StoryAdventureMod.LOGGER.info("[Instance] Removed {} entities from {}", entitiesToRemove.size(), level.dimension().location());
            }
        }
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
        
        List<StageEdge> edges = current.getEdges();
        
        // Check all edges and find one that can transition
        for (StageEdge edge : edges) {
            if (edge.canTransition(this, null)) {
                StoryAdventureMod.LOGGER.info("[Instance.evaluateAutoTransitions] Found valid edge to {} from {}", 
                    edge.getTargetNodeId(), current.getId());
                
                // Exit current node
                exitNode(currentNodeId);
                
                // Update current node
                String previousNodeId = currentNodeId;
                currentNodeId = edge.getTargetNodeId();
                lastUpdateMillis = System.currentTimeMillis();
                
                StoryAdventureMod.LOGGER.info("[Instance.evaluateAutoTransitions] Transitioning: {} -> {}", 
                    previousNodeId, currentNodeId);
                
                // Enter new node
                enterNode(currentNodeId);
                return;
            }
        }
        
        StoryAdventureMod.LOGGER.debug("[Instance.evaluateAutoTransitions] No valid transitions found from {}", current.getId());
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
                            if (action instanceof com.warmpixel.storyadventure.core.action.SpawnNPCAction spawnAction) {
                                spawnAction.setInstanceId(instanceId);
                            } else if (action instanceof com.warmpixel.storyadventure.core.action.DespawnEntitiesAction despawnAction) {
                                despawnAction.setInstanceId(instanceId);
                            } else if (action instanceof com.warmpixel.storyadventure.core.action.CommandAction cmdAction) {
                                cmdAction.setInstanceId(instanceId);
                            }
                            
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
