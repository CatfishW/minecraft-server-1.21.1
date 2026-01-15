package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.loader.StoryRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles network communication between server and clients for story synchronization.
 */
public class NetworkHandler {
    
    /**
     * Register custom payload types for networking.
     */
    public static void registerPayloadTypes() {
        // Register the OpenUI payload for server-to-client communication
        PayloadTypeRegistry.playS2C().register(OpenUIPayload.TYPE, OpenUIPayload.STREAM_CODEC);
        // Register the SyncInstances payload for server-to-client communication
        PayloadTypeRegistry.playS2C().register(SyncInstancesPayload.TYPE, SyncInstancesPayload.STREAM_CODEC);
        // Register the Invite payload
        PayloadTypeRegistry.playS2C().register(InvitePayload.TYPE, InvitePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(InvitePayload.TYPE, InvitePayload.STREAM_CODEC);
        // Register the SyncLobby payload
        PayloadTypeRegistry.playS2C().register(SyncLobbyPayload.TYPE, SyncLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SyncLobbyPayload.TYPE, SyncLobbyPayload.STREAM_CODEC);
        // Register the ToggleReady payload
        PayloadTypeRegistry.playC2S().register(ToggleReadyPayload.TYPE, ToggleReadyPayload.STREAM_CODEC);
        
        // New payloads
        PayloadTypeRegistry.playC2S().register(StoryActionPayload.TYPE, StoryActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DialogueChoicePayload.TYPE, DialogueChoicePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PuzzleInputPayload.TYPE, PuzzleInputPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncAdminStoriesPayload.TYPE, SyncAdminStoriesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncStoriesPayload.TYPE, SyncStoriesPayload.STREAM_CODEC);
        
        PayloadTypeRegistry.playS2C().register(SyncStoryGraphPayload.TYPE, SyncStoryGraphPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestStoryGraphPayload.TYPE, RequestStoryGraphPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SaveStoryPayload.TYPE, SaveStoryPayload.STREAM_CODEC);
        
        // Waypoint system payloads
        PayloadTypeRegistry.playS2C().register(SyncWaypointsPayload.TYPE, SyncWaypointsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncTriggerBoxesPayload.TYPE, SyncTriggerBoxesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AdminTriggerActionPayload.TYPE, AdminTriggerActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AdminStoryActionPayload.TYPE, AdminStoryActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AdminInstanceActionPayload.TYPE, AdminInstanceActionPayload.STREAM_CODEC);
        
        // Victory confirmation payload
        PayloadTypeRegistry.playC2S().register(VictoryConfirmPayload.TYPE, VictoryConfirmPayload.STREAM_CODEC);
        
        StoryAdventureMod.LOGGER.info("Registered network payload types");
    }
    
    /**
     * Register server-side packet receivers.
     */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(InvitePayload.TYPE, (payload, context) -> {
            ServerPlayer inviter = context.player();
            com.warmpixel.storyadventure.instance.PartyManager partyManager = StoryAdventureMod.getInstance().getPartyManager();
            
            if (payload.isResponse()) {
                // Handle response from invited player
                if (payload.accept()) {
                    // Find the inviter's party (simplified - usually we'd track invitation ID)
                    // For now, we'll look for a player with the 'name' from the payload (who is the inviter)
                    ServerPlayer originalInviter = context.server().getPlayerList().getPlayerByName(payload.name());
                    if (originalInviter != null) {
                        var party = partyManager.getPlayerParty(originalInviter.getUUID());
                        if (party != null) {
                            partyManager.joinParty(inviter.getUUID(), party.getPartyId());
                            inviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已加入队伍！"));
                            originalInviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a" + inviter.getName().getString() + " 已加入你的队伍"));
                            broadcastLobbySync(party, context.server());
                        }
                    }
                } else {
                    ServerPlayer originalInviter = context.server().getPlayerList().getPlayerByName(payload.name());
                    if (originalInviter != null) {
                        originalInviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c" + inviter.getName().getString() + " 拒绝了你的邀请"));
                    }
                }
            } else {
                // Sending an invite
                ServerPlayer target = context.server().getPlayerList().getPlayerByName(payload.name());
                if (target == null) {
                    inviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c找不到玩家: " + payload.name()));
                } else if (target == inviter) {
                    inviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你不能邀请你自己"));
                } else {
                    // Send invite to target
                    ServerPlayNetworking.send(target, new InvitePayload(inviter.getName().getString(), false, false));
                    inviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已对 " + target.getName().getString() + " 发送邀请"));
                }
            }
        });
        
        ServerPlayNetworking.registerGlobalReceiver(ToggleReadyPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            com.warmpixel.storyadventure.instance.Party party = StoryAdventureMod.getInstance().getPartyManager().getPlayerParty(player.getUUID());
            
            if (party != null) {
                party.setReady(player.getUUID(), payload.ready());
                broadcastLobbySync(party, context.server());
            }
        });

        // New handlers
        ServerPlayNetworking.registerGlobalReceiver(StoryActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var mod = StoryAdventureMod.getInstance();
            var partyManager = mod.getPartyManager();
            var instanceManager = mod.getInstanceManager();
            
            StoryAdventureMod.LOGGER.debug("[NetworkHandler] Received StoryActionPayload: action={}, data={}, player={}", 
                payload.action(), payload.data(), player.getName().getString());
            
            switch (payload.action()) {
                case SELECT_STORY -> {
                    String storyId = payload.data();
                    var story = mod.getStoryRegistry().getStory(storyId);
                    if (story == null) {
                        StoryAdventureMod.LOGGER.warn("[NetworkHandler] SELECT_STORY failed: Story '{}' not found for player {}", storyId, player.getName().getString());
                        return;
                    }
                    
                    // Create party if not exists
                    var party = partyManager.getPlayerParty(player.getUUID());
                    if (party == null) {
                        StoryAdventureMod.LOGGER.info("[NetworkHandler] Creating new party for player {} to play story {}", player.getName().getString(), storyId);
                        party = partyManager.createParty(player.getUUID(), story.getMaxPlayers());
                    } else if (!party.isLeader(player.getUUID())) {
                        StoryAdventureMod.LOGGER.warn("[NetworkHandler] SELECT_STORY failed: Player {} is not party leader", player.getName().getString());
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c只有队长可以选择故事"));
                        return;
                    }
                    
                    party.setSelectedStoryId(storyId);
                    StoryAdventureMod.LOGGER.info("[NetworkHandler] Party {} selected story {}", party.getPartyId(), storyId);
                    
                    // Open Lobby UI
                    // Construct JSON data for lobby
                    String lobbyData = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"desc\":\"%s\",\"min\":%d,\"max\":%d,\"time\":%d}",
                        story.getStoryId(), story.getName(), story.getDescription().replace("\"", "\\\"").replace("\n", "\\n"), 
                        story.getMinPlayers(), story.getMaxPlayers(), story.getEstimatedDurationMinutes());
                    
                    sendOpenUI(player, OpenUIPayload.SCREEN_LOBBY, lobbyData);
                    
                    // Sync initial lobby state
                    syncLobby(player, party, context.server());
                }
                
                case START_ADVENTURE -> {
                    var party = partyManager.getPlayerParty(player.getUUID());
                    if (party == null) {
                        StoryAdventureMod.LOGGER.warn("[NetworkHandler] START_ADVENTURE failed: Player {} is not in a party", player.getName().getString());
                        return;
                    }
                    if (!party.isLeader(player.getUUID())) {
                        StoryAdventureMod.LOGGER.warn("[NetworkHandler] START_ADVENTURE failed: Player {} is not party leader", player.getName().getString());
                        return;
                    }
                    
                    String storyId = party.getSelectedStoryId();
                    if (storyId == null) {
                        StoryAdventureMod.LOGGER.warn("[NetworkHandler] START_ADVENTURE failed: No story selected for party {}", party.getPartyId());
                        return;
                    }
                    
                    var story = mod.getStoryRegistry().getStory(storyId);
                    if (story == null) {
                        StoryAdventureMod.LOGGER.error("[NetworkHandler] START_ADVENTURE failed: Selected story '{}' not found in registry", storyId);
                        return;
                    }
                    
                    // Check ready status
                    long readyCount = party.getMembers().stream().filter(party::isReady).count();
                    StoryAdventureMod.LOGGER.debug("[NetworkHandler] Checking readiness: {} ready / {} min required", readyCount, story.getMinPlayers());
                    
                    if (readyCount < story.getMinPlayers()) {
                         StoryAdventureMod.LOGGER.warn("[NetworkHandler] START_ADVENTURE failed: Insufficient ready players ({} < {})", readyCount, story.getMinPlayers());
                         player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c准备人数不足！"));
                         return;
                    }
                    
                    // Start countdown
                    StoryAdventureMod.LOGGER.info("[NetworkHandler] Starting 5s countdown for party {} to play {}", party.getPartyId(), storyId);
                    party.setCountdownSeconds(5);
                    broadcastLobbySync(party, context.server());
                }
                
                case LEAVE_PARTY -> {
                    StoryAdventureMod.LOGGER.info("[NetworkHandler] Player {} leaving party", player.getName().getString());
                    partyManager.leaveParty(player.getUUID());
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e已离开队伍"));
                    // Close UI
                    player.closeContainer();
                }
                
                case DISBAND_PARTY -> {
                    var party = partyManager.getPlayerParty(player.getUUID());
                    if (party != null && party.isLeader(player.getUUID())) {
                        StoryAdventureMod.LOGGER.info("[NetworkHandler] Player {} disbanding party {}", player.getName().getString(), party.getPartyId());
                        for (java.util.UUID memberId : party.getMembers()) {
                             ServerPlayer member = context.server().getPlayerList().getPlayer(memberId);
                             if (member != null) {
                                 member.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c队伍已解散"));
                                 member.closeContainer();
                             }
                        }
                        partyManager.disbandParty(party.getPartyId());
                    } else {
                        StoryAdventureMod.LOGGER.warn("[NetworkHandler] DISBAND_PARTY failed: Player {} not leader or not in party", player.getName().getString());
                    }
                }
            }
        });
        
        ServerPlayNetworking.registerGlobalReceiver(DialogueChoicePayload.TYPE, (payload, context) -> {
             ServerPlayer player = context.player();
             var instance = StoryAdventureMod.getInstance().getInstanceManager().getPlayerInstance(player.getUUID());
             if (instance != null) {
                 var currentNode = instance.getCurrentNode();
                 var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(currentNode.getType());
                 if (handler != null) {
                     handler.onAction(instance, currentNode, player, "choice", payload.choiceId());
                 }
             }
        });
        
        ServerPlayNetworking.registerGlobalReceiver(PuzzleInputPayload.TYPE, (payload, context) -> {
             ServerPlayer player = context.player();
             var instance = StoryAdventureMod.getInstance().getInstanceManager().getPlayerInstance(player.getUUID());
             if (instance != null) {
                 var currentNode = instance.getCurrentNode();
                 var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(currentNode.getType());
                 if (handler != null) {
                     handler.onAction(instance, currentNode, player, "submit_answer", payload.input());
                 }
             }
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestStoryGraphPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String storyId = payload.storyId();
            
            try {
                java.nio.file.Path path = java.nio.file.Paths.get("config", "storyadventure", "stories", storyId + ".json");
                if (java.nio.file.Files.exists(path)) {
                    String json = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                    ServerPlayNetworking.send(player, new SyncStoryGraphPayload(storyId, json));
                    StoryAdventureMod.LOGGER.info("Synced story graph {} to {}", storyId, player.getName().getString());
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c服务器找不到故事文件: " + storyId));
                }
            } catch (Exception e) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c读取故事文件失败: " + e.getMessage()));
                e.printStackTrace();
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveStoryPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String storyId = payload.storyId();
            String json = payload.json();
            
            // Check permissions (should be operator)
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c只有管理员可以保存故事"));
                return;
            }

            try {
                java.nio.file.Path path = java.nio.file.Paths.get("config", "storyadventure", "stories", storyId + ".json");
                // Backup
                java.nio.file.Path backup = path.resolveSibling(storyId + ".json.bak");
                if (java.nio.file.Files.exists(path)) {
                    java.nio.file.Files.copy(path, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                
                // Save
                java.nio.file.Files.writeString(path, json, java.nio.charset.StandardCharsets.UTF_8);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a故事已成功保存到服务器: " + storyId));
                StoryAdventureMod.LOGGER.info("Admin {} saved story graph {}", player.getName().getString(), storyId);
                
                // Trigger reload in memory
                StoryAdventureMod.getInstance().getStoryLoader().loadAllStories();
            } catch (Exception e) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c保存故事失败: " + e.getMessage()));
                e.printStackTrace();
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(AdminTriggerActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> handleAdminTriggerAction(context.player(), payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(AdminStoryActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> handleAdminStoryAction(context.player(), payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(AdminInstanceActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> handleAdminInstanceAction(context.player(), payload));
        });
        
        // Victory confirmation handler - teleport player to spawn
        ServerPlayNetworking.registerGlobalReceiver(VictoryConfirmPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> handleVictoryConfirm(context.player(), context.server()));
        });
        
        StoryAdventureMod.LOGGER.debug("Registered server network receivers");
    }

    // ... (rest of methods)

    public static void broadcastLobbySync(com.warmpixel.storyadventure.instance.Party party, net.minecraft.server.MinecraftServer server) {
        java.util.List<SyncLobbyPayload.MemberInfo> infos = new java.util.ArrayList<>();
        for (java.util.UUID memberId : party.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            String name = member != null ? member.getName().getString() : "未知玩家";
            infos.add(new SyncLobbyPayload.MemberInfo(memberId, name, party.isReady(memberId), party.isLeader(memberId)));
        }
        
        SyncLobbyPayload syncPayload = new SyncLobbyPayload(infos, party.getCountdownSeconds());
        for (java.util.UUID memberId : party.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                ServerPlayNetworking.send(member, syncPayload);
            }
        }
    }

    public static void syncLobby(ServerPlayer player, com.warmpixel.storyadventure.instance.Party party, net.minecraft.server.MinecraftServer server) {
        java.util.List<SyncLobbyPayload.MemberInfo> infos = new java.util.ArrayList<>();
        for (java.util.UUID memberId : party.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            String name = member != null ? member.getName().getString() : "未知玩家";
            infos.add(new SyncLobbyPayload.MemberInfo(memberId, name, party.isReady(memberId), party.isLeader(memberId)));
        }
        
        ServerPlayNetworking.send(player, new SyncLobbyPayload(infos, party.getCountdownSeconds()));
    }
    
    /**
     * Register client-side packet receivers.
     * This is called from the client initializer.
     */
    public static void registerClientReceivers() {
        // Client receivers are registered in ClientNetworkHandler to avoid server-side class loading issues
        StoryAdventureMod.LOGGER.debug("Client receiver registration delegated to ClientNetworkHandler");
    }
    
    /**
     * Send a request to the client to open a specific UI screen.
     */
    public static void sendOpenUI(ServerPlayer player, String screenType) {
        sendOpenUI(player, screenType, "");
    }
    
    /**
     * Send a request to the client to open a specific UI screen with extra data.
     */
    public static void sendOpenUI(ServerPlayer player, String screenType, String extraData) {
        if (player != null && player.connection != null) {
            ServerPlayNetworking.send(player, new OpenUIPayload(screenType, extraData));
            StoryAdventureMod.LOGGER.debug("Sent OpenUI packet to {}: screen={}, data={}", 
                player.getName().getString(), screenType, extraData);
        }
    }
    
    /**
     * Sync the list of active instances to an administrative player.
     */
    public static void syncInstances(ServerPlayer player, com.warmpixel.storyadventure.instance.InstanceManager manager) {
        if (player == null || player.connection == null) return;
        
        java.util.List<SyncInstancesPayload.InstanceInfo> infos = new java.util.ArrayList<>();
        for (com.warmpixel.storyadventure.instance.Instance inst : manager.getAllInstances()) {
            infos.add(new SyncInstancesPayload.InstanceInfo(
                inst.getInstanceId(),
                inst.getGraph().getName(),
                inst.getCurrentNodeId(),
                inst.getStatus().name(),
                inst.getParty().getMemberCount(),
                inst.getElapsedMillis()
            ));
        }
        
        ServerPlayNetworking.send(player, new SyncInstancesPayload(infos));
        StoryAdventureMod.LOGGER.debug("Synced {} instances to admin {}", infos.size(), player.getName().getString());
    }
    public static void syncStoryList(ServerPlayer player, StoryRegistry registry) {
        java.util.List<SyncStoriesPayload.StorySummary> summaries = new java.util.ArrayList<>();
        for (var story : registry.getAllStories()) {
            summaries.add(new SyncStoriesPayload.StorySummary(
                story.getStoryId(), story.getName(), story.getDescription(),
                story.getMinPlayers(), story.getMaxPlayers(), story.getEstimatedDurationMinutes()
            ));
        }
        ServerPlayNetworking.send(player, new SyncStoriesPayload(summaries));
    }
    
    public static void syncAdminStories(ServerPlayer player, StoryRegistry registry) {
        java.util.List<SyncAdminStoriesPayload.AdminStoryInfo> infos = new java.util.ArrayList<>();
        var validator = new com.warmpixel.storyadventure.loader.StoryValidator();
        
        for (var story : registry.getAllStories()) {
            var errors = validator.validate(story);
            boolean valid = errors.isEmpty();
            String errorMsg = valid ? "" : String.join("; ", errors);
            
            infos.add(new SyncAdminStoriesPayload.AdminStoryInfo(
                story.getStoryId(), story.getName(), story.getNodeCount(),
                story.getVersion(), valid, errorMsg
            ));
        }
        ServerPlayNetworking.send(player, new SyncAdminStoriesPayload(infos));
    }
    
    /**
     * Sync instance state to party members.
     */
    public static void syncInstanceState(com.warmpixel.storyadventure.instance.Instance instance) {
        // Sync current state to all party members
        for (java.util.UUID memberId : instance.getParty().getMembers()) {
            // Send state update packet
        }
    }
    
    /**
     * Send node transition notification.
     */
    public static void notifyNodeTransition(com.warmpixel.storyadventure.instance.Instance instance, 
                                            String fromNode, String toNode) {
        // Notify all party members of node change
    }
    
    /**
     * Send HUD update to player.
     */
    public static void sendHudUpdate(ServerPlayer player, String objectiveText, long remainingTime) {
        // Send HUD update packet
    }

    public static void syncTriggerBoxes(ServerPlayer player) {
        var manager = com.warmpixel.storyadventure.core.waypoint.TriggerBoxManager.getInstance();
        if (manager == null) return;

        java.util.List<SyncTriggerBoxesPayload.TriggerBoxData> list = new java.util.ArrayList<>();
        for (var box : manager.getAllBoxes()) {
            list.add(new SyncTriggerBoxesPayload.TriggerBoxData(
                box.getId(), box.getLabel(),
                box.getBounds().minX, box.getBounds().minY, box.getBounds().minZ,
                box.getBounds().maxX, box.getBounds().maxY, box.getBounds().maxZ,
                !box.getPlayersInside().isEmpty()
            ));
        }
        ServerPlayNetworking.send(player, new SyncTriggerBoxesPayload(list));
    }

    private static void handleAdminTriggerAction(ServerPlayer player, AdminTriggerActionPayload payload) {
        if (player == null || !player.hasPermissions(2)) return;
        
        var manager = com.warmpixel.storyadventure.core.waypoint.TriggerBoxManager.getInstance();
        if (manager == null) return;
        
        switch (payload.action()) {
            case LIST -> syncTriggerBoxes(player);
            case SAVE -> {
                var box = new com.warmpixel.storyadventure.core.waypoint.TriggerBox(payload.id(), 
                    new net.minecraft.world.phys.AABB(payload.minX(), payload.minY(), payload.minZ(), 
                                                   payload.maxX(), payload.maxY(), payload.maxZ()));
                box.setLabel(payload.label());
                box.setLinkedNodeId(payload.linkedNodeId().isEmpty() ? null : payload.linkedNodeId());
                manager.updateBox(payload.id(), box);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已保存触发器: " + payload.id()));
                syncTriggerBoxes(player);
            }
            case DELETE -> {
                if (manager.deleteBox(payload.id())) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已删除触发器: " + payload.id()));
                    syncTriggerBoxes(player);
                }
            }
        }
    }

    private static void handleAdminStoryAction(ServerPlayer player, AdminStoryActionPayload payload) {
        if (player == null || !player.hasPermissions(2)) return;
        
        var registry = StoryAdventureMod.getInstance().getStoryRegistry();
        var loader = StoryAdventureMod.getInstance().getStoryLoader();
        
        switch (payload.action()) {
            case SYNC -> syncAdminStories(player, registry);
            case RELOAD -> {
                loader.reload();
                syncAdminStories(player, registry);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a故事已重载"));
            }
            case VALIDATE -> {
                loader.reload(); // Reload triggers validation
                syncAdminStories(player, registry);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a故事已验证"));
            }
        }
    }

    private static void handleAdminInstanceAction(ServerPlayer player, AdminInstanceActionPayload payload) {
        if (player == null || !player.hasPermissions(2)) return;
        
        var manager = StoryAdventureMod.getInstance().getInstanceManager();
        
        switch (payload.action()) {
            case SYNC -> syncInstances(player, manager);
            case TERMINATE -> {
                if (payload.instanceId() != null) {
                    manager.cleanupInstance(payload.instanceId());
                    syncInstances(player, manager);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a实例已终止"));
                }
            }
            case PAUSE -> {
                if (payload.instanceId() != null) {
                    var inst = manager.getInstance(payload.instanceId());
                    if (inst != null) {
                        inst.pause();
                        syncInstances(player, manager);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a实例已暂停"));
                    }
                }
            }
            case RESUME -> {
                if (payload.instanceId() != null) {
                    var inst = manager.getInstance(payload.instanceId());
                    if (inst != null) {
                        inst.resume();
                        syncInstances(player, manager);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a实例已恢复"));
                    }
                }
            }
        }
    }
    
    private static void handleVictoryConfirm(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        if (player == null) return;
        
        StoryAdventureMod.LOGGER.info("Victory confirmed by player {}, teleporting to spawn", player.getName().getString());
        
        // Get world spawn point
        var overworld = server.overworld();
        var spawnPos = overworld.getSharedSpawnPos();
        
        // Teleport player to world spawn
        player.teleportTo(overworld, 
            spawnPos.getX() + 0.5, 
            spawnPos.getY(), 
            spawnPos.getZ() + 0.5, 
            0, 0);
        
        // Send message
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已返回出生点"));
        
        // Clear instance association
        var instanceManager = StoryAdventureMod.getInstance().getInstanceManager();
        instanceManager.removePlayerFromInstance(player.getUUID());
        
        StoryAdventureMod.LOGGER.info("Player {} cleared from instance system after victory", player.getName().getString());
        
        // Hide HUD
        sendOpenUI(player, OpenUIPayload.SCREEN_HUD_HIDE);
    }
}
