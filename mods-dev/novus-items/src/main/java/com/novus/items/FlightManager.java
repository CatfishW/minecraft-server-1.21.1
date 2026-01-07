package com.novus.items;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player flight times and permissions.
 * Handles granting, checking, and expiring flight abilities.
 */
public class FlightManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, FlightData> playerFlightData = new ConcurrentHashMap<>();
    private static Path dataFile;
    private static int tickCounter = 0;
    private static final int SAVE_INTERVAL = 20 * 60; // Save every minute
    private static final int CHECK_INTERVAL = 20; // Check every second
    
    /**
     * Data class to store flight information for a player.
     */
    public static class FlightData {
        public long expirationTime; // -1 for permanent
        public boolean isPermanent;
        public boolean isActive = true; // Whether flight is currently toggled on
        
        public FlightData() {}
        
        public FlightData(long expirationTime, boolean isPermanent) {
            this.expirationTime = expirationTime;
            this.isPermanent = isPermanent;
        }
        
        public boolean isExpired() {
            if (isPermanent) return false;
            return System.currentTimeMillis() > expirationTime;
        }
    }
    
    /**
     * Grants flight time to a player.
     */
    public static void grantFlightTime(ServerPlayer player, FlightDuration duration) {
        UUID uuid = player.getUUID();
        
        FlightData data = playerFlightData.get(uuid);
        if (data == null) {
            data = new FlightData();
            playerFlightData.put(uuid, data);
        }

        if (duration.isPermanent()) {
            data.expirationTime = -1;
            data.isPermanent = true;
        } else {
            long currentTime = System.currentTimeMillis();
            long newExpiration = currentTime + (duration.getDurationSeconds() * 1000L);
            
            // If player already has flight time, add to existing time
            if (!data.isPermanent && !data.isExpired()) {
                long remainingTime = data.expirationTime - currentTime;
                newExpiration = currentTime + remainingTime + (duration.getDurationSeconds() * 1000L);
            }
            
            data.expirationTime = newExpiration;
            data.isPermanent = false;
        }
        
        data.isActive = true; // Always enable on grant/use
        
        // Enable flight ability
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        
        saveData();
    }

    /**
     * Toggles flight for a player.
     * Returns the new state, or null if no flight data.
     */
    public static Boolean toggleFlight(ServerPlayer player) {
        FlightData data = playerFlightData.get(player.getUUID());
        if (data == null || data.isExpired()) return null;

        data.isActive = !data.isActive;
        
        // Apply immediately
        updatePlayerFlightStatus(player, data);
        saveData();
        
        return data.isActive;
    }

    private static void updatePlayerFlightStatus(ServerPlayer player, FlightData data) {
        if (player.isCreative() || player.isSpectator()) return;

        boolean shouldFly = data != null && !data.isExpired() && data.isActive;
        
        if (player.getAbilities().mayfly != shouldFly) {
            player.getAbilities().mayfly = shouldFly;
            if (!shouldFly) {
                player.getAbilities().flying = false;
            }
            player.onUpdateAbilities();
        }
    }
    
    /**
     * Checks if a player has permanent flight.
     */
    public static boolean hasPermanentFlight(ServerPlayer player) {
        FlightData data = playerFlightData.get(player.getUUID());
        return data != null && data.isPermanent;
    }
    
    /**
     * Gets remaining flight time in seconds for a player.
     * Returns -1 for permanent, 0 if expired or no flight.
     */
    public static long getRemainingTimeSeconds(ServerPlayer player) {
        FlightData data = playerFlightData.get(player.getUUID());
        if (data == null) return 0;
        if (data.isPermanent) return -1;
        
        long remaining = (data.expirationTime - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }
    
    /**
     * Formats remaining time as a human-readable string.
     */
    public static String formatRemainingTime(long seconds) {
        if (seconds < 0) return "Permanent";
        if (seconds == 0) return "Expired";
        
        long days = seconds / (24 * 3600);
        seconds %= (24 * 3600);
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.isEmpty() || seconds > 0) sb.append(seconds).append("s");
        
        return sb.toString().trim();
    }
    
    /**
     * Tick handler - checks and manages flight expiration.
     */
    public static void tick(MinecraftServer server) {
        tickCounter++;
        
        // Initialize data file path on first tick
        if (dataFile == null) {
            dataFile = server.getWorldPath(LevelResource.ROOT).resolve("novus_flight_data.json");
            loadData();
        }
        
        // Check flight expiration every second
        if (tickCounter % CHECK_INTERVAL == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();
                FlightData data = playerFlightData.get(uuid);
                
                if (data == null) {
                    // Ensure flight is disabled for players without data
                    if (!player.isCreative() && !player.isSpectator() && player.getAbilities().mayfly) {
                        player.getAbilities().mayfly = false;
                        player.getAbilities().flying = false;
                        player.onUpdateAbilities();
                    }
                    continue;
                }
                
                if (data.isExpired()) {
                    // Flight has expired
                    playerFlightData.remove(uuid);
                    
                    if (!player.isCreative() && !player.isSpectator()) {
                        player.getAbilities().mayfly = false;
                        player.getAbilities().flying = false;
                        player.onUpdateAbilities();
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c§l✦ §eYour flight time has expired! §c§l✦"));
                    }
                } else {
                    // Enforce the current toggled state
                    updatePlayerFlightStatus(player, data);
                }
            }
        }
        
        // Save data periodically
        if (tickCounter % SAVE_INTERVAL == 0) {
            saveData();
        }
    }
    
    /**
     * Called when a player joins - restores their flight status.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        FlightData data = playerFlightData.get(player.getUUID());
        
        if (data != null && !data.isExpired()) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            
            if (data.isPermanent) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6✦ §eYou have permanent flight! §6✦"));
            } else {
                String remaining = formatRemainingTime(getRemainingTimeSeconds(player));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§a✦ §eYou have §a" + remaining + "§e of flight remaining! §a✦"));
            }
        } else if (data != null && data.isExpired()) {
            playerFlightData.remove(player.getUUID());
        }
    }
    
    /**
     * Admin function to grant flight to any player.
     */
    public static void adminGrantFlight(ServerPlayer target, FlightDuration duration) {
        grantFlightTime(target, duration);
    }
    
    /**
     * Admin function to revoke flight from a player.
     */
    public static void adminRevokeFlight(ServerPlayer target) {
        playerFlightData.remove(target.getUUID());
        target.getAbilities().mayfly = false;
        target.getAbilities().flying = false;
        target.onUpdateAbilities();
        saveData();
    }
    
    /**
     * Saves flight data to disk.
     */
    private static void saveData() {
        if (dataFile == null) return;
        
        try {
            // Convert UUID keys to strings for JSON
            Map<String, FlightData> saveData = new ConcurrentHashMap<>();
            for (Map.Entry<UUID, FlightData> entry : playerFlightData.entrySet()) {
                saveData.put(entry.getKey().toString(), entry.getValue());
            }
            
            String json = GSON.toJson(saveData);
            Files.writeString(dataFile, json);
        } catch (IOException e) {
            NovusItemsMod.LOGGER.error("Failed to save flight data", e);
        }
    }
    
    /**
     * Loads flight data from disk.
     */
    private static void loadData() {
        if (dataFile == null || !Files.exists(dataFile)) return;
        
        try {
            String json = Files.readString(dataFile);
            Map<String, FlightData> loadedData = GSON.fromJson(json, 
                new TypeToken<Map<String, FlightData>>(){}.getType());
            
            if (loadedData != null) {
                for (Map.Entry<String, FlightData> entry : loadedData.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        playerFlightData.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        NovusItemsMod.LOGGER.warn("Invalid UUID in flight data: {}", entry.getKey());
                    }
                }
            }
            
            NovusItemsMod.LOGGER.info("Loaded {} player flight records", playerFlightData.size());
        } catch (IOException e) {
            NovusItemsMod.LOGGER.error("Failed to load flight data", e);
        }
    }
}
