package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Action that spawns an NPC at a specific location.
 */
public class SpawnNPCAction implements NodeAction {
    
    private final String npcTemplate;
    private final String dimension;
    private final Vec3 position;
    private final float yaw;
    private final float pitch;
    private java.util.UUID instanceId;
    
    public SpawnNPCAction(String npcTemplate, String dimension, Vec3 position, float yaw, float pitch) {
        this.npcTemplate = npcTemplate;
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
    }
    
    public void setInstanceId(java.util.UUID instanceId) {
        this.instanceId = instanceId;
    }
    
    @Override
    public String getType() {
        return "SPAWN_NPC";
    }

    @Override
    public String getSummary() {
        return "Spawn NPC: " + npcTemplate;
    }

    @Override
    public void execute(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        
        var server = players.get(0).getServer();
        if (server == null) return;
        
        // Get the server level for spawning
        net.minecraft.server.level.ServerLevel level = null;
        for (var serverLevel : server.getAllLevels()) {
            if (serverLevel.dimension().location().toString().equals(dimension)) {
                level = serverLevel;
                break;
            }
        }
        
        if (level == null) {
            level = server.overworld();
        }
        
        // Use NPCTemplateManager API to spawn entity to get reference
        try {
            com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info("[SpawnNPCAction] Attempting api spawn for template: {} at {},{},{}", 
                npcTemplate, position.x, position.y, position.z);
            
            net.minecraft.world.entity.Entity spawnedEntity = null;
            try {
                spawnedEntity = de.markusbordihn.easynpc.config.NPCTemplateManager.spawnEntityFromTemplate(
                    level, npcTemplate, position.x, position.y, position.z
                );
            } catch (Throwable t) {
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.error("[SpawnNPCAction] API call failed: {}", t.getMessage());
            }
            
            com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info("[SpawnNPCAction] API result for {}: {}", npcTemplate, spawnedEntity);
            
            if (spawnedEntity != null) {
                // Success! Add tags
                spawnedEntity.addTag("story_enemy");
                if (instanceId != null) {
                    spawnedEntity.addTag("instance_" + instanceId.toString());
                }
                
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info(
                    "[SpawnNPCAction] Successfully spawned NPC '{}' at {},{},{} with tags", 
                    npcTemplate, position.x, position.y, position.z);
                
                // Set rotation
                spawnedEntity.setYRot(yaw);
                spawnedEntity.setXRot(pitch);
                // Force position update to apply rotation
                spawnedEntity.teleportTo(level, position.x, position.y, position.z, java.util.Set.of(), yaw, pitch);
                
            } else {
                // Fallback to command if API fails using the NEW command syntax
                StringBuilder tags = new StringBuilder("[\"story_enemy\"");
                if (instanceId != null) {
                    tags.append(",\"instance_").append(instanceId.toString()).append("\"");
                }
                tags.append("]");
                
                String nbt = String.format("{Tags:%s}", tags.toString());
                
                // Use the new syntax: easy_npc template spawn <template> <x> <y> <z> <nbt>
                // Enforce US Locale for coordinates to ensure '.' usage
                String cmd = String.format(java.util.Locale.US,
                    "easy_npc template spawn %s %.2f %.2f %.2f %s", 
                    npcTemplate, position.x, position.y, position.z, nbt
                );
                
                // Execute in the correct dimension
                String fullCmd = String.format("execute in %s run %s", dimension, cmd);
                
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info("[SpawnNPCAction] Executing fallback command: {}", fullCmd);
                
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput().withLevel(level).withPermission(2), 
                    fullCmd
                );
            }
        } catch (Exception e) {
            com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.error(
                "[SpawnNPCAction] Error spawning NPC '{}': {}", npcTemplate, e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "SPAWN_NPC");
        json.addProperty("npc_template", npcTemplate);
        json.addProperty("dimension", dimension);
        json.addProperty("x", position.x);
        json.addProperty("y", position.y);
        json.addProperty("z", position.z);
        json.addProperty("yaw", yaw);
        json.addProperty("pitch", pitch);
        return json;
    }
    
    public static SpawnNPCAction fromJson(JsonObject json) {
        String template = json.has("npc_template") ? json.get("npc_template").getAsString() : "unknown";
        String dim = json.has("dimension") ? json.get("dimension").getAsString() : "minecraft:overworld";
        double x = json.has("x") ? json.get("x").getAsDouble() : 0;
        double y = json.has("y") ? json.get("y").getAsDouble() : 64;
        double z = json.has("z") ? json.get("z").getAsDouble() : 0;
        float yaw = json.has("yaw") ? json.get("yaw").getAsFloat() : 0;
        float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 0;
        
        return new SpawnNPCAction(template, dim, new Vec3(x, y, z), yaw, pitch);
    }
}
