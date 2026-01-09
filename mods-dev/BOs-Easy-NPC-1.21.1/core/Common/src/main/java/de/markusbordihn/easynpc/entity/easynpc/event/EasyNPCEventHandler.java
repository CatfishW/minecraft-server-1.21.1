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

package de.markusbordihn.easynpc.entity.easynpc.event;

import de.markusbordihn.easynpc.data.action.ActionEventType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.ActionEventDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.ObjectiveDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.TradingDataCapable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.portal.DimensionTransition;
import de.markusbordihn.easynpc.handler.SpawningHandler;



public final class EasyNPCEventHandler {

  public static <E extends PathfinderMob> void handlePlayerJoinEvent(
      EasyNPC<E> easyNPC, ServerPlayer serverPlayer) {
    ObjectiveDataCapable<E> objectiveData = easyNPC.getEasyNPCObjectiveData();
    if (objectiveData != null) {
      objectiveData.onPlayerJoinUpdateObjective(serverPlayer);
    }
  }


  public static <E extends PathfinderMob> void handlePlayerLeaveEvent(
      EasyNPC<E> easyNPC, ServerPlayer serverPlayer) {
    ObjectiveDataCapable<E> objectiveData = easyNPC.getEasyNPCObjectiveData();
    if (objectiveData != null) {
      objectiveData.onPlayerLeaveUpdateObjective(serverPlayer);
    }
  }

  public static <E extends PathfinderMob> void handleLivingEntityJoinEvent(
      EasyNPC<E> easyNPC, LivingEntity livingEntity) {
    ObjectiveDataCapable<E> objectiveData = easyNPC.getEasyNPCObjectiveData();
    if (objectiveData != null) {
      objectiveData.onLivingEntityJoinUpdateObjective(livingEntity);
    }
  }

  public static <E extends PathfinderMob> void handleLivingEntityLeaveEvent(
      EasyNPC<E> easyNPC, LivingEntity livingEntity) {
    ObjectiveDataCapable<E> objectiveData = easyNPC.getEasyNPCObjectiveData();
    if (objectiveData != null) {
      objectiveData.onLivingEntityLeaveUpdateObjective(livingEntity);
    }
  }

  public static <E extends PathfinderMob> void handleEasyNPCJoinEvent(
      EasyNPC<E> easyNPC, EasyNPC<?> entity) {
    ObjectiveDataCapable<E> objectiveData = easyNPC.getEasyNPCObjectiveData();
    if (objectiveData != null && entity != null) {
      objectiveData.onEasyNPCJoinUpdateObjective(entity);
    }
  }

  public static <E extends PathfinderMob> void handleEasyNPCLeaveEvent(
      EasyNPC<E> easyNPC, EasyNPC<?> entity) {
    ObjectiveDataCapable<E> objectiveData = easyNPC.getEasyNPCObjectiveData();
    if (objectiveData != null && entity != null) {
      objectiveData.onEasyNPCLeaveUpdateObjective(entity);
    }
  }

  public static <E extends PathfinderMob> void handleDieEvent(
      EasyNPC<E> easyNPC, DamageSource damageSource) {
    // Notify spawning handler about death to maintain population
    SpawningHandler.onNPCDeath(easyNPC.getEntity());
    
    // Check for custom drop configuration via Tags
    for (String tag : easyNPC.getEntity().getTags()) {
      if (tag.startsWith("easynpc_drop|")) {
        try {
          String[] parts = tag.split("\\|");
          if (parts.length >= 5) {
            String itemId = parts[1];
            int count = Integer.parseInt(parts[2]);
            float chance = Float.parseFloat(parts[3]);
            boolean playerKillOnly = Boolean.parseBoolean(parts[4]);
            
            boolean shouldDrop = true;
            if (playerKillOnly && !(damageSource.getEntity() instanceof ServerPlayer)) {
              shouldDrop = false;
            }
            
            if (shouldDrop) {
              if (easyNPC.getEntity().getRandom().nextFloat() < chance) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.parse(itemId));
                    
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                   easyNPC.getEntity().spawnAtLocation(new net.minecraft.world.item.ItemStack(item, count > 0 ? count : 1));
                }
              }
            }
          }
        } catch (Exception e) {
           // Ignore parsing errors
        }
      }
    }

    TradingDataCapable<E> tradingData = easyNPC.getEasyNPCTradingData();
    if (tradingData != null) {
      tradingData.stopMerchantTrading();
    }

    ActionEventDataCapable<E> actionEventData = easyNPC.getEasyNPCActionEventData();
    if (actionEventData != null) {
      actionEventData.handleActionEvent(
          ActionEventType.ON_DEATH, getServerPlayerFromDamageSource(damageSource));
    }
  }

  public static <E extends PathfinderMob> void handleKillEvent(EasyNPC<E> easyNPC) {
    // Notify spawning handler about kill to maintain population
    SpawningHandler.onNPCDeath(easyNPC.getEntity());

    TradingDataCapable<E> tradingData = easyNPC.getEasyNPCTradingData();
    if (tradingData != null) {
      tradingData.stopMerchantTrading();
    }

    ActionEventDataCapable<E> actionEventData = easyNPC.getEasyNPCActionEventData();
    if (actionEventData != null) {
      actionEventData.handleActionEvent(ActionEventType.ON_KILL);
    }
  }

  public static <E extends PathfinderMob> void handleChangeDimensionEvent(
      EasyNPC<E> easyNPC, DimensionTransition dimensionTransition) {
    TradingDataCapable<E> tradingData = easyNPC.getEasyNPCTradingData();
    if (tradingData != null) {
      tradingData.stopMerchantTrading();
    }
  }

  public static <E extends PathfinderMob> void handleHurtEvent(
      EasyNPC<E> easyNPC, DamageSource damageSource, float damage) {
    ActionEventDataCapable<E> actionEventData = easyNPC.getEasyNPCActionEventData();
    if (actionEventData != null) {
      actionEventData.handleActionEvent(
          ActionEventType.ON_HURT, getServerPlayerFromDamageSource(damageSource));
    }
  }

  private static ServerPlayer getServerPlayerFromDamageSource(DamageSource damageSource) {
    if (damageSource.getEntity() instanceof ServerPlayer serverPlayer) {
      return serverPlayer;
    }
    if (damageSource.getDirectEntity() instanceof Projectile projectile
        && projectile.getOwner() instanceof ServerPlayer serverPlayerOfProjectile) {
      return serverPlayerOfProjectile;
    }
    return null;
  }
}
