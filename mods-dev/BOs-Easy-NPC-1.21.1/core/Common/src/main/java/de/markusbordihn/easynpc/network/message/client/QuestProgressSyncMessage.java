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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import java.util.UUID;

public record QuestProgressSyncMessage(UUID questId, String title, String description, int progress, int targetAmount, boolean completed, int rewardXP, String rewardItemID, int rewardItemAmount) implements NetworkMessageRecord {

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
    int rewardXP = buffer.readInt();
    String rewardItemID = buffer.readUtf();
    int rewardItemAmount = buffer.readInt();
    return new QuestProgressSyncMessage(questId, title, description, progress, targetAmount, completed, rewardXP, rewardItemID, rewardItemAmount);
  }

  @Override
  public void write(FriendlyByteBuf buffer) {
    buffer.writeUUID(questId);
    buffer.writeUtf(title);
    buffer.writeUtf(description != null ? description : "");
    buffer.writeInt(progress);
    buffer.writeInt(targetAmount);
    buffer.writeBoolean(completed);
    buffer.writeInt(rewardXP);
    buffer.writeUtf(rewardItemID != null ? rewardItemID : "");
    buffer.writeInt(rewardItemAmount);
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
    QuestProgressSyncHandler.onQuestProgressSync(questId, title, description, progress, targetAmount, completed, rewardXP, rewardItemID, rewardItemAmount);
  }

  public static class QuestProgressSyncHandler {
    private static QuestProgressSyncConsumer handler = null;

    @FunctionalInterface
    public interface QuestProgressSyncConsumer {
      void accept(UUID questId, String title, String description, int progress, int targetAmount, boolean completed, int rewardXP, String rewardItemID, int rewardItemAmount);
    }

    public static void setHandler(QuestProgressSyncConsumer h) {
      handler = h;
    }

    public static void onQuestProgressSync(UUID questId, String title, String description, int progress, int targetAmount, boolean completed, int rewardXP, String rewardItemID, int rewardItemAmount) {
      boolean wasCompleted = de.markusbordihn.easynpc.client.quest.ClientQuestManager.hasQuest(questId) && 
                            de.markusbordihn.easynpc.client.quest.ClientQuestManager.getQuests().stream()
                            .filter(q -> q.id.equals(questId))
                            .anyMatch(q -> q.completed);
      
      de.markusbordihn.easynpc.client.quest.ClientQuestManager.addQuest(questId, title, description, progress, targetAmount, completed, rewardXP, rewardItemID, rewardItemAmount);
      
      if (completed && !wasCompleted) {
        showQuestCompletionTitle(title, rewardXP, rewardItemID, rewardItemAmount);
      }

      if (handler != null) {
        handler.accept(questId, title, description, progress, targetAmount, completed, rewardXP, rewardItemID, rewardItemAmount);
      }
    }

    private static void showQuestCompletionTitle(String title, int xp, String item, int amount) {
       net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
       if (minecraft.player != null) {
          // Main Title: Chinese for "Quest Completed"
          Component mainTitle = Component.translatable("gui.easy_npc.quest.completed_title");
          
          // Subtitle: Title of quest and rewards
          StringBuilder rewardStr = new StringBuilder();
          if (xp > 0) {
              rewardStr.append("§e+").append(xp).append(" XP ");
          }
          if (item != null && !item.isEmpty() && amount > 0) {
              String name = item;
              try {
                  // Try to get localized item name
                  name = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)).getDescription().getString();
              } catch (Exception ignore) {}
              rewardStr.append("§a+").append(amount).append(" ").append(name);
          }
          
          Component subTitle = Component.literal("§6" + title + (rewardStr.length() > 0 ? " §7- " + rewardStr.toString() : ""));
          
          // Use Minecraft's built-in title system
          minecraft.gui.setTimes(10, 70, 20); // fade in, stay, fade out
          minecraft.gui.setTitle(mainTitle);
          minecraft.gui.setSubtitle(subTitle);
          
          // Also a task bar message for backup
          minecraft.player.displayClientMessage(Component.literal("§6[EasyNPC] §a✔ ").append(mainTitle).append(Component.literal(": " + title)), false);
       }
    }
  }
}
