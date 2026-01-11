/*
 * Copyright 2024 Markus Bordihn
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

package de.markusbordihn.easynpc.network.message;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.handler.CrimeHandler;
import de.markusbordihn.easynpc.handler.GuardResponseHandler;
import de.markusbordihn.easynpc.handler.LawSystemHandler;
import de.markusbordihn.easynpc.handler.MerchantSpawnHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Network message for admin actions from client to server.
 */
public record AdminActionMessage(ActionType action, String targetPlayer, int intValue) 
    implements NetworkMessageRecord {

  public static final ResourceLocation ID = 
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "admin_action");
  
  public static final CustomPacketPayload.Type<AdminActionMessage> TYPE = 
      new CustomPacketPayload.Type<>(ID);

  public static final CustomPacketPayload.Type<AdminActionMessage> PAYLOAD_TYPE = TYPE;

  public static final StreamCodec<RegistryFriendlyByteBuf, AdminActionMessage> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public AdminActionMessage decode(RegistryFriendlyByteBuf buf) {
          ActionType action = ActionType.values()[buf.readVarInt()];
          String targetPlayer = buf.readUtf();
          int intValue = buf.readVarInt();
          return new AdminActionMessage(action, targetPlayer, intValue);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AdminActionMessage message) {
          buf.writeVarInt(message.action().ordinal());
          buf.writeUtf(message.targetPlayer());
          buf.writeVarInt(message.intValue());
        }
      };

  public enum ActionType {
    TOGGLE_SYSTEM,
    SET_WANTED_LEVEL,
    SET_PEACE_VALUE,
    CLEAR_CRIMES,
    CLEAR_ALL_WANTED,
    DESPAWN_ALL_GUARDS,
    DESPAWN_ALL_MERCHANTS,
    SPAWN_PATROL,
    SPAWN_PURSUIT_SQUAD,
    SPAWN_MERCHANT_GROUP,
    RUN_TEST_SIMULATION,
    TOGGLE_IMMUNITY,
    RELOAD_CONFIG,
    SAVE_CONFIG
  }

  public static AdminActionMessage create(FriendlyByteBuf buf) {
    ActionType action = ActionType.values()[buf.readVarInt()];
    String targetPlayer = buf.readUtf();
    int intValue = buf.readVarInt();
    return new AdminActionMessage(action, targetPlayer, intValue);
  }

  public static AdminActionMessage toggleSystem() {
    return new AdminActionMessage(ActionType.TOGGLE_SYSTEM, "", 0);
  }

  public static AdminActionMessage setWantedLevel(String playerName, int level) {
    return new AdminActionMessage(ActionType.SET_WANTED_LEVEL, playerName, level);
  }

  public static AdminActionMessage setPeaceValue(String playerName, int value) {
    return new AdminActionMessage(ActionType.SET_PEACE_VALUE, playerName, value);
  }

  public static AdminActionMessage clearCrimes(String playerName) {
    return new AdminActionMessage(ActionType.CLEAR_CRIMES, playerName, 0);
  }

  public static AdminActionMessage clearAllWanted() {
    return new AdminActionMessage(ActionType.CLEAR_ALL_WANTED, "", 0);
  }

  public static AdminActionMessage despawnAllGuards() {
    return new AdminActionMessage(ActionType.DESPAWN_ALL_GUARDS, "", 0);
  }

  public static AdminActionMessage despawnAllMerchants() {
    return new AdminActionMessage(ActionType.DESPAWN_ALL_MERCHANTS, "", 0);
  }

  public static AdminActionMessage spawnPatrol(int tierLevel) {
    return new AdminActionMessage(ActionType.SPAWN_PATROL, "", tierLevel);
  }

  public static AdminActionMessage spawnPursuitSquad(String targetPlayer, int tierLevel) {
    return new AdminActionMessage(ActionType.SPAWN_PURSUIT_SQUAD, targetPlayer, tierLevel);
  }

  public static AdminActionMessage toggleImmunity(String playerName) {
    return new AdminActionMessage(ActionType.TOGGLE_IMMUNITY, playerName, 0);
  }

  public static AdminActionMessage spawnMerchantGroup(int groupCount) {
    return new AdminActionMessage(ActionType.SPAWN_MERCHANT_GROUP, "", groupCount);
  }

  public static AdminActionMessage runTestSimulation(int seconds) {
    return new AdminActionMessage(ActionType.RUN_TEST_SIMULATION, "", seconds);
  }

  public static AdminActionMessage reloadConfig() {
    return new AdminActionMessage(ActionType.RELOAD_CONFIG, "", 0);
  }

  public static AdminActionMessage saveConfig() {
    return new AdminActionMessage(ActionType.SAVE_CONFIG, "", 0);
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  @Override
  public ResourceLocation id() {
    return ID;
  }

  @Override
  public void write(FriendlyByteBuf buf) {
    buf.writeVarInt(this.action.ordinal());
    buf.writeUtf(this.targetPlayer);
    buf.writeVarInt(this.intValue);
  }

  @Override
  public void handleServer(ServerPlayer serverPlayer) {
    if (serverPlayer == null || serverPlayer.getServer() == null) {
      return;
    }
    if (!serverPlayer.hasPermissions(2)) {
      log.warn("Player {} attempted admin action {} without permission", 
          serverPlayer.getName().getString(), action);
      return;
    }

    LawSystemHandler lawHandler = LawSystemHandler.getInstance();
    switch (action) {
      case TOGGLE_SYSTEM -> lawHandler.setSystemEnabled(!lawHandler.isSystemEnabled());
      case SET_WANTED_LEVEL -> {
        ServerPlayer target = serverPlayer.getServer().getPlayerList().getPlayerByName(targetPlayer);
        if (target != null) {
          lawHandler.setPlayerWantedLevel(target.getUUID(), intValue);
        }
      }
      case SET_PEACE_VALUE -> {
        ServerPlayer target = serverPlayer.getServer().getPlayerList().getPlayerByName(targetPlayer);
        if (target != null) {
          lawHandler.setPlayerPeaceValue(target.getUUID(), intValue);
        }
      }
      case CLEAR_CRIMES -> {
        ServerPlayer target = serverPlayer.getServer().getPlayerList().getPlayerByName(targetPlayer);
        if (target != null) {
          lawHandler.clearPlayerCrimes(target.getUUID());
        }
      }
      case CLEAR_ALL_WANTED -> lawHandler.clearAllWanted();
      case DESPAWN_ALL_GUARDS -> GuardResponseHandler.getInstance().despawnAllGuards(serverPlayer.serverLevel());
      case DESPAWN_ALL_MERCHANTS -> CrimeHandler.getInstance().despawnAllMerchants(serverPlayer.getServer());
      case SPAWN_PATROL -> GuardResponseHandler.getInstance().spawnPatrolAt(
          serverPlayer.serverLevel(), serverPlayer.blockPosition(), Math.max(1, intValue));
      case SPAWN_PURSUIT_SQUAD -> {
        ServerPlayer target = serverPlayer.getServer().getPlayerList().getPlayerByName(targetPlayer);
        if (target != null) {
          GuardResponseHandler.getInstance().spawnPursuitSquad(target, Math.max(1, intValue));
        }
      }
      case SPAWN_MERCHANT_GROUP -> MerchantSpawnHandler.getInstance().spawnMerchantGroup(
          serverPlayer.serverLevel(), serverPlayer.blockPosition(), Math.max(1, intValue));
      case RUN_TEST_SIMULATION -> lawHandler.runTestSimulation(serverPlayer.getServer(), intValue);
      case TOGGLE_IMMUNITY -> {
        ServerPlayer target = serverPlayer.getServer().getPlayerList().getPlayerByName(targetPlayer);
        if (target != null) {
          lawHandler.toggleCrimeImmunity(target.getUUID());
        }
      }
      case RELOAD_CONFIG -> lawHandler.loadConfig();
      case SAVE_CONFIG -> lawHandler.saveAll();
    }

    lawHandler.sendAdminData(serverPlayer, false);
  }

  @Override
  public void handleClient() {
    // Client -> server only.
  }
}
