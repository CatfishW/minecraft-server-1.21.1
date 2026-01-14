package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks relationship level with an NPC.
 */
public class RelationshipCondition implements EdgeCondition {
    public static final String TYPE = "RELATIONSHIP";
    
    private final String npcId;
    private final int threshold;
    private final CompareOp operation;
    
    public enum CompareOp {
        GREATER_THAN, LESS_THAN, EQUALS, GREATER_OR_EQUAL, LESS_OR_EQUAL
    }
    
    public RelationshipCondition(String npcId, int threshold, CompareOp operation) {
        this.npcId = npcId;
        this.threshold = threshold;
        this.operation = operation;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        if (player == null) return false;
        
        int relationship = instance.getState().getRelationship(player.getUUID(), npcId);
        
        return switch (operation) {
            case GREATER_THAN -> relationship > threshold;
            case LESS_THAN -> relationship < threshold;
            case EQUALS -> relationship == threshold;
            case GREATER_OR_EQUAL -> relationship >= threshold;
            case LESS_OR_EQUAL -> relationship <= threshold;
        };
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("npc", npcId);
        json.addProperty("threshold", threshold);
        json.addProperty("operation", operation.name());
        return json;
    }
    
    @Override
    public String getDescription() {
        String op = switch (operation) {
            case GREATER_THAN -> ">";
            case LESS_THAN -> "<";
            case EQUALS -> "=";
            case GREATER_OR_EQUAL -> ">=";
            case LESS_OR_EQUAL -> "<=";
        };
        return "Relationship with " + npcId + " " + op + " " + threshold;
    }
    
    public static RelationshipCondition fromJson(JsonObject json) {
        String npcId = json.get("npc").getAsString();
        int threshold = json.get("threshold").getAsInt();
        CompareOp op = json.has("operation") ? 
            CompareOp.valueOf(json.get("operation").getAsString().toUpperCase()) : 
            CompareOp.GREATER_OR_EQUAL;
        return new RelationshipCondition(npcId, threshold, op);
    }
}
