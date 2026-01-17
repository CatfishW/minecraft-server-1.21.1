package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle ESC key for skipping cutscenes.
 */
@Mixin(Minecraft.class)
public class CinematicMinecraftMixin {
    
    /**
     * Intercept pause menu to allow cutscene skipping with ESC.
     */
    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void storyadventure$interceptPauseForSkip(boolean pauseOnly, CallbackInfo ci) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive() && controller.isSkippable()) {
            controller.skipCutscene();
            // Cancel the pause action - don't open pause menu during cutscene
            ci.cancel();
        }
    }
}