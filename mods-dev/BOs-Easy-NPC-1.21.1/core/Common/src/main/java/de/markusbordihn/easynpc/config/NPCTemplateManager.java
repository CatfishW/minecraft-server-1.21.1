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

package de.markusbordihn.easynpc.config;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.data.action.ActionDataEntry;
import de.markusbordihn.easynpc.data.attribute.CombatAttributeType;
import de.markusbordihn.easynpc.handler.AttributeHandler;
import de.markusbordihn.easynpc.data.action.ActionDataSet;
import de.markusbordihn.easynpc.data.action.ActionDataType;
import de.markusbordihn.easynpc.data.condition.ConditionDataEntry;
import de.markusbordihn.easynpc.data.condition.ConditionOperationType;
import de.markusbordihn.easynpc.data.condition.ConditionType;
import de.markusbordihn.easynpc.data.dialog.DialogButtonEntry;
import de.markusbordihn.easynpc.data.dialog.DialogButtonType;
import de.markusbordihn.easynpc.data.dialog.DialogDataEntry;
import de.markusbordihn.easynpc.data.dialog.DialogDataSet;
import de.markusbordihn.easynpc.data.dialog.DialogPriority;
import de.markusbordihn.easynpc.data.dialog.DialogType;
import de.markusbordihn.easynpc.data.objective.ObjectiveDataEntry;
import de.markusbordihn.easynpc.data.objective.ObjectiveType;
import de.markusbordihn.easynpc.data.skin.SkinDataEntry;
import de.markusbordihn.easynpc.data.synched.SynchedDataIndex;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.ConfigDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.ObjectiveDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.SkinDataCapable;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manager for NPC templates. Handles spawning NPCs from JSON templates.
 */
public class NPCTemplateManager {
  
  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[NPCTemplateManager]";
  
  private NPCTemplateManager() {}
  
  /**
   * Get all available template names.
   */
  public static Set<String> getTemplateNames() {
    return NPCJsonConfig.getTemplateNames();
  }
  
  /**
   * Check if a template exists.
   */
  public static boolean hasTemplate(String name) {
    return NPCJsonConfig.hasTemplate(name);
  }
  
  /**
   * Get a template by name.
   */
  public static Optional<NPCTemplateData> getTemplate(String name) {
    return Optional.ofNullable(NPCJsonConfig.getTemplate(name));
  }
  
  /**
   * Reload all templates from disk.
   */
  public static void reloadTemplates() {

    NPCJsonConfig.reloadTemplates();
    
    // Reload quests
    de.markusbordihn.easynpc.data.quest.QuestManager.clearQuests();
    java.nio.file.Path questDir = java.nio.file.Paths.get("config/easy_npc/quests");
    de.markusbordihn.easynpc.data.quest.QuestManager.loadQuests(questDir);
    log.info("{} Reloaded {} templates and quests", LOG_PREFIX, getTemplateNames().size());
  }
  
  /**
   * Spawn an NPC from a template at a player's location.
   */
  public static boolean spawnFromTemplate(ServerPlayer player, String templateName) {
    Vec3 pos = player.position();
    return spawnFromTemplate(player.serverLevel(), templateName, pos.x, pos.y, pos.z);
  }
  
  /**
   * Spawn an NPC from a template at a specific position.
   */
  public static boolean spawnFromTemplate(ServerLevel level, String templateName, double x, double y, double z) {
    NPCTemplateData template = NPCJsonConfig.getTemplate(templateName);
    if (template == null) {
      log.warn("{} Template not found: {}", LOG_PREFIX, templateName);
      return false;
    }
    
    return spawnFromTemplate(level, template, x, y, z);
  }

  /**
   * Spawn an NPC entity from a template at a specific position.
   */
  public static Entity spawnEntityFromTemplate(ServerLevel level, String templateName, double x, double y, double z) {
    NPCTemplateData template = NPCJsonConfig.getTemplate(templateName);
    if (template == null) {
      log.warn("{} Template not found: {}", LOG_PREFIX, templateName);
      return null;
    }
    
    return spawnEntityFromTemplate(level, template, x, y, z);
  }
  
  /**
   * Spawn an NPC from a template data object.
   */
  public static boolean spawnFromTemplate(ServerLevel level, NPCTemplateData template, double x, double y, double z) {
    return spawnEntityFromTemplate(level, template, x, y, z) != null;
  }

  /**
   * Spawn an NPC entity from a template data object and return it.
   */
  public static Entity spawnEntityFromTemplate(ServerLevel level, NPCTemplateData template, double x, double y, double z) {
    if (template == null) {
      log.warn("{} Cannot spawn from null template", LOG_PREFIX);
      return null;
    }
    
    String entityTypeStr = template.getEntityType();
    if (entityTypeStr == null || entityTypeStr.isEmpty()) {
      entityTypeStr = "easy_npc:humanoid";
    }
    
    try {
      // Parse entity type
      Optional<EntityType<?>> entityTypeOpt = EntityType.byString(entityTypeStr);
      if (entityTypeOpt.isEmpty()) {
        log.error("{} Unknown entity type: {}", LOG_PREFIX, entityTypeStr);
        return null;
      }
      
      EntityType<?> entityType = entityTypeOpt.get();
      
      // Spawn the entity
      Entity entity = entityType.spawn(level, BlockPos.containing(x, y, z), MobSpawnType.COMMAND);
      if (entity == null) {
        log.error("{} Failed to spawn entity of type: {}", LOG_PREFIX, entityTypeStr);
        return null;
      }
      
      // Apply template configuration to the NPC
      if (entity instanceof EasyNPC<?> easyNPC) {
        applyTemplateToNPC(easyNPC, template);
        log.info("{} Spawned NPC '{}' from template at ({}, {}, {})", 
            LOG_PREFIX, template.getName(), x, y, z);
        return entity;
      } else {
        log.warn("{} Spawned entity is not an EasyNPC: {}", LOG_PREFIX, entity.getClass().getName());
        return entity; // Entity was still spawned
      }
      
    } catch (Exception e) {
      log.error("{} Failed to spawn NPC from template:", LOG_PREFIX, e);
      return null;
    }
  }
  
  /**
   * Apply template configuration to an existing NPC.
   */
  public static void applyTemplateToNPC(EasyNPC<?> npc, NPCTemplateData template) {
    if (npc == null || template == null) {
      log.warn("{} Cannot apply template: npc={}, template={}", LOG_PREFIX, npc, template);
      return;
    }
    
    log.info("{} Starting to apply template '{}' to NPC {}", 
        LOG_PREFIX, template.getName(), npc.getEntityUUID());
    
    // Apply name
    if (template.getName() != null && !template.getName().isEmpty()) {
      npc.getEntity().setCustomName(net.minecraft.network.chat.Component.literal(template.getName()));
      npc.getEntity().setCustomNameVisible(true);
      log.info("{} Applied name: {}", LOG_PREFIX, template.getName());
    }
    
    // Apply faction
    if (template.getFaction() != null && !template.getFaction().isEmpty()) {
      if (npc instanceof ConfigDataCapable<?> configCapable) {
        configCapable.setFaction(template.getFaction());
        log.info("{} Applied faction: {}", LOG_PREFIX, template.getFaction());
      }
    }
    
    // Apply attributes
    if (template.getAttributes() != null) {
      try {
        applyAttributes(npc, template.getAttributes());
        log.info("{} Applied attributes successfully", LOG_PREFIX);
      } catch (Exception e) {
        log.error("{} Failed to apply attributes:", LOG_PREFIX, e);
      }
    }
    
    // Apply dialog configuration
    // Apply dialog configuration
    if (template.getDialogs() != null && !template.getDialogs().isEmpty()) {
      log.info("{} Template has dialogs map config, applying...", LOG_PREFIX);
      try {
        applyDialogs(npc, template.getDialogs());
      } catch (Exception e) {
        log.error("{} Failed to apply dialogs map:", LOG_PREFIX, e);
      }
    } else if (template.getDialog() != null) {
      log.info("{} Template has legacy dialog config, applying...", LOG_PREFIX);
      try {
        applyDialog(npc, template.getDialog());
      } catch (Exception e) {
        log.error("{} Failed to apply dialog:", LOG_PREFIX, e);
      }
    } else {
      log.warn("{} Template has no dialog config", LOG_PREFIX);
    }
    
    // Apply skin configuration
    if (template.getSkin() != null) {
      log.info("{} Template has skin config, applying...", LOG_PREFIX);
      try {
        applySkin(npc, template.getSkin());
      } catch (Exception e) {
        log.error("{} Failed to apply skin:", LOG_PREFIX, e);
      }
    }
    
    // Apply equipment
    if (template.getEquipment() != null) {
      log.info("{} Template has equipment config, applying...", LOG_PREFIX);
      try {
        applyEquipment(npc, template.getEquipment());
      } catch (Exception e) {
        log.error("{} Failed to apply equipment:", LOG_PREFIX, e);
      }
    }

    // Apply trading configuration
    if (template.getTrading() != null) {
      log.info("{} Template has trading config, applying...", LOG_PREFIX);
      try {
        applyTrading(npc, template.getTrading());
      } catch (Exception e) {
        log.error("{} Failed to apply trading:", LOG_PREFIX, e);
      }
    }
    
    // Apply objectives
    if (template.getObjectives() != null) {
      log.info("{} Template has objectives config, applying...", LOG_PREFIX);
      try {
        applyObjectives(npc, template.getObjectives());
      } catch (Exception e) {
        log.error("{} Failed to apply objectives:", LOG_PREFIX, e);
      }
    }
    
    // Apply drop configuration
    if (template.getDrop() != null) {
      log.info("{} Template has drop config, applying...", LOG_PREFIX);
      try {
        applyDrops(npc, template.getDrop());
      } catch (Exception e) {
        log.error("{} Failed to apply drop config:", LOG_PREFIX, e);
      }
    }
    
    log.info("{} Finished applying template '{}' to NPC {}", 
        LOG_PREFIX, template.getName(), npc.getEntityUUID());
  }

  /**
   * Apply dialogs map configuration to an NPC.
   */
  private static void applyDialogs(EasyNPC<?> npc, java.util.Map<String, NPCTemplateData.DialogConfig> dialogsMap) {
    if (!(npc instanceof DialogDataCapable<?> dialogCapable)) {
      log.warn("{} NPC does not support dialog data", LOG_PREFIX);
      return;
    }

    try {
      // Create a new DialogDataSet
      DialogDataSet dialogDataSet = new DialogDataSet(DialogType.STANDARD);
      
      // Track if we need to add a one-time conversation return dialog
      boolean hasOneTimeConversation = false;
      String shortDialogText = null;
      String shortDialogButtonLabel = null;
      NPCTemplateData.DialogButton mainQuestButton = null;
      
      // Check if any dialog has oneTimeConversation enabled
      // If so, we need unique labels per NPC to prevent shared tracking
      boolean needsUniqueLabels = dialogsMap.values().stream()
          .anyMatch(c -> c.isOneTimeConversation());
      
      // Create a label prefix using NPC UUID (first 8 chars) for unique tracking
      String labelPrefix = needsUniqueLabels ? 
          npc.getEntityUUID().toString().substring(0, 8) + "_" : "";
      
      for (java.util.Map.Entry<String, NPCTemplateData.DialogConfig> entry : dialogsMap.entrySet()) {
        String dialogId = entry.getKey();
        NPCTemplateData.DialogConfig dialogConfig = entry.getValue();
        
        if (dialogConfig.getGreeting() == null || dialogConfig.getGreeting().isEmpty()) {
          continue;
        }

        Set<DialogButtonEntry> buttons = new LinkedHashSet<>();
        
        // Create buttons from JSON config
        if (dialogConfig.getButtons() != null) {
          for (NPCTemplateData.DialogButton buttonConfig : dialogConfig.getButtons()) {
            DialogButtonEntry button = createButtonWithPrefix(buttonConfig, labelPrefix);
            if (button != null) {
              buttons.add(button);
              
              // Store quest button if it's the main dialog
              if ("main".equals(dialogId) && buttonConfig.getAction() != null && buttonConfig.getAction().startsWith("OPEN_QUEST_DIALOG")) {
                  mainQuestButton = buttonConfig;
              }
            }
          }
        }
        
        // Check for LLM config on the main/root dialog if needed, or global.
        // For now, we assume simple dialogs.
        if (dialogConfig.isUseLLM()) {
             dialogDataSet.setLLMEnabled(true);
             if (dialogConfig.getLlmSystemPrompt() != null && !dialogConfig.getLlmSystemPrompt().isEmpty()) {
               dialogDataSet.setLLMSystemPrompt(dialogConfig.getLlmSystemPrompt());
             }
        }

        // Create the dialog entry with prefixed label for unique tracking
        String uniqueLabel = labelPrefix + dialogId;
        DialogDataEntry dialogEntry = new DialogDataEntry(
            uniqueLabel,        // label (prefixed for unique tracking)
            dialogId,           // name (display name in editor - unchanged)
            dialogConfig.getGreeting(),  // text
            buttons           // buttons
        );
        
        // Handle one-time conversation for "main" dialog
        if ("main".equals(dialogId) && dialogConfig.isOneTimeConversation()) {
          hasOneTimeConversation = true;
          shortDialogText = dialogConfig.getShortDialogText();
          shortDialogButtonLabel = dialogConfig.getShortDialogButtonLabel();
          
          // Set main dialog to HIGH priority - it will show on first visit (when condition passes)
          dialogEntry.setPriority(DialogPriority.HIGH);
          
          // Add EXECUTION_LIMIT condition: limit=1, interval=LIFETIME
          Set<ConditionDataEntry> conditions = new LinkedHashSet<>();
          conditions.add(new ConditionDataEntry(
              ConditionType.EXECUTION_LIMIT,
              ConditionOperationType.NONE,
              "",  // name not used for EXECUTION_LIMIT
              1,   // limit: 1 time only
              "LIFETIME"  // interval: permanent
          ));
          dialogEntry.setConditions(conditions);
          
          log.info("{} Configured one-time conversation for dialog '{}' (label: {}) with limit=1", 
              LOG_PREFIX, dialogId, uniqueLabel);
        }
        
        dialogDataSet.addDialog(dialogEntry);
        log.debug("{} Added dialog '{}' with label '{}' and {} buttons", 
            LOG_PREFIX, dialogId, uniqueLabel, buttons.size());
      }
      
      // Create return dialog for one-time conversation (shows after first interaction)
      if (hasOneTimeConversation) {
        String returnText = shortDialogText != null ? shortDialogText : "我们之前聊过了。再见！";
        String returnButtonLabel = shortDialogButtonLabel != null ? shortDialogButtonLabel : "告别";
        
        // Create close button for return dialog
        Set<DialogButtonEntry> returnButtons = new LinkedHashSet<>();
        ActionDataSet closeActions = new ActionDataSet();
        closeActions.add(new ActionDataEntry(ActionDataType.CLOSE_DIALOG));
        returnButtons.add(new DialogButtonEntry(returnButtonLabel, "close_btn", DialogButtonType.CLOSE, closeActions));
        
        // Add quest button to return dialog if present and player hasn't taken it
        if (mainQuestButton != null) {
            DialogButtonEntry questButton = createButtonWithPrefix(mainQuestButton, labelPrefix);
            if (questButton != null) {
                // Add QUEST_NOT_ACCEPTED condition
                String questIdStr = "";
                if (mainQuestButton.getAction().contains(":")) {
                    questIdStr = mainQuestButton.getAction().substring(mainQuestButton.getAction().indexOf(":") + 1).trim();
                }
                
                Set<ConditionDataEntry> conditions = new java.util.LinkedHashSet<>();
                conditions.add(new ConditionDataEntry(ConditionType.QUEST_NOT_ACCEPTED, ConditionOperationType.NONE, questIdStr, 0, ""));
                questButton = questButton.withConditions(conditions);
                returnButtons.add(questButton);
                log.info("{} Added conditional quest button to return dialog for quest ID {}", LOG_PREFIX, questIdStr);
            }
        }
        
        String returnLabel = labelPrefix + "return";
        DialogDataEntry returnDialog = new DialogDataEntry(
            returnLabel,   // prefixed label
            "Return",      // name
            returnText,
            returnButtons
        );
        
        // Set LOW priority - this is a fallback that shows when main dialog's condition fails
        returnDialog.setPriority(DialogPriority.LOW);
        dialogDataSet.addDialog(returnDialog);
        log.info("{} Added return dialog with label '{}' for one-time conversation feature", 
            LOG_PREFIX, returnLabel);
      }
      
      // Apply the dialog data set to the NPC
      dialogCapable.setDialogDataSet(dialogDataSet);
      log.info("{} Applied dialog configuration with {} dialogs", 
          LOG_PREFIX, dialogDataSet.getDialogsByLabel().size());

    } catch (Exception e) {
      log.error("{} Failed to apply dialogs configuration:", LOG_PREFIX, e);
    }
  }
  
  /**
   * Apply dialog configuration to an NPC.
   */
  private static void applyDialog(EasyNPC<?> npc, NPCTemplateData.DialogConfig dialogConfig) {
    if (!(npc instanceof DialogDataCapable<?> dialogCapable)) {
      log.warn("{} NPC does not support dialog data", LOG_PREFIX);
      return;
    }
    
    try {
      // Create a new DialogDataSet
      DialogDataSet dialogDataSet = new DialogDataSet(DialogType.STANDARD);
      
      // Set LLM options if configured
      if (dialogConfig.isUseLLM()) {
        dialogDataSet.setLLMEnabled(true);
        if (dialogConfig.getLlmSystemPrompt() != null && !dialogConfig.getLlmSystemPrompt().isEmpty()) {
          dialogDataSet.setLLMSystemPrompt(dialogConfig.getLlmSystemPrompt());
        }
      }
      
      // Create the main greeting dialog
      if (dialogConfig.getGreeting() != null && !dialogConfig.getGreeting().isEmpty()) {
        Set<DialogButtonEntry> buttons = new LinkedHashSet<>();
        
        // Create buttons from JSON config
        if (dialogConfig.getButtons() != null) {
          for (NPCTemplateData.DialogButton buttonConfig : dialogConfig.getButtons()) {
            DialogButtonEntry button = createButton(buttonConfig);
            if (button != null) {
              buttons.add(button);
            }
          }
        }
        
        // Create the main dialog entry with greeting text
        DialogDataEntry mainDialog = new DialogDataEntry(
            "main",           // label
            "Greeting",       // name (display name in editor)
            dialogConfig.getGreeting(),  // text
            buttons           // buttons
        );
        
        dialogDataSet.addDialog(mainDialog);
        log.info("{} Added main dialog with {} buttons", LOG_PREFIX, buttons.size());
      }
      
      // Create additional dialogs
      if (dialogConfig.getAdditionalDialogs() != null) {
        for (NPCTemplateData.AdditionalDialog addlDialog : dialogConfig.getAdditionalDialogs()) {
          if (addlDialog.getId() == null || addlDialog.getText() == null) {
            continue;
          }
          
          Set<DialogButtonEntry> buttons = new LinkedHashSet<>();
          
          // Create buttons for this dialog
          if (addlDialog.getButtons() != null) {
            for (NPCTemplateData.DialogButton buttonConfig : addlDialog.getButtons()) {
              DialogButtonEntry button = createButton(buttonConfig);
              if (button != null) {
                buttons.add(button);
              }
            }
          }
          
          // If no buttons defined, add a default "Back" button
          if (buttons.isEmpty()) {
            buttons.add(new DialogButtonEntry("Back", DialogButtonType.CLOSE));
          }
          
          DialogDataEntry additionalEntry = new DialogDataEntry(
              addlDialog.getId(),   // label
              addlDialog.getId(),   // name
              addlDialog.getText(), // text
              buttons               // buttons
          );
          
          dialogDataSet.addDialog(additionalEntry);
          log.debug("{} Added dialog '{}' with {} buttons", 
              LOG_PREFIX, addlDialog.getId(), buttons.size());
        }
      }
      
      // Apply the dialog data set to the NPC
      dialogCapable.setDialogDataSet(dialogDataSet);
      log.info("{} Applied dialog configuration with {} dialogs", 
          LOG_PREFIX, dialogDataSet.getDialogsByLabel().size());
      
    } catch (Exception e) {
      log.error("{} Failed to apply dialog configuration:", LOG_PREFIX, e);
    }
  }
  
  /**
   * Create a DialogButtonEntry from JSON button config.
   */
  private static DialogButtonEntry createButton(NPCTemplateData.DialogButton buttonConfig) {
    if (buttonConfig == null || buttonConfig.getLabel() == null) {
      return null;
    }
    
    String action = buttonConfig.getAction();
    DialogButtonType buttonType = DialogButtonType.DEFAULT;
    ActionDataSet actionDataSet = new ActionDataSet();
    
    if (action != null) {
      // Parse the action string
      if (action.equalsIgnoreCase("CLOSE_DIALOG") || action.equalsIgnoreCase("CLOSE")) {
        buttonType = DialogButtonType.CLOSE;
        // Add close dialog action
        actionDataSet.add(new ActionDataEntry(ActionDataType.CLOSE_DIALOG));
      } else if (action.equalsIgnoreCase("OPEN_TRADING")) {
        buttonType = DialogButtonType.DEFAULT;
        // Create action to open trading
        actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_TRADING_SCREEN));
      } else if (action.startsWith("SHOW_DIALOG:")) {
        // Navigate to another dialog
        String targetDialog = action.substring("SHOW_DIALOG:".length());
        buttonType = DialogButtonType.DEFAULT;
        // Use OPEN_NAMED_DIALOG with dialog label
        String dialogLabel = targetDialog.equalsIgnoreCase("root") || targetDialog.equalsIgnoreCase("main") ? "main" : targetDialog;
        actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_NAMED_DIALOG, dialogLabel));
      } else if (action.startsWith("OPEN_QUEST_DIALOG")) {
          buttonType = DialogButtonType.DEFAULT;
          if (action.contains(":")) {
             String arg = action.substring(action.indexOf(":") + 1).trim();
             if (arg.startsWith("RANDOM_POOL:")) {
                 // Store the pool string in the command field
                 actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_QUEST_DIALOG, arg));
             } else {
                 try {
                     UUID questId = UUID.fromString(arg);
                     actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_QUEST_DIALOG, questId, ""));
                 } catch (IllegalArgumentException e) {
                     log.warn("Invalid Quest UUID in action: {}", action);
                     actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_QUEST_DIALOG));
                 }
             }
          } else {
             actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_QUEST_DIALOG));
          }
      } else if (action.startsWith("COMMAND:") || action.startsWith("PLAYER_COMMAND:")) {
        // Execute a command (support multiple commands split by ';')
        boolean isPlayerCommand = action.startsWith("PLAYER_COMMAND:");
        String fullCommand = isPlayerCommand ? action.substring("PLAYER_COMMAND:".length()) : action.substring("COMMAND:".length());
        String[] commands = fullCommand.split(";");
        buttonType = DialogButtonType.ACTION;
        for (String cmd : commands) {
          String trimmedCmd = cmd.trim();
          if (trimmedCmd.isEmpty()) {
            continue;
          }
          if (trimmedCmd.equalsIgnoreCase("CLOSE") || trimmedCmd.equalsIgnoreCase("CLOSE_DIALOG")) {
            buttonType = DialogButtonType.CLOSE;
            actionDataSet.add(new ActionDataEntry(ActionDataType.CLOSE_DIALOG));
          } else {
            actionDataSet.add(new ActionDataEntry(ActionDataType.COMMAND, trimmedCmd, 2, isPlayerCommand));
          }
        }
      }
    }
    
    return new DialogButtonEntry(
        buttonConfig.getLabel(),  // name (display text)
        buttonConfig.getId(),     // label (auto-generated or explicit id)
        buttonType,
        actionDataSet
    );
  }
  
  /**
   * Create a DialogButtonEntry with label prefix for unique NPC dialog tracking.
   * This prefixes SHOW_DIALOG targets to ensure they reference the NPC's unique dialog labels.
   */
  private static DialogButtonEntry createButtonWithPrefix(NPCTemplateData.DialogButton buttonConfig, String labelPrefix) {
    if (buttonConfig == null || buttonConfig.getLabel() == null) {
      return null;
    }
    
    String action = buttonConfig.getAction();
    DialogButtonType buttonType = DialogButtonType.DEFAULT;
    ActionDataSet actionDataSet = new ActionDataSet();
    
    if (action != null) {
      // Parse the action string
      if (action.equalsIgnoreCase("CLOSE_DIALOG") || action.equalsIgnoreCase("CLOSE")) {
        buttonType = DialogButtonType.CLOSE;
        actionDataSet.add(new ActionDataEntry(ActionDataType.CLOSE_DIALOG));
      } else if (action.equalsIgnoreCase("OPEN_TRADING")) {
        buttonType = DialogButtonType.DEFAULT;
        actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_TRADING_SCREEN));
      } else if (action.startsWith("SHOW_DIALOG:")) {
        // Navigate to another dialog - apply prefix for unique tracking
        String targetDialog = action.substring("SHOW_DIALOG:".length());
        buttonType = DialogButtonType.DEFAULT;
        String dialogLabel = targetDialog.equalsIgnoreCase("root") || targetDialog.equalsIgnoreCase("main") ? "main" : targetDialog;
        // Apply prefix to target dialog label
        String prefixedDialogLabel = labelPrefix + dialogLabel;
        actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_NAMED_DIALOG, prefixedDialogLabel));
      } else if (action.startsWith("OPEN_QUEST_DIALOG")) {
          buttonType = DialogButtonType.DEFAULT;
          if (action.contains(":")) {
             String arg = action.substring(action.indexOf(":") + 1).trim();
             if (arg.startsWith("RANDOM_POOL:")) {
                 actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_QUEST_DIALOG, arg));
             } else {
                 try {
                     UUID questId = UUID.fromString(arg);
                     actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_QUEST_DIALOG, questId, ""));
                 } catch (IllegalArgumentException e) {
                     log.warn("Invalid Quest UUID in action: {}", action);
                     actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_QUEST_DIALOG));
                 }
             }
          } else {
             actionDataSet.add(new ActionDataEntry(ActionDataType.OPEN_QUEST_DIALOG));
          }
      } else if (action.startsWith("COMMAND:") || action.startsWith("PLAYER_COMMAND:")) {
        boolean isPlayerCommand = action.startsWith("PLAYER_COMMAND:");
        String fullCommand = isPlayerCommand ? action.substring("PLAYER_COMMAND:".length()) : action.substring("COMMAND:".length());
        String[] commands = fullCommand.split(";");
        buttonType = DialogButtonType.ACTION;
        for (String cmd : commands) {
          String trimmedCmd = cmd.trim();
          if (trimmedCmd.isEmpty()) {
            continue;
          }
          if (trimmedCmd.equalsIgnoreCase("CLOSE") || trimmedCmd.equalsIgnoreCase("CLOSE_DIALOG")) {
            buttonType = DialogButtonType.CLOSE;
            actionDataSet.add(new ActionDataEntry(ActionDataType.CLOSE_DIALOG));
          } else {
            actionDataSet.add(new ActionDataEntry(ActionDataType.COMMAND, trimmedCmd, 2, isPlayerCommand));
          }
        }
      }
    }
    
    return new DialogButtonEntry(
        buttonConfig.getLabel(),  // name (display text)
        buttonConfig.getId(),     // label (auto-generated or explicit id)
        buttonType,
        actionDataSet
    );
  }
  
  /**
   * Apply skin configuration to an NPC.
   */
  private static void applySkin(EasyNPC<?> npc, NPCTemplateData.SkinConfig skinConfig) {
    if (!(npc instanceof SkinDataCapable<?> skinCapable)) {
      log.warn("{} NPC does not support skin data", LOG_PREFIX);
      return;
    }
    
    String type = skinConfig.getType();
    if ("PLAYER_SKIN".equalsIgnoreCase(type) && skinConfig.getPlayerName() != null) {
      log.info("{} Applying player skin: {}", LOG_PREFIX, skinConfig.getPlayerName());
      skinCapable.setSkinDataEntry(SkinDataEntry.createPlayerSkin(skinConfig.getPlayerName(), Constants.BLANK_UUID));
    } else if ("URL_SKIN".equalsIgnoreCase(type) && skinConfig.getSkinUrl() != null) {
      log.info("{} Applying URL skin: {}", LOG_PREFIX, skinConfig.getSkinUrl());
      skinCapable.setSkinDataEntry(SkinDataEntry.createRemoteSkin(skinConfig.getSkinUrl()));
    } else if ("CUSTOM".equalsIgnoreCase(type) && skinConfig.getTextureId() != null) {
      log.info("{} Applying custom texture: {}", LOG_PREFIX, skinConfig.getTextureId());
      skinCapable.setSkinDataEntry(SkinDataEntry.createCustomSkin(UUID.nameUUIDFromBytes(skinConfig.getTextureId().getBytes()), false));
    } else {
      log.warn("{} Unknown or incomplete skin type: {}", LOG_PREFIX, type);
    }
  }
  
  /**
   * Apply attribute configuration to an NPC.
   */
  private static void applyAttributes(EasyNPC<?> npc, NPCTemplateData.AttributeConfig attributes) {
    Entity entity = npc.getEntity();
    
    // Apply invulnerability
    entity.setInvulnerable(attributes.isInvulnerable());
    
    // Apply combat attributes using AttributeHandler
    AttributeHandler.setCombatAttribute(npc, CombatAttributeType.IS_INVULNERABLE, attributes.isInvulnerable());
    AttributeHandler.setCombatAttribute(npc, CombatAttributeType.IS_ATTACKABLE_BY_PLAYERS, attributes.isAttackableByPlayers());
    AttributeHandler.setCombatAttribute(npc, CombatAttributeType.IS_ATTACKABLE_BY_MONSTERS, attributes.isAttackableByMonsters());
    
    // Apply other attributes via the entity's attribute system
    if (entity instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
      // Set max health
      var healthAttr = livingEntity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
      if (healthAttr != null && attributes.getMaxHealth() > 0) {
        healthAttr.setBaseValue(attributes.getMaxHealth());
        livingEntity.setHealth(attributes.getMaxHealth());
      }
      
      // Set movement speed
      var speedAttr = livingEntity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
      if (speedAttr != null && attributes.getMovementSpeed() >= 0) {
        speedAttr.setBaseValue(attributes.getMovementSpeed());
      }
      
      // Set attack damage
      var attackAttr = livingEntity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
      if (attackAttr != null && attributes.getAttackDamage() > 0) {
        attackAttr.setBaseValue(attributes.getAttackDamage());
      }
      
      // Set armor
      var armorAttr = livingEntity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
      if (armorAttr != null && attributes.getArmor() > 0) {
        armorAttr.setBaseValue(attributes.getArmor());
      }
    }
    
    log.debug("{} Applied attributes: health={}, speed={}, invulnerable={}, attackableByPlayers={}, attackableByMonsters={}", 
        LOG_PREFIX, attributes.getMaxHealth(), attributes.getMovementSpeed(), attributes.isInvulnerable(),
        attributes.isAttackableByPlayers(), attributes.isAttackableByMonsters());
  }
  
  /**
   * Export an existing NPC to a template.
   */
  public static boolean exportToTemplate(EasyNPC<?> npc, String templateName) {
    if (npc == null || templateName == null || templateName.isEmpty()) {
      return false;
    }
    
    try {
      NPCTemplateData template = new NPCTemplateData();
      
      // Export basic info
      Entity entity = npc.getEntity();
      template.setName(entity.hasCustomName() ? entity.getCustomName().getString() : "Exported NPC");
      template.setEntityType(EntityType.getKey(entity.getType()).toString());
      template.setDescription("Exported from in-game NPC");
      
      // Export attributes
      NPCTemplateData.AttributeConfig attributes = new NPCTemplateData.AttributeConfig();
      attributes.setInvulnerable(entity.isInvulnerable());
      if (entity instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
        attributes.setMaxHealth((int) livingEntity.getMaxHealth());
        var speedAttr = livingEntity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
          attributes.setMovementSpeed(speedAttr.getBaseValue());
        }
      }
      template.setAttributes(attributes);
      
      // Save template
      boolean success = NPCJsonConfig.saveTemplate(templateName, template);
      if (success) {
        log.info("{} Exported NPC {} to template '{}'", 
            LOG_PREFIX, npc.getEntityUUID(), templateName);
      }
      return success;
      
    } catch (Exception e) {
      log.error("{} Failed to export NPC to template:", LOG_PREFIX, e);
      return false;
    }
  }

  /**
   * Apply equipment configuration to an NPC.
   */
  private static void applyEquipment(EasyNPC<?> npc, NPCTemplateData.EquipmentConfig equipment) {
    Entity entity = npc.getEntity();
    if (!(entity instanceof net.minecraft.world.entity.Mob mob)) {
      return;
    }
    
    setEquipmentSlot(mob, EquipmentSlot.MAINHAND, equipment.getMainHand());
    setEquipmentSlot(mob, EquipmentSlot.OFFHAND, equipment.getOffHand());
    setEquipmentSlot(mob, EquipmentSlot.HEAD, equipment.getHead());
    setEquipmentSlot(mob, EquipmentSlot.CHEST, equipment.getChest());
    setEquipmentSlot(mob, EquipmentSlot.LEGS, equipment.getLegs());
    setEquipmentSlot(mob, EquipmentSlot.FEET, equipment.getFeet());
  }

  private static void applyTrading(EasyNPC<?> npc, NPCTemplateData.TradingConfig trading) {
    if (!(npc instanceof de.markusbordihn.easynpc.entity.easynpc.data.TradingDataCapable<?> tradingCapable)) {
      log.warn("{} NPC does not support trading data", LOG_PREFIX);
      return;
    }
    if (trading == null) {
      return;
    }

    de.markusbordihn.easynpc.data.trading.TradingDataSet tradingDataSet =
        new de.markusbordihn.easynpc.data.trading.TradingDataSet();
    de.markusbordihn.easynpc.data.trading.TradingType tradingType =
        de.markusbordihn.easynpc.data.trading.TradingType.get(trading.getType());
    tradingDataSet.setType(tradingType);
    tradingDataSet.setMaxUses(trading.getMaxUses());
    tradingDataSet.setRewardedXP(trading.getRewardedXP());
    tradingDataSet.setResetsEveryMin(trading.getResetsEveryMin());
    tradingCapable.setTradingDataSet(tradingDataSet);

    net.minecraft.world.item.trading.MerchantOffers offers =
        new net.minecraft.world.item.trading.MerchantOffers();

    if (trading.isRandomizeOffers()
        && trading.getBuyList() != null
        && trading.getSellList() != null
        && trading.getBuyList().length > 0
        && trading.getSellList().length > 0) {
      int maxPairs = Math.min(trading.getBuyList().length, trading.getSellList().length);
      int targetCount = trading.getRandomOfferCount() > 0
          ? Math.min(trading.getRandomOfferCount(), maxPairs)
          : maxPairs;
      java.util.List<Integer> indices = new java.util.ArrayList<>();
      for (int i = 0; i < maxPairs; i++) {
        indices.add(i);
      }
      java.util.Collections.shuffle(indices, new java.util.Random());
      for (int i = 0; i < targetCount; i++) {
        int index = indices.get(i);
        NPCTemplateData.ItemStack buyConfig = trading.getBuyList()[index];
        NPCTemplateData.ItemStack sellConfig = trading.getSellList()[index];
        net.minecraft.world.item.ItemStack buy = createItemStackFromConfig(buyConfig);
        net.minecraft.world.item.ItemStack sell = createItemStackFromConfig(sellConfig);
        if (buy.isEmpty() || sell.isEmpty()) {
          continue;
        }
        offers.add(new net.minecraft.world.item.trading.MerchantOffer(
            new net.minecraft.world.item.trading.ItemCost(buy.getItem(), buy.getCount()),
            java.util.Optional.empty(),
            sell,
            trading.getMaxUses(),
            trading.getRewardedXP(),
            0.0F));
      }
    } else if (trading.getBuyList() != null
        && trading.getSellList() != null
        && trading.getBuyList().length > 0
        && trading.getSellList().length > 0) {
      int pairCount = Math.min(trading.getBuyList().length, trading.getSellList().length);
      for (int i = 0; i < pairCount; i++) {
        net.minecraft.world.item.ItemStack buy = createItemStackFromConfig(trading.getBuyList()[i]);
        net.minecraft.world.item.ItemStack sell = createItemStackFromConfig(trading.getSellList()[i]);
        if (buy.isEmpty() || sell.isEmpty()) {
          continue;
        }
        offers.add(new net.minecraft.world.item.trading.MerchantOffer(
            new net.minecraft.world.item.trading.ItemCost(buy.getItem(), buy.getCount()),
            java.util.Optional.empty(),
            sell,
            trading.getMaxUses(),
            trading.getRewardedXP(),
            0.0F));
      }
    } else if (trading.getOffers() != null) {
      for (NPCTemplateData.TradeOffer offer : trading.getOffers()) {
        if (offer == null) {
          continue;
        }
        net.minecraft.world.item.ItemStack buy = createItemStackFromConfig(offer.getBuy());
        net.minecraft.world.item.ItemStack buyExtra = createItemStackFromConfig(offer.getBuyExtra());
        net.minecraft.world.item.ItemStack sell = createItemStackFromConfig(offer.getSell());
        if (buy.isEmpty() || sell.isEmpty()) {
          continue;
        }
        int maxUses = offer.getMaxUses() > 0 ? offer.getMaxUses() : trading.getMaxUses();
        int xpReward = offer.getXpReward() > 0 ? offer.getXpReward() : trading.getRewardedXP();
        java.util.Optional<net.minecraft.world.item.trading.ItemCost> costB =
            buyExtra.isEmpty()
                ? java.util.Optional.empty()
                : java.util.Optional.of(
                    new net.minecraft.world.item.trading.ItemCost(
                        buyExtra.getItem(), buyExtra.getCount()));
        offers.add(new net.minecraft.world.item.trading.MerchantOffer(
            new net.minecraft.world.item.trading.ItemCost(buy.getItem(), buy.getCount()),
            costB,
            sell,
            maxUses,
            xpReward,
            0.0F));
      }
    }

    if (!offers.isEmpty()) {
      tradingCapable.setTradingOffers(offers);
    }
  }

  private static net.minecraft.world.item.ItemStack createItemStackFromConfig(
      NPCTemplateData.ItemStack itemConfig) {
    if (itemConfig == null || itemConfig.getItem() == null || itemConfig.getItem().isEmpty()) {
      return net.minecraft.world.item.ItemStack.EMPTY;
    }
    try {
      ResourceLocation location = ResourceLocation.parse(itemConfig.getItem());
      net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(location);
      if (item == null || item == net.minecraft.world.item.Items.AIR) {
        return net.minecraft.world.item.ItemStack.EMPTY;
      }
      net.minecraft.world.item.ItemStack itemStack =
          new net.minecraft.world.item.ItemStack(item, itemConfig.getCount() > 0 ? itemConfig.getCount() : 1);
      if (itemConfig.getNbt() != null && !itemConfig.getNbt().isEmpty()) {
        try {
          net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(itemConfig.getNbt());
          itemStack.set(
              net.minecraft.core.component.DataComponents.CUSTOM_DATA,
              net.minecraft.world.item.component.CustomData.of(tag));
        } catch (Exception e) {
          log.error("{} Failed to parse/apply NBT for item {}: {}", LOG_PREFIX, itemConfig.getItem(), e.getMessage());
        }
      }
      return itemStack;
    } catch (Exception e) {
      log.error("{} Failed to parse item {}: {}", LOG_PREFIX, itemConfig.getItem(), e.getMessage());
      return net.minecraft.world.item.ItemStack.EMPTY;
    }
  }
  
  private static void setEquipmentSlot(net.minecraft.world.entity.Mob mob, EquipmentSlot slot, NPCTemplateData.ItemStack itemConfig) {
    if (itemConfig == null || itemConfig.getItem() == null || itemConfig.getItem().isEmpty()) {
      return;
    }
    
    try {
      ResourceLocation location = ResourceLocation.parse(itemConfig.getItem());
      net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(location);
      if (item != null && item != net.minecraft.world.item.Items.AIR) {
        ItemStack itemStack = new ItemStack(item, itemConfig.getCount() > 0 ? itemConfig.getCount() : 1);
        
        // Apply NBT to CustomData component
        if (itemConfig.getNbt() != null && !itemConfig.getNbt().isEmpty()) {
          try {
            net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(itemConfig.getNbt());
            itemStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
          } catch (Exception e) {
             log.error("{} Failed to parse/apply NBT for item {}: {}", LOG_PREFIX, itemConfig.getItem(), e.getMessage());
          }
        }

        mob.setItemSlot(slot, itemStack);
        mob.setDropChance(slot, 0.0f); // Default to no drop for template NPCs
        log.info("{} Equipped {} to {}", LOG_PREFIX, itemConfig.getItem(), slot);
      } else {
        log.warn("{} Item not found: {}", LOG_PREFIX, itemConfig.getItem());
      }
    } catch (Exception e) {
      log.error("{} Failed to set equipment slot {}:", LOG_PREFIX, slot, e);
    }
  }

  /**
   * Apply objective configuration to an NPC.
   */
  private static void applyObjectives(EasyNPC<?> npc, NPCTemplateData.ObjectiveConfig objectives) {
    if (!(npc instanceof ObjectiveDataCapable<?> objectiveCapable)) {
       log.warn("{} NPC does not support objective data", LOG_PREFIX);
       return;
    }
    
    // Standard objectives
    if (objectives.isAttackHostileMobs()) {
      objectiveCapable.addOrUpdateCustomObjective(new ObjectiveDataEntry(ObjectiveType.ATTACK_MONSTER));
    }
    if (objectives.isAttackPlayers()) {
      objectiveCapable.addOrUpdateCustomObjective(new ObjectiveDataEntry(ObjectiveType.ATTACK_PLAYER));
    }
    if (objectives.isReturnToSpawn()) {
      objectiveCapable.addOrUpdateCustomObjective(new ObjectiveDataEntry(ObjectiveType.MOVE_BACK_TO_HOME));
    }
    if (objectives.isFollowOwner()) {
      objectiveCapable.addOrUpdateCustomObjective(new ObjectiveDataEntry(ObjectiveType.FOLLOW_OWNER));
    }
    
    // Random Stroll
    if (objectives.getWanderRange() > 0) {
      objectiveCapable.addOrUpdateCustomObjective(new ObjectiveDataEntry(ObjectiveType.RANDOM_STROLL));
    }
    
    // Custom Attack
    if (objectives.isUseCustomAttack()) {
       ObjectiveDataEntry customAttack = new ObjectiveDataEntry(ObjectiveType.CUSTOM_ATTACK);
       customAttack.setAttackRadius((float) objectives.getAttackRadius());
       // Use our new Full Auto field!
       customAttack.setFullAuto(objectives.isFullAuto());
       objectiveCapable.addOrUpdateCustomObjective(customAttack);
       log.info("{} Applied Custom Attack objective: fullAuto={}, radius={}", LOG_PREFIX, objectives.isFullAuto(), objectives.getAttackRadius());
    }
    
    // Faction-based targeting
    if (objectives.isAttackHostileFactions() && objectives.getHostileFactions() != null && objectives.getHostileFactions().length > 0) {
      ObjectiveDataEntry factionAttack = new ObjectiveDataEntry(ObjectiveType.ATTACK_HOSTILE_FACTION);
      factionAttack.setHostileFactions(java.util.Set.of(objectives.getHostileFactions()));
      objectiveCapable.addOrUpdateCustomObjective(factionAttack);
      log.info("{} Applied Faction Attack objective: hostileFactions={}", LOG_PREFIX, java.util.Arrays.toString(objectives.getHostileFactions()));
    }
  }

  /**
   * Apply drop configuration to an NPC.
   * Stores the configuration in the entity's persistent data (NBT).
   */
  /**
   * Apply drop configuration to an NPC.
   * Stores the configuration in the entity's tags.
   * Format: easynpc_drop|itemId|count|chance|playerKillOnly
   */
  private static void applyDrops(EasyNPC<?> npc, NPCTemplateData.DropConfig dropConfig) {
    if (dropConfig == null || dropConfig.getItem() == null || dropConfig.getItem().getItem() == null) {
      return;
    }
    
    Entity entity = npc.getEntity();
    String configString = String.format("easynpc_drop|%s|%d|%f|%b",
        dropConfig.getItem().getItem(),
        dropConfig.getItem().getCount(),
        dropConfig.getChance(),
        dropConfig.isPlayerKillOnly());
        
    entity.addTag(configString);
    log.info("{} Applied drop configuration tag: {}", LOG_PREFIX, configString);
  }
}
