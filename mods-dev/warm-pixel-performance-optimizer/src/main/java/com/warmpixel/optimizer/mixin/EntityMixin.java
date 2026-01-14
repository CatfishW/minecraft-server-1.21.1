package com.warmpixel.optimizer.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class EntityMixin {

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void onAiStep(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide) {
            return;
        }

        // Optimization: Throttled AI for distant entities
        if (entity instanceof Mob mob) {
            long gameTime = entity.level().getGameTime();
            
            // Check distance to nearest player
            Player nearestPlayer = entity.level().getNearestPlayer(entity, 128.0);
            if (nearestPlayer != null) {
                double distanceSq = entity.distanceToSqr(nearestPlayer);
                
                int tickRate = 1;
                if (distanceSq > 64 * 64) {
                    tickRate = 10; // AI every 10 ticks if > 64 blocks away
                } else if (distanceSq > 32 * 32) {
                    tickRate = 4; // AI every 4 ticks if > 32 blocks away
                }
                
                if (tickRate > 1 && (gameTime + entity.getId()) % tickRate != 0) {
                    ci.cancel();
                }
            } else {
                // No player nearby, AI very rarely
                if ((gameTime + entity.getId()) % 40 != 0) {
                    ci.cancel();
                }
            }
        }
    }
}
