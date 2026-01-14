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
