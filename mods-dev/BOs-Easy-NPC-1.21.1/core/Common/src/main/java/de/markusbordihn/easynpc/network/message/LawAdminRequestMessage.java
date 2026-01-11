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
import de.markusbordihn.easynpc.handler.LawSystemHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> server request for law admin data.
 */
public record LawAdminRequestMessage(RequestType requestType) implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "law_admin_request");

  public static final CustomPacketPayload.Type<LawAdminRequestMessage> TYPE =
      new CustomPacketPayload.Type<>(MESSAGE_ID);

  public static final CustomPacketPayload.Type<LawAdminRequestMessage> PAYLOAD_TYPE = TYPE;

  public static final StreamCodec<RegistryFriendlyByteBuf, LawAdminRequestMessage> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public LawAdminRequestMessage decode(RegistryFriendlyByteBuf buf) {
          RequestType requestType = RequestType.values()[buf.readVarInt()];
          return new LawAdminRequestMessage(requestType);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, LawAdminRequestMessage message) {
          buf.writeVarInt(message.requestType().ordinal());
        }
      };

  public enum RequestType {
    OPEN,
    REFRESH
  }

  public static LawAdminRequestMessage create(FriendlyByteBuf buf) {
    RequestType requestType = RequestType.values()[buf.readVarInt()];
    return new LawAdminRequestMessage(requestType);
  }

  public static LawAdminRequestMessage open() {
    return new LawAdminRequestMessage(RequestType.OPEN);
  }

  public static LawAdminRequestMessage refresh() {
    return new LawAdminRequestMessage(RequestType.REFRESH);
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public void write(FriendlyByteBuf buf) {
    buf.writeVarInt(this.requestType.ordinal());
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
      log.warn("Player {} attempted law admin request without permission",
          serverPlayer.getName().getString());
      return;
    }
    boolean openScreen = this.requestType == RequestType.OPEN;
    LawSystemHandler.getInstance().sendAdminData(serverPlayer, openScreen);
  }

  @Override
  public void handleClient() {
    // Client -> server only.
  }
}
