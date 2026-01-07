package com.novus.auth;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.novus.auth.networking.AuthPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AuthCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("login")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (NovusAuth.getInstance().getAuthManager().isAuthenticated(player.getUUID())) {
                    player.sendSystemMessage(Component.translatable("novus_auth.msg.already_authenticated"));
                    return 0;
                }
                ServerPlayNetworking.send(player, new AuthPackets.OpenAuthScreenPayload(true));
                return 1;
            })
        );

        dispatcher.register(Commands.literal("register")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (NovusAuth.getInstance().getAuthManager().isAuthenticated(player.getUUID())) {
                    player.sendSystemMessage(Component.translatable("novus_auth.msg.already_authenticated"));
                    return 0;
                }
                ServerPlayNetworking.send(player, new AuthPackets.OpenAuthScreenPayload(false));
                return 1;
            })
        );

        dispatcher.register(Commands.literal("changepassword")
            .then(Commands.argument("oldPassword", StringArgumentType.string())
            .then(Commands.argument("newPassword", StringArgumentType.string())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                String oldPass = StringArgumentType.getString(context, "oldPassword");
                String newPass = StringArgumentType.getString(context, "newPassword");

                NovusAuth.getInstance().getAuthService().changePassword(player.getUUID(), oldPass, newPass).thenAccept(result -> {
                    if (result == AuthService.AuthResult.SUCCESS) {
                        player.sendSystemMessage(Component.translatable("novus_auth.msg.password_changed"));
                    } else {
                        player.sendSystemMessage(Component.translatable("novus_auth.msg.password_change_failed"));
                    }
                });
                return 1;
            })))
        );
    }
}
