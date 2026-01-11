package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.AuctionListing;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;

public final class AuctionGui {
    private AuctionGui() {
    }

    public static void open(ServerPlayer player, String query, int page) {
        int pageSize = WarmPixelEconomyMod.getContext().config().shop.pageSize;
        WarmPixelEconomyMod.getContext().auctionService().listOpenListings(query, page, pageSize)
                .thenAccept(listings -> player.server.execute(() -> openMenu(player, listings, query, page)));
    }

    private static void openMenu(ServerPlayer player, List<AuctionListing> listings, String query, int page) {
        List<AuctionListingView> views = new ArrayList<>(listings.size());
        for (AuctionListing listing : listings) {
            views.add(AuctionListingView.from(listing));
        }
        AuctionMenu.Data data = new AuctionMenu.Data(views, query == null ? "" : query, page);
        player.openMenu(new ExtendedScreenHandlerFactory<AuctionMenu.Data>() {
            @Override
            public AuctionMenu.Data getScreenOpeningData(ServerPlayer player) {
                return data;
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable("ui.warm_pixel_economy.auction.title");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                return new AuctionMenu(syncId, inventory, (ServerPlayer) player, listings, query == null ? "" : query, page);
            }
        });
    }
}
