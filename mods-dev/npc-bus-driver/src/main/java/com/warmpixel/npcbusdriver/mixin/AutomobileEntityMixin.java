package com.warmpixel.npcbusdriver.mixin;

import com.warmpixel.npcbusdriver.BusDriverManager;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AutomobileEntity.class)
public abstract class AutomobileEntityMixin {
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
