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
            // Story list screen - no instance required
            .then(Commands.literal("stories")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_STORIES);
                    NetworkHandler.syncStoryList(player, com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getStoryRegistry());
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.storyadventure.ui.stories.opening"), false);
                    return 1;
                }))
            
            // Lobby/ready screen - no instance required, creates party if needed
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
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.storyadventure.ui.lobby.opening"), false);
                    return 1;
                }))
            
            // Dialogue screen - requires active instance
            .then(Commands.literal("dialogue")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    
                    // Check if player is in an active instance
                    var instance = instanceManager.getPlayerInstance(player.getUUID());
                    if (instance == null) {
                        ctx.getSource().sendFailure(Component.translatable("command.storyadventure.error.no_instance"));
                        return 0;
                    }
                    
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_DIALOGUE);
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.storyadventure.ui.dialogue.opening"), false);
                    return 1;
                }))
            
            // Puzzle screen - requires active instance
            .then(Commands.literal("puzzle")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    
                    // Check if player is in an active instance
                    var instance = instanceManager.getPlayerInstance(player.getUUID());
                    if (instance == null) {
                        ctx.getSource().sendFailure(Component.translatable("command.storyadventure.error.no_instance"));
                        return 0;
                    }
                    
                    String extraData = "";
                    try {
                        var node = instance.getCurrentNode();
                        if (node != null && node.getType() == com.warmpixel.storyadventure.core.graph.NodeType.PUZZLE) {
                            var data = node.getData();
                            var json = new com.google.gson.JsonObject();
                            json.addProperty("puzzle_type", node.getString("puzzle_type", "CODE_LOCK"));
                            json.addProperty("title", node.getString("title", "解谜"));
                            json.addProperty("subtitle", node.getString("description", ""));
                            json.addProperty("max_attempts", node.getInt("max_attempts", 3));
                            String solution = node.getString("solution", "");
                            json.addProperty("code_length", solution.isEmpty() ? 4 : solution.length());
                            if (data.has("hint")) {
                                json.add("hint", data.get("hint"));
                            }
                            if (data.has("hints") && data.get("hints").isJsonArray()) {
                                json.add("hints", data.getAsJsonArray("hints"));
                            }
                            extraData = json.toString();
                        }
                    } catch (Exception e) {
                        // Keep defaults on failure
                    }

                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_PUZZLE, extraData);
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.storyadventure.ui.puzzle.opening"), false);
                    return 1;
                }))
            
            // Toggle HUD - requires active instance
            .then(Commands.literal("hud")
                .then(Commands.literal("show")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        
                        // Check if player is in an active instance
                        var instance = instanceManager.getPlayerInstance(player.getUUID());
                        if (instance == null) {
                            ctx.getSource().sendFailure(Component.translatable("command.storyadventure.error.no_instance_hud"));
                            return 0;
                        }
                        
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
                ctx.getSource().sendSuccess(() -> Component.literal("§6=== Story UI Commands ==="), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyui stories §7- Open story list"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyui lobby §7- Open lobby"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyui dialogue §7- Dialogue screen"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyui puzzle §7- Puzzle screen"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyui hud show/hide §7- Show/Hide HUD"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadminui §7- Admin Dashboard"), false);
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
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.storyadventure.ui.admin.dashboard.opening"), false);
                    return 1;
                }))
            
            // Instance manager
            .then(Commands.literal("instances")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.syncInstances(player, instanceManager);
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_ADMIN_INSTANCES);
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.storyadventure.ui.admin.instances.opening"), false);
                    return 1;
                }))
            
            // Story manager
            .then(Commands.literal("stories")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_ADMIN_STORIES);
                    NetworkHandler.syncAdminStories(player, com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getStoryRegistry());
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.storyadventure.ui.admin.stories.opening"), false);
                    return 1;
                }))
            
            // Sync only versions - does not open UI
            .then(Commands.literal("sync_stories")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.syncAdminStories(player, com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getStoryRegistry());
                    return 1;
                }))
            
            .then(Commands.literal("sync_instances")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHandler.syncInstances(player, instanceManager);
                    return 1;
                }))
            
            // Default - open dashboard
            .requires(source -> source.hasPermission(2))
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_ADMIN_DASHBOARD);
                ctx.getSource().sendSuccess(() -> Component.translatable("command.storyadventure.ui.admin.dashboard.opening"), false);
                return 1;
            })
        );
    }
}
