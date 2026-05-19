package me.zerinho23.zerac.managers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.config.ZeracConfig;
import me.zerinho23.zerac.models.BanRecord;
import me.zerinho23.zerac.utils.MessageUtil;
import okhttp3.*;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages the global UUID blacklist with REST API synchronization.
 *
 * <p>The blacklist is maintained on a central ZERAC API server and synced
 * locally at a configurable interval. Local cache allows instant lookups
 * without API latency on player join.
 *
 * <p>REST endpoints:
 * <ul>
 *   <li>{@code POST /ban}            — adds a UUID to the blacklist
 *   <li>{@code GET  /check/{uuid}}   — checks if a UUID is blacklisted
 *   <li>{@code POST /unban}          — removes a UUID from the blacklist
 * </ul>
 *
 * <p>All API requests include the {@code X-ZERAC-Token} header for authentication.
 */
public class BlacklistManager {

    private final ZeracPlugin  plugin;
    private final ZeracConfig  config;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Local UUID cache — reduces API calls to O(1) per join check. */
    private final Set<UUID> localCache = ConcurrentHashMap.newKeySet();

    /** Rate limiter: track last sync timestamp. */
    private long lastSync = 0L;

    public BlacklistManager(ZeracPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getZeracConfig();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    // Inject API token into every request
                    Request authenticated = chain.request().newBuilder()
                            .header("X-ZERAC-Token", config.getBlacklistApiToken())
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "ZERAC-Plugin/1.0")
                            .build();
                    return chain.proceed(authenticated);
                })
                .build();
    }

    // ── Periodic sync ─────────────────────────────────────────────────────────

    /**
     * Starts the periodic sync task. Pulls the full blacklist from the API
     * at the configured interval.
     */
    public void startSyncTask() {
        if (!config.isBlacklistEnabled()) return;

        long intervalTicks = config.getBlacklistSyncInterval() * 20L * 60L; // minutes → ticks
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                this::syncFromApi, 100L, intervalTicks);
    }

    /**
     * Pulls the full blacklist from the remote API and refreshes the local cache.
     */
    private void syncFromApi() {
        if (!config.isBlacklistEnabled()) return;
        if (config.getBlacklistApiToken().isBlank()) return;

        String url = config.getBlacklistApiUrl() + "/blacklist";
        Request req = new Request.Builder().url(url).get().build();

        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return;

            JsonNode root = mapper.readTree(resp.body().string());
            JsonNode list = root.get("uuids");
            if (list == null || !list.isArray()) return;

            localCache.clear();
            for (JsonNode node : list) {
                try {
                    localCache.add(UUID.fromString(node.asText()));
                } catch (IllegalArgumentException ignored) { /* malformed UUID */ }
            }
            lastSync = System.currentTimeMillis();

            if (config.isDebug()) {
                plugin.getLogger().info("[Blacklist] Synced " + localCache.size() + " entries from API.");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Blacklist] Sync failed: " + e.getMessage());
        }
    }

    // ── Join check ────────────────────────────────────────────────────────────

    /**
     * Checks a player's UUID against the blacklist on join.
     * Uses local cache first; falls back to API if cache is stale.
     * Runs asynchronously — kicks on main thread if blacklisted.
     *
     * @param player the joining player
     */
    public void checkOnJoin(Player player) {
        if (!config.isBlacklistEnabled() || !config.isBlacklistBlockOnJoin()) return;

        UUID uuid = player.getUniqueId();

        // Fast local cache check
        if (config.isBlacklistLocalCache() && localCache.contains(uuid)) {
            kickBlacklisted(player, "Global Blacklist");
            return;
        }

        // Async API check
        isBlacklisted(uuid).thenAccept(result -> {
            if (result && player.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> kickBlacklisted(player, "Global Blacklist"));
            }
        });
    }

    /**
     * Checks the API for a specific UUID.
     *
     * @param uuid the UUID to check
     * @return future with true if blacklisted
     */
    public CompletableFuture<Boolean> isBlacklisted(UUID uuid) {
        // Local cache hit
        if (localCache.contains(uuid)) {
            return CompletableFuture.completedFuture(true);
        }

        if (!config.isBlacklistEnabled() || config.getBlacklistApiToken().isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String url = config.getBlacklistApiUrl() + "/check/" + uuid;
            Request req = new Request.Builder().url(url).get().build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return false;
                JsonNode root = mapper.readTree(resp.body().string());
                boolean blacklisted = root.path("blacklisted").asBoolean(false);
                if (blacklisted) localCache.add(uuid); // cache positive results
                return blacklisted;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[Blacklist] Check failed for " + uuid, e);
                return false;
            }
        });
    }

    // ── Management ────────────────────────────────────────────────────────────

    /**
     * Adds a UUID to the global blacklist via the API.
     *
     * @param record the ban record to submit
     * @return future with the generated ban ID
     */
    public CompletableFuture<String> addToBlacklist(BanRecord record) {
        localCache.add(record.getUuid()); // optimistic local add

        return CompletableFuture.supplyAsync(() -> {
            ObjectNode body = mapper.createObjectNode();
            body.put("uuid",       record.getUuid().toString());
            body.put("name",       record.getName());
            body.put("reason",     record.getReason());
            body.put("check_name", record.getCheckName());
            body.put("server",     record.getServer());
            body.put("timestamp",  record.getTimestamp());

            RequestBody requestBody = RequestBody.create(
                    body.toString(), MediaType.parse("application/json"));
            Request req = new Request.Builder()
                    .url(config.getBlacklistApiUrl() + "/ban")
                    .post(requestBody)
                    .build();

            try (Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return "ERR";
                JsonNode root = mapper.readTree(resp.body().string());
                return root.path("id").asText("ERR");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[Blacklist] Failed to add " + record.getName(), e);
                return "ERR";
            }
        });
    }

    /**
     * Removes a UUID from the global blacklist via the API.
     *
     * @param uuid the UUID to unban
     * @return future with true if successful
     */
    public CompletableFuture<Boolean> removeFromBlacklist(UUID uuid) {
        localCache.remove(uuid);

        return CompletableFuture.supplyAsync(() -> {
            ObjectNode body = mapper.createObjectNode();
            body.put("uuid", uuid.toString());

            RequestBody requestBody = RequestBody.create(
                    body.toString(), MediaType.parse("application/json"));
            Request req = new Request.Builder()
                    .url(config.getBlacklistApiUrl() + "/unban")
                    .post(requestBody)
                    .build();

            try (Response resp = httpClient.newCall(req).execute()) {
                return resp.isSuccessful();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[Blacklist] Failed to unban " + uuid, e);
                return false;
            }
        });
    }

    // ── Kick ─────────────────────────────────────────────────────────────────

    private void kickBlacklisted(Player player, String reason) {
        String msg = MessageUtil.parseMessages(
                config.getMessage("punishment.blacklisted-join",
                        "You are globally blacklisted from ZERAC servers."),
                Map.of("reason", reason, "id", "N/A")
        );
        player.kick(MessageUtil.toComponent(msg));
    }

    // ── Cache management ──────────────────────────────────────────────────────

    /**
     * Clears the local cache — called on reload.
     */
    public void clearCache() {
        localCache.clear();
    }

    /**
     * Returns the current size of the local blacklist cache.
     */
    public int getCacheSize() {
        return localCache.size();
    }

    /**
     * Returns a snapshot of the local blacklist cache.
     */
    public Set<UUID> getCachedEntries() {
        return Set.copyOf(localCache);
    }
}
