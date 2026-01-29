package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
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
    private final List<String> tags;
    private java.util.UUID instanceId;
    private final String spawnMode; // "ABSOLUTE" or "RELATIVE_TO_PLAYER"
    private final double minDistance;
    private final double maxDistance;

    public SpawnNPCAction(String npcTemplate, String dimension, Vec3 position, float yaw, float pitch) {
        this(npcTemplate, dimension, position, yaw, pitch, List.of(), "ABSOLUTE", 0, 0);
    }
    
    public SpawnNPCAction(String npcTemplate, String dimension, Vec3 position, float yaw, float pitch, List<String> tags) {
        this(npcTemplate, dimension, position, yaw, pitch, tags, "ABSOLUTE", 0, 0);
    }

    public SpawnNPCAction(String npcTemplate, String dimension, Vec3 position, float yaw, float pitch, List<String> tags, String spawnMode, double minDistance, double maxDistance) {
        this.npcTemplate = npcTemplate;
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.tags = new ArrayList<>(tags);
        this.spawnMode = spawnMode != null ? spawnMode : "ABSOLUTE";
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
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
            double spawnX = position.x;
            double spawnY = position.y;
            double spawnZ = position.z;

            // Handle RELATIVE_TO_PLAYER mode
            if ("RELATIVE_TO_PLAYER".equals(spawnMode) && !players.isEmpty()) {
                ServerPlayer target = players.get(0); // Target the first player (usually triggerer)
                Vec3 pPos = target.position();
                
                // Try to find a valid spot
                for (int i = 0; i < 10; i++) {
                    double dist = minDistance + (maxDistance - minDistance) * level.random.nextDouble();
                    
                    // If it's an enemy, force 11-15 blocks as requested
                    boolean isEnemy = false;
                    for (String tag : tags) {
                        if (tag != null && (tag.equals("story_enemy") || tag.startsWith("enemy_"))) {
                            isEnemy = true;
                            break;
                        }
                    }
                    if (isEnemy) {
                        dist = 11.0 + level.random.nextDouble() * 4.0;
                    }

                    double angle = level.random.nextDouble() * 2 * Math.PI;
                    double tx = pPos.x + dist * Math.cos(angle);
                    double tz = pPos.z + dist * Math.sin(angle);
                    
                    // Find ground level
                    int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)tx, (int)tz);
                    
                    // New robust ground check
                    boolean valid = false;
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos checkPos = new BlockPos((int)tx, groundY + dy, (int)tz);
                        if (Math.abs(checkPos.getY() - pPos.y) > 10) continue;
                        
                        BlockState foot = level.getBlockState(checkPos);
                        BlockState head = level.getBlockState(checkPos.above());
                        BlockState ground = level.getBlockState(checkPos.below());
                        FluidState fluid = level.getFluidState(checkPos);
                        
                        if (!ground.isAir() && !ground.getFluidState().isSource() && foot.isAir() && head.isAir() && fluid.isEmpty()) {
                            spawnX = tx;
                            spawnY = checkPos.getY();
                            spawnZ = tz;
                            valid = true;
                            break;
                        }
                    }
                    if (valid) break;
                }
            }

            com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info("[SpawnNPCAction] Attempting api spawn for template: {} at {},{},{} (Mode: {})", 
                npcTemplate, spawnX, spawnY, spawnZ, spawnMode);
            
            net.minecraft.world.entity.Entity spawnedEntity = null;
            try {
                spawnedEntity = de.markusbordihn.easynpc.config.NPCTemplateManager.spawnEntityFromTemplate(
                    level, npcTemplate, spawnX, spawnY, spawnZ
                );
            } catch (Throwable t) {
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.error("[SpawnNPCAction] API call failed: {}", t.getMessage());
            }
            
            if (spawnedEntity != null) {
                // Success! Boost AI follow range for "spotting from far away"
                if (spawnedEntity instanceof net.minecraft.world.entity.LivingEntity living) {
                    var followAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
                    if (followAttr != null) {
                        followAttr.setBaseValue(100.0);
                    }
                }

                // Add tags
                List<String> resolvedTags = new ArrayList<>();
                for (String tag : tags) {
                    if (tag == null || tag.isEmpty()) continue;
                    if (instanceId != null) {
                        resolvedTags.add(tag.replace("{instance_id}", instanceId.toString()));
                    } else {
                        resolvedTags.add(tag);
                    }
                }
                if (!resolvedTags.contains("story_entity")) {
                    resolvedTags.add("story_entity");
                }
                
                // Automatically add story_enemy tag if it's an enemy based on our detection
                // This ensures they get the red outline from our Mixin and the enemy indicator.
                boolean isEnemy = false;
                for (String tag : resolvedTags) {
                    if (tag != null && (tag.equals("story_enemy") || tag.startsWith("enemy_"))) {
                        isEnemy = true;
                        break;
                    }
                }
                if (isEnemy && !resolvedTags.contains("story_enemy")) {
                    resolvedTags.add("story_enemy");
                }

                if (instanceId != null) {
                    resolvedTags.add("instance_" + instanceId.toString());
                }
                
                for (String tag : resolvedTags) {
                    spawnedEntity.addTag(tag);
                }
                
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info(
                    "[SpawnNPCAction] Successfully spawned NPC '{}' at {},{},{} with tags", 
                    npcTemplate, spawnX, spawnY, spawnZ);
                
                // Set rotation
                spawnedEntity.setYRot(yaw);
                spawnedEntity.setXRot(pitch);
                // Force position update to apply rotation
                spawnedEntity.teleportTo(level, spawnX, spawnY, spawnZ, java.util.Set.of(), yaw, pitch);
                
                // Apply glow effect for visibility (same effect as EasyNPC wand)
                // REMOVED at user request: Only combat nodes should glow
                // if (isEnemy) {
                //    spawnedEntity.setGlowingTag(true);
                // }
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.debug(
                    "[SpawnNPCAction] Applied glow effect to NPC '{}'", npcTemplate);
                
            } else {
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.error(
                    "[SpawnNPCAction] API spawn returned null for template '{}'", npcTemplate);
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
        if (!"ABSOLUTE".equals(spawnMode)) {
            json.addProperty("spawn_mode", spawnMode);
            json.addProperty("min_distance", minDistance);
            json.addProperty("max_distance", maxDistance);
        }
        if (!tags.isEmpty()) {
            var tagsArray = new com.google.gson.JsonArray();
            for (String tag : tags) {
                tagsArray.add(tag);
            }
            json.add("tags", tagsArray);
        }
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
        String spawnMode = json.has("spawn_mode") ? json.get("spawn_mode").getAsString() : "ABSOLUTE";
        double minDistance = json.has("min_distance") ? json.get("min_distance").getAsDouble() : 0;
        double maxDistance = json.has("max_distance") ? json.get("max_distance").getAsDouble() : 0;
        
        List<String> tags = new ArrayList<>();
        if (json.has("tags") && json.get("tags").isJsonArray()) {
            for (var elem : json.getAsJsonArray("tags")) {
                if (elem.isJsonPrimitive()) {
                    tags.add(elem.getAsString());
                }
            }
        }
        
        return new SpawnNPCAction(template, dim, new Vec3(x, y, z), yaw, pitch, tags, spawnMode, minDistance, maxDistance);
    }
}
