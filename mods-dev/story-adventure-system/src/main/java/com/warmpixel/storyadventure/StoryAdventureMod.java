package com.warmpixel.storyadventure;

import com.warmpixel.storyadventure.command.DebugCommands;
import com.warmpixel.storyadventure.command.InstanceCommands;
import com.warmpixel.storyadventure.command.ServerUICommands;
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
        
        // Register networking
        NetworkHandler.registerPayloadTypes();
        NetworkHandler.registerServerReceivers();
        
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            StoryCommands.register(dispatcher);
            ServerUICommands.register(dispatcher, instanceManager, partyManager);
            InstanceCommands.register(dispatcher, instanceManager, partyManager, storyRegistry);
            DebugCommands.register(dispatcher, instanceManager, storyRegistry);
        });

        // Server tick event
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var instance : instanceManager.getAllInstances()) {
                instance.tick();
            }
        });
        
        // Server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Loading story definitions...");
            storyLoader.loadAllStories();
            LOGGER.info("Loaded {} stories", storyRegistry.getStoryCount());
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
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
