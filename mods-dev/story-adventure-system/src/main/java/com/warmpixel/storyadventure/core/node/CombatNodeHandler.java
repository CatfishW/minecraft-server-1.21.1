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
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

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

        // Clear any leftover bossbars from previous runs
        instance.cleanupBossBars();
        
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
                
                // Collect additional tags if specified
                java.util.List<String> extraTags = new java.util.ArrayList<>();
                if (enemy.has("tags") && enemy.get("tags").isJsonArray()) {
                    for (var tagElem : enemy.getAsJsonArray("tags")) {
                        extraTags.add(tagElem.getAsString());
                    }
                }
                
                StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Spawning {} x {} with radius {}", count, entityType, spawnRadius);
                
                for (int i = 0; i < count; i++) {
                    net.minecraft.server.level.ServerLevel level = spawnCenter.serverLevel();
                    double spawnX = centerX;
                    double spawnZ = centerZ;
                    double spawnY = centerY;
                    boolean foundStart = false;

                    for (int attempt = 0; attempt < 15; attempt++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        // Use 11-15 range as requested
                        double distance = 11.0 + random.nextDouble() * 4.0;
                        double tx = centerX + Math.cos(angle) * distance;
                        double tz = centerZ + Math.sin(angle) * distance;
                        
                        int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)tx, (int)tz);
                        
                        // New robust ground check
                        for (int dy = 2; dy >= -3; dy--) {
                            BlockPos checkPos = new BlockPos((int)tx, groundY + dy, (int)tz);
                            if (Math.abs(checkPos.getY() - centerY) > 10) continue;
                            
                            BlockState foot = level.getBlockState(checkPos);
                            BlockState head = level.getBlockState(checkPos.above());
                            BlockState ground = level.getBlockState(checkPos.below());
                            FluidState fluid = level.getFluidState(checkPos);
                            
                            if (!ground.isAir() && !ground.getFluidState().isSource() && foot.isAir() && head.isAir() && fluid.isEmpty()) {
                                spawnX = tx;
                                spawnY = checkPos.getY();
                                spawnZ = tz;
                                foundStart = true;
                                break;
                            }
                        }
                        if (foundStart) break;
                    }
                    
                    if (!foundStart) {
                        spawnX = centerX + (random.nextDouble() - 0.5) * 2;
                        spawnY = centerY;
                        spawnZ = centerZ + (random.nextDouble() - 0.5) * 2;
                    }
                    
                    // Collect all tags to apply
                    java.util.List<String> allTags = new java.util.ArrayList<>();
                    allTags.add("story_entity");
                    allTags.add("story_enemy");
                    allTags.add("instance_" + instance.getInstanceId().toString());
                    allTags.addAll(extraTags);
                    
                    // If type has a colon (e.g. minecraft:zombie), assume it's a standard entity.
                    // If it's a template name (no colon or starts with easy_npc prefix), use NPCTemplateManager API.
                    net.minecraft.world.entity.Entity spawnedEntity = null;
                    
                    if (entityType.contains(":") && !entityType.toLowerCase().startsWith("easy_npc:")) {
                        // Standard entity - use summon command
                        StringBuilder tagsList = new StringBuilder("[\"story_entity\",\"story_enemy\",\"instance_" + instance.getInstanceId().toString() + "\"");
                        for (String t : extraTags) {
                            tagsList.append(",\"").append(t).append("\"");
                        }
                        tagsList.append("]");
                        
                        String cmd = String.format("summon %s %.2f %.2f %.2f {Glowing:1b,Tags:%s}",
                            entityType, spawnX, spawnY, spawnZ, tagsList.toString());
                        instance.getServer().getCommands().performPrefixedCommand(
                            instance.getServer().createCommandSourceStack().withSuppressedOutput(),
                            cmd
                        );

                        // For summon command, try to find and track the entity
                        // Search nearby for the spawned entity
                        var nearbyEntities = level.getEntitiesOfClass(
                            net.minecraft.world.entity.LivingEntity.class,
                            new net.minecraft.world.phys.AABB(spawnX - 2, spawnY - 2, spawnZ - 2, spawnX + 2, spawnY + 2, spawnZ + 2),
                            e -> e.getTags().contains("story_enemy") && e.getTags().contains("instance_" + instance.getInstanceId().toString())
                        );
                        if (!nearbyEntities.isEmpty()) {
                            var foundEntity = nearbyEntities.get(0);
                            instance.trackEnemyEntity(foundEntity.getUUID());
                            StoryAdventureMod.LOGGER.debug("[CombatNodeHandler] Tracked summon-command entity: {}", foundEntity.getUUID());
                        }
                    } else {
                        // NPC template - use NPCTemplateManager API directly
                        try {
                            net.minecraft.server.level.ServerLevel serverLevel = spawnCenter.serverLevel();
                            spawnedEntity = de.markusbordihn.easynpc.config.NPCTemplateManager.spawnEntityFromTemplate(
                                serverLevel, entityType, spawnX, spawnY, spawnZ
                            );
                            
                            if (spawnedEntity != null) {
                                // Add all tags
                                for (String tag : allTags) {
                                    spawnedEntity.addTag(tag);
                                }
                                
                                // Apply glow effect for visibility
                                spawnedEntity.setGlowingTag(true);

                                // Boost AI follow range
                                if (spawnedEntity instanceof net.minecraft.world.entity.LivingEntity living) {
                                    var followAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
                                    if (followAttr != null) followAttr.setBaseValue(100.0);
                                }
                                
                                StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Successfully spawned NPC '{}' at {},{},{}", 
                                    entityType, spawnX, spawnY, spawnZ);
                            } else {
                                StoryAdventureMod.LOGGER.error("[CombatNodeHandler] Failed to spawn NPC template '{}' - API returned null", entityType);
                            }
                        } catch (Exception e) {
                            StoryAdventureMod.LOGGER.error("[CombatNodeHandler] Error spawning NPC '{}': {}", entityType, e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    
                    // Apply scale if specified
                    if (enemy.has("scale")) {
                        float scale = enemy.get("scale").getAsFloat();
                        if (spawnedEntity != null) {
                            // Use entity UUID directly for API-spawned NPCs
                            String scaleCmd = String.format(java.util.Locale.US, "easy_npc scale %s main %.2f",
                                spawnedEntity.getStringUUID(), scale);
                            instance.getServer().getCommands().performPrefixedCommand(
                                instance.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(2),
                                scaleCmd
                            );
                        } else {
                            // Fallback for summon-command spawned entities
                            String scaleCmd = String.format(java.util.Locale.US, "easy_npc scale @e[tag=instance_%s,tag=story_enemy,limit=1,sort=nearest] main %.2f",
                                instance.getInstanceId().toString(), scale);
                            instance.getServer().getCommands().performPrefixedCommand(
                                instance.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(2),
                                scaleCmd
                            );
                        }
                    }

                    // Track the spawned entity for death monitoring (even non-player kills)
                    if (spawnedEntity != null) {
                        instance.trackEnemyEntity(spawnedEntity.getUUID());
                    }
                } // end for count
                
                // Auto-setup bossbar if specified
                if (enemy.has("bossbar")) {
                    String bossLabel = enemy.get("bossbar").getAsString();
                    String bossId = "boss_" + instance.getInstanceId().toString().replace("-", "_");
                    setupBossBar(instance, bossId, bossLabel);
                    // Store boss entity tag if provided for tracking
                    if (!extraTags.isEmpty()) {
                        instance.getState().getMetadata().addProperty("boss_entity_tag", extraTags.get(0));
                    }
                    instance.getState().getMetadata().addProperty("boss_bar_id", bossId);
                }
                totalToSpawn += count;
            } // end for enemyElem
        } // end if has enemies
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Spawned {} enemies total", totalToSpawn);
        instance.getState().getMetadata().addProperty("combat_total", totalToSpawn);
        
        // Initial HUD sync
        syncHudToParty(instance, node);
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        if (!isCombatActive(instance)) return;
        
        // Update bossbar if active
        if (instance.getState().getMetadata().has("boss_bar_id")) {
            String bossId = instance.getState().getMetadata().get("boss_bar_id").getAsString();
            String bossTag = instance.getState().getMetadata().has("boss_entity_tag") ? 
                instance.getState().getMetadata().get("boss_entity_tag").getAsString() : "";
            updateBossBar(instance, bossId, bossTag);
        }

        int killed = getKilledCount(instance);
        int total = getTotalCount(instance);
        
        if (total > 0 && killed >= total) {
            markCombatVictory(instance, node);
            return;
        }
        

    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[CombatNodeHandler] onAction: player={}, action={}, data={}",
            player != null ? player.getName().getString() : "null", action, data);

        switch (action) {
            case "enemy_killed" -> {
                if (!(data instanceof net.minecraft.world.entity.Entity entity)) return;

                // Only count entities tagged as story_enemy
                if (!entity.getTags().contains("story_enemy")) return;

                // If a specific target tag is required (e.g. for a boss), check it
                String targetTag = node.getString("target_tag", "");
                if (!targetTag.isEmpty() && !entity.getTags().contains(targetTag)) {
                    return;
                }

                // Handle drops when enemy is killed
                handleEnemyDrops(instance, node, entity);

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
            case "sync_hud" -> {
                // Triggered by death event listener to update lives display
                syncHudToParty(instance, node);
            }
            case "escape_attempt" -> {
                if (node.getBoolean("escape_available", false)) {
                    StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Escape successful!");
                    instance.cleanupBossBars();
                    instance.getState().setNodeResult("escaped");
                    instance.getState().getMetadata().addProperty("combat_active", false);
                    instance.evaluateAutoTransitions();
                }
            }
        }
    }

    /**
     * Called when an enemy is killed by a non-player cause (fall, lava, other mobs, etc).
     * This ensures all deaths count toward combat completion, not just player kills.
     */
    public void onEnemyKilledByExternalCause(Instance instance, StageNode node) {
        if (!isCombatActive(instance)) return;

        // Increment killed count without requiring player attribution
        int killed = getKilledCount(instance) + 1;
        instance.getState().getMetadata().addProperty("combat_killed", killed);

        int total = getTotalCount(instance);
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Enemy died from external cause: {}/{}", killed, total);

        // Update HUD for progress
        syncHudToParty(instance, node);

        if (killed >= total) {
            markCombatVictory(instance, node);
        }
    }

    private void markCombatVictory(Instance instance, StageNode node) {
        if (!isCombatActive(instance)) return;
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Combat victory! Node: {}", node.getId());
        instance.cleanupBossBars();
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
        instance.cleanupBossBars();
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
        hudJson.append("],");
        // Add lives information
        hudJson.append("\"remainingLives\":").append(instance.getRemainingLives()).append(",");
        hudJson.append("\"maxLives\":").append(instance.getMaxTeamDeaths()).append(",");
        // Add instance time limit (remaining time in milliseconds)
        long remainingMillis = instance.getRemainingMillis();
        hudJson.append("\"instanceTimer\":").append(remainingMillis);
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
        // Cleanup bossbar if it exists
        instance.cleanupBossBars();
        instance.getState().getMetadata().addProperty("combat_active", false);
        // Clear tracked enemies when leaving combat node
        instance.clearTrackedEnemies();
    }

    private void setupBossBar(Instance instance, String bossId, String label) {
        String addCmd = String.format("bossbar add %s {\"text\":\"%s\"}", bossId, label);
        instance.getServer().getCommands().performPrefixedCommand(
            instance.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(2), addCmd);
        
        instance.getServer().getCommands().performPrefixedCommand(
            instance.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(2),
            String.format("bossbar set %s color red", bossId));
        
        instance.getServer().getCommands().performPrefixedCommand(
            instance.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(2),
            String.format("bossbar set %s players @a", bossId));
    }

    private void updateBossBar(Instance instance, String bossId, String bossTag) {
        // Find boss entity by tag and instance id
        String selector = bossTag.isEmpty() ? 
            String.format("@e[tag=instance_%s,tag=story_enemy,limit=1,sort=nearest]", instance.getInstanceId()) :
            String.format("@e[tag=instance_%s,tag=%s,limit=1]", instance.getInstanceId(), bossTag);
            
        String updateCmd = String.format("execute store result bossbar %s value run data get entity %s Health", bossId, selector);
        instance.getServer().getCommands().performPrefixedCommand(
            instance.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(2),
            updateCmd);
    }
    
    /**
     * Handle item drops when an enemy is killed.
     * Checks the enemy definition in the node data for a "drops" field.
     */
    private void handleEnemyDrops(Instance instance, StageNode node, net.minecraft.world.entity.Entity entity) {
        JsonObject data = node.getData();
        if (!data.has("enemies") || !data.get("enemies").isJsonArray()) {
            return;
        }

        JsonArray enemies = data.getAsJsonArray("enemies");
        var serverLevel = (net.minecraft.server.level.ServerLevel) entity.level();

        // Get entity tags to match against enemy definitions
        Set<String> entityTags = entity.getTags();
        String entityType = entity.getType().toString();

        for (var enemyElem : enemies) {
            if (!enemyElem.isJsonObject()) continue;
            JsonObject enemy = enemyElem.getAsJsonObject();

            // Check if this enemy definition has drops
            if (!enemy.has("drops") || !enemy.get("drops").isJsonArray()) {
                continue;
            }

            // Check if the entity matches this enemy definition by tags
            // If enemy has specific tags, check if entity has them
            if (enemy.has("tags") && enemy.get("tags").isJsonArray()) {
                JsonArray enemyTags = enemy.getAsJsonArray("tags");
                boolean matches = false;
                for (var tagElem : enemyTags) {
                    String tag = tagElem.getAsString();
                    if (entityTags.contains(tag)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) {
                    continue;
                }
            }

            // Process drops
            JsonArray drops = enemy.getAsJsonArray("drops");
            for (var dropElem : drops) {
                if (!dropElem.isJsonObject()) continue;
                JsonObject drop = dropElem.getAsJsonObject();

                String itemId = drop.has("item") ? drop.get("item").getAsString() : "minecraft:air";
                int count = drop.has("count") ? drop.get("count").getAsInt() : 1;
                double chance = drop.has("chance") ? drop.get("chance").getAsDouble() : 1.0;
                String nbt = drop.has("nbt") ? drop.get("nbt").getAsString() : null;

                // Check chance
                if (Math.random() > chance) {
                    continue;
                }

                // Create item stack
                ResourceLocation itemRL = ResourceLocation.parse(itemId);
                var item = BuiltInRegistries.ITEM.get(itemRL);
                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    StoryAdventureMod.LOGGER.warn("[CombatNodeHandler] Unknown item for drop: {}", itemId);
                    continue;
                }

                ItemStack stack = new ItemStack(item, count);

                // Apply NBT if specified (using 1.21+ component system)
                if (nbt != null && !nbt.isEmpty()) {
                    try {
                        var nbtTag = TagParser.parseTag(nbt);
                        stack.applyComponents(
                            DataComponentPatch.builder()
                                .set(DataComponents.CUSTOM_DATA, CustomData.of(nbtTag))
                                .build()
                        );
                    } catch (Exception e) {
                        StoryAdventureMod.LOGGER.error("[CombatNodeHandler] Failed to parse NBT for drop: {}", nbt, e);
                    }
                }

                // Spawn item entity at the killed entity's position
                ItemEntity itemEntity = new ItemEntity(
                    serverLevel,
                    entity.getX(),
                    entity.getY() + 0.5, // Slightly above ground
                    entity.getZ(),
                    stack
                );
                itemEntity.setDefaultPickUpDelay();
                serverLevel.addFreshEntity(itemEntity);

                StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Dropped {} x{} at {},{},{}",
                    itemId, count, entity.getX(), entity.getY(), entity.getZ());
            }
        }
    }

    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return !isCombatActive(instance);
    }
}
