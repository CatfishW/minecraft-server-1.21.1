package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to prevent player interactions during cinematic cutscenes.
 */
@Mixin(Minecraft.class)
public class CinematicPlayerAttackMixin {
    
    /**
     * Prevent left-click (attack/break) during cutscenes.
     */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void storyadventure$preventAttack(CallbackInfoReturnable<Boolean> cir) {
        if (CinematicCameraController.getInstance().isActive()) {
            cir.setReturnValue(false);
        }
    }
    
    /**
     * Prevent right-click (use/interact) during cutscenes.
     */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void storyadventure$preventUse(CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
    
    /**
     * Prevent block picking during cutscenes.
     */
    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void storyadventure$preventPick(CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
}