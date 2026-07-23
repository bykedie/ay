package com.qazr.legacy.util;

public final class CombatMath {
    private CombatMath() {
    }

    public static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
    }

    public static float pitch(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    public static double score(double distanceSq, float health, String priority) {
        return "health".equalsIgnoreCase(priority) ? health : distanceSq;
    }
}
