package com.warmpixel.storyadventure.client.cinematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a complete camera path for a cutscene.
 * Contains a sequence of keyframes and optional look-at target.
 */
public class CameraPath {
    
    private final List<CameraKeyframe> keyframes;
    private final int totalDurationTicks;
    private final LookAtTarget lookAtTarget;
    
    public CameraPath(List<CameraKeyframe> keyframes, LookAtTarget lookAtTarget) {
        this.keyframes = new ArrayList<>(keyframes);
        this.lookAtTarget = lookAtTarget;
        
        // Calculate total duration
        int total = 0;
        for (CameraKeyframe kf : keyframes) {
            total += kf.getDurationTicks();
        }
        this.totalDurationTicks = total;
    }
    
    // ==================== Getters ====================
    
    public List<CameraKeyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }
    
    public int getTotalDurationTicks() {
        return totalDurationTicks;
    }
    
    public LookAtTarget getLookAtTarget() {
        return lookAtTarget;
    }
    
    public boolean hasLookAtTarget() {
        return lookAtTarget != null;
    }
    
    public int getKeyframeCount() {
        return keyframes.size();
    }
    
    // ==================== Interpolation ====================
    
    /**
     * Get the interpolated camera state at a given time.
     * @param tickTime Current tick time since cutscene start
     * @param partialTicks Partial tick for smooth rendering
     * @return Interpolated camera state
     */
    public CameraState getStateAt(int tickTime, float partialTicks) {
        if (keyframes.isEmpty()) {
            return new CameraState(Vec3.ZERO, 0, 0, 0, 70f);
        }
        
        if (keyframes.size() == 1) {
            CameraKeyframe kf = keyframes.get(0);
            return new CameraState(kf.getPosition(), kf.getYaw(), kf.getPitch(), kf.getRoll(), kf.getFov());
        }
        
        float exactTime = tickTime + partialTicks;
        
        // Find the current keyframe pair
        int accumulatedTime = 0;
        for (int i = 1; i < keyframes.size(); i++) {
            CameraKeyframe from = keyframes.get(i - 1);
            CameraKeyframe to = keyframes.get(i);
            int segmentDuration = to.getDurationTicks();
            
            if (exactTime <= accumulatedTime + segmentDuration || i == keyframes.size() - 1) {
                // We're in this segment
                float segmentTime = exactTime - accumulatedTime;
                float t = segmentDuration > 0 ? segmentTime / segmentDuration : 1f;
                t = Math.max(0f, Math.min(1f, t));
                
                // Apply easing
                double eased = to.getEasing().apply(t);
                
                return interpolate(from, to, (float) eased);
            }
            
            accumulatedTime += segmentDuration;
        }
        
        // Past the end - return last keyframe
        CameraKeyframe last = keyframes.get(keyframes.size() - 1);
        return new CameraState(last.getPosition(), last.getYaw(), last.getPitch(), last.getRoll(), last.getFov());
    }
    
    /**
     * Interpolate between two keyframes.
     */
    private CameraState interpolate(CameraKeyframe from, CameraKeyframe to, float t) {
        // Position lerp
        Vec3 pos = from.getPosition().lerp(to.getPosition(), t);
        
        // Rotation lerp (with angle wrapping for yaw)
        float yaw = lerpAngle(from.getYaw(), to.getYaw(), t);
        float pitch = lerp(from.getPitch(), to.getPitch(), t);
        float roll = lerp(from.getRoll(), to.getRoll(), t);
        
        // FOV lerp
        float fov = lerp(from.getFov(), to.getFov(), t);
        
        return new CameraState(pos, yaw, pitch, roll, fov);
    }
    
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    /**
     * Lerp angles, handling the wrap-around at 180/-180.
     */
    private float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        
        // Normalize difference to [-180, 180]
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        
        return a + diff * t;
    }
    
    // ==================== JSON Serialization ====================
    
    /**
     * Create a CameraPath from JSON.
     * Expected format:
     * {
     *   "keyframes": [...],
     *   "look_at": { "type": "position", "value": [x, y, z] }
     * }
     */
    public static CameraPath fromJson(JsonObject json) {
        List<CameraKeyframe> keyframes = new ArrayList<>();
        
        if (json.has("keyframes") && json.get("keyframes").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("keyframes");
            for (JsonElement elem : arr) {
                if (elem.isJsonObject()) {
                    keyframes.add(CameraKeyframe.fromJson(elem.getAsJsonObject()));
                }
            }
        }
        
        LookAtTarget lookAt = null;
        if (json.has("look_at") && json.get("look_at").isJsonObject()) {
            lookAt = LookAtTarget.fromJson(json.getAsJsonObject("look_at"));
        }
        
        return new CameraPath(keyframes, lookAt);
    }
    
    /**
     * Serialize this path to JSON.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        
        JsonArray kfArr = new JsonArray();
        for (CameraKeyframe kf : keyframes) {
            kfArr.add(kf.toJson());
        }
        json.add("keyframes", kfArr);
        
        if (lookAtTarget != null) {
            json.add("look_at", lookAtTarget.toJson());
        }
        
        return json;
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * Represents the interpolated camera state at a point in time.
     */
    public static class CameraState {
        private final Vec3 position;
        private final float yaw;
        private final float pitch;
        private final float roll;
        private final float fov;
        
        public CameraState(Vec3 position, float yaw, float pitch, float roll, float fov) {
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.fov = fov;
        }
        
        public Vec3 getPosition() { return position; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public float getRoll() { return roll; }
        public float getFov() { return fov; }
    }
    
    /**
     * Represents a look-at target for the camera.
     */
    public static class LookAtTarget {
        public enum Type { POSITION, ENTITY }
        
        private final Type type;
        private final Vec3 position;
        private final String entitySelector;
        
        private LookAtTarget(Type type, Vec3 position, String entitySelector) {
            this.type = type;
            this.position = position;
            this.entitySelector = entitySelector;
        }
        
        public static LookAtTarget position(Vec3 pos) {
            return new LookAtTarget(Type.POSITION, pos, null);
        }
        
        public static LookAtTarget entity(String selector) {
            return new LookAtTarget(Type.ENTITY, null, selector);
        }
        
        public Type getType() { return type; }
        public Vec3 getPosition() { return position; }
        public String getEntitySelector() { return entitySelector; }
        
        public static LookAtTarget fromJson(JsonObject json) {
            String type = json.has("type") ? json.get("type").getAsString() : "position";
            
            if ("entity".equalsIgnoreCase(type)) {
                String selector = json.has("value") ? json.get("value").getAsString() : "@p";
                return LookAtTarget.entity(selector);
            } else {
                Vec3 pos = Vec3.ZERO;
                if (json.has("value") && json.get("value").isJsonArray()) {
                    JsonArray arr = json.getAsJsonArray("value");
                    if (arr.size() >= 3) {
                        pos = new Vec3(
                            arr.get(0).getAsDouble(),
                            arr.get(1).getAsDouble(),
                            arr.get(2).getAsDouble()
                        );
                    }
                }
                return LookAtTarget.position(pos);
            }
        }
        
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("type", type.name().toLowerCase());
            
            if (type == Type.POSITION && position != null) {
                JsonArray arr = new JsonArray();
                arr.add(position.x);
                arr.add(position.y);
                arr.add(position.z);
                json.add("value", arr);
            } else if (type == Type.ENTITY && entitySelector != null) {
                json.addProperty("value", entitySelector);
            }
            
            return json;
        }
    }
    
    @Override
    public String toString() {
        return String.format("CameraPath{keyframes=%d, duration=%d ticks, lookAt=%s}",
            keyframes.size(), totalDurationTicks, lookAtTarget != null);
    }
}
