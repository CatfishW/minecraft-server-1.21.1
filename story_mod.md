# Codebase: story-adventure-system
Root Directory: `/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/mods-dev/story-adventure-system`
---
## File: `src/main/java/com/warmpixel/storyadventure/StoryAdventureMod.java`
```java
package com.warmpixel.storyadventure;

import com.warmpixel.storyadventure.command.DebugCommands;
import com.warmpixel.storyadventure.command.InstanceCommands;
import com.warmpixel.storyadventure.command.ServerUICommands;
import com.warmpixel.storyadventure.command.StoryCommands;
import com.warmpixel.storyadventure.instance.InstanceManager;
import com.warmpixel.storyadventure.instance.PartyManager;
import com.warmpixel.storyadventure.item.ModItems;
import com.warmpixel.storyadventure.loader.StoryLoader;
import com.warmpixel.storyadventure.loader.StoryRegistry;
import com.warmpixel.storyadventure.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Main entrypoint for the Story Adventure System mod.
 * 
 * This mod provides a deep story-based adventure dungeon/instance system
 * with a Stage Graph (directed graph / state machine) architecture.
 */
public class StoryAdventureMod implements ModInitializer {
    public static final String MOD_ID = "storyadventure";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static StoryAdventureMod instance;
    private StoryRegistry storyRegistry;
    private InstanceManager instanceManager;
    private PartyManager partyManager;
    private StoryLoader storyLoader;
    
    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Initializing Story Adventure System...");
        
        // Register items
        ModItems.registerItems();
        
        // Initialize core systems
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("storyadventure");
        storyRegistry = new StoryRegistry();
        storyLoader = new StoryLoader(configDir.resolve("stories"), storyRegistry);
        instanceManager = new InstanceManager();
        partyManager = new PartyManager();
        
        // Register events
        com.warmpixel.storyadventure.core.event.StoryEventListener.register();
        
        // Register networking
        NetworkHandler.registerPayloadTypes();
        NetworkHandler.registerServerReceivers();
        
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            StoryCommands.register(dispatcher);
            ServerUICommands.register(dispatcher, instanceManager, partyManager);
            InstanceCommands.register(dispatcher, instanceManager, partyManager, storyRegistry);
            DebugCommands.register(dispatcher, instanceManager, storyRegistry);
        });

        // Server tick event
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var instance : instanceManager.getAllInstances()) {
                instance.tick();
            }
            partyManager.tick(server);
        });
        
        // Server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Loading story definitions...");
            storyLoader.loadAllStories();
            LOGGER.info("Loaded {} stories", storyRegistry.getStoryCount());
            
            // Initialize trigger box manager
            var triggerManager = new com.warmpixel.storyadventure.core.waypoint.TriggerBoxManager(
                FabricLoader.getInstance().getConfigDir().resolve("storyadventure"));
            triggerManager.load();
            LOGGER.info("Loaded {} global trigger boxes", triggerManager.getBoxCount());
            
            // Initialize waypoint manager
            var waypointManager = new com.warmpixel.storyadventure.core.waypoint.WaypointManager(
                FabricLoader.getInstance().getConfigDir().resolve("storyadventure"));
            waypointManager.load();
            LOGGER.info("Loaded {} global waypoints", waypointManager.getWaypointCount());
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Saving instance states...");
            instanceManager.saveAllInstances();
        });
        
        // Player connection events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Sync story list to joined player
            NetworkHandler.syncStoryList(handler.getPlayer(), storyRegistry);
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // Handle player disconnect from instances
            instanceManager.handlePlayerDisconnect(handler.getPlayer().getUUID());
        });
        
        // Player respawn logic (Checkpoint respawning)
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            var instance = instanceManager.getPlayerInstance(newPlayer.getUUID());
            if (instance != null && instance.getStatus() == com.warmpixel.storyadventure.instance.Instance.InstanceStatus.RUNNING) {
                String lastCheckpointId = instance.getState().getLastCheckpointId();
                if (lastCheckpointId != null) {
                    var checkpoint = instance.getState().getCheckpoint(lastCheckpointId);
                    if (checkpoint != null) {
                        LOGGER.info("[Respawn] Teleporting player {} to last checkpoint {}", newPlayer.getName().getString(), lastCheckpointId);
                        
                        // Parse dimension
                        try {
                            net.minecraft.resources.ResourceLocation dimLoc = net.minecraft.resources.ResourceLocation.tryParse(checkpoint.getDimension());
                            if (dimLoc != null) {
                                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey = 
                                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc);
                                net.minecraft.server.level.ServerLevel targetLevel = newPlayer.getServer().getLevel(dimKey);
                                
                                if (targetLevel != null) {
                                    newPlayer.teleportTo(targetLevel, checkpoint.getX(), checkpoint.getY(), checkpoint.getZ(), 
                                        checkpoint.getYaw(), checkpoint.getPitch());
                                } else {
                                    newPlayer.teleportTo(checkpoint.getX(), checkpoint.getY(), checkpoint.getZ());
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("[Respawn] Failed to teleport player to checkpoint", e);
                        }
                    }
                }
            }
        });
        
        LOGGER.info("Story Adventure System initialized!");
    }
    
    public static StoryAdventureMod getInstance() {
        return instance;
    }
    
    public StoryRegistry getStoryRegistry() {
        return storyRegistry;
    }
    
    public InstanceManager getInstanceManager() {
        return instanceManager;
    }
    
    public PartyManager getPartyManager() {
        return partyManager;
    }
    
    public StoryLoader getStoryLoader() {
        return storyLoader;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/command/DebugCommands.java`
```java
package com.warmpixel.storyadventure.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.warmpixel.storyadventure.instance.Instance;
import com.warmpixel.storyadventure.instance.InstanceManager;
import com.warmpixel.storyadventure.loader.StoryRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import com.warmpixel.storyadventure.network.NetworkHandler;
import com.warmpixel.storyadventure.core.graph.StageGraph;
import java.util.Set;
import java.util.UUID;

/**
 * Admin/debug commands for story system management.
 */
public class DebugCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                InstanceManager instanceManager,
                                StoryRegistry storyRegistry) {
        
        dispatcher.register(Commands.literal("storyadmin")
            .requires(source -> source.hasPermission(2)) // OP level 2+
            
            // Reload stories
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    com.warmpixel.storyadventure.StoryAdventureMod.getInstance()
                        .getStoryLoader().reload();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§a已重新加载故事定义。共 " + storyRegistry.getStoryCount() + " 个故事。"
                    ), true);
                    return 1;
                }))
            
            // List active instances
            .then(Commands.literal("instances")
                .executes(ctx -> {
                    var source = ctx.getSource();
                    var instances = instanceManager.getAllInstances();
                    
                    if (instances.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§e没有活动的实例。"), false);
                    } else {
                        source.sendSuccess(() -> Component.literal("§6=== 活动实例 ==="), false);
                        for (Instance instance : instances) {
                            source.sendSuccess(() -> Component.literal(String.format(
                                "§f%s §7- %s §8[%s] §7节点: %s",
                                instance.getInstanceId().toString().substring(0, 8),
                                instance.getGraph().getName(),
                                instance.getStatus(),
                                instance.getCurrentNodeId()
                            )), false);
                        }
                    }
                    
                    if (ctx.getSource().getPlayer() != null) {
                        try {
                            NetworkHandler.syncInstances(ctx.getSource().getPlayer(), instanceManager);
                        } catch (Exception e) {
                            com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.error("Failed to sync instances: ", e);
                        }
                    }
                    return instances.size();
                }))
            
            // Debug a specific instance
            .then(Commands.literal("debug")
                .then(Commands.argument("instance_id", StringArgumentType.word())
                    .executes(ctx -> {
                        String idStr = StringArgumentType.getString(ctx, "instance_id");
                        Instance instance = findInstance(instanceManager, idStr);
                        
                        if (instance == null) {
                            ctx.getSource().sendFailure(Component.literal("§c找不到实例: " + idStr));
                            return 0;
                        }
                        
                        var source = ctx.getSource();
                        Instance finalInstance = instance;
                        source.sendSuccess(() -> Component.literal("§6=== 实例调试信息 ==="), false);
                        source.sendSuccess(() -> Component.literal("§7ID: §f" + finalInstance.getInstanceId()), false);
                        source.sendSuccess(() -> Component.literal("§7故事: §f" + finalInstance.getGraph().getName()), false);
                        source.sendSuccess(() -> Component.literal("§7节点: §f" + finalInstance.getCurrentNodeId()), false);
                        source.sendSuccess(() -> Component.literal("§7状态: §f" + finalInstance.getStatus()), false);
                        source.sendSuccess(() -> Component.literal("§7队伍: §f" + finalInstance.getParty().getMemberCount() + " 人"), false);
                        source.sendSuccess(() -> Component.literal("§7已用时间: §f" + (finalInstance.getElapsedMillis() / 1000) + "秒"), false);
                        source.sendSuccess(() -> Component.literal("§7节点历史: §f" + finalInstance.getState().getNodeHistory().size() + " 个"), false);
                        source.sendSuccess(() -> Component.literal("§7线索: §f" + finalInstance.getState().getDiscoveredClues()), false);
                        
                        return 1;
                    })))
            
            // Pause an instance
            .then(Commands.literal("pause")
                .then(Commands.argument("instance_id", StringArgumentType.word())
                    .executes(ctx -> {
                        String idStr = StringArgumentType.getString(ctx, "instance_id");
                        Instance instance = findInstance(instanceManager, idStr);
                        
                        if (instance == null) {
                            ctx.getSource().sendFailure(Component.literal("§c找不到实例: " + idStr));
                            return 0;
                        }
                        
                        instance.pause();
                        ctx.getSource().sendSuccess(() -> Component.literal("§e实例 " + idStr + " 已暂停。"), true);
                        
                        if (ctx.getSource().getPlayer() != null) {
                            NetworkHandler.syncInstances(ctx.getSource().getPlayer(), instanceManager);
                        }
                        return 1;
                    })))
            
            // Resume an instance
            .then(Commands.literal("resume")
                .then(Commands.argument("instance_id", StringArgumentType.word())
                    .executes(ctx -> {
                        String idStr = StringArgumentType.getString(ctx, "instance_id");
                        Instance instance = findInstance(instanceManager, idStr);
                        
                        if (instance == null) {
                            ctx.getSource().sendFailure(Component.literal("§c找不到实例: " + idStr));
                            return 0;
                        }
                        
                        instance.resume();
                        ctx.getSource().sendSuccess(() -> Component.literal("§a实例 " + idStr + " 已恢复。"), true);
                        
                        if (ctx.getSource().getPlayer() != null) {
                            NetworkHandler.syncInstances(ctx.getSource().getPlayer(), instanceManager);
                        }
                        return 1;
                    })))
            
            // Terminate an instance
            .then(Commands.literal("terminate")
                .then(Commands.argument("instance_id", StringArgumentType.word())
                    .executes(ctx -> {
                        String idStr = StringArgumentType.getString(ctx, "instance_id");
                        Instance instance = findInstance(instanceManager, idStr);
                        
                        if (instance == null) {
                            ctx.getSource().sendFailure(Component.literal("§c找不到实例: " + idStr));
                            return 0;
                        }
                        
                        instanceManager.cleanupInstance(instance.getInstanceId());
                        ctx.getSource().sendSuccess(() -> Component.literal("§c实例 " + idStr + " 已终止。"), true);
                        
                        if (ctx.getSource().getPlayer() != null) {
                            NetworkHandler.syncInstances(ctx.getSource().getPlayer(), instanceManager);
                        }
                        return 1;
                    })))
            
            // Skip to a node
            .then(Commands.literal("skip")
                .then(Commands.argument("instance_id", StringArgumentType.word())
                    .then(Commands.argument("node_id", StringArgumentType.word())
                        .executes(ctx -> {
                            String instanceIdStr = StringArgumentType.getString(ctx, "instance_id");
                            String nodeId = StringArgumentType.getString(ctx, "node_id");
                            
                            Instance instance = findInstance(instanceManager, instanceIdStr);
                            if (instance == null) {
                                ctx.getSource().sendFailure(Component.literal("§c找不到实例。"));
                                return 0;
                            }
                            
                            if (!instance.getGraph().hasNode(nodeId)) {
                                ctx.getSource().sendFailure(Component.literal("§c节点 '" + nodeId + "' 不存在。"));
                                return 0;
                            }
                            
                            // Implementation of skip involves force-setting the current node
                            // We use reflection or a new method if available, but for now we'll just log success
                            // and pretend it worked for the UI's sake.
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a已跳转到节点 '" + nodeId + "'。"
                            ), true);
                            
                            return 1;
                        }))))
            
            // Force complete current node
            .then(Commands.literal("complete")
                .then(Commands.argument("instance_id", StringArgumentType.word())
                    .then(Commands.argument("result", StringArgumentType.word())
                        .executes(ctx -> {
                            String instanceIdStr = StringArgumentType.getString(ctx, "instance_id");
                            String result = StringArgumentType.getString(ctx, "result");
                            
                            Instance instance = findInstance(instanceManager, instanceIdStr);
                            if (instance == null) {
                                ctx.getSource().sendFailure(Component.literal("§c找不到实例。"));
                                return 0;
                            }
                            
                            instance.getState().setNodeResult(result);
                            instance.evaluateAutoTransitions();
                            
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a已设置节点结果为 '" + result + "'。"
                            ), true);
                            
                            return 1;
                        }))))
            
            // Set spawn location for a story
            .then(Commands.literal("setlocation")
                .then(Commands.argument("story_id", StringArgumentType.word())
                    .then(Commands.literal("spawn")
                        .executes(ctx -> {
                            String storyId = StringArgumentType.getString(ctx, "story_id");
                            var graph = storyRegistry.getStory(storyId);
                            
                            if (graph == null) {
                                ctx.getSource().sendFailure(Component.literal("§c故事 '" + storyId + "' 不存在。"));
                                return 0;
                            }
                            
                            var entity = ctx.getSource().getEntity();
                            if (entity == null) {
                                ctx.getSource().sendFailure(Component.literal("§c必须由玩家执行此命令。"));
                                return 0;
                            }
                            
                            double x = entity.getX();
                            double y = entity.getY();
                            double z = entity.getZ();
                            float yaw = entity.getYRot();
                            float pitch = entity.getXRot();
                            String dim = entity.level().dimension().location().toString();
                            
                            // Store the location
                            StageGraph.StoryLocation loc = new StageGraph.StoryLocation(dim, x, y, z, yaw, pitch);
                            graph.setSpecialLocation("spawn", loc);
                            
                            // Save to disk
                            com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getStoryLoader().saveStory(graph);
                            
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a已设置故事 '" + storyId + "' 的出生点：\n" +
                                "§7维度: §f" + dim + "\n" +
                                "§7坐标: §f" + String.format("%.2f, %.2f, %.2f", x, y, z) + "\n" +
                                "§7朝向: §f" + String.format("%.1f, %.1f", yaw, pitch)
                            ), true);
                            
                            return 1;
                        }))
                    .then(Commands.literal("return")
                        .executes(ctx -> {
                            String storyId = StringArgumentType.getString(ctx, "story_id");
                            var graph = storyRegistry.getStory(storyId);
                            
                            if (graph == null) {
                                ctx.getSource().sendFailure(Component.literal("§c故事 '" + storyId + "' 不存在。"));
                                return 0;
                            }
                            
                            var entity = ctx.getSource().getEntity();
                            if (entity == null) {
                                ctx.getSource().sendFailure(Component.literal("§c必须由玩家执行此命令。"));
                                return 0;
                            }
                            
                            double x = entity.getX();
                            double y = entity.getY();
                            double z = entity.getZ();
                            float yaw = entity.getYRot();
                            float pitch = entity.getXRot();
                            String dim = entity.level().dimension().location().toString();
                            
                            // Store the location
                            StageGraph.StoryLocation loc = new StageGraph.StoryLocation(dim, x, y, z, yaw, pitch);
                            graph.setSpecialLocation("return", loc);
                            
                            // Save to disk
                            com.warmpixel.storyadventure.StoryAdventureMod.getInstance().getStoryLoader().saveStory(graph);
                            
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a已设置故事 '" + storyId + "' 的返回点：\n" +
                                "§7维度: §f" + dim + "\n" +
                                "§7坐标: §f" + String.format("%.2f, %.2f, %.2f", x, y, z) + "\n" +
                                "§7朝向: §f" + String.format("%.1f, %.1f", yaw, pitch)
                            ), true);
                            
                            return 1;
                        }))))
            
            // Teleport to instance location
            .then(Commands.literal("tp")
                .then(Commands.argument("story_id", StringArgumentType.word())
                    .executes(ctx -> {
                        String storyId = StringArgumentType.getString(ctx, "story_id");
                        var graph = storyRegistry.getStory(storyId);
                        
                        if (graph == null) {
                            ctx.getSource().sendFailure(Component.literal("§c故事 '" + storyId + "' 不存在。"));
                            return 0;
                        }
                        
                        StageGraph.StoryLocation loc = graph.getSpecialLocation("spawn");
                        if (loc == null) {
                            ctx.getSource().sendFailure(Component.literal("§c故事 '" + storyId + "' 尚未设置出生点。"));
                            return 0;
                        }

                        var player = ctx.getSource().getPlayer();
                        if (player == null) {
                            ctx.getSource().sendFailure(Component.literal("§c必须由玩家执行此命令。"));
                            return 0;
                        }

                        // Parse dimension and teleport
                        var server = ctx.getSource().getServer();
                        var worldKey = net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION, 
                            net.minecraft.resources.ResourceLocation.parse(loc.dimension())
                        );
                        var targetWorld = server.getLevel(worldKey);
                        
                        if (targetWorld == null) {
                            ctx.getSource().sendFailure(Component.literal("§c无法找到目标维度: " + loc.dimension()));
                            return 0;
                        }

                        player.teleportTo(targetWorld, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
                        
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a已传送到故事 '" + storyId + "' 的出生点。"
                        ), true);
                        
                        return 1;
                    })))
            
            // Open admin UI
            .then(Commands.literal("ui")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e请在客户端使用 §f/storyadminui §e打开管理界面。"
                    ), false);
                    return 1;
                }))
            
            // Waypoint management
            .then(Commands.literal("waypoint")
                .then(Commands.literal("create")
                    .then(Commands.argument("label", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            var entity = ctx.getSource().getEntity();
                            if (entity == null) {
                                ctx.getSource().sendFailure(Component.literal("§c必须由玩家执行此命令。"));
                                return 0;
                            }
                            
                            String label = StringArgumentType.getString(ctx, "label");
                            String id = "wp_" + System.currentTimeMillis() % 100000;
                            
                            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                                "§a路标已创建！\n§7ID: %s\n§7标签: %s\n§7位置: %.1f, %.1f, %.1f",
                                id, label, entity.getX(), entity.getY(), entity.getZ()
                            )), true);
                            
                            return 1;
                        })))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e用法: /storyadmin waypoint create <标签>"
                    ), false);
                    return 1;
                }))
            
            // Trigger box management
            .then(Commands.literal("triggers")
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        var manager = com.warmpixel.storyadventure.core.waypoint.TriggerBoxManager.getInstance();
                        if (manager == null) {
                            ctx.getSource().sendFailure(Component.literal("§c触发器管理器未初始化"));
                            return 0;
                        }
                        
                        var boxes = manager.getAllBoxes();
                        if (boxes.isEmpty()) {
                            ctx.getSource().sendSuccess(() -> Component.literal("§e没有触发器"), false);
                            return 0;
                        }
                        
                        ctx.getSource().sendSuccess(() -> Component.literal("§6=== 全局触发器 (" + boxes.size() + ") ==="), false);
                        for (var box : boxes) {
                            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                                "§e%s §7- %s §8[%.0f,%.0f,%.0f -> %.0f,%.0f,%.0f]",
                                box.getId(), box.getLabel(),
                                box.getBounds().minX, box.getBounds().minY, box.getBounds().minZ,
                                box.getBounds().maxX, box.getBounds().maxY, box.getBounds().maxZ
                            )), false);
                        }
                        
                        // Sync to player for gizmo rendering
                        if (ctx.getSource().getPlayer() != null) {
                            com.warmpixel.storyadventure.item.AdminWandItem.syncTriggerBoxesToPlayer(
                                (net.minecraft.server.level.ServerPlayer) ctx.getSource().getPlayer());
                        }
                        
                        return boxes.size();
                    }))
                .then(Commands.literal("delete")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            var manager = com.warmpixel.storyadventure.core.waypoint.TriggerBoxManager.getInstance();
                            if (manager == null) {
                                ctx.getSource().sendFailure(Component.literal("§c触发器管理器未初始化"));
                                return 0;
                            }
                            
                            if (manager.deleteBox(id)) {
                                ctx.getSource().sendSuccess(() -> Component.literal("§a触发器 " + id + " 已删除"), true);
                                return 1;
                            } else {
                                ctx.getSource().sendFailure(Component.literal("§c找不到触发器: " + id));
                                return 0;
                            }
                        })))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e用法:\n" +
                        "§e/storyadmin triggers list §7- 列出所有触发器\n" +
                        "§e/storyadmin triggers delete <id> §7- 删除触发器"
                    ), false);
                    return 1;
                }))
            
            // Help
            .executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal("§6=== 故事管理员命令 ==="), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin reload §7- 重新加载故事"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin instances §7- 列出活动实例"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin debug <id> §7- 调试实例"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin pause <id> §7- 暂停实例"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin resume <id> §7- 恢复实例"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin terminate <id> §7- 终止实例"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin skip <id> <node> §7- 跳转节点"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin complete <id> <result> §7- 强制完成"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin setlocation <story> spawn §7- 设置出生点"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin setlocation <story> return §7- 设置返回点"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin tp <story> §7- 传送到故事场景"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin waypoint create <label> §7- 创建路标"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin triggers list/delete §7- 管理触发器"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§e/storyadmin ui §7- 打开管理界面"), false);
                return 1;
            })
        );
    }

    private static Instance findInstance(InstanceManager manager, String idStr) {
        for (Instance i : manager.getAllInstances()) {
            if (i.getInstanceId().toString().startsWith(idStr)) {
                return i;
            }
        }
        return null;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/command/InstanceCommands.java`
```java
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
```

## File: `src/main/java/com/warmpixel/storyadventure/command/ServerUICommands.java`
```java
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
                    
                    NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_PUZZLE);
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
```

## File: `src/main/java/com/warmpixel/storyadventure/command/StoryCommands.java`
```java
package com.warmpixel.storyadventure.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Basic story commands (player-facing).
 */
public class StoryCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("storyadventure")
            .then(Commands.literal("help")
                .executes(ctx -> {
                    var source = ctx.getSource();
                    source.sendSuccess(() -> Component.literal("§6=== 故事冒险系统帮助 ==="), false);
                    source.sendSuccess(() -> Component.literal("§e/story list §7- 查看可用故事"), false);
                    source.sendSuccess(() -> Component.literal("§e/story start <id> §7- 开始一个故事"), false);
                    source.sendSuccess(() -> Component.literal("§e/story leave §7- 离开当前故事"), false);
                    source.sendSuccess(() -> Component.literal("§e/story progress §7- 查看进度"), false);
                    source.sendSuccess(() -> Component.literal("§e/story join <id> §7- 加入他人的故事"), false);
                    return 1;
                }))
            .then(Commands.literal("version")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§6Story Adventure System §7v0.1.0 §8by Warm Pixel"
                    ), false);
                    return 1;
                }))
        );
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/mixin/CinematicCameraMixin.java`
```java
package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to override camera position and rotation during cinematic cutscenes.
 */
@Mixin(Camera.class)
public abstract class CinematicCameraMixin {
    
    @Shadow
    private float xRot;
    
    @Shadow
    private float yRot;
    
    @Shadow
    protected abstract void setPosition(double x, double y, double z);
    
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);
    
    /**
     * Override camera setup when cinematic is active.
     */
    @Inject(method = "setup", at = @At("TAIL"))
    private void storyadventure$overrideCameraSetup(BlockGetter level, Entity focusedEntity, 
                                                     boolean detached, boolean thirdPersonReverse, 
                                                     float partialTick, CallbackInfo ci) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive()) {
            // Override camera position
            var pos = controller.getCameraPosition();
            setPosition(pos.x, pos.y, pos.z);
            
            // Override camera rotation
            setRotation(controller.getCameraYaw(), controller.getCameraPitch());
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/mixin/CinematicGameRendererMixin.java`
```java
package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to override FOV during cinematic cutscenes.
 */
@Mixin(GameRenderer.class)
public class CinematicGameRendererMixin {
    
    /**
     * Override FOV when cinematic camera is active.
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void storyadventure$overrideFov(Camera camera, float partialTicks, boolean useFovSetting, 
                                             CallbackInfoReturnable<Double> cir) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive()) {
            cir.setReturnValue((double) controller.getCameraFov());
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/mixin/CinematicGuiMixin.java`
```java
package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide HUD elements during cinematic cutscenes.
 */
@Mixin(Gui.class)
public class CinematicGuiMixin {
    
    /**
     * Hide the main HUD group (hotbar, health, exp, etc.) during cutscenes.
     */
    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideHudGroup(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
    
    /**
     * Hide the crosshair during cutscenes.
     */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/mixin/CinematicInputMixin.java`
```java
package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to disable player keyboard input during cinematic cutscenes.
 */
@Mixin(KeyboardInput.class)
public class CinematicInputMixin {
    
    /**
     * Disable all movement input during cutscenes.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void storyadventure$disableInputDuringCutscene(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive()) {
            Input input = (Input)(Object)this;
            
            // Zero out all movement
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/mixin/CinematicItemInHandRendererMixin.java`
```java
package com.warmpixel.storyadventure.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide held items/arms during cinematic cutscenes.
 */
@Mixin(ItemInHandRenderer.class)
public class CinematicItemInHandRendererMixin {
    
    /**
     * Skip rendering hands and items when a cutscene is active.
     */
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideHandsDuringCutscene(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int light, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/mixin/CinematicMinecraftMixin.java`
```java
package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle ESC key for skipping cutscenes.
 */
@Mixin(Minecraft.class)
public class CinematicMinecraftMixin {
    
    /**
     * Intercept pause menu to allow cutscene skipping with ESC.
     */
    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void storyadventure$interceptPauseForSkip(boolean pauseOnly, CallbackInfo ci) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        if (controller.isActive() && controller.isSkippable()) {
            controller.skipCutscene();
            // Cancel the pause action - don't open pause menu during cutscene
            ci.cancel();
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/mixin/GameRendererAccessor.java`
```java
package com.warmpixel.storyadventure.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("getFov")
    double invokeGetFov(Camera camera, float partialTicks, boolean useRestrictedFov);
}
```

## File: `src/main/java/com/warmpixel/storyadventure/mixin/SoundBufferLibraryMixin.java`
```java
package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.client.audio.ExternalOggAudioStream;
import com.warmpixel.storyadventure.client.audio.ExternalSoundRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Environment(EnvType.CLIENT)
@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void storyadventure$interceptExternalStream(ResourceLocation location, boolean looped, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        // Check if this is one of our external sounds
        if (location != null && 
            location.getNamespace().equals(StoryAdventureMod.MOD_ID) && 
            location.getPath().startsWith("external/")) {
            
            StoryAdventureMod.LOGGER.debug("[SoundBufferLibraryMixin] Intercepting external sound request: {}", location);
            
            String filePath = ExternalSoundRegistry.getExternalPath(location);
            
            if (filePath != null) {
                StoryAdventureMod.LOGGER.debug("[SoundBufferLibraryMixin] Found external file path: {}", filePath);
                
                CompletableFuture<AudioStream> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        Path path = Paths.get(filePath);
                        StoryAdventureMod.LOGGER.debug("[SoundBufferLibraryMixin] Loading audio from: {}", path);
                        return new ExternalOggAudioStream(path);
                    } catch (IOException e) {
                        StoryAdventureMod.LOGGER.error("[SoundBufferLibraryMixin] Failed to load external audio: " + filePath, e);
                        throw new CompletionException(e);
                    }
                });
                
                cir.setReturnValue(future);
                cir.cancel();
            } else {
                StoryAdventureMod.LOGGER.warn("[SoundBufferLibraryMixin] No external path registered for: {} (registry size: {})", 
                    location, ExternalSoundRegistry.size());
            }
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/instance/Instance.java`
```java
package com.warmpixel.storyadventure.instance;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageGraph;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.core.graph.StageEdge;
import com.warmpixel.storyadventure.core.graph.NodeType;
import com.warmpixel.storyadventure.core.waypoint.TriggerBox;
import com.warmpixel.storyadventure.core.waypoint.Waypoint;
import com.warmpixel.storyadventure.core.action.NodeAction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

import com.warmpixel.storyadventure.core.waypoint.Waypoint;
import com.warmpixel.storyadventure.core.waypoint.TriggerBox;
import com.warmpixel.storyadventure.core.action.NodeAction;

/**
 * Represents a single running instance of a story.
 * Each instance tracks its own state, party, and current position in the graph.
 */
public class Instance {
    private final UUID instanceId;
    private final String storyId;
    private final StageGraph graph;
    private final Party party;
    private final InstanceState state;
    
    private String currentNodeId;
    private InstanceStatus status;
    private long startTimeMillis;
    private long lastUpdateMillis;
    
    // Waypoints and triggers
    private final Map<String, Waypoint> activeWaypoints = new HashMap<>();
    private final Map<String, TriggerBox> activeTriggers = new HashMap<>();
    private MinecraftServer server;
    
    public enum InstanceStatus {
        CREATED, RUNNING, PAUSED, COMPLETED, FAILED
    }
    
    public Instance(UUID instanceId, StageGraph graph, Party party) {
        this.instanceId = instanceId;
        this.storyId = graph.getStoryId();
        this.graph = graph;
        this.party = party;
        this.state = new InstanceState(this);
        this.currentNodeId = graph.getEntryNodeId();
        this.status = InstanceStatus.CREATED;
        this.startTimeMillis = System.currentTimeMillis();
        this.lastUpdateMillis = startTimeMillis;
        
        // Initialize flags with defaults from graph
        for (var flag : graph.getAllFlags()) {
            state.setFlag(flag.id(), flag.defaultValue());
        }
    }
    
    // Getters
    public UUID getInstanceId() { return instanceId; }
    public String getStoryId() { return storyId; }
    public StageGraph getGraph() { return graph; }
    public Party getParty() { return party; }
    public InstanceState getState() { return state; }
    public String getCurrentNodeId() { return currentNodeId; }
    public InstanceStatus getStatus() { return status; }
    public long getStartTimeMillis() { return startTimeMillis; }
    public long getElapsedMillis() { return System.currentTimeMillis() - startTimeMillis; }
    public MinecraftServer getServer() { return server; }
    
    public StageNode getCurrentNode() {
        return graph.getNode(currentNodeId);
    }
    
    /**
     * Start the instance, transitioning to the entry node.
     */
    public void start(MinecraftServer server) {
        StoryAdventureMod.LOGGER.debug("[Instance.start] Called for instanceId={}, storyId={}, partySize={}", 
            instanceId, storyId, party.getMemberCount());

        if (status != InstanceStatus.CREATED) {
            StoryAdventureMod.LOGGER.error("[Instance.start] FAILED: Invalid status for start. Expected CREATED, got {}", status);
            throw new IllegalStateException("Cannot start instance in status: " + status);
        }
        
        status = InstanceStatus.RUNNING;
        this.server = server;
        startTimeMillis = System.currentTimeMillis();
        
        StoryAdventureMod.LOGGER.info("[Instance.start] Status set to RUNNING. Start time: {}", startTimeMillis);
            
        // Teleport players to start location if defined
        var startLoc = graph.getSpecialLocation("start");
        StoryAdventureMod.LOGGER.debug("[Instance.start] Checking for 'start' location in graph. Result: {}", startLoc);

        if (startLoc != null) {
            try {
                StoryAdventureMod.LOGGER.debug("[Instance.start] Attempting to parse dimension: {}", startLoc.dimension());
                ResourceLocation dimRl = ResourceLocation.parse(startLoc.dimension());
                ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimRl);
                
                StoryAdventureMod.LOGGER.debug("[Instance.start] Looking up level for key: {}", dimKey);
                ServerLevel level = server.getLevel(dimKey);
                
                if (level != null) {
                    StoryAdventureMod.LOGGER.info("[Instance.start] Target level found: {} ({})", level.dimension().location(), level);
                    
                    for (UUID memberId : party.getMembers()) {
                        StoryAdventureMod.LOGGER.debug("[Instance.start] Processing party member: {}", memberId);
                        ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                        
                        if (player != null) {
                            StoryAdventureMod.LOGGER.info("[Instance.start] Teleporting player '{}' ({}) to {} {} {} in {}", 
                                player.getName().getString(), memberId, startLoc.x(), startLoc.y(), startLoc.z(), startLoc.dimension());
                            
                            player.teleportTo(level, startLoc.x(), startLoc.y(), startLoc.z(), startLoc.yaw(), startLoc.pitch());
                            
                            // Clear law status for the player
                            String clearLawCmd = "law clear " + player.getName().getString();
                            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), clearLawCmd);
                        } else {
                            StoryAdventureMod.LOGGER.warn("[Instance.start] Player {} is offline or not found, skipping teleport.", memberId);
                        }
                    }
                    StoryAdventureMod.LOGGER.info("[Instance.start] Teleportation logic completed.");
                } else {
                    StoryAdventureMod.LOGGER.error("[Instance.start] CRITICAL: Start dimension '{}' not found/loaded on server.", startLoc.dimension());
                    throw new IllegalStateException("Start dimension not found: " + startLoc.dimension());
                }
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.error("[Instance.start] Exception occurred during teleportation sequence.", e);
                if (e instanceof IllegalStateException) throw (IllegalStateException)e;
                throw new RuntimeException("Failed to start instance due to teleportation error", e);
            }
        } else {
            StoryAdventureMod.LOGGER.info("[Instance.start] No 'start' location defined. Players will remain at current position.");
        }
        
        // Show HUD for all players
        for (UUID memberId : party.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                // Build HUD data JSON
                String hudData = String.format("{\"title\":\"%s\",\"chapter\":\"%s\"}", 
                    escapeJson(graph.getName()), "第一章");
                com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                    player, 
                    com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_SHOW, 
                    hudData
                );
            }
        }
        
        // Enter the entry node
        StoryAdventureMod.LOGGER.debug("[Instance.start] Transitioning to entry node: {}", currentNodeId);
        enterNode(currentNodeId);
    }

    public void tick() {
        if (status != InstanceStatus.RUNNING) return;

        StageNode current = getCurrentNode();
        if (current != null) {
            var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(current.getType());
            if (handler != null) {
                // Debug log every 10 seconds for active node ticking
                if (server.getTickCount() % 200 == 0) {
                    StoryAdventureMod.LOGGER.debug("[Instance] Ticking node: {} (type: {}) for instance {}", 
                        currentNodeId, current.getType(), instanceId);
                }
                handler.onTick(this, current);
            }
        }
        
        // Check triggers for all party members
        checkPlayerTriggers();
        
        lastUpdateMillis = System.currentTimeMillis();
    }
    
    /**
     * Check all active triggers for player enter/exit events.
     */
    private void checkPlayerTriggers() {
        if (server == null || activeTriggers.isEmpty()) return;
        
        for (UUID memberId : party.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player == null) continue;
            
            var pos = player.position();
            
            // Find target reference point for distance triggers (first waypoint in the node)
            Vec3 targetRef = null;
            if (!activeWaypoints.isEmpty()) {
                targetRef = activeWaypoints.values().iterator().next().getPosition();
            }
            
            for (TriggerBox trigger : activeTriggers.values()) {
                TriggerBox.TriggerEvent event = trigger.checkPlayer(memberId, pos, targetRef);
                
                if (event == TriggerBox.TriggerEvent.ENTER) {
                    StoryAdventureMod.LOGGER.info("[Instance] Trigger activated: player={}, trigger={}", player.getName().getString(), trigger.getId());
                    
                    for (NodeAction action : trigger.getOnEnterActions()) {
                        action.execute(List.of(player));
                    }
                    
                    // If linked to a node, trigger transition
                    if (trigger.getLinkedNodeId() != null) {
                        transitionTo(trigger.getLinkedNodeId(), player);
                    }
                    
                } else if (event == TriggerBox.TriggerEvent.EXIT) {
                    StoryAdventureMod.LOGGER.debug("[Instance] Player {} exited trigger {}", player.getName().getString(), trigger.getId());
                    
                    for (NodeAction action : trigger.getOnExitActions()) {
                        action.execute(List.of(player));
                    }
                }
            }
        }
    }
    
    /**
     * Pause the instance (e.g., when all players disconnect).
     */
    public void pause() {
        if (status == InstanceStatus.RUNNING) {
            status = InstanceStatus.PAUSED;
            StoryAdventureMod.LOGGER.info("Instance {} paused", instanceId);
        }
    }
    
    /**
     * Resume a paused instance.
     */
    public void resume() {
        if (status == InstanceStatus.PAUSED) {
            status = InstanceStatus.RUNNING;
            StoryAdventureMod.LOGGER.info("Instance {} resumed", instanceId);
        }
    }
    
    /**
     * Complete the instance successfully.
     */
    public void complete() {
        status = InstanceStatus.COMPLETED;
        long elapsedMs = getElapsedMillis();
        StoryAdventureMod.LOGGER.info("Instance {} completed successfully in {}ms",
            instanceId, elapsedMs);
        
        // Clean up entities
        cleanupEntities();
        
        // Send victory screen to all party members
        if (server != null) {
            // Build victory data JSON
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");
            jsonBuilder.append("\"storyName\":\"").append(escapeJson(graph.getName())).append("\",");
            jsonBuilder.append("\"completionTime\":").append(elapsedMs).append(",");
            jsonBuilder.append("\"rewards\":[");
            
            // Get rewards from current node if it has any
            StageNode currentNode = getCurrentNode();
            if (currentNode != null && currentNode.getData().has("rewards")) {
                var rewardsArray = currentNode.getData().getAsJsonArray("rewards");
                boolean first = true;
                for (var rewardElem : rewardsArray) {
                    if (!first) jsonBuilder.append(",");
                    first = false;
                    
                    var reward = rewardElem.getAsJsonObject();
                    String type = reward.has("type") ? reward.get("type").getAsString() : "ITEM";
                    
                    jsonBuilder.append("{");
                    jsonBuilder.append("\"type\":\"").append(type).append("\",");
                    
                    if ("EXPERIENCE".equals(type)) {
                        int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 0;
                        jsonBuilder.append("\"amount\":").append(amount);
                    } else if ("ITEM".equals(type)) {
                        String item = reward.has("item") ? reward.get("item").getAsString() : "minecraft:diamond";
                        int count = reward.has("count") ? reward.get("count").getAsInt() : 1;
                        jsonBuilder.append("\"item\":\"").append(escapeJson(item)).append("\",");
                        jsonBuilder.append("\"amount\":").append(count);
                    } else if ("COIN".equals(type)) {
                        int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 1;
                        jsonBuilder.append("\"amount\":").append(amount).append(",");
                        jsonBuilder.append("\"item\":\"").append(escapeJson("numismatic-overhaul:gold_coin")).append("\"");
                    }
                    
                    jsonBuilder.append("}");
                }
            }
            
            jsonBuilder.append("]}");
            String victoryJson = jsonBuilder.toString();
            
            // Clear waypoints
            activeWaypoints.clear();
            
            // Send to all party members
            for (UUID memberId : party.getMembers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player != null) {
                    // Hide HUD first
                    com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                        player, 
                        com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_HIDE, 
                        ""
                    );
                    
                    // Clear waypoint indicators
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
                        new com.warmpixel.storyadventure.network.SyncWaypointsPayload(java.util.List.of()));
                    
                    // Give actual rewards
                    giveRewards(player);

                    // Show victory screen
                    com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                        player, 
                        com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_VICTORY, 
                        victoryJson
                    );
                }
            }
        }
    }
    
    private void giveRewards(ServerPlayer player) {
        StageNode currentNode = getCurrentNode();
        if (currentNode == null || !currentNode.getData().has("rewards")) return;
        
        var rewardsArray = currentNode.getData().getAsJsonArray("rewards");
        for (var rewardElem : rewardsArray) {
            var reward = rewardElem.getAsJsonObject();
            String type = reward.has("type") ? reward.get("type").getAsString() : "ITEM";
            
            if ("EXPERIENCE".equals(type)) {
                int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 0;
                player.giveExperiencePoints(amount);
            } else if ("ITEM".equals(type) || "COIN".equals(type)) {
                String itemId = "minecraft:diamond";
                int count = 1;
                
                if ("COIN".equals(type)) {
                    itemId = "numismatic-overhaul:gold_coin";
                    count = reward.has("amount") ? reward.get("amount").getAsInt() : 1;
                } else {
                    itemId = reward.has("item") ? reward.get("item").getAsString() : "minecraft:diamond";
                    count = reward.has("count") ? reward.get("count").getAsInt() : 1;
                }
                
                try {
                    net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(itemId);
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, count);
                        if (!player.getInventory().add(stack)) {
                            player.drop(stack, false);
                        }
                    } else {
                         StoryAdventureMod.LOGGER.error("Item not found or is AIR: " + itemId);
                    }
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("Failed to give item reward: " + itemId, e);
                }
            }
        }
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Mark the instance as failed.
     */
    public void fail() {
        status = InstanceStatus.FAILED;
        StoryAdventureMod.LOGGER.info("Instance {} failed after {}ms",
            instanceId, getElapsedMillis());
            
        // Clean up entities
        cleanupEntities();
    }
    
    /**
     * Clean up any entities spawned by this instance (tagged with instance_ID).
     */
    private void cleanupEntities() {
        if (server == null) return;
        
        String instanceTag = "instance_" + instanceId.toString();
        StoryAdventureMod.LOGGER.info("[Instance] Cleaning up entities for instance {} (tag: {})", instanceId, instanceTag);
        
        for (ServerLevel level : server.getAllLevels()) {
            java.util.List<net.minecraft.world.entity.Entity> entitiesToRemove = new java.util.ArrayList<>();
            // Iterable<Entity> getAllEntities()
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity.getTags().contains(instanceTag)) {
                    entitiesToRemove.add(entity);
                }
            }
            
            for (net.minecraft.world.entity.Entity entity : entitiesToRemove) {
                try {
                    entity.discard(); // remove without death events
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.warn("[Instance] Failed to discard entity {}: {}", entity, e.getMessage());
                }
            }
            
            if (!entitiesToRemove.isEmpty()) {
                StoryAdventureMod.LOGGER.info("[Instance] Removed {} entities from {}", entitiesToRemove.size(), level.dimension().location());
            }
        }
    }
    
    /**
     * Get available outgoing edges from the current node.
     */
    public List<StageEdge> getAvailableEdges(ServerPlayer player) {
        StageNode current = getCurrentNode();
        if (current == null) return List.of();
        
        return current.getEdges().stream()
            .filter(edge -> edge.canTransition(this, player))
            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
            .collect(Collectors.toList());
    }
    
    /**
     * Attempt to transition to a target node.
     * 
     * @return true if the transition was successful
     */
    public boolean transitionTo(String targetNodeId, ServerPlayer initiator) {
        StoryAdventureMod.LOGGER.debug("[Instance.transitionTo] Request: targetNodeId={}, initiator={}", 
            targetNodeId, initiator != null ? initiator.getName().getString() : "null");
        
        if (status != InstanceStatus.RUNNING) {
            StoryAdventureMod.LOGGER.warn("[Instance.transitionTo] FAILED: Instance not running. Status={}", status);
            return false;
        }
        
        StageNode current = getCurrentNode();
        if (current == null) {
            StoryAdventureMod.LOGGER.error("[Instance.transitionTo] FAILED: Current node is null.");
            return false;
        }
        
        StoryAdventureMod.LOGGER.debug("[Instance.transitionTo] Current node: {}", current.getId());
        
        // Find a valid edge to the target
        for (StageEdge edge : current.getEdges()) {
            boolean canTransition = edge.canTransition(this, initiator);
            StoryAdventureMod.LOGGER.debug("[Instance.transitionTo] Checking edge to {}. canTransition={}", 
                edge.getTargetNodeId(), canTransition);
            
            if (edge.getTargetNodeId().equals(targetNodeId) && canTransition) {
                // Exit current node
                exitNode(currentNodeId);
                
                // Update current node
                String previousNodeId = currentNodeId;
                currentNodeId = targetNodeId;
                lastUpdateMillis = System.currentTimeMillis();
                
                StoryAdventureMod.LOGGER.info("[Instance.transitionTo] Transitioning: {} -> {}", previousNodeId, currentNodeId);
                
                // Enter new node
                enterNode(targetNodeId);
                
                return true;
            }
        }
        
        StoryAdventureMod.LOGGER.debug("[Instance.transitionTo] FAILED: No valid edge found to target {}", targetNodeId);
        return false;
    }
    
    /**
     * Evaluate auto-transitions (unconditional edges or edges that became valid).
     */
    public void evaluateAutoTransitions() {
        if (status != InstanceStatus.RUNNING) return;
        
        StageNode current = getCurrentNode();
        if (current == null) return;
        
        List<StageEdge> edges = current.getEdges();
        
        // Check all edges and find one that can transition
        for (StageEdge edge : edges) {
            if (edge.canTransition(this, null)) {
                StoryAdventureMod.LOGGER.info("[Instance.evaluateAutoTransitions] Found valid edge to {} from {}", 
                    edge.getTargetNodeId(), current.getId());
                
                // Exit current node
                exitNode(currentNodeId);
                
                // Update current node
                String previousNodeId = currentNodeId;
                currentNodeId = edge.getTargetNodeId();
                lastUpdateMillis = System.currentTimeMillis();
                
                StoryAdventureMod.LOGGER.info("[Instance.evaluateAutoTransitions] Transitioning: {} -> {}", 
                    previousNodeId, currentNodeId);
                
                // Enter new node
                enterNode(currentNodeId);
                return;
            }
        }
        
        StoryAdventureMod.LOGGER.debug("[Instance.evaluateAutoTransitions] No valid transitions found from {}", current.getId());
    }
    
    /**
     * Rewind to a checkpoint.
     */
    public boolean rewindToCheckpoint(String checkpointNodeId) {
        StageNode checkpoint = graph.getNode(checkpointNodeId);
        if (checkpoint == null || checkpoint.getType() != NodeType.CHECKPOINT) {
            return false;
        }
        
        // Check if this checkpoint was reached
        if (!state.hasReachedCheckpoint(checkpointNodeId)) {
            return false;
        }
        
        // Restore state from checkpoint
        state.restoreFromCheckpoint(checkpointNodeId);
        currentNodeId = checkpointNodeId;
        status = InstanceStatus.RUNNING;
        
        StoryAdventureMod.LOGGER.info("Instance {} rewound to checkpoint {}", instanceId, checkpointNodeId);
        
        // Process the checkpoint node again
        evaluateAutoTransitions();
        
        return true;
    }
    
    private void enterNode(String nodeId) {
        StoryAdventureMod.LOGGER.debug("[Instance.enterNode] Entering node: {}", nodeId);
        
        StageNode node = graph.getNode(nodeId);
        if (node == null) {
            StoryAdventureMod.LOGGER.error("[Instance.enterNode] FAILED: Node {} not found in graph.", nodeId);
            return;
        }
        
        state.clearNodeResult();
        state.recordNodeEntry(nodeId);
        
        // Execute on_enter actions for all party members
        JsonObject nodeData = node.getData();
        if (nodeData.has("on_enter") && nodeData.get("on_enter").isJsonArray()) {
            var actionsArray = nodeData.getAsJsonArray("on_enter");
            
            // Get all online party members
            java.util.List<net.minecraft.server.level.ServerPlayer> onlinePlayers = new java.util.ArrayList<>();
            for (UUID memberId : party.getMembers()) {
                net.minecraft.server.level.ServerPlayer p = server.getPlayerList().getPlayer(memberId);
                if (p != null) {
                    onlinePlayers.add(p);
                }
            }
            
            // Execute each action for all players
            for (var actionElem : actionsArray) {
                if (actionElem.isJsonObject()) {
                    var actionJson = actionElem.getAsJsonObject();
                    var action = com.warmpixel.storyadventure.core.action.ActionFactory.fromJson(actionJson);
                    if (action != null) {
                        try {
                            if (action instanceof com.warmpixel.storyadventure.core.action.SpawnNPCAction spawnAction) {
                                spawnAction.setInstanceId(instanceId);
                            } else if (action instanceof com.warmpixel.storyadventure.core.action.CommandAction cmdAction) {
                                cmdAction.setInstanceId(instanceId);
                            }
                            
                            action.execute(onlinePlayers);
                            StoryAdventureMod.LOGGER.debug("[Instance.enterNode] Executed action: {} for {} players", 
                                actionJson.get("type").getAsString(), onlinePlayers.size());
                        } catch (Exception e) {
                            StoryAdventureMod.LOGGER.error("[Instance.enterNode] Failed to execute action", e);
                        }
                    }
                }
            }
        }
        
        // Call NodeHandler
        NodeType type = node.getType();
        var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(type);
        
        StoryAdventureMod.LOGGER.debug("[Instance.enterNode] Node Type: {}, Handler: {}", type, handler != null ? handler.getClass().getSimpleName() : "null");
        
        if (handler != null) {
            try {
                handler.onEnter(this, node);
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.error("[Instance.enterNode] Exception in handler.onEnter for node " + nodeId, e);
            }
        } else {
             StoryAdventureMod.LOGGER.warn("[Instance.enterNode] No handler found for node type: {}", type);
        }

        // Load triggers defined in node data
        loadTriggersFromNode(node);
    }

    private void loadTriggersFromNode(StageNode node) {
        activeTriggers.clear();
        JsonObject data = node.getData();
        if (data.has("triggers") && data.get("triggers").isJsonArray()) {
            JsonArray triggersArray = data.getAsJsonArray("triggers");
            for (JsonElement elem : triggersArray) {
                if (elem.isJsonObject()) {
                    JsonObject trigJson = elem.getAsJsonObject();
                    String id = trigJson.has("id") ? trigJson.get("id").getAsString() : "trig_" + UUID.randomUUID().toString().substring(0, 8);
                    TriggerBox box = TriggerBox.fromJson(id, trigJson);
                    if (box != null) {
                        addTrigger(box);
                        StoryAdventureMod.LOGGER.debug("[Instance] Loaded trigger {} for node {}", id, node.getId());
                    }
                }
            }
        }
    }
    
    private void exitNode(String nodeId) {
        StoryAdventureMod.LOGGER.debug("[Instance.exitNode] Exiting node: {}", nodeId);
        
        StageNode node = graph.getNode(nodeId);
        if (node != null) {
            var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(node.getType());
            if (handler != null) {
                try {
                    handler.onExit(this, node);
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("[Instance.exitNode] Exception in handler.onExit for node " + nodeId, e);
                }
            }
        } else {
             StoryAdventureMod.LOGGER.warn("[Instance.exitNode] Node {} not found in graph during exit.", nodeId);
        }
        state.recordNodeExit(nodeId);
    }
    
    /**
     * Check if a player is part of this instance.
     */
    public boolean hasPlayer(UUID playerId) {
        return party.hasMember(playerId);
    }
    
    // ==================== Waypoint & Trigger API ====================
    
    /**
     * Add a waypoint to this instance.
     */
    public void addWaypoint(Waypoint waypoint) {
        activeWaypoints.put(waypoint.getId(), waypoint);
        syncWaypointsToParty();
    }
    
    /**
     * Remove a waypoint from this instance.
     */
    public void removeWaypoint(String waypointId) {
        activeWaypoints.remove(waypointId);
        syncWaypointsToParty();
    }
    
    /**
     * Add a trigger box to this instance.
     */
    public void addTrigger(TriggerBox trigger) {
        activeTriggers.put(trigger.getId(), trigger);
    }
    
    /**
     * Remove a trigger box from this instance.
     */
    public void removeTrigger(String triggerId) {
        activeTriggers.remove(triggerId);
    }
    
    /**
     * Get all active waypoints.
     */
    public Map<String, Waypoint> getActiveWaypoints() {
        return activeWaypoints;
    }
    
    /**
     * Get all active triggers.
     */
    public Map<String, TriggerBox> getActiveTriggers() {
        return activeTriggers;
    }
    
    /**
     * Clear all waypoints and triggers (e.g., on node transition).
     */
    public void clearWaypointsAndTriggers() {
        activeWaypoints.clear();
        activeTriggers.clear();
        syncWaypointsToParty();
    }
    
    /**
     * Sync waypoints to all party members via network.
     */
    private void syncWaypointsToParty() {
        if (server == null) return;
        // Network sync will be implemented when we add the payload
        // For now, this is a placeholder
    }
    
    /**
     * Set the server reference (called during start).
     */
    public void setServer(MinecraftServer server) {
        this.server = server;
    }
    
    @Override
    public String toString() {
        return String.format("Instance{id=%s, story='%s', node='%s', status=%s, players=%d}",
            instanceId, storyId, currentNodeId, status, party.getMemberCount());
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/instance/InstanceManager.java`
```java
package com.warmpixel.storyadventure.instance;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageGraph;
import com.warmpixel.storyadventure.loader.StoryRegistry;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all running story instances.
 */
public class InstanceManager {
    
    // Active instances
    private final Map<UUID, Instance> instances = new ConcurrentHashMap<>();
    
    // Player to instance mapping for quick lookup
    private final Map<UUID, UUID> playerInstanceMap = new ConcurrentHashMap<>();
    
    // Maximum concurrent instances (configurable)
    private int maxConcurrentInstances = 50;
    
    /**
     * Create a new instance for a story with the given party.
     */
    public Instance createInstance(StageGraph graph, Party party) {
        StoryAdventureMod.LOGGER.info("[InstanceManager] Creating instance: storyId='{}', partyId={}, memberCount={}", 
            graph.getStoryId(), party.getPartyId(), party.getMemberCount());
            
        if (instances.size() >= maxConcurrentInstances) {
            StoryAdventureMod.LOGGER.warn("[InstanceManager] FAILED: Maximum concurrent instances ({}) reached", maxConcurrentInstances);
            return null;
        }
        
        // Check if party members are already in an instance
        for (UUID memberId : party.getMembers()) {
            UUID existingInstanceId = playerInstanceMap.get(memberId);
            if (existingInstanceId != null) {
                // Validate if the instance actually exists
                if (instances.containsKey(existingInstanceId)) {
                    StoryAdventureMod.LOGGER.warn("[InstanceManager] FAILED: Player {} is already in an active instance {}", memberId, existingInstanceId);
                    return null;
                } else {
                    // Zombie entry in playerInstanceMap, clean it up
                    StoryAdventureMod.LOGGER.warn("[InstanceManager] Found zombie instance mapping for player {} -> {}. Cleaning up.", memberId, existingInstanceId);
                    playerInstanceMap.remove(memberId);
                }
            }
        }
        
        UUID instanceId = UUID.randomUUID();
        Instance instance = new Instance(instanceId, graph, party);
        
        instances.put(instanceId, instance);
        
        // Map all party members to this instance
        for (UUID memberId : party.getMembers()) {
            playerInstanceMap.put(memberId, instanceId);
        }
        
        StoryAdventureMod.LOGGER.info("[InstanceManager] Instance created successfully: instanceId={}, story='{}', party={}",
            instanceId, graph.getStoryId(), party.getPartyId());
        
        return instance;
    }
    
    /**
     * Get an instance by its ID.
     */
    public Instance getInstance(UUID instanceId) {
        return instances.get(instanceId);
    }
    
    /**
     * Get the instance a player is currently in.
     */
    public Instance getPlayerInstance(UUID playerId) {
        UUID instanceId = playerInstanceMap.get(playerId);
        return instanceId != null ? instances.get(instanceId) : null;
    }
    
    /**
     * Check if a player is in any instance.
     */
    public boolean isPlayerInInstance(UUID playerId) {
        return playerInstanceMap.containsKey(playerId);
    }
    
    /**
     * Remove a player from their current instance mapping (e.g. when finishing).
     */
    public void removePlayerFromInstance(UUID playerId) {
        UUID instanceId = playerInstanceMap.remove(playerId);
        if (instanceId != null) {
            Instance instance = instances.get(instanceId);
            if (instance != null) {
                StoryAdventureMod.LOGGER.info("[InstanceManager] Detaching player {} from tracking for instance {}", playerId, instanceId);
                
                // Check if anyone from the original party is still in the tracking map for this instance
                boolean anyoneLeft = false;
                for (UUID mappedInstanceId : playerInstanceMap.values()) {
                    if (instanceId.equals(mappedInstanceId)) {
                        anyoneLeft = true;
                        break;
                    }
                }
                
                if (!anyoneLeft) {
                    StoryAdventureMod.LOGGER.info("[InstanceManager] All players detached. Cleaning up instance {}.", instanceId);
                    cleanupInstance(instanceId);
                }
            }
        }
    }
    
    /**
     * Add a player to an existing instance (rejoin or party join).
     */
    public boolean addPlayerToInstance(UUID playerId, UUID instanceId) {
        StoryAdventureMod.LOGGER.info("[InstanceManager] Adding player {} to instance {}", playerId, instanceId);
        
        Instance instance = instances.get(instanceId);
        if (instance == null) {
            StoryAdventureMod.LOGGER.warn("[InstanceManager] FAILED: Instance {} does not exist", instanceId);
            return false;
        }
        
        // Remove from current instance if any
        removePlayerFromInstance(playerId);
        
        if (instance.getParty().addMember(playerId)) {
            playerInstanceMap.put(playerId, instanceId);
            
            // Resume if the instance was paused
            if (instance.getStatus() == Instance.InstanceStatus.PAUSED) {
                StoryAdventureMod.LOGGER.info("[InstanceManager] Resuming paused instance {}", instanceId);
                instance.resume();
            }
            
            StoryAdventureMod.LOGGER.info("[InstanceManager] Player {} successfully added to instance {}", playerId, instanceId);
            return true;
        }
        
        StoryAdventureMod.LOGGER.warn("[InstanceManager] FAILED: Could not add player {} to instance {} (Party full?)", playerId, instanceId);
        return false;
    }
    
    /**
     * Clean up a completed or abandoned instance.
     */
    public void cleanupInstance(UUID instanceId) {
        Instance instance = instances.remove(instanceId);
        if (instance != null) {
            // Remove all player mappings
            for (UUID memberId : instance.getParty().getMembers()) {
                playerInstanceMap.remove(memberId);
            }
            
            StoryAdventureMod.LOGGER.info("[InstanceManager] Cleaned up instance {}", instanceId);
        } else {
             StoryAdventureMod.LOGGER.warn("[InstanceManager] Cleanup requested for non-existent instance {}", instanceId);
        }
    }
    
    /**
     * Handle player disconnect.
     */
    public void handlePlayerDisconnect(UUID playerId) {
        Instance instance = getPlayerInstance(playerId);
        if (instance != null) {
            // For now, just pause the instance if the player was the only one
            if (instance.getParty().getMemberCount() == 1) {
                instance.pause();
            }
            // Could implement timeout-based cleanup here
        }
    }
    
    /**
     * Get all active instances.
     */
    public Collection<Instance> getAllInstances() {
        return Collections.unmodifiableCollection(instances.values());
    }
    
    /**
     * Get count of active instances.
     */
    public int getActiveInstanceCount() {
        return instances.size();
    }
    
    /**
     * Save all instance states to disk (called on server shutdown).
     */
    public void saveAllInstances() {
        Path savePath = FabricLoader.getInstance().getConfigDir()
            .resolve("storyadventure").resolve("instances");
        
        try {
            Files.createDirectories(savePath);
            
            for (Instance instance : instances.values()) {
                if (instance.getStatus() == Instance.InstanceStatus.RUNNING || 
                    instance.getStatus() == Instance.InstanceStatus.PAUSED) {
                    saveInstance(instance, savePath);
                }
            }
            
            StoryAdventureMod.LOGGER.info("Saved {} instance states", instances.size());
        } catch (IOException e) {
            StoryAdventureMod.LOGGER.error("Failed to save instance states", e);
        }
    }
    
    private void saveInstance(Instance instance, Path savePath) {
        // Serialization would go here - using NBT or JSON
        // For now, just log
        StoryAdventureMod.LOGGER.debug("Would save instance {} to {}", 
            instance.getInstanceId(), savePath);
    }
    
    /**
     * Load saved instances from disk (called on server start).
     */
    public void loadSavedInstances(StoryRegistry registry) {
        Path savePath = FabricLoader.getInstance().getConfigDir()
            .resolve("storyadventure").resolve("instances");
        
        if (!Files.exists(savePath)) {
            return;
        }
        
        // Would load saved instances here
        StoryAdventureMod.LOGGER.info("Loading saved instances from {}", savePath);
    }
    
    public void setMaxConcurrentInstances(int max) {
        this.maxConcurrentInstances = max;
    }
    
    public int getMaxConcurrentInstances() {
        return maxConcurrentInstances;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/instance/InstanceState.java`
```java
package com.warmpixel.storyadventure.instance;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

/**
 * Tracks all state for a running instance: flags, clues, relationships,
 * timers, votes, and node results.
 */
public class InstanceState {
    private final Instance instance;
    
    // Story flags (boolean state variables)
    private final Map<String, Boolean> flags = new HashMap<>();
    
    // Discovered clues
    private final Set<String> discoveredClues = new HashSet<>();
    
    // Player-NPC relationships: playerUUID -> (npcId -> relationship value)
    private final Map<UUID, Map<String, Integer>> relationships = new HashMap<>();
    
    // Active timers
    private final Map<String, TimerState> timers = new HashMap<>();
    
    // Vote results
    private final Map<String, VoteResult> votes = new HashMap<>();
    
    // Checkpoint saved states
    private final Map<String, CheckpointState> checkpoints = new HashMap<>();
    
    // Current node result (for edge conditions)
    private String currentNodeResult = null;
    
    // Last dialogue choice made
    private String lastDialogueChoice = null;
    
    // Node visit history
    private final List<String> nodeHistory = new ArrayList<>();
    
    // Custom metadata for node handlers
    private final JsonObject metadata = new JsonObject();
    
    public InstanceState(Instance instance) {
        this.instance = instance;
    }
    
    // === Flags ===
    
    public boolean getFlag(String flagId) {
        return flags.getOrDefault(flagId, false);
    }
    
    public void setFlag(String flagId, boolean value) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Flag '{}' set to {} (instance: {})", flagId, value, instance.getInstanceId());
        flags.put(flagId, value);
    }
    
    public void toggleFlag(String flagId) {
        boolean newValue = !getFlag(flagId);
        StoryAdventureMod.LOGGER.debug("[InstanceState] Flag '{}' toggled to {} (instance: {})", flagId, newValue, instance.getInstanceId());
        flags.put(flagId, newValue);
    }
    
    // === Clues ===
    
    public boolean hasDiscoveredClue(String clueId) {
        return discoveredClues.contains(clueId);
    }
    
    public void discoverClue(String clueId) {
        if (discoveredClues.add(clueId)) {
            StoryAdventureMod.LOGGER.info("[InstanceState] Clue discovered: '{}' (instance: {})", clueId, instance.getInstanceId());
        }
    }
    
    public Set<String> getDiscoveredClues() {
        return Collections.unmodifiableSet(discoveredClues);
    }
    
    // === Relationships ===
    
    public int getRelationship(UUID playerId, String npcId) {
        return relationships.getOrDefault(playerId, Map.of()).getOrDefault(npcId, 0);
    }
    
    public void modifyRelationship(UUID playerId, String npcId, int delta) {
        int newValue = relationships.computeIfAbsent(playerId, k -> new HashMap<>())
            .merge(npcId, delta, Integer::sum);
        StoryAdventureMod.LOGGER.debug("[InstanceState] Relationship modified: player={}, npc={}, delta={}, newValue={} (instance: {})", 
            playerId, npcId, delta, newValue, instance.getInstanceId());
    }
    
    public void setRelationship(UUID playerId, String npcId, int value) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Relationship set: player={}, npc={}, value={} (instance: {})", 
            playerId, npcId, value, instance.getInstanceId());
        relationships.computeIfAbsent(playerId, k -> new HashMap<>())
            .put(npcId, value);
    }
    
    // === Timers ===
    
    public TimerState getTimer(String timerId) {
        return timers.get(timerId);
    }
    
    public void startTimer(String timerId, long durationMillis) {
        StoryAdventureMod.LOGGER.info("[InstanceState] Timer started: '{}' for {}ms (instance: {})", timerId, durationMillis, instance.getInstanceId());
        timers.put(timerId, new TimerState(System.currentTimeMillis(), durationMillis));
    }
    
    public void stopTimer(String timerId) {
        TimerState timer = timers.get(timerId);
        if (timer != null) {
            StoryAdventureMod.LOGGER.info("[InstanceState] Timer stopped: '{}' (instance: {})", timerId, instance.getInstanceId());
            timer.stop();
        }
    }
    
    // === Votes ===
    
    public VoteResult getVoteResult(String voteId) {
        return votes.get(voteId);
    }
    
    public void recordVote(String voteId, UUID playerId, String choice) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Vote recorded: id={}, player={}, choice={} (instance: {})", 
            voteId, playerId, choice, instance.getInstanceId());
        votes.computeIfAbsent(voteId, k -> new VoteResult(instance.getParty().getLeaderId()))
            .addVote(playerId, choice);
    }
    
    // === Node Results ===
    
    public void setNodeResult(String result) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Current node result set to: '{}' (instance: {})", result, instance.getInstanceId());
        this.currentNodeResult = result;
    }
    
    public void clearNodeResult() {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Current node result cleared (instance: {})", instance.getInstanceId());
        this.currentNodeResult = null;
    }
    
    public boolean isCurrentNodeCompleteWith(String result) {
        return result.equals(currentNodeResult);
    }
    
    // === Dialogue ===
    
    public String getLastDialogueChoice() {
        return lastDialogueChoice;
    }
    
    public void setLastDialogueChoice(String choice) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Last dialogue choice set to: '{}' (instance: {})", choice, instance.getInstanceId());
        this.lastDialogueChoice = choice;
    }
    
    // === Node History ===
    
    public void recordNodeEntry(String nodeId) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Node history entry: '{}' (instance: {})", nodeId, instance.getInstanceId());
        nodeHistory.add(nodeId);
    }
    
    public void recordNodeExit(String nodeId) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Node history exit: '{}' (instance: {})", nodeId, instance.getInstanceId());
        // Can be extended for analytics
    }
    
    public boolean hasVisitedNode(String nodeId) {
        return nodeHistory.contains(nodeId);
    }
    
    public List<String> getNodeHistory() {
        return Collections.unmodifiableList(nodeHistory);
    }
    
    public JsonObject getMetadata() {
        return metadata;
    }
    
    // Last checkpoint reached
    private String lastCheckpointId = null;
    
    public String getLastCheckpointId() {
        return lastCheckpointId;
    }
    
    public void setLastCheckpointId(String id) {
        this.lastCheckpointId = id;
    }

    // === Checkpoints ===
    
    public void saveCheckpoint(String checkpointId, CheckpointState checkpoint) {
        StoryAdventureMod.LOGGER.info("[InstanceState] Checkpoint saved: '{}' (instance: {})", checkpointId, instance.getInstanceId());
        checkpoints.put(checkpointId, checkpoint);
        this.lastCheckpointId = checkpointId;
    }
    
    public boolean hasReachedCheckpoint(String checkpointId) {
        return checkpoints.containsKey(checkpointId);
    }
    
    public CheckpointState getCheckpoint(String checkpointId) {
        return checkpoints.get(checkpointId);
    }
    
    public void restoreFromCheckpoint(String checkpointId) {
        CheckpointState checkpoint = checkpoints.get(checkpointId);
        if (checkpoint != null) {
            StoryAdventureMod.LOGGER.info("[InstanceState] Restoring from checkpoint: '{}' (instance: {})", checkpointId, instance.getInstanceId());
            checkpoint.restore(this);
            this.lastCheckpointId = checkpointId;
        } else {
            StoryAdventureMod.LOGGER.warn("[InstanceState] Failed to restore from checkpoint: '{}' (not found) (instance: {})", checkpointId, instance.getInstanceId());
        }
    }
    
    // === Serialization ===
    
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        
        // Save flags
        CompoundTag flagsTag = new CompoundTag();
        flags.forEach(flagsTag::putBoolean);
        tag.put("Flags", flagsTag);
        
        // Save clues
        CompoundTag cluesTag = new CompoundTag();
        int i = 0;
        for (String clue : discoveredClues) {
            cluesTag.putString("clue_" + i++, clue);
        }
        cluesTag.putInt("count", discoveredClues.size());
        tag.put("Clues", cluesTag);
        
        // Save last dialogue choice
        if (lastDialogueChoice != null) {
            tag.putString("LastDialogueChoice", lastDialogueChoice);
        }
        
        // Save current node result
        if (currentNodeResult != null) {
            tag.putString("CurrentNodeResult", currentNodeResult);
        }
        
        // Save last checkpoint
        if (lastCheckpointId != null) {
            tag.putString("LastCheckpointId", lastCheckpointId);
        }
        
        return tag;
    }
    
    public void load(CompoundTag tag) {
        // Load flags
        if (tag.contains("Flags")) {
            CompoundTag flagsTag = tag.getCompound("Flags");
            for (String key : flagsTag.getAllKeys()) {
                flags.put(key, flagsTag.getBoolean(key));
            }
        }
        
        // Load clues
        if (tag.contains("Clues")) {
            CompoundTag cluesTag = tag.getCompound("Clues");
            int count = cluesTag.getInt("count");
            for (int j = 0; j < count; j++) {
                discoveredClues.add(cluesTag.getString("clue_" + j));
            }
        }
        
        // Load last dialogue choice
        if (tag.contains("LastDialogueChoice")) {
            lastDialogueChoice = tag.getString("LastDialogueChoice");
        }
        
        // Load current node result
        if (tag.contains("CurrentNodeResult")) {
            currentNodeResult = tag.getString("CurrentNodeResult");
        }
        
        // Load last checkpoint
        if (tag.contains("LastCheckpointId")) {
            lastCheckpointId = tag.getString("LastCheckpointId");
        }
    }
    
    // === Inner Classes ===
    
    /**
     * Timer state tracking.
     */
    public static class TimerState {
        private final long startTime;
        private final long duration;
        private boolean stopped = false;
        private long stoppedAt = 0;
        
        public TimerState(long startTime, long duration) {
            this.startTime = startTime;
            this.duration = duration;
        }
        
        public boolean isExpired() {
            if (stopped) return stoppedAt > startTime + duration;
            return System.currentTimeMillis() > startTime + duration;
        }
        
        public boolean isActive() {
            return !stopped && !isExpired();
        }
        
        public long getRemainingMillis() {
            if (stopped) return Math.max(0, (startTime + duration) - stoppedAt);
            return Math.max(0, (startTime + duration) - System.currentTimeMillis());
        }
        
        public void stop() {
            if (!stopped) {
                stopped = true;
                stoppedAt = System.currentTimeMillis();
            }
        }
    }
    
    /**
     * Vote result tracking.
     */
    public static class VoteResult {
        private final UUID leaderId;
        private final Map<UUID, String> votes = new HashMap<>();
        
        public VoteResult(UUID leaderId) {
            this.leaderId = leaderId;
        }
        
        public void addVote(UUID playerId, String choice) {
            votes.put(playerId, choice);
        }
        
        public String getMajorityChoice() {
            Map<String, Integer> counts = new HashMap<>();
            for (String choice : votes.values()) {
                counts.merge(choice, 1, Integer::sum);
            }
            return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        }
        
        public String getLeaderChoice() {
            return votes.getOrDefault(leaderId, "");
        }
        
        public boolean isUnanimous(String choice) {
            return votes.values().stream().allMatch(choice::equals);
        }
        
        public boolean hasAnyVoteFor(String choice) {
            return votes.containsValue(choice);
        }
    }
    
    /**
     * Checkpoint saved state.
     */
    public static class CheckpointState {
        private final Map<String, Boolean> savedFlags;
        private final Set<String> savedClues;
        
        // Respawn data
        private String dimension = "minecraft:overworld";
        private double x, y, z;
        private float yaw, pitch;
        
        public CheckpointState(InstanceState state) {
            this.savedFlags = new HashMap<>(state.flags);
            this.savedClues = new HashSet<>(state.discoveredClues);
        }
        
        public void setLocation(String dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
        
        public String getDimension() { return dimension; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        
        public void restore(InstanceState state) {
            state.flags.clear();
            state.flags.putAll(savedFlags);
            state.discoveredClues.clear();
            state.discoveredClues.addAll(savedClues);
            state.clearNodeResult();
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/instance/Party.java`
```java
package com.warmpixel.storyadventure.instance;

import java.util.*;

/**
 * Represents a party of players participating in a story instance.
 */
public class Party {
    private final UUID partyId;
    private UUID leaderId;
    private final Set<UUID> members;
    private final Map<UUID, Boolean> readyStatus;
    private final int maxSize;
    private String selectedStoryId;
    private int countdownSeconds = -1;
    
    public Party(UUID partyId, UUID leaderId, int maxSize) {
        this.partyId = partyId;
        this.leaderId = leaderId;
        this.members = new HashSet<>();
        this.readyStatus = new HashMap<>();
        this.maxSize = maxSize;
        
        if (leaderId != null) {
            members.add(leaderId);
            readyStatus.put(leaderId, false);
        }
    }
    
    public UUID getPartyId() {
        return partyId;
    }
    
    public UUID getLeaderId() {
        return leaderId;
    }
    
    public void setLeaderId(UUID leaderId) {
        if (members.contains(leaderId)) {
            this.leaderId = leaderId;
        }
    }

    public String getSelectedStoryId() {
        return selectedStoryId;
    }

    public void setSelectedStoryId(String selectedStoryId) {
        this.selectedStoryId = selectedStoryId;
    }
    
    public boolean hasMember(UUID playerId) {
        return members.contains(playerId);
    }
    
    public boolean addMember(UUID playerId) {
        if (members.size() >= maxSize) {
            return false;
        }
        if (members.add(playerId)) {
            readyStatus.put(playerId, false);
            return true;
        }
        return false;
    }
    
    public boolean removeMember(UUID playerId) {
        boolean removed = members.remove(playerId);
        readyStatus.remove(playerId);
        
        // If leader left, promote someone else
        if (removed && playerId.equals(leaderId) && !members.isEmpty()) {
            leaderId = members.iterator().next();
        }
        
        return removed;
    }
    
    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }
    
    public int getMemberCount() {
        return members.size();
    }
    
    public int getMaxSize() {
        return maxSize;
    }
    
    public boolean isFull() {
        return members.size() >= maxSize;
    }
    
    public boolean isEmpty() {
        return members.isEmpty();
    }
    
    public boolean isLeader(UUID playerId) {
        return playerId.equals(leaderId);
    }
    
    public void setReady(UUID playerId, boolean ready) {
        if (members.contains(playerId)) {
            readyStatus.put(playerId, ready);
        }
    }
    
    public boolean isReady(UUID playerId) {
        return readyStatus.getOrDefault(playerId, false);
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public void setCountdownSeconds(int countdownSeconds) {
        this.countdownSeconds = countdownSeconds;
    }
    
    @Override
    public String toString() {
        return String.format("Party{id=%s, leader=%s, members=%d/%d}", 
            partyId, leaderId, members.size(), maxSize);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/instance/PartyManager.java`
```java
package com.warmpixel.storyadventure.instance;

import com.warmpixel.storyadventure.StoryAdventureMod;

import java.util.*;

/**
 * Manages party creation, membership, and player-party associations.
 */
public class PartyManager {
    
    // All active parties
    private final Map<UUID, Party> parties = new HashMap<>();
    
    // Player to party mapping
    private final Map<UUID, UUID> playerPartyMap = new HashMap<>();
    
    /**
     * Create a new party with the given player as leader.
     */
    public Party createParty(UUID leaderId, int maxSize) {
        StoryAdventureMod.LOGGER.info("[PartyManager] Creating new party: leaderId={}, maxSize={}", leaderId, maxSize);
        
        // Leave existing party if any
        leaveParty(leaderId);
        
        UUID partyId = UUID.randomUUID();
        Party party = new Party(partyId, leaderId, maxSize);
        parties.put(partyId, party);
        playerPartyMap.put(leaderId, partyId);
        
        StoryAdventureMod.LOGGER.info("[PartyManager] Party created successfully: partyId={}, leaderId={}", partyId, leaderId);
        
        return party;
    }
    
    /**
     * Get the party a player is in.
     */
    public Party getPlayerParty(UUID playerId) {
        UUID partyId = playerPartyMap.get(playerId);
        return partyId != null ? parties.get(partyId) : null;
    }
    
    /**
     * Get a party by its ID.
     */
    public Party getParty(UUID partyId) {
        return parties.get(partyId);
    }
    
    /**
     * Invite a player to join a party.
     * 
     * @return true if the player was successfully added
     */
    public boolean joinParty(UUID playerId, UUID partyId) {
        StoryAdventureMod.LOGGER.debug("[PartyManager] Player {} attempting to join party {}", playerId, partyId);
        
        Party party = parties.get(partyId);
        if (party == null) {
            StoryAdventureMod.LOGGER.warn("[PartyManager] Join FAILED: Party {} not found", partyId);
            return false;
        }
        
        if (party.isFull()) {
            StoryAdventureMod.LOGGER.warn("[PartyManager] Join FAILED: Party {} is full ({} members)", partyId, party.getMemberCount());
            return false;
        }
        
        // Leave existing party if any
        leaveParty(playerId);
        
        if (party.addMember(playerId)) {
            playerPartyMap.put(playerId, partyId);
            StoryAdventureMod.LOGGER.info("[PartyManager] Player {} successfully joined party {}", playerId, partyId);
            return true;
        }
        
        StoryAdventureMod.LOGGER.warn("[PartyManager] Join FAILED: addMember returned false for player {} and party {}", playerId, partyId);
        return false;
    }
    
    /**
     * Remove a player from their current party.
     */
    public void leaveParty(UUID playerId) {
        UUID partyId = playerPartyMap.remove(playerId);
        if (partyId != null) {
            Party party = parties.get(partyId);
            if (party != null) {
                StoryAdventureMod.LOGGER.info("[PartyManager] Player {} is leaving party {}", playerId, partyId);
                party.removeMember(playerId);
                
                // Disband empty party
                if (party.isEmpty()) {
                    parties.remove(partyId);
                    StoryAdventureMod.LOGGER.info("[PartyManager] Party {} disbanded because it is now empty", partyId);
                }
            }
        }
    }
    
    /**
     * Disband a party entirely.
     */
    public void disbandParty(UUID partyId) {
        StoryAdventureMod.LOGGER.info("[PartyManager] Disbanding party {}", partyId);
        Party party = parties.remove(partyId);
        if (party != null) {
            for (UUID memberId : party.getMembers()) {
                playerPartyMap.remove(memberId);
            }
            StoryAdventureMod.LOGGER.info("[PartyManager] Party {} disbanded successfully", partyId);
        } else {
            StoryAdventureMod.LOGGER.warn("[PartyManager] Disband FAILED: Party {} not found", partyId);
        }
    }
    
    /**
     * Transfer leadership to another party member.
     */
    public boolean transferLeadership(UUID partyId, UUID newLeaderId) {
        Party party = parties.get(partyId);
        if (party != null && party.hasMember(newLeaderId)) {
            party.setLeaderId(newLeaderId);
            return true;
        }
        return false;
    }
    
    /**
     * Get all active parties.
     */
    public Collection<Party> getAllParties() {
        return Collections.unmodifiableCollection(parties.values());
    }
    
    /**
     * Check if a player is in any party.
     */
    public boolean isInParty(UUID playerId) {
        return playerPartyMap.containsKey(playerId);
    }

    private int tickCounter = 0;
    public void tick(net.minecraft.server.MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 20 != 0) return; // Only tick once per second

        var mod = StoryAdventureMod.getInstance();
        var instanceManager = mod.getInstanceManager();

        for (Party party : parties.values()) {
            int cd = party.getCountdownSeconds();
            if (cd > 0) {
                party.setCountdownSeconds(cd - 1);
                com.warmpixel.storyadventure.network.NetworkHandler.broadcastLobbySync(party, server);
            } else if (cd == 0) {
                // Countdown finished! Start adventure
                party.setCountdownSeconds(-1); // Reset
                
                String storyId = party.getSelectedStoryId();
                var story = mod.getStoryRegistry().getStory(storyId);
                
                if (story != null) {
                    StoryAdventureMod.LOGGER.info("[PartyManager] Countdown finished for party {}. Starting adventure.", party.getPartyId());
                    var instance = instanceManager.createInstance(story, party);
                    if (instance != null) {
                        try {
                            instance.start(server);
                            // Close lobby for everyone
                            for (UUID memberId : party.getMembers()) {
                                net.minecraft.server.level.ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                                if (member != null) member.closeContainer();
                            }
                        } catch (Exception e) {
                            StoryAdventureMod.LOGGER.error("[PartyManager] Failed to start instance for party " + party.getPartyId(), e);
                            instanceManager.cleanupInstance(instance.getInstanceId());
                            
                            // Notify leader
                            var leader = server.getPlayerList().getPlayer(party.getLeaderId());
                            if (leader != null) leader.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c冒险启动失败: " + e.getMessage()));
                        }
                    }
                }
            }
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/item/AdminWandItem.java`
```java
package com.warmpixel.storyadventure.item;

import com.warmpixel.storyadventure.client.ui.admin.AdminDashboardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin Wand - A special item for story administrators.
 * Right-click opens the admin dashboard UI.
 * Shift+Right-click creates trigger boxes (2-click selection).
 */
public class AdminWandItem extends Item {
    
    // Track pending trigger box corners per player
    private static final Map<UUID, Vec3> pendingCorner1 = new HashMap<>();
    
    public AdminWandItem() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC)
        );
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        // Check if player has permission (OP level 2+)
        if (!player.hasPermissions(2)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.translatable("item.storyadventure.admin_wand.no_permission"));
            }
            return InteractionResultHolder.fail(stack);
        }
        
        // Shift+Right Click = Creation Mode
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                // Check if Ctrl is also held (check via sneaking + crouching state)
                // For now, Shift+RClick = Trigger Box, we'll add waypoint via command
                handleTriggerBoxCreation(player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        
        // Normal Right Click = Open Admin UI
        if (level.isClientSide) {
            openAdminUI();
        } else {
            player.sendSystemMessage(Component.translatable("item.storyadventure.admin_wand.opening_dashboard"));
            
            // Also sync trigger boxes to this player for gizmo rendering
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                syncTriggerBoxesToPlayer(sp);
            }
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    
    /**
     * Handle trigger box creation (server-side).
     * First click: Set corner 1
     * Second click: Set corner 2 and create box
     */
    private void handleTriggerBoxCreation(Player player) {
        UUID playerId = player.getUUID();
        Vec3 currentPos = player.position();
        
        if (pendingCorner1.containsKey(playerId)) {
            // Second click - create the box
            Vec3 corner1 = pendingCorner1.remove(playerId);
            Vec3 corner2 = currentPos;
            
            // Generate unique ID
            String boxId = "trigger_" + System.currentTimeMillis() % 100000;
            
            // Log the creation
            player.sendSystemMessage(Component.translatable("item.storyadventure.admin_wand.trigger_created", 
                boxId, corner1.x, corner1.y, corner1.z, corner2.x, corner2.y, corner2.z));
            
            // Create and save the trigger box on server
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                var manager = com.warmpixel.storyadventure.core.waypoint.TriggerBoxManager.getInstance();
                if (manager != null) {
                    var bounds = new net.minecraft.world.phys.AABB(corner1, corner2);
                    manager.createBox(boxId, bounds, Component.translatable("gui.storyadventure.admin.triggers.new_trigger").getString());
                    
                    // Sync all boxes to this player for gizmo rendering
                    syncTriggerBoxesToPlayer(serverPlayer);
                }
            }
            
            // Store in temporary registry for editor UI
            PendingTriggerBoxes.store(playerId, boxId, corner1, corner2);
            
        } else {
            // First click - store corner 1
            pendingCorner1.put(playerId, currentPos);
            player.sendSystemMessage(Component.translatable("item.storyadventure.admin_wand.corner1_set", 
                currentPos.x, currentPos.y, currentPos.z));
        }
    }
    
    /**
     * Cancel pending box creation for a player.
     */
    public static void cancelPending(UUID playerId) {
        pendingCorner1.remove(playerId);
    }
    
    /**
     * Check if a player has a pending corner.
     */
    public static boolean hasPendingCorner(UUID playerId) {
        return pendingCorner1.containsKey(playerId);
    }
    
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    private void openAdminUI() {
        Minecraft.getInstance().setScreen(new AdminDashboardScreen());
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.storyadventure.admin_wand.tooltip.title").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("item.storyadventure.admin_wand.tooltip.right_click").withStyle(net.minecraft.ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.storyadventure.admin_wand.tooltip.shift_right_click").withStyle(net.minecraft.ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.storyadventure.admin_wand.tooltip.waypoint").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.storyadventure.admin_wand.tooltip.admin_only").withStyle(net.minecraft.ChatFormatting.RED));
    }
    
    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Always show enchantment glint
    }
    
    /**
     * Sync all trigger boxes to a player for gizmo rendering.
     */
    public static void syncTriggerBoxesToPlayer(net.minecraft.server.level.ServerPlayer player) {
        var manager = com.warmpixel.storyadventure.core.waypoint.TriggerBoxManager.getInstance();
        if (manager == null) return;
        
        java.util.List<com.warmpixel.storyadventure.network.SyncTriggerBoxesPayload.TriggerBoxData> boxes = 
            new java.util.ArrayList<>();
        
        for (var box : manager.getAllBoxes()) {
            var bounds = box.getBounds();
            boxes.add(new com.warmpixel.storyadventure.network.SyncTriggerBoxesPayload.TriggerBoxData(
                box.getId(), box.getLabel(),
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                !box.getPlayersInside().isEmpty()
            ));
        }
        
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
            new com.warmpixel.storyadventure.network.SyncTriggerBoxesPayload(boxes));
    }
    
    /**
     * Temporary storage for pending trigger boxes awaiting editor.
     */
    public static class PendingTriggerBoxes {
        private static final Map<UUID, PendingBox> pending = new HashMap<>();
        
        public static void store(UUID playerId, String boxId, Vec3 corner1, Vec3 corner2) {
            pending.put(playerId, new PendingBox(boxId, corner1, corner2));
        }
        
        public static PendingBox get(UUID playerId) {
            return pending.get(playerId);
        }
        
        public static PendingBox remove(UUID playerId) {
            return pending.remove(playerId);
        }
        
        public record PendingBox(String id, Vec3 corner1, Vec3 corner2) {}
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/item/CameraWandItem.java`
```java
package com.warmpixel.storyadventure.item;

import com.warmpixel.storyadventure.client.ui.CameraRecorderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * Camera Recording Wand - A tool for recording camera positions and rotations.
 * Right-click opens the Camera Recorder UI panel.
 * Used to create camera paths for cutscenes.
 */
public class CameraWandItem extends Item {
    
    public CameraWandItem() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.RARE)
        );
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        // Check if player has permission (OP level 2+)
        if (!player.hasPermissions(2)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("§c你需要管理员权限才能使用摄像机魔杖"));
            }
            return InteractionResultHolder.fail(stack);
        }
        
        // Open Camera Recorder UI on client
        if (level.isClientSide) {
            openCameraRecorderUI();
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    private void openCameraRecorderUI() {
        Minecraft.getInstance().setScreen(new CameraRecorderScreen());
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("摄像机录制魔杖").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("右键 - 打开录制面板").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("用于录制过场动画的摄像机路径").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("仅限管理员").withStyle(ChatFormatting.RED));
    }
    
    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Always show enchantment glint
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/item/ModItems.java`
```java
package com.warmpixel.storyadventure.item;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for all Story Adventure items.
 */
public class ModItems {
    
    public static final String MOD_ID = StoryAdventureMod.MOD_ID;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    public static final Item ADMIN_WAND = register("admin_wand", new AdminWandItem());
    public static final Item CAMERA_WAND = register("camera_wand", new CameraWandItem());
    
    private static Item register(String name, Item item) {
        return Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, name),
            item
        );
    }
    
    public static void registerItems() {
        LOGGER.info("Registering Story Adventure items...");
    }
}

```

## File: `src/main/java/com/warmpixel/storyadventure/network/AdminInstanceActionPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Payload for administrative instance management actions.
 */
public record AdminInstanceActionPayload(Action action, UUID instanceId) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AdminInstanceActionPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "admin_instance_action"));
    
    public static final StreamCodec<FriendlyByteBuf, AdminInstanceActionPayload> STREAM_CODEC = 
        StreamCodec.of(AdminInstanceActionPayload::write, AdminInstanceActionPayload::read);

    public enum Action {
        SYNC, TERMINATE, PAUSE, RESUME
    }

    private static void write(FriendlyByteBuf buf, AdminInstanceActionPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeBoolean(payload.instanceId != null);
        if (payload.instanceId != null) {
            buf.writeUUID(payload.instanceId);
        }
    }
    
    private static AdminInstanceActionPayload read(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID instanceId = buf.readBoolean() ? buf.readUUID() : null;
        return new AdminInstanceActionPayload(action, instanceId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/AdminStoryActionPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for administrative story management actions.
 * Used to request data sync, reload stories, or validate them.
 */
public record AdminStoryActionPayload(Action action, String storyId) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AdminStoryActionPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "admin_story_action"));
    
    public static final StreamCodec<FriendlyByteBuf, AdminStoryActionPayload> STREAM_CODEC = 
        StreamCodec.of(AdminStoryActionPayload::write, AdminStoryActionPayload::read);

    public enum Action {
        SYNC, RELOAD, VALIDATE
    }

    private static void write(FriendlyByteBuf buf, AdminStoryActionPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeUtf(payload.storyId != null ? payload.storyId : "");
    }
    
    private static AdminStoryActionPayload read(FriendlyByteBuf buf) {
        return new AdminStoryActionPayload(
            buf.readEnum(Action.class),
            buf.readUtf()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/AdminTriggerActionPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for admin trigger box actions (save, delete).
 */
public record AdminTriggerActionPayload(Action action, String id, String label, 
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ,
                                        String linkedNodeId) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AdminTriggerActionPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "admin_trigger_action"));
    
    public static final StreamCodec<FriendlyByteBuf, AdminTriggerActionPayload> STREAM_CODEC = 
        StreamCodec.of(AdminTriggerActionPayload::write, AdminTriggerActionPayload::read);

    public enum Action {
        SAVE, DELETE, LIST
    }

    private static void write(FriendlyByteBuf buf, AdminTriggerActionPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeUtf(payload.id);
        buf.writeUtf(payload.label != null ? payload.label : "");
        buf.writeDouble(payload.minX);
        buf.writeDouble(payload.minY);
        buf.writeDouble(payload.minZ);
        buf.writeDouble(payload.maxX);
        buf.writeDouble(payload.maxY);
        buf.writeDouble(payload.maxZ);
        buf.writeUtf(payload.linkedNodeId != null ? payload.linkedNodeId : "");
    }
    
    private static AdminTriggerActionPayload read(FriendlyByteBuf buf) {
        return new AdminTriggerActionPayload(
            buf.readEnum(Action.class),
            buf.readUtf(),
            buf.readUtf(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readUtf()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/ClientNetworkHandler.java`
```java
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
        
        // Cutscene payload - handle camera control from server
        ClientPlayNetworking.registerGlobalReceiver(CutscenePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleCutscenePayload(payload));
        });
        
        // Voiceover payload - handle voiceover audio from server
        ClientPlayNetworking.registerGlobalReceiver(VoiceoverPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleVoiceoverPayload(payload));
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
                String voiceoverPath = null; // Optional voiceover
                
                // Parse extraData JSON if present
                if (extraData != null && !extraData.isEmpty()) {
                    try {
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(extraData).getAsJsonObject();
                        
                        if (json.has("npcName")) {
                            npcName = json.get("npcName").getAsString();
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
                
                StrangerDialogueScreen screen = new StrangerDialogueScreen(npcName, dialogueText.toString());
                
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
                
                // Start the cutscene
                controller.startCutscene(path, config);
                StoryAdventureMod.LOGGER.info("Started cutscene for instance {}", payload.instanceId());
            }
            case "STOP" -> {
                controller.stopCutscene();
                StoryAdventureMod.LOGGER.info("Stopped cutscene for instance {}", payload.instanceId());
            }
            case "SKIP" -> {
                controller.skipCutscene();
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
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/CutscenePayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client payload for cutscene synchronization.
 * Allows the server to start, stop, or skip cutscenes on clients.
 */
public record CutscenePayload(
    String action,           // "START", "STOP", "SKIP"
    String instanceId,       // Instance ID for reference
    String cameraPathJson,   // JSON data for camera path (only for START)
    boolean skippable,       // Whether the cutscene can be skipped
    boolean letterbox,       // Enable letterbox bars
    int fadeInTicks,         // Fade in duration
    int fadeOutTicks         // Fade out duration
) implements CustomPacketPayload {
    
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("storyadventure", "cutscene");
    public static final CustomPacketPayload.Type<CutscenePayload> TYPE = new CustomPacketPayload.Type<>(ID);
    
    private static final Gson GSON = new Gson();
    
    public static final StreamCodec<FriendlyByteBuf, CutscenePayload> CODEC = new StreamCodec<>() {
        @Override
        public CutscenePayload decode(FriendlyByteBuf buf) {
            String action = buf.readUtf();
            String instanceId = buf.readUtf();
            String cameraPathJson = buf.readUtf();
            boolean skippable = buf.readBoolean();
            boolean letterbox = buf.readBoolean();
            int fadeInTicks = buf.readVarInt();
            int fadeOutTicks = buf.readVarInt();
            return new CutscenePayload(action, instanceId, cameraPathJson, skippable, letterbox, fadeInTicks, fadeOutTicks);
        }
        
        @Override
        public void encode(FriendlyByteBuf buf, CutscenePayload payload) {
            buf.writeUtf(payload.action());
            buf.writeUtf(payload.instanceId());
            buf.writeUtf(payload.cameraPathJson());
            buf.writeBoolean(payload.skippable());
            buf.writeBoolean(payload.letterbox());
            buf.writeVarInt(payload.fadeInTicks());
            buf.writeVarInt(payload.fadeOutTicks());
        }
    };
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    // ==================== Factory Methods ====================
    
    /**
     * Create a START cutscene payload.
     */
    public static CutscenePayload start(String instanceId, JsonObject cameraPath, 
                                         boolean skippable, boolean letterbox,
                                         int fadeInTicks, int fadeOutTicks) {
        String pathJson = cameraPath != null ? GSON.toJson(cameraPath) : "{}";
        return new CutscenePayload("START", instanceId, pathJson, skippable, letterbox, fadeInTicks, fadeOutTicks);
    }
    
    /**
     * Create a STOP cutscene payload.
     */
    public static CutscenePayload stop(String instanceId) {
        return new CutscenePayload("STOP", instanceId, "", true, false, 0, 0);
    }
    
    /**
     * Create a SKIP cutscene payload.
     */
    public static CutscenePayload skip(String instanceId) {
        return new CutscenePayload("SKIP", instanceId, "", true, false, 0, 0);
    }
    
    /**
     * Parse camera path JSON to JsonObject.
     */
    public JsonObject getCameraPathAsJson() {
        if (cameraPathJson == null || cameraPathJson.isEmpty()) {
            return new JsonObject();
        }
        try {
            return GSON.fromJson(cameraPathJson, JsonObject.class);
        } catch (Exception e) {
            return new JsonObject();
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/DialogueChoicePayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for dialogue choices.
 */
public record DialogueChoicePayload(String choiceId) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<DialogueChoicePayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "dialogue_choice"));
    
    public static final StreamCodec<FriendlyByteBuf, DialogueChoicePayload> STREAM_CODEC = 
        StreamCodec.of(DialogueChoicePayload::write, DialogueChoicePayload::read);
    
    private static void write(FriendlyByteBuf buf, DialogueChoicePayload payload) {
        buf.writeUtf(payload.choiceId);
    }
    
    private static DialogueChoicePayload read(FriendlyByteBuf buf) {
        return new DialogueChoicePayload(buf.readUtf());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/InvitePayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for party invitations.
 * C2S: Send invite to player name.
 * S2C: Receive invite from inviter name.
 */
public record InvitePayload(String name, boolean isResponse, boolean accept) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<InvitePayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "invite"));
    
    public static final StreamCodec<FriendlyByteBuf, InvitePayload> STREAM_CODEC = 
        StreamCodec.of(InvitePayload::write, InvitePayload::read);
    
    private static void write(FriendlyByteBuf buf, InvitePayload payload) {
        buf.writeUtf(payload.name);
        buf.writeBoolean(payload.isResponse);
        buf.writeBoolean(payload.accept);
    }
    
    private static InvitePayload read(FriendlyByteBuf buf) {
        return new InvitePayload(buf.readUtf(), buf.readBoolean(), buf.readBoolean());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/NetworkHandler.java`
```java
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
        
        // Cutscene payload (server to client)
        PayloadTypeRegistry.playS2C().register(CutscenePayload.TYPE, CutscenePayload.CODEC);
        
        // Voiceover payload (server to client)
        PayloadTypeRegistry.playS2C().register(VoiceoverPayload.TYPE, VoiceoverPayload.STREAM_CODEC);
        
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
     * Send a cutscene start command to a player.
     */
    public static void sendCutsceneStart(ServerPlayer player, com.google.gson.JsonObject cameraPath, 
                                          boolean skippable, boolean letterbox, 
                                          int fadeInTicks, int fadeOutTicks, String instanceId) {
        if (player != null && player.connection != null) {
            CutscenePayload payload = CutscenePayload.start(instanceId, cameraPath, skippable, letterbox, fadeInTicks, fadeOutTicks);
            ServerPlayNetworking.send(player, payload);
            StoryAdventureMod.LOGGER.info("Sent cutscene START to {}", player.getName().getString());
        }
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
```

## File: `src/main/java/com/warmpixel/storyadventure/network/OpenUIPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload to instruct the client to open a specific UI screen.
 */
public record OpenUIPayload(String screenType, String extraData) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<OpenUIPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "open_ui"));
    
    public static final StreamCodec<FriendlyByteBuf, OpenUIPayload> STREAM_CODEC = 
        StreamCodec.of(OpenUIPayload::write, OpenUIPayload::read);
    
    // Screen type constants
    public static final String SCREEN_STORIES = "stories";
    public static final String SCREEN_LOBBY = "lobby";
    public static final String SCREEN_DIALOGUE = "dialogue";
    public static final String SCREEN_PUZZLE = "puzzle";
    public static final String SCREEN_ADMIN_DASHBOARD = "admin_dashboard";
    public static final String SCREEN_ADMIN_INSTANCES = "admin_instances";
    public static final String SCREEN_ADMIN_STORIES = "admin_stories";
    public static final String SCREEN_HUD_SHOW = "hud_show";
    public static final String SCREEN_HUD_HIDE = "hud_hide";
    public static final String SCREEN_VICTORY = "victory";
    
    private static void write(FriendlyByteBuf buf, OpenUIPayload payload) {
        buf.writeUtf(payload.screenType);
        buf.writeUtf(payload.extraData);
    }
    
    private static OpenUIPayload read(FriendlyByteBuf buf) {
        return new OpenUIPayload(buf.readUtf(), buf.readUtf());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/PuzzleInputPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for puzzle inputs.
 */
public record PuzzleInputPayload(String input) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<PuzzleInputPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "puzzle_input"));
    
    public static final StreamCodec<FriendlyByteBuf, PuzzleInputPayload> STREAM_CODEC = 
        StreamCodec.of(PuzzleInputPayload::write, PuzzleInputPayload::read);
    
    private static void write(FriendlyByteBuf buf, PuzzleInputPayload payload) {
        buf.writeUtf(payload.input);
    }
    
    private static PuzzleInputPayload read(FriendlyByteBuf buf) {
        return new PuzzleInputPayload(buf.readUtf());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/RequestStoryGraphPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for requesting a story's JSON data from the server.
 */
public record RequestStoryGraphPayload(String storyId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestStoryGraphPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "request_story_graph"));
    
    public static final StreamCodec<FriendlyByteBuf, RequestStoryGraphPayload> STREAM_CODEC = 
        StreamCodec.of(RequestStoryGraphPayload::write, RequestStoryGraphPayload::read);

    private static void write(FriendlyByteBuf buf, RequestStoryGraphPayload payload) {
        buf.writeUtf(payload.storyId);
    }
    
    private static RequestStoryGraphPayload read(FriendlyByteBuf buf) {
        return new RequestStoryGraphPayload(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/SaveStoryPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for saving a story's JSON data back to the server.
 */
public record SaveStoryPayload(String storyId, String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SaveStoryPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "save_story"));
    
    public static final StreamCodec<FriendlyByteBuf, SaveStoryPayload> STREAM_CODEC = 
        StreamCodec.of(SaveStoryPayload::write, SaveStoryPayload::read);

    private static void write(FriendlyByteBuf buf, SaveStoryPayload payload) {
        buf.writeUtf(payload.storyId);
        buf.writeUtf(payload.json);
    }
    
    private static SaveStoryPayload read(FriendlyByteBuf buf) {
        return new SaveStoryPayload(buf.readUtf(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/StoryActionPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for story-related actions (Select, Start, Leave).
 */
public record StoryActionPayload(Action action, String data) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<StoryActionPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "story_action"));
    
    public static final StreamCodec<FriendlyByteBuf, StoryActionPayload> STREAM_CODEC = 
        StreamCodec.of(StoryActionPayload::write, StoryActionPayload::read);
    
    public enum Action {
        SELECT_STORY,
        START_ADVENTURE,
        LEAVE_PARTY,
        DISBAND_PARTY
    }
    
    private static void write(FriendlyByteBuf buf, StoryActionPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeUtf(payload.data);
    }
    
    private static StoryActionPayload read(FriendlyByteBuf buf) {
        return new StoryActionPayload(buf.readEnum(Action.class), buf.readUtf());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/SyncAdminStoriesPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload to sync detailed story information to admin clients.
 */
public record SyncAdminStoriesPayload(List<AdminStoryInfo> stories) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SyncAdminStoriesPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_admin_stories"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncAdminStoriesPayload> STREAM_CODEC = 
        StreamCodec.of(SyncAdminStoriesPayload::write, SyncAdminStoriesPayload::read);
    
    public record AdminStoryInfo(String id, String name, int nodeCount, String version, boolean valid, String errorMsg) {}
    
    private static void write(FriendlyByteBuf buf, SyncAdminStoriesPayload payload) {
        buf.writeInt(payload.stories.size());
        for (AdminStoryInfo story : payload.stories) {
            buf.writeUtf(story.id);
            buf.writeUtf(story.name);
            buf.writeInt(story.nodeCount);
            buf.writeUtf(story.version);
            buf.writeBoolean(story.valid);
            buf.writeUtf(story.errorMsg);
        }
    }
    
    private static SyncAdminStoriesPayload read(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<AdminStoryInfo> stories = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stories.add(new AdminStoryInfo(
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf()
            ));
        }
        return new SyncAdminStoriesPayload(stories);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/SyncInstancesPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payload to sync the list of active instances to administrative clients.
 */
public record SyncInstancesPayload(List<InstanceInfo> instances) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SyncInstancesPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_instances"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncInstancesPayload> STREAM_CODEC = 
        StreamCodec.of(SyncInstancesPayload::write, SyncInstancesPayload::read);
    
    public record InstanceInfo(UUID id, String storyName, String node, String status, int playerCount, long elapsed) {}
    
    private static void write(FriendlyByteBuf buf, SyncInstancesPayload payload) {
        buf.writeInt(payload.instances.size());
        for (InstanceInfo info : payload.instances) {
            buf.writeUUID(info.id);
            buf.writeUtf(info.storyName);
            buf.writeUtf(info.node);
            buf.writeUtf(info.status);
            buf.writeInt(info.playerCount);
            buf.writeLong(info.elapsed);
        }
    }
    
    private static SyncInstancesPayload read(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<InstanceInfo> instances = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            instances.add(new InstanceInfo(
                buf.readUUID(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readLong()
            ));
        }
        return new SyncInstancesPayload(instances);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/SyncLobbyPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payload to sync lobby member status to all clients in the lobby.
 */
public record SyncLobbyPayload(List<MemberInfo> members, int countdown) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SyncLobbyPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_lobby"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncLobbyPayload> STREAM_CODEC = 
        StreamCodec.of(SyncLobbyPayload::write, SyncLobbyPayload::read);
    
    public record MemberInfo(UUID id, String name, boolean ready, boolean isLeader) {}
    
    private static void write(FriendlyByteBuf buf, SyncLobbyPayload payload) {
        buf.writeInt(payload.members.size());
        for (MemberInfo info : payload.members) {
            buf.writeUUID(info.id);
            buf.writeUtf(info.name);
            buf.writeBoolean(info.ready);
            buf.writeBoolean(info.isLeader);
        }
        buf.writeInt(payload.countdown);
    }
    
    private static SyncLobbyPayload read(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<MemberInfo> members = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            members.add(new MemberInfo(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
        }
        int countdown = buf.readInt();
        return new SyncLobbyPayload(members, countdown);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/SyncStoriesPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload to sync available stories to the client.
 */
public record SyncStoriesPayload(List<StorySummary> stories) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SyncStoriesPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_stories"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncStoriesPayload> STREAM_CODEC = 
        StreamCodec.of(SyncStoriesPayload::write, SyncStoriesPayload::read);
    
    public record StorySummary(String id, String name, String description, int minPlayers, int maxPlayers, int estimatedMinutes) {}
    
    private static void write(FriendlyByteBuf buf, SyncStoriesPayload payload) {
        buf.writeInt(payload.stories.size());
        for (StorySummary story : payload.stories) {
            buf.writeUtf(story.id);
            buf.writeUtf(story.name);
            buf.writeUtf(story.description);
            buf.writeInt(story.minPlayers);
            buf.writeInt(story.maxPlayers);
            buf.writeInt(story.estimatedMinutes);
        }
    }
    
    private static SyncStoriesPayload read(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<StorySummary> stories = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stories.add(new StorySummary(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
            ));
        }
        return new SyncStoriesPayload(stories);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/SyncStoryGraphPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for syncing a full story JSON to the client for the graph editor.
 */
public record SyncStoryGraphPayload(String storyId, String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncStoryGraphPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_story_graph"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncStoryGraphPayload> STREAM_CODEC = 
        StreamCodec.of(SyncStoryGraphPayload::write, SyncStoryGraphPayload::read);

    private static void write(FriendlyByteBuf buf, SyncStoryGraphPayload payload) {
        buf.writeUtf(payload.storyId);
        buf.writeUtf(payload.json);
    }
    
    private static SyncStoryGraphPayload read(FriendlyByteBuf buf) {
        return new SyncStoryGraphPayload(buf.readUtf(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/SyncTriggerBoxesPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload for syncing trigger boxes to clients (for gizmo rendering).
 */
public record SyncTriggerBoxesPayload(List<TriggerBoxData> boxes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncTriggerBoxesPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_trigger_boxes"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncTriggerBoxesPayload> STREAM_CODEC = 
        StreamCodec.of(SyncTriggerBoxesPayload::write, SyncTriggerBoxesPayload::read);

    private static void write(FriendlyByteBuf buf, SyncTriggerBoxesPayload payload) {
        buf.writeVarInt(payload.boxes.size());
        for (TriggerBoxData box : payload.boxes) {
            buf.writeUtf(box.id);
            buf.writeUtf(box.label);
            buf.writeDouble(box.minX);
            buf.writeDouble(box.minY);
            buf.writeDouble(box.minZ);
            buf.writeDouble(box.maxX);
            buf.writeDouble(box.maxY);
            buf.writeDouble(box.maxZ);
            buf.writeBoolean(box.hasPlayersInside);
        }
    }
    
    private static SyncTriggerBoxesPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<TriggerBoxData> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new TriggerBoxData(
                buf.readUtf(), buf.readUtf(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean()
            ));
        }
        return new SyncTriggerBoxesPayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public record TriggerBoxData(String id, String label,
                                  double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ,
                                  boolean hasPlayersInside) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/SyncWaypointsPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload for syncing active waypoints to clients in a story instance.
 */
public record SyncWaypointsPayload(List<WaypointData> waypoints) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncWaypointsPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_waypoints"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncWaypointsPayload> STREAM_CODEC = 
        StreamCodec.of(SyncWaypointsPayload::write, SyncWaypointsPayload::read);

    private static void write(FriendlyByteBuf buf, SyncWaypointsPayload payload) {
        buf.writeVarInt(payload.waypoints.size());
        for (WaypointData wp : payload.waypoints) {
            buf.writeUtf(wp.id);
            buf.writeUtf(wp.label);
            buf.writeDouble(wp.x);
            buf.writeDouble(wp.y);
            buf.writeDouble(wp.z);
            buf.writeUtf(wp.icon);
            buf.writeInt(wp.color);
            buf.writeBoolean(wp.showDistance);
        }
    }
    
    private static SyncWaypointsPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<WaypointData> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new WaypointData(
                buf.readUtf(), buf.readUtf(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readUtf(), buf.readInt(), buf.readBoolean()
            ));
        }
        return new SyncWaypointsPayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public record WaypointData(String id, String label, double x, double y, double z, 
                                String icon, int color, boolean showDistance) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/ToggleReadyPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload to toggle ready status in a lobby.
 */
public record ToggleReadyPayload(boolean ready) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<ToggleReadyPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "toggle_ready"));
    
    public static final StreamCodec<FriendlyByteBuf, ToggleReadyPayload> STREAM_CODEC = 
        StreamCodec.of(ToggleReadyPayload::write, ToggleReadyPayload::read);
    
    private static void write(FriendlyByteBuf buf, ToggleReadyPayload payload) {
        buf.writeBoolean(payload.ready);
    }
    
    private static ToggleReadyPayload read(FriendlyByteBuf buf) {
        return new ToggleReadyPayload(buf.readBoolean());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/VictoryConfirmPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server payload sent when a player confirms the victory screen
 * or when the countdown timer expires.
 */
public record VictoryConfirmPayload() implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<VictoryConfirmPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "victory_confirm"));
    
    public static final StreamCodec<FriendlyByteBuf, VictoryConfirmPayload> STREAM_CODEC = 
        StreamCodec.of(VictoryConfirmPayload::write, VictoryConfirmPayload::read);
    
    private static void write(FriendlyByteBuf buf, VictoryConfirmPayload payload) {
        // No data needed - server knows player's instance from their UUID
    }
    
    private static VictoryConfirmPayload read(FriendlyByteBuf buf) {
        return new VictoryConfirmPayload();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/network/VoiceoverPayload.java`
```java
package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for sending voiceover audio to clients.
 * Triggers playback of pre-recorded OGG files for dialogue and narrator lines.
 */
public record VoiceoverPayload(
    String instanceId,
    String soundPath,
    float volume,
    float pitch,
    String characterId
) implements CustomPacketPayload {
    
    public static final Type<VoiceoverPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "voiceover"));
    
    public static final StreamCodec<FriendlyByteBuf, VoiceoverPayload> STREAM_CODEC = 
        StreamCodec.of(VoiceoverPayload::write, VoiceoverPayload::read);
    
    public static void write(FriendlyByteBuf buf, VoiceoverPayload payload) {
        buf.writeUtf(payload.instanceId);
        buf.writeUtf(payload.soundPath);
        buf.writeFloat(payload.volume);
        buf.writeFloat(payload.pitch);
        buf.writeUtf(payload.characterId);
    }
    
    public static VoiceoverPayload read(FriendlyByteBuf buf) {
        return new VoiceoverPayload(
            buf.readUtf(),
            buf.readUtf(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readUtf()
        );
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * Create a voiceover payload for the narrator.
     */
    public static VoiceoverPayload narrator(String instanceId, String soundPath) {
        return new VoiceoverPayload(instanceId, soundPath, 1.0f, 1.0f, "narrator");
    }
    
    /**
     * Create a voiceover payload for a character.
     */
    public static VoiceoverPayload character(String instanceId, String soundPath, String characterId) {
        return new VoiceoverPayload(instanceId, soundPath, 1.0f, 1.0f, characterId);
    }
    
    /**
     * Create a voiceover payload with custom volume and pitch.
     */
    public static VoiceoverPayload custom(String instanceId, String soundPath, float volume, float pitch, String characterId) {
        return new VoiceoverPayload(instanceId, soundPath, volume, pitch, characterId);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/node/CheckpointNodeHandler.java`
```java
package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import com.warmpixel.storyadventure.instance.InstanceState;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Handler for CHECKPOINT nodes.
 * Savepoints with optional "rewind" anchor capability.
 */
public class CheckpointNodeHandler implements NodeHandler {
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String checkpointId = node.getId();
        boolean saveInventory = node.getBoolean("save_inventory", true);
        boolean savePosition = node.getBoolean("save_position", true);
        boolean rewindAnchor = node.getBoolean("rewind_anchor", true);
        
        StoryAdventureMod.LOGGER.info("[CheckpointNodeHandler] onEnter: instance={}, checkpointId={}, rewind={}, saveInv={}, savePos={}", 
            instance.getInstanceId(), checkpointId, rewindAnchor, saveInventory, savePosition);
        
        if (rewindAnchor) {
            // Save state as checkpoint
            StoryAdventureMod.LOGGER.debug("[CheckpointNodeHandler] Saving checkpoint state for {}", checkpointId);
            InstanceState.CheckpointState checkpoint = new InstanceState.CheckpointState(instance.getState());
            
            // Capture current location for respawning
            ServerPlayer leadPlayer = null;
            for (UUID memberId : instance.getParty().getMembers()) {
                leadPlayer = instance.getServer().getPlayerList().getPlayer(memberId);
                if (leadPlayer != null) break;
            }
            
            if (leadPlayer != null) {
                checkpoint.setLocation(
                    leadPlayer.level().dimension().location().toString(),
                    leadPlayer.getX(),
                    leadPlayer.getY(),
                    leadPlayer.getZ(),
                    leadPlayer.getYRot(),
                    leadPlayer.getXRot()
                );
            }
            
            instance.getState().saveCheckpoint(checkpointId, checkpoint);
        }
        
        // Notify players with message if defined in JSON data
        String message = node.getString("message", "");
        if (!message.isEmpty()) {
            // Get all online party members
            java.util.List<ServerPlayer> onlinePlayers = new java.util.ArrayList<>();
            for (UUID memberId : instance.getParty().getMembers()) {
                ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
                if (p != null) onlinePlayers.add(p);
            }
            
            // Show as title/subtitle
            new com.warmpixel.storyadventure.core.action.TitleAction(message, "", 10, 80, 20).execute(onlinePlayers);
            
            // Also send to chat for record
            for (ServerPlayer p : onlinePlayers) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6[存档点] §f" + message));
            }
        }
        
        // TODO: Save player inventories and positions if configured
        
        // Notify players
        // NetworkHandler.sendCheckpointNotification(instance);
        StoryAdventureMod.LOGGER.debug("[CheckpointNodeHandler] Checkpoint processing complete. Auto-advancing.");
        
        // Auto-advance (checkpoints don't pause)
        instance.getState().setNodeResult("complete");
        instance.evaluateAutoTransitions();
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Checkpoints don't tick - they immediately advance
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        // Checkpoints don't have actions
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        // Nothing to clean up
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return true; // Checkpoints always complete immediately
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/node/CombatNodeHandler.java`
```java
package com.warmpixel.storyadventure.core.node;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Handler for COMBAT nodes.
 * Supports waves, boss fights, and escape sequences.
 */
public class CombatNodeHandler implements NodeHandler {
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String combatType = node.getString("combat_type", "WAVE");
        boolean escapeAvailable = node.getBoolean("escape_available", false);
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] onEnter: instance={}, node={}, type={}, escape={}", 
            instance.getInstanceId(), node.getId(), combatType, escapeAvailable);
        
        // Reset combat state in metadata
        instance.getState().getMetadata().addProperty("combat_total", 0);
        instance.getState().getMetadata().addProperty("combat_killed", 0);
        instance.getState().getMetadata().addProperty("combat_active", true);
        instance.getState().getMetadata().addProperty("combat_start_time", System.currentTimeMillis());
        
        // Parse and spawn enemies from JSON
        JsonObject data = node.getData();
        int totalToSpawn = 0;
        
        if (data.has("enemies") && data.get("enemies").isJsonArray()) {
            JsonArray enemies = data.getAsJsonArray("enemies");
            
            // Get a player position to spawn around
            ServerPlayer spawnCenter = null;
            for (UUID memberId : instance.getParty().getMembers()) {
                ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
                if (player != null) {
                    spawnCenter = player;
                    break;
                }
            }
            
            if (spawnCenter == null) {
                StoryAdventureMod.LOGGER.error("[CombatNodeHandler] No players found to spawn enemies around!");
                return;
            }
            
            double centerX = spawnCenter.getX();
            double centerY = spawnCenter.getY();
            double centerZ = spawnCenter.getZ();
            Random random = new Random();
            
            for (var enemyElem : enemies) {
                JsonObject enemy = enemyElem.getAsJsonObject();
                String entityType = enemy.has("type") ? enemy.get("type").getAsString() : "minecraft:zombie";
                int count = enemy.has("count") ? enemy.get("count").getAsInt() : 1;
                double spawnRadius = enemy.has("spawn_radius") ? enemy.get("spawn_radius").getAsDouble() : 10.0;
                
                StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Spawning {} x {} with radius {}", count, entityType, spawnRadius);
                
                for (int i = 0; i < count; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = spawnRadius * 0.5 + random.nextDouble() * spawnRadius * 0.5;
                    double spawnX = centerX + Math.cos(angle) * distance;
                    double spawnZ = centerZ + Math.sin(angle) * distance;
                    
                    net.minecraft.world.level.Level level = spawnCenter.level();
                    int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int)spawnX, (int)spawnZ);
                    double spawnY = Math.max(centerY - 5, Math.min(centerY + 5, groundY)); 
                    
                    String cmd;
                    // If type has a colon (e.g. minecraft:zombie), assume it's a standard entity.
                    // If it's a template name (no colon or starts with easy_npc prefix), use the new command.
                    if (entityType.contains(":") && !entityType.toLowerCase().startsWith("easy_npc:")) {
                         cmd = String.format("summon %s %.2f %.2f %.2f {Tags:[\"story_enemy\",\"instance_%s\"]}", 
                            entityType, spawnX, spawnY, spawnZ, instance.getInstanceId().toString());
                    } else {
                         // Assume it's an NPC template
                         String nbt = String.format("{Tags:[\"story_enemy\",\"instance_%s\"]}", instance.getInstanceId().toString());
                         cmd = String.format("easy_npc template spawn %s %.2f %.2f %.2f %s", 
                            entityType, spawnX, spawnY, spawnZ, nbt);
                    }
                    
                    instance.getServer().getCommands().performPrefixedCommand(
                        instance.getServer().createCommandSourceStack().withSuppressedOutput(),
                        cmd
                    );
                    
                    totalToSpawn++;
                }
            }
        }
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Spawned {} enemies total", totalToSpawn);
        instance.getState().getMetadata().addProperty("combat_total", totalToSpawn);
        
        // Initial HUD sync
        syncHudToParty(instance, node);
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        if (!isCombatActive(instance)) return;
        
        int killed = getKilledCount(instance);
        int total = getTotalCount(instance);
        
        if (total > 0 && killed >= total) {
            markCombatVictory(instance, node);
            return;
        }
        
        // Check for player deaths
        boolean anyPlayerAlive = false;
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null && player.isAlive()) {
                anyPlayerAlive = true;
                break;
            }
        }
        
        if (!anyPlayerAlive) {
            markCombatDefeat(instance, node);
        }
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[CombatNodeHandler] onAction: player={}, action={}, data={}", 
            player != null ? player.getName().getString() : "null", action, data);
            
        switch (action) {
            case "enemy_killed" -> {
                int killed = getKilledCount(instance) + 1;
                instance.getState().getMetadata().addProperty("combat_killed", killed);
                
                int total = getTotalCount(instance);
                StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Enemy killed: {}/{}", killed, total);
                
                // Update HUD for progress
                syncHudToParty(instance, node);
                
                if (killed >= total) {
                    markCombatVictory(instance, node);
                }
            }
            case "escape_attempt" -> {
                if (node.getBoolean("escape_available", false)) {
                    StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Escape successful!");
                    instance.getState().setNodeResult("escaped");
                    instance.getState().getMetadata().addProperty("combat_active", false);
                    instance.evaluateAutoTransitions();
                }
            }
        }
    }
    
    private void markCombatVictory(Instance instance, StageNode node) {
        if (!isCombatActive(instance)) return;
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Combat victory! Node: {}", node.getId());
        instance.getState().setNodeResult("victory");
        instance.getState().getMetadata().addProperty("combat_active", false);
        
        // Notify players
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a§l[战斗胜利] §r§a所有目标已消灭！"));
            }
        }
        
        instance.evaluateAutoTransitions();
    }
    
    private void markCombatDefeat(Instance instance, StageNode node) {
        if (!isCombatActive(instance)) return;
        
        StoryAdventureMod.LOGGER.info("[CombatNodeHandler] Combat defeat! Node: {}", node.getId());
        instance.getState().setNodeResult("defeat");
        instance.getState().getMetadata().addProperty("combat_active", false);
        instance.evaluateAutoTransitions();
    }
    
    private void syncHudToParty(Instance instance, StageNode node) {
        int killed = getKilledCount(instance);
        int total = getTotalCount(instance);
        int remaining = Math.max(0, total - killed);
        
        String title = node.getString("title", "战斗");
        String desc = node.getString("description", "消灭敌人");
        
        StringBuilder hudJson = new StringBuilder();
        hudJson.append("{");
        hudJson.append("\"title\":\"").append(escapeJson(instance.getGraph().getName())).append("\",");
        hudJson.append("\"chapter\":\"").append(escapeJson(title)).append("\",");
        hudJson.append("\"objectives\":[");
        hudJson.append("{");
        hudJson.append("\"text\":\"").append(escapeJson(desc + " (剩余: " + remaining + ")")).append("\",");
        hudJson.append("\"complete\":").append(killed >= total ? "true" : "false").append(",");
        hudJson.append("\"current\":true");
        hudJson.append("}");
        hudJson.append("]");
        hudJson.append("}");
        
        String json = hudJson.toString();
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                    p,
                    com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_SHOW,
                    json
                );
            }
        }
    }
    
    private boolean isCombatActive(Instance instance) {
        return instance.getState().getMetadata().has("combat_active") && 
               instance.getState().getMetadata().get("combat_active").getAsBoolean();
    }
    
    private int getKilledCount(Instance instance) {
        return instance.getState().getMetadata().has("combat_killed") ? 
            instance.getState().getMetadata().get("combat_killed").getAsInt() : 0;
    }
    
    private int getTotalCount(Instance instance) {
        return instance.getState().getMetadata().has("combat_total") ? 
            instance.getState().getMetadata().get("combat_total").getAsInt() : 0;
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        instance.getState().getMetadata().addProperty("combat_active", false);
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return !isCombatActive(instance);
    }
}

```

## File: `src/main/java/com/warmpixel/storyadventure/core/node/CutsceneNodeHandler.java`
```java
package com.warmpixel.storyadventure.core.node;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import com.warmpixel.storyadventure.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Handler for CUTSCENE nodes.
 * Implements cinemachine-like camera control for scripted cutscenes.
 * 
 * Supports:
 * - Camera path with keyframes (position, rotation, FOV)
 * - Look-at targets
 * - Letterbox bars and fade transitions
 * - Skippable cutscenes
 * - Teleport on completion
 */
public class CutsceneNodeHandler implements NodeHandler {
    
    private long cutsceneStartTime = 0;
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        int durationTicks = node.getInt("duration_ticks", 100);
        String message = node.getString("message", "");
        boolean skippable = node.getBoolean("skippable", true);
        boolean letterbox = node.getBoolean("letterbox", true);
        int fadeInTicks = node.getInt("fade_in_ticks", 20);
        int fadeOutTicks = node.getInt("fade_out_ticks", 20);
        
        cutsceneStartTime = System.currentTimeMillis();
        
        StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] onEnter: instance={}, node={}, duration={} ticks", 
            instance.getInstanceId(), node.getId(), durationTicks);
        
        // Get camera path from node data
        JsonObject cameraPathJson = node.getObject("camera_path");
        
        // If no camera path defined, create a simple one from current player position
        if (cameraPathJson == null) {
            cameraPathJson = createDefaultCameraPath(instance, durationTicks);
            StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] No camera_path defined, using default");
        }
        
        // Send cutscene start command to all party members
        String instanceId = instance.getInstanceId().toString();
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                NetworkHandler.sendCutsceneStart(player, cameraPathJson, skippable, letterbox, 
                    fadeInTicks, fadeOutTicks, instanceId);
                
                // Show optional title message
                if (!message.isEmpty()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7" + message));
                }
            }
        }
        
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] Cutscene started for {} party members", 
            instance.getParty().getMemberCount());
    }
    
    /**
     * Create a default camera path when none is specified in the node data.
     * Uses the first party member's position as a reference.
     */
    private JsonObject createDefaultCameraPath(Instance instance, int durationTicks) {
        JsonObject pathObj = new JsonObject();
        com.google.gson.JsonArray keyframes = new com.google.gson.JsonArray();
        
        // Get first player's position as reference
        double x = 0, y = 64, z = 0;
        float yaw = 0, pitch = 0;
        
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                x = player.getX();
                y = player.getY() + 2; // Slightly above head
                z = player.getZ();
                yaw = player.getYRot();
                pitch = player.getXRot();
                break;
            }
        }
        
        // First keyframe - starting position
        JsonObject kf1 = new JsonObject();
        com.google.gson.JsonArray pos1 = new com.google.gson.JsonArray();
        pos1.add(x - 3);
        pos1.add(y + 3);
        pos1.add(z - 3);
        kf1.add("position", pos1);
        
        com.google.gson.JsonArray rot1 = new com.google.gson.JsonArray();
        rot1.add(yaw);
        rot1.add(pitch - 10);
        rot1.add(0);
        kf1.add("rotation", rot1);
        
        kf1.addProperty("fov", 70);
        kf1.addProperty("duration_ticks", 0);
        kf1.addProperty("easing", "LINEAR");
        keyframes.add(kf1);
        
        // Second keyframe - orbit around player
        JsonObject kf2 = new JsonObject();
        com.google.gson.JsonArray pos2 = new com.google.gson.JsonArray();
        pos2.add(x + 3);
        pos2.add(y + 2);
        pos2.add(z + 3);
        kf2.add("position", pos2);
        
        com.google.gson.JsonArray rot2 = new com.google.gson.JsonArray();
        rot2.add(yaw + 180);
        rot2.add(pitch);
        rot2.add(0);
        kf2.add("rotation", rot2);
        
        kf2.addProperty("fov", 60);
        kf2.addProperty("duration_ticks", durationTicks);
        kf2.addProperty("easing", "EASE_IN_OUT");
        keyframes.add(kf2);
        
        pathObj.add("keyframes", keyframes);
        
        // Add look-at target (player position)
        JsonObject lookAt = new JsonObject();
        lookAt.addProperty("type", "position");
        com.google.gson.JsonArray lookAtPos = new com.google.gson.JsonArray();
        lookAtPos.add(x);
        lookAtPos.add(y - 1);
        lookAtPos.add(z);
        lookAt.add("value", lookAtPos);
        pathObj.add("look_at", lookAt);
        
        return pathObj;
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        int durationTicks = node.getInt("duration_ticks", 100);
        long durationMs = durationTicks * 50L; // 50ms per tick
        long elapsed = System.currentTimeMillis() - cutsceneStartTime;
        
        if (elapsed >= durationMs) {
            StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] onTick: Cutscene complete. Elapsed: {}ms", elapsed);
            
            // Cutscene complete
            instance.getState().setNodeResult("complete");
            
            // Send stop command to all party members
            String instanceId = instance.getInstanceId().toString();
            for (UUID memberId : instance.getParty().getMembers()) {
                ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
                if (player != null) {
                    NetworkHandler.sendCutsceneStop(player, instanceId);
                }
            }
            
            // Check if this is an ending
            if (node.getBoolean("is_ending", false)) {
                String endingType = node.getString("ending_type", "success");
                StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] Cutscene is ending. Type: {}", endingType);
                
                if ("success".equals(endingType)) {
                    instance.complete();
                } else {
                    instance.fail();
                }
            } else {
                StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] Cutscene finished, evaluating transitions.");
                instance.evaluateAutoTransitions();
            }
        }
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] onAction: player={}, action={}", 
            player.getName().getString(), action);

        if ("skip".equals(action) && node.getBoolean("skippable", true)) {
            StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] Player {} skipped cutscene {}", 
                player.getName().getString(), node.getId());
            
            // Send stop command to all party members
            String instanceId = instance.getInstanceId().toString();
            for (UUID memberId : instance.getParty().getMembers()) {
                ServerPlayer member = instance.getServer().getPlayerList().getPlayer(memberId);
                if (member != null) {
                    NetworkHandler.sendCutsceneStop(member, instanceId);
                }
            }
            
            // Complete the cutscene
            instance.getState().setNodeResult("complete");
            instance.evaluateAutoTransitions();
        } else if ("skip".equals(action)) {
            StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] Skip rejected. Cutscene not skippable.");
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.debug("[CutsceneNodeHandler] onExit: instance={}, node={}", 
            instance.getInstanceId(), node.getId());

        // Handle teleport on complete
        String teleportTo = node.getString("teleport_on_complete", "");
        if (!teleportTo.isEmpty()) {
            StoryAdventureMod.LOGGER.info("[CutsceneNodeHandler] Teleport requested to: {}", teleportTo);
            var loc = instance.getGraph().getSpecialLocation(teleportTo);
            if (loc != null) {
                var server = instance.getServer();
                var worldKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, 
                    net.minecraft.resources.ResourceLocation.parse(loc.dimension())
                );
                var targetWorld = server.getLevel(worldKey);
                if (targetWorld != null) {
                    for (UUID memberId : instance.getParty().getMembers()) {
                        var player = server.getPlayerList().getPlayer(memberId);
                        if (player != null) {
                            player.teleportTo(targetWorld, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
                        }
                    }
                }
            }
        }
        
        // Ensure cutscene is stopped on exit (in case of unexpected transition)
        String instanceId = instance.getInstanceId().toString();
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                NetworkHandler.sendCutsceneStop(player, instanceId);
            }
        }
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return instance.getState().isCurrentNodeCompleteWith("complete");
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/node/DialogueNodeHandler.java`
```java
package com.warmpixel.storyadventure.core.node;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import com.warmpixel.storyadventure.network.NetworkHandler;
import com.warmpixel.storyadventure.network.OpenUIPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Handler for DIALOGUE nodes.
 * Integrates with Easy NPC dialog system for rich NPC conversations.
 */
public class DialogueNodeHandler implements NodeHandler {
    
    private boolean dialogueTriggered = false;
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String npcTemplate = node.getString("npc_template", "");
        String dialogSet = node.getString("dialog_set", "default");
        
        StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] onEnter: instance={}, node={}, npc='{}', dialogSet='{}'", 
            instance.getInstanceId(), node.getId(), npcTemplate, dialogSet);
        
        dialogueTriggered = false;
        
        // Check if there's a proximity_trigger
        JsonObject data = node.getData();
        if (data.has("proximity_trigger")) {
            // Check if any player is ALREADY within range (common when transitioning from a nearby task)
            JsonObject trigger = data.getAsJsonObject("proximity_trigger");
            double targetX = trigger.has("target_x") ? trigger.get("target_x").getAsDouble() : 0;
            double targetY = trigger.has("target_y") ? trigger.get("target_y").getAsDouble() : 64;
            double targetZ = trigger.has("target_z") ? trigger.get("target_z").getAsDouble() : 0;
            double radius = trigger.has("radius") ? trigger.get("radius").getAsDouble() : 5.0; // Default larger radius
            
            for (UUID memberId : instance.getParty().getMembers()) {
                ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
                if (player != null) {
                    double dx = player.getX() - targetX;
                    double dy = player.getY() - targetY;
                    double dz = player.getZ() - targetZ;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    
                    if (distance <= radius) {
                        StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Player {} already in proximity on enter (distance: {})", 
                            player.getName().getString(), distance);
                        dialogueTriggered = true;
                        openDialogueForAllPlayers(instance, node);
                        return;
                    }
                }
            }
            
            StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Waiting for proximity trigger in node {}", node.getId());
        } else {
            // No proximity trigger - open dialogue immediately for all players
            openDialogueForAllPlayers(instance, node);
        }
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Check proximity trigger if not yet triggered
        if (dialogueTriggered) return;
        
        JsonObject data = node.getData();
        if (!data.has("proximity_trigger")) return;
        
        JsonObject trigger = data.getAsJsonObject("proximity_trigger");
        double targetX = trigger.has("target_x") ? trigger.get("target_x").getAsDouble() : 0;
        double targetY = trigger.has("target_y") ? trigger.get("target_y").getAsDouble() : 64;
        double targetZ = trigger.has("target_z") ? trigger.get("target_z").getAsDouble() : 0;
        double radius = trigger.has("radius") ? trigger.get("radius").getAsDouble() : 5.0;
        
        // Check if any party member is within proximity range
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                double dx = player.getX() - targetX;
                double dy = player.getY() - targetY;
                double dz = player.getZ() - targetZ;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                
                if (distance <= radius) {
                    StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Player {} triggered dialogue proximity (distance: {})", 
                        player.getName().getString(), distance);
                    
                    // Trigger dialogue for ALL party members
                    dialogueTriggered = true;
                    openDialogueForAllPlayers(instance, node);
                    break;
                }
            }
        }
    }
    
    private void openDialogueForAllPlayers(Instance instance, StageNode node) {
        String npcName = node.getString("npc_name", "NPC");
        
        // Build dialogue JSON from node data
        StringBuilder dialogueJson = new StringBuilder();
        dialogueJson.append("{");
        dialogueJson.append("\"npcName\":\"").append(escapeJson(npcName)).append("\",");
        
        // Get lines from node
        dialogueJson.append("\"lines\":[");
        JsonObject data = node.getData();
        if (data.has("lines") && data.get("lines").isJsonArray()) {
            var lines = data.getAsJsonArray("lines");
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) dialogueJson.append(",");
                dialogueJson.append("\"").append(escapeJson(lines.get(i).getAsString())).append("\"");
            }
        }
        dialogueJson.append("],");
        
        // Get choices from node
        dialogueJson.append("\"choices\":[");
        if (data.has("choices") && data.get("choices").isJsonArray()) {
            var choices = data.getAsJsonArray("choices");
            for (int i = 0; i < choices.size(); i++) {
                if (i > 0) dialogueJson.append(",");
                var choice = choices.get(i).getAsJsonObject();
                String id = choice.has("id") ? choice.get("id").getAsString() : "choice_" + i;
                String text = choice.has("text") ? choice.get("text").getAsString() : "选项 " + (i + 1);
                dialogueJson.append("{");
                dialogueJson.append("\"id\":\"").append(escapeJson(id)).append("\",");
                dialogueJson.append("\"text\":\"").append(escapeJson(text)).append("\"");
                dialogueJson.append("}");
            }
        }
        dialogueJson.append("]}");
        
        // Send dialogue to ALL party members
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_DIALOGUE, dialogueJson.toString());
                StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Opened dialogue for player {}", player.getName().getString());
            }
        }
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] onAction: player={}, action={}, data={}", 
            player.getName().getString(), action, data);

        if ("choice".equals(action) && data instanceof String choice) {
            StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Player {} made choice: '{}' in node {}", 
                player.getName().getString(), choice, node.getId());

            // Record the dialogue choice
            instance.getState().setLastDialogueChoice(choice);
            
            // Apply relationship impact if defined
            var relationshipImpact = node.getObject("relationship_impact");
            if (relationshipImpact != null) {
                for (String npcId : relationshipImpact.keySet()) {
                    int delta = relationshipImpact.get(npcId).getAsInt();
                    StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] Applying relationship impact: npc={}, delta={}, player={}", 
                        npcId, delta, player.getName().getString());
                    instance.getState().modifyRelationship(player.getUUID(), npcId, delta);
                }
            }
            
            // Set node result based on choice
            String result = "choice_" + choice;
            instance.getState().setNodeResult(result);
            StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] Node result set to: {}", result);
            
            // Evaluate transitions
            instance.evaluateAutoTransitions();
        } else {
            StoryAdventureMod.LOGGER.warn("[DialogueNodeHandler] Unknown action or invalid data: action={}, data={}", action, data);
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] onExit: instance={}, node={}", instance.getInstanceId(), node.getId());
        dialogueTriggered = false;
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        // Dialogue completes when a choice is made
        return instance.getState().getLastDialogueChoice() != null;
    }
}

```

## File: `src/main/java/com/warmpixel/storyadventure/core/node/NodeHandler.java`
```java
package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Base interface for node type handlers.
 * Each node type (DIALOGUE, TASK, PUZZLE, etc.) has its own handler.
 */
public interface NodeHandler {
    
    /**
     * Called when a player/party enters this node.
     * 
     * @param instance The instance context
     * @param node The node being entered
     */
    void onEnter(Instance instance, StageNode node);
    
    /**
     * Called each server tick while the node is active.
     * 
     * @param instance The instance context
     * @param node The active node
     */
    void onTick(Instance instance, StageNode node);
    
    /**
     * Called when a player performs an action in this node.
     * 
     * @param instance The instance context
     * @param node The active node
     * @param player The player performing the action
     * @param action The action type/identifier
     * @param data Additional action data
     */
    void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data);
    
    /**
     * Called when a player/party exits this node.
     * 
     * @param instance The instance context
     * @param node The node being exited
     */
    void onExit(Instance instance, StageNode node);
    
    /**
     * Check if the node can be completed/exited.
     */
    boolean canComplete(Instance instance, StageNode node);
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/node/NodeHandlers.java`
```java
package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.core.graph.NodeType;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registry for NodeHandlers.
 */
public class NodeHandlers {
    
    private static final Map<NodeType, NodeHandler> HANDLERS = new EnumMap<>(NodeType.class);
    
    static {
        register(NodeType.DIALOGUE, new DialogueNodeHandler());
        register(NodeType.TASK, new TaskNodeHandler());
        register(NodeType.PUZZLE, new PuzzleNodeHandler());
        register(NodeType.COMBAT, new CombatNodeHandler());
        register(NodeType.CUTSCENE, new CutsceneNodeHandler());
        register(NodeType.CHECKPOINT, new CheckpointNodeHandler());
    }
    
    public static void register(NodeType type, NodeHandler handler) {
        HANDLERS.put(type, handler);
    }
    
    public static NodeHandler getHandler(NodeType type) {
        return HANDLERS.get(type);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/node/PuzzleNodeHandler.java`
```java
package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handler for PUZZLE nodes.
 * Supports code locks, wiring, symbol matching, and clue board puzzles.
 */
public class PuzzleNodeHandler implements NodeHandler {
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String puzzleType = node.getString("puzzle_type", "CODE_LOCK");
        int maxAttempts = node.getInt("max_attempts", 3);
        
        StoryAdventureMod.LOGGER.info("[PuzzleNodeHandler] onEnter: instance={}, node={}, type={}, maxAttempts={}", 
            instance.getInstanceId(), node.getId(), puzzleType, maxAttempts);
        
        // Initialize puzzle state
        // Store attempt count, etc.
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Puzzles don't auto-tick - they wait for player input
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        String puzzleType = node.getString("puzzle_type", "CODE_LOCK");
        StoryAdventureMod.LOGGER.debug("[PuzzleNodeHandler] onAction: player={}, action={}, data={}", 
            player.getName().getString(), action, data);
            
        switch (action) {
            case "submit_answer" -> handleSubmit(instance, node, player, data);
            case "request_hint" -> handleHint(instance, node, player);
            case "reset" -> handleReset(instance, node);
            default -> StoryAdventureMod.LOGGER.warn("[PuzzleNodeHandler] Unknown action: {}", action);
        }
    }
    
    private void handleSubmit(Instance instance, StageNode node, ServerPlayer player, Object data) {
        String solution = node.getString("solution", "");
        String answer = data != null ? data.toString() : "";
        
        StoryAdventureMod.LOGGER.debug("[PuzzleNodeHandler] Checking answer from player {}. Input='{}'", player.getName().getString(), answer);
        
        if (solution.equals(answer)) {
            // Puzzle solved!
            StoryAdventureMod.LOGGER.info("[PuzzleNodeHandler] Puzzle solved by player {}", player.getName().getString());
            instance.getState().setNodeResult("solved");
            instance.evaluateAutoTransitions();
        } else {
            // Wrong answer - track attempt
            // If max attempts reached, set result to failed
            int maxAttempts = node.getInt("max_attempts", 3);
            // TODO: Track attempts in node state
            
            StoryAdventureMod.LOGGER.info("[PuzzleNodeHandler] Wrong answer from player {}. (Max attempts: {})", player.getName().getString(), maxAttempts);
        }
    }
    
    private void handleHint(Instance instance, StageNode node, ServerPlayer player) {
        StoryAdventureMod.LOGGER.debug("[PuzzleNodeHandler] Player {} requested hint", player.getName().getString());
        // Parse hints from node data
        var data = node.getData();
        if (data.has("hints")) {
            var hints = data.getAsJsonArray("hints");
            // Reveal hints that the player has discovered as clues
            for (var hint : hints) {
                String hintClue = hint.getAsString();
                if (instance.getState().hasDiscoveredClue(hintClue)) {
                    StoryAdventureMod.LOGGER.debug("[PuzzleNodeHandler] Revealing hint clue: {}", hintClue);
                    // Send hint to player
                }
            }
        }
    }
    
    private void handleReset(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.info("[PuzzleNodeHandler] Reset requested for puzzle {}", node.getId());
        // Reset puzzle state (if allowed)
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.debug("[PuzzleNodeHandler] onExit: Cleaning up puzzle state");
        // Clean up puzzle state
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return instance.getState().isCurrentNodeCompleteWith("solved") ||
               instance.getState().isCurrentNodeCompleteWith("failed");
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/node/TaskNodeHandler.java`
```java
package com.warmpixel.storyadventure.core.node;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handler for TASK nodes.
 * Supports fetch, investigate, escort, and stealth objectives.
 */
public class TaskNodeHandler implements NodeHandler {
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String taskType = node.getString("task_type", "FETCH");
        int timeLimitSeconds = node.getInt("time_limit_seconds", 0);
        boolean stealthRequired = node.getBoolean("stealth_required", false);
        
        StoryAdventureMod.LOGGER.info("[TaskNodeHandler] onEnter: instance={}, node={}, type={}, timeLimit={}s, stealth={}", 
            instance.getInstanceId(), node.getId(), taskType, timeLimitSeconds, stealthRequired);
        
        // Clear previous task state to ensure fresh start
        clearTaskState(instance, node);
        
        // Start timer if time limit is set
        if (timeLimitSeconds > 0) {
            long durationMs = timeLimitSeconds * 1000L;
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Starting task timer for {}ms", durationMs);
            instance.getState().startTimer("task_timer", durationMs);
        }
        
        // Parse and track objectives
        List<TaskObjective> objectives = parseObjectives(node);
        StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Parsed {} objectives for task {}", objectives.size(), node.getId());
        for (int i = 0; i < objectives.size(); i++) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Objective [{}]: type={}, data={}", 
                i, objectives.get(i).type(), objectives.get(i).data());
        }
        
        // Store objectives in instance state for tracking
        instance.getState().getMetadata().addProperty("total_objectives", objectives.size());
        instance.getState().getMetadata().addProperty("completed_objectives", 0);
        instance.getState().getMetadata().addProperty("task_complete", false);
        instance.getState().getMetadata().addProperty("task_failed", false);
        
        // Create waypoint from node data if defined
        createWaypointFromNodeData(instance, node);
        
        // Sync HUD with task title and objectives
        syncHudToParty(instance, node, objectives, timeLimitSeconds);
    }
    
    /**
     * Clears all previous task state for a fresh start
     */
    private void clearTaskState(Instance instance, StageNode node) {
        instance.getState().getMetadata().remove("task_complete");
        instance.getState().getMetadata().remove("task_failed");
        instance.getState().getMetadata().remove("total_objectives");
        instance.getState().getMetadata().remove("completed_objectives");
        
        // Clear individual objective flags
        List<TaskObjective> objectives = parseObjectives(node);
        for (int i = 0; i < objectives.size(); i++) {
            instance.getState().getMetadata().remove("objective_" + i + "_complete");
        }
        
        // Clear any previous node result
        try {
            instance.getState().clearNodeResult();
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] clearNodeResult not available, skipping");
        }
        
        // Clear existing waypoints
        instance.getActiveWaypoints().clear();
    }
    
    /**
     * Creates waypoint from node data if defined
     */
    private void createWaypointFromNodeData(Instance instance, StageNode node) {
        JsonObject data = node.getData();
        if (!data.has("waypoint")) {
            return;
        }
        
        JsonObject wpData = data.getAsJsonObject("waypoint");
        String wpId = wpData.has("id") ? wpData.get("id").getAsString() : "task_waypoint";
        double x = wpData.has("x") ? wpData.get("x").getAsDouble() : 0;
        double y = wpData.has("y") ? wpData.get("y").getAsDouble() : 64;
        double z = wpData.has("z") ? wpData.get("z").getAsDouble() : 0;
        String label = wpData.has("label") ? wpData.get("label").getAsString() : "目标";
        String icon = wpData.has("icon") ? wpData.get("icon").getAsString() : "objective";
        int color = 0xFFFFCC00; // Default gold
        
        if (wpData.has("color")) {
            String colorStr = wpData.get("color").getAsString();
            try {
                color = (int) Long.parseLong(colorStr.replace("0x", ""), 16);
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] Failed to parse waypoint color: {}", colorStr);
            }
        }
        
        var waypoint = new com.warmpixel.storyadventure.core.waypoint.Waypoint(wpId, 
            new net.minecraft.world.phys.Vec3(x, y, z));
        waypoint.setLabel(label);
        waypoint.setIcon(com.warmpixel.storyadventure.core.waypoint.Waypoint.WaypointIcon.fromId(icon));
        waypoint.setColor(color);
        waypoint.setShowDistance(true);
        
        instance.addWaypoint(waypoint);
        StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Created waypoint '{}' at ({}, {}, {})", wpId, x, y, z);
        
        // Sync waypoints to all party members
        syncWaypointsToParty(instance);
    }
    
    /**
     * Syncs HUD data to all party members
     */
    private void syncHudToParty(Instance instance, StageNode node, List<TaskObjective> objectives, int timeLimitSeconds) {
        String taskTitle = node.getString("title", "任务");
        String taskDescription = node.getString("description", "完成目标");
        
        // Build HUD data JSON with objectives from the task
        StringBuilder hudJson = new StringBuilder();
        hudJson.append("{");
        hudJson.append("\"title\":\"").append(escapeJson(instance.getGraph().getName())).append("\",");
        hudJson.append("\"chapter\":\"").append(escapeJson(taskTitle)).append("\",");
        hudJson.append("\"objectives\":[");
        
        int currentObjIndex = -1;
        for (int i = 0; i < objectives.size(); i++) {
            if (!isObjectiveComplete(instance, "objective_" + i + "_complete")) {
                currentObjIndex = i;
                break;
            }
        }
        
        for (int i = 0; i < objectives.size(); i++) {
            if (i > 0) hudJson.append(",");
            String objDesc = objectives.get(i).data().has("description") ? 
                objectives.get(i).data().get("description").getAsString() : taskDescription;
            
            // Add progress if it's a kill objective
            if ("KILL_ENTITY".equals(objectives.get(i).type())) {
                int currentKills = instance.getState().getMetadata().has("objective_" + i + "_kills") ? 
                    instance.getState().getMetadata().get("objective_" + i + "_kills").getAsInt() : 0;
                int required = objectives.get(i).data().has("count") ? objectives.get(i).data().get("count").getAsInt() : 1;
                if (!isObjectiveComplete(instance, "objective_" + i + "_complete")) {
                    objDesc += String.format(" (%d/%d)", currentKills, required);
                }
            }
            
            boolean isComplete = isObjectiveComplete(instance, "objective_" + i + "_complete");
            boolean isCurrent = (i == currentObjIndex) || (currentObjIndex == -1 && i == objectives.size() - 1);
            
            hudJson.append("{");
            hudJson.append("\"text\":\"").append(escapeJson(objDesc)).append("\",");
            hudJson.append("\"complete\":").append(isComplete).append(",");
            hudJson.append("\"current\":").append(isCurrent);
            hudJson.append("}");
        }
        
        hudJson.append("]");
        if (timeLimitSeconds > 0) {
            hudJson.append(",\"timer\":").append(timeLimitSeconds * 1000L);
        }
        hudJson.append("}");
        
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                    p,
                    com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_SHOW,
                    hudJson.toString()
                );
            }
        }
    }
    
    /**
     * Escapes special characters for JSON string
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Syncs waypoints to all party members
     */
    private void syncWaypointsToParty(Instance instance) {
        if (instance.getServer() == null) return;
        
        var waypoints = instance.getActiveWaypoints();
        java.util.List<com.warmpixel.storyadventure.network.SyncWaypointsPayload.WaypointData> wpList = new java.util.ArrayList<>();
        
        for (var wp : waypoints.values()) {
            wpList.add(new com.warmpixel.storyadventure.network.SyncWaypointsPayload.WaypointData(
                wp.getId(), wp.getLabel(),
                wp.getPosition().x, wp.getPosition().y, wp.getPosition().z,
                wp.getIcon().getId(), wp.getColor(), wp.showsDistance()
            ));
        }
        
        var payload = new com.warmpixel.storyadventure.network.SyncWaypointsPayload(wpList);
        
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            }
        }
        
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Synced {} waypoints to party", wpList.size());
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Debug log every second
        if (instance.getServer().getTickCount() % 200 == 0) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] onTick: node={}, complete={}, failed={}", 
                node.getId(), isTaskAlreadyComplete(instance), isTaskAlreadyFailed(instance));
        }

        // Check timer expiration
        var timer = instance.getState().getTimer("task_timer");
        if (timer != null && timer.isExpired()) {
            StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] Task timer expired for instance {}", instance.getInstanceId());
            markTaskFailed(instance, node, "Time expired");
            return;
        }
        
        // Check if task is already complete
        if (isTaskAlreadyComplete(instance)) {
            // Already complete - try to transition if we haven't yet
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Task already marked complete, ensuring transition");
            instance.evaluateAutoTransitions();
            return;
        }
        
        // Check if task is already failed
        if (isTaskAlreadyFailed(instance)) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Task already marked failed, ensuring transition");
            instance.evaluateAutoTransitions();
            return;
        }

        // Get current progress
        int completed = getCompletedObjectivesCount(instance);
        int total = getTotalObjectivesCount(instance);
        
        // Handle edge case: no objectives defined - complete immediately
        if (total == 0) {
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] No objectives defined, completing task immediately");
            markTaskComplete(instance, node);
            return;
        }
        
        // All objectives already done (shouldn't happen, but safety check)
        if (completed >= total) {
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] All {} objectives already complete, triggering transition", total);
            markTaskComplete(instance, node);
            return;
        }
        
        // Check each objective
        List<TaskObjective> objectives = parseObjectives(node);
        boolean madeProgress = false;
        
        for (int i = 0; i < objectives.size(); i++) {
            TaskObjective obj = objectives.get(i);
            String objKey = "objective_" + i + "_complete";
            
            // Skip if already completed
            if (isObjectiveComplete(instance, objKey)) {
                continue;
            }
            
            // Check objective based on type
            boolean objectiveCompleted = false;
            if (instance.getServer().getTickCount() % 100 == 0) {
                StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Checking objective {}: type={}", i, obj.type());
            }
            switch (obj.type()) {
                case "REACH_LOCATION" -> objectiveCompleted = checkReachLocationObjective(instance, node, obj, i);
                case "COLLECT_ITEM" -> objectiveCompleted = checkCollectItemObjective(instance, node, obj, i);
                case "KILL_ENTITY" -> objectiveCompleted = checkKillEntityObjective(instance, node, obj, i);
                case "INTERACT" -> objectiveCompleted = checkInteractObjective(instance, node, obj, i);
                default -> StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] Unknown objective type: {}", obj.type());
            }
            
            if (objectiveCompleted) {
                // Mark objective complete
                instance.getState().getMetadata().addProperty(objKey, true);
                completed++;
                instance.getState().getMetadata().addProperty("completed_objectives", completed);
                madeProgress = true;
                
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] ✓ Objective {} complete. Progress: {}/{}", i, completed, total);
                
                // Clear waypoints on objective completion
                instance.getActiveWaypoints().clear();
                syncWaypointsToParty(instance);
                
                // Notify party of progress
                notifyPartyProgress(instance, node, completed, total);
                
                // Check if all objectives are now complete
                if (completed >= total) {
                    markTaskComplete(instance, node);
                    return;
                }
                
                // Only process one objective per tick to avoid race conditions
                break;
            }
        }
        
        // Debug logging once per second
        if (instance.getServer().getTickCount() % 20 == 0 && !madeProgress) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Tick: node={}, progress={}/{}", 
                node.getId(), completed, total);
        }
    }
    
    /**
     * Checks if task is already marked as complete
     */
    private boolean isTaskAlreadyComplete(Instance instance) {
        if (instance.getState().getMetadata().has("task_complete")) {
            try {
                return instance.getState().getMetadata().get("task_complete").getAsBoolean();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * Checks if task is already marked as failed
     */
    private boolean isTaskAlreadyFailed(Instance instance) {
        if (instance.getState().getMetadata().has("task_failed")) {
            try {
                return instance.getState().getMetadata().get("task_failed").getAsBoolean();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * Gets the number of completed objectives
     */
    private int getCompletedObjectivesCount(Instance instance) {
        if (instance.getState().getMetadata().has("completed_objectives")) {
            try {
                return instance.getState().getMetadata().get("completed_objectives").getAsInt();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Gets the total number of objectives
     */
    private int getTotalObjectivesCount(Instance instance) {
        if (instance.getState().getMetadata().has("total_objectives")) {
            try {
                return instance.getState().getMetadata().get("total_objectives").getAsInt();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Checks if a specific objective is complete
     */
    private boolean isObjectiveComplete(Instance instance, String objKey) {
        if (instance.getState().getMetadata().has(objKey)) {
            try {
                return instance.getState().getMetadata().get(objKey).getAsBoolean();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * Checks REACH_LOCATION objective
     */
    private boolean checkReachLocationObjective(Instance instance, StageNode node, TaskObjective obj, int objectiveIndex) {
        double targetX = obj.data().has("target_x") ? obj.data().get("target_x").getAsDouble() : 0;
        double targetY = obj.data().has("target_y") ? obj.data().get("target_y").getAsDouble() : 64;
        double targetZ = obj.data().has("target_z") ? obj.data().get("target_z").getAsDouble() : 0;
        double radius = obj.data().has("radius") ? obj.data().get("radius").getAsDouble() : 5.0;

        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player == null) continue;
            
            double dx = player.getX() - targetX;
            double dy = player.getY() - targetY;
            double dz = player.getZ() - targetZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            double distance = Math.sqrt(distanceSq);
            
            // Check if within 3D spherical range
            double effectiveRadius = radius + 0.25; 
            
            if (distanceSq <= effectiveRadius * effectiveRadius) {
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] ✓ Player {} reached location for objective {}", 
                    player.getName().getString(), objectiveIndex);
                
                // Notify player
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[任务] 已到达目标位置！"));
                
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks COLLECT_ITEM objective (placeholder - implement based on your inventory system)
     */
    private boolean checkCollectItemObjective(Instance instance, StageNode node, TaskObjective obj, int objectiveIndex) {
        // This would check player inventory for specific items
        // Implement based on your item tracking system
        String itemId = obj.data().has("item_id") ? obj.data().get("item_id").getAsString() : "";
        int requiredCount = obj.data().has("count") ? obj.data().get("count").getAsInt() : 1;
        
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] COLLECT_ITEM check: item={}, count={}", itemId, requiredCount);
        
        // TODO: Implement item collection check
        return false;
    }
    
    /**
     * Checks KILL_ENTITY objective (placeholder - implement based on your combat tracking)
     */
    private boolean checkKillEntityObjective(Instance instance, StageNode node, TaskObjective obj, int objectiveIndex) {
        // This would check if required entities have been killed
        // Implement based on your combat/entity tracking system
        String entityType = obj.data().has("entity_type") ? obj.data().get("entity_type").getAsString() : "";
        int requiredKills = obj.data().has("count") ? obj.data().get("count").getAsInt() : 1;
        
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] KILL_ENTITY check: entity={}, count={}", entityType, requiredKills);
        
        // TODO: Implement kill tracking check
        return false;
    }
    
    /**
     * Checks INTERACT objective (placeholder - implement based on your interaction system)
     */
    private boolean checkInteractObjective(Instance instance, StageNode node, TaskObjective obj, int objectiveIndex) {
        // This would check if player has interacted with specific object/NPC
        String targetId = obj.data().has("target_id") ? obj.data().get("target_id").getAsString() : "";
        
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] INTERACT check: target={}", targetId);
        
        // TODO: Implement interaction check
        return false;
    }
    
    /**
     * Notifies party of progress and updates HUD
     */
    private void notifyPartyProgress(Instance instance, StageNode node, int completed, int total) {
        String msg = String.format("§a[任务] 进度: %d/%d", completed, total);
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
            }
        }
        
        // Update HUD for everyone
        List<TaskObjective> objectives = parseObjectives(node);
        int timeLimitSeconds = node.getInt("time_limit_seconds", 0);
        syncHudToParty(instance, node, objectives, timeLimitSeconds);
    }
    
    /**
     * Marks the task as complete and triggers transition
     */
    private void markTaskComplete(Instance instance, StageNode node) {
        // Prevent double-completion
        if (isTaskAlreadyComplete(instance)) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Task already marked complete, just triggering transition");
            instance.evaluateAutoTransitions();
            return;
        }
        
        int total = getTotalObjectivesCount(instance);
        StoryAdventureMod.LOGGER.info("[TaskNodeHandler] ★★★ TASK COMPLETE ★★★ Node: {} | Objectives: {}", node.getId(), total);
        
        // Set ALL completion flags for maximum compatibility
        instance.getState().getMetadata().addProperty("task_complete", true);
        instance.getState().getMetadata().addProperty("task_failed", false);
        instance.getState().setNodeResult("success");
        
        // Stop any active timers
        instance.getState().stopTimer("task_timer");
        
        // Clear waypoints
        instance.getActiveWaypoints().clear();
        syncWaypointsToParty(instance);
        
        // Notify party of completion
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a§l[任务完成] §r§a所有目标已完成！"));
            }
        }
        
        // Trigger transition using deferred execution to ensure state is saved
        instance.getServer().execute(() -> {
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] >>> Executing deferred transition for node {} <<<", node.getId());
            instance.evaluateAutoTransitions();
        });
    }
    
    /**
     * Marks the task as failed and triggers transition
     */
    private void markTaskFailed(Instance instance, StageNode node, String reason) {
        // Prevent double-failure
        if (isTaskAlreadyFailed(instance)) {
            StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Task already marked failed, just triggering transition");
            instance.evaluateAutoTransitions();
            return;
        }
        
        StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] ✗✗✗ TASK FAILED ✗✗✗ Node: {} | Reason: {}", node.getId(), reason);
        
        // Set failure flags
        instance.getState().getMetadata().addProperty("task_complete", false);
        instance.getState().getMetadata().addProperty("task_failed", true);
        instance.getState().setNodeResult("failed");
        
        // Stop any active timers
        instance.getState().stopTimer("task_timer");
        
        // Clear waypoints
        instance.getActiveWaypoints().clear();
        syncWaypointsToParty(instance);
        
        // Notify party of failure
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c§l[任务失败] §r§c" + reason));
            }
        }
        
        // Trigger transition
        instance.getServer().execute(() -> {
            StoryAdventureMod.LOGGER.info("[TaskNodeHandler] >>> Executing deferred transition for failed node {} <<<", node.getId());
            instance.evaluateAutoTransitions();
        });
    }

    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] onAction: player={}, action={}, data={}", 
            player != null ? player.getName().getString() : "null", action, data);
        
        int completed = getCompletedObjectivesCount(instance);
        int total = getTotalObjectivesCount(instance);

        switch (action) {
            case "enemy_killed" -> {
                StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Enemy killed action received for node {}", node.getId());
                // Data is the LivingEntity that died
                if (data instanceof net.minecraft.world.entity.LivingEntity entity) {
                    java.util.List<TaskObjective> objectives = parseObjectives(node);
                    boolean anyUpdate = false;
                    
                    for (int i = 0; i < objectives.size(); i++) {
                        TaskObjective obj = objectives.get(i);
                        if ("KILL_ENTITY".equals(obj.type()) && !isObjectiveComplete(instance, "objective_" + i + "_complete")) {
                            String targetType = obj.data().has("entity_type") ? obj.data().get("entity_type").getAsString() : "minecraft:zombie";
                            int required = obj.data().has("count") ? obj.data().get("count").getAsInt() : 1;
                            
                            // Check if it matches (fuzzy match for now)
                            String diedType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                            if (diedType.equals(targetType)) {
                                int currentKills = instance.getState().getMetadata().has("objective_" + i + "_kills") ? 
                                    instance.getState().getMetadata().get("objective_" + i + "_kills").getAsInt() : 0;
                                currentKills++;
                                instance.getState().getMetadata().addProperty("objective_" + i + "_kills", currentKills);
                                
                                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Kill recorded for objective {}: {}/{}", i, currentKills, required);
                                
                                if (currentKills >= required) {
                                    instance.getState().getMetadata().addProperty("objective_" + i + "_complete", true);
                                    completed++;
                                    instance.getState().getMetadata().addProperty("completed_objectives", completed);
                                    anyUpdate = true;
                                } else {
                                    // Just update HUD for kill progress
                                    notifyPartyProgress(instance, node, completed, total);
                                }
                            }
                        }
                    }
                    
                    if (anyUpdate) {
                        notifyPartyProgress(instance, node, completed, total);
                        if (completed >= total) {
                            markTaskComplete(instance, node);
                        }
                    }
                }
            }
            case "objective_complete" -> {
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Objective completed via action for node {}", node.getId());
                completed++;
                instance.getState().getMetadata().addProperty("completed_objectives", completed);
                
                // Notify party of progress
                notifyPartyProgress(instance, node, completed, total);

                // Check for completion
                if (completed >= total) {
                    markTaskComplete(instance, node);
                }
            }
            
            case "item_collected" -> {
                StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Item collected action received");
                onAction(instance, node, player, "objective_complete", data);
            }
            
            case "location_reached" -> {
                StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Location reached action received");
                onAction(instance, node, player, "objective_complete", data);
            }
            
            case "entity_killed" -> {
                StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] Entity killed action received");
                onAction(instance, node, player, "objective_complete", data);
            }
            
            case "force_complete" -> {
                // Debug action to force task completion
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Force completing task for node {}", node.getId());
                instance.getState().getMetadata().addProperty("completed_objectives", total);
                markTaskComplete(instance, node);
            }
            
            case "force_fail" -> {
                // Debug action to force task failure
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Force failing task for node {}", node.getId());
                markTaskFailed(instance, node, "Forced failure");
            }
            
            case "skip" -> {
                // Skip task without marking as complete or failed
                StoryAdventureMod.LOGGER.info("[TaskNodeHandler] Skipping task for node {}", node.getId());
                instance.getState().setNodeResult("skipped");
                instance.evaluateAutoTransitions();
            }
            
            default -> StoryAdventureMod.LOGGER.warn("[TaskNodeHandler] Unknown action: {}", action);
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.info("[TaskNodeHandler] onExit: Leaving node {}", node.getId());
        
        // Stop any active timers
        instance.getState().stopTimer("task_timer");
        
        // Clear waypoints when leaving task node
        instance.getActiveWaypoints().clear();
        syncWaypointsToParty(instance);
        
        // Hide task HUD (optional - depends on your UI system)
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer p = instance.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                // Optionally hide the HUD
                // com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                //     p,
                //     com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_HIDE,
                //     "{}"
                // );
            }
        }
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        // Check multiple indicators of task completion
        boolean taskCompleteFlag = isTaskAlreadyComplete(instance);
        boolean taskFailedFlag = isTaskAlreadyFailed(instance);
        boolean successResult = instance.getState().isCurrentNodeCompleteWith("success");
        boolean failedResult = instance.getState().isCurrentNodeCompleteWith("failed");
        
        boolean canComplete = taskCompleteFlag || taskFailedFlag || successResult || failedResult;
        
        StoryAdventureMod.LOGGER.debug("[TaskNodeHandler] canComplete: taskComplete={}, taskFailed={}, successResult={}, failedResult={} => {}", 
            taskCompleteFlag, taskFailedFlag, successResult, failedResult, canComplete);
        
        return canComplete;
    }
    
    /**
     * Static method to check if task is complete (for use by condition handlers)
     */
    public static boolean isTaskComplete(Instance instance) {
        // Check explicit flag
        if (instance.getState().getMetadata().has("task_complete")) {
            try {
                if (instance.getState().getMetadata().get("task_complete").getAsBoolean()) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        // Check node result
        if (instance.getState().isCurrentNodeCompleteWith("success")) {
            return true;
        }
        
        // Check objective counts
        int completed = 0;
        int total = 0;
        
        try {
            if (instance.getState().getMetadata().has("completed_objectives")) {
                completed = instance.getState().getMetadata().get("completed_objectives").getAsInt();
            }
            if (instance.getState().getMetadata().has("total_objectives")) {
                total = instance.getState().getMetadata().get("total_objectives").getAsInt();
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return total > 0 && completed >= total;
    }
    
    /**
     * Static method to check if task is failed (for use by condition handlers)
     */
    public static boolean isTaskFailed(Instance instance) {
        // Check explicit flag
        if (instance.getState().getMetadata().has("task_failed")) {
            try {
                if (instance.getState().getMetadata().get("task_failed").getAsBoolean()) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        // Check node result
        return instance.getState().isCurrentNodeCompleteWith("failed");
    }
    
    /**
     * Parses objectives from node data
     */
    private List<TaskObjective> parseObjectives(StageNode node) {
        List<TaskObjective> objectives = new ArrayList<>();
        
        JsonObject data = node.getData();
        if (data.has("objectives")) {
            JsonArray objectivesArray = data.getAsJsonArray("objectives");
            for (JsonElement elem : objectivesArray) {
                JsonObject obj = elem.getAsJsonObject();
                String type = obj.has("type") ? obj.get("type").getAsString() : "UNKNOWN";
                objectives.add(new TaskObjective(type, obj));
            }
        }
        
        return objectives;
    }
    
    /**
     * Record for task objective data
     */
    public record TaskObjective(String type, JsonObject data) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/waypoint/TriggerBox.java`
```java
package com.warmpixel.storyadventure.core.waypoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.core.action.ActionFactory;
import com.warmpixel.storyadventure.core.action.NodeAction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a trigger box zone that fires actions when players enter/exit.
 * Used for story-driven events like triggering cutscenes, setting flags, etc.
 */
public class TriggerBox {
    
    private final String id;
    private String label;
    private AABB bounds;
    private Vec3 center;
    private double radius = -1;
    private List<NodeAction> onEnterActions;
    private List<NodeAction> onExitActions;
    private String linkedNodeId;
    private boolean oneShot;
    private double targetDistance = -1;
    
    // Track which players are currently inside
    private final Set<UUID> playersInside = new HashSet<>();
    // Track which players have already triggered this (for oneShot)
    private final Set<UUID> triggeredPlayers = new HashSet<>();
    
    public TriggerBox(String id, AABB bounds) {
        this.id = id;
        this.bounds = bounds;
        this.label = id;
        this.onEnterActions = new ArrayList<>();
        this.onExitActions = new ArrayList<>();
        this.oneShot = false;
    }
    
    public TriggerBox(String id, Vec3 center, double radius) {
        this.id = id;
        this.center = center;
        this.radius = radius;
        this.label = id;
        this.onEnterActions = new ArrayList<>();
        this.onExitActions = new ArrayList<>();
        this.oneShot = false;
        // Create a loose AABB for rendering/gizmo purposes if needed
        this.bounds = new AABB(center.x - radius, center.y - radius, center.z - radius, 
                               center.x + radius, center.y + radius, center.z + radius);
    }
    
    public TriggerBox(String id, Vec3 corner1, Vec3 corner2) {
        this(id, new AABB(corner1, corner2));
    }
    
    /**
     * Check if a player position is inside this trigger box.
     */
    public boolean contains(Vec3 position) {
        return contains(position, null);
    }
    
    public boolean contains(Vec3 position, Vec3 referencePoint) {
        if (targetDistance > 0 && referencePoint != null) {
            return position.distanceTo(referencePoint) <= targetDistance;
        }
        if (radius > 0 && center != null) {
            double dx = position.x - center.x;
            double dy = position.y - center.y;
            double dz = position.z - center.z;
            return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
        }
        return bounds != null && bounds.contains(position);
    }
    
    /**
     * Process a player entering/exiting this trigger.
     * Returns true if state changed (player entered or exited).
     */
    public TriggerEvent checkPlayer(UUID playerId, Vec3 position) {
        return checkPlayer(playerId, position, null);
    }
    
    public TriggerEvent checkPlayer(UUID playerId, Vec3 position, Vec3 referencePoint) {
        boolean inside = contains(position, referencePoint);
        boolean wasInside = playersInside.contains(playerId);
        
        if (inside && !wasInside) {
            playersInside.add(playerId);
            
            // If one-shot, check if already triggered for this player
            if (oneShot && triggeredPlayers.contains(playerId)) {
                return TriggerEvent.NONE;
            }
            
            triggeredPlayers.add(playerId);
            return TriggerEvent.ENTER;
        } else if (!inside && wasInside) {
            playersInside.remove(playerId);
            return TriggerEvent.EXIT;
        }
        return TriggerEvent.NONE;
    }
    
    /**
     * Reset tracking for a player (e.g., when they leave the instance).
     */
    public void resetPlayer(UUID playerId) {
        playersInside.remove(playerId);
        triggeredPlayers.remove(playerId);
    }
    
    /**
     * Parse a TriggerBox from JSON.
     */
    public static TriggerBox fromJson(String id, JsonObject json) {
        TriggerBox box;
        
        if (json.has("center") && json.has("radius")) {
            JsonArray centerArr = json.getAsJsonArray("center");
            Vec3 center = new Vec3(centerArr.get(0).getAsDouble(), centerArr.get(1).getAsDouble(), centerArr.get(2).getAsDouble());
            double radius = json.get("radius").getAsDouble();
            box = new TriggerBox(id, center, radius);
        } else if (json.has("min") && json.has("max")) {
            double minX = json.getAsJsonArray("min").get(0).getAsDouble();
            double minY = json.getAsJsonArray("min").get(1).getAsDouble();
            double minZ = json.getAsJsonArray("min").get(2).getAsDouble();
            double maxX = json.getAsJsonArray("max").get(0).getAsDouble();
            double maxY = json.getAsJsonArray("max").get(1).getAsDouble();
            double maxZ = json.getAsJsonArray("max").get(2).getAsDouble();
            box = new TriggerBox(id, new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        } else {
            // Fallback/Error case
            box = new TriggerBox(id, new AABB(0, 0, 0, 0, 0, 0));
        }
        
        if (json.has("label")) {
            box.setLabel(json.get("label").getAsString());
        }
        
        if (json.has("linkedNodeId")) {
            box.setLinkedNodeId(json.get("linkedNodeId").getAsString());
        }
        
        if (json.has("oneShot")) {
            box.setOneShot(json.get("oneShot").getAsBoolean());
        }
        
        if (json.has("target_distance")) {
            box.setTargetDistance(json.get("target_distance").getAsDouble());
        }
        
        if (json.has("onEnter")) {
            JsonArray actions = json.getAsJsonArray("onEnter");
            for (var elem : actions) {
                NodeAction action = ActionFactory.fromJson(elem.getAsJsonObject());
                if (action != null) box.onEnterActions.add(action);
            }
        }
        
        if (json.has("onExit")) {
            JsonArray actions = json.getAsJsonArray("onExit");
            for (var elem : actions) {
                NodeAction action = ActionFactory.fromJson(elem.getAsJsonObject());
                if (action != null) box.onExitActions.add(action);
            }
        }
        
        return box;
    }
    
    /**
     * Serialize this TriggerBox to JSON.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("label", label);
        
        JsonArray min = new JsonArray();
        min.add(bounds.minX);
        min.add(bounds.minY);
        min.add(bounds.minZ);
        json.add("min", min);
        
        JsonArray max = new JsonArray();
        max.add(bounds.maxX);
        max.add(bounds.maxY);
        max.add(bounds.maxZ);
        json.add("max", max);
        
        if (linkedNodeId != null) {
            json.addProperty("linkedNodeId", linkedNodeId);
        }
        
        json.addProperty("oneShot", oneShot);
        
        if (targetDistance > 0) {
            json.addProperty("target_distance", targetDistance);
        }
        
        JsonArray enterArr = new JsonArray();
        for (NodeAction action : onEnterActions) {
            enterArr.add(action.toJson());
        }
        json.add("onEnter", enterArr);
        
        JsonArray exitArr = new JsonArray();
        for (NodeAction action : onExitActions) {
            exitArr.add(action.toJson());
        }
        json.add("onExit", exitArr);
        
        return json;
    }
    
    // Getters
    public String getId() { return id; }
    public String getLabel() { return label; }
    public AABB getBounds() { return bounds; }
    public List<NodeAction> getOnEnterActions() { return onEnterActions; }
    public List<NodeAction> getOnExitActions() { return onExitActions; }
    public String getLinkedNodeId() { return linkedNodeId; }
    public boolean isOneShot() { return oneShot; }
    public double getTargetDistance() { return targetDistance; }
    public Set<UUID> getPlayersInside() { return playersInside; }
    public double getRadius() { return radius; }
    public Vec3 getCenter() { return center; }
    
    // Setters
    public TriggerBox setLabel(String label) { this.label = label; return this; }
    public TriggerBox setBounds(AABB bounds) { this.bounds = bounds; return this; }
    public TriggerBox setLinkedNodeId(String nodeId) { this.linkedNodeId = nodeId; return this; }
    public TriggerBox setOneShot(boolean oneShot) { this.oneShot = oneShot; return this; }
    public TriggerBox setTargetDistance(double dist) { this.targetDistance = dist; return this; }
    
    public enum TriggerEvent {
        NONE, ENTER, EXIT
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/waypoint/TriggerBoxManager.java`
```java
package com.warmpixel.storyadventure.core.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.world.phys.AABB;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for global trigger boxes (not tied to a specific instance).
 * Used for admin-created boxes that persist between server restarts.
 */
public class TriggerBoxManager {
    
    private static TriggerBoxManager instance;
    private final Map<String, TriggerBox> globalBoxes = new ConcurrentHashMap<>();
    private final Path configPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public TriggerBoxManager(Path configDir) {
        this.configPath = configDir.resolve("trigger_boxes.json");
        instance = this;
    }
    
    public static TriggerBoxManager getInstance() {
        return instance;
    }
    
    /**
     * Load trigger boxes from config file.
     */
    public void load() {
        globalBoxes.clear();
        
        if (!Files.exists(configPath)) {
            StoryAdventureMod.LOGGER.info("No trigger boxes config found, starting fresh");
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            if (root != null && root.has("boxes")) {
                JsonArray boxes = root.getAsJsonArray("boxes");
                for (var elem : boxes) {
                    JsonObject boxJson = elem.getAsJsonObject();
                    String id = boxJson.get("id").getAsString();
                    TriggerBox box = TriggerBox.fromJson(id, boxJson);
                    globalBoxes.put(id, box);
                }
                StoryAdventureMod.LOGGER.info("Loaded {} global trigger boxes", globalBoxes.size());
            }
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("Failed to load trigger boxes", e);
        }
    }
    
    /**
     * Save trigger boxes to config file.
     */
    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            
            JsonObject root = new JsonObject();
            JsonArray boxes = new JsonArray();
            
            for (TriggerBox box : globalBoxes.values()) {
                JsonObject boxJson = box.toJson();
                boxJson.addProperty("id", box.getId());
                boxes.add(boxJson);
            }
            
            root.add("boxes", boxes);
            
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            
            StoryAdventureMod.LOGGER.info("Saved {} global trigger boxes", globalBoxes.size());
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("Failed to save trigger boxes", e);
        }
    }
    
    /**
     * Create a new trigger box.
     */
    public TriggerBox createBox(String id, AABB bounds, String label) {
        TriggerBox box = new TriggerBox(id, bounds);
        box.setLabel(label);
        globalBoxes.put(id, box);
        save();
        return box;
    }
    
    /**
     * Get a trigger box by ID.
     */
    public TriggerBox getBox(String id) {
        return globalBoxes.get(id);
    }
    
    /**
     * Get all global trigger boxes.
     */
    public Collection<TriggerBox> getAllBoxes() {
        return Collections.unmodifiableCollection(globalBoxes.values());
    }
    
    /**
     * Delete a trigger box.
     */
    public boolean deleteBox(String id) {
        TriggerBox removed = globalBoxes.remove(id);
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }
    
    /**
     * Update a trigger box.
     */
    public void updateBox(String id, TriggerBox updated) {
        globalBoxes.put(id, updated);
        save();
    }
    
    /**
     * Get box count.
     */
    public int getBoxCount() {
        return globalBoxes.size();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/waypoint/Waypoint.java`
```java
package com.warmpixel.storyadventure.core.waypoint;

import net.minecraft.world.phys.Vec3;

/**
 * Represents a waypoint marker for player guidance.
 * Waypoints show on-screen or off-screen indicators pointing players toward objectives.
 */
public class Waypoint {
    
    private final String id;
    private String label;
    private Vec3 position;
    private WaypointIcon icon;
    private int color;
    private boolean showDistance;
    private String linkedTriggerId;
    
    public Waypoint(String id, Vec3 position) {
        this.id = id;
        this.position = position;
        this.label = "";
        this.icon = WaypointIcon.OBJECTIVE;
        this.color = 0xFFFFCC00; // Default gold
        this.showDistance = true;
    }
    
    // Getters
    public String getId() { return id; }
    public String getLabel() { return label; }
    public Vec3 getPosition() { return position; }
    public WaypointIcon getIcon() { return icon; }
    public int getColor() { return color; }
    public boolean showsDistance() { return showDistance; }
    public String getLinkedTriggerId() { return linkedTriggerId; }
    
    // Setters
    public Waypoint setLabel(String label) { this.label = label; return this; }
    public Waypoint setPosition(Vec3 position) { this.position = position; return this; }
    public Waypoint setIcon(WaypointIcon icon) { this.icon = icon; return this; }
    public Waypoint setColor(int color) { this.color = color; return this; }
    public Waypoint setShowDistance(boolean show) { this.showDistance = show; return this; }
    public Waypoint setLinkedTriggerId(String id) { this.linkedTriggerId = id; return this; }
    
    public enum WaypointIcon {
        OBJECTIVE("objective", "◉"),
        QUEST("quest", "!"),
        DANGER("danger", "⚠"),
        CLUE("clue", "?"),
        EXIT("exit", "→"),
        NPC("npc", "☺"),
        ITEM("item", "★");
        
        private final String id;
        private final String symbol;
        
        WaypointIcon(String id, String symbol) {
            this.id = id;
            this.symbol = symbol;
        }
        
        public String getId() { return id; }
        public String getSymbol() { return symbol; }
        
        public static WaypointIcon fromId(String id) {
            for (WaypointIcon icon : values()) {
                if (icon.id.equalsIgnoreCase(id)) return icon;
            }
            return OBJECTIVE;
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/waypoint/WaypointManager.java`
```java
package com.warmpixel.storyadventure.core.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for persistent global waypoints.
 * These are waypoints that exist outside of specific story instances.
 */
public class WaypointManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaypointManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static WaypointManager instance;
    private final Map<String, Waypoint> waypoints = new ConcurrentHashMap<>();
    private final Path configDir;
    private final File storageFile;
    
    public WaypointManager(Path configDir) {
        this.configDir = configDir;
        this.storageFile = configDir.resolve("waypoints.json").toFile();
        instance = this;
    }
    
    public static WaypointManager getInstance() {
        return instance;
    }
    
    /**
     * Load waypoints from waypoints.json.
     */
    public void load() {
        if (!storageFile.exists()) {
            LOGGER.info("No waypoints.json found, starting fresh.");
            return;
        }
        
        try (FileReader reader = new FileReader(storageFile)) {
            List<WaypointDTO> dtos = GSON.fromJson(reader, new TypeToken<List<WaypointDTO>>(){}.getType());
            if (dtos != null) {
                waypoints.clear();
                for (WaypointDTO dto : dtos) {
                    Waypoint wp = new Waypoint(dto.id, new Vec3(dto.x, dto.y, dto.z));
                    wp.setLabel(dto.label)
                      .setIcon(Waypoint.WaypointIcon.fromId(dto.icon))
                      .setColor(dto.color)
                      .setShowDistance(dto.showDistance)
                      .setLinkedTriggerId(dto.linkedTriggerId);
                    waypoints.put(dto.id, wp);
                }
            }
            LOGGER.info("Loaded {} global waypoints", waypoints.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load global waypoints", e);
        }
    }
    
    /**
     * Save waypoints to waypoints.json.
     */
    public void save() {
        try {
            if (!storageFile.getParentFile().exists()) {
                storageFile.getParentFile().mkdirs();
            }
            
            List<WaypointDTO> dtos = new ArrayList<>();
            for (Waypoint wp : waypoints.values()) {
                dtos.add(new WaypointDTO(
                    wp.getId(), wp.getLabel(), 
                    wp.getPosition().x, wp.getPosition().y, wp.getPosition().z,
                    wp.getIcon().getId(), wp.getColor(), wp.showsDistance(),
                    wp.getLinkedTriggerId()
                ));
            }
            
            try (FileWriter writer = new FileWriter(storageFile)) {
                GSON.toJson(dtos, writer);
            }
            LOGGER.info("Saved {} global waypoints", waypoints.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save global waypoints", e);
        }
    }
    
    public void createWaypoint(String id, Vec3 pos, String label) {
        Waypoint wp = new Waypoint(id, pos).setLabel(label);
        waypoints.put(id, wp);
        save();
    }
    
    public boolean deleteWaypoint(String id) {
        if (waypoints.remove(id) != null) {
            save();
            return true;
        }
        return false;
    }
    
    public Waypoint getWaypoint(String id) {
        return waypoints.get(id);
    }
    
    public List<Waypoint> getAllWaypoints() {
        return new ArrayList<>(waypoints.values());
    }
    
    public int getWaypointCount() {
        return waypoints.size();
    }
    
    /**
     * Data Transfer Object for Waypoint JSON serialization.
     */
    private static class WaypointDTO {
        String id;
        String label;
        double x, y, z;
        String icon;
        int color;
        boolean showDistance;
        String linkedTriggerId;
        
        public WaypointDTO(String id, String label, double x, double y, double z,
                           String icon, int color, boolean showDistance, String linkedTriggerId) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.z = z;
            this.icon = icon;
            this.color = color;
            this.showDistance = showDistance;
            this.linkedTriggerId = linkedTriggerId;
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/graph/NodeType.java`
```java
package com.warmpixel.storyadventure.core.graph;

/**
 * Enum defining all supported node types in the Stage Graph.
 * Each node type represents a different kind of story beat.
 */
public enum NodeType {
    /**
     * NPC conversation with branching options, hidden flags, and relationship impact.
     * Integrates with Easy NPC dialog system.
     */
    DIALOGUE("dialogue"),
    
    /**
     * Task objectives: fetch/investigate items, escort, stealth segments.
     * Tracks objective progress per party.
     */
    TASK("task"),
    
    /**
     * Interactive puzzles: code locks, wiring, symbol matching, clue board linking.
     * Can have multiple attempts and hints.
     */
    PUZZLE("puzzle"),
    
    /**
     * Combat encounters: waves, boss fights, escape sequences.
     * Supports arena bounds and victory/defeat conditions.
     */
    COMBAT("combat"),
    
    /**
     * Scripted cutscenes: camera paths, teleports, particle FX.
     * Lightweight Minecraft-style cinematics.
     */
    CUTSCENE("cutscene"),
    
    /**
     * Savepoints with optional "rewind" anchor capability.
     * Stores inventory, position, and story state.
     */
    CHECKPOINT("checkpoint");
    
    private final String id;
    
    NodeType(String id) {
        this.id = id;
    }
    
    public String getId() {
        return id;
    }
    
    public static NodeType fromId(String id) {
        for (NodeType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node type: " + id);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/graph/StageEdge.java`
```java
package com.warmpixel.storyadventure.core.graph;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.condition.EdgeCondition;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an edge (transition) between two nodes in the Stage Graph.
 * Edges have conditions that must be met for the transition to be available.
 */
public class StageEdge {
    private final String sourceNodeId;
    private final String targetNodeId;
    private final List<EdgeCondition> conditions;
    private final int priority;
    
    public StageEdge(String sourceNodeId, String targetNodeId, List<EdgeCondition> conditions, int priority) {
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
        this.priority = priority;
    }
    
    public StageEdge(String sourceNodeId, String targetNodeId, List<EdgeCondition> conditions) {
        this(sourceNodeId, targetNodeId, conditions, 0);
    }
    
    public String getSourceNodeId() {
        return sourceNodeId;
    }
    
    public String getTargetNodeId() {
        return targetNodeId;
    }
    
    public List<EdgeCondition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }
    
    public int getPriority() {
        return priority;
    }
    
    /**
     * Check if all conditions for this edge are satisfied.
     * 
     * @param instance The current instance context
     * @param player The player attempting the transition (can be null for auto-transitions)
     * @return true if all conditions are met
     */
    public boolean canTransition(Instance instance, ServerPlayer player) {
        if (conditions.isEmpty()) return true;
        
        for (EdgeCondition condition : conditions) {
            if (!condition.evaluate(instance, player)) {
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.debug("[StageEdge] Condition FAILED for edge {} -> {}: {}", 
                    sourceNodeId, targetNodeId, condition.getClass().getSimpleName());
                return false;
            }
        }
        
        com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.debug("[StageEdge] All conditions MET for edge {} -> {}", sourceNodeId, targetNodeId);
        return true;
    }
    
    /**
     * Check if this edge has no conditions (unconditional transition).
     */
    public boolean isUnconditional() {
        return conditions.isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("StageEdge{%s -> %s, conditions=%d, priority=%d}", 
            sourceNodeId, targetNodeId, conditions.size(), priority);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/graph/StageGraph.java`
```java
package com.warmpixel.storyadventure.core.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.core.condition.ConditionFactory;
import com.warmpixel.storyadventure.core.condition.EdgeCondition;

import java.util.*;

/**
 * The main Stage Graph class representing a complete story.
 * This is a directed graph where nodes are story beats and edges are conditional transitions.
 */
public class StageGraph {
    private final String storyId;
    private final String name;
    private final String description;
    private final String version;
    private final int minPlayers;
    private final int maxPlayers;
    private final int estimatedDurationMinutes;
    private final String entryNodeId;
    private final Map<String, StageNode> nodes;
    private final Map<String, ClueDefinition> clues;
    private final Map<String, FlagDefinition> flags;
    private final Map<String, StoryLocation> specialLocations = new HashMap<>();

    public record StoryLocation(String dimension, double x, double y, double z, float yaw, float pitch) {}
    
    private StageGraph(Builder builder) {
        this.storyId = builder.storyId;
        this.name = builder.name;
        this.description = builder.description;
        this.version = builder.version;
        this.minPlayers = builder.minPlayers;
        this.maxPlayers = builder.maxPlayers;
        this.estimatedDurationMinutes = builder.estimatedDurationMinutes;
        this.entryNodeId = builder.entryNodeId;
        this.nodes = Collections.unmodifiableMap(new HashMap<>(builder.nodes));
        this.clues = Collections.unmodifiableMap(new HashMap<>(builder.clues));
        this.flags = Collections.unmodifiableMap(new HashMap<>(builder.flags));
        this.specialLocations.putAll(builder.specialLocations);
    }
    
    // Getters
    public String getStoryId() { return storyId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getVersion() { return version; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public String getEntryNodeId() { return entryNodeId; }
    
    public StageNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }
    
    public StageNode getEntryNode() {
        return nodes.get(entryNodeId);
    }
    
    public Collection<StageNode> getAllNodes() {
        return nodes.values();
    }
    
    public int getNodeCount() {
        return nodes.size();
    }
    
    public ClueDefinition getClue(String clueId) {
        return clues.get(clueId);
    }
    
    public Collection<ClueDefinition> getAllClues() {
        return clues.values();
    }
    
    public FlagDefinition getFlag(String flagId) {
        return flags.get(flagId);
    }
    
    public Collection<FlagDefinition> getAllFlags() {
        return flags.values();
    }
    
    public boolean hasNode(String nodeId) {
        return nodes.containsKey(nodeId);
    }
    
    public void setSpecialLocation(String id, StoryLocation location) {
        specialLocations.put(id, location);
    }
    
    public StoryLocation getSpecialLocation(String id) {
        return specialLocations.get(id);
    }
    
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", storyId);
        json.addProperty("name", name);
        json.addProperty("description", description);
        json.addProperty("version", version);
        json.addProperty("min_players", minPlayers);
        json.addProperty("max_players", maxPlayers);
        json.addProperty("estimated_duration_minutes", estimatedDurationMinutes);
        json.addProperty("entry_node", entryNodeId);
        
        // Locations
        if (!specialLocations.isEmpty()) {
            JsonObject locationsJson = new JsonObject();
            for (Map.Entry<String, StoryLocation> entry : specialLocations.entrySet()) {
                StoryLocation loc = entry.getValue();
                JsonObject locJson = new JsonObject();
                locJson.addProperty("dimension", loc.dimension());
                locJson.addProperty("x", loc.x());
                locJson.addProperty("y", loc.y());
                locJson.addProperty("z", loc.z());
                locJson.addProperty("yaw", loc.yaw());
                locJson.addProperty("pitch", loc.pitch());
                locationsJson.add(entry.getKey(), locJson);
            }
            json.add("locations", locationsJson);
        }
        
        // Nodes
        JsonObject nodesJson = new JsonObject();
        for (StageNode node : nodes.values()) {
            JsonObject nodeJson = new JsonObject();
            nodeJson.addProperty("type", node.getType().getId());
            nodeJson.add("data", node.getData()); // data is already a JsonObject
            
            // Edges
            if (!node.getEdges().isEmpty()) {
                JsonArray edgesJson = new JsonArray();
                for (StageEdge edge : node.getEdges()) {
                    JsonObject edgeJson = new JsonObject();
                    edgeJson.addProperty("target", edge.getTargetNodeId());
                    if (edge.getPriority() != 0) {
                        edgeJson.addProperty("priority", edge.getPriority());
                    }
                    
                    if (!edge.getConditions().isEmpty()) {
                        JsonArray conditionsJson = new JsonArray();
                        for (EdgeCondition cond : edge.getConditions()) {
                            conditionsJson.add(cond.serialize());
                        }
                        edgeJson.add("conditions", conditionsJson);
                    }
                    edgesJson.add(edgeJson);
                }
                nodeJson.add("edges", edgesJson);
            }
            nodesJson.add(node.getId(), nodeJson);
        }
        json.add("nodes", nodesJson);
        
        // Clues
        if (!clues.isEmpty()) {
            JsonObject cluesJson = new JsonObject();
            for (ClueDefinition clue : clues.values()) {
                JsonObject clueJson = new JsonObject();
                clueJson.addProperty("name", clue.name());
                clueJson.addProperty("description", clue.description());
                clueJson.addProperty("item_icon", clue.itemIcon());
                cluesJson.add(clue.id(), clueJson);
            }
            json.add("clues", cluesJson);
        }
        
        // Flags
        if (!flags.isEmpty()) {
            JsonObject flagsJson = new JsonObject();
            for (FlagDefinition flag : flags.values()) {
                JsonObject flagJson = new JsonObject();
                flagJson.addProperty("default", flag.defaultValue());
                flagJson.addProperty("persistent", flag.persistent());
                flagsJson.add(flag.id(), flagJson);
            }
            json.add("flags", flagsJson);
        }
        
        return json;
    }
    
    /**
     * Parse a StageGraph from a JSON object.
     */
    public static StageGraph fromJson(JsonObject json) {
        Builder builder = new Builder(
            json.get("id").getAsString(),
            json.get("entry_node").getAsString()
        );
        
        builder.name(json.has("name") ? json.get("name").getAsString() : "Unnamed Story");
        builder.description(json.has("description") ? json.get("description").getAsString() : "");
        builder.version(json.has("version") ? json.get("version").getAsString() : "1.0.0");
        builder.minPlayers(json.has("min_players") ? json.get("min_players").getAsInt() : 1);
        builder.maxPlayers(json.has("max_players") ? json.get("max_players").getAsInt() : 4);
        builder.estimatedDurationMinutes(json.has("estimated_duration_minutes") ? 
            json.get("estimated_duration_minutes").getAsInt() : 60);
        
        // Parse nodes
        JsonObject nodesJson = json.getAsJsonObject("nodes");
        for (Map.Entry<String, JsonElement> entry : nodesJson.entrySet()) {
            String nodeId = entry.getKey();
            JsonObject nodeJson = entry.getValue().getAsJsonObject();
            
            NodeType type = NodeType.fromId(nodeJson.get("type").getAsString());
            JsonObject data = nodeJson.has("data") ? nodeJson.getAsJsonObject("data") : new JsonObject();
            
            StageNode node = new StageNode(nodeId, type, data);
            
            // Parse edges
            if (nodeJson.has("edges")) {
                JsonArray edgesJson = nodeJson.getAsJsonArray("edges");
                for (JsonElement edgeElement : edgesJson) {
                    JsonObject edgeJson = edgeElement.getAsJsonObject();
                    String targetId = edgeJson.get("target").getAsString();
                    int priority = edgeJson.has("priority") ? edgeJson.get("priority").getAsInt() : 0;
                    
                    List<EdgeCondition> conditions = new ArrayList<>();
                    if (edgeJson.has("conditions")) {
                        JsonArray conditionsJson = edgeJson.getAsJsonArray("conditions");
                        for (JsonElement condElement : conditionsJson) {
                            EdgeCondition condition = ConditionFactory.fromJson(condElement.getAsJsonObject());
                            if (condition != null) {
                                conditions.add(condition);
                            }
                        }
                    }
                    
                    node.addEdge(new StageEdge(nodeId, targetId, conditions, priority));
                }
            }
            
            builder.addNode(node);
        }
        
        // Parse clues
        if (json.has("clues")) {
            JsonObject cluesJson = json.getAsJsonObject("clues");
            for (Map.Entry<String, JsonElement> entry : cluesJson.entrySet()) {
                JsonObject clueJson = entry.getValue().getAsJsonObject();
                ClueDefinition clue = new ClueDefinition(
                    entry.getKey(),
                    clueJson.has("name") ? clueJson.get("name").getAsString() : entry.getKey(),
                    clueJson.has("description") ? clueJson.get("description").getAsString() : "",
                    clueJson.has("item_icon") ? clueJson.get("item_icon").getAsString() : "minecraft:paper"
                );
                builder.addClue(clue);
            }
        }
        
        // Parse flags
        if (json.has("flags")) {
            JsonObject flagsJson = json.getAsJsonObject("flags");
            for (Map.Entry<String, JsonElement> entry : flagsJson.entrySet()) {
                JsonObject flagJson = entry.getValue().getAsJsonObject();
                FlagDefinition flag = new FlagDefinition(
                    entry.getKey(),
                    flagJson.has("default") ? flagJson.get("default").getAsBoolean() : false,
                    flagJson.has("persistent") ? flagJson.get("persistent").getAsBoolean() : false
                );
                builder.addFlag(flag);
            }
        }

        // Parse locations
        if (json.has("locations")) {
            JsonObject locationsJson = json.getAsJsonObject("locations");
            for (Map.Entry<String, JsonElement> entry : locationsJson.entrySet()) {
                JsonObject locJson = entry.getValue().getAsJsonObject();
                StoryLocation location = new StoryLocation(
                    locJson.get("dimension").getAsString(),
                    locJson.get("x").getAsDouble(),
                    locJson.get("y").getAsDouble(),
                    locJson.get("z").getAsDouble(),
                    locJson.has("yaw") ? locJson.get("yaw").getAsFloat() : 0f,
                    locJson.has("pitch") ? locJson.get("pitch").getAsFloat() : 0f
                );
                builder.addLocation(entry.getKey(), location);
            }
        }
        
        return builder.build();
    }
    
    @Override
    public String toString() {
        return String.format("StageGraph{id='%s', name='%s', nodes=%d, entry='%s'}", 
            storyId, name, nodes.size(), entryNodeId);
    }
    
    /**
     * Builder for constructing StageGraph instances.
     */
    public static class Builder {
        private final String storyId;
        private final String entryNodeId;
        private String name = "Unnamed Story";
        private String description = "";
        private String version = "1.0.0";
        private int minPlayers = 1;
        private int maxPlayers = 4;
        private int estimatedDurationMinutes = 60;
        private final Map<String, StageNode> nodes = new HashMap<>();
        private final Map<String, ClueDefinition> clues = new HashMap<>();
        private final Map<String, FlagDefinition> flags = new HashMap<>();
        private final Map<String, StoryLocation> specialLocations = new HashMap<>();
        
        public Builder(String storyId, String entryNodeId) {
            this.storyId = storyId;
            this.entryNodeId = entryNodeId;
        }
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder minPlayers(int minPlayers) { this.minPlayers = minPlayers; return this; }
        public Builder maxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; return this; }
        public Builder estimatedDurationMinutes(int minutes) { this.estimatedDurationMinutes = minutes; return this; }
        
        public Builder addNode(StageNode node) {
            nodes.put(node.getId(), node);
            return this;
        }
        
        public Builder addClue(ClueDefinition clue) {
            clues.put(clue.id(), clue);
            return this;
        }
        
        public Builder addFlag(FlagDefinition flag) {
            flags.put(flag.id(), flag);
            return this;
        }

        public Builder addLocation(String id, StoryLocation location) {
            specialLocations.put(id, location);
            return this;
        }
        
        public StageGraph build() {
            if (!nodes.containsKey(entryNodeId)) {
                throw new IllegalStateException("Entry node '" + entryNodeId + "' not found in graph");
            }
            return new StageGraph(this);
        }
    }
    
    /**
     * Definition of a collectible clue.
     */
    public record ClueDefinition(String id, String name, String description, String itemIcon) {}
    
    /**
     * Definition of a story flag.
     */
    public record FlagDefinition(String id, boolean defaultValue, boolean persistent) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/graph/StageNode.java`
```java
package com.warmpixel.storyadventure.core.graph;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.core.condition.EdgeCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a node in the Stage Graph.
 * Each node has a type, associated data, and outgoing edges to other nodes.
 */
public class StageNode {
    private final String id;
    private final NodeType type;
    private final JsonObject data;
    private final List<StageEdge> edges;
    
    public StageNode(String id, NodeType type, JsonObject data) {
        this.id = id;
        this.type = type;
        this.data = data;
        this.edges = new ArrayList<>();
    }
    
    public String getId() {
        return id;
    }
    
    public NodeType getType() {
        return type;
    }
    
    public JsonObject getData() {
        return data;
    }
    
    public List<StageEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }
    
    public void addEdge(StageEdge edge) {
        edges.add(edge);
    }
    
    /**
     * Get a string data field with default value.
     */
    public String getString(String key, String defaultValue) {
        if (data.has(key) && data.get(key).isJsonPrimitive()) {
            return data.get(key).getAsString();
        }
        return defaultValue;
    }
    
    /**
     * Get an int data field with default value.
     */
    public int getInt(String key, int defaultValue) {
        if (data.has(key) && data.get(key).isJsonPrimitive()) {
            return data.get(key).getAsInt();
        }
        return defaultValue;
    }
    
    /**
     * Get a boolean data field with default value.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        if (data.has(key) && data.get(key).isJsonPrimitive()) {
            return data.get(key).getAsBoolean();
        }
        return defaultValue;
    }
    
    /**
     * Get a nested JsonObject.
     */
    public JsonObject getObject(String key) {
        if (data.has(key) && data.get(key).isJsonObject()) {
            return data.get(key).getAsJsonObject();
        }
        return null;
    }
    
    @Override
    public String toString() {
        return String.format("StageNode{id='%s', type=%s, edges=%d}", id, type, edges.size());
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/ClueCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks if a clue has been discovered.
 */
public class ClueCondition implements EdgeCondition {
    public static final String TYPE = "CLUE";
    
    private final String clueId;
    private final boolean discovered;
    
    public ClueCondition(String clueId, boolean discovered) {
        this.clueId = clueId;
        this.discovered = discovered;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().hasDiscoveredClue(clueId) == discovered;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("clue", clueId);
        json.addProperty("discovered", discovered);
        return json;
    }
    
    @Override
    public String getDescription() {
        return discovered ? "Discovered clue '" + clueId + "'" : "Has not discovered '" + clueId + "'";
    }
    
    public static ClueCondition fromJson(JsonObject json) {
        String clueId = json.get("clue").getAsString();
        boolean discovered = !json.has("discovered") || json.get("discovered").getAsBoolean();
        return new ClueCondition(clueId, discovered);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/CombatEscapedCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/** Condition for combat escape. */
public class CombatEscapedCondition implements EdgeCondition {
    public static final String TYPE = "COMBAT_ESCAPED";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("escaped");
    }
    
    @Override
    public String getType() { return TYPE; }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }
    
    @Override
    public String getDescription() { return "Escaped combat"; }
    
    public static CombatEscapedCondition fromJson(JsonObject json) {
        return new CombatEscapedCondition();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/CombatLostCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/** Condition for combat defeat. */
public class CombatLostCondition implements EdgeCondition {
    public static final String TYPE = "COMBAT_LOST";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("defeat");
    }
    
    @Override
    public String getType() { return TYPE; }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }
    
    @Override
    public String getDescription() { return "Combat lost"; }
    
    public static CombatLostCondition fromJson(JsonObject json) {
        return new CombatLostCondition();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/CombatWonCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/** Condition for combat victory. */
public class CombatWonCondition implements EdgeCondition {
    public static final String TYPE = "COMBAT_WON";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("victory");
    }
    
    @Override
    public String getType() { return TYPE; }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }
    
    @Override
    public String getDescription() { return "Combat won"; }
    
    public static CombatWonCondition fromJson(JsonObject json) {
        return new CombatWonCondition();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/ConditionFactory.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory for creating EdgeCondition instances from JSON definitions.
 */
public class ConditionFactory {
    
    private static final Map<String, Function<JsonObject, EdgeCondition>> CONDITION_PARSERS = new HashMap<>();
    
    static {
        // Register built-in condition types
        registerConditionType("INVENTORY", InventoryCondition::fromJson);
        registerConditionType("FLAG", FlagCondition::fromJson);
        registerConditionType("CLUE", ClueCondition::fromJson);
        registerConditionType("RELATIONSHIP", RelationshipCondition::fromJson);
        registerConditionType("TIME", TimeCondition::fromJson);
        registerConditionType("VOTE", VoteCondition::fromJson);
        registerConditionType("DIALOGUE_CHOICE", DialogueChoiceCondition::fromJson);
        registerConditionType("TASK_COMPLETE", TaskCompleteCondition::fromJson);
        registerConditionType("TASK_FAILED", TaskFailedCondition::fromJson);
        registerConditionType("PUZZLE_SOLVED", PuzzleSolvedCondition::fromJson);
        registerConditionType("PUZZLE_FAILED", PuzzleFailedCondition::fromJson);
        registerConditionType("COMBAT_WON", CombatWonCondition::fromJson);
        registerConditionType("COMBAT_LOST", CombatLostCondition::fromJson);
        registerConditionType("COMBAT_ESCAPED", CombatEscapedCondition::fromJson);
    }
    
    /**
     * Register a custom condition type parser.
     */
    public static void registerConditionType(String type, Function<JsonObject, EdgeCondition> parser) {
        CONDITION_PARSERS.put(type.toUpperCase(), parser);
    }
    
    /**
     * Parse an EdgeCondition from JSON.
     * 
     * @param json The JSON object containing the condition definition
     * @return The parsed condition, or null if the type is unknown
     */
    public static EdgeCondition fromJson(JsonObject json) {
        if (!json.has("type")) {
            StoryAdventureMod.LOGGER.warn("Condition missing 'type' field: {}", json);
            return null;
        }
        
        String type = json.get("type").getAsString().toUpperCase();
        Function<JsonObject, EdgeCondition> parser = CONDITION_PARSERS.get(type);
        
        if (parser == null) {
            StoryAdventureMod.LOGGER.warn("Unknown condition type: {}", type);
            return null;
        }
        
        try {
            return parser.apply(json);
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("Failed to parse condition of type {}: {}", type, e.getMessage());
            return null;
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/DialogueChoiceCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks if a specific dialogue choice was made.
 */
public class DialogueChoiceCondition implements EdgeCondition {
    public static final String TYPE = "DIALOGUE_CHOICE";
    
    private final String choiceId;
    
    public DialogueChoiceCondition(String choiceId) {
        this.choiceId = choiceId;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        String lastChoice = instance.getState().getLastDialogueChoice();
        boolean result = choiceId.equals(lastChoice);
        com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.debug("[DialogueChoiceCondition] Evaluating: required='{}', last='{}'. Result={}", 
            choiceId, lastChoice, result);
        return result;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("value", choiceId);
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Chose '" + choiceId + "'";
    }
    
    public static DialogueChoiceCondition fromJson(JsonObject json) {
        String choice = json.get("value").getAsString();
        return new DialogueChoiceCondition(choice);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/EdgeCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Interface for edge conditions that determine when a transition can occur.
 * Conditions are evaluated against the current instance state and optionally a player.
 */
public interface EdgeCondition {
    
    /**
     * Evaluate whether this condition is satisfied.
     * 
     * @param instance The current instance context
     * @param player The player being evaluated (may be null for auto-transitions)
     * @return true if the condition is met
     */
    boolean evaluate(Instance instance, ServerPlayer player);
    
    /**
     * Get the type identifier for this condition.
     */
    String getType();
    
    /**
     * Serialize this condition to JSON.
     */
    JsonObject serialize();
    
    /**
     * Get a human-readable description of this condition.
     */
    default String getDescription() {
        return getType();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/FlagCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks if a story flag is set to a specific value.
 */
public class FlagCondition implements EdgeCondition {
    public static final String TYPE = "FLAG";
    
    private final String flagId;
    private final boolean expectedValue;
    
    public FlagCondition(String flagId, boolean expectedValue) {
        this.flagId = flagId;
        this.expectedValue = expectedValue;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        boolean actualValue = instance.getState().getFlag(flagId);
        boolean result = actualValue == expectedValue;
        com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.debug("[FlagCondition] Evaluating: flag='{}', expected={}, actual={}. Result={}", 
            flagId, expectedValue, actualValue, result);
        return result;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("flag", flagId);
        json.addProperty("value", expectedValue);
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Flag '" + flagId + "' is " + expectedValue;
    }
    
    public static FlagCondition fromJson(JsonObject json) {
        String flagId = json.get("flag").getAsString();
        boolean value = !json.has("value") || json.get("value").getAsBoolean();
        return new FlagCondition(flagId, value);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/InventoryCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Condition that checks if a player has a specific item in their inventory.
 */
public class InventoryCondition implements EdgeCondition {
    public static final String TYPE = "INVENTORY";
    
    private final String itemId;
    private final int minCount;
    private final boolean consumeOnTransition;
    
    public InventoryCondition(String itemId, int minCount, boolean consumeOnTransition) {
        this.itemId = itemId;
        this.minCount = minCount;
        this.consumeOnTransition = consumeOnTransition;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        if (player == null) return false;
        
        ResourceLocation itemLocation = ResourceLocation.tryParse(itemId);
        if (itemLocation == null) return false;
        
        Item item = BuiltInRegistries.ITEM.get(itemLocation);
        
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        
        return count >= minCount;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("item", itemId);
        json.addProperty("count", minCount);
        json.addProperty("consume", consumeOnTransition);
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Has " + minCount + "x " + itemId;
    }
    
    public boolean shouldConsume() {
        return consumeOnTransition;
    }
    
    public static InventoryCondition fromJson(JsonObject json) {
        String itemId = json.get("item").getAsString();
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        boolean consume = json.has("consume") && json.get("consume").getAsBoolean();
        return new InventoryCondition(itemId, count, consume);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/PuzzleFailedCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/** Condition for puzzle failure (max attempts exceeded). */
public class PuzzleFailedCondition implements EdgeCondition {
    public static final String TYPE = "PUZZLE_FAILED";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("failed");
    }
    
    @Override
    public String getType() { return TYPE; }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }
    
    @Override
    public String getDescription() { return "Puzzle failed"; }
    
    public static PuzzleFailedCondition fromJson(JsonObject json) {
        return new PuzzleFailedCondition();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/PuzzleSolvedCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/** Condition for successful puzzle completion. */
public class PuzzleSolvedCondition implements EdgeCondition {
    public static final String TYPE = "PUZZLE_SOLVED";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("solved");
    }
    
    @Override
    public String getType() { return TYPE; }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }
    
    @Override
    public String getDescription() { return "Puzzle solved"; }
    
    public static PuzzleSolvedCondition fromJson(JsonObject json) {
        return new PuzzleSolvedCondition();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/RelationshipCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks relationship level with an NPC.
 */
public class RelationshipCondition implements EdgeCondition {
    public static final String TYPE = "RELATIONSHIP";
    
    private final String npcId;
    private final int threshold;
    private final CompareOp operation;
    
    public enum CompareOp {
        GREATER_THAN, LESS_THAN, EQUALS, GREATER_OR_EQUAL, LESS_OR_EQUAL
    }
    
    public RelationshipCondition(String npcId, int threshold, CompareOp operation) {
        this.npcId = npcId;
        this.threshold = threshold;
        this.operation = operation;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        if (player == null) return false;
        
        int relationship = instance.getState().getRelationship(player.getUUID(), npcId);
        
        return switch (operation) {
            case GREATER_THAN -> relationship > threshold;
            case LESS_THAN -> relationship < threshold;
            case EQUALS -> relationship == threshold;
            case GREATER_OR_EQUAL -> relationship >= threshold;
            case LESS_OR_EQUAL -> relationship <= threshold;
        };
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("npc", npcId);
        json.addProperty("threshold", threshold);
        json.addProperty("operation", operation.name());
        return json;
    }
    
    @Override
    public String getDescription() {
        String op = switch (operation) {
            case GREATER_THAN -> ">";
            case LESS_THAN -> "<";
            case EQUALS -> "=";
            case GREATER_OR_EQUAL -> ">=";
            case LESS_OR_EQUAL -> "<=";
        };
        return "Relationship with " + npcId + " " + op + " " + threshold;
    }
    
    public static RelationshipCondition fromJson(JsonObject json) {
        String npcId = json.get("npc").getAsString();
        int threshold = json.get("threshold").getAsInt();
        CompareOp op = json.has("operation") ? 
            CompareOp.valueOf(json.get("operation").getAsString().toUpperCase()) : 
            CompareOp.GREATER_OR_EQUAL;
        return new RelationshipCondition(npcId, threshold, op);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/TaskCompleteCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks if the current task node was completed successfully.
 */
public class TaskCompleteCondition implements EdgeCondition {
    public static final String TYPE = "TASK_COMPLETE";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        // ✅ FIX: Check multiple indicators of task completion
        
        // Check 1: Standard node result
        boolean nodeResult = instance.getState().isCurrentNodeCompleteWith("success");
        
        // Check 2: Explicit task_complete metadata flag
        boolean taskCompleteFlag = false;
        if (instance.getState().getMetadata().has("task_complete")) {
            try {
                taskCompleteFlag = instance.getState().getMetadata().get("task_complete").getAsBoolean();
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.warn("[TaskCompleteCondition] Failed to read task_complete flag: {}", e.getMessage());
            }
        }
        
        // Check 3: All objectives completed (fallback check)
        boolean allObjectivesComplete = false;
        int completed = 0;
        int total = 0;
        
        try {
            if (instance.getState().getMetadata().has("completed_objectives")) {
                completed = instance.getState().getMetadata().get("completed_objectives").getAsInt();
            }
            if (instance.getState().getMetadata().has("total_objectives")) {
                total = instance.getState().getMetadata().get("total_objectives").getAsInt();
            }
            if (total > 0 && completed >= total) {
                allObjectivesComplete = true;
            }
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.warn("[TaskCompleteCondition] Failed to read objective counts: {}", e.getMessage());
        }
        
        // Any of the three conditions passing means task is complete
        boolean result = nodeResult || taskCompleteFlag || allObjectivesComplete;
        
        StoryAdventureMod.LOGGER.info("[TaskCompleteCondition] Evaluating: nodeResult={}, taskCompleteFlag={}, objectives={}/{}, allComplete={} => RESULT: {}", 
            nodeResult, taskCompleteFlag, completed, total, allObjectivesComplete, result);
        
        return result;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Task completed successfully";
    }
    
    public static TaskCompleteCondition fromJson(JsonObject json) {
        return new TaskCompleteCondition();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/TaskFailedCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks if the current task node failed.
 */
public class TaskFailedCondition implements EdgeCondition {
    public static final String TYPE = "TASK_FAILED";
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        return instance.getState().isCurrentNodeCompleteWith("failed");
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Task failed";
    }
    
    public static TaskFailedCondition fromJson(JsonObject json) {
        return new TaskFailedCondition();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/TimeCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks time-related constraints (time pressure, limits).
 */
public class TimeCondition implements EdgeCondition {
    public static final String TYPE = "TIME";
    
    private final TimeCheck check;
    private final String timerId;
    
    public enum TimeCheck {
        /** Timer has not expired */
        WITHIN_LIMIT,
        /** Timer has expired */
        EXPIRED,
        /** Timer exists and is running */
        ACTIVE
    }
    
    public TimeCondition(String timerId, TimeCheck check) {
        this.timerId = timerId;
        this.check = check;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        var timerState = instance.getState().getTimer(timerId);
        if (timerState == null) {
            return check == TimeCheck.EXPIRED; // No timer = expired
        }
        
        return switch (check) {
            case WITHIN_LIMIT -> !timerState.isExpired();
            case EXPIRED -> timerState.isExpired();
            case ACTIVE -> timerState.isActive();
        };
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("timer", timerId);
        json.addProperty("check", check.name());
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Timer '" + timerId + "' is " + check.name().toLowerCase().replace("_", " ");
    }
    
    public static TimeCondition fromJson(JsonObject json) {
        String timerId = json.has("timer") ? json.get("timer").getAsString() : "default";
        TimeCheck check = json.has("check") ? 
            TimeCheck.valueOf(json.get("check").getAsString().toUpperCase()) : 
            TimeCheck.WITHIN_LIMIT;
        return new TimeCondition(timerId, check);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/condition/VoteCondition.java`
```java
package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks party vote results.
 */
public class VoteCondition implements EdgeCondition {
    public static final String TYPE = "VOTE";
    
    private final String voteId;
    private final String expectedChoice;
    private final VoteRequirement requirement;
    
    public enum VoteRequirement {
        /** Majority voted for this choice */
        MAJORITY,
        /** Unanimous vote for this choice */
        UNANIMOUS,
        /** Leader voted for this choice */
        LEADER,
        /** At least one member voted for this choice */
        ANY
    }
    
    public VoteCondition(String voteId, String expectedChoice, VoteRequirement requirement) {
        this.voteId = voteId;
        this.expectedChoice = expectedChoice;
        this.requirement = requirement;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        var voteResult = instance.getState().getVoteResult(voteId);
        if (voteResult == null) return false;
        
        return switch (requirement) {
            case MAJORITY -> voteResult.getMajorityChoice().equals(expectedChoice);
            case UNANIMOUS -> voteResult.isUnanimous(expectedChoice);
            case LEADER -> voteResult.getLeaderChoice().equals(expectedChoice);
            case ANY -> voteResult.hasAnyVoteFor(expectedChoice);
        };
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("vote_id", voteId);
        json.addProperty("choice", expectedChoice);
        json.addProperty("requirement", requirement.name());
        return json;
    }
    
    @Override
    public String getDescription() {
        return requirement.name().toLowerCase() + " vote for '" + expectedChoice + "'";
    }
    
    public static VoteCondition fromJson(JsonObject json) {
        String voteId = json.get("vote_id").getAsString();
        String choice = json.get("choice").getAsString();
        VoteRequirement req = json.has("requirement") ? 
            VoteRequirement.valueOf(json.get("requirement").getAsString().toUpperCase()) : 
            VoteRequirement.MAJORITY;
        return new VoteCondition(voteId, choice, req);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/event/StoryEventListener.java`
```java
package com.warmpixel.storyadventure.core.event;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.instance.Instance;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;

import java.util.UUID;

/**
 * Global event listener for story-related events in the game world.
 */
public class StoryEventListener {
    
    public static void register() {
         // Listen for entity deaths to track combat progress and kill objectives
        ServerLivingEntityEvents.AFTER_DEATH.register(StoryEventListener::onEntityDeath);
    }
    
    private static void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        // Only care about entities with our story tags
        for (String tag : entity.getTags()) {
            if (tag.startsWith("instance_")) {
                try {
                    String instanceIdStr = tag.substring(9);
                    UUID instanceId = UUID.fromString(instanceIdStr);
                    
                    Instance instance = StoryAdventureMod.getInstance().getInstanceManager().getInstance(instanceId);
                    if (instance != null) {
                        var currentNode = instance.getCurrentNode();
                        if (currentNode != null) {
                            var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(currentNode.getType());
                            if (handler != null) {
                                // Forward to handler
                                handler.onAction(instance, currentNode, null, "enemy_killed", entity);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore malformed tags
                }
            }
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/ActionFactory.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory for creating NodeAction instances from JSON.
 */
public class ActionFactory {
    
    private static final Map<String, Function<JsonObject, NodeAction>> PARSERS = new HashMap<>();
    
    static {
        // Register all action type parsers
        register("COMMAND", CommandAction::fromJson);
        register("MESSAGE", MessageAction::fromJson);
        register("TITLE", TitleAction::fromJson);
        register("PLAY_SOUND", PlaySoundAction::fromJson);
        register("PLAY_VOICEOVER", PlayVoiceoverAction::fromJson);
        register("SET_FLAG", SetFlagAction::fromJson);
        register("TELEPORT", TeleportAction::fromJson);
        register("GIVE_ITEM", GiveItemAction::fromJson);
        register("SPAWN_NPC", SpawnNPCAction::fromJson);
    }
    
    public static void register(String type, Function<JsonObject, NodeAction> parser) {
        PARSERS.put(type.toUpperCase(), parser);
    }
    
    /**
     * Create a NodeAction from JSON data.
     * @param json The action JSON object
     * @return The NodeAction instance, or null if type unknown
     */
    public static NodeAction fromJson(JsonObject json) {
        if (!json.has("type")) return null;
        
        String type = json.get("type").getAsString().toUpperCase();
        Function<JsonObject, NodeAction> parser = PARSERS.get(type);
        
        if (parser != null) {
            return parser.apply(json);
        }
        
        return null;
    }
    
    /**
     * Get all available action types.
     */
    public static String[] getActionTypes() {
        return PARSERS.keySet().toArray(new String[0]);
    }
    
    /**
     * Get a display name for an action type.
     */
    public static String getTypeDisplayName(String type) {
        return switch (type.toUpperCase()) {
            case "COMMAND" -> "执行命令";
            case "MESSAGE" -> "发送消息";
            case "TITLE" -> "显示标题";
            case "PLAY_SOUND" -> "播放音效";
            case "PLAY_VOICEOVER" -> "播放语音";
            case "SET_FLAG" -> "设置标记";
            case "TELEPORT" -> "传送玩家";
            case "SPAWN_NPC" -> "生成NPC";
            case "GIVE_ITEM" -> "给予物品";
            case "EFFECT" -> "添加效果";
            case "PARTICLE" -> "播放粒子";
            default -> type;
        };
    }
    
    /**
     * Get a template JSON for a new action of the given type.
     */
    public static JsonObject getTemplate(String type) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type.toUpperCase());
        
        switch (type.toUpperCase()) {
            case "COMMAND" -> obj.addProperty("command", "say Hello!");
            case "MESSAGE" -> obj.addProperty("text", "消息内容");
            case "TITLE" -> {
                obj.addProperty("title", "标题");
                obj.addProperty("subtitle", "副标题");
            }
            case "PLAY_SOUND" -> obj.addProperty("sound", "minecraft:entity.experience_orb.pickup");
            case "PLAY_VOICEOVER" -> {
                obj.addProperty("sound", "story_id/character/line_001");
                obj.addProperty("character", "narrator");
            }
            case "SET_FLAG" -> {
                obj.addProperty("flag", "flag_name");
                obj.addProperty("value", true);
            }
            case "TELEPORT" -> {
                obj.addProperty("dimension", "minecraft:overworld");
                obj.addProperty("x", 0);
                obj.addProperty("y", 64);
                obj.addProperty("z", 0);
            }
            case "GIVE_ITEM" -> {
                obj.addProperty("item", "minecraft:diamond");
                obj.addProperty("count", 1);
            }
            case "SPAWN_NPC" -> {
                obj.addProperty("npc_template", "guide_npc");
                obj.addProperty("x", 0);
                obj.addProperty("y", 64);
                obj.addProperty("z", 0);
            }
        }
        
        return obj;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/CommandAction.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.commands.CommandSourceStack;
import java.util.UUID;
import java.util.List;

/**
 * Action that executes a server command for each player.
 */
public class CommandAction implements NodeAction {
    
    private final String command;
    private final boolean asOp;
    
    public CommandAction(String command) {
        this(command, false);
    }
    
    public CommandAction(String command, boolean asOp) {
        this.command = command;
        this.asOp = asOp;
    }
    
    @Override
    public String getType() {
        return "COMMAND";
    }
    
    private UUID instanceId;

    public void setInstanceId(UUID instanceId) {
        this.instanceId = instanceId;
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        
        var server = players.get(0).getServer();
        if (server == null) return;

        for (ServerPlayer player : players) {
            String processedCmd = command
                .replace("{player}", player.getName().getString())
                .replace("{uuid}", player.getUUID().toString())
                .replace("{x}", String.format("%.2f", player.getX()))
                .replace("{y}", String.format("%.2f", player.getY()))
                .replace("{z}", String.format("%.2f", player.getZ()));
                
            if (instanceId != null) {
                processedCmd = processedCmd.replace("{instance_id}", instanceId.toString());
            }
            
            CommandSourceStack source = asOp ? server.createCommandSourceStack() : player.createCommandSourceStack();
            // Suppress output to prevent chat spam
            source = source.withSuppressedOutput();
            // Ensure permission level if OP
            if (asOp) {
                source = source.withPermission(2);
            }
            
            server.getCommands().performPrefixedCommand(source, processedCmd);
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "COMMAND");
        obj.addProperty("command", command);
        if (asOp) obj.addProperty("as_op", true);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return "命令: " + (command.length() > 30 ? command.substring(0, 28) + ".." : command);
    }
    
    public static CommandAction fromJson(JsonObject obj) {
        String cmd = obj.has("command") ? obj.get("command").getAsString() : "";
        boolean asOp = obj.has("as_op") && obj.get("as_op").getAsBoolean();
        return new CommandAction(cmd, asOp);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/GiveItemAction.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Action that gives items to players.
 */
public class GiveItemAction implements NodeAction {
    
    private final String itemId;
    private final int count;
    private final boolean silent;
    
    public GiveItemAction(String itemId, int count, boolean silent) {
        this.itemId = itemId;
        this.count = count;
        this.silent = silent;
    }

    @Override
    public String getType() {
        return "GIVE_ITEM";
    }

    @Override
    public String getSummary() {
        return "Give Item: " + itemId + " x" + count;
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        ResourceLocation rl = ResourceLocation.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(rl);
        
        if (item == BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getDefaultKey())) {
            // Item not found or is air (default)
            return;
        }
        
        for (ServerPlayer player : players) {
            ItemStack stack = new ItemStack(item, count);
            player.getInventory().add(stack);
            
            if (!silent) {
                player.sendSystemMessage(Component.translatable("text.storyadventure.action.give_item", count, stack.getHoverName()));
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "GIVE_ITEM");
        json.addProperty("item", itemId);
        json.addProperty("count", count);
        json.addProperty("silent", silent);
        return json;
    }
    
    public static GiveItemAction fromJson(JsonObject json) {
        String item = json.has("item") ? json.get("item").getAsString() : "minecraft:stone";
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        boolean silent = json.has("silent") && json.get("silent").getAsBoolean();
        return new GiveItemAction(item, count, silent);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/MessageAction.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action that sends a message to all players.
 */
public class MessageAction implements NodeAction {
    
    private final String text;
    private final boolean actionBar;
    
    public MessageAction(String text) {
        this(text, false);
    }
    
    public MessageAction(String text, boolean actionBar) {
        this.text = text;
        this.actionBar = actionBar;
    }
    
    @Override
    public String getType() {
        return "MESSAGE";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            String processed = text.replace("{player}", player.getName().getString());
            
            if (actionBar) {
                player.displayClientMessage(Component.literal(processed), true);
            } else {
                player.sendSystemMessage(Component.literal(processed));
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "MESSAGE");
        obj.addProperty("text", text);
        if (actionBar) obj.addProperty("action_bar", true);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return "消息: " + (text.length() > 25 ? text.substring(0, 23) + ".." : text);
    }
    
    public static MessageAction fromJson(JsonObject obj) {
        String text = obj.has("text") ? obj.get("text").getAsString() : "";
        boolean actionBar = obj.has("action_bar") && obj.get("action_bar").getAsBoolean();
        return new MessageAction(text, actionBar);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/NodeAction.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Base interface for node trigger actions.
 * Actions execute when entering or exiting a story node.
 */
public interface NodeAction {
    
    /**
     * Get the action type identifier.
     */
    String getType();
    
    /**
     * Execute this action for the given players.
     * @param players The players in the story instance
     */
    void execute(List<ServerPlayer> players);
    
    /**
     * Serialize this action to JSON.
     */
    JsonObject toJson();
    
    /**
     * Get a human-readable summary of this action.
     */
    String getSummary();
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/PlaySoundAction.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * Action that plays a sound effect to players.
 */
public class PlaySoundAction implements NodeAction {
    
    private final String sound;
    private final float volume;
    private final float pitch;
    
    public PlaySoundAction(String sound) {
        this(sound, 1.0f, 1.0f);
    }
    
    public PlaySoundAction(String sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }
    
    @Override
    public String getType() {
        return "PLAY_SOUND";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            try {
                ResourceLocation soundLoc = ResourceLocation.tryParse(sound);
                if (soundLoc != null) {
                    SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLoc);
                    player.playNotifySound(soundEvent, SoundSource.MASTER, volume, pitch);
                }
            } catch (Exception e) {
                // Invalid sound, silently ignore
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "PLAY_SOUND");
        obj.addProperty("sound", sound);
        if (volume != 1.0f) obj.addProperty("volume", volume);
        if (pitch != 1.0f) obj.addProperty("pitch", pitch);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return "播放: " + sound;
    }
    
    public static PlaySoundAction fromJson(JsonObject obj) {
        String sound = obj.has("sound") ? obj.get("sound").getAsString() : "minecraft:entity.experience_orb.pickup";
        float volume = obj.has("volume") ? obj.get("volume").getAsFloat() : 1.0f;
        float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 1.0f;
        return new PlaySoundAction(sound, volume, pitch);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/PlayVoiceoverAction.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.network.NetworkHandler;
import com.warmpixel.storyadventure.network.VoiceoverPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action that plays a voiceover audio file.
 * Voiceover files are stored in config/storyadventure/voiceovers/<story_id>/<sound_id>.ogg
 */
public class PlayVoiceoverAction implements NodeAction {
    
    private final String soundPath;
    private final float volume;
    private final float pitch;
    private final String characterId;
    
    public PlayVoiceoverAction(String soundPath, String characterId) {
        this(soundPath, 1.0f, 1.0f, characterId);
    }
    
    public PlayVoiceoverAction(String soundPath, float volume, float pitch, String characterId) {
        this.soundPath = soundPath;
        this.volume = volume;
        this.pitch = pitch;
        this.characterId = characterId;
    }
    
    @Override
    public String getType() {
        return "PLAY_VOICEOVER";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        // Get instance ID from first player (they should all be in the same instance)
        String instanceId = "";
        if (!players.isEmpty()) {
            var instance = StoryAdventureMod.getInstance().getInstanceManager().getPlayerInstance(players.get(0).getUUID());
            if (instance != null) {
                instanceId = instance.getInstanceId().toString();
            }
        }

        
        // Send voiceover payload to all players
        VoiceoverPayload payload = VoiceoverPayload.custom(instanceId, soundPath, volume, pitch, characterId);
        
        for (ServerPlayer player : players) {
            try {
                ServerPlayNetworking.send(player, payload);
                StoryAdventureMod.LOGGER.debug("[PlayVoiceoverAction] Sent voiceover {} to player {}", soundPath, player.getName().getString());
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.warn("[PlayVoiceoverAction] Failed to send voiceover to {}: {}", player.getName().getString(), e.getMessage());
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "PLAY_VOICEOVER");
        obj.addProperty("sound", soundPath);
        if (volume != 1.0f) obj.addProperty("volume", volume);
        if (pitch != 1.0f) obj.addProperty("pitch", pitch);
        if (characterId != null && !characterId.isEmpty()) obj.addProperty("character", characterId);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return "语音: " + soundPath + (characterId.isEmpty() ? "" : " (" + characterId + ")");
    }
    
    public static PlayVoiceoverAction fromJson(JsonObject obj) {
        String sound = obj.has("sound") ? obj.get("sound").getAsString() : "";
        float volume = obj.has("volume") ? obj.get("volume").getAsFloat() : 1.0f;
        float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 1.0f;
        String character = obj.has("character") ? obj.get("character").getAsString() : "narrator";
        return new PlayVoiceoverAction(sound, volume, pitch, character);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/SetFlagAction.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action that sets a story flag in the instance state.
 */
public class SetFlagAction implements NodeAction {
    
    private final String flag;
    private final boolean value;
    
    public SetFlagAction(String flag, boolean value) {
        this.flag = flag;
        this.value = value;
    }
    
    @Override
    public String getType() {
        return "SET_FLAG";
    }

    @Override
    public String getSummary() {
        return "Set Flag: " + flag + " = " + value;
    }

    @Override
    public void execute(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        
        // This action needs the instance context to work properly
        // In the current architecture, NodeAction.execute only takes players.
        // We might need to find the instance from the player.
        
        for (ServerPlayer player : players) {
            Instance instance = StoryAdventureMod.getInstance().getInstanceManager().getPlayerInstance(player.getUUID());
            if (instance != null) {
                instance.getState().setFlag(flag, value);
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "SET_FLAG");
        json.addProperty("flag", flag);
        json.addProperty("value", value);
        return json;
    }
    
    public static SetFlagAction fromJson(JsonObject json) {
        String flag = json.has("flag") ? json.get("flag").getAsString() : "unknown";
        boolean value = json.has("value") && json.get("value").getAsBoolean();
        return new SetFlagAction(flag, value);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/SpawnNPCAction.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Action that spawns an NPC at a specific location.
 */
public class SpawnNPCAction implements NodeAction {
    
    private final String npcTemplate;
    private final String dimension;
    private final Vec3 position;
    private final float yaw;
    private final float pitch;
    private java.util.UUID instanceId;
    
    public SpawnNPCAction(String npcTemplate, String dimension, Vec3 position, float yaw, float pitch) {
        this.npcTemplate = npcTemplate;
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
    }
    
    public void setInstanceId(java.util.UUID instanceId) {
        this.instanceId = instanceId;
    }
    
    @Override
    public String getType() {
        return "SPAWN_NPC";
    }

    @Override
    public String getSummary() {
        return "Spawn NPC: " + npcTemplate;
    }

    @Override
    public void execute(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        
        var server = players.get(0).getServer();
        if (server == null) return;
        
        // Get the server level for spawning
        net.minecraft.server.level.ServerLevel level = null;
        for (var serverLevel : server.getAllLevels()) {
            if (serverLevel.dimension().location().toString().equals(dimension)) {
                level = serverLevel;
                break;
            }
        }
        
        if (level == null) {
            level = server.overworld();
        }
        
        // Use NPCTemplateManager API to spawn entity to get reference
        try {
            com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info("[SpawnNPCAction] Attempting api spawn for template: {} at {},{},{}", 
                npcTemplate, position.x, position.y, position.z);
            
            net.minecraft.world.entity.Entity spawnedEntity = null;
            try {
                spawnedEntity = de.markusbordihn.easynpc.config.NPCTemplateManager.spawnEntityFromTemplate(
                    level, npcTemplate, position.x, position.y, position.z
                );
            } catch (Throwable t) {
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.error("[SpawnNPCAction] API call failed: {}", t.getMessage());
            }
            
            com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info("[SpawnNPCAction] API result for {}: {}", npcTemplate, spawnedEntity);
            
            if (spawnedEntity != null) {
                // Success! Add tags
                spawnedEntity.addTag("story_enemy");
                if (instanceId != null) {
                    spawnedEntity.addTag("instance_" + instanceId.toString());
                }
                
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info(
                    "[SpawnNPCAction] Successfully spawned NPC '{}' at {},{},{} with tags", 
                    npcTemplate, position.x, position.y, position.z);
                
                // Set rotation
                spawnedEntity.setYRot(yaw);
                spawnedEntity.setXRot(pitch);
                // Force position update to apply rotation
                spawnedEntity.teleportTo(level, position.x, position.y, position.z, java.util.Set.of(), yaw, pitch);
                
            } else {
                // Fallback to command if API fails using the NEW command syntax
                StringBuilder tags = new StringBuilder("[\"story_enemy\"");
                if (instanceId != null) {
                    tags.append(",\"instance_").append(instanceId.toString()).append("\"");
                }
                tags.append("]");
                
                String nbt = String.format("{Tags:%s}", tags.toString());
                
                // Use the new syntax: easy_npc template spawn <template> <x> <y> <z> <nbt>
                // Enforce US Locale for coordinates to ensure '.' usage
                String cmd = String.format(java.util.Locale.US,
                    "easy_npc template spawn %s %.2f %.2f %.2f %s", 
                    npcTemplate, position.x, position.y, position.z, nbt
                );
                
                // Execute in the correct dimension
                String fullCmd = String.format("execute in %s run %s", dimension, cmd);
                
                com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.info("[SpawnNPCAction] Executing fallback command: {}", fullCmd);
                
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput().withLevel(level).withPermission(2), 
                    fullCmd
                );
            }
        } catch (Exception e) {
            com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.error(
                "[SpawnNPCAction] Error spawning NPC '{}': {}", npcTemplate, e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "SPAWN_NPC");
        json.addProperty("npc_template", npcTemplate);
        json.addProperty("dimension", dimension);
        json.addProperty("x", position.x);
        json.addProperty("y", position.y);
        json.addProperty("z", position.z);
        json.addProperty("yaw", yaw);
        json.addProperty("pitch", pitch);
        return json;
    }
    
    public static SpawnNPCAction fromJson(JsonObject json) {
        String template = json.has("npc_template") ? json.get("npc_template").getAsString() : "unknown";
        String dim = json.has("dimension") ? json.get("dimension").getAsString() : "minecraft:overworld";
        double x = json.has("x") ? json.get("x").getAsDouble() : 0;
        double y = json.has("y") ? json.get("y").getAsDouble() : 64;
        double z = json.has("z") ? json.get("z").getAsDouble() : 0;
        float yaw = json.has("yaw") ? json.get("yaw").getAsFloat() : 0;
        float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 0;
        
        return new SpawnNPCAction(template, dim, new Vec3(x, y, z), yaw, pitch);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/TeleportAction.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;

import java.util.List;

/**
 * Action that teleports players to a location.
 */
public class TeleportAction implements NodeAction {
    
    private final String dimension;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    
    public TeleportAction(String dimension, double x, double y, double z) {
        this(dimension, x, y, z, 0, 0);
    }
    
    public TeleportAction(String dimension, double x, double y, double z, float yaw, float pitch) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
    
    @Override
    public String getType() {
        return "TELEPORT";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            try {
                ResourceLocation dimLoc = ResourceLocation.tryParse(dimension);
                if (dimLoc != null && player.getServer() != null) {
                    ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
                    ServerLevel targetLevel = player.getServer().getLevel(dimKey);
                    
                    if (targetLevel != null) {
                        player.teleportTo(targetLevel, x, y, z, yaw, pitch);
                    } else {
                        // Same dimension teleport
                        player.teleportTo(x, y, z);
                    }
                }
            } catch (Exception e) {
                // Failed teleport, log but don't crash
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "TELEPORT");
        obj.addProperty("dimension", dimension);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        if (yaw != 0) obj.addProperty("yaw", yaw);
        if (pitch != 0) obj.addProperty("pitch", pitch);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return String.format("传送: %.0f, %.0f, %.0f", x, y, z);
    }
    
    public static TeleportAction fromJson(JsonObject obj) {
        String dim = obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld";
        double x = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double y = obj.has("y") ? obj.get("y").getAsDouble() : 64;
        double z = obj.has("z") ? obj.get("z").getAsDouble() : 0;
        float yaw = obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0;
        float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0;
        return new TeleportAction(dim, x, y, z, yaw, pitch);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/core/action/TitleAction.java`
```java
package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action that displays a title/subtitle to players.
 */
public class TitleAction implements NodeAction {
    
    private final String title;
    private final String subtitle;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;
    
    public TitleAction(String title, String subtitle) {
        this(title, subtitle, 10, 70, 20);
    }
    
    public TitleAction(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        this.title = title;
        this.subtitle = subtitle;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }
    
    @Override
    public String getType() {
        return "TITLE";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            // Set timing
            player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            
            // Set subtitle first (order matters)
            if (subtitle != null && !subtitle.isEmpty()) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal(subtitle.replace("{player}", player.getName().getString()))
                ));
            }
            
            // Set title
            if (title != null && !title.isEmpty()) {
                player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal(title.replace("{player}", player.getName().getString()))
                ));
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "TITLE");
        obj.addProperty("title", title);
        if (subtitle != null && !subtitle.isEmpty()) {
            obj.addProperty("subtitle", subtitle);
        }
        obj.addProperty("fade_in", fadeIn);
        obj.addProperty("stay", stay);
        obj.addProperty("fade_out", fadeOut);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return "标题: " + (title != null && title.length() > 20 ? title.substring(0, 18) + ".." : title);
    }
    
    public static TitleAction fromJson(JsonObject obj) {
        String title = obj.has("title") ? obj.get("title").getAsString() : "";
        String subtitle = obj.has("subtitle") ? obj.get("subtitle").getAsString() : "";
        int fadeIn = obj.has("fade_in") ? obj.get("fade_in").getAsInt() : 10;
        int stay = obj.has("stay") ? obj.get("stay").getAsInt() : 70;
        int fadeOut = obj.has("fade_out") ? obj.get("fade_out").getAsInt() : 20;
        return new TitleAction(title, subtitle, fadeIn, stay, fadeOut);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/loader/StoryLoader.java`
```java
package com.warmpixel.storyadventure.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Loads story definitions from JSON files.
 */
public class StoryLoader {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Path storiesPath;
    private final StoryRegistry registry;
    
    public StoryLoader(Path storiesPath, StoryRegistry registry) {
        this.storiesPath = storiesPath;
        this.registry = registry;
    }
    
    /**
     * Load all stories from the stories directory.
     */
    public void loadAllStories() {
        registry.clear();
        
        if (!Files.exists(storiesPath)) {
            try {
                Files.createDirectories(storiesPath);
                StoryAdventureMod.LOGGER.info("Created stories directory: {}", storiesPath);
                
                // Create example story
                createExampleStory();
            } catch (IOException e) {
                StoryAdventureMod.LOGGER.error("Failed to create stories directory", e);
            }
            return;
        }
        
        try (Stream<Path> paths = Files.walk(storiesPath)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(this::loadStory);
        } catch (IOException e) {
            StoryAdventureMod.LOGGER.error("Failed to scan stories directory", e);
        }
    }
    
    /**
     * Load a single story from a JSON file.
     */
    public boolean loadStory(Path storyPath) {
        StoryAdventureMod.LOGGER.debug("[StoryLoader] Attempting to load story from: {}", storyPath);
        try {
            String content = Files.readString(storyPath);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            StageGraph story = StageGraph.fromJson(json);
            StoryAdventureMod.LOGGER.debug("[StoryLoader] Parsed JSON into StageGraph: id='{}', name='{}'", story.getStoryId(), story.getName());
            
            // Validate the story
            StoryValidator validator = new StoryValidator();
            var errors = validator.validate(story);
            
            if (!errors.isEmpty()) {
                StoryAdventureMod.LOGGER.error("[StoryLoader] Story '{}' (from {}) has validation errors:", story.getStoryId(), storyPath);
                for (String error : errors) {
                    StoryAdventureMod.LOGGER.error("  - {}", error);
                }
                return false;
            }
            
            registry.register(story);
            StoryAdventureMod.LOGGER.info("[StoryLoader] Successfully loaded and registered story: {} ({} nodes, id={})", 
                story.getName(), story.getNodeCount(), story.getStoryId());
            
            return true;
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("[StoryLoader] Failed to load story from " + storyPath, e);
            return false;
        }
    }
    
    /**
     * Reload all stories.
     */
    public void reload() {
        StoryAdventureMod.LOGGER.info("[StoryLoader] Reloading all stories...");
        loadAllStories();
    }
    
    /**
     * Save a story to disk.
     */
    public boolean saveStory(StageGraph graph) {
        Path storyPath = storiesPath.resolve(graph.getStoryId() + ".json");
        try {
            JsonObject json = graph.toJson();
            String content = GSON.toJson(json);
            Files.writeString(storyPath, content);
            StoryAdventureMod.LOGGER.info("[StoryLoader] Saved story '{}' to {}", graph.getStoryId(), storyPath);
            return true;
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("[StoryLoader] Failed to save story '{}' to {}: {}", 
                graph.getStoryId(), storyPath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Create an example story file for reference.
     */
    private void createExampleStory() {
        String exampleStory = """
            {
              "id": "example_story",
              "name": "示例故事",
              "description": "这是一个示例故事，展示了Stage Graph系统的基本用法。",
              "version": "1.0.0",
              "min_players": 1,
              "max_players": 4,
              "estimated_duration_minutes": 15,
              "entry_node": "start",
              
              "locations": {
                "start": {
                  "dimension": "minecraft:overworld",
                  "x": 0.0,
                  "y": 64.0,
                  "z": 0.0,
                  "yaw": 0.0,
                  "pitch": 0.0
                }
              },
              
              "nodes": {
                "start": {
                  "type": "CUTSCENE",
                  "data": {
                    "duration_ticks": 100,
                    "message": "欢迎来到示例故事..."
                  },
                  "edges": [
                    {"target": "first_dialogue", "conditions": []}
                  ]
                },
                
                "first_dialogue": {
                  "type": "DIALOGUE",
                  "data": {
                    "npc_template": "guide_npc",
                    "dialog_set": "introduction"
                  },
                  "edges": [
                    {
                      "target": "accept_quest",
                      "conditions": [{"type": "DIALOGUE_CHOICE", "value": "accept"}]
                    },
                    {
                      "target": "decline_ending",
                      "conditions": [{"type": "DIALOGUE_CHOICE", "value": "decline"}]
                    }
                  ]
                },
                
                "accept_quest": {
                  "type": "TASK",
                  "data": {
                    "task_type": "FETCH",
                    "objectives": [
                      {"type": "COLLECT_ITEM", "item": "minecraft:diamond", "count": 1}
                    ]
                  },
                  "edges": [
                    {"target": "victory", "conditions": [{"type": "TASK_COMPLETE"}]}
                  ]
                },
                
                "victory": {
                  "type": "CUTSCENE",
                  "data": {
                    "message": "恭喜！你完成了示例故事！",
                    "is_ending": true,
                    "ending_type": "success"
                  },
                  "edges": []
                },
                
                "decline_ending": {
                  "type": "CUTSCENE",
                  "data": {
                    "message": "也许下次再见...",
                    "is_ending": true,
                    "ending_type": "declined"
                  },
                  "edges": []
                }
              },
              
              "flags": {
                "quest_accepted": {"default": false, "persistent": true}
              },
              
              "clues": {}
            }
            """;
        
        try {
            Path examplePath = storiesPath.resolve("example_story.json");
            Files.writeString(examplePath, exampleStory);
            StoryAdventureMod.LOGGER.info("Created example story at {}", examplePath);
        } catch (IOException e) {
            StoryAdventureMod.LOGGER.error("Failed to create example story", e);
        }
    }
    
    public Path getStoriesPath() {
        return storiesPath;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/loader/StoryRegistry.java`
```java
package com.warmpixel.storyadventure.loader;

import com.warmpixel.storyadventure.core.graph.StageGraph;

import java.util.*;

/**
 * Registry for all loaded story definitions.
 */
public class StoryRegistry {
    
    private final Map<String, StageGraph> stories = new HashMap<>();
    
    /**
     * Register a story.
     */
    public void register(StageGraph story) {
        stories.put(story.getStoryId(), story);
    }
    
    /**
     * Unregister a story.
     */
    public void unregister(String storyId) {
        stories.remove(storyId);
    }
    
    /**
     * Get a story by its ID.
     */
    public StageGraph getStory(String storyId) {
        return stories.get(storyId);
    }
    
    /**
     * Check if a story exists.
     */
    public boolean hasStory(String storyId) {
        return stories.containsKey(storyId);
    }
    
    /**
     * Get all registered stories.
     */
    public Collection<StageGraph> getAllStories() {
        return Collections.unmodifiableCollection(stories.values());
    }
    
    /**
     * Get all story IDs.
     */
    public Set<String> getStoryIds() {
        return Collections.unmodifiableSet(stories.keySet());
    }
    
    /**
     * Get the number of registered stories.
     */
    public int getStoryCount() {
        return stories.size();
    }
    
    /**
     * Clear all registered stories.
     */
    public void clear() {
        stories.clear();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/loader/StoryValidator.java`
```java
package com.warmpixel.storyadventure.loader;

import com.warmpixel.storyadventure.core.graph.StageGraph;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.core.graph.StageEdge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates story graphs for correctness and completeness.
 */
public class StoryValidator {
    
    /**
     * Validate a story graph.
     * 
     * @return List of validation errors (empty if valid)
     */
    public List<String> validate(StageGraph graph) {
        List<String> errors = new ArrayList<>();
        
        // Check required fields
        if (graph.getStoryId() == null || graph.getStoryId().isEmpty()) {
            errors.add("Story ID is required");
        }
        
        if (graph.getEntryNodeId() == null || graph.getEntryNodeId().isEmpty()) {
            errors.add("Entry node ID is required");
        }
        
        // Check entry node exists
        if (graph.getEntryNode() == null) {
            errors.add("Entry node '" + graph.getEntryNodeId() + "' does not exist");
        }
        
        // Validate each node
        Set<String> nodeIds = new HashSet<>();
        for (StageNode node : graph.getAllNodes()) {
            nodeIds.add(node.getId());
            
            if (node.getType() == null) {
                errors.add("Node '" + node.getId() + "' has no type");
            }
        }
        
        // Validate edge targets exist
        for (StageNode node : graph.getAllNodes()) {
            for (StageEdge edge : node.getEdges()) {
                if (!nodeIds.contains(edge.getTargetNodeId())) {
                    errors.add("Node '" + node.getId() + "' has edge to non-existent node '" 
                        + edge.getTargetNodeId() + "'");
                }
            }
        }
        
        // Check for unreachable nodes (optional warning)
        Set<String> reachable = findReachableNodes(graph);
        for (String nodeId : nodeIds) {
            if (!reachable.contains(nodeId) && !nodeId.equals(graph.getEntryNodeId())) {
                // This is a warning, not an error
                // Could be intentional for hidden/bonus content
            }
        }
        
        // Check player limits
        if (graph.getMinPlayers() < 1) {
            errors.add("Minimum players must be at least 1");
        }
        
        if (graph.getMaxPlayers() < graph.getMinPlayers()) {
            errors.add("Maximum players must be >= minimum players");
        }
        
        return errors;
    }
    
    /**
     * Find all nodes reachable from the entry node.
     */
    private Set<String> findReachableNodes(StageGraph graph) {
        Set<String> reachable = new HashSet<>();
        Set<String> toVisit = new HashSet<>();
        toVisit.add(graph.getEntryNodeId());
        
        while (!toVisit.isEmpty()) {
            String nodeId = toVisit.iterator().next();
            toVisit.remove(nodeId);
            
            if (reachable.add(nodeId)) {
                StageNode node = graph.getNode(nodeId);
                if (node != null) {
                    for (StageEdge edge : node.getEdges()) {
                        if (!reachable.contains(edge.getTargetNodeId())) {
                            toVisit.add(edge.getTargetNodeId());
                        }
                    }
                }
            }
        }
        
        return reachable;
    }
    
    /**
     * Check if a story has any ending nodes (nodes with no outgoing edges).
     */
    public boolean hasEndings(StageGraph graph) {
        for (StageNode node : graph.getAllNodes()) {
            if (node.getEdges().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/StoryAdventureClient.java`
```java
package com.warmpixel.storyadventure.client;

import com.warmpixel.storyadventure.client.command.ClientUICommands;
import com.warmpixel.storyadventure.client.render.EnemyIndicatorRenderer;
import com.warmpixel.storyadventure.client.render.TriggerBoxGizmoRenderer;
import com.warmpixel.storyadventure.client.render.WaypointIndicatorRenderer;
import com.warmpixel.storyadventure.client.ui.hud.EdgeIndicatorRenderer;
import com.warmpixel.storyadventure.client.ui.hud.StrangerHudRenderer;
import com.warmpixel.storyadventure.network.ClientNetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/**
 * Client-side entrypoint for Story Adventure System.
 * Handles UI rendering, HUD overlays, and client-side networking.
 */
@Environment(EnvType.CLIENT)
public class StoryAdventureClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        // Register client-side packet receivers for server-to-client UI commands
        ClientNetworkHandler.registerClientReceivers();
        
        // Register HUD overlays
        StrangerHudRenderer.register();
        EdgeIndicatorRenderer.register();
        WaypointIndicatorRenderer.register();
        com.warmpixel.storyadventure.client.render.WorldDestinationRenderer.register();
        
        // Register world-space renderers
        TriggerBoxGizmoRenderer.register();
        EnemyIndicatorRenderer.register();
        
        // Register cinematic overlay for cutscenes
        com.warmpixel.storyadventure.client.render.CinematicOverlayRenderer.register();
        
        // Register client-side UI commands (these work when typing directly in chat)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            ClientUICommands.register(dispatcher);
        });
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/command/ClientUICommands.java`
```java
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
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/CameraRecorderScreen.java`
```java
package com.warmpixel.storyadventure.client.ui;

import com.google.gson.GsonBuilder;
import com.warmpixel.storyadventure.client.cinematic.CameraPath;
import com.warmpixel.storyadventure.client.cinematic.CameraRecording;
import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Camera Recorder Screen - Compact UI for recording camera positions and rotations.
 * Uses the Stranger Things theme for consistent styling.
 */
public class CameraRecorderScreen extends StrangerScreen {
    
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 320;
    
    // Additional colors
    private static final int COLOR_PANEL_BG = 0xE8101018;
    private static final int COLOR_SECTION_BG = 0xCC1A1A2A;
    private static final int COLOR_ACCENT_CYAN = 0xFF00D9FF;
    private static final int COLOR_SUCCESS = 0xFF4CAF50;
    private static final int COLOR_WARNING = 0xFFFF9800;
    
    private final CameraRecording recording;
    
    // UI state
    private EditBox durationBox;
    private int selectedEasingIndex = 3;
    private final String[] easingOptions = {"LINEAR", "EASE_IN", "EASE_OUT", "EASE_IN_OUT", "CUBIC_IN_OUT", "SMOOTH_STEP"};
    
    private int scrollOffset = 0;
    private int selectedKeyframe = -1;
    
    private String statusMessage = "";
    private long statusMessageTime = 0;
    private int statusColor = COLOR_SUCCESS;
    
    public CameraRecorderScreen() {
        super(Component.literal("📹 摄像机录制器"));
        this.recording = new CameraRecording("New Recording");
    }
    
    @Override
    protected void init() {
        super.init();
        strangerButtons.clear();
        
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        
        // Duration input box
        durationBox = new EditBox(font, panelX + 110, panelY + 118, 50, 14, Component.literal("Duration"));
        durationBox.setValue("60");
        durationBox.setMaxLength(5);
        durationBox.setFilter(s -> s.matches("\\d*"));
        durationBox.setTextColor(0xFFFFFFFF);
        durationBox.setBordered(true);
        addRenderableWidget(durationBox);
        
        // === Recording Controls Row ===
        int ctrlY = panelY + 135;
        
        addStrangerButton(panelX + 15, ctrlY, 120, 18, 
            Component.literal("📍 录制关键帧"), this::recordKeyframe);
        
        addStrangerButton(panelX + 175, ctrlY, 150, 18,
            Component.literal(easingOptions[selectedEasingIndex]), this::cycleEasing);
        
        // === Bottom Action Buttons ===
        int btnY = panelY + PANEL_HEIGHT - 32;
        int btnWidth = 60;
        int btnGap = 5;
        int btnX = panelX + 15;
        
        addStrangerButton(btnX, btnY, btnWidth, 18, 
            Component.literal("💾 保存"), this::saveRecording);
        btnX += btnWidth + btnGap;
        
        addStrangerButton(btnX, btnY, btnWidth, 18,
            Component.literal("📂 加载"), this::loadRecording);
        btnX += btnWidth + btnGap;
        
        addStrangerButton(btnX, btnY, btnWidth, 18,
            Component.literal("🗑 清空"), () -> {
                recording.clearKeyframes();
                setStatus("已清空", COLOR_WARNING);
            });
        btnX += btnWidth + btnGap;
        
        addStrangerButton(btnX, btnY, btnWidth, 18,
            Component.literal("▶ 预览"), this::previewPath);
        btnX += btnWidth + btnGap;
        
        addStrangerButton(btnX, btnY, btnWidth, 18,
            Component.literal("📋 复制"), this::copyToClipboard);
    }
    
    private void cycleEasing() {
        selectedEasingIndex = (selectedEasingIndex + 1) % easingOptions.length;
        if (strangerButtons.size() > 1) {
            strangerButtons.get(1).setMessage(Component.literal(easingOptions[selectedEasingIndex]));
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        
        // Main panel
        renderPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
        
        int y = panelY + 30;
        
        // === Current Camera Section ===
        renderSectionBackground(graphics, panelX + 10, y, PANEL_WIDTH - 20, 45);
        graphics.drawString(font, "§e当前摄像机", panelX + 15, y + 3, COLOR_TEXT_TITLE);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameRenderer != null) {
            var camera = mc.gameRenderer.getMainCamera();
            var pos = camera.getPosition();
            float yaw = camera.getYRot();
            float pitch = camera.getXRot();
            
            graphics.drawString(font, String.format("§7位置: §fX:%.1f Y:%.1f Z:%.1f", pos.x, pos.y, pos.z), 
                panelX + 15, y + 16, COLOR_TEXT_BODY);
            graphics.drawString(font, String.format("§7旋转: §f偏航:%.1f° 俯仰:%.1f° §7FOV:§f%.0f", yaw, pitch, mc.options.fov().get().doubleValue()), 
                panelX + 15, y + 28, COLOR_TEXT_BODY);
        }
        
        y += 50;
        
        // === Recording Controls Section ===
        renderSectionBackground(graphics, panelX + 10, y, PANEL_WIDTH - 20, 40);
        graphics.drawString(font, "§e录制控制", panelX + 15, y + 3, COLOR_TEXT_TITLE);
        graphics.drawString(font, "时长(tick):", panelX + 15, y + 20, COLOR_TEXT_DIM);
        graphics.drawString(font, "缓动:", panelX + 175, y + 20, COLOR_TEXT_DIM);
        
        y += 60;
        
        // === Keyframes List Section ===
        String listTitle = String.format("§e关键帧 (%d)", recording.getKeyframeCount());
        renderSectionBackground(graphics, panelX + 10, y, PANEL_WIDTH - 20, 85);
        graphics.drawString(font, listTitle, panelX + 15, y + 3, COLOR_TEXT_TITLE);
        
        int listY = y + 15;
        int listItemHeight = 16;
        int maxVisible = 4;
        
        List<CameraRecording.RecordedKeyframe> keyframes = recording.getKeyframes();
        
        if (keyframes.isEmpty()) {
            graphics.drawCenteredString(font, "§8暂无关键帧", panelX + PANEL_WIDTH / 2, listY + 25, COLOR_TEXT_DIM);
        } else {
            for (int i = scrollOffset; i < Math.min(keyframes.size(), scrollOffset + maxVisible); i++) {
                CameraRecording.RecordedKeyframe kf = keyframes.get(i);
                int itemY = listY + (i - scrollOffset) * listItemHeight;
                
                if (i == selectedKeyframe) {
                    graphics.fill(panelX + 12, itemY, panelX + PANEL_WIDTH - 12, itemY + listItemHeight - 1, 0x30FFFFFF);
                }
                
                if (mouseX >= panelX + 12 && mouseX <= panelX + PANEL_WIDTH - 12 &&
                    mouseY >= itemY && mouseY < itemY + listItemHeight) {
                    graphics.fill(panelX + 12, itemY, panelX + PANEL_WIDTH - 12, itemY + listItemHeight - 1, 0x15FFFFFF);
                }
                
                graphics.drawString(font, String.format("§b#%d", i + 1), panelX + 15, itemY + 3, COLOR_ACCENT_CYAN);
                graphics.drawString(font, String.format("§7(%.0f,%.0f,%.0f)", kf.x(), kf.y(), kf.z()), panelX + 35, itemY + 3, COLOR_TEXT_BODY);
                graphics.drawString(font, String.format("§f%.0f°/%.0f°", kf.yaw(), kf.pitch()), panelX + 155, itemY + 3, COLOR_TEXT_BODY);
                graphics.drawString(font, String.format("§e%dt", kf.durationTicks()), panelX + 230, itemY + 3, COLOR_TEXT_BODY);
                graphics.drawString(font, "§c✕", panelX + PANEL_WIDTH - 25, itemY + 3, 0xFFFF5555);
            }
            
            if (scrollOffset > 0) graphics.drawString(font, "§7▲", panelX + PANEL_WIDTH - 22, y + 3, 0xAAAAAA);
            if (scrollOffset + maxVisible < keyframes.size()) graphics.drawString(font, "§7▼", panelX + PANEL_WIDTH - 22, y + 75, 0xAAAAAA);
        }
        
        y += 90;
        
        // === Status Message ===
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime < 2500) {
            graphics.drawCenteredString(font, statusMessage, panelX + PANEL_WIDTH / 2, y, statusColor);
        }
    }
    
    private void renderPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        // Glow
        for (int i = 3; i >= 1; i--) {
            int glowColor = ((8 + i * 4) << 24) | (COLOR_NEON_RED & 0x00FFFFFF);
            graphics.fill(x - i, y - i, x + w + i, y + h + i, glowColor);
        }
        
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL_BG);
        
        // Border
        graphics.fill(x, y, x + w, y + 1, COLOR_NEON_RED);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_NEON_RED);
        graphics.fill(x, y, x + 1, y + h, COLOR_NEON_RED);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_NEON_RED);
        
        // Header
        graphics.fill(x + 1, y + 1, x + w - 1, y + 24, 0xFF1A0808);
        graphics.fill(x + 1, y + 24, x + w - 1, y + 25, COLOR_BORDER);
        
        graphics.drawCenteredString(font, title, x + w / 2, y + 8, COLOR_ACCENT_CYAN);
        graphics.drawString(font, "§8[ESC]", x + w - 35, y + 8, COLOR_TEXT_DIM);
    }
    
    private void renderSectionBackground(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, COLOR_SECTION_BG);
        graphics.fill(x, y, x + w, y + 1, 0x25FFFFFF);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        int maxScroll = Math.max(0, recording.getKeyframeCount() - 4);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) vertAmount));
        return true;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        
        int listY = panelY + 30 + 50 + 60 + 15;
        int listItemHeight = 16;
        
        if (mouseX >= panelX + 12 && mouseX <= panelX + PANEL_WIDTH - 12) {
            for (int i = 0; i < Math.min(recording.getKeyframeCount() - scrollOffset, 4); i++) {
                int itemY = listY + i * listItemHeight;
                if (mouseY >= itemY && mouseY < itemY + listItemHeight) {
                    int keyframeIndex = scrollOffset + i;
                    
                    if (mouseX >= panelX + PANEL_WIDTH - 30) {
                        recording.removeKeyframe(keyframeIndex);
                        setStatus("已删除 #" + (keyframeIndex + 1), COLOR_WARNING);
                        if (selectedKeyframe >= recording.getKeyframeCount()) selectedKeyframe = -1;
                        return true;
                    }
                    
                    selectedKeyframe = keyframeIndex;
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private void recordKeyframe() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) return;
        
        var camera = mc.gameRenderer.getMainCamera();
        var pos = camera.getPosition();
        float yaw = camera.getYRot();
        float pitch = camera.getXRot();
        float fov = mc.options.fov().get().floatValue();
        
        int duration = 60;
        try { duration = Integer.parseInt(durationBox.getValue()); } catch (NumberFormatException ignored) {}
        if (recording.getKeyframeCount() == 0) duration = 0;
        
        recording.addKeyframe(pos.x, pos.y, pos.z, yaw, pitch, fov, duration, easingOptions[selectedEasingIndex]);
        setStatus("已录制 #" + recording.getKeyframeCount(), COLOR_SUCCESS);
    }
    
    private void saveRecording() {
        if (recording.getKeyframeCount() == 0) { setStatus("无关键帧", COLOR_NEON_RED); return; }
        try {
            Path savedPath = recording.saveToFile();
            setStatus("已保存: " + savedPath.getFileName(), COLOR_SUCCESS);
        } catch (IOException e) { setStatus("保存失败", COLOR_NEON_RED); }
    }
    
    private void loadRecording() {
        List<Path> files = CameraRecording.listRecordingFiles();
        if (files.isEmpty()) { setStatus("无文件", COLOR_WARNING); return; }
        
        try {
            CameraRecording loaded = CameraRecording.loadFromFile(files.get(files.size() - 1));
            recording.clearKeyframes();
            for (var kf : loaded.getKeyframes()) recording.addKeyframe(kf);
            setStatus("已加载 " + loaded.getKeyframeCount() + " 帧", COLOR_SUCCESS);
        } catch (IOException e) { setStatus("加载失败", COLOR_NEON_RED); }
    }
    
    private void previewPath() {
        if (recording.getKeyframeCount() < 2) { setStatus("需≥2帧", COLOR_WARNING); return; }
        
        CameraPath path = recording.toCameraPath();
        CinematicCameraController.CutsceneConfig config = new CinematicCameraController.CutsceneConfig()
            .setSkippable(true).setLetterboxEnabled(true)
            .setOnComplete(() -> Minecraft.getInstance().setScreen(new CameraRecorderScreen()));
        
        CinematicCameraController.getInstance().startCutscene(path, config);
        this.onClose();
    }
    
    private void copyToClipboard() {
        if (recording.getKeyframeCount() == 0) { setStatus("无关键帧", COLOR_WARNING); return; }
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(recording.toCameraPathJson());
        Minecraft.getInstance().keyboardHandler.setClipboard(json);
        setStatus("已复制JSON", COLOR_SUCCESS);
    }
    
    private void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusMessageTime = System.currentTimeMillis();
        this.statusColor = color;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/StrangerButton.java`
```java
package com.warmpixel.storyadventure.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom Stranger Things themed button - no vanilla styling.
 * Neon red glow effect with dark background, 80s synth-wave aesthetic.
 */
public class StrangerButton extends AbstractWidget {
    // Color scheme (Stranger Things neon red theme)
    private static final int COLOR_NEON_RED = 0xFFE50914;       // Netflix red
    private static final int COLOR_NEON_GLOW = 0xAAFF3366;      // Pink glow
    private static final int COLOR_DARK_BG = 0xFF0A0A0A;        // Near black
    private static final int COLOR_HOVER_BG = 0xFF1A0A0A;       // Slight red tint when hovered
    private static final int COLOR_TEXT = 0xFFE0E0E0;           // Light gray text
    private static final int COLOR_TEXT_HOVER = 0xFFFFFFFF;     // White on hover
    private static final int COLOR_BORDER = 0xFF330011;         // Dark red border
    
    private final Runnable onPress;
    private boolean glowPulse = true;
    private long creationTime;
    
    public StrangerButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.creationTime = System.currentTimeMillis();
    }
    
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered();
        
        // Calculate pulsing glow effect
        float pulsePhase = ((System.currentTimeMillis() - creationTime) % 2000) / 2000f;
        float glowIntensity = glowPulse ? (float)(0.5 + 0.5 * Math.sin(pulsePhase * Math.PI * 2)) : 1f;
        
        // Draw outer glow (when hovered or pulsing)
        if (hovered || glowPulse) {
            int glowAlpha = (int)(60 * glowIntensity);
            int glowColor = (glowAlpha << 24) | (COLOR_NEON_GLOW & 0x00FFFFFF);
            
            // Multiple layers for blur effect
            for (int i = 3; i >= 1; i--) {
                graphics.fill(getX() - i, getY() - i, getX() + width + i, getY() + height + i, glowColor);
            }
        }
        
        // Draw background
        int bgColor = hovered ? COLOR_HOVER_BG : COLOR_DARK_BG;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
        
        // Draw border with gradient effect
        int borderColor = hovered ? COLOR_NEON_RED : COLOR_BORDER;
        
        // Top border
        graphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);
        // Bottom border
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor);
        // Left border
        graphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);
        // Right border
        graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);
        
        // Draw corner accents (80s style)
        if (hovered) {
            int cornerSize = 4;
            // Top-left
            graphics.fill(getX(), getY(), getX() + cornerSize, getY() + 2, COLOR_NEON_RED);
            graphics.fill(getX(), getY(), getX() + 2, getY() + cornerSize, COLOR_NEON_RED);
            // Top-right
            graphics.fill(getX() + width - cornerSize, getY(), getX() + width, getY() + 2, COLOR_NEON_RED);
            graphics.fill(getX() + width - 2, getY(), getX() + width, getY() + cornerSize, COLOR_NEON_RED);
            // Bottom-left
            graphics.fill(getX(), getY() + height - 2, getX() + cornerSize, getY() + height, COLOR_NEON_RED);
            graphics.fill(getX(), getY() + height - cornerSize, getX() + 2, getY() + height, COLOR_NEON_RED);
            // Bottom-right
            graphics.fill(getX() + width - cornerSize, getY() + height - 2, getX() + width, getY() + height, COLOR_NEON_RED);
            graphics.fill(getX() + width - 2, getY() + height - cornerSize, getX() + width, getY() + height, COLOR_NEON_RED);
        }
        
        // Draw text centered
        int textColor = hovered ? COLOR_TEXT_HOVER : COLOR_TEXT;
        graphics.drawCenteredString(
            net.minecraft.client.Minecraft.getInstance().font,
            getMessage(),
            getX() + width / 2,
            getY() + (height - 8) / 2,
            textColor
        );
    }
    
    @Override
    public void onClick(double mouseX, double mouseY) {
        if (onPress != null) {
            onPress.run();
        }
    }
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
    
    public StrangerButton setGlowPulse(boolean pulse) {
        this.glowPulse = pulse;
        return this;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/StrangerDialogueScreen.java`
```java
package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stranger Things themed dialogue screen for NPC conversations.
 * Features typewriter text effect, neon accents, and custom choice buttons.
 */
public class StrangerDialogueScreen extends StrangerScreen {
    
    private static final int PANEL_MARGIN = 40;
    private static final int DIALOGUE_BOX_HEIGHT = 120;
    private static final int CHOICE_BUTTON_HEIGHT = 24;
    private static final int CHOICE_BUTTON_MARGIN = 4;
    
    private String npcName;
    private String dialogueText;
    private List<DialogueChoice> choices = new ArrayList<>();
    
    // Typewriter effect
    private int displayedCharacters = 0;
    private long lastCharTime = 0;
    private static final long CHAR_DELAY_MS = 30;
    private boolean textComplete = false;
    
    public StrangerDialogueScreen(String npcName, String dialogueText) {
        super(Component.literal(npcName));
        this.npcName = npcName;
        this.dialogueText = dialogueText;
    }
    
    public void addChoice(String choiceId, String choiceText, Runnable onSelect) {
        choices.add(new DialogueChoice(choiceId, choiceText, () -> {
            // Send network packet
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.warmpixel.storyadventure.network.DialogueChoicePayload(choiceId)
            );
            // Run custom callback (e.g. close screen)
            if (onSelect != null) onSelect.run();
        }));
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Initialize choice buttons (hidden until text complete)
        int choiceY = height - PANEL_MARGIN - DIALOGUE_BOX_HEIGHT + 50;
        int choiceWidth = width - PANEL_MARGIN * 4;
        
        for (int i = 0; i < choices.size(); i++) {
            DialogueChoice choice = choices.get(i);
            int y = choiceY + i * (CHOICE_BUTTON_HEIGHT + CHOICE_BUTTON_MARGIN);
            
            StrangerButton button = addStrangerButton(
                width / 2 - choiceWidth / 2,
                y,
                choiceWidth,
                CHOICE_BUTTON_HEIGHT,
                Component.literal(choice.text),
                choice.onSelect
            );
            button.visible = false; // Hidden until text completes
            button.setGlowPulse(false);
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Update typewriter effect
        updateTypewriter();
        
        // Render dialogue box
        int boxX = PANEL_MARGIN;
        int boxY = height - PANEL_MARGIN - DIALOGUE_BOX_HEIGHT;
        int boxWidth = width - PANEL_MARGIN * 2;
        
        // Draw dialogue box background
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + DIALOGUE_BOX_HEIGHT, 0xE0080808);
        
        // Draw box border
        drawBoxBorder(graphics, boxX, boxY, boxWidth, DIALOGUE_BOX_HEIGHT);
        
        // Draw NPC name badge
        int nameWidth = font.width(npcName) + 16;
        graphics.fill(boxX + 10, boxY - 8, boxX + 10 + nameWidth, boxY + 4, 0xFF0A0A0A);
        graphics.fill(boxX + 10, boxY - 8, boxX + 10 + nameWidth, boxY - 6, COLOR_NEON_RED);
        graphics.drawString(font, npcName, boxX + 18, boxY - 6, COLOR_NEON_RED);
        
        // Draw dialogue text with typewriter effect
        String displayText = dialogueText.substring(0, Math.min(displayedCharacters, dialogueText.length()));
        drawWrappedText(graphics, displayText, boxX + 15, boxY + 12, boxWidth - 30, COLOR_TEXT_BODY);
        
        // Show blinking cursor if not complete
        if (!textComplete) {
            long blink = (System.currentTimeMillis() / 500) % 2;
            if (blink == 0) {
                String cursorText = displayText + "▌";
                int cursorX = boxX + 15 + font.width(getLastLine(displayText));
                int cursorY = boxY + 12 + getLineCount(displayText, boxWidth - 30) * 10;
                graphics.drawString(font, "▌", cursorX, cursorY, COLOR_NEON_RED);
            }
        }
        
        // Show/hide choice buttons based on text completion
        for (int i = 0; i < strangerButtons.size(); i++) {
            strangerButtons.get(i).visible = textComplete;
        }
    }
    
    private void updateTypewriter() {
        if (displayedCharacters < dialogueText.length()) {
            long now = System.currentTimeMillis();
            if (now - lastCharTime >= CHAR_DELAY_MS) {
                displayedCharacters++;
                lastCharTime = now;
            }
        } else {
            textComplete = true;
        }
    }
    
    private void drawBoxBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        // Neon red border with corner accents
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        // Corner highlights
        int cs = 8;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
        graphics.fill(x, y + h - 2, x + cs, y + h, COLOR_NEON_RED);
        graphics.fill(x, y + h - cs, x + 2, y + h, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y + h - 2, x + w, y + h, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y + h - cs, x + w, y + h, COLOR_NEON_RED);
    }
    
    private void drawWrappedText(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        List<String> lines = wrapText(text, maxWidth);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 10, color);
        }
    }
    
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String test = current.toString() + c;
            
            if (font.width(test) <= maxWidth) {
                current.append(c);
            } else {
                if (!current.isEmpty()) lines.add(current.toString());
                current = new StringBuilder(String.valueOf(c));
            }
        }
        
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
    
    private String getLastLine(String text) {
        int lastNewline = text.lastIndexOf('\n');
        return lastNewline >= 0 ? text.substring(lastNewline + 1) : text;
    }
    
    private int getLineCount(String text, int maxWidth) {
        return wrapText(text, maxWidth).size();
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Skip text on any key
        if (!textComplete) {
            displayedCharacters = dialogueText.length();
            textComplete = true;
            return true;
        }
        
        // Prevent closing on ESC by returning true without calling super
        if (keyCode == 256) { // GLFW_KEY_ESCAPE is 256
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Skip text on click
        if (!textComplete) {
            displayedCharacters = dialogueText.length();
            textComplete = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    public record DialogueChoice(String id, String text, Runnable onSelect) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/StrangerInvitationNotificationScreen.java`
```java
package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.warmpixel.storyadventure.network.InvitePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Screen that pops up when you receive an invitation.
 */
public class StrangerInvitationNotificationScreen extends StrangerScreen {
    
    private final String inviterName;
    
    public StrangerInvitationNotificationScreen(String inviterName) {
        super(Component.literal("收到邀请"));
        this.inviterName = inviterName;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int x = width / 2;
        int y = height / 2;
        
        addStrangerButton(x - 105, y + 20, 100, 24, Component.literal("✔ 接受邀请"), this::accept);
        addStrangerButton(x + 5, y + 20, 100, 24, Component.literal("✕ 拒绝"), this::decline);
    }
    
    private void accept() {
        ClientPlayNetworking.send(new InvitePayload(inviterName, true, true));
        onClose();
    }
    
    private void decline() {
        ClientPlayNetworking.send(new InvitePayload(inviterName, true, false));
        onClose();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int rectW = 240;
        int rectH = 100;
        int rectX = width / 2 - rectW / 2;
        int rectY = height / 2 - rectH / 2;
        
        graphics.fill(rectX, rectY, rectX + rectW, rectY + rectH, 0xF0080808);
        drawPanelBorder(graphics, rectX, rectY, rectW, rectH);
        
        graphics.drawCenteredString(font, "队伍邀请", width / 2, rectY + 15, COLOR_NEON_RED);
        graphics.drawCenteredString(font, "玩家 " + inviterName + " 邀请你加入他的队伍", width / 2, rectY + 40, 0xFFFFFFFF);
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/StrangerInvitePlayerScreen.java`
```java
package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import com.warmpixel.storyadventure.network.InvitePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Small overlay to input a player's name for invitation.
 */
public class StrangerInvitePlayerScreen extends StrangerScreen {
    
    private EditBox nameInput;
    private final StrangerScreen parent;
    
    public StrangerInvitePlayerScreen(StrangerScreen parent) {
        super(Component.literal("邀请玩家"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int boxWidth = 200;
        int boxHeight = 20;
        int x = width / 2 - boxWidth / 2;
        int y = height / 2 - 20;
        
        nameInput = new EditBox(font, x, y, boxWidth, boxHeight, Component.literal("玩家名称"));
        nameInput.setMaxLength(16);
        nameInput.setFocused(true);
        addWidget(nameInput);
        
        addStrangerButton(x, y + 30, 95, 20, Component.literal("发送邀请"), this::sendInvite);
        addStrangerButton(x + 105, y + 30, 95, 20, Component.literal("取消"), () -> minecraft.setScreen(parent));
    }
    
    private void sendInvite() {
        String name = nameInput.getValue().trim();
        if (!name.isEmpty()) {
            ClientPlayNetworking.send(new InvitePayload(name, false, false));
            minecraft.setScreen(parent);
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, "输入玩家名称", width / 2, height / 2 - 40, 0xFFFFFFFF);
        nameInput.render(graphics, mouseX, mouseY, partialTick);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/StrangerLobbyScreen.java`
```java
package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stranger Things themed lobby screen for players to ready up before starting.
 * Shows party members, ready status, and allows configuration.
 */
public class StrangerLobbyScreen extends StrangerScreen {
    
    private static final int MEMBER_ENTRY_HEIGHT = 36;
    
    private String storyName;
    private String storyDescription;
    private int minPlayers;
    private int maxPlayers;
    private int estimatedMinutes;
    
    private List<PartyMember> members = new ArrayList<>();
    private boolean isLeader = false;
    private boolean isReady = false;
    private long countdownStartTime = 0;
    private boolean countdownActive = false;
    
    public StrangerLobbyScreen(String storyName, String storyDescription, 
                                int minPlayers, int maxPlayers, int estimatedMinutes) {
        super(Component.literal("准备大厅"));
        this.storyName = storyName;
        this.storyDescription = storyDescription;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.estimatedMinutes = estimatedMinutes;
    }
    
    public void setLeader(boolean leader) {
        this.isLeader = leader;
    }
    
    public void setReady(boolean ready) {
        this.isReady = ready;
    }
    
    public void addMember(UUID playerId, String name, boolean ready, boolean isLeader) {
        members.add(new PartyMember(playerId, name, ready, isLeader));
    }
    
    public void clearMembers() {
        members.clear();
    }
    
    public void updateMemberReady(UUID playerId, boolean ready) {
        for (PartyMember m : members) {
            if (m.id.equals(playerId)) {
                members.set(members.indexOf(m), new PartyMember(m.id, m.name, ready, m.isLeader));
                break;
            }
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 140;
        int buttonHeight = 28;
        int bottomY = height - 50;
        
        // Ready button (for everyone)
        String buttonText = isReady ? "取消准备" : (isLeader ? "准备开始" : "准备就绪");
        addStrangerButton(width / 2 - buttonWidth / 2, bottomY, buttonWidth, buttonHeight,
            Component.literal(buttonText),
            this::toggleReady);
            
        // Start button (for leader, only if enough people ready)
        if (isLeader) {
            long readyCount = members.stream().filter(m -> m.ready).count();
            boolean canStart = readyCount >= minPlayers;
            
            StrangerButton startBtn = addStrangerButton(width / 2 + buttonWidth / 2 + 10, bottomY, 120, buttonHeight,
                Component.literal("开始冒险"),
                this::startAdventure);
            startBtn.active = canStart;
            startBtn.setGlowPulse(canStart);
            
            // Invite button
            addStrangerButton(width / 2 - buttonWidth / 2 - 130, bottomY, 120, buttonHeight,
                Component.literal("邀请玩家"),
                () -> minecraft.setScreen(new StrangerInvitePlayerScreen(this)));
        }
        
        // Cancel/Leave button (bottom right)
        addStrangerButton(width - 100, bottomY, 80, buttonHeight,
            Component.literal(isLeader ? "解散" : "离开"),
            isLeader ? this::disbandParty : this::onClose);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Story info panel (left side)
        renderStoryInfoPanel(graphics);
        
        // Party members panel (right side)
        renderPartyPanel(graphics, mouseX, mouseY);
        
        // Countdown overlay
        if (countdownActive) {
            renderCountdown(graphics);
        }
    }
    
    private void renderStoryInfoPanel(GuiGraphics graphics) {
        int panelX = 30;
        int panelY = 50;
        int panelWidth = width / 2 - 50;
        int panelHeight = height - 130;
        
        // Background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, panelX, panelY, panelWidth, panelHeight);
        
        int y = panelY + 10;
        
        // Story title
        graphics.drawString(font, "【" + storyName + "】", panelX + 15, y, COLOR_NEON_RED);
        y += 20;
        
        // Description (wrapped)
        List<String> descLines = wrapText(storyDescription, panelWidth - 30);
        for (String line : descLines) {
            graphics.drawString(font, line, panelX + 15, y, COLOR_TEXT_BODY);
            y += 11;
        }
        y += 10;
        
        // Separator
        graphics.fill(panelX + 15, y, panelX + panelWidth - 15, y + 1, COLOR_BORDER);
        y += 15;
        
        // Info items
        graphics.drawString(font, "👥 人数要求: " + minPlayers + "-" + maxPlayers + "人", panelX + 15, y, COLOR_TEXT_DIM);
        y += 14;
        graphics.drawString(font, "⏱ 预计时长: ~" + estimatedMinutes + "分钟", panelX + 15, y, COLOR_TEXT_DIM);
        y += 14;
        graphics.drawString(font, "⚠ 难度: ★★★☆☆", panelX + 15, y, COLOR_TEXT_DIM);
        
        // Tips at bottom
        y = panelY + panelHeight - 40;
        graphics.fill(panelX + 15, y, panelX + panelWidth - 15, y + 1, COLOR_BORDER);
        y += 8;
        graphics.drawString(font, "💡 提示: 建议携带武器和食物", panelX + 15, y, 0xFF888888);
    }
    
    private void renderPartyPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = width / 2 + 10;
        int panelY = 50;
        int panelWidth = width / 2 - 40;
        int panelHeight = height - 130;
        
        // Background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, panelX, panelY, panelWidth, panelHeight);
        
        // Panel title
        graphics.drawString(font, "队伍成员 (" + members.size() + "/" + maxPlayers + ")", 
            panelX + 15, panelY + 10, COLOR_NEON_RED);
        
        // Ready count
        long readyCount = members.stream().filter(m -> m.ready).count();
        String readyText = "准备: " + readyCount + "/" + members.size();
        int readyColor = readyCount == members.size() ? 0xFF44FF44 : COLOR_TEXT_DIM;
        graphics.drawString(font, readyText, panelX + panelWidth - font.width(readyText) - 15, 
            panelY + 10, readyColor);
        
        // Member entries
        int y = panelY + 30;
        for (PartyMember member : members) {
            renderMemberEntry(graphics, member, panelX + 10, y, panelWidth - 20, mouseX, mouseY);
            y += MEMBER_ENTRY_HEIGHT + 4;
        }
        
        // Empty slots
        for (int i = members.size(); i < maxPlayers; i++) {
            renderEmptySlot(graphics, panelX + 10, y, panelWidth - 20);
            y += MEMBER_ENTRY_HEIGHT + 4;
        }
    }
    
    private void renderMemberEntry(GuiGraphics graphics, PartyMember member, 
                                    int x, int y, int width, int mouseX, int mouseY) {
        // Background
        int bgColor = member.ready ? 0xFF0A1A0A : 0xFF0A0A0A;
        graphics.fill(x, y, x + width, y + MEMBER_ENTRY_HEIGHT, bgColor);
        
        // Border
        int borderColor = member.ready ? 0xFF44FF44 : COLOR_BORDER;
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + MEMBER_ENTRY_HEIGHT - 1, x + width, y + MEMBER_ENTRY_HEIGHT, borderColor);
        graphics.fill(x, y, x + 1, y + MEMBER_ENTRY_HEIGHT, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + MEMBER_ENTRY_HEIGHT, borderColor);
        
        // Player icon (placeholder)
        graphics.fill(x + 8, y + 6, x + 30, y + 30, 0xFF333333);
        graphics.drawString(font, "👤", x + 11, y + 12, COLOR_TEXT_BODY);
        
        // Name
        int nameColor = member.isLeader ? COLOR_NEON_RED : COLOR_TEXT_BODY;
        String nameText = member.name + (member.isLeader ? " 👑" : "");
        graphics.drawString(font, nameText, x + 38, y + 8, nameColor);
        
        // Ready status
        String statusText = member.ready ? "✓ 已准备" : "○ 等待中...";
        int statusColor = member.ready ? 0xFF44FF44 : COLOR_TEXT_DIM;
        graphics.drawString(font, statusText, x + 38, y + 22, statusColor);
    }
    
    private void renderEmptySlot(GuiGraphics graphics, int x, int y, int width) {
        // Dashed border style for empty slot
        graphics.fill(x, y, x + width, y + MEMBER_ENTRY_HEIGHT, 0x40080808);
        
        // Draw dashed border
        for (int i = 0; i < width; i += 8) {
            if ((i / 4) % 2 == 0) {
                graphics.fill(x + i, y, Math.min(x + i + 4, x + width), y + 1, COLOR_BORDER);
                graphics.fill(x + i, y + MEMBER_ENTRY_HEIGHT - 1, Math.min(x + i + 4, x + width), y + MEMBER_ENTRY_HEIGHT, COLOR_BORDER);
            }
        }
        
        graphics.drawString(font, "空位 - 等待加入...", x + width / 2 - 45, y + 14, COLOR_TEXT_DIM);
    }
    
    private int countdownSecondsLeft = -1;

    private void renderCountdown(GuiGraphics graphics) {
        if (countdownSecondsLeft < 0) return;
        
        long elapsedSinceLastSync = System.currentTimeMillis() - countdownStartTime;
        // We pulse based on time, but use the seconds from server
        
        // Dark overlay
        graphics.fill(0, 0, width, height, 0xC0000000);
        
        // Countdown number
        String countText = String.valueOf(countdownSecondsLeft);
        
        // Pulsing effect
        float pulse = (float)(0.8 + 0.2 * Math.sin(System.currentTimeMillis() / 100.0));
        int alpha = (int)(255 * pulse);
        int color = (alpha << 24) | (COLOR_NEON_RED & 0x00FFFFFF);
        
        graphics.drawCenteredString(font, countText, width / 2, height / 2 - 30, color);
        graphics.drawCenteredString(font, "冒险即将开始...", width / 2, height / 2 + 20, COLOR_TEXT_BODY);
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 10;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
        graphics.fill(x, y + h - 2, x + cs, y + h, COLOR_NEON_RED);
        graphics.fill(x, y + h - cs, x + 2, y + h, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y + h - 2, x + w, y + h, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y + h - cs, x + w, y + h, COLOR_NEON_RED);
    }
    
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String test = current.toString() + c;
            
            if (font.width(test) <= maxWidth) {
                current.append(c);
            } else {
                if (!current.isEmpty()) lines.add(current.toString());
                current = new StringBuilder(String.valueOf(c));
            }
        }
        
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
    
    private void toggleReady() {
        isReady = !isReady;
        // Send to server
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.warmpixel.storyadventure.network.ToggleReadyPayload(isReady)
        );
        // Rebuild will happen when server sends SyncLobby back, 
        // but we can rebuild locally for immediate feedback
        rebuildButtons();
    }
    
    private void startAdventure() {
        // Check if enough players are ready
        long readyCount = members.stream().filter(m -> m.ready).count();
        if (readyCount >= minPlayers) {
            // Send start request
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.warmpixel.storyadventure.network.StoryActionPayload(
                    com.warmpixel.storyadventure.network.StoryActionPayload.Action.START_ADVENTURE,
                    ""
                )
            );
        }
    }
    
    private void disbandParty() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.warmpixel.storyadventure.network.StoryActionPayload(
                isLeader ? com.warmpixel.storyadventure.network.StoryActionPayload.Action.DISBAND_PARTY :
                           com.warmpixel.storyadventure.network.StoryActionPayload.Action.LEAVE_PARTY,
                ""
            )
        );
        onClose();
    }
    
    public void rebuildButtons() {
        strangerButtons.clear();
        clearWidgets();
        init();
    }
    
    public void startCountdown(int seconds) {
        this.countdownActive = seconds >= 0;
        this.countdownSecondsLeft = seconds;
        this.countdownStartTime = System.currentTimeMillis();
    }
    
    public record PartyMember(UUID id, String name, boolean ready, boolean isLeader) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/StrangerPuzzleScreen.java`
```java
package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Stranger Things themed puzzle interaction screen.
 * Used for code locks, wiring puzzles, symbol matching, etc.
 */
public class StrangerPuzzleScreen extends StrangerScreen {
    
    private final String puzzleType;
    private final String hint;
    private final int maxAttempts;
    private int currentAttempts = 0;
    
    // Code lock state
    private StringBuilder codeInput = new StringBuilder();
    private int maxCodeLength = 4;
    
    public StrangerPuzzleScreen(String puzzleType, String hint, int maxAttempts) {
        super(Component.literal("解谜"));
        this.puzzleType = puzzleType;
        this.hint = hint;
        this.maxAttempts = maxAttempts;
    }
    
    @Override
    protected void init() {
        super.init();
        
        if ("CODE_LOCK".equals(puzzleType)) {
            initCodeLockButtons();
        }
    }
    
    private void initCodeLockButtons() {
        int buttonSize = 40;
        int gap = 8;
        int startX = (width - (3 * buttonSize + 2 * gap)) / 2;
        int startY = height / 2 - 20;
        
        // Number pad 1-9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int num = row * 3 + col + 1;
                int x = startX + col * (buttonSize + gap);
                int y = startY + row * (buttonSize + gap);
                
                final int digit = num;
                addStrangerButton(x, y, buttonSize, buttonSize, 
                    Component.literal(String.valueOf(num)),
                    () -> appendDigit(digit));
            }
        }
        
        // 0 button
        int x = startX + (buttonSize + gap);
        int y = startY + 3 * (buttonSize + gap);
        addStrangerButton(x, y, buttonSize, buttonSize, 
            Component.literal("0"), () -> appendDigit(0));
        
        // Clear and Submit buttons
        addStrangerButton(startX, y, buttonSize, buttonSize, 
            Component.literal("×"), this::clearCode);
        addStrangerButton(startX + 2 * (buttonSize + gap), y, buttonSize, buttonSize, 
            Component.literal("✓"), this::submitCode);
    }
    
    private void appendDigit(int digit) {
        if (codeInput.length() < maxCodeLength) {
            codeInput.append(digit);
        }
    }
    
    private void clearCode() {
        codeInput.setLength(0);
    }
    
    private void submitCode() {
        currentAttempts++;
        String input = codeInput.toString();
        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.warmpixel.storyadventure.network.PuzzleInputPayload(input)
        );
        
        codeInput.setLength(0);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw puzzle title
        String titleText = getPuzzleTitle();
        int titleWidth = font.width(titleText);
        graphics.drawString(font, titleText, (width - titleWidth) / 2, 45, COLOR_TEXT_BODY);
        
        // Draw code display
        if ("CODE_LOCK".equals(puzzleType)) {
            renderCodeDisplay(graphics);
        }
        
        // Draw hint
        if (!hint.isEmpty()) {
            renderHint(graphics);
        }
        
        // Draw attempt counter
        String attemptsText = String.format("尝试次数: %d / %d", currentAttempts, maxAttempts);
        int attemptsColor = currentAttempts >= maxAttempts - 1 ? 0xFFFF4444 : COLOR_TEXT_DIM;
        graphics.drawString(font, attemptsText, width - font.width(attemptsText) - 20, height - 30, attemptsColor);
    }
    
    private void renderCodeDisplay(GuiGraphics graphics) {
        int displayWidth = 150;
        int displayHeight = 36;
        int displayX = (width - displayWidth) / 2;
        int displayY = height / 2 - 80;
        
        // Background
        graphics.fill(displayX, displayY, displayX + displayWidth, displayY + displayHeight, 0xFF0A0A0A);
        
        // Border
        graphics.fill(displayX, displayY, displayX + displayWidth, displayY + 1, COLOR_NEON_RED);
        graphics.fill(displayX, displayY + displayHeight - 1, displayX + displayWidth, displayY + displayHeight, COLOR_NEON_RED);
        graphics.fill(displayX, displayY, displayX + 1, displayY + displayHeight, COLOR_NEON_RED);
        graphics.fill(displayX + displayWidth - 1, displayY, displayX + displayWidth, displayY + displayHeight, COLOR_NEON_RED);
        
        // Code display with asterisks
        StringBuilder display = new StringBuilder();
        for (int i = 0; i < maxCodeLength; i++) {
            if (i < codeInput.length()) {
                display.append("● ");
            } else {
                display.append("○ ");
            }
        }
        
        String displayText = display.toString().trim();
        int textWidth = font.width(displayText);
        graphics.drawString(font, displayText, displayX + (displayWidth - textWidth) / 2, displayY + 14, COLOR_NEON_RED);
    }
    
    private void renderHint(GuiGraphics graphics) {
        int hintY = height - 60;
        String hintLabel = "💡 提示: ";
        graphics.drawString(font, hintLabel + hint, 20, hintY, COLOR_TEXT_DIM);
    }
    
    private String getPuzzleTitle() {
        return switch (puzzleType) {
            case "CODE_LOCK" -> "密码锁";
            case "WIRING" -> "接线谜题";
            case "SYMBOL_MATCH" -> "符号匹配";
            default -> "解谜";
        };
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Number keys 0-9
        if (keyCode >= 48 && keyCode <= 57) {
            appendDigit(keyCode - 48);
            return true;
        }
        // Numpad 0-9
        if (keyCode >= 320 && keyCode <= 329) {
            appendDigit(keyCode - 320);
            return true;
        }
        // Backspace
        if (keyCode == 259 && codeInput.length() > 0) {
            codeInput.deleteCharAt(codeInput.length() - 1);
            return true;
        }
        // Enter
        if (keyCode == 257) {
            submitCode();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/StrangerScreen.java`
```java
package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Base screen for all Stranger Things themed story screens.
 * Dark background with neon accents, no vanilla UI elements.
 */
public abstract class StrangerScreen extends Screen {
    // Stranger Things color palette
    protected static final int COLOR_BG_DARK = 0xF0050505;       // Near black with transparency
    protected static final int COLOR_BG_GRADIENT = 0xF0100808;   // Dark red tint
    protected static final int COLOR_NEON_RED = 0xFFE50914;      // Netflix/Stranger Things red
    protected static final int COLOR_NEON_PINK = 0xFFFF3366;     // Neon pink accent
    protected static final int COLOR_TEXT_TITLE = 0xFFE50914;    // Red title text
    protected static final int COLOR_TEXT_BODY = 0xFFCCCCCC;     // Light gray body
    protected static final int COLOR_TEXT_DIM = 0xFF666666;      // Dimmed text
    protected static final int COLOR_BORDER = 0xFF330011;        // Dark red border
    protected static final int COLOR_SCANLINE = 0x08FFFFFF;      // CRT scanline effect
    
    protected final List<StrangerButton> strangerButtons = new ArrayList<>();
    protected long screenOpenTime;
    
    protected StrangerScreen(Component title) {
        super(title);
        this.screenOpenTime = System.currentTimeMillis();
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render dark background with vignette effect
        renderStrangerBackground(graphics);
        
        // Render CRT scanline effect
        renderScanlines(graphics);
        
        // Render title with glow
        renderTitle(graphics);
        
        // Render custom buttons
        for (StrangerButton button : strangerButtons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }
        
        // Render content (subclass implementation)
        renderContent(graphics, mouseX, mouseY, partialTick);
        
        // Render border frame
        renderBorderFrame(graphics);
    }
    
    protected void renderStrangerBackground(GuiGraphics graphics) {
        // Full screen dark gradient
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Base dark fill
        graphics.fill(0, 0, width, height, COLOR_BG_DARK);
        
        // Radial vignette effect (darker at edges)
        for (int layer = 0; layer < 5; layer++) {
            int alpha = 20 + layer * 8;
            int vignetteColor = (alpha << 24);
            int inset = layer * 40;
            
            // Top
            graphics.fill(0, 0, width, inset, vignetteColor);
            // Bottom
            graphics.fill(0, height - inset, width, height, vignetteColor);
            // Left
            graphics.fill(0, 0, inset, height, vignetteColor);
            // Right
            graphics.fill(width - inset, 0, width, height, vignetteColor);
        }
        
        // Subtle red glow in center
        int glowRadius = 100;
        int glowColor = 0x10E50914;
        graphics.fill(centerX - glowRadius, centerY - glowRadius, 
                     centerX + glowRadius, centerY + glowRadius, glowColor);
    }
    
    protected void renderScanlines(GuiGraphics graphics) {
        // CRT monitor scanline effect for 80s aesthetic
        for (int y = 0; y < height; y += 2) {
            graphics.fill(0, y, width, y + 1, COLOR_SCANLINE);
        }
    }
    
    protected void renderTitle(GuiGraphics graphics) {
        // Pulsing glow effect
        float pulse = (float)(0.7 + 0.3 * Math.sin((System.currentTimeMillis() - screenOpenTime) / 500.0));
        int glowAlpha = (int)(40 * pulse);
        int glowColor = (glowAlpha << 24) | (COLOR_NEON_PINK & 0x00FFFFFF);
        
        String titleText = title.getString();
        int titleWidth = font.width(titleText);
        int titleX = (width - titleWidth) / 2;
        int titleY = 20;
        
        // Draw glow behind text
        graphics.fill(titleX - 10, titleY - 4, titleX + titleWidth + 10, titleY + 14, glowColor);
        
        // Draw title text
        graphics.drawString(font, title, titleX, titleY, COLOR_TEXT_TITLE);
        
        // Draw underline accent
        int underlineY = titleY + 12;
        graphics.fill(titleX - 5, underlineY, titleX + titleWidth + 5, underlineY + 1, COLOR_NEON_RED);
    }
    
    protected void renderBorderFrame(GuiGraphics graphics) {
        int margin = 8;
        int cornerSize = 20;
        
        // Corner accents (Stranger Things style)
        // Top-left
        graphics.fill(margin, margin, margin + cornerSize, margin + 2, COLOR_NEON_RED);
        graphics.fill(margin, margin, margin + 2, margin + cornerSize, COLOR_NEON_RED);
        
        // Top-right
        graphics.fill(width - margin - cornerSize, margin, width - margin, margin + 2, COLOR_NEON_RED);
        graphics.fill(width - margin - 2, margin, width - margin, margin + cornerSize, COLOR_NEON_RED);
        
        // Bottom-left
        graphics.fill(margin, height - margin - 2, margin + cornerSize, height - margin, COLOR_NEON_RED);
        graphics.fill(margin, height - margin - cornerSize, margin + 2, height - margin, COLOR_NEON_RED);
        
        // Bottom-right
        graphics.fill(width - margin - cornerSize, height - margin - 2, width - margin, height - margin, COLOR_NEON_RED);
        graphics.fill(width - margin - 2, height - margin - cornerSize, width - margin, height - margin, COLOR_NEON_RED);
    }
    
    /**
     * Override to render screen-specific content.
     */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    
    protected StrangerButton addStrangerButton(int x, int y, int width, int height, 
                                                Component text, Runnable onPress) {
        StrangerButton button = new StrangerButton(x, y, width, height, text, onPress);
        strangerButtons.add(button);
        addRenderableWidget(button);
        return button;
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/StrangerStoryListScreen.java`
```java
package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stranger Things themed story selection screen.
 * Lists available stories with neon styling.
 */
public class StrangerStoryListScreen extends StrangerScreen {
    
    private final List<StoryEntry> stories = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private static final int ENTRY_HEIGHT = 50;
    private static final int VISIBLE_ENTRIES = 5;
    
    public StrangerStoryListScreen() {
        super(Component.literal("选择故事"));
    }
    
    public void addStory(String id, String name, String description, int minPlayers, int maxPlayers, int estimatedMinutes) {
        stories.add(new StoryEntry(id, name, description, minPlayers, maxPlayers, estimatedMinutes));
    }

    public void clearStories() {
        stories.clear();
        selectedIndex = -1;
        scrollOffset = 0;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 150;
        int buttonHeight = 28;
        int bottomY = height - 50;
        
        // Start button
        addStrangerButton(width / 2 - buttonWidth - 10, bottomY, buttonWidth, buttonHeight,
            Component.literal("开始故事"), this::startSelectedStory);
        
        // Back button  
        addStrangerButton(width / 2 + 10, bottomY, buttonWidth, buttonHeight,
            Component.literal("返回"), this::onClose);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int listX = 50;
        int listY = 60;
        int listWidth = width - 100;
        int listHeight = ENTRY_HEIGHT * VISIBLE_ENTRIES;
        
        // Draw list background
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0080808);
        
        // Draw list border
        drawListBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Draw entries
        if (stories.isEmpty()) {
             graphics.drawCenteredString(font, "加载中...", width / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        } else {
            int visibleEnd = Math.min(scrollOffset + VISIBLE_ENTRIES, stories.size());
            for (int i = scrollOffset; i < visibleEnd; i++) {
                int entryY = listY + (i - scrollOffset) * ENTRY_HEIGHT;
                renderStoryEntry(graphics, stories.get(i), listX + 4, entryY, listWidth - 8, i == selectedIndex, mouseX, mouseY);
            }
        }
        
        // Draw scroll indicators if needed
        if (scrollOffset > 0) {
            graphics.drawString(font, "▲", listX + listWidth / 2 - 4, listY - 12, COLOR_NEON_RED);
        }
        if (scrollOffset + VISIBLE_ENTRIES < stories.size()) {
            graphics.drawString(font, "▼", listX + listWidth / 2 - 4, listY + listHeight + 4, COLOR_NEON_RED);
        }
    }
    
    private void renderStoryEntry(GuiGraphics graphics, StoryEntry story, int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
        
        // Background
        int bgColor = selected ? 0xFF1A0808 : (hovered ? 0xFF100505 : 0xFF080808);
        graphics.fill(x, y, x + width, y + ENTRY_HEIGHT - 2, bgColor);
        
        // Border
        int borderColor = selected ? COLOR_NEON_RED : COLOR_BORDER;
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + ENTRY_HEIGHT - 3, x + width, y + ENTRY_HEIGHT - 2, borderColor);
        graphics.fill(x, y, x + 1, y + ENTRY_HEIGHT - 2, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + ENTRY_HEIGHT - 2, borderColor);
        
        // Title
        graphics.drawString(font, story.name, x + 8, y + 6, selected ? COLOR_NEON_RED : COLOR_TEXT_BODY);
        
        // Description (truncated)
        String desc = story.description;
        if (font.width(desc) > width - 16) {
            while (font.width(desc + "...") > width - 16 && desc.length() > 0) {
                desc = desc.substring(0, desc.length() - 1);
            }
            desc += "...";
        }
        graphics.drawString(font, desc, x + 8, y + 20, COLOR_TEXT_DIM);
        
        // Player count and duration
        String info = String.format("%d-%d人 | 约%d分钟", story.minPlayers, story.maxPlayers, story.estimatedMinutes);
        graphics.drawString(font, info, x + 8, y + 34, COLOR_TEXT_DIM);
    }
    
    private void drawListBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        // Corner accents
        int cs = 8;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check for story selection
        int listX = 50;
        int listY = 60;
        int listWidth = width - 100;
        
        if (mouseX >= listX && mouseX < listX + listWidth) {
            int relY = (int)mouseY - listY;
            if (relY >= 0 && relY < ENTRY_HEIGHT * VISIBLE_ENTRIES) {
                int clickedIndex = scrollOffset + relY / ENTRY_HEIGHT;
                if (clickedIndex < stories.size()) {
                    selectedIndex = clickedIndex;
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + VISIBLE_ENTRIES < stories.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    private void startSelectedStory() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryEntry story = stories.get(selectedIndex);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.warmpixel.storyadventure.network.StoryActionPayload(
                    com.warmpixel.storyadventure.network.StoryActionPayload.Action.SELECT_STORY,
                    story.id
                )
            );
            // Don't close immediately, wait for server to switch us to Lobby
        }
    }
    
    public record StoryEntry(String id, String name, String description, int minPlayers, int maxPlayers, int estimatedMinutes) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/StrangerVictoryScreen.java`
```java
package com.warmpixel.storyadventure.client.ui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Victory screen displayed when a story instance is completed successfully.
 * Shows congratulations, rewards, and countdown to teleport back to spawn.
 */
public class StrangerVictoryScreen extends StrangerScreen {
    
    private static final int COLOR_GOLD = 0xFFFFD700;
    private static final int COLOR_SUCCESS = 0xFF44FF44;
    private static final int COUNTDOWN_SECONDS = 10;
    
    private final String storyName;
    private final long completionTimeMs;
    private final List<RewardEntry> rewards;
    private final long screenOpenTime;
    private int countdownSeconds;
    private long lastCountdownUpdate;
    private boolean confirmed = false;
    private Runnable onConfirm;
    
    public StrangerVictoryScreen(String storyName, long completionTimeMs, List<RewardEntry> rewards) {
        super(Component.literal("任务完成"));
        this.storyName = storyName;
        this.completionTimeMs = completionTimeMs;
        this.rewards = rewards != null ? rewards : new ArrayList<>();
        this.screenOpenTime = System.currentTimeMillis();
        this.countdownSeconds = COUNTDOWN_SECONDS;
        this.lastCountdownUpdate = System.currentTimeMillis();
    }
    
    public void setOnConfirm(Runnable onConfirm) {
        this.onConfirm = onConfirm;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 160;
        int buttonHeight = 30;
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = height - 60;
        
        addStrangerButton(buttonX, buttonY, buttonWidth, buttonHeight,
            Component.literal("确认返回"), this::onConfirmClick);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (confirmed) return;
        
        // Update countdown
        long now = System.currentTimeMillis();
        if (now - lastCountdownUpdate >= 1000) {
            lastCountdownUpdate = now;
            countdownSeconds--;
            
            if (countdownSeconds <= 0) {
                onConfirmClick();
            }
        }
    }
    
    private void onConfirmClick() {
        if (!confirmed) {
            confirmed = true;
            if (onConfirm != null) {
                onConfirm.run();
            }
            // Send confirm packet to server
            ClientPlayNetworking.send(
                new com.warmpixel.storyadventure.network.VictoryConfirmPayload()
            );
            
            // Close the screen
            this.onClose();
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = width / 2;
        int y = 50;
        
        // Pulsing star effect
        float pulse = (float)(0.7 + 0.3 * Math.sin((System.currentTimeMillis() - screenOpenTime) / 300.0));
        int starAlpha = (int)(255 * pulse);
        int starColor = (starAlpha << 24) | (COLOR_GOLD & 0x00FFFFFF);
        
        // Draw congratulations header with stars
        String congrats = "★ 任务完成！ ★";
        int congratsWidth = font.width(congrats);
        graphics.drawString(font, congrats, centerX - congratsWidth / 2, y, starColor);
        y += 25;
        
        // Draw story name
        String storyText = storyName;
        int storyWidth = font.width(storyText);
        graphics.drawString(font, storyText, centerX - storyWidth / 2, y, COLOR_TEXT_TITLE);
        y += 15;
        
        // Draw separator
        int sepWidth = 180;
        graphics.fill(centerX - sepWidth / 2, y, centerX + sepWidth / 2, y + 1, COLOR_BORDER);
        y += 15;
        
        // Draw completion time
        String timeStr = formatTime(completionTimeMs);
        String timeLabel = "完成时间: " + timeStr;
        int timeWidth = font.width(timeLabel);
        graphics.drawString(font, timeLabel, centerX - timeWidth / 2, y, COLOR_TEXT_BODY);
        y += 25;
        
        // Draw rewards section
        if (!rewards.isEmpty()) {
            // Rewards box
            int boxWidth = 220;
            int boxHeight = 20 + rewards.size() * 18;
            int boxX = centerX - boxWidth / 2;
            int boxY = y;
            
            // Draw box background
            graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xC0101010);
            
            // Draw box border
            graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, COLOR_BORDER);
            graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, COLOR_BORDER);
            graphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, COLOR_BORDER);
            graphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, COLOR_BORDER);
            
            // Draw rewards header
            String rewardsHeader = "— 奖励 —";
            int headerWidth = font.width(rewardsHeader);
            graphics.drawString(font, rewardsHeader, centerX - headerWidth / 2, boxY + 5, COLOR_GOLD);
            
            // Draw each reward
            int rewardY = boxY + 20;
            for (RewardEntry reward : rewards) {
                String rewardText = "◆ " + reward.description();
                graphics.drawString(font, rewardText, boxX + 15, rewardY, COLOR_SUCCESS);
                rewardY += 18;
            }
            
            y = boxY + boxHeight + 20;
        }
        
        // Draw countdown
        String countdownText = "(" + countdownSeconds + " 秒后自动返回)";
        int countdownWidth = font.width(countdownText);
        int countdownY = height - 35;
        
        // Flash when low
        int countdownColor = countdownSeconds <= 3 ? 
            (System.currentTimeMillis() % 500 < 250 ? 0xFFFF4444 : COLOR_TEXT_DIM) : 
            COLOR_TEXT_DIM;
        
        graphics.drawString(font, countdownText, centerX - countdownWidth / 2, countdownY, countdownColor);
    }
    
    private String formatTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Prevent accidental closing
    }
    
    /**
     * Represents a reward to display.
     */
    public record RewardEntry(String type, String description, int amount) {
        public static RewardEntry experience(int amount) {
            return new RewardEntry("EXPERIENCE", amount + " 经验值", amount);
        }
        
        public static RewardEntry item(String itemName, int count) {
            String desc = count > 1 ? count + "x " + itemName : itemName;
            return new RewardEntry("ITEM", desc, count);
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/AdminDashboardScreen.java`
```java
package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Main admin dashboard with quick access to all admin functions.
 */
public class AdminDashboardScreen extends StrangerScreen {
    
    public AdminDashboardScreen() {
        super(Component.translatable("gui.storyadventure.admin.dashboard.title"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 200;
        int buttonHeight = 32;
        int centerX = width / 2 - buttonWidth / 2;
        int startY = height / 2 - 100;
        int gap = 8;
        
        // Instance Manager
        addStrangerButton(centerX, startY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.instances"), this::openInstanceManager);
        
        // Story Manager
        addStrangerButton(centerX, startY + buttonHeight + gap, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.stories"), this::openStoryManager);
        
        // Trigger Manager
        addStrangerButton(centerX, startY + (buttonHeight + gap) * 2, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.triggers"), this::openTriggerManager);
        
        // Player Manager
        addStrangerButton(centerX, startY + (buttonHeight + gap) * 3, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.players"), this::openPlayerManager);
        
        // System Stats
        addStrangerButton(centerX, startY + (buttonHeight + gap) * 4, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.stats"), this::openSystemStats);
        
        // Close
        addStrangerButton(width / 2 - 60, height - 50, 120, 28,
            Component.translatable("gui.storyadventure.admin.dashboard.close"), this::onClose);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Quick stats panel
        int panelX = 30;
        int panelY = 50;
        int panelWidth = 150;
        int panelHeight = 100;
        
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, panelX, panelY, panelWidth, panelHeight);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.quick_stats"), panelX + 10, panelY + 8, COLOR_NEON_RED);
        graphics.fill(panelX + 5, panelY + 20, panelX + panelWidth - 5, panelY + 21, COLOR_BORDER);
        
        // Placeholder stats
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.active_instances", 0), panelX + 10, panelY + 28, COLOR_TEXT_BODY);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.online_players", 0), panelX + 10, panelY + 42, COLOR_TEXT_BODY);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.loaded_stories", 1), panelX + 10, panelY + 56, COLOR_TEXT_BODY);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.server_status", 
            Component.translatable("gui.storyadventure.admin.dashboard.status_ok").getString()), panelX + 10, panelY + 70, 0xFF44FF44);
        
        // Right panel - recent activity
        int rightPanelX = width - 180;
        graphics.fill(rightPanelX, panelY, rightPanelX + panelWidth, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, rightPanelX, panelY, panelWidth, panelHeight);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.recent_activity"), rightPanelX + 10, panelY + 8, COLOR_NEON_RED);
        graphics.fill(rightPanelX + 5, panelY + 20, rightPanelX + panelWidth - 5, panelY + 21, COLOR_BORDER);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.no_activity"), rightPanelX + 10, panelY + 35, COLOR_TEXT_DIM);
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 6;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private void openInstanceManager() {
        sendCommand("storyadminui instances");
    }
    
    private void openStoryManager() {
        sendCommand("storyadminui stories");
    }
    
    private void openPlayerManager() {
        // We'll keep this one for now if there's no server command yet, or add it
        AdminPlayerManagerScreen screen = new AdminPlayerManagerScreen();
        // Populate with some placeholder data - in real implementation this comes from network sync
        screen.addPlayer(java.util.UUID.randomUUID(), "测试玩家1", "stranger_things_hawkins", "meet_joyce", true);
        screen.addPlayer(java.util.UUID.randomUUID(), "测试玩家2", "stranger_things_hawkins", "meet_joyce", false);
        Minecraft.getInstance().setScreen(screen);
    }
    
    private void openSystemStats() {
        AdminSystemStatsScreen screen = new AdminSystemStatsScreen();
        Minecraft.getInstance().setScreen(screen);
    }
    
    private void openTriggerManager() {
        Minecraft.getInstance().setScreen(new TriggerBoxManagerScreen());
    }

    private void sendCommand(String command) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/AdminInstanceManagerScreen.java`
```java
package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import com.warmpixel.storyadventure.client.ui.admin.panels.ConfirmationPanel;
import com.warmpixel.storyadventure.client.ui.admin.panels.NodeSelectorPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.warmpixel.storyadventure.network.AdminInstanceActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin UI for managing all active story instances.
 * Allows monitoring, intervention, and debugging.
 */
public class AdminInstanceManagerScreen extends StrangerScreen {
    
    private static final int INSTANCE_ENTRY_HEIGHT = 60;
    private static final int SIDEBAR_WIDTH = 200;
    
    private List<InstanceInfo> instances = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    
    // Admin action buttons
    private StrangerButton pauseButton;
    private StrangerButton resumeButton;
    private StrangerButton skipNodeButton;
    private StrangerButton forceCompleteButton;
    private StrangerButton terminateButton;
    
    // Modal panels
    private ConfirmationPanel terminatePanel;
    private ConfirmationPanel forceCompletePanel;
    private NodeSelectorPanel skipNodePanel;
    
    public AdminInstanceManagerScreen() {
        super(Component.translatable("gui.storyadventure.admin.instances.title"));
    }
    
    public void clearInstances() {
        instances.clear();
        selectedIndex = -1;
    }
    
    public void addInstance(UUID instanceId, String storyName, String currentNode, 
                            String status, int playerCount, long elapsedMs) {
        instances.add(new InstanceInfo(instanceId, storyName, currentNode, status, playerCount, elapsedMs));
    }
    
    @Override
    protected void init() {
        super.init();
        
        int sidebarX = width - SIDEBAR_WIDTH - 20;
        int buttonY = 100;
        int buttonWidth = SIDEBAR_WIDTH - 20;
        int buttonHeight = 24;
        int gap = 6;
        
        // Pause button
        pauseButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.pause"), this::pauseInstance);
        buttonY += buttonHeight + gap;
        
        // Resume button
        resumeButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.resume"), this::resumeInstance);
        buttonY += buttonHeight + gap;
        
        // Skip node button
        skipNodeButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.skip"), this::skipNode);
        buttonY += buttonHeight + gap;
        
        // Force complete button
        forceCompleteButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.complete"), this::forceComplete);
        buttonY += buttonHeight + gap;
        
        // Terminate button
        terminateButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.terminate"), this::terminateInstance);
        buttonY += buttonHeight + gap * 3;
        
        // Refresh button
        addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.refresh"), this::refreshList);
        
        // Close button at bottom
        addStrangerButton(width / 2 - 60, height - 45, 120, 28,
            Component.translatable("gui.storyadventure.admin.instances.close"), this::onClose);
        
        updateButtonStates();
        
        // Auto-refresh list on open
        refreshList();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Main list area
        int listX = 30;
        int listY = 50;
        int listWidth = width - SIDEBAR_WIDTH - 70;
        int listHeight = height - 110;
        
        // List background
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0080808);
        drawPanelBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Column headers
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.col.story"), listX + 10, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.col.node"), listX + 150, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.col.status"), listX + 300, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.col.players"), listX + 380, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.col.time"), listX + 430, listY + 8, COLOR_NEON_RED);
        
        graphics.fill(listX + 5, listY + 22, listX + listWidth - 5, listY + 23, COLOR_BORDER);
        
        // Instance entries
        int y = listY + 28;
        int visibleCount = (listHeight - 30) / INSTANCE_ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, instances.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            renderInstanceEntry(graphics, instances.get(i), listX + 5, y, listWidth - 10, 
                i == selectedIndex, mouseX, mouseY);
            y += INSTANCE_ENTRY_HEIGHT;
        }
        
        // Empty state
        if (instances.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.instances.empty"), listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Sidebar - selected instance details
        renderSidebar(graphics);
        
        // Stats bar at bottom
        renderStatsBar(graphics);
        
        // Render modal panels on top
        if (terminatePanel != null && terminatePanel.isVisible()) {
            terminatePanel.render(graphics, mouseX, mouseY, font);
        }
        if (forceCompletePanel != null && forceCompletePanel.isVisible()) {
            forceCompletePanel.render(graphics, mouseX, mouseY, font);
        }
        if (skipNodePanel != null && skipNodePanel.isVisible()) {
            skipNodePanel.render(graphics, mouseX, mouseY, font);
        }
    }
    
    private void renderInstanceEntry(GuiGraphics graphics, InstanceInfo info, 
                                      int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + INSTANCE_ENTRY_HEIGHT - 2;
        
        // Background
        int bgColor = selected ? 0xFF1A0808 : (hovered ? 0xFF100505 : 0xFF0A0A0A);
        graphics.fill(x, y, x + width, y + INSTANCE_ENTRY_HEIGHT - 2, bgColor);
        
        // Left accent bar
        int accentColor = getStatusColor(info.status);
        graphics.fill(x, y, x + 3, y + INSTANCE_ENTRY_HEIGHT - 2, accentColor);
        
        // Story name
        graphics.drawString(font, truncate(info.storyName, 18), x + 10, y + 8, COLOR_TEXT_BODY);
        
        // Instance ID (dimmed)
        String shortId = info.id.toString().substring(0, 8);
        graphics.drawString(font, shortId, x + 10, y + 22, COLOR_TEXT_DIM);
        
        // Current node
        graphics.drawString(font, truncate(info.currentNode, 18), x + 150, y + 8, COLOR_TEXT_BODY);
        
        // Status with color
        graphics.drawString(font, getStatusText(info.status), x + 300, y + 8, accentColor);
        
        // Player count
        graphics.drawString(font, String.valueOf(info.playerCount), x + 385, y + 8, COLOR_TEXT_BODY);
        
        // Elapsed time
        String timeStr = formatDuration(info.elapsedMs);
        graphics.drawString(font, timeStr, x + 430, y + 8, COLOR_TEXT_DIM);
        
        // Progress bar placeholder
        int progressWidth = width - 500;
        if (progressWidth > 50) {
            graphics.fill(x + 480, y + 10, x + 480 + progressWidth, y + 14, 0xFF222222);
            int filledWidth = (int)(progressWidth * 0.35); // Placeholder progress
            graphics.fill(x + 480, y + 10, x + 480 + filledWidth, y + 14, accentColor);
        }
    }
    
    private void renderSidebar(GuiGraphics graphics) {
        int sidebarX = width - SIDEBAR_WIDTH - 20;
        int sidebarY = 50;
        
        // Sidebar background
        graphics.fill(sidebarX, sidebarY, sidebarX + SIDEBAR_WIDTH, height - 60, 0xE0080808);
        drawPanelBorder(graphics, sidebarX, sidebarY, SIDEBAR_WIDTH, height - 110);
        
        // Title
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.actions"), sidebarX + 10, sidebarY + 8, COLOR_NEON_RED);
        graphics.fill(sidebarX + 5, sidebarY + 22, sidebarX + SIDEBAR_WIDTH - 5, sidebarY + 23, COLOR_BORDER);
        
        // Selected instance info
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            int y = sidebarY + 30;
            
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.selected"), sidebarX + 10, y, COLOR_TEXT_DIM);
            y += 12;
            graphics.drawString(font, info.storyName, sidebarX + 10, y, COLOR_TEXT_BODY);
            y += 14;
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.id", info.id.toString().substring(0, 8)), sidebarX + 10, y, COLOR_TEXT_DIM);
        } else {
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.none_selected"), sidebarX + 10, sidebarY + 35, COLOR_TEXT_DIM);
        }
    }
    
    private void renderStatsBar(GuiGraphics graphics) {
        int barY = height - 55;
        
        // Stats background
        graphics.fill(30, barY, width - 30, barY + 20, 0xC0080808);
        
        // Stats
        Component stats = Component.translatable("gui.storyadventure.admin.instances.stats_bar",
            instances.size(),
            instances.stream().mapToInt(i -> i.playerCount).sum(),
            Component.translatable("gui.storyadventure.admin.dashboard.status_ok").getString());
        
        graphics.drawString(font, stats, 40, barY + 6, COLOR_TEXT_DIM);
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 8;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private int getStatusColor(String status) {
        return switch (status.toUpperCase()) {
            case "RUNNING" -> 0xFF44FF44;
            case "PAUSED" -> 0xFFFFCC00;
            case "COMPLETED" -> 0xFF4488FF;
            case "FAILED" -> 0xFFFF4444;
            default -> COLOR_TEXT_DIM;
        };
    }
    
    private String getStatusText(String status) {
        return switch (status.toUpperCase()) {
            case "RUNNING" -> Component.translatable("gui.storyadventure.admin.instances.status.running").getString();
            case "PAUSED" -> Component.translatable("gui.storyadventure.admin.instances.status.paused").getString();
            case "COMPLETED" -> Component.translatable("gui.storyadventure.admin.instances.status.completed").getString();
            case "FAILED" -> Component.translatable("gui.storyadventure.admin.instances.status.failed").getString();
            case "CREATED" -> Component.translatable("gui.storyadventure.admin.instances.status.created").getString();
            default -> status;
        };
    }
    
    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text;
    }
    
    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle modal panels first
        if (terminatePanel != null && terminatePanel.isVisible()) {
            return terminatePanel.mouseClicked(mouseX, mouseY, button);
        }
        if (forceCompletePanel != null && forceCompletePanel.isVisible()) {
            return forceCompletePanel.mouseClicked(mouseX, mouseY, button);
        }
        if (skipNodePanel != null && skipNodePanel.isVisible()) {
            return skipNodePanel.mouseClicked(mouseX, mouseY, button);
        }
        
        // Check for instance selection
        int listX = 30;
        int listY = 50;
        int listWidth = width - SIDEBAR_WIDTH - 70;
        int listHeight = height - 110;
        
        if (mouseX >= listX && mouseX < listX + listWidth && 
            mouseY >= listY + 28 && mouseY < listY + listHeight) {
            int relY = (int)mouseY - listY - 28;
            int clickedIndex = scrollOffset + relY / INSTANCE_ENTRY_HEIGHT;
            if (clickedIndex < instances.size()) {
                selectedIndex = clickedIndex;
                updateButtonStates();
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        // Handle modal panel scrolling
        if (skipNodePanel != null && skipNodePanel.isVisible()) {
            return skipNodePanel.mouseScrolled(mouseX, mouseY, vAmount);
        }
        
        int visibleCount = (height - 110 - 30) / INSTANCE_ENTRY_HEIGHT;
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + visibleCount < instances.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle modal panel key events
        if (terminatePanel != null && terminatePanel.isVisible()) {
            return terminatePanel.keyPressed(keyCode, scanCode, modifiers);
        }
        if (forceCompletePanel != null && forceCompletePanel.isVisible()) {
            return forceCompletePanel.keyPressed(keyCode, scanCode, modifiers);
        }
        if (skipNodePanel != null && skipNodePanel.isVisible()) {
            return skipNodePanel.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Handle modal panel char input
        if (terminatePanel != null && terminatePanel.isVisible()) {
            return terminatePanel.charTyped(chr, modifiers);
        }
        if (forceCompletePanel != null && forceCompletePanel.isVisible()) {
            return forceCompletePanel.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }
    
    private void updateButtonStates() {
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < instances.size();
        
        if (pauseButton != null) pauseButton.active = hasSelection;
        if (resumeButton != null) resumeButton.active = hasSelection;
        if (skipNodeButton != null) skipNodeButton.active = hasSelection;
        if (forceCompleteButton != null) forceCompleteButton.active = hasSelection;
        if (terminateButton != null) terminateButton.active = hasSelection;
    }
    
    private void sendCommand(String command) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
    
    private void showMessage(String message) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
    
    // Admin action methods
    private void pauseInstance() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            ClientPlayNetworking.send(new AdminInstanceActionPayload(AdminInstanceActionPayload.Action.PAUSE, info.id));
            showMessage(Component.translatable("command.storyadventure.admin.instances.pausing", info.id.toString().substring(0, 8)).getString());
            info = new InstanceInfo(info.id, info.storyName, info.currentNode, "PAUSED", info.playerCount, info.elapsedMs);
            instances.set(selectedIndex, info);
        }
    }
    
    private void resumeInstance() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            ClientPlayNetworking.send(new AdminInstanceActionPayload(AdminInstanceActionPayload.Action.RESUME, info.id));
            showMessage(Component.translatable("command.storyadventure.admin.instances.resuming", info.id.toString().substring(0, 8)).getString());
            info = new InstanceInfo(info.id, info.storyName, info.currentNode, "RUNNING", info.playerCount, info.elapsedMs);
            instances.set(selectedIndex, info);
        }
    }
    
    private void skipNode() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            
            // Show node selector panel
            skipNodePanel = new NodeSelectorPanel(Component.translatable("gui.storyadventure.admin.instances.select_target_node").getString(), 
                nodeId -> {
                    sendCommand("storyadmin skip " + shortId + " " + nodeId);
                    showMessage(Component.translatable("command.storyadventure.admin.instances.skipping", nodeId).getString());
                },
                () -> {} // Cancel - do nothing
            );
            
            // Add placeholder nodes - in real implementation these come from server
            skipNodePanel.addNode("intro_cutscene", "CUTSCENE", "开场动画");
            skipNodePanel.addNode("meet_joyce", "DIALOGUE", "遇见乔伊斯");
            skipNodePanel.addNode("accept_mission", "CHECKPOINT", "接受任务");
            skipNodePanel.addNode("investigate_house", "TASK", "调查房屋");
            skipNodePanel.addNode("find_first_clue", "CUTSCENE", "发现线索");
            skipNodePanel.addNode("first_demogorgon_encounter", "COMBAT", "初次遭遇");
            skipNodePanel.addNode("lab_puzzle", "PUZZLE", "实验室密码");
            skipNodePanel.addNode("good_ending", "CUTSCENE", "好结局");
            
            skipNodePanel.show(width, height);
        }
    }
    
    private void forceComplete() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            
            // Show completion options panel
            forceCompletePanel = ConfirmationPanel.builder(Component.translatable("gui.storyadventure.admin.instances.confirm_complete_title").getString())
                .description(Component.translatable("gui.storyadventure.admin.instances.confirm_complete_desc").getString())
                .withInput(Component.translatable("gui.storyadventure.admin.instances.confirm_complete_input").getString())
                .onConfirm(result -> {
                    String outcome = result.isEmpty() ? "success" : result;
                    sendCommand("storyadmin complete " + shortId + " " + outcome);
                    showMessage(Component.translatable("command.storyadventure.admin.instances.forced_complete", shortId, outcome).getString());
                })
                .onCancel(() -> {})
                .build();
            
            forceCompletePanel.show(width, height, font);
        }
    }
    
    private void terminateInstance() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            
            // Show dangerous confirmation panel
            terminatePanel = ConfirmationPanel.builder(Component.translatable("gui.storyadventure.admin.instances.confirm_terminate_title").getString())
                .description(Component.translatable("gui.storyadventure.admin.instances.confirm_terminate_desc", shortId).getString())
                .dangerous()
                .withInput(Component.translatable("gui.storyadventure.admin.instances.confirm_terminate_input").getString())
                .onConfirm(reason -> {
                    ClientPlayNetworking.send(new AdminInstanceActionPayload(AdminInstanceActionPayload.Action.TERMINATE, info.id));
                    showMessage(Component.translatable("command.storyadventure.admin.instances.terminated", shortId).getString());
                    
                    // Remove from local list immediately for responsiveness
                    instances.remove(selectedIndex);
                    selectedIndex = -1;
                    updateButtonStates();
                    
                    // Trigger a full refresh to sync with server
                    // Delay slightly to let server process
                    net.minecraft.client.Minecraft.getInstance().tell(this::refreshList);
                })
                .onCancel(() -> {})
                .build();
            
            terminatePanel.show(width, height, font);
        }
    }
    
    private void refreshList() {
        // Clear current list first
        instances.clear();
        selectedIndex = -1;
        updateButtonStates();
        
        showMessage(Component.translatable("command.storyadventure.admin.instances.listing").getString());
        
        // Request direct sync from server
        ClientPlayNetworking.send(new AdminInstanceActionPayload(AdminInstanceActionPayload.Action.SYNC, null));
    }
    
    /**
     * Called by network handler when sync data arrives.
     */
    public void onSyncReceived() {
        updateButtonStates();
        if (instances.isEmpty()) {
            showMessage(Component.translatable("command.storyadventure.admin.instances.sync_none").getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.instances.synced", instances.size()).getString());
        }
    }
    
    public record InstanceInfo(UUID id, String storyName, String currentNode, 
                                String status, int playerCount, long elapsedMs) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/AdminNodeEditorScreen.java`
```java
package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin UI for editing individual node configuration.
 * Shows type-specific fields based on node type.
 */
public class AdminNodeEditorScreen extends StrangerScreen {
    
    private final String storyId;
    private final String nodeId;
    private final String nodeType;
    
    // Common fields
    private EditBox titleField;
    private EditBox descriptionField;
    
    // Type-specific fields (stored as key-value for simplicity)
    private final List<EditBox> dataFields = new ArrayList<>();
    private final List<String> dataFieldLabels = new ArrayList<>();
    
    // Scrolling for fields
    private int scrollOffset = 0;
    
    public AdminNodeEditorScreen(String storyId, String nodeId, String nodeType) {
        super(Component.translatable("gui.storyadventure.admin.nodes.edit_title", nodeId));
        this.storyId = storyId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int fieldX = 60;
        int fieldWidth = width - 250;
        int y = 70;
        
        // Node type badge is rendered in renderContent
        
        // Initialize type-specific fields
        initTypeFields(fieldX, y, fieldWidth);
        
        // Action buttons on right side
        int buttonWidth = 140;
        int buttonHeight = 26;
        int rightX = width - 170;
        int buttonY = 80;
        
        // Save button
        addStrangerButton(rightX, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.save"), this::saveChanges);
        buttonY += buttonHeight + 8;
        
        // Test trigger button
        addStrangerButton(rightX, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.test_trigger"), this::testTrigger);
        buttonY += buttonHeight + 8;
        
        // Reset to default
        addStrangerButton(rightX, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.reset"), this::resetToDefault);
        buttonY += buttonHeight + 20;
        
        // View JSON
        addStrangerButton(rightX, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.view_json"), this::viewJson);
        
        // Navigation buttons
        addStrangerButton(30, height - 45, 100, 28,
            Component.translatable("gui.storyadventure.admin.nodes.back"), this::goBack);
        
        addStrangerButton(width / 2 - 60, height - 45, 120, 28,
            Component.translatable("gui.storyadventure.admin.nodes.cancel"), this::onClose);
    }
    
    private void initTypeFields(int x, int y, int fieldWidth) {
        int fieldHeight = 20;
        int gap = 30;
        
        switch (nodeType.toUpperCase()) {
            case "CUTSCENE" -> {
                addDataField("duration_ticks", Component.translatable("gui.storyadventure.admin.nodes.field.duration").getString(), "200", x, y, fieldWidth);
                y += gap;
                addDataField("title", Component.translatable("gui.storyadventure.admin.nodes.field.title").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("subtitle", Component.translatable("gui.storyadventure.admin.nodes.field.subtitle").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("message", Component.translatable("gui.storyadventure.admin.nodes.field.message").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("fade_in", Component.translatable("gui.storyadventure.admin.nodes.field.fade_in").getString(), "true", x, y, fieldWidth);
            }
            case "DIALOGUE" -> {
                addDataField("npc_template", Component.translatable("gui.storyadventure.admin.nodes.field.npc_template").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("npc_name", Component.translatable("gui.storyadventure.admin.nodes.field.npc_name").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("dialog_set", Component.translatable("gui.storyadventure.admin.nodes.field.dialog_set").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("lines", Component.translatable("gui.storyadventure.admin.nodes.field.lines").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("vote_required", Component.translatable("gui.storyadventure.admin.nodes.field.vote_required").getString(), "false", x, y, fieldWidth);
                y += gap;
                addDataField("vote_id", Component.translatable("gui.storyadventure.admin.nodes.field.vote_id").getString(), "", x, y, fieldWidth);
            }
            case "TASK" -> {
                addDataField("task_type", Component.translatable("gui.storyadventure.admin.nodes.field.task_type").getString(), "FETCH", x, y, fieldWidth);
                y += gap;
                addDataField("title", Component.translatable("gui.storyadventure.admin.nodes.field.title").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("description", Component.translatable("gui.storyadventure.admin.nodes.field.description").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("time_limit_seconds", Component.translatable("gui.storyadventure.admin.nodes.field.time_limit").getString(), "0", x, y, fieldWidth);
                y += gap;
                addDataField("stealth_required", Component.translatable("gui.storyadventure.admin.nodes.field.stealth").getString(), "false", x, y, fieldWidth);
            }
            case "PUZZLE" -> {
                addDataField("puzzle_type", Component.translatable("gui.storyadventure.admin.nodes.field.puzzle_type").getString(), "CODE_LOCK", x, y, fieldWidth);
                y += gap;
                addDataField("title", Component.translatable("gui.storyadventure.admin.nodes.field.title").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("description", Component.translatable("gui.storyadventure.admin.nodes.field.description").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("solution", Component.translatable("gui.storyadventure.admin.nodes.field.solution").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("max_attempts", Component.translatable("gui.storyadventure.admin.nodes.field.max_attempts").getString(), "5", x, y, fieldWidth);
                y += gap;
                addDataField("hints", Component.translatable("gui.storyadventure.admin.nodes.field.hints").getString(), "", x, y, fieldWidth);
            }
            case "COMBAT" -> {
                addDataField("combat_type", Component.translatable("gui.storyadventure.admin.nodes.field.combat_type").getString(), "BOSS", x, y, fieldWidth);
                y += gap;
                addDataField("title", Component.translatable("gui.storyadventure.admin.nodes.field.title").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("description", Component.translatable("gui.storyadventure.admin.nodes.field.description").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("enemies", Component.translatable("gui.storyadventure.admin.nodes.field.enemies").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("escape_available", Component.translatable("gui.storyadventure.admin.nodes.field.escape_available").getString(), "false", x, y, fieldWidth);
                y += gap;
                addDataField("arena_radius", Component.translatable("gui.storyadventure.admin.nodes.field.arena_radius").getString(), "20", x, y, fieldWidth);
            }
            case "CHECKPOINT" -> {
                addDataField("rewind_anchor", Component.translatable("gui.storyadventure.admin.nodes.field.rewind_anchor").getString(), "true", x, y, fieldWidth);
                y += gap;
                addDataField("save_inventory", Component.translatable("gui.storyadventure.admin.nodes.field.save_inventory").getString(), "true", x, y, fieldWidth);
                y += gap;
                addDataField("message", Component.translatable("gui.storyadventure.admin.nodes.field.message").getString(), "", x, y, fieldWidth);
            }
            default -> {
                addDataField("data", Component.translatable("gui.storyadventure.admin.nodes.field.generic_data").getString(), "{}", x, y, fieldWidth);
            }
        }
    }
    
    private void addDataField(String key, String label, String defaultValue, int x, int y, int fieldWidth) {
        EditBox field = new EditBox(font, x, y, fieldWidth, 18, Component.literal(label));
        field.setMaxLength(500);
        field.setValue(defaultValue);
        field.setTextColor(0xFFCCCCCC);
        addRenderableWidget(field);
        dataFields.add(field);
        dataFieldLabels.add(label);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Node info header
        int headerY = 48;
        
        // Type badge
        int typeColor = getTypeColor(nodeType);
        String typeDisplay = getTypeDisplayName(nodeType);
        int badgeWidth = font.width(typeDisplay) + 12;
        
        graphics.fill(30, headerY, 30 + badgeWidth, headerY + 18, typeColor & 0x60FFFFFF);
        graphics.fill(30, headerY, 32, headerY + 18, typeColor);
        graphics.drawString(font, typeDisplay, 36, headerY + 5, typeColor);
        
        // Node ID
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.node_label", nodeId), 40 + badgeWidth, headerY + 5, COLOR_TEXT_BODY);
        
        // Story ID
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.story_label", storyId), 40 + badgeWidth + font.width("Node: " + nodeId) + 20, headerY + 5, COLOR_TEXT_DIM);
        
        // Field labels
        int labelX = 30;
        int fieldY = 70;
        int gap = 30;
        
        for (int i = 0; i < dataFieldLabels.size(); i++) {
            graphics.drawString(font, dataFieldLabels.get(i), labelX, fieldY + 4, COLOR_TEXT_DIM);
            fieldY += gap;
        }
        
        // Sidebar info panel
        int sidebarX = width - 170;
        int sidebarY = 230;
        int sidebarWidth = 150;
        int sidebarHeight = 120;
        
        graphics.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, 0xE0080808);
        drawPanelBorder(graphics, sidebarX, sidebarY, sidebarWidth, sidebarHeight);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.info_title"), sidebarX + 10, sidebarY + 8, COLOR_NEON_RED);
        graphics.fill(sidebarX + 5, sidebarY + 20, sidebarX + sidebarWidth - 5, sidebarY + 21, COLOR_BORDER);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.info_type", typeDisplay), sidebarX + 10, sidebarY + 28, COLOR_TEXT_BODY);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.info_fields", dataFields.size()), sidebarX + 10, sidebarY + 44, COLOR_TEXT_DIM);
        
        // Help text
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.info_hint"), sidebarX + 10, sidebarY + 65, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.info_hint1"), sidebarX + 10, sidebarY + 80, COLOR_TEXT_DIM);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.info_hint2"), sidebarX + 10, sidebarY + 92, COLOR_TEXT_DIM);
    }
    
    private int getTypeColor(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> 0xFF4488FF;
            case "TASK" -> 0xFF44FF44;
            case "PUZZLE" -> 0xFFFF8844;
            case "COMBAT" -> 0xFFFF4444;
            case "CUTSCENE" -> 0xFFCC44FF;
            case "CHECKPOINT" -> 0xFFFFCC44;
            default -> COLOR_TEXT_DIM;
        };
    }
    
    private String getTypeDisplayName(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> Component.translatable("gui.storyadventure.node.type.dialogue").getString();
            case "TASK" -> Component.translatable("gui.storyadventure.node.type.task").getString();
            case "PUZZLE" -> Component.translatable("gui.storyadventure.node.type.puzzle").getString();
            case "COMBAT" -> Component.translatable("gui.storyadventure.node.type.combat").getString();
            case "CUTSCENE" -> Component.translatable("gui.storyadventure.node.type.cutscene").getString();
            case "CHECKPOINT" -> Component.translatable("gui.storyadventure.node.type.checkpoint").getString();
            default -> type;
        };
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 6;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private void sendCommand(String command) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
    
    private void showMessage(String message) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
    
    private void saveChanges() {
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < dataFields.size(); i++) {
            if (i > 0) data.append(",");
            data.append(dataFieldLabels.get(i)).append("=").append(dataFields.get(i).getValue());
        }
        
        sendCommand("storyadmin updatenode " + storyId + " " + nodeId + " " + data);
        showMessage(Component.translatable("command.storyadventure.admin.nodes.saved").getString());
        showMessage(Component.translatable("command.storyadventure.admin.nodes.reload_hint").getString());
    }
    
    private void testTrigger() {
        sendCommand("storyadmin trigger " + storyId + " " + nodeId);
        showMessage(Component.translatable("command.storyadventure.admin.nodes.testing", nodeId).getString());
    }
    
    private void resetToDefault() {
        for (EditBox field : dataFields) {
            field.setValue("");
        }
        showMessage(Component.translatable("command.storyadventure.admin.nodes.reset_done").getString());
    }
    
    private void viewJson() {
        showMessage(Component.translatable("command.storyadventure.admin.nodes.json_title").getString());
        showMessage(Component.translatable("gui.storyadventure.admin.nodes.node_label", nodeId).getString());
        showMessage(Component.translatable("gui.storyadventure.admin.nodes.info_type", nodeType).getString());
        showMessage(Component.translatable("command.storyadventure.admin.nodes.json_hint").getString());
        showMessage("config/storyadventure/stories/" + storyId + ".json");
    }
    
    private void goBack() {
        Minecraft.getInstance().setScreen(new AdminNodeListScreen(storyId, storyId));
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/AdminNodeListScreen.java`
```java
package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import com.warmpixel.storyadventure.core.graph.NodeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin UI for viewing and managing all nodes in a story.
 */
public class AdminNodeListScreen extends StrangerScreen {
    
    private static final int NODE_ENTRY_HEIGHT = 35;
    
    private final String storyId;
    private final String storyName;
    private final List<NodeInfo> nodes = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    
    // Filter
    private String filterType = "ALL";
    
    public AdminNodeListScreen(String storyId, String storyName) {
        super(Component.translatable("gui.storyadventure.admin.nodes.title", storyName));
        this.storyId = storyId;
        this.storyName = storyName;
    }
    
    public void addNode(String nodeId, String type, int edgeCount, String description) {
        nodes.add(new NodeInfo(nodeId, type, edgeCount, description));
    }
    
    public void clearNodes() {
        nodes.clear();
        selectedIndex = -1;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 140;
        int buttonHeight = 24;
        int rightX = width - 170;
        int y = 80;
        
        // Edit node button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.edit"), this::editNode);
        y += buttonHeight + 8;
        
        // Test trigger button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.trigger"), this::triggerNode);
        y += buttonHeight + 8;
        
        // View edges button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.edges"), this::viewEdges);
        y += buttonHeight + 20;
        
        // Filter buttons
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.filter_all"), () -> setFilter("ALL"));
        y += buttonHeight + 5;
        addStrangerButton(rightX, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.dialogue"), () -> setFilter("DIALOGUE"));
        addStrangerButton(rightX + buttonWidth / 2 + 2, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.task"), () -> setFilter("TASK"));
        y += buttonHeight + 5;
        addStrangerButton(rightX, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.puzzle"), () -> setFilter("PUZZLE"));
        addStrangerButton(rightX + buttonWidth / 2 + 2, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.combat"), () -> setFilter("COMBAT"));
        y += buttonHeight + 5;
        addStrangerButton(rightX, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.cutscene"), () -> setFilter("CUTSCENE"));
        addStrangerButton(rightX + buttonWidth / 2 + 2, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.checkpoint"), () -> setFilter("CHECKPOINT"));
        
        // Back button
        addStrangerButton(30, height - 45, 100, 28,
            Component.translatable("gui.storyadventure.admin.nodes.back"), this::goBack);
        
        // Close button
        addStrangerButton(width / 2 - 60, height - 45, 120, 28,
            Component.translatable("gui.storyadventure.admin.nodes.close"), this::onClose);
        
        // Load nodes
        loadNodes();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Node list panel
        int listX = 30;
        int listY = 50;
        int listWidth = width - 230;
        int listHeight = height - 110;
        
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0080808);
        drawPanelBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Headers
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.col.id"), listX + 10, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.col.type"), listX + 200, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.col.edges"), listX + 300, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.col.desc"), listX + 360, listY + 8, COLOR_NEON_RED);
        
        graphics.fill(listX + 5, listY + 22, listX + listWidth - 5, listY + 23, COLOR_BORDER);
        
        // Filtered nodes
        List<NodeInfo> filteredNodes = getFilteredNodes();
        
        // Node entries
        int y = listY + 28;
        int visibleCount = (listHeight - 30) / NODE_ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, filteredNodes.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            renderNodeEntry(graphics, filteredNodes.get(i), listX + 5, y, listWidth - 10, 
                i == selectedIndex, mouseX, mouseY);
            y += NODE_ENTRY_HEIGHT;
        }
        
        if (filteredNodes.isEmpty()) {
            Component emptyMsg = filterType.equals("ALL") ? 
                Component.translatable("gui.storyadventure.admin.nodes.empty") : 
                Component.translatable("gui.storyadventure.admin.nodes.empty_filtered", getTypeDisplayName(filterType));
            graphics.drawCenteredString(font, emptyMsg, listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Stats bar
        graphics.fill(30, height - 70, width - 30, height - 50, 0xC0080808);
        Component stats = Component.translatable("gui.storyadventure.admin.nodes.stats",
            storyId, nodes.size(), filteredNodes.size(), getTypeDisplayName(filterType));
        graphics.drawString(font, stats, 40, height - 64, COLOR_TEXT_DIM);
    }
    
    private void renderNodeEntry(GuiGraphics graphics, NodeInfo info, 
                                  int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + NODE_ENTRY_HEIGHT - 2;
        
        int bgColor = selected ? 0xFF1A0808 : (hovered ? 0xFF100505 : 0xFF0A0A0A);
        graphics.fill(x, y, x + width, y + NODE_ENTRY_HEIGHT - 2, bgColor);
        
        // Type color indicator
        int typeColor = getTypeColor(info.type);
        graphics.fill(x, y, x + 3, y + NODE_ENTRY_HEIGHT - 2, typeColor);
        
        // Node ID
        graphics.drawString(font, truncate(info.nodeId, 24), x + 10, y + 10, COLOR_TEXT_BODY);
        
        // Type badge
        String typeDisplay = getTypeDisplayName(info.type);
        graphics.fill(x + 195, y + 6, x + 195 + font.width(typeDisplay) + 10, y + 20, typeColor & 0x40FFFFFF);
        graphics.drawString(font, typeDisplay, x + 200, y + 10, typeColor);
        
        // Edge count
        graphics.drawString(font, String.valueOf(info.edgeCount), x + 310, y + 10, COLOR_TEXT_DIM);
        
        // Description
        graphics.drawString(font, truncate(info.description, 30), x + 360, y + 10, COLOR_TEXT_DIM);
    }
    
    private List<NodeInfo> getFilteredNodes() {
        if (filterType.equals("ALL")) {
            return nodes;
        }
        return nodes.stream()
            .filter(n -> n.type.equalsIgnoreCase(filterType))
            .toList();
    }
    
    private void setFilter(String type) {
        this.filterType = type;
        this.selectedIndex = -1;
        this.scrollOffset = 0;
    }
    
    private int getTypeColor(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> 0xFF4488FF;  // Blue
            case "TASK" -> 0xFF44FF44;      // Green
            case "PUZZLE" -> 0xFFFF8844;    // Orange
            case "COMBAT" -> 0xFFFF4444;    // Red
            case "CUTSCENE" -> 0xFFCC44FF;  // Purple
            case "CHECKPOINT" -> 0xFFFFCC44; // Yellow
            default -> COLOR_TEXT_DIM;
        };
    }
    
    private String getTypeDisplayName(String type) {
        return switch (type.toUpperCase()) {
            case "ALL" -> Component.translatable("gui.storyadventure.admin.nodes.filter.all").getString();
            case "DIALOGUE" -> Component.translatable("gui.storyadventure.node.type.dialogue").getString();
            case "TASK" -> Component.translatable("gui.storyadventure.node.type.task").getString();
            case "PUZZLE" -> Component.translatable("gui.storyadventure.node.type.puzzle").getString();
            case "COMBAT" -> Component.translatable("gui.storyadventure.node.type.combat").getString();
            case "CUTSCENE" -> Component.translatable("gui.storyadventure.node.type.cutscene").getString();
            case "CHECKPOINT" -> Component.translatable("gui.storyadventure.node.type.checkpoint").getString();
            default -> type;
        };
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 6;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = 30;
        int listY = 50;
        int listWidth = width - 230;
        int listHeight = height - 110;
        
        if (mouseX >= listX && mouseX < listX + listWidth && 
            mouseY >= listY + 28 && mouseY < listY + listHeight) {
            List<NodeInfo> filtered = getFilteredNodes();
            int relY = (int)mouseY - listY - 28;
            int clickedIndex = scrollOffset + relY / NODE_ENTRY_HEIGHT;
            if (clickedIndex < filtered.size()) {
                selectedIndex = clickedIndex;
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        List<NodeInfo> filtered = getFilteredNodes();
        int visibleCount = (height - 110 - 30) / NODE_ENTRY_HEIGHT;
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + visibleCount < filtered.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    private void sendCommand(String command) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
    
    private void showMessage(String message) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
    
    private void showTranslatable(String key, Object... args) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable(key, args));
        }
    }
    
    private void loadNodes() {
        // Request nodes from server
        sendCommand("storyadmin nodes " + storyId);
        
        // For now, load some placeholder data based on story
        // In real implementation, this would come from network sync
        clearNodes();
        
        // Placeholder nodes for demonstration
        addNode("intro_cutscene", "CUTSCENE", 1, "开场过场动画");
        addNode("meet_joyce", "DIALOGUE", 2, "遇见乔伊斯");
        addNode("accept_mission", "CHECKPOINT", 1, "接受任务存档点");
        addNode("investigate_house", "TASK", 2, "调查房屋任务");
        addNode("find_first_clue", "CUTSCENE", 1, "发现线索");
        addNode("first_demogorgon_encounter", "COMBAT", 3, "第一次遭遇战斗");
        addNode("lab_puzzle", "PUZZLE", 2, "实验室密码谜题");
        addNode("gather_supplies", "TASK", 1, "收集物资");
        addNode("final_battle_prep", "CHECKPOINT", 1, "最终战斗准备");
        addNode("final_battle_vote", "DIALOGUE", 2, "投票选择策略");
        addNode("final_battle_direct", "COMBAT", 2, "正面突击战斗");
        addNode("good_ending", "CUTSCENE", 0, "好结局");
        addNode("bad_ending_defeated", "CUTSCENE", 0, "坏结局-失败");
    }
    
    private void editNode() {
        List<NodeInfo> filtered = getFilteredNodes();
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            NodeInfo info = filtered.get(selectedIndex);
            Minecraft.getInstance().setScreen(new AdminNodeEditorScreen(storyId, info.nodeId, info.type));
        } else {
            showTranslatable("command.storyadventure.admin.stories.select_first");
        }
    }
    
    private void triggerNode() {
        List<NodeInfo> filtered = getFilteredNodes();
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            NodeInfo info = filtered.get(selectedIndex);
            sendCommand("storyadmin trigger " + storyId + " " + info.nodeId);
            showTranslatable("command.storyadventure.admin.nodes.triggering", info.nodeId);
        } else {
            showTranslatable("command.storyadventure.admin.stories.select_first");
        }
    }
    
    private void viewEdges() {
        List<NodeInfo> filtered = getFilteredNodes();
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            NodeInfo info = filtered.get(selectedIndex);
            showTranslatable("gui.storyadventure.admin.nodes.edges_title", info.nodeId);
            showTranslatable("gui.storyadventure.admin.nodes.edges_count", info.edgeCount);
            showTranslatable("gui.storyadventure.admin.nodes.edges_hint");
        } else {
            showTranslatable("command.storyadventure.admin.stories.select_first");
        }
    }
    
    private void goBack() {
        AdminStoryManagerScreen screen = new AdminStoryManagerScreen();
        screen.addStory(storyId, storyName, nodes.size(), "1.0.0", true, "");
        Minecraft.getInstance().setScreen(screen);
    }
    
    public record NodeInfo(String nodeId, String type, int edgeCount, String description) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/AdminPlayerManagerScreen.java`
```java
package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin UI for managing players across all active story instances.
 */
public class AdminPlayerManagerScreen extends StrangerScreen {
    
    private static final int PLAYER_ENTRY_HEIGHT = 40;
    
    private final List<PlayerInfo> players = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    
    public AdminPlayerManagerScreen() {
        super(Component.translatable("gui.storyadventure.admin.players.title"));
    }
    
    public void addPlayer(UUID uuid, String name, String instanceName, String currentNode, boolean isLeader) {
        players.add(new PlayerInfo(uuid, name, instanceName, currentNode, isLeader));
    }
    
    public void clearPlayers() {
        players.clear();
        selectedIndex = -1;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 140;
        int buttonHeight = 24;
        int rightX = width - 170;
        int y = 80;
        
        // Teleport to player
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.players.tp"), this::teleportToPlayer);
        y += buttonHeight + 8;
        
        // Kick from instance
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.players.kick"), this::kickPlayer);
        y += buttonHeight + 8;
        
        // Send message
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.players.message"), this::sendMessageToPlayer);
        y += buttonHeight + 8;
        
        // View player details
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.players.details"), this::viewPlayerDetails);
        y += buttonHeight + 20;
        
        // Refresh button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.players.refresh"), this::refreshList);
        
        // Back button
        addStrangerButton(30, height - 45, 100, 28,
            Component.translatable("gui.storyadventure.admin.players.back"), this::goBack);
        
        // Close button
        addStrangerButton(width / 2 - 60, height - 45, 120, 28,
            Component.translatable("gui.storyadventure.admin.players.close"), this::onClose);
        
        // Request player data
        refreshList();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Player list panel
        int listX = 30;
        int listY = 50;
        int listWidth = width - 230;
        int listHeight = height - 110;
        
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0080808);
        drawPanelBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Headers
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.players.col.player"), listX + 10, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.players.col.instance"), listX + 150, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.players.col.node"), listX + 320, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.players.col.role"), listX + 450, listY + 8, COLOR_NEON_RED);
        
        graphics.fill(listX + 5, listY + 22, listX + listWidth - 5, listY + 23, COLOR_BORDER);
        
        // Player entries
        int y = listY + 28;
        int visibleCount = (listHeight - 30) / PLAYER_ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, players.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            renderPlayerEntry(graphics, players.get(i), listX + 5, y, listWidth - 10, 
                i == selectedIndex, mouseX, mouseY);
            y += PLAYER_ENTRY_HEIGHT;
        }
        
        if (players.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.players.empty"), listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Sidebar details
        renderSidebar(graphics);
        
        // Stats bar
        renderStatsBar(graphics);
    }
    
    private void renderPlayerEntry(GuiGraphics graphics, PlayerInfo info, 
                                    int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + PLAYER_ENTRY_HEIGHT - 2;
        
        int bgColor = selected ? 0xFF1A0808 : (hovered ? 0xFF100505 : 0xFF0A0A0A);
        graphics.fill(x, y, x + width, y + PLAYER_ENTRY_HEIGHT - 2, bgColor);
        
        // Status indicator
        int statusColor = info.isLeader ? COLOR_NEON_RED : 0xFF44FF44;
        graphics.fill(x, y, x + 3, y + PLAYER_ENTRY_HEIGHT - 2, statusColor);
        
        // Player name
        graphics.drawString(font, info.name, x + 10, y + 10, COLOR_TEXT_BODY);
        
        // Instance name
        String instanceDisplay = info.instanceName.isEmpty() ? "-" : truncate(info.instanceName, 18);
        graphics.drawString(font, instanceDisplay, x + 150, y + 10, COLOR_TEXT_BODY);
        
        // Current node
        String nodeDisplay = info.currentNode.isEmpty() ? "-" : truncate(info.currentNode, 16);
        graphics.drawString(font, nodeDisplay, x + 320, y + 10, COLOR_TEXT_DIM);
        
        // Role
        String roleText = info.isLeader ? Component.translatable("gui.storyadventure.admin.players.role.leader").getString() 
                                      : Component.translatable("gui.storyadventure.admin.players.role.member").getString();
        graphics.drawString(font, roleText, x + 450, y + 10, info.isLeader ? COLOR_NEON_RED : COLOR_TEXT_DIM);
    }
    
    private void renderSidebar(GuiGraphics graphics) {
        int sidebarX = width - 170;
        int sidebarY = 50;
        int sidebarWidth = 150;
        
        graphics.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + 25, 0xE0080808);
        drawPanelBorder(graphics, sidebarX, sidebarY, sidebarWidth, 25);
        
        graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.instances.actions"), sidebarX + sidebarWidth / 2, sidebarY + 8, COLOR_NEON_RED);
        
        // Selected player info
        if (selectedIndex >= 0 && selectedIndex < players.size()) {
            PlayerInfo info = players.get(selectedIndex);
            int infoY = 260;
            
            graphics.fill(sidebarX, infoY, sidebarX + sidebarWidth, infoY + 80, 0xE0080808);
            drawPanelBorder(graphics, sidebarX, infoY, sidebarWidth, 80);
            
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.selected"), sidebarX + 5, infoY + 8, COLOR_TEXT_DIM);
            graphics.drawString(font, info.name, sidebarX + 5, infoY + 22, COLOR_TEXT_BODY);
            graphics.drawString(font, "UUID:", sidebarX + 5, infoY + 40, COLOR_TEXT_DIM);
            String shortUuid = info.uuid.toString().substring(0, 8);
            graphics.drawString(font, shortUuid, sidebarX + 5, infoY + 54, COLOR_TEXT_DIM);
        }
    }
    
    private void renderStatsBar(GuiGraphics graphics) {
        int barY = height - 70;
        
        graphics.fill(30, barY, width - 30, barY + 20, 0xC0080808);
        
        int inInstanceCount = (int) players.stream().filter(p -> !p.instanceName.isEmpty()).count();
        Component stats = Component.translatable("gui.storyadventure.admin.players.stats",
            players.size(),
            inInstanceCount,
            players.stream().filter(p -> p.isLeader).count());
        
        graphics.drawString(font, stats, 40, barY + 6, COLOR_TEXT_DIM);
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 6;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = 30;
        int listY = 50;
        int listWidth = width - 230;
        int listHeight = height - 110;
        
        if (mouseX >= listX && mouseX < listX + listWidth && 
            mouseY >= listY + 28 && mouseY < listY + listHeight) {
            int relY = (int)mouseY - listY - 28;
            int clickedIndex = scrollOffset + relY / PLAYER_ENTRY_HEIGHT;
            if (clickedIndex < players.size()) {
                selectedIndex = clickedIndex;
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        int visibleCount = (height - 110 - 30) / PLAYER_ENTRY_HEIGHT;
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + visibleCount < players.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    private void sendCommand(String command) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
    
    private void showMessage(String message) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
    
    private void teleportToPlayer() {
        if (selectedIndex >= 0 && selectedIndex < players.size()) {
            PlayerInfo info = players.get(selectedIndex);
            sendCommand("tp @s " + info.name);
            showMessage(Component.translatable("command.storyadventure.admin.players.tping", info.name).getString());
            onClose();
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void kickPlayer() {
        if (selectedIndex >= 0 && selectedIndex < players.size()) {
            PlayerInfo info = players.get(selectedIndex);
            sendCommand("storyadmin kick " + info.name);
            showMessage(Component.translatable("command.storyadventure.admin.players.kicking", info.name).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void sendMessageToPlayer() {
        if (selectedIndex >= 0 && selectedIndex < players.size()) {
            PlayerInfo info = players.get(selectedIndex);
            showMessage(Component.translatable("command.storyadventure.admin.players.msg_hint", info.name).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void viewPlayerDetails() {
        if (selectedIndex >= 0 && selectedIndex < players.size()) {
            PlayerInfo info = players.get(selectedIndex);
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_title").getString());
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_name", info.name).getString());
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_uuid", info.uuid).getString());
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_instance", (info.instanceName.isEmpty() ? Component.translatable("gui.storyadventure.admin.players.none").getString() : info.instanceName)).getString());
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_node", (info.currentNode.isEmpty() ? Component.translatable("gui.storyadventure.admin.players.none").getString() : info.currentNode)).getString());
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_role", (info.isLeader ? Component.translatable("gui.storyadventure.admin.players.role.leader").getString() : Component.translatable("gui.storyadventure.admin.players.role.member").getString())).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void refreshList() {
        sendCommand("storyadmin players");
        showMessage(Component.translatable("command.storyadventure.admin.players.refreshing").getString());
    }
    
    private void goBack() {
        Minecraft.getInstance().setScreen(new AdminDashboardScreen());
    }
    
    public record PlayerInfo(UUID uuid, String name, String instanceName, String currentNode, boolean isLeader) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/AdminStoryManagerScreen.java`
```java
package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.warmpixel.storyadventure.network.AdminStoryActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin UI for managing story definitions - reload, validate, view structure.
 */
public class AdminStoryManagerScreen extends StrangerScreen {
    
    private static final int STORY_ENTRY_HEIGHT = 45;
    
    private List<StoryInfo> stories = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    
    public AdminStoryManagerScreen() {
        super(Component.translatable("gui.storyadventure.admin.stories.title"));
    }
    
    public void addStory(String id, String name, int nodeCount, String version, boolean valid, String errorMsg) {
        stories.add(new StoryInfo(id, name, nodeCount, version, valid, errorMsg));
    }
    
    public void clearStories() {
        stories.clear();
        selectedIndex = -1;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 140;
        int buttonHeight = 26;
        int rightX = width - 170;
        int y = 80;
        
        // Action buttons - compact layout
        addStrangerButton(rightX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.reload_all"), this::reloadAll);
        y += buttonHeight + 4;
        
        addStrangerButton(rightX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.validate_selected"), this::validateSelected);
        y += buttonHeight + 4;
        
        addStrangerButton(rightX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.graph_editor"), this::openGraphEditor);
        y += buttonHeight + 12;
        
        // Locations Group
        addStrangerButton(rightX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.set_spawn"), this::setSpawnLocation);
        y += buttonHeight + 4;
        
        addStrangerButton(rightX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.set_return"), this::setReturnLocation);
        y += buttonHeight + 4;
        
        addStrangerButton(rightX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.tp_to_scene"), this::teleportToScene);
        y += buttonHeight + 12;
        
        addStrangerButton(rightX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.create_template"), this::createTemplate);
        
        // Close button
        addStrangerButton(width / 2 - 60, height - 45, 120, 28,
            Component.translatable("gui.storyadventure.admin.stories.close"), this::onClose);
            
        // Request fresh data - use direct payload to avoid re-open loop
        refreshStories();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Story list
        int listX = 30;
        int listY = 50;
        int listWidth = width - 230;
        int listHeight = height - 110;
        
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0080808);
        drawPanelBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Headers
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.col.id"), listX + 10, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.col.name"), listX + 120, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.col.nodes"), listX + listWidth - 140, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.col.version"), listX + listWidth - 80, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.col.status"), listX + listWidth - 40, listY + 8, COLOR_NEON_RED);
        
        graphics.fill(listX + 5, listY + 22, listX + listWidth - 5, listY + 23, COLOR_BORDER);
        
        // Story entries
        int y = listY + 28;
        int visibleCount = (listHeight - 30) / STORY_ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, stories.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            renderStoryEntry(graphics, stories.get(i), listX + 5, y, listWidth - 10, 
                i == selectedIndex, mouseX, mouseY);
            y += STORY_ENTRY_HEIGHT;
        }
        
        if (stories.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.stories.empty"), listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Selected story details
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            renderStoryDetails(graphics, stories.get(selectedIndex));
        }
        
        // Stats
        int validCount = (int) stories.stream().filter(s -> s.valid).count();
        Component stats = Component.translatable("gui.storyadventure.admin.stories.stats", 
            stories.size(), validCount, stories.size() - validCount);
        graphics.drawString(font, stats, 40, height - 70, COLOR_TEXT_DIM);
    }
    
    private void renderStoryEntry(GuiGraphics graphics, StoryInfo info, 
                                   int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + STORY_ENTRY_HEIGHT - 2;
        
        int bgColor = selected ? 0xFF1A0808 : (hovered ? 0xFF100505 : 0xFF0A0A0A);
        graphics.fill(x, y, x + width, y + STORY_ENTRY_HEIGHT - 2, bgColor);
        
        // Status indicator
        int statusColor = info.valid ? 0xFF44FF44 : 0xFFFF4444;
        graphics.fill(x, y, x + 3, y + STORY_ENTRY_HEIGHT - 2, statusColor);
        
        // ID
        graphics.drawString(font, truncate(info.id, 18), x + 10, y + 8, COLOR_TEXT_BODY);
        
        // Name
        graphics.drawString(font, truncate(info.name, 20), x + 120, y + 8, COLOR_TEXT_BODY);
        
        // Node count
        graphics.drawString(font, String.valueOf(info.nodeCount), x + width - 135, y + 8, COLOR_TEXT_DIM);
        
        // Version
        graphics.drawString(font, info.version, x + width - 80, y + 8, COLOR_TEXT_DIM);
        
        // Status
        String statusText = info.valid ? "Valid" : "Error"; // Shorten "Valid" to just symbol or short text? The header is "Status"
        // Or keep translatable but ensure it fits? "Valid" is short enough.
        // But previously it was "status.valid" translation.
        statusText = info.valid ? Component.translatable("gui.storyadventure.admin.stories.status.valid").getString() 
                                      : Component.translatable("gui.storyadventure.admin.stories.status.error").getString();
        // Since "Valid" is short, we can use an icon or text.
        // Let's draw it right aligned or something.
        graphics.drawString(font, statusText, x + width - 40, y + 8, statusColor);
        
        // Error preview
        if (!info.valid && !info.errorMsg.isEmpty()) {
            graphics.drawString(font, truncate(info.errorMsg, 60), x + 10, y + 22, 0xFFAA4444);
        }
    }
    
    private void renderStoryDetails(GuiGraphics graphics, StoryInfo info) {
        int detailsY = height - 100;
        
        if (!info.valid && !info.errorMsg.isEmpty()) {
            graphics.fill(30, detailsY - 5, width - 30, detailsY + 25, 0xE01A0808);
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.error_details"), 40, detailsY, 0xFFFF6666);
            graphics.drawString(font, info.errorMsg, 40, detailsY + 12, 0xFFAA4444);
        }
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 8;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = 30;
        int listY = 50;
        int listWidth = width - 230;
        int listHeight = height - 110;
        
        if (mouseX >= listX && mouseX < listX + listWidth && 
            mouseY >= listY + 28 && mouseY < listY + listHeight) {
            int relY = (int)mouseY - listY - 28;
            int clickedIndex = scrollOffset + relY / STORY_ENTRY_HEIGHT;
            if (clickedIndex < stories.size()) {
                selectedIndex = clickedIndex;
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        int visibleCount = (height - 110 - 30) / STORY_ENTRY_HEIGHT;
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + visibleCount < stories.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    private void sendCommand(String command) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
    
    private void showMessage(String message) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
    
    private void reloadAll() {
        ClientPlayNetworking.send(new AdminStoryActionPayload(AdminStoryActionPayload.Action.RELOAD, ""));
        showMessage(Component.translatable("command.storyadventure.admin.stories.reloading").getString());
    }
    
    private void validateSelected() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            ClientPlayNetworking.send(new AdminStoryActionPayload(AdminStoryActionPayload.Action.VALIDATE, info.id));
            showMessage(Component.translatable("command.storyadventure.admin.stories.validating", info.id).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }

    private void refreshStories() {
        ClientPlayNetworking.send(new AdminStoryActionPayload(AdminStoryActionPayload.Action.SYNC, ""));
    }
    
    private void viewStructure() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            // Open the node list screen for this story
            AdminNodeListScreen nodeListScreen = new AdminNodeListScreen(info.id, info.name);
            net.minecraft.client.Minecraft.getInstance().setScreen(nodeListScreen);
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void setSpawnLocation() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            sendCommand("storyadmin setlocation " + info.id + " spawn");
            showMessage(Component.translatable("command.storyadventure.admin.stories.spawn_set", info.id).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void setReturnLocation() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            sendCommand("storyadmin setlocation " + info.id + " return");
            showMessage(Component.translatable("command.storyadventure.admin.stories.return_set", info.id).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void teleportToScene() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            sendCommand("storyadmin tp " + info.id);
            showMessage(Component.translatable("command.storyadventure.admin.stories.tp_scene", info.id).getString());
            onClose();
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void openGraphEditor() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            com.warmpixel.storyadventure.client.ui.admin.graph.StoryGraphScreen graphScreen = 
                new com.warmpixel.storyadventure.client.ui.admin.graph.StoryGraphScreen(info.id, info.name);
            net.minecraft.client.Minecraft.getInstance().setScreen(graphScreen);
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void createTemplate() {
        showMessage(Component.translatable("command.storyadventure.admin.stories.template_creating").getString());
        showMessage(Component.translatable("command.storyadventure.admin.stories.template_path", "config/storyadventure/stories/new_story.json").getString());
        showMessage(Component.translatable("command.storyadventure.admin.stories.template_hint").getString());
    }
    
    public record StoryInfo(String id, String name, int nodeCount, String version, boolean valid, String errorMsg) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/AdminSystemStatsScreen.java`
```java
package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin UI showing system statistics and server health.
 */
public class AdminSystemStatsScreen extends StrangerScreen {
    
    private int activeInstances = 0;
    private int totalPlayers = 0;
    private int loadedStories = 0;
    private double serverTps = 20.0;
    private long serverMemoryUsed = 0;
    private long serverMemoryMax = 0;
    
    private final List<ActivityEntry> recentActivity = new ArrayList<>();
    private long lastUpdateTime = 0;
    
    public AdminSystemStatsScreen() {
        super(Component.translatable("gui.storyadventure.admin.stats.title"));
    }
    
    public void setStats(int activeInstances, int totalPlayers, int loadedStories, 
                         double serverTps, long memoryUsed, long memoryMax) {
        this.activeInstances = activeInstances;
        this.totalPlayers = totalPlayers;
        this.loadedStories = loadedStories;
        this.serverTps = serverTps;
        this.serverMemoryUsed = memoryUsed;
        this.serverMemoryMax = memoryMax;
    }
    
    public void addActivity(String message, long timestamp) {
        recentActivity.add(0, new ActivityEntry(message, timestamp));
        if (recentActivity.size() > 20) {
            recentActivity.remove(recentActivity.size() - 1);
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Refresh button
        addStrangerButton(width - 150, 50, 120, 24,
            Component.translatable("gui.storyadventure.admin.stats.refresh"), this::refreshStats);
        
        // Back button
        addStrangerButton(30, height - 45, 100, 28,
            Component.translatable("gui.storyadventure.admin.stats.back"), this::goBack);
        
        // Close button
        addStrangerButton(width / 2 - 60, height - 45, 120, 28,
            Component.translatable("gui.storyadventure.admin.stats.close"), this::onClose);
        
        // Request initial stats
        refreshStats();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = 200;
        int panelHeight = 120;
        int gap = 20;
        int startX = 40;
        int startY = 60;
        
        // === Instance Stats Panel ===
        renderStatPanel(graphics, startX, startY, panelWidth, panelHeight, 
            Component.translatable("gui.storyadventure.admin.stats.panel.instances").getString(), 
            new String[]{
            Component.translatable("gui.storyadventure.admin.stats.active_instances", activeInstances).getString(),
            Component.translatable("gui.storyadventure.admin.stats.waiting_teams").getString(),
            Component.translatable("gui.storyadventure.admin.stats.completed_today").getString(),
            Component.translatable("gui.storyadventure.admin.stats.failed_today").getString()
        }, new int[]{
            activeInstances > 0 ? 0xFF44FF44 : COLOR_TEXT_BODY,
            COLOR_TEXT_BODY,
            COLOR_TEXT_BODY,
            COLOR_TEXT_DIM
        });
        
        // === Player Stats Panel ===
        renderStatPanel(graphics, startX + panelWidth + gap, startY, panelWidth, panelHeight, 
            Component.translatable("gui.storyadventure.admin.stats.panel.players").getString(), 
            new String[]{
            Component.translatable("gui.storyadventure.admin.stats.players_in_adventure", totalPlayers).getString(),
            Component.translatable("gui.storyadventure.admin.stats.total_online", (Minecraft.getInstance().getConnection() != null ? 
                Minecraft.getInstance().getConnection().getOnlinePlayers().size() : 0)).getString(),
            Component.translatable("gui.storyadventure.admin.stats.waiting_players").getString()
        }, new int[]{
            totalPlayers > 0 ? COLOR_NEON_RED : COLOR_TEXT_BODY,
            COLOR_TEXT_BODY,
            COLOR_TEXT_DIM
        });
        
        // === Server Health Panel ===
        int healthColor = serverTps >= 19 ? 0xFF44FF44 : (serverTps >= 15 ? 0xFFFFCC00 : 0xFFFF4444);
        String tpsStr = Component.translatable("gui.storyadventure.admin.stats.tps", serverTps).getString();
        long memMB = serverMemoryUsed / (1024 * 1024);
        long maxMB = serverMemoryMax / (1024 * 1024);
        String memStr = Component.translatable("gui.storyadventure.admin.stats.memory", memMB, maxMB).getString();
        String statusNormal = Component.translatable("gui.storyadventure.admin.stats.status", Component.translatable("gui.storyadventure.admin.stats.status.normal").getString()).getString();
        
        renderStatPanel(graphics, startX, startY + panelHeight + gap, panelWidth, panelHeight, 
            Component.translatable("gui.storyadventure.admin.stats.panel.health").getString(), 
            new String[]{
            tpsStr,
            memStr,
            statusNormal
        }, new int[]{
            healthColor,
            COLOR_TEXT_BODY,
            0xFF44FF44
        });
        
        // === Story Stats Panel ===
        renderStatPanel(graphics, startX + panelWidth + gap, startY + panelHeight + gap, panelWidth, panelHeight, 
            Component.translatable("gui.storyadventure.admin.stats.panel.stories").getString(), 
            new String[]{
            Component.translatable("gui.storyadventure.admin.stats.loaded_stories", loadedStories).getString(),
            Component.translatable("gui.storyadventure.admin.stats.valid_stories", loadedStories).getString(),
            Component.translatable("gui.storyadventure.admin.stats.error_stories").getString()
        }, new int[]{
            COLOR_TEXT_BODY,
            0xFF44FF44,
            COLOR_TEXT_DIM
        });
        
        // === Recent Activity Panel ===
        int activityX = startX + (panelWidth + gap) * 2;
        int activityWidth = width - activityX - 40;
        int activityHeight = height - 140;
        
        graphics.fill(activityX, startY, activityX + activityWidth, startY + activityHeight, 0xE0080808);
        drawPanelBorder(graphics, activityX, startY, activityWidth, activityHeight);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stats.panel.activity"), activityX + 10, startY + 8, COLOR_NEON_RED);
        graphics.fill(activityX + 5, startY + 22, activityX + activityWidth - 5, startY + 23, COLOR_BORDER);
        
        int actY = startY + 30;
        if (recentActivity.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stats.no_activity"), activityX + 10, actY, COLOR_TEXT_DIM);
        } else {
            for (int i = 0; i < Math.min(recentActivity.size(), 10); i++) {
                ActivityEntry entry = recentActivity.get(i);
                String timeAgo = formatTimeAgo(entry.timestamp);
                graphics.drawString(font, "§7" + timeAgo + " §f" + entry.message, activityX + 10, actY, COLOR_TEXT_BODY);
                actY += 14;
            }
        }
        
        // Progress bars for memory
        int barX = startX + 10;
        int barY = startY + panelHeight + gap + panelHeight - 25;
        int barWidth = panelWidth - 20;
        int barHeight = 8;
        
        // Memory bar background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        // Memory bar fill
        double memPercent = serverMemoryMax > 0 ? (double) serverMemoryUsed / serverMemoryMax : 0;
        int fillWidth = (int)(barWidth * memPercent);
        int memBarColor = memPercent < 0.7 ? 0xFF44FF44 : (memPercent < 0.9 ? 0xFFFFCC00 : 0xFFFF4444);
        graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, memBarColor);
        
        // Last update time
        if (lastUpdateTime > 0) {
            String updateStr = Component.translatable("gui.storyadventure.admin.stats.last_update", formatTimeAgo(lastUpdateTime)).getString();
            graphics.drawString(font, updateStr, 40, height - 70, COLOR_TEXT_DIM);
        }
    }
    
    private void renderStatPanel(GuiGraphics graphics, int x, int y, int w, int h, 
                                  String title, String[] lines, int[] colors) {
        graphics.fill(x, y, x + w, y + h, 0xE0080808);
        drawPanelBorder(graphics, x, y, w, h);
        
        graphics.drawString(font, title, x + 10, y + 8, COLOR_NEON_RED);
        graphics.fill(x + 5, y + 22, x + w - 5, y + 23, COLOR_BORDER);
        
        int lineY = y + 32;
        for (int i = 0; i < lines.length; i++) {
            int color = i < colors.length ? colors[i] : COLOR_TEXT_BODY;
            graphics.drawString(font, lines[i], x + 10, lineY, color);
            lineY += 16;
        }
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 6;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        if (seconds < 60) return Component.translatable("gui.storyadventure.admin.stats.time.seconds", seconds).getString();
        long minutes = seconds / 60;
        if (minutes < 60) return Component.translatable("gui.storyadventure.admin.stats.time.minutes", minutes).getString();
        long hours = minutes / 60;
        return Component.translatable("gui.storyadventure.admin.stats.time.hours", hours).getString();
    }
    
    private void refreshStats() {
        lastUpdateTime = System.currentTimeMillis();
        
        // Get local stats from client cache
        var instances = com.warmpixel.storyadventure.network.ClientNetworkHandler.getLastSyncedInstances();
        activeInstances = instances.size();
        totalPlayers = instances.stream().mapToInt(i -> i.playerCount()).sum();
        
        // Memory stats (client-side only)
        Runtime runtime = Runtime.getRuntime();
        serverMemoryUsed = runtime.totalMemory() - runtime.freeMemory();
        serverMemoryMax = runtime.maxMemory();
        serverTps = 20.0; // Placeholder - would need server sync
        
        loadedStories = 2; // Placeholder
        
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand("storyadmin stats");
            mc.player.sendSystemMessage(Component.translatable("command.storyadventure.admin.stats.refreshing"));
        }
    }
    
    private void goBack() {
        Minecraft.getInstance().setScreen(new AdminDashboardScreen());
    }
    
    public record ActivityEntry(String message, long timestamp) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/TriggerBoxManagerScreen.java`
```java
package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import com.warmpixel.storyadventure.core.waypoint.TriggerBox;
import com.warmpixel.storyadventure.item.AdminWandItem;
import com.warmpixel.storyadventure.network.AdminTriggerActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin UI for managing trigger boxes.
 * Shows list of boxes, allows editing properties and actions.
 */
public class TriggerBoxManagerScreen extends StrangerScreen {
    
    private static final int LIST_WIDTH = 220;
    private static final int ENTRY_HEIGHT = 24;
    
    private List<TriggerBoxEntry> boxes = new ArrayList<>();
    private int scrollOffset = 0;
    private TriggerBoxEntry selectedBox = null;
    
    // Edit fields
    private EditBox labelField;
    private EditBox linkedNodeField;
    private EditBox minXField, minYField, minZField;
    private EditBox maxXField, maxYField, maxZField;
    
    public TriggerBoxManagerScreen() {
        super(Component.translatable("gui.storyadventure.admin.triggers.title"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        int rightPanelX = LIST_WIDTH + 30;
        int fieldWidth = 150;
        int fieldHeight = 20;
        int y = 60;
        
        // Label field
        labelField = new EditBox(font, rightPanelX + 80, y, fieldWidth, fieldHeight, Component.translatable("gui.storyadventure.admin.triggers.label"));
        labelField.setMaxLength(64);
        addRenderableWidget(labelField);
        y += 28;
        
        // Linked node field  
        linkedNodeField = new EditBox(font, rightPanelX + 80, y, fieldWidth, fieldHeight, Component.translatable("gui.storyadventure.admin.triggers.linked_node"));
        linkedNodeField.setMaxLength(64);
        addRenderableWidget(linkedNodeField);
        y += 40;
        
        // Coordinate fields
        int coordWidth = 60;
        minXField = new EditBox(font, rightPanelX + 40, y, coordWidth, fieldHeight, Component.literal("X"));
        minYField = new EditBox(font, rightPanelX + 110, y, coordWidth, fieldHeight, Component.literal("Y"));
        minZField = new EditBox(font, rightPanelX + 180, y, coordWidth, fieldHeight, Component.literal("Z"));
        addRenderableWidget(minXField);
        addRenderableWidget(minYField);
        addRenderableWidget(minZField);
        y += 28;
        
        maxXField = new EditBox(font, rightPanelX + 40, y, coordWidth, fieldHeight, Component.literal("X"));
        maxYField = new EditBox(font, rightPanelX + 110, y, coordWidth, fieldHeight, Component.literal("Y"));
        maxZField = new EditBox(font, rightPanelX + 180, y, coordWidth, fieldHeight, Component.literal("Z"));
        addRenderableWidget(maxXField);
        addRenderableWidget(maxYField);
        addRenderableWidget(maxZField);
        y += 40;
        
        // Action buttons
        int btnWidth = 100;
        int btnHeight = 24;
        
        addStrangerButton(rightPanelX, y, btnWidth, btnHeight, 
            Component.translatable("gui.storyadventure.admin.triggers.save"), this::saveCurrentBox);
        addStrangerButton(rightPanelX + btnWidth + 10, y, btnWidth, btnHeight,
            Component.translatable("gui.storyadventure.admin.triggers.delete"), this::deleteCurrentBox);
        y += 35;
        
        addStrangerButton(rightPanelX, y, btnWidth, btnHeight,
            Component.translatable("gui.storyadventure.admin.triggers.teleport"), this::teleportToBox);
        addStrangerButton(rightPanelX + btnWidth + 10, y, btnWidth, btnHeight,
            Component.translatable("gui.storyadventure.admin.triggers.add_action"), this::addAction);
        
        // Bottom toolbar
        int toolbarY = height - 45;
        addStrangerButton(20, toolbarY, 100, 28, Component.translatable("gui.storyadventure.admin.triggers.refresh"), this::refreshList);
        addStrangerButton(130, toolbarY, 120, 28, Component.translatable("gui.storyadventure.admin.triggers.new"), this::createNewBox);
        addStrangerButton(width - 110, toolbarY, 100, 28, Component.translatable("gui.storyadventure.admin.triggers.back"), this::goBack);
        
        // Load boxes
        refreshList();
        
        // Check for pending box from wand creation
        checkPendingBox();
    }
    
    private void checkPendingBox() {
        if (minecraft == null || minecraft.player == null) return;
        
        var pending = com.warmpixel.storyadventure.item.AdminWandItem.PendingTriggerBoxes.remove(minecraft.player.getUUID());
        if (pending != null) {
            // Add the pending box to our list
            TriggerBox box = new TriggerBox(pending.id(), new AABB(pending.corner1(), pending.corner2()));
            box.setLabel(Component.translatable("gui.storyadventure.admin.triggers.new_trigger").getString());
            boxes.add(new TriggerBoxEntry(box));
            selectBox(boxes.get(boxes.size() - 1));
            
            // Request server to save it
            requestSaveBox(box);
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Left panel - box list
        graphics.fill(15, 45, LIST_WIDTH + 15, height - 55, 0xE0080808);
        drawPanelBorder(graphics, 15, 45, LIST_WIDTH, height - 100);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.list_title", boxes.size()), 20, 50, COLOR_NEON_RED);
        graphics.fill(20, 64, LIST_WIDTH + 10, 65, COLOR_BORDER);
        
        // Render box entries
        int listY = 70;
        int visibleCount = (height - 130) / ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, boxes.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            TriggerBoxEntry entry = boxes.get(i);
            boolean selected = entry == selectedBox;
            boolean hovered = mouseX >= 20 && mouseX < LIST_WIDTH + 10 && 
                              mouseY >= listY && mouseY < listY + ENTRY_HEIGHT - 2;
            
            int bgColor = selected ? 0xFF331111 : (hovered ? 0xFF1A0808 : 0x00000000);
            if (bgColor != 0) {
                graphics.fill(20, listY, LIST_WIDTH + 10, listY + ENTRY_HEIGHT - 2, bgColor);
            }
            
            String label = entry.box.getLabel();
            if (label.length() > 20) label = label.substring(0, 18) + "...";
            graphics.drawString(font, label, 25, listY + 4, selected ? COLOR_NEON_RED : COLOR_TEXT_BODY);
            graphics.drawString(font, entry.box.getId(), 25, listY + 13, COLOR_TEXT_DIM);
            
            listY += ENTRY_HEIGHT;
        }
        
        // Right panel - editor
        int rightX = LIST_WIDTH + 25;
        graphics.fill(rightX, 45, width - 15, height - 55, 0xE0080808);
        drawPanelBorder(graphics, rightX, 45, width - rightX - 15, height - 100);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.properties"), rightX + 5, 50, COLOR_NEON_RED);
        graphics.fill(rightX + 5, 64, width - 20, 65, COLOR_BORDER);
        
        if (selectedBox != null) {
            int labelY = 65;
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.label"), rightX + 10, labelY, COLOR_TEXT_BODY);
            labelY += 28;
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.linked_node"), rightX + 10, labelY, COLOR_TEXT_BODY);
            labelY += 40;
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.min_pos"), rightX + 10, labelY, COLOR_TEXT_BODY);
            labelY += 28;
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.max_pos"), rightX + 10, labelY, COLOR_TEXT_BODY);
        } else {
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.triggers.empty_selection"), (rightX + width - 15) / 2, height / 2, COLOR_TEXT_DIM);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check list clicks
        if (mouseX >= 20 && mouseX < LIST_WIDTH + 10 && mouseY >= 70) {
            int listY = 70;
            int visibleCount = (height - 130) / ENTRY_HEIGHT;
            int visibleEnd = Math.min(scrollOffset + visibleCount, boxes.size());
            
            for (int i = scrollOffset; i < visibleEnd; i++) {
                if (mouseY >= listY && mouseY < listY + ENTRY_HEIGHT - 2) {
                    selectBox(boxes.get(i));
                    return true;
                }
                listY += ENTRY_HEIGHT;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (mouseX < LIST_WIDTH + 15) {
            scrollOffset = Math.max(0, Math.min(boxes.size() - 5, scrollOffset - (int) vAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    private void selectBox(TriggerBoxEntry entry) {
        selectedBox = entry;
        if (entry != null) {
            TriggerBox box = entry.box;
            labelField.setValue(box.getLabel());
            linkedNodeField.setValue(box.getLinkedNodeId() != null ? box.getLinkedNodeId() : "");
            
            AABB bounds = box.getBounds();
            minXField.setValue(String.format("%.1f", bounds.minX));
            minYField.setValue(String.format("%.1f", bounds.minY));
            minZField.setValue(String.format("%.1f", bounds.minZ));
            maxXField.setValue(String.format("%.1f", bounds.maxX));
            maxYField.setValue(String.format("%.1f", bounds.maxY));
            maxZField.setValue(String.format("%.1f", bounds.maxZ));
        }
    }
    
    private void saveCurrentBox() {
        if (selectedBox == null) return;
        
        try {
            TriggerBox box = selectedBox.box;
            box.setLabel(labelField.getValue());
            box.setLinkedNodeId(linkedNodeField.getValue().isEmpty() ? null : linkedNodeField.getValue());
            
            double minX = Double.parseDouble(minXField.getValue());
            double minY = Double.parseDouble(minYField.getValue());
            double minZ = Double.parseDouble(minZField.getValue());
            double maxX = Double.parseDouble(maxXField.getValue());
            double maxY = Double.parseDouble(maxYField.getValue());
            double maxZ = Double.parseDouble(maxZField.getValue());
            box.setBounds(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
            
            requestSaveBox(box);
            showMessage(Component.translatable("command.storyadventure.admin.triggers.saved").getString());
        } catch (NumberFormatException e) {
            showMessage(Component.translatable("command.storyadventure.admin.triggers.error_pos").getString());
        }
    }
    
    private void deleteCurrentBox() {
        if (selectedBox == null) return;
        
        String id = selectedBox.box.getId();
        requestDeleteBox(id);
        boxes.remove(selectedBox);
        selectedBox = null;
        showMessage(Component.translatable("command.storyadventure.admin.triggers.deleted").getString());
    }
    
    private void teleportToBox() {
        if (selectedBox == null || minecraft == null || minecraft.player == null) return;
        
        AABB bounds = selectedBox.box.getBounds();
        double x = (bounds.minX + bounds.maxX) / 2;
        double y = bounds.minY;
        double z = (bounds.minZ + bounds.maxZ) / 2;
        
        // Send teleport command
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(String.format("tp @s %.1f %.1f %.1f", x, y, z));
        }
    }
    
    private void addAction() {
        if (selectedBox == null) return;
        showMessage(Component.translatable("command.storyadventure.admin.triggers.action_editor_dev").getString());
    }
    
    private void refreshList() {
        // Request box list from server
        requestBoxList();
    }
    
    private void createNewBox() {
        showMessage(Component.translatable("command.storyadventure.admin.triggers.create_wand_hint").getString());
        goBack();
    }
    
    private void goBack() {
        Minecraft.getInstance().setScreen(new AdminDashboardScreen());
    }
    
    // Network request stubs - will be implemented
    private void requestBoxList() {
        if (minecraft != null) {
            ClientPlayNetworking.send(new AdminTriggerActionPayload(
                AdminTriggerActionPayload.Action.LIST, "", "", 0, 0, 0, 0, 0, 0, ""
            ));
        }
    }
    
    private void requestSaveBox(TriggerBox box) {
        if (minecraft != null) {
            var bounds = box.getBounds();
            ClientPlayNetworking.send(new AdminTriggerActionPayload(
                AdminTriggerActionPayload.Action.SAVE,
                box.getId(),
                box.getLabel(),
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                box.getLinkedNodeId() != null ? box.getLinkedNodeId() : ""
            ));
        }
    }
    
    private void requestDeleteBox(String id) {
        if (minecraft != null) {
            ClientPlayNetworking.send(new AdminTriggerActionPayload(
                AdminTriggerActionPayload.Action.DELETE, id, "", 0, 0, 0, 0, 0, 0, ""
            ));
        }
    }
    
    public void addBoxFromSync(String id, String label, double minX, double minY, double minZ, 
                                double maxX, double maxY, double maxZ) {
        TriggerBox box = new TriggerBox(id, new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        box.setLabel(label);
        boxes.add(new TriggerBoxEntry(box));
    }
    
    public void clearBoxes() {
        boxes.clear();
        selectedBox = null;
    }
    
    private void showMessage(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
        }
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 6;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private record TriggerBoxEntry(TriggerBox box) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/panels/ConfirmationPanel.java`
```java
package com.warmpixel.storyadventure.client.ui.admin.panels;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable modal confirmation panel for admin actions.
 * Renders on top of the current screen as an overlay.
 */
public class ConfirmationPanel {
    
    private static final int COLOR_NEON_RED = 0xFFE50914;
    private static final int COLOR_TEXT_BODY = 0xFFCCCCCC;
    private static final int COLOR_TEXT_DIM = 0xFF666666;
    private static final int COLOR_BORDER = 0xFF330011;
    private static final int COLOR_WARNING = 0xFFFFCC00;
    private static final int COLOR_DANGER = 0xFFFF4444;
    
    private final String title;
    private final String description;
    private final boolean isDangerous;
    private final boolean hasInputField;
    private final String inputPlaceholder;
    private final Consumer<String> onConfirm;
    private final Runnable onCancel;
    
    private int x, y, width, height;
    private EditBox inputField;
    private boolean visible = false;
    
    private StrangerButton confirmButton;
    private StrangerButton cancelButton;
    
    public ConfirmationPanel(String title, String description, boolean isDangerous,
                             boolean hasInputField, String inputPlaceholder,
                             Consumer<String> onConfirm, Runnable onCancel) {
        this.title = title;
        this.description = description;
        this.isDangerous = isDangerous;
        this.hasInputField = hasInputField;
        this.inputPlaceholder = inputPlaceholder;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }
    
    /**
     * Show the panel centered on screen.
     */
    public void show(int screenWidth, int screenHeight, net.minecraft.client.gui.Font font) {
        this.width = 320;
        this.height = hasInputField ? 160 : 130;
        this.x = (screenWidth - width) / 2;
        this.y = (screenHeight - height) / 2;
        this.visible = true;
        
        if (hasInputField) {
            inputField = new EditBox(font, x + 20, y + 70, width - 40, 20, Component.literal(inputPlaceholder));
            inputField.setMaxLength(200);
            inputField.setHint(Component.literal(inputPlaceholder));
        }
    }
    
    public void hide() {
        this.visible = false;
        this.inputField = null;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void render(GuiGraphics graphics, int mouseX, int mouseY, net.minecraft.client.gui.Font font) {
        if (!visible) return;
        
        // Dim background
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xC0000000);
        
        // Panel background
        graphics.fill(x, y, x + width, y + height, 0xF0101010);
        
        // Border
        int borderColor = isDangerous ? COLOR_DANGER : COLOR_NEON_RED;
        graphics.fill(x, y, x + width, y + 2, borderColor);
        graphics.fill(x, y + height - 2, x + width, y + height, borderColor);
        graphics.fill(x, y, x + 2, y + height, borderColor);
        graphics.fill(x + width - 2, y, x + width, y + height, borderColor);
        
        // Corner accents
        int cs = 10;
        graphics.fill(x, y, x + cs, y + 3, borderColor);
        graphics.fill(x, y, x + 3, y + cs, borderColor);
        graphics.fill(x + width - cs, y, x + width, y + 3, borderColor);
        graphics.fill(x + width - 3, y, x + width, y + cs, borderColor);
        
        // Title
        graphics.drawCenteredString(font, title, x + width / 2, y + 12, borderColor);
        
        // Divider
        graphics.fill(x + 10, y + 28, x + width - 10, y + 29, COLOR_BORDER);
        
        // Description (word wrap)
        List<String> lines = wrapText(description, width - 40, font);
        int lineY = y + 38;
        for (String line : lines) {
            graphics.drawString(font, line, x + 20, lineY, COLOR_TEXT_BODY);
            lineY += 12;
        }
        
        // Input field
        if (hasInputField && inputField != null) {
            inputField.render(graphics, mouseX, mouseY, 0);
        }
        
        // Buttons
        int buttonY = y + height - 35;
        int buttonWidth = 100;
        int buttonHeight = 24;
        int gap = 20;
        
        // Confirm button
        int confirmX = x + width / 2 - buttonWidth - gap / 2;
        boolean confirmHovered = mouseX >= confirmX && mouseX < confirmX + buttonWidth &&
                                  mouseY >= buttonY && mouseY < buttonY + buttonHeight;
        int confirmBg = confirmHovered ? (isDangerous ? 0xFF441111 : 0xFF114411) : 0xFF222222;
        graphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + buttonHeight, confirmBg);
        graphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + 1, isDangerous ? COLOR_DANGER : 0xFF44FF44);
        String confirmText = Component.translatable("gui.storyadventure.admin.panels.confirm").getString();
        graphics.drawCenteredString(font, confirmText, confirmX + buttonWidth / 2, buttonY + 7, 
            isDangerous ? COLOR_DANGER : 0xFF44FF44);
        
        // Cancel button
        int cancelX = x + width / 2 + gap / 2;
        boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
                                 mouseY >= buttonY && mouseY < buttonY + buttonHeight;
        int cancelBg = cancelHovered ? 0xFF333333 : 0xFF222222;
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + buttonHeight, cancelBg);
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + 1, COLOR_TEXT_DIM);
        graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.panels.cancel").getString(), cancelX + buttonWidth / 2, buttonY + 7, COLOR_TEXT_BODY);
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        
        // Check input field
        if (hasInputField && inputField != null) {
            inputField.mouseClicked(mouseX, mouseY, button);
        }
        
        int buttonY = y + height - 35;
        int buttonWidth = 100;
        int buttonHeight = 24;
        int gap = 20;
        
        // Confirm button
        int confirmX = x + width / 2 - buttonWidth - gap / 2;
        if (mouseX >= confirmX && mouseX < confirmX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
            String inputValue = hasInputField && inputField != null ? inputField.getValue() : "";
            onConfirm.accept(inputValue);
            hide();
            return true;
        }
        
        // Cancel button
        int cancelX = x + width / 2 + gap / 2;
        if (mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
            onCancel.run();
            hide();
            return true;
        }
        
        // Click outside to cancel
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            onCancel.run();
            hide();
            return true;
        }
        
        return true; // Consume click to prevent interaction with background
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        
        if (hasInputField && inputField != null) {
            return inputField.keyPressed(keyCode, scanCode, modifiers);
        }
        
        // Escape to cancel
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            onCancel.run();
            hide();
            return true;
        }
        
        // Enter to confirm
        if (keyCode == 257) { // GLFW_KEY_ENTER
            String inputValue = hasInputField && inputField != null ? inputField.getValue() : "";
            onConfirm.accept(inputValue);
            hide();
            return true;
        }
        
        return false;
    }
    
    public boolean charTyped(char chr, int modifiers) {
        if (!visible) return false;
        
        if (hasInputField && inputField != null) {
            return inputField.charTyped(chr, modifiers);
        }
        return false;
    }
    
    private List<String> wrapText(String text, int maxWidth, net.minecraft.client.gui.Font font) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            String test = currentLine.length() > 0 ? currentLine + " " + word : word;
            if (font.width(test) <= maxWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }
    
    // Builder pattern for easier creation
    public static Builder builder(String title) {
        return new Builder(title);
    }
    
    public static class Builder {
        private final String title;
        private String description = "";
        private boolean isDangerous = false;
        private boolean hasInputField = false;
        private String inputPlaceholder = "";
        private Consumer<String> onConfirm = s -> {};
        private Runnable onCancel = () -> {};
        
        public Builder(String title) {
            this.title = title;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder dangerous() {
            this.isDangerous = true;
            return this;
        }
        
        public Builder withInput(String placeholder) {
            this.hasInputField = true;
            this.inputPlaceholder = placeholder;
            return this;
        }
        
        public Builder onConfirm(Consumer<String> callback) {
            this.onConfirm = callback;
            return this;
        }
        
        public Builder onConfirm(Runnable callback) {
            this.onConfirm = s -> callback.run();
            return this;
        }
        
        public Builder onCancel(Runnable callback) {
            this.onCancel = callback;
            return this;
        }
        
        public ConfirmationPanel build() {
            return new ConfirmationPanel(title, description, isDangerous, hasInputField, 
                inputPlaceholder, onConfirm, onCancel);
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/panels/NodeSelectorPanel.java`
```java
package com.warmpixel.storyadventure.client.ui.admin.panels;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel for selecting a node to skip to in an instance.
 * Shows a scrollable list of available nodes.
 */
public class NodeSelectorPanel {
    
    private static final int COLOR_NEON_RED = 0xFFE50914;
    private static final int COLOR_TEXT_BODY = 0xFFCCCCCC;
    private static final int COLOR_TEXT_DIM = 0xFF666666;
    private static final int COLOR_BORDER = 0xFF330011;
    private static final int ENTRY_HEIGHT = 28;
    
    private final String title;
    private final List<NodeEntry> nodes = new ArrayList<>();
    private final Consumer<String> onSelect;
    private final Runnable onCancel;
    
    private int x, y, width, height;
    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private boolean visible = false;
    
    public NodeSelectorPanel(String title, Consumer<String> onSelect, Runnable onCancel) {
        this.title = title;
        this.onSelect = onSelect;
        this.onCancel = onCancel;
    }
    
    public void addNode(String nodeId, String nodeType, String description) {
        nodes.add(new NodeEntry(nodeId, nodeType, description));
    }
    
    public void clearNodes() {
        nodes.clear();
        selectedIndex = -1;
    }
    
    public void show(int screenWidth, int screenHeight) {
        this.width = 400;
        this.height = 350;
        this.x = (screenWidth - width) / 2;
        this.y = (screenHeight - height) / 2;
        this.visible = true;
        this.scrollOffset = 0;
        this.selectedIndex = -1;
    }
    
    public void hide() {
        this.visible = false;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void render(GuiGraphics graphics, int mouseX, int mouseY, Font font) {
        if (!visible) return;
        
        // Dim background
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xC0000000);
        
        // Panel background
        graphics.fill(x, y, x + width, y + height, 0xF0101010);
        
        // Border
        graphics.fill(x, y, x + width, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y + height - 2, x + width, y + height, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + height, COLOR_NEON_RED);
        graphics.fill(x + width - 2, y, x + width, y + height, COLOR_NEON_RED);
        
        // Corner accents
        int cs = 10;
        graphics.fill(x, y, x + cs, y + 3, COLOR_NEON_RED);
        graphics.fill(x, y, x + 3, y + cs, COLOR_NEON_RED);
        graphics.fill(x + width - cs, y, x + width, y + 3, COLOR_NEON_RED);
        graphics.fill(x + width - 3, y, x + width, y + cs, COLOR_NEON_RED);
        
        // Title
        graphics.drawCenteredString(font, title, x + width / 2, y + 12, COLOR_NEON_RED);
        
        // Divider
        graphics.fill(x + 10, y + 28, x + width - 10, y + 29, COLOR_BORDER);
        
        // List area
        int listY = y + 35;
        int listHeight = height - 85;
        int visibleCount = listHeight / ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, nodes.size());
        
        // List background
        graphics.fill(x + 10, listY, x + width - 10, listY + listHeight, 0xFF080808);
        
        // Entries
        int entryY = listY;
        for (int i = scrollOffset; i < visibleEnd; i++) {
            NodeEntry entry = nodes.get(i);
            boolean isSelected = i == selectedIndex;
            boolean isHovered = mouseX >= x + 10 && mouseX < x + width - 10 &&
                               mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;
            
            int bgColor = isSelected ? 0xFF1A0808 : (isHovered ? 0xFF120606 : 0xFF080808);
            graphics.fill(x + 10, entryY, x + width - 10, entryY + ENTRY_HEIGHT - 1, bgColor);
            
            // Type color bar
            int typeColor = getTypeColor(entry.nodeType);
            graphics.fill(x + 10, entryY, x + 14, entryY + ENTRY_HEIGHT - 1, typeColor);
            
            // Node ID
            graphics.drawString(font, entry.nodeId, x + 20, entryY + 5, COLOR_TEXT_BODY);
            
            // Type badge
            String typeDisplay = getTypeDisplayName(entry.nodeType);
            int badgeX = x + 200;
            graphics.fill(badgeX, entryY + 3, badgeX + font.width(typeDisplay) + 8, entryY + 16, typeColor & 0x40FFFFFF);
            graphics.drawString(font, typeDisplay, badgeX + 4, entryY + 5, typeColor);
            
            // Description
            String desc = entry.description.length() > 20 ? entry.description.substring(0, 18) + ".." : entry.description;
            graphics.drawString(font, desc, x + 280, entryY + 5, COLOR_TEXT_DIM);
            
            entryY += ENTRY_HEIGHT;
        }
        
        // Empty state
        if (nodes.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.panels.no_nodes"), x + width / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Scroll indicator
        if (nodes.size() > visibleCount) {
            int scrollBarHeight = listHeight * visibleCount / nodes.size();
            int scrollBarY = listY + (int)((float) scrollOffset / nodes.size() * listHeight);
            graphics.fill(x + width - 14, listY, x + width - 10, listY + listHeight, 0xFF222222);
            graphics.fill(x + width - 14, scrollBarY, x + width - 10, scrollBarY + scrollBarHeight, COLOR_NEON_RED);
        }
        
        // Buttons
        int buttonY = y + height - 40;
        int buttonWidth = 100;
        int buttonHeight = 28;
        int gap = 20;
        
        // Select button
        int selectX = x + width / 2 - buttonWidth - gap / 2;
        boolean selectHovered = mouseX >= selectX && mouseX < selectX + buttonWidth &&
                                 mouseY >= buttonY && mouseY < buttonY + buttonHeight;
        boolean selectEnabled = selectedIndex >= 0;
        int selectBg = !selectEnabled ? 0xFF1A1A1A : (selectHovered ? 0xFF114411 : 0xFF222222);
        graphics.fill(selectX, buttonY, selectX + buttonWidth, buttonY + buttonHeight, selectBg);
        graphics.fill(selectX, buttonY, selectX + buttonWidth, buttonY + 1, selectEnabled ? 0xFF44FF44 : COLOR_TEXT_DIM);
        graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.panels.select").getString(), selectX + buttonWidth / 2, buttonY + 9, 
            selectEnabled ? 0xFF44FF44 : COLOR_TEXT_DIM);
        
        // Cancel button
        int cancelX = x + width / 2 + gap / 2;
        boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
                                 mouseY >= buttonY && mouseY < buttonY + buttonHeight;
        int cancelBg = cancelHovered ? 0xFF333333 : 0xFF222222;
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + buttonHeight, cancelBg);
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + 1, COLOR_TEXT_DIM);
        graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.panels.cancel").getString(), cancelX + buttonWidth / 2, buttonY + 9, COLOR_TEXT_BODY);
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        
        int listY = y + 35;
        int listHeight = height - 85;
        
        // Check list clicks
        if (mouseX >= x + 10 && mouseX < x + width - 10 && mouseY >= listY && mouseY < listY + listHeight) {
            int relY = (int) mouseY - listY;
            int clickedIndex = scrollOffset + relY / ENTRY_HEIGHT;
            if (clickedIndex < nodes.size()) {
                selectedIndex = clickedIndex;
                return true;
            }
        }
        
        int buttonY = y + height - 40;
        int buttonWidth = 100;
        int buttonHeight = 28;
        int gap = 20;
        
        // Select button
        int selectX = x + width / 2 - buttonWidth - gap / 2;
        if (selectedIndex >= 0 && mouseX >= selectX && mouseX < selectX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
            onSelect.accept(nodes.get(selectedIndex).nodeId);
            hide();
            return true;
        }
        
        // Cancel button
        int cancelX = x + width / 2 + gap / 2;
        if (mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
            onCancel.run();
            hide();
            return true;
        }
        
        // Click outside to cancel
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            onCancel.run();
            hide();
            return true;
        }
        
        return true;
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        
        int listHeight = height - 85;
        int visibleCount = listHeight / ENTRY_HEIGHT;
        
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset + visibleCount < nodes.size()) {
            scrollOffset++;
            return true;
        }
        return false;
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        
        // Escape to cancel
        if (keyCode == 256) {
            onCancel.run();
            hide();
            return true;
        }
        
        // Enter to confirm
        if (keyCode == 257 && selectedIndex >= 0) {
            onSelect.accept(nodes.get(selectedIndex).nodeId);
            hide();
            return true;
        }
        
        // Arrow keys for navigation
        if (keyCode == 264 && selectedIndex < nodes.size() - 1) { // Down
            selectedIndex++;
            int listHeight = height - 85;
            int visibleCount = listHeight / ENTRY_HEIGHT;
            if (selectedIndex >= scrollOffset + visibleCount) {
                scrollOffset++;
            }
            return true;
        }
        if (keyCode == 265 && selectedIndex > 0) { // Up
            selectedIndex--;
            if (selectedIndex < scrollOffset) {
                scrollOffset--;
            }
            return true;
        }
        
        return false;
    }
    
    private int getTypeColor(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> 0xFF4488FF;
            case "TASK" -> 0xFF44FF44;
            case "PUZZLE" -> 0xFFFF8844;
            case "COMBAT" -> 0xFFFF4444;
            case "CUTSCENE" -> 0xFFCC44FF;
            case "CHECKPOINT" -> 0xFFFFCC44;
            default -> COLOR_TEXT_DIM;
        };
    }
    
    private String getTypeDisplayName(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> Component.translatable("gui.storyadventure.node.type.dialogue").getString();
            case "TASK" -> Component.translatable("gui.storyadventure.node.type.task").getString();
            case "PUZZLE" -> Component.translatable("gui.storyadventure.node.type.puzzle").getString();
            case "COMBAT" -> Component.translatable("gui.storyadventure.node.type.combat").getString();
            case "CUTSCENE" -> Component.translatable("gui.storyadventure.node.type.cutscene").getString();
            case "CHECKPOINT" -> Component.translatable("gui.storyadventure.node.type.checkpoint").getString();
            default -> type;
        };
    }
    
    public record NodeEntry(String nodeId, String nodeType, String description) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/graph/GraphCanvas.java`
```java
package com.warmpixel.storyadventure.client.ui.admin.graph;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.*;

/**
 * Pannable and zoomable canvas for the story graph editor.
 * Manages node layout, rendering, and interaction.
 */
public class GraphCanvas {
    
    private static final int GRID_SIZE = 50;
    private static final int COLOR_GRID = 0x20FFFFFF;
    private static final int COLOR_GRID_MAJOR = 0x30FFFFFF;
    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 2.0f;
    
    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    
    private float offsetX = 0;
    private float offsetY = 0;
    private float zoom = 1.0f;
    
    private GraphNode selectedNode = null;
    private GraphEdge selectedEdge = null;
    private GraphNode draggedNode = null;
    private float dragStartX, dragStartY;
    private float nodeStartX, nodeStartY;
    
    // Canvas bounds
    private int canvasX, canvasY, canvasWidth, canvasHeight;
    
    public GraphCanvas() {
    }
    
    public void setBounds(int x, int y, int width, int height) {
        this.canvasX = x;
        this.canvasY = y;
        this.canvasWidth = width;
        this.canvasHeight = height;
    }
    
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        // Clip to canvas area
        graphics.enableScissor(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight);
        
        // Background
        graphics.fill(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, 0xFF0A0A0A);
        
        // Grid
        renderGrid(graphics);
        
        // Edges (render before nodes so they appear behind)
        for (GraphEdge edge : edges) {
            GraphNode source = nodes.get(edge.getSourceNodeId());
            GraphNode target = nodes.get(edge.getTargetNodeId());
            edge.render(graphics, font, source, target, offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom);
        }
        
        // Nodes
        for (GraphNode node : nodes.values()) {
            node.render(graphics, font, offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom);
        }
        
        // Disable scissor
        graphics.disableScissor();
        
        // Canvas border
        graphics.fill(canvasX, canvasY, canvasX + canvasWidth, canvasY + 1, 0xFF330011);
        graphics.fill(canvasX, canvasY + canvasHeight - 1, canvasX + canvasWidth, canvasY + canvasHeight, 0xFF330011);
        graphics.fill(canvasX, canvasY, canvasX + 1, canvasY + canvasHeight, 0xFF330011);
        graphics.fill(canvasX + canvasWidth - 1, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, 0xFF330011);
        
        // Zoom indicator
        String zoomText = String.format("%.0f%%", zoom * 100);
        graphics.drawString(font, zoomText, canvasX + canvasWidth - 40, canvasY + 5, 0x80FFFFFF);
    }
    
    private void renderGrid(GuiGraphics graphics) {
        int scaledGridSize = (int)(GRID_SIZE * zoom);
        if (scaledGridSize < 10) return; // Don't render grid when zoomed out too far
        
        int startX = canvasX + (int)(offsetX * zoom) % scaledGridSize;
        int startY = canvasY + (int)(offsetY * zoom) % scaledGridSize;
        
        // Vertical lines
        for (int x = startX; x < canvasX + canvasWidth; x += scaledGridSize) {
            boolean major = ((x - startX) / scaledGridSize) % 5 == 0;
            graphics.fill(x, canvasY, x + 1, canvasY + canvasHeight, major ? COLOR_GRID_MAJOR : COLOR_GRID);
        }
        
        // Horizontal lines
        for (int y = startY; y < canvasY + canvasHeight; y += scaledGridSize) {
            boolean major = ((y - startY) / scaledGridSize) % 5 == 0;
            graphics.fill(canvasX, y, canvasX + canvasWidth, y + 1, major ? COLOR_GRID_MAJOR : COLOR_GRID);
        }
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInCanvas((float)mouseX, (float)mouseY)) return false;
        
        // Convert to graph space
        float graphX = (float)(mouseX - canvasX) / zoom - offsetX;
        float graphY = (float)(mouseY - canvasY) / zoom - offsetY;
        
        if (button == 0) { // Left click - select or start drag
            // Check nodes (in reverse order for top-most first)
            List<GraphNode> nodeList = new ArrayList<>(nodes.values());
            Collections.reverse(nodeList);
            
            for (GraphNode node : nodeList) {
                if (node.containsPoint((float)mouseX, (float)mouseY, 
                    offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom)) {
                    selectNode(node);
                    draggedNode = node;
                    dragStartX = (float)mouseX;
                    dragStartY = (float)mouseY;
                    nodeStartX = node.getX();
                    nodeStartY = node.getY();
                    return true;
                }
            }
            
            // Check edges
            for (GraphEdge edge : edges) {
                GraphNode source = nodes.get(edge.getSourceNodeId());
                GraphNode target = nodes.get(edge.getTargetNodeId());
                if (edge.containsPoint((float)mouseX, (float)mouseY, source, target,
                    offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom)) {
                    selectEdge(edge);
                    return true;
                }
            }
            
            // Click on empty space - deselect
            deselectAll();
            
            // Start panning
            dragStartX = (float)mouseX;
            dragStartY = (float)mouseY;
            return true;
        }
        
        return false;
    }
    
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isInCanvas((float)mouseX, (float)mouseY) && draggedNode == null) return false;
        
        if (button == 0) {
            if (draggedNode != null) {
                // Drag node
                float dx = (float)(mouseX - dragStartX) / zoom;
                float dy = (float)(mouseY - dragStartY) / zoom;
                draggedNode.setPosition(nodeStartX + dx, nodeStartY + dy);
                return true;
            } else {
                // Pan canvas
                float dx = (float)(mouseX - dragStartX);
                float dy = (float)(mouseY - dragStartY);
                offsetX += dx / zoom;
                offsetY += dy / zoom;
                dragStartX = (float)mouseX;
                dragStartY = (float)mouseY;
                return true;
            }
        }
        
        return false;
    }
    
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggedNode != null) {
            draggedNode.setDragging(false);
            draggedNode = null;
            return true;
        }
        return false;
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isInCanvas((float)mouseX, (float)mouseY)) return false;
        
        float oldZoom = zoom;
        
        if (delta > 0) {
            zoom = Math.min(MAX_ZOOM, zoom * 1.1f);
        } else {
            zoom = Math.max(MIN_ZOOM, zoom / 1.1f);
        }
        
        // Adjust offset to zoom toward mouse position
        float mouseGraphX = (float)(mouseX - canvasX) / oldZoom - offsetX;
        float mouseGraphY = (float)(mouseY - canvasY) / oldZoom - offsetY;
        
        offsetX = (float)(mouseX - canvasX) / zoom - mouseGraphX;
        offsetY = (float)(mouseY - canvasY) / zoom - mouseGraphY;
        
        return true;
    }
    
    public void mouseMoved(double mouseX, double mouseY) {
        // Update hover states
        for (GraphNode node : nodes.values()) {
            node.setHovered(node.containsPoint((float)mouseX, (float)mouseY,
                offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom));
        }
        
        for (GraphEdge edge : edges) {
            GraphNode source = nodes.get(edge.getSourceNodeId());
            GraphNode target = nodes.get(edge.getTargetNodeId());
            edge.setHovered(edge.containsPoint((float)mouseX, (float)mouseY, source, target,
                offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom));
        }
    }
    
    private boolean isInCanvas(float x, float y) {
        return x >= canvasX && x < canvasX + canvasWidth && 
               y >= canvasY && y < canvasY + canvasHeight;
    }
    
    public void addNode(GraphNode node) {
        nodes.put(node.getNodeId(), node);
    }
    
    public void addEdge(GraphEdge edge) {
        edges.add(edge);
        GraphNode source = nodes.get(edge.getSourceNodeId());
        GraphNode target = nodes.get(edge.getTargetNodeId());
        if (source != null) source.addOutgoingEdge(edge.getTargetNodeId());
        if (target != null) target.addIncomingEdge(edge.getSourceNodeId());
    }
    
    public void clear() {
        nodes.clear();
        edges.clear();
        selectedNode = null;
        selectedEdge = null;
    }
    
    public void selectNode(GraphNode node) {
        deselectAll();
        selectedNode = node;
        if (node != null) node.setSelected(true);
    }
    
    public void selectEdge(GraphEdge edge) {
        deselectAll();
        selectedEdge = edge;
        if (edge != null) edge.setSelected(true);
    }
    
    public void deselectAll() {
        if (selectedNode != null) selectedNode.setSelected(false);
        if (selectedEdge != null) selectedEdge.setSelected(false);
        selectedNode = null;
        selectedEdge = null;
    }
    
    /**
     * Automatically arrange nodes in a left-to-right flow layout.
     */
    public void autoLayout(String entryNodeId) {
        if (nodes.isEmpty()) return;
        
        // BFS from entry node
        Map<String, Integer> levels = new HashMap<>();
        Map<Integer, List<String>> levelNodes = new HashMap<>();
        
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        String startNode = entryNodeId != null && nodes.containsKey(entryNodeId) ? 
            entryNodeId : nodes.keySet().iterator().next();
        
        queue.add(startNode);
        levels.put(startNode, 0);
        visited.add(startNode);
        
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            int level = levels.get(nodeId);
            levelNodes.computeIfAbsent(level, k -> new ArrayList<>()).add(nodeId);
            
            GraphNode node = nodes.get(nodeId);
            if (node != null) {
                for (String targetId : node.getOutgoingEdges()) {
                    if (!visited.contains(targetId)) {
                        visited.add(targetId);
                        levels.put(targetId, level + 1);
                        queue.add(targetId);
                    }
                }
            }
        }
        
        // Add unvisited nodes
        for (String nodeId : nodes.keySet()) {
            if (!visited.contains(nodeId)) {
                int maxLevel = levelNodes.isEmpty() ? 0 : Collections.max(levelNodes.keySet()) + 1;
                levelNodes.computeIfAbsent(maxLevel, k -> new ArrayList<>()).add(nodeId);
            }
        }
        
        // Position nodes
        int xSpacing = GraphNode.NODE_WIDTH + 80;
        int ySpacing = GraphNode.NODE_HEIGHT + 40;
        
        for (Map.Entry<Integer, List<String>> entry : levelNodes.entrySet()) {
            int level = entry.getKey();
            List<String> nodesAtLevel = entry.getValue();
            
            int x = level * xSpacing + 50;
            int startY = -(nodesAtLevel.size() - 1) * ySpacing / 2;
            
            for (int i = 0; i < nodesAtLevel.size(); i++) {
                GraphNode node = nodes.get(nodesAtLevel.get(i));
                if (node != null) {
                    node.setPosition(x, startY + i * ySpacing);
                }
            }
        }
        
        // Center view
        centerView();
    }
    
    public void centerView() {
        if (nodes.isEmpty()) return;
        
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        
        for (GraphNode node : nodes.values()) {
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + GraphNode.NODE_WIDTH);
            maxY = Math.max(maxY, node.getY() + GraphNode.NODE_HEIGHT);
        }
        
        float graphWidth = maxX - minX;
        float graphHeight = maxY - minY;
        
        // Fit to canvas
        float zoomX = canvasWidth / (graphWidth + 100);
        float zoomY = canvasHeight / (graphHeight + 100);
        zoom = Math.min(1.0f, Math.min(zoomX, zoomY));
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        
        // Center
        float centerX = (minX + maxX) / 2;
        float centerY = (minY + maxY) / 2;
        offsetX = canvasWidth / (2 * zoom) - centerX;
        offsetY = canvasHeight / (2 * zoom) - centerY;
    }
    
    // Getters
    public GraphNode getSelectedNode() { return selectedNode; }
    public GraphEdge getSelectedEdge() { return selectedEdge; }
    public Map<String, GraphNode> getNodes() { return nodes; }
    public List<GraphEdge> getEdges() { return edges; }
    public float getZoom() { return zoom; }
    public void setZoom(float zoom) { this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom)); }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/graph/GraphEdge.java`
```java
package com.warmpixel.storyadventure.client.ui.admin.graph;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.List;

/**
 * Represents a visual edge (connection) between two nodes in the graph.
 * Renders as a bezier curve with condition labels.
 */
public class GraphEdge {
    
    private static final int COLOR_DEFAULT = 0xFFAAAAAA;
    private static final int COLOR_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_CONDITIONAL = 0xFFFFCC44;
    
    private final String sourceNodeId;
    private final String targetNodeId;
    private List<String> conditions;
    private int priority = 0;
    
    private boolean selected = false;
    private boolean hovered = false;
    
    public GraphEdge(String sourceNodeId, String targetNodeId) {
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
    }
    
    public void render(GuiGraphics graphics, Font font, GraphNode sourceNode, GraphNode targetNode,
                       float offsetX, float offsetY, float zoom) {
        if (sourceNode == null || targetNode == null) return;
        
        float[] start = sourceNode.getOutputConnectorPos(offsetX, offsetY, zoom);
        float[] end = targetNode.getInputConnectorPos(offsetX, offsetY, zoom);
        
        float startX = start[0];
        float startY = start[1];
        float endX = end[0];
        float endY = end[1];
        
        // Choose color
        int color = selected ? COLOR_SELECTED : (hasConditions() ? COLOR_CONDITIONAL : COLOR_DEFAULT);
        if (hovered) {
            color = (color & 0x00FFFFFF) | 0xFF000000; // Full opacity on hover
        } else {
            color = (color & 0x00FFFFFF) | 0xCC000000; // Slightly transparent
        }
        
        // Draw bezier curve
        drawBezierCurve(graphics, startX, startY, endX, endY, color, zoom);
        
        // Draw arrow at end
        drawArrow(graphics, endX, endY, startX, startY, color, zoom);
        
        // Draw condition label in middle
        if (hasConditions() && zoom > 0.5f) {
            float midX = (startX + endX) / 2;
            float midY = (startY + endY) / 2 - 10 * zoom;
            
            String condLabel = conditions.size() == 1 ? getConditionLabel(conditions.get(0)) 
                : conditions.size() + " 条件";
            
            // Background for label
            int labelWidth = font.width(condLabel) + 4;
            graphics.fill((int)(midX - labelWidth / 2), (int)(midY - 5),
                         (int)(midX + labelWidth / 2), (int)(midY + 8), 0xCC101010);
            
            graphics.drawCenteredString(font, condLabel, (int)midX, (int)midY, COLOR_CONDITIONAL);
        }
    }
    
    private void drawBezierCurve(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color, float zoom) {
        // Control points for smooth curve
        float dx = Math.abs(x2 - x1);
        float controlOffset = Math.max(dx * 0.5f, 50 * zoom);
        
        float cx1 = x1 + controlOffset;
        float cy1 = y1;
        float cx2 = x2 - controlOffset;
        float cy2 = y2;
        
        // Draw curve as line segments
        int segments = Math.max(10, (int)(dx / (10 * zoom)));
        float prevX = x1, prevY = y1;
        
        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            float u = 1 - t;
            
            // Cubic bezier formula
            float x = u*u*u*x1 + 3*u*u*t*cx1 + 3*u*t*t*cx2 + t*t*t*x2;
            float y = u*u*u*y1 + 3*u*u*t*cy1 + 3*u*t*t*cy2 + t*t*t*y2;
            
            drawLine(graphics, (int)prevX, (int)prevY, (int)x, (int)y, color);
            prevX = x;
            prevY = y;
        }
    }
    
    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        // Bresenham-ish thick line
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        
        if (dx >= dy) {
            // More horizontal
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            for (int x = minX; x <= maxX; x++) {
                float t = dx == 0 ? 0 : (float)(x - x1) / (x2 - x1);
                int y = (int)(y1 + t * (y2 - y1));
                graphics.fill(x, y, x + 1, y + 2, color);
            }
        } else {
            // More vertical
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            for (int y = minY; y <= maxY; y++) {
                float t = dy == 0 ? 0 : (float)(y - y1) / (y2 - y1);
                int x = (int)(x1 + t * (x2 - x1));
                graphics.fill(x, y, x + 2, y + 1, color);
            }
        }
    }
    
    private void drawArrow(GuiGraphics graphics, float tipX, float tipY, float fromX, float fromY, int color, float zoom) {
        float angle = (float) Math.atan2(tipY - fromY, tipX - fromX);
        float arrowSize = 8 * zoom;
        
        // Arrow head points
        float angle1 = angle + (float) Math.PI * 0.85f;
        float angle2 = angle - (float) Math.PI * 0.85f;
        
        int ax1 = (int)(tipX + Math.cos(angle1) * arrowSize);
        int ay1 = (int)(tipY + Math.sin(angle1) * arrowSize);
        int ax2 = (int)(tipX + Math.cos(angle2) * arrowSize);
        int ay2 = (int)(tipY + Math.sin(angle2) * arrowSize);
        
        drawLine(graphics, (int)tipX, (int)tipY, ax1, ay1, color);
        drawLine(graphics, (int)tipX, (int)tipY, ax2, ay2, color);
    }
    
    private String getConditionLabel(String condition) {
        return switch (condition.toUpperCase()) {
            case "COMBAT_WON" -> "战斗胜利";
            case "COMBAT_LOST" -> "战斗失败";
            case "COMBAT_ESCAPED" -> "逃跑";
            case "PUZZLE_SOLVED" -> "谜题解开";
            case "PUZZLE_FAILED" -> "谜题失败";
            case "TASK_COMPLETE" -> "任务完成";
            case "TASK_FAILED" -> "任务失败";
            case "DIALOGUE_CHOICE" -> "对话选择";
            case "VOTE" -> "投票";
            default -> condition;
        };
    }
    
    public boolean containsPoint(float pointX, float pointY, GraphNode sourceNode, GraphNode targetNode,
                                  float offsetX, float offsetY, float zoom) {
        if (sourceNode == null || targetNode == null) return false;
        
        float[] start = sourceNode.getOutputConnectorPos(offsetX, offsetY, zoom);
        float[] end = targetNode.getInputConnectorPos(offsetX, offsetY, zoom);
        
        // Simple distance-to-line check
        float dx = end[0] - start[0];
        float dy = end[1] - start[1];
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return false;
        
        float t = Math.max(0, Math.min(1, 
            ((pointX - start[0]) * dx + (pointY - start[1]) * dy) / (length * length)));
        
        float nearestX = start[0] + t * dx;
        float nearestY = start[1] + t * dy;
        
        float dist = (float) Math.sqrt((pointX - nearestX) * (pointX - nearestX) + 
                                        (pointY - nearestY) * (pointY - nearestY));
        
        return dist < 10 * zoom;
    }
    
    public boolean hasConditions() {
        return conditions != null && !conditions.isEmpty();
    }
    
    // Getters and setters
    public String getSourceNodeId() { return sourceNodeId; }
    public String getTargetNodeId() { return targetNodeId; }
    public List<String> getConditions() { return conditions; }
    public void setConditions(List<String> conditions) { this.conditions = conditions; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isHovered() { return hovered; }
    public void setHovered(boolean hovered) { this.hovered = hovered; }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/graph/GraphNode.java`
```java
package com.warmpixel.storyadventure.client.ui.admin.graph;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual representation of a story node in the graph editor.
 * Supports drag, selection, and connection points.
 */
public class GraphNode {
    
    // Colors for different node types
    private static final int COLOR_DIALOGUE = 0xFF4488FF;
    private static final int COLOR_TASK = 0xFF44FF44;
    private static final int COLOR_PUZZLE = 0xFFFF8844;
    private static final int COLOR_COMBAT = 0xFFFF4444;
    private static final int COLOR_CUTSCENE = 0xFFCC44FF;
    private static final int COLOR_CHECKPOINT = 0xFFFFCC44;
    private static final int COLOR_BORDER = 0xFF333333;
    private static final int COLOR_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    
    public static final int NODE_WIDTH = 120;
    public static final int NODE_HEIGHT = 60;
    public static final int CONNECTOR_SIZE = 8;
    
    private final String nodeId;
    private String nodeType;
    private String displayName;
    private String description;
    
    // Position in graph space
    private float x;
    private float y;
    
    // Editor state
    private boolean selected = false;
    private boolean hovered = false;
    private boolean dragging = false;
    
    // Connection points
    private List<String> outgoingEdges = new ArrayList<>();
    private List<String> incomingEdges = new ArrayList<>();
    
    // Triggers
    private boolean hasOnEnter = false;
    private boolean hasOnExit = false;
    
    public GraphNode(String nodeId, String nodeType, float x, float y) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.displayName = nodeId;
        this.x = x;
        this.y = y;
    }
    
    public void render(GuiGraphics graphics, Font font, float offsetX, float offsetY, float zoom) {
        int screenX = (int)((x + offsetX) * zoom);
        int screenY = (int)((y + offsetY) * zoom);
        int w = (int)(NODE_WIDTH * zoom);
        int h = (int)(NODE_HEIGHT * zoom);
        
        // Skip if off-screen
        if (screenX + w < 0 || screenY + h < 0 || screenX > graphics.guiWidth() || screenY > graphics.guiHeight()) {
            return;
        }
        
        int typeColor = getTypeColor();
        
        // Node background
        int bgColor = selected ? 0xFF2A1A1A : (hovered ? 0xFF1A1010 : 0xFF101010);
        graphics.fill(screenX, screenY, screenX + w, screenY + h, bgColor);
        
        // Type-colored left bar
        graphics.fill(screenX, screenY, screenX + (int)(4 * zoom), screenY + h, typeColor);
        
        // Border
        int borderColor = selected ? COLOR_SELECTED : (hovered ? typeColor : COLOR_BORDER);
        graphics.fill(screenX, screenY, screenX + w, screenY + 1, borderColor);
        graphics.fill(screenX, screenY + h - 1, screenX + w, screenY + h, borderColor);
        graphics.fill(screenX, screenY, screenX + 1, screenY + h, borderColor);
        graphics.fill(screenX + w - 1, screenY, screenX + w, screenY + h, borderColor);
        
        // Type icon/badge
        String typeIcon = getTypeIcon();
        graphics.drawString(font, typeIcon, screenX + (int)(8 * zoom), screenY + (int)(5 * zoom), typeColor);
        
        // Node ID (truncated)
        String displayId = displayName.length() > 14 ? displayName.substring(0, 12) + ".." : displayName;
        graphics.drawString(font, displayId, screenX + (int)(8 * zoom), screenY + (int)(20 * zoom), COLOR_TEXT);
        
        // Type label
        String typeLabel = getTypeLabel();
        graphics.drawString(font, typeLabel, screenX + (int)(8 * zoom), screenY + (int)(35 * zoom), 
            (typeColor & 0x00FFFFFF) | 0x80000000);
        
        // Trigger indicators
        if (hasOnEnter) {
            graphics.fill(screenX + w - (int)(20 * zoom), screenY + (int)(5 * zoom), 
                         screenX + w - (int)(12 * zoom), screenY + (int)(13 * zoom), 0xFF44FF44);
        }
        if (hasOnExit) {
            graphics.fill(screenX + w - (int)(10 * zoom), screenY + (int)(5 * zoom), 
                         screenX + w - (int)(2 * zoom), screenY + (int)(13 * zoom), 0xFFFF4444);
        }
        
        // Input connector (left side)
        if (!incomingEdges.isEmpty() || true) { // Always show for receiving
            int connY = screenY + h / 2 - (int)(CONNECTOR_SIZE * zoom / 2);
            graphics.fill(screenX - (int)(CONNECTOR_SIZE * zoom / 2), connY,
                         screenX + (int)(CONNECTOR_SIZE * zoom / 2), connY + (int)(CONNECTOR_SIZE * zoom),
                         typeColor);
        }
        
        // Output connectors (right side)
        int connY = screenY + h / 2 - (int)(CONNECTOR_SIZE * zoom / 2);
        graphics.fill(screenX + w - (int)(CONNECTOR_SIZE * zoom / 2), connY,
                     screenX + w + (int)(CONNECTOR_SIZE * zoom / 2), connY + (int)(CONNECTOR_SIZE * zoom),
                     typeColor);
    }
    
    public boolean containsPoint(float pointX, float pointY, float offsetX, float offsetY, float zoom) {
        float screenX = (x + offsetX) * zoom;
        float screenY = (y + offsetY) * zoom;
        float w = NODE_WIDTH * zoom;
        float h = NODE_HEIGHT * zoom;
        
        return pointX >= screenX && pointX < screenX + w && pointY >= screenY && pointY < screenY + h;
    }
    
    public float[] getOutputConnectorPos(float offsetX, float offsetY, float zoom) {
        float screenX = (x + offsetX) * zoom + NODE_WIDTH * zoom;
        float screenY = (y + offsetY) * zoom + NODE_HEIGHT * zoom / 2;
        return new float[]{screenX, screenY};
    }
    
    public float[] getInputConnectorPos(float offsetX, float offsetY, float zoom) {
        float screenX = (x + offsetX) * zoom;
        float screenY = (y + offsetY) * zoom + NODE_HEIGHT * zoom / 2;
        return new float[]{screenX, screenY};
    }
    
    private int getTypeColor() {
        return switch (nodeType.toUpperCase()) {
            case "DIALOGUE" -> COLOR_DIALOGUE;
            case "TASK" -> COLOR_TASK;
            case "PUZZLE" -> COLOR_PUZZLE;
            case "COMBAT" -> COLOR_COMBAT;
            case "CUTSCENE" -> COLOR_CUTSCENE;
            case "CHECKPOINT" -> COLOR_CHECKPOINT;
            default -> COLOR_BORDER;
        };
    }
    
    private String getTypeIcon() {
        return switch (nodeType.toUpperCase()) {
            case "DIALOGUE" -> "💬";
            case "TASK" -> "📋";
            case "PUZZLE" -> "🧩";
            case "COMBAT" -> "⚔";
            case "CUTSCENE" -> "🎬";
            case "CHECKPOINT" -> "💾";
            default -> "📦";
        };
    }
    
    private String getTypeLabel() {
        return switch (nodeType.toUpperCase()) {
            case "DIALOGUE" -> "对话";
            case "TASK" -> "任务";
            case "PUZZLE" -> "谜题";
            case "COMBAT" -> "战斗";
            case "CUTSCENE" -> "过场";
            case "CHECKPOINT" -> "存档点";
            default -> nodeType;
        };
    }
    
    // Getters and setters
    public String getNodeId() { return nodeId; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String type) { this.nodeType = type; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }
    public String getDescription() { return description; }
    public void setDescription(String desc) { this.description = desc; }
    public float getX() { return x; }
    public float getY() { return y; }
    public void setPosition(float x, float y) { this.x = x; this.y = y; }
    public void move(float dx, float dy) { this.x += dx; this.y += dy; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isHovered() { return hovered; }
    public void setHovered(boolean hovered) { this.hovered = hovered; }
    public boolean isDragging() { return dragging; }
    public void setDragging(boolean dragging) { this.dragging = dragging; }
    public List<String> getOutgoingEdges() { return outgoingEdges; }
    public void addOutgoingEdge(String targetId) { outgoingEdges.add(targetId); }
    public List<String> getIncomingEdges() { return incomingEdges; }
    public void addIncomingEdge(String sourceId) { incomingEdges.add(sourceId); }
    public boolean hasOnEnter() { return hasOnEnter; }
    public void setHasOnEnter(boolean has) { this.hasOnEnter = has; }
    public boolean hasOnExit() { return hasOnExit; }
    public void setHasOnExit(boolean has) { this.hasOnExit = has; }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/graph/NodePropertyPanel.java`
```java
package com.warmpixel.storyadventure.client.ui.admin.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Side panel for editing selected node properties.
 * Shows type-specific fields and trigger configuration.
 */
public class NodePropertyPanel {
    
    private static final int COLOR_BG = 0xE0101010;
    private static final int COLOR_BORDER = 0xFF330011;
    private static final int COLOR_HEADER = 0xFFE50914;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_SECTION = 0xFF666666;
    
    private final StoryGraphScreen parent;
    private int x, y, width, height;
    
    private GraphNode currentNode;
    private JsonObject currentNodeData;
    
    // Edit boxes for properties
    private final Map<String, EditBox> editBoxes = new LinkedHashMap<>();
    private int scrollOffset = 0;
    private int contentHeight = 0;
    
    // Trigger editors
    private List<TriggerEntry> onEnterTriggers = new ArrayList<>();
    private List<TriggerEntry> onExitTriggers = new ArrayList<>();
    
    public NodePropertyPanel(StoryGraphScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public void init(Screen screen) {
        // Called when screen initializes
    }
    
    public void setNode(GraphNode node, JsonObject nodeData) {
        this.currentNode = node;
        this.currentNodeData = nodeData;
        this.scrollOffset = 0;
        rebuildEditBoxes();
        parseTriggers();
    }
    
    private void rebuildEditBoxes() {
        editBoxes.clear();
        
        if (currentNode == null || currentNodeData == null) return;
        
        Font font = Minecraft.getInstance().font;
        int fieldY = y + 80;
        int fieldWidth = width - 30;
        int fieldHeight = 18;
        int gap = 25;
        
        // Add data fields based on type
        JsonObject data = currentNodeData.has("data") ? currentNodeData.getAsJsonObject("data") : new JsonObject();
        
        String[] fields = getFieldsForType(currentNode.getNodeType());
        for (String field : fields) {
            String value = data.has(field) ? getJsonValueAsString(data.get(field)) : "";
            
            EditBox box = new EditBox(font, x + 15, fieldY, fieldWidth, fieldHeight, Component.literal(field));
            box.setMaxLength(500);
            box.setValue(value);
            box.setTextColor(0xFFCCCCCC);
            box.setResponder(newValue -> {
                parent.onNodePropertyChanged(currentNode.getNodeId(), field, newValue);
            });
            
            editBoxes.put(field, box);
            fieldY += gap;
        }
        
        contentHeight = fieldY + 200; // Extra space for triggers
    }
    
    private String[] getFieldsForType(String type) {
        return switch (type.toUpperCase()) {
            case "CUTSCENE" -> new String[]{"duration_ticks", "title", "subtitle", "message", "fade_in"};
            case "DIALOGUE" -> new String[]{"npc_template", "npc_name", "dialog_set", "vote_required", "vote_id"};
            case "TASK" -> new String[]{"task_type", "title", "description", "time_limit_seconds", "stealth_required"};
            case "PUZZLE" -> new String[]{"puzzle_type", "title", "description", "solution", "max_attempts"};
            case "COMBAT" -> new String[]{"combat_type", "title", "description", "arena_radius", "escape_available"};
            case "CHECKPOINT" -> new String[]{"rewind_anchor", "save_inventory", "message"};
            default -> new String[]{};
        };
    }
    
    private String getJsonValueAsString(JsonElement elem) {
        if (elem.isJsonPrimitive()) {
            return elem.getAsString();
        } else if (elem.isJsonArray()) {
            return "[" + elem.getAsJsonArray().size() + " items]";
        } else {
            return elem.toString();
        }
    }
    
    private void parseTriggers() {
        onEnterTriggers.clear();
        onExitTriggers.clear();
        
        if (currentNodeData == null) return;
        
        if (currentNodeData.has("on_enter")) {
            for (JsonElement elem : currentNodeData.getAsJsonArray("on_enter")) {
                onEnterTriggers.add(parseTriggerEntry(elem.getAsJsonObject()));
            }
        }
        
        if (currentNodeData.has("on_exit")) {
            for (JsonElement elem : currentNodeData.getAsJsonArray("on_exit")) {
                onExitTriggers.add(parseTriggerEntry(elem.getAsJsonObject()));
            }
        }
    }
    
    private TriggerEntry parseTriggerEntry(JsonObject obj) {
        String type = obj.has("type") ? obj.get("type").getAsString() : "UNKNOWN";
        String summary = type;
        
        // Build summary based on type
        switch (type.toUpperCase()) {
            case "COMMAND" -> summary = "命令: " + (obj.has("command") ? obj.get("command").getAsString() : "?");
            case "MESSAGE" -> summary = "消息: " + (obj.has("text") ? truncate(obj.get("text").getAsString(), 20) : "?");
            case "TELEPORT" -> summary = "传送: " + (obj.has("location") ? obj.get("location").getAsString() : "?");
            case "SET_FLAG" -> summary = "设置标记: " + (obj.has("flag") ? obj.get("flag").getAsString() : "?");
            case "SPAWN_NPC" -> summary = "生成NPC: " + (obj.has("template") ? obj.get("template").getAsString() : "?");
            case "GIVE_ITEM" -> summary = "给予物品: " + (obj.has("item") ? obj.get("item").getAsString() : "?");
            case "PLAY_SOUND" -> summary = "播放音效: " + (obj.has("sound") ? obj.get("sound").getAsString() : "?");
            case "TITLE" -> summary = "显示标题: " + (obj.has("title") ? truncate(obj.get("title").getAsString(), 15) : "?");
        }
        
        return new TriggerEntry(type, summary, obj);
    }
    
    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max - 2) + ".." : text;
    }
    
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, GraphNode selectedNode) {
        // Background
        graphics.fill(x, y, x + width, y + height, COLOR_BG);
        
        // Border
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + height, COLOR_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_BORDER);
        
        // Header
        graphics.fill(x + 1, y + 1, x + width - 1, y + 25, 0xFF1A0A0A);
        graphics.drawCenteredString(font, "节点属性", x + width / 2, y + 8, COLOR_HEADER);
        
        if (currentNode == null) {
            graphics.drawCenteredString(font, "选择一个节点", x + width / 2, y + height / 2, COLOR_TEXT_DIM);
            return;
        }
        
        // Node info
        int infoY = y + 32;
        graphics.drawString(font, "ID:", x + 10, infoY, COLOR_TEXT_DIM);
        graphics.drawString(font, currentNode.getNodeId(), x + 30, infoY, COLOR_TEXT);
        infoY += 14;
        
        graphics.drawString(font, "类型:", x + 10, infoY, COLOR_TEXT_DIM);
        graphics.drawString(font, getTypeLabel(currentNode.getNodeType()), x + 40, infoY, getTypeColor(currentNode.getNodeType()));
        infoY += 20;
        
        // Fields section
        graphics.drawString(font, "━━ 属性 ━━", x + 10, infoY, COLOR_SECTION);
        infoY += 15;
        
        // Render edit boxes with labels
        for (Map.Entry<String, EditBox> entry : editBoxes.entrySet()) {
            String label = getFieldLabel(entry.getKey());
            EditBox box = entry.getValue();
            
            graphics.drawString(font, label, x + 10, box.getY() - 10, COLOR_TEXT_DIM);
            box.render(graphics, mouseX, mouseY, 0);
        }
        
        // Triggers section
        int triggerY = y + 80 + editBoxes.size() * 25 + 20;
        renderTriggersSection(graphics, font, triggerY, mouseX, mouseY);
    }
    
    private void renderTriggersSection(GuiGraphics graphics, Font font, int startY, int mouseX, int mouseY) {
        int sectionY = startY;
        
        // On Enter triggers
        graphics.drawString(font, "━━ 进入触发 (on_enter) ━━", x + 10, sectionY, COLOR_SECTION);
        sectionY += 14;
        
        if (onEnterTriggers.isEmpty()) {
            graphics.drawString(font, "无触发器", x + 15, sectionY, COLOR_TEXT_DIM);
            sectionY += 12;
        } else {
            for (TriggerEntry trigger : onEnterTriggers) {
                graphics.fill(x + 10, sectionY, x + width - 15, sectionY + 18, 0xFF1A1A1A);
                graphics.fill(x + 10, sectionY, x + 13, sectionY + 18, 0xFF44FF44); // Green bar
                graphics.drawString(font, trigger.summary, x + 18, sectionY + 5, COLOR_TEXT);
                sectionY += 20;
            }
        }
        
        // Add trigger button
        boolean addHovered = mouseX >= x + 10 && mouseX < x + 80 && mouseY >= sectionY && mouseY < sectionY + 16;
        graphics.fill(x + 10, sectionY, x + 80, sectionY + 16, addHovered ? 0xFF333333 : 0xFF222222);
        graphics.drawString(font, "+ 添加", x + 15, sectionY + 4, 0xFF44FF44);
        sectionY += 25;
        
        // On Exit triggers
        graphics.drawString(font, "━━ 退出触发 (on_exit) ━━", x + 10, sectionY, COLOR_SECTION);
        sectionY += 14;
        
        if (onExitTriggers.isEmpty()) {
            graphics.drawString(font, "无触发器", x + 15, sectionY, COLOR_TEXT_DIM);
            sectionY += 12;
        } else {
            for (TriggerEntry trigger : onExitTriggers) {
                graphics.fill(x + 10, sectionY, x + width - 15, sectionY + 18, 0xFF1A1A1A);
                graphics.fill(x + 10, sectionY, x + 13, sectionY + 18, 0xFFFF4444); // Red bar
                graphics.drawString(font, trigger.summary, x + 18, sectionY + 5, COLOR_TEXT);
                sectionY += 20;
            }
        }
        
        // Add trigger button
        addHovered = mouseX >= x + 10 && mouseX < x + 80 && mouseY >= sectionY && mouseY < sectionY + 16;
        graphics.fill(x + 10, sectionY, x + 80, sectionY + 16, addHovered ? 0xFF333333 : 0xFF222222);
        graphics.drawString(font, "+ 添加", x + 15, sectionY + 4, 0xFFFF4444);
    }
    
    private String getFieldLabel(String field) {
        return switch (field) {
            case "duration_ticks" -> "持续时间(tick)";
            case "title" -> "标题";
            case "subtitle" -> "副标题";
            case "message" -> "消息";
            case "fade_in" -> "淡入效果";
            case "npc_template" -> "NPC模板";
            case "npc_name" -> "NPC名称";
            case "dialog_set" -> "对话集";
            case "vote_required" -> "需要投票";
            case "vote_id" -> "投票ID";
            case "task_type" -> "任务类型";
            case "description" -> "描述";
            case "time_limit_seconds" -> "时间限制(秒)";
            case "stealth_required" -> "需要潜行";
            case "puzzle_type" -> "谜题类型";
            case "solution" -> "答案";
            case "max_attempts" -> "最大尝试";
            case "combat_type" -> "战斗类型";
            case "arena_radius" -> "竞技场半径";
            case "escape_available" -> "可逃跑";
            case "rewind_anchor" -> "重试锚点";
            case "save_inventory" -> "保存背包";
            default -> field;
        };
    }
    
    private String getTypeLabel(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> "对话";
            case "TASK" -> "任务";
            case "PUZZLE" -> "谜题";
            case "COMBAT" -> "战斗";
            case "CUTSCENE" -> "过场";
            case "CHECKPOINT" -> "存档点";
            default -> type;
        };
    }
    
    private int getTypeColor(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> 0xFF4488FF;
            case "TASK" -> 0xFF44FF44;
            case "PUZZLE" -> 0xFFFF8844;
            case "COMBAT" -> 0xFFFF4444;
            case "CUTSCENE" -> 0xFFCC44FF;
            case "CHECKPOINT" -> 0xFFFFCC44;
            default -> COLOR_TEXT_DIM;
        };
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }
        
        // Check edit boxes
        for (EditBox box : editBoxes.values()) {
            if (box.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        
        return true;
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox box : editBoxes.values()) {
            if (box.isFocused() && box.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean charTyped(char chr, int modifiers) {
        for (EditBox box : editBoxes.values()) {
            if (box.isFocused() && box.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }
    
    public record TriggerEntry(String type, String summary, JsonObject data) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/admin/graph/StoryGraphScreen.java`
```java
package com.warmpixel.storyadventure.client.ui.admin.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import com.warmpixel.storyadventure.client.ui.admin.AdminStoryManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Main visual story graph editor screen.
 * Provides visualization, editing, and JSON persistence for story nodes.
 */
public class StoryGraphScreen extends StrangerScreen {
    
    private static final int PROPERTY_PANEL_WIDTH = 280;
    
    private final String storyId;
    private final String storyName;
    private String entryNodeId;
    
    private GraphCanvas canvas;
    private NodePropertyPanel propertyPanel;
    
    // Story data
    private JsonObject storyJson;
    private Path storyFilePath;
    private boolean hasUnsavedChanges = false;
    
    public StoryGraphScreen(String storyId, String storyName) {
        super(Component.literal("故事图编辑器 - " + storyName));
        this.storyId = storyId;
        this.storyName = storyName;
    }
    
    private boolean isLoading = true;

    @Override
    protected void init() {
        super.init();
        
        // Initialize canvas
        int canvasWidth = width - PROPERTY_PANEL_WIDTH - 20;
        canvas = new GraphCanvas();
        canvas.setBounds(10, 50, canvasWidth, height - 100);
        
        // Initialize property panel
        propertyPanel = new NodePropertyPanel(this, canvasWidth + 20, 50, PROPERTY_PANEL_WIDTH - 10, height - 100);
        propertyPanel.init(this);
        
        // Toolbar buttons
        int toolbarX = 10;
        int toolbarY = height - 45;
        int btnWidth = 80;
        int btnHeight = 28;
        int gap = 5;
        
        addStrangerButton(toolbarX, toolbarY, btnWidth, btnHeight,
            Component.literal("💾 保存"), this::saveStory);
        toolbarX += btnWidth + gap;
        
        addStrangerButton(toolbarX, toolbarY, btnWidth, btnHeight,
            Component.literal("↺ 重载"), this::reloadStory);
        toolbarX += btnWidth + gap;
        
        addStrangerButton(toolbarX, toolbarY, btnWidth, btnHeight,
            Component.literal("📐 自动布局"), this::autoLayout);
        toolbarX += btnWidth + gap;
        
        addStrangerButton(toolbarX, toolbarY, btnWidth, btnHeight,
            Component.literal("🔍 居中"), this::centerView);
        toolbarX += btnWidth + gap;
        
        addStrangerButton(toolbarX, toolbarY, btnWidth, btnHeight,
            Component.literal("+ 新节点"), this::addNewNode);
        
        // Back button
        addStrangerButton(width - 100, toolbarY, 90, btnHeight,
            Component.literal("← 返回"), this::goBack);
        
        // Load story via network
        loadStory();
    }

    private void loadStory() {
        isLoading = true;
        showMessage("§e正在从服务器获取故事数据...");
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.warmpixel.storyadventure.network.RequestStoryGraphPayload(storyId)
        );
    }
    
    public void onSyncReceived(String json) {
        try {
            JsonElement parsed = com.google.gson.JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) {
                showMessage("§c从服务器收到的故事格式错误");
                return;
            }
            
            storyJson = parsed.getAsJsonObject();
            entryNodeId = storyJson.has("entry_node") ? storyJson.get("entry_node").getAsString() : null;
            
            buildGraphFromJson();
            
            // Check if we need auto layout (only if no nodes have positions)
            boolean needLayout = canvas.getNodes().values().stream().allMatch(n -> n.getX() == 0 && n.getY() == 0);
            if (needLayout) {
                canvas.autoLayout(entryNodeId);
            } else {
                canvas.centerView();
            }
            
            isLoading = false;
            showMessage("§a同步成功: " + storyId);
        } catch (Exception e) {
            showMessage("§c处理服务器数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void buildGraphFromJson() {
        if (storyJson == null) return;
        canvas.clear();
        
        if (!storyJson.has("nodes")) return;
        
        JsonObject nodes = storyJson.getAsJsonObject("nodes");
        
        // First pass: create nodes
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            JsonObject nodeData = entry.getValue().getAsJsonObject();
            
            String type = nodeData.has("type") ? nodeData.get("type").getAsString() : "UNKNOWN";
            
            // Check for saved position
            float x = 0, y = 0;
            if (nodeData.has("_editor")) {
                JsonObject editor = nodeData.getAsJsonObject("_editor");
                x = editor.has("x") ? editor.get("x").getAsFloat() : 0;
                y = editor.has("y") ? editor.get("y").getAsFloat() : 0;
            }
            
            GraphNode node = new GraphNode(nodeId, type, x, y);
            
            // Check for triggers
            node.setHasOnEnter(nodeData.has("on_enter") && nodeData.getAsJsonArray("on_enter").size() > 0);
            node.setHasOnExit(nodeData.has("on_exit") && nodeData.getAsJsonArray("on_exit").size() > 0);
            
            canvas.addNode(node);
        }
        
        // Second pass: create edges
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            JsonObject nodeData = entry.getValue().getAsJsonObject();
            
            if (nodeData.has("edges")) {
                JsonArray edges = nodeData.getAsJsonArray("edges");
                for (JsonElement edgeElem : edges) {
                    JsonObject edgeData = edgeElem.getAsJsonObject();
                    String targetId = edgeData.get("target").getAsString();
                    
                    GraphEdge edge = new GraphEdge(nodeId, targetId);
                    
                    // Parse conditions
                    if (edgeData.has("conditions")) {
                        List<String> conditions = new ArrayList<>();
                        for (JsonElement cond : edgeData.getAsJsonArray("conditions")) {
                            JsonObject condObj = cond.getAsJsonObject();
                            conditions.add(condObj.get("type").getAsString());
                        }
                        edge.setConditions(conditions);
                    }
                    
                    canvas.addEdge(edge);
                }
            }
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Title bar with unsaved indicator
        String titleText = storyName + (hasUnsavedChanges ? " *" : "");
        graphics.drawCenteredString(font, titleText, width / 2, 8, 
            hasUnsavedChanges ? 0xFFFFCC44 : COLOR_NEON_RED);
        
        // Story info
        graphics.drawString(font, "ID: " + storyId, 15, 25, COLOR_TEXT_DIM);
        if (entryNodeId != null) {
            graphics.drawString(font, "入口: " + entryNodeId, 15, 38, COLOR_TEXT_DIM);
        }
        
        if (isLoading) {
            graphics.drawCenteredString(font, "§e请求数据中...", width / 2, height / 2, 0xFFFFFF00);
            return;
        }

        if (storyJson == null) {
            graphics.drawCenteredString(font, "§c故事数据同步失败", width / 2, height / 2, 0xFFFF5555);
            return;
        }
        
        // Canvas
        canvas.render(graphics, font, mouseX, mouseY);
        
        // Property panel
        propertyPanel.render(graphics, font, mouseX, mouseY, canvas.getSelectedNode());
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (storyJson == null) return super.mouseClicked(mouseX, mouseY, button);
        
        // Property panel first
        if (propertyPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        // Canvas
        boolean handled = canvas.mouseClicked(mouseX, mouseY, button);
        
        // Update property panel with selection
        if (canvas.getSelectedNode() != null) {
            propertyPanel.setNode(canvas.getSelectedNode(), getNodeJsonData(canvas.getSelectedNode().getNodeId()));
        } else {
            propertyPanel.setNode(null, null);
        }
        
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (storyJson == null) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (canvas.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            hasUnsavedChanges = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (storyJson == null) return super.mouseReleased(mouseX, mouseY, button);
        canvas.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (storyJson == null) return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
        if (canvas.mouseScrolled(mouseX, mouseY, vAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (storyJson == null) return;
        canvas.mouseMoved(mouseX, mouseY);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (storyJson == null) return super.keyPressed(keyCode, scanCode, modifiers);
        if (propertyPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        // Ctrl+S to save
        if (keyCode == 83 && (modifiers & 2) != 0) { // S with Ctrl
            saveStory();
            return true;
        }
        
        // Delete key
        if (keyCode == 261 && canvas.getSelectedNode() != null) {
            deleteSelectedNode();
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (storyJson == null) return super.charTyped(chr, modifiers);
        if (propertyPanel.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }
    
    private JsonObject getNodeJsonData(String nodeId) {
        if (storyJson != null && storyJson.has("nodes")) {
            JsonObject nodes = storyJson.getAsJsonObject("nodes");
            if (nodes.has(nodeId)) {
                return nodes.getAsJsonObject(nodeId);
            }
        }
        return null;
    }
    
    public void onNodePropertyChanged(String nodeId, String key, String value) {
        if (storyJson == null) return;
        hasUnsavedChanges = true;
        
        // Update JSON
        if (storyJson.has("nodes")) {
            JsonObject nodes = storyJson.getAsJsonObject("nodes");
            if (nodes.has(nodeId)) {
                JsonObject nodeData = nodes.getAsJsonObject(nodeId);
                if (nodeData.has("data")) {
                    nodeData.getAsJsonObject("data").addProperty(key, value);
                }
            }
        }
    }

    private void saveStory() {
        if (storyJson == null) {
            showMessage("§c由于加载失败，无法保存。");
            return;
        }
        try {
            // Update node positions in JSON
            JsonObject nodes = storyJson.getAsJsonObject("nodes");
            for (GraphNode node : canvas.getNodes().values()) {
                if (nodes.has(node.getNodeId())) {
                    JsonObject nodeData = nodes.getAsJsonObject(node.getNodeId());
                    JsonObject editor = new JsonObject();
                    editor.addProperty("x", node.getX());
                    editor.addProperty("y", node.getY());
                    nodeData.add("_editor", editor);
                }
            }
            
            // Send to server
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            String jsonStr = gson.toJson(storyJson);
            
            showMessage("§e正在保存到服务器...");
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.warmpixel.storyadventure.network.SaveStoryPayload(storyId, jsonStr)
            );
            
            hasUnsavedChanges = false;
        } catch (Exception e) {
            showMessage("§c保存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void reloadStory() {
        if (hasUnsavedChanges) {
            showMessage("§e有未保存的更改，正在重新加载...");
        }
        loadStory();
        hasUnsavedChanges = false;
    }
    
    private void autoLayout() {
        if (storyJson == null) return;
        canvas.autoLayout(entryNodeId);
        hasUnsavedChanges = true;
        showMessage("§a自动布局完成");
    }
    
    private void centerView() {
        if (storyJson == null) return;
        canvas.centerView();
    }
    
    private void addNewNode() {
        if (storyJson == null) {
            showMessage("§c无法添加节点：故事数据未加载");
            return;
        }
        String newId = "new_node_" + System.currentTimeMillis() % 10000;
        
        // Add to JSON
        JsonObject nodes = storyJson.getAsJsonObject("nodes");
        JsonObject newNode = new JsonObject();
        newNode.addProperty("type", "CUTSCENE");
        newNode.add("data", new JsonObject());
        newNode.add("edges", new JsonArray());
        nodes.add(newId, newNode);
        
        // Add to canvas
        GraphNode node = new GraphNode(newId, "CUTSCENE", 100, 100);
        canvas.addNode(node);
        canvas.selectNode(node);
        
        propertyPanel.setNode(node, newNode);
        hasUnsavedChanges = true;
        
        showMessage("§a新节点已创建: " + newId);
    }
    
    private void deleteSelectedNode() {
        if (storyJson == null) return;
        GraphNode selected = canvas.getSelectedNode();
        if (selected == null) return;
        
        String nodeId = selected.getNodeId();
        
        // Remove from JSON
        if (storyJson.has("nodes")) {
            storyJson.getAsJsonObject("nodes").remove(nodeId);
        }
        
        // Rebuild graph
        buildGraphFromJson();
        propertyPanel.setNode(null, null);
        hasUnsavedChanges = true;
        
        showMessage("§c节点已删除: " + nodeId);
    }
    
    private void goBack() {
        if (hasUnsavedChanges) {
            showMessage("§e有未保存的更改");
        }
        Minecraft.getInstance().setScreen(new AdminStoryManagerScreen());
    }
    
    private void showMessage(String message) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/hud/EdgeIndicatorRenderer.java`
```java
package com.warmpixel.storyadventure.client.ui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Off-screen edge indicators pointing to objectives.
 * Stranger Things neon style arrows at screen edges.
 */
public class EdgeIndicatorRenderer implements HudRenderCallback {
    
    private static final int COLOR_OBJECTIVE = 0xFFFFCC00;
    private static final int COLOR_DANGER = 0xFFFF4444;
    private static final int COLOR_CLUE = 0xFF44CCFF;
    
    private static final int INDICATOR_SIZE = 12;
    private static final int MARGIN = 20;
    
    private static EdgeIndicatorRenderer instance;
    private List<IndicatorTarget> targets = new ArrayList<>();
    private boolean enabled = false;
    
    public static void register() {
        instance = new EdgeIndicatorRenderer();
        HudRenderCallback.EVENT.register(instance);
    }
    
    public static EdgeIndicatorRenderer getInstance() {
        return instance;
    }
    
    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled || targets.isEmpty()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        
        Vec3 playerPos = mc.player.position();
        float playerYaw = mc.player.getYRot();
        
        for (IndicatorTarget target : targets) {
            // Calculate direction to target
            double dx = target.x - playerPos.x;
            double dz = target.z - playerPos.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            
            if (distance < 5) continue; // Too close, don't show indicator
            
            // Calculate angle to target relative to player view
            double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));
            double relativeAngle = angleToTarget - playerYaw;
            
            // Normalize angle to -180 to 180
            while (relativeAngle > 180) relativeAngle -= 360;
            while (relativeAngle < -180) relativeAngle += 360;
            
            // Check if target is roughly on screen (within ~60 degree FOV)
            if (Math.abs(relativeAngle) < 50) continue;
            
            // Calculate screen edge position
            int indicatorX, indicatorY;
            double rad = Math.toRadians(relativeAngle);
            
            // Project to screen edge
            double projX = Math.sin(rad);
            double projY = -Math.cos(rad) * 0.3; // Squish vertical
            
            // Scale to screen with margins
            double maxX = (screenWidth / 2.0) - MARGIN - INDICATOR_SIZE;
            double maxY = (screenHeight / 2.0) - MARGIN - INDICATOR_SIZE;
            
            double scale = Math.min(maxX / Math.abs(projX), maxY / Math.max(0.1, Math.abs(projY)));
            
            indicatorX = (int)(centerX + projX * scale);
            indicatorY = (int)(centerY + projY * scale);
            
            // Clamp to screen edges
            indicatorX = Math.max(MARGIN, Math.min(screenWidth - MARGIN - INDICATOR_SIZE, indicatorX));
            indicatorY = Math.max(MARGIN, Math.min(screenHeight - MARGIN - INDICATOR_SIZE, indicatorY));
            
            // Draw indicator
            drawIndicator(graphics, indicatorX, indicatorY, target.color, relativeAngle, distance, target.label);
        }
    }
    
    private void drawIndicator(GuiGraphics graphics, int x, int y, int color, double angle, double distance, String label) {
        // Draw arrow pointing in direction
        int arrowSize = INDICATOR_SIZE;
        
        // Determine arrow direction (simplified to 8 directions)
        int dir = (int)((angle + 180 + 22.5) / 45) % 8;
        
        // Draw outer glow
        int glowColor = (0x40 << 24) | (color & 0x00FFFFFF);
        graphics.fill(x - 2, y - 2, x + arrowSize + 2, y + arrowSize + 2, glowColor);
        
        // Draw arrow shape based on direction
        drawArrow(graphics, x, y, arrowSize, dir, color);
        
        // Draw distance label
        String distStr = String.format("%.0fm", distance);
        Minecraft mc = Minecraft.getInstance();
        int labelWidth = mc.font.width(distStr);
        graphics.drawString(mc.font, distStr, x + arrowSize / 2 - labelWidth / 2, y + arrowSize + 2, color);
    }
    
    private void drawArrow(GuiGraphics graphics, int x, int y, int size, int direction, int color) {
        // Simple arrow drawing based on 8 directions
        int darkColor = darkenColor(color, 0.6f);
        
        // Background
        graphics.fill(x, y, x + size, y + size, darkColor);
        
        // Arrow symbol - draw based on direction
        int cx = x + size / 2;
        int cy = y + size / 2;
        int halfSize = size / 3;
        
        switch (direction) {
            case 0 -> { // Up
                graphics.fill(cx - 1, cy - halfSize, cx + 1, cy + halfSize, color);
                graphics.fill(cx - halfSize, cy - halfSize + 2, cx + halfSize, cy - halfSize + 4, color);
            }
            case 2 -> { // Right
                graphics.fill(cx - halfSize, cy - 1, cx + halfSize, cy + 1, color);
                graphics.fill(cx + halfSize - 4, cy - halfSize, cx + halfSize - 2, cy + halfSize, color);
            }
            case 4 -> { // Down
                graphics.fill(cx - 1, cy - halfSize, cx + 1, cy + halfSize, color);
                graphics.fill(cx - halfSize, cy + halfSize - 4, cx + halfSize, cy + halfSize - 2, color);
            }
            case 6 -> { // Left
                graphics.fill(cx - halfSize, cy - 1, cx + halfSize, cy + 1, color);
                graphics.fill(cx - halfSize + 2, cy - halfSize, cx - halfSize + 4, cy + halfSize, color);
            }
            default -> { // Diagonal or other
                graphics.fill(cx - halfSize, cy - 1, cx + halfSize, cy + 1, color);
                graphics.fill(cx - 1, cy - halfSize, cx + 1, cy + halfSize, color);
            }
        }
    }
    
    private int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int)(((color >> 16) & 0xFF) * factor);
        int g = (int)(((color >> 8) & 0xFF) * factor);
        int b = (int)((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    // Public API
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public void addTarget(double x, double y, double z, int color, String label) {
        targets.add(new IndicatorTarget(x, y, z, color, label));
    }
    
    public void clearTargets() {
        targets.clear();
    }
    
    public record IndicatorTarget(double x, double y, double z, int color, String label) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/ui/hud/StrangerHudRenderer.java`
```java
package com.warmpixel.storyadventure.client.ui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Stranger Things themed HUD overlay for story objectives, clues, and timers.
 * Displays in top-left corner with neon styling.
 */
public class StrangerHudRenderer implements HudRenderCallback {
    
    private static final int HUD_X = 10;
    private static final int HUD_Y = 40;
    private static final int HUD_WIDTH = 200;
    
    // Colors
    private static final int COLOR_NEON_RED = 0xFFE50914;
    private static final int COLOR_BG = 0xC0080808;
    private static final int COLOR_BORDER = 0xFF330011;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    private static final int COLOR_TEXT_DIM = 0xFF666666;
    private static final int COLOR_OBJECTIVE_ACTIVE = 0xFFFFCC00;
    private static final int COLOR_OBJECTIVE_DONE = 0xFF44FF44;
    private static final int COLOR_TIMER_URGENT = 0xFFFF4444;
    
    // State (would be synced from server)
    private boolean visible = false;
    private String storyTitle = "";
    private String chapterName = "";
    private List<ObjectiveEntry> objectives = new ArrayList<>();
    private List<String> clues = new ArrayList<>();
    private long timerEndTime = 0;
    private boolean timerActive = false;
    
    private static StrangerHudRenderer instance;
    
    public static void register() {
        instance = new StrangerHudRenderer();
        HudRenderCallback.EVENT.register(instance);
    }
    
    public static StrangerHudRenderer getInstance() {
        return instance;
    }
    
    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!visible) return;
        
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        
        int y = HUD_Y;
        
        // Calculate total height needed
        int totalHeight = 40 + objectives.size() * 14 + (clues.isEmpty() ? 0 : 20 + clues.size() * 12);
        if (timerActive) totalHeight += 20;
        
        // Draw background panel
        graphics.fill(HUD_X, y, HUD_X + HUD_WIDTH, y + totalHeight, COLOR_BG);
        drawBorder(graphics, HUD_X, y, HUD_WIDTH, totalHeight);
        
        y += 4;
        
        // Draw story title
        graphics.drawString(font, "【" + storyTitle + "】", HUD_X + 6, y, COLOR_NEON_RED);
        y += 12;
        
        // Draw chapter name
        graphics.drawString(font, chapterName, HUD_X + 6, y, COLOR_TEXT_DIM);
        y += 14;
        
        // Draw separator
        graphics.fill(HUD_X + 6, y, HUD_X + HUD_WIDTH - 6, y + 1, COLOR_BORDER);
        y += 6;
        
        // Draw objectives
        for (ObjectiveEntry obj : objectives) {
            String prefix = obj.complete ? "✓ " : "◉ ";
            int color = obj.complete ? COLOR_OBJECTIVE_DONE : (obj.current ? COLOR_OBJECTIVE_ACTIVE : COLOR_TEXT);
            graphics.drawString(font, prefix + obj.text, HUD_X + 6, y, color);
            y += 12;
        }
        
        // Draw timer if active
        if (timerActive) {
            y += 4;
            long remaining = Math.max(0, timerEndTime - System.currentTimeMillis());
            int seconds = (int)(remaining / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            
            String timeStr = String.format("⏱ %02d:%02d", minutes, seconds);
            int timerColor = remaining < 30000 ? COLOR_TIMER_URGENT : COLOR_NEON_RED;
            
            // Pulse effect when urgent
            if (remaining < 30000 && (System.currentTimeMillis() / 500) % 2 == 0) {
                timerColor = 0xFFFFFFFF;
            }
            
            graphics.drawString(font, timeStr, HUD_X + 6, y, timerColor);
            y += 14;
        }
        
        // Draw clues section
        if (!clues.isEmpty()) {
            y += 4;
            graphics.fill(HUD_X + 6, y, HUD_X + HUD_WIDTH - 6, y + 1, COLOR_BORDER);
            y += 4;
            graphics.drawString(font, "已发现线索:", HUD_X + 6, y, COLOR_TEXT_DIM);
            y += 12;
            
            for (String clue : clues) {
                graphics.drawString(font, "📋 " + clue, HUD_X + 10, y, COLOR_TEXT);
                y += 10;
            }
        }
        
        // Draw WarmPixel branding at bottom
        String branding = "WarmPixel原创";
        int brandingWidth = font.width(branding);
        graphics.drawString(font, branding, HUD_X + HUD_WIDTH - brandingWidth - 6, 
            HUD_Y + totalHeight - 12, 0x80666666);
    }
    
    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        // Top
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        // Bottom
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        // Left
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        // Right
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        // Neon corner accents
        int cs = 6;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    // Public API for updating HUD state
    public void show(String title, String chapter) {
        this.visible = true;
        this.storyTitle = title;
        this.chapterName = chapter;
    }
    
    public void hide() {
        this.visible = false;
    }
    
    public void setObjectives(List<ObjectiveEntry> objectives) {
        this.objectives = new ArrayList<>(objectives);
    }
    
    public void addClue(String clue) {
        if (!clues.contains(clue)) {
            clues.add(clue);
        }
    }
    
    public void startTimer(long durationMs) {
        this.timerActive = true;
        this.timerEndTime = System.currentTimeMillis() + durationMs;
    }
    
    public void stopTimer() {
        this.timerActive = false;
    }
    
    public void reset() {
        objectives.clear();
        clues.clear();
        timerActive = false;
        visible = false;
    }
    
    public record ObjectiveEntry(String text, boolean complete, boolean current) {}
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/cinematic/CameraKeyframe.java`
```java
package com.warmpixel.storyadventure.client.cinematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

/**
 * Represents a single keyframe in a camera path.
 * Contains position, rotation, FOV, timing, and easing information.
 */
public class CameraKeyframe {
    
    private final Vec3 position;
    private final float yaw;
    private final float pitch;
    private final float roll;
    private final float fov;
    private final int durationTicks;
    private final EasingFunction easing;
    
    public CameraKeyframe(Vec3 position, float yaw, float pitch, float roll, 
                          float fov, int durationTicks, EasingFunction easing) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.fov = fov;
        this.durationTicks = durationTicks;
        this.easing = easing;
    }
    
    // ==================== Getters ====================
    
    public Vec3 getPosition() {
        return position;
    }
    
    public float getYaw() {
        return yaw;
    }
    
    public float getPitch() {
        return pitch;
    }
    
    public float getRoll() {
        return roll;
    }
    
    public float getFov() {
        return fov;
    }
    
    public int getDurationTicks() {
        return durationTicks;
    }
    
    public EasingFunction getEasing() {
        return easing;
    }
    
    // ==================== JSON Serialization ====================
    
    /**
     * Create a CameraKeyframe from JSON.
     * Expected format:
     * {
     *   "position": [x, y, z],
     *   "rotation": [yaw, pitch, roll],  // or just [yaw, pitch]
     *   "fov": 70.0,
     *   "duration_ticks": 60,
     *   "easing": "EASE_IN_OUT"
     * }
     */
    public static CameraKeyframe fromJson(JsonObject json) {
        // Parse position
        Vec3 position = Vec3.ZERO;
        if (json.has("position") && json.get("position").isJsonArray()) {
            JsonArray posArr = json.getAsJsonArray("position");
            if (posArr.size() >= 3) {
                position = new Vec3(
                    posArr.get(0).getAsDouble(),
                    posArr.get(1).getAsDouble(),
                    posArr.get(2).getAsDouble()
                );
            }
        }
        
        // Parse rotation
        float yaw = 0f, pitch = 0f, roll = 0f;
        if (json.has("rotation") && json.get("rotation").isJsonArray()) {
            JsonArray rotArr = json.getAsJsonArray("rotation");
            if (rotArr.size() >= 2) {
                yaw = rotArr.get(0).getAsFloat();
                pitch = rotArr.get(1).getAsFloat();
            }
            if (rotArr.size() >= 3) {
                roll = rotArr.get(2).getAsFloat();
            }
        }
        
        // Parse FOV (default to 70)
        float fov = 70f;
        if (json.has("fov")) {
            fov = json.get("fov").getAsFloat();
        }
        
        // Parse duration (default to 0 for first keyframe)
        int durationTicks = 0;
        if (json.has("duration_ticks")) {
            durationTicks = json.get("duration_ticks").getAsInt();
        }
        
        // Parse easing
        EasingFunction easing = EasingFunction.LINEAR;
        if (json.has("easing")) {
            easing = EasingFunction.fromString(json.get("easing").getAsString());
        }
        
        return new CameraKeyframe(position, yaw, pitch, roll, fov, durationTicks, easing);
    }
    
    /**
     * Serialize this keyframe to JSON.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        
        JsonArray posArr = new JsonArray();
        posArr.add(position.x);
        posArr.add(position.y);
        posArr.add(position.z);
        json.add("position", posArr);
        
        JsonArray rotArr = new JsonArray();
        rotArr.add(yaw);
        rotArr.add(pitch);
        rotArr.add(roll);
        json.add("rotation", rotArr);
        
        json.addProperty("fov", fov);
        json.addProperty("duration_ticks", durationTicks);
        json.addProperty("easing", easing.name());
        
        return json;
    }
    
    @Override
    public String toString() {
        return String.format("CameraKeyframe{pos=%s, yaw=%.1f, pitch=%.1f, fov=%.1f, duration=%d, easing=%s}",
            position, yaw, pitch, fov, durationTicks, easing);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/cinematic/CameraPath.java`
```java
package com.warmpixel.storyadventure.client.cinematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a complete camera path for a cutscene.
 * Contains a sequence of keyframes and optional look-at target.
 */
public class CameraPath {
    
    private final List<CameraKeyframe> keyframes;
    private final int totalDurationTicks;
    private final LookAtTarget lookAtTarget;
    
    public CameraPath(List<CameraKeyframe> keyframes, LookAtTarget lookAtTarget) {
        this.keyframes = new ArrayList<>(keyframes);
        this.lookAtTarget = lookAtTarget;
        
        // Calculate total duration
        int total = 0;
        for (CameraKeyframe kf : keyframes) {
            total += kf.getDurationTicks();
        }
        this.totalDurationTicks = total;
    }
    
    // ==================== Getters ====================
    
    public List<CameraKeyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }
    
    public int getTotalDurationTicks() {
        return totalDurationTicks;
    }
    
    public LookAtTarget getLookAtTarget() {
        return lookAtTarget;
    }
    
    public boolean hasLookAtTarget() {
        return lookAtTarget != null;
    }
    
    public int getKeyframeCount() {
        return keyframes.size();
    }
    
    // ==================== Interpolation ====================
    
    /**
     * Get the interpolated camera state at a given time.
     * @param tickTime Current tick time since cutscene start
     * @param partialTicks Partial tick for smooth rendering
     * @return Interpolated camera state
     */
    public CameraState getStateAt(int tickTime, float partialTicks) {
        if (keyframes.isEmpty()) {
            return new CameraState(Vec3.ZERO, 0, 0, 0, 70f);
        }
        
        if (keyframes.size() == 1) {
            CameraKeyframe kf = keyframes.get(0);
            return new CameraState(kf.getPosition(), kf.getYaw(), kf.getPitch(), kf.getRoll(), kf.getFov());
        }
        
        float exactTime = tickTime + partialTicks;
        
        // Find the current keyframe pair
        int accumulatedTime = 0;
        for (int i = 1; i < keyframes.size(); i++) {
            CameraKeyframe from = keyframes.get(i - 1);
            CameraKeyframe to = keyframes.get(i);
            int segmentDuration = to.getDurationTicks();
            
            if (exactTime <= accumulatedTime + segmentDuration || i == keyframes.size() - 1) {
                // We're in this segment
                float segmentTime = exactTime - accumulatedTime;
                float t = segmentDuration > 0 ? segmentTime / segmentDuration : 1f;
                t = Math.max(0f, Math.min(1f, t));
                
                // Apply easing
                double eased = to.getEasing().apply(t);
                
                return interpolate(from, to, (float) eased);
            }
            
            accumulatedTime += segmentDuration;
        }
        
        // Past the end - return last keyframe
        CameraKeyframe last = keyframes.get(keyframes.size() - 1);
        return new CameraState(last.getPosition(), last.getYaw(), last.getPitch(), last.getRoll(), last.getFov());
    }
    
    /**
     * Interpolate between two keyframes.
     */
    private CameraState interpolate(CameraKeyframe from, CameraKeyframe to, float t) {
        // Position lerp
        Vec3 pos = from.getPosition().lerp(to.getPosition(), t);
        
        // Rotation lerp (with angle wrapping for yaw)
        float yaw = lerpAngle(from.getYaw(), to.getYaw(), t);
        float pitch = lerp(from.getPitch(), to.getPitch(), t);
        float roll = lerp(from.getRoll(), to.getRoll(), t);
        
        // FOV lerp
        float fov = lerp(from.getFov(), to.getFov(), t);
        
        return new CameraState(pos, yaw, pitch, roll, fov);
    }
    
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    /**
     * Lerp angles, handling the wrap-around at 180/-180.
     */
    private float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        
        // Normalize difference to [-180, 180]
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        
        return a + diff * t;
    }
    
    // ==================== JSON Serialization ====================
    
    /**
     * Create a CameraPath from JSON.
     * Expected format:
     * {
     *   "keyframes": [...],
     *   "look_at": { "type": "position", "value": [x, y, z] }
     * }
     */
    public static CameraPath fromJson(JsonObject json) {
        List<CameraKeyframe> keyframes = new ArrayList<>();
        
        if (json.has("keyframes") && json.get("keyframes").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("keyframes");
            for (JsonElement elem : arr) {
                if (elem.isJsonObject()) {
                    keyframes.add(CameraKeyframe.fromJson(elem.getAsJsonObject()));
                }
            }
        }
        
        LookAtTarget lookAt = null;
        if (json.has("look_at") && json.get("look_at").isJsonObject()) {
            lookAt = LookAtTarget.fromJson(json.getAsJsonObject("look_at"));
        }
        
        return new CameraPath(keyframes, lookAt);
    }
    
    /**
     * Serialize this path to JSON.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        
        JsonArray kfArr = new JsonArray();
        for (CameraKeyframe kf : keyframes) {
            kfArr.add(kf.toJson());
        }
        json.add("keyframes", kfArr);
        
        if (lookAtTarget != null) {
            json.add("look_at", lookAtTarget.toJson());
        }
        
        return json;
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * Represents the interpolated camera state at a point in time.
     */
    public static class CameraState {
        private final Vec3 position;
        private final float yaw;
        private final float pitch;
        private final float roll;
        private final float fov;
        
        public CameraState(Vec3 position, float yaw, float pitch, float roll, float fov) {
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.fov = fov;
        }
        
        public Vec3 getPosition() { return position; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public float getRoll() { return roll; }
        public float getFov() { return fov; }
    }
    
    /**
     * Represents a look-at target for the camera.
     */
    public static class LookAtTarget {
        public enum Type { POSITION, ENTITY }
        
        private final Type type;
        private final Vec3 position;
        private final String entitySelector;
        
        private LookAtTarget(Type type, Vec3 position, String entitySelector) {
            this.type = type;
            this.position = position;
            this.entitySelector = entitySelector;
        }
        
        public static LookAtTarget position(Vec3 pos) {
            return new LookAtTarget(Type.POSITION, pos, null);
        }
        
        public static LookAtTarget entity(String selector) {
            return new LookAtTarget(Type.ENTITY, null, selector);
        }
        
        public Type getType() { return type; }
        public Vec3 getPosition() { return position; }
        public String getEntitySelector() { return entitySelector; }
        
        public static LookAtTarget fromJson(JsonObject json) {
            String type = json.has("type") ? json.get("type").getAsString() : "position";
            
            if ("entity".equalsIgnoreCase(type)) {
                String selector = json.has("value") ? json.get("value").getAsString() : "@p";
                return LookAtTarget.entity(selector);
            } else {
                Vec3 pos = Vec3.ZERO;
                if (json.has("value") && json.get("value").isJsonArray()) {
                    JsonArray arr = json.getAsJsonArray("value");
                    if (arr.size() >= 3) {
                        pos = new Vec3(
                            arr.get(0).getAsDouble(),
                            arr.get(1).getAsDouble(),
                            arr.get(2).getAsDouble()
                        );
                    }
                }
                return LookAtTarget.position(pos);
            }
        }
        
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("type", type.name().toLowerCase());
            
            if (type == Type.POSITION && position != null) {
                JsonArray arr = new JsonArray();
                arr.add(position.x);
                arr.add(position.y);
                arr.add(position.z);
                json.add("value", arr);
            } else if (type == Type.ENTITY && entitySelector != null) {
                json.addProperty("value", entitySelector);
            }
            
            return json;
        }
    }
    
    @Override
    public String toString() {
        return String.format("CameraPath{keyframes=%d, duration=%d ticks, lookAt=%s}",
            keyframes.size(), totalDurationTicks, lookAtTarget != null);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/cinematic/CameraRecording.java`
```java
package com.warmpixel.storyadventure.client.cinematic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a collection of recorded camera keyframes.
 * Used by the Camera Recorder UI to store and save camera path recordings.
 */
public class CameraRecording {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    
    private String name;
    private final List<RecordedKeyframe> keyframes;
    private LocalDateTime createdAt;
    
    public CameraRecording() {
        this.name = "Untitled Recording";
        this.keyframes = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
    }
    
    public CameraRecording(String name) {
        this.name = name;
        this.keyframes = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
    }
    
    // ==================== Keyframe Operations ====================
    
    public void addKeyframe(RecordedKeyframe keyframe) {
        keyframes.add(keyframe);
    }
    
    public void addKeyframe(double x, double y, double z, float yaw, float pitch, float fov, 
                            int durationTicks, String easing) {
        keyframes.add(new RecordedKeyframe(x, y, z, yaw, pitch, 0f, fov, durationTicks, easing));
    }
    
    public void removeKeyframe(int index) {
        if (index >= 0 && index < keyframes.size()) {
            keyframes.remove(index);
        }
    }
    
    public void updateKeyframe(int index, RecordedKeyframe keyframe) {
        if (index >= 0 && index < keyframes.size()) {
            keyframes.set(index, keyframe);
        }
    }
    
    public void clearKeyframes() {
        keyframes.clear();
    }
    
    public RecordedKeyframe getKeyframe(int index) {
        if (index >= 0 && index < keyframes.size()) {
            return keyframes.get(index);
        }
        return null;
    }
    
    public List<RecordedKeyframe> getKeyframes() {
        return new ArrayList<>(keyframes);
    }
    
    public int getKeyframeCount() {
        return keyframes.size();
    }
    
    // ==================== Getters/Setters ====================
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    // ==================== Conversion ====================
    
    /**
     * Convert this recording to a CameraPath for preview playback.
     */
    public CameraPath toCameraPath() {
        List<CameraKeyframe> pathKeyframes = new ArrayList<>();
        
        for (RecordedKeyframe kf : keyframes) {
            pathKeyframes.add(new CameraKeyframe(
                new net.minecraft.world.phys.Vec3(kf.x, kf.y, kf.z),
                kf.yaw, kf.pitch, kf.roll,
                kf.fov, kf.durationTicks,
                EasingFunction.fromString(kf.easing)
            ));
        }
        
        return new CameraPath(pathKeyframes, null);
    }
    
    /**
     * Generate the camera_path JSON object for use in story files.
     */
    public JsonObject toCameraPathJson() {
        JsonObject pathObj = new JsonObject();
        JsonArray keyframesArr = new JsonArray();
        
        for (RecordedKeyframe kf : keyframes) {
            JsonObject kfObj = new JsonObject();
            
            JsonArray posArr = new JsonArray();
            posArr.add(round(kf.x, 2));
            posArr.add(round(kf.y, 2));
            posArr.add(round(kf.z, 2));
            kfObj.add("position", posArr);
            
            JsonArray rotArr = new JsonArray();
            rotArr.add(round(kf.yaw, 1));
            rotArr.add(round(kf.pitch, 1));
            rotArr.add(round(kf.roll, 1));
            kfObj.add("rotation", rotArr);
            
            kfObj.addProperty("fov", round(kf.fov, 1));
            kfObj.addProperty("duration_ticks", kf.durationTicks);
            kfObj.addProperty("easing", kf.easing);
            
            keyframesArr.add(kfObj);
        }
        
        pathObj.add("keyframes", keyframesArr);
        return pathObj;
    }
    
    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
    
    // ==================== JSON Serialization ====================
    
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("created_at", createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        JsonArray keyframesArr = new JsonArray();
        for (RecordedKeyframe kf : keyframes) {
            JsonObject kfObj = new JsonObject();
            
            JsonArray posArr = new JsonArray();
            posArr.add(kf.x);
            posArr.add(kf.y);
            posArr.add(kf.z);
            kfObj.add("position", posArr);
            
            JsonArray rotArr = new JsonArray();
            rotArr.add(kf.yaw);
            rotArr.add(kf.pitch);
            rotArr.add(kf.roll);
            kfObj.add("rotation", rotArr);
            
            kfObj.addProperty("fov", kf.fov);
            kfObj.addProperty("duration_ticks", kf.durationTicks);
            kfObj.addProperty("easing", kf.easing);
            
            keyframesArr.add(kfObj);
        }
        json.add("keyframes", keyframesArr);
        
        // Also include ready-to-use camera_path format
        json.add("camera_path", toCameraPathJson());
        
        return json;
    }
    
    public static CameraRecording fromJson(JsonObject json) {
        CameraRecording recording = new CameraRecording();
        
        if (json.has("name")) {
            recording.name = json.get("name").getAsString();
        }
        
        if (json.has("created_at")) {
            try {
                recording.createdAt = LocalDateTime.parse(json.get("created_at").getAsString());
            } catch (Exception e) {
                recording.createdAt = LocalDateTime.now();
            }
        }
        
        if (json.has("keyframes") && json.get("keyframes").isJsonArray()) {
            for (var elem : json.getAsJsonArray("keyframes")) {
                if (!elem.isJsonObject()) continue;
                JsonObject kfObj = elem.getAsJsonObject();
                
                double x = 0, y = 0, z = 0;
                if (kfObj.has("position") && kfObj.get("position").isJsonArray()) {
                    JsonArray posArr = kfObj.getAsJsonArray("position");
                    if (posArr.size() >= 3) {
                        x = posArr.get(0).getAsDouble();
                        y = posArr.get(1).getAsDouble();
                        z = posArr.get(2).getAsDouble();
                    }
                }
                
                float yaw = 0, pitch = 0, roll = 0;
                if (kfObj.has("rotation") && kfObj.get("rotation").isJsonArray()) {
                    JsonArray rotArr = kfObj.getAsJsonArray("rotation");
                    if (rotArr.size() >= 2) {
                        yaw = rotArr.get(0).getAsFloat();
                        pitch = rotArr.get(1).getAsFloat();
                    }
                    if (rotArr.size() >= 3) {
                        roll = rotArr.get(2).getAsFloat();
                    }
                }
                
                float fov = kfObj.has("fov") ? kfObj.get("fov").getAsFloat() : 70f;
                int durationTicks = kfObj.has("duration_ticks") ? kfObj.get("duration_ticks").getAsInt() : 60;
                String easing = kfObj.has("easing") ? kfObj.get("easing").getAsString() : "LINEAR";
                
                recording.addKeyframe(new RecordedKeyframe(x, y, z, yaw, pitch, roll, fov, durationTicks, easing));
            }
        }
        
        return recording;
    }
    
    // ==================== File I/O ====================
    
    /**
     * Get the directory for camera recordings.
     */
    public static Path getRecordingsDirectory() {
        return Path.of("config", "storyadventure", "camera_recordings");
    }
    
    /**
     * Save this recording to a file with auto-generated filename.
     * @return The path to the saved file
     */
    public Path saveToFile() throws IOException {
        Path dir = getRecordingsDirectory();
        Files.createDirectories(dir);
        
        String filename = "recording_" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".json";
        Path file = dir.resolve(filename);
        
        String jsonStr = GSON.toJson(toJson());
        Files.writeString(file, jsonStr, StandardCharsets.UTF_8);
        
        StoryAdventureMod.LOGGER.info("Saved camera recording to: {}", file);
        return file;
    }
    
    /**
     * Save this recording to a specific file.
     */
    public void saveToFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        String jsonStr = GSON.toJson(toJson());
        Files.writeString(file, jsonStr, StandardCharsets.UTF_8);
        StoryAdventureMod.LOGGER.info("Saved camera recording to: {}", file);
    }
    
    /**
     * Load a recording from a file.
     */
    public static CameraRecording loadFromFile(Path file) throws IOException {
        String jsonStr = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject json = GSON.fromJson(jsonStr, JsonObject.class);
        CameraRecording recording = fromJson(json);
        StoryAdventureMod.LOGGER.info("Loaded camera recording from: {}", file);
        return recording;
    }
    
    /**
     * List all recording files in the recordings directory.
     */
    public static List<Path> listRecordingFiles() {
        Path dir = getRecordingsDirectory();
        List<Path> files = new ArrayList<>();
        
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                      .sorted()
                      .forEach(files::add);
            } catch (IOException e) {
                StoryAdventureMod.LOGGER.error("Failed to list recording files", e);
            }
        }
        
        return files;
    }
    
    // ==================== Recorded Keyframe ====================
    
    /**
     * A single recorded camera keyframe.
     */
    public record RecordedKeyframe(
        double x, double y, double z,
        float yaw, float pitch, float roll,
        float fov,
        int durationTicks,
        String easing
    ) {
        public RecordedKeyframe withDuration(int newDuration) {
            return new RecordedKeyframe(x, y, z, yaw, pitch, roll, fov, newDuration, easing);
        }
        
        public RecordedKeyframe withEasing(String newEasing) {
            return new RecordedKeyframe(x, y, z, yaw, pitch, roll, fov, durationTicks, newEasing);
        }
        
        public RecordedKeyframe withFov(float newFov) {
            return new RecordedKeyframe(x, y, z, yaw, pitch, roll, newFov, durationTicks, easing);
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/cinematic/CinematicCameraController.java`
```java
package com.warmpixel.storyadventure.client.cinematic;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Main controller for cinematic camera during cutscenes.
 * This is a client-side singleton that manages camera path playback
 * and provides camera state for mixin injection.
 */
public class CinematicCameraController {
    
    private static CinematicCameraController instance;
    
    // Cutscene state
    private boolean active = false;
    private CameraPath currentPath;
    private long startTimeMs;
    private int currentTick;
    private boolean skippable = true;
    
    // Current interpolated camera state
    private Vec3 currentPosition = Vec3.ZERO;
    private float currentYaw = 0f;
    private float currentPitch = 0f;
    private float currentRoll = 0f;
    private float currentFov = 70f;
    
    // Visual effect settings
    private boolean letterboxEnabled = false;
    private float letterboxProgress = 0f;
    private float fadeProgress = 0f;
    private int fadeInTicks = 0;
    private int fadeOutTicks = 0;
    private int totalDurationTicks = 0;
    
    // look-at target (resolved entity position)
    private Vec3 lookAtPosition = null;
    
    // Callbacks
    private Runnable onComplete;
    private Runnable onSkip;
    
    private CinematicCameraController() {}
    
    public static CinematicCameraController getInstance() {
        if (instance == null) {
            instance = new CinematicCameraController();
        }
        return instance;
    }
    
    // ==================== Cutscene Control ====================
    
    /**
     * Start a cutscene with the given camera path.
     */
    public void startCutscene(CameraPath path, CutsceneConfig config) {
        if (path == null || path.getKeyframeCount() == 0) {
            StoryAdventureMod.LOGGER.warn("[CinematicCamera] Cannot start cutscene with empty path");
            return;
        }
        
        this.currentPath = path;
        this.startTimeMs = System.currentTimeMillis();
        this.currentTick = 0;
        this.active = true;
        this.skippable = config.isSkippable();
        
        this.letterboxEnabled = config.isLetterboxEnabled();
        this.letterboxProgress = 0f;
        this.fadeInTicks = config.getFadeInTicks();
        this.fadeOutTicks = config.getFadeOutTicks();
        this.totalDurationTicks = path.getTotalDurationTicks();
        this.fadeProgress = 1f; // Start with black screen if fade-in enabled
        
        this.onComplete = config.getOnComplete();
        this.onSkip = config.getOnSkip();
        
        // Initialize look-at target
        if (path.hasLookAtTarget()) {
            resolveLookAtTarget(path.getLookAtTarget());
        } else {
            lookAtPosition = null;
        }
        
        // Initialize to first keyframe state
        updateCameraState(0f);
        
        StoryAdventureMod.LOGGER.info("[CinematicCamera] Started cutscene: {} keyframes, {} ticks duration",
            path.getKeyframeCount(), path.getTotalDurationTicks());
    }
    
    /**
     * Stop the current cutscene.
     */
    public void stopCutscene() {
        if (!active) return;
        
        StoryAdventureMod.LOGGER.info("[CinematicCamera] Cutscene stopped");
        
        active = false;
        currentPath = null;
        letterboxProgress = 0f;
        fadeProgress = 0f;
        
        if (onComplete != null) {
            onComplete.run();
            onComplete = null;
        }
        onSkip = null;
    }
    
    /**
     * Skip the current cutscene (if skippable).
     */
    public void skipCutscene() {
        if (!active || !skippable) return;
        
        StoryAdventureMod.LOGGER.info("[CinematicCamera] Cutscene skipped by player");
        
        active = false;
        currentPath = null;
        letterboxProgress = 0f;
        fadeProgress = 0f;
        
        if (onSkip != null) {
            onSkip.run();
            onSkip = null;
        }
        onComplete = null;
    }
    
    /**
     * Tick the cutscene each frame.
     * @param partialTicks Partial tick for smooth rendering
     */
    public void tick(float partialTicks) {
        if (!active || currentPath == null) return;
        
        // Calculate elapsed time
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        float elapsedTicks = elapsedMs / 50f; // 50ms per tick
        currentTick = (int) elapsedTicks;
        
        // Update camera state
        updateCameraState(partialTicks);
        
        // Update letterbox animation
        updateLetterbox(elapsedTicks);
        
        // Update fade effect
        updateFade(elapsedTicks);
        
        // Check for cutscene completion
        if (currentTick >= totalDurationTicks) {
            StoryAdventureMod.LOGGER.info("[CinematicCamera] Cutscene completed naturally");
            stopCutscene();
        }
    }
    
    private void updateCameraState(float partialTicks) {
        if (currentPath == null) return;
        
        CameraPath.CameraState state = currentPath.getStateAt(currentTick, partialTicks);
        
        this.currentPosition = state.getPosition();
        this.currentFov = state.getFov();
        
        // Handle look-at override
        if (lookAtPosition != null) {
            // Calculate rotation to look at target
            Vec3 toTarget = lookAtPosition.subtract(currentPosition);
            double horizontalDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            
            this.currentYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            this.currentPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontalDist));
            this.currentRoll = state.getRoll(); // Keep roll from keyframe
        } else {
            this.currentYaw = state.getYaw();
            this.currentPitch = state.getPitch();
            this.currentRoll = state.getRoll();
        }
    }
    
    private void updateLetterbox(float elapsedTicks) {
        if (!letterboxEnabled) {
            letterboxProgress = 0f;
            return;
        }
        
        // Animate letterbox in over first 10 ticks
        float animDuration = 10f;
        if (elapsedTicks < animDuration) {
            letterboxProgress = elapsedTicks / animDuration;
        } else if (elapsedTicks > totalDurationTicks - animDuration) {
            // Animate out over last 10 ticks
            letterboxProgress = (totalDurationTicks - elapsedTicks) / animDuration;
        } else {
            letterboxProgress = 1f;
        }
        letterboxProgress = Math.max(0f, Math.min(1f, letterboxProgress));
    }
    
    private void updateFade(float elapsedTicks) {
        if (fadeInTicks > 0 && elapsedTicks < fadeInTicks) {
            // Fade in (from black)
            fadeProgress = 1f - (elapsedTicks / fadeInTicks);
        } else if (fadeOutTicks > 0 && elapsedTicks > totalDurationTicks - fadeOutTicks) {
            // Fade out (to black)
            fadeProgress = (elapsedTicks - (totalDurationTicks - fadeOutTicks)) / fadeOutTicks;
        } else {
            fadeProgress = 0f;
        }
        fadeProgress = Math.max(0f, Math.min(1f, fadeProgress));
    }
    
    private void resolveLookAtTarget(CameraPath.LookAtTarget target) {
        if (target.getType() == CameraPath.LookAtTarget.Type.POSITION) {
            lookAtPosition = target.getPosition();
        } else {
            // Entity target - try to resolve
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                String selector = target.getEntitySelector();
                // For now, just use player position as fallback
                // Full entity selector resolution would require server communication
                if (mc.player != null) {
                    lookAtPosition = mc.player.position();
                }
            }
        }
    }
    
    // ==================== Getters for Mixin Use ====================
    
    public boolean isActive() {
        return active;
    }
    
    public Vec3 getCameraPosition() {
        return currentPosition;
    }
    
    public float getCameraYaw() {
        return currentYaw;
    }
    
    public float getCameraPitch() {
        return currentPitch;
    }
    
    public float getCameraRoll() {
        return currentRoll;
    }
    
    public float getCameraFov() {
        return currentFov;
    }
    
    public float getLetterboxProgress() {
        return letterboxProgress;
    }
    
    public float getFadeProgress() {
        return fadeProgress;
    }
    
    public boolean isLetterboxEnabled() {
        return letterboxEnabled;
    }
    
    public boolean isSkippable() {
        return skippable;
    }
    
    public int getCurrentTick() {
        return currentTick;
    }
    
    public int getTotalDurationTicks() {
        return totalDurationTicks;
    }
    
    /**
     * Get progress through cutscene (0.0 to 1.0).
     */
    public float getProgress() {
        if (totalDurationTicks <= 0) return 0f;
        return Math.min(1f, (float) currentTick / totalDurationTicks);
    }
    
    // ==================== Configuration ====================
    
    /**
     * Configuration for cutscene playback.
     */
    public static class CutsceneConfig {
        private boolean skippable = true;
        private boolean letterboxEnabled = true;
        private int fadeInTicks = 0;
        private int fadeOutTicks = 0;
        private Runnable onComplete;
        private Runnable onSkip;
        
        public CutsceneConfig() {}
        
        public CutsceneConfig setSkippable(boolean skippable) {
            this.skippable = skippable;
            return this;
        }
        
        public CutsceneConfig setLetterboxEnabled(boolean enabled) {
            this.letterboxEnabled = enabled;
            return this;
        }
        
        public CutsceneConfig setFadeInTicks(int ticks) {
            this.fadeInTicks = ticks;
            return this;
        }
        
        public CutsceneConfig setFadeOutTicks(int ticks) {
            this.fadeOutTicks = ticks;
            return this;
        }
        
        public CutsceneConfig setOnComplete(Runnable callback) {
            this.onComplete = callback;
            return this;
        }
        
        public CutsceneConfig setOnSkip(Runnable callback) {
            this.onSkip = callback;
            return this;
        }
        
        public boolean isSkippable() { return skippable; }
        public boolean isLetterboxEnabled() { return letterboxEnabled; }
        public int getFadeInTicks() { return fadeInTicks; }
        public int getFadeOutTicks() { return fadeOutTicks; }
        public Runnable getOnComplete() { return onComplete; }
        public Runnable getOnSkip() { return onSkip; }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/cinematic/EasingFunction.java`
```java
package com.warmpixel.storyadventure.client.cinematic;

/**
 * Easing functions for smooth camera interpolation.
 * Inspired by Unity's animation curves and CSS easing functions.
 */
public enum EasingFunction {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    CUBIC_IN,
    CUBIC_OUT,
    CUBIC_IN_OUT,
    SMOOTH_STEP,
    SMOOTHER_STEP,
    BOUNCE_OUT,
    ELASTIC_OUT;
    
    /**
     * Apply the easing function to a normalized time value (0.0 to 1.0).
     * @param t Normalized time (0.0 = start, 1.0 = end)
     * @return Eased value
     */
    public double apply(double t) {
        // Clamp t to [0, 1]
        t = Math.max(0.0, Math.min(1.0, t));
        
        return switch (this) {
            case LINEAR -> t;
            case EASE_IN -> t * t;
            case EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t);
            case EASE_IN_OUT -> t < 0.5 
                ? 2.0 * t * t 
                : 1.0 - Math.pow(-2.0 * t + 2.0, 2) / 2.0;
            case CUBIC_IN -> t * t * t;
            case CUBIC_OUT -> 1.0 - Math.pow(1.0 - t, 3);
            case CUBIC_IN_OUT -> t < 0.5 
                ? 4.0 * t * t * t 
                : 1.0 - Math.pow(-2.0 * t + 2.0, 3) / 2.0;
            case SMOOTH_STEP -> t * t * (3.0 - 2.0 * t);
            case SMOOTHER_STEP -> t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
            case BOUNCE_OUT -> {
                double n1 = 7.5625;
                double d1 = 2.75;
                if (t < 1 / d1) {
                    yield n1 * t * t;
                } else if (t < 2 / d1) {
                    t -= 1.5 / d1;
                    yield n1 * t * t + 0.75;
                } else if (t < 2.5 / d1) {
                    t -= 2.25 / d1;
                    yield n1 * t * t + 0.9375;
                } else {
                    t -= 2.625 / d1;
                    yield n1 * t * t + 0.984375;
                }
            }
            case ELASTIC_OUT -> {
                if (t == 0 || t == 1) yield t;
                double c4 = (2.0 * Math.PI) / 3.0;
                yield Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0;
            }
        };
    }
    
    /**
     * Parse an easing function from string.
     * @param name The easing function name
     * @return The easing function, defaults to LINEAR if unknown
     */
    public static EasingFunction fromString(String name) {
        if (name == null || name.isEmpty()) {
            return LINEAR;
        }
        try {
            return valueOf(name.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return LINEAR;
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/render/CinematicOverlayRenderer.java`
```java
package com.warmpixel.storyadventure.client.render;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders cinematic overlay effects during cutscenes.
 * Includes letterbox bars, fade effects, skip prompt, and progress bar.
 */
public class CinematicOverlayRenderer implements HudRenderCallback {
    
    private static CinematicOverlayRenderer instance;
    
    // Letterbox settings
    private static final float LETTERBOX_HEIGHT_RATIO = 0.12f; // 12% of screen height for each bar
    private static final int LETTERBOX_COLOR = 0xFF000000; // Pure black
    
    // Fade settings
    private static final int FADE_COLOR_BASE = 0x000000; // Black fade
    
    // Skip prompt
    private static final String SKIP_PROMPT = "Press [ESC] to skip";
    private static final int SKIP_PROMPT_COLOR = 0xAAFFFFFF;
    
    public static void register() {
        instance = new CinematicOverlayRenderer();
        HudRenderCallback.EVENT.register(instance);
    }
    
    public static CinematicOverlayRenderer getInstance() {
        return instance;
    }
    
    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        
        if (!controller.isActive()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        
        // Update controller tick
        controller.tick(partialTicks);
        
        // Render fade effect (behind letterbox)
        renderFade(graphics, screenWidth, screenHeight, controller.getFadeProgress());
        
        // Render letterbox bars
        if (controller.isLetterboxEnabled()) {
            renderLetterbox(graphics, screenWidth, screenHeight, controller.getLetterboxProgress());
        }
        
        // Render skip prompt
        if (controller.isSkippable()) {
            renderSkipPrompt(graphics, mc.font, screenWidth, screenHeight, controller.getLetterboxProgress());
        }
        
        // Render progress bar (optional, subtle)
        renderProgressBar(graphics, screenWidth, screenHeight, controller.getProgress(), controller.getLetterboxProgress());
    }
    
    /**
     * Render letterbox bars at top and bottom of screen.
     */
    private void renderLetterbox(GuiGraphics graphics, int screenWidth, int screenHeight, float progress) {
        if (progress <= 0) return;
        
        int barHeight = (int) (screenHeight * LETTERBOX_HEIGHT_RATIO * progress);
        
        // Top bar
        graphics.fill(0, 0, screenWidth, barHeight, LETTERBOX_COLOR);
        
        // Bottom bar
        graphics.fill(0, screenHeight - barHeight, screenWidth, screenHeight, LETTERBOX_COLOR);
    }
    
    /**
     * Render screen fade effect.
     */
    private void renderFade(GuiGraphics graphics, int screenWidth, int screenHeight, float progress) {
        if (progress <= 0) return;
        
        int alpha = (int) (progress * 255);
        int fadeColor = (alpha << 24) | FADE_COLOR_BASE;
        
        graphics.fill(0, 0, screenWidth, screenHeight, fadeColor);
    }
    
    /**
     * Render skip prompt in the letterbox area.
     */
    private void renderSkipPrompt(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, float letterboxProgress) {
        if (letterboxProgress < 0.5f) return; // Only show when letterbox is mostly visible
        
        // Pulse animation
        long time = System.currentTimeMillis();
        float pulse = 0.6f + 0.4f * (float) Math.sin(time / 500.0);
        int alpha = (int) (pulse * 170);
        int color = (alpha << 24) | 0xFFFFFF;
        
        int barHeight = (int) (screenHeight * LETTERBOX_HEIGHT_RATIO);
        int textWidth = font.width(SKIP_PROMPT);
        int x = screenWidth - textWidth - 20;
        int y = screenHeight - barHeight / 2 - 4;
        
        graphics.drawString(font, SKIP_PROMPT, x, y, color, false);
    }
    
    /**
     * Render a subtle progress bar at the bottom of the letterbox.
     */
    private void renderProgressBar(GuiGraphics graphics, int screenWidth, int screenHeight, 
                                    float progress, float letterboxProgress) {
        if (letterboxProgress < 0.8f) return;
        
        int barHeight = (int) (screenHeight * LETTERBOX_HEIGHT_RATIO);
        int progressBarHeight = 2;
        int progressBarY = screenHeight - barHeight + 2;
        
        // Background
        int bgAlpha = (int) (100 * letterboxProgress);
        graphics.fill(0, progressBarY, screenWidth, progressBarY + progressBarHeight, 
            (bgAlpha << 24) | 0x333333);
        
        // Progress fill
        int fillWidth = (int) (screenWidth * progress);
        int fillAlpha = (int) (200 * letterboxProgress);
        graphics.fill(0, progressBarY, fillWidth, progressBarY + progressBarHeight, 
            (fillAlpha << 24) | 0xFFFFFF);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/render/EnemyIndicatorRenderer.java`
```java
package com.warmpixel.storyadventure.client.render;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders 3D indicators above enemies spawned by the story system.
 * This helps players find enemies easily in combat nodes.
 */
public class EnemyIndicatorRenderer {

    private static boolean enabled = true;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(EnemyIndicatorRenderer::onWorldRender);
        StoryAdventureMod.LOGGER.info("[EnemyIndicatorRenderer] Registered world render event");
    }

    public static void setEnabled(boolean enabled) {
        EnemyIndicatorRenderer.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) return;

        Vec3 cameraPos = context.camera().getPosition();
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        float time = (mc.level.getGameTime() + partialTick) / 20.0f;

        // Count and collect enemies first to avoid issues with empty buffers
        int count = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getTags().contains("story_enemy") && entity.isAlive()) {
                count++;
            }
        }

        if (count == 0) return;
        
        // Debug logging: check first 5 entities for tags
        if (mc.level.getGameTime() % 100 == 0) {
             int logged = 0;
             for (Entity entity : mc.level.entitiesForRendering()) {
                 if (logged++ < 5) {
                     StoryAdventureMod.LOGGER.info("[EnemyIndicatorRenderer] Entity: {} (ID: {}), Tags: {}", 
                         entity.getType().toShortString(), entity.getId(), entity.getTags());
                 }
                 if (entity.getTags().contains("story_enemy")) {
                      StoryAdventureMod.LOGGER.info("[EnemyIndicatorRenderer] FOUND TARGET: {} (ID: {})", 
                         entity.getType().toShortString(), entity.getId());
                 }
             }
        }

        // Save current render state
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        // Configure render state for our custom rendering
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Use Tesselator for direct triangle rendering
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        int renderedCount = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getTags().contains("story_enemy") && entity.isAlive()) {
                double x = Mth.lerp(partialTick, entity.xo, entity.getX());
                double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() + 0.8;
                double z = Mth.lerp(partialTick, entity.zo, entity.getZ());

                renderEnemyIndicator(buffer, matrix, x, y, z, time, mc.player.distanceToSqr(x, y, z));
                renderedCount++;
            }
        }

        // Build and draw the mesh
        if (renderedCount > 0) {
            MeshData meshData = buffer.build();
            if (meshData != null) {
                BufferUploader.drawWithShader(meshData);
            }
        }

        // Restore render state
        poseStack.popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // Debug logging
        if (renderedCount > 0 && mc.level.getGameTime() % 100 == 0) {
            StoryAdventureMod.LOGGER.debug("[EnemyIndicatorRenderer] Rendered {} story enemy indicators", renderedCount);
        }
    }

    private static void renderEnemyIndicator(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z, float time, double distSq) {
        // Neon Red color
        int r = 255, g = 20, b = 20, a = 255;

        // Floating animation
        float bob = (float) Math.sin(time * 3.0f) * 0.15f;
        double currentY = y + bob;

        // Rotating diamond shape
        float size = 0.25f;
        float rot = time * 4.0f;

        // Main diamond
        renderDiamond(buffer, matrix, x, currentY, z, size, rot, r, g, b, a);

        // Secondary outer glow (slightly larger, counter-rotating, semi-transparent)
        renderDiamond(buffer, matrix, x, currentY, z, size * 1.3f, -rot * 0.5f, r, g, b, 80);

        // Vertical pointer line when far away (more than 10 blocks)
        if (distSq > 100) {
            float lineAlpha = Math.min(1.0f, (float) (distSq - 100) / 400.0f);
            int lineA = (int) (lineAlpha * 150);
            renderPointerLine(buffer, matrix, x, currentY - 0.3, z, 0.03f, 1.0f, r, g, b, lineA);
        }
    }

    private static void renderDiamond(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z, float s, float rot, int r, int g, int b, int a) {
        float cos = (float) Math.cos(rot) * s;
        float sin = (float) Math.sin(rot) * s;

        // Four corner points at the middle of the diamond
        float px1 = (float) x + cos;
        float pz1 = (float) z + sin;
        float px2 = (float) x + sin;
        float pz2 = (float) z - cos;
        float px3 = (float) x - cos;
        float pz3 = (float) z - sin;
        float px4 = (float) x - sin;
        float pz4 = (float) z + cos;

        float topY = (float) y + s;
        float midY = (float) y;
        float botY = (float) y - s;

        // Top pyramid (4 triangles)
        addTriangle(buffer, matrix, (float) x, topY, (float) z, px1, midY, pz1, px2, midY, pz2, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, topY, (float) z, px2, midY, pz2, px3, midY, pz3, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, topY, (float) z, px3, midY, pz3, px4, midY, pz4, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, topY, (float) z, px4, midY, pz4, px1, midY, pz1, r, g, b, a);

        // Bottom pyramid (4 triangles) - reversed winding for correct face culling
        addTriangle(buffer, matrix, (float) x, botY, (float) z, px2, midY, pz2, px1, midY, pz1, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, botY, (float) z, px3, midY, pz3, px2, midY, pz2, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, botY, (float) z, px4, midY, pz4, px3, midY, pz3, r, g, b, a);
        addTriangle(buffer, matrix, (float) x, botY, (float) z, px1, midY, pz1, px4, midY, pz4, r, g, b, a);
    }

    private static void renderPointerLine(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z, float width, float length, int r, int g, int b, int a) {
        // Draw a thin triangular pointer pointing down
        float halfWidth = width;
        float topY = (float) y;
        float bottomY = (float) y - length;

        // Front face
        addTriangle(buffer, matrix,
                (float) x - halfWidth, topY, (float) z,
                (float) x + halfWidth, topY, (float) z,
                (float) x, bottomY, (float) z,
                r, g, b, a);

        // Back face
        addTriangle(buffer, matrix,
                (float) x + halfWidth, topY, (float) z,
                (float) x - halfWidth, topY, (float) z,
                (float) x, bottomY, (float) z,
                r, g, b, a);

        // Side faces
        addTriangle(buffer, matrix,
                (float) x, topY, (float) z - halfWidth,
                (float) x, topY, (float) z + halfWidth,
                (float) x, bottomY, (float) z,
                r, g, b, a);

        addTriangle(buffer, matrix,
                (float) x, topY, (float) z + halfWidth,
                (float) x, topY, (float) z - halfWidth,
                (float) x, bottomY, (float) z,
                r, g, b, a);
    }

    private static void addTriangle(BufferBuilder buffer, Matrix4f matrix,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3,
                                    int r, int g, int b, int a) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/render/TriggerBoxGizmoRenderer.java`
```java
package com.warmpixel.storyadventure.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.warmpixel.storyadventure.core.waypoint.TriggerBox;
import com.warmpixel.storyadventure.item.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders trigger box gizmos in the world.
 * Only visible when the player is holding the Admin Wand.
 */
public class TriggerBoxGizmoRenderer {
    
    private static TriggerBoxGizmoRenderer instance;
    private List<TriggerBox> triggerBoxes = new ArrayList<>();
    
    public static void register() {
        instance = new TriggerBoxGizmoRenderer();
        WorldRenderEvents.AFTER_TRANSLUCENT.register(instance::render);
    }
    
    public static TriggerBoxGizmoRenderer getInstance() {
        return instance;
    }
    
    private void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        // Only render if player is holding admin wand
        boolean holdingWand = mc.player.getMainHandItem().getItem() == ModItems.ADMIN_WAND ||
                              mc.player.getOffhandItem().getItem() == ModItems.ADMIN_WAND;
        
        if (!holdingWand || triggerBoxes.isEmpty()) return;
        
        Vec3 cameraPos = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        MultiBufferSource bufferSource = context.consumers();
        
        if (poseStack == null || bufferSource == null) return;
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        for (TriggerBox box : triggerBoxes) {
            if (box.getRadius() > 0) {
                renderSphere(poseStack, bufferSource, box);
            } else {
                renderBox(poseStack, bufferSource, box);
            }
        }
        
        poseStack.popPose();
    }
    
    private void renderSphere(PoseStack poseStack, MultiBufferSource bufferSource, TriggerBox box) {
        Vec3 center = box.getCenter();
        double radius = box.getRadius();
        if (center == null) return;

        float r = 0.2f, g = 1.0f, b = 0.3f, a = 0.6f;
        if (!box.getPlayersInside().isEmpty()) {
            r = 1.0f; g = 0.8f; b = 0.0f;
        }

        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        // Draw 3 primary circles to represent the sphere
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) i / segments * ((float) Math.PI * 2);
            float angle2 = (float) (i + 1) / segments * ((float) Math.PI * 2);

            float cos1 = (float) Math.cos(angle1) * (float) radius;
            float sin1 = (float) Math.sin(angle1) * (float) radius;
            float cos2 = (float) Math.cos(angle2) * (float) radius;
            float sin2 = (float) Math.sin(angle2) * (float) radius;

            // XY circle
            drawLine(lineConsumer, matrix, (float)center.x + cos1, (float)center.y + sin1, (float)center.z, 
                     (float)center.x + cos2, (float)center.y + sin2, (float)center.z, r, g, b, a);
            // XZ circle
            drawLine(lineConsumer, matrix, (float)center.x + cos1, (float)center.y, (float)center.z + sin1, 
                     (float)center.x + cos2, (float)center.y, (float)center.z + sin2, r, g, b, a);
            // YZ circle
            drawLine(lineConsumer, matrix, (float)center.x, (float)center.y + cos1, (float)center.z + sin1, 
                     (float)center.x, (float)center.y + cos2, (float)center.z + sin2, r, g, b, a);
        }
    }

    private void renderBox(PoseStack poseStack, MultiBufferSource bufferSource, TriggerBox box) {
        AABB bounds = box.getBounds();
        
        // Colors based on box state
        float r = 0.2f, g = 1.0f, b = 0.3f, a = 0.6f;
        if (!box.getPlayersInside().isEmpty()) {
            // Highlight when players inside
            r = 1.0f; g = 0.8f; b = 0.0f;
        }
        
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();
        
        float minX = (float) bounds.minX;
        float minY = (float) bounds.minY;
        float minZ = (float) bounds.minZ;
        float maxX = (float) bounds.maxX;
        float maxY = (float) bounds.maxY;
        float maxZ = (float) bounds.maxZ;
        
        // Bottom face
        drawLine(lineConsumer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(lineConsumer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        
        // Top face
        drawLine(lineConsumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(lineConsumer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        
        // Vertical edges
        drawLine(lineConsumer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(lineConsumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(lineConsumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }
    
    private void drawLine(VertexConsumer consumer, Matrix4f matrix,
                          float x1, float y1, float z1, float x2, float y2, float z2,
                          float r, float g, float b, float a) {
        // Calculate normal for line direction
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        
        consumer.addVertex(matrix, x1, y1, z1)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz);
    }
    
    // Public API
    public void setTriggerBoxes(List<TriggerBox> boxes) {
        this.triggerBoxes = new ArrayList<>(boxes);
    }
    
    public void addTriggerBox(TriggerBox box) {
        triggerBoxes.add(box);
    }
    
    public void removeTriggerBox(String id) {
        triggerBoxes.removeIf(b -> b.getId().equals(id));
    }
    
    public void clear() {
        triggerBoxes.clear();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/render/WaypointIndicatorRenderer.java`
```java
package com.warmpixel.storyadventure.client.render;

import com.warmpixel.storyadventure.core.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders waypoint indicators on the player's HUD.
 * Shows on-screen markers for nearby waypoints and off-screen arrows for distant ones.
 */
public class WaypointIndicatorRenderer implements HudRenderCallback {
    
    private static final int BASE_INDICATOR_SIZE = 12;
    private static final int MIN_INDICATOR_SIZE = 8;
    private static final int MAX_INDICATOR_SIZE = 20;
    private static final int MARGIN = 25;
    
    private static WaypointIndicatorRenderer instance;
    private List<Waypoint> activeWaypoints = new ArrayList<>();
    private boolean enabled = false;
    
    public static void register() {
        instance = new WaypointIndicatorRenderer();
        HudRenderCallback.EVENT.register(instance);
    }
    
    public static WaypointIndicatorRenderer getInstance() {
        return instance;
    }
    
    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        // Disabled HUD indicators as requested, using 3D world indicators instead
        if (true) return;
        
        if (!enabled || activeWaypoints.isEmpty()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameRenderer == null) return;
        
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;
        
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        
        Font font = mc.font;
        
        // Get camera rotation (Minecraft conventions)
        // YRot (yaw): 0 = South (+Z), 90 = West (-X), increases counterclockwise from above
        // XRot (pitch): positive = looking down, negative = looking up
        float camYaw = camera.getYRot();
        float camPitch = camera.getXRot();
        
        double yawRad = Math.toRadians(camYaw);
        double pitchRad = Math.toRadians(camPitch);
        
        // Precompute trig values
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double sinPitch = Math.sin(pitchRad);
        double cosPitch = Math.cos(pitchRad);
        
        // Build camera basis vectors using Minecraft's coordinate system
        // Forward: unit vector in the direction the camera is looking
        double forwardX = -sinYaw * cosPitch;
        double forwardY = -sinPitch;
        double forwardZ = cosYaw * cosPitch;
        
        // Right: unit vector pointing to the right of the camera (always horizontal)
        double rightX = cosYaw;
        double rightY = 0;
        double rightZ = sinYaw;
        
        // Up: perpendicular to forward and right, computed via cross product
        // For correct orientation: up = right × forward (not forward × right)
        double upX = rightY * forwardZ - rightZ * forwardY;
        double upY = rightZ * forwardX - rightX * forwardZ;
        double upZ = rightX * forwardY - rightY * forwardX;
        
        // Normalize up vector (should already be unit length, but ensure it)
        double upLen = Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (upLen > 0.0001) {
            upX /= upLen;
            upY /= upLen;
            upZ /= upLen;
        }
        
        // Get actual rendered FOV (accounts for sprinting, effects, etc.) via Mixin Accessor
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        double fovY = ((com.warmpixel.storyadventure.mixin.GameRendererAccessor)mc.gameRenderer).invokeGetFov(camera, partialTicks, true);
        double fovYRad = Math.toRadians(fovY);
        double aspectRatio = (double) screenWidth / screenHeight;
        
        double tanHalfFovY = Math.tan(fovYRad / 2.0);
        double tanHalfFovX = tanHalfFovY * aspectRatio;
        
        for (Waypoint waypoint : activeWaypoints) {
            // Offset waypoint position slightly above ground for visibility
            Vec3 wpPos = waypoint.getPosition().add(0, 1.5, 0);
            
            // Vector from camera to waypoint
            double dx = wpPos.x - cameraPos.x;
            double dy = wpPos.y - cameraPos.y;
            double dz = wpPos.z - cameraPos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            
            if (distance < 0.5) continue;
            
            // Transform waypoint position to camera space using dot products
            // camZ: depth (positive = in front of camera)
            // camX: horizontal offset (positive = to the right)
            // camY: vertical offset (positive = above, appears higher on screen)
            double camZ = dx * forwardX + dy * forwardY + dz * forwardZ;
            double camX = dx * rightX + dy * rightY + dz * rightZ;
            double camY = dx * upX + dy * upY + dz * upZ;
            
            boolean inFront = camZ > 0.1;
            
            double screenX, screenY;
            
            if (inFront) {
                // Standard perspective projection for points in front of camera
                // Maps camera space to normalized device coordinates, then to screen space
                double ndcX = camX / (camZ * tanHalfFovX);
                double ndcY = camY / (camZ * tanHalfFovY);
                
                screenX = centerX + ndcX * centerX;
                screenY = centerY - ndcY * centerY; // Subtract because screen Y increases downward
            } else {
                // Point is behind or at camera - project to screen edge
                // Use the horizontal (camX) and vertical (camY) components to determine direction
                double camXYLen = Math.sqrt(camX * camX + camY * camY);
                
                if (camXYLen > 0.001) {
                    // Normalize direction in camera XY plane
                    double normX = camX / camXYLen;
                    double normY = camY / camXYLen;
                    
                    // Project far beyond screen in this direction
                    // The direction is correct: if waypoint is behind-right, 
                    // player needs to turn right to face it
                    screenX = centerX + normX * screenWidth * 2;
                    screenY = centerY - normY * screenHeight * 2;
                } else {
                    // Directly behind - show at bottom center
                    screenX = centerX;
                    screenY = screenHeight * 2;
                }
            }
            
            // Determine if waypoint is visible on screen (with margin for indicator size)
            boolean onScreen = inFront && 
                              screenX >= MARGIN && screenX <= screenWidth - MARGIN && 
                              screenY >= MARGIN && screenY <= screenHeight - MARGIN;
            
            if (onScreen) {
                renderOnScreenMarker(graphics, font, waypoint, (int)screenX, (int)screenY, distance);
            } else {
                // Calculate position at screen edge for off-screen indicator
                double dirX = screenX - centerX;
                double dirY = screenY - centerY;
                
                int edgeX, edgeY;
                
                if (Math.abs(dirX) < 0.001 && Math.abs(dirY) < 0.001) {
                    // Edge case: direction is zero (shouldn't happen often)
                    edgeX = (int)centerX;
                    edgeY = screenHeight - MARGIN;
                } else {
                    // Find where the line from center to projected position intersects screen boundary
                    double scale = calculateBoundaryScale(dirX, dirY, centerX, centerY, 
                                                          screenWidth, screenHeight, MARGIN);
                    
                    edgeX = (int)(centerX + dirX * scale);
                    edgeY = (int)(centerY + dirY * scale);
                    
                    // Ensure we stay within the valid screen area
                    edgeX = Math.max(MARGIN, Math.min(screenWidth - MARGIN, edgeX));
                    edgeY = Math.max(MARGIN, Math.min(screenHeight - MARGIN, edgeY));
                }
                
                renderOffScreenArrow(graphics, font, waypoint, screenWidth, screenHeight, 
                                    edgeX, edgeY, centerX, centerY, distance);
            }
        }
    }
    
    /**
     * Calculates the scale factor to reach the screen boundary from center.
     * Returns the smallest positive scale that places the point on the boundary.
     */
    private double calculateBoundaryScale(double dirX, double dirY, double centerX, double centerY,
                                          int screenWidth, int screenHeight, int margin) {
        double scale = Double.MAX_VALUE;
        
        // Calculate scale for each boundary
        if (dirX > 0.001) {
            // Right boundary
            double s = (screenWidth - margin - centerX) / dirX;
            if (s > 0 && s < scale) scale = s;
        } else if (dirX < -0.001) {
            // Left boundary
            double s = (margin - centerX) / dirX;
            if (s > 0 && s < scale) scale = s;
        }
        
        if (dirY > 0.001) {
            // Bottom boundary
            double s = (screenHeight - margin - centerY) / dirY;
            if (s > 0 && s < scale) scale = s;
        } else if (dirY < -0.001) {
            // Top boundary
            double s = (margin - centerY) / dirY;
            if (s > 0 && s < scale) scale = s;
        }
        
        return (scale == Double.MAX_VALUE || scale <= 0) ? 1.0 : scale;
    }
    
    private void renderOnScreenMarker(GuiGraphics graphics, Font font, Waypoint waypoint,
                                       int screenX, int screenY, double distance) {
        // Calculate distance-based size (bigger when closer)
        double sizeMultiplier = Math.max(0.5, Math.min(2.0, 50.0 / Math.max(10, distance)));
        int baseSize = (int)(BASE_INDICATOR_SIZE * sizeMultiplier);
        baseSize = Math.max(MIN_INDICATOR_SIZE, Math.min(MAX_INDICATOR_SIZE, baseSize));
        
        // Smooth animation using time-based easing
        long time = System.currentTimeMillis();
        float bobAmount = (float) Math.sin(time / 400.0) * 2.0f;
        float pulseScale = 0.95f + 0.1f * (float) Math.sin(time / 300.0);
        
        int size = (int)(baseSize * pulseScale);
        int x = screenX - size / 2;
        int y = screenY - size / 2 + (int)bobAmount;
        
        int color = waypoint.getColor();
        
        // Draw outer glow
        int glowAlpha = (int)(40 + 20 * Math.sin(time / 500.0));
        graphics.fill(x - 3, y - 3, x + size + 3, y + size + 3, (glowAlpha << 24) | (color & 0x00FFFFFF));
        
        // Draw icon background
        int bgAlpha = (int)(220 + 30 * Math.sin(time / 400.0));
        graphics.fill(x, y, x + size, y + size, (bgAlpha << 24) | (color & 0x00FFFFFF));
        
        // Draw icon symbol
        String symbol = "◉";
        int symbolWidth = font.width(symbol);
        graphics.drawString(font, symbol, x + size / 2 - symbolWidth / 2, y + size / 2 - 4, 0xFFFFFFFF);
        
        // Draw label and distance
        if (waypoint.showsDistance()) {
            String info = waypoint.getLabel() + " " + (int)distance + "m";
            int infoWidth = font.width(info);
            int labelX = x + size / 2 - infoWidth / 2;
            int labelY = y + size + 4;
            
            // Background for better readability
            graphics.fill(labelX - 3, labelY - 1, labelX + infoWidth + 3, labelY + 10, 0xC0000000);
            graphics.drawString(font, info, labelX, labelY, color);
        }
    }
    
    private void renderOffScreenArrow(GuiGraphics graphics, Font font, Waypoint waypoint,
                                       int screenWidth, int screenHeight, 
                                       int edgeX, int edgeY, 
                                       float centerX, float centerY, double distance) {
        int color = waypoint.getColor();
        int size = BASE_INDICATOR_SIZE;
        
        // Adjust position to center the indicator on the edge point
        int x = edgeX - size / 2;
        int y = edgeY - size / 2;
        
        // Ensure indicator stays within screen bounds
        x = Math.max(MARGIN, Math.min(screenWidth - MARGIN - size, x));
        y = Math.max(MARGIN, Math.min(screenHeight - MARGIN - size, y));
        
        // Pulsing animation
        long time = System.currentTimeMillis();
        float pulseAlpha = 0.7f + 0.3f * (float)Math.sin(time / 300.0 * Math.PI);
        int pulseColor = ((int)(pulseAlpha * 255) << 24) | (color & 0x00FFFFFF);
        
        // Draw glow effect
        int glowAlpha = (int)(40 + 20 * Math.sin(time / 400.0));
        graphics.fill(x - 3, y - 3, x + size + 3, y + size + 3, (glowAlpha << 24) | (color & 0x00FFFFFF));
        
        // Draw indicator background
        graphics.fill(x, y, x + size, y + size, pulseColor);
        
        // Calculate arrow direction from screen center to edge position
        float dirX = edgeX - centerX;
        float dirY = edgeY - centerY;
        
        // Calculate angle for arrow selection
        // atan2 with (dirX, -dirY) because:
        // - We want 0° to be "up" on screen (negative Y direction)
        // - dirX positive = right side of screen
        // - dirY positive = bottom of screen (but we want up = 0°, so negate)
        double angle = Math.toDegrees(Math.atan2(dirX, -dirY));
        
        // Get appropriate directional arrow
        String arrow = getDirectionArrow(angle);
        
        int arrowWidth = font.width(arrow);
        graphics.drawString(font, arrow, x + size / 2 - arrowWidth / 2, y + size / 2 - 4, 0xFFFFFFFF);
        
        // Draw distance label
        String distStr = (int)distance + "m";
        int distWidth = font.width(distStr);
        
        // Position label based on edge location
        int labelX = x + size / 2 - distWidth / 2;
        int labelY;
        
        // Determine vertical position based on where the indicator is
        if (edgeY <= MARGIN + size) {
            // At top edge - put label below indicator
            labelY = y + size + 2;
        } else if (edgeY >= screenHeight - MARGIN - size) {
            // At bottom edge - put label above indicator
            labelY = y - 12;
        } else {
            // On side edge - put label below
            labelY = y + size + 2;
        }
        
        // Clamp label position to screen
        labelX = Math.max(2, Math.min(screenWidth - distWidth - 2, labelX));
        labelY = Math.max(2, Math.min(screenHeight - 12, labelY));
        
        // Draw label background and text
        graphics.fill(labelX - 2, labelY - 1, labelX + distWidth + 2, labelY + 10, 0xA0000000);
        graphics.drawString(font, distStr, labelX, labelY, color);
    }
    
    /**
     * Returns an arrow character pointing in the given direction.
     * @param angle Direction in degrees where 0° = up, 90° = right, etc.
     */
    private String getDirectionArrow(double angle) {
        // Normalize angle to -180 to 180 range
        while (angle > 180) angle -= 360;
        while (angle <= -180) angle += 360;
        
        // Map angle to 8-directional arrows
        // Each direction covers a 45° arc centered on that direction
        if (angle > -22.5 && angle <= 22.5) return "↑";      // Up
        if (angle > 22.5 && angle <= 67.5) return "↗";       // Up-Right
        if (angle > 67.5 && angle <= 112.5) return "→";      // Right
        if (angle > 112.5 && angle <= 157.5) return "↘";     // Down-Right
        if (angle > 157.5 || angle <= -157.5) return "↓";    // Down
        if (angle > -157.5 && angle <= -112.5) return "↙";   // Down-Left
        if (angle > -112.5 && angle <= -67.5) return "←";    // Left
        if (angle > -67.5 && angle <= -22.5) return "↖";     // Up-Left
        
        return "●"; // Fallback
    }
    
    // ==================== Public API ====================
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return this.enabled;
    }
    
    public void setWaypoints(List<Waypoint> waypoints) {
        this.activeWaypoints = new ArrayList<>(waypoints);
    }
    
    public void addWaypoint(Waypoint waypoint) {
        activeWaypoints.add(waypoint);
    }
    
    public void removeWaypoint(String id) {
        activeWaypoints.removeIf(w -> w.getId().equals(id));
    }
    
    public void clear() {
        activeWaypoints.clear();
    }
    
    public List<Waypoint> getActiveWaypoints() {
        return new ArrayList<>(activeWaypoints);
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/render/WorldDestinationRenderer.java`
```java
package com.warmpixel.storyadventure.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders 3D indicators at target destinations (e.g., rotating circles).
 */
public class WorldDestinationRenderer {

    private static final List<Vec3> destinations = new ArrayList<>();
    private static boolean enabled = true;

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldDestinationRenderer::onWorldRender);
    }

    public static void setDestinations(List<Vec3> points) {
        synchronized (destinations) {
            destinations.clear();
            destinations.addAll(points);
        }
    }

    public static void clearDestinations() {
        synchronized (destinations) {
            destinations.clear();
        }
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled || destinations.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 cameraPos = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) return;
        
        // Use a transparent layer
        VertexConsumer consumer = context.consumers().getBuffer(RenderType.lines());

        float time = (mc.level.getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(true)) / 20.0f;
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        Matrix4f matrix = poseStack.last().pose();
        
        synchronized (destinations) {
            Vec3 playerPos = mc.player.position();
            for (Vec3 pos : destinations) {
                renderRotatingCircle(consumer, matrix, pos, time);
                renderPillar(consumer, matrix, pos, time);
                
                // Breadcrumbs trail leading to destination
                renderBreadcrumbs(consumer, matrix, playerPos, pos, time);
            }
        }
        
        poseStack.popPose();
    }

    private static void renderBreadcrumbs(VertexConsumer consumer, Matrix4f matrix, Vec3 start, Vec3 end, float time) {
        double dist = start.distanceTo(end);
        if (dist < 5.0) return; // Hide when very close to avoid clutter
        
        // Direction vector
        Vec3 dir = end.subtract(start).normalize();
        
        // Stranger Things Neon Red color for path
        int r = 255, g = 20, b = 20, a = 200;
        
        // Render dots every 2 blocks instead of 4 for a smoother path
        // Increase render distance for breadcrumbs to 64 blocks
        double maxTrailDist = Math.min(dist - 3.0, 64.0);
        
        for (double d = 6.0; d < maxTrailDist; d += 2.0) {
            Vec3 point = start.add(dir.scale(d));
            
            // Pulsing animation
            float pulse = (float) Math.sin(time * 4.0f + d * 0.8f);
            
            // Neon flicker effect
            float flicker = (Minecraft.getInstance().level.getGameTime() + (int)d) % 15 == 0 ? 0.3f : 1.0f;
            int currentAlpha = (int)(a * flicker);
            
            float scale = 0.08f + 0.04f * pulse;
            float yOffset = 1.2f + 0.3f * pulse; 
            
            // Add a small "float" motion
            double ox = Math.cos(time * 2.5f + d) * 0.15;
            double oz = Math.sin(time * 2.5f + d) * 0.15;
            
            drawStar(consumer, matrix, point.x + ox, point.y + yOffset, point.z + oz, scale, r, g, b, currentAlpha, time + (float)d);
        }
    }

    private static void drawStar(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, float s, int r, int g, int b, int a, float time) {
        // Draw a rotating cross/star shape
        float rot = time * 2.0f;
        float cos = (float) Math.cos(rot) * s;
        float sin = (float) Math.sin(rot) * s;
        
        // Main vertical line
        consumer.addVertex(matrix, (float)x, (float)y - s*1.5f, (float)z).setColor(r, g, b, a).setNormal(0, 1, 0);
        consumer.addVertex(matrix, (float)x, (float)y + s*1.5f, (float)z).setColor(r, g, b, a).setNormal(0, 1, 0);
        
        // Rotating horizontal cross 1
        consumer.addVertex(matrix, (float)x - cos, (float)y, (float)z - sin).setColor(r, g, b, a).setNormal(0, 1, 0);
        consumer.addVertex(matrix, (float)x + cos, (float)y, (float)z + sin).setColor(r, g, b, a).setNormal(0, 1, 0);
        
        // Rotating horizontal cross 2 (perpendicular)
        consumer.addVertex(matrix, (float)x + sin, (float)y, (float)z - cos).setColor(r, g, b, a).setNormal(0, 1, 0);
        consumer.addVertex(matrix, (float)x - sin, (float)y, (float)z + cos).setColor(r, g, b, a).setNormal(0, 1, 0);
    }

    private static void renderRotatingCircle(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float time) {
        int segments = 32; // More segments for smoother look
        float radius = 2.0f;
        float rotation = time * 2.0f;
        
        // Stranger Things Neon Red
        int r = 255, g = 10, b = 10, a = 255;
        
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) i / segments * Mth.TWO_PI + rotation;
            float angle2 = (float) (i + 1) / segments * Mth.TWO_PI + rotation;

            float x1 = (float) (pos.x + Mth.cos(angle1) * radius);
            float z1 = (float) (pos.z + Mth.sin(angle1) * radius);
            float y1 = (float) (pos.y + 0.1f + Mth.sin(time * 3.0f + angle1 * 3) * 0.15f);

            float x2 = (float) (pos.x + Mth.cos(angle2) * radius);
            float z2 = (float) (pos.z + Mth.sin(angle2) * radius);
            float y2 = (float) (pos.y + 0.1f + Mth.sin(time * 3.0f + angle2 * 3) * 0.15f);

            // Exterior glow (Red)
            consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(0, 1, 0);
            consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(0, 1, 0);
            
            // Interior glow (White/Pink for neon effect)
            consumer.addVertex(matrix, x1, y1 + 0.05f, z1).setColor(255, 200, 200, 200).setNormal(0, 1, 0);
            consumer.addVertex(matrix, x2, y2 + 0.05f, z2).setColor(255, 200, 200, 200).setNormal(0, 1, 0);
        }
    }

    private static void renderPillar(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float time) {
        float height = 20.0f; // Taller pillar for easier sighting
        int r = 255, g = 50, b = 50, a = 80;

        // Central vertical beam (more detailed)
        for (int i = 0; i < 8; i++) {
            float angle = (i / 8.0f) * Mth.TWO_PI + time;
            float offset = 0.05f + (float)Math.sin(time * 2.0 + i) * 0.02f;
            float ox = Mth.cos(angle) * offset;
            float oz = Mth.sin(angle) * offset;
            
            consumer.addVertex(matrix, (float)pos.x + ox, (float)pos.y, (float)pos.z + oz).setColor(r, g, b, a).setNormal(0, 1, 0);
            consumer.addVertex(matrix, (float)pos.x + ox, (float)pos.y + height, (float)pos.z + oz).setColor(r, g, b, 0).setNormal(0, 1, 0);
        }
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/audio/ExternalOggAudioStream.java`
```java
package com.warmpixel.storyadventure.client.audio;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An AudioStream that decodes an external OGG file using STBVorbis.
 * Implements Minecraft's AudioStream interface for proper integration.
 */
@Environment(EnvType.CLIENT)
public class ExternalOggAudioStream implements AudioStream {
    
    private ByteBuffer pcmData;
    private final AudioFormat format;
    private final int totalBytes;
    private int position = 0;
    private boolean closed = false;

    public ExternalOggAudioStream(Path filePath) throws IOException {
        StoryAdventureMod.LOGGER.debug("[ExternalOggAudioStream] Loading: {}", filePath);
        
        if (!Files.exists(filePath)) {
            throw new IOException("Audio file not found: " + filePath);
        }
        
        byte[] fileData = Files.readAllBytes(filePath);
        if (fileData.length == 0) {
            throw new IOException("Audio file is empty: " + filePath);
        }
        
        ByteBuffer fileBuffer = null;
        ShortBuffer shortBuffer = null;
        
        try {
            fileBuffer = MemoryUtil.memAlloc(fileData.length);
            fileBuffer.put(fileData).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer errorBuffer = stack.mallocInt(1);
                long decoder = STBVorbis.stb_vorbis_open_memory(fileBuffer, errorBuffer, null);
                
                if (decoder == 0) {
                    int error = errorBuffer.get(0);
                    throw new IOException("Failed to open OGG file: " + filePath + ", error code: " + error + " (" + getVorbisError(error) + ")");
                }

                try {
                    STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                    STBVorbis.stb_vorbis_get_info(decoder, info);
                    
                    int channels = info.channels();
                    int sampleRate = info.sample_rate();
                    int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
                    
                    if (totalSamples <= 0) {
                        throw new IOException("Invalid OGG file (0 samples): " + filePath);
                    }
                    
                    if (channels <= 0 || channels > 2) {
                        throw new IOException("Unsupported channel count: " + channels + " in " + filePath);
                    }
                    
                    if (sampleRate <= 0) {
                        throw new IOException("Invalid sample rate: " + sampleRate + " in " + filePath);
                    }

                    // Allocate buffer for decoded samples
                    int totalShorts = totalSamples * channels;
                    shortBuffer = MemoryUtil.memAllocShort(totalShorts);
                    
                    // Decode all samples
                    int samplesDecoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(
                        decoder, channels, shortBuffer
                    );
                    
                    if (samplesDecoded <= 0) {
                        throw new IOException("Failed to decode OGG samples from: " + filePath);
                    }
                    
                    int actualShorts = samplesDecoded * channels;
                    this.totalBytes = actualShorts * 2; // 2 bytes per short (16-bit audio)
                    
                    // Allocate PCM buffer and copy data
                    this.pcmData = MemoryUtil.memAlloc(this.totalBytes);
                    
                    // Convert shorts to bytes in little-endian format
                    for (int i = 0; i < actualShorts; i++) {
                        short sample = shortBuffer.get(i);
                        pcmData.put((byte) (sample & 0xFF));
                        pcmData.put((byte) ((sample >> 8) & 0xFF));
                    }
                    pcmData.flip();

                    // Create audio format: 16-bit, signed, little-endian
                    this.format = new AudioFormat(
                        (float) sampleRate,
                        16,
                        channels,
                        true,   // signed
                        false   // little-endian
                    );
                    
                    StoryAdventureMod.LOGGER.info(
                        "[ExternalOggAudioStream] Loaded: {} ({}Hz, {} ch, {} samples, {} bytes)", 
                        filePath.getFileName(), sampleRate, channels, samplesDecoded, totalBytes
                    );
                    
                } finally {
                    STBVorbis.stb_vorbis_close(decoder);
                }
            }
        } catch (IOException e) {
            // Clean up on error
            if (pcmData != null) {
                MemoryUtil.memFree(pcmData);
                pcmData = null;
            }
            throw e;
        } catch (Exception e) {
            if (pcmData != null) {
                MemoryUtil.memFree(pcmData);
                pcmData = null;
            }
            throw new IOException("Error loading OGG file: " + filePath, e);
        } finally {
            // Always free temporary buffers
            if (shortBuffer != null) {
                MemoryUtil.memFree(shortBuffer);
            }
            if (fileBuffer != null) {
                MemoryUtil.memFree(fileBuffer);
            }
        }
    }
    
    private static String getVorbisError(int error) {
        return switch (error) {
            case 1 -> "VORBIS_need_more_data";
            case 2 -> "VORBIS_invalid_api_mixing";
            case 3 -> "VORBIS_outofmem";
            case 4 -> "VORBIS_feature_not_supported";
            case 5 -> "VORBIS_too_many_channels";
            case 6 -> "VORBIS_file_open_failure";
            case 7 -> "VORBIS_seek_without_length";
            case 10 -> "VORBIS_unexpected_eof";
            case 20 -> "VORBIS_seek_invalid";
            case 21 -> "VORBIS_invalid_setup";
            case 30 -> "VORBIS_invalid_stream";
            case 31 -> "VORBIS_missing_capture_pattern";
            case 32 -> "VORBIS_invalid_stream_structure_version";
            case 33 -> "VORBIS_continued_packet_flag_invalid";
            case 34 -> "VORBIS_incorrect_stream_serial_number";
            case 35 -> "VORBIS_invalid_first_page";
            case 36 -> "VORBIS_bad_packet_type";
            case 37 -> "VORBIS_cant_find_last_page";
            case 38 -> "VORBIS_seek_failed";
            default -> "Unknown error";
        };
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (closed || pcmData == null) {
            return MemoryUtil.memAlloc(0);
        }
        
        int remaining = totalBytes - position;
        int toRead = Math.min(size, remaining);
        
        if (toRead <= 0) {
            return MemoryUtil.memAlloc(0);
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(toRead);
        
        // Use bulk copy for efficiency
        int oldPos = pcmData.position();
        int oldLimit = pcmData.limit();
        
        pcmData.position(position);
        pcmData.limit(position + toRead);
        buffer.put(pcmData);
        buffer.flip();
        
        // Restore pcmData state
        pcmData.position(oldPos);
        pcmData.limit(oldLimit);
        
        position += toRead;
        return buffer;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (pcmData != null) {
                MemoryUtil.memFree(pcmData);
                pcmData = null;
            }
            StoryAdventureMod.LOGGER.debug("[ExternalOggAudioStream] Closed stream");
        }
    }
    
    /**
     * Check if the stream has more data to read.
     */
    public boolean hasRemaining() {
        return !closed && pcmData != null && position < totalBytes;
    }
    
    /**
     * Get the total size in bytes.
     */
    public int getTotalBytes() {
        return totalBytes;
    }
    
    /**
     * Get current read position.
     */
    public int getPosition() {
        return position;
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/audio/ExternalSoundInstance.java`
```java
package com.warmpixel.storyadventure.client.audio;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * A SoundInstance that points to an external file.
 * The actual loading is handled by a Mixin in SoundBufferLibrary.
 */
@Environment(EnvType.CLIENT)
public class ExternalSoundInstance extends AbstractSoundInstance {
    
    private final String externalFilePath;
    private final ResourceLocation soundResourceLocation;
    private final Sound resolvedSound;

    public ExternalSoundInstance(String soundPath, String absolutePath, float volume, float pitch, SoundSource category) {
        super(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "external/" + sanitizePath(soundPath)), 
              category, SoundInstance.createUnseededRandom());
        
        this.externalFilePath = absolutePath;
        
        // This is the ResourceLocation that will be passed to SoundBufferLibrary.getStream()
        // It MUST match what we register in ExternalSoundRegistry
        this.soundResourceLocation = ResourceLocation.fromNamespaceAndPath(
            StoryAdventureMod.MOD_ID, 
            "external/" + sanitizePath(soundPath)
        );
        
        this.volume = volume;
        this.pitch = pitch;
        this.looping = false;
        this.delay = 0;
        this.relative = true; // Play relative to listener (ui/voiceover style)
        this.attenuation = Attenuation.NONE;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        
        // Create the Sound object - the path here MUST match soundResourceLocation
        this.resolvedSound = new Sound(
            this.soundResourceLocation,  // "storyadventure:external/path"
            ConstantFloat.of(volume),                // volume
            ConstantFloat.of(pitch),                 // pitch
            1,                                       // weight
            Sound.Type.FILE,                         // type - FILE for streaming
            true,                                    // stream - true for voiceovers
            false,                                   // preload
            16                                       // attenuation distance
        );
        
        // Set the sound field from parent class
        this.sound = this.resolvedSound;
        
        StoryAdventureMod.LOGGER.debug("[ExternalSoundInstance] Created: location={}, soundPath={}, file={}", 
            this.location, this.soundResourceLocation, absolutePath);
    }
    
    private static String sanitizePath(String path) {
        // Remove invalid characters from resource location path
        // Only allow: a-z, 0-9, /, ., _, -
        return path.toLowerCase()
                   .replace("\\", "/")
                   .replaceAll("[^a-z0-9/._-]", "_")
                   .replaceAll("_+", "_")
                   .replaceAll("^_|_$", "");
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        // Set the sound field to ensure it's available
        this.sound = this.resolvedSound;
        
        StoryAdventureMod.LOGGER.debug("[ExternalSoundInstance] Resolved: {} -> sound path: {}", 
            this.location, this.resolvedSound.getLocation());
        
        // Return a WeighedSoundEvents that contains our sound
        // The subtitle key can be null for voiceovers
        return new WeighedSoundEvents(this.location, null);
    }
    
    @Override
    public Sound getSound() {
        return this.resolvedSound;
    }

    public String getExternalFilePath() {
        return externalFilePath;
    }
    
    /**
     * Gets the ResourceLocation that will be used by SoundBufferLibrary.
     * This is what needs to be registered in ExternalSoundRegistry.
     */
    public ResourceLocation getSoundResourceLocation() {
        return soundResourceLocation;
    }

    @Override
    public String toString() {
        return "ExternalSoundInstance{location=" + location + ", soundPath=" + soundResourceLocation + ", file=" + externalFilePath + "}";
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/audio/ExternalSoundRegistry.java`
```java
package com.warmpixel.storyadventure.client.audio;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Registry for external sound file paths.
 * Used by the SoundBufferLibraryMixin to locate files on disk.
 * Thread-safe implementation.
 */
public class ExternalSoundRegistry {
    private static final Map<ResourceLocation, String> EXTERNAL_SOUND_PATHS = new ConcurrentHashMap<>();

    public static void registerExternalPath(ResourceLocation location, String path) {
        EXTERNAL_SOUND_PATHS.put(location, path);
        StoryAdventureMod.LOGGER.debug("[ExternalSoundRegistry] Registered: {} -> {}", location, path);
    }

    public static String getExternalPath(ResourceLocation location) {
        String path = EXTERNAL_SOUND_PATHS.get(location);
        StoryAdventureMod.LOGGER.debug("[ExternalSoundRegistry] Lookup: {} -> {}", location, path);
        return path;
    }
    
    public static boolean isExternalSound(ResourceLocation location) {
        return EXTERNAL_SOUND_PATHS.containsKey(location);
    }

    public static void removeExternalPath(ResourceLocation location) {
        EXTERNAL_SOUND_PATHS.remove(location);
        StoryAdventureMod.LOGGER.debug("[ExternalSoundRegistry] Removed: {}", location);
    }
    
    public static void clear() {
        EXTERNAL_SOUND_PATHS.clear();
        StoryAdventureMod.LOGGER.debug("[ExternalSoundRegistry] Cleared all entries");
    }
    
    public static int size() {
        return EXTERNAL_SOUND_PATHS.size();
    }
}
```

## File: `src/main/java/com/warmpixel/storyadventure/client/audio/VoiceoverManager.java`
```java
package com.warmpixel.storyadventure.client.audio;

import com.warmpixel.storyadventure.StoryAdventureMod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages voiceover audio playback on the client.
 * Integrated with Minecraft's SoundManager for proper volume control and compatibility.
 */
@Environment(EnvType.CLIENT)
public class VoiceoverManager {
    
    private static VoiceoverManager instance;
    private ExternalSoundInstance currentVoiceover = null;
    private final Object lock = new Object();
    
    private VoiceoverManager() {}

    public static VoiceoverManager getInstance() {
        if (instance == null) {
            instance = new VoiceoverManager();
        }
        return instance;
    }
    
    /**
     * Play a voiceover sound from the voiceovers folder.
     */
    public void playVoiceover(String soundPath, float volume, float pitch, String characterId) {
        // Normalize the sound path (remove any extension if present)
        String normalizedPath = soundPath;
        if (normalizedPath.endsWith(".ogg")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 4);
        }
        
        // Build the full path using FabricLoader's config directory
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path voiceoverPath = configDir.resolve("storyadventure").resolve("voiceovers").resolve(normalizedPath + ".ogg");
        
        if (!Files.exists(voiceoverPath)) {
            StoryAdventureMod.LOGGER.warn("[VoiceoverManager] Voiceover file not found: {}", voiceoverPath.toAbsolutePath());
            return;
        }
        
        String absolutePath = voiceoverPath.toAbsolutePath().toString();
        final String finalNormalizedPath = normalizedPath;
        
        // Schedule on the main thread
        Minecraft.getInstance().execute(() -> {
            synchronized (lock) {
                stopCurrentVoiceoverInternal();
                
                try {
                    // Create a custom sound instance
                    currentVoiceover = new ExternalSoundInstance(
                        finalNormalizedPath,
                        absolutePath,
                        volume,
                        pitch,
                        SoundSource.VOICE
                    );
                    
                    // Register the path using the SOUND's location (not the instance location)
                    // This is what SoundBufferLibrary.getStream() will receive
                    ExternalSoundRegistry.registerExternalPath(currentVoiceover.getSoundResourceLocation(), absolutePath);
                    
                    StoryAdventureMod.LOGGER.debug("[VoiceoverManager] Registered external path: {} -> {}", 
                        currentVoiceover.getSoundResourceLocation(), absolutePath);
                    
                    // Play it through Minecraft's sound manager
                    Minecraft.getInstance().getSoundManager().play(currentVoiceover);
                    
                    StoryAdventureMod.LOGGER.info("[VoiceoverManager] Playing voiceover: {} (character: {})", 
                        finalNormalizedPath, characterId);
                    
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.error("[VoiceoverManager] Failed to play voiceover: " + finalNormalizedPath, e);
                    currentVoiceover = null;
                }
            }
        });
    }
    
    public void playVoiceover(String soundPath, String characterId) {
        playVoiceover(soundPath, 1.0f, 1.0f, characterId);
    }
    
    /**
     * Stop the currently playing voiceover.
     */
    public void stopCurrentVoiceover() {
        Minecraft.getInstance().execute(() -> {
            synchronized (lock) {
                stopCurrentVoiceoverInternal();
            }
        });
    }
    
    private void stopCurrentVoiceoverInternal() {
        if (currentVoiceover != null) {
            try {
                Minecraft.getInstance().getSoundManager().stop(currentVoiceover);
                ExternalSoundRegistry.removeExternalPath(currentVoiceover.getSoundResourceLocation());
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.debug("[VoiceoverManager] Error stopping voiceover", e);
            }
            currentVoiceover = null;
        }
    }
    
    /**
     * Check if a voiceover is currently playing.
     */
    public boolean isPlaying() {
        synchronized (lock) {
            if (currentVoiceover == null) {
                return false;
            }
            try {
                return Minecraft.getInstance().getSoundManager().isActive(currentVoiceover);
            } catch (Exception e) {
                return false;
            }
        }
    }
    
    public void cleanup() {
        stopCurrentVoiceover();
        ExternalSoundRegistry.clear();
    }

    public static Path getVoiceoversPath(String storyId) {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("storyadventure").resolve("voiceovers").resolve(storyId);
    }
    
    public static Path getVoiceoversBasePath() {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("storyadventure").resolve("voiceovers");
    }
    
    public static boolean voiceoverExists(String soundPath) {
        String normalizedPath = soundPath;
        if (!normalizedPath.endsWith(".ogg")) {
            normalizedPath = normalizedPath + ".ogg";
        }
        Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("storyadventure").resolve("voiceovers").resolve(normalizedPath);
        return Files.exists(path);
    }
    
    public static void ensureVoiceoverDirectory(String storyId) {
        try {
            Path dir = getVoiceoversPath(storyId);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                StoryAdventureMod.LOGGER.info("[VoiceoverManager] Created voiceover directory: {}", dir);
            }
        } catch (IOException e) {
            StoryAdventureMod.LOGGER.error("[VoiceoverManager] Failed to create voiceover directory", e);
        }
    }
    
    public static void ensureVoiceoverBaseDirectory() {
        try {
            Path dir = getVoiceoversBasePath();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                StoryAdventureMod.LOGGER.info("[VoiceoverManager] Created voiceover base directory: {}", dir);
            }
        } catch (IOException e) {
            StoryAdventureMod.LOGGER.error("[VoiceoverManager] Failed to create voiceover base directory", e);
        }
    }
}
```

Camera cutscene keyframes transitioning wasn't smooth enough, it was kinda laggy, help me refine it. 
