package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to override camera position and rotation during cinematic cutscenes.
 */
@Mixin(Camera.class)
public abstract class CinematicCameraMixin {
    
    @Shadow
    private float xRot;
    
    @Shadow
    private float yRot;
    
    @Shadow
    protected abstract void setPosition(double x, double y, double z);
    
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);
    
    /**
     * Override camera setup when cinematic is active.
     */
    @Inject(method = "setup", at = @At("TAIL"))
    private void storyadventure$overrideCameraSetup(BlockGetter level, Entity focusedEntity, 
                                                     boolean detached, boolean thirdPersonReverse, 
                                                     float partialTick, CallbackInfo ci) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive()) {
            // Override camera position
            var pos = controller.getCameraPosition();
            setPosition(pos.x, pos.y, pos.z);
            
            // Override camera rotation
            setRotation(controller.getCameraYaw(), controller.getCameraPitch());
        }
    }
}
