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

    // NPC Animations
    private java.util.List<NPCAnimation> animations = new java.util.ArrayList<>();
    private java.util.Set<Integer> triggeredAnimations = new java.util.HashSet<>();
    private float totalDurationTicks = 0f;
    
    // Subtitles
    private java.util.List<Subtitle> subtitles = new java.util.ArrayList<>();
    private String currentSubtitle = null;
    private String currentSubtitleVoiceover = null;
    private int lastSubtitleIndex = -1; // Track which subtitle is currently active
    
    // Subtitle Typewriter Effect
    private int subtitleDisplayedCharacters = 0;
    private long lastSubtitleCharTime = 0;
    private static final long SUBTITLE_CHAR_DELAY_MS = 30;
    private boolean subtitleComplete = false;
    
    public record Subtitle(String text, int startTick, int durationTick, String voiceover, String focusTarget) {}
    public record NPCAnimation(int tick, String npc, String pose) {}
    
    // Letterbox animation
    private static final float LETTERBOX_ANIM_DURATION = 10f;
    
    // Look-at target (resolved entity position)
    private Vec3 lookAtPosition = null;
    private CameraPath.LookAtTarget activePathTarget = null; // Store original path look-at target
    private Vec3 baseLookAtPosition = null; // Store last resolved base look-at position
    private Vec3 lookAtVelocity = Vec3.ZERO;
    private float lookAtWeight = 0f; // 0.0 = keyframe rotation, 1.0 = look-at rotation
    private float focusFovWeight = 0f; // 0.0 = normal, 1.0 = zoomed
    private static final float FOCUS_FOV_ZOOM = 0.65f; // Zoom to 65% FOV (stronger zoom)
    
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
    
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            getInstance().gameTick();
            // Always tick animation manager for standalone animations
            com.warmpixel.storyadventure.client.animation.AnimationManager.getInstance().tick();
        });
    }
    
    // ==================== Private Helpers ====================
    
    private void applyPose(String npcName, String poseId) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;
            
            net.minecraft.resources.ResourceLocation poseLoc = net.minecraft.resources.ResourceLocation.parse(poseId);
            de.markusbordihn.easynpc.data.animation.AnimationData.Animation animation = 
                de.markusbordihn.easynpc.client.pose.PoseManager.getPoseData(poseLoc);
            
            if (animation == null) {
                StoryAdventureMod.LOGGER.warn("[CinematicCamera] Pose data not found for {}", poseId);
                return;
            }
            
            // Find NPC in client world
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof de.markusbordihn.easynpc.entity.easynpc.EasyNPC<?> easyNPC) {
                    // Try to match by various means
                    if (entity.getScoreboardName().equalsIgnoreCase(npcName) || 
                        entity.getTags().contains(npcName) ||
                        (entity.getCustomName() != null && entity.getCustomName().getString().equals(npcName)) ||
                        (entity.getCustomName() != null && entity.getCustomName().getString().toLowerCase().contains(npcName.toLowerCase()))) {
                        
                        // We must run this on the main thread
                        mc.execute(() -> {
                            de.markusbordihn.easynpc.client.pose.PoseManager.setModelPose(easyNPC, animation);
                        });
                        StoryAdventureMod.LOGGER.info("[CinematicCamera] Applied pose {} to {}", poseId, npcName);
                        // We found one match, but there could be more? We usually assume unique Names in range.
                        // Break to avoid double application or apply to all? Let's apply to all matches.
                    }
                }
            }
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("Failed to apply pose {} to {}", poseId, npcName, e);
        }
    }

    private void startAnimation(String npcName, String animationId) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;

        // Find NPC in client world
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
               // Try to match by various means
                if (entity.getScoreboardName().equalsIgnoreCase(npcName) || 
                    entity.getTags().contains(npcName) ||
                    (entity.getCustomName() != null && entity.getCustomName().getString().equals(npcName)) ||
                    (entity.getCustomName() != null && entity.getCustomName().getString().toLowerCase().contains(npcName.toLowerCase()))) {
                    
                    com.warmpixel.storyadventure.client.animation.AnimationManager.getInstance().startAnimation(living, animationId);
                    StoryAdventureMod.LOGGER.info("[CinematicCamera] Started animation {} on {}", animationId, npcName);
                }
            }
        }
    }

    private void stopAnimation(String npcName) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;

        // Find NPC in client world
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
               // Try to match by various means
                if (entity.getScoreboardName().equalsIgnoreCase(npcName) || 
                    entity.getTags().contains(npcName) ||
                    (entity.getCustomName() != null && entity.getCustomName().getString().equals(npcName)) ||
                    (entity.getCustomName() != null && entity.getCustomName().getString().toLowerCase().contains(npcName.toLowerCase()))) {
                    
                    com.warmpixel.storyadventure.client.animation.AnimationManager.getInstance().stopAnimation(living);
                    StoryAdventureMod.LOGGER.info("[CinematicCamera] Stopped animation on {}", npcName);
                }
            }
        }
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
        
        this.subtitles = config.subtitles != null ? config.subtitles : new java.util.ArrayList<>();
        this.currentSubtitle = null;
        this.currentSubtitleVoiceover = null;
        this.lastSubtitleIndex = -1;
        this.subtitleDisplayedCharacters = 0;
        this.subtitleComplete = false;
        StoryAdventureMod.LOGGER.info("[CinematicCamera] startCutscene: loaded {} subtitles, totalDuration={} ticks", 
            this.subtitles.size(), path.getTotalDurationTicks());
        
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
        
        this.animations = config.animations != null ? config.animations : new java.util.ArrayList<>();
        this.triggeredAnimations.clear();
        
        // Initialize look-at target
        this.activePathTarget = path.getLookAtTarget();
        if (activePathTarget != null) {
            resolveLookAtInitialPosition(activePathTarget);
            baseLookAtPosition = lookAtPosition;
            lookAtWeight = 1.0f;
        } else {
            lookAtPosition = null;
            baseLookAtPosition = null;
            lookAtWeight = 0f;
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
        
        // Stop all animations on entities
        // We can't easy stop specific ones without tracking, but AnimationManager handles cleanup naturally or we can force stop if needed.
        // For now, let them finish or loop until natural end or overwritten.
        // Actually, better to clear active animations if we want instant stop?
        // com.warmpixel.storyadventure.client.animation.AnimationManager.getInstance().stopAll(); // Not implemented yet
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
        
        // Update subtitles
        updateSubtitles(currentExactTick);
        updateSubtitleTypewriter();



        // Update animations
        for (int i = 0; i < animations.size(); i++) {
            NPCAnimation anim = animations.get(i);
            if (currentExactTick >= anim.tick() && !triggeredAnimations.contains(i)) {
                triggeredAnimations.add(i);

                String poseId = anim.pose();
                String npcName = anim.npc();

                if (poseId.startsWith("storyadventure:")) {
                     startAnimation(npcName, poseId);
                } else {
                     // Try to see if this is an EasyNPC animation with keyframes
                     try {
                         net.minecraft.resources.ResourceLocation poseLoc = net.minecraft.resources.ResourceLocation.parse(poseId);
                         de.markusbordihn.easynpc.data.animation.AnimationData.Animation easyAnim = 
                             de.markusbordihn.easynpc.client.pose.PoseManager.getPoseData(poseLoc);
                             
                         boolean isAnimated = false;
                         if (easyAnim != null) {
                             // Check for keyframes in any bone
                             if (easyAnim.getBones() != null) {
                                  isAnimated = easyAnim.getBones().values().stream()
                                      .anyMatch(b -> b.getKeyframeRotation() != null && !b.getKeyframeRotation().isEmpty());
                             }
                         }
                         
                         if (isAnimated) {
                             // Register in our manager if not present
                             com.warmpixel.storyadventure.client.animation.AnimationManager animMgr = 
                                 com.warmpixel.storyadventure.client.animation.AnimationManager.getInstance();
                                 
                             if (!animMgr.hasAnimation(poseLoc)) {
                                 animMgr.registerAnimation(poseLoc, 
                                     new com.warmpixel.storyadventure.client.animation.AnimationDefinition(easyAnim));
                                 StoryAdventureMod.LOGGER.info("[CinematicCamera] Registered EasyNPC animation: {}", poseId);
                             }
                             
                             // Start it using our system
                             startAnimation(npcName, poseId);
                         } else {
                             // Static pose
                             stopAnimation(npcName);
                             applyPose(npcName, poseId);
                         }
                     } catch (Exception e) {
                         StoryAdventureMod.LOGGER.error("Failed to check EasyNPC animation {}", poseId, e);
                         // Fallback
                         applyPose(npcName, poseId);
                     }
                }
            }
        }
        
        // Check for cutscene completion
        if (currentExactTick >= totalDurationTicks) {
            StoryAdventureMod.LOGGER.info("[CinematicCamera] Cutscene completed naturally");
            stopCutscene();
        }
    }
    
    private void updateSubtitleTypewriter() {
        if (currentSubtitle != null && subtitleDisplayedCharacters < currentSubtitle.length()) {
            long now = System.currentTimeMillis();
            if (now - lastSubtitleCharTime >= SUBTITLE_CHAR_DELAY_MS) {
                subtitleDisplayedCharacters++;
                lastSubtitleCharTime = now;
            }
        } else {
            subtitleComplete = true;
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
        if (lookAtWeight > 0.001f && lookAtPosition != null) {
            CameraPath.CameraState lookAtState = applyLookAt(targetState);
            targetState = CameraPath.CameraState.lerpRotationOnly(targetState, lookAtState, lookAtWeight);
            
            // Apply FOV Zoom
            // Apply FOV Zoom & Dynamic Close-Up Position
            if (focusFovWeight > 0.001f) {
                float zoomedFov = targetState.getFov() * (1.0f - focusFovWeight * (1.0f - FOCUS_FOV_ZOOM));
                
                // Calculate Dynamic Close-Up Position
                // Instead of being far away, we blend towards a position closer to the NPC
                Vec3 currentPos = targetState.getPosition();
                Vec3 toCam = currentPos.subtract(lookAtPosition);
                double currentDist = toCam.length();
                double targetDist = 3.5; // Ideal distance for talking (3.5 blocks)
                
                Vec3 newPos = currentPos;
                if (currentDist > targetDist) {
                    // Position along the vector from NPC to Camera, but at targetDist
                    Vec3 idealPos = lookAtPosition.add(toCam.normalize().scale(targetDist));
                    
                    // Smoothly blend to this position based on focus weight
                    // We use a stronger curve for position to ensure we really get there when focused
                    float posWeight = focusFovWeight * focusFovWeight; // Quadratic for smoother entry
                    newPos = currentPos.lerp(idealPos, posWeight);
                }
                
                targetState = new CameraPath.CameraState(newPos, targetState.getYaw(), targetState.getPitch(), targetState.getRoll(), zoomedFov);
            }
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
        if (lookAtWeight > 0.001f && lookAtPosition != null) {
            CameraPath.CameraState lookAtState = applyLookAt(currentState);
            currentState = CameraPath.CameraState.lerpRotationOnly(currentState, lookAtState, lookAtWeight);
            
            // Apply FOV Zoom
            // Apply FOV Zoom & Dynamic Close-Up Position
            if (focusFovWeight > 0.001f) {
                float zoomedFov = currentState.getFov() * (1.0f - focusFovWeight * (1.0f - FOCUS_FOV_ZOOM));
                
                // Calculate Dynamic Close-Up Position
                Vec3 currentPos = currentState.getPosition();
                Vec3 toCam = currentPos.subtract(lookAtPosition);
                double currentDist = toCam.length();
                double targetDist = 3.5;
                
                Vec3 newPos = currentPos;
                if (currentDist > targetDist) {
                    Vec3 idealPos = lookAtPosition.add(toCam.normalize().scale(targetDist));
                    float posWeight = focusFovWeight * focusFovWeight;
                    newPos = currentPos.lerp(idealPos, posWeight);
                }

                currentState = new CameraPath.CameraState(newPos, currentState.getYaw(), currentState.getPitch(), currentState.getRoll(), zoomedFov);
            }
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
    
    /**
     * Resolve the initial position for a look-at target.
     */
    private void resolveLookAtInitialPosition(CameraPath.LookAtTarget target) {
        if (target.getType() == CameraPath.LookAtTarget.Type.POSITION) {
            lookAtPosition = target.getPosition();
        } else {
            // Use the new resolution method for both entity paths and focus targets
            Vec3 targetPos = resolveFocusTarget(target.getEntitySelector());
            if (targetPos != null) {
                lookAtPosition = targetPos;
            } else {
                // Fallback to player eye level if not found yet
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null && mc.player != null) {
                    lookAtPosition = mc.player.position().add(0, mc.player.getEyeHeight(), 0);
                }
            }
        }
    }
    
    // Expanded to support "block:x,y,z" and "x,y,z"
    private Vec3 resolveFocusTarget(String selector) {
        if (selector == null || selector.isEmpty()) return null;
        
        // Handle coordinates (block:x,y,z or just x,y,z)
        if (selector.startsWith("block:") || selector.contains(",")) {
            try {
                String clean = selector.startsWith("block:") ? selector.substring(6) : selector;
                String[] parts = clean.split(",");
                if (parts.length == 3) {
                    double x = Double.parseDouble(parts[0].trim());
                    double y = Double.parseDouble(parts[1].trim());
                    double z = Double.parseDouble(parts[2].trim());
                    // Focus on the center of the block
                    return new Vec3(x + 0.5, y + 0.5, z + 0.5);
                }
            } catch (NumberFormatException e) {
                StoryAdventureMod.LOGGER.debug("[CinematicCamera] Invalid coordinate format: {}", selector);
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        
        // Handle special selectors
        if (selector.equals("@p") || selector.equals("@s")) {
            if (mc.player != null) return mc.player.position().add(0, mc.player.getEyeHeight(), 0);
        }
        
        // Search for entity by name, scoreboard name, or tag
        String selectorLower = selector.toLowerCase();
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            // Exact matches
            if (selector.equals(entity.getScoreboardName()) || 
                entity.getTags().contains(selector) ||
                (entity.getCustomName() != null && selector.equals(entity.getCustomName().getString())) ||
                selector.equalsIgnoreCase(entity.getUUID().toString())) {
                return entity.position().add(0, entity.getEyeHeight(), 0);
            }
            
            // Fuzzy matches (contains)
            if ((entity.getCustomName() != null && entity.getCustomName().getString().toLowerCase().contains(selectorLower)) ||
                entity.getScoreboardName().toLowerCase().contains(selectorLower) ||
                entity.getTags().stream().anyMatch(t -> t.toLowerCase().contains(selectorLower))) {
                return entity.position().add(0, entity.getEyeHeight(), 0);
            }
        }
        
        return null;
    }
    
    /**
     * Update look-at target for entity tracking (call each tick).
     */
    public void updateLookAtTarget(Vec3 newPosition, boolean highPriority) {
        if (lookAtPosition != null && newPosition != null) {
            // Smooth look-at target movement
            Vec3 diff = newPosition.subtract(lookAtPosition);
            // Higher speed for high priority (subtitles) vs low speed for base path
            float speed = highPriority ? 0.25f : 0.12f;
            lookAtPosition = lookAtPosition.add(diff.scale(speed));
        } else {
            lookAtPosition = newPosition;
        }
    }
    
    @Deprecated
    public void updateLookAtTarget(Vec3 newPosition) {
        updateLookAtTarget(newPosition, false);
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
    
    private void updateSubtitles(float tick) {
        currentSubtitle = null;
        currentSubtitleVoiceover = null;
        int newSubtitleIndex = -1;
        
        Vec3 targetFocus = baseLookAtPosition;
        
        for (int i = 0; i < subtitles.size(); i++) {
            Subtitle sub = subtitles.get(i);
            if (tick >= sub.startTick() && tick < sub.startTick() + sub.durationTick()) {
                currentSubtitle = sub.text();
                currentSubtitleVoiceover = sub.voiceover();
                newSubtitleIndex = i;
                
                // Handle focus target (NPC, Block, Entity)
                if (sub.focusTarget() != null && !sub.focusTarget().isEmpty()) {
                    Vec3 focusPos = resolveFocusTarget(sub.focusTarget());
                    if (focusPos != null) {
                        targetFocus = focusPos;
                    }
                }
                break;
            }
        }
        
        // If no subtitle focus, update base focus from path if it's dynamic
        if (targetFocus == baseLookAtPosition && activePathTarget != null && activePathTarget.getType() == CameraPath.LookAtTarget.Type.ENTITY) {
            Vec3 currentPathTargetPos = resolveFocusTarget(activePathTarget.getEntitySelector());
            if (currentPathTargetPos != null) {
                baseLookAtPosition = currentPathTargetPos;
                targetFocus = baseLookAtPosition;
            }
        }
        
        // Update look-at weight for smooth transitions
        float targetWeight = (targetFocus != null) ? 1.0f : 0.0f;
        lookAtWeight += (targetWeight - lookAtWeight) * 0.15f;
        
        // Update FOV focus weight
        boolean isNpcFocus = targetFocus != baseLookAtPosition && targetFocus != null;
        float targetFovWeight = isNpcFocus ? 1.0f : 0.0f;
        focusFovWeight += (targetFovWeight - focusFovWeight) * 0.1f;
        
        // Apply the resolved focus (smoothed target movement)
        updateLookAtTarget(targetFocus, isNpcFocus);
        
        
        // Trigger voiceover when a new subtitle becomes active
        if (newSubtitleIndex != -1 && newSubtitleIndex != lastSubtitleIndex) {
            StoryAdventureMod.LOGGER.info("[CinematicCamera] Subtitle changed: index={} -> {}, text='{}', voiceover='{}'", 
                lastSubtitleIndex, newSubtitleIndex, currentSubtitle, currentSubtitleVoiceover);
            lastSubtitleIndex = newSubtitleIndex;
            
            // Reset typewriter when subtitle changes
            subtitleDisplayedCharacters = 0;
            subtitleComplete = false;
            lastSubtitleCharTime = System.currentTimeMillis();
            
            if (currentSubtitleVoiceover != null && !currentSubtitleVoiceover.isEmpty()) {
                // Play the voiceover audio on the client
                com.warmpixel.storyadventure.client.audio.VoiceoverManager.getInstance()
                    .playVoiceover(currentSubtitleVoiceover, "cutscene");
            }
        } else if (newSubtitleIndex == -1) {
            lastSubtitleIndex = -1;
        }
    }
    
    public String getCurrentSubtitle() {
        return currentSubtitle;
    }
    
    public int getSubtitleDisplayedCharacters() {
        return subtitleDisplayedCharacters;
    }
    
    public boolean isSubtitleComplete() {
        return subtitleComplete;
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
        private java.util.List<Subtitle> subtitles = new java.util.ArrayList<>();
        private java.util.List<NPCAnimation> animations = new java.util.ArrayList<>();
        
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
        
        public CutsceneConfig setSubtitles(java.util.List<Subtitle> subtitles) {
            this.subtitles = subtitles;
            return this;
        }

        public CutsceneConfig setAnimations(java.util.List<NPCAnimation> animations) {
            this.animations = animations;
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
        public java.util.List<Subtitle> getSubtitles() { return subtitles; }
    }
}