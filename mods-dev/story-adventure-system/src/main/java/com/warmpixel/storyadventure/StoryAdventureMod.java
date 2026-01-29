package com.warmpixel.storyadventure;

import com.warmpixel.storyadventure.command.DebugCommands;
import com.warmpixel.storyadventure.command.InstanceCommands;
import com.warmpixel.storyadventure.command.ServerUICommands;
import com.warmpixel.storyadventure.command.StoryAdminCommand;
import com.warmpixel.storyadventure.command.IndicatorCommands;
import com.warmpixel.storyadventure.command.StoryCommands;
import com.warmpixel.storyadventure.instance.InstanceManager;
import com.warmpixel.storyadventure.instance.PartyManager;
import com.warmpixel.storyadventure.item.ModItems;
import com.warmpixel.storyadventure.loader.StoryLoader;
import com.warmpixel.storyadventure.loader.StoryRegistry;
import com.warmpixel.storyadventure.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Main entrypoint for the Story Adventure System mod.
 * 
 * This mod provides a deep story-based adventure dungeon/instance system
 * with a Stage Graph (directed graph / state machine) architecture.
 */
public class StoryAdventureMod implements ModInitializer {
    public static final String MOD_ID = "storyadventure";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static StoryAdventureMod instance;
    private StoryRegistry storyRegistry;
    private InstanceManager instanceManager;
    private PartyManager partyManager;
    private StoryLoader storyLoader;
    
    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Initializing Story Adventure System...");
        
        // Register items
        ModItems.registerItems();
        
        // Initialize core systems
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("storyadventure");
        storyRegistry = new StoryRegistry();
        storyLoader = new StoryLoader(configDir.resolve("stories"), storyRegistry);
        instanceManager = new InstanceManager();
        partyManager = new PartyManager();
        
        // Register events
        com.warmpixel.storyadventure.core.event.StoryEventListener.register();
        
        // Register networking
        NetworkHandler.registerPayloadTypes();
        NetworkHandler.registerServerReceivers();
        
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            StoryCommands.register(dispatcher);
            ServerUICommands.register(dispatcher, instanceManager, partyManager);
            InstanceCommands.register(dispatcher, instanceManager, partyManager, storyRegistry);
            DebugCommands.register(dispatcher, instanceManager, storyRegistry);
            StoryAdminCommand.register(dispatcher);
            IndicatorCommands.register(dispatcher);
        });

        // Server tick event
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var instance : instanceManager.getAllInstances()) {
                instance.tick();
            }
            partyManager.tick(server);
        });
        
        // Server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Clean up orphaned story entities from previous server sessions
            // These have "story_entity" tag but instances are gone after restart
            LOGGER.info("Cleaning up orphaned story entities from previous sessions...");
            try {
                String cleanupCmd = "kill @e[tag=story_entity]";
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    cleanupCmd
                );
                // Also clean up story_enemy entities
                String enemyCleanupCmd = "kill @e[tag=story_enemy]";
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    enemyCleanupCmd
                );
                // Also clean up with easy_npc command for NPC entities
                String npcCleanupCmd = "easy_npc delete @e[tag=story_entity]";
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    npcCleanupCmd
                );
                // Also clean up story_enemy NPCs
                String enemyNpcCleanupCmd = "easy_npc delete @e[tag=story_enemy]";
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    enemyNpcCleanupCmd
                );
                LOGGER.info("Orphaned story entity cleanup complete");
            } catch (Exception e) {
                LOGGER.warn("Failed to cleanup orphaned story entities: {}", e.getMessage());
            }
            
            LOGGER.info("Loading story definitions...");
            storyLoader.loadAllStories();
            LOGGER.info("Loaded {} stories", storyRegistry.getStoryCount());
            
            // Initialize trigger box manager
            var triggerManager = new com.warmpixel.storyadventure.core.waypoint.TriggerBoxManager(
                FabricLoader.getInstance().getConfigDir().resolve("storyadventure"));
            triggerManager.load();
            LOGGER.info("Loaded {} global trigger boxes", triggerManager.getBoxCount());
            
            // Initialize waypoint manager
            var waypointManager = new com.warmpixel.storyadventure.core.waypoint.WaypointManager(
                FabricLoader.getInstance().getConfigDir().resolve("storyadventure"));
            waypointManager.load();
            LOGGER.info("Loaded {} global waypoints", waypointManager.getWaypointCount());
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Cleaning up instance entities before shutdown...");
            for (var instance : instanceManager.getAllInstances()) {
                try {
                    instance.cleanupBossBars();
                    instance.cleanupEntities();
                } catch (Exception e) {
                    LOGGER.error("[Shutdown] Failed to cleanup entities for instance {}", instance.getInstanceId(), e);
                }
            }
            
            LOGGER.info("Saving instance states...");
            instanceManager.saveAllInstances();
        });
        
        // Player connection events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Sync story list to joined player
            NetworkHandler.syncStoryList(handler.getPlayer(), storyRegistry);
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // Handle player disconnect from instances
            instanceManager.handlePlayerDisconnect(handler.getPlayer().getUUID());
        });
        
        // Player respawn logic (Checkpoint respawning)
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            var instance = instanceManager.getPlayerInstance(newPlayer.getUUID());
            if (instance != null && instance.getStatus() == com.warmpixel.storyadventure.instance.Instance.InstanceStatus.RUNNING) {
                String lastCheckpointId = instance.getState().getLastCheckpointId();
                if (lastCheckpointId != null) {
                    var checkpoint = instance.getState().getCheckpoint(lastCheckpointId);
                    if (checkpoint != null) {
                        LOGGER.info("[Respawn] Teleporting player {} to last checkpoint {}", newPlayer.getName().getString(), lastCheckpointId);
                        
                        // Parse dimension
                        try {
                            net.minecraft.resources.ResourceLocation dimLoc = net.minecraft.resources.ResourceLocation.tryParse(checkpoint.getDimension());
                            if (dimLoc != null) {
                                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey = 
                                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc);
                                net.minecraft.server.level.ServerLevel targetLevel = newPlayer.getServer().getLevel(dimKey);
                                
                                if (targetLevel != null) {
                                    newPlayer.teleportTo(targetLevel, checkpoint.getX(), checkpoint.getY(), checkpoint.getZ(), 
                                        checkpoint.getYaw(), checkpoint.getPitch());
                                } else {
                                    newPlayer.teleportTo(checkpoint.getX(), checkpoint.getY(), checkpoint.getZ());
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("[Respawn] Failed to teleport player to checkpoint", e);
                        }
                    }
                }
            }
        });
        
        LOGGER.info("Story Adventure System initialized!");
    }
    
    public static StoryAdventureMod getInstance() {
        return instance;
    }
    
    public StoryRegistry getStoryRegistry() {
        return storyRegistry;
    }
    
    public InstanceManager getInstanceManager() {
        return instanceManager;
    }
    
    public PartyManager getPartyManager() {
        return partyManager;
    }
    
    public StoryLoader getStoryLoader() {
        return storyLoader;
    }
}
