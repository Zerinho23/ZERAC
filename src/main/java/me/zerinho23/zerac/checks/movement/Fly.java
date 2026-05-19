package me.zerinho23.zerac.checks.movement;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Fly detection check.
 *
 * <p>Detects players floating in the air longer than physics allows.
 *
 * <p>Detection approach:
 * <ul>
 *   <li>Counts consecutive ticks the player is airborne
 *   <li>Flags when airborne ticks exceed {@code airtime-threshold}
 *   <li>Also checks vertical velocity against expected gravity
 * </ul>
 *
 * <p>Exemptions:
 * <ul>
 *   <li>Creative / Spectator mode
 *   <li>Players with fly permission on the server
 *   <li>Players in water or lava (fluid reduces fall)
 *   <li>Jump boost potion effects
 *   <li>Elytra gliding
 *   <li>Players near climbable blocks (ladders, vines)
 * </ul>
 */
public class Fly extends AbstractCheck {

    private final int    airtimeThreshold;
    private final double gravityTolerance;

    public Fly(ZeracPlugin plugin) {
        super(plugin, "Fly");
        this.airtimeThreshold = config.getInt("airtime-threshold", 20);
        this.gravityTolerance = config.getDouble("gravity-tolerance", 0.02);
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        // ── Exemptions ────────────────────────────────────────────────────────
        if (isExempt(player)) {
            data.setAirTicks(0);
            decayVL(data);
            return;
        }

        // ── Airtime check ─────────────────────────────────────────────────────
        if (data.isOnGround()) {
            data.setAirTicks(0);
            decayVL(data);
            return;
        }

        int airTicks = data.getAirTicks();

        if (airTicks > airtimeThreshold) {
            flag(data, "Airtime: " + airTicks + " ticks");
            return;
        }

        // ── Vertical velocity check (gravity) ─────────────────────────────────
        // After the first few ticks in the air, gravity should be pulling down.
        // If the player is moving upward without jump boost / flight, flag.
        if (airTicks > 3 && data.getLastLocation() != null) {
            double dy = player.getLocation().getY() - data.getLastLocation().getY();
            // Gravity in vanilla = -0.0784 per tick after initial jump peak
            if (dy > gravityTolerance && airTicks > 8) {
                flag(data, "NoGravity: dy=" + String.format("%.4f", dy) + " air=" + airTicks);
            }
        }
    }

    private boolean isExempt(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return true;
        if (player.getGameMode() == GameMode.SPECTATOR) return true;
        if (player.getAllowFlight() && player.isFlying()) return true;
        if (player.isGliding()) return true;
        if (player.isInWater()) return true;
        if (player.isInLava()) return true;
        if (player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) return true;
        // Check for climbable blocks (ladder, vine)
        try {
            if (player.getLocation().getBlock().isPassable() &&
                player.getLocation().subtract(0, 0.1, 0).getBlock()
                    .getType().name().contains("LADDER")) return true;
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    protected String getCategory() { return "movement"; }
}
