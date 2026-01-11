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
import de.markusbordihn.easynpc.data.crime.LawSystemConfig;
import de.markusbordihn.easynpc.handler.LawSystemHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> server config update for the law system.
 */
public record LawConfigUpdateMessage(LawSystemConfig config, boolean save)
    implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "law_config_update");

  public static final CustomPacketPayload.Type<LawConfigUpdateMessage> TYPE =
      new CustomPacketPayload.Type<>(MESSAGE_ID);

  public static final CustomPacketPayload.Type<LawConfigUpdateMessage> PAYLOAD_TYPE = TYPE;

  public static final StreamCodec<RegistryFriendlyByteBuf, LawConfigUpdateMessage> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public LawConfigUpdateMessage decode(RegistryFriendlyByteBuf buf) {
          var configTag = buf.readNbt();
          LawSystemConfig config =
              configTag != null ? new LawSystemConfig(configTag) : new LawSystemConfig();
          boolean save = buf.readBoolean();
          return new LawConfigUpdateMessage(config, save);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, LawConfigUpdateMessage message) {
          LawSystemConfig config = message.config() != null ? message.config() : new LawSystemConfig();
          buf.writeNbt(config.createTag());
          buf.writeBoolean(message.save());
        }
      };

  public static LawConfigUpdateMessage create(FriendlyByteBuf buf) {
    var configTag = buf.readNbt();
    LawSystemConfig config =
        configTag != null ? new LawSystemConfig(configTag) : new LawSystemConfig();
    boolean save = buf.readBoolean();
    return new LawConfigUpdateMessage(config, save);
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public void write(FriendlyByteBuf buf) {
    LawSystemConfig config = this.config != null ? this.config : new LawSystemConfig();
    buf.writeNbt(config.createTag());
    buf.writeBoolean(this.save);
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  @Override
  public void handleServer(ServerPlayer serverPlayer) {
    if (serverPlayer == null || serverPlayer.getServer() == null) {
      return;
    }
    if (!serverPlayer.hasPermissions(2)) {
      log.warn("Player {} attempted law config update without permission",
          serverPlayer.getName().getString());
      return;
    }

    LawSystemHandler handler = LawSystemHandler.getInstance();
    if (this.config != null) {
      handler.setConfig(this.config);
    }
    handler.syncAllPlayers();
    if (this.save) {
      handler.saveAll();
    }
    handler.sendAdminData(serverPlayer, false);
  }

  @Override
  public void handleClient() {
    // Client -> server only.
  }
}
