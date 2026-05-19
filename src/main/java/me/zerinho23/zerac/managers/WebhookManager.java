package me.zerinho23.zerac.managers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.config.ZeracConfig;
import me.zerinho23.zerac.models.BanRecord;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.TPSUtil;
import okhttp3.*;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Sends rich Discord Embed webhooks for detections, bans, VPN detections,
 * and staff alerts.
 *
 * <p>All network calls are fire-and-forget and run on a dedicated async
 * thread — the main thread is never blocked.
 */
public class WebhookManager {

    private final ZeracPlugin  plugin;
    private final ZeracConfig  config;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public WebhookManager(ZeracPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getZeracConfig();
        this.http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    // ── Detection webhook ─────────────────────────────────────────────────────

    /**
     * Sends a detection embed when a player flags a check.
     *
     * @param data      the flagged player's data
     * @param checkName the check that fired
     * @param vl        current violation level
     * @param maxVl     max violation level for the check
     */
    public void sendDetection(PlayerData data, String checkName, int vl, int maxVl) {
        if (!config.isDetectionWebhookEnabled()) return;
        if (vl < config.getDetectionWebhookMinVl()) return;
        String url = config.getDetectionWebhookUrl();
        if (url.isBlank()) return;

        Player player = data.getPlayer();
        String coords = player != null
                ? (int) player.getLocation().getX() + ", "
                  + (int) player.getLocation().getY() + ", "
                  + (int) player.getLocation().getZ()
                : "Unknown";

        ObjectNode embed = buildEmbed(
                "⚠️ Detection — " + checkName,
                "**" + data.getName() + "** triggered **" + checkName + "**",
                0xF59E0B  // amber
        );
        ArrayNode fields = embed.putArray("fields");
        addField(fields, "Player",    data.getName(), true);
        addField(fields, "Check",     checkName, true);
        addField(fields, "VL",        vl + " / " + maxVl, true);
        addField(fields, "Ping",      player != null ? player.getPing() + "ms" : "?", true);
        addField(fields, "TPS",       String.format("%.1f", TPSUtil.getTPS()), true);
        addField(fields, "Coords",    coords, true);
        addField(fields, "Server",    config.getServerName(), true);

        sendAsync(url, wrapEmbed(embed));
    }

    // ── Ban webhook ───────────────────────────────────────────────────────────

    /**
     * Sends a ban embed when a player is banned by ZERAC.
     *
     * @param record the ban record
     */
    public void sendBan(BanRecord record) {
        if (!config.isBanWebhookEnabled()) return;
        String url = config.getBanWebhookUrl();
        if (url.isBlank()) return;

        int color = record.isGlobal() ? 0xEF4444 : 0x8B5CF6; // red for global, purple for local
        String title = record.isGlobal() ? "🚫 Global Ban" : "🔨 Ban";

        ObjectNode embed = buildEmbed(title, null, color);
        ArrayNode fields = embed.putArray("fields");
        addField(fields, "Player",  record.getName(), true);
        addField(fields, "UUID",    record.getUuid().toString(), false);
        addField(fields, "Reason",  record.getReason(), false);
        addField(fields, "Check",   record.getCheckName(), true);
        addField(fields, "Server",  record.getServer(), true);
        addField(fields, "ID",      record.getBanId(), true);
        addField(fields, "Global",  record.isGlobal() ? "Yes" : "No", true);

        sendAsync(url, wrapEmbed(embed));
    }

    // ── VPN Detection webhook ─────────────────────────────────────────────────

    /**
     * Sends a VPN detection embed.
     *
     * @param player  the player detected with a VPN
     * @param ip      the IP address
     * @param country the country code
     */
    public void sendVpnDetection(Player player, String ip, String country) {
        if (!config.isVpnWebhookEnabled()) return;
        String url = config.getVpnWebhookUrl();
        if (url.isBlank()) return;

        ObjectNode embed = buildEmbed("🛡️ VPN Detected", null, 0x06B6D4);
        ArrayNode fields = embed.putArray("fields");
        addField(fields, "Player",  player.getName(), true);
        addField(fields, "IP",      ip, true);
        addField(fields, "Country", country.isBlank() ? "Unknown" : country, true);
        addField(fields, "Server",  config.getServerName(), true);
        addField(fields, "Action",  config.getAntiVPNAction(), true);

        sendAsync(url, wrapEmbed(embed));
    }

    // ── Staff alert webhook ───────────────────────────────────────────────────

    /**
     * Sends a staff alert embed.
     *
     * @param message the alert text
     */
    public void sendStaffAlert(String message) {
        if (!config.isStaffWebhookEnabled()) return;
        String url = config.getStaffWebhookUrl();
        if (url.isBlank()) return;

        ObjectNode embed = buildEmbed("📢 Staff Alert", message, 0x10B981);
        sendAsync(url, wrapEmbed(embed));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ObjectNode buildEmbed(String title, String description, int color) {
        ObjectNode embed = mapper.createObjectNode();
        embed.put("title", title);
        if (description != null) embed.put("description", description);
        embed.put("color", color);
        embed.put("timestamp", Instant.now().toString());

        ObjectNode footer = embed.putObject("footer");
        footer.put("text", "ZERAC AntiCheat • " + config.getServerName());

        return embed;
    }

    private ObjectNode wrapEmbed(ObjectNode embed) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("username", "ZERAC AntiCheat");
        ArrayNode embeds = payload.putArray("embeds");
        embeds.add(embed);
        return payload;
    }

    private void addField(ArrayNode fields, String name, String value, boolean inline) {
        ObjectNode field = fields.addObject();
        field.put("name", name);
        field.put("value", value != null ? value : "—");
        field.put("inline", inline);
    }

    /**
     * Sends a webhook payload asynchronously. Failures are logged and silently swallowed
     * — webhook errors should never impact plugin performance.
     *
     * @param webhookUrl the Discord webhook URL
     * @param payload    the JSON payload node
     */
    private void sendAsync(String webhookUrl, ObjectNode payload) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String json = mapper.writeValueAsString(payload);
                RequestBody body = RequestBody.create(json, JSON);
                Request req = new Request.Builder()
                        .url(webhookUrl)
                        .post(body)
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    if (!resp.isSuccessful()) {
                        plugin.getLogger().warning("[Webhook] POST failed: " + resp.code());
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[Webhook] Send failed", e);
            }
        });
    }
}
