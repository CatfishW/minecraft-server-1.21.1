package com.warmpixel.optimizer.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public abstract class BulletMixin extends Projectile {

    protected BulletMixin(net.minecraft.world.entity.EntityType<? extends Projectile> type, net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/particle/AmmoParticleSpawner;addParticle(Lcom/tacz/guns/entity/EntityKineticBullet;)V"), cancellable = true)
    private void onAddParticle(CallbackInfo ci) {
        // Optimization: Reduce particle density for rapid fire or distant bullets
        if (this.level().isClientSide) {
            // Only spawn particles every 3 ticks for distant bullets
            Entity cameraEntity = net.minecraft.client.Minecraft.getInstance().cameraEntity;
            if (cameraEntity != null) {
                double distSq = this.distanceToSqr(cameraEntity);
                if (distSq > 32 * 32) {
                    if (this.tickCount % 3 != 0) {
                        ci.cancel();
                    }
                }
            }
        }
    }
}
