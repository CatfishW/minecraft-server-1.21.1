package com.warmpixel.economy.core;

public record AccountBalance(String accountId, String currencyId, long balance, long reserved, long version) {
}
