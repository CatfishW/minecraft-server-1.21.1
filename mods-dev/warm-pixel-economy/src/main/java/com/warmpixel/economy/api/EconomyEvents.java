package com.warmpixel.economy.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.UUID;

public final class EconomyEvents {
    public static final Event<BalanceChanged> BALANCE_CHANGED = EventFactory.createArrayBacked(
            BalanceChanged.class,
            listeners -> (accountId, currencyId, delta, reason, txId) -> {
                for (BalanceChanged listener : listeners) {
                    listener.onBalanceChanged(accountId, currencyId, delta, reason, txId);
                }
            }
    );

    public static final Event<ShopTransaction> SHOP_TRANSACTION = EventFactory.createArrayBacked(
            ShopTransaction.class,
            listeners -> (offerId, buyerId, itemHash, count, total, txId) -> {
                for (ShopTransaction listener : listeners) {
                    listener.onShopTransaction(offerId, buyerId, itemHash, count, total, txId);
                }
            }
    );

    public static final Event<AuctionListingCreated> AUCTION_LISTING_CREATED = EventFactory.createArrayBacked(
            AuctionListingCreated.class,
            listeners -> (listingId, sellerId, itemHash) -> {
                for (AuctionListingCreated listener : listeners) {
                    listener.onAuctionListingCreated(listingId, sellerId, itemHash);
                }
            }
    );

    public static final Event<AuctionBidPlaced> AUCTION_BID_PLACED = EventFactory.createArrayBacked(
            AuctionBidPlaced.class,
            listeners -> (listingId, bidderId, amount) -> {
                for (AuctionBidPlaced listener : listeners) {
                    listener.onAuctionBidPlaced(listingId, bidderId, amount);
                }
            }
    );

    public static final Event<AuctionListingExpired> AUCTION_LISTING_EXPIRED = EventFactory.createArrayBacked(
            AuctionListingExpired.class,
            listeners -> listingId -> {
                for (AuctionListingExpired listener : listeners) {
                    listener.onAuctionListingExpired(listingId);
                }
            }
    );

    public static final Event<DeliveryCreated> DELIVERY_CREATED = EventFactory.createArrayBacked(
            DeliveryCreated.class,
            listeners -> (deliveryId, ownerId, type) -> {
                for (DeliveryCreated listener : listeners) {
                    listener.onDeliveryCreated(deliveryId, ownerId, type);
                }
            }
    );

    private EconomyEvents() {
    }

    @FunctionalInterface
    public interface BalanceChanged {
        void onBalanceChanged(UUID accountId, String currencyId, long delta, String reason, String txId);
    }

    @FunctionalInterface
    public interface ShopTransaction {
        void onShopTransaction(String offerId, UUID buyerId, String itemHash, int count, long total, String txId);
    }

    @FunctionalInterface
    public interface AuctionListingCreated {
        void onAuctionListingCreated(String listingId, UUID sellerId, String itemHash);
    }

    @FunctionalInterface
    public interface AuctionBidPlaced {
        void onAuctionBidPlaced(String listingId, UUID bidderId, long amount);
    }

    @FunctionalInterface
    public interface AuctionListingExpired {
        void onAuctionListingExpired(String listingId);
    }

    @FunctionalInterface
    public interface DeliveryCreated {
        void onDeliveryCreated(String deliveryId, UUID ownerId, String type);
    }
}
