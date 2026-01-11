package com.warmpixel.economy.core;

public record Delivery(
        String deliveryId,
        String ownerAccount,
        DeliveryType type,
        String itemHash,
        String itemJson,
        int count,
        String currencyId,
        long amount,
        DeliveryStatus status,
        long createdAt,
        long lastAttempt
) {
}
