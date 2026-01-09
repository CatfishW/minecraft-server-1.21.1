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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QuestManager {

  protected static final Logger log = LogManager.getLogger(QuestManager.class);
  private static final Map<UUID, QuestDataEntry> questMap = new HashMap<>();

  private QuestManager() {}

  public static void clearQuests() {
      questMap.clear();
      log.info("Cleared all registered quests.");
  }

  public static void registerQuest(QuestDataEntry quest) {
      if (quest != null && quest.getId() != null) {
          questMap.put(quest.getId(), quest);
          log.info("Registered quest: {} ({})", quest.getTitle(), quest.getId());
      }
  }

  public static QuestDataEntry getQuest(UUID id) {
      return questMap.get(id);
  }

  public static void loadQuests(java.nio.file.Path questDir) {
      if (!java.nio.file.Files.exists(questDir)) {
          try {
              java.nio.file.Files.createDirectories(questDir);
          } catch (java.io.IOException e) {
              log.error("Failed to create quest directory: {}", e.getMessage());
              return;
          }
      }

      com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

      try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(questDir)) {
          stream.filter(path -> path.toString().endsWith(".json"))
                .forEach(path -> {
                    try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(path)) {
                        com.google.gson.JsonObject json = gson.fromJson(reader, com.google.gson.JsonObject.class);
                        
                        UUID id = UUID.fromString(json.get("id").getAsString());
                        String title = json.get("title").getAsString();
                        String description = json.get("description").getAsString();
                        
                        QuestDataEntry quest = new QuestDataEntry(id, title, description);
                        
                        if (json.has("objective")) {
                            com.google.gson.JsonObject obj = json.getAsJsonObject("objective");
                            String type = obj.get("type").getAsString();
                            String target = obj.get("target").getAsString();
                            int amount = obj.get("amount").getAsInt();
                            quest.setObjective(type, target, amount);
                        }
                        
                        if (json.has("reward")) {
                             com.google.gson.JsonObject rew = json.getAsJsonObject("reward");
                             if (rew.has("xp")) {
                                 quest.setRewardXP(rew.get("xp").getAsInt());
                             }
                             if (rew.has("itemId")) {
                                 quest.setRewardItem(rew.get("itemId").getAsString(), rew.has("amount") ? rew.get("amount").getAsInt() : 1);
                             }
                        }

                        registerQuest(quest);
                        log.info("Successfully loaded quest {} from {}", quest.getId(), path.getFileName());
                    } catch (Exception e) {
                        log.error("Failed to load quest from {}: {}", path, e.getMessage());
                    }
                });
      } catch (java.io.IOException e) {
          log.error("Error walking quest directory: {}", e.getMessage());
      }
  }
}
