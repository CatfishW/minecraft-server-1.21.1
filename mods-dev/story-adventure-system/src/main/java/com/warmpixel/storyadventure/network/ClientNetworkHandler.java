package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.client.cinematic.CameraPath;
import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import com.warmpixel.storyadventure.client.ui.*;
import com.warmpixel.storyadventure.client.ui.admin.*;
import com.warmpixel.storyadventure.client.ui.hud.StrangerHudRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Client-side network handler for receiving UI open requests from the server.
 */
@Environment(EnvType.CLIENT)
public class ClientNetworkHandler {
    
    /**
     * Register client-side packet receivers.
     */
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(OpenUIPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleOpenUI(payload.screenType(), payload.extraData()));
        });
        
        ClientPlayNetworking.registerGlobalReceiver(SyncInstancesPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleSyncInstances(payload));
        });
        
        ClientPlayNetworking.registerGlobalReceiver(InvitePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (!payload.isResponse()) {
                    Minecraft.getInstance().setScreen(new StrangerInvitationNotificationScreen(payload.name()));
                }
            });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(SyncLobbyPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof StrangerLobbyScreen lobbyScreen) {
                    lobbyScreen.clearMembers();
                    for (var member : payload.members()) {
                        lobbyScreen.addMember(member.id(), member.name(), member.ready(), member.isLeader());
                    }
                    
                    // Handle countdown
                    if (payload.countdown() >= 0) {
                        lobbyScreen.startCountdown(payload.countdown());
                    }

                    // Also update ready button text based on self status
                    if (context.client().player != null) {
                         var self = payload.members().stream()
                             .filter(m -> m.id().equals(context.client().player.getUUID()))
                             .findFirst().orElse(null);
                         if (self != null) {
                             lobbyScreen.setReady(self.ready());
                             lobbyScreen.setLeader(self.isLeader());
                             lobbyScreen.rebuildButtons();
                         }
                    }
                }
            });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(SyncStoriesPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof StrangerStoryListScreen listScreen) {
                    listScreen.clearStories();
                    for (var story : payload.stories()) {
                        listScreen.addStory(story.id(), story.name(), story.description(), 
                            story.minPlayers(), story.maxPlayers(), story.estimatedMinutes(), story.cover());
                    }
                }
            });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(SyncAdminStoriesPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof AdminStoryManagerScreen managerScreen) {
                    managerScreen.clearStories();
                    for (var story : payload.stories()) {
                        managerScreen.addStory(story.id(), story.name(), story.nodeCount(), 
                            story.version(), story.valid(), story.errorMsg());
                    }
                }
            });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(SyncStoryGraphPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof com.warmpixel.storyadventure.client.ui.admin.graph.StoryGraphScreen graphScreen) {
                    graphScreen.onSyncReceived(payload.json());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PuzzleResultPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof StrangerPuzzleScreen) {
                    Minecraft.getInstance().setScreen(null);
                }
            });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(SyncWaypointsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                var renderer = com.warmpixel.storyadventure.client.render.WaypointIndicatorRenderer.getInstance();
                if (renderer == null) return;
                
                renderer.clear();
                for (var wpData : payload.waypoints()) {
                    var wp = new com.warmpixel.storyadventure.core.waypoint.Waypoint(wpData.id(), 
                        new net.minecraft.world.phys.Vec3(wpData.x(), wpData.y(), wpData.z()));
                    wp.setLabel(wpData.label());
                    wp.setColor(wpData.color());
                    wp.setShowDistance(wpData.showDistance());
                    wp.setIcon(com.warmpixel.storyadventure.core.waypoint.Waypoint.WaypointIcon.fromId(wpData.icon()));
                    renderer.addWaypoint(wp);
                }
                renderer.setEnabled(!payload.waypoints().isEmpty());
                
                // Update 3D world indicators with label support
                if (payload.waypoints().size() == 1) {
                    var wpData = payload.waypoints().get(0);
                    com.warmpixel.storyadventure.client.render.WorldDestinationRenderer.setDestinationWithLabel(
                        new net.minecraft.world.phys.Vec3(wpData.x(), wpData.y(), wpData.z()),
                        wpData.label()
                    );
                } else {
                    java.util.List<net.minecraft.world.phys.Vec3> destPoints = new java.util.ArrayList<>();
                    for (var wpData : payload.waypoints()) {
                        destPoints.add(new net.minecraft.world.phys.Vec3(wpData.x(), wpData.y(), wpData.z()));
                    }
                    com.warmpixel.storyadventure.client.render.WorldDestinationRenderer.setDestinations(destPoints);
                }
            });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(SyncTriggerBoxesPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                var renderer = com.warmpixel.storyadventure.client.render.TriggerBoxGizmoRenderer.getInstance();
                if (renderer == null) return;
                
                renderer.clear();
                for (var boxData : payload.boxes()) {
                    var box = new com.warmpixel.storyadventure.core.waypoint.TriggerBox(boxData.id(),
                        new net.minecraft.world.phys.AABB(
                            boxData.minX(), boxData.minY(), boxData.minZ(),
                            boxData.maxX(), boxData.maxY(), boxData.maxZ()
                        ));
                    box.setLabel(boxData.label());
                    renderer.addTriggerBox(box);
                }

                // Also update TriggerBoxManagerScreen if open
                if (context.client().screen instanceof com.warmpixel.storyadventure.client.ui.admin.TriggerBoxManagerScreen managerScreen) {
                    managerScreen.clearBoxes();
                    for (var boxData : payload.boxes()) {
                        managerScreen.addBoxFromSync(boxData.id(), boxData.label(), 
                            boxData.minX(), boxData.minY(), boxData.minZ(),
                            boxData.maxX(), boxData.maxY(), boxData.maxZ());
                    }
                }
            });
        });
        
        // Cutscene payload - handle camera control from server
        ClientPlayNetworking.registerGlobalReceiver(CutscenePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleCutscenePayload(payload));
        });
        
        // Voiceover payload - handle voiceover audio from server
        ClientPlayNetworking.registerGlobalReceiver(VoiceoverPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleVoiceoverPayload(payload));
        });
        
        // Xaero Waypoint payload - handle Xaero waypoint integration
        ClientPlayNetworking.registerGlobalReceiver(XaeroWaypointPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleXaeroWaypointPayload(payload));
        });
        
        // BGM payload - handle BGM from server
        ClientPlayNetworking.registerGlobalReceiver(BGMPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleBGMPayload(payload));
        });
        
        // Item/Block indicator payloads
        ClientPlayNetworking.registerGlobalReceiver(
            com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorAddPacket.TYPE, 
            (payload, context) -> {
                context.client().execute(() -> {
                    var indicator = new com.warmpixel.storyadventure.client.render.ItemBlockIndicatorRenderer.ItemBlockIndicator(
                        payload.id(),
                        new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z()),
                        payload.color(),
                        payload.label(),
                        payload.circleRadius(),
                        payload.showArrow(),
                        payload.showCircle()
                    );
                    com.warmpixel.storyadventure.client.render.ItemBlockIndicatorRenderer.addIndicator(indicator);
                    StoryAdventureMod.LOGGER.debug("[ItemBlockIndicator] Added indicator '{}' at ({}, {}, {})", 
                        payload.id(), payload.x(), payload.y(), payload.z());
                });
            }
        );
        
        ClientPlayNetworking.registerGlobalReceiver(
            com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorRemovePacket.TYPE, 
            (payload, context) -> {
                context.client().execute(() -> {
                    com.warmpixel.storyadventure.client.render.ItemBlockIndicatorRenderer.removeIndicator(payload.id());
                    StoryAdventureMod.LOGGER.debug("[ItemBlockIndicator] Removed indicator '{}'", payload.id());
                });
            }
        );
        
        ClientPlayNetworking.registerGlobalReceiver(
            com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorClearPacket.TYPE, 
            (payload, context) -> {
                context.client().execute(() -> {
                    com.warmpixel.storyadventure.client.render.ItemBlockIndicatorRenderer.clearIndicators();
                    StoryAdventureMod.LOGGER.debug("[ItemBlockIndicator] Cleared all indicators");
                });
            }
        );
        
        // UI Tutorial packet handler
        ClientPlayNetworking.registerGlobalReceiver(
            com.warmpixel.storyadventure.network.packet.UITutorialPacket.TYPE,
            (payload, context) -> {
                context.client().execute(() -> {
                    switch (payload.action().toLowerCase()) {
                        case "show" -> {
                            var entry = new com.warmpixel.storyadventure.client.ui.hud.UITutorialRenderer.TutorialEntry(
                                payload.id(),
                                payload.elementType(),
                                payload.elementIndex(),
                                payload.screenX(),
                                payload.screenY(),
                                payload.width(),
                                payload.height(),
                                payload.message(),
                                payload.keyHint(),
                                payload.color(),
                                payload.showArrow(),
                                payload.showPulse(),
                                payload.showClickHint(),
                                payload.durationTicks(),
                                payload.requireClick()
                            );
                            com.warmpixel.storyadventure.client.ui.hud.UITutorialRenderer.showTutorial(entry);
                            StoryAdventureMod.LOGGER.debug("[UITutorial] Added tutorial '{}' ({}): {}", 
                                payload.id(), payload.elementType(), payload.message());
                        }
                        case "hide" -> {
                            com.warmpixel.storyadventure.client.ui.hud.UITutorialRenderer.hideTutorial(payload.id());
                            StoryAdventureMod.LOGGER.debug("[UITutorial] Removed tutorial '{}'", payload.id());
                        }
                        case "clear" -> {
                            com.warmpixel.storyadventure.client.ui.hud.UITutorialRenderer.clearTutorials();
                            StoryAdventureMod.LOGGER.debug("[UITutorial] Cleared all tutorials");
                        }
                    }
                });
            }
        );
        
        // UITutorialClickPayload is registered in NetworkHandler.registerPayloadTypes()
        
        StoryAdventureMod.LOGGER.info("Registered client network receivers");
    }
    
    private static void handleXaeroWaypointPayload(XaeroWaypointPayload payload) {
        switch (payload.action()) {
            case START -> com.warmpixel.storyadventure.client.integration.XaeroWaypointIntegration.onInstanceStart();
            case ADD -> com.warmpixel.storyadventure.client.integration.XaeroWaypointIntegration.addWaypoint(
                payload.id(), payload.name(), payload.x(), payload.y(), payload.z(), payload.color()
            );
            case REMOVE -> com.warmpixel.storyadventure.client.integration.XaeroWaypointIntegration.removeWaypoint(
                payload.id(), payload.name()
            );
            case END -> com.warmpixel.storyadventure.client.integration.XaeroWaypointIntegration.onInstanceEnd();
        }
    }
    
    /**
     * Handle the OpenUI payload by opening the appropriate screen.
     */
    private static void handleOpenUI(String screenType, String extraData) {
        Minecraft mc = Minecraft.getInstance();
        
        switch (screenType) {
            case OpenUIPayload.SCREEN_STORIES -> {
                StrangerStoryListScreen screen = new StrangerStoryListScreen();
                mc.setScreen(screen);
                // The server will send SyncStoriesPayload shortly after
            }
            
            case OpenUIPayload.SCREEN_LOBBY -> {
                try {
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(extraData).getAsJsonObject();
                    String name = json.get("name").getAsString();
                    String desc = json.get("desc").getAsString();
                    int min = json.get("min").getAsInt();
                    int max = json.get("max").getAsInt();
                    int time = json.get("time").getAsInt();
                    
                    StrangerLobbyScreen screen = new StrangerLobbyScreen(name, desc, min, max, time);
                    mc.setScreen(screen);
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("Failed to parse lobby data", e);
                    // Fallback
                    StrangerLobbyScreen screen = new StrangerLobbyScreen("Unknown Story", "Error loading data", 1, 4, 0);
                    mc.setScreen(screen);
                }
            }
            
            case OpenUIPayload.SCREEN_DIALOGUE -> {
                String npcName = "NPC";
                StringBuilder dialogueText = new StringBuilder();
                java.util.List<String[]> choicesList = new java.util.ArrayList<>();
                String voiceoverPath = null; // Optional voiceover
                
                String profileId = null;
                
                // Parse extraData JSON if present
                if (extraData != null && !extraData.isEmpty()) {
                    try {
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(extraData).getAsJsonObject();
                        
                        if (json.has("npcName")) {
                            npcName = json.get("npcName").getAsString();
                        }

                        if (json.has("profileId")) {
                            profileId = json.get("profileId").getAsString();
                        }
                        
                        // Parse voiceover if present
                        if (json.has("voiceover")) {
                            voiceoverPath = json.get("voiceover").getAsString();
                        }
                        
                        if (json.has("lines") && json.get("lines").isJsonArray()) {
                            var lines = json.getAsJsonArray("lines");
                            for (int i = 0; i < lines.size(); i++) {
                                if (i > 0) dialogueText.append("\n\n");
                                // Lines can be strings or objects with text and voiceover
                                if (lines.get(i).isJsonPrimitive()) {
                                    dialogueText.append(lines.get(i).getAsString());
                                } else if (lines.get(i).isJsonObject()) {
                                    var lineObj = lines.get(i).getAsJsonObject();
                                    dialogueText.append(lineObj.has("text") ? lineObj.get("text").getAsString() : "");
                                    // If this is the first line and has voiceover, use it
                                    if (i == 0 && lineObj.has("voiceover") && voiceoverPath == null) {
                                        voiceoverPath = lineObj.get("voiceover").getAsString();
                                    }
                                }
                            }
                        }
                        
                        if (json.has("choices") && json.get("choices").isJsonArray()) {
                            var choices = json.getAsJsonArray("choices");
                            for (var choiceElem : choices) {
                                var choice = choiceElem.getAsJsonObject();
                                String id = choice.has("id") ? choice.get("id").getAsString() : "default";
                                String text = choice.has("text") ? choice.get("text").getAsString() : "确定";
                                choicesList.add(new String[]{id, text});
                            }
                        }
                    } catch (Exception e) {
                        StoryAdventureMod.LOGGER.error("Failed to parse dialogue data", e);
                    }
                }
                
                // Fallback if no dialogue text
                if (dialogueText.isEmpty()) {
                    dialogueText.append("...");
                }
                
                // Play voiceover if present
                if (voiceoverPath != null && !voiceoverPath.isEmpty()) {
                    var voiceoverManager = com.warmpixel.storyadventure.client.audio.VoiceoverManager.getInstance();
                    voiceoverManager.playVoiceover(voiceoverPath, npcName);
                }
                
                StrangerDialogueScreen screen = new StrangerDialogueScreen(npcName, dialogueText.toString(), profileId);
                
                // Add choices from JSON
                for (String[] choice : choicesList) {
                    screen.addChoice(choice[0], choice[1], () -> {
                        // Stop voiceover when making a choice
                        com.warmpixel.storyadventure.client.audio.VoiceoverManager.getInstance().stopCurrentVoiceover();
                        mc.setScreen(null); // Close dialogue after selection
                    });
                }
                
                // Add default choice if none defined
                if (choicesList.isEmpty()) {
                    screen.addChoice("continue", "继续", () -> {
                        com.warmpixel.storyadventure.client.audio.VoiceoverManager.getInstance().stopCurrentVoiceover();
                        mc.setScreen(null);
                    });
                }
                
                mc.setScreen(screen);
            }

            
            case OpenUIPayload.SCREEN_PUZZLE -> {
                String puzzleType = "CODE_LOCK";
                String title = "";
                String subtitle = "";
                int maxAttempts = 5;
                int codeLength = 4;
                java.util.List<String> hints = new java.util.ArrayList<>();

                if (extraData != null && !extraData.isEmpty()) {
                    try {
                        var json = com.google.gson.JsonParser.parseString(extraData).getAsJsonObject();
                        if (json.has("puzzle_type")) puzzleType = json.get("puzzle_type").getAsString();
                        if (json.has("title")) title = json.get("title").getAsString();
                        if (json.has("subtitle")) subtitle = json.get("subtitle").getAsString();
                        if (json.has("max_attempts")) maxAttempts = json.get("max_attempts").getAsInt();
                        if (json.has("code_length")) codeLength = json.get("code_length").getAsInt();
                        if (json.has("hint")) {
                            String hint = json.get("hint").getAsString();
                            if (!hint.isEmpty()) hints.add(hint);
                        }
                        if (json.has("hints") && json.get("hints").isJsonArray()) {
                            for (var elem : json.getAsJsonArray("hints")) {
                                if (elem.isJsonPrimitive()) {
                                    String hint = elem.getAsString();
                                    if (!hint.isEmpty()) hints.add(hint);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Fallback to defaults if JSON is malformed
                    }
                }

                StrangerPuzzleScreen screen = new StrangerPuzzleScreen(
                    puzzleType, title, subtitle, hints, maxAttempts, codeLength);
                mc.setScreen(screen);
            }
            
            case OpenUIPayload.SCREEN_ADMIN_DASHBOARD -> {
                mc.setScreen(new AdminDashboardScreen());
            }
            
            case OpenUIPayload.SCREEN_ADMIN_INSTANCES -> {
                AdminInstanceManagerScreen screen = new AdminInstanceManagerScreen();
                // Admin screens handle their own data via dedicated sync payload
                mc.setScreen(screen);
            }
            
            case OpenUIPayload.SCREEN_ADMIN_STORIES -> {
                AdminStoryManagerScreen screen = new AdminStoryManagerScreen();
                // Admin screens handle their own data via dedicated sync payload (not implemented for stories yet, but structure exists)
                screen.addStory("stranger_things_hawkins", "怪奇物语：霍金斯事件", 
                    20, "1.0.0", true, "");
                screen.addStory("example_story", "示例故事", 
                    5, "1.0.0", true, "");
                mc.setScreen(screen);
            }
            
            case OpenUIPayload.SCREEN_HUD_SHOW -> {
                try {
                    String title = "怪奇物语";
                    String chapter = "第一章";
                    
                    if (extraData != null && !extraData.isEmpty()) {
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(extraData).getAsJsonObject();
                        if (json.has("title")) title = json.get("title").getAsString();
                        if (json.has("chapter")) chapter = json.get("chapter").getAsString();
                        
                        StrangerHudRenderer.getInstance().show(title, chapter);
                        
                        // Parse objectives from JSON if present
                        if (json.has("objectives") && json.get("objectives").isJsonArray()) {
                            var objectives = new java.util.ArrayList<StrangerHudRenderer.ObjectiveEntry>();
                            for (var objElem : json.getAsJsonArray("objectives")) {
                                var obj = objElem.getAsJsonObject();
                                String text = obj.has("text") ? obj.get("text").getAsString() : "目标";
                                boolean complete = obj.has("complete") && obj.get("complete").getAsBoolean();
                                boolean current = obj.has("current") && obj.get("current").getAsBoolean();
                                objectives.add(new StrangerHudRenderer.ObjectiveEntry(text, complete, current));
                            }
                            StrangerHudRenderer.getInstance().setObjectives(objectives);
                        }
                        
                        // Timer if present (task-specific timer)
                        if (json.has("timer")) {
                            long timerMs = json.get("timer").getAsLong();
                            if (timerMs > 0) {
                                StrangerHudRenderer.getInstance().startTimer(timerMs);
                            }
                        }

                        // Instance timer (overall instance time limit)
                        if (json.has("instanceTimer")) {
                            long instanceTimerMs = json.get("instanceTimer").getAsLong();
                            if (instanceTimerMs > 0) {
                                StrangerHudRenderer.getInstance().setInstanceTimer(instanceTimerMs);
                            }
                        }

                        // Lives display if present
                        if (json.has("remainingLives") && json.has("maxLives")) {
                            int remaining = json.get("remainingLives").getAsInt();
                            int max = json.get("maxLives").getAsInt();
                            StrangerHudRenderer.getInstance().setLives(remaining, max);
                        }
                    } else {
                        StrangerHudRenderer.getInstance().show(title, chapter);
                    }
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("Failed to parse HUD data", e);
                    StrangerHudRenderer.getInstance().show("怪奇物语", "第一章");
                }
            }
            
            case OpenUIPayload.SCREEN_HUD_HIDE -> {
                StrangerHudRenderer.getInstance().hide();
            }
            
            case OpenUIPayload.SCREEN_VICTORY -> {
                try {
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(extraData).getAsJsonObject();
                    String storyName = json.has("storyName") ? json.get("storyName").getAsString() : "未知故事";
                    long completionTime = json.has("completionTime") ? json.get("completionTime").getAsLong() : 0;
                    
                    java.util.List<StrangerVictoryScreen.RewardEntry> rewards = new java.util.ArrayList<>();
                    if (json.has("rewards") && json.get("rewards").isJsonArray()) {
                        for (var rewardElem : json.getAsJsonArray("rewards")) {
                            var reward = rewardElem.getAsJsonObject();
                            String type = reward.has("type") ? reward.get("type").getAsString() : "ITEM";
                            int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 1;
                            
                            if ("EXPERIENCE".equals(type)) {
                                rewards.add(StrangerVictoryScreen.RewardEntry.experience(amount));
                            } else if ("ITEM".equals(type)) {
                                String item = reward.has("item") ? reward.get("item").getAsString() : "物品";
                                rewards.add(StrangerVictoryScreen.RewardEntry.item(item, amount));
                            } else if ("COIN".equals(type)) {
                                rewards.add(StrangerVictoryScreen.RewardEntry.item("金币", amount));
                            }
                        }
                    }
                    
                    mc.setScreen(new StrangerVictoryScreen(storyName, completionTime, rewards));
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("Failed to parse victory data", e);
                    // Fallback with default data
                    mc.setScreen(new StrangerVictoryScreen("任务完成", 0, new java.util.ArrayList<>()));
                }
            }
            
            case OpenUIPayload.SCREEN_DEFEAT -> {
                try {
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(extraData).getAsJsonObject();
                    String storyName = json.has("storyName") ? json.get("storyName").getAsString() : "未知故事";
                    String reason = json.has("reason") ? json.get("reason").getAsString() : "未知原因";
                    int deathCount = json.has("deathCount") ? json.get("deathCount").getAsInt() : 0;
                    int maxDeaths = json.has("maxDeaths") ? json.get("maxDeaths").getAsInt() : 15;
                    
                    java.util.List<StrangerDefeatScreen.RewardEntry> rewards = new java.util.ArrayList<>();
                    if (json.has("rewards") && json.get("rewards").isJsonArray()) {
                        for (var rewardElem : json.getAsJsonArray("rewards")) {
                            var reward = rewardElem.getAsJsonObject();
                            String type = reward.has("type") ? reward.get("type").getAsString() : "EXPERIENCE";
                            int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 0;
                            
                            if ("EXPERIENCE".equals(type)) {
                                rewards.add(StrangerDefeatScreen.RewardEntry.experience(amount));
                            }
                        }
                    }
                    
                    mc.setScreen(new StrangerDefeatScreen(storyName, reason, deathCount, maxDeaths, rewards));
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("Failed to parse defeat data", e);
                    // Fallback with default data
                    mc.setScreen(new StrangerDefeatScreen("任务失败", "未知原因", 0, 15, new java.util.ArrayList<>()));
                }
            }

            
            default -> {
                StoryAdventureMod.LOGGER.warn("Unknown screen type received: {}", screenType);
            }
        }
    }
    
    private static java.util.List<SyncInstancesPayload.InstanceInfo> lastSyncedInstances = new java.util.ArrayList<>();

    private static void handleSyncInstances(SyncInstancesPayload payload) {
        lastSyncedInstances = payload.instances();
        StoryAdventureMod.LOGGER.info("Received sync for {} instances", lastSyncedInstances.size());
        
        // If an admin manager screen is open, update it
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AdminInstanceManagerScreen managerScreen) {
            managerScreen.clearInstances();
            for (SyncInstancesPayload.InstanceInfo info : lastSyncedInstances) {
                managerScreen.addInstance(info.id(), info.storyName(), info.node(), info.status(), info.playerCount(), info.elapsed());
            }
            managerScreen.onSyncReceived();
        }
    }

    public static java.util.List<SyncInstancesPayload.InstanceInfo> getLastSyncedInstances() {
        return lastSyncedInstances;
    }
    
    /**
     * Handle cutscene payload from server.
     */
    private static void handleCutscenePayload(CutscenePayload payload) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        
        switch (payload.action().toUpperCase()) {
            case "START" -> {
                // Parse camera path from JSON
                com.google.gson.JsonObject pathJson = payload.getCameraPathAsJson();
                CameraPath path = CameraPath.fromJson(pathJson);
                
                // Configure cutscene
                CinematicCameraController.CutsceneConfig config = new CinematicCameraController.CutsceneConfig()
                    .setSkippable(payload.skippable())
                    .setLetterboxEnabled(payload.letterbox())
                    .setFadeInTicks(payload.fadeInTicks())
                    .setFadeOutTicks(payload.fadeOutTicks());
                
                // Parse subtitles
                var subtitlesJson = payload.getSubtitlesAsJson();
                StoryAdventureMod.LOGGER.info("[ClientNetworkHandler] Raw subtitlesJson: {}", payload.subtitlesJson());
                StoryAdventureMod.LOGGER.info("[ClientNetworkHandler] Parsed subtitlesJson array size: {}", subtitlesJson != null ? subtitlesJson.size() : 0);
                if (subtitlesJson != null && !subtitlesJson.isEmpty()) {
                    var subtitleList = new java.util.ArrayList<CinematicCameraController.Subtitle>();
                    for (var elem : subtitlesJson) {
                        if (elem.isJsonObject()) {
                            var obj = elem.getAsJsonObject();
                            String text = obj.has("text") ? obj.get("text").getAsString() : "";
                            
                            int start = 0;
                            if (obj.has("start_tick")) start = obj.get("start_tick").getAsInt();
                            else if (obj.has("start_ticks")) start = obj.get("start_ticks").getAsInt();
                            else if (obj.has("start")) start = obj.get("start").getAsInt();
                            
                            int duration = 60;
                            if (obj.has("duration_tick")) duration = obj.get("duration_tick").getAsInt();
                            else if (obj.has("duration_ticks")) duration = obj.get("duration_ticks").getAsInt();
                            else if (obj.has("duration")) duration = obj.get("duration").getAsInt();
                            
                            String voiceover = obj.has("voiceover") ? obj.get("voiceover").getAsString() : null;
                            String focusTarget = null;
                            if (obj.has("focus_target")) focusTarget = obj.get("focus_target").getAsString();
                            else if (obj.has("focus_npc")) focusTarget = obj.get("focus_npc").getAsString();
                            else if (obj.has("focusNpc")) focusTarget = obj.get("focusNpc").getAsString();
                            else if (obj.has("focus")) focusTarget = obj.get("focus").getAsString();
                            
                            subtitleList.add(new CinematicCameraController.Subtitle(text, start, duration, voiceover, focusTarget));
                            StoryAdventureMod.LOGGER.info("[ClientNetworkHandler] Parsed subtitle: text='{}', start={}, duration={}, voiceover={}, focusTarget={}", 
                                text, start, duration, voiceover, focusTarget);
                        }
                    }
                    config.setSubtitles(subtitleList);
                    StoryAdventureMod.LOGGER.info("[ClientNetworkHandler] Set {} subtitles on config", subtitleList.size());
                }

                // Parse NPC animations
                var animationsJson = payload.getAnimationsAsJson();
                if (animationsJson != null && !animationsJson.isEmpty()) {
                    var animList = new java.util.ArrayList<CinematicCameraController.NPCAnimation>();
                    for (var elem : animationsJson) {
                        if (elem.isJsonObject()) {
                            var obj = elem.getAsJsonObject();
                            int tick = obj.has("tick") ? obj.get("tick").getAsInt() : 0;
                            String npc = obj.has("npc") ? obj.get("npc").getAsString() : "";
                            String pose = obj.has("pose") ? obj.get("pose").getAsString() : "";
                            if (!npc.isEmpty() && !pose.isEmpty()) {
                                animList.add(new CinematicCameraController.NPCAnimation(tick, npc, pose));
                            }
                        }
                    }
                    config.setAnimations(animList);
                    StoryAdventureMod.LOGGER.info("[ClientNetworkHandler] Set {} animations on config", animList.size());
                }
                
                // Start the cutscene
                controller.startCutscene(path, config);
                StoryAdventureMod.LOGGER.info("Started cutscene for instance {}", payload.instanceId());

                // Play cutscene voiceover if provided
                if (payload.voiceover() != null && !payload.voiceover().isEmpty()) {
                    com.warmpixel.storyadventure.client.audio.VoiceoverManager.getInstance().playVoiceover(payload.voiceover(), "narrator");
                }
            }
            case "STOP" -> {
                controller.stopCutscene();
                com.warmpixel.storyadventure.client.audio.VoiceoverManager.getInstance().stopCurrentVoiceover();
                StoryAdventureMod.LOGGER.info("Stopped cutscene for instance {}", payload.instanceId());
            }
            case "SKIP" -> {
                controller.skipCutscene();
                com.warmpixel.storyadventure.client.audio.VoiceoverManager.getInstance().stopCurrentVoiceover();
                StoryAdventureMod.LOGGER.info("Skipped cutscene for instance {}", payload.instanceId());
            }
            default -> StoryAdventureMod.LOGGER.warn("Unknown cutscene action: {}", payload.action());
        }
    }
    
    /**
     * Handle voiceover payload from server.
     * Plays the voiceover audio using VoiceoverManager.
     */
    private static void handleVoiceoverPayload(VoiceoverPayload payload) {
        var voiceoverManager = com.warmpixel.storyadventure.client.audio.VoiceoverManager.getInstance();
        voiceoverManager.playVoiceover(payload.soundPath(), payload.volume(), payload.pitch(), payload.characterId());
        StoryAdventureMod.LOGGER.debug("Playing voiceover: {} (character: {})", payload.soundPath(), payload.characterId());
    }

    private static void handleBGMPayload(BGMPayload payload) {
        var bgmManager = com.warmpixel.storyadventure.client.audio.BGMManager.getInstance();
        if (payload.stop()) {
            bgmManager.stopBGM(payload.fadeTicks());
        } else {
            bgmManager.playBGM(payload.soundPath(), payload.volume(), payload.loop(), payload.fadeTicks());
        }
    }

    public static void sendTutorialClick(String tutorialId) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.warmpixel.storyadventure.network.packet.UITutorialClickPayload(tutorialId)
        );
    }
}
