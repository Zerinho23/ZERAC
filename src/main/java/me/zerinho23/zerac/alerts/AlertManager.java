package me.zerinho23.zerac.alerts;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.MessageUtil;
import me.zerinho23.zerac.utils.TPSUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages alert broadcasting to staff members.
 *
 * <p>Alert format (MiniMessage):
 * {@code [ZERAC] Kevin detectado usando Speed (x6) — VL: 60/100}
 *
 * <p>Hover shows: ping, TPS, version, coordinates, server.
 * Click teleports the staff member to the flagged player.
 *
 * <p>Alerts are rate-limited per player/check pair to avoid spam.
 * Rate limiting uses a configurable cooldown in ticks.
 */
public class AlertManager {

    private final ZeracPlugin plugin;
    private final MiniMessage  miniMessage = MiniMessage.miniMessage();

    /** Tracks last alert time per "playerName|checkName" key. */
    private final ConcurrentHashMap<String, Long> alertCooldowns = new ConcurrentHashMap<>();

    public AlertManager(ZeracPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Broadcasts a formatted detection alert to all online staff with alerts enabled.
     *
     * @param data       flagged player's runtime data
     * @param checkName  name of the check that fired
     * @param multiplier how many times above baseline (shown as "x6")
     * @param vl         current violation level
     * @param maxVl      maximum violation level for this check
     * @param detail     optional extra detail string
     */
    public void broadcastAlert(PlayerData data, String checkName,
                               int multiplier, int vl, int maxVl, String detail) {
        // Rate limiting — one alert per cooldown ticks
        String key = data.getName() + "|" + checkName;
        long now   = System.currentTimeMillis();
        int cooldownMs = plugin.getZeracConfig().getAlertCooldown() * 50; // ticks → ms

        Long lastAlert = alertCooldowns.get(key);
        if (lastAlert != null && (now - lastAlert) < cooldownMs) return;
        alertCooldowns.put(key, now);

        // Skip if below minimum broadcast VL
        if (vl < plugin.getZeracConfig().getAlertMinBroadcastVl()) return;

        // Build the alert component on the calling thread, send on main thread
        Component alertComponent = buildAlertComponent(data, checkName, multiplier, vl, maxVl, detail);

        // Webhooks (async)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getWebhookManager().sendDetection(data, checkName, vl, maxVl));

        // Dispatch to staff on main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (!staff.hasPermission("zerac.alerts")) continue;

                PlayerData staffData = plugin.getPlayerDataCache().get(staff.getUniqueId());
                if (staffData != null && !staffData.isAlertsEnabled()) continue;

                plugin.getAdventure().player(staff).sendMessage(alertComponent);

                // Play alert sound
                String soundName = plugin.getZeracConfig().getAlertSound();
                try {
                    Sound sound = Sound.valueOf(soundName);
                    staff.playSound(staff.getLocation(), sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException ignored) { /* invalid sound name */ }
            }
        });
    }

    /**
     * Builds the rich alert {@link Component} with hover and click actions.
     */
    private Component buildAlertComponent(PlayerData data, String checkName,
                                          int multiplier, int vl, int maxVl, String detail) {
        Player player    = data.getPlayer();
        double tps       = TPSUtil.getTPS();
        int    ping      = player != null ? player.getPing() : 0;
        String version   = player != null ? getClientVersion(data) : "?";
        String x         = player != null ? String.valueOf((int) player.getLocation().getX()) : "?";
        String y         = player != null ? String.valueOf((int) player.getLocation().getY()) : "?";
        String z         = player != null ? String.valueOf((int) player.getLocation().getZ()) : "?";
        String server    = plugin.getZeracConfig().getServerName();

        // Main alert line
        String alertFormat = plugin.getZeracConfig().getAlertFormat();
        Component main = miniMessage.deserialize(alertFormat,
                Placeholder.parsed("prefix",     MessageUtil.getPrefix()),
                Placeholder.parsed("player",     data.getName()),
                Placeholder.parsed("check",      checkName),
                Placeholder.parsed("multiplier", String.valueOf(multiplier)),
                Placeholder.parsed("vl",         String.valueOf(vl)),
                Placeholder.parsed("max-vl",     String.valueOf(maxVl))
        );

        // Hover component (shown on mouse-over)
        if (plugin.getZeracConfig().isAlertHover()) {
            String hoverTemplate = plugin.getZeracConfig().getMessages()
                    .getString("alert.hover", "<gray>No hover configured");

            Component hover = miniMessage.deserialize(hoverTemplate,
                    Placeholder.parsed("player",  data.getName()),
                    Placeholder.parsed("check",   checkName),
                    Placeholder.parsed("vl",      String.valueOf(vl)),
                    Placeholder.parsed("max-vl",  String.valueOf(maxVl)),
                    Placeholder.parsed("ping",    String.valueOf(ping)),
                    Placeholder.parsed("tps",     String.format("%.1f", tps)),
                    Placeholder.parsed("version", version),
                    Placeholder.parsed("x",       x),
                    Placeholder.parsed("y",       y),
                    Placeholder.parsed("z",       z),
                    Placeholder.parsed("server",  server)
            );

            main = main.hoverEvent(HoverEvent.showText(hover));
        }

        // Click action — teleport to flagged player
        if (player != null) {
            main = main.clickEvent(ClickEvent.runCommand(
                    "/tp " + data.getName()));
        }

        return main;
    }

    /**
     * Returns a human-readable client version string for display in alerts.
     *
     * @param data player data
     * @return version string like "1.20.4" or "1.8" or "Bedrock"
     */
    private String getClientVersion(PlayerData data) {
        if (data.isBedrockPlayer()) return "Bedrock";
        int protocol = data.getProtocolVersion();
        if (protocol <= 0) return "Unknown";
        // Basic mapping of common protocol versions
        return switch (protocol) {
            case 47  -> "1.8";
            case 110 -> "1.9.4";
            case 315 -> "1.12.2";
            case 340 -> "1.12.2";
            case 404 -> "1.13.2";
            case 477 -> "1.14";
            case 578 -> "1.15.2";
            case 754 -> "1.16.5";
            case 758 -> "1.18.2";
            case 760 -> "1.19.2";
            case 763 -> "1.20.1";
            case 765 -> "1.20.3";
            case 767 -> "1.21";
            default  -> "1.x (v" + protocol + ")";
        };
    }

    /**
     * Clears all cooldown entries — called on reload.
     */
    public void clearCooldowns() {
        alertCooldowns.clear();
    }
}
