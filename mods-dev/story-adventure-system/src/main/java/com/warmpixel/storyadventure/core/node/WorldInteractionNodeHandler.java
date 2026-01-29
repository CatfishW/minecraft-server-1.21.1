package com.warmpixel.storyadventure.core.node;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handler for WORLD_INTERACTION nodes.
 * requires players to perform specific physical actions in the world.
 */
public class WorldInteractionNodeHandler implements NodeHandler {

    private static final String INTERACTION_PROGRESS_PREFIX = "interaction_progress_";
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String title = node.getString("title", "Interact");
        String description = node.getString("description", "Complete the challenge");
        
        StoryAdventureMod.LOGGER.info("[WorldInteractionNodeHandler] onEnter: instance={}, title={}", 
            instance.getInstanceId(), title);
            
        // Reset progress flags
        resetProgress(instance, node);

        // Create waypoint if configured
        createWaypointFromNodeData(instance, node);
        
        // Notify players
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                player.sendSystemMessage(Component.literal("§e[Objective] §f" + title));
                if (!description.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§7" + description));
                }
            }
        }
        
        // Initial HUD sync
        syncHud(instance, node);
    }
    
    private void resetProgress(Instance instance, StageNode node) {
        var interactions = getInteractions(node);
        if (interactions != null) {
            var metadata = instance.getState().getMetadata();
            for (int i = 0; i < interactions.size(); i++) {
                metadata.addProperty(INTERACTION_PROGRESS_PREFIX + i, 0);
            }
        }
    }

    @Override
    public void onTick(Instance instance, StageNode node) {
        // Evaluate completion
        if (checkCompletion(instance, node)) {
            return;
        }

        // 1. Stand on Block Logic & Jump Detection (Tick based)
        var metadata = instance.getState().getMetadata();
        var interactions = getInteractions(node);
        boolean progressMade = false;

        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player == null) continue;

            BlockPos playerPos = player.blockPosition();
            BlockPos belowPos = playerPos.below();

            for (int i = 0; i < interactions.size(); i++) {
                var req = interactions.get(i);
                String type = req.getString("type", "");
                
                // Location match
                int tx = req.getInt("x", 0);
                int ty = req.getInt("y", 0);
                int tz = req.getInt("z", 0);
                BlockPos targetPos = new BlockPos(tx, ty, tz);

                // Check distance
                if (playerPos.equals(targetPos) || belowPos.equals(targetPos)) {
                    // STAND_ON_BLOCK
                    if ("STAND_ON_BLOCK".equals(type)) {
                        String key = INTERACTION_PROGRESS_PREFIX + i;
                        int currentTicks = metadata.has(key) ? metadata.get(key).getAsInt() : 0;
                        int targetTicks = req.getInt("ticks", 100); // Default 5 seconds
                        
                        if (currentTicks < targetTicks) {
                            metadata.addProperty(key, currentTicks + 1);
                            progressMade = true;
                            
                            // Visual feedback every second
                            if (currentTicks % 20 == 0) {
                                spawnFeedback(player, req, false);
                            }
                        }
                    }
                    
                    // JUMP_ON_BLOCK (Simplified: detect if player is in air but was just on block)
                    // Better: Check player's deltaY and ground state
                    if ("JUMP_ON_BLOCK".equals(type)) {
                        String lastGroundKey = "jump_last_ground_" + player.getUUID() + "_" + i;
                        boolean lastGround = metadata.has(lastGroundKey) && metadata.get(lastGroundKey).getAsBoolean();
                        boolean currentGround = player.onGround();
                        
                        if (lastGround && !currentGround && player.getDeltaMovement().y > 0) {
                            // Jumped!
                            if (attemptProgress(instance, node, player, i, req)) {
                                progressMade = true;
                                spawnFeedback(player, req, true);
                            }
                        }
                        metadata.addProperty(lastGroundKey, currentGround);
                    }
                }
            }
        }

        if (progressMade) {
            syncHud(instance, node);
            if (checkCompletion(instance, node)) {
                completeNode(instance, node);
            }
        }
    }

    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        if (checkCompletion(instance, node)) return;

        boolean progressMade = false;
        
        // Handle manual completion action (e.g. debug or bypass)
        if ("complete".equals(action)) {
            forceComplete(instance);
            return;
        }
        
        // Handle world interactions passed from Event Listener
        if ("world_interaction".equals(action) && data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> interactionData = (Map<String, Object>) data;
            progressMade = handleInteraction(instance, node, player, interactionData);
        }
        
        if (progressMade) {
            syncHud(instance, node);
            if (checkCompletion(instance, node)) {
                completeNode(instance, node);
            }
        }
    }
    
    private boolean handleInteraction(Instance instance, StageNode node, ServerPlayer player, Map<String, Object> data) {
        String type = (String) data.get("type"); // CLICK_BLOCK, BREAK_BLOCK, etc.
        BlockPos pos = (BlockPos) data.get("pos");
        
        if (type == null || pos == null) return false;
        
        boolean changed = false;
        var requiredInteractions = getInteractions(node);
        
        for (int i = 0; i < requiredInteractions.size(); i++) {
            var req = requiredInteractions.get(i);
            
            // Check type match
            String reqType = req.getString("type", "");
            if (!reqType.equalsIgnoreCase(type)) continue;
            
            // Check location match
            int x = req.getInt("x", 0);
            int y = req.getInt("y", 0);
            int z = req.getInt("z", 0);
            
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                // Match found!
                if (attemptProgress(instance, node, player, i, req)) {
                    changed = true;
                    spawnFeedback(player, req, true);
                    
                    // Force break if it's a break interaction (to bypass protection)
                    if ("BREAK_BLOCK".equals(type)) {
                        instance.getServer().execute(() -> {
                            player.serverLevel().destroyBlock(pos, false);
                        });
                    }
                }
            }
        }
        
        return changed;
    }

    private boolean attemptProgress(Instance instance, StageNode node, ServerPlayer player, int index, StageNode req) {
        var metadata = instance.getState().getMetadata();
        boolean ordered = node.getBoolean("ordered", false);
        
        if (ordered) {
            // Must complete interaction at index if all indices < index are complete
            for (int prev = 0; prev < index; prev++) {
                String prevKey = INTERACTION_PROGRESS_PREFIX + prev;
                int prevCount = metadata.has(prevKey) ? metadata.get(prevKey).getAsInt() : 0;
                int prevTarget = getInteractions(node).get(prev).getInt("count", 1);
                if (prevCount < prevTarget) {
                    // Out of order!
                    if (node.getBoolean("reset_on_wrong", true)) {
                        resetProgress(instance, node);
                        syncHud(instance, node);
                        player.displayClientMessage(Component.literal("§cWrong sequence! Progress reset."), true);
                        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 0.5f);
                    }
                    return false;
                }
            }
        }

        String key = INTERACTION_PROGRESS_PREFIX + index;
        int current = metadata.has(key) ? metadata.get(key).getAsInt() : 0;
        int target = req.getInt("count", 1);
        
        if (current < target) {
            current++;
            metadata.addProperty(key, current);
            
            // Feedback
            if (player != null) {
                String msg = req.getString("feedback_msg", "");
                if (msg.isEmpty()) {
                    msg = "Progress: " + current + "/" + target;
                }
                player.displayClientMessage(Component.literal("§a" + msg), true);
            }
            return true;
        }
        return false;
    }

    private void spawnFeedback(ServerPlayer player, StageNode req, boolean major) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = new BlockPos(req.getInt("x", 0), req.getInt("y", 0), req.getInt("z", 0));
        
        if (major) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.1);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0f, 1.0f);
        } else {
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private boolean checkCompletion(Instance instance, StageNode node) {
        if (instance.getState().isCurrentNodeCompleteWith("complete")) return true;
        
        var interactions = getInteractions(node);
        if (interactions.isEmpty()) return true; // No requirements?
        
        var metadata = instance.getState().getMetadata();
        boolean allComplete = true;
        for (int i = 0; i < interactions.size(); i++) {
            var req = interactions.get(i);
            String key = INTERACTION_PROGRESS_PREFIX + i;
            int current = metadata.has(key) ? metadata.get(key).getAsInt() : 0;
            int target = req.getInt("count", 1);
            if (current < target) {
                allComplete = false;
                break;
            }
        }
        
        return allComplete;
    }
    
    private void completeNode(Instance instance, StageNode node) {
        instance.getState().setNodeResult("complete");
        
        // Notify players
        String title = node.getString("title", "Completed");
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                player.sendSystemMessage(Component.literal("§a[Completed] §f" + title));
            }
        }
        
        // Trigger transition
        instance.evaluateAutoTransitions();
    }
    
    private void forceComplete(Instance instance) {
        instance.getState().setNodeResult("complete");
        instance.evaluateAutoTransitions();
    }

    private List<com.warmpixel.storyadventure.core.graph.StageNode> getInteractions(StageNode node) {
        // Helper to parse "interactions" array from node data
        List<com.warmpixel.storyadventure.core.graph.StageNode> list = new ArrayList<>();
        if (node.getData().has("interactions")) {
            var array = node.getData().getAsJsonArray("interactions");
            for (var element : array) {
                if (element.isJsonObject()) {
                    // Use TASK type as generic holder, since we just need the data accessor methods
                    list.add(new com.warmpixel.storyadventure.core.graph.StageNode("temp", com.warmpixel.storyadventure.core.graph.NodeType.TASK, element.getAsJsonObject()));
                }
            }
        }
        return list;
    }

    @Override
    public void onExit(Instance instance, StageNode node) {
        // Cleanup states
        var interactions = getInteractions(node);
        var metadata = instance.getState().getMetadata();
        for (int i = 0; i < interactions.size(); i++) {
            metadata.remove(INTERACTION_PROGRESS_PREFIX + i);
        }

        // Remove waypoint for this interaction node
        removeWaypointFromNodeData(instance, node);
    }

    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return instance.getState().isCurrentNodeCompleteWith("complete");
    }
    
    private void syncHud(Instance instance, StageNode node) {
        var interactions = getInteractions(node);
        if (interactions.isEmpty()) return;

        var metadata = instance.getState().getMetadata();
        String title = node.getString("title", "Interact");
        
        StringBuilder hudJson = new StringBuilder();
        hudJson.append("{");
        hudJson.append("\"title\":\"").append(escapeJson(instance.getGraph().getName())).append("\",");
        hudJson.append("\"chapter\":\"").append(escapeJson(title)).append("\",");
        hudJson.append("\"objectives\":[");

        for (int i = 0; i < interactions.size(); i++) {
            var req = interactions.get(i);
            String key = INTERACTION_PROGRESS_PREFIX + i;
            int current = metadata.has(key) ? metadata.get(key).getAsInt() : 0;
            int target = req.getInt("count", 1);
            if ("STAND_ON_BLOCK".equals(req.getString("type", ""))) {
                target = req.getInt("ticks", 100) / 20; // Show seconds
                current = current / 20;
            }

            String desc = req.getString("feedback_msg", "");
            if (desc.isEmpty()) {
                desc = req.getString("type", "Interact") + " (" + current + "/" + target + ")";
            } else if (target > 1) {
                desc = desc + " (" + current + "/" + target + ")";
            }

            if (i > 0) hudJson.append(",");
            hudJson.append("{");
            hudJson.append("\"text\":\"").append(escapeJson(desc)).append("\",");
            hudJson.append("\"complete\":").append(current >= target ? "true" : "false").append(",");
            hudJson.append("\"current\":").append(current < target ? "true" : "false");
            hudJson.append("}");
        }

        hudJson.append("],");
        hudJson.append("\"remainingLives\":").append(instance.getRemainingLives()).append(",");
        hudJson.append("\"maxLives\":").append(instance.getMaxTeamDeaths());
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

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void createWaypointFromNodeData(Instance instance, StageNode node) {
        JsonObject data = node.getData();
        if (!data.has("waypoint")) {
            return;
        }

        JsonObject wpData = data.getAsJsonObject("waypoint");
        String wpId = wpData.has("id") ? wpData.get("id").getAsString() : "interaction_waypoint";
        double x = wpData.has("x") ? wpData.get("x").getAsDouble() : 0;
        double y = wpData.has("y") ? wpData.get("y").getAsDouble() : 64;
        double z = wpData.has("z") ? wpData.get("z").getAsDouble() : 0;
        String label = wpData.has("label") ? wpData.get("label").getAsString() : "目标";
        String icon = wpData.has("icon") ? wpData.get("icon").getAsString() : "objective";
        int color = 0xFFFFCC00;

        if (wpData.has("color")) {
            String colorStr = wpData.get("color").getAsString();
            try {
                color = (int) Long.parseLong(colorStr.replace("0x", ""), 16);
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.warn("[WorldInteractionNodeHandler] Failed to parse waypoint color: {}", colorStr);
            }
        }

        var waypoint = new com.warmpixel.storyadventure.core.waypoint.Waypoint(wpId, new Vec3(x, y, z));
        waypoint.setLabel(label);
        waypoint.setIcon(com.warmpixel.storyadventure.core.waypoint.Waypoint.WaypointIcon.fromId(icon));
        waypoint.setColor(color);
        waypoint.setShowDistance(true);

        instance.addWaypoint(waypoint);
        StoryAdventureMod.LOGGER.info("[WorldInteractionNodeHandler] Created waypoint '{}' at ({}, {}, {})", wpId, x, y, z);
    }

    private void removeWaypointFromNodeData(Instance instance, StageNode node) {
        JsonObject data = node.getData();
        if (!data.has("waypoint")) {
            return;
        }

        JsonObject wpData = data.getAsJsonObject("waypoint");
        String wpId = wpData.has("id") ? wpData.get("id").getAsString() : "interaction_waypoint";
        instance.removeWaypoint(wpId);
    }
}
