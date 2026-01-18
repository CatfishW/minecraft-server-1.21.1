package com.warmpixel.storyadventure.client.audio;

import com.warmpixel.storyadventure.StoryAdventureMod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.util.RandomSource;

/**
 * Manages voiceover audio playback on the client.
 * Uses Minecraft's built-in resource system for sound loading.
 */
@Environment(EnvType.CLIENT)
public class VoiceoverManager {
    
    private static VoiceoverManager instance;
    private SoundInstance currentVoiceover = null;
    private final Object lock = new Object();
    
    private VoiceoverManager() {}

    public static VoiceoverManager getInstance() {
        if (instance == null) {
            instance = new VoiceoverManager();
        }
        return instance;
    }
    
    /**
     * Play a voiceover sound from the mod's assets or config folder.
     * @param soundPath The path to the voiceover, e.g. "stranger_things_hawkins/bg_story_1_msg_0"
     * @param volume Volume of the sound (0.0-1.0)
     * @param pitch Pitch of the sound
     * @param characterId ID of the speaking character (for logging)
     */
    public void playVoiceover(String soundPath, float volume, float pitch, String characterId) {
        if (soundPath == null || soundPath.isEmpty()) return;

        // Normalize the sound path (ensure lowercase and remove extension)
        String normalizedPath = soundPath.toLowerCase().trim();
        if (normalizedPath.endsWith(".ogg")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 4);
        }
        
        // Strip namespace prefix if present (e.g., "storyadventure:" or "minecraft:")
        if (normalizedPath.contains(":")) {
            normalizedPath = normalizedPath.substring(normalizedPath.indexOf(":") + 1);
        }
        
        // Convert path to sound event name (e.g. stranger_things_hawkins/bg_story_1 -> voiceover.stranger_things_hawkins.bg_story_1)
        final String soundEventName = "voiceover." + normalizedPath.replace("/", ".");
        
        StoryAdventureMod.LOGGER.info("[VoiceoverManager] Requesting playback: {} (character: {})", soundEventName, characterId);

        // Schedule on the main thread
        Minecraft.getInstance().execute(() -> {
            synchronized (lock) {
                stopCurrentVoiceoverInternal();
                
                try {
                    // Create the ResourceLocation for the sound event
                    ResourceLocation soundLocation = ResourceLocation.fromNamespaceAndPath(
                        StoryAdventureMod.MOD_ID, 
                        soundEventName
                    );
                    
                    // Create a standard sound instance (since it's now registered in sounds.json)
                    currentVoiceover = new SimpleSoundInstance(
                        soundLocation,
                        SoundSource.MASTER,
                        volume,
                        pitch,
                        RandomSource.create(),
                        false,  // looping
                        0,      // delay
                        SoundInstance.Attenuation.NONE,
                        0.0,    // x
                        0.0,    // y
                        0.0,    // z
                        true    // relative (plays at player's position)
                    );
                    
                    // Play it through Minecraft's sound manager
                    Minecraft.getInstance().getSoundManager().play(currentVoiceover);
                    
                    StoryAdventureMod.LOGGER.info("[VoiceoverManager] Now playing: {} (character: {})", 
                        soundLocation, characterId);
                    
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("[VoiceoverManager] Error playing voiceover {}: {}", soundEventName, e.getMessage());
                    e.printStackTrace();
                    currentVoiceover = null;
                }
            }
        });
    }
    
    public void playVoiceover(String soundPath, String characterId) {
        playVoiceover(soundPath, 1.0f, 1.0f, characterId);
    }
    
    /**
     * Stop the currently playing voiceover.
     */
    public void stopCurrentVoiceover() {
        Minecraft.getInstance().execute(() -> {
            synchronized (lock) {
                stopCurrentVoiceoverInternal();
            }
        });
    }
    
    private void stopCurrentVoiceoverInternal() {
        if (currentVoiceover != null) {
            try {
                Minecraft.getInstance().getSoundManager().stop(currentVoiceover);
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.debug("[VoiceoverManager] Error stopping voiceover", e);
            }
            currentVoiceover = null;
        }
    }
    
    /**
     * Check if a voiceover is currently playing.
     */
    public boolean isPlaying() {
        synchronized (lock) {
            if (currentVoiceover == null) {
                return false;
            }
            try {
                return Minecraft.getInstance().getSoundManager().isActive(currentVoiceover);
            } catch (Exception e) {
                return false;
            }
        }
    }
    
    public void cleanup() {
        stopCurrentVoiceover();
    }

    /**
     * Check if a voiceover exists in the mod's assets.
     * Note: This now checks if the sound event is registered.
     */
    public static boolean voiceoverExists(String soundPath) {
        String normalizedPath = soundPath;
        if (normalizedPath.endsWith(".ogg")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 4);
        }
        
        // Strip namespace prefix if present
        if (normalizedPath.contains(":")) {
            normalizedPath = normalizedPath.substring(normalizedPath.indexOf(":") + 1);
        }
        
        String soundEventName = "voiceover." + normalizedPath.replace("/", ".");
        ResourceLocation soundLocation = ResourceLocation.fromNamespaceAndPath(
            StoryAdventureMod.MOD_ID, 
            soundEventName
        );
        
        // Check if the sound event is registered in the sound manager
        try {
            boolean exists = Minecraft.getInstance().getSoundManager().getSoundEvent(soundLocation) != null;
            StoryAdventureMod.LOGGER.debug("[VoiceoverManager] Checked existence of sound event: {} -> {}", soundLocation, exists);
            return exists;
        } catch (Exception e) {
            return false;
        }
    }
}