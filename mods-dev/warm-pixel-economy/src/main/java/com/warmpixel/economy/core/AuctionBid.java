package com.warmpixel.economy.core;

public record AuctionBid(
        String bidId,
        String listingId,
        String bidderAccount,
        long amount,
        long createdAt,
        AuctionBidStatus status
) {
}
