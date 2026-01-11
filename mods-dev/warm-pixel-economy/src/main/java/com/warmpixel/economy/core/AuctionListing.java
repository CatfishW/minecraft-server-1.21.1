package com.warmpixel.economy.core;

public record AuctionListing(
        String listingId,
        String sellerAccount,
        String registryId,
        String itemHash,
        String itemJson,
        int count,
        long startingPrice,
        Long buyoutPrice,
        long bidIncrement,
        long createdAt,
        long expiresAt,
        AuctionStatus status,
        String highestBidder,
        Long highestBid,
        long version
) {
}
