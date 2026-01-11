package com.warmpixel.economy.service;

import com.warmpixel.economy.api.EconomyApi;
import com.warmpixel.economy.core.EconomyResult;
import com.warmpixel.economy.core.ItemKey;
import com.warmpixel.economy.core.PriceQuote;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyApiImpl implements EconomyApi {
    private final BalanceService balanceService;
    private final ShopService shopService;
    private final AuctionService auctionService;
    private final String defaultCurrency;

    public EconomyApiImpl(BalanceService balanceService, ShopService shopService, AuctionService auctionService, String defaultCurrency) {
        this.balanceService = balanceService;
        this.shopService = shopService;
        this.auctionService = auctionService;
        this.defaultCurrency = defaultCurrency;
    }

    @Override
    public CompletableFuture<Long> getBalance(UUID playerId, String currencyId) {
        return balanceService.getBalance(playerId, currencyId == null ? defaultCurrency : currencyId);
    }

    @Override
    public CompletableFuture<EconomyResult> credit(UUID playerId, String currencyId, long amount, String reason) {
        return balanceService.credit(playerId, currencyId == null ? defaultCurrency : currencyId, amount, reason);
    }

    @Override
    public CompletableFuture<EconomyResult> debit(UUID playerId, String currencyId, long amount, String reason) {
        return balanceService.debit(playerId, currencyId == null ? defaultCurrency : currencyId, amount, reason);
    }

    @Override
    public CompletableFuture<PriceQuote> priceCheck(ItemKey key, int count, String shopId) {
        return shopService.priceCheck(key, count, shopId);
    }

    @Override
    public CompletableFuture<EconomyResult> createListing(UUID sellerId, ItemKey key, int count, long startingPrice, Long buyoutPrice, long expiresAt) {
        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.listing_requires_player"));
    }

    @Override
    public CompletableFuture<EconomyResult> placeBid(UUID bidderId, String listingId, long amount) {
        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.bid_requires_player"));
    }

    @Override
    public CompletableFuture<EconomyResult> buyout(UUID buyerId, String listingId) {
        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.buyout_requires_player"));
    }
}
