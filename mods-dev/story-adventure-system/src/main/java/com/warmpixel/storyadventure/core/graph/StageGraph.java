package com.warmpixel.storyadventure.core.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.core.condition.ConditionFactory;
import com.warmpixel.storyadventure.core.condition.EdgeCondition;

import java.util.*;

/**
 * The main Stage Graph class representing a complete story.
 * This is a directed graph where nodes are story beats and edges are conditional transitions.
 */
public class StageGraph {
    private final String storyId;
    private final String name;
    private final String description;
    private final String version;
    private final int minPlayers;
    private final int maxPlayers;
    private final int estimatedDurationMinutes;
    private final String entryNodeId;
    private final Map<String, StageNode> nodes;
    private final Map<String, ClueDefinition> clues;
    private final Map<String, FlagDefinition> flags;
    private final Map<String, StoryLocation> specialLocations = new HashMap<>();

    public record StoryLocation(String dimension, double x, double y, double z, float yaw, float pitch) {}
    
    private StageGraph(Builder builder) {
        this.storyId = builder.storyId;
        this.name = builder.name;
        this.description = builder.description;
        this.version = builder.version;
        this.minPlayers = builder.minPlayers;
        this.maxPlayers = builder.maxPlayers;
        this.estimatedDurationMinutes = builder.estimatedDurationMinutes;
        this.entryNodeId = builder.entryNodeId;
        this.nodes = Collections.unmodifiableMap(new HashMap<>(builder.nodes));
        this.clues = Collections.unmodifiableMap(new HashMap<>(builder.clues));
        this.flags = Collections.unmodifiableMap(new HashMap<>(builder.flags));
        this.specialLocations.putAll(builder.specialLocations);
    }
    
    // Getters
    public String getStoryId() { return storyId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getVersion() { return version; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public String getEntryNodeId() { return entryNodeId; }
    
    public StageNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }
    
    public StageNode getEntryNode() {
        return nodes.get(entryNodeId);
    }
    
    public Collection<StageNode> getAllNodes() {
        return nodes.values();
    }
    
    public int getNodeCount() {
        return nodes.size();
    }
    
    public ClueDefinition getClue(String clueId) {
        return clues.get(clueId);
    }
    
    public Collection<ClueDefinition> getAllClues() {
        return clues.values();
    }
    
    public FlagDefinition getFlag(String flagId) {
        return flags.get(flagId);
    }
    
    public Collection<FlagDefinition> getAllFlags() {
        return flags.values();
    }
    
    public boolean hasNode(String nodeId) {
        return nodes.containsKey(nodeId);
    }
    
    public void setSpecialLocation(String id, StoryLocation location) {
        specialLocations.put(id, location);
    }
    
    public StoryLocation getSpecialLocation(String id) {
        return specialLocations.get(id);
    }
    
    /**
     * Parse a StageGraph from a JSON object.
     */
    public static StageGraph fromJson(JsonObject json) {
        Builder builder = new Builder(
            json.get("id").getAsString(),
            json.get("entry_node").getAsString()
        );
        
        builder.name(json.has("name") ? json.get("name").getAsString() : "Unnamed Story");
        builder.description(json.has("description") ? json.get("description").getAsString() : "");
        builder.version(json.has("version") ? json.get("version").getAsString() : "1.0.0");
        builder.minPlayers(json.has("min_players") ? json.get("min_players").getAsInt() : 1);
        builder.maxPlayers(json.has("max_players") ? json.get("max_players").getAsInt() : 4);
        builder.estimatedDurationMinutes(json.has("estimated_duration_minutes") ? 
            json.get("estimated_duration_minutes").getAsInt() : 60);
        
        // Parse nodes
        JsonObject nodesJson = json.getAsJsonObject("nodes");
        for (Map.Entry<String, JsonElement> entry : nodesJson.entrySet()) {
            String nodeId = entry.getKey();
            JsonObject nodeJson = entry.getValue().getAsJsonObject();
            
            NodeType type = NodeType.fromId(nodeJson.get("type").getAsString());
            JsonObject data = nodeJson.has("data") ? nodeJson.getAsJsonObject("data") : new JsonObject();
            
            StageNode node = new StageNode(nodeId, type, data);
            
            // Parse edges
            if (nodeJson.has("edges")) {
                JsonArray edgesJson = nodeJson.getAsJsonArray("edges");
                for (JsonElement edgeElement : edgesJson) {
                    JsonObject edgeJson = edgeElement.getAsJsonObject();
                    String targetId = edgeJson.get("target").getAsString();
                    int priority = edgeJson.has("priority") ? edgeJson.get("priority").getAsInt() : 0;
                    
                    List<EdgeCondition> conditions = new ArrayList<>();
                    if (edgeJson.has("conditions")) {
                        JsonArray conditionsJson = edgeJson.getAsJsonArray("conditions");
                        for (JsonElement condElement : conditionsJson) {
                            EdgeCondition condition = ConditionFactory.fromJson(condElement.getAsJsonObject());
                            if (condition != null) {
                                conditions.add(condition);
                            }
                        }
                    }
                    
                    node.addEdge(new StageEdge(nodeId, targetId, conditions, priority));
                }
            }
            
            builder.addNode(node);
        }
        
        // Parse clues
        if (json.has("clues")) {
            JsonObject cluesJson = json.getAsJsonObject("clues");
            for (Map.Entry<String, JsonElement> entry : cluesJson.entrySet()) {
                JsonObject clueJson = entry.getValue().getAsJsonObject();
                ClueDefinition clue = new ClueDefinition(
                    entry.getKey(),
                    clueJson.has("name") ? clueJson.get("name").getAsString() : entry.getKey(),
                    clueJson.has("description") ? clueJson.get("description").getAsString() : "",
                    clueJson.has("item_icon") ? clueJson.get("item_icon").getAsString() : "minecraft:paper"
                );
                builder.addClue(clue);
            }
        }
        
        // Parse flags
        if (json.has("flags")) {
            JsonObject flagsJson = json.getAsJsonObject("flags");
            for (Map.Entry<String, JsonElement> entry : flagsJson.entrySet()) {
                JsonObject flagJson = entry.getValue().getAsJsonObject();
                FlagDefinition flag = new FlagDefinition(
                    entry.getKey(),
                    flagJson.has("default") ? flagJson.get("default").getAsBoolean() : false,
                    flagJson.has("persistent") ? flagJson.get("persistent").getAsBoolean() : false
                );
                builder.addFlag(flag);
            }
        }

        // Parse locations
        if (json.has("locations")) {
            JsonObject locationsJson = json.getAsJsonObject("locations");
            for (Map.Entry<String, JsonElement> entry : locationsJson.entrySet()) {
                JsonObject locJson = entry.getValue().getAsJsonObject();
                StoryLocation location = new StoryLocation(
                    locJson.get("dimension").getAsString(),
                    locJson.get("x").getAsDouble(),
                    locJson.get("y").getAsDouble(),
                    locJson.get("z").getAsDouble(),
                    locJson.has("yaw") ? locJson.get("yaw").getAsFloat() : 0f,
                    locJson.has("pitch") ? locJson.get("pitch").getAsFloat() : 0f
                );
                builder.addLocation(entry.getKey(), location);
            }
        }
        
        return builder.build();
    }
    
    @Override
    public String toString() {
        return String.format("StageGraph{id='%s', name='%s', nodes=%d, entry='%s'}", 
            storyId, name, nodes.size(), entryNodeId);
    }
    
    /**
     * Builder for constructing StageGraph instances.
     */
    public static class Builder {
        private final String storyId;
        private final String entryNodeId;
        private String name = "Unnamed Story";
        private String description = "";
        private String version = "1.0.0";
        private int minPlayers = 1;
        private int maxPlayers = 4;
        private int estimatedDurationMinutes = 60;
        private final Map<String, StageNode> nodes = new HashMap<>();
        private final Map<String, ClueDefinition> clues = new HashMap<>();
        private final Map<String, FlagDefinition> flags = new HashMap<>();
        private final Map<String, StoryLocation> specialLocations = new HashMap<>();
        
        public Builder(String storyId, String entryNodeId) {
            this.storyId = storyId;
            this.entryNodeId = entryNodeId;
        }
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder minPlayers(int minPlayers) { this.minPlayers = minPlayers; return this; }
        public Builder maxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; return this; }
        public Builder estimatedDurationMinutes(int minutes) { this.estimatedDurationMinutes = minutes; return this; }
        
        public Builder addNode(StageNode node) {
            nodes.put(node.getId(), node);
            return this;
        }
        
        public Builder addClue(ClueDefinition clue) {
            clues.put(clue.id(), clue);
            return this;
        }
        
        public Builder addFlag(FlagDefinition flag) {
            flags.put(flag.id(), flag);
            return this;
        }

        public Builder addLocation(String id, StoryLocation location) {
            specialLocations.put(id, location);
            return this;
        }
        
        public StageGraph build() {
            if (!nodes.containsKey(entryNodeId)) {
                throw new IllegalStateException("Entry node '" + entryNodeId + "' not found in graph");
            }
            return new StageGraph(this);
        }
    }
    
    /**
     * Definition of a collectible clue.
     */
    public record ClueDefinition(String id, String name, String description, String itemIcon) {}
    
    /**
     * Definition of a story flag.
     */
    public record FlagDefinition(String id, boolean defaultValue, boolean persistent) {}
}
