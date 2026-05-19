package me.zerinho23.zerac.checks.combat;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.MathUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * KillAura detection check.
 *
 * <p>Detects KillAura by analyzing:
 * <ul>
 *   <li><b>Angle check</b> — the angle between the player's look direction
 *       and the direction to the attacked entity must be within a human
 *       feasible range. KillAura clients often hit entities behind or to the
 *       side of the player.
 *   <li><b>Multi-target check</b> — legitimate players can only click once
 *       per tick; hitting multiple entities per swing is impossible without
 *       a client mod.
 *   <li><b>Attack rate</b> — more than {@code maxAttacksPerTick} entity
 *       attacks in a single tick indicates automation.
 * </ul>
 *
 * <p>False positive mitigations:
 * <ul>
 *   <li>Bedrock players are exempt (Geyser touch controls behave differently)
 *   <li>Configurable angle tolerance ({@code max-angle-diff})
 *   <li>Configurable max attacks per tick
 * </ul>
 */
public class KillAura extends AbstractCheck {

    /** Configurable: max degrees between look direction and attack target. */
    private final double maxAngleDiff;
    /** Configurable: max entity attacks allowed per tick. */
    private final int    maxAttacksPerTick;

    /** Tracks attacks per tick for multi-target detection. */
    private int  attacksThisTick = 0;
    private long lastAttackTick  = -1L;

    public KillAura(ZeracPlugin plugin) {
        super(plugin, "KillAura");
        this.maxAngleDiff     = config.getDouble("max-angle-diff", 30.0);
        this.maxAttacksPerTick = config.getInt("max-attacks-per-tick", 3);
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Only check when the player just attacked (prevents constant processing)
        long currentTick = data.getServerTick();
        long lastAttack  = data.getLastAttackTick();

        // No attack this tick — decay VL and reset counter
        if (currentTick - lastAttack > 2) {
            decayVL(data);
            attacksThisTick = 0;
            return;
        }

        // ── Multi-target attack check ─────────────────────────────────────────
        if (lastAttack == currentTick) {
            attacksThisTick++;
            if (attacksThisTick > maxAttacksPerTick) {
                flag(data, "Multi-target: " + attacksThisTick + " attacks/tick");
                return;
            }
        } else {
            attacksThisTick = 1;
        }

        // ── Angle check against nearby entities ───────────────────────────────
        Collection<Entity> nearby = player.getNearbyEntities(6, 3, 6);
        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.equals(player)) continue;

            double angle = MathUtil.getAngleDifference(
                    player.getEyeLocation(),
                    entity.getLocation()
            );

            // If the player is attacking but looking far away from every entity,
            // it's suspicious — KillAura typically locks on at odd angles
            if (angle > maxAngleDiff + 90) {
                flag(data, "Angle: " + String.format("%.1f", angle) + "°");
                return;
            }
        }

        decayVL(data);
    }

    @Override
    protected String getCategory() {
        return "combat";
    }
}
