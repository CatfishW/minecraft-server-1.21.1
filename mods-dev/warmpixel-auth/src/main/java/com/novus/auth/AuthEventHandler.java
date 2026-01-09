package com.novus.auth;

import com.novus.auth.networking.AuthPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class AuthEventHandler {
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            
            // Skip auth for single player mode
            if (!NovusAuth.isAuthRequired()) {
                NovusAuth.getInstance().getAuthManager().authenticate(player.getUUID());
                return;
            }
            
            NovusAuth.getInstance().getAuthManager().deauthenticate(player.getUUID());
            
            NovusAuth.getInstance().getAuthService().isRegistered(player.getUUID()).thenAccept(registered -> {
                server.execute(() -> {
                    ServerPlayNetworking.send(player, new AuthPackets.OpenAuthScreenPayload(registered));
                });
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            NovusAuth.getInstance().getAuthManager().deauthenticate(handler.player.getUUID());
        });
    }
}
