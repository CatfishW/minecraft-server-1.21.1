package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to disable player keyboard input during cinematic cutscenes.
 */
@Mixin(KeyboardInput.class)
public class CinematicInputMixin {
    
    /**
     * Disable all movement input during cutscenes.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void storyadventure$disableInputDuringCutscene(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive()) {
            Input input = (Input)(Object)this;
            
            // Zero out all movement
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }
}
