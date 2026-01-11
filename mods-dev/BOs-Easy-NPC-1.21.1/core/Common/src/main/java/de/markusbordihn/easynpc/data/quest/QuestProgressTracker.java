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

package de.markusbordihn.easynpc.data.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QuestProgressTracker extends SavedData {

  private static final Logger log = LogManager.getLogger(QuestProgressTracker.class);
  private static final String DATA_NAME = "easy_npc_quest_progress";
  private static final String DATA_PLAYERS_TAG = "Players";
  private static final String DATA_PLAYER_UUID_TAG = "PlayerUUID";
  private static final String DATA_QUESTS_TAG = "Quests";
  private static final String DATA_QUEST_ID_TAG = "QuestId";
  private static final String DATA_QUEST_PROGRESS_TAG = "Progress";
  private static final String DATA_QUEST_COMPLETED_TAG = "Completed";

  // Map<PlayerUUID, Map<QuestID, ProgressEntry>>
  private final Map<UUID, Map<UUID, QuestProgressEntry>> trackingData = new HashMap<>();

  public QuestProgressTracker() {}

  public static QuestProgressTracker load(CompoundTag compoundTag, HolderLookup.Provider provider) {
    QuestProgressTracker tracker = new QuestProgressTracker();
    if (compoundTag.contains(DATA_PLAYERS_TAG)) {
        ListTag playersTag = compoundTag.getList(DATA_PLAYERS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < playersTag.size(); i++) {
          CompoundTag playerTag = playersTag.getCompound(i);
          UUID playerId = playerTag.getUUID(DATA_PLAYER_UUID_TAG);
          
          Map<UUID, QuestProgressEntry> playerQuests = new HashMap<>();
          ListTag questsTag = playerTag.getList(DATA_QUESTS_TAG, Tag.TAG_COMPOUND);
          for (int j = 0; j < questsTag.size(); j++) {
              CompoundTag questTag = questsTag.getCompound(j);
              UUID questId = questTag.getUUID(DATA_QUEST_ID_TAG);
              int progress = questTag.getInt(DATA_QUEST_PROGRESS_TAG);
              boolean completed = questTag.getBoolean(DATA_QUEST_COMPLETED_TAG);
              playerQuests.put(questId, new QuestProgressEntry(progress, completed));
          }
          tracker.trackingData.put(playerId, playerQuests);
        }
    }
    return tracker;
  }

  @Override
  public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
    ListTag playersTag = new ListTag();
    for (Map.Entry<UUID, Map<UUID, QuestProgressEntry>> playerEntry : trackingData.entrySet()) {
        CompoundTag playerTag = new CompoundTag();
        playerTag.putUUID(DATA_PLAYER_UUID_TAG, playerEntry.getKey());
        
        ListTag questsTag = new ListTag();
        for (Map.Entry<UUID, QuestProgressEntry> questEntry : playerEntry.getValue().entrySet()) {
            CompoundTag questTag = new CompoundTag();
            questTag.putUUID(DATA_QUEST_ID_TAG, questEntry.getKey());
            questTag.putInt(DATA_QUEST_PROGRESS_TAG, questEntry.getValue().progress);
            questTag.putBoolean(DATA_QUEST_COMPLETED_TAG, questEntry.getValue().completed);
            questsTag.add(questTag);
        }
        playerTag.put(DATA_QUESTS_TAG, questsTag);
        playersTag.add(playerTag);
    }
    compoundTag.put(DATA_PLAYERS_TAG, playersTag);
    return compoundTag;
  }

  public static QuestProgressTracker get(ServerLevel serverLevel) {
    return serverLevel.getDataStorage().computeIfAbsent(
        new SavedData.Factory<>(QuestProgressTracker::new, QuestProgressTracker::load, null),
        DATA_NAME);
  }

  public void acceptQuest(UUID playerUUID, UUID questUUID) {
      Map<UUID, QuestProgressEntry> playerQuests = trackingData.computeIfAbsent(playerUUID, k -> new HashMap<>());
      if (!playerQuests.containsKey(questUUID)) {
          playerQuests.put(questUUID, new QuestProgressEntry(0, false));
          setDirty();
      }
  }

  public void updateProgress(UUID playerUUID, UUID questUUID, int amount) {
      if (trackingData.containsKey(playerUUID)) {
          QuestProgressEntry entry = trackingData.get(playerUUID).get(questUUID);
          if (entry != null && !entry.completed) {
              entry.progress += amount;
              setDirty();
          }
      }
  }

  public void setProgress(UUID playerUUID, UUID questUUID, int progress) {
      if (trackingData.containsKey(playerUUID)) {
          QuestProgressEntry entry = trackingData.get(playerUUID).get(questUUID);
          if (entry != null && !entry.completed) {
              entry.progress = progress;
              setDirty();
          }
      }
  }

  public void completeQuest(UUID playerUUID, UUID questUUID) {
      if (trackingData.containsKey(playerUUID)) {
          QuestProgressEntry entry = trackingData.get(playerUUID).get(questUUID);
          if (entry != null) {
              entry.completed = true;
              setDirty();
          }
      }
  }

  public void removeQuest(UUID playerUUID, UUID questUUID) {
      if (trackingData.containsKey(playerUUID)) {
          trackingData.get(playerUUID).remove(questUUID);
          setDirty();
      }
  }
  
  public Map<UUID, QuestProgressEntry> getPlayerQuests(UUID playerUUID) {
      return trackingData.getOrDefault(playerUUID, new HashMap<>());
  }

  public static class QuestProgressEntry {
      public int progress;
      public boolean completed;

      public QuestProgressEntry(int progress, boolean completed) {
          this.progress = progress;
          this.completed = completed;
      }
  }
}
