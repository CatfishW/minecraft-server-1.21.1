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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Network message to sync player law state from server to client.
 * Used for the player overlay display.
 */
public record LawStateSyncMessage(int wantedLevel, int peaceValue, boolean hasImmunity) 
    implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID = 
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "law_state_sync");
  
  public static final CustomPacketPayload.Type<LawStateSyncMessage> TYPE = 
      new CustomPacketPayload.Type<>(MESSAGE_ID);
  
  public static final CustomPacketPayload.Type<LawStateSyncMessage> PAYLOAD_TYPE = TYPE;

  public static final StreamCodec<RegistryFriendlyByteBuf, LawStateSyncMessage> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public LawStateSyncMessage decode(RegistryFriendlyByteBuf buf) {
          int wantedLevel = buf.readVarInt();
          int peaceValue = buf.readVarInt();
          boolean hasImmunity = buf.readBoolean();
          return new LawStateSyncMessage(wantedLevel, peaceValue, hasImmunity);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, LawStateSyncMessage message) {
          buf.writeVarInt(message.wantedLevel());
          buf.writeVarInt(message.peaceValue());
          buf.writeBoolean(message.hasImmunity());
        }
      };

  // Client handler singleton reference (set by platform-specific code)
  private static ClientHandler clientHandler;
  
  /**
   * Create from FriendlyByteBuf (for network registration).
   */
  public static LawStateSyncMessage create(FriendlyByteBuf buf) {
    int wantedLevel = buf.readVarInt();
    int peaceValue = buf.readVarInt();
    boolean hasImmunity = buf.readBoolean();
    return new LawStateSyncMessage(wantedLevel, peaceValue, hasImmunity);
  }
  
  /**
   * Set the client handler for processing received messages.
   */
  public static void setClientHandler(ClientHandler handler) {
    clientHandler = handler;
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public void write(FriendlyByteBuf buf) {
    buf.writeVarInt(wantedLevel);
    buf.writeVarInt(peaceValue);
    buf.writeBoolean(hasImmunity);
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
  
  @Override
  public void handleClient() {
    if (clientHandler != null) {
      clientHandler.handleLawStateSync(wantedLevel, peaceValue, hasImmunity);
    } else {
      NetworkMessageRecord.log.debug("LawStateSyncMessage: wanted={}, peace={}, immunity={} (no handler)", 
          wantedLevel, peaceValue, hasImmunity);
    }
  }

  @Override
  public void handleServer(ServerPlayer serverPlayer) {
    // This message is server-to-client only, no server handling needed
  }

  /**
   * Interface for client-side handling (implemented in platform-specific code).
   */
  public interface ClientHandler {
    void handleLawStateSync(int wantedLevel, int peaceValue, boolean hasImmunity);
  }
}
