package com.warmpixel.economy.core;

public record ShopOffer(
        String offerId,
        String shopId,
        String registryId,
        String itemHash,
        String itemJson,
        int count,
        long price,
        int stock,
        boolean infiniteStock,
        boolean buyEnabled,
        boolean sellEnabled,
        String category,
        long version
) {
}
