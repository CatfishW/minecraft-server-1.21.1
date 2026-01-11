package com.novus.items.client;

import com.novus.items.FlightManager;
import com.novus.items.NovusItemsMod;
import com.novus.items.bounty.BountyBoardManager;
import com.novus.items.client.bounty.BountyBoardScreen;
import com.novus.items.client.bounty.TaskPoolScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

public class NovusItemsClient implements ClientModInitializer {
    private static boolean hudEnabled = false;
    private static boolean permanent = false;
    private static long remainingSeconds = 0;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BountyBoardManager.BountyBoardOpenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.setScreen(new BountyBoardScreen(payload.available(), payload.my(), payload.refreshTimes(), payload.isOp(), payload.systemRefreshAddCount(), payload.playerMaxActive(), payload.taskPool()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(BountyBoardManager.BountyBoardSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof BountyBoardScreen screen) {
                    screen.updateData(payload.available(), payload.my(), payload.refreshTimes(), payload.isOp(), payload.systemRefreshAddCount(), payload.playerMaxActive(), payload.taskPool());
                } else if (Minecraft.getInstance().screen instanceof TaskPoolScreen screen) {
                    screen.updatePool(payload.taskPool());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FlightManager.FlightTimeHudPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                hudEnabled = payload.enabled();
                permanent = payload.permanent();
                remainingSeconds = payload.remainingSeconds();
            });
        });

        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            if (!hudEnabled) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.font == null) {
                return;
            }

            String value;
            if (permanent) {
                value = "永久";
            } else if (remainingSeconds <= 0) {
                value = "无";
            } else {
                value = FlightManager.formatRemainingTime(remainingSeconds);
            }

            graphics.drawString(minecraft.font, "飞行时间: " + value, 6, 6, 0xFFFFFF);
        });

        NovusItemsMod.LOGGER.info("Novus Items Client Initialized");
    }
}
