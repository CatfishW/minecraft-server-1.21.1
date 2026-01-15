package com.warmpixel.storyadventure.core.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.world.phys.AABB;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for global trigger boxes (not tied to a specific instance).
 * Used for admin-created boxes that persist between server restarts.
 */
public class TriggerBoxManager {
    
    private static TriggerBoxManager instance;
    private final Map<String, TriggerBox> globalBoxes = new ConcurrentHashMap<>();
    private final Path configPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public TriggerBoxManager(Path configDir) {
        this.configPath = configDir.resolve("trigger_boxes.json");
        instance = this;
    }
    
    public static TriggerBoxManager getInstance() {
        return instance;
    }
    
    /**
     * Load trigger boxes from config file.
     */
    public void load() {
        globalBoxes.clear();
        
        if (!Files.exists(configPath)) {
            StoryAdventureMod.LOGGER.info("No trigger boxes config found, starting fresh");
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            if (root != null && root.has("boxes")) {
                JsonArray boxes = root.getAsJsonArray("boxes");
                for (var elem : boxes) {
                    JsonObject boxJson = elem.getAsJsonObject();
                    String id = boxJson.get("id").getAsString();
                    TriggerBox box = TriggerBox.fromJson(id, boxJson);
                    globalBoxes.put(id, box);
                }
                StoryAdventureMod.LOGGER.info("Loaded {} global trigger boxes", globalBoxes.size());
            }
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("Failed to load trigger boxes", e);
        }
    }
    
    /**
     * Save trigger boxes to config file.
     */
    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            
            JsonObject root = new JsonObject();
            JsonArray boxes = new JsonArray();
            
            for (TriggerBox box : globalBoxes.values()) {
                JsonObject boxJson = box.toJson();
                boxJson.addProperty("id", box.getId());
                boxes.add(boxJson);
            }
            
            root.add("boxes", boxes);
            
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            
            StoryAdventureMod.LOGGER.info("Saved {} global trigger boxes", globalBoxes.size());
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("Failed to save trigger boxes", e);
        }
    }
    
    /**
     * Create a new trigger box.
     */
    public TriggerBox createBox(String id, AABB bounds, String label) {
        TriggerBox box = new TriggerBox(id, bounds);
        box.setLabel(label);
        globalBoxes.put(id, box);
        save();
        return box;
    }
    
    /**
     * Get a trigger box by ID.
     */
    public TriggerBox getBox(String id) {
        return globalBoxes.get(id);
    }
    
    /**
     * Get all global trigger boxes.
     */
    public Collection<TriggerBox> getAllBoxes() {
        return Collections.unmodifiableCollection(globalBoxes.values());
    }
    
    /**
     * Delete a trigger box.
     */
    public boolean deleteBox(String id) {
        TriggerBox removed = globalBoxes.remove(id);
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }
    
    /**
     * Update a trigger box.
     */
    public void updateBox(String id, TriggerBox updated) {
        globalBoxes.put(id, updated);
        save();
    }
    
    /**
     * Get box count.
     */
    public int getBoxCount() {
        return globalBoxes.size();
    }
}
