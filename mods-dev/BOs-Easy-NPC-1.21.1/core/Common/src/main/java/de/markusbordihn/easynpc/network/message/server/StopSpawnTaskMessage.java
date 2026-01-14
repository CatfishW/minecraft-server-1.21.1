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

package de.markusbordihn.easynpc.network.message.server;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.handler.SpawningHandler;
import de.markusbordihn.easynpc.network.message.NetworkMessageRecord;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record StopSpawnTaskMessage(String templateName) implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "stop_spawn_task");
  public static final CustomPacketPayload.Type<StopSpawnTaskMessage> PAYLOAD_TYPE =
      new Type<>(MESSAGE_ID);
  public static final StreamCodec<RegistryFriendlyByteBuf, StopSpawnTaskMessage> STREAM_CODEC =
      StreamCodec.of((buffer, message) -> message.write(buffer), StopSpawnTaskMessage::create);

  public static StopSpawnTaskMessage create(final FriendlyByteBuf buffer) {
    return new StopSpawnTaskMessage(buffer.readUtf());
  }

  @Override
  public void write(final FriendlyByteBuf buffer) {
    buffer.writeUtf(this.templateName);
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return PAYLOAD_TYPE;
  }

  @Override
  public void handleServer(final ServerPlayer serverPlayer) {
    if (serverPlayer.hasPermissions(2)) {
      int count = SpawningHandler.stopSpawningTask(this.templateName);
      if (count > 0) {
        serverPlayer.sendSystemMessage(
            Component.literal("§eStopped " + count + " spawning task(s) for " + templateName + "."));
      }
    }
  }
}
