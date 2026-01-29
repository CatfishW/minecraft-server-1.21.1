package com.warmpixel.storyadventure.client.audio;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Manages background music playback on the client.
 * Supports looping, fading, and exclusive BGM management.
 */
@Environment(EnvType.CLIENT)
public class BGMManager {
    
    private static BGMManager instance;
    private BGMSoundInstance currentBGM = null;
    
    private BGMManager() {}

    public static BGMManager getInstance() {
        if (instance == null) {
            instance = new BGMManager();
        }
        return instance;
    }
    
    public void playBGM(String soundPath, float volume, boolean loop, int fadeTicks) {
        if (soundPath == null || soundPath.isEmpty()) return;

        // Sound Event transformation
        String normalizedPath = soundPath.toLowerCase().trim();
        if (normalizedPath.endsWith(".ogg")) normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 4);
        if (normalizedPath.contains(":")) normalizedPath = normalizedPath.substring(normalizedPath.indexOf(":") + 1);
        
        String dotPath = normalizedPath.replace("/", ".");
        final String soundEventName = dotPath.startsWith("bgm.") ? dotPath : "bgm." + dotPath;
        final ResourceLocation soundLocation = ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, soundEventName);

        Minecraft.getInstance().execute(() -> {
            if (currentBGM != null) {
                if (currentBGM.getSound().getLocation().equals(soundLocation)) {
                    // Already playing this BGM, just update volume/loop if needed?
                    // For now, let it continue.
                    return;
                }
                currentBGM.fadeOut(fadeTicks);
            }

            currentBGM = new BGMSoundInstance(soundLocation, volume, loop, fadeTicks);
            Minecraft.getInstance().getSoundManager().play(currentBGM);
            StoryAdventureMod.LOGGER.info("[BGMManager] Playing BGM: {}", soundLocation);
        });
    }

    public void stopBGM(int fadeTicks) {
        Minecraft.getInstance().execute(() -> {
            if (currentBGM != null) {
                currentBGM.fadeOut(fadeTicks);
                currentBGM = null;
            }
        });
    }

    /**
     * Specialized sound instance for BGM with fading support.
     */
    private static class BGMSoundInstance extends SimpleSoundInstance implements TickableSoundInstance {
        private final boolean loop;
        private final float targetVolume;
        private int fadeTicks;
        private int currentFadeTicks = 0;
        private boolean fadingOut = false;
        private boolean finished = false;

        public BGMSoundInstance(ResourceLocation location, float volume, boolean loop, int fadeInTicks) {
            super(location, SoundSource.RECORDS, volume, 1.0f, RandomSource.create(), loop, 0, Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
            this.loop = loop;
            this.targetVolume = volume;
            this.fadeTicks = fadeInTicks;
            if (this.fadeTicks <= 0) this.volume = volume;
        }

        @Override
        public void tick() {
            if (finished) return;

            if (fadingOut) {
                currentFadeTicks--;
                if (currentFadeTicks <= 0) {
                    this.volume = 0;
                    this.finished = true;
                } else {
                    this.volume = (float) currentFadeTicks / fadeTicks * targetVolume;
                }
            } else if (currentFadeTicks < fadeTicks) {
                currentFadeTicks++;
                this.volume = (float) currentFadeTicks / fadeTicks * targetVolume;
            } else {
                this.volume = targetVolume;
            }
        }

        public void fadeOut(int ticks) {
            if (ticks <= 0) {
                this.finished = true;
                return;
            }
            this.fadingOut = true;
            this.fadeTicks = ticks;
            this.currentFadeTicks = ticks;
        }

        @Override
        public boolean isStopped() {
            return finished;
        }
    }
}
