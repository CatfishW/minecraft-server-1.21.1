package com.warmpixel.storyadventure.client.audio;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * A SoundInstance that points to an external file.
 * The actual loading is handled by a Mixin in SoundBufferLibrary.
 */
@Environment(EnvType.CLIENT)
public class ExternalSoundInstance extends AbstractSoundInstance {
    
    private final String externalFilePath;
    private final ResourceLocation soundResourceLocation;
    private final Sound resolvedSound;

    public ExternalSoundInstance(String soundPath, String absolutePath, float volume, float pitch, SoundSource category) {
        super(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "external/" + sanitizePath(soundPath)), 
              category, SoundInstance.createUnseededRandom());
        
        this.externalFilePath = absolutePath;
        
        // This is the ResourceLocation that will be passed to SoundBufferLibrary.getStream()
        // It MUST match what we register in ExternalSoundRegistry
        this.soundResourceLocation = ResourceLocation.fromNamespaceAndPath(
            StoryAdventureMod.MOD_ID, 
            "external/" + sanitizePath(soundPath)
        );
        
        this.volume = volume;
        this.pitch = pitch;
        this.looping = false;
        this.delay = 0;
        this.relative = true; // Play relative to listener (ui/voiceover style)
        this.attenuation = Attenuation.NONE;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        
        // Create the Sound object - the path here MUST match soundResourceLocation
        this.resolvedSound = new Sound(
            this.soundResourceLocation,  // "storyadventure:external/path"
            ConstantFloat.of(volume),                // volume
            ConstantFloat.of(pitch),                 // pitch
            1,                                       // weight
            Sound.Type.FILE,                         // type - FILE for streaming
            true,                                    // stream - true for voiceovers
            false,                                   // preload
            16                                       // attenuation distance
        );
        
        // Set the sound field from parent class
        this.sound = this.resolvedSound;
        
        StoryAdventureMod.LOGGER.debug("[ExternalSoundInstance] Created: location={}, soundPath={}, file={}", 
            this.location, this.soundResourceLocation, absolutePath);
    }
    
    private static String sanitizePath(String path) {
        // Remove invalid characters from resource location path
        // Only allow: a-z, 0-9, /, ., _, -
        return path.toLowerCase()
                   .replace("\\", "/")
                   .replaceAll("[^a-z0-9/._-]", "_")
                   .replaceAll("_+", "_")
                   .replaceAll("^_|_$", "");
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        // Set the sound field to ensure it's available
        this.sound = this.resolvedSound;
        
        StoryAdventureMod.LOGGER.debug("[ExternalSoundInstance] Resolved: {} -> sound path: {}", 
            this.location, this.resolvedSound.getLocation());
        
        // Return a WeighedSoundEvents that contains our sound
        // The subtitle key can be null for voiceovers
        return new WeighedSoundEvents(this.location, null);
    }
    
    @Override
    public Sound getSound() {
        return this.resolvedSound;
    }

    public String getExternalFilePath() {
        return externalFilePath;
    }
    
    /**
     * Gets the ResourceLocation that will be used by SoundBufferLibrary.
     * This is what needs to be registered in ExternalSoundRegistry.
     */
    public ResourceLocation getSoundResourceLocation() {
        return soundResourceLocation;
    }

    @Override
    public String toString() {
        return "ExternalSoundInstance{location=" + location + ", soundPath=" + soundResourceLocation + ", file=" + externalFilePath + "}";
    }
}