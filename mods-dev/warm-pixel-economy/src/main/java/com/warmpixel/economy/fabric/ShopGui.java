package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.ShopOffer;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;

public final class ShopGui {
    private ShopGui() {
    }

    public static void open(ServerPlayer player, String category, String query, int page) {
        int pageSize = WarmPixelEconomyMod.getContext().config().shop.pageSize;
        WarmPixelEconomyMod.getContext().shopService()
                .listOffers(WarmPixelEconomyMod.getContext().config().shop.adminShopId, category, query, page, pageSize)
                .thenAccept(offers -> player.server.execute(() -> openMenu(player, offers, category, query, page)));
    }

    private static void openMenu(ServerPlayer player, List<ShopOffer> offers, String category, String query, int page) {
        List<ShopOfferView> views = new ArrayList<>(offers.size());
        for (ShopOffer offer : offers) {
            views.add(ShopOfferView.from(offer));
        }
        ShopMenu.Data data = new ShopMenu.Data(views, category == null ? "" : category, query == null ? "" : query, page);
        player.openMenu(new ExtendedScreenHandlerFactory<ShopMenu.Data>() {
            @Override
            public ShopMenu.Data getScreenOpeningData(ServerPlayer player) {
                return data;
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable("ui.warm_pixel_economy.shop.title");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                return new ShopMenu(syncId, inventory, (ServerPlayer) player, offers,
                        category == null ? "" : category, query == null ? "" : query, page);
            }
        });
    }
}
