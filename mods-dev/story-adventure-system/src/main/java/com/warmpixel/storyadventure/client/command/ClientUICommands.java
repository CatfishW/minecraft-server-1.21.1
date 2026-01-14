package com.warmpixel.storyadventure.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.warmpixel.storyadventure.client.ui.*;
import com.warmpixel.storyadventure.client.ui.admin.*;
import com.warmpixel.storyadventure.client.ui.hud.StrangerHudRenderer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Client-side commands to open UI screens.
 */
public class ClientUICommands {
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        
        // Main story UI command - renamed to avoid conflict with server command
        dispatcher.register(ClientCommandManager.literal("storyuidev")
            // Story list screen
            .then(ClientCommandManager.literal("stories")
                .executes(ctx -> {
                    Minecraft.getInstance().execute(() -> {
                        StrangerStoryListScreen screen = new StrangerStoryListScreen();
                        screen.addStory("stranger_things_hawkins", "怪奇物语：霍金斯事件", 
                            "1983年，印第安纳州霍金斯镇。揭开神秘失踪案的真相...", 1, 4, 45);
                        screen.addStory("example_story", "示例故事", 
                            "这是一个示例故事，展示系统基本用法。", 1, 4, 15);
                        Minecraft.getInstance().setScreen(screen);
                    });
                    return 1;
                }))
            
            // Lobby/ready screen
            .then(ClientCommandManager.literal("lobby")
                .executes(ctx -> {
                    Minecraft.getInstance().execute(() -> {
                        StrangerLobbyScreen screen = new StrangerLobbyScreen(
                            "怪奇物语：霍金斯事件",
                            "1983年，印第安纳州霍金斯镇。一个神秘的男孩失踪了...",
                            1, 4, 45);
                        screen.setLeader(true);
                        screen.addMember(UUID.randomUUID(), 
                            Minecraft.getInstance().player.getName().getString(), false, true);
                        Minecraft.getInstance().setScreen(screen);
                    });
                    return 1;
                }))
            
            // Dialogue screen demo
            .then(ClientCommandManager.literal("dialogue")
                .executes(ctx -> {
                    Minecraft.getInstance().execute(() -> {
                        StrangerDialogueScreen screen = new StrangerDialogueScreen(
                            "乔伊斯·拜尔斯",
                            "求求你！你一定要帮帮我！威尔...我的儿子威尔失踪了！警察说他可能只是离家出走，但我知道不是这样的。灯...家里的灯一直在闪烁。我知道这很疯狂，但我觉得他在试图联系我。");
                        screen.addChoice("help", "我会帮助你", () -> {
                            Minecraft.getInstance().player.sendSystemMessage(
                                Component.literal("§a你选择了帮助乔伊斯"));
                            Minecraft.getInstance().setScreen(null);
                        });
                        screen.addChoice("unsure", "这听起来很奇怪...", () -> {
                            Minecraft.getInstance().player.sendSystemMessage(
                                Component.literal("§e你表示怀疑"));
                            Minecraft.getInstance().setScreen(null);
                        });
                        Minecraft.getInstance().setScreen(screen);
                    });
                    return 1;
                }))
            
            // Puzzle screen demo
            .then(ClientCommandManager.literal("puzzle")
                .executes(ctx -> {
                    Minecraft.getInstance().execute(() -> {
                        StrangerPuzzleScreen screen = new StrangerPuzzleScreen(
                            "CODE_LOCK", "威尔失踪的年份", 5);
                        Minecraft.getInstance().setScreen(screen);
                    });
                    return 1;
                }))
            
            // Toggle HUD
            .then(ClientCommandManager.literal("hud")
                .then(ClientCommandManager.literal("show")
                    .executes(ctx -> {
                        StrangerHudRenderer.getInstance().show("怪奇物语", "第一章：失踪");
                        var objectives = new java.util.ArrayList<StrangerHudRenderer.ObjectiveEntry>();
                        objectives.add(new StrangerHudRenderer.ObjectiveEntry("调查威尔的房间", false, true));
                        objectives.add(new StrangerHudRenderer.ObjectiveEntry("收集闪烁的灯泡", false, false));
                        objectives.add(new StrangerHudRenderer.ObjectiveEntry("检查后院棚屋", false, false));
                        StrangerHudRenderer.getInstance().setObjectives(objectives);
                        StrangerHudRenderer.getInstance().addClue("黏液痕迹");
                        StrangerHudRenderer.getInstance().startTimer(300000); // 5 minutes
                        ctx.getSource().sendFeedback(Component.literal("§aHUD已显示"));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("hide")
                    .executes(ctx -> {
                        StrangerHudRenderer.getInstance().hide();
                        ctx.getSource().sendFeedback(Component.literal("§eHUD已隐藏"));
                        return 1;
                    })))
            
            // Help
            .executes(ctx -> {
                ctx.getSource().sendFeedback(Component.literal("§6=== 故事UI (客户端开发)命令 ==="));
                ctx.getSource().sendFeedback(Component.literal("§e/storyuidev stories §7- 打开故事列表"));
                ctx.getSource().sendFeedback(Component.literal("§e/storyuidev lobby §7- 打开准备大厅"));
                ctx.getSource().sendFeedback(Component.literal("§e/storyuidev dialogue §7- 演示对话界面"));
                ctx.getSource().sendFeedback(Component.literal("§e/storyuidev puzzle §7- 演示解谜界面"));
                ctx.getSource().sendFeedback(Component.literal("§e/storyuidev hud show/hide §7- 显示/隐藏HUD"));
                ctx.getSource().sendFeedback(Component.literal("§e/storyadminui §7- 管理员控制台"));
                return 1;
            })
        );
        
        // Admin UI command
        dispatcher.register(ClientCommandManager.literal("storyadminuidev")
            // Main dashboard
            .then(ClientCommandManager.literal("dashboard")
                .executes(ctx -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(new AdminDashboardScreen());
                    });
                    return 1;
                }))
            
            // Instance manager
            .then(ClientCommandManager.literal("instances")
                .executes(ctx -> {
                    Minecraft.getInstance().execute(() -> {
                        AdminInstanceManagerScreen screen = new AdminInstanceManagerScreen();
                        screen.addInstance(UUID.randomUUID(), "怪奇物语：霍金斯事件", 
                            "investigate_house", "RUNNING", 2, 180000);
                        screen.addInstance(UUID.randomUUID(), "示例故事", 
                            "first_dialogue", "PAUSED", 1, 60000);
                        Minecraft.getInstance().setScreen(screen);
                    });
                    return 1;
                }))
            
            // Story manager
            .then(ClientCommandManager.literal("stories")
                .executes(ctx -> {
                    Minecraft.getInstance().execute(() -> {
                        AdminStoryManagerScreen screen = new AdminStoryManagerScreen();
                        screen.addStory("stranger_things_hawkins", "怪奇物语：霍金斯事件", 
                            20, "1.0.0", true, "");
                        screen.addStory("example_story", "示例故事", 
                            5, "1.0.0", true, "");
                        Minecraft.getInstance().setScreen(screen);
                    });
                    return 1;
                }))
            
            // Default - open dashboard
            .executes(ctx -> {
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().setScreen(new AdminDashboardScreen());
                });
                return 1;
            })
        );
    }
}
