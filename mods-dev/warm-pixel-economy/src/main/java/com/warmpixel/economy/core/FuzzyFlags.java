package com.warmpixel.economy.core;

public final class FuzzyFlags {
    public static final int NONE = 0;
    public static final int IGNORE_DAMAGE = 1 << 0;
    public static final int IGNORE_CUSTOM_NAME = 1 << 1;
    public static final int IGNORE_LORE = 1 << 2;
    public static final int IGNORE_ENCHANTS = 1 << 3;
    public static final int IGNORE_COMPONENTS = 1 << 4;

    private FuzzyFlags() {
    }
}
