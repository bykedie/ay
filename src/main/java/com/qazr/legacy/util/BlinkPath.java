package com.qazr.legacy.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlinkPath {
    private BlinkPath() {
    }

    public static List<Point> interpolate(Point from, Point to, double maxStep) {
        if (from == null || to == null) throw new IllegalArgumentException("Path endpoints are required");
        if (!Double.isFinite(maxStep) || maxStep <= 0.0) throw new IllegalArgumentException("maxStep must be positive");
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance == 0.0) return Collections.emptyList();

        int steps = (int) Math.ceil(distance / maxStep);
        List<Point> result = new ArrayList<>(steps);
        for (int i = 1; i <= steps; i++) {
            double progress = (double) i / steps;
            result.add(new Point(
                from.x + dx * progress,
                from.y + dy * progress,
                from.z + dz * progress
            ));
        }
        return result;
    }

    public static List<Point> roundTrip(Point origin, List<Point> outward) {
        if (origin == null || outward == null) throw new IllegalArgumentException("Round-trip path is required");
        if (outward.isEmpty()) return Collections.emptyList();
        List<Point> result = new ArrayList<>(outward.size() * 2);
        result.addAll(outward);
        result.addAll(returnPath(origin, outward, outward.size()));
        return result;
    }

    public static List<Point> returnPath(Point origin, List<Point> outward, int sentPoints) {
        if (origin == null || outward == null) throw new IllegalArgumentException("Return path is required");
        if (sentPoints < 0 || sentPoints > outward.size()) throw new IllegalArgumentException("Invalid sent point count");
        if (sentPoints == 0) return Collections.emptyList();
        List<Point> result = new ArrayList<>(sentPoints);
        for (int i = sentPoints - 2; i >= 0; i--) result.add(outward.get(i));
        result.add(origin);
        return result;
    }

    public static Point approach(Point origin, Point target, double remainingDistance) {
        if (origin == null || target == null) throw new IllegalArgumentException("Approach endpoints are required");
        if (!Double.isFinite(remainingDistance) || remainingDistance <= 0.0) {
            throw new IllegalArgumentException("remainingDistance must be positive");
        }
        double distance = origin.distanceTo(target);
        if (distance <= remainingDistance) return origin;
        double progress = (distance - remainingDistance) / distance;
        return new Point(
            origin.x + (target.x - origin.x) * progress,
            origin.y + (target.y - origin.y) * progress,
            origin.z + (target.z - origin.z) * progress
        );
    }

    public static Point limitDistance(Point origin, Point target, double maxDistance) {
        if (origin == null || target == null) throw new IllegalArgumentException("Limit endpoints are required");
        if (!Double.isFinite(maxDistance) || maxDistance <= 0.0) {
            throw new IllegalArgumentException("maxDistance must be positive");
        }
        double distance = origin.distanceTo(target);
        if (distance <= maxDistance) return target;
        double progress = maxDistance / distance;
        return new Point(
            origin.x + (target.x - origin.x) * progress,
            origin.y + (target.y - origin.y) * progress,
            origin.z + (target.z - origin.z) * progress
        );
    }

    public static final class Point {
        public final double x;
        public final double y;
        public final double z;

        public Point(double x, double y, double z) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Point coordinates must be finite");
            }
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double distanceTo(Point other) {
            double dx = other.x - x;
            double dy = other.y - y;
            double dz = other.z - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }
}
