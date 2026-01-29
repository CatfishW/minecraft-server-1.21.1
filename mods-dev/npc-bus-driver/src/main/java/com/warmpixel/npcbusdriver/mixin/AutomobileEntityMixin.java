package com.warmpixel.npcbusdriver.mixin;

import com.warmpixel.npcbusdriver.BusDriverManager;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AutomobileEntity.class)
public abstract class AutomobileEntityMixin {
    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void onCanAddPassenger(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        // Block all monsters (zombies, skeletons, etc.)
        if (passenger instanceof Monster) {
            cir.setReturnValue(false);
            return;
        }

        // Block NPCs by tags
        if (passenger.getTags().contains("horde_member") ||
            passenger.getTags().contains("story_entity") ||
            passenger.getTags().contains("story_horde") ||
            passenger.getTags().contains("story_bandits") ||
            passenger.getTags().contains("boss_minion") ||
            passenger.getTags().contains("story_npc") ||
            passenger.getTags().contains("instance_npc")) {
            cir.setReturnValue(false);
            return;
        }

        // Block Easy NPC entities by class name check (reflection to avoid hard dependency)
        String className = passenger.getClass().getName();
        if (className.contains("easynpc") || className.contains("EasyNPC") || className.contains("NPC")) {
            cir.setReturnValue(false);
        }
    }

    public LivingEntity getControllingPassenger() {
        Entity vehicle = (Entity) (Object) this;
        if (BusDriverManager.isManagedVehicle(vehicle.getUUID())) {
             Entity npc = BusDriverManager.getManagedDriver(vehicle.getUUID());
             if (npc instanceof LivingEntity le) {
                 return le;
             }
        }
        Entity firstPassenger = vehicle.getFirstPassenger();
        return firstPassenger instanceof LivingEntity le ? le : null;
    }
}
