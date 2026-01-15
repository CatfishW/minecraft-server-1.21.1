package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
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
                            story.minPlayers(), story.maxPlayers(), story.estimatedMinutes());
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
                
                // Update 3D world indicators
                java.util.List<net.minecraft.world.phys.Vec3> destPoints = new java.util.ArrayList<>();
                for (var wpData : payload.waypoints()) {
                    destPoints.add(new net.minecraft.world.phys.Vec3(wpData.x(), wpData.y(), wpData.z()));
                }
                com.warmpixel.storyadventure.client.render.WorldDestinationRenderer.setDestinations(destPoints);
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
        
        StoryAdventureMod.LOGGER.info("Registered client network receivers");
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
                
                // Parse extraData JSON if present
                if (extraData != null && !extraData.isEmpty()) {
                    try {
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(extraData).getAsJsonObject();
                        
                        if (json.has("npcName")) {
                            npcName = json.get("npcName").getAsString();
                        }
                        
                        if (json.has("lines") && json.get("lines").isJsonArray()) {
                            var lines = json.getAsJsonArray("lines");
                            for (int i = 0; i < lines.size(); i++) {
                                if (i > 0) dialogueText.append("\n\n");
                                dialogueText.append(lines.get(i).getAsString());
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
                
                StrangerDialogueScreen screen = new StrangerDialogueScreen(npcName, dialogueText.toString());
                
                // Add choices from JSON
                for (String[] choice : choicesList) {
                    screen.addChoice(choice[0], choice[1], () -> {
                        mc.setScreen(null); // Close dialogue after selection
                    });
                }
                
                // Add default choice if none defined
                if (choicesList.isEmpty()) {
                    screen.addChoice("continue", "继续", () -> {
                        mc.setScreen(null);
                    });
                }
                
                mc.setScreen(screen);
            }
            
            case OpenUIPayload.SCREEN_PUZZLE -> {
                StrangerPuzzleScreen screen = new StrangerPuzzleScreen(
                    "CODE_LOCK", "威尔失踪的年份", 5);
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
                        
                        // Timer if present
                        if (json.has("timer")) {
                            long timerMs = json.get("timer").getAsLong();
                            if (timerMs > 0) {
                                StrangerHudRenderer.getInstance().startTimer(timerMs);
                            }
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
}
