package me.zerinho23.zerac.utils;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Math utilities for anticheat checks.
 *
 * <p>All methods are static and thread-safe. No Bukkit API calls that
 * require the main thread are made here.
 */
public final class MathUtil {

    private MathUtil() {}

    // ── Distance & Reach ──────────────────────────────────────────────────────

    /**
     * Returns the 3D distance between two locations without squaring,
     * accounting for eye height of the attacker.
     *
     * @param attacker attacker location
     * @param target   target location
     * @return precise 3D distance in blocks
     */
    public static double getReach(Location attacker, Location target) {
        double eyeX = attacker.getX();
        double eyeY = attacker.getY() + 1.62; // standard eye height
        double eyeZ = attacker.getZ();

        double dx = eyeX - target.getX();
        double dy = eyeY - target.getY();
        double dz = eyeZ - target.getZ();

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Returns the reach from an entity's eye to another entity's hitbox center.
     *
     * @param attacker the attacking entity
     * @param target   the target entity
     * @return distance in blocks
     */
    public static double getReach(Entity attacker, Entity target) {
        return getReach(attacker.getEyeLocation(), target.getLocation());
    }

    // ── Angle helpers ─────────────────────────────────────────────────────────

    /**
     * Calculates the absolute yaw difference between two angles (0–180°).
     *
     * @param yaw1 first yaw angle
     * @param yaw2 second yaw angle
     * @return absolute angular difference
     */
    public static float getYawDifference(float yaw1, float yaw2) {
        float diff = Math.abs(yaw1 - yaw2) % 360;
        return diff > 180 ? 360 - diff : diff;
    }

    /**
     * Returns the angle (in degrees) between the attacker's look direction
     * and the direction to the target.
     *
     * @param attacker attacker location (with yaw/pitch)
     * @param target   target location
     * @return angle in degrees [0, 180]
     */
    public static double getAngleDifference(Location attacker, Location target) {
        Vector attackDir = attacker.getDirection().normalize();
        Vector toTarget  = target.toVector().subtract(attacker.toVector()).normalize();
        double dot       = attackDir.dot(toTarget);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    // ── GCD (Greatest Common Divisor) — for aim assist detection ─────────────

    /**
     * Computes the GCD of two long values using the Euclidean algorithm.
     * Used for detecting mouse sensitivity patterns in aim assist detection.
     *
     * @param a first value
     * @param b second value
     * @return GCD of a and b
     */
    public static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    /**
     * Converts a float rotation delta to a long for GCD calculation,
     * using the same bit-manipulation approach as legitimate clients.
     *
     * @param delta rotation delta (degrees)
     * @return long representation suitable for GCD
     */
    public static long rotationToGCDLong(float delta) {
        return (long) (delta * 1e6);
    }

    // ── Velocity ──────────────────────────────────────────────────────────────

    /**
     * Returns the horizontal speed magnitude from a velocity vector.
     *
     * @param vel the velocity vector
     * @return horizontal speed (XZ only)
     */
    public static double getHorizontalSpeed(Vector vel) {
        return Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
    }

    /**
     * Returns the horizontal distance between two locations.
     *
     * @param a first location
     * @param b second location
     * @return XZ-only distance in blocks
     */
    public static double getHorizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ── General math ──────────────────────────────────────────────────────────

    /**
     * Clamps a value between min and max.
     *
     * @param value the value to clamp
     * @param min   minimum allowed value
     * @param max   maximum allowed value
     * @return clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Linearly interpolates between two values.
     *
     * @param a start value
     * @param b end value
     * @param t interpolation factor [0, 1]
     * @return interpolated value
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp(t, 0, 1);
    }

    /**
     * Returns a variance measure of a set of values.
     * Used in AutoClicker detection to check for suspiciously consistent clicking.
     *
     * @param values array of values
     * @return variance (low = very consistent = suspicious)
     */
    public static double variance(double[] values) {
        if (values.length == 0) return 0;
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;
        double var = 0;
        for (double v : values) var += (v - mean) * (v - mean);
        return var / values.length;
    }
}
