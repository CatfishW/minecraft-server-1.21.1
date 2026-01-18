package com.warmpixel.storyadventure.core.node;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import com.warmpixel.storyadventure.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    
    private static final double CUTSCENE_TELEPORT_DISTANCE = 96.0;
    private static final String META_CUTSCENE_RETURN_PREFIX = "cutscene_return_";
    private static final String META_CUTSCENE_SLOW_FALL_PREFIX = "cutscene_slow_fall_";
    private static final int CUTSCENE_TELEPORT_INTERVAL_TICKS = 10;
    private static final double CUTSCENE_TRACK_DISTANCE_SQ = 9.0;
    private static final int CUTSCENE_FALL_PROTECTION_BUFFER_TICKS = 40;
    private static final java.util.Map<UUID, CutsceneRuntime> runtimes = new HashMap<>();
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

        // If the cutscene camera is far from players, temporarily move them near the camera to load chunks.
        var cameraStart = getCameraStartPosition(cameraPathJson);
        var keyframes = parseKeyframes(cameraPathJson);
        if (!keyframes.isEmpty()) {
            runtimes.put(instance.getInstanceId(), new CutsceneRuntime(System.currentTimeMillis(), keyframes));
        }
        
        // Parse subtitles
        com.google.gson.JsonArray subtitles = node.getData().has("subtitles") ? 
            node.getData().getAsJsonArray("subtitles") : null;
        
        // Send cutscene start command to all party members
        String instanceId = instance.getInstanceId().toString();
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                if (cameraStart != null) {
                    double dx = player.getX() - cameraStart.x;
                    double dy = player.getY() - cameraStart.y;
                    double dz = player.getZ() - cameraStart.z;
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq > CUTSCENE_TELEPORT_DISTANCE * CUTSCENE_TELEPORT_DISTANCE) {
                        storeReturnLocation(instance, player);
                        player.teleportTo(cameraStart.x, cameraStart.y, cameraStart.z);
                        applyFallProtection(instance, player, durationTicks);
                    }
                }
                applyFallProtection(instance, player, durationTicks);
                NetworkHandler.sendCutsceneStart(player, cameraPathJson, skippable, letterbox, 
                    fadeInTicks, fadeOutTicks, instanceId, voiceover, subtitles);
                
                // Set to spectator mode during cutscene
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                
                // Show optional title message
                if (!message.isEmpty()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7" + message));
                }
            }
        }
        
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] Cutscene started for {} party members", 
            instance.getParty().getMemberCount());
    }

    private static class CutsceneKeyframe {
        final Vec3 position;
        final int durationTicks;

        CutsceneKeyframe(Vec3 position, int durationTicks) {
            this.position = position;
            this.durationTicks = durationTicks;
        }
    }

    private static class CutsceneRuntime {
        final long startTimeMs;
        final List<CutsceneKeyframe> keyframes;
        long lastTeleportTick = -1;
        final long totalTicks;

        CutsceneRuntime(long startTimeMs, List<CutsceneKeyframe> keyframes) {
            this.startTimeMs = startTimeMs;
            this.keyframes = keyframes;
            long total = 0;
            for (CutsceneKeyframe kf : keyframes) {
                total += Math.max(0, kf.durationTicks);
            }
            this.totalTicks = total;
        }
    }

    private List<CutsceneKeyframe> parseKeyframes(JsonObject cameraPathJson) {
        List<CutsceneKeyframe> keyframes = new ArrayList<>();
        if (cameraPathJson == null || !cameraPathJson.has("keyframes")) {
            return keyframes;
        }
        var frames = cameraPathJson.getAsJsonArray("keyframes");
        for (var elem : frames) {
            if (!elem.isJsonObject()) continue;
            var frame = elem.getAsJsonObject();
            if (!frame.has("position")) continue;
            var pos = frame.getAsJsonArray("position");
            if (pos.size() < 3) continue;
            double x = pos.get(0).getAsDouble();
            double y = pos.get(1).getAsDouble();
            double z = pos.get(2).getAsDouble();
            int duration = frame.has("duration_ticks") ? frame.get("duration_ticks").getAsInt() : 0;
            keyframes.add(new CutsceneKeyframe(new Vec3(x, y, z), duration));
        }
        return keyframes;
    }

    private static class CameraStart {
        final double x;
        final double y;
        final double z;

        CameraStart(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private CameraStart getCameraStartPosition(JsonObject cameraPathJson) {
        if (cameraPathJson == null || !cameraPathJson.has("keyframes")) {
            return null;
        }
        var keyframes = cameraPathJson.getAsJsonArray("keyframes");
        if (keyframes.isEmpty()) {
            return null;
        }
        var first = keyframes.get(0).getAsJsonObject();
        if (!first.has("position")) {
            return null;
        }
        var pos = first.getAsJsonArray("position");
        if (pos.size() < 3) {
            return null;
        }
        return new CameraStart(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble());
    }

    private Vec3 getCameraPositionAtTick(CutsceneRuntime runtime, long tick) {
        if (runtime == null || runtime.keyframes.isEmpty()) {
            return null;
        }
        if (runtime.keyframes.size() == 1) {
            return runtime.keyframes.get(0).position;
        }
        long remaining = tick;
        for (int i = 0; i < runtime.keyframes.size() - 1; i++) {
            CutsceneKeyframe current = runtime.keyframes.get(i);
            CutsceneKeyframe next = runtime.keyframes.get(i + 1);
            long segment = Math.max(0, current.durationTicks);
            if (segment == 0) {
                continue;
            }
            if (remaining <= segment) {
                double t = remaining / (double) segment;
                return new Vec3(
                    Mth.lerp(t, current.position.x, next.position.x),
                    Mth.lerp(t, current.position.y, next.position.y),
                    Mth.lerp(t, current.position.z, next.position.z)
                );
            }
            remaining -= segment;
        }
        return runtime.keyframes.get(runtime.keyframes.size() - 1).position;
    }

    private void storeReturnLocation(Instance instance, ServerPlayer player) {
        var meta = instance.getState().getMetadata();
        String key = META_CUTSCENE_RETURN_PREFIX + player.getUUID();
        if (meta.has(key)) {
            return;
        }
        JsonObject loc = new JsonObject();
        loc.addProperty("dimension", player.level().dimension().location().toString());
        loc.addProperty("x", player.getX());
        loc.addProperty("y", player.getY());
        loc.addProperty("z", player.getZ());
        loc.addProperty("yaw", player.getYRot());
        loc.addProperty("pitch", player.getXRot());
        meta.add(key, loc);
    }

    private void applyFallProtection(Instance instance, ServerPlayer player, int durationTicks) {
        String key = META_CUTSCENE_SLOW_FALL_PREFIX + player.getUUID();
        var meta = instance.getState().getMetadata();
        boolean alreadyProtected = meta.has(key);
        boolean hasSlowFalling = player.hasEffect(MobEffects.SLOW_FALLING);
        if (!alreadyProtected) {
            JsonObject info = new JsonObject();
            info.addProperty("applied", !hasSlowFalling);
            meta.add(key, info);
            if (!hasSlowFalling) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
                    durationTicks + CUTSCENE_FALL_PROTECTION_BUFFER_TICKS, 0, true, false, false));
            }
        } else if (!hasSlowFalling) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
                durationTicks + CUTSCENE_FALL_PROTECTION_BUFFER_TICKS, 0, true, false, false));
        }
        player.fallDistance = 0.0f;
    }

    private void clearFallProtection(Instance instance, ServerPlayer player) {
        String key = META_CUTSCENE_SLOW_FALL_PREFIX + player.getUUID();
        var meta = instance.getState().getMetadata();
        if (!meta.has(key)) {
            return;
        }
        boolean applied = meta.getAsJsonObject(key).has("applied")
            && meta.getAsJsonObject(key).get("applied").getAsBoolean();
        if (applied) {
            player.removeEffect(MobEffects.SLOW_FALLING);
        }
        meta.remove(key);
        player.fallDistance = 0.0f;
    }

    private void restoreReturnLocations(Instance instance) {
        var meta = instance.getState().getMetadata();
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player == null) {
                continue;
            }
            String key = META_CUTSCENE_RETURN_PREFIX + memberId;
            if (!meta.has(key)) {
                continue;
            }
            var loc = meta.getAsJsonObject(key);
            String dimension = loc.has("dimension") ? loc.get("dimension").getAsString() : "minecraft:overworld";
            double x = loc.has("x") ? loc.get("x").getAsDouble() : player.getX();
            double y = loc.has("y") ? loc.get("y").getAsDouble() : player.getY();
            double z = loc.has("z") ? loc.get("z").getAsDouble() : player.getZ();
            float yaw = loc.has("yaw") ? loc.get("yaw").getAsFloat() : player.getYRot();
            float pitch = loc.has("pitch") ? loc.get("pitch").getAsFloat() : player.getXRot();

            var server = instance.getServer();
            var worldKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(dimension)
            );
            var targetWorld = server.getLevel(worldKey);
            if (targetWorld != null) {
                player.teleportTo(targetWorld, x, y, z, yaw, pitch);
            } else {
                player.teleportTo(x, y, z);
            }
            player.fallDistance = 0.0f;
            meta.remove(key);
        }
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
        CutsceneRuntime runtime = runtimes.get(instance.getInstanceId());
        if (runtime != null) {
            long elapsedTicks = elapsed / 50L;
            if (runtime.lastTeleportTick == -1 || elapsedTicks - runtime.lastTeleportTick >= CUTSCENE_TELEPORT_INTERVAL_TICKS) {
                runtime.lastTeleportTick = elapsedTicks;
                Vec3 cameraPos = getCameraPositionAtTick(runtime, Math.min(elapsedTicks, runtime.totalTicks));
                if (cameraPos != null) {
                    for (UUID memberId : instance.getParty().getMembers()) {
                        ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
                        if (player != null && player.distanceToSqr(cameraPos) > CUTSCENE_TRACK_DISTANCE_SQ) {
                            player.teleportTo(cameraPos.x, cameraPos.y, cameraPos.z);
                            applyFallProtection(instance, player, durationTicks);
                        }
                    }
                }
            }
        }
        
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
                    clearFallProtection(instance, player);
                    // Revert to survival mode after cutscene
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                }
            }
            restoreReturnLocations(instance);
            runtimes.remove(instance.getInstanceId());
            
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
                    clearFallProtection(instance, member);
                    // Revert to survival mode after skip
                    member.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                }
            }
            restoreReturnLocations(instance);
            runtimes.remove(instance.getInstanceId());
            
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
                            player.fallDistance = 0.0f;
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
                clearFallProtection(instance, player);
                // Ensure survival mode on exit
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
        }
        restoreReturnLocations(instance);
        runtimes.remove(instance.getInstanceId());
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return instance.getState().isCurrentNodeCompleteWith("complete");
    }
}
