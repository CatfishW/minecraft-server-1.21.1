package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide boss bars during cinematic cutscenes.
 */
@Mixin(BossHealthOverlay.class)
public class CinematicBossBarMixin {
    
    /**
     * Hide boss bars during cutscenes.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideBossBar(GuiGraphics graphics, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
