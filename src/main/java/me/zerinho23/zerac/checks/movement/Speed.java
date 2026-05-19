package me.zerinho23.zerac.checks.movement;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.MathUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Speed detection check.
 *
 * <p>Measures horizontal movement speed and compares it against the
 * expected maximum for the player's current state.
 *
 * <p>Expected speed factors:
 * <ul>
 *   <li>Base walk speed: 0.2 blocks/tick (4.0 b/s)
 *   <li>Sprint bonus: +30% (0.26 b/tick, 5.6 b/s)
 *   <li>Speed effect: +20% per amplifier level
 *   <li>Soulsand: ~0.4× speed
 *   <li>Ice: slight increase
 * </ul>
 *
 * <p>The check grants a tolerance multiplier ({@code max-speed-multiplier})
 * above the calculated limit to absorb legitimate latency spikes.
 */
public class Speed extends AbstractCheck {

    private final double maxSpeedMultiplier;

    /** Base horizontal speed in blocks per tick. */
    private static final double BASE_SPEED   = 0.2;
    private static final double SPRINT_BONUS = 0.3;

    public Speed(ZeracPlugin plugin) {
        super(plugin, "Speed");
        this.maxSpeedMultiplier = config.getDouble("max-speed-multiplier", 1.05);
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Exempt players in vehicles, on ladders, or using elytra
        if (player.isInsideVehicle()) return;
        if (player.isGliding()) return;

        // Calculate expected max speed for current state
        double expectedMax = calculateExpectedMaxSpeed(player);
        double limit       = expectedMax * maxSpeedMultiplier;

        // Measure actual speed from position delta
        if (data.getLastLocation() == null) return;
        double actualSpeed = MathUtil.getHorizontalDistance(
                player.getLocation(), data.getLastLocation());

        if (actualSpeed > limit) {
            double excess = actualSpeed - expectedMax;
            flag(data, String.format("Speed: %.4f (limit %.4f, excess +%.4f)",
                    actualSpeed, expectedMax, excess));
        } else {
            decayVL(data);
        }
    }

    /**
     * Calculates the maximum expected horizontal speed for the player's current
     * state, accounting for sprint, speed effects, and other modifiers.
     *
     * @param player the player to calculate for
     * @return max blocks/tick
     */
    private double calculateExpectedMaxSpeed(Player player) {
        double speed = BASE_SPEED;

        // Apply sprint bonus
        if (player.isSprinting()) {
            speed += SPRINT_BONUS * BASE_SPEED;
        }

        // Apply Speed potion effect
        PotionEffect speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEffect != null) {
            int amplifier = speedEffect.getAmplifier(); // 0-indexed (Speed I = 0)
            speed += speed * 0.20 * (amplifier + 1);
        }

        // Slowness potion reduces speed
        PotionEffect slowEffect = player.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slowEffect != null) {
            int amplifier = slowEffect.getAmplifier();
            speed -= speed * 0.15 * (amplifier + 1);
        }

        // Custom walk speed setting
        speed *= player.getWalkSpeed() / 0.2;

        return Math.max(0, speed);
    }

    @Override
    protected String getCategory() { return "movement"; }
}
