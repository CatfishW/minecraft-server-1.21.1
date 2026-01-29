package com.warmpixel.storyadventure.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Mixin to override the outline color (team color) for story entities.
 * Enemies get a red outline, while other story entities get the default or a specific color.
 */
@Mixin(Entity.class)
public abstract class EntityOutlineMixin {

    @Shadow public abstract Set<String> getTags();

    /**
     * Override the team color value used for the glowing outline.
     * RED (0xFFFF5555 or 0xFF0000) for enemies.
     */
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void storyadventure$overrideOutlineColor(CallbackInfoReturnable<Integer> cir) {
        Set<String> tags = this.getTags();
        if (tags.contains("story_enemy")) {
            // Bright Red
            cir.setReturnValue(0xFF5555);
        } else if (tags.contains("story_entity")) {
            // If it's a story entity but not an enemy, maybe highlight it in a different color?
            // For now, let's just use a soft blue/aqua or default.
            // cir.setReturnValue(0x55FFFF); 
        }
    }
}
