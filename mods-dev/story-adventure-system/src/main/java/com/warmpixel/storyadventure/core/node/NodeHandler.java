package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Base interface for node type handlers.
 * Each node type (DIALOGUE, TASK, PUZZLE, etc.) has its own handler.
 */
public interface NodeHandler {
    
    /**
     * Called when a player/party enters this node.
     * 
     * @param instance The instance context
     * @param node The node being entered
     */
    void onEnter(Instance instance, StageNode node);
    
    /**
     * Called each server tick while the node is active.
     * 
     * @param instance The instance context
     * @param node The active node
     */
    void onTick(Instance instance, StageNode node);
    
    /**
     * Called when a player performs an action in this node.
     * 
     * @param instance The instance context
     * @param node The active node
     * @param player The player performing the action
     * @param action The action type/identifier
     * @param data Additional action data
     */
    void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data);
    
    /**
     * Called when a player/party exits this node.
     * 
     * @param instance The instance context
     * @param node The node being exited
     */
    void onExit(Instance instance, StageNode node);
    
    /**
     * Check if the node can be completed/exited.
     */
    boolean canComplete(Instance instance, StageNode node);
}
