package com.qazr.legacy.config;

public enum ModuleId {
    AUTO_GG("autoGg"),
    AUTO_REPLY("autoReply"),
    AUTO_MINE("autoMine"),
    CREATIVE_TOOLS("creativeTools"),
    MELEE_AURA("meleeAura"),
    BLINK_STRIKE("blinkStrike"),
    CRITICALS("criticals");

    private final String key;

    ModuleId(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ModuleId parse(String raw) {
        String normalized = raw.trim().replace('-', '_').toUpperCase();
        for (ModuleId id : values()) {
            if (id.name().equals(normalized) || id.key.toUpperCase().equals(normalized)) return id;
        }
        throw new IllegalArgumentException("Unknown module: " + raw);
    }
}
