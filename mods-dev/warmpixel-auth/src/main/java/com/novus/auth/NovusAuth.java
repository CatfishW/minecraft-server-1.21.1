package com.novus.auth;

import com.novus.auth.networking.AuthNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class NovusAuth implements ModInitializer {
    public static final String MOD_ID = "novus_auth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static NovusAuth instance;
    private static MinecraftServer currentServer;
    private AuthService authService;
    private AuthManager authManager;

    @Override
    public void onInitialize() {
        instance = this;
        
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        configDir.toFile().mkdirs();
        
        SQLiteAuthStorage storage = new SQLiteAuthStorage(configDir.resolve("auth.db").toString());
        BcryptHasher hasher = new BcryptHasher();
        this.authService = new AuthServiceImpl(storage, hasher);
        this.authManager = new AuthManager();

        // Track server lifecycle for single player detection
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            currentServer = server;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            currentServer = null;
        });

        AuthEventHandler.register();
        AuthNetworking.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AuthCommands.register(dispatcher);
        });

        LOGGER.info("Novus Auth initialized.");
    }

    /**
     * Checks if authentication is required.
     * Returns false for single player mode (integrated server).
     */
    public static boolean isAuthRequired() {
        if (currentServer == null) {
            return true; // Default to requiring auth if we can't determine
        }
        // Check if it's a single player world (integrated server)
        if (currentServer.isSingleplayer()) {
            LOGGER.debug("Single player mode detected - skipping authentication");
            return false;
        }
        return true;
    }

    public static NovusAuth getInstance() {
        return instance;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }
}
