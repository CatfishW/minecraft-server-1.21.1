package com.warmpixel.storyadventure.instance;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageGraph;
import com.warmpixel.storyadventure.loader.StoryRegistry;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all running story instances.
 */
public class InstanceManager {
    
    // Active instances
    private final Map<UUID, Instance> instances = new ConcurrentHashMap<>();
    
    // Player to instance mapping for quick lookup
    private final Map<UUID, UUID> playerInstanceMap = new ConcurrentHashMap<>();
    
    // Maximum concurrent instances (configurable)
    private int maxConcurrentInstances = 50;
    
    /**
     * Create a new instance for a story with the given party.
     */
    public Instance createInstance(StageGraph graph, Party party) {
        StoryAdventureMod.LOGGER.info("[InstanceManager] Creating instance: storyId='{}', partyId={}, memberCount={}", 
            graph.getStoryId(), party.getPartyId(), party.getMemberCount());
            
        if (instances.size() >= maxConcurrentInstances) {
            StoryAdventureMod.LOGGER.warn("[InstanceManager] FAILED: Maximum concurrent instances ({}) reached", maxConcurrentInstances);
            return null;
        }
        
        // Check if party members are already in an instance
        for (UUID memberId : party.getMembers()) {
            UUID existingInstanceId = playerInstanceMap.get(memberId);
            if (existingInstanceId != null) {
                // Validate if the instance actually exists
                if (instances.containsKey(existingInstanceId)) {
                    StoryAdventureMod.LOGGER.warn("[InstanceManager] FAILED: Player {} is already in an active instance {}", memberId, existingInstanceId);
                    return null;
                } else {
                    // Zombie entry in playerInstanceMap, clean it up
                    StoryAdventureMod.LOGGER.warn("[InstanceManager] Found zombie instance mapping for player {} -> {}. Cleaning up.", memberId, existingInstanceId);
                    playerInstanceMap.remove(memberId);
                }
            }
        }
        
        UUID instanceId = UUID.randomUUID();
        Instance instance = new Instance(instanceId, graph, party);
        
        instances.put(instanceId, instance);
        
        // Map all party members to this instance
        for (UUID memberId : party.getMembers()) {
            playerInstanceMap.put(memberId, instanceId);
        }
        
        StoryAdventureMod.LOGGER.info("[InstanceManager] Instance created successfully: instanceId={}, story='{}', party={}",
            instanceId, graph.getStoryId(), party.getPartyId());
        
        return instance;
    }
    
    /**
     * Get an instance by its ID.
     */
    public Instance getInstance(UUID instanceId) {
        return instances.get(instanceId);
    }
    
    /**
     * Get the instance a player is currently in.
     */
    public Instance getPlayerInstance(UUID playerId) {
        UUID instanceId = playerInstanceMap.get(playerId);
        return instanceId != null ? instances.get(instanceId) : null;
    }
    
    /**
     * Check if a player is in any instance.
     */
    public boolean isPlayerInInstance(UUID playerId) {
        return playerInstanceMap.containsKey(playerId);
    }
    
    /**
     * Remove a player from their current instance mapping (e.g. when finishing).
     */
    public void removePlayerFromInstance(UUID playerId) {
        UUID instanceId = playerInstanceMap.remove(playerId);
        if (instanceId != null) {
            Instance instance = instances.get(instanceId);
            if (instance != null) {
                StoryAdventureMod.LOGGER.info("[InstanceManager] Detaching player {} from tracking for instance {}", playerId, instanceId);
                
                // Check if anyone from the original party is still in the tracking map for this instance
                boolean anyoneLeft = false;
                for (UUID mappedInstanceId : playerInstanceMap.values()) {
                    if (instanceId.equals(mappedInstanceId)) {
                        anyoneLeft = true;
                        break;
                    }
                }
                
                if (!anyoneLeft) {
                    StoryAdventureMod.LOGGER.info("[InstanceManager] All players detached. Cleaning up instance {}.", instanceId);
                    cleanupInstance(instanceId);
                }
            }
        }
    }
    
    /**
     * Add a player to an existing instance (rejoin or party join).
     */
    public boolean addPlayerToInstance(UUID playerId, UUID instanceId) {
        StoryAdventureMod.LOGGER.info("[InstanceManager] Adding player {} to instance {}", playerId, instanceId);
        
        Instance instance = instances.get(instanceId);
        if (instance == null) {
            StoryAdventureMod.LOGGER.warn("[InstanceManager] FAILED: Instance {} does not exist", instanceId);
            return false;
        }
        
        // Remove from current instance if any
        removePlayerFromInstance(playerId);
        
        if (instance.getParty().addMember(playerId)) {
            playerInstanceMap.put(playerId, instanceId);
            
            // Resume if the instance was paused
            if (instance.getStatus() == Instance.InstanceStatus.PAUSED) {
                StoryAdventureMod.LOGGER.info("[InstanceManager] Resuming paused instance {}", instanceId);
                instance.resume();
            }
            
            StoryAdventureMod.LOGGER.info("[InstanceManager] Player {} successfully added to instance {}", playerId, instanceId);
            return true;
        }
        
        StoryAdventureMod.LOGGER.warn("[InstanceManager] FAILED: Could not add player {} to instance {} (Party full?)", playerId, instanceId);
        return false;
    }
    
    /**
     * Clean up a completed or abandoned instance.
     */
    public void cleanupInstance(UUID instanceId) {
        Instance instance = instances.remove(instanceId);
        if (instance != null) {
            try {
                instance.cleanupEntities();
                instance.cleanupPlayers();
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.error("[InstanceManager] Error cleaning up instance " + instanceId, e);
            }

            // Remove all player mappings
            for (UUID memberId : instance.getParty().getMembers()) {
                playerInstanceMap.remove(memberId);
                
                // Restore Xaero waypoints on client
                net.minecraft.server.level.ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
                if (player != null) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
                        com.warmpixel.storyadventure.network.XaeroWaypointPayload.end());
                }
            }
            
            StoryAdventureMod.LOGGER.info("[InstanceManager] Cleaned up instance {}", instanceId);
        } else {
             StoryAdventureMod.LOGGER.warn("[InstanceManager] Cleanup requested for non-existent instance {}", instanceId);
        }
    }
    
    /**
     * Handle player disconnect.
     */
    public void handlePlayerDisconnect(UUID playerId) {
        Instance instance = getPlayerInstance(playerId);
        if (instance != null) {
            // For now, just pause the instance if the player was the only one
            if (instance.getParty().getMemberCount() == 1) {
                instance.pause();
            }
            // Could implement timeout-based cleanup here
        }
    }
    
    /**
     * Get all active instances.
     */
    public Collection<Instance> getAllInstances() {
        return Collections.unmodifiableCollection(instances.values());
    }
    
    /**
     * Get count of active instances.
     */
    public int getActiveInstanceCount() {
        return instances.size();
    }
    
    /**
     * Save all instance states to disk (called on server shutdown).
     */
    public void saveAllInstances() {
        Path savePath = FabricLoader.getInstance().getConfigDir()
            .resolve("storyadventure").resolve("instances");
        
        try {
            Files.createDirectories(savePath);
            
            for (Instance instance : instances.values()) {
                if (instance.getStatus() == Instance.InstanceStatus.RUNNING || 
                    instance.getStatus() == Instance.InstanceStatus.PAUSED) {
                    saveInstance(instance, savePath);
                }
            }
            
            StoryAdventureMod.LOGGER.info("Saved {} instance states", instances.size());
        } catch (IOException e) {
            StoryAdventureMod.LOGGER.error("Failed to save instance states", e);
        }
    }
    
    private void saveInstance(Instance instance, Path savePath) {
        // Serialization would go here - using NBT or JSON
        // For now, just log
        StoryAdventureMod.LOGGER.debug("Would save instance {} to {}", 
            instance.getInstanceId(), savePath);
    }
    
    /**
     * Load saved instances from disk (called on server start).
     */
    public void loadSavedInstances(StoryRegistry registry) {
        Path savePath = FabricLoader.getInstance().getConfigDir()
            .resolve("storyadventure").resolve("instances");
        
        if (!Files.exists(savePath)) {
            return;
        }
        
        // Would load saved instances here
        StoryAdventureMod.LOGGER.info("Loading saved instances from {}", savePath);
    }
    
    public void setMaxConcurrentInstances(int max) {
        this.maxConcurrentInstances = max;
    }
    
    public int getMaxConcurrentInstances() {
        return maxConcurrentInstances;
    }
}
