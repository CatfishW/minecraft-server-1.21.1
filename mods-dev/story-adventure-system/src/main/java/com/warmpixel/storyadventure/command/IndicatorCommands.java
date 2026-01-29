package com.warmpixel.storyadventure.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorAddPacket;
import com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorClearPacket;
import com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorRemovePacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Commands for testing and managing item/block indicators.
 * 
 * Usage:
 *   /indicator show <id> [x y z] [label] [color] - Show indicator at position
 *   /indicator here <id> [label] - Show indicator at player's current position
 *   /indicator hide <id> - Hide specific indicator
 *   /indicator clear - Clear all indicators
 *   /indicator record <id> - Record current position and copy JSON to chat
 * 
 * Requires operator permissions (level 2).
 */
public class IndicatorCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("indicator")
                .requires(source -> source.hasPermission(2))
                
                // /indicator show <id> <x> <y> <z> [label] [color]
                .then(Commands.literal("show")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                            .executes(ctx -> {
                                String id = StringArgumentType.getString(ctx, "id");
                                BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                return showIndicator(ctx.getSource(), id, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, "", 0x00FFFF, 1.0f);
                            })
                            .then(Commands.argument("label", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    String label = StringArgumentType.getString(ctx, "label");
                                    return showIndicator(ctx.getSource(), id, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, label, 0x00FFFF, 1.0f);
                                })
                            )
                        )
                    )
                )
                
                // /indicator here <id> [label]
                .then(Commands.literal("here")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Vec3 pos = player.position();
                            return showIndicatorAndRecord(ctx.getSource(), id, pos.x, pos.y, pos.z, "", 0x00FFFF, 1.0f);
                        })
                        .then(Commands.argument("label", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String id = StringArgumentType.getString(ctx, "id");
                                String label = StringArgumentType.getString(ctx, "label");
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                Vec3 pos = player.position();
                                return showIndicatorAndRecord(ctx.getSource(), id, pos.x, pos.y, pos.z, label, 0x00FFFF, 1.0f);
                            })
                        )
                    )
                )
                
                // /indicator block <id> - Use looked-at block
                .then(Commands.literal("block")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            var hit = player.pick(64, 0, false);
                            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                                var blockHit = (net.minecraft.world.phys.BlockHitResult) hit;
                                BlockPos pos = blockHit.getBlockPos();
                                return showIndicatorAndRecord(ctx.getSource(), id, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, "", 0x00FFFF, 1.0f);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("§c未对准任何方块"));
                                return 0;
                            }
                        })
                        .then(Commands.argument("label", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String id = StringArgumentType.getString(ctx, "id");
                                String label = StringArgumentType.getString(ctx, "label");
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                var hit = player.pick(64, 0, false);
                                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                                    var blockHit = (net.minecraft.world.phys.BlockHitResult) hit;
                                    BlockPos pos = blockHit.getBlockPos();
                                    return showIndicatorAndRecord(ctx.getSource(), id, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, label, 0x00FFFF, 1.0f);
                                } else {
                                    ctx.getSource().sendFailure(Component.literal("§c未对准任何方块"));
                                    return 0;
                                }
                            })
                        )
                    )
                )
                
                // /indicator hide <id>
                .then(Commands.literal("hide")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            return hideIndicator(ctx.getSource(), id);
                        })
                    )
                )
                
                // /indicator clear
                .then(Commands.literal("clear")
                    .executes(ctx -> clearIndicators(ctx.getSource()))
                )
                
                // /indicator color <id> <color> - Change indicator color (hex like FF00FF)
                .then(Commands.literal("color")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("color", StringArgumentType.word())
                            .executes(ctx -> {
                                String id = StringArgumentType.getString(ctx, "id");
                                String colorStr = StringArgumentType.getString(ctx, "color");
                                try {
                                    int color = Integer.parseInt(colorStr.replace("#", ""), 16);
                                    ctx.getSource().sendSuccess(() -> Component.literal("§a颜色更新为: #" + colorStr + " (重新执行 show 命令生效)"), false);
                                    return 1;
                                } catch (NumberFormatException e) {
                                    ctx.getSource().sendFailure(Component.literal("§c无效颜色格式，请使用十六进制如: FF00FF"));
                                    return 0;
                                }
                            })
                        )
                    )
                )
                
                // /indicator json <id> - Generate JSON snippet for story file
                .then(Commands.literal("json")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Vec3 pos = player.position();
                            return generateJson(ctx.getSource(), id, pos.x, pos.y, pos.z, "目标");
                        })
                        .then(Commands.argument("label", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String id = StringArgumentType.getString(ctx, "id");
                                String label = StringArgumentType.getString(ctx, "label");
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                Vec3 pos = player.position();
                                return generateJson(ctx.getSource(), id, pos.x, pos.y, pos.z, label);
                            })
                        )
                    )
                )
        );
        
        StoryAdventureMod.LOGGER.info("[IndicatorCommands] Registered /indicator command");
    }

    private static int showIndicator(CommandSourceStack source, String id, double x, double y, double z, 
                                     String label, int color, float radius) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            
            ItemBlockIndicatorAddPacket packet = new ItemBlockIndicatorAddPacket(
                id, x, y, z, color, label, radius, true, true
            );
            ServerPlayNetworking.send(player, packet);
            
            source.sendSuccess(() -> Component.literal(String.format(
                "§a显示指示器 '%s' 于 (%.1f, %.1f, %.1f)", id, x, y, z
            )), false);
            
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c发送指示器失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int showIndicatorAndRecord(CommandSourceStack source, String id, double x, double y, double z, 
                                              String label, int color, float radius) {
        // First show the indicator
        int result = showIndicator(source, id, x, y, z, label, color, radius);
        
        if (result > 0) {
            // Then generate and show the JSON
            String json = generateIndicatorJson(id, x, y, z, label, color, radius);
            
            // Create clickable message
            Component clickableJson = Component.literal("§7[复制JSON]")
                .withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, json))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击复制到剪贴板\n\n" + json)))
                );
            
            source.sendSuccess(() -> Component.literal("§e坐标已记录! ").append(clickableJson), false);
        }
        
        return result;
    }

    private static int hideIndicator(CommandSourceStack source, String id) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            
            ItemBlockIndicatorRemovePacket packet = new ItemBlockIndicatorRemovePacket(id);
            ServerPlayNetworking.send(player, packet);
            
            source.sendSuccess(() -> Component.literal("§a已隐藏指示器: " + id), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c隐藏指示器失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int clearIndicators(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            
            ItemBlockIndicatorClearPacket packet = new ItemBlockIndicatorClearPacket();
            ServerPlayNetworking.send(player, packet);
            
            source.sendSuccess(() -> Component.literal("§a已清除所有指示器"), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c清除指示器失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int generateJson(CommandSourceStack source, String id, double x, double y, double z, String label) {
        String json = generateIndicatorJson(id, x, y, z, label, 0x00FFFF, 1.0f);
        
        // Create clickable message
        Component clickableJson = Component.literal("§b§n[点击复制JSON]")
            .withStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, json))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(json)))
            );
        
        source.sendSuccess(() -> Component.literal("§a生成的JSON (用于story actions): \n§7").append(clickableJson), false);
        source.sendSuccess(() -> Component.literal("§8" + json), false);
        
        return 1;
    }

    private static String generateIndicatorJson(String id, double x, double y, double z, String label, int color, float radius) {
        return String.format(
            """
            {
              "type": "INDICATOR",
              "action": "show",
              "id": "%s",
              "x": %.2f,
              "y": %.2f,
              "z": %.2f,
              "label": "%s",
              "color": "#%06X",
              "radius": %.1f,
              "show_arrow": true,
              "show_circle": true
            }""",
            id, x, y, z, label, color & 0xFFFFFF, radius
        );
    }
}
