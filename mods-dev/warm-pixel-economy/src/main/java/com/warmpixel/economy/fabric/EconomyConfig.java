package com.warmpixel.economy.fabric;

public class EconomyConfig {
    public String defaultCurrency = "coins";
    public boolean enableGui = true;
    public boolean enablePlayerShops = false;
    public boolean enableAuctionHouse = true;
    public int deliveryRetrySeconds = 30;
    public int auctionExpirationCheckSeconds = 15;
    public int maxListingsPerPlayer = 20;

    public TaxesConfig taxes = new TaxesConfig();
    public ShopConfig shop = new ShopConfig();
    public AuctionConfig auction = new AuctionConfig();

    public static class TaxesConfig {
        public double shopTaxRate = 0.0;
        public double auctionListingFee = 0.0;
        public double auctionFinalFeeRate = 0.0;
    }

    public static class ShopConfig {
        public String adminShopId = "admin";
        public boolean adminShopInfiniteStock = true;
        public int pageSize = 45;
        public long importDefaultPrice = 10;
        public int importDefaultStock = 0;
        public int importDefaultCount = 1;
        public boolean importBuyEnabled = true;
        public boolean importSellEnabled = true;
    }

    public static class AuctionConfig {
        public long defaultDurationSeconds = 86400;
        public long minBidIncrement = 1;
        public long minStartPrice = 1;
    }
}
