package me.zerinho23.zerac.listeners;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.models.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join and quit lifecycle for ZERAC.
 *
 * <p>On join:
 * <ol>
 *   <li>Creates a {@link PlayerData} entry in cache
 *   <li>Upserts player record in database (async)
 *   <li>Runs blacklist check (async)
 *   <li>Runs AntiVPN check (async)
 *   <li>Detects Bedrock/Geyser players
 * </ol>
 *
 * <p>On quit:
 * <ol>
 *   <li>Saves final player data (async)
 *   <li>Removes entry from cache
 * </ol>
 *
 * <p>On move:
 * <ul>
 *   <li>Updates the setback position when on safe ground
 * </ul>
 */
public class PlayerJoinLeaveListener implements Listener {

    private final ZeracPlugin plugin;

    public PlayerJoinLeaveListener(ZeracPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Create in-memory data
        PlayerData data = plugin.getPlayerDataCache().create(player);

        // 2. Detect Bedrock via Geyser/Floodgate prefix or UUID range
        detectBedrock(player, data);

        // 3. Detect ViaVersion protocol version
        detectProtocol(player, data);

        // 4. DB upsert (async — never blocks join)
        plugin.getDatabaseManager().upsertPlayer(player.getUniqueId(), player.getName());

        // 5. Blacklist check (async)
        plugin.getBlacklistManager().checkOnJoin(player);

        // 6. AntiVPN check (async)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getAntiVPNManager().checkPlayer(player));

        // 7. Dispatch check scheduler for this player
        scheduleCheckTask(data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Save final VL snapshot (async)
        plugin.getDatabaseManager().upsertPlayer(player.getUniqueId(), player.getName());

        // Remove from cache
        plugin.getPlayerDataCache().remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        PlayerData data = plugin.getPlayerDataCache().get(event.getPlayer());
        if (data == null) return;

        // Update setback on safe ground movement
        data.updateSetback();

        // Update ground state
        data.setWasOnGround(data.isOnGround());
        data.setOnGround(event.getPlayer().isOnGround());

        if (data.isOnGround()) {
            data.setGroundTicks(data.getGroundTicks() + 1);
            data.setAirTicks(0);
        } else {
            data.setAirTicks(data.getAirTicks() + 1);
            data.setGroundTicks(0);
        }

        // Update last location
        if (event.getTo() != null) {
            data.setLastLocation(event.getTo().clone());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Starts a repeating task that runs all checks for this player every tick.
     * Cancelled automatically when the player leaves.
     */
    private void scheduleCheckTask(PlayerData data) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!data.getPlayer().isOnline()) {
                task.cancel();
                return;
            }
            data.setServerTick(data.getServerTick() + 1);
            plugin.getCheckRegistry().runChecks(data);
        }, 10L, 1L);
    }

    /**
     * Detects whether the player is on Bedrock via Geyser/Floodgate UUID prefix.
     * Bedrock UUIDs from Geyser start with 00000000-0000-0000-.
     */
    private void detectBedrock(Player player, PlayerData data) {
        String uuidStr = player.getUniqueId().toString();
        boolean isFloodgate = uuidStr.startsWith("00000000-0000-0000-");

        // Also check if Geyser plugin is present and player is recognized
        if (!isFloodgate) {
            try {
                Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object geyserApi = api.getMethod("api").invoke(null);
                boolean isGeyser = (boolean) geyserApi.getClass()
                        .getMethod("isBedrockPlayer", java.util.UUID.class)
                        .invoke(geyserApi, player.getUniqueId());
                isFloodgate = isGeyser;
            } catch (Exception ignored) { /* Geyser not installed */ }
        }

        data.setBedrockPlayer(isFloodgate);
        if (isFloodgate) data.setExempt(true); // exempt Bedrock players from some checks
    }

    /**
     * Detects the player's protocol version via ViaVersion if available.
     */
    private void detectProtocol(Player player, PlayerData data) {
        try {
            Class<?> viaApi = Class.forName("com.viaversion.viaversion.api.Via");
            Object api = viaApi.getMethod("getAPI").invoke(null);
            int version = (int) api.getClass()
                    .getMethod("getPlayerVersion", java.util.UUID.class)
                    .invoke(api, player.getUniqueId());
            data.setProtocolVersion(version);
        } catch (Exception ignored) {
            // ViaVersion not installed — use server version
        }
    }
}
