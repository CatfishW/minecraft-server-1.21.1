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
 * Uses Catmull-Rom spline interpolation for smooth position curves.
 */
public class CameraPath {
    
    private final List<CameraKeyframe> keyframes;
    private final float totalDurationTicks;
    private final LookAtTarget lookAtTarget;
    
    // Pre-computed segment data for fast lookup
    private final float[] segmentStartTimes;
    
    // Interpolation mode
    private InterpolationMode positionInterpolation = InterpolationMode.CATMULL_ROM;
    private InterpolationMode rotationInterpolation = InterpolationMode.SMOOTH_LERP;
    
    public enum InterpolationMode {
        LINEAR,
        SMOOTH_LERP,
        CATMULL_ROM
    }
    
    public CameraPath(List<CameraKeyframe> keyframes, LookAtTarget lookAtTarget) {
        this.keyframes = new ArrayList<>(keyframes);
        this.lookAtTarget = lookAtTarget;
        
        // Pre-compute segment start times for O(1) lookup
        this.segmentStartTimes = new float[keyframes.size()];
        float accumulated = 0f;
        for (int i = 0; i < keyframes.size(); i++) {
            segmentStartTimes[i] = accumulated;
            keyframes.get(i).setAccumulatedTime(accumulated);
            accumulated += keyframes.get(i).getDurationTicks();
        }
        this.totalDurationTicks = accumulated;
    }
    
    // ==================== Getters ====================
    
    public List<CameraKeyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }
    
    public float getTotalDurationTicks() {
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
    
    public void setPositionInterpolation(InterpolationMode mode) {
        this.positionInterpolation = mode;
    }
    
    public void setRotationInterpolation(InterpolationMode mode) {
        this.rotationInterpolation = mode;
    }
    
    // ==================== Interpolation ====================
    
    /**
     * Get the interpolated camera state at a given time.
     * Uses high-precision float timing for smooth interpolation.
     * 
     * @param exactTime Exact time in ticks (including fractional part)
     * @return Interpolated camera state
     */
    public CameraState getStateAt(float exactTime) {
        if (keyframes.isEmpty()) {
            return new CameraState(Vec3.ZERO, 0, 0, 0, 70f);
        }
        
        if (keyframes.size() == 1) {
            CameraKeyframe kf = keyframes.get(0);
            return new CameraState(kf.getPosition(), kf.getYaw(), kf.getPitch(), kf.getRoll(), kf.getFov());
        }
        
        // Clamp time to valid range
        exactTime = Math.max(0f, Math.min(totalDurationTicks, exactTime));
        
        // Binary search for the current segment (O(log n))
        int segmentIndex = findSegmentIndex(exactTime);
        
        if (segmentIndex >= keyframes.size() - 1) {
            // At or past the end
            CameraKeyframe last = keyframes.get(keyframes.size() - 1);
            return new CameraState(last.getPosition(), last.getYaw(), last.getPitch(), last.getRoll(), last.getFov());
        }
        
        CameraKeyframe from = keyframes.get(segmentIndex);
        CameraKeyframe to = keyframes.get(segmentIndex + 1);
        
        // Calculate local time within segment
        float segmentStart = segmentStartTimes[segmentIndex];
        float segmentDuration = to.getDurationTicks();
        
        if (segmentDuration <= 0) {
            return new CameraState(to.getPosition(), to.getYaw(), to.getPitch(), to.getRoll(), to.getFov());
        }
        
        float localTime = exactTime - segmentStart;
        float t = localTime / segmentDuration;
        t = Math.max(0f, Math.min(1f, t));
        
        // Apply easing function
        float eased = (float) to.getEasing().apply(t);
        
        return interpolate(segmentIndex, from, to, eased);
    }
    
    /**
     * Binary search for segment containing the given time.
     */
    private int findSegmentIndex(float time) {
        int low = 0;
        int high = keyframes.size() - 1;
        
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (segmentStartTimes[mid] <= time) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        
        return low;
    }
    
    /**
     * Interpolate between keyframes using configured interpolation modes.
     */
    private CameraState interpolate(int segmentIndex, CameraKeyframe from, CameraKeyframe to, float t) {
        // Position interpolation
        Vec3 pos = interpolatePosition(segmentIndex, t);
        
        // Rotation interpolation with proper angle handling
        float yaw = interpolateRotation(from.getYaw(), to.getYaw(), t);
        float pitch = interpolateRotation(from.getPitch(), to.getPitch(), t);
        float roll = smoothLerp(from.getRoll(), to.getRoll(), t);
        
        // FOV interpolation (smooth lerp for natural feel)
        float fov = smoothLerp(from.getFov(), to.getFov(), t);
        
        return new CameraState(pos, yaw, pitch, roll, fov);
    }
    
    /**
     * Interpolate position using Catmull-Rom spline for smooth curves.
     */
    private Vec3 interpolatePosition(int segmentIndex, float t) {
        CameraKeyframe from = keyframes.get(segmentIndex);
        CameraKeyframe to = keyframes.get(segmentIndex + 1);
        
        if (positionInterpolation == InterpolationMode.LINEAR) {
            return lerpVec3(from.getPosition(), to.getPosition(), t);
        }
        
        if (positionInterpolation == InterpolationMode.SMOOTH_LERP) {
            // Smoothstep interpolation
            float smoothT = smoothstep(t);
            return lerpVec3(from.getPosition(), to.getPosition(), smoothT);
        }
        
        // Catmull-Rom spline interpolation
        Vec3 p0 = segmentIndex > 0 
            ? keyframes.get(segmentIndex - 1).getPosition() 
            : extrapolatePoint(to.getPosition(), from.getPosition());
            
        Vec3 p1 = from.getPosition();
        Vec3 p2 = to.getPosition();
        
        Vec3 p3 = segmentIndex + 2 < keyframes.size() 
            ? keyframes.get(segmentIndex + 2).getPosition() 
            : extrapolatePoint(from.getPosition(), to.getPosition());
        
        return catmullRom(p0, p1, p2, p3, t);
    }
    
    /**
     * Catmull-Rom spline interpolation for smooth curves through control points.
     */
    private Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        
        // Catmull-Rom basis functions
        float b0 = -0.5f * t3 + t2 - 0.5f * t;
        float b1 = 1.5f * t3 - 2.5f * t2 + 1.0f;
        float b2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t;
        float b3 = 0.5f * t3 - 0.5f * t2;
        
        double x = b0 * p0.x + b1 * p1.x + b2 * p2.x + b3 * p3.x;
        double y = b0 * p0.y + b1 * p1.y + b2 * p2.y + b3 * p3.y;
        double z = b0 * p0.z + b1 * p1.z + b2 * p2.z + b3 * p3.z;
        
        return new Vec3(x, y, z);
    }
    
    /**
     * Extrapolate a point for spline boundaries.
     */
    private Vec3 extrapolatePoint(Vec3 anchor, Vec3 direction) {
        return new Vec3(
            2 * direction.x - anchor.x,
            2 * direction.y - anchor.y,
            2 * direction.z - anchor.z
        );
    }
    
    /**
     * Linear interpolation for Vec3.
     */
    private Vec3 lerpVec3(Vec3 a, Vec3 b, float t) {
        return new Vec3(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t
        );
    }
    
    /**
     * Interpolate angles, handling wrap-around at 180/-180.
     * Uses spherical linear interpolation approach for smooth rotation.
     */
    private float interpolateRotation(float from, float to, float t) {
        // Normalize angles
        float diff = to - from;
        
        // Find shortest path
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        
        if (rotationInterpolation == InterpolationMode.SMOOTH_LERP) {
            t = smoothstep(t);
        }
        
        return from + diff * t;
    }
    
    /**
     * Smooth linear interpolation.
     */
    private float smoothLerp(float a, float b, float t) {
        t = smoothstep(t);
        return a + (b - a) * t;
    }
    
    /**
     * Smoothstep function for natural easing.
     */
    private float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }
    
    // ==================== JSON Serialization ====================
    
    /**
     * Create a CameraPath from JSON.
     * Expected format:
     * {
     *   "keyframes": [...],
     *   "look_at": { "type": "position", "value": [x, y, z] },
     *   "position_interpolation": "CATMULL_ROM",
     *   "rotation_interpolation": "SMOOTH_LERP"
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
        
        CameraPath path = new CameraPath(keyframes, lookAt);
        
        // Parse interpolation modes
        if (json.has("position_interpolation")) {
            try {
                path.positionInterpolation = InterpolationMode.valueOf(
                    json.get("position_interpolation").getAsString().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        
        if (json.has("rotation_interpolation")) {
            try {
                path.rotationInterpolation = InterpolationMode.valueOf(
                    json.get("rotation_interpolation").getAsString().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        
        return path;
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
        
        json.addProperty("position_interpolation", positionInterpolation.name());
        json.addProperty("rotation_interpolation", rotationInterpolation.name());
        
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
        
        /**
         * Interpolate between two camera states.
         */
        public static CameraState lerp(CameraState a, CameraState b, float t) {
            return new CameraState(
                a.position.lerp(b.position, t),
                lerpAngle(a.yaw, b.yaw, t),
                lerpAngle(a.pitch, b.pitch, t),
                a.roll + (b.roll - a.roll) * t,
                a.fov + (b.fov - a.fov) * t
            );
        }

        public static CameraState lerpRotationOnly(CameraState a, CameraState b, float t) {
            return new CameraState(
                a.position,
                lerpAngle(a.yaw, b.yaw, t),
                lerpAngle(a.pitch, b.pitch, t),
                a.roll + (b.roll - a.roll) * t,
                a.fov // Keep FOV from original
            );
        }
        
        private static float lerpAngle(float a, float b, float t) {
            float diff = b - a;
            while (diff > 180f) diff -= 360f;
            while (diff < -180f) diff += 360f;
            return a + diff * t;
        }
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
        return String.format("CameraPath{keyframes=%d, duration=%.1f ticks, lookAt=%s}",
            keyframes.size(), totalDurationTicks, lookAtTarget != null);
    }
}