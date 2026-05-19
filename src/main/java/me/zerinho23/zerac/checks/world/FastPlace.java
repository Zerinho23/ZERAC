package me.zerinho23.zerac.checks.world;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import org.bukkit.entity.Player;

/**
 * FastPlace detection check.
 *
 * <p>Detects players who place blocks faster than physically possible.
 *
 * <p>The Minecraft client enforces a minimum delay between block placements
 * (approximately 4 ticks / 200ms on vanilla clients). FastPlace mods remove
 * this delay, allowing block placements every tick.
 *
 * <p>Detection: if the time between consecutive block placements is less than
 * {@code min-place-delay} ticks × 50ms, the player is flagged.
 */
public class FastPlace extends AbstractCheck {

    private final long minPlaceDelayMs;

    public FastPlace(ZeracPlugin plugin) {
        super(plugin, "FastPlace");
        int delayTicks = config.getInt("min-place-delay", 2);
        this.minPlaceDelayMs = delayTicks * 50L; // ticks → ms
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        long lastPlace = data.getLastBlockPlaceMs();
        if (lastPlace <= 0) return;

        long elapsed = System.currentTimeMillis() - lastPlace;

        if (elapsed < minPlaceDelayMs && elapsed > 0) {
            flag(data, "Delay: " + elapsed + "ms (min " + minPlaceDelayMs + "ms)");
        } else {
            decayVL(data);
        }
    }

    @Override
    protected String getCategory() { return "world"; }
}
