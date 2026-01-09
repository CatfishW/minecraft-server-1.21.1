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

package de.markusbordihn.easynpc.handler;

import de.markusbordihn.easynpc.data.quest.QuestDataEntry;
import de.markusbordihn.easynpc.data.quest.QuestManager;
import de.markusbordihn.easynpc.data.quest.QuestProgressTracker;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.message.client.QuestProgressSyncMessage;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public class QuestSyncHandler {

  private QuestSyncHandler() {}

  public static void syncAllQuests(ServerPlayer player) {
    if (player == null) return;
    
    QuestProgressTracker tracker = QuestProgressTracker.get(player.serverLevel());
    Map<UUID, QuestProgressTracker.QuestProgressEntry> quests = tracker.getPlayerQuests(player.getUUID());
    
    for (Map.Entry<UUID, QuestProgressTracker.QuestProgressEntry> entry : quests.entrySet()) {
        syncQuest(player, entry.getKey(), entry.getValue());
    }
  }

  public static void syncQuest(ServerPlayer player, UUID questId) {
      if (player == null || questId == null) return;
      QuestProgressTracker tracker = QuestProgressTracker.get(player.serverLevel());
      QuestProgressTracker.QuestProgressEntry entry = tracker.getPlayerQuests(player.getUUID()).get(questId);
      if (entry != null) {
          syncQuest(player, questId, entry);
      }
  }

  private static void syncQuest(ServerPlayer player, UUID questId, QuestProgressTracker.QuestProgressEntry entry) {
      QuestDataEntry quest = QuestManager.getQuest(questId);
      if (quest != null) {
          NetworkHandlerManager.sendMessageToPlayer(
              new QuestProgressSyncMessage(questId, quest.getTitle(), quest.getDescription(), entry.progress, quest.getObjectiveAmount(), entry.completed),
              player);
      }
  }
}
