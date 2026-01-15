package com.warmpixel.storyadventure.client.cinematic;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Main controller for cinematic camera during cutscenes.
 * This is a client-side singleton that manages camera path playback
 * and provides camera state for mixin injection.
 */
public class CinematicCameraController {
    
    private static CinematicCameraController instance;
    
    // Cutscene state
    private boolean active = false;
    private CameraPath currentPath;
    private long startTimeMs;
    private int currentTick;
    private boolean skippable = true;
    
    // Current interpolated camera state
    private Vec3 currentPosition = Vec3.ZERO;
    private float currentYaw = 0f;
    private float currentPitch = 0f;
    private float currentRoll = 0f;
    private float currentFov = 70f;
    
    // Visual effect settings
    private boolean letterboxEnabled = false;
    private float letterboxProgress = 0f;
    private float fadeProgress = 0f;
    private int fadeInTicks = 0;
    private int fadeOutTicks = 0;
    private int totalDurationTicks = 0;
    
    // look-at target (resolved entity position)
    private Vec3 lookAtPosition = null;
    
    // Callbacks
    private Runnable onComplete;
    private Runnable onSkip;
    
    private CinematicCameraController() {}
    
    public static CinematicCameraController getInstance() {
        if (instance == null) {
            instance = new CinematicCameraController();
        }
        return instance;
    }
    
    // ==================== Cutscene Control ====================
    
    /**
     * Start a cutscene with the given camera path.
     */
    public void startCutscene(CameraPath path, CutsceneConfig config) {
        if (path == null || path.getKeyframeCount() == 0) {
            StoryAdventureMod.LOGGER.warn("[CinematicCamera] Cannot start cutscene with empty path");
            return;
        }
        
        this.currentPath = path;
        this.startTimeMs = System.currentTimeMillis();
        this.currentTick = 0;
        this.active = true;
        this.skippable = config.isSkippable();
        
        this.letterboxEnabled = config.isLetterboxEnabled();
        this.letterboxProgress = 0f;
        this.fadeInTicks = config.getFadeInTicks();
        this.fadeOutTicks = config.getFadeOutTicks();
        this.totalDurationTicks = path.getTotalDurationTicks();
        this.fadeProgress = 1f; // Start with black screen if fade-in enabled
        
        this.onComplete = config.getOnComplete();
        this.onSkip = config.getOnSkip();
        
        // Initialize look-at target
        if (path.hasLookAtTarget()) {
            resolveLookAtTarget(path.getLookAtTarget());
        } else {
            lookAtPosition = null;
        }
        
        // Initialize to first keyframe state
        updateCameraState(0f);
        
        StoryAdventureMod.LOGGER.info("[CinematicCamera] Started cutscene: {} keyframes, {} ticks duration",
            path.getKeyframeCount(), path.getTotalDurationTicks());
    }
    
    /**
     * Stop the current cutscene.
     */
    public void stopCutscene() {
        if (!active) return;
        
        StoryAdventureMod.LOGGER.info("[CinematicCamera] Cutscene stopped");
        
        active = false;
        currentPath = null;
        letterboxProgress = 0f;
        fadeProgress = 0f;
        
        if (onComplete != null) {
            onComplete.run();
            onComplete = null;
        }
        onSkip = null;
    }
    
    /**
     * Skip the current cutscene (if skippable).
     */
    public void skipCutscene() {
        if (!active || !skippable) return;
        
        StoryAdventureMod.LOGGER.info("[CinematicCamera] Cutscene skipped by player");
        
        active = false;
        currentPath = null;
        letterboxProgress = 0f;
        fadeProgress = 0f;
        
        if (onSkip != null) {
            onSkip.run();
            onSkip = null;
        }
        onComplete = null;
    }
    
    /**
     * Tick the cutscene each frame.
     * @param partialTicks Partial tick for smooth rendering
     */
    public void tick(float partialTicks) {
        if (!active || currentPath == null) return;
        
        // Calculate elapsed time
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        float elapsedTicks = elapsedMs / 50f; // 50ms per tick
        currentTick = (int) elapsedTicks;
        
        // Update camera state
        updateCameraState(partialTicks);
        
        // Update letterbox animation
        updateLetterbox(elapsedTicks);
        
        // Update fade effect
        updateFade(elapsedTicks);
        
        // Check for cutscene completion
        if (currentTick >= totalDurationTicks) {
            StoryAdventureMod.LOGGER.info("[CinematicCamera] Cutscene completed naturally");
            stopCutscene();
        }
    }
    
    private void updateCameraState(float partialTicks) {
        if (currentPath == null) return;
        
        CameraPath.CameraState state = currentPath.getStateAt(currentTick, partialTicks);
        
        this.currentPosition = state.getPosition();
        this.currentFov = state.getFov();
        
        // Handle look-at override
        if (lookAtPosition != null) {
            // Calculate rotation to look at target
            Vec3 toTarget = lookAtPosition.subtract(currentPosition);
            double horizontalDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            
            this.currentYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            this.currentPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontalDist));
            this.currentRoll = state.getRoll(); // Keep roll from keyframe
        } else {
            this.currentYaw = state.getYaw();
            this.currentPitch = state.getPitch();
            this.currentRoll = state.getRoll();
        }
    }
    
    private void updateLetterbox(float elapsedTicks) {
        if (!letterboxEnabled) {
            letterboxProgress = 0f;
            return;
        }
        
        // Animate letterbox in over first 10 ticks
        float animDuration = 10f;
        if (elapsedTicks < animDuration) {
            letterboxProgress = elapsedTicks / animDuration;
        } else if (elapsedTicks > totalDurationTicks - animDuration) {
            // Animate out over last 10 ticks
            letterboxProgress = (totalDurationTicks - elapsedTicks) / animDuration;
        } else {
            letterboxProgress = 1f;
        }
        letterboxProgress = Math.max(0f, Math.min(1f, letterboxProgress));
    }
    
    private void updateFade(float elapsedTicks) {
        if (fadeInTicks > 0 && elapsedTicks < fadeInTicks) {
            // Fade in (from black)
            fadeProgress = 1f - (elapsedTicks / fadeInTicks);
        } else if (fadeOutTicks > 0 && elapsedTicks > totalDurationTicks - fadeOutTicks) {
            // Fade out (to black)
            fadeProgress = (elapsedTicks - (totalDurationTicks - fadeOutTicks)) / fadeOutTicks;
        } else {
            fadeProgress = 0f;
        }
        fadeProgress = Math.max(0f, Math.min(1f, fadeProgress));
    }
    
    private void resolveLookAtTarget(CameraPath.LookAtTarget target) {
        if (target.getType() == CameraPath.LookAtTarget.Type.POSITION) {
            lookAtPosition = target.getPosition();
        } else {
            // Entity target - try to resolve
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                String selector = target.getEntitySelector();
                // For now, just use player position as fallback
                // Full entity selector resolution would require server communication
                if (mc.player != null) {
                    lookAtPosition = mc.player.position();
                }
            }
        }
    }
    
    // ==================== Getters for Mixin Use ====================
    
    public boolean isActive() {
        return active;
    }
    
    public Vec3 getCameraPosition() {
        return currentPosition;
    }
    
    public float getCameraYaw() {
        return currentYaw;
    }
    
    public float getCameraPitch() {
        return currentPitch;
    }
    
    public float getCameraRoll() {
        return currentRoll;
    }
    
    public float getCameraFov() {
        return currentFov;
    }
    
    public float getLetterboxProgress() {
        return letterboxProgress;
    }
    
    public float getFadeProgress() {
        return fadeProgress;
    }
    
    public boolean isLetterboxEnabled() {
        return letterboxEnabled;
    }
    
    public boolean isSkippable() {
        return skippable;
    }
    
    public int getCurrentTick() {
        return currentTick;
    }
    
    public int getTotalDurationTicks() {
        return totalDurationTicks;
    }
    
    /**
     * Get progress through cutscene (0.0 to 1.0).
     */
    public float getProgress() {
        if (totalDurationTicks <= 0) return 0f;
        return Math.min(1f, (float) currentTick / totalDurationTicks);
    }
    
    // ==================== Configuration ====================
    
    /**
     * Configuration for cutscene playback.
     */
    public static class CutsceneConfig {
        private boolean skippable = true;
        private boolean letterboxEnabled = true;
        private int fadeInTicks = 0;
        private int fadeOutTicks = 0;
        private Runnable onComplete;
        private Runnable onSkip;
        
        public CutsceneConfig() {}
        
        public CutsceneConfig setSkippable(boolean skippable) {
            this.skippable = skippable;
            return this;
        }
        
        public CutsceneConfig setLetterboxEnabled(boolean enabled) {
            this.letterboxEnabled = enabled;
            return this;
        }
        
        public CutsceneConfig setFadeInTicks(int ticks) {
            this.fadeInTicks = ticks;
            return this;
        }
        
        public CutsceneConfig setFadeOutTicks(int ticks) {
            this.fadeOutTicks = ticks;
            return this;
        }
        
        public CutsceneConfig setOnComplete(Runnable callback) {
            this.onComplete = callback;
            return this;
        }
        
        public CutsceneConfig setOnSkip(Runnable callback) {
            this.onSkip = callback;
            return this;
        }
        
        public boolean isSkippable() { return skippable; }
        public boolean isLetterboxEnabled() { return letterboxEnabled; }
        public int getFadeInTicks() { return fadeInTicks; }
        public int getFadeOutTicks() { return fadeOutTicks; }
        public Runnable getOnComplete() { return onComplete; }
        public Runnable getOnSkip() { return onSkip; }
    }
}
