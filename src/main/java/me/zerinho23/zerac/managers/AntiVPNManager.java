package me.zerinho23.zerac.managers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.config.ZeracConfig;
import me.zerinho23.zerac.utils.MessageUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * AntiVPN detection system.
 *
 * <p>Checks whether a player's IP belongs to a VPN, proxy, or datacenter
 * using an external API (proxycheck.io or ipqualityscore.com).
 *
 * <p>Results are cached by IP for {@code cache-ttl} seconds to avoid
 * redundant API calls for the same address.
 *
 * <p>Detection runs asynchronously on player join — the main thread
 * is never blocked.
 */
public class AntiVPNManager {

    private final ZeracPlugin  plugin;
    private final ZeracConfig  config;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    /** IP → VPN check result cache. Boolean: true = is VPN/proxy. */
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

    public AntiVPNManager(ZeracPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getZeracConfig();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Checks a player's IP and applies the configured action if it's a VPN.
     * Runs fully asynchronously — safe to call from any thread.
     *
     * @param player the player to check
     */
    public void checkPlayer(Player player) {
        if (!config.isAntiVPNEnabled()) return;

        String ip = getPlayerIP(player);
        if (ip == null || ip.isBlank()) return;

        // Check whitelist IPs
        if (config.getAntiVPNWhitelistIps().contains(ip)) return;

        // Check bypass permission
        if (player.hasPermission("zerac.bypass.vpn")) return;

        checkIP(ip).thenAccept(result -> {
            if (!result.isVpn()) return;
            if (!player.isOnline()) return;

            // Log to database async
            plugin.getDatabaseManager().saveVpnLog(
                    player.getUniqueId(), player.getName(), ip,
                    config.getAntiVPNProvider(), result.getCountry(),
                    config.getAntiVPNAction()
            );

            // Notify staff
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                notifyStaff(player, ip, result.getCountry());
                applyAction(player, ip);
            });

            // Send webhook
            plugin.getWebhookManager().sendVpnDetection(player, ip, result.getCountry());
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "AntiVPN check failed for " + player.getName(), ex);
            return null;
        });
    }

    /**
     * Checks a raw IP address against the configured VPN detection API.
     * Results are cached for {@code cache-ttl} seconds.
     *
     * @param ip the IP address string
     * @return future with the detection result
     */
    public CompletableFuture<VpnResult> checkIP(String ip) {
        // Check cache first
        CachedResult cached = cache.get(ip);
        if (cached != null && !cached.isExpired(config.getAntiVPNCacheTtl())) {
            return CompletableFuture.completedFuture(cached.result());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                VpnResult result = switch (config.getAntiVPNProvider().toLowerCase()) {
                    case "proxycheck"      -> checkWithProxyCheck(ip);
                    case "ipqualityscore" -> checkWithIPQualityScore(ip);
                    default               -> checkWithProxyCheck(ip);
                };
                cache.put(ip, new CachedResult(result, System.currentTimeMillis()));
                return result;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "AntiVPN API request failed", e);
                return new VpnResult(false, "", "");
            }
        });
    }

    // ── Provider implementations ──────────────────────────────────────────────

    /**
     * Queries proxycheck.io API.
     *
     * @param ip the IP to check
     * @return VPN detection result
     */
    private VpnResult checkWithProxyCheck(String ip) throws IOException {
        String apiKey  = config.getAntiVPNApiKey();
        String url     = apiKey.isBlank()
                ? "https://proxycheck.io/v2/" + ip + "?vpn=1&asn=1"
                : "https://proxycheck.io/v2/" + ip + "?key=" + apiKey + "&vpn=1&asn=1";

        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                return new VpnResult(false, "", "");
            }
            JsonNode root    = mapper.readTree(resp.body().string());
            JsonNode ipNode  = root.get(ip);
            if (ipNode == null) return new VpnResult(false, "", "");

            boolean isProxy  = "yes".equalsIgnoreCase(ipNode.path("proxy").asText("no"));
            String country   = ipNode.path("country").asText("");
            String isp       = ipNode.path("isp").asText("");
            return new VpnResult(isProxy, country, isp);
        }
    }

    /**
     * Queries IPQualityScore API.
     *
     * @param ip the IP to check
     * @return VPN detection result
     */
    private VpnResult checkWithIPQualityScore(String ip) throws IOException {
        String apiKey = config.getAntiVPNApiKey();
        if (apiKey.isBlank()) {
            plugin.getLogger().warning("IPQualityScore requires an API key in config.yml (anti-vpn.api-key)");
            return new VpnResult(false, "", "");
        }

        String url = "https://ipqualityscore.com/api/json/ip/" + apiKey + "/" + ip
                + "?strictness=1&allow_public_access_points=true";

        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                return new VpnResult(false, "", "");
            }
            JsonNode root    = mapper.readTree(resp.body().string());
            boolean isProxy  = root.path("proxy").asBoolean(false)
                    || root.path("vpn").asBoolean(false)
                    || root.path("tor").asBoolean(false);
            String country   = root.path("country_code").asText("");
            String isp       = root.path("ISP").asText("");
            return new VpnResult(isProxy, country, isp);
        }
    }

    // ── Action handling ───────────────────────────────────────────────────────

    /**
     * Applies the configured action (KICK, BAN, LOG) to a detected VPN user.
     *
     * @param player the player to act on
     * @param ip     the detected IP
     */
    private void applyAction(Player player, String ip) {
        if (!player.isOnline()) return;

        String action = config.getAntiVPNAction();
        switch (action) {
            case "KICK" -> {
                String msg = MessageUtil.parseMessages(
                        plugin.getZeracConfig().getMessage("anti-vpn.kicked",
                                "VPN detected. Disconnect your VPN to join."),
                        Map.of("ip", ip, "server", config.getServerName())
                );
                player.kick(MessageUtil.toComponent(msg));
            }
            case "BAN" -> {
                String reason = "VPN/Proxy detected";
                plugin.getPunishmentManager().ban(player, reason, "AntiVPN");
            }
            // LOG — just log, already logged in DB
            default -> plugin.getLogger().info("[AntiVPN] Logged VPN for " + player.getName() + " (" + ip + ")");
        }
    }

    /**
     * Sends a staff notification about a VPN detection.
     */
    private void notifyStaff(Player player, String ip, String country) {
        String msg = MessageUtil.parseMessages(
                plugin.getZeracConfig().getMessage("staff.vpn-detected",
                        "<prefix> <player> detected with VPN — <ip>"),
                Map.of("player", player.getName(), "ip", ip)
        );
        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("zerac.alerts"))
                .forEach(p -> plugin.getAdventure().player(p)
                        .sendMessage(MessageUtil.toComponent(msg)));
    }

    // ── Cache management ──────────────────────────────────────────────────────

    /**
     * Clears the IP cache. Called on reload.
     */
    public void clearCache() {
        cache.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getPlayerIP(Player player) {
        if (player.getAddress() == null) return null;
        return player.getAddress().getAddress().getHostAddress();
    }

    // ── Inner records ─────────────────────────────────────────────────────────

    /** Wraps a VPN check result with a timestamp for cache expiry. */
    private record CachedResult(VpnResult result, long timestamp) {
        boolean isExpired(int ttlSeconds) {
            return System.currentTimeMillis() - timestamp > ttlSeconds * 1000L;
        }
    }

    /** Result of a VPN/proxy check. */
    public record VpnResult(boolean isVpn, String country, String isp) {}
}
