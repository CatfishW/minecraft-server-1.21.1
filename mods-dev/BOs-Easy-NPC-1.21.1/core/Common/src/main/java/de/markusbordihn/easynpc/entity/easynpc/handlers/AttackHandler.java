/*
 * Copyright 2023 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.easynpc.entity.easynpc.handlers;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.data.attribute.CombatAttributes;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPCBase;
import de.markusbordihn.easynpc.item.ModItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.component.ChargedProjectiles;

public class AttackHandler {

  private AttackHandler() {}

  public static void addChargedProjectile(
      ItemStack weaponItemStack, ItemStack projectileItemStack) {
    weaponItemStack.set(
        DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(projectileItemStack));
  }

  public static boolean isMeleeWeapon(ItemStack itemStack) {
    return itemStack.getItem() instanceof TieredItem;
  }

  public static boolean isBowWeapon(ItemStack itemStack) {
    return itemStack.getItem() instanceof BowItem || itemStack.is(ModItemTags.RANGED_WEAPON_BOW);
  }

  public static boolean isCrossbowWeapon(ItemStack itemStack) {
    return itemStack.getItem() instanceof CrossbowItem
        || itemStack.is(ModItemTags.RANGED_WEAPON_CROSSBOW);
  }

  public static boolean isGunWeapon(ItemStack itemStack) {
    if (itemStack.isEmpty()) {
      return false;
    }
    if (itemStack.is(ModItemTags.RANGED_WEAPON_GUN)) {
      return true;
    }
    try {
      Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
      return iGunClass.isInstance(itemStack.getItem());
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  public static boolean canFireProjectileWeapon(ProjectileWeaponItem projectileWeaponItem) {
    return projectileWeaponItem instanceof CrossbowItem || projectileWeaponItem instanceof BowItem;
  }

  public static boolean isHoldingBowWeapon(LivingEntity livingEntity) {
    return livingEntity != null && isBowWeapon(livingEntity.getMainHandItem());
  }

  public static boolean isHoldingCrossbowWeapon(LivingEntity livingEntity) {
    return livingEntity != null && isCrossbowWeapon(livingEntity.getMainHandItem());
  }

  public static boolean isHoldingGunWeapon(LivingEntity livingEntity) {
    return livingEntity != null && isGunWeapon(livingEntity.getMainHandItem());
  }

  public static boolean isHoldingMeleeWeapon(LivingEntity livingEntity) {
    return livingEntity != null && isMeleeWeapon(livingEntity.getMainHandItem());
  }

  public static boolean isHoldingProjectileWeapon(LivingEntity livingEntity) {
    return livingEntity != null
        && livingEntity.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
  }

  public static boolean isHoldingWeapon(LivingEntity livingEntity) {
    return isHoldingMeleeWeapon(livingEntity)
        || isHoldingProjectileWeapon(livingEntity)
        || isHoldingGunWeapon(livingEntity);
  }

  public static void performDefaultRangedAttack(
      LivingEntity livingEntity, LivingEntity targedtedLivingEntity, float damage) {
    if (isHoldingBowWeapon(livingEntity)) {
      performBowAttack(livingEntity, targedtedLivingEntity, damage);
    } else if (livingEntity instanceof CrossbowAttackMob crossbowAttackMob
        && isHoldingCrossbowWeapon(livingEntity)) {
      addChargedProjectile(livingEntity.getMainHandItem(), new ItemStack(Items.ARROW, 1));
      crossbowAttackMob.performCrossbowAttack(livingEntity, 1.6F);
    } else if (isHoldingGunWeapon(livingEntity)) {
      performGunAttack(livingEntity, targedtedLivingEntity, damage);
    }
  }

  public static InteractionHand getBowHoldingHand(LivingEntity livingEntity) {
    ItemStack itemStack = livingEntity.getMainHandItem();
    return isBowWeapon(itemStack) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
  }

  public static InteractionHand getCrossbowHoldingHand(LivingEntity livingEntity) {
    ItemStack itemStack = livingEntity.getMainHandItem();
    return isCrossbowWeapon(itemStack) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
  }

  public static InteractionHand getGunHoldingHand(LivingEntity livingEntity) {
    ItemStack itemStack = livingEntity.getMainHandItem();
    return isGunWeapon(itemStack) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
  }

  public static void performGunAttack(
      LivingEntity livingEntity, LivingEntity livingEntityTarget, float damage) {
    ItemStack itemStackWeapon = livingEntity.getItemInHand(getGunHoldingHand(livingEntity));
    AbstractArrow abstractArrow = getBullet(livingEntity, itemStackWeapon, damage);
    if (isGunWeapon(livingEntity.getMainHandItem())) {
      double targetX = livingEntityTarget.getX() - livingEntity.getX();
      double targetY = livingEntityTarget.getY() - abstractArrow.getY();
      double targetZ = livingEntityTarget.getZ() - livingEntity.getZ();
      double targetRadius = Math.sqrt(targetX * targetX + targetZ * targetZ);
      abstractArrow.shoot(
          targetX,
          targetY + targetRadius * 0.2F,
          targetZ,
          1.6F,
          14.0F - livingEntity.level().getDifficulty().getId() * 4);
      livingEntity.playSound(
          SoundEvents.FIRECHARGE_USE,
          1.0F,
          1.0F / (livingEntity.getRandom().nextFloat() * 0.4F + 0.8F));
      livingEntity.level().addFreshEntity(abstractArrow);
    }
  }

  public static void performCustomAttack(
      LivingEntity livingEntity, LivingEntity livingEntityTarget, float damage) {
    ItemStack itemStack = livingEntity.getMainHandItem();
    if (itemStack.isEmpty()) {
      return;
    }

    // Handle TACZ Guns
    try {
      Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
      if (iGunClass.isInstance(itemStack.getItem())) {
        // de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.info("AttackHandler: Item {} is a TACZ Gun.", itemStack.getItem());
        Class<?> iGunOperatorClass = Class.forName("com.tacz.guns.api.entity.IGunOperator");
        if (iGunOperatorClass.isInstance(livingEntity)) {
          // de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.info("AttackHandler: Entity {} is an IGunOperator.", livingEntity);
          
          // Ensure data initialization
          java.lang.reflect.Method getDataHolderMethod = iGunOperatorClass.getMethod("getDataHolder");
          Object dataHolder = getDataHolderMethod.invoke(livingEntity);
          java.lang.reflect.Field currentGunItemField = dataHolder.getClass().getField("currentGunItem");
          if (currentGunItemField.get(dataHolder) == null) {
              // de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.info("AttackHandler: Initializing gun data for entity.");
              java.lang.reflect.Method initialDataMethod = iGunOperatorClass.getMethod("initialData");
              initialDataMethod.invoke(livingEntity);
          }
          
          // Look at target
          if (livingEntity instanceof net.minecraft.world.entity.Mob mob) {
            mob.getLookControl().setLookAt(livingEntityTarget, 30.0F, 30.0F);
          }

          // Auto-Reload / Infinite Ammo Logic
          net.minecraft.world.item.component.CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
          if (customData != null) {
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            if (tag.contains("GunCurrentAmmoCount")) {
              int currentAmmo = tag.getInt("GunCurrentAmmoCount");
              if (currentAmmo <= 0) {
                 // de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.info("AttackHandler: Ammo empty, reloading/refilling.");
                 // Refill to 30 or similar. Ideally should be max ammo but hardcoded for safety is better than 0.
                 tag.putInt("GunCurrentAmmoCount", 30);
                 tag.putBoolean("HasBulletInBarrel", true);
                 itemStack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
              }
            } else {
               // Initial fill if missing
               // de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.info("AttackHandler: Ammo tag missing, initializing.");
               tag.putInt("GunCurrentAmmoCount", 30);
               tag.putBoolean("HasBulletInBarrel", true);
               itemStack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            }
          }

          // Call shoot(Supplier<Float> pitch, Supplier<Float> yaw)
          // de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.debug("AttackHandler: Calling shoot method.");
          java.lang.reflect.Method shootMethod =
              iGunOperatorClass.getMethod(
                  "shoot", java.util.function.Supplier.class, java.util.function.Supplier.class);
          java.util.function.Supplier<Float> pitchSupplier = livingEntity::getXRot;
          java.util.function.Supplier<Float> yawSupplier = livingEntity::getYRot;
          Object result = shootMethod.invoke(livingEntity, pitchSupplier, yawSupplier);
          // de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.debug("AttackHandler: Shoot method result: {}", result);
          return;
        } else {
             // de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.warn("AttackHandler: Entity {} is NOT an IGunOperator.", livingEntity);
        }
      }
    } catch (Exception e) {
      de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.error("AttackHandler: Error handling TACZ gun attack: {}", e.getMessage(), e);
    }
    
    // Default behavior for other items: use them or perform standard ranged attack
    if (itemStack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) {
      performDefaultRangedAttack(livingEntity, livingEntityTarget, damage);
    } else {
      // Just try right-click?
      livingEntity.swing(InteractionHand.MAIN_HAND);
    }
  }

  public static void performBowAttack(
      LivingEntity livingEntity, LivingEntity livingEntityTarget, float damage) {
    ItemStack itemStackWeapon = livingEntity.getItemInHand(getBowHoldingHand(livingEntity));
    ItemStack itemStackProjectile = livingEntity.getProjectile(itemStackWeapon);
    AbstractArrow abstractArrow =
        getArrow(
            livingEntity,
            itemStackWeapon,
            itemStackProjectile.isEmpty() ? new ItemStack(Items.ARROW) : itemStackProjectile,
            damage);
    if (isBowWeapon(livingEntity.getMainHandItem())) {
      double targetX = livingEntityTarget.getX() - livingEntity.getX();
      double targetY = livingEntityTarget.getY(0.3333333333333333D) - abstractArrow.getY();
      double targetZ = livingEntityTarget.getZ() - livingEntity.getZ();
      double targetRadius = Math.sqrt(targetX * targetX + targetZ * targetZ);
      abstractArrow.shoot(
          targetX,
          targetY + targetRadius * 0.2F,
          targetZ,
          1.6F,
          14.0F - livingEntity.level().getDifficulty().getId() * 4);
      livingEntity.playSound(
          SoundEvents.SKELETON_SHOOT,
          1.0F,
          1.0F / (livingEntity.getRandom().nextFloat() * 0.4F + 0.8F));
      livingEntity.level().addFreshEntity(abstractArrow);
    }
  }

  public static AbstractArrow getArrow(
      LivingEntity livingEntity,
      ItemStack itemStackWeapon,
      ItemStack itemStackProjectile,
      float damage) {
    return ProjectileUtil.getMobArrow(
        livingEntity,
        itemStackProjectile.isEmpty() ? new ItemStack(Items.ARROW) : itemStackProjectile,
        damage,
        itemStackWeapon);
  }

  public static AbstractArrow getBullet(
      LivingEntity livingEntity, ItemStack itemStackWeapon, float damage) {
    Item item =
        BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "bullet"));
    return ProjectileUtil.getMobArrow(
        livingEntity,
        item != null && item != Items.AIR ? new ItemStack(item) : new ItemStack(Items.ARROW),
        damage,
        itemStackWeapon);
  }

  public static boolean handleIsInvulnerableTo(
      EasyNPCBase<?> easyNPC, DamageSource damageSource, boolean defaultValue) {
    // Allow certain damage types to bypass invulnerability like void or /kill command.
    if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
      return defaultValue;
    }

    // If the NPC is invulnerable, return true.
    if (easyNPC.getEntityAttributes().getCombatAttributes().isInvulnerable()) {
      return true;
    }

    // Check if the damage source is from a player or monster and if the NPC is attackable by them.
    CombatAttributes combatAttributes = easyNPC.getEntityAttributes().getCombatAttributes();
    if (damageSource.getEntity() instanceof Player) {
      return !combatAttributes.isAttackableByPlayers();
    } else if (damageSource.getEntity() instanceof Monster) {
      return !combatAttributes.isAttackableByMonsters();
    }

    return defaultValue;
  }
}
