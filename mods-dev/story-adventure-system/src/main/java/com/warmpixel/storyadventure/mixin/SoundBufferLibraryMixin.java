package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.client.audio.ExternalOggAudioStream;
import com.warmpixel.storyadventure.client.audio.ExternalSoundRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Environment(EnvType.CLIENT)
@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void storyadventure$interceptExternalStream(ResourceLocation location, boolean looped, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        // Check if this is one of our external sounds
        if (location != null && 
            location.getNamespace().equals(StoryAdventureMod.MOD_ID) && 
            location.getPath().startsWith("external/")) {
            
            StoryAdventureMod.LOGGER.debug("[SoundBufferLibraryMixin] Intercepting external sound request: {}", location);
            
            String filePath = ExternalSoundRegistry.getExternalPath(location);
            
            if (filePath != null) {
                StoryAdventureMod.LOGGER.debug("[SoundBufferLibraryMixin] Found external file path: {}", filePath);
                
                CompletableFuture<AudioStream> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        Path path = Paths.get(filePath);
                        StoryAdventureMod.LOGGER.debug("[SoundBufferLibraryMixin] Loading audio from: {}", path);
                        return new ExternalOggAudioStream(path);
                    } catch (IOException e) {
                        StoryAdventureMod.LOGGER.error("[SoundBufferLibraryMixin] Failed to load external audio: " + filePath, e);
                        throw new CompletionException(e);
                    }
                });
                
                cir.setReturnValue(future);
                cir.cancel();
            } else {
                StoryAdventureMod.LOGGER.warn("[SoundBufferLibraryMixin] No external path registered for: {} (registry size: {})", 
                    location, ExternalSoundRegistry.size());
            }
        }
    }
}