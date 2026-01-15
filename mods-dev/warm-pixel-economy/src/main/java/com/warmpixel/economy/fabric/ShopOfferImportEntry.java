package com.warmpixel.economy.fabric;

public record ShopOfferImportEntry(
        String registryId,
        int count,
        long price,
        int stock,
        boolean buyEnabled,
        boolean sellEnabled,
        String category,
        String itemSnbt
) {
    public ShopOfferImportEntry(String registryId, int count, long price, int stock, boolean buyEnabled, boolean sellEnabled, String category) {
        this(registryId, count, price, stock, buyEnabled, sellEnabled, category, null);
    }
}
