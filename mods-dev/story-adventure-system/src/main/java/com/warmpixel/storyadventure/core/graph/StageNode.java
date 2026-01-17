package com.warmpixel.storyadventure.core.graph;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.core.condition.EdgeCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a node in the Stage Graph.
 * Each node has a type, associated data, and outgoing edges to other nodes.
 */
public class StageNode {
    private final String id;
    private final NodeType type;
    private final JsonObject data;
    private final List<StageEdge> edges;
    
    public StageNode(String id, NodeType type, JsonObject data) {
        this.id = id;
        this.type = type;
        this.data = data;
        this.edges = new ArrayList<>();
    }
    
    public String getId() {
        return id;
    }
    
    public NodeType getType() {
        return type;
    }
    
    public JsonObject getData() {
        return data;
    }
    
    public List<StageEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }
    
    public void addEdge(StageEdge edge) {
        edges.add(edge);
    }
    
    /**
     * Get a string data field with default value.
     */
    public String getString(String key, String defaultValue) {
        if (data.has(key) && data.get(key).isJsonPrimitive()) {
            return data.get(key).getAsString();
        }
        return defaultValue;
    }
    
    /**
     * Get an int data field with default value.
     */
    public int getInt(String key, int defaultValue) {
        if (data.has(key) && data.get(key).isJsonPrimitive()) {
            return data.get(key).getAsInt();
        }
        return defaultValue;
    }
    
    /**
     * Get a boolean data field with default value.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        if (data.has(key) && data.get(key).isJsonPrimitive()) {
            return data.get(key).getAsBoolean();
        }
        return defaultValue;
    }
    
    /**
     * Get a float data field with default value.
     */
    public float getFloat(String key, float defaultValue) {
        if (data.has(key) && data.get(key).isJsonPrimitive()) {
            return data.get(key).getAsFloat();
        }
        return defaultValue;
    }
    
    /**
     * Get a double data field with default value.
     */
    public double getDouble(String key, double defaultValue) {
        if (data.has(key) && data.get(key).isJsonPrimitive()) {
            return data.get(key).getAsDouble();
        }
        return defaultValue;
    }
    
    /**
     * Get a nested JsonObject.
     */
    public JsonObject getObject(String key) {
        if (data.has(key) && data.get(key).isJsonObject()) {
            return data.get(key).getAsJsonObject();
        }
        return null;
    }
    
    @Override
    public String toString() {
        return String.format("StageNode{id='%s', type=%s, edges=%d}", id, type, edges.size());
    }
}
