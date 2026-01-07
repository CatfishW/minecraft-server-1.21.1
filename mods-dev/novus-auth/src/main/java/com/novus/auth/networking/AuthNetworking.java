package com.novus.auth.networking;

import com.novus.auth.AuthService;
import com.novus.auth.NovusAuth;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AuthNetworking {
    public static void register() {
        PayloadTypeRegistry.playS2C().register(AuthPackets.OpenAuthScreenPayload.TYPE, AuthPackets.OpenAuthScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AuthPackets.AuthActionPayload.TYPE, AuthPackets.AuthActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AuthPackets.AuthActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String password = payload.password();

            NovusAuth.getInstance().getAuthService().isRegistered(player.getUUID()).thenAccept(registered -> {
                if (registered) {
                    handleLogin(player, password);
                } else {
                    handleRegister(player, password);
                }
            });
        });
    }

    private static void handleLogin(ServerPlayer player, String password) {
        NovusAuth.getInstance().getAuthService().login(player.getUUID(), password).thenAccept(result -> {
            if (result == AuthService.AuthResult.SUCCESS) {
                NovusAuth.getInstance().getAuthManager().authenticate(player.getUUID());
                player.sendSystemMessage(Component.translatable("novus_auth.msg.login_success"));
            } else {
                player.sendSystemMessage(Component.translatable("novus_auth.msg.login_failed"));
                ServerPlayNetworking.send(player, new AuthPackets.OpenAuthScreenPayload(true));
            }
        });
    }

    private static void handleRegister(ServerPlayer player, String password) {
        NovusAuth.getInstance().getAuthService().register(player.getUUID(), player.getScoreboardName(), password).thenAccept(result -> {
            if (result == AuthService.AuthResult.SUCCESS) {
                NovusAuth.getInstance().getAuthManager().authenticate(player.getUUID());
                player.sendSystemMessage(Component.translatable("novus_auth.msg.register_success"));
            } else if (result == AuthService.AuthResult.INVALID_PASSWORD) {
                player.sendSystemMessage(Component.translatable("novus_auth.msg.invalid_password"));
                ServerPlayNetworking.send(player, new AuthPackets.OpenAuthScreenPayload(false));
            } else {
                player.sendSystemMessage(Component.translatable("novus_auth.msg.register_failed"));
                ServerPlayNetworking.send(player, new AuthPackets.OpenAuthScreenPayload(false));
            }
        });
    }
}
