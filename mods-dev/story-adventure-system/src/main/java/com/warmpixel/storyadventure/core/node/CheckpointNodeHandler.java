package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import com.warmpixel.storyadventure.instance.InstanceState;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handler for CHECKPOINT nodes.
 * Savepoints with optional "rewind" anchor capability.
 */
public class CheckpointNodeHandler implements NodeHandler {
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String checkpointId = node.getId();
        boolean saveInventory = node.getBoolean("save_inventory", true);
        boolean savePosition = node.getBoolean("save_position", true);
        boolean rewindAnchor = node.getBoolean("rewind_anchor", true);
        
        StoryAdventureMod.LOGGER.info("[CheckpointNodeHandler] onEnter: instance={}, checkpointId={}, rewind={}, saveInv={}, savePos={}", 
            instance.getInstanceId(), checkpointId, rewindAnchor, saveInventory, savePosition);
        
        if (rewindAnchor) {
            // Save state as checkpoint
            StoryAdventureMod.LOGGER.debug("[CheckpointNodeHandler] Saving checkpoint state for {}", checkpointId);
            InstanceState.CheckpointState checkpoint = new InstanceState.CheckpointState(instance.getState());
            instance.getState().saveCheckpoint(checkpointId, checkpoint);
        }
        
        // TODO: Save player inventories and positions if configured
        
        // Notify players
        // NetworkHandler.sendCheckpointNotification(instance);
        StoryAdventureMod.LOGGER.debug("[CheckpointNodeHandler] Checkpoint processing complete. Auto-advancing.");
        
        // Auto-advance (checkpoints don't pause)
        instance.getState().setNodeResult("complete");
        instance.evaluateAutoTransitions();
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Checkpoints don't tick - they immediately advance
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        // Checkpoints don't have actions
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        // Nothing to clean up
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return true; // Checkpoints always complete immediately
    }
}
