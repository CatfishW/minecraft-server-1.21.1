package com.warmpixel.storyadventure.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Mixin to override camera position and rotation during cinematic cutscenes.
 * Calls renderTick() for smooth frame interpolation before applying camera state.
 */
@Mixin(Camera.class)
public abstract class CinematicCameraMixin {
    
    @Shadow
    protected abstract void setPosition(double x, double y, double z);
    
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);
    
    /**
     * Force first-person mode during cutscenes to prevent third-person offsets.
     */
    @ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean storyadventure$forceFirstPerson(boolean detached) {
        if (CinematicCameraController.getInstance().isActive()) {
            return false;
        }
        return detached;
    }

    /**
     * Modify arguments passed to setPosition to use the cinematic camera position.
     * This ensures the camera is positioned correctly before block/fluid checks occur.
     */
    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void storyadventure$modifyPositionArgs(Args args) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive()) {
            // Update render tick here since we removed the previous redirect that did it
            // This is safe because setPosition is called every frame in setup()
            controller.renderTick(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
            
            Vec3 pos = controller.getCameraPosition();
            if (pos.lengthSqr() > 0.0001) {
                args.set(0, pos.x);
                args.set(1, pos.y);
                args.set(2, pos.z);
            }
        }
    }

    /**
     * Modify arguments passed to setRotation to use the cinematic camera rotation.
     */
    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void storyadventure$modifyRotationArgs(Args args) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive()) {
            args.set(0, controller.getCameraYaw());
            args.set(1, controller.getCameraPitch());
        }
    }
}