package com.warmpixel.storyadventure.client.cinematic;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Main controller for cinematic camera during cutscenes.
 * This is a client-side singleton that manages camera path playback
 * and provides camera state for mixin injection.
 * 
 * Uses high-precision timing and frame interpolation for smooth camera movement.
 */
public class CinematicCameraController {
    
    private static CinematicCameraController instance;
    
    // Timing constants
    private static final float TICKS_PER_SECOND = 20f;
    private static final long NANOS_PER_TICK = 50_000_000L; // 50ms = 50,000,000 nanoseconds
    
    // Cutscene state
    private boolean active = false;
    private CameraPath currentPath;
    private long startTimeNanos;
    private float currentExactTick;
    private boolean skippable = true;
    private boolean paused = false;
    
    // Current interpolated camera state (for rendering)
    private CameraPath.CameraState currentState;
    private CameraPath.CameraState previousState;
    private CameraPath.CameraState renderState;
    
    // Visual effect settings
    private boolean letterboxEnabled = false;
    private float letterboxProgress = 0f;
    private float fadeProgress = 0f;
    private int fadeInTicks = 0;
    private int fadeOutTicks = 0;
    private boolean fadeEnabled = false;
    private float totalDurationTicks = 0f;
    
    // Letterbox animation
    private static final float LETTERBOX_ANIM_DURATION = 10f;
    
    // Look-at target (resolved entity position)
    private Vec3 lookAtPosition = null;
    private Vec3 lookAtVelocity = Vec3.ZERO;
    
    // Smoothing parameters for extra smoothness
    private float smoothingFactor = 0.15f; // Lower = smoother but more latency
    private boolean enableExtraSmoothing = true;
    
    // Callbacks
    private Runnable onComplete;
    private Runnable onSkip;
    
    private CinematicCameraController() {
        CameraPath.CameraState defaultState = new CameraPath.CameraState(Vec3.ZERO, 0, 0, 0, 70f);
        this.currentState = defaultState;
        this.previousState = defaultState;
        this.renderState = defaultState;
    }
    
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
        this.startTimeNanos = System.nanoTime();
        this.currentExactTick = 0f;
        this.active = true;
        this.paused = false;
        this.skippable = config.isSkippable();
        
        this.letterboxEnabled = config.isLetterboxEnabled();
        this.letterboxProgress = 0f;
        this.fadeInTicks = config.getFadeInTicks();
        this.fadeOutTicks = config.getFadeOutTicks();
        this.totalDurationTicks = path.getTotalDurationTicks();
        this.fadeEnabled = fadeInTicks > 0 || fadeOutTicks > 0;
        this.fadeProgress = fadeEnabled ? 1f : 0f;
        
        this.enableExtraSmoothing = config.isExtraSmoothingEnabled();
        this.smoothingFactor = config.getSmoothingFactor();
        
        this.onComplete = config.getOnComplete();
        this.onSkip = config.getOnSkip();
        
        // Initialize look-at target
        if (path.hasLookAtTarget()) {
            resolveLookAtTarget(path.getLookAtTarget());
        } else {
            lookAtPosition = null;
        }
        
        // Initialize camera state to first keyframe
        CameraPath.CameraState initialState = path.getStateAt(0f);
        this.currentState = initialState;
        this.previousState = initialState;
        this.renderState = initialState;
        
        StoryAdventureMod.LOGGER.info("[CinematicCamera] Initialized camera at: {}", initialState.getPosition());
        
        StoryAdventureMod.LOGGER.info("[CinematicCamera] Started cutscene: {} keyframes, {:.1f} ticks duration",
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
        fadeEnabled = false;
        
        if (onComplete != null) {
            Runnable callback = onComplete;
            onComplete = null;
            callback.run();
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
        fadeEnabled = false;
        
        if (onSkip != null) {
            Runnable callback = onSkip;
            onSkip = null;
            callback.run();
        }
        onComplete = null;
    }
    
    /**
     * Pause/resume the cutscene.
     */
    public void setPaused(boolean paused) {
        if (this.paused == paused) return;
        
        if (paused) {
            // Store current time offset when pausing
            this.paused = true;
        } else {
            // Adjust start time when resuming to maintain position
            long currentNanos = System.nanoTime();
            long elapsedNanos = (long)(currentExactTick * NANOS_PER_TICK);
            this.startTimeNanos = currentNanos - elapsedNanos;
            this.paused = false;
        }
    }
    
    /**
     * Called every game tick (20 times per second).
     * Updates the base camera state.
     */
    public void gameTick() {
        if (!active || currentPath == null || paused) return;
        
        if (Minecraft.getInstance().player.tickCount % 20 == 0) {
            StoryAdventureMod.LOGGER.info("[CinematicCamera] Tick: {:.2f}/{:.2f}, Fade: {:.2f}", currentExactTick, totalDurationTicks, fadeProgress);
        }
        
        // Store previous state for interpolation
        previousState = currentState;
        
        // Calculate exact time
        long elapsedNanos = System.nanoTime() - startTimeNanos;
        currentExactTick = (float) elapsedNanos / NANOS_PER_TICK;
        
        // Update camera state at current tick
        updateCameraState(currentExactTick);
        
        // Update visual effects
        updateLetterbox(currentExactTick);
        updateFade(currentExactTick);
        
        // Check for cutscene completion
        if (currentExactTick >= totalDurationTicks) {
            StoryAdventureMod.LOGGER.info("[CinematicCamera] Cutscene completed naturally");
            stopCutscene();
        }
    }
    
    /**
     * Called every render frame for smooth interpolation.
     * @param partialTicks Partial tick (0.0 to 1.0) within current game tick
     */
    public void renderTick(float partialTicks) {
        if (!active || currentPath == null) return;
        
        // Calculate precise time including partial ticks
        long elapsedNanos = System.nanoTime() - startTimeNanos;
        float exactTime = (float) elapsedNanos / NANOS_PER_TICK;
        
        if (paused) {
            exactTime = currentExactTick;
        }
        
        // Get the interpolated state directly from the path at exact time
        CameraPath.CameraState targetState = currentPath.getStateAt(exactTime);
        
        // Apply look-at override if needed
        if (lookAtPosition != null) {
            targetState = applyLookAt(targetState);
        }
        
        // Apply extra temporal smoothing if enabled
        if (enableExtraSmoothing && renderState != null) {
            renderState = smoothState(renderState, targetState, smoothingFactor);
        } else {
            renderState = targetState;
        }
    }
    
    /**
     * Apply exponential smoothing between states for extra smoothness.
     */
    private CameraPath.CameraState smoothState(CameraPath.CameraState current, CameraPath.CameraState target, float factor) {
        // Use frame-rate independent smoothing
        float deltaTime = Minecraft.getInstance().getTimer().getGameTimeDeltaTicks() / 50f; // Normalize to tick time
        float smoothFactor = 1f - (float) Math.pow(1f - factor, deltaTime * 20f);
        
        return CameraPath.CameraState.lerp(current, target, smoothFactor);
    }
    
    private void updateCameraState(float exactTime) {
        if (currentPath == null) return;
        
        currentState = currentPath.getStateAt(exactTime);
        
        // Apply look-at override
        if (lookAtPosition != null) {
            currentState = applyLookAt(currentState);
        }
    }
    
    private CameraPath.CameraState applyLookAt(CameraPath.CameraState state) {
        Vec3 toTarget = lookAtPosition.subtract(state.getPosition());
        double horizontalDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontalDist));
        
        return new CameraPath.CameraState(
            state.getPosition(),
            yaw,
            pitch,
            state.getRoll(),
            state.getFov()
        );
    }
    
    private void updateLetterbox(float elapsedTicks) {
        if (!letterboxEnabled) {
            letterboxProgress = 0f;
            return;
        }
        
        // Smooth letterbox animation
        float targetProgress;
        if (elapsedTicks < LETTERBOX_ANIM_DURATION) {
            targetProgress = elapsedTicks / LETTERBOX_ANIM_DURATION;
        } else if (elapsedTicks > totalDurationTicks - LETTERBOX_ANIM_DURATION) {
            targetProgress = (totalDurationTicks - elapsedTicks) / LETTERBOX_ANIM_DURATION;
        } else {
            targetProgress = 1f;
        }
        
        // Smooth the letterbox progress
        letterboxProgress += (targetProgress - letterboxProgress) * 0.2f;
        letterboxProgress = Math.max(0f, Math.min(1f, letterboxProgress));
    }
    
    private void updateFade(float elapsedTicks) {
        if (!fadeEnabled) {
            fadeProgress = 0f;
            return;
        }
        float targetFade;
        
        if (fadeInTicks > 0 && elapsedTicks < fadeInTicks) {
            targetFade = 1f - (elapsedTicks / fadeInTicks);
        } else if (fadeOutTicks > 0 && elapsedTicks > totalDurationTicks - fadeOutTicks) {
            targetFade = (elapsedTicks - (totalDurationTicks - fadeOutTicks)) / fadeOutTicks;
        } else {
            targetFade = 0f;
        }
        
        // Smooth fade transitions
        fadeProgress += (targetFade - fadeProgress) * 0.15f;
        fadeProgress = Math.max(0f, Math.min(1f, fadeProgress));
    }
    
    private void resolveLookAtTarget(CameraPath.LookAtTarget target) {
        if (target.getType() == CameraPath.LookAtTarget.Type.POSITION) {
            lookAtPosition = target.getPosition();
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                lookAtPosition = mc.player.position().add(0, mc.player.getEyeHeight(), 0);
            }
        }
    }
    
    /**
     * Update look-at target for entity tracking (call each tick).
     */
    public void updateLookAtTarget(Vec3 newPosition) {
        if (lookAtPosition != null && newPosition != null) {
            // Smooth look-at target movement
            Vec3 diff = newPosition.subtract(lookAtPosition);
            lookAtPosition = lookAtPosition.add(diff.scale(0.1));
        } else {
            lookAtPosition = newPosition;
        }
    }
    
    // ==================== Getters for Mixin Use ====================
    
    public boolean isActive() {
        return active;
    }
    
    public boolean isPaused() {
        return paused;
    }
    
    /**
     * Get the current camera position for rendering.
     * Uses the smoothed render state.
     */
    public Vec3 getCameraPosition() {
        return renderState != null ? renderState.getPosition() : Vec3.ZERO;
    }
    
    /**
     * Get the current camera yaw for rendering.
     */
    public float getCameraYaw() {
        return renderState != null ? renderState.getYaw() : 0f;
    }
    
    /**
     * Get the current camera pitch for rendering.
     */
    public float getCameraPitch() {
        return renderState != null ? renderState.getPitch() : 0f;
    }
    
    /**
     * Get the current camera roll for rendering.
     */
    public float getCameraRoll() {
        return renderState != null ? renderState.getRoll() : 0f;
    }
    
    /**
     * Get the current camera FOV for rendering.
     */
    public float getCameraFov() {
        return renderState != null ? renderState.getFov() : 70f;
    }
    
    /**
     * Get the raw camera state without smoothing (for debugging).
     */
    public CameraPath.CameraState getRawState() {
        return currentState;
    }
    
    /**
     * Get the smoothed render state.
     */
    public CameraPath.CameraState getRenderState() {
        return renderState;
    }
    
    public float getLetterboxProgress() {
        return letterboxProgress;
    }
    
    public float getFadeProgress() {
        return fadeProgress;
    }

    public boolean isFadeEnabled() {
        return fadeEnabled;
    }
    
    public boolean isLetterboxEnabled() {
        return letterboxEnabled;
    }
    
    public boolean isSkippable() {
        return skippable;
    }
    
    public float getCurrentTick() {
        return currentExactTick;
    }
    
    public float getTotalDurationTicks() {
        return totalDurationTicks;
    }
    
    /**
     * Get progress through cutscene (0.0 to 1.0).
     */
    public float getProgress() {
        if (totalDurationTicks <= 0) return 0f;
        return Math.min(1f, currentExactTick / totalDurationTicks);
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
        private boolean extraSmoothingEnabled = true;
        private float smoothingFactor = 0.15f;
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
        
        public CutsceneConfig setExtraSmoothingEnabled(boolean enabled) {
            this.extraSmoothingEnabled = enabled;
            return this;
        }
        
        /**
         * Set the smoothing factor (0.0 to 1.0).
         * Lower values = smoother but more latency.
         * Higher values = more responsive but potentially jittery.
         * Default: 0.15
         */
        public CutsceneConfig setSmoothingFactor(float factor) {
            this.smoothingFactor = Math.max(0.01f, Math.min(1f, factor));
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
        public boolean isExtraSmoothingEnabled() { return extraSmoothingEnabled; }
        public float getSmoothingFactor() { return smoothingFactor; }
        public Runnable getOnComplete() { return onComplete; }
        public Runnable getOnSkip() { return onSkip; }
    }
}