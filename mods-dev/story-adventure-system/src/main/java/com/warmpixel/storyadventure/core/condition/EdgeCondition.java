package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Interface for edge conditions that determine when a transition can occur.
 * Conditions are evaluated against the current instance state and optionally a player.
 */
public interface EdgeCondition {
    
    /**
     * Evaluate whether this condition is satisfied.
     * 
     * @param instance The current instance context
     * @param player The player being evaluated (may be null for auto-transitions)
     * @return true if the condition is met
     */
    boolean evaluate(Instance instance, ServerPlayer player);
    
    /**
     * Get the type identifier for this condition.
     */
    String getType();
    
    /**
     * Serialize this condition to JSON.
     */
    JsonObject serialize();
    
    /**
     * Get a human-readable description of this condition.
     */
    default String getDescription() {
        return getType();
    }
}
