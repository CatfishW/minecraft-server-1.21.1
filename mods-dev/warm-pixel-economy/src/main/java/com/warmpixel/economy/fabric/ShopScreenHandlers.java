package com.warmpixel.economy.fabric;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class ShopScreenHandlers {
    public static final ExtendedScreenHandlerType<ShopMenu, ShopMenu.Data> SHOP_BROWSER =
            new ExtendedScreenHandlerType<>(ShopMenu::new, ShopMenu.Data.STREAM_CODEC);
    public static final ExtendedScreenHandlerType<ShopTradeMenu, ShopTradeMenu.Data> SHOP_TRADE =
            new ExtendedScreenHandlerType<>(ShopTradeMenu::new, ShopTradeMenu.Data.STREAM_CODEC);

    private static boolean registered = false;

    private ShopScreenHandlers() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(BuiltInRegistries.MENU, ResourceLocation.fromNamespaceAndPath(WarmPixelEconomyMod.MOD_ID, "shop_browser"), SHOP_BROWSER);
        Registry.register(BuiltInRegistries.MENU, ResourceLocation.fromNamespaceAndPath(WarmPixelEconomyMod.MOD_ID, "shop_trade"), SHOP_TRADE);
    }
}
