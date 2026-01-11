package com.warmpixel.npcbusdriver;

import com.warmpixel.npcbusdriver.client.BusDriverScreen;
import com.warmpixel.npcbusdriver.network.OpenWandGuiPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class NPCBusDriverClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenWandGuiPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new BusDriverScreen(payload.points()));
            });
        });
    }
}
