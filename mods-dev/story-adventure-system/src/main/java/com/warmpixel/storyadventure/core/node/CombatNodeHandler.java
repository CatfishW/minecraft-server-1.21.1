package com.warmpixel.storyadventure.core.node;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Handler for COMBAT nodes.
 * Supports waves, boss fights, and escape sequences.
 */
public class CombatNodeHandler implements NodeHandler {
    
    private int totalEnemiesSpawned = 0;
    private int enemiesKilled = 0;
    private final Set<UUID> spawnedEnemyUUIDs = new HashSet<>();
    private long combatStartTime = 0;
    private boolean combatStarted = false;
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String combatType = node.getString("combat_type", "WAVE");
        boolean escapeAvailable = node.getBoolean("escape_available", false);
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] onEnter: instance={}, node={}, type={}, escape={}", 
            instance.getInstanceId(), node.getId(), combatType, escapeAvailable);
        
        // Reset combat state
        totalEnemiesSpawned = 0;
        enemiesKilled = 0;
        spawnedEnemyUUIDs.clear();
        combatStarted = false;
        combatStartTime = System.currentTimeMillis();
        
        // Parse and spawn enemies from JSON
        JsonObject data = node.getData();
        if (data.has("enemies") && data.get("enemies").isJsonArray()) {
            JsonArray enemies = data.getAsJsonArray("enemies");
            
            // Get a player position to spawn around
            ServerPlayer spawnCenter = null;
            for (UUID memberId : instance.getParty().getMembers()) {
                ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
                if (player != null) {
                    spawnCenter = player;
                    break;
                }
            }
            
            if (spawnCenter == null) {
                StoryAdventureMod.LOGGER.error("[CombatNodeHandler] No players found to spawn enemies around!");
                return;
            }
            
            double centerX = spawnCenter.getX();
            double centerY = spawnCenter.getY();
            double centerZ = spawnCenter.getZ();
            
            Random random = new Random();
            
            for (var enemyElem : enemies) {
                JsonObject enemy = enemyElem.getAsJsonObject();
                String entityType = enemy.has("type") ? enemy.get("type").getAsString() : "minecraft:zombie";
                int count = enemy.has("count") ? enemy.get("count").getAsInt() : 1;
                double spawnRadius = enemy.has("spawn_radius") ? enemy.get("spawn_radius").getAsDouble() : 10.0;
                
                StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Spawning {} x {} with radius {}", count, entityType, spawnRadius);
                
                for (int i = 0; i < count; i++) {
                    // Calculate random position around player within spawn_radius
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = spawnRadius * 0.5 + random.nextDouble() * spawnRadius * 0.5; // Min 50% of radius
                    double spawnX = centerX + Math.cos(angle) * distance;
                    double spawnZ = centerZ + Math.sin(angle) * distance;
                    double spawnY = centerY;
                    
                    // Use summon command
                    String summonCmd = String.format("summon %s %.2f %.2f %.2f", 
                        entityType, spawnX, spawnY, spawnZ);
                    
                    instance.getServer().getCommands().performPrefixedCommand(
                        instance.getServer().createCommandSourceStack().withSuppressedOutput(),
                        summonCmd
                    );
                    
                    totalEnemiesSpawned++;
                }
            }
            
            StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Spawned {} enemies total", totalEnemiesSpawned);
            combatStarted = true;
            
            // Store in metadata for tracking
            instance.getState().getMetadata().addProperty("total_enemies", totalEnemiesSpawned);
            instance.getState().getMetadata().addProperty("enemies_killed", 0);
        }
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        if (!combatStarted) return;
        
        // Check for combat victory/defeat
        // For now we use a simple approach - check if combat has been going for a reasonable time
        // and enemies should be dead based on player actions
        
        // Read from metadata
        int killed = instance.getState().getMetadata().has("enemies_killed") ? 
            instance.getState().getMetadata().get("enemies_killed").getAsInt() : 0;
        int total = instance.getState().getMetadata().has("total_enemies") ? 
            instance.getState().getMetadata().get("total_enemies").getAsInt() : totalEnemiesSpawned;
        
        if (total > 0 && killed >= total) {
            StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Combat victory! All {} enemies defeated", total);
            instance.getState().setNodeResult("victory");
            instance.evaluateAutoTransitions();
            combatStarted = false;
        }
        
        // Check for player deaths (all players dead = defeat)
        boolean anyPlayerAlive = false;
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null && player.isAlive()) {
                anyPlayerAlive = true;
                break;
            }
        }
        
        if (!anyPlayerAlive) {
            StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Combat defeat! All players dead");
            instance.getState().setNodeResult("defeat");
            instance.evaluateAutoTransitions();
            combatStarted = false;
        }
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[CombatNodeHandler] onAction: player={}, action={}, data={}", 
            player != null ? player.getName().getString() : "null", action, data);
            
        switch (action) {
            case "enemy_killed" -> {
                int killed = instance.getState().getMetadata().has("enemies_killed") ? 
                    instance.getState().getMetadata().get("enemies_killed").getAsInt() : 0;
                killed++;
                instance.getState().getMetadata().addProperty("enemies_killed", killed);
                
                int total = instance.getState().getMetadata().has("total_enemies") ? 
                    instance.getState().getMetadata().get("total_enemies").getAsInt() : totalEnemiesSpawned;
                
                StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Enemy killed: {}/{}", killed, total);
                
                // Notify all players of progress
                String progress = String.format("§e[战斗] §f消灭进度: %d/%d", killed, total);
                for (UUID memberId : instance.getParty().getMembers()) {
                    ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
                    if (p != null) {
                        p.sendSystemMessage(net.minecraft.network.chat.Component.literal(progress));
                    }
                }
            }
            case "player_death" -> {
                StoryAdventureMod.LOGGER.warn("[CombatNodeHandler] Player death recorded: {}", 
                    player != null ? player.getName().getString() : "unknown");
            }
            case "escape_attempt" -> {
                if (node.getBoolean("escape_available", false)) {
                    StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Escape successful!");
                    instance.getState().setNodeResult("escaped");
                    instance.evaluateAutoTransitions();
                } else {
                    StoryAdventureMod.LOGGER.debug("[CombatNodeHandler] Escape attempt failed (not available).");
                }
            }
            default -> StoryAdventureMod.LOGGER.warn("[CombatNodeHandler] Unknown action: {}", action);
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.debug("[CombatNodeHandler] onExit: Cleaning up combat encounter");
        combatStarted = false;
        spawnedEnemyUUIDs.clear();
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return instance.getState().isCurrentNodeCompleteWith("victory") ||
               instance.getState().isCurrentNodeCompleteWith("defeat") ||
               instance.getState().isCurrentNodeCompleteWith("escaped");
    }
}

