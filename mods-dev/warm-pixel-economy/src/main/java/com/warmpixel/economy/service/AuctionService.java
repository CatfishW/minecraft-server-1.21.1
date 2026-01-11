package com.warmpixel.economy.service;

import com.warmpixel.economy.core.AuctionListing;
import com.warmpixel.economy.core.AuctionStatus;
import com.warmpixel.economy.core.EconomyResult;
import com.warmpixel.economy.core.ItemSnapshot;
import com.warmpixel.economy.db.TransactionAbortException;
import com.warmpixel.economy.fabric.EconomyConfig;
import com.warmpixel.economy.fabric.InventoryAdapter;
import com.warmpixel.economy.fabric.ItemKeyFactory;
import com.warmpixel.economy.storage.EconomyStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class AuctionService {
    private final EconomyStorage storage;
    private final DeliveryService deliveryService;
    private final InventoryAdapter inventoryAdapter;
    private final NumismaticCurrencyService currencyService;
    private final MinecraftServer server;
    private final EconomyConfig config;
    private final ExecutorService executor;

    public AuctionService(EconomyStorage storage, DeliveryService deliveryService, InventoryAdapter inventoryAdapter,
                          NumismaticCurrencyService currencyService, MinecraftServer server, EconomyConfig config,
                          ExecutorService executor) {
        this.storage = storage;
        this.deliveryService = deliveryService;
        this.inventoryAdapter = inventoryAdapter;
        this.currencyService = currencyService;
        this.server = server;
        this.config = config;
        this.executor = executor;
    }

    public CompletableFuture<EconomyResult> createListing(ServerPlayer seller, ItemStack stack, int count, long startingPrice, Long buyoutPrice, long expiresAt, String currencyId) {
        if (count <= 0) {
            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.count_positive"));
        }
        if (startingPrice < config.auction.minStartPrice) {
            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.start_price_low"));
        }
        ItemStack listingStack = stack.copy();
        listingStack.setCount(count);
        ItemSnapshot snapshot = ItemKeyFactory.snapshot(listingStack, 0, seller.getServer().registryAccess());

        return inventoryAdapter.removeMatching(seller, snapshot.key(), count).thenCompose(removed -> {
            if (!removed) {
                return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.missing_items"));
            }
            long fee = Math.round(config.taxes.auctionListingFee);
            CompletableFuture<Boolean> feeWithdraw = fee > 0 ? currencyService.withdraw(seller, fee) : CompletableFuture.completedFuture(true);
            return feeWithdraw.thenCompose(withdrawn -> {
                if (!withdrawn) {
                    inventoryAdapter.insertStack(seller, listingStack);
                    return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.listing_fee_insufficient"));
                }
                return CompletableFuture.supplyAsync(() -> {
                    int openListings = storage.countOpenListings(seller.getUUID().toString());
                    if (openListings >= config.maxListingsPerPlayer) {
                        throw new TransactionAbortException("Listing limit reached.");
                    }
                    AuctionListing listing = new AuctionListing(
                            UUID.randomUUID().toString(),
                            seller.getUUID().toString(),
                            snapshot.key().registryId(),
                            snapshot.key().itemHash(),
                            snapshot.fullSnbt(),
                            count,
                            startingPrice,
                            buyoutPrice,
                            Math.max(config.auction.minBidIncrement, 1),
                            System.currentTimeMillis(),
                            expiresAt,
                            AuctionStatus.OPEN,
                            null,
                            null,
                            0
                    );
                    storage.createListing(listing);
                    return listing;
                }, executor).handle((listing, ex) -> {
                    if (ex != null || listing == null) {
                        if (fee > 0) {
                            currencyService.deposit(seller, fee);
                        }
                        inventoryAdapter.insertStack(seller, listingStack);
                        return EconomyResult.fail("message.warm_pixel_economy.listing_failed");
                    }
                    return EconomyResult.ok("message.warm_pixel_economy.listing_created");
                });
            });
        });
    }

    public CompletableFuture<EconomyResult> placeBid(ServerPlayer bidder, String listingId, long amount, String currencyId) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.bid_positive"));
        }
        return currencyService.withdraw(bidder, amount).thenCompose(withdrawn -> {
            if (!withdrawn) {
                return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.insufficient_funds"));
            }
            return CompletableFuture.supplyAsync(() -> {
                AuctionListing listing = storage.getListing(listingId).orElse(null);
                if (listing == null || listing.status() != AuctionStatus.OPEN) {
                    throw new TransactionAbortException("Listing not found or closed.");
                }
                long minBid = listing.highestBid() == null ? listing.startingPrice() : listing.highestBid() + listing.bidIncrement();
                if (amount < minBid) {
                    throw new TransactionAbortException("Bid too low.");
                }
                AuctionListing updated = new AuctionListing(
                        listing.listingId(),
                        listing.sellerAccount(),
                        listing.registryId(),
                        listing.itemHash(),
                        listing.itemJson(),
                        listing.count(),
                        listing.startingPrice(),
                        listing.buyoutPrice(),
                        listing.bidIncrement(),
                        listing.createdAt(),
                        listing.expiresAt(),
                        listing.status(),
                        bidder.getUUID().toString(),
                        amount,
                        listing.version()
                );
                boolean updatedListing = storage.updateListing(updated, listing.version());
                if (!updatedListing) {
                    throw new TransactionAbortException("Bid conflicted.");
                }
                return listing;
            }, executor).handle((original, ex) -> {
                if (ex != null || original == null) {
                    currencyService.deposit(bidder, amount);
                    return EconomyResult.fail("message.warm_pixel_economy.bid_failed");
                }
                if (original.highestBidder() != null && original.highestBid() != null) {
                    UUID previous = UUID.fromString(original.highestBidder());
                    ServerPlayer prevPlayer = server.getPlayerList().getPlayer(previous);
                    if (prevPlayer != null) {
                        currencyService.deposit(prevPlayer, original.highestBid());
                    } else {
                        deliveryService.createMoneyDelivery(previous, config.defaultCurrency, original.highestBid());
                    }
                }
                return EconomyResult.ok("message.warm_pixel_economy.bid_placed");
            });
        });
    }

    public CompletableFuture<EconomyResult> buyout(ServerPlayer buyer, String listingId, String currencyId) {
        return CompletableFuture.supplyAsync(() -> storage.getListing(listingId).orElse(null), executor)
                .thenCompose(listing -> {
                    if (listing == null || listing.status() != AuctionStatus.OPEN) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.listing_not_found_or_closed"));
                    }
                    if (listing.buyoutPrice() == null || listing.buyoutPrice() <= 0) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.buyout_not_available"));
                    }
                    return currencyService.withdraw(buyer, listing.buyoutPrice()).thenCompose(withdrawn -> {
                        if (!withdrawn) {
                            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.insufficient_funds"));
                        }
                        return CompletableFuture.supplyAsync(() -> {
                            AuctionListing fresh = storage.getListing(listingId).orElse(null);
                            if (fresh == null || fresh.status() != AuctionStatus.OPEN) {
                                throw new TransactionAbortException("Listing not found or closed.");
                            }
                            AuctionListing closed = new AuctionListing(
                                    fresh.listingId(),
                                    fresh.sellerAccount(),
                                    fresh.registryId(),
                                    fresh.itemHash(),
                                    fresh.itemJson(),
                                    fresh.count(),
                                    fresh.startingPrice(),
                                    fresh.buyoutPrice(),
                                    fresh.bidIncrement(),
                                    fresh.createdAt(),
                                    fresh.expiresAt(),
                                    AuctionStatus.CLOSED,
                                    buyer.getUUID().toString(),
                                    fresh.buyoutPrice(),
                                    fresh.version()
                            );
                            if (!storage.updateListing(closed, fresh.version())) {
                                throw new TransactionAbortException("Listing updated by someone else.");
                            }
                            return fresh;
                        }, executor).handle((fresh, ex) -> {
                            if (ex != null || fresh == null) {
                                currencyService.deposit(buyer, listing.buyoutPrice());
                                return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.buyout_failed"));
                            }

                            if (fresh.highestBidder() != null && fresh.highestBid() != null) {
                                UUID previous = UUID.fromString(fresh.highestBidder());
                                ServerPlayer prevPlayer = server.getPlayerList().getPlayer(previous);
                                if (prevPlayer != null) {
                                    currencyService.deposit(prevPlayer, fresh.highestBid());
                                } else {
                                    deliveryService.createMoneyDelivery(previous, config.defaultCurrency, fresh.highestBid());
                                }
                            }

                            long fee = Math.round(fresh.buyoutPrice() * config.taxes.auctionFinalFeeRate);
                            long payout = fresh.buyoutPrice() - fee;
                            UUID sellerId = UUID.fromString(fresh.sellerAccount());
                            ServerPlayer seller = server.getPlayerList().getPlayer(sellerId);
                            if (seller != null) {
                                currencyService.deposit(seller, payout);
                            } else {
                                deliveryService.createMoneyDelivery(sellerId, config.defaultCurrency, payout);
                            }

                            ItemStack stack = ItemKeyFactory.stackFromSnbt(fresh.itemJson(), fresh.count(), buyer.getServer().registryAccess());
                            return inventoryAdapter.insertStack(buyer, stack).thenCompose(success -> {
                                if (success) {
                                    return CompletableFuture.completedFuture(EconomyResult.ok("message.warm_pixel_economy.buyout_complete"));
                                }
                                return deliveryService.createItemDelivery(buyer.getUUID(), fresh.itemHash(), fresh.itemJson(), fresh.count())
                                        .thenApply(delivery -> EconomyResult.ok("message.warm_pixel_economy.items_sent_delivery"));
                            });
                        }).thenCompose(result -> result);
                    });
                });
    }

    public CompletableFuture<EconomyResult> cancelListing(ServerPlayer seller, String listingId) {
        return CompletableFuture.supplyAsync(() -> {
            AuctionListing listing = storage.getListing(listingId).orElse(null);
            if (listing == null || listing.status() != AuctionStatus.OPEN) {
                throw new TransactionAbortException("Listing not found or closed.");
            }
            if (!listing.sellerAccount().equals(seller.getUUID().toString())) {
                throw new TransactionAbortException("Not your listing.");
            }
            if (listing.highestBid() != null) {
                throw new TransactionAbortException("Listing has bids.");
            }
            storage.updateStatus(listing.listingId(), AuctionStatus.CANCELLED, listing.version());
            return listing;
        }, executor).handle((listing, ex) -> {
            if (ex != null || listing == null) {
                return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.cancel_failed"));
            }
            return deliveryService.createItemDelivery(seller.getUUID(), listing.itemHash(), listing.itemJson(), listing.count())
                    .thenApply(delivery -> EconomyResult.ok("message.warm_pixel_economy.item_returned_delivery"));
        }).thenCompose(result -> result);
    }

    public CompletableFuture<List<AuctionListing>> listOpenListings(String query, int page, int pageSize) {
        int offset = Math.max(0, page) * pageSize;
        return CompletableFuture.supplyAsync(() -> storage.listListings(AuctionStatus.OPEN, query, offset, pageSize), executor);
    }

    public CompletableFuture<Void> processExpirations(String currencyId) {
        return CompletableFuture.supplyAsync(() -> storage.listExpired(System.currentTimeMillis(), 50), executor)
                .thenCompose(listings -> {
                    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                    for (AuctionListing listing : listings) {
                        chain = chain.thenCompose(ignored -> expireListing(listing));
                    }
                    return chain;
                });
    }

    private CompletableFuture<Void> expireListing(AuctionListing listing) {
        return CompletableFuture.supplyAsync(() -> {
            AuctionListing latest = storage.getListing(listing.listingId()).orElse(null);
            if (latest == null || latest.status() != AuctionStatus.OPEN) {
                return null;
            }
            if (latest.highestBidder() != null && latest.highestBid() != null) {
                storage.updateStatus(latest.listingId(), AuctionStatus.CLOSED, latest.version());
                return new ExpireResult(latest, true);
            }
            storage.updateStatus(latest.listingId(), AuctionStatus.EXPIRED, latest.version());
            return new ExpireResult(latest, false);
        }, executor).thenCompose(result -> {
            if (result == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (result.hasWinner) {
                UUID winnerId = UUID.fromString(result.listing.highestBidder());
                ServerPlayer winner = server.getPlayerList().getPlayer(winnerId);
                if (winner != null) {
                    ItemStack stack = ItemKeyFactory.stackFromSnbt(result.listing.itemJson(), result.listing.count(), winner.getServer().registryAccess());
                    inventoryAdapter.insertStack(winner, stack);
                } else {
                    deliveryService.createItemDelivery(winnerId, result.listing.itemHash(), result.listing.itemJson(), result.listing.count());
                }

                long fee = Math.round(result.listing.highestBid() * config.taxes.auctionFinalFeeRate);
                long payout = result.listing.highestBid() - fee;
                UUID sellerId = UUID.fromString(result.listing.sellerAccount());
                ServerPlayer seller = server.getPlayerList().getPlayer(sellerId);
                if (seller != null) {
                    currencyService.deposit(seller, payout);
                } else {
                    deliveryService.createMoneyDelivery(sellerId, config.defaultCurrency, payout);
                }
                return CompletableFuture.completedFuture(null);
            }
            UUID sellerId = UUID.fromString(result.listing.sellerAccount());
            ServerPlayer seller = server.getPlayerList().getPlayer(sellerId);
            if (seller != null) {
                ItemStack stack = ItemKeyFactory.stackFromSnbt(result.listing.itemJson(), result.listing.count(), seller.getServer().registryAccess());
                inventoryAdapter.insertStack(seller, stack);
            } else {
                deliveryService.createItemDelivery(sellerId, result.listing.itemHash(), result.listing.itemJson(), result.listing.count());
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    private record ExpireResult(AuctionListing listing, boolean hasWinner) {
    }
}
