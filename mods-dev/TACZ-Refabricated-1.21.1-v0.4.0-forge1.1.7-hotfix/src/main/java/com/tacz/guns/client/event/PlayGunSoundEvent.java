package com.tacz.guns.client.event;

import cn.sh1rocu.tacz.api.event.PlaySoundSourceEvent;
import com.mojang.blaze3d.audio.SoundBuffer;
import com.tacz.guns.client.sound.GunSoundInstance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class PlayGunSoundEvent {
    public static void onPlaySoundSource(PlaySoundSourceEvent event) {
        if (event.getSound() instanceof GunSoundInstance instance) {
            SoundBuffer soundBuffer = instance.getSoundBuffer();
            if (soundBuffer != null) {
                event.getChannel().attachStaticBuffer(soundBuffer);
                event.getChannel().play();
            }
        }
    }
}
