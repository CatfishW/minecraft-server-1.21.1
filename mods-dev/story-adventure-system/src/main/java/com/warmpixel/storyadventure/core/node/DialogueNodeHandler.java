package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handler for DIALOGUE nodes.
 * Integrates with Easy NPC dialog system for rich NPC conversations.
 */
public class DialogueNodeHandler implements NodeHandler {
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String npcTemplate = node.getString("npc_template", "");
        String dialogSet = node.getString("dialog_set", "default");
        
        StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] onEnter: instance={}, node={}, npc='{}', dialogSet='{}'", 
            instance.getInstanceId(), node.getId(), npcTemplate, dialogSet);
        
        // TODO: Integration with Easy NPC
        // 1. Find or spawn the NPC from template
        // 2. Open dialog screen for party members
        // 3. Track relationship impacts
        StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] Ready for NPC interaction in node {}", node.getId());
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Dialogue nodes don't tick - they wait for player input
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] onAction: player={}, action={}, data={}", 
            player.getName().getString(), action, data);

        if ("choice".equals(action) && data instanceof String choice) {
            StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Player {} made choice: '{}' in node {}", 
                player.getName().getString(), choice, node.getId());

            // Record the dialogue choice
            instance.getState().setLastDialogueChoice(choice);
            
            // Apply relationship impact if defined
            var relationshipImpact = node.getObject("relationship_impact");
            if (relationshipImpact != null) {
                for (String npcId : relationshipImpact.keySet()) {
                    int delta = relationshipImpact.get(npcId).getAsInt();
                    StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] Applying relationship impact: npc={}, delta={}, player={}", 
                        npcId, delta, player.getName().getString());
                    instance.getState().modifyRelationship(player.getUUID(), npcId, delta);
                }
            }
            
            // Set node result based on choice
            String result = "choice_" + choice;
            instance.getState().setNodeResult(result);
            StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] Node result set to: {}", result);
            
            // Evaluate transitions
            instance.evaluateAutoTransitions();
        } else {
            StoryAdventureMod.LOGGER.warn("[DialogueNodeHandler] Unknown action or invalid data: action={}, data={}", action, data);
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] onExit: instance={}, node={}", instance.getInstanceId(), node.getId());
        // Clean up dialogue state
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        // Dialogue completes when a choice is made
        return instance.getState().getLastDialogueChoice() != null;
    }
}
