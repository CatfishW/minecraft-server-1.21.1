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
import de.markusbordihn.easynpc.data.crime.PlayerLawState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server -> client payload containing law system admin data and config.
 */
public record LawAdminDataMessage(
    LawSystemConfig config,
    int merchantCount,
    int guardCount,
    List<PlayerSnapshot> players,
    boolean openScreen)
    implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "law_admin_data");

  public static final CustomPacketPayload.Type<LawAdminDataMessage> TYPE =
      new CustomPacketPayload.Type<>(MESSAGE_ID);

  public static final CustomPacketPayload.Type<LawAdminDataMessage> PAYLOAD_TYPE = TYPE;

  public static final StreamCodec<RegistryFriendlyByteBuf, LawAdminDataMessage> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public LawAdminDataMessage decode(RegistryFriendlyByteBuf buf) {
          var configTag = buf.readNbt();
          LawSystemConfig config = configTag != null ? new LawSystemConfig(configTag) : new LawSystemConfig();
          int merchantCount = buf.readVarInt();
          int guardCount = buf.readVarInt();
          int playerCount = buf.readVarInt();
          List<PlayerSnapshot> players = new ArrayList<>();
          for (int i = 0; i < playerCount; i++) {
            UUID uuid = buf.readUUID();
            String name = buf.readUtf();
            var stateTag = buf.readNbt();
            PlayerLawState state = stateTag != null ? new PlayerLawState(stateTag) : new PlayerLawState(uuid);
            players.add(new PlayerSnapshot(uuid, name, state));
          }
          boolean openScreen = buf.readBoolean();
          return new LawAdminDataMessage(config, merchantCount, guardCount, players, openScreen);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, LawAdminDataMessage message) {
          LawSystemConfig config = message.config() != null ? message.config() : new LawSystemConfig();
          buf.writeNbt(config.createTag());
          buf.writeVarInt(message.merchantCount());
          buf.writeVarInt(message.guardCount());
          List<PlayerSnapshot> players = message.players() != null ? message.players() : List.of();
          buf.writeVarInt(players.size());
          for (PlayerSnapshot snapshot : players) {
            buf.writeUUID(snapshot.uuid());
            buf.writeUtf(snapshot.name());
            buf.writeNbt(snapshot.state().createTag());
          }
          buf.writeBoolean(message.openScreen());
        }
      };

  public record PlayerSnapshot(UUID uuid, String name, PlayerLawState state) {}

  private static ClientHandler clientHandler;

  public static LawAdminDataMessage create(FriendlyByteBuf buf) {
    var configTag = buf.readNbt();
    LawSystemConfig config = configTag != null ? new LawSystemConfig(configTag) : new LawSystemConfig();
    int merchantCount = buf.readVarInt();
    int guardCount = buf.readVarInt();
    int playerCount = buf.readVarInt();
    List<PlayerSnapshot> players = new ArrayList<>();
    for (int i = 0; i < playerCount; i++) {
      UUID uuid = buf.readUUID();
      String name = buf.readUtf();
      var stateTag = buf.readNbt();
      PlayerLawState state = stateTag != null ? new PlayerLawState(stateTag) : new PlayerLawState(uuid);
      players.add(new PlayerSnapshot(uuid, name, state));
    }
    boolean openScreen = buf.readBoolean();
    return new LawAdminDataMessage(config, merchantCount, guardCount, players, openScreen);
  }

  public static void setClientHandler(ClientHandler handler) {
    clientHandler = handler;
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public void write(FriendlyByteBuf buf) {
    LawSystemConfig config = this.config != null ? this.config : new LawSystemConfig();
    buf.writeNbt(config.createTag());
    buf.writeVarInt(this.merchantCount);
    buf.writeVarInt(this.guardCount);
    List<PlayerSnapshot> players = this.players != null ? this.players : List.of();
    buf.writeVarInt(players.size());
    for (PlayerSnapshot snapshot : players) {
      buf.writeUUID(snapshot.uuid());
      buf.writeUtf(snapshot.name());
      buf.writeNbt(snapshot.state().createTag());
    }
    buf.writeBoolean(this.openScreen);
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  @Override
  public void handleClient() {
    if (clientHandler != null) {
      clientHandler.handleLawAdminData(this);
    } else {
      NetworkMessageRecord.log.debug("LawAdminDataMessage received with no handler");
    }
  }

  @Override
  public void handleServer(ServerPlayer serverPlayer) {
    // Server -> client only.
  }

  public interface ClientHandler {
    void handleLawAdminData(LawAdminDataMessage message);
  }
}
