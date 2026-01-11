package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.EconomyResult;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ShopNetworking {
    private static boolean payloadsRegistered = false;

    private ShopNetworking() {
    }

    public static void registerPayloadTypes() {
        if (payloadsRegistered) {
            return;
        }
        payloadsRegistered = true;
        PayloadTypeRegistry.playC2S().register(ShopTradeActionPayload.TYPE, ShopTradeActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ShopTradeResultPayload.TYPE, ShopTradeResultPayload.STREAM_CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ShopTradeActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            EconomyContext economyContext = WarmPixelEconomyMod.getContext();
            if (economyContext == null || economyContext.shopService() == null) {
                sendResult(player, EconomyResult.fail("message.warm_pixel_economy.shop_unavailable"), economyContext);
                return;
            }
            if (!(player.containerMenu instanceof ShopTradeMenu menu)) {
                sendResult(player, EconomyResult.fail("message.warm_pixel_economy.no_active_trade"), economyContext);
                return;
            }
            if (!menu.offer().offerId().equals(payload.offerId())) {
                sendResult(player, EconomyResult.fail("message.warm_pixel_economy.offer_mismatch"), economyContext);
                return;
            }
            int units = Math.max(1, payload.units());
            CompletableFuture<EconomyResult> resultFuture;
            if (payload.mode() == TradeMode.SELL) {
                resultFuture = economyContext.shopService().sellToShop(player, payload.offerId(), economyContext.config().defaultCurrency, units);
            } else {
                resultFuture = economyContext.shopService().buyOffer(player, payload.offerId(), economyContext.config().defaultCurrency, units);
            }
            resultFuture.thenAccept(result -> sendResult(player, result, economyContext));
        });
    }

    private static void sendResult(ServerPlayer player, EconomyResult result, EconomyContext context) {
        if (context == null) {
            ServerPlayNetworking.send(player, new ShopTradeResultPayload(result.success(), result.messageKey(), toArgs(result), 0L));
            return;
        }
        context.balanceService().getBalance(player.getUUID(), context.config().defaultCurrency)
                .thenAccept(balance -> ServerPlayNetworking.send(player,
                        new ShopTradeResultPayload(result.success(), result.messageKey(), toArgs(result), balance)));
    }

    private static List<String> toArgs(EconomyResult result) {
        if (result.messageArgs() == null || result.messageArgs().length == 0) {
            return List.of();
        }
        return Arrays.stream(result.messageArgs())
                .map(String::valueOf)
                .toList();
    }
}
