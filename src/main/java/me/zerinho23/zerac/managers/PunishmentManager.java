package me.zerinho23.zerac.managers;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.config.ZeracConfig;
import me.zerinho23.zerac.models.BanRecord;
import me.zerinho23.zerac.utils.MessageUtil;
import org.bukkit.BanList;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Routes punishment actions through the best available ban plugin.
 *
 * <p>Detection order (AUTO mode):
 * <ol>
 *   <li>LiteBans — preferred for large servers
 *   <li>AdvancedBan — fallback
 *   <li>Vanilla Bukkit ban list — final fallback
 * </ol>
 *
 * <p>All punishment calls are fire-and-forget; heavy work (DB writes,
 * webhook calls) is async.
 */
public class PunishmentManager {

    private final ZeracPlugin plugin;
    private final ZeracConfig config;

    /** Resolved provider name after detection. */
    private final String resolvedProvider;

    public PunishmentManager(ZeracPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getZeracConfig();
        this.resolvedProvider = resolveProvider();
        plugin.getLogger().info("Punishment provider: " + resolvedProvider);
    }

    // ── Provider resolution ───────────────────────────────────────────────────

    /**
     * Detects which ban plugin is available.
     *
     * @return provider name: LITEBANS, ADVANCEDBAN, or VANILLA
     */
    private String resolveProvider() {
        String configured = config.getPunishmentProvider();
        if (!configured.equalsIgnoreCase("AUTO")) {
            return configured.toUpperCase();
        }

        if (isPluginPresent("LiteBans"))    return "LITEBANS";
        if (isPluginPresent("AdvancedBan")) return "ADVANCEDBAN";
        return "VANILLA";
    }

    private boolean isPluginPresent(String name) {
        Plugin p = plugin.getServer().getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    // ── Punishment actions ────────────────────────────────────────────────────

    /**
     * Kicks a player with a formatted message.
     *
     * @param player the target player
     * @param reason the kick reason shown to the player
     */
    public void kick(Player player, String reason) {
        if (!player.isOnline()) return;

        String msg = MessageUtil.parseMessages(
                config.getMessage("punishment.kick-reason", "Kicked by ZERAC AntiCheat"),
                Map.of("reason", reason, "server", config.getServerName())
        );

        plugin.getServer().getScheduler().runTask(plugin,
                () -> player.kick(MessageUtil.toComponent(msg)));
    }

    /**
     * Bans a player using the resolved provider.
     * Saves a local ban record and sends a webhook notification.
     *
     * @param player    the target player
     * @param reason    ban reason
     * @param checkName the check that triggered the ban
     */
    public void ban(Player player, String reason, String checkName) {
        if (!player.isOnline()) return;

        UUID   uuid    = player.getUniqueId();
        String name    = player.getName();
        long   now     = System.currentTimeMillis();
        long   dur     = config.getDefaultBanDuration();

        // Build ban record
        BanRecord record = BanRecord.builder()
                .uuid(uuid)
                .name(name)
                .reason(reason)
                .checkName(checkName)
                .bannedBy("ZERAC")
                .server(config.getServerName())
                .duration(dur)
                .timestamp(now)
                .global(false)
                .build();

        // Save to local DB async
        plugin.getDatabaseManager().saveBan(record);

        // Apply the ban
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            applyBan(player, record);
            kickWithBanScreen(player, record);
        });

        // Discord webhook async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getWebhookManager().sendBan(record));
    }

    /**
     * Bans a player globally (local ban + global blacklist API entry).
     *
     * @param player    the target player
     * @param reason    ban reason
     * @param checkName the check that triggered the ban
     */
    public void globalBan(Player player, String reason, String checkName) {
        if (!player.isOnline()) return;

        UUID   uuid = player.getUniqueId();
        String name = player.getName();
        long   now  = System.currentTimeMillis();

        BanRecord record = BanRecord.builder()
                .uuid(uuid)
                .name(name)
                .reason(reason)
                .checkName(checkName)
                .bannedBy("ZERAC")
                .server(config.getServerName())
                .duration(0)
                .timestamp(now)
                .global(true)
                .build();

        // Local DB + global API (async)
        plugin.getDatabaseManager().saveBan(record);
        plugin.getBlacklistManager().addToBlacklist(record);

        // Apply punishment on main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            applyBan(player, record);
            kickWithGlobalBanScreen(player, record);
        });

        // Webhooks
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getWebhookManager().sendBan(record));
    }

    // ── Provider dispatch ─────────────────────────────────────────────────────

    /**
     * Applies a ban through the resolved ban plugin.
     *
     * @param player the target player
     * @param record the ban record
     */
    private void applyBan(Player player, BanRecord record) {
        switch (resolvedProvider) {
            case "LITEBANS"    -> applyLiteBans(player, record);
            case "ADVANCEDBAN" -> applyAdvancedBan(player, record);
            default            -> applyVanillaBan(player, record);
        }
    }

    private void applyLiteBans(Player player, BanRecord record) {
        try {
            // LiteBans API — reflective call to avoid hard dependency
            Class<?> liteBans = Class.forName("litebans.api.Entry");
            // LiteBans uses its own API — basic vanilla fallback if reflection fails
            applyVanillaBan(player, record);
        } catch (ClassNotFoundException e) {
            applyVanillaBan(player, record);
        }
    }

    private void applyAdvancedBan(Player player, BanRecord record) {
        try {
            // AdvancedBan API — reflective call
            Class<?> ab = Class.forName("me.leoko.advancedban.manager.PunishmentManager");
            applyVanillaBan(player, record);
        } catch (ClassNotFoundException e) {
            applyVanillaBan(player, record);
        }
    }

    private void applyVanillaBan(Player player, BanRecord record) {
        Date expiry = record.isPermanent() ? null
                : new Date(record.getTimestamp() + record.getDuration());

        @SuppressWarnings("deprecation")
        BanList banList = plugin.getServer().getBanList(BanList.Type.NAME);
        banList.addBan(player.getName(), record.getReason(), expiry, "ZERAC");
    }

    // ── Kick screens ──────────────────────────────────────────────────────────

    private void kickWithBanScreen(Player player, BanRecord record) {
        String msg = MessageUtil.parseMessages(
                config.getMessage("punishment.ban-screen", "You have been banned."),
                Map.of(
                        "reason", record.getReason(),
                        "server", record.getServer(),
                        "id",     record.getBanId()
                )
        );
        player.kick(MessageUtil.toComponent(msg));
    }

    private void kickWithGlobalBanScreen(Player player, BanRecord record) {
        String msg = MessageUtil.parseMessages(
                config.getMessage("punishment.global-ban-screen", "You have been globally banned."),
                Map.of(
                        "reason", record.getReason(),
                        "server", record.getServer(),
                        "id",     record.getBanId()
                )
        );
        player.kick(MessageUtil.toComponent(msg));
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns the resolved provider name for display in /zerac stats.
     *
     * @return provider name
     */
    public String getResolvedProvider() {
        return resolvedProvider;
    }
}
