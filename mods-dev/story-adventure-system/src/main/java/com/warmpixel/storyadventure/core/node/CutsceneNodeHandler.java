package com.warmpixel.storyadventure.core.node;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import com.warmpixel.storyadventure.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Handler for CUTSCENE nodes.
 * Implements cinemachine-like camera control for scripted cutscenes.
 * 
 * Supports:
 * - Camera path with keyframes (position, rotation, FOV)
 * - Look-at targets
 * - Letterbox bars and fade transitions
 * - Skippable cutscenes
 * - Teleport on completion
 */
public class CutsceneNodeHandler implements NodeHandler {
    
    private long cutsceneStartTime = 0;
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        int durationTicks = node.getInt("duration_ticks", 100);
        String message = node.getString("message", "");
        String voiceover = node.getString("voiceover", "");
        boolean skippable = node.getBoolean("skippable", true);
        boolean letterbox = node.getBoolean("letterbox", true);
        int fadeInTicks = node.getInt("fade_in_ticks", 20);
        int fadeOutTicks = node.getInt("fade_out_ticks", 20);
        
        cutsceneStartTime = System.currentTimeMillis();
        
        StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] onEnter: instance={}, node={}, duration={} ticks", 
            instance.getInstanceId(), node.getId(), durationTicks);
        
        // Get camera path from node data
        JsonObject cameraPathJson = node.getObject("camera_path");
        
        // If no camera path defined, create a simple one from current player position
        if (cameraPathJson == null) {
            cameraPathJson = createDefaultCameraPath(instance, durationTicks);
            StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] No camera_path defined, using default");
        }
        
        // Send cutscene start command to all party members
        String instanceId = instance.getInstanceId().toString();
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                NetworkHandler.sendCutsceneStart(player, cameraPathJson, skippable, letterbox, 
                    fadeInTicks, fadeOutTicks, instanceId, voiceover);
                
                // Show optional title message
                if (!message.isEmpty()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7" + message));
                }
            }
        }
        
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] Cutscene started for {} party members", 
            instance.getParty().getMemberCount());
    }
    
    /**
     * Create a default camera path when none is specified in the node data.
     * Uses the first party member's position as a reference.
     */
    private JsonObject createDefaultCameraPath(Instance instance, int durationTicks) {
        JsonObject pathObj = new JsonObject();
        com.google.gson.JsonArray keyframes = new com.google.gson.JsonArray();
        
        // Get first player's position as reference
        double x = 0, y = 64, z = 0;
        float yaw = 0, pitch = 0;
        
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                x = player.getX();
                y = player.getY() + 2; // Slightly above head
                z = player.getZ();
                yaw = player.getYRot();
                pitch = player.getXRot();
                break;
            }
        }
        
        // First keyframe - starting position
        JsonObject kf1 = new JsonObject();
        com.google.gson.JsonArray pos1 = new com.google.gson.JsonArray();
        pos1.add(x - 3);
        pos1.add(y + 3);
        pos1.add(z - 3);
        kf1.add("position", pos1);
        
        com.google.gson.JsonArray rot1 = new com.google.gson.JsonArray();
        rot1.add(yaw);
        rot1.add(pitch - 10);
        rot1.add(0);
        kf1.add("rotation", rot1);
        
        kf1.addProperty("fov", 70);
        kf1.addProperty("duration_ticks", 0);
        kf1.addProperty("easing", "LINEAR");
        keyframes.add(kf1);
        
        // Second keyframe - orbit around player
        JsonObject kf2 = new JsonObject();
        com.google.gson.JsonArray pos2 = new com.google.gson.JsonArray();
        pos2.add(x + 3);
        pos2.add(y + 2);
        pos2.add(z + 3);
        kf2.add("position", pos2);
        
        com.google.gson.JsonArray rot2 = new com.google.gson.JsonArray();
        rot2.add(yaw + 180);
        rot2.add(pitch);
        rot2.add(0);
        kf2.add("rotation", rot2);
        
        kf2.addProperty("fov", 60);
        kf2.addProperty("duration_ticks", durationTicks);
        kf2.addProperty("easing", "EASE_IN_OUT");
        keyframes.add(kf2);
        
        pathObj.add("keyframes", keyframes);
        
        // Add look-at target (player position)
        JsonObject lookAt = new JsonObject();
        lookAt.addProperty("type", "position");
        com.google.gson.JsonArray lookAtPos = new com.google.gson.JsonArray();
        lookAtPos.add(x);
        lookAtPos.add(y - 1);
        lookAtPos.add(z);
        lookAt.add("value", lookAtPos);
        pathObj.add("look_at", lookAt);
        
        return pathObj;
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        int durationTicks = node.getInt("duration_ticks", 100);
        long durationMs = durationTicks * 50L; // 50ms per tick
        long elapsed = System.currentTimeMillis() - cutsceneStartTime;
        
        if (elapsed >= durationMs) {
            StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] onTick: Cutscene complete. Elapsed: {}ms", elapsed);
            
            // Cutscene complete
            instance.getState().setNodeResult("complete");
            
            // Send stop command to all party members
            String instanceId = instance.getInstanceId().toString();
            for (UUID memberId : instance.getParty().getMembers()) {
                ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
                if (player != null) {
                    NetworkHandler.sendCutsceneStop(player, instanceId);
                }
            }
            
            // Check if this is an ending
            if (node.getBoolean("is_ending", false)) {
                String endingType = node.getString("ending_type", "success");
                StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] Cutscene is ending. Type: {}", endingType);
                
                if ("success".equals(endingType)) {
                    instance.complete();
                } else {
                    instance.fail();
                }
            } else {
                StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] Cutscene finished, evaluating transitions.");
                instance.evaluateAutoTransitions();
            }
        }
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] onAction: player={}, action={}", 
            player.getName().getString(), action);

        if ("skip".equals(action) && node.getBoolean("skippable", true)) {
            StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] Player {} skipped cutscene {}", 
                player.getName().getString(), node.getId());
            
            // Send stop command to all party members
            String instanceId = instance.getInstanceId().toString();
            for (UUID memberId : instance.getParty().getMembers()) {
                ServerPlayer member = instance.getServer().getPlayerList().getPlayer(memberId);
                if (member != null) {
                    NetworkHandler.sendCutsceneStop(member, instanceId);
                }
            }
            
            // Complete the cutscene
            instance.getState().setNodeResult("complete");
            instance.evaluateAutoTransitions();
        } else if ("skip".equals(action)) {
            StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] Skip rejected. Cutscene not skippable.");
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] onExit: instance={}, node={}", 
            instance.getInstanceId(), node.getId());

        // Handle teleport on complete
        String teleportTo = node.getString("teleport_on_complete", "");
        if (!teleportTo.isEmpty()) {
            StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] Teleport requested to: {}", teleportTo);
            var loc = instance.getGraph().getSpecialLocation(teleportTo);
            if (loc != null) {
                var server = instance.getServer();
                var worldKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, 
                    net.minecraft.resources.ResourceLocation.parse(loc.dimension())
                );
                var targetWorld = server.getLevel(worldKey);
                if (targetWorld != null) {
                    for (UUID memberId : instance.getParty().getMembers()) {
                        var player = server.getPlayerList().getPlayer(memberId);
                        if (player != null) {
                            player.teleportTo(targetWorld, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
                        }
                    }
                }
            }
        }
        
        // Ensure cutscene is stopped on exit (in case of unexpected transition)
        String instanceId = instance.getInstanceId().toString();
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                NetworkHandler.sendCutsceneStop(player, instanceId);
            }
        }
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return instance.getState().isCurrentNodeCompleteWith("complete");
    }
}
