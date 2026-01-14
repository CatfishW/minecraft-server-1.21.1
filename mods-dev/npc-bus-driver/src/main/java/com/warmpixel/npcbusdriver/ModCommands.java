package com.warmpixel.npcbusdriver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.warmpixel.npcbusdriver.item.PathWandItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.Optional;

public class ModCommands {

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext registryAccess, net.minecraft.commands.Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("npcbusdriver")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("save_path")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ModCommands::savePath)))
            .then(Commands.literal("setup_bus_driver")
                .then(Commands.argument("path_name", StringArgumentType.string())
                    .then(Commands.argument("bus_type", StringArgumentType.string())
                         .executes(ModCommands::setupBusDriver))))
            .then(Commands.literal("request_stop")
                 .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                      .executes(context -> requestStop(context, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds"))))
            )
            .then(Commands.literal("reload_paths")
                 .executes(ModCommands::reloadPaths))
        );
    }

    private static int reloadPaths(CommandContext<CommandSourceStack> context) {
        BusDriverManager.reloadAllPaths();
        context.getSource().sendSuccess(() -> Component.literal("All bus paths reloaded from disk."), true);
        return Command.SINGLE_SUCCESS;
    }


    private static int requestStop(CommandContext<CommandSourceStack> context, int seconds) {
        Entity executor = context.getSource().getEntity();
        if (executor != null) {
            BusDriverManager.requestStopNearby(executor.position(), seconds);
            if (executor instanceof ServerPlayer player) {
                player.closeContainer();
            }
            context.getSource().sendSuccess(() -> Component.literal("Bus stopping for " + seconds + " seconds."), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int savePath(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof PathWandItem)) {
                context.getSource().sendFailure(Component.literal("Hold the Path Wand to save the path!"));
                return 0;
            }

            String name = StringArgumentType.getString(context, "name");
            List<BlockPos> points = ((PathWandItem) stack.getItem()).getPoints(stack);
            
            if (points.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Path is empty!"));
                return 0;
            }

            PathManager.savePath(name, points);
            context.getSource().sendSuccess(() -> Component.literal("Path '" + name + "' saved with " + points.size() + " points."), true);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error saving path: " + e.getMessage()));
            e.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int setupBusDriver(CommandContext<CommandSourceStack> context) {
        try {
            Entity executor = context.getSource().getEntity();
            String pathName = StringArgumentType.getString(context, "path_name");
            String busTypeStr = StringArgumentType.getString(context, "bus_type");

            if (executor == null) {
                context.getSource().sendFailure(Component.literal("This command must be executed by an entity (NPC)."));
                return 0;
            }

            // Delegate complex spawning logic to BusDriverManager
            if (BusDriverManager.setupBusDriver(context.getSource().getLevel(), executor, pathName, busTypeStr) != null) {
                context.getSource().sendSuccess(() -> Component.literal("Setup bus driver on path '" + pathName + "'"), true);
            } else {
                context.getSource().sendFailure(Component.literal("Failed to setup bus driver (check logs)."));
                return 0;
            }

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error setting up bus driver: " + e.getMessage()));
            e.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }
}
