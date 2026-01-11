package com.warmpixel.economy.fabric.client;

import com.warmpixel.economy.fabric.AuctionScreenHandlers;
import com.warmpixel.economy.fabric.ShopNetworking;
import com.warmpixel.economy.fabric.ShopScreenHandlers;
import com.warmpixel.economy.fabric.ShopTradeResultPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class WarmPixelEconomyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ShopScreenHandlers.register();
        AuctionScreenHandlers.register();
        ShopNetworking.registerPayloadTypes();
        MenuScreens.register(ShopScreenHandlers.SHOP_BROWSER, ShopBrowserScreen::new);
        MenuScreens.register(ShopScreenHandlers.SHOP_TRADE, ShopTradeScreen::new);
        MenuScreens.register(AuctionScreenHandlers.AUCTION_BROWSER, AuctionBrowserScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(ShopTradeResultPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof ShopTradeScreen tradeScreen) {
                    tradeScreen.handleResult(payload);
                }
            });
        });
    }
}
