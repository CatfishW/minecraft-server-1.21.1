package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;

import java.util.List;

/**
 * Action that teleports players to a location.
 */
public class TeleportAction implements NodeAction {
    
    private final String dimension;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    
    public TeleportAction(String dimension, double x, double y, double z) {
        this(dimension, x, y, z, 0, 0);
    }
    
    public TeleportAction(String dimension, double x, double y, double z, float yaw, float pitch) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
    
    @Override
    public String getType() {
        return "TELEPORT";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            try {
                ResourceLocation dimLoc = ResourceLocation.tryParse(dimension);
                if (dimLoc != null && player.getServer() != null) {
                    ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
                    ServerLevel targetLevel = player.getServer().getLevel(dimKey);
                    
                    if (targetLevel != null) {
                        player.teleportTo(targetLevel, x, y, z, yaw, pitch);
                    } else {
                        // Same dimension teleport
                        player.teleportTo(x, y, z);
                    }
                }
            } catch (Exception e) {
                // Failed teleport, log but don't crash
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "TELEPORT");
        obj.addProperty("dimension", dimension);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        if (yaw != 0) obj.addProperty("yaw", yaw);
        if (pitch != 0) obj.addProperty("pitch", pitch);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return String.format("传送: %.0f, %.0f, %.0f", x, y, z);
    }
    
    public static TeleportAction fromJson(JsonObject obj) {
        String dim = obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld";
        double x = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double y = obj.has("y") ? obj.get("y").getAsDouble() : 64;
        double z = obj.has("z") ? obj.get("z").getAsDouble() : 0;
        float yaw = obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0;
        float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0;
        return new TeleportAction(dim, x, y, z, yaw, pitch);
    }
}
