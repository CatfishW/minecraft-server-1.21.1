package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.ShopOffer;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class ShopTradeGui {
    private ShopTradeGui() {
    }

    public static void open(ServerPlayer player, ShopOffer offer, TradeMode mode, String category, String query, int page) {
        EconomyContext context = WarmPixelEconomyMod.getContext();
        context.balanceService().getBalance(player.getUUID(), context.config().defaultCurrency)
                .thenAccept(balance -> player.server.execute(() -> openMenu(player, offer, mode, balance, category, query, page)));
    }

    private static void openMenu(ServerPlayer player, ShopOffer offer, TradeMode mode, long balance, String category, String query, int page) {
        EconomyContext context = WarmPixelEconomyMod.getContext();
        ShopOfferView view = ShopOfferView.from(offer);
        ShopTradeMenu.Data data = new ShopTradeMenu.Data(view, mode, balance, context.config().taxes.shopTaxRate, 
                category == null ? "" : category, query == null ? "" : query, page);
        player.openMenu(new ExtendedScreenHandlerFactory<ShopTradeMenu.Data>() {
            @Override
            public ShopTradeMenu.Data getScreenOpeningData(ServerPlayer player) {
                return data;
            }

            @Override
            public Component getDisplayName() {
                return mode == TradeMode.BUY
                        ? Component.translatable("ui.warm_pixel_economy.trade.title.buy")
                        : Component.translatable("ui.warm_pixel_economy.trade.title.sell");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                return new ShopTradeMenu(syncId, view, mode, balance, context.config().taxes.shopTaxRate, category, query, page);
            }
        });
    }
}
