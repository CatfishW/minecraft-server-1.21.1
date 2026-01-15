package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handler for CUTSCENE nodes.
 * Scripted camera movements, teleports, and particle effects.
 */
public class CutsceneNodeHandler implements NodeHandler {
    
    private long cutsceneStartTime = 0;
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        int durationTicks = node.getInt("duration_ticks", 100);
        String message = node.getString("message", "");
        
        cutsceneStartTime = System.currentTimeMillis();
        
        StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] onEnter: instance={}, node={}, duration={} ticks, message='{}'", 
            instance.getInstanceId(), node.getId(), durationTicks, message);
        
        // TODO:
        // 1. Disable player movement
        // 2. Start camera path
        // 3. Show title/subtitle
        // 4. Play particles/effects
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] Cutscene visual effects started for instance {}", instance.getInstanceId());
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        int durationTicks = node.getInt("duration_ticks", 100);
        long durationMs = durationTicks * 50L; // 50ms per tick
        long elapsed = System.currentTimeMillis() - cutsceneStartTime;
        
        // Log periodically or on completion
        if (elapsed >= durationMs) {
            StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] onTick: Cutscene complete. Elapsed: {}ms, Duration: {}ms", elapsed, durationMs);
            
            // Cutscene complete
            instance.getState().setNodeResult("complete");
            
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
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] onAction: player={}, action={}, data={}", 
            player.getName().getString(), action, data);

        if ("skip".equals(action) && node.getBoolean("skippable", true)) {
            StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] Player {} skipped cutscene {}", player.getName().getString(), node.getId());
            // Skip the cutscene
            instance.getState().setNodeResult("complete");
            instance.evaluateAutoTransitions();
        } else if ("skip".equals(action)) {
             StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] Skip rejected. Skippable: {}", node.getBoolean("skippable", true));
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] onExit: instance={}, node={}", instance.getInstanceId(), node.getId());

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
                    for (java.util.UUID memberId : instance.getParty().getMembers()) {
                        var player = server.getPlayerList().getPlayer(memberId);
                        if (player != null) {
                            player.teleportTo(targetWorld, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
                        }
                    }
                }
            }
        }
        
        // Re-enable player movement
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] Cleanup: Re-enabling player movement");
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return instance.getState().isCurrentNodeCompleteWith("complete");
    }
}
