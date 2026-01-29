package com.warmpixel.storyadventure.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.warmpixel.storyadventure.core.admin.AdminToolManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class StoryAdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("storyadmin")
            .requires(source -> source.hasPermission(2))
            
            // set_interaction_item on/off
            .then(Commands.literal("set_interaction_item")
                .then(Commands.argument("state", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("on");
                        builder.suggest("off");
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String state = StringArgumentType.getString(ctx, "state");
                        
                        boolean enable = "on".equalsIgnoreCase(state);
                        AdminToolManager.setRecording(player, enable);
                        
                        return 1;
                    })))
            
            // storyadmin setlocation <storyId> <spawn|return>
            .then(Commands.literal("setlocation")
                .then(Commands.argument("storyId", StringArgumentType.string())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("spawn");
                            builder.suggest("return");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String storyId = StringArgumentType.getString(ctx, "storyId");
                            String type = StringArgumentType.getString(ctx, "type");
                            
                            var registry = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getStoryRegistry();
                            var loader = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getStoryLoader();
                            var story = registry.getStory(storyId);
                            
                            if (story != null) {
                                story.setSpecialLocation(type, new com.warmpixel.storyadventure.core.graph.StageGraph.StoryLocation(
                                    player.level().dimension().location().toString(),
                                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
                                ));
                                loader.saveStory(story);
                                player.sendSystemMessage(Component.literal("§aStory '" + storyId + "' " + type + " location updated successfully."));
                                return 1;
                            } else {
                                player.sendSystemMessage(Component.literal("§cStory not found: " + storyId));
                                return 0;
                            }
                        }))))
            
            // storyadmin tp <storyId>
            .then(Commands.literal("tp")
                .then(Commands.argument("storyId", StringArgumentType.string())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String storyId = StringArgumentType.getString(ctx, "storyId");
                        
                        var registry = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getStoryRegistry();
                        var story = registry.getStory(storyId);
                        
                        if (story != null) {
                            var loc = story.getSpecialLocation("spawn");
                            if (loc != null) {
                                try {
                                    net.minecraft.resources.ResourceLocation dimLoc = net.minecraft.resources.ResourceLocation.parse(loc.dimension());
                                    net.minecraft.server.level.ServerLevel level = player.getServer().getLevel(
                                        net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc));
                                    
                                    if (level != null) {
                                        player.teleportTo(level, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
                                        player.sendSystemMessage(Component.literal("§aTeleported to scene of '" + storyId + "'"));
                                        return 1;
                                    }
                                } catch (Exception e) {
                                    player.sendSystemMessage(Component.literal("§cFailed to teleport: " + e.getMessage()));
                                }
                            } else {
                                player.sendSystemMessage(Component.literal("§cNo spawn location defined for story: " + storyId));
                            }
                        } else {
                            player.sendSystemMessage(Component.literal("§cStory not found: " + storyId));
                        }
                        return 0;
                    })))

            // storyadmin trigger <storyId> <nodeId>
            .then(Commands.literal("trigger")
                .then(Commands.argument("storyId", StringArgumentType.string())
                    .then(Commands.argument("nodeId", StringArgumentType.string())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String storyId = StringArgumentType.getString(ctx, "storyId");
                            String nodeId = StringArgumentType.getString(ctx, "nodeId");

                            var manager = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getInstanceManager();
                            var instance = manager.getPlayerInstance(player.getUUID());
                            
                            if (instance != null && instance.getStoryId().equals(storyId)) {
                                instance.forceTransition(nodeId);
                                player.sendSystemMessage(Component.literal("§aForced transition to node: " + nodeId));
                                return 1;
                            } else {
                                player.sendSystemMessage(Component.literal("§cYou are not in a running instance of story: " + storyId));
                                return 0;
                            }
                        }))))

            // storyadmin skip <instanceShortId> <nodeId>
            .then(Commands.literal("skip")
                .then(Commands.argument("instanceId", StringArgumentType.string())
                    .then(Commands.argument("nodeId", StringArgumentType.string())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String instanceIdStr = StringArgumentType.getString(ctx, "instanceId");
                            String nodeId = StringArgumentType.getString(ctx, "nodeId");

                            var manager = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getInstanceManager();
                            var instance = manager.getAllInstances().stream()
                                .filter(i -> i.getInstanceId().toString().startsWith(instanceIdStr))
                                .findFirst().orElse(null);
                            
                            if (instance != null) {
                                instance.forceTransition(nodeId);
                                player.sendSystemMessage(Component.literal("§aForced instance " + instanceIdStr + " to node: " + nodeId));
                                return 1;
                            }
                            return 0;
                        }))))

            // storyadmin complete <instanceShortId>
            .then(Commands.literal("complete")
                .then(Commands.argument("instanceId", StringArgumentType.string())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String instanceIdStr = StringArgumentType.getString(ctx, "instanceId");

                        var manager = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getInstanceManager();
                        var instance = manager.getAllInstances().stream()
                                .filter(i -> i.getInstanceId().toString().startsWith(instanceIdStr))
                                .findFirst().orElse(null);
                            
                        if (instance != null) {
                            instance.complete();
                            player.sendSystemMessage(Component.literal("§aForced instance " + instanceIdStr + " to complete successfully."));
                            return 1;
                        }
                        return 0;
                    })))

            // storyadmin terminate <instanceShortId>
            .then(Commands.literal("terminate")
                .then(Commands.argument("instanceId", StringArgumentType.string())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String instanceIdStr = StringArgumentType.getString(ctx, "instanceId");

                        var manager = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getInstanceManager();
                        var instance = manager.getAllInstances().stream()
                                .filter(i -> i.getInstanceId().toString().startsWith(instanceIdStr))
                                .findFirst().orElse(null);
                            
                        if (instance != null) {
                            manager.cleanupInstance(instance.getInstanceId());
                            player.sendSystemMessage(Component.literal("§aTerminated instance " + instanceIdStr));
                            return 1;
                        }
                        return 0;
                    })))

            // storyadmin pause <instanceShortId>
            .then(Commands.literal("pause")
                .then(Commands.argument("instanceId", StringArgumentType.string())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String instanceIdStr = StringArgumentType.getString(ctx, "instanceId");

                        var manager = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getInstanceManager();
                        var instance = manager.getAllInstances().stream()
                                .filter(i -> i.getInstanceId().toString().startsWith(instanceIdStr))
                                .findFirst().orElse(null);
                            
                        if (instance != null) {
                            instance.pause();
                            player.sendSystemMessage(Component.literal("§aPaused instance " + instanceIdStr));
                            return 1;
                        }
                        return 0;
                    })))

            // storyadmin resume <instanceShortId>
            .then(Commands.literal("resume")
                .then(Commands.argument("instanceId", StringArgumentType.string())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String instanceIdStr = StringArgumentType.getString(ctx, "instanceId");

                        var manager = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getInstanceManager();
                        var instance = manager.getAllInstances().stream()
                                .filter(i -> i.getInstanceId().toString().startsWith(instanceIdStr))
                                .findFirst().orElse(null);
                            
                        if (instance != null) {
                            instance.resume();
                            player.sendSystemMessage(Component.literal("§aResumed instance " + instanceIdStr));
                            return 1;
                        }
                        return 0;
                    })))

            // storyadmin players
            .then(Commands.literal("players")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    var manager = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getInstanceManager();
                    
                    player.sendSystemMessage(Component.literal("§6=== Online Operators ==="));
                    for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                        var inst = manager.getPlayerInstance(p.getUUID());
                        String instInfo = inst != null ? "§aIn: " + inst.getStoryId() + " (" + inst.getCurrentNodeId() + ")" : "§7Idle";
                        player.sendSystemMessage(Component.literal("§e" + p.getName().getString() + " §7- " + instInfo));
                    }
                    return 1;
                }))

            // storyadmin kick <playerName>
            .then(Commands.literal("kick")
                .then(Commands.argument("target", StringArgumentType.string())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String targetName = StringArgumentType.getString(ctx, "target");
                        ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(targetName);
                        
                        if (target != null) {
                            var manager = com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getInstanceManager();
                            manager.removePlayerFromInstance(target.getUUID());
                            player.sendSystemMessage(Component.literal("§aRemoved " + targetName + " from their story instance."));
                            target.sendSystemMessage(Component.literal("§eYou have been extracted from the operation by command."));
                            return 1;
                        }
                        return 0;
                    })))
        );
    }
}
