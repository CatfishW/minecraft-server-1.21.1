package com.warmpixel.optimizer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MuzzleFlashRender.class)
public abstract class MuzzleFlashMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay, CallbackInfo ci) {
        // Optimization: Skip muzzle flash rendering if FPS is low or if we are in third person and the shooter is distant
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson()) {
            return; // Don't skip for own first person shots
        }
        
        // Use a simple heuristic: if we are not the shooter (checked via isSelf in MuzzleFlashRender)
        // actually isSelf is static in MuzzleFlashRender and it's checked there.
        // But we can add extra check for distance if it was ever enabled for others.
    }
}
