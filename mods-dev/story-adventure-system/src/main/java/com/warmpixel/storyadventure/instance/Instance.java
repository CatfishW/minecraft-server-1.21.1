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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private long timeLimitMillis;
    private long lastPlayerCheckMillis;
    private static final long PLAYER_CHECK_INTERVAL_MS = 30000; // Check every 30 seconds
    
    // Waypoints and triggers
    private final Map<String, Waypoint> activeWaypoints = new HashMap<>();
    private final Map<String, TriggerBox> activeTriggers = new HashMap<>();
    private MinecraftServer server;

    // Track enemy entity UUIDs for combat nodes (to detect deaths from any cause)
    private final Set<UUID> trackedEnemyEntities = new HashSet<>();
    private int combatCheckCooldown = 0;
    
    // Delayed tasks system
    private final List<DelayedTask> pendingTasks = new CopyOnWriteArrayList<>();
    
    private record DelayedTask(long executeAtTick, NodeAction action, List<ServerPlayer> players) {}
    
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
        this.timeLimitMillis = graph.getTimeLimitMinutes() * 60L * 1000L;
        this.lastPlayerCheckMillis = 0;
        
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

    /**
     * Get the time limit in milliseconds for this instance.
     */
    public long getTimeLimitMillis() {
        return timeLimitMillis;
    }

    /**
     * Get the remaining time in milliseconds.
     */
    public long getRemainingMillis() {
        return Math.max(0, timeLimitMillis - getElapsedMillis());
    }

    /**
     * Check if the instance has exceeded its time limit.
     */
    public boolean isTimeLimitExceeded() {
        return timeLimitMillis > 0 && getElapsedMillis() >= timeLimitMillis;
    }
    
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
        lastPlayerCheckMillis = startTimeMillis;

        StoryAdventureMod.LOGGER.info("[Instance.start] Status set to RUNNING. Start time: {}, Time limit: {} minutes",
            startTimeMillis, graph.getTimeLimitMinutes());
        
        // Remove entities from previous instances that no longer exist.
        cleanupOrphanedInstanceEntities(server);
        
        // Start Xaero Waypoint session on clients
        for (UUID memberId : party.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
                    com.warmpixel.storyadventure.network.XaeroWaypointPayload.start());
            }
        }

        // Clean up any lingering NPCs from previous runs using the correct commands
        try {
            // Delete all story_entity tagged NPCs
            String storyEntityCmd = "easy_npc delete @e[tag=story_entity]";
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                storyEntityCmd
            );
            StoryAdventureMod.LOGGER.info("[Instance.start] Executed: {}", storyEntityCmd);
            
            // Delete all story_enemy tagged NPCs
            String storyEnemyCmd = "easy_npc delete @e[tag=story_enemy]";
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                storyEnemyCmd
            );
            StoryAdventureMod.LOGGER.info("[Instance.start] Executed: {}", storyEnemyCmd);
            
            // Delete all story_vehicle tagged entities using /kill
            String storyVehicleCmd = "kill @e[tag=story_vehicle]";
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                storyVehicleCmd
            );
            StoryAdventureMod.LOGGER.info("[Instance.start] Executed: {}", storyVehicleCmd);
            
            StoryAdventureMod.LOGGER.info("[Instance.start] Cleaned up lingering NPCs and vehicles for instance {}", instanceId);
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.warn("[Instance.start] Failed to cleanup lingering entities: {}", e.getMessage());
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
        if (status == InstanceStatus.RUNNING) {
            long currentTime = System.currentTimeMillis();

            // Check time limit (every second)
            if (server.getTickCount() % 20 == 0) {
                if (isTimeLimitExceeded()) {
                    StoryAdventureMod.LOGGER.warn("[Instance] Time limit exceeded for instance {}. Failing instance.", instanceId);
                    failWithReason("副本时间已耗尽");
                    return;
                }
            }

            // Check if all players are offline (every 30 seconds)
            if (currentTime - lastPlayerCheckMillis >= PLAYER_CHECK_INTERVAL_MS) {
                lastPlayerCheckMillis = currentTime;
                if (!hasAnyOnlinePlayers()) {
                    StoryAdventureMod.LOGGER.warn("[Instance] No online players in instance {}. Failing instance.", instanceId);
                    failWithReason("所有玩家已离线");
                    return;
                }
            }

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

            // Check instance area boundary
            checkInstanceAreaBoundary();

            // Clear law status for players in instance (every 5 seconds)
            if (server.getTickCount() % 100 == 0) {
                clearLawForPlayers();
            }

            // Process delayed tasks
            processDelayedTasks();

            // Handle ally and enemy NPC AI logic
            tickNPCAI();

            // Check for enemy entity deaths (from any cause)
            checkTrackedEnemyDeaths();
        }

        lastUpdateMillis = System.currentTimeMillis();
    }

    /**
     * Track an enemy entity UUID for death monitoring.
     * Used by combat nodes to track enemies that need to be killed.
     */
    public void trackEnemyEntity(UUID entityId) {
        trackedEnemyEntities.add(entityId);
        StoryAdventureMod.LOGGER.debug("[Instance] Tracking enemy entity: {} (total tracked: {})",
            entityId, trackedEnemyEntities.size());
    }

    /**
     * Clear all tracked enemy entities.
     * Called when leaving a combat node.
     */
    public void clearTrackedEnemies() {
        trackedEnemyEntities.clear();
    }

    /**
     * Check tracked enemy entities and count removals as deaths.
     * This ensures enemies killed by any cause (fall, lava, other mobs) are counted.
     */
    private void checkTrackedEnemyDeaths() {
        if (trackedEnemyEntities.isEmpty()) return;

        // Only check every 10 ticks (0.5 seconds) to reduce overhead
        combatCheckCooldown++;
        if (combatCheckCooldown < 10) return;
        combatCheckCooldown = 0;

        // Check if we're in a combat node
        StageNode current = getCurrentNode();
        if (current == null || current.getType() != com.warmpixel.storyadventure.core.graph.NodeType.COMBAT) {
            // Not in combat, clear tracking
            trackedEnemyEntities.clear();
            return;
        }

        int deathsDetected = 0;
        Iterator<UUID> it = trackedEnemyEntities.iterator();
        while (it.hasNext()) {
            UUID entityId = it.next();
            boolean stillAlive = false;

            // Check all levels for the entity
            for (ServerLevel level : server.getAllLevels()) {
                net.minecraft.world.entity.Entity entity = level.getEntity(entityId);
                if (entity != null && entity.isAlive()) {
                    stillAlive = true;
                    break;
                }
            }

            if (!stillAlive) {
                // Entity is dead or removed, count as killed
                it.remove();
                deathsDetected++;
                StoryAdventureMod.LOGGER.info("[Instance] Tracked enemy {} died (non-player kill), counting toward combat", entityId);
            }
        }

        // If any deaths detected, trigger the combat handler
        if (deathsDetected > 0) {
            var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(current.getType());
            if (handler instanceof com.warmpixel.storyadventure.core.node.CombatNodeHandler combatHandler) {
                for (int i = 0; i < deathsDetected; i++) {
                    // Create a dummy entity reference for the action
                    // The handler checks tags, so we pass null and let it increment based on metadata
                    combatHandler.onEnemyKilledByExternalCause(this, current);
                }
            }
        }
    }

    /**
     * Check if any party member is currently online.
     */
    private boolean hasAnyOnlinePlayers() {
        if (server == null) return false;
        for (UUID memberId : party.getMembers()) {
            if (server.getPlayerList().getPlayer(memberId) != null) {
                return true;
            }
        }
        return false;
    }

    public void onAction(ServerPlayer player, String action, Object data) {
        if (status != InstanceStatus.RUNNING) return;
        
        StageNode current = getCurrentNode();
        if (current != null) {
            var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(current.getType());
            if (handler != null) {
                handler.onAction(this, current, player, action, data);
            }
        }
    }

    private void tickNPCAI() {
        if (server == null || server.getTickCount() % 10 != 0) return;
        
        List<ServerPlayer> members = new ArrayList<>();
        for (UUID memberId : party.getMembers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberId);
            if (p != null && p.isAlive() && !p.isCreative() && !p.isSpectator()) {
                members.add(p);
            }
        }
        
        if (members.isEmpty()) return;
        
        String instanceTag = "instance_" + instanceId.toString();
        
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                Set<String> tags = entity.getTags();
                if (tags.contains("ally_follow") && tags.contains(instanceTag)) {
                    if (entity instanceof Mob mob) {
                        ServerPlayer nearest = null;
                        double minDistSq = Double.MAX_VALUE;
                        
                        for (ServerPlayer player : members) {
                            if (player.level() == level) {
                                double d2 = mob.distanceToSqr(player);
                                if (d2 < minDistSq) {
                                    minDistSq = d2;
                                    nearest = player;
                                }
                            }
                        }
                        
                        if (nearest != null) {
                            if (minDistSq > 400) { // Teleport if very far (> 20 blocks)
                                mob.teleportTo(nearest.getX(), nearest.getY(), nearest.getZ());
                            } else if (minDistSq > 16) { // Move to if far (> 4 blocks)
                                mob.getNavigation().moveTo(nearest, 1.2);
                            } else if (minDistSq < 6.25) { // Stop if close (< 2.5 blocks)
                                mob.getNavigation().stop();
                            }
                        }
                    }
                } else if (tags.contains(instanceTag) && (tags.contains("story_enemy") || tags.stream().anyMatch(t -> t.startsWith("enemy_")))) {
                    if (entity instanceof Mob mob) {
                        ServerPlayer nearest = null;
                        double minDistSq = Double.MAX_VALUE;
                        
                        for (ServerPlayer player : members) {
                            if (player.level() == level && !player.isCreative() && !player.isSpectator()) {
                                double d2 = mob.distanceToSqr(player);
                                if (d2 < minDistSq) {
                                    minDistSq = d2;
                                    nearest = player;
                                }
                            }
                        }
                        
                        if (nearest != null) {
                            // Aggressively find way to player regardless of range
                            if (minDistSq > 2) { // Only move if not already on top of the player
                                mob.getNavigation().moveTo(nearest, 1.3);
                                if (mob.getTarget() != nearest) {
                                    mob.setTarget(nearest);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void processDelayedTasks() {
        if (pendingTasks.isEmpty() || server == null) return;
        
        long currentTick = server.getTickCount();
        List<DelayedTask> toExecute = new ArrayList<>();
        
        for (DelayedTask task : pendingTasks) {
            if (currentTick >= task.executeAtTick) {
                toExecute.add(task);
            }
        }
        
        if (!toExecute.isEmpty()) {
            pendingTasks.removeAll(toExecute);
            for (DelayedTask task : toExecute) {
                try {
                    task.action.execute(task.players);
                    StoryAdventureMod.LOGGER.debug("[Instance] Executed delayed task: {}", task.action.getType());
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("[Instance] Failed to execute delayed task", e);
                }
            }
        }
    }
    
    /**
     * Check if players are within the defined instance area.
     * If not, kick them out of the instance.
     */
    private void checkInstanceAreaBoundary() {
        if (server == null || server.getTickCount() % 20 != 0) return; // Check every second
        
        net.minecraft.world.phys.AABB area = graph.getInstanceArea();
        if (area == null) return;
        
        for (UUID memberId : party.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player == null) continue;
            
            // Check if player is alive and in game mode survival/adventure to be fair
            if (!player.isAlive() || player.isSpectator()) continue;
            
            if (!area.contains(player.position())) {
                StoryAdventureMod.LOGGER.warn("[Instance] Player {} is outside instance area: {}. Kicking from instance.", 
                    player.getName().getString(), player.position());
                
                // Kick logic
                kickPlayerFromInstance(player);
            }
        }
    }
    
    private void kickPlayerFromInstance(ServerPlayer player) {
        if (player == null) return;
        
        // Hide HUD and any other UI
        com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
            player, 
            com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_HIDE, 
            ""
        );
        
        // Stop BGM for the player leaving
        com.warmpixel.storyadventure.network.NetworkHandler.sendBGMStop(player, 20);
        
        // Send system message
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你离开了故事区域，已被移出副本。"));
        
        // Remove from party/instance logic
        // We use partyManager to leave, which triggers cleanup if empty
        StoryAdventureMod.getInstance().getPartyManager().leaveParty(player.getUUID());
        
        // Initial spawn teleport
        var overworld = server.overworld();
        var spawnPos = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
    }
    
    /**
     * Clear law status for all players in the instance.
     * This disables the law system during instance gameplay.
     */
    private void clearLawForPlayers() {
        if (server == null) return;
        
        for (UUID memberId : party.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                try {
                    String clearLawCmd = "law clear " + player.getName().getString();
                    server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withSuppressedOutput(),
                        clearLawCmd
                    );
                } catch (Exception e) {
                    // Silently ignore if law mod is not installed
                }
            }
        }
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
                        if (action instanceof com.warmpixel.storyadventure.core.action.SpawnNPCAction spawnAction) {
                            spawnAction.setInstanceId(instanceId);
                        } else if (action instanceof com.warmpixel.storyadventure.core.action.DespawnEntitiesAction despawnAction) {
                            despawnAction.setInstanceId(instanceId);
                        } else if (action instanceof com.warmpixel.storyadventure.core.action.CommandAction cmdAction) {
                            cmdAction.setInstanceId(instanceId);
                        }
                        action.execute(List.of(player));
                    }
                    
                    // If linked to a node, trigger transition
                    if (trigger.getLinkedNodeId() != null) {
                        transitionTo(trigger.getLinkedNodeId(), player);
                    }
                    
                } else if (event == TriggerBox.TriggerEvent.EXIT) {
                    StoryAdventureMod.LOGGER.debug("[Instance] Player {} exited trigger {}", player.getName().getString(), trigger.getId());
                    
                    for (NodeAction action : trigger.getOnExitActions()) {
                        if (action instanceof com.warmpixel.storyadventure.core.action.SpawnNPCAction spawnAction) {
                            spawnAction.setInstanceId(instanceId);
                        } else if (action instanceof com.warmpixel.storyadventure.core.action.DespawnEntitiesAction despawnAction) {
                            despawnAction.setInstanceId(instanceId);
                        } else if (action instanceof com.warmpixel.storyadventure.core.action.CommandAction cmdAction) {
                            cmdAction.setInstanceId(instanceId);
                        }
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

        cleanupBossBars();
        
        // Clean up entities immediately
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
                    
                    // Stop BGM
                    com.warmpixel.storyadventure.network.NetworkHandler.sendBGMStop(player, 40);
                    
                    // Clear waypoint indicators
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
                        new com.warmpixel.storyadventure.network.SyncWaypointsPayload(java.util.List.of()));
                    
                    // Send Xaero waypoint END signal to restore player's original waypoint set
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
                        com.warmpixel.storyadventure.network.XaeroWaypointPayload.end());
                    
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
            
            // Ensure instance is fully cleaned up and mappings are cleared after victory screen shown
            // Revert to original cleaning logic: delayed cleanup via server execute task
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
        failWithReason("团队死亡次数超过限制");
    }

    /**
     * Mark the instance as failed with a specific reason.
     */
    public void failWithReason(String reason) {
        if (status == InstanceStatus.FAILED || status == InstanceStatus.COMPLETED) {
            // Already ended, don't double-process
            return;
        }

        status = InstanceStatus.FAILED;
        long elapsedMs = getElapsedMillis();
        StoryAdventureMod.LOGGER.info("Instance {} failed after {}ms. Reason: {}",
            instanceId, elapsedMs, reason);

        cleanupBossBars();

        // Clean up entities
        cleanupEntities();
        cleanupOrphanedInstanceEntities(server);

        // Send defeat screen to all party members
        if (server != null) {
            // Build defeat data JSON
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");
            jsonBuilder.append("\"storyName\":\"").append(escapeJson(graph.getName())).append("\",");
            jsonBuilder.append("\"reason\":\"").append(escapeJson(reason)).append("\",");
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
                    
                    // Stop BGM
                    com.warmpixel.storyadventure.network.NetworkHandler.sendBGMStop(player, 40);
                    
                    // Clear waypoint indicators
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
                        new com.warmpixel.storyadventure.network.SyncWaypointsPayload(java.util.List.of()));
                    
                    // Send Xaero waypoint END signal to restore player's original waypoint set
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
                        com.warmpixel.storyadventure.network.XaeroWaypointPayload.end());
                    
                    // Give failure rewards
                    giveFailureRewards(player);
                    
                    // Show defeat screen
                    com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                        player, 
                        com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_DEFEAT, 
                        defeatJson
                    );
                }
            }
            
            // Revert to original cleaning logic: delayed cleanup and teleport
            server.execute(() -> {
                try {
                    // Wait for defeat screen to show
                    Thread.sleep(3000);
                    
                    // Teleport to world spawn for all members
                    var overworld = server.overworld();
                    var spawnPos = overworld.getSharedSpawnPos();
                    
                    for (UUID memberId : party.getMembers()) {
                        ServerPlayer p = server.getPlayerList().getPlayer(memberId);
                        if (p != null) {
                            p.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                            // Also run /spawn command to be safe
                            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withSuppressedOutput(), "spawn");
                        }
                    }
                    
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Final instance cleanup
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
     * Clean up any bossbars created for this instance.
     */
    public void cleanupBossBars() {
        if (server == null) return;

        String bossId = null;
        if (state.getMetadata().has("boss_bar_id")) {
            bossId = state.getMetadata().get("boss_bar_id").getAsString();
        }
        if (bossId == null || bossId.isBlank()) {
            bossId = "boss_" + instanceId.toString().replace("-", "_");
        }

        String removeCmd = String.format("bossbar remove %s", bossId);
        server.getCommands().performPrefixedCommand(
            server.createCommandSourceStack().withSuppressedOutput().withPermission(2),
            removeCmd
        );

        state.getMetadata().remove("boss_bar_id");
        state.getMetadata().remove("boss_entity_tag");
    }
    
    /**
     * Clean up any entities spawned by this instance (tagged with instance_ID).
     * Uses the correct commands: easy_npc delete @e[tag=story_entity] and @e[tag=story_enemy]
     */
    public void cleanupEntities() {
        if (server == null) return;
        
        String instanceTag = "instance_" + instanceId.toString();
        StoryAdventureMod.LOGGER.info("[Instance] Cleaning up entities for instance {} (tag: {})", instanceId, instanceTag);
        
        // Use Easy NPC delete commands for NPCs - delete by story_entity and story_enemy tags
        try {
            // Delete all story_entity tagged NPCs (includes friendly NPCs)
            String storyEntityCmd = "easy_npc delete @e[tag=story_entity]";
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput().withPermission(2),
                storyEntityCmd
            );
            StoryAdventureMod.LOGGER.info("[Instance] Executed: {}", storyEntityCmd);
            
            // Delete all story_enemy tagged NPCs (includes enemy NPCs)
            String storyEnemyCmd = "easy_npc delete @e[tag=story_enemy]";
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput().withPermission(2),
                storyEnemyCmd
            );
            StoryAdventureMod.LOGGER.info("[Instance] Executed: {}", storyEnemyCmd);
            
            // Also delete by instance-specific tag as fallback
            String instanceCmd = String.format("easy_npc delete @e[tag=%s]", instanceTag);
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput().withPermission(2),
                instanceCmd
            );
            StoryAdventureMod.LOGGER.info("[Instance] Executed: {}", instanceCmd);

            // Kill all story_vehicle tagged entities
            String storyVehicleCmd = "kill @e[tag=story_vehicle]";
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput().withPermission(2),
                storyVehicleCmd
            );
            StoryAdventureMod.LOGGER.info("[Instance] Executed: {}", storyVehicleCmd);
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.warn("[Instance] Failed to execute delete/kill commands: {}", e.getMessage());
        }
        
        // Also remove via entity discard as fallback
        for (ServerLevel level : server.getAllLevels()) {
            java.util.List<net.minecraft.world.entity.Entity> entitiesToRemove = new java.util.ArrayList<>();
            // Iterable<Entity> getAllEntities()
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity.getTags().contains(instanceTag) || 
                    entity.getTags().contains("story_entity") || 
                    entity.getTags().contains("story_enemy") ||
                    entity.getTags().contains("story_vehicle")) {
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
     * Clean up player states for this instance (e.g. revert game modes).
     */
    public void cleanupPlayers() {
        if (server == null) return;
        
        StoryAdventureMod.LOGGER.info("[Instance] Cleaning up player states for instance {}", instanceId);
        
        for (UUID memberId : party.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                // Ensure survival mode (back from cutscene spectator mode if necessary)
                if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR) {
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                }
                
                // Stop any client-side cutscene just in case
                com.warmpixel.storyadventure.network.NetworkHandler.sendCutsceneStop(player, instanceId.toString());

                // Final BGM stop safeguard
                com.warmpixel.storyadventure.network.NetworkHandler.sendBGMStop(player, 20);
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
     * Force a transition to a target node, bypassing all condition checks.
     * Used mainly for administrative "Skip Node" functionality.
     */
    public boolean forceTransition(String targetNodeId) {
        if (targetNodeId == null || !graph.hasNode(targetNodeId)) {
            StoryAdventureMod.LOGGER.warn("[Admin] Attempted to force transition instance {} to invalid node {}", instanceId, targetNodeId);
            return false;
        }

        StoryAdventureMod.LOGGER.info("[Admin] Forcing transition of instance {} to node {}", instanceId, targetNodeId);
        
        // Exit current node
        if (currentNodeId != null) {
            exitNode(currentNodeId);
        }
        
        // Update current node
        currentNodeId = targetNodeId;
        lastUpdateMillis = System.currentTimeMillis();
        
        // Enter new node
        enterNode(targetNodeId);
        return true;
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
                            
                            // Check for delay_ticks - schedule delayed execution if present
                            int delayTicks = actionJson.has("delay_ticks") ? actionJson.get("delay_ticks").getAsInt() : 0;
                            if (delayTicks > 0 && server != null) {
                                pendingTasks.add(new DelayedTask(
                                    server.getTickCount() + delayTicks,
                                    action,
                                    new ArrayList<>(onlinePlayers)
                                ));
                                StoryAdventureMod.LOGGER.debug("[Instance.enterNode] Scheduled action for {} ticks later: {}", 
                                    delayTicks, actionJson.get("type").getAsString());
                            } else {
                                action.execute(onlinePlayers);
                                StoryAdventureMod.LOGGER.debug("[Instance.enterNode] Executed action: {} for {} players", 
                                    actionJson.get("type").getAsString(), onlinePlayers.size());
                            }
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
            // Execute on_exit actions for all party members
            JsonObject nodeData = node.getData();
            if (nodeData.has("on_exit") && nodeData.get("on_exit").isJsonArray()) {
                var actionsArray = nodeData.getAsJsonArray("on_exit");
                
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
                                
                                // Check for delay_ticks - schedule delayed execution if present
                                int delayTicks = actionJson.has("delay_ticks") ? actionJson.get("delay_ticks").getAsInt() : 0;
                                if (delayTicks > 0 && server != null) {
                                    pendingTasks.add(new DelayedTask(
                                        server.getTickCount() + delayTicks,
                                        action,
                                        new ArrayList<>(onlinePlayers)
                                    ));
                                    StoryAdventureMod.LOGGER.debug("[Instance.exitNode] Scheduled action for {} ticks later: {}", 
                                        delayTicks, actionJson.get("type").getAsString());
                                } else {
                                    action.execute(onlinePlayers);
                                    StoryAdventureMod.LOGGER.debug("[Instance.exitNode] Executed action: {} for {} players", 
                                        actionJson.get("type").getAsString(), onlinePlayers.size());
                                }
                            } catch (Exception e) {
                                StoryAdventureMod.LOGGER.error("[Instance.exitNode] Failed to execute action", e);
                            }
                        }
                    }
                }
            }

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
        
        List<com.warmpixel.storyadventure.network.SyncWaypointsPayload.WaypointData> list = new ArrayList<>();
        for (Waypoint wp : activeWaypoints.values()) {
            list.add(new com.warmpixel.storyadventure.network.SyncWaypointsPayload.WaypointData(
                wp.getId(), wp.getLabel(), 
                wp.getPosition().x, wp.getPosition().y, wp.getPosition().z,
                wp.getIcon().getId(), wp.getColor(), wp.showsDistance()
            ));
        }
        
        com.warmpixel.storyadventure.network.SyncWaypointsPayload payload = new com.warmpixel.storyadventure.network.SyncWaypointsPayload(list);
        
        for (UUID memberId : party.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
                
                // Also sync to Xaero waypoints
                for (Waypoint wp : activeWaypoints.values()) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
                        com.warmpixel.storyadventure.network.XaeroWaypointPayload.add(
                            wp.getId(), wp.getLabel(),
                            wp.getPosition().x, wp.getPosition().y, wp.getPosition().z,
                            wp.getColor()
                        ));
                }
            }
        }
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
