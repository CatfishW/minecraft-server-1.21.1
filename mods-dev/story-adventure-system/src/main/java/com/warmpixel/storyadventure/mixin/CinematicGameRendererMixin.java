package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to override FOV during cinematic cutscenes.
 */
@Mixin(GameRenderer.class)
public class CinematicGameRendererMixin {
    
    /**
     * Override FOV when cinematic camera is active.
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void storyadventure$overrideFov(Camera camera, float partialTicks, boolean useFovSetting, 
                                             CallbackInfoReturnable<Double> cir) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive()) {
            cir.setReturnValue((double) controller.getCameraFov());
        }
    }
}
