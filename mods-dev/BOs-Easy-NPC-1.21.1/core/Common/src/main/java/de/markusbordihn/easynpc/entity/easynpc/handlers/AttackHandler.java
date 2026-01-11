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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.Vec3;

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
    if (livingEntity == null) {
      return false;
    }
    return isBowWeapon(livingEntity.getMainHandItem())
        || isBowWeapon(livingEntity.getOffhandItem());
  }

  public static boolean isHoldingCrossbowWeapon(LivingEntity livingEntity) {
    if (livingEntity == null) {
      return false;
    }
    return isCrossbowWeapon(livingEntity.getMainHandItem())
        || isCrossbowWeapon(livingEntity.getOffhandItem());
  }

  public static boolean isHoldingGunWeapon(LivingEntity livingEntity) {
    if (livingEntity == null) {
      return false;
    }
    return isGunWeapon(livingEntity.getMainHandItem())
        || isGunWeapon(livingEntity.getOffhandItem());
  }

  public static boolean isHoldingMeleeWeapon(LivingEntity livingEntity) {
    if (livingEntity == null) {
      return false;
    }
    return isMeleeWeapon(livingEntity.getMainHandItem())
        || isMeleeWeapon(livingEntity.getOffhandItem());
  }

  public static boolean isHoldingProjectileWeapon(LivingEntity livingEntity) {
    if (livingEntity == null) {
      return false;
    }
    return livingEntity.getMainHandItem().getItem() instanceof ProjectileWeaponItem
        || livingEntity.getOffhandItem().getItem() instanceof ProjectileWeaponItem;
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

  private static InteractionHand getHeldWeaponHand(
      LivingEntity livingEntity, ItemStack itemStack) {
    if (livingEntity == null || itemStack == null || itemStack.isEmpty()) {
      return InteractionHand.MAIN_HAND;
    }
    return itemStack == livingEntity.getOffhandItem()
        ? InteractionHand.OFF_HAND
        : InteractionHand.MAIN_HAND;
  }

  public static void performGunAttack(
      LivingEntity livingEntity, LivingEntity livingEntityTarget, float damage) {
    ItemStack itemStackWeapon = livingEntity.getItemInHand(getGunHoldingHand(livingEntity));
    if (!isGunWeapon(itemStackWeapon)) {
      return;
    }
    AbstractArrow abstractArrow = getBullet(livingEntity, itemStackWeapon, damage);
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

  public static void performCustomAttack(
      LivingEntity livingEntity, LivingEntity livingEntityTarget, float damage) {
    ItemStack itemStack = getHeldWeaponStack(livingEntity);
    if (itemStack.isEmpty()) {
      return;
    }
    InteractionHand weaponHand = getHeldWeaponHand(livingEntity, itemStack);

    // Handle TACZ Guns
    try {
      Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
      if (iGunClass.isInstance(itemStack.getItem())) {
        // de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.info("AttackHandler: Item {} is a TACZ Gun.", itemStack.getItem());
        Class<?> iGunOperatorClass = Class.forName("com.tacz.guns.api.entity.IGunOperator");
        Object gunOperator = null;
        if (iGunOperatorClass.isInstance(livingEntity)) {
          gunOperator = livingEntity;
        } else {
          java.lang.reflect.Method fromLivingEntityMethod =
              iGunOperatorClass.getMethod("fromLivingEntity", LivingEntity.class);
          gunOperator = fromLivingEntityMethod.invoke(null, livingEntity);
        }
        if (gunOperator != null) {
          // Ensure data initialization
          java.lang.reflect.Method getDataHolderMethod = iGunOperatorClass.getMethod("getDataHolder");
          Object dataHolder = getDataHolderMethod.invoke(gunOperator);
          java.lang.reflect.Field currentGunItemField =
              dataHolder.getClass().getField("currentGunItem");
          currentGunItemField.setAccessible(true);
          Object currentGunItem = currentGunItemField.get(dataHolder);
          if (currentGunItem == null) {
            java.lang.reflect.Method initialDataMethod = iGunOperatorClass.getMethod("initialData");
            initialDataMethod.invoke(gunOperator);
            currentGunItem = currentGunItemField.get(dataHolder);
          }
          java.util.function.Supplier<ItemStack> gunSupplier = () -> itemStack;
          if (!(currentGunItem instanceof java.util.function.Supplier<?> supplier)) {
            currentGunItemField.set(dataHolder, gunSupplier);
            currentGunItem = gunSupplier;
          } else {
            Object suppliedItem = supplier.get();
            if (!(suppliedItem instanceof ItemStack) || suppliedItem != itemStack) {
              currentGunItemField.set(dataHolder, gunSupplier);
              currentGunItem = gunSupplier;
            }
          }

          // Look at target
          if (livingEntity instanceof net.minecraft.world.entity.Mob mob) {
            mob.getLookControl().setLookAt(livingEntityTarget, 30.0F, 30.0F);
          }

          java.lang.reflect.Method drawMethod =
              iGunOperatorClass.getMethod("draw", java.util.function.Supplier.class);
          if (currentGunItem == null) {
            de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.debug("AttackHandler: Drawing gun...");
            drawMethod.invoke(gunOperator, gunSupplier);
          }

          // Auto-Reload / Infinite Ammo Logic
          // Use reflection to call IGun methods directly instead of partial NBT parsing
          try {
            java.lang.reflect.Method getCurrentAmmoCountMethod = iGunClass.getMethod("getCurrentAmmoCount", ItemStack.class);
            java.lang.reflect.Method setCurrentAmmoCountMethod = iGunClass.getMethod("setCurrentAmmoCount", ItemStack.class, int.class);
            java.lang.reflect.Method hasBulletInBarrelMethod = iGunClass.getMethod("hasBulletInBarrel", ItemStack.class);
            java.lang.reflect.Method setBulletInBarrelMethod = iGunClass.getMethod("setBulletInBarrel", ItemStack.class, boolean.class);

            int currentAmmo = (int) getCurrentAmmoCountMethod.invoke(itemStack.getItem(), itemStack);
            boolean hasBullet = (boolean) hasBulletInBarrelMethod.invoke(itemStack.getItem(), itemStack);

            if (currentAmmo <= 0 || !hasBullet) {
               // Refill ammo to ensure NPC can keep firing
               de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.debug("AttackHandler: Refilling ammo for TACZ gun.");
               setCurrentAmmoCountMethod.invoke(itemStack.getItem(), itemStack, 30);
               setBulletInBarrelMethod.invoke(itemStack.getItem(), itemStack, true);
            }
          } catch (Exception e) {
             de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.error("AttackHandler: Error handling TACZ ammo: {}", e.getMessage());
          }

          // Call shoot with timestamp if available
          java.util.function.Supplier<Float> pitchSupplier = livingEntity::getXRot;
          java.util.function.Supplier<Float> yawSupplier = livingEntity::getYRot;
          Object result;
          try {
            java.lang.reflect.Method shootMethod =
                iGunOperatorClass.getMethod(
                    "shoot",
                    java.util.function.Supplier.class,
                    java.util.function.Supplier.class,
                    long.class);
            
            // Calculate correct timestamp relative to baseTimestamp to satisfy LivingEntityShoot network check
            java.lang.reflect.Field baseTimestampField = dataHolder.getClass().getField("baseTimestamp");
            long baseTimestamp = baseTimestampField.getLong(dataHolder);
            long timestamp = System.currentTimeMillis() - baseTimestamp;

            de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.debug("AttackHandler: Shooting with timestamp: {} (base: {})", timestamp, baseTimestamp);
            
            result = shootMethod.invoke(gunOperator, pitchSupplier, yawSupplier, timestamp);
          } catch (NoSuchMethodException noMethod) {
            java.lang.reflect.Method shootMethod =
                iGunOperatorClass.getMethod(
                    "shoot", java.util.function.Supplier.class, java.util.function.Supplier.class);
            result = shootMethod.invoke(gunOperator, pitchSupplier, yawSupplier);
          } catch (Exception e) {
             de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.error("AttackHandler: Error calling shoot: {}", e.getMessage());
             result = null;
          }
          de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.debug("AttackHandler: Shoot result: {}", result);

          if (result != null && result.toString().contains("NOT_DRAW")) {
            de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.debug("AttackHandler: Gun not drawn, drawing now...");
            drawMethod.invoke(gunOperator, gunSupplier);
            return;
          }
          if (result != null && result.toString().contains("SUCCESS")) {
            ensureTaczMuzzleFlash(livingEntity, itemStack);
          }
          livingEntity.swing(weaponHand);
          return;
        }
        performGunAttack(livingEntity, livingEntityTarget, damage);
        return;
      }
    } catch (Exception e) {
      de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.error("AttackHandler: Error handling TACZ gun attack: {}", e.getMessage(), e);
    }
    
    // Default behavior for other items: use them or perform standard ranged attack
    if (itemStack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) {
      performDefaultRangedAttack(livingEntity, livingEntityTarget, damage);
    } else {
      // Just try right-click?
      livingEntity.swing(weaponHand);
    }
  }

  private static ItemStack getHeldWeaponStack(LivingEntity livingEntity) {
    if (livingEntity == null) {
      return ItemStack.EMPTY;
    }
    ItemStack main = livingEntity.getMainHandItem();
    if (!main.isEmpty()
        && (isMeleeWeapon(main) || isGunWeapon(main) || isBowWeapon(main) || isCrossbowWeapon(main)
            || main.getItem() instanceof ProjectileWeaponItem)) {
      return main;
    }
    ItemStack offhand = livingEntity.getOffhandItem();
    if (!offhand.isEmpty()
        && (isMeleeWeapon(offhand)
            || isGunWeapon(offhand)
            || isBowWeapon(offhand)
            || isCrossbowWeapon(offhand)
            || offhand.getItem() instanceof ProjectileWeaponItem)) {
      return offhand;
    }
    return main;
  }

  private static void ensureTaczMuzzleFlash(LivingEntity livingEntity, ItemStack itemStack) {
    if (livingEntity == null || itemStack == null || livingEntity.level().isClientSide) {
      return;
    }
    try {
      Class<?> networkHandlerClass = Class.forName("com.tacz.guns.network.NetworkHandler");
      java.lang.reflect.Method sendMethod = null;
      for (java.lang.reflect.Method method : networkHandlerClass.getMethods()) {
        if (method.getName().equals("sendToTrackingEntityAndSelf") && method.getParameterCount() == 2) {
          sendMethod = method;
          break;
        }
      }
      if (sendMethod == null) {
        for (java.lang.reflect.Method method : networkHandlerClass.getMethods()) {
          if (method.getName().equals("sendToTrackingEntity") && method.getParameterCount() == 2) {
            sendMethod = method;
            break;
          }
        }
      }
      if (sendMethod != null) {
        sendTaczMessage(sendMethod, livingEntity, "com.tacz.guns.network.message.event.ServerMessageGunShoot", itemStack);
        sendTaczMessage(sendMethod, livingEntity, "com.tacz.guns.network.message.event.ServerMessageGunFire", itemStack);
      }
    } catch (Exception e) {
      de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.debug("AttackHandler: Failed to force muzzle flash: {}", e.getMessage());
    }

    if (livingEntity.level() instanceof ServerLevel serverLevel) {
      Vec3 eyePos = livingEntity.getEyePosition();
      Vec3 look = livingEntity.getViewVector(1.0F);
      Vec3 muzzlePos = eyePos.add(look.scale(0.35));
      serverLevel.sendParticles(
          ParticleTypes.FLASH,
          muzzlePos.x,
          muzzlePos.y,
          muzzlePos.z,
          1,
          0.0,
          0.0,
          0.0,
          0.0);
      serverLevel.sendParticles(
          ParticleTypes.SMOKE,
          muzzlePos.x,
          muzzlePos.y,
          muzzlePos.z,
          1,
          0.01,
          0.01,
          0.01,
          0.01);
      serverLevel.sendParticles(
          ParticleTypes.CRIT,
          muzzlePos.x,
          muzzlePos.y,
          muzzlePos.z,
          1,
          0.01,
          0.01,
          0.01,
          0.01);
    }
  }

  private static void sendTaczMessage(
      java.lang.reflect.Method sendMethod,
      LivingEntity livingEntity,
      String messageClassName,
      ItemStack itemStack) {
    try {
      Class<?> messageClass = Class.forName(messageClassName);
      Object message = null;
      try {
        java.lang.reflect.Constructor<?> constructor =
            messageClass.getConstructor(int.class, ItemStack.class);
        message = constructor.newInstance(livingEntity.getId(), itemStack);
      } catch (NoSuchMethodException ignored) {
        for (java.lang.reflect.Constructor<?> constructor : messageClass.getConstructors()) {
          Class<?>[] params = constructor.getParameterTypes();
          if (params.length == 3 && params[0] == int.class && params[1] == ItemStack.class
              && params[2] == boolean.class) {
            message = constructor.newInstance(livingEntity.getId(), itemStack, true);
            break;
          }
        }
      }
      if (message != null) {
        sendMethod.invoke(null, livingEntity, message);
      }
    } catch (Exception e) {
      de.markusbordihn.easynpc.entity.easynpc.EasyNPC.log.debug(
          "AttackHandler: Failed to send TACZ message {}: {}",
          messageClassName,
          e.getMessage());
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
    if (!isBowWeapon(itemStackWeapon)) {
      return;
    }
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
