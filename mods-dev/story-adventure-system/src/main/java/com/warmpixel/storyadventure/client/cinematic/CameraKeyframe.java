package com.warmpixel.storyadventure.client.cinematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

/**
 * Represents a single keyframe in a camera path.
 * Contains position, rotation, FOV, timing, and easing information.
 */
public class CameraKeyframe {
    
    private final Vec3 position;
    private final float yaw;
    private final float pitch;
    private final float roll;
    private final float fov;
    private final int durationTicks;
    private final EasingFunction easing;
    
    // Cached accumulated time for faster lookup
    private float accumulatedTime = 0f;
    
    public CameraKeyframe(Vec3 position, float yaw, float pitch, float roll, 
                          float fov, int durationTicks, EasingFunction easing) {
        this.position = position;
        this.yaw = normalizeAngle(yaw);
        this.pitch = pitch;
        this.roll = roll;
        this.fov = fov;
        this.durationTicks = durationTicks;
        this.easing = easing;
    }
    
    /**
     * Normalize angle to [-180, 180] range.
     */
    private static float normalizeAngle(float angle) {
        angle = angle % 360f;
        if (angle > 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }
    
    // ==================== Getters ====================
    
    public Vec3 getPosition() {
        return position;
    }
    
    public float getYaw() {
        return yaw;
    }
    
    public float getPitch() {
        return pitch;
    }
    
    public float getRoll() {
        return roll;
    }
    
    public float getFov() {
        return fov;
    }
    
    public int getDurationTicks() {
        return durationTicks;
    }
    
    public EasingFunction getEasing() {
        return easing;
    }
    
    public float getAccumulatedTime() {
        return accumulatedTime;
    }
    
    public void setAccumulatedTime(float time) {
        this.accumulatedTime = time;
    }
    
    // ==================== JSON Serialization ====================
    
    /**
     * Create a CameraKeyframe from JSON.
     * Expected format:
     * {
     *   "position": [x, y, z],
     *   "rotation": [yaw, pitch, roll],  // or just [yaw, pitch]
     *   "fov": 70.0,
     *   "duration_ticks": 60,
     *   "easing": "EASE_IN_OUT"
     * }
     */
    public static CameraKeyframe fromJson(JsonObject json) {
        // Parse position
        Vec3 position = Vec3.ZERO;
        if (json.has("position") && json.get("position").isJsonArray()) {
            JsonArray posArr = json.getAsJsonArray("position");
            if (posArr.size() >= 3) {
                position = new Vec3(
                    posArr.get(0).getAsDouble(),
                    posArr.get(1).getAsDouble(),
                    posArr.get(2).getAsDouble()
                );
            }
        }
        
        // Parse rotation
        float yaw = 0f, pitch = 0f, roll = 0f;
        if (json.has("rotation") && json.get("rotation").isJsonArray()) {
            JsonArray rotArr = json.getAsJsonArray("rotation");
            if (rotArr.size() >= 2) {
                yaw = rotArr.get(0).getAsFloat();
                pitch = rotArr.get(1).getAsFloat();
            }
            if (rotArr.size() >= 3) {
                roll = rotArr.get(2).getAsFloat();
            }
        }
        
        // Parse FOV (default to 70)
        float fov = 70f;
        if (json.has("fov")) {
            fov = json.get("fov").getAsFloat();
        }
        
        // Parse duration (default to 0 for first keyframe)
        int durationTicks = 0;
        if (json.has("duration_ticks")) {
            durationTicks = json.get("duration_ticks").getAsInt();
        }
        
        // Parse easing
        EasingFunction easing = EasingFunction.LINEAR;
        if (json.has("easing")) {
            easing = EasingFunction.fromString(json.get("easing").getAsString());
        }
        
        return new CameraKeyframe(position, yaw, pitch, roll, fov, durationTicks, easing);
    }
    
    /**
     * Serialize this keyframe to JSON.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        
        JsonArray posArr = new JsonArray();
        posArr.add(position.x);
        posArr.add(position.y);
        posArr.add(position.z);
        json.add("position", posArr);
        
        JsonArray rotArr = new JsonArray();
        rotArr.add(yaw);
        rotArr.add(pitch);
        rotArr.add(roll);
        json.add("rotation", rotArr);
        
        json.addProperty("fov", fov);
        json.addProperty("duration_ticks", durationTicks);
        json.addProperty("easing", easing.name());
        
        return json;
    }
    
    @Override
    public String toString() {
        return String.format("CameraKeyframe{pos=%s, yaw=%.1f, pitch=%.1f, fov=%.1f, duration=%d, easing=%s}",
            position, yaw, pitch, fov, durationTicks, easing);
    }
}