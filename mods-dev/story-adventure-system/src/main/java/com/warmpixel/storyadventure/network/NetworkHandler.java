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
        PayloadTypeRegistry.playS2C().register(PuzzleResultPayload.TYPE, PuzzleResultPayload.STREAM_CODEC);
        
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
        
        // Cutscene payload (server to client)
        PayloadTypeRegistry.playS2C().register(CutscenePayload.TYPE, CutscenePayload.STREAM_CODEC);
        
        // Voiceover payload (server to client)
        PayloadTypeRegistry.playS2C().register(VoiceoverPayload.TYPE, VoiceoverPayload.STREAM_CODEC);
        
        // Xaero Waypoint payload (server to client)
        PayloadTypeRegistry.playS2C().register(XaeroWaypointPayload.TYPE, XaeroWaypointPayload.STREAM_CODEC);
        
        // BGM payload (server to client)
        PayloadTypeRegistry.playS2C().register(BGMPayload.TYPE, BGMPayload.STREAM_CODEC);
        
        // Item/Block indicator payloads (server to client)
        com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorAddPacket.register();
        com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorRemovePacket.register();
        com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorClearPacket.register();
        
        // UI Tutorial packet (server to client)
        com.warmpixel.storyadventure.network.packet.UITutorialPacket.register();
        com.warmpixel.storyadventure.network.packet.UITutorialClickPayload.register();
        
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
                    StoryAdventureMod.LOGGER.info("[NetworkHandler] Invite acceptance: acceptingPlayer={}, originalInviter={}", 
                        inviter.getName().getString(), payload.name());
                    
                    if (originalInviter != null) {
                        var party = partyManager.getPlayerParty(originalInviter.getUUID());
                        StoryAdventureMod.LOGGER.info("[NetworkHandler] Party lookup: originalInviter={}, party={}", 
                            originalInviter.getName().getString(), party != null ? party.getPartyId() : "null");
                        
                        if (party != null) {
                            boolean joined = partyManager.joinParty(inviter.getUUID(), party.getPartyId());
                            StoryAdventureMod.LOGGER.info("[NetworkHandler] joinParty result: player={}, partyId={}, success={}", 
                                inviter.getName().getString(), party.getPartyId(), joined);
                            
                            if (joined) {
                                inviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已加入队伍！"));
                                originalInviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a" + inviter.getName().getString() + " 已加入你的队伍"));
                                
                                // Open lobby screen for the joined player
                                String storyId = party.getSelectedStoryId();
                                String lobbyData = null;
                                
                                if (storyId != null) {
                                    var story = StoryAdventureMod.getInstance().getStoryRegistry().getStory(storyId);
                                    if (story != null) {
                                        lobbyData = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"desc\":\"%s\",\"min\":%d,\"max\":%d,\"time\":%d}",
                                            story.getStoryId(), story.getName(), 
                                            story.getDescription().replace("\"", "\\\"").replace("\n", "\\n"), 
                                            story.getMinPlayers(), story.getMaxPlayers(), story.getEstimatedDurationMinutes());
                                    } else {
                                        StoryAdventureMod.LOGGER.warn("[NetworkHandler] Story not found in registry: {}", storyId);
                                        // Fallback with basic data
                                        lobbyData = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"desc\":\"%s\",\"min\":%d,\"max\":%d,\"time\":%d}",
                                            storyId, storyId, "加载中...", 1, party.getMaxSize(), 0);
                                    }
                                } else {
                                    StoryAdventureMod.LOGGER.warn("[NetworkHandler] Party has no selected story: {}", party.getPartyId());
                                    // Fallback with placeholder data
                                    lobbyData = "{\"id\":\"unknown\",\"name\":\"队伍大厅\",\"desc\":\"等待队长选择故事...\",\"min\":1,\"max\":" + party.getMaxSize() + ",\"time\":0}";
                                }
                                
                                // Always open lobby for invited player
                                sendOpenUI(inviter, OpenUIPayload.SCREEN_LOBBY, lobbyData);
                                StoryAdventureMod.LOGGER.info("[NetworkHandler] Sent lobby UI to invited player: {}", inviter.getName().getString());
                                
                                // Delay the sync slightly to ensure UI is open first
                                context.server().execute(() -> {
                                    broadcastLobbySync(party, context.server());
                                });
                            } else {
                                inviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c无法加入队伍，队伍可能已满或你已在该队伍中"));
                            }
                        } else {
                            inviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c队伍不存在或已解散"));
                        }
                    } else {
                        inviter.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c邀请者已离线"));
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
                case REQUEST_STORY_LIST -> {
                    syncStoryList(player, StoryAdventureMod.getInstance().getStoryRegistry());
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
        
        // UI Tutorial click handler
        ServerPlayNetworking.registerGlobalReceiver(com.warmpixel.storyadventure.network.packet.UITutorialClickPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var instance = StoryAdventureMod.getInstance().getInstanceManager().getPlayerInstance(player.getUUID());
            if (instance != null) {
                var currentNode = instance.getCurrentNode();
                var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(currentNode.getType());
                if (handler != null) {
                    handler.onAction(instance, currentNode, player, "ui_click", payload.tutorialId());
                }
            }
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
     * Send a cutscene start command to a player.
     */
    public static void sendCutsceneStart(ServerPlayer player, com.google.gson.JsonObject cameraPath, 
                                          boolean skippable, boolean letterbox, 
                                          int fadeInTicks, int fadeOutTicks, String instanceId, String voiceover,
                                          com.google.gson.JsonArray subtitles, com.google.gson.JsonArray animations) {
        if (player != null && player.connection != null) {
            CutscenePayload payload = CutscenePayload.start(instanceId, cameraPath, skippable, letterbox, fadeInTicks, fadeOutTicks, voiceover, subtitles, animations);
            ServerPlayNetworking.send(player, payload);
            StoryAdventureMod.LOGGER.info("Sent cutscene START to {} (voiceover: {})", player.getName().getString(), voiceover);
        }
    }
    
    // Overload for backward compatibility/laziness if needed, or update callers
    public static void sendCutsceneStart(ServerPlayer player, com.google.gson.JsonObject cameraPath, 
                                          boolean skippable, boolean letterbox, 
                                          int fadeInTicks, int fadeOutTicks, String instanceId, String voiceover) {
        sendCutsceneStart(player, cameraPath, skippable, letterbox, fadeInTicks, fadeOutTicks, instanceId, voiceover, null, null);
    }
    
    /**
     * Send a cutscene stop command to a player.
     */
    public static void sendCutsceneStop(ServerPlayer player, String instanceId) {
        if (player != null && player.connection != null) {
            CutscenePayload payload = CutscenePayload.stop(instanceId);
            ServerPlayNetworking.send(player, payload);
            StoryAdventureMod.LOGGER.debug("Sent cutscene STOP to {}", player.getName().getString());
        }
    }
    
    /**
     * Send a voiceover to a player.
     */
    public static void sendVoiceover(ServerPlayer player, String instanceId, String soundPath, String characterId) {
        sendVoiceover(player, instanceId, soundPath, 1.0f, 1.0f, characterId);
    }
    
    /**
     * Send a voiceover to a player with custom volume and pitch.
     */
    public static void sendVoiceover(ServerPlayer player, String instanceId, String soundPath, 
                                      float volume, float pitch, String characterId) {
        if (player != null && player.connection != null) {
            VoiceoverPayload payload = VoiceoverPayload.custom(instanceId, soundPath, volume, pitch, characterId);
            ServerPlayNetworking.send(player, payload);
            StoryAdventureMod.LOGGER.debug("Sent voiceover {} to {}", soundPath, player.getName().getString());
        }
    }
    
    /**
     * Send a voiceover to all party members of an instance.
     */
    public static void sendVoiceoverToParty(com.warmpixel.storyadventure.instance.Instance instance, 
                                             String soundPath, String characterId) {
        if (instance == null) return;
        
        for (java.util.UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                sendVoiceover(player, instance.getInstanceId().toString(), soundPath, characterId);
            }
        }
    }
    
    /**
     * Send BGM update to a player.
     */
    public static void sendBGM(ServerPlayer player, String soundPath, float volume, boolean loop, int fadeTicks) {
        if (player != null && player.connection != null) {
            ServerPlayNetworking.send(player, BGMPayload.play(soundPath, volume, loop, fadeTicks));
        }
    }
    
    /**
     * Send BGM stop command to a player.
     */
    public static void sendBGMStop(ServerPlayer player, int fadeTicks) {
        if (player != null && player.connection != null) {
            ServerPlayNetworking.send(player, BGMPayload.stop(fadeTicks));
        }
    }
    
    /**
     * Send BGM update to all party members.
     */
    public static void sendBGMToParty(com.warmpixel.storyadventure.instance.Instance instance, String soundPath, float volume, boolean loop, int fadeTicks) {
        if (instance == null) return;
        for (java.util.UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                sendBGM(player, soundPath, volume, loop, fadeTicks);
            }
        }
    }

    /**
     * Stop BGM for all party members.
     */
    public static void sendBGMStopToParty(com.warmpixel.storyadventure.instance.Instance instance, int fadeTicks) {
        if (instance == null) return;
        for (java.util.UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                sendBGMStop(player, fadeTicks);
            }
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
            // Skip tutorial stories that have gold coin rewards
            if (story.getStoryId().toLowerCase().contains("tutorial") && story.hasCoinReward()) {
                continue;
            }
            summaries.add(new SyncStoriesPayload.StorySummary(
                story.getStoryId(), story.getName(), story.getDescription(),
                story.getMinPlayers(), story.getMaxPlayers(), story.getEstimatedDurationMinutes(), story.getCover()
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
            case SET_SPAWN -> {
                var story = registry.getStory(payload.storyId());
                if (story != null) {
                    story.setSpecialLocation("spawn", new com.warmpixel.storyadventure.core.graph.StageGraph.StoryLocation(
                        player.level().dimension().location().toString(),
                        player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
                    ));
                    loader.saveStory(story);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已设置故事 '" + payload.storyId() + "' 的出生点"));
                }
            }
            case SET_RETURN -> {
                var story = registry.getStory(payload.storyId());
                if (story != null) {
                    story.setSpecialLocation("return", new com.warmpixel.storyadventure.core.graph.StageGraph.StoryLocation(
                        player.level().dimension().location().toString(),
                        player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
                    ));
                    loader.saveStory(story);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已设置故事 '" + payload.storyId() + "' 的返回点"));
                }
            }
            case TP_TO_SCENE -> {
                var story = registry.getStory(payload.storyId());
                if (story != null) {
                    var loc = story.getSpecialLocation("spawn");
                    if (loc != null) {
                        player.teleportTo(player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION, 
                            net.minecraft.resources.ResourceLocation.parse(loc.dimension()))),
                            loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已传送至故事场景"));
                    }
                }
            }
            case CREATE_TEMPLATE -> {
                // Placeholder for template creation logic
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e模板创建功能尚未完全实现，请手动复制 JSON。"));
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
            case SKIP_NODE -> {
                if (payload.instanceId() != null && payload.data() != null) {
                    var inst = manager.getInstance(payload.instanceId());
                    if (inst != null) {
                        inst.forceTransition(payload.data());
                        syncInstances(player, manager);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已跳过至节点: " + payload.data()));
                    }
                }
            }
            case COMPLETE -> {
                if (payload.instanceId() != null) {
                    var inst = manager.getInstance(payload.instanceId());
                    if (inst != null) {
                        inst.complete();
                        syncInstances(player, manager);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a实例已强制完成"));
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
