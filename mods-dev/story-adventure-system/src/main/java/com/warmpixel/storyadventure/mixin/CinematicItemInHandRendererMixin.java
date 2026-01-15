package com.warmpixel.storyadventure.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide held items/arms during cinematic cutscenes.
 */
@Mixin(ItemInHandRenderer.class)
public class CinematicItemInHandRendererMixin {
    
    /**
     * Skip rendering hands and items when a cutscene is active.
     */
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideHandsDuringCutscene(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int light, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
