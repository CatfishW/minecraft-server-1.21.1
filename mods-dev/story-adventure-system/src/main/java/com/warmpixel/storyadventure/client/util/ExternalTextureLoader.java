package com.warmpixel.storyadventure.client.util;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility to load external textures from the config folder.
 */
public class ExternalTextureLoader {

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final String BASE_PATH = "config/storyadventure/profiles/";

    public static ResourceLocation getProfileTexture(String profileId) {
        if (profileId == null || profileId.isEmpty()) return null;
        
        String cleanId = profileId.trim().toLowerCase(java.util.Locale.ROOT);
        
        if (CACHE.containsKey(cleanId)) {
            return CACHE.get(cleanId);
        }

        // Use internal resource directly - this is the standard way to load mod assets
        ResourceLocation internalLoc = ResourceLocation.fromNamespaceAndPath("storyadventure", "textures/gui/profiles/" + cleanId + ".png");
        
        // We can still try to check if it exists, or just return it. 
        // Returning it directly ensures that if the file is there (which we verified), it will load.
        // If it's missing, it will show the purple/black checkerboard, which is standard debugging behavior.
        CACHE.put(cleanId, internalLoc);
        return internalLoc;
    }
    
    public static void clearCache() {
        // Note: Dynamic textures should ideally be released, but for this mod's scale/lifecycle it might be okay.
        // Proper cleanup would involve TextureManager.release()
        CACHE.clear();
    }
}
