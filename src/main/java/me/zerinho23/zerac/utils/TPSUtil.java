package me.zerinho23.zerac.utils;

import me.zerinho23.zerac.ZeracPlugin;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;

/**
 * Utility for reading the server TPS (Ticks Per Second).
 *
 * <p>Uses the Bukkit API on Paper 1.15+. Falls back to NMS reflection
 * on older builds / vanilla Spigot.
 *
 * <p>All methods are static and thread-safe.
 */
public final class TPSUtil {

    private TPSUtil() {}

    /**
     * Returns the 1-minute average TPS.
     * Clamped to [0, 20] to avoid UI weirdness on overclocked servers.
     *
     * @return TPS value in [0, 20]
     */
    public static double getTPS() {
        try {
            // Paper API (1.15+)
            double[] tps = Bukkit.getServer().getTPS();
            return Math.min(20.0, tps[0]);
        } catch (NoSuchMethodError e) {
            return getTpsViaReflection();
        }
    }

    /**
     * Returns all three TPS samples: [1m, 5m, 15m].
     *
     * @return double array of length 3
     */
    public static double[] getAllTPS() {
        try {
            return Bukkit.getServer().getTPS();
        } catch (NoSuchMethodError e) {
            double tps = getTpsViaReflection();
            return new double[]{tps, tps, tps};
        }
    }

    /**
     * Returns a formatted TPS string, colored by health:
     * Green (>18), Yellow (15-18), Red (<15).
     *
     * @return MiniMessage-formatted TPS string
     */
    public static String getFormattedTPS() {
        double tps = getTPS();
        String color;
        if (tps > 18.0)      color = "<green>";
        else if (tps > 15.0) color = "<yellow>";
        else                 color = "<red>";
        return color + String.format("%.1f", tps) + "<reset>";
    }

    // ── NMS fallback ──────────────────────────────────────────────────────────

    private static double getTpsViaReflection() {
        try {
            // MinecraftServer.recentTps field (NMS)
            Object minecraftServer = Bukkit.getServer().getClass()
                    .getMethod("getServer")
                    .invoke(Bukkit.getServer());
            Field recentTps = minecraftServer.getClass()
                    .getSuperclass()
                    .getDeclaredField("recentTps");
            recentTps.setAccessible(true);
            double[] tps = (double[]) recentTps.get(minecraftServer);
            return Math.min(20.0, tps[0]);
        } catch (Exception e) {
            return 20.0; // assume good TPS if detection fails
        }
    }

    /**
     * Returns the current server lag in milliseconds per tick.
     *
     * @return ms per tick (50ms = perfect)
     */
    public static double getMsPerTick() {
        double tps = getTPS();
        if (tps <= 0) return 0;
        return 1000.0 / tps;
    }
}
