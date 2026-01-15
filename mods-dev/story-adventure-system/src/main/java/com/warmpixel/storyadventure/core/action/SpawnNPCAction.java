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
        
        // NPC spawning logic will vary depending on the NPC mod being used (e.g., EasyNPC, Taterzens, etc.)
        // For now, we'll log it and implement a placeholder command-based spawn.
        var server = players.get(0).getServer();
        if (server != null) {
            // Example command: /easynpc spawn {template} {x} {y} {z}
            String cmd = String.format("easynpc spawn %s %.2f %.2f %.2f", npcTemplate, position.x, position.y, position.z);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
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
