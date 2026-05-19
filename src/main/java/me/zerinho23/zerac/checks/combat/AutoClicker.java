package me.zerinho23.zerac.checks.combat;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;

/**
 * AutoClicker detection check.
 *
 * <p>Detects automated clicking by analyzing:
 * <ul>
 *   <li><b>CPS (Clicks Per Second)</b> — sustained CPS above the configured
 *       {@code max-cps} threshold is flagged. Human CPS is generally capped by
 *       physical ability (≈ 14–16 CPS for jitter clicking, ≈ 6–8 for regular).
 *   <li><b>Consistency</b> — AutoClicker software produces clicks with very
 *       consistent intervals. A high consistency ratio (approaching 1.0) with
 *       high CPS strongly indicates a macro or AutoClicker.
 * </ul>
 *
 * <p>False positive mitigations:
 * <ul>
 *   <li>Bedrock players are exempt (touch input can produce high CPS)
 *   <li>Requires minimum click samples before evaluating
 *   <li>Configurable CPS ceiling and consistency threshold
 * </ul>
 */
public class AutoClicker extends AbstractCheck {

    private final int    maxCps;
    private final double consistencyThreshold;

    /** Minimum number of click samples before running consistency analysis. */
    private static final int MIN_SAMPLES = 10;

    public AutoClicker(ZeracPlugin plugin) {
        super(plugin, "AutoClicker");
        this.maxCps               = config.getInt("max-cps", 20);
        this.consistencyThreshold = config.getDouble("consistency-threshold", 0.95);
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;
        if (data.isBedrockPlayer()) return; // exempt Bedrock players

        double cps = data.getCPS();

        // Not enough data yet
        if (data.getClickTimestamps().size() < MIN_SAMPLES) return;

        // ── CPS limit check ───────────────────────────────────────────────────
        if (cps > maxCps) {
            flag(data, String.format("CPS: %.1f (max %d)", cps, maxCps));
            return;
        }

        // ── Consistency check (macro detection) ───────────────────────────────
        double consistency = calculateConsistency(data);
        if (consistency > consistencyThreshold && cps > 8) {
            flag(data, String.format("CPS: %.1f, Consistency: %.3f", cps, consistency));
            return;
        }

        decayVL(data);
    }

    /**
     * Calculates a consistency ratio [0.0, 1.0] of click intervals.
     * 1.0 = perfectly uniform (macro), lower = more human variation.
     *
     * @param data player data
     * @return consistency ratio
     */
    private double calculateConsistency(PlayerData data) {
        Long[] timestamps = data.getClickTimestamps().toArray(new Long[0]);
        if (timestamps.length < 3) return 0.0;

        long[] intervals = new long[timestamps.length - 1];
        for (int i = 1; i < timestamps.length; i++) {
            intervals[i - 1] = timestamps[i] - timestamps[i - 1];
        }

        // Calculate mean interval
        long sum = 0;
        for (long v : intervals) sum += v;
        double mean = (double) sum / intervals.length;

        if (mean == 0) return 1.0;

        // Calculate coefficient of variation (stddev / mean)
        double variance = 0;
        for (long v : intervals) {
            double diff = v - mean;
            variance += diff * diff;
        }
        variance /= intervals.length;
        double stddev = Math.sqrt(variance);
        double cv = stddev / mean;

        // Low CV = high consistency; invert for a "consistency score"
        return Math.max(0, 1.0 - cv);
    }

    @Override
    protected String getCategory() { return "combat"; }
}
