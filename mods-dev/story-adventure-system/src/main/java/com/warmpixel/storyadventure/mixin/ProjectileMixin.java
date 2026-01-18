package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.core.event.StoryEventListener;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {
    
    @Inject(method = "onHitBlock", at = @At("HEAD"))
    protected void onHitBlock(BlockHitResult blockHitResult, CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        // Forward to StoryEventListener
        StoryEventListener.onProjectileHit(projectile, blockHitResult);
    }
}
