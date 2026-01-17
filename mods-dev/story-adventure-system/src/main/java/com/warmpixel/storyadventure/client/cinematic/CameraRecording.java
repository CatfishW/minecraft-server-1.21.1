package com.warmpixel.storyadventure.client.cinematic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a collection of recorded camera keyframes.
 * Used by the Camera Recorder UI to store and save camera path recordings.
 */
public class CameraRecording {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    
    private String name;
    private final List<RecordedKeyframe> keyframes;
    private LocalDateTime createdAt;
    private CameraPath.InterpolationMode positionInterpolation = CameraPath.InterpolationMode.CATMULL_ROM;
    private CameraPath.InterpolationMode rotationInterpolation = CameraPath.InterpolationMode.SMOOTH_LERP;
    
    public CameraRecording() {
        this.name = "Untitled Recording";
        this.keyframes = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
    }
    
    public CameraRecording(String name) {
        this.name = name;
        this.keyframes = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
    }
    
    // ==================== Keyframe Operations ====================
    
    public void addKeyframe(RecordedKeyframe keyframe) {
        keyframes.add(keyframe);
    }
    
    public void addKeyframe(double x, double y, double z, float yaw, float pitch, float fov, 
                            int durationTicks, String easing) {
        keyframes.add(new RecordedKeyframe(x, y, z, yaw, pitch, 0f, fov, durationTicks, easing));
    }
    
    public void removeKeyframe(int index) {
        if (index >= 0 && index < keyframes.size()) {
            keyframes.remove(index);
        }
    }
    
    public void updateKeyframe(int index, RecordedKeyframe keyframe) {
        if (index >= 0 && index < keyframes.size()) {
            keyframes.set(index, keyframe);
        }
    }
    
    public void clearKeyframes() {
        keyframes.clear();
    }
    
    public RecordedKeyframe getKeyframe(int index) {
        if (index >= 0 && index < keyframes.size()) {
            return keyframes.get(index);
        }
        return null;
    }
    
    public List<RecordedKeyframe> getKeyframes() {
        return new ArrayList<>(keyframes);
    }
    
    public int getKeyframeCount() {
        return keyframes.size();
    }
    
    // ==================== Getters/Setters ====================
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public CameraPath.InterpolationMode getPositionInterpolation() {
        return positionInterpolation;
    }
    
    public void setPositionInterpolation(CameraPath.InterpolationMode mode) {
        this.positionInterpolation = mode;
    }
    
    public CameraPath.InterpolationMode getRotationInterpolation() {
        return rotationInterpolation;
    }
    
    public void setRotationInterpolation(CameraPath.InterpolationMode mode) {
        this.rotationInterpolation = mode;
    }
    
    // ==================== Conversion ====================
    
    /**
     * Convert this recording to a CameraPath for preview playback.
     */
    public CameraPath toCameraPath() {
        List<CameraKeyframe> pathKeyframes = new ArrayList<>();
        
        for (RecordedKeyframe kf : keyframes) {
            pathKeyframes.add(new CameraKeyframe(
                new net.minecraft.world.phys.Vec3(kf.x, kf.y, kf.z),
                kf.yaw, kf.pitch, kf.roll,
                kf.fov, kf.durationTicks,
                EasingFunction.fromString(kf.easing)
            ));
        }
        
        CameraPath path = new CameraPath(pathKeyframes, null);
        path.setPositionInterpolation(positionInterpolation);
        path.setRotationInterpolation(rotationInterpolation);
        return path;
    }
    
    /**
     * Generate the camera_path JSON object for use in story files.
     */
    public JsonObject toCameraPathJson() {
        JsonObject pathObj = new JsonObject();
        JsonArray keyframesArr = new JsonArray();
        
        for (RecordedKeyframe kf : keyframes) {
            JsonObject kfObj = new JsonObject();
            
            JsonArray posArr = new JsonArray();
            posArr.add(round(kf.x, 2));
            posArr.add(round(kf.y, 2));
            posArr.add(round(kf.z, 2));
            kfObj.add("position", posArr);
            
            JsonArray rotArr = new JsonArray();
            rotArr.add(round(kf.yaw, 1));
            rotArr.add(round(kf.pitch, 1));
            rotArr.add(round(kf.roll, 1));
            kfObj.add("rotation", rotArr);
            
            kfObj.addProperty("fov", round(kf.fov, 1));
            kfObj.addProperty("duration_ticks", kf.durationTicks);
            kfObj.addProperty("easing", kf.easing);
            
            keyframesArr.add(kfObj);
        }
        
        pathObj.add("keyframes", keyframesArr);
        pathObj.addProperty("position_interpolation", positionInterpolation.name());
        pathObj.addProperty("rotation_interpolation", rotationInterpolation.name());
        return pathObj;
    }
    
    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
    
    // ==================== JSON Serialization ====================
    
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("created_at", createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        json.addProperty("position_interpolation", positionInterpolation.name());
        json.addProperty("rotation_interpolation", rotationInterpolation.name());
        
        JsonArray keyframesArr = new JsonArray();
        for (RecordedKeyframe kf : keyframes) {
            JsonObject kfObj = new JsonObject();
            
            JsonArray posArr = new JsonArray();
            posArr.add(kf.x);
            posArr.add(kf.y);
            posArr.add(kf.z);
            kfObj.add("position", posArr);
            
            JsonArray rotArr = new JsonArray();
            rotArr.add(kf.yaw);
            rotArr.add(kf.pitch);
            rotArr.add(kf.roll);
            kfObj.add("rotation", rotArr);
            
            kfObj.addProperty("fov", kf.fov);
            kfObj.addProperty("duration_ticks", kf.durationTicks);
            kfObj.addProperty("easing", kf.easing);
            
            keyframesArr.add(kfObj);
        }
        json.add("keyframes", keyframesArr);
        
        // Also include ready-to-use camera_path format
        json.add("camera_path", toCameraPathJson());
        
        return json;
    }
    
    public static CameraRecording fromJson(JsonObject json) {
        CameraRecording recording = new CameraRecording();
        
        if (json.has("name")) {
            recording.name = json.get("name").getAsString();
        }
        
        if (json.has("created_at")) {
            try {
                recording.createdAt = LocalDateTime.parse(json.get("created_at").getAsString());
            } catch (Exception e) {
                recording.createdAt = LocalDateTime.now();
            }
        }
        
        if (json.has("position_interpolation")) {
            try {
                recording.positionInterpolation = CameraPath.InterpolationMode.valueOf(
                    json.get("position_interpolation").getAsString().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        
        if (json.has("rotation_interpolation")) {
            try {
                recording.rotationInterpolation = CameraPath.InterpolationMode.valueOf(
                    json.get("rotation_interpolation").getAsString().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        
        if (json.has("keyframes") && json.get("keyframes").isJsonArray()) {
            for (var elem : json.getAsJsonArray("keyframes")) {
                if (!elem.isJsonObject()) continue;
                JsonObject kfObj = elem.getAsJsonObject();
                
                double x = 0, y = 0, z = 0;
                if (kfObj.has("position") && kfObj.get("position").isJsonArray()) {
                    JsonArray posArr = kfObj.getAsJsonArray("position");
                    if (posArr.size() >= 3) {
                        x = posArr.get(0).getAsDouble();
                        y = posArr.get(1).getAsDouble();
                        z = posArr.get(2).getAsDouble();
                    }
                }
                
                float yaw = 0, pitch = 0, roll = 0;
                if (kfObj.has("rotation") && kfObj.get("rotation").isJsonArray()) {
                    JsonArray rotArr = kfObj.getAsJsonArray("rotation");
                    if (rotArr.size() >= 2) {
                        yaw = rotArr.get(0).getAsFloat();
                        pitch = rotArr.get(1).getAsFloat();
                    }
                    if (rotArr.size() >= 3) {
                        roll = rotArr.get(2).getAsFloat();
                    }
                }
                
                float fov = kfObj.has("fov") ? kfObj.get("fov").getAsFloat() : 70f;
                int durationTicks = kfObj.has("duration_ticks") ? kfObj.get("duration_ticks").getAsInt() : 60;
                String easing = kfObj.has("easing") ? kfObj.get("easing").getAsString() : "LINEAR";
                
                recording.addKeyframe(new RecordedKeyframe(x, y, z, yaw, pitch, roll, fov, durationTicks, easing));
            }
        }
        
        return recording;
    }
    
    // ==================== File I/O ====================
    
    /**
     * Get the directory for camera recordings.
     */
    public static Path getRecordingsDirectory() {
        return Path.of("config", "storyadventure", "camera_recordings");
    }
    
    /**
     * Save this recording to a file with auto-generated filename.
     * @return The path to the saved file
     */
    public Path saveToFile() throws IOException {
        Path dir = getRecordingsDirectory();
        Files.createDirectories(dir);
        
        String filename = "recording_" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".json";
        Path file = dir.resolve(filename);
        
        String jsonStr = GSON.toJson(toJson());
        Files.writeString(file, jsonStr, StandardCharsets.UTF_8);
        
        StoryAdventureMod.LOGGER.info("Saved camera recording to: {}", file);
        return file;
    }
    
    /**
     * Save this recording to a specific file.
     */
    public void saveToFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        String jsonStr = GSON.toJson(toJson());
        Files.writeString(file, jsonStr, StandardCharsets.UTF_8);
        StoryAdventureMod.LOGGER.info("Saved camera recording to: {}", file);
    }
    
    /**
     * Load a recording from a file.
     */
    public static CameraRecording loadFromFile(Path file) throws IOException {
        String jsonStr = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject json = GSON.fromJson(jsonStr, JsonObject.class);
        CameraRecording recording = fromJson(json);
        StoryAdventureMod.LOGGER.info("Loaded camera recording from: {}", file);
        return recording;
    }
    
    /**
     * List all recording files in the recordings directory.
     */
    public static List<Path> listRecordingFiles() {
        Path dir = getRecordingsDirectory();
        List<Path> files = new ArrayList<>();
        
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                      .sorted()
                      .forEach(files::add);
            } catch (IOException e) {
                StoryAdventureMod.LOGGER.error("Failed to list recording files", e);
            }
        }
        
        return files;
    }
    
    // ==================== Recorded Keyframe ====================
    
    /**
     * A single recorded camera keyframe.
     */
    public record RecordedKeyframe(
        double x, double y, double z,
        float yaw, float pitch, float roll,
        float fov,
        int durationTicks,
        String easing
    ) {
        public RecordedKeyframe withDuration(int newDuration) {
            return new RecordedKeyframe(x, y, z, yaw, pitch, roll, fov, newDuration, easing);
        }
        
        public RecordedKeyframe withEasing(String newEasing) {
            return new RecordedKeyframe(x, y, z, yaw, pitch, roll, fov, durationTicks, newEasing);
        }
        
        public RecordedKeyframe withFov(float newFov) {
            return new RecordedKeyframe(x, y, z, yaw, pitch, roll, newFov, durationTicks, easing);
        }
        
        public RecordedKeyframe withPosition(double newX, double newY, double newZ) {
            return new RecordedKeyframe(newX, newY, newZ, yaw, pitch, roll, fov, durationTicks, easing);
        }
        
        public RecordedKeyframe withRotation(float newYaw, float newPitch, float newRoll) {
            return new RecordedKeyframe(x, y, z, newYaw, newPitch, newRoll, fov, durationTicks, easing);
        }
    }
}