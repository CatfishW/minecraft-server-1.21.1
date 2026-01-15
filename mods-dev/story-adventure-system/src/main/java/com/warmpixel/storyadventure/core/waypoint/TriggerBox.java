package com.warmpixel.storyadventure.core.waypoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.core.action.ActionFactory;
import com.warmpixel.storyadventure.core.action.NodeAction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a trigger box zone that fires actions when players enter/exit.
 * Used for story-driven events like triggering cutscenes, setting flags, etc.
 */
public class TriggerBox {
    
    private final String id;
    private String label;
    private AABB bounds;
    private Vec3 center;
    private double radius = -1;
    private List<NodeAction> onEnterActions;
    private List<NodeAction> onExitActions;
    private String linkedNodeId;
    private boolean oneShot;
    private double targetDistance = -1;
    
    // Track which players are currently inside
    private final Set<UUID> playersInside = new HashSet<>();
    // Track which players have already triggered this (for oneShot)
    private final Set<UUID> triggeredPlayers = new HashSet<>();
    
    public TriggerBox(String id, AABB bounds) {
        this.id = id;
        this.bounds = bounds;
        this.label = id;
        this.onEnterActions = new ArrayList<>();
        this.onExitActions = new ArrayList<>();
        this.oneShot = false;
    }
    
    public TriggerBox(String id, Vec3 center, double radius) {
        this.id = id;
        this.center = center;
        this.radius = radius;
        this.label = id;
        this.onEnterActions = new ArrayList<>();
        this.onExitActions = new ArrayList<>();
        this.oneShot = false;
        // Create a loose AABB for rendering/gizmo purposes if needed
        this.bounds = new AABB(center.x - radius, center.y - radius, center.z - radius, 
                               center.x + radius, center.y + radius, center.z + radius);
    }
    
    public TriggerBox(String id, Vec3 corner1, Vec3 corner2) {
        this(id, new AABB(corner1, corner2));
    }
    
    /**
     * Check if a player position is inside this trigger box.
     */
    public boolean contains(Vec3 position) {
        return contains(position, null);
    }
    
    public boolean contains(Vec3 position, Vec3 referencePoint) {
        if (targetDistance > 0 && referencePoint != null) {
            return position.distanceTo(referencePoint) <= targetDistance;
        }
        if (radius > 0 && center != null) {
            double dx = position.x - center.x;
            double dy = position.y - center.y;
            double dz = position.z - center.z;
            return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
        }
        return bounds != null && bounds.contains(position);
    }
    
    /**
     * Process a player entering/exiting this trigger.
     * Returns true if state changed (player entered or exited).
     */
    public TriggerEvent checkPlayer(UUID playerId, Vec3 position) {
        return checkPlayer(playerId, position, null);
    }
    
    public TriggerEvent checkPlayer(UUID playerId, Vec3 position, Vec3 referencePoint) {
        boolean inside = contains(position, referencePoint);
        boolean wasInside = playersInside.contains(playerId);
        
        if (inside && !wasInside) {
            playersInside.add(playerId);
            
            // If one-shot, check if already triggered for this player
            if (oneShot && triggeredPlayers.contains(playerId)) {
                return TriggerEvent.NONE;
            }
            
            triggeredPlayers.add(playerId);
            return TriggerEvent.ENTER;
        } else if (!inside && wasInside) {
            playersInside.remove(playerId);
            return TriggerEvent.EXIT;
        }
        return TriggerEvent.NONE;
    }
    
    /**
     * Reset tracking for a player (e.g., when they leave the instance).
     */
    public void resetPlayer(UUID playerId) {
        playersInside.remove(playerId);
        triggeredPlayers.remove(playerId);
    }
    
    /**
     * Parse a TriggerBox from JSON.
     */
    public static TriggerBox fromJson(String id, JsonObject json) {
        TriggerBox box;
        
        if (json.has("center") && json.has("radius")) {
            JsonArray centerArr = json.getAsJsonArray("center");
            Vec3 center = new Vec3(centerArr.get(0).getAsDouble(), centerArr.get(1).getAsDouble(), centerArr.get(2).getAsDouble());
            double radius = json.get("radius").getAsDouble();
            box = new TriggerBox(id, center, radius);
        } else if (json.has("min") && json.has("max")) {
            double minX = json.getAsJsonArray("min").get(0).getAsDouble();
            double minY = json.getAsJsonArray("min").get(1).getAsDouble();
            double minZ = json.getAsJsonArray("min").get(2).getAsDouble();
            double maxX = json.getAsJsonArray("max").get(0).getAsDouble();
            double maxY = json.getAsJsonArray("max").get(1).getAsDouble();
            double maxZ = json.getAsJsonArray("max").get(2).getAsDouble();
            box = new TriggerBox(id, new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        } else {
            // Fallback/Error case
            box = new TriggerBox(id, new AABB(0, 0, 0, 0, 0, 0));
        }
        
        if (json.has("label")) {
            box.setLabel(json.get("label").getAsString());
        }
        
        if (json.has("linkedNodeId")) {
            box.setLinkedNodeId(json.get("linkedNodeId").getAsString());
        }
        
        if (json.has("oneShot")) {
            box.setOneShot(json.get("oneShot").getAsBoolean());
        }
        
        if (json.has("target_distance")) {
            box.setTargetDistance(json.get("target_distance").getAsDouble());
        }
        
        if (json.has("onEnter")) {
            JsonArray actions = json.getAsJsonArray("onEnter");
            for (var elem : actions) {
                NodeAction action = ActionFactory.fromJson(elem.getAsJsonObject());
                if (action != null) box.onEnterActions.add(action);
            }
        }
        
        if (json.has("onExit")) {
            JsonArray actions = json.getAsJsonArray("onExit");
            for (var elem : actions) {
                NodeAction action = ActionFactory.fromJson(elem.getAsJsonObject());
                if (action != null) box.onExitActions.add(action);
            }
        }
        
        return box;
    }
    
    /**
     * Serialize this TriggerBox to JSON.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("label", label);
        
        JsonArray min = new JsonArray();
        min.add(bounds.minX);
        min.add(bounds.minY);
        min.add(bounds.minZ);
        json.add("min", min);
        
        JsonArray max = new JsonArray();
        max.add(bounds.maxX);
        max.add(bounds.maxY);
        max.add(bounds.maxZ);
        json.add("max", max);
        
        if (linkedNodeId != null) {
            json.addProperty("linkedNodeId", linkedNodeId);
        }
        
        json.addProperty("oneShot", oneShot);
        
        if (targetDistance > 0) {
            json.addProperty("target_distance", targetDistance);
        }
        
        JsonArray enterArr = new JsonArray();
        for (NodeAction action : onEnterActions) {
            enterArr.add(action.toJson());
        }
        json.add("onEnter", enterArr);
        
        JsonArray exitArr = new JsonArray();
        for (NodeAction action : onExitActions) {
            exitArr.add(action.toJson());
        }
        json.add("onExit", exitArr);
        
        return json;
    }
    
    // Getters
    public String getId() { return id; }
    public String getLabel() { return label; }
    public AABB getBounds() { return bounds; }
    public List<NodeAction> getOnEnterActions() { return onEnterActions; }
    public List<NodeAction> getOnExitActions() { return onExitActions; }
    public String getLinkedNodeId() { return linkedNodeId; }
    public boolean isOneShot() { return oneShot; }
    public double getTargetDistance() { return targetDistance; }
    public Set<UUID> getPlayersInside() { return playersInside; }
    public double getRadius() { return radius; }
    public Vec3 getCenter() { return center; }
    
    // Setters
    public TriggerBox setLabel(String label) { this.label = label; return this; }
    public TriggerBox setBounds(AABB bounds) { this.bounds = bounds; return this; }
    public TriggerBox setLinkedNodeId(String nodeId) { this.linkedNodeId = nodeId; return this; }
    public TriggerBox setOneShot(boolean oneShot) { this.oneShot = oneShot; return this; }
    public TriggerBox setTargetDistance(double dist) { this.targetDistance = dist; return this; }
    
    public enum TriggerEvent {
        NONE, ENTER, EXIT
    }
}
