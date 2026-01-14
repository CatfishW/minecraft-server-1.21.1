package com.warmpixel.optimizer.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void onShouldRender(Entity entity, net.minecraft.client.renderer.culling.Frustum frustum, double d, double e, double f, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double distanceSq = entity.distanceToSqr(mc.player);
        
        // More aggressive frustum culling or distance culling
        if (distanceSq > 128 * 128) {
            cir.setReturnValue(false);
        }
    }
}
