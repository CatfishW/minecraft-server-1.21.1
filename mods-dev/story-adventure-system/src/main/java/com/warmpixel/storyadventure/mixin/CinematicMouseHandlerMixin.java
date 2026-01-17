package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to disable mouse look during cinematic cutscenes.
 */
@Mixin(MouseHandler.class)
public class CinematicMouseHandlerMixin {
    
    /**
     * Disable mouse look rotation during cutscenes.
     */
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void storyadventure$disableMouseLook(CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
}   