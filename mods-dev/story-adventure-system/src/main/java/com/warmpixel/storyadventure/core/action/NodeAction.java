package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Base interface for node trigger actions.
 * Actions execute when entering or exiting a story node.
 */
public interface NodeAction {
    
    /**
     * Get the action type identifier.
     */
    String getType();
    
    /**
     * Execute this action for the given players.
     * @param players The players in the story instance
     */
    void execute(List<ServerPlayer> players);
    
    /**
     * Serialize this action to JSON.
     */
    JsonObject toJson();
    
    /**
     * Get a human-readable summary of this action.
     */
    String getSummary();
}
