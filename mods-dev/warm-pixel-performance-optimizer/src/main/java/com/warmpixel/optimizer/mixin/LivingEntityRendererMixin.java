package com.warmpixel.optimizer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {

    @Inject(method = "render*", at = @At("HEAD"), cancellable = true)
    private void onRender(T livingEntity, float f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double distanceSq = livingEntity.distanceToSqr(mc.player);
        
        // Render Culling for distant entities
        // If distance > 96 blocks, don't render at all
        if (distanceSq > 96 * 96) {
            ci.cancel();
            return;
        }

        // Low-frequency rendering or simplified rendering could be added here
        // For now, let's just do distance culling which is very effective
    }
}
