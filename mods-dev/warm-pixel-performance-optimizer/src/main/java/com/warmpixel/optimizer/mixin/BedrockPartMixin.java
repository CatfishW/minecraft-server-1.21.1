package com.warmpixel.optimizer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.warmpixel.optimizer.render.RenderOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockPart.class, remap = false)
public abstract class BedrockPartMixin {

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void onCompile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        // We only optimize if the GPU pipeline is ready and active
        // And maybe some config check?
        // Using "this" (BedrockPart) as key
        // Need to pack color from rgba floats
        // Optimization disabled temporarily because it breaks texture rendering (missing Texture Atlas/Context).
        // Restoring vanilla rendering to fix "invisible gun" issue.
        /*
        int r = (int)(red * 255.0f);
        int g = (int)(green * 255.0f);
        int b = (int)(blue * 255.0f);
        int a = (int)(alpha * 255.0f);
        int color = (a << 24) | (b << 16) | (g << 8) | r;
        
        if (RenderOptimizer.INSTANCE.render(this, pose, light, overlay, color)) {
            ci.cancel();
        }
        */
    }
}
