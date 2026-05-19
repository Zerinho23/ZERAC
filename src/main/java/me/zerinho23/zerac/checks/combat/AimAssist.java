package me.zerinho23.zerac.checks.combat;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.MathUtil;

/**
 * AimAssist detection check using GCD (Greatest Common Divisor) analysis.
 *
 * <p>Legitimate mouse input is constrained by the player's mouse sensitivity
 * setting. All rotation deltas are multiples of a minimum unit derived from
 * the sensitivity. This minimum unit can be computed via GCD.
 *
 * <p>Aim assist mods often produce rotation deltas that are too smooth
 * (very small GCD values, indicating sub-pixel precision) or that follow
 * mathematically perfect curves (zero variance in GCD).
 *
 * <p>This check is marked experimental in the default config because it
 * requires careful tuning — different sensitivity settings produce different
 * valid GCD ranges.
 */
public class AimAssist extends AbstractCheck {

    private final double minGcdVariance;
    private final boolean detectCinematic;

    // Running GCD tracker
    private long lastGcd   = 0L;
    private int  samples   = 0;
    private double gcdVarianceAccumulator = 0.0;

    public AimAssist(ZeracPlugin plugin) {
        super(plugin, "AimAssist");
        this.minGcdVariance  = config.getDouble("min-gcd-variance", 0.0001);
        this.detectCinematic = config.getInt("detect-cinematic", 1) == 1;
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;

        float yawDelta   = data.getYawDelta();
        float pitchDelta = data.getPitchDelta();

        // Skip if no rotation happened
        if (yawDelta == 0 && pitchDelta == 0) return;

        // Convert to long for GCD calculation
        long yawLong   = MathUtil.rotationToGCDLong(yawDelta);
        long pitchLong = MathUtil.rotationToGCDLong(pitchDelta);

        if (yawLong == 0 || pitchLong == 0) return;

        long gcd = MathUtil.gcd(Math.abs(yawLong), Math.abs(pitchLong));

        if (lastGcd != 0) {
            long gcdDiff = Math.abs(gcd - lastGcd);
            gcdVarianceAccumulator += gcdDiff;
            samples++;

            if (samples >= 20) {
                double avgVariance = gcdVarianceAccumulator / samples;

                // Too-perfect (low variance) = aim assist smoothing
                if (avgVariance < minGcdVariance) {
                    flag(data, "GCD variance: " + String.format("%.6f", avgVariance));
                } else {
                    decayVL(data);
                }

                // Reset accumulator periodically
                samples = 0;
                gcdVarianceAccumulator = 0.0;
            }
        }

        lastGcd = gcd;
    }

    @Override
    protected String getCategory() { return "combat"; }
}
