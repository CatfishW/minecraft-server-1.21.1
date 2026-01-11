package com.warmpixel.economy.fabric;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class AuctionConfirmGui {
    private AuctionConfirmGui() {
    }

    public static void open(ServerPlayer player, AuctionListingView listing) {
        player.openMenu(new ExtendedScreenHandlerFactory<AuctionConfirmMenu.Data>() {
            @Override
            public AuctionConfirmMenu.Data getScreenOpeningData(ServerPlayer player) {
                return new AuctionConfirmMenu.Data(listing);
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable("ui.warm_pixel_economy.trade.title.buy");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                return new AuctionConfirmMenu(syncId, inventory, (ServerPlayer) player, listing);
            }
        });
    }
}
