package com.warmpixel.bleedgore.mixin;

import com.warmpixel.bleedgore.BleedGoreMod;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "hurt", at = @At("RETURN"))
    private void bleedGore$afterHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            BleedGoreMod.handleEntityDamaged((LivingEntity) (Object) this, source, amount);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void bleedGore$onDie(DamageSource source, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        BleedGoreMod.handleEntityDeath((LivingEntity) (Object) this, source);
    }
}
