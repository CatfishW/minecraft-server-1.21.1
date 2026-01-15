package com.warmpixel.storyadventure.core.waypoint;

import net.minecraft.world.phys.Vec3;

/**
 * Represents a waypoint marker for player guidance.
 * Waypoints show on-screen or off-screen indicators pointing players toward objectives.
 */
public class Waypoint {
    
    private final String id;
    private String label;
    private Vec3 position;
    private WaypointIcon icon;
    private int color;
    private boolean showDistance;
    private String linkedTriggerId;
    
    public Waypoint(String id, Vec3 position) {
        this.id = id;
        this.position = position;
        this.label = "";
        this.icon = WaypointIcon.OBJECTIVE;
        this.color = 0xFFFFCC00; // Default gold
        this.showDistance = true;
    }
    
    // Getters
    public String getId() { return id; }
    public String getLabel() { return label; }
    public Vec3 getPosition() { return position; }
    public WaypointIcon getIcon() { return icon; }
    public int getColor() { return color; }
    public boolean showsDistance() { return showDistance; }
    public String getLinkedTriggerId() { return linkedTriggerId; }
    
    // Setters
    public Waypoint setLabel(String label) { this.label = label; return this; }
    public Waypoint setPosition(Vec3 position) { this.position = position; return this; }
    public Waypoint setIcon(WaypointIcon icon) { this.icon = icon; return this; }
    public Waypoint setColor(int color) { this.color = color; return this; }
    public Waypoint setShowDistance(boolean show) { this.showDistance = show; return this; }
    public Waypoint setLinkedTriggerId(String id) { this.linkedTriggerId = id; return this; }
    
    public enum WaypointIcon {
        OBJECTIVE("objective", "◉"),
        QUEST("quest", "!"),
        DANGER("danger", "⚠"),
        CLUE("clue", "?"),
        EXIT("exit", "→"),
        NPC("npc", "☺"),
        ITEM("item", "★");
        
        private final String id;
        private final String symbol;
        
        WaypointIcon(String id, String symbol) {
            this.id = id;
            this.symbol = symbol;
        }
        
        public String getId() { return id; }
        public String getSymbol() { return symbol; }
        
        public static WaypointIcon fromId(String id) {
            for (WaypointIcon icon : values()) {
                if (icon.id.equalsIgnoreCase(id)) return icon;
            }
            return OBJECTIVE;
        }
    }
}
