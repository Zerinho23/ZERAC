package me.zerinho23.zerac.checks.combat;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.MathUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Reach detection check.
 *
 * <p>Measures the distance between the attacker's eye position and the
 * target entity's bounding box when an attack packet is received.
 *
 * <p>Legitimate reach:
 * <ul>
 *   <li>Survival: 3.0 blocks
 *   <li>Creative: 5.0 blocks
 *   <li>1.8 clients have a 3.0 block base reach (with a small latency buffer)
 * </ul>
 *
 * <p>A configurable tolerance buffer ({@code tolerance}) reduces false positives
 * caused by network latency and server-side hitbox interpolation.
 */
public class Reach extends AbstractCheck {

    private final double maxReach;
    private final double tolerance;

    public Reach(ZeracPlugin plugin) {
        super(plugin, "Reach");
        this.maxReach  = config.getDouble("max-reach", 3.1);
        this.tolerance = config.getDouble("tolerance", 0.05);
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Only process on attack ticks
        if (data.getServerTick() - data.getLastAttackTick() > 2) {
            decayVL(data);
            return;
        }

        // Determine reach limit for this player
        double reachLimit = player.isCreativeMode() ? 5.0 : maxReach;

        // Find the entity the player last attacked
        Entity target = findAttackedEntity(player);
        if (target == null) return;

        double distance = MathUtil.getReach(player, target);
        double threshold = reachLimit + tolerance;

        if (distance > threshold) {
            double excess = distance - reachLimit;
            flag(data, String.format("%.2f blocks (limit %.1f, excess +%.2f)",
                    distance, reachLimit, excess));
        } else {
            decayVL(data);
        }
    }

    /**
     * Finds the nearest entity within plausible attack range.
     * This is a best-effort heuristic since we don't directly know
     * which entity was in the attack packet without deeper packet inspection.
     *
     * @param player the attacking player
     * @return nearest candidate entity, or null
     */
    private Entity findAttackedEntity(Player player) {
        return player.getNearbyEntities(7, 4, 7).stream()
                .filter(e -> e != player)
                .filter(e -> e instanceof org.bukkit.entity.LivingEntity)
                .min((a, b) -> Double.compare(
                        MathUtil.getReach(player, a),
                        MathUtil.getReach(player, b)
                ))
                .orElse(null);
    }

    @Override
    protected String getCategory() { return "combat"; }
}
