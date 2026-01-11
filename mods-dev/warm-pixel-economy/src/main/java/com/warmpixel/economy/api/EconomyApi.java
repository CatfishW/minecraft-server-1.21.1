package com.warmpixel.economy.api;

import com.warmpixel.economy.core.EconomyResult;
import com.warmpixel.economy.core.ItemKey;
import com.warmpixel.economy.core.PriceQuote;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EconomyApi {
    CompletableFuture<Long> getBalance(UUID playerId, String currencyId);

    CompletableFuture<EconomyResult> credit(UUID playerId, String currencyId, long amount, String reason);

    CompletableFuture<EconomyResult> debit(UUID playerId, String currencyId, long amount, String reason);

    CompletableFuture<PriceQuote> priceCheck(ItemKey key, int count, String shopId);

    CompletableFuture<EconomyResult> createListing(UUID sellerId, ItemKey key, int count, long startingPrice, Long buyoutPrice, long expiresAt);

    CompletableFuture<EconomyResult> placeBid(UUID bidderId, String listingId, long amount);

    CompletableFuture<EconomyResult> buyout(UUID buyerId, String listingId);
}
