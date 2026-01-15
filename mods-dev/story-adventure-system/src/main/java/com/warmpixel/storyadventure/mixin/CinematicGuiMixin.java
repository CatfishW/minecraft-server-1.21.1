package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide HUD elements during cinematic cutscenes.
 */
@Mixin(Gui.class)
public class CinematicGuiMixin {
    
    /**
     * Hide the main HUD group (hotbar, health, exp, etc.) during cutscenes.
     */
    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideHudGroup(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
    
    /**
     * Hide the crosshair during cutscenes.
     */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
