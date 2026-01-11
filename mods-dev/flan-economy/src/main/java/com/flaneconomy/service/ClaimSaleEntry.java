package com.flaneconomy.service;

import java.util.UUID;

public record ClaimSaleEntry(UUID claimId, UUID sellerId, long price, String iconId) {
    public ClaimSaleEntry(UUID claimId, UUID sellerId, long price) {
        this(claimId, sellerId, price, "minecraft:grass_block");
    }
}
