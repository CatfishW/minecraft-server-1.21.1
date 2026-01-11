package com.flaneconomy.client;

import com.flaneconomy.network.ClaimShopPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class FlanEconomyClientNetworking {
    private FlanEconomyClientNetworking() {
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ClaimShopPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof ClaimShopScreen shopScreen) {
                    shopScreen.setData(ClaimShopData.fromPayload(payload));
                } else {
                    context.client().setScreen(new ClaimShopScreen(ClaimShopData.fromPayload(payload)));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(com.flaneconomy.network.ClaimMarketPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof ClaimMarketScreen marketScreen) {
                    marketScreen.updateData(payload);
                } else {
                    context.client().setScreen(new ClaimMarketScreen(payload));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(com.flaneconomy.network.PlayerClaimsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof MyClaimsScreen myClaimsScreen) {
                    myClaimsScreen.updateData(payload);
                } else {
                    context.client().setScreen(new MyClaimsScreen(payload));
                }
            });
        });
    }
}
