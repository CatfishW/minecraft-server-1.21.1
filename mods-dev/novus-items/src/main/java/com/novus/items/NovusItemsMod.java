package com.novus.items;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NovusItemsMod implements ModInitializer {
    public static final String MOD_ID = "novus_items";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Novus Items...");
        
        // Register items
        ModItems.register();
        
        // Register flight manager tick handler
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FlightManager.tick(server);
        });
        
        // Register commands
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FlightCommands.register(dispatcher);
        });
        
        LOGGER.info("Novus Items Initialized!");
    }
}
