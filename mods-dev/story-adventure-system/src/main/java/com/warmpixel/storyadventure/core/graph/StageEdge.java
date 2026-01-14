package com.warmpixel.storyadventure.core.graph;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.condition.EdgeCondition;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an edge (transition) between two nodes in the Stage Graph.
 * Edges have conditions that must be met for the transition to be available.
 */
public class StageEdge {
    private final String sourceNodeId;
    private final String targetNodeId;
    private final List<EdgeCondition> conditions;
    private final int priority;
    
    public StageEdge(String sourceNodeId, String targetNodeId, List<EdgeCondition> conditions, int priority) {
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
        this.priority = priority;
    }
    
    public StageEdge(String sourceNodeId, String targetNodeId, List<EdgeCondition> conditions) {
        this(sourceNodeId, targetNodeId, conditions, 0);
    }
    
    public String getSourceNodeId() {
        return sourceNodeId;
    }
    
    public String getTargetNodeId() {
        return targetNodeId;
    }
    
    public List<EdgeCondition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }
    
    public int getPriority() {
        return priority;
    }
    
    /**
     * Check if all conditions for this edge are satisfied.
     * 
     * @param instance The current instance context
     * @param player The player attempting the transition (can be null for auto-transitions)
     * @return true if all conditions are met
     */
    public boolean canTransition(Instance instance, ServerPlayer player) {
        if (conditions.isEmpty()) return true;
        
        for (EdgeCondition condition : conditions) {
            if (!condition.evaluate(instance, player)) {
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.debug("[StageEdge] Condition FAILED for edge {} -> {}: {}", 
                    sourceNodeId, targetNodeId, condition.getClass().getSimpleName());
                return false;
            }
        }
        
        com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.debug("[StageEdge] All conditions MET for edge {} -> {}", sourceNodeId, targetNodeId);
        return true;
    }
    
    /**
     * Check if this edge has no conditions (unconditional transition).
     */
    public boolean isUnconditional() {
        return conditions.isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("StageEdge{%s -> %s, conditions=%d, priority=%d}", 
            sourceNodeId, targetNodeId, conditions.size(), priority);
    }
}
