package com.warmpixel.storyadventure.command;

import com.mojang.brigadier.CommandDispatcher;
import com.warmpixel.storyadventure.network.NetworkHandler;
import com.warmpixel.storyadventure.network.OpenUIPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side commands to trigger UI screens on the client.
 * These commands send network packets to the client to open the appropriate UI.
 */
public class ServerUICommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, 
                                com.warmpixel.storyadventure.instance.InstanceManager instanceManager,
                                com.warmpixel.storyadventure.instance.PartyManager partyManager) {
        
        // Main storyui command - server-side version that sends packets to client
        dispatcher.register(Commands.literal("storyui")
            // Story list screen
            .then(Commands.literal("stories")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_STORIES);
                    NetworkHandler.syncStoryList(player, com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getStoryRegistry());
                    ctx.getSource().sendSuccess(() -> Component.literal("§a正在打开故事列表..."), false);
                    return 1;
                }))
            
            // Lobby/ready screen
            .then(Commands.literal("lobby")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    
                    // Ensure player is in a party for the lobby
                    var party = partyManager.getPlayerParty(player.getUUID());
                    if (party == null) {
                        party = partyManager.createParty(player.getUUID(), 4);
                    }
                    
                    // Sync current lobby status
                    NetworkHandler.syncLobby(player, party, ctx.getSource().getServer());
                    
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_LOBBY);
                    ctx.getSource().sendSuccess(() -> Component.literal("§a正在打开准备大厅..."), false);
                    return 1;
                }))
            
            // Dialogue screen demo
            .then(Commands.literal("dialogue")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_DIALOGUE);
                    ctx.getSource().sendSuccess(() -> Component.literal("§a正在打开对话演示..."), false);
                    return 1;
                }))
            
            // Puzzle screen demo
            .then(Commands.literal("puzzle")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_PUZZLE);
                    ctx.getSource().sendSuccess(() -> Component.literal("§a正在打开解谜演示..."), false);
                    return 1;
                }))
            
            // Toggle HUD
            .then(Commands.literal("hud")
                .then(Commands.literal("show")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_HUD_SHOW);
                        return 1;
                    }))
                .then(Commands.literal("hide")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_HUD_HIDE);
                        return 1;
                    })))
            
            // Help
            .executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal("§6=== 故事UI命令 ==="), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyui stories §7- 打开故事列表"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyui lobby §7- 打开准备大厅"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyui dialogue §7- 演示对话界面"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyui puzzle §7- 演示解谜界面"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyui hud show/hide §7- 显示/隐藏HUD"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadminui §7- 管理员控制台"), false);
                return 1;
            })
        );
        
        // Admin UI command
        dispatcher.register(Commands.literal("storyadminui")
            // Main dashboard
            .then(Commands.literal("dashboard")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.syncInstances(player, instanceManager);
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_ADMIN_DASHBOARD);
                    ctx.getSource().sendSuccess(() -> Component.literal("§a正在打开管理员控制台..."), false);
                    return 1;
                }))
            
            // Instance manager
            .then(Commands.literal("instances")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.syncInstances(player, instanceManager);
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_ADMIN_INSTANCES);
                    ctx.getSource().sendSuccess(() -> Component.literal("§a正在打开实例管理器..."), false);
                    return 1;
                }))
            
            // Story manager
            .then(Commands.literal("stories")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_ADMIN_STORIES);
                    ctx.getSource().sendSuccess(() -> Component.literal("§a正在打开故事管理器..."), false);
                    return 1;
                }))
            
            // Default - open dashboard
            .requires(source -> source.hasPermission(2))
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_ADMIN_DASHBOARD);
                ctx.getSource().sendSuccess(() -> Component.literal("§a正在打开管理员控制台..."), false);
                return 1;
            })
        );
    }
}
