package com.warmpixel.economy.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.warmpixel.economy.core.AuctionListing;
import com.warmpixel.economy.core.AuctionStatus;
import com.warmpixel.economy.core.Delivery;
import com.warmpixel.economy.core.DeliveryStatus;
import com.warmpixel.economy.core.ShopOffer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class EconomyStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private Data data;

    public EconomyStorage(Path path) {
        this.path = path;
        this.data = loadInternal(path);
    }

    public synchronized void save() {
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
            // Best-effort persistence.
        }
    }

    public synchronized void createOffer(ShopOffer offer) {
        data.shopOffers.put(offer.offerId(), offer);
        save();
    }

    public synchronized void clearOffers(String shopId) {
        data.shopOffers.entrySet().removeIf(entry -> Objects.equals(entry.getValue().shopId(), shopId));
        save();
    }

    public synchronized Optional<ShopOffer> getOffer(String offerId) {
        return Optional.ofNullable(data.shopOffers.get(offerId));
    }

    public synchronized List<ShopOffer> listOffers(String shopId, String category, String query, int offset, int limit) {
        List<ShopOffer> offers = new ArrayList<>();
        for (ShopOffer offer : data.shopOffers.values()) {
            if (!Objects.equals(offer.shopId(), shopId)) {
                continue;
            }
            if (category != null && !category.isBlank() && !Objects.equals(offer.category(), category)) {
                continue;
            }
            if (query != null && !query.isBlank()) {
                String q = query.toLowerCase();
                if (!(offer.registryId().toLowerCase().contains(q) || offer.itemJson().toLowerCase().contains(q))) {
                    continue;
                }
            }
            offers.add(offer);
        }
        offers.sort(Comparator.comparing(ShopOffer::registryId));
        int from = Math.min(offset, offers.size());
        int to = Math.min(from + limit, offers.size());
        return offers.subList(from, to);
    }

    public synchronized boolean hasOffer(String shopId, String registryId, String category) {
        for (ShopOffer offer : data.shopOffers.values()) {
            if (!Objects.equals(offer.shopId(), shopId)) {
                continue;
            }
            if (!Objects.equals(offer.registryId(), registryId)) {
                continue;
            }
            if (category != null && !category.isBlank() && !Objects.equals(offer.category(), category)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public synchronized boolean updateStock(String offerId, int newStock, long expectedVersion) {
        ShopOffer offer = data.shopOffers.get(offerId);
        if (offer == null || offer.version() != expectedVersion) {
            return false;
        }
        ShopOffer updated = new ShopOffer(
                offer.offerId(),
                offer.shopId(),
                offer.registryId(),
                offer.itemHash(),
                offer.itemJson(),
                offer.count(),
                offer.price(),
                newStock,
                offer.infiniteStock(),
                offer.buyEnabled(),
                offer.sellEnabled(),
                offer.category(),
                offer.version() + 1
        );
        data.shopOffers.put(offerId, updated);
        save();
        return true;
    }

    public synchronized void createListing(AuctionListing listing) {
        data.auctionListings.put(listing.listingId(), listing);
        save();
    }

    public synchronized Optional<AuctionListing> getListing(String listingId) {
        return Optional.ofNullable(data.auctionListings.get(listingId));
    }

    public synchronized boolean updateListing(AuctionListing updated, long expectedVersion) {
        AuctionListing current = data.auctionListings.get(updated.listingId());
        if (current == null || current.version() != expectedVersion) {
            return false;
        }
        AuctionListing next = new AuctionListing(
                updated.listingId(),
                updated.sellerAccount(),
                updated.registryId(),
                updated.itemHash(),
                updated.itemJson(),
                updated.count(),
                updated.startingPrice(),
                updated.buyoutPrice(),
                updated.bidIncrement(),
                updated.createdAt(),
                updated.expiresAt(),
                updated.status(),
                updated.highestBidder(),
                updated.highestBid(),
                expectedVersion + 1
        );
        data.auctionListings.put(updated.listingId(), next);
        save();
        return true;
    }

    public synchronized void updateStatus(String listingId, AuctionStatus status, long expectedVersion) {
        AuctionListing current = data.auctionListings.get(listingId);
        if (current == null || current.version() != expectedVersion) {
            return;
        }
        AuctionListing updated = new AuctionListing(
                current.listingId(),
                current.sellerAccount(),
                current.registryId(),
                current.itemHash(),
                current.itemJson(),
                current.count(),
                current.startingPrice(),
                current.buyoutPrice(),
                current.bidIncrement(),
                current.createdAt(),
                current.expiresAt(),
                status,
                current.highestBidder(),
                current.highestBid(),
                current.version() + 1
        );
        data.auctionListings.put(listingId, updated);
        save();
    }

    public synchronized int countOpenListings(String sellerAccount) {
        int count = 0;
        for (AuctionListing listing : data.auctionListings.values()) {
            if (listing.status() == AuctionStatus.OPEN && Objects.equals(listing.sellerAccount(), sellerAccount)) {
                count++;
            }
        }
        return count;
    }

    public synchronized List<AuctionListing> listListings(AuctionStatus status, String query, int offset, int limit) {
        List<AuctionListing> listings = new ArrayList<>();
        for (AuctionListing listing : data.auctionListings.values()) {
            if (listing.status() != status) {
                continue;
            }
            if (query != null && !query.isBlank()) {
                String q = query.toLowerCase();
                if (!(listing.registryId().toLowerCase().contains(q) || listing.itemHash().toLowerCase().contains(q))) {
                    continue;
                }
            }
            listings.add(listing);
        }
        listings.sort(Comparator.comparingLong(AuctionListing::expiresAt));
        int from = Math.min(offset, listings.size());
        int to = Math.min(from + limit, listings.size());
        return listings.subList(from, to);
    }

    public synchronized List<AuctionListing> listExpired(long now, int limit) {
        List<AuctionListing> expired = new ArrayList<>();
        for (AuctionListing listing : data.auctionListings.values()) {
            if (listing.status() == AuctionStatus.OPEN && listing.expiresAt() <= now) {
                expired.add(listing);
            }
        }
        expired.sort(Comparator.comparingLong(AuctionListing::expiresAt));
        return expired.subList(0, Math.min(limit, expired.size()));
    }

    public synchronized void insertDelivery(Delivery delivery) {
        data.deliveries.put(delivery.deliveryId(), delivery);
        save();
    }

    public synchronized List<Delivery> listPendingDeliveries(String ownerAccount, int limit) {
        List<Delivery> deliveries = new ArrayList<>();
        for (Delivery delivery : data.deliveries.values()) {
            if (Objects.equals(delivery.ownerAccount(), ownerAccount) && delivery.status() == DeliveryStatus.PENDING) {
                deliveries.add(delivery);
            }
        }
        deliveries.sort(Comparator.comparingLong(Delivery::createdAt));
        return deliveries.subList(0, Math.min(limit, deliveries.size()));
    }

    public synchronized boolean markDeliveryClaimed(String deliveryId) {
        Delivery delivery = data.deliveries.get(deliveryId);
        if (delivery == null || delivery.status() != DeliveryStatus.PENDING) {
            return false;
        }
        Delivery updated = new Delivery(
                delivery.deliveryId(),
                delivery.ownerAccount(),
                delivery.type(),
                delivery.itemHash(),
                delivery.itemJson(),
                delivery.count(),
                delivery.currencyId(),
                delivery.amount(),
                DeliveryStatus.CLAIMED,
                delivery.createdAt(),
                System.currentTimeMillis()
        );
        data.deliveries.put(deliveryId, updated);
        save();
        return true;
    }

    public synchronized void updateDeliveryAttempt(String deliveryId) {
        Delivery delivery = data.deliveries.get(deliveryId);
        if (delivery == null) {
            return;
        }
        Delivery updated = new Delivery(
                delivery.deliveryId(),
                delivery.ownerAccount(),
                delivery.type(),
                delivery.itemHash(),
                delivery.itemJson(),
                delivery.count(),
                delivery.currencyId(),
                delivery.amount(),
                delivery.status(),
                delivery.createdAt(),
                System.currentTimeMillis()
        );
        data.deliveries.put(deliveryId, updated);
        save();
    }

    private static Data loadInternal(Path path) {
        if (!Files.exists(path)) {
            return new Data();
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            return loaded == null ? new Data() : loaded;
        } catch (IOException e) {
            return new Data();
        }
    }

    private static class Data {
        private Map<String, ShopOffer> shopOffers = new HashMap<>();
        private Map<String, AuctionListing> auctionListings = new HashMap<>();
        private Map<String, Delivery> deliveries = new HashMap<>();
    }
}
