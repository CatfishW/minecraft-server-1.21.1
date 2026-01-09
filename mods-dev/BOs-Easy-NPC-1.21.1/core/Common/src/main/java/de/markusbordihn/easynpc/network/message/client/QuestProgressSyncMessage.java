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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record QuestProgressSyncMessage(UUID questId, String title, String description, int progress, int targetAmount, boolean completed) implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "quest_progress_sync");
  public static final CustomPacketPayload.Type<QuestProgressSyncMessage> PAYLOAD_TYPE =
      new Type<>(MESSAGE_ID);
  public static final StreamCodec<RegistryFriendlyByteBuf, QuestProgressSyncMessage> STREAM_CODEC =
      StreamCodec.of((buffer, message) -> message.write(buffer), QuestProgressSyncMessage::create);

  public static QuestProgressSyncMessage create(final FriendlyByteBuf buffer) {
    UUID questId = buffer.readUUID();
    String title = buffer.readUtf();
    String description = buffer.readUtf();
    int progress = buffer.readInt();
    int targetAmount = buffer.readInt();
    boolean completed = buffer.readBoolean();
    return new QuestProgressSyncMessage(questId, title, description, progress, targetAmount, completed);
  }

  @Override
  public void write(FriendlyByteBuf buffer) {
    buffer.writeUUID(questId);
    buffer.writeUtf(title);
    buffer.writeUtf(description != null ? description : "");
    buffer.writeInt(progress);
    buffer.writeInt(targetAmount);
    buffer.writeBoolean(completed);
  }

  @Override
  public Type<QuestProgressSyncMessage> type() {
    return PAYLOAD_TYPE;
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public void handleClient() {
    log.debug("Received quest progress sync for {}", title);
    QuestProgressSyncHandler.onQuestProgressSync(questId, title, description, progress, targetAmount, completed);
  }

  public static class QuestProgressSyncHandler {
    private static QuestProgressSyncConsumer handler = null;

    @FunctionalInterface
    public interface QuestProgressSyncConsumer {
      void accept(UUID questId, String title, String description, int progress, int targetAmount, boolean completed);
    }

    public static void setHandler(QuestProgressSyncConsumer h) {
      handler = h;
    }

    public static void onQuestProgressSync(UUID questId, String title, String description, int progress, int targetAmount, boolean completed) {
      de.markusbordihn.easynpc.client.quest.ClientQuestManager.addQuest(questId, title, description, progress, targetAmount, completed);
      if (handler != null) {
        handler.accept(questId, title, description, progress, targetAmount, completed);
      }
    }
  }
}
