package com.qazr.legacy.config;

public enum HudPosition {
    TOP_LEFT("topLeft", "左上", false, false),
    BOTTOM_LEFT("bottomLeft", "左下", false, true),
    TOP_RIGHT("topRight", "右上", true, false),
    BOTTOM_RIGHT("bottomRight", "右下", true, true);

    private final String key;
    private final String displayName;
    private final boolean right;
    private final boolean bottom;

    HudPosition(String key, String displayName, boolean right, boolean bottom) {
        this.key = key;
        this.displayName = displayName;
        this.right = right;
        this.bottom = bottom;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public boolean right() {
        return right;
    }

    public boolean bottom() {
        return bottom;
    }

    public static HudPosition fromKey(String key) {
        for (HudPosition position : values()) {
            if (position.key.equalsIgnoreCase(key)) return position;
        }
        return TOP_LEFT;
    }

    public static HudPosition next(HudPosition current) {
        HudPosition[] values = values();
        return values[(current.ordinal() + 1) % values.length];
    }
}
