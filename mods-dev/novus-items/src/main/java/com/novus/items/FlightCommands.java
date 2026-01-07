package com.novus.items;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class FlightCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Main command: /flight
        dispatcher.register(Commands.literal("flight")
            // Check own flight time (default action)
            .executes(FlightCommands::checkOwnFlight)
            
            // Toggle flight on/off: /flight fly
            .then(Commands.literal("fly")
                .executes(FlightCommands::toggleFlight))
            
            // Check another player's flight time (OP only): /flight check <player>
            .then(Commands.literal("check")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(FlightCommands::checkPlayerFlight)))
            
            // Grant flight to a player (OP only): /flight grant <player> <duration>
            .then(Commands.literal("grant")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("duration", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("1h");
                            builder.suggest("12h");
                            builder.suggest("24h");
                            builder.suggest("3d");
                            builder.suggest("7d");
                            builder.suggest("permanent");
                            return builder.buildFuture();
                        })
                        .executes(FlightCommands::grantFlight))))
            
            // Revoke flight from a player (OP only): /flight revoke <player>
            .then(Commands.literal("revoke")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(FlightCommands::revokeFlight)))
        );
    }
    
    private static int toggleFlight(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return 0;
        }
        
        Boolean newState = FlightManager.toggleFlight(player);
        
        if (newState == null) {
            source.sendFailure(Component.literal("§c✦ You don't have any flight time! §c✦"));
            return 0;
        }
        
        if (newState) {
            source.sendSuccess(() -> Component.literal("§a✦ Flight enabled! §a✦"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§c✦ Flight disabled! §c✦"), false);
        }
        
        return 1;
    }
    
    private static int checkOwnFlight(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return 0;
        }
        
        long remaining = FlightManager.getRemainingTimeSeconds(player);
        
        if (FlightManager.hasPermanentFlight(player)) {
            source.sendSuccess(() -> Component.literal("§6✦ §eYou have §6§lPERMANENT FLIGHT§e! §6✦"), false);
        } else if (remaining > 0) {
            String timeStr = FlightManager.formatRemainingTime(remaining);
            source.sendSuccess(() -> Component.literal("§a✦ §eYou have §a" + timeStr + "§e of flight remaining! §a✦"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§c✦ §eYou do not have any flight time. §c✦"), false);
        }
        
        return 1;
    }
    
    private static int checkPlayerFlight(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            long remaining = FlightManager.getRemainingTimeSeconds(target);
            String name = target.getGameProfile().getName();
            
            if (FlightManager.hasPermanentFlight(target)) {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§6✦ §e" + name + " has §6§lPERMANENT FLIGHT§e! §6✦"), false);
            } else if (remaining > 0) {
                String timeStr = FlightManager.formatRemainingTime(remaining);
                context.getSource().sendSuccess(() -> 
                    Component.literal("§a✦ §e" + name + " has §a" + timeStr + "§e of flight remaining! §a✦"), false);
            } else {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§c✦ §e" + name + " does not have any flight time. §c✦"), false);
            }
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Player not found!"));
            return 0;
        }
    }
    
    private static int grantFlight(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            String durationStr = StringArgumentType.getString(context, "duration").toLowerCase();
            
            FlightDuration duration = parseDuration(durationStr);
            if (duration == null) {
                context.getSource().sendFailure(Component.literal(
                    "§cInvalid duration! Use: 1h, 12h, 24h, 3d, 7d, or permanent"));
                return 0;
            }
            
            FlightManager.adminGrantFlight(target, duration);
            
            String name = target.getGameProfile().getName();
            context.getSource().sendSuccess(() -> Component.literal(
                "§aGranted §e" + duration.getDisplayName() + "§a flight to §e" + name + "§a!"), true);
            
            target.sendSystemMessage(Component.literal(
                "§a✦ An admin granted you §e" + duration.getDisplayName() + "§a of flight! §a✦"));
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int revokeFlight(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            FlightManager.adminRevokeFlight(target);
            
            String name = target.getGameProfile().getName();
            context.getSource().sendSuccess(() -> Component.literal(
                "§cRevoked flight from §e" + name + "§c!"), true);
            
            target.sendSystemMessage(Component.literal(
                "§c✦ Your flight has been revoked by an admin! §c✦"));
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
    
    private static FlightDuration parseDuration(String str) {
        return switch (str) {
            case "1h", "1hour" -> FlightDuration.ONE_HOUR;
            case "12h", "12hours" -> FlightDuration.TWELVE_HOURS;
            case "24h", "1d", "1day" -> FlightDuration.ONE_DAY;
            case "3d", "3days" -> FlightDuration.THREE_DAYS;
            case "7d", "7days", "1w", "1week" -> FlightDuration.SEVEN_DAYS;
            case "permanent", "perm", "forever" -> FlightDuration.PERMANENT;
            default -> null;
        };
    }
}
