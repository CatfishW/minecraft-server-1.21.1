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

package de.markusbordihn.easynpc.entity;

import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.handler.GuardResponseHandler;
import de.markusbordihn.easynpc.handler.LawSystemHandler;
import de.markusbordihn.easynpc.menu.MenuManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class LivingEntityEvents {

  protected LivingEntityEvents() {}

  public static void handleLivingEntityJoinEvent(LivingEntity livingEntity) {
    if (livingEntity == null) {
      return;
    }

    if (livingEntity instanceof EasyNPC<?> easyNPC) {
      if (!livingEntity.level().isClientSide) {
        java.util.UUID ownerUUID = GuardResponseHandler.getGuardOwnerUUID(easyNPC.getEntity());
        if (ownerUUID != null) {
          var state = LawSystemHandler.getInstance().getPlayerState(ownerUUID);
          boolean ownerWanted = state != null && state.isWanted();
          var server = livingEntity.getServer();
          boolean ownerOnline = server != null && server.getPlayerList().getPlayer(ownerUUID) != null;
          if (!ownerOnline || !ownerWanted) {
            easyNPC.getEntity().discard();
            GuardResponseHandler.getInstance().onGuardRemoved(easyNPC.getEntityUUID());
            return;
          }
        }
      }
      LivingEntityManager.addEasyNPC(easyNPC);
      de.markusbordihn.easynpc.handler.CrimeHandler.getInstance().autoRegisterEasyNPC(easyNPC);
    } else if (livingEntity instanceof ServerPlayer serverPlayer) {
      LivingEntityManager.addServerPlayer(serverPlayer);
    } else {
      LivingEntityManager.addLivingEntity(livingEntity);
    }
  }

  public static void handleLivingEntityLeaveEvent(LivingEntity livingEntity) {
    if (livingEntity == null) {
      return;
    }

    if (livingEntity instanceof EasyNPC<?> easyNPC) {
      LivingEntityManager.removeEasyNPC(easyNPC);
      de.markusbordihn.easynpc.handler.CrimeHandler.getInstance().unregisterNPC(easyNPC.getEntityUUID());
      if (!livingEntity.level().isClientSide
          && GuardResponseHandler.getGuardOwnerUUID(easyNPC.getEntity()) != null) {
        GuardResponseHandler.getInstance().onGuardRemoved(easyNPC.getEntityUUID());
      }
    } else if (livingEntity instanceof ServerPlayer serverPlayer) {
      LivingEntityManager.removeServerPlayer(serverPlayer);
      MenuManager.cleanupPlayerMenus(serverPlayer);
    } else {
      LivingEntityManager.removeLivingEntity(livingEntity);
    }
  }
}
