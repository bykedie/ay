package com.qazr.legacy.config;

public enum ModuleId {
    AUTO_GG("autoGg", "自动发送 GG"),
    AUTO_REPLY("autoReply", "自动回复"),
    AUTO_MINE("autoMine", "自动挖矿"),
    ORE_VISUALIZER("oreVisualizer", "矿物可视化"),
    CREATIVE_TOOLS("creativeTools", "创造工具"),
    MELEE_AURA("meleeAura", "自动近战"),
    BLINK_STRIKE("blinkStrike", "闪现攻击"),
    CRITICALS("criticals", "自动暴击"),
    TARGET_VISUALIZER("targetVisualizer", "目标可视化");

    private final String key;
    private final String displayName;

    ModuleId(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String translationKey() {
        return "module.qazr." + key;
    }

    public String displayName() {
        return displayName;
    }

    public static ModuleId parse(String raw) {
        String normalized = raw.trim().replace('-', '_').toUpperCase();
        for (ModuleId id : values()) {
            if (id.name().equals(normalized) || id.key.toUpperCase().equals(normalized)) return id;
        }
        throw new IllegalArgumentException("Unknown module: " + raw);
    }
}
