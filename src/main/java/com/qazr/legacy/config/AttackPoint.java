package com.qazr.legacy.config;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;

public enum AttackPoint {
    HEAD("head", "头部", 0.90),
    CHEST("chest", "胸口", 0.62),
    LEGS("legs", "腿部", 0.28),
    FEET("feet", "脚部", 0.08);

    private final String key;
    private final String displayName;
    private final double heightRatio;

    AttackPoint(String key, String displayName, double heightRatio) {
        this.key = key;
        this.displayName = displayName;
        this.heightRatio = heightRatio;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public Vec3d point(EntityLivingBase target) {
        double y = target.getEntityBoundingBox().minY + Math.max(0.05, target.height * heightRatio);
        return new Vec3d(target.posX, y, target.posZ);
    }

    public static AttackPoint fromKey(String key) {
        for (AttackPoint point : values()) {
            if (point.key.equalsIgnoreCase(key)) return point;
        }
        return CHEST;
    }

    public static AttackPoint next(AttackPoint current) {
        AttackPoint[] values = values();
        return values[(current.ordinal() + 1) % values.length];
    }
}
