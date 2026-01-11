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

package de.markusbordihn.easynpc.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.data.crime.PlayerLawState;
import de.markusbordihn.easynpc.handler.CrimeHandler;
import de.markusbordihn.easynpc.handler.GuardResponseHandler;
import de.markusbordihn.easynpc.handler.LawSystemHandler;
import de.markusbordihn.easynpc.network.components.TextComponent;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Commands for the law enforcement system.
 */
public class LawCommand extends Command {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[LawCommand]";

  private LawCommand() {}

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    LiteralArgumentBuilder<CommandSourceStack> lawCommand = Commands.literal("law")
        .requires(source -> source.hasPermission(2)); // Require OP level 2

    // /law admin - open admin GUI
    lawCommand.then(Commands.literal("admin")
        .executes(context -> openAdminGui(context.getSource())));

    // /law toggle - toggle system on/off
    lawCommand.then(Commands.literal("toggle")
        .executes(context -> toggleSystem(context.getSource())));

    // /law status [player] - show status
    lawCommand.then(Commands.literal("status")
        .executes(context -> showStatus(context.getSource(), null))
        .then(Commands.argument("player", EntityArgument.player())
            .executes(context -> showStatus(context.getSource(), 
                EntityArgument.getPlayer(context, "player")))));

    // /law set <player> wanted <level>
    lawCommand.then(Commands.literal("set")
        .then(Commands.argument("player", EntityArgument.player())
            .then(Commands.literal("wanted")
                .then(Commands.argument("level", IntegerArgumentType.integer(0, 10))
                    .executes(context -> setWantedLevel(
                        context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        IntegerArgumentType.getInteger(context, "level")))))
            .then(Commands.literal("peace")
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                    .executes(context -> setPeaceValue(
                        context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        IntegerArgumentType.getInteger(context, "value")))))));

    // /law clear <player> - clear crimes
    lawCommand.then(Commands.literal("clear")
        .then(Commands.argument("player", EntityArgument.player())
            .executes(context -> clearCrimes(
                context.getSource(),
                EntityArgument.getPlayer(context, "player")))));

    // /law clearall - clear all wanted
    lawCommand.then(Commands.literal("clearall")
        .executes(context -> clearAllWanted(context.getSource())));

    // /law reload - reload config
    lawCommand.then(Commands.literal("reload")
        .executes(context -> reloadConfig(context.getSource())));

    // /law immunity <player> - toggle crime immunity
    lawCommand.then(Commands.literal("immunity")
        .then(Commands.argument("player", EntityArgument.player())
            .executes(context -> toggleImmunity(
                context.getSource(),
                EntityArgument.getPlayer(context, "player")))));

    // /law spawn patrol <tier>
    lawCommand.then(Commands.literal("spawn")
        .then(Commands.literal("patrol")
            .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                .executes(context -> spawnPatrol(
                    context.getSource(),
                    IntegerArgumentType.getInteger(context, "tier")))))
        .then(Commands.literal("pursuit")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                    .executes(context -> spawnPursuit(
                        context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        IntegerArgumentType.getInteger(context, "tier")))))));

    // /law stats - show system stats
    lawCommand.then(Commands.literal("stats")
        .executes(context -> showStats(context.getSource())));

    // /law debug - toggle client overlay debug mode (sends sync message)
    lawCommand.then(Commands.literal("debug")
        .executes(context -> toggleDebugOverlay(context.getSource())));

    // /law sync - force sync to all players
    lawCommand.then(Commands.literal("sync")
        .executes(context -> syncAllPlayers(context.getSource())));

    dispatcher.register(lawCommand);
    log.info("{} Registered /law commands", LOG_PREFIX);
  }

  private static int openAdminGui(CommandSourceStack source) {
    if (source.getPlayer() == null) {
      return sendFailureMessage(source, "This command can only be run by a player");
    }
    
    ServerPlayer player = source.getPlayer();
    LawSystemHandler.getInstance().syncPlayerState(player);
    LawSystemHandler.getInstance().sendAdminData(player, true);
    
    return SINGLE_SUCCESS;
  }

  private static int toggleDebugOverlay(CommandSourceStack source) {
    if (source.getPlayer() == null) {
      return sendFailureMessage(source, "This command can only be run by a player");
    }
    
    ServerPlayer player = source.getPlayer();
    
    // Toggle by setting wanted to 2 for testing, or clearing
    PlayerLawState state = LawSystemHandler.getInstance().getOrCreatePlayerState(player.getUUID());
    if (state.getWantedLevel() == 0) {
      state.setWantedLevel(2);
      state.setPeaceValue(75);
      LawSystemHandler.getInstance().syncPlayerState(player);
      return sendSuccessMessage(source, "Debug overlay ENABLED (Wanted: 2★, Peace: 75%)", ChatFormatting.GREEN);
    } else {
      state.setWantedLevel(0);
      state.setPeaceValue(100);
      LawSystemHandler.getInstance().syncPlayerState(player);
      return sendSuccessMessage(source, "Debug overlay DISABLED (Wanted: 0, Peace: 100%)", ChatFormatting.YELLOW);
    }
  }

  private static int syncAllPlayers(CommandSourceStack source) {
    int count = LawSystemHandler.getInstance().syncAllPlayers();
    return sendSuccessMessage(source, "Synced law state to " + count + " player(s)", ChatFormatting.GREEN);
  }

  private static int toggleSystem(CommandSourceStack source) {
    LawSystemHandler handler = LawSystemHandler.getInstance();
    boolean newState = !handler.isSystemEnabled();
    handler.setSystemEnabled(newState);
    
    String status = newState ? "ENABLED" : "DISABLED";
    ChatFormatting color = newState ? ChatFormatting.GREEN : ChatFormatting.RED;
    return sendSuccessMessage(source, "Law System " + status, color);
  }

  private static int showStatus(CommandSourceStack source, ServerPlayer targetPlayer) {
    LawSystemHandler handler = LawSystemHandler.getInstance();
    
    if (targetPlayer == null) {
      // Show system status
      boolean enabled = handler.isSystemEnabled();
      int wantedCount = handler.getWantedPlayerCount();
      int guardCount = GuardResponseHandler.getInstance().getTotalGuardCount();
      int merchantCount = CrimeHandler.getInstance().getMerchantCount();
      
      source.sendSuccess(() -> TextComponent.getText("§6=== Law System Status ==="), false);
      source.sendSuccess(() -> TextComponent.getText("System: " + 
          (enabled ? "§aENABLED" : "§cDISABLED")), false);
      source.sendSuccess(() -> TextComponent.getText("Wanted Players: §e" + wantedCount), false);
      source.sendSuccess(() -> TextComponent.getText("Active Guards: §e" + guardCount), false);
      source.sendSuccess(() -> TextComponent.getText("Tracked Merchants: §e" + merchantCount), false);
      return SINGLE_SUCCESS;
    }
    
    // Show player status
    UUID playerUUID = targetPlayer.getUUID();
    PlayerLawState state = handler.getOrCreatePlayerState(playerUUID);
    
    source.sendSuccess(() -> TextComponent.getText("§6=== " + targetPlayer.getName().getString() + " ==="), false);
    source.sendSuccess(() -> TextComponent.getText("Wanted Level: " + getWantedStars(state.getWantedLevel())), false);
    source.sendSuccess(() -> TextComponent.getText("Peace Value: §b" + state.getPeaceValue() + "/100"), false);
    source.sendSuccess(() -> TextComponent.getText("Crime Immunity: " + 
        (state.hasCrimeImmunity() ? "§aYES" : "§7NO")), false);
    source.sendSuccess(() -> TextComponent.getText("Recent Crimes: §7" + state.getCrimeHistory().size()), false);
    
    return SINGLE_SUCCESS;
  }

  private static String getWantedStars(int level) {
    if (level == 0) {
      return "§7None";
    }
    StringBuilder stars = new StringBuilder("§c");
    for (int i = 0; i < level; i++) {
      stars.append("★");
    }
    for (int i = level; i < 5; i++) {
      stars.append("§8☆");
    }
    return stars.toString();
  }

  private static int setWantedLevel(CommandSourceStack source, ServerPlayer target, int level) {
    LawSystemHandler.getInstance().setPlayerWantedLevel(target.getUUID(), level);
    return sendSuccessMessage(source, 
        "Set " + target.getName().getString() + "'s wanted level to " + level, 
        ChatFormatting.GREEN);
  }

  private static int setPeaceValue(CommandSourceStack source, ServerPlayer target, int value) {
    LawSystemHandler.getInstance().setPlayerPeaceValue(target.getUUID(), value);
    return sendSuccessMessage(source, 
        "Set " + target.getName().getString() + "'s peace value to " + value, 
        ChatFormatting.GREEN);
  }

  private static int clearCrimes(CommandSourceStack source, ServerPlayer target) {
    LawSystemHandler.getInstance().clearPlayerCrimes(target.getUUID());
    return sendSuccessMessage(source, 
        "Cleared all crimes for " + target.getName().getString(), 
        ChatFormatting.GREEN);
  }

  private static int clearAllWanted(CommandSourceStack source) {
    LawSystemHandler.getInstance().clearAllWanted();
    return sendSuccessMessage(source, "Cleared all wanted players", ChatFormatting.GREEN);
  }

  private static int reloadConfig(CommandSourceStack source) {
    LawSystemHandler.getInstance().loadConfig();
    return sendSuccessMessage(source, "Law system config reloaded", ChatFormatting.GREEN);
  }

  private static int toggleImmunity(CommandSourceStack source, ServerPlayer target) {
    LawSystemHandler.getInstance().toggleCrimeImmunity(target.getUUID());
    PlayerLawState state = LawSystemHandler.getInstance().getPlayerState(target.getUUID());
    String status = state != null && state.hasCrimeImmunity() ? "ENABLED" : "DISABLED";
    return sendSuccessMessage(source, 
        "Crime immunity " + status + " for " + target.getName().getString(), 
        ChatFormatting.YELLOW);
  }

  private static int spawnPatrol(CommandSourceStack source, int tier) {
    if (source.getPlayer() == null) {
      return sendFailureMessage(source, "This command requires a player");
    }
    
    ServerPlayer player = source.getPlayer();
    GuardResponseHandler.getInstance().spawnPatrolAt(
        player.serverLevel(), 
        player.blockPosition(), 
        tier);
    
    return sendSuccessMessage(source, "Spawned tier " + tier + " patrol", ChatFormatting.GREEN);
  }

  private static int spawnPursuit(CommandSourceStack source, ServerPlayer target, int tier) {
    GuardResponseHandler.getInstance().spawnPursuitSquad(target, tier);
    return sendSuccessMessage(source, 
        "Spawned tier " + tier + " pursuit squad for " + target.getName().getString(), 
        ChatFormatting.GREEN);
  }

  private static int showStats(CommandSourceStack source) {
    LawSystemHandler handler = LawSystemHandler.getInstance();
    
    source.sendSuccess(() -> TextComponent.getText("§6=== Law System Statistics ==="), false);
    source.sendSuccess(() -> TextComponent.getText("Max Wanted Level: §e" + 
        handler.getConfig().getMaxWantedLevel()), false);
    source.sendSuccess(() -> TextComponent.getText("Active Regions: §e" + 
        handler.getConfig().getRegions().size()), false);
    source.sendSuccess(() -> TextComponent.getText("Guard Tiers: §e" + 
        handler.getConfig().getGuardTiers().size()), false);
    source.sendSuccess(() -> TextComponent.getText("Profile: §b" + 
        handler.getConfig().getProfileName()), false);
    
    return SINGLE_SUCCESS;
  }
}
