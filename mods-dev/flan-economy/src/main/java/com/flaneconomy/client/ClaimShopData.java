package com.flaneconomy.client;

import com.flaneconomy.network.ClaimShopPayload;

import java.util.UUID;

public record ClaimShopData(
        long balance,
        int claimBlocks,
        boolean hasClaim,
        UUID claimId,
        String claimName,
        boolean forSale,
        long salePrice,
        String sellerName,
        boolean isOwner,
        String iconId
) {
    public static ClaimShopData fromPayload(ClaimShopPayload payload) {
        return new ClaimShopData(
                payload.balance(),
                payload.claimBlocks(),
                payload.hasClaim(),
                payload.claimId(),
                payload.claimName(),
                payload.forSale(),
                payload.salePrice(),
                payload.sellerName(),
                payload.isOwner(),
                payload.iconId()
        );
    }
}
