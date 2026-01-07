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
import de.markusbordihn.easynpc.data.action.ActionDataSet;
import de.markusbordihn.easynpc.data.action.ActionDataType;
import de.markusbordihn.easynpc.data.dialog.DialogButtonEntry;
import de.markusbordihn.easynpc.data.dialog.DialogButtonType;
import de.markusbordihn.easynpc.data.dialog.DialogDataEntry;
import de.markusbordihn.easynpc.data.dialog.DialogDataSet;
import de.markusbordihn.easynpc.data.dialog.DialogType;
import de.markusbordihn.easynpc.data.skin.SkinDataEntry;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.SkinDataCapable;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
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
    log.info("{} Reloaded {} templates", LOG_PREFIX, getTemplateNames().size());
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
   * Spawn an NPC from a template data object.
   */
  public static boolean spawnFromTemplate(ServerLevel level, NPCTemplateData template, double x, double y, double z) {
    if (template == null) {
      log.warn("{} Cannot spawn from null template", LOG_PREFIX);
      return false;
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
        return false;
      }
      
      EntityType<?> entityType = entityTypeOpt.get();
      
      // Spawn the entity
      Entity entity = entityType.spawn(level, BlockPos.containing(x, y, z), MobSpawnType.COMMAND);
      if (entity == null) {
        log.error("{} Failed to spawn entity of type: {}", LOG_PREFIX, entityTypeStr);
        return false;
      }
      
      // Apply template configuration to the NPC
      if (entity instanceof EasyNPC<?> easyNPC) {
        applyTemplateToNPC(easyNPC, template);
        log.info("{} Spawned NPC '{}' from template at ({}, {}, {})", 
            LOG_PREFIX, template.getName(), x, y, z);
        return true;
      } else {
        log.warn("{} Spawned entity is not an EasyNPC: {}", LOG_PREFIX, entity.getClass().getName());
        return true; // Entity was still spawned
      }
      
    } catch (Exception e) {
      log.error("{} Failed to spawn NPC from template:", LOG_PREFIX, e);
      return false;
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
    if (template.getDialog() != null) {
      log.info("{} Template has dialog config, applying...", LOG_PREFIX);
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
    
    log.info("{} Finished applying template '{}' to NPC {}", 
        LOG_PREFIX, template.getName(), npc.getEntityUUID());
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
      } else if (action.startsWith("COMMAND:")) {
        // Execute a command
        String command = action.substring("COMMAND:".length());
        buttonType = DialogButtonType.ACTION;
        actionDataSet.add(new ActionDataEntry(ActionDataType.COMMAND, command, 2, true));
      }
    }
    
    return new DialogButtonEntry(
        buttonConfig.getLabel(),  // name (display text)
        null,                     // label (auto-generated)
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
    
    log.debug("{} Applied attributes: health={}, speed={}, invulnerable={}", 
        LOG_PREFIX, attributes.getMaxHealth(), attributes.getMovementSpeed(), attributes.isInvulnerable());
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
}

