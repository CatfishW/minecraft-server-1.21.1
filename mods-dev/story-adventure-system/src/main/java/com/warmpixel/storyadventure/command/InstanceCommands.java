package com.warmpixel.storyadventure.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageGraph;
import com.warmpixel.storyadventure.instance.Instance;
import com.warmpixel.storyadventure.instance.InstanceManager;
import com.warmpixel.storyadventure.instance.Party;
import com.warmpixel.storyadventure.instance.PartyManager;
import com.warmpixel.storyadventure.loader.StoryRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Commands for managing story instances.
 */
public class InstanceCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                InstanceManager instanceManager,
                                PartyManager partyManager,
                                StoryRegistry storyRegistry) {
        
        dispatcher.register(Commands.literal("story")
            // List available stories
            .then(Commands.literal("list")
                .executes(ctx -> {
                    var source = ctx.getSource();
                    var stories = storyRegistry.getAllStories();
                    
                    if (stories.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§e没有可用的故事。"), false);
                        return 0;
                    }
                    
                    source.sendSuccess(() -> Component.literal("§6=== 可用故事 ==="), false);
                    for (StageGraph story : stories) {
                        source.sendSuccess(() -> Component.literal(String.format(
                            "§a%s §7- %s §8(%d节点, %d-%d人)",
                            story.getStoryId(),
                            story.getName(),
                            story.getNodeCount(),
                            story.getMinPlayers(),
                            story.getMaxPlayers()
                        )), false);
                    }
                    
                    return stories.size();
                }))
            
            // Start a story
            .then(Commands.literal("start")
                .then(Commands.argument("story_id", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String id : storyRegistry.getStoryIds()) {
                            builder.suggest(id);
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String storyId = StringArgumentType.getString(ctx, "story_id");
                        
                        StageGraph story = storyRegistry.getStory(storyId);
                        if (story == null) {
                            ctx.getSource().sendFailure(Component.literal("§c故事 '" + storyId + "' 不存在。"));
                            return 0;
                        }
                        
                        // Check if already in instance
                        if (instanceManager.isPlayerInInstance(player.getUUID())) {
                            ctx.getSource().sendFailure(Component.literal("§c你已经在一个故事实例中。使用 /story leave 离开。"));
                            return 0;
                        }
                        
                        // Create party with player as leader
                        Party party = partyManager.createParty(player.getUUID(), story.getMaxPlayers());
                        
                        // Create and start instance
                        Instance instance = instanceManager.createInstance(story, party);
                        if (instance == null) {
                            ctx.getSource().sendFailure(Component.literal("§c无法创建实例。"));
                            return 0;
                        }
                        
                        instance.start(ctx.getSource().getServer());
                        
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a故事 '" + story.getName() + "' 已开始！实例ID: " + instance.getInstanceId()
                        ), false);
                        
                        return 1;
                    })))
            
            // Leave current instance
            .then(Commands.literal("leave")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    
                    Instance instance = instanceManager.getPlayerInstance(player.getUUID());
                    if (instance == null) {
                        ctx.getSource().sendFailure(Component.literal("§c你不在任何故事实例中。"));
                        return 0;
                    }
                    
                    instanceManager.removePlayerFromInstance(player.getUUID());
                    partyManager.leaveParty(player.getUUID());
                    
                    ctx.getSource().sendSuccess(() -> Component.literal("§e已离开故事实例。"), false);
                    return 1;
                }))
            
            // Show current progress
            .then(Commands.literal("progress")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    
                    Instance instance = instanceManager.getPlayerInstance(player.getUUID());
                    if (instance == null) {
                        ctx.getSource().sendFailure(Component.literal("§c你不在任何故事实例中。"));
                        return 0;
                    }
                    
                    var source = ctx.getSource();
                    source.sendSuccess(() -> Component.literal("§6=== 故事进度 ==="), false);
                    source.sendSuccess(() -> Component.literal("§7故事: §f" + instance.getGraph().getName()), false);
                    source.sendSuccess(() -> Component.literal("§7当前节点: §f" + instance.getCurrentNodeId()), false);
                    source.sendSuccess(() -> Component.literal("§7状态: §f" + instance.getStatus()), false);
                    source.sendSuccess(() -> Component.literal("§7队伍人数: §f" + instance.getParty().getMemberCount()), false);
                    source.sendSuccess(() -> Component.literal("§7已发现线索: §f" + instance.getState().getDiscoveredClues().size()), false);
                    
                    long elapsedMin = instance.getElapsedMillis() / 60000;
                    source.sendSuccess(() -> Component.literal("§7已用时间: §f" + elapsedMin + "分钟"), false);
                    
                    return 1;
                }))
            
            // Join an instance by ID
            .then(Commands.literal("join")
                .then(Commands.argument("instance_id", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String instanceIdStr = StringArgumentType.getString(ctx, "instance_id");
                        
                        UUID instanceId;
                        try {
                            instanceId = UUID.fromString(instanceIdStr);
                        } catch (IllegalArgumentException e) {
                            ctx.getSource().sendFailure(Component.literal("§c无效的实例ID。"));
                            return 0;
                        }
                        
                        if (instanceManager.addPlayerToInstance(player.getUUID(), instanceId)) {
                            ctx.getSource().sendSuccess(() -> Component.literal("§a已加入实例。"), false);
                            return 1;
                        } else {
                            ctx.getSource().sendFailure(Component.literal("§c无法加入实例。"));
                            return 0;
                        }
                    })))
        );
    }
}
