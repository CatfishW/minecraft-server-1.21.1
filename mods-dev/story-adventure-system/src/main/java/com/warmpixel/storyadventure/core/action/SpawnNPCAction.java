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
    
    public SpawnNPCAction(String npcTemplate, String dimension, Vec3 position, float yaw, float pitch) {
        this.npcTemplate = npcTemplate;
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
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
        
        // Use NPCTemplateManager API to spawn at specific position
        try {
            boolean success = de.markusbordihn.easynpc.config.NPCTemplateManager.spawnFromTemplate(
                level, npcTemplate, position.x, position.y, position.z
            );
            
            if (!success) {
                // Fallback to command if API fails
                String cmd = String.format(
                    "execute in %s positioned %.2f %.2f %.2f run easy_npc template spawn %s", 
                    dimension, position.x, position.y, position.z, npcTemplate
                );
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), 
                    cmd
                );
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info("[SpawnNPCAction] Attempted fallback command spawn for {}", npcTemplate);
            } else {
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info(
                    "[SpawnNPCAction] Successfully spawned NPC '{}' at {},{},{}", 
                    npcTemplate, position.x, position.y, position.z);
                
                // Set rotation after spawn if possible (using command as it's easier to target latest entity)
                String rotCmd = String.format(
                    "execute in %s as @e[type=easy_npc:humanoid,distance=..2,limit=1,sort=nearest] run tp @s ~ ~ ~ %.2f %.2f",
                    dimension, yaw, pitch
                );
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), 
                    rotCmd
                );
            }
        } catch (Exception e) {
            com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.error(
                "[SpawnNPCAction] Error spawning NPC '{}': {}", npcTemplate, e.getMessage());
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
