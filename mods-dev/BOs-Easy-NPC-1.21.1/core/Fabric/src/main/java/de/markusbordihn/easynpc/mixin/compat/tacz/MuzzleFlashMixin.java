package de.markusbordihn.easynpc.mixin.compat.tacz;

import de.markusbordihn.easynpc.client.renderer.entity.EasyNPCLivingEntityRenderer;
import de.markusbordihn.easynpc.entity.easynpc.handlers.AttackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash", remap = false)
public abstract class MuzzleFlashMixin {

  @Inject(method = "getScale", at = @At("RETURN"), cancellable = true)
  private void adjustNPCMuzzleFlashScale(CallbackInfoReturnable<Float> cir) {
    if (EasyNPCLivingEntityRenderer.IS_RENDERING_NPC.get()) {
      float npcScale = AttackHandler.MuzzleFlashConfig.getScale();
      cir.setReturnValue(cir.getReturnValue() * npcScale);
    }
  }
}
