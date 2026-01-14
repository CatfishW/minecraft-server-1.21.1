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
                StrangerDialogueScreen screen = new StrangerDialogueScreen(
                    "乔伊斯·拜尔斯",
                    "求求你！你一定要帮帮我！威尔...我的儿子威尔失踪了！警察说他可能只是离家出走，但我知道不是这样的。灯...家里的灯一直在闪烁。我知道这很疯狂，但我觉得他在试图联系我。");
                screen.addChoice("help", "我会帮助你", () -> {
                    // This is just a demo/fallback if triggered manually
                });
                screen.addChoice("unsure", "这听起来很奇怪...", () -> {
                });
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
                StrangerHudRenderer.getInstance().show("怪奇物语", "第一章：失踪");
                // Demo data
                var objectives = new java.util.ArrayList<StrangerHudRenderer.ObjectiveEntry>();
                objectives.add(new StrangerHudRenderer.ObjectiveEntry("调查威尔的房间", false, true));
                StrangerHudRenderer.getInstance().setObjectives(objectives);
                StrangerHudRenderer.getInstance().startTimer(300000);
            }
            
            case OpenUIPayload.SCREEN_HUD_HIDE -> {
                StrangerHudRenderer.getInstance().hide();
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
        }
    }

    public static java.util.List<SyncInstancesPayload.InstanceInfo> getLastSyncedInstances() {
        return lastSyncedInstances;
    }
}
