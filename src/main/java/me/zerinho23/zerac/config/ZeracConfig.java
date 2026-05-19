package me.zerinho23.zerac.config;

import lombok.Getter;
import me.zerinho23.zerac.ZeracPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * Thread-safe configuration wrapper for ZERAC.
 *
 * <p>All config values are cached on load() so reads don't touch disk.
 * Call reload() / load() to pick up changes from config.yml.
 */
@Getter
public class ZeracConfig {

    private final ZeracPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messages;

    // ── Plugin ────────────────────────────────────────────────────────────────
    private String prefix;
    private boolean debug;
    private boolean verbose;
    private String serverName;
    private String licenseKey;

    // ── Database ──────────────────────────────────────────────────────────────
    private String databaseType;  // SQLITE or MYSQL
    private String sqliteFile;
    private String mysqlHost;
    private int    mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int    mysqlPoolSize;

    // ── Redis ─────────────────────────────────────────────────────────────────
    private boolean redisEnabled;
    private String  redisHost;
    private int     redisPort;
    private String  redisPassword;
    private int     redisDatabase;
    private int     redisCacheTtl;

    // ── Blacklist ─────────────────────────────────────────────────────────────
    private boolean blacklistEnabled;
    private String  blacklistApiUrl;
    private String  blacklistApiToken;
    private int     blacklistSyncInterval;
    private boolean blacklistBlockOnJoin;
    private boolean blacklistLocalCache;

    // ── AntiVPN ───────────────────────────────────────────────────────────────
    private boolean       antiVPNEnabled;
    private String        antiVPNProvider;
    private String        antiVPNApiKey;
    private String        antiVPNAction;    // KICK, BAN, LOG
    private List<String>  antiVPNWhitelistCountries;
    private int           antiVPNCacheTtl;
    private List<String>  antiVPNWhitelistIps;

    // ── Punishments ───────────────────────────────────────────────────────────
    private String punishmentProvider;  // AUTO, LITEBANS, ADVANCEDBAN, VANILLA
    private int    defaultBanDuration;
    private String defaultBanReason;
    private int    vlWarn;
    private int    vlKick;
    private int    vlBan;
    private int    vlGlobalBan;

    // ── Webhooks ──────────────────────────────────────────────────────────────
    private boolean detectionWebhookEnabled;
    private String  detectionWebhookUrl;
    private int     detectionWebhookMinVl;
    private boolean banWebhookEnabled;
    private String  banWebhookUrl;
    private boolean staffWebhookEnabled;
    private String  staffWebhookUrl;
    private boolean vpnWebhookEnabled;
    private String  vpnWebhookUrl;

    // ── Alerts ────────────────────────────────────────────────────────────────
    private String alertFormat;
    private boolean alertHover;
    private String alertSound;
    private int    alertMinBroadcastVl;
    private int    alertCooldown;

    public ZeracConfig(ZeracPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Saves default configs from jar and loads all values into memory.
     */
    public void load() {
        // Save defaults if not present
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Load messages.yml
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Merge defaults from jar into the file on disk
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load messages defaults", e);
        }

        cacheValues();
    }

    /**
     * Reads all values from the loaded FileConfiguration and caches them.
     * Must be called after config is loaded.
     */
    private void cacheValues() {
        // Plugin
        prefix     = config.getString("plugin.prefix", "<gradient:#6C63FF:#A78BFA>[ZERAC]</gradient>");
        debug      = config.getBoolean("plugin.debug", false);
        verbose    = config.getBoolean("plugin.verbose", false);
        serverName = config.getString("plugin.server-name", "Server");
        licenseKey = config.getString("plugin.license-key", "");

        // Database
        databaseType    = config.getString("database.type", "SQLITE").toUpperCase();
        sqliteFile      = config.getString("database.sqlite.file", "zerac.db");
        mysqlHost       = config.getString("database.mysql.host", "localhost");
        mysqlPort       = config.getInt("database.mysql.port", 3306);
        mysqlDatabase   = config.getString("database.mysql.database", "zerac");
        mysqlUsername   = config.getString("database.mysql.username", "root");
        mysqlPassword   = config.getString("database.mysql.password", "");
        mysqlPoolSize   = config.getInt("database.mysql.pool-size", 10);

        // Redis
        redisEnabled   = config.getBoolean("redis.enabled", false);
        redisHost      = config.getString("redis.host", "localhost");
        redisPort      = config.getInt("redis.port", 6379);
        redisPassword  = config.getString("redis.password", "");
        redisDatabase  = config.getInt("redis.database", 0);
        redisCacheTtl  = config.getInt("redis.cache-ttl", 300);

        // Blacklist
        blacklistEnabled       = config.getBoolean("blacklist.enabled", true);
        blacklistApiUrl        = config.getString("blacklist.api-url", "https://api.zerac.gg");
        blacklistApiToken      = config.getString("blacklist.api-token", "");
        blacklistSyncInterval  = config.getInt("blacklist.sync-interval", 5);
        blacklistBlockOnJoin   = config.getBoolean("blacklist.block-on-join", true);
        blacklistLocalCache    = config.getBoolean("blacklist.local-cache", true);

        // AntiVPN
        antiVPNEnabled           = config.getBoolean("anti-vpn.enabled", true);
        antiVPNProvider          = config.getString("anti-vpn.provider", "proxycheck");
        antiVPNApiKey            = config.getString("anti-vpn.api-key", "");
        antiVPNAction            = config.getString("anti-vpn.action", "KICK").toUpperCase();
        antiVPNWhitelistCountries = config.getStringList("anti-vpn.whitelist-countries");
        antiVPNCacheTtl          = config.getInt("anti-vpn.cache-ttl", 3600);
        antiVPNWhitelistIps      = config.getStringList("anti-vpn.whitelist-ips");

        // Punishments
        punishmentProvider = config.getString("punishments.provider", "AUTO").toUpperCase();
        defaultBanDuration = config.getInt("punishments.default-ban-duration", 0);
        defaultBanReason   = config.getString("punishments.default-ban-reason", "Hacking - ZERAC AntiCheat");
        vlWarn             = config.getInt("punishments.vl-thresholds.warn", 50);
        vlKick             = config.getInt("punishments.vl-thresholds.kick", 75);
        vlBan              = config.getInt("punishments.vl-thresholds.ban", 100);
        vlGlobalBan        = config.getInt("punishments.vl-thresholds.global-ban", 150);

        // Webhooks
        detectionWebhookEnabled = config.getBoolean("webhooks.detection.enabled", false);
        detectionWebhookUrl     = config.getString("webhooks.detection.url", "");
        detectionWebhookMinVl   = config.getInt("webhooks.detection.min-vl", 50);
        banWebhookEnabled       = config.getBoolean("webhooks.ban.enabled", false);
        banWebhookUrl           = config.getString("webhooks.ban.url", "");
        staffWebhookEnabled     = config.getBoolean("webhooks.staff-alerts.enabled", false);
        staffWebhookUrl         = config.getString("webhooks.staff-alerts.url", "");
        vpnWebhookEnabled       = config.getBoolean("webhooks.vpn.enabled", false);
        vpnWebhookUrl           = config.getString("webhooks.vpn.url", "");

        // Alerts
        alertFormat         = config.getString("alerts.format", "<prefix> <player> flagged <check> VL:<vl>");
        alertHover          = config.getBoolean("alerts.hover", true);
        alertSound          = config.getString("alerts.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        alertMinBroadcastVl = config.getInt("alerts.min-broadcast-vl", 10);
        alertCooldown       = config.getInt("alerts.cooldown", 40);
    }

    /**
     * Convenience method — returns the messages.yml FileConfiguration.
     */
    public FileConfiguration getMessages() {
        return messages;
    }

    /**
     * Returns a message string from messages.yml, with a fallback.
     *
     * @param path     the dot-notation path in messages.yml
     * @param fallback default value if path is missing
     * @return the message string
     */
    public String getMessage(String path, String fallback) {
        return messages.getString(path, fallback);
    }

    /**
     * Returns a check's configuration section as a typesafe wrapper.
     *
     * @param checkName the check class name (e.g. "KillAura")
     * @return a CheckConfig wrapping the relevant config section
     */
    public CheckConfig getCheckConfig(String checkName) {
        return new CheckConfig(config.getConfigurationSection("checks." + checkName));
    }
}
