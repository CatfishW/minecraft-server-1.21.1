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

package de.markusbordihn.easynpc.client.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientQuestManager {

  private static final List<ClientQuestEntry> activeQuests = new ArrayList<>();

  private ClientQuestManager() {}

  public static void addQuest(
      UUID questId, String title, String description, int progress, int targetAmount, boolean completed) {
    // Remove existing if any
    activeQuests.removeIf(q -> q.id.equals(questId));
    activeQuests.add(new ClientQuestEntry(questId, title, description, progress, targetAmount, completed));
  }

  public static void removeQuest(UUID questId) {
    activeQuests.removeIf(q -> q.id.equals(questId));
  }

  public static boolean hasQuest(UUID questId) {
    return activeQuests.stream().anyMatch(q -> q.id.equals(questId));
  }
  
  public static boolean isQuestCompleted(UUID questId) {
      return activeQuests.stream()
          .filter(q -> q.id.equals(questId))
          .anyMatch(q -> q.progress >= q.targetAmount);
  }

  public static List<ClientQuestEntry> getQuests() {
    return activeQuests;
  }

  public static void clearQuests() {
    activeQuests.clear();
  }

  public static class ClientQuestEntry {
    public UUID id;
    public String title;
    public String description;
    public int progress;
    public int targetAmount;
    public boolean completed;

    public ClientQuestEntry(
        UUID id, String title, String description, int progress, int targetAmount, boolean completed) {
      this.id = id;
      this.title = title;
      this.description = description;
      this.progress = progress;
      this.targetAmount = targetAmount;
      this.completed = completed;
    }
  }
}
