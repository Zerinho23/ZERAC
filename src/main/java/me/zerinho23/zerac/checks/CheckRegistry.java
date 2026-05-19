package me.zerinho23.zerac.checks;

import lombok.Getter;
import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.combat.*;
import me.zerinho23.zerac.checks.movement.*;
import me.zerinho23.zerac.checks.world.*;
import me.zerinho23.zerac.models.PlayerData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Central registry for all anticheat checks.
 *
 * <p>Manages instantiation, ordering, and dispatch of checks.
 * Each check is instantiated once and reused across all players.
 * Checks are processed in registration order.
 */
public class CheckRegistry {

    private final ZeracPlugin plugin;

    @Getter
    private final List<AbstractCheck> checks = new ArrayList<>();

    public CheckRegistry(ZeracPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Instantiates and registers all checks.
     * Disabled checks are still registered (for stats) but skipped in dispatch.
     */
    public void registerAll() {
        checks.clear();

        // ── Combat checks ─────────────────────────────────────────────────────
        register(new KillAura(plugin));
        register(new Reach(plugin));
        register(new AimAssist(plugin));
        register(new AutoClicker(plugin));

        // ── Movement checks ───────────────────────────────────────────────────
        register(new Speed(plugin));
        register(new Fly(plugin));
        register(new Velocity(plugin));
        register(new Timer(plugin));
        register(new NoSlow(plugin));

        // ── World checks ──────────────────────────────────────────────────────
        register(new Scaffold(plugin));
        register(new FastBreak(plugin));
        register(new FastPlace(plugin));

        plugin.getLogger().info("Registered " + checks.size() + " checks.");
    }

    /**
     * Adds a single check to the registry.
     *
     * @param check the check instance to register
     */
    public void register(AbstractCheck check) {
        checks.add(check);
        if (plugin.getZeracConfig().isDebug()) {
            plugin.getLogger().info("Registered check: " + check.getCheckName()
                    + " (enabled=" + check.isEnabled() + ")");
        }
    }

    /**
     * Dispatches all enabled checks for a specific player.
     * Called every tick from the scheduler.
     *
     * @param data player runtime data
     */
    public void runChecks(PlayerData data) {
        if (data == null || data.getPlayer() == null) return;
        if (!data.getPlayer().isOnline()) return;
        if (data.isExempt()) return;

        for (AbstractCheck check : checks) {
            if (!check.isEnabled()) continue;
            try {
                check.process(data);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error in check " + check.getCheckName()
                                + " for player " + data.getName(), e);
            }
        }
    }

    /**
     * Reloads all check configurations from disk.
     */
    public void reloadAll() {
        for (AbstractCheck check : checks) {
            try {
                check.reload();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error reloading check " + check.getCheckName(), e);
            }
        }
    }

    /**
     * Returns an unmodifiable view of all registered checks.
     *
     * @return immutable check list
     */
    public List<AbstractCheck> getAll() {
        return Collections.unmodifiableList(checks);
    }

    /**
     * Returns the total number of registered checks.
     *
     * @return check count
     */
    public int getCheckCount() {
        return checks.size();
    }

    /**
     * Finds a check by its class-simple-name.
     *
     * @param name check name (e.g. "KillAura")
     * @return the check instance, or null if not found
     */
    public AbstractCheck getCheck(String name) {
        return checks.stream()
                .filter(c -> c.getCheckName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
