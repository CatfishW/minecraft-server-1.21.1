package com.warmpixel.storyadventure.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to prevent crowbar despawning of story-spawned vehicles.
 * Vehicles tagged with "story_vehicle" cannot be destroyed with a crowbar.
 */
@Mixin(value = AutomobileEntity.class, remap = false)
public abstract class StoryVehicleProtectionMixin {
    
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true, require = 0)
    private void onInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Entity self = (Entity) (Object) this;
        
        // Check if this is a story-spawned vehicle
        if (self.getTags().contains("story_vehicle")) {
            ItemStack stack = player.getItemInHand(hand);
            
            // Check if player is holding a crowbar
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null && itemId.toString().equals("automobility:crowbar")) {
                // Prevent crowbar interaction - send message to player
                if (!player.level().isClientSide()) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§c无法移除任务车辆"),
                        true
                    );
                }
                cir.setReturnValue(InteractionResult.FAIL);
            }
        }
    }
}
