package com.warmpixel.economy.core;

import java.util.Objects;

public record ItemKey(String registryId, String componentsSnbt, String itemHash, int fuzzyFlags) {
    public ItemKey {
        Objects.requireNonNull(registryId, "registryId");
        Objects.requireNonNull(componentsSnbt, "componentsSnbt");
        Objects.requireNonNull(itemHash, "itemHash");
    }
}
