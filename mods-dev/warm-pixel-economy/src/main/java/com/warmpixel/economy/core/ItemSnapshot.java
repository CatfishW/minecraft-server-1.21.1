package com.warmpixel.economy.core;

import java.util.Objects;

public record ItemSnapshot(ItemKey key, String fullSnbt, int count) {
    public ItemSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(fullSnbt, "fullSnbt");
    }
}
