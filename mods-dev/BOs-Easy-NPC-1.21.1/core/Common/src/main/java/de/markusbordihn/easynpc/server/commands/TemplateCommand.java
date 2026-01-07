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

package de.markusbordihn.easynpc.server.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.config.NPCJsonConfig;
import de.markusbordihn.easynpc.config.NPCTemplateManager;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Commands for managing NPC templates.
 * 
 * Usage:
 * - /easy_npc template list - List all available templates
 * - /easy_npc template reload - Reload templates from disk
 * - /easy_npc template spawn <template_name> - Spawn NPC from template
 * - /easy_npc template export <entity> <template_name> - Export NPC to template
 */
public class TemplateCommand {
  
  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[TemplateCommand]";
  
  private TemplateCommand() {}
  
  /**
   * Register the template command.
   */
  public static ArgumentBuilder<CommandSourceStack, ?> register() {
    return Commands.literal("template")
        .requires(source -> source.hasPermission(2))
        .then(registerList())
        .then(registerReload())
        .then(registerSpawn())
        .then(registerExport());
  }
  
  /**
   * Register the list subcommand.
   */
  private static ArgumentBuilder<CommandSourceStack, ?> registerList() {
    return Commands.literal("list")
        .executes(TemplateCommand::executeList);
  }
  
  /**
   * Register the reload subcommand.
   */
  private static ArgumentBuilder<CommandSourceStack, ?> registerReload() {
    return Commands.literal("reload")
        .executes(TemplateCommand::executeReload);
  }
  
  /**
   * Register the spawn subcommand.
   */
  private static ArgumentBuilder<CommandSourceStack, ?> registerSpawn() {
    return Commands.literal("spawn")
        .then(Commands.argument("template_name", StringArgumentType.word())
            .suggests(TemplateCommand::suggestTemplateNames)
            .executes(TemplateCommand::executeSpawn));
  }
  
  /**
   * Register the export subcommand.
   */
  private static ArgumentBuilder<CommandSourceStack, ?> registerExport() {
    return Commands.literal("export")
        .then(Commands.argument("target", EntityArgument.entity())
            .then(Commands.argument("template_name", StringArgumentType.word())
                .executes(TemplateCommand::executeExport)));
  }
  
  /**
   * Suggest template names for tab completion.
   */
  private static CompletableFuture<Suggestions> suggestTemplateNames(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    Set<String> templates = NPCTemplateManager.getTemplateNames();
    return SharedSuggestionProvider.suggest(templates, builder);
  }
  
  /**
   * Execute the list command.
   */
  private static int executeList(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    Set<String> templates = NPCTemplateManager.getTemplateNames();
    
    if (templates.isEmpty()) {
      source.sendSuccess(() -> Component.literal("§eNo NPC templates found."), false);
      source.sendSuccess(() -> Component.literal("§7Templates are loaded from: config/easy_npc/npc_templates/"), false);
    } else {
      source.sendSuccess(() -> Component.literal("§aAvailable NPC templates (" + templates.size() + "):"), false);
      for (String template : templates) {
        source.sendSuccess(() -> Component.literal("§7 - §f" + template), false);
      }
    }
    
    return Command.SINGLE_SUCCESS;
  }
  
  /**
   * Execute the reload command.
   */
  private static int executeReload(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    
    try {
      NPCJsonConfig.loadGlobalSettings();
      NPCTemplateManager.reloadTemplates();
      
      int count = NPCTemplateManager.getTemplateNames().size();
      source.sendSuccess(() -> Component.literal("§aReloaded " + count + " NPC template(s) and global settings."), true);
      log.info("{} Templates reloaded by {}", LOG_PREFIX, source.getTextName());
      
    } catch (Exception e) {
      source.sendFailure(Component.literal("§cFailed to reload templates: " + e.getMessage()));
      log.error("{} Failed to reload templates:", LOG_PREFIX, e);
      return 0;
    }
    
    return Command.SINGLE_SUCCESS;
  }
  
  /**
   * Execute the spawn command.
   */
  private static int executeSpawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    CommandSourceStack source = context.getSource();
    String templateName = StringArgumentType.getString(context, "template_name");
    
    // Check if player
    ServerPlayer player = source.getPlayerOrException();
    
    // Check if template exists
    if (!NPCTemplateManager.hasTemplate(templateName)) {
      source.sendFailure(Component.literal("§cTemplate not found: " + templateName));
      source.sendFailure(Component.literal("§7Use /easy_npc template list to see available templates."));
      return 0;
    }
    
    // Spawn NPC
    boolean success = NPCTemplateManager.spawnFromTemplate(player, templateName);
    
    if (success) {
      source.sendSuccess(() -> Component.literal("§aSpawned NPC from template: §f" + templateName), true);
      log.info("{} {} spawned NPC from template '{}'", LOG_PREFIX, player.getName().getString(), templateName);
    } else {
      source.sendFailure(Component.literal("§cFailed to spawn NPC from template: " + templateName));
      return 0;
    }
    
    return Command.SINGLE_SUCCESS;
  }
  
  /**
   * Execute the export command.
   */
  private static int executeExport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    CommandSourceStack source = context.getSource();
    Entity target = EntityArgument.getEntity(context, "target");
    String templateName = StringArgumentType.getString(context, "template_name");
    
    // Validate template name
    if (templateName.isEmpty() || templateName.contains("/") || templateName.contains("\\")) {
      source.sendFailure(Component.literal("§cInvalid template name: " + templateName));
      return 0;
    }
    
    // Check if target is an EasyNPC
    if (!(target instanceof EasyNPC<?>)) {
      source.sendFailure(Component.literal("§cTarget entity is not an Easy NPC."));
      return 0;
    }
    
    EasyNPC<?> npc = (EasyNPC<?>) target;
    
    // Export NPC
    boolean success = NPCTemplateManager.exportToTemplate(npc, templateName);
    
    if (success) {
      source.sendSuccess(() -> Component.literal("§aExported NPC to template: §f" + templateName), true);
      source.sendSuccess(() -> Component.literal("§7Template saved to: config/easy_npc/npc_templates/" + templateName + ".json"), false);
      log.info("{} {} exported NPC {} to template '{}'", 
          LOG_PREFIX, source.getTextName(), npc.getEntityUUID(), templateName);
    } else {
      source.sendFailure(Component.literal("§cFailed to export NPC to template: " + templateName));
      return 0;
    }
    
    return Command.SINGLE_SUCCESS;
  }
}
