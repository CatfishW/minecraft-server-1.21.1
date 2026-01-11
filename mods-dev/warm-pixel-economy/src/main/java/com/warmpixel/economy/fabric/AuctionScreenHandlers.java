package com.warmpixel.economy.fabric;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class AuctionScreenHandlers {
    public static final ExtendedScreenHandlerType<AuctionMenu, AuctionMenu.Data> AUCTION_BROWSER =
            new ExtendedScreenHandlerType<>(AuctionMenu::new, AuctionMenu.Data.STREAM_CODEC);
    public static final ExtendedScreenHandlerType<AuctionConfirmMenu, AuctionConfirmMenu.Data> AUCTION_CONFIRM =
            new ExtendedScreenHandlerType<>(AuctionConfirmMenu::new, AuctionConfirmMenu.Data.STREAM_CODEC);

    private static boolean registered = false;

    private AuctionScreenHandlers() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(BuiltInRegistries.MENU, ResourceLocation.fromNamespaceAndPath(WarmPixelEconomyMod.MOD_ID, "auction_browser"), AUCTION_BROWSER);
        Registry.register(BuiltInRegistries.MENU, ResourceLocation.fromNamespaceAndPath(WarmPixelEconomyMod.MOD_ID, "auction_confirm"), AUCTION_CONFIRM);
    }

}
