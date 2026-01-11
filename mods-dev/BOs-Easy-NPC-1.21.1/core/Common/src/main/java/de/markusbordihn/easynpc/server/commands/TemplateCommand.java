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
import de.markusbordihn.easynpc.item.configuration.SpawnRectWandItem;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
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
        .then(registerSpawnAll())
        .then(registerSpawnRect())
        .then(registerSpawnWand())
        .then(registerSpawnTimers())
        .then(registerStopSpawnRect())
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
   * Register the spawn_all subcommand.
   */
  private static ArgumentBuilder<CommandSourceStack, ?> registerSpawnAll() {
    return Commands.literal("spawn_all")
        .executes(TemplateCommand::executeSpawnAll);
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
    Set<String> templates = new java.util.LinkedHashSet<>();
    templates.add("all");
    templates.add("spawn_all");
    templates.addAll(NPCTemplateManager.getTemplateNames());
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
   * Execute the spawn_all command.
   */
  private static int executeSpawnAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    CommandSourceStack source = context.getSource();
    ServerPlayer player = source.getPlayerOrException();
    Set<String> templates = NPCTemplateManager.getTemplateNames();

    if (templates.isEmpty()) {
      source.sendFailure(Component.literal("§cNo templates found to spawn."));
      return 0;
    }

    int spawnedCount = 0;
    java.util.Random random = new java.util.Random();
    net.minecraft.server.level.ServerLevel level = player.serverLevel();

    for (String templateName : templates) {
      // Calculate random position in 15-80 block radius
      double angle = random.nextDouble() * 2 * Math.PI;
      double distance = 15 + random.nextDouble() * 65; // 15 to 80
      
      double x = player.getX() + distance * Math.cos(angle);
      double z = player.getZ() + distance * Math.sin(angle);
      
      // Find ground level
      int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)x, (int)z);
      
      if (NPCTemplateManager.spawnFromTemplate(level, templateName, x, y, z)) {
        spawnedCount++;
      } else {
        log.warn("{} Failed to spawn NPC from template: {}", LOG_PREFIX, templateName);
      }
    }

    int finalCount = spawnedCount;
    source.sendSuccess(() -> Component.literal("§aSpawned " + finalCount + " NPCs from templates within 15-80 blocks."), true);
    log.info("{} {} spawned all {} NPCs from templates", LOG_PREFIX, player.getName().getString(), spawnedCount);

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
  /**
   * Register the spawn_rect subcommand.
   */
  private static ArgumentBuilder<CommandSourceStack, ?> registerSpawnRect() {
    return Commands.literal("spawn_rect")
        .then(Commands.argument("template_name", StringArgumentType.word())
            .suggests(TemplateCommand::suggestTemplateNames)
            .then(Commands.argument("pos1", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                .then(Commands.argument("pos2", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                    .then(Commands.argument("max_count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                        .then(Commands.argument("delay_ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .executes(TemplateCommand::executeSpawnRect))))));
  }

  /**
   * Execute the spawn_rect command.
   */
  private static int executeSpawnRect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    CommandSourceStack source = context.getSource();
    String templateName = StringArgumentType.getString(context, "template_name");
    net.minecraft.core.BlockPos pos1 = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(context, "pos1");
    net.minecraft.core.BlockPos pos2 = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(context, "pos2");
    int maxCount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "max_count");
    int delayTicks = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "delay_ticks");

    // Check if template exists
    if (!NPCTemplateManager.hasTemplate(templateName)) {
      source.sendFailure(Component.literal("§cTemplate not found: " + templateName));
      return 0;
    }

    try {
      ServerPlayer player = source.getPlayerOrException();
      de.markusbordihn.easynpc.handler.SpawningHandler.addSpawningTask(
          templateName, player.serverLevel(), pos1, pos2, maxCount, delayTicks);
      
      source.sendSuccess(() -> Component.literal(
          String.format("§aStarted spawning %d %s NPCs every %d ticks within the defined area.", 
              maxCount, templateName, delayTicks)), true);
      log.info("{} {} started rect spawn for template '{}' (count: {}, delay: {})", 
          LOG_PREFIX, player.getName().getString(), templateName, maxCount, delayTicks);
    } catch (Exception e) {
      source.sendFailure(Component.literal("§cFailed to start spawning: " + e.getMessage()));
      return 0;
    }

    return Command.SINGLE_SUCCESS;
  }

  /**
   * Register the spawn_wand subcommand - uses positions from held Spawn Rect Wand.
   */
  private static ArgumentBuilder<CommandSourceStack, ?> registerSpawnWand() {
    return Commands.literal("spawn_wand")
        .then(Commands.argument("template_name", StringArgumentType.word())
            .suggests(TemplateCommand::suggestTemplateNames)
            .then(Commands.argument("max_count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                .then(Commands.argument("delay_ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                    .executes(ctx -> executeSpawnWandWithGroup(ctx, false))
                    .then(Commands.literal("group")
                        .executes(ctx -> executeSpawnWandWithGroup(ctx, true))))));
  }

  /**
   * Execute the spawn_wand command - reads pos1/pos2 from held wand.
   */
  private static int executeSpawnWandWithGroup(CommandContext<CommandSourceStack> context, boolean groupSpawn) throws CommandSyntaxException {
    CommandSourceStack source = context.getSource();
    ServerPlayer player = source.getPlayerOrException();
    String templateName = StringArgumentType.getString(context, "template_name");
    int maxCount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "max_count");
    int delayTicks = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "delay_ticks");
    boolean spawnAll = "all".equalsIgnoreCase(templateName) || "spawn_all".equalsIgnoreCase(templateName);

    // Check if template exists
    if (!spawnAll && !NPCTemplateManager.hasTemplate(templateName)) {
      source.sendFailure(Component.literal("§cTemplate not found: " + templateName));
      return 0;
    }

    // Get held item
    ItemStack mainHand = player.getMainHandItem();
    ItemStack offHand = player.getOffhandItem();
    ItemStack wandStack = null;
    
    if (mainHand.getItem() instanceof SpawnRectWandItem) {
      wandStack = mainHand;
    } else if (offHand.getItem() instanceof SpawnRectWandItem) {
      wandStack = offHand;
    }
    
    if (wandStack == null) {
      source.sendFailure(Component.literal("§cYou must be holding a Spawn Rect Wand!"));
      return 0;
    }

    BlockPos pos1 = SpawnRectWandItem.getPos1(wandStack);
    BlockPos pos2 = SpawnRectWandItem.getPos2(wandStack);
    
    if (pos1 == null || pos2 == null) {
      source.sendFailure(Component.literal("§cBoth corners must be set on the wand!"));
      source.sendFailure(Component.literal("§7Right-click block = corner 1, Shift+Right-click = corner 2"));
      return 0;
    }

    try {
      if (spawnAll) {
        Set<String> templates = NPCTemplateManager.getTemplateNames();
        if (templates.isEmpty()) {
          source.sendFailure(Component.literal("§cNo templates found to spawn."));
          return 0;
        }
        int spawnedTasks = 0;
        for (String name : templates) {
          de.markusbordihn.easynpc.handler.SpawningHandler.addSpawningTask(
              name, player.serverLevel(), pos1, pos2, maxCount, delayTicks, groupSpawn);
          spawnedTasks++;
        }

        String modeStr = groupSpawn ? " (GROUP MODE)" : "";
        int finalCount = spawnedTasks;
        source.sendSuccess(() -> Component.literal(
            String.format("§aStarted spawning %d NPC template(s) every %d ticks%s in area (%d,%d,%d) to (%d,%d,%d)",
                finalCount, delayTicks, modeStr,
                pos1.getX(), pos1.getY(), pos1.getZ(),
                pos2.getX(), pos2.getY(), pos2.getZ())), true);
        log.info("{} {} started wand spawn for all templates (count: {}, delay: {}, group: {})",
            LOG_PREFIX, player.getName().getString(), maxCount, delayTicks, groupSpawn);
      } else {
        de.markusbordihn.easynpc.handler.SpawningHandler.addSpawningTask(
            templateName, player.serverLevel(), pos1, pos2, maxCount, delayTicks, groupSpawn);

        String modeStr = groupSpawn ? " (GROUP MODE)" : "";
        source.sendSuccess(() -> Component.literal(
            String.format("§aStarted spawning %d %s NPCs every %d ticks%s in area (%d,%d,%d) to (%d,%d,%d)", 
                maxCount, templateName, delayTicks, modeStr,
                pos1.getX(), pos1.getY(), pos1.getZ(),
                pos2.getX(), pos2.getY(), pos2.getZ())), true);
        log.info("{} {} started wand spawn for template '{}' (count: {}, delay: {}, group: {})",
            LOG_PREFIX, player.getName().getString(), templateName, maxCount, delayTicks, groupSpawn);
      }
    } catch (Exception e) {
      source.sendFailure(Component.literal("§cFailed to start spawning: " + e.getMessage()));
      return 0;
    }

    return Command.SINGLE_SUCCESS;
  }

  /**
   * Register the stop_rect_spawn subcommand.
   */
  private static ArgumentBuilder<CommandSourceStack, ?> registerStopSpawnRect() {
    return Commands.literal("stop_rect_spawn")
        .executes(TemplateCommand::executeStopSpawnRect);
  }

  /**
   * Execute the stop_rect_spawn command.
   */
  private static int executeStopSpawnRect(CommandContext<CommandSourceStack> context) {
    int count = de.markusbordihn.easynpc.handler.SpawningHandler.stopSpawningTasks();
    context.getSource().sendSuccess(() -> Component.literal("§eStopped " + count + " active spawning tasks."), true);
    return Command.SINGLE_SUCCESS;
  }

  /**
   * Register the spawn_timers subcommand - no permission required so all players can use it.
   */
  private static ArgumentBuilder<CommandSourceStack, ?> registerSpawnTimers() {
    return Commands.literal("spawn_timers")
        .requires(source -> source.hasPermission(0)) // Anyone can use this
        .executes(TemplateCommand::executeSpawnTimers);
  }

  /**
   * Execute the spawn_timers command - shows current spawn timer status.
   */
  private static int executeSpawnTimers(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    java.util.List<Object[]> timers = de.markusbordihn.easynpc.handler.SpawningHandler.getTimerInfo();
    
    if (timers.isEmpty()) {
      source.sendSuccess(() -> Component.literal("§7No active spawn tasks."), false);
      return Command.SINGLE_SUCCESS;
    }
    
    source.sendSuccess(() -> Component.literal("§6=== Spawn Timers ==="), false);
    for (Object[] timer : timers) {
      String templateName = (String) timer[0];
      int ticksRemaining = (int) timer[1];
      int totalTicks = (int) timer[2];
      boolean isGroupSpawn = (boolean) timer[3];
      
      float secondsRemaining = ticksRemaining / 20.0f;
      float totalSeconds = totalTicks / 20.0f;
      String modeStr = isGroupSpawn ? "§c[GROUP]§r " : "§a[MAINTAIN]§r ";
      
      source.sendSuccess(() -> Component.literal(
          String.format("%s%s: §e%.1fs §7/ %.1fs remaining", 
              modeStr, templateName, secondsRemaining, totalSeconds)), false);
    }
    
    source.sendSuccess(() -> Component.literal("§7Tip: Use client key N to toggle HUD overlay"), false);
    return Command.SINGLE_SUCCESS;
  }
}
