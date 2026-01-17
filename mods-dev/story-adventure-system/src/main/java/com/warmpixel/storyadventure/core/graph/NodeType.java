package com.warmpixel.storyadventure.core.graph;

/**
 * Enum defining all supported node types in the Stage Graph.
 * Each node type represents a different kind of story beat.
 */
public enum NodeType {
    /**
     * NPC conversation with branching options, hidden flags, and relationship impact.
     * Integrates with Easy NPC dialog system.
     */
    DIALOGUE("dialogue"),
    
    /**
     * Task objectives: fetch/investigate items, escort, stealth segments.
     * Tracks objective progress per party.
     */
    TASK("task"),
    
    /**
     * Interactive puzzles: code locks, wiring, symbol matching, clue board linking.
     * Can have multiple attempts and hints.
     */
    PUZZLE("puzzle"),
    
    /**
     * Combat encounters: waves, boss fights, escape sequences.
     * Supports arena bounds and victory/defeat conditions.
     */
    COMBAT("combat"),
    
    /**
     * Scripted cutscenes: camera paths, teleports, particle FX.
     * Lightweight Minecraft-style cinematics.
     */
    CUTSCENE("cutscene"),
    
    /**
     * Savepoints with optional "rewind" anchor capability.
     * Stores inventory, position, and story state.
     */
    CHECKPOINT("checkpoint"),
    
    /**
     * Item/vehicle interactions: getting on buses, using objects, etc.
     * Tracks when player interacts with spawned entities.
     */
    ITEM_INTERACTION("item_interaction");
    
    private final String id;
    
    NodeType(String id) {
        this.id = id;
    }
    
    public String getId() {
        return id;
    }
    
    public static NodeType fromId(String id) {
        for (NodeType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node type: " + id);
    }
}
