package com.warmpixel.economy.fabric;

public enum TradeMode {
    BUY,
    SELL;

    public static TradeMode fromOrdinal(int value) {
        if (value <= 0) {
            return BUY;
        }
        return SELL;
    }
}
