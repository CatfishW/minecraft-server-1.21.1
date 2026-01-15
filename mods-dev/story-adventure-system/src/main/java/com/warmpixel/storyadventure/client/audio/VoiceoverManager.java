package com.warmpixel.storyadventure.client.audio;

import com.warmpixel.storyadventure.StoryAdventureMod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages voiceover audio playback on the client.
 * Integrated with Minecraft's SoundManager for proper volume control and compatibility.
 */
@Environment(EnvType.CLIENT)
public class VoiceoverManager {
    
    private static VoiceoverManager instance;
    private ExternalSoundInstance currentVoiceover = null;
    private final Object lock = new Object();
    
    private VoiceoverManager() {}

    public static VoiceoverManager getInstance() {
        if (instance == null) {
            instance = new VoiceoverManager();
        }
        return instance;
    }
    
    /**
     * Play a voiceover sound from the voiceovers folder.
     */
    public void playVoiceover(String soundPath, float volume, float pitch, String characterId) {
        // Normalize the sound path (remove any extension if present)
        String normalizedPath = soundPath;
        if (normalizedPath.endsWith(".ogg")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 4);
        }
        
        // Build the full path using FabricLoader's config directory
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path voiceoverPath = configDir.resolve("storyadventure").resolve("voiceovers").resolve(normalizedPath + ".ogg");
        
        if (!Files.exists(voiceoverPath)) {
            StoryAdventureMod.LOGGER.warn("[VoiceoverManager] Voiceover file not found: {}", voiceoverPath.toAbsolutePath());
            return;
        }
        
        String absolutePath = voiceoverPath.toAbsolutePath().toString();
        final String finalNormalizedPath = normalizedPath;
        
        // Schedule on the main thread
        Minecraft.getInstance().execute(() -> {
            synchronized (lock) {
                stopCurrentVoiceoverInternal();
                
                try {
                    // Create a custom sound instance
                    currentVoiceover = new ExternalSoundInstance(
                        finalNormalizedPath,
                        absolutePath,
                        volume,
                        pitch,
                        SoundSource.VOICE
                    );
                    
                    // Register the path using the SOUND's location (not the instance location)
                    // This is what SoundBufferLibrary.getStream() will receive
                    ExternalSoundRegistry.registerExternalPath(currentVoiceover.getSoundResourceLocation(), absolutePath);
                    
                    StoryAdventureMod.LOGGER.debug("[VoiceoverManager] Registered external path: {} -> {}", 
                        currentVoiceover.getSoundResourceLocation(), absolutePath);
                    
                    // Play it through Minecraft's sound manager
                    Minecraft.getInstance().getSoundManager().play(currentVoiceover);
                    
                    StoryAdventureMod.LOGGER.info("[VoiceoverManager] Playing voiceover: {} (character: {})", 
                        finalNormalizedPath, characterId);
                    
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("[VoiceoverManager] Failed to play voiceover: " + finalNormalizedPath, e);
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
                ExternalSoundRegistry.removeExternalPath(currentVoiceover.getSoundResourceLocation());
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
        ExternalSoundRegistry.clear();
    }

    public static Path getVoiceoversPath(String storyId) {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("storyadventure").resolve("voiceovers").resolve(storyId);
    }
    
    public static Path getVoiceoversBasePath() {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("storyadventure").resolve("voiceovers");
    }
    
    public static boolean voiceoverExists(String soundPath) {
        String normalizedPath = soundPath;
        if (!normalizedPath.endsWith(".ogg")) {
            normalizedPath = normalizedPath + ".ogg";
        }
        Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("storyadventure").resolve("voiceovers").resolve(normalizedPath);
        return Files.exists(path);
    }
    
    public static void ensureVoiceoverDirectory(String storyId) {
        try {
            Path dir = getVoiceoversPath(storyId);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                StoryAdventureMod.LOGGER.info("[VoiceoverManager] Created voiceover directory: {}", dir);
            }
        } catch (IOException e) {
            StoryAdventureMod.LOGGER.error("[VoiceoverManager] Failed to create voiceover directory", e);
        }
    }
    
    public static void ensureVoiceoverBaseDirectory() {
        try {
            Path dir = getVoiceoversBasePath();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                StoryAdventureMod.LOGGER.info("[VoiceoverManager] Created voiceover base directory: {}", dir);
            }
        } catch (IOException e) {
            StoryAdventureMod.LOGGER.error("[VoiceoverManager] Failed to create voiceover base directory", e);
        }
    }
}