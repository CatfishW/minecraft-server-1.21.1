package com.warmpixel.storyadventure.client.audio;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Registry for external sound file paths.
 * Used by the SoundBufferLibraryMixin to locate files on disk.
 * Thread-safe implementation.
 */
public class ExternalSoundRegistry {
    private static final Map<ResourceLocation, String> EXTERNAL_SOUND_PATHS = new ConcurrentHashMap<>();

    public static void registerExternalPath(ResourceLocation location, String path) {
        EXTERNAL_SOUND_PATHS.put(location, path);
        StoryAdventureMod.LOGGER.debug("[ExternalSoundRegistry] Registered: {} -> {}", location, path);
    }

    public static String getExternalPath(ResourceLocation location) {
        String path = EXTERNAL_SOUND_PATHS.get(location);
        StoryAdventureMod.LOGGER.debug("[ExternalSoundRegistry] Lookup: {} -> {}", location, path);
        return path;
    }
    
    public static boolean isExternalSound(ResourceLocation location) {
        return EXTERNAL_SOUND_PATHS.containsKey(location);
    }

    public static void removeExternalPath(ResourceLocation location) {
        EXTERNAL_SOUND_PATHS.remove(location);
        StoryAdventureMod.LOGGER.debug("[ExternalSoundRegistry] Removed: {}", location);
    }
    
    public static void clear() {
        EXTERNAL_SOUND_PATHS.clear();
        StoryAdventureMod.LOGGER.debug("[ExternalSoundRegistry] Cleared all entries");
    }
    
    public static int size() {
        return EXTERNAL_SOUND_PATHS.size();
    }
}