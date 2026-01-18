package com.warmpixel.storyadventure.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.warmpixel.storyadventure.core.admin.AdminToolManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StoryAdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("storyadmin")
            .requires(source -> source.hasPermission(2))
            
            // set_interaction_item on/off
            .then(Commands.literal("set_interaction_item")
                .then(Commands.argument("state", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("on");
                        builder.suggest("off");
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String state = StringArgumentType.getString(ctx, "state");
                        
                        boolean enable = "on".equalsIgnoreCase(state);
                        AdminToolManager.setRecording(player, enable);
                        
                        return 1;
                    })))
        );
    }
}
