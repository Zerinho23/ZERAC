package me.zerinho23.zerac.checks.movement;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.MathUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Velocity (Anti-Knockback) detection check.
 *
 * <p>Detects players who ignore or reduce server-sent velocity packets
 * (knockback from attacks, explosions, etc.).
 *
 * <p>Method:
 * <ol>
 *   <li>When the server sends an EntityVelocity packet to the player,
 *       the expected velocity is stored in {@link PlayerData}.
 *   <li>On the next position packet (within a grace window), the actual
 *       horizontal displacement is compared to the expected velocity.
 *   <li>If the player moved less than {@code min-velocity-acceptance}
 *       percent of the expected knockback, they are flagged.
 * </ol>
 *
 * <p>Grace window: 5 ticks after the velocity packet. This absorbs
 * normal ping variation (200ms at 20 TPS ≈ 4 ticks).
 */
public class Velocity extends AbstractCheck {

    private final double minAcceptance;

    /** Grace window in ticks between velocity packet and position check. */
    private static final int GRACE_TICKS = 5;

    public Velocity(ZeracPlugin plugin) {
        super(plugin, "Velocity");
        this.minAcceptance = config.getDouble("min-velocity-acceptance", 0.85);
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        // No pending velocity to check
        if (!data.isVelocityExpected()) return;

        long ticksSinceVelocity = data.getServerTick() - data.getVelocityExpectedTick();

        // Too early — wait for the client to respond
        if (ticksSinceVelocity < 1) return;

        // Too late — velocity opportunity has passed, reset
        if (ticksSinceVelocity > GRACE_TICKS) {
            data.setVelocityExpected(false);
            return;
        }

        // Compare expected vs. actual horizontal displacement
        double expectedH = MathUtil.getHorizontalSpeed(new Vector(
                data.getLastExpectedVelocityX(),
                data.getLastExpectedVelocityY(),
                data.getLastExpectedVelocityZ()
        ));

        if (expectedH < 0.05) {
            // Very small velocity — not worth checking (reduces false positives)
            data.setVelocityExpected(false);
            return;
        }

        if (data.getLastLocation() == null) return;

        double actualH = MathUtil.getHorizontalDistance(
                player.getLocation(), data.getLastLocation());

        double acceptedRatio = expectedH > 0 ? actualH / expectedH : 1.0;

        if (acceptedRatio < minAcceptance) {
            flag(data, String.format("Accepted %.0f%% of knockback (min %.0f%%)",
                    acceptedRatio * 100, minAcceptance * 100));
        } else {
            decayVL(data);
        }

        data.setVelocityExpected(false);
    }

    @Override
    protected String getCategory() { return "movement"; }
}
