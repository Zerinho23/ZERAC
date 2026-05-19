package me.zerinho23.zerac.checks.movement;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import org.bukkit.entity.Player;

/**
 * Timer detection check.
 *
 * <p>Detects clients that speed up the game clock ("Timer hack"), causing
 * the client to send more position packets per second than the server expects.
 *
 * <p>The Minecraft protocol expects exactly 20 position packets per second.
 * Timer hacks send 22–100+ per second. This check counts the position update
 * rate and flags when it significantly exceeds 20/s.
 *
 * <p>Approach:
 * <ul>
 *   <li>Every second (20 server ticks), count how many position packets
 *       were received from this client.
 *   <li>If the count exceeds {@code max-tps} (default 21), flag the player.
 *   <li>The counter resets each second.
 * </ul>
 */
public class Timer extends AbstractCheck {

    private final double maxTps;

    /** Position update counter for the current second window. */
    private int  updateCount = 0;
    private long windowStart = 0L;

    public Timer(ZeracPlugin plugin) {
        super(plugin, "Timer");
        this.maxTps = config.getDouble("max-tps", 21.0);
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

        updateCount++;

        // Evaluate every 1000ms (1 second window)
        if (now - windowStart >= 1000L) {
            double measuredTps = updateCount * (1000.0 / (now - windowStart));

            if (measuredTps > maxTps) {
                flag(data, String.format("Packets/s: %.1f (max %.1f)", measuredTps, maxTps));
            } else {
                decayVL(data);
            }

            // Reset window
            updateCount  = 0;
            windowStart  = now;
        }
    }

    @Override
    protected String getCategory() { return "movement"; }
}
