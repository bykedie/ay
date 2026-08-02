package com.qazr.legacy.config;

public enum FlightMode {
    STATIC("static", "静态"),
    VANILLA("vanilla", "原版");

    private final String key;
    private final String displayName;

    FlightMode(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static FlightMode fromKey(String key) {
        for (FlightMode mode : values()) {
            if (mode.key.equalsIgnoreCase(key)) return mode;
        }
        return STATIC;
    }

    public static FlightMode next(FlightMode current) {
        FlightMode[] modes = values();
        return modes[(current.ordinal() + 1) % modes.length];
    }
}
