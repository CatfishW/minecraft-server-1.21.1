package com.warmpixel.storyadventure.core.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for persistent global waypoints.
 * These are waypoints that exist outside of specific story instances.
 */
public class WaypointManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaypointManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static WaypointManager instance;
    private final Map<String, Waypoint> waypoints = new ConcurrentHashMap<>();
    private final Path configDir;
    private final File storageFile;
    
    public WaypointManager(Path configDir) {
        this.configDir = configDir;
        this.storageFile = configDir.resolve("waypoints.json").toFile();
        instance = this;
    }
    
    public static WaypointManager getInstance() {
        return instance;
    }
    
    /**
     * Load waypoints from waypoints.json.
     */
    public void load() {
        if (!storageFile.exists()) {
            LOGGER.info("No waypoints.json found, starting fresh.");
            return;
        }
        
        try (FileReader reader = new FileReader(storageFile)) {
            List<WaypointDTO> dtos = GSON.fromJson(reader, new TypeToken<List<WaypointDTO>>(){}.getType());
            if (dtos != null) {
                waypoints.clear();
                for (WaypointDTO dto : dtos) {
                    Waypoint wp = new Waypoint(dto.id, new Vec3(dto.x, dto.y, dto.z));
                    wp.setLabel(dto.label)
                      .setIcon(Waypoint.WaypointIcon.fromId(dto.icon))
                      .setColor(dto.color)
                      .setShowDistance(dto.showDistance)
                      .setLinkedTriggerId(dto.linkedTriggerId);
                    waypoints.put(dto.id, wp);
                }
            }
            LOGGER.info("Loaded {} global waypoints", waypoints.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load global waypoints", e);
        }
    }
    
    /**
     * Save waypoints to waypoints.json.
     */
    public void save() {
        try {
            if (!storageFile.getParentFile().exists()) {
                storageFile.getParentFile().mkdirs();
            }
            
            List<WaypointDTO> dtos = new ArrayList<>();
            for (Waypoint wp : waypoints.values()) {
                dtos.add(new WaypointDTO(
                    wp.getId(), wp.getLabel(), 
                    wp.getPosition().x, wp.getPosition().y, wp.getPosition().z,
                    wp.getIcon().getId(), wp.getColor(), wp.showsDistance(),
                    wp.getLinkedTriggerId()
                ));
            }
            
            try (FileWriter writer = new FileWriter(storageFile)) {
                GSON.toJson(dtos, writer);
            }
            LOGGER.info("Saved {} global waypoints", waypoints.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save global waypoints", e);
        }
    }
    
    public void createWaypoint(String id, Vec3 pos, String label) {
        Waypoint wp = new Waypoint(id, pos).setLabel(label);
        waypoints.put(id, wp);
        save();
    }
    
    public boolean deleteWaypoint(String id) {
        if (waypoints.remove(id) != null) {
            save();
            return true;
        }
        return false;
    }
    
    public Waypoint getWaypoint(String id) {
        return waypoints.get(id);
    }
    
    public List<Waypoint> getAllWaypoints() {
        return new ArrayList<>(waypoints.values());
    }
    
    public int getWaypointCount() {
        return waypoints.size();
    }
    
    /**
     * Data Transfer Object for Waypoint JSON serialization.
     */
    private static class WaypointDTO {
        String id;
        String label;
        double x, y, z;
        String icon;
        int color;
        boolean showDistance;
        String linkedTriggerId;
        
        public WaypointDTO(String id, String label, double x, double y, double z,
                           String icon, int color, boolean showDistance, String linkedTriggerId) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.z = z;
            this.icon = icon;
            this.color = color;
            this.showDistance = showDistance;
            this.linkedTriggerId = linkedTriggerId;
        }
    }
}
