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

package de.markusbordihn.easynpc.network.message.client;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.network.message.NetworkMessageRecord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Message to sync spawn timer data from server to client.
 */
public record SpawnTimerSyncMessage(List<TimerEntry> timers) implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "spawn_timer_sync");
  public static final CustomPacketPayload.Type<SpawnTimerSyncMessage> PAYLOAD_TYPE =
      new Type<>(MESSAGE_ID);
  public static final StreamCodec<RegistryFriendlyByteBuf, SpawnTimerSyncMessage> STREAM_CODEC =
      StreamCodec.of((buffer, message) -> message.write(buffer), SpawnTimerSyncMessage::create);

  public static SpawnTimerSyncMessage create(final FriendlyByteBuf buffer) {
    int count = buffer.readVarInt();
    List<TimerEntry> timers = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      String templateName = buffer.readUtf();
      int ticksRemaining = buffer.readVarInt();
      int totalTicks = buffer.readVarInt();
      boolean isGroupSpawn = buffer.readBoolean();
      timers.add(new TimerEntry(templateName, ticksRemaining, totalTicks, isGroupSpawn));
    }
    return new SpawnTimerSyncMessage(timers);
  }

  @Override
  public void write(FriendlyByteBuf buffer) {
    buffer.writeVarInt(timers.size());
    for (TimerEntry entry : timers) {
      buffer.writeUtf(entry.templateName);
      buffer.writeVarInt(entry.ticksRemaining);
      buffer.writeVarInt(entry.totalTicks);
      buffer.writeBoolean(entry.isGroupSpawn);
    }
  }

  @Override
  public Type<SpawnTimerSyncMessage> type() {
    return PAYLOAD_TYPE;
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public void handleClient() {
    // This will be handled by the client-side overlay via SpawnTimerSyncHandler
    log.debug("Received {} spawn timers from server", timers.size());
    
    // Store in a static field that the client overlay can access
    SpawnTimerSyncHandler.onTimerSync(timers);
  }
  
  /**
   * Handler interface that the client can implement.
   */
  public static class SpawnTimerSyncHandler {
    private static java.util.function.Consumer<List<TimerEntry>> handler = null;
    
    public static void setHandler(java.util.function.Consumer<List<TimerEntry>> h) {
      handler = h;
    }
    
    public static void onTimerSync(List<TimerEntry> timers) {
      if (handler != null) {
        handler.accept(timers);
      }
    }
  }

  /**
   * Timer entry data.
   */
  public record TimerEntry(String templateName, int ticksRemaining, int totalTicks, boolean isGroupSpawn) {}
}
