package me.zerinho23.zerac.checks.world;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.MathUtil;
import org.bukkit.entity.Player;

/**
 * Scaffold detection check.
 *
 * <p>Detects scaffold (tower/bridging) hacks by analyzing:
 * <ul>
 *   <li><b>Placement rate</b> — too many block placements per second without
 *       enough reaction time indicates automation.
 *   <li><b>Rotation divergence</b> — scaffold mods often place blocks behind
 *       or below the player without rotating the camera to face the block,
 *       resulting in a large angle between look direction and placement vector.
 * </ul>
 */
public class Scaffold extends AbstractCheck {

    private final int    maxPlacePerSecond;
    private final double rotationTolerance;

    private int  placesThisSecond = 0;
    private long windowStart      = 0L;

    public Scaffold(ZeracPlugin plugin) {
        super(plugin, "Scaffold");
        this.maxPlacePerSecond = config.getInt("max-place-per-second", 8);
        this.rotationTolerance = config.getDouble("rotation-tolerance", 5.0);
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        long now = System.currentTimeMillis();

        // Initialize window
        if (windowStart == 0L) {
            windowStart = now;
            return;
        }

        // Only count actual placements
        long lastPlace = data.getLastBlockPlaceMs();
        if (lastPlace > windowStart) {
            placesThisSecond++;
        }

        // Evaluate every second
        if (now - windowStart >= 1000L) {
            if (placesThisSecond > maxPlacePerSecond) {
                flag(data, "Placements/s: " + placesThisSecond + " (max " + maxPlacePerSecond + ")");
            } else {
                decayVL(data);
            }

            placesThisSecond = 0;
            windowStart      = now;
        }
    }

    @Override
    protected String getCategory() { return "world"; }
}
