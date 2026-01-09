package com.novus.auth.client;

import com.novus.auth.networking.AuthPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class NovusAuthClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(AuthPackets.OpenAuthScreenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new AuthScreen(payload.registered()));
            });
        });
    }
}
