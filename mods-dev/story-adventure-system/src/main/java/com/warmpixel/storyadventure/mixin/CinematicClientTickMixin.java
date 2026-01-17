package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to update the cinematic camera controller on each game tick.
 * This ensures timing and state updates happen at a consistent rate.
 */
@Mixin(Minecraft.class)
public class CinematicClientTickMixin {
    
    /**
     * Call gameTick() on the cinematic controller each client tick.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void storyadventure$tickCinematicCamera(CallbackInfo ci) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive()) {
            controller.gameTick();
        }
    }
}