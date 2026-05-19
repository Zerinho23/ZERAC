package me.zerinho23.zerac.api;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.models.ViolationRecord;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for ZERAC AntiCheat.
 *
 * <p>Third-party plugins can use this API to:
 * <ul>
 *   <li>Query current violation levels
 *   <li>Exempt players from checks
 *   <li>Register custom checks
 *   <li>Listen to ZERAC events (via Bukkit event system)
 *   <li>Query the global blacklist
 * </ul>
 *
 * <p>Access via:
 * <pre>{@code
 * ZeracAPI api = ZeracPlugin.getInstance().getApi();
 * }</pre>
 *
 * <p>The API is stable — methods will not be removed without deprecation notice.
 */
public class ZeracAPI {

    private final ZeracPlugin plugin;

    public ZeracAPI(ZeracPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Player queries ────────────────────────────────────────────────────────

    /**
     * Returns the current violation level for a player on a specific check.
     *
     * @param uuid      the player's UUID
     * @param checkName the check name (e.g. "KillAura")
     * @return current VL, or 0.0 if not flagged or offline
     */
    public double getVL(UUID uuid, String checkName) {
        PlayerData data = plugin.getPlayerDataCache().get(uuid);
        return data != null ? data.getVL(checkName) : 0.0;
    }

    /**
     * Returns all current violation levels for an online player.
     *
     * @param uuid the player's UUID
     * @return map of check name → VL, or empty if offline
     */
    public java.util.Map<String, Double> getAllVLs(UUID uuid) {
        PlayerData data = plugin.getPlayerDataCache().get(uuid);
        return data != null ? java.util.Collections.unmodifiableMap(data.getViolationLevels())
                : java.util.Collections.emptyMap();
    }

    /**
     * Returns the historical violations for a player from the database.
     *
     * @param uuid the player's UUID
     * @return future resolving to a list of violation records
     */
    public CompletableFuture<List<ViolationRecord>> getViolationHistory(UUID uuid) {
        return plugin.getDatabaseManager().getViolations(uuid);
    }

    // ── Exemption management ──────────────────────────────────────────────────

    /**
     * Exempts a player from all ZERAC checks.
     * Useful during teleports, cutscenes, or NPC interactions.
     *
     * @param uuid   the player's UUID
     * @param exempt true to exempt, false to re-enable checks
     */
    public void setExempt(UUID uuid, boolean exempt) {
        PlayerData data = plugin.getPlayerDataCache().get(uuid);
        if (data != null) data.setExempt(exempt);
    }

    /**
     * Returns whether a player is currently exempt from checks.
     *
     * @param uuid the player's UUID
     * @return true if exempt
     */
    public boolean isExempt(UUID uuid) {
        PlayerData data = plugin.getPlayerDataCache().get(uuid);
        return data == null || data.isExempt();
    }

    // ── Check management ──────────────────────────────────────────────────────

    /**
     * Registers a custom check with ZERAC.
     * Must be called after plugin enable.
     *
     * @param check the check to register
     */
    public void registerCheck(AbstractCheck check) {
        plugin.getCheckRegistry().register(check);
    }

    /**
     * Returns all registered checks (including disabled ones).
     *
     * @return unmodifiable collection of checks
     */
    public Collection<AbstractCheck> getChecks() {
        return plugin.getCheckRegistry().getAll();
    }

    /**
     * Returns a check by name.
     *
     * @param name the check name
     * @return the check, or null if not found
     */
    public AbstractCheck getCheck(String name) {
        return plugin.getCheckRegistry().getCheck(name);
    }

    // ── Blacklist ─────────────────────────────────────────────────────────────

    /**
     * Checks if a UUID is on the global blacklist.
     * May make a network request if the UUID is not cached locally.
     *
     * @param uuid the UUID to check
     * @return future resolving to true if blacklisted
     */
    public CompletableFuture<Boolean> isBlacklisted(UUID uuid) {
        return plugin.getBlacklistManager().isBlacklisted(uuid);
    }

    // ── Plugin version ────────────────────────────────────────────────────────

    /**
     * Returns the ZERAC plugin version string.
     *
     * @return version (e.g. "1.0.0")
     */
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
}
