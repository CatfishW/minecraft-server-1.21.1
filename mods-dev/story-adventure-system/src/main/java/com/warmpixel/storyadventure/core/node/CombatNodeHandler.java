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
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String combatType = node.getString("combat_type", "WAVE");
        boolean escapeAvailable = node.getBoolean("escape_available", false);
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] onEnter: instance={}, node={}, type={}, escape={}", 
            instance.getInstanceId(), node.getId(), combatType, escapeAvailable);
        
        // Reset combat state in metadata
        instance.getState().getMetadata().addProperty("combat_total", 0);
        instance.getState().getMetadata().addProperty("combat_killed", 0);
        instance.getState().getMetadata().addProperty("combat_active", true);
        instance.getState().getMetadata().addProperty("combat_start_time", System.currentTimeMillis());
        
        // Parse and spawn enemies from JSON
        JsonObject data = node.getData();
        int totalToSpawn = 0;
        
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
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = spawnRadius * 0.5 + random.nextDouble() * spawnRadius * 0.5;
                    double spawnX = centerX + Math.cos(angle) * distance;
                    double spawnZ = centerZ + Math.sin(angle) * distance;
                    
                    net.minecraft.world.level.Level level = spawnCenter.level();
                    int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int)spawnX, (int)spawnZ);
                    double spawnY = Math.max(centerY - 5, Math.min(centerY + 5, groundY)); 
                    
                    String cmd;
                    // If type has a colon (e.g. minecraft:zombie), assume it's a standard entity.
                    // If it's a template name (no colon or starts with easy_npc prefix), use the new command.
                    if (entityType.contains(":") && !entityType.toLowerCase().startsWith("easy_npc:")) {
                         cmd = String.format("summon %s %.2f %.2f %.2f {Tags:[\"story_enemy\",\"instance_%s\"]}", 
                            entityType, spawnX, spawnY, spawnZ, instance.getInstanceId().toString());
                    } else {
                         // Assume it's an NPC template
                         String nbt = String.format("{Tags:[\"story_enemy\",\"instance_%s\"]}", instance.getInstanceId().toString());
                         cmd = String.format("easy_npc template spawn %s %.2f %.2f %.2f %s", 
                            entityType, spawnX, spawnY, spawnZ, nbt);
                    }
                    
                    instance.getServer().getCommands().performPrefixedCommand(
                        instance.getServer().createCommandSourceStack().withSuppressedOutput(),
                        cmd
                    );
                    
                    totalToSpawn++;
                }
            }
        }
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Spawned {} enemies total", totalToSpawn);
        instance.getState().getMetadata().addProperty("combat_total", totalToSpawn);
        
        // Initial HUD sync
        syncHudToParty(instance, node);
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        if (!isCombatActive(instance)) return;
        
        int killed = getKilledCount(instance);
        int total = getTotalCount(instance);
        
        if (total > 0 && killed >= total) {
            markCombatVictory(instance, node);
            return;
        }
        
        // Check for player deaths
        boolean anyPlayerAlive = false;
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null && player.isAlive()) {
                anyPlayerAlive = true;
                break;
            }
        }
        
        if (!anyPlayerAlive) {
            markCombatDefeat(instance, node);
        }
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[CombatNodeHandler] onAction: player={}, action={}, data={}", 
            player != null ? player.getName().getString() : "null", action, data);
            
        switch (action) {
            case "enemy_killed" -> {
                int killed = getKilledCount(instance) + 1;
                instance.getState().getMetadata().addProperty("combat_killed", killed);
                
                int total = getTotalCount(instance);
                StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Enemy killed: {}/{}", killed, total);
                
                // Update HUD for progress
                syncHudToParty(instance, node);
                
                if (killed >= total) {
                    markCombatVictory(instance, node);
                }
            }
            case "escape_attempt" -> {
                if (node.getBoolean("escape_available", false)) {
                    StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Escape successful!");
                    instance.getState().setNodeResult("escaped");
                    instance.getState().getMetadata().addProperty("combat_active", false);
                    instance.evaluateAutoTransitions();
                }
            }
        }
    }
    
    private void markCombatVictory(Instance instance, StageNode node) {
        if (!isCombatActive(instance)) return;
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Combat victory! Node: {}", node.getId());
        instance.getState().setNodeResult("victory");
        instance.getState().getMetadata().addProperty("combat_active", false);
        
        // Notify players
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a§l[战斗胜利] §r§a所有目标已消灭！"));
            }
        }
        
        instance.evaluateAutoTransitions();
    }
    
    private void markCombatDefeat(Instance instance, StageNode node) {
        if (!isCombatActive(instance)) return;
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Combat defeat! Node: {}", node.getId());
        instance.getState().setNodeResult("defeat");
        instance.getState().getMetadata().addProperty("combat_active", false);
        instance.evaluateAutoTransitions();
    }
    
    private void syncHudToParty(Instance instance, StageNode node) {
        int killed = getKilledCount(instance);
        int total = getTotalCount(instance);
        int remaining = Math.max(0, total - killed);
        
        String title = node.getString("title", "战斗");
        String desc = node.getString("description", "消灭敌人");
        
        StringBuilder hudJson = new StringBuilder();
        hudJson.append("{");
        hudJson.append("\"title\":\"").append(escapeJson(instance.getGraph().getName())).append("\",");
        hudJson.append("\"chapter\":\"").append(escapeJson(title)).append("\",");
        hudJson.append("\"objectives\":[");
        hudJson.append("{");
        hudJson.append("\"text\":\"").append(escapeJson(desc + " (剩余: " + remaining + ")")).append("\",");
        hudJson.append("\"complete\":").append(killed >= total ? "true" : "false").append(",");
        hudJson.append("\"current\":true");
        hudJson.append("}");
        hudJson.append("]");
        hudJson.append("}");
        
        String json = hudJson.toString();
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                    p,
                    com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_SHOW,
                    json
                );
            }
        }
    }
    
    private boolean isCombatActive(Instance instance) {
        return instance.getState().getMetadata().has("combat_active") && 
               instance.getState().getMetadata().get("combat_active").getAsBoolean();
    }
    
    private int getKilledCount(Instance instance) {
        return instance.getState().getMetadata().has("combat_killed") ? 
            instance.getState().getMetadata().get("combat_killed").getAsInt() : 0;
    }
    
    private int getTotalCount(Instance instance) {
        return instance.getState().getMetadata().has("combat_total") ? 
            instance.getState().getMetadata().get("combat_total").getAsInt() : 0;
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        instance.getState().getMetadata().addProperty("combat_active", false);
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return !isCombatActive(instance);
    }
}

