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
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QuestObjectiveHandler {

  private static final Logger log = LogManager.getLogger(QuestObjectiveHandler.class);
  private static final String OBJECTIVE_KILL = "KILL";
  private static final String OBJECTIVE_GATHER = "GATHER";
  private static final String OBJECTIVE_TALK = "TALK";

  private QuestObjectiveHandler() {}

  public static void onKill(ServerPlayer player, LivingEntity target) {
      if (player == null || target == null) return;
      
      String targetKey = EntityType.getKey(target.getType()).toString();
      log.info("QuestObjectiveHandler: onKill triggered for player {} target {} ({})", player.getName().getString(), targetKey, target.getName().getString());
      checkProgress(player, OBJECTIVE_KILL, targetKey, target);
  }

  public static void onTalk(ServerPlayer player, EasyNPC<?> npc) {
       if (player == null || npc == null) return;
       // Check name or possibly UUID/Type as target
       String npcName = npc.getEntity().hasCustomName() ? npc.getEntity().getCustomName().getString() : npc.getEntity().getName().getString();
       // We might check both raw name or stripped color codes
       checkProgress(player, OBJECTIVE_TALK, npcName, npc.getEntity());
  }

  public static void onGatherCheck(ServerPlayer player) {
      if (player == null) return;
      
      // Checking inventory is expensive, should be done periodically or triggered.
      QuestProgressTracker tracker = QuestProgressTracker.get(player.serverLevel());
      Map<UUID, QuestProgressTracker.QuestProgressEntry> activeQuests = tracker.getPlayerQuests(player.getUUID());
      
      for (Map.Entry<UUID, QuestProgressTracker.QuestProgressEntry> entry : activeQuests.entrySet()) {
          if (entry.getValue().completed) continue;
          
          QuestDataEntry quest = QuestManager.getQuest(entry.getKey());
          if (quest != null && (OBJECTIVE_GATHER.equalsIgnoreCase(quest.getObjectiveType()) || "CRAFT".equalsIgnoreCase(quest.getObjectiveType()))) {
               String targetItem = quest.getObjectiveTarget();
               int requiredAmount = quest.getObjectiveAmount();
               
               int currentCount = countItemInInventory(player, targetItem);
               
               // Update only if changed/different. 
               int newProgress = Math.min(currentCount, requiredAmount);
               if (newProgress != entry.getValue().progress) {
                   if (currentCount >= requiredAmount) {
                     tracker.completeQuest(player.getUUID(), quest.getId());
                     player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aQuest Completed: " + quest.getTitle()), true);
                     grantReward(player, quest);
                   }
               }
          }
      }
  }
  
  private static int countItemInInventory(ServerPlayer player, String itemId) {
      int count = 0;
      try {
          Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
          for (ItemStack stack : player.getInventory().items) {
               if (stack.getItem() == item) {
                   count += stack.getCount();
               }
          }
          // Also check offhand?
          if (player.getOffhandItem().getItem() == item) {
               count += player.getOffhandItem().getCount();
          }
      } catch (Exception e) {
          log.warn("Invalid item id in quest config: {}", itemId);
      }
      return count;
  }

  private static void checkProgress(ServerPlayer player, String type, String target, net.minecraft.world.entity.Entity entityContext) {
      QuestProgressTracker tracker = QuestProgressTracker.get(player.serverLevel());
      Map<UUID, QuestProgressTracker.QuestProgressEntry> activeQuests = tracker.getPlayerQuests(player.getUUID());

      log.debug("Checking progress for player {} type {} target {}", player.getName().getString(), type, target);

      for (Map.Entry<UUID, QuestProgressTracker.QuestProgressEntry> entry : activeQuests.entrySet()) {
          if (entry.getValue().completed) continue;

          QuestDataEntry quest = QuestManager.getQuest(entry.getKey());
          if (quest == null) continue;

          if (type.equalsIgnoreCase(quest.getObjectiveType())) {
              // Match target
              log.debug("Evaluating quest '{}' target '{}' against event target '{}'", quest.getTitle(), quest.getObjectiveTarget(), target);
              
              if (targetsMatch(quest.getObjectiveTarget(), target, entityContext)) {
                  int amount = 1; // Default increment
                  log.info("Match found! Updating progress for quest {}", quest.getId());
                  tracker.updateProgress(player.getUUID(), quest.getId(), amount);
                  
                  // Check completion
                   if (entry.getValue().progress >= quest.getObjectiveAmount()) {
                      tracker.completeQuest(player.getUUID(), quest.getId());
                      player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aQuest Completed: " + quest.getTitle()), true);
                      grantReward(player, quest);
                  }
                  QuestSyncHandler.syncQuest(player, quest.getId());
              } else {
                  log.debug("Target mismatch for quest {}", quest.getId());
              }
          }
      }
  }
  
  private static boolean targetsMatch(String questTarget, String eventTarget, net.minecraft.world.entity.Entity entityContext) {
      if (questTarget == null || eventTarget == null) return false;
      
      String normalizedQuest = questTarget.trim().toLowerCase();
      String normalizedEvent = eventTarget.trim().toLowerCase();
      
      // Check full equality
      if (normalizedQuest.equals(normalizedEvent)) return true;
      
      // Check if event/quest key ends with the other (e.g. "minecraft:zombie" vs "zombie")
      if (normalizedEvent.endsWith(":" + normalizedQuest) || normalizedQuest.endsWith(":" + normalizedEvent)) return true;

      // Check if one contains the other (looser check for things like "zombie" matching "minecraft:zombie")
      if (normalizedEvent.contains(":" + normalizedQuest) || normalizedQuest.contains(":" + normalizedEvent)) return true;
      
      // Color code stripped check
      String strippedQuest = questTarget.replaceAll("§.", "").trim();
      String strippedEvent = eventTarget.replaceAll("§.", "").trim();
      
      if (strippedQuest.equalsIgnoreCase(strippedEvent)) return true;

      // Check against entity context (Localized Name, Custom Name)
      if (entityContext != null) {
          String entityName = entityContext.getName().getString().trim().toLowerCase();
          String entityDisplayName = entityContext.getDisplayName().getString().trim().toLowerCase();
          
          if (entityName.equals(normalizedQuest) || entityName.contains(normalizedQuest) || normalizedQuest.contains(entityName)) return true;
          if (entityDisplayName.equals(normalizedQuest) || entityDisplayName.contains(normalizedQuest) || normalizedQuest.contains(entityDisplayName)) return true;
          
          // Color stripped context
          String strippedEntityName = entityContext.getName().getString().replaceAll("§.", "").trim();
          if (strippedEntityName.equalsIgnoreCase(strippedQuest)) return true;
      }

      return false;
  }
  
  private static void grantReward(ServerPlayer player, QuestDataEntry quest) {
      if (quest.getRewardXP() > 0) {
          player.giveExperiencePoints(quest.getRewardXP());
      }
      if (quest.getRewardItemID() != null && !quest.getRewardItemID().isEmpty() && quest.getRewardItemAmount() > 0) {
          try {
              Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(quest.getRewardItemID()));
              ItemStack stack = new ItemStack(item, quest.getRewardItemAmount());
              boolean added = player.getInventory().add(stack);
              if (!added) {
                  player.drop(stack, false);
              }
              log.info("Granted item reward {} x{} to player {}", quest.getRewardItemID(), quest.getRewardItemAmount(), player.getName().getString());
          } catch (Exception e) {
              log.error("Failed to grant item reward for quest {}: {}", quest.getId(), e.getMessage());
          }
      }
  }
}
