package me.zerinho23.zerac.checks;

import lombok.Getter;
import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.alerts.AlertManager;
import me.zerinho23.zerac.config.CheckConfig;
import me.zerinho23.zerac.models.PlayerData;
import org.bukkit.entity.Player;

/**
 * Base class for all anticheat checks.
 *
 * <p>Each subclass overrides {@link #process(PlayerData)} and calls
 * {@link #flag(PlayerData, String)} when a violation is detected.
 *
 * <p>The VL lifecycle:
 * <ol>
 *   <li>On violation: {@code vl += vlIncrement}
 *   <li>On clean tick: {@code vl -= vlDecay} (clamped to 0)
 *   <li>Punishment thresholds are evaluated after every flag
 * </ol>
 */
@Getter
public abstract class AbstractCheck {

    protected final ZeracPlugin plugin;
    protected final String      checkName;
    protected final CheckConfig config;

    // ── Configuration cache ───────────────────────────────────────────────────

    protected final boolean enabled;
    protected final boolean experimental;
    protected final boolean debugMode;
    protected final boolean setback;
    protected final double  vlIncrement;
    protected final double  vlDecay;
    protected final double  maxVl;
    protected final String  punishment;

    // ─────────────────────────────────────────────────────────────────────────

    protected AbstractCheck(ZeracPlugin plugin, String checkName) {
        this.plugin    = plugin;
        this.checkName = checkName;
        this.config    = plugin.getZeracConfig().getCheckConfig(checkName);

        this.enabled      = config.isEnabled();
        this.experimental = config.isExperimental();
        this.debugMode    = config.isDebug();
        this.setback      = config.isSetback();
        this.vlIncrement  = config.getVlIncrement();
        this.vlDecay      = config.getVlDecay();
        this.maxVl        = config.getMaxVl();
        this.punishment   = config.getPunishment();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Called every tick (or per-packet) for each online player.
     * Subclasses implement the detection logic here.
     *
     * @param data the current player's runtime data
     */
    public abstract void process(PlayerData data);

    /**
     * Called on plugin reload — subclasses may override to refresh
     * their cached config values if needed.
     */
    public void reload() {
        // default: no-op; subclasses override if they cache extra values
    }

    // ── Flagging helpers ──────────────────────────────────────────────────────

    /**
     * Records a violation for a player, broadcasts an alert, applies setback,
     * and triggers punishment if the VL threshold is reached.
     *
     * @param data   player runtime data
     * @param detail human-readable description of the violation
     */
    protected void flag(PlayerData data, String detail) {
        if (!enabled) return;

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Exempt players (creative, bypass permission, Bedrock)
        if (data.isExempt()) return;
        if (player.hasPermission("zerac.bypass")) return;
        if (player.hasPermission("zerac.bypass." + getCategory())) return;

        double newVl = data.addVL(checkName, vlIncrement);

        // Broadcast alert to staff
        AlertManager alerts = plugin.getAlertManager();
        if (alerts != null) {
            double multiplier = Math.max(1, newVl / vlIncrement);
            alerts.broadcastAlert(data, checkName, (int) multiplier, (int) newVl, (int) maxVl, detail);
        }

        // Setback if enabled
        if (setback && data.getSetbackLocation() != null) {
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> player.teleport(data.getSetbackLocation()));
        }

        // Debug log
        if (debugMode || plugin.getZeracConfig().isDebug()) {
            plugin.getLogger().info("[DEBUG][" + checkName + "] " + player.getName()
                    + " VL=" + newVl + " | " + detail);
        }

        // Check punishment threshold
        evaluatePunishment(data, (int) newVl);
    }

    /**
     * Records a violation using the default VL increment.
     *
     * @param data player runtime data
     */
    protected void flag(PlayerData data) {
        flag(data, "");
    }

    /**
     * Decays VL on a clean movement/action tick.
     *
     * @param data player runtime data
     */
    protected void decayVL(PlayerData data) {
        data.decayVL(checkName, vlDecay);
    }

    // ── Punishment ────────────────────────────────────────────────────────────

    /**
     * Evaluates whether the current VL warrants punishment and delegates
     * to the PunishmentManager if so.
     *
     * @param data player data
     * @param vl   current total VL for this check
     */
    private void evaluatePunishment(PlayerData data, int vl) {
        int banThreshold        = plugin.getZeracConfig().getVlBan();
        int kickThreshold       = plugin.getZeracConfig().getVlKick();
        int globalBanThreshold  = plugin.getZeracConfig().getVlGlobalBan();

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        String reason = "Hacking [" + checkName + "] - ZERAC AntiCheat";

        if (vl >= globalBanThreshold) {
            plugin.getPunishmentManager().globalBan(player, reason, checkName);
        } else if (vl >= banThreshold) {
            plugin.getPunishmentManager().ban(player, reason, checkName);
        } else if (vl >= kickThreshold) {
            plugin.getPunishmentManager().kick(player, reason);
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Returns the check category string (combat, movement, world, player)
     * used for bypass permission nodes. Defaults to "combat".
     *
     * @return category name
     */
    protected String getCategory() {
        return "combat";
    }

    /**
     * Returns whether the check is active and should run.
     *
     * @return true if enabled
     */
    public boolean isActive() {
        return enabled;
    }

    @Override
    public String toString() {
        return checkName + "(enabled=" + enabled + ", maxVl=" + maxVl + ")";
    }
}
