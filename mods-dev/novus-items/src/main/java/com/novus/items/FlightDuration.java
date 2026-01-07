package com.novus.items;

/**
 * Enum representing different flight durations for Aether Scrolls.
 */
public enum FlightDuration {
    ONE_HOUR("1 Hour", 60 * 60),              // 3600 seconds
    TWELVE_HOURS("12 Hours", 12 * 60 * 60),   // 43200 seconds
    ONE_DAY("24 Hours", 24 * 60 * 60),        // 86400 seconds
    THREE_DAYS("3 Days", 3 * 24 * 60 * 60),   // 259200 seconds
    SEVEN_DAYS("7 Days", 7 * 24 * 60 * 60),   // 604800 seconds
    PERMANENT("Permanent", -1);                // -1 indicates permanent

    private final String displayName;
    private final int durationSeconds;

    FlightDuration(String displayName, int durationSeconds) {
        this.displayName = displayName;
        this.durationSeconds = durationSeconds;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public boolean isPermanent() {
        return this == PERMANENT;
    }
}
