package me.zerinho23.zerac;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import me.zerinho23.zerac.alerts.AlertManager;
import me.zerinho23.zerac.api.ZeracAPI;
import me.zerinho23.zerac.checks.CheckRegistry;
import me.zerinho23.zerac.commands.ZeracCommand;
import me.zerinho23.zerac.config.ZeracConfig;
import me.zerinho23.zerac.data.cache.PlayerDataCache;
import me.zerinho23.zerac.data.database.DatabaseManager;
import me.zerinho23.zerac.gui.GuiManager;
import me.zerinho23.zerac.listeners.PacketListener;
import me.zerinho23.zerac.listeners.PlayerJoinLeaveListener;
import me.zerinho23.zerac.managers.AntiVPNManager;
import me.zerinho23.zerac.managers.BlacklistManager;
import me.zerinho23.zerac.managers.PunishmentManager;
import me.zerinho23.zerac.managers.WebhookManager;
import me.zerinho23.zerac.utils.MessageUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * ZERAC AntiCheat - Main plugin entry point.
 *
 * <p>Initializes all subsystems in a specific order to ensure correct
 * dependency resolution. All heavy I/O operations run asynchronously.
 *
 * <p>Subsystem initialization order:
 * <ol>
 *   <li>PacketEvents (packet interception layer)
 *   <li>Config &amp; Messages
 *   <li>Database (async)
 *   <li>Cache
 *   <li>Managers (AntiVPN, Blacklist, Punishment, Webhook)
 *   <li>Check Registry
 *   <li>Alert Manager
 *   <li>Listeners &amp; Commands
 *   <li>GUI
 *   <li>Public API exposure
 * </ol>
 */
@Getter
public final class ZeracPlugin extends JavaPlugin {

    /** Singleton instance — use ZeracPlugin.getInstance() for API access. */
    private static ZeracPlugin instance;

    // ── Core Infrastructure ──────────────────────────────────────────────────

    /** Adventure platform for modern component-based text handling. */
    private BukkitAudiences adventure;

    /** Plugin configuration wrapper (thread-safe reads). */
    private ZeracConfig zeracConfig;

    // ── Data Layer ────────────────────────────────────────────────────────────

    /** Async database connection pool (HikariCP). */
    private DatabaseManager databaseManager;

    /** In-memory player data cache — avoids repeated DB hits. */
    private PlayerDataCache playerDataCache;

    // ── Feature Managers ─────────────────────────────────────────────────────

    /** Manages VPN/proxy detection with IP caching and rate limiting. */
    private AntiVPNManager antiVPNManager;

    /** Manages the global UUID blacklist with API synchronization. */
    private BlacklistManager blacklistManager;

    /** Handles punishment routing: LiteBans → AdvancedBan → Vanilla. */
    private PunishmentManager punishmentManager;

    /** Sends rich Discord webhook payloads for detections and bans. */
    private WebhookManager webhookManager;

    /** Registry of all active anticheat checks. */
    private CheckRegistry checkRegistry;

    /** Broadcasts formatted alerts to staff with metadata hover. */
    private AlertManager alertManager;

    /** Inventory-based GUI for admin monitoring. */
    private GuiManager guiManager;

    /** Public API instance for third-party integrations. */
    private ZeracAPI api;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onLoad() {
        instance = this;

        // PacketEvents must be initialized in onLoad() — before onEnable()
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(false)          // no unnecessary re-encoding
                .checkForUpdates(false)            // disable auto-update checks
                .bStats(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║   ZERAC AntiCheat v" + getDescription().getVersion() + "           ║");
        getLogger().info("║   Starting up...                     ║");
        getLogger().info("╚══════════════════════════════════════╝");

        try {
            // 1. Adventure — text component platform
            this.adventure = BukkitAudiences.create(this);

            // 2. Configuration
            this.zeracConfig = new ZeracConfig(this);
            this.zeracConfig.load();

            // 3. Message utility (static helper, requires adventure)
            MessageUtil.init(this);

            // 4. Database (synchronous init, async operations internally)
            this.databaseManager = new DatabaseManager(this);
            this.databaseManager.init();

            // 5. Player data cache
            this.playerDataCache = new PlayerDataCache(this);

            // 6. Feature managers
            this.webhookManager    = new WebhookManager(this);
            this.punishmentManager = new PunishmentManager(this);
            this.antiVPNManager    = new AntiVPNManager(this);
            this.blacklistManager  = new BlacklistManager(this);
            this.blacklistManager.startSyncTask();

            // 7. Check registry — registers all checks
            this.checkRegistry = new CheckRegistry(this);
            this.checkRegistry.registerAll();

            // 8. Alert manager
            this.alertManager = new AlertManager(this);

            // 9. Listeners
            getServer().getPluginManager().registerEvents(new PlayerJoinLeaveListener(this), this);

            // 10. PacketEvents — must be initialized after all managers
            PacketEvents.getAPI().getEventManager().registerListener(new PacketListener(this));
            PacketEvents.getAPI().init();

            // 11. Commands
            ZeracCommand cmd = new ZeracCommand(this);
            var cmdMeta = getServer().getCommandMap().getCommand("zerac");
            if (cmdMeta != null) {
                cmdMeta.setExecutor(cmd);
                cmdMeta.setTabCompleter(cmd);
            }

            // 12. GUI manager
            this.guiManager = new GuiManager(this);

            // 13. Public API
            this.api = new ZeracAPI(this);

            long elapsed = System.currentTimeMillis() - start;
            getLogger().info("ZERAC initialized successfully in " + elapsed + "ms.");
            getLogger().info("Checks loaded: " + checkRegistry.getCheckCount());
            getLogger().info("Database: " + zeracConfig.getDatabaseType());
            getLogger().info("AntiVPN: " + (zeracConfig.isAntiVPNEnabled() ? "ENABLED" : "DISABLED"));
            getLogger().info("Global Blacklist: " + (zeracConfig.isBlacklistEnabled() ? "ENABLED" : "DISABLED"));

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "ZERAC failed to initialize! Disabling plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("ZERAC is shutting down...");

        // Unregister packet listeners before everything else
        try {
            PacketEvents.getAPI().terminate();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error terminating PacketEvents", e);
        }

        // Save any pending player data
        if (playerDataCache != null) {
            playerDataCache.saveAll();
        }

        // Close database connections
        if (databaseManager != null) {
            databaseManager.close();
        }

        // Close Adventure platform
        if (adventure != null) {
            adventure.close();
        }

        getLogger().info("ZERAC shutdown complete. Goodbye!");
    }

    /**
     * Reloads all configuration files and restarts affected managers.
     * Runs on the main thread — config reads are fast and safe.
     */
    public void reload() {
        zeracConfig.load();
        MessageUtil.reload();
        checkRegistry.reloadAll();
        blacklistManager.clearCache();
        antiVPNManager.clearCache();
        getLogger().info("ZERAC reloaded successfully.");
    }

    /**
     * Returns the singleton plugin instance.
     *
     * @return the ZeracPlugin instance
     */
    public static ZeracPlugin getInstance() {
        return instance;
    }
}
