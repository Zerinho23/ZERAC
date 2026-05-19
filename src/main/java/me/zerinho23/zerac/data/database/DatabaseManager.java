package me.zerinho23.zerac.data.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.config.ZeracConfig;
import me.zerinho23.zerac.models.BanRecord;
import me.zerinho23.zerac.models.ViolationRecord;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Async database manager using HikariCP connection pooling.
 *
 * <p>Supports SQLite (for small/single-server setups) and MySQL
 * (for multi-server deployments). All public methods return
 * {@link CompletableFuture} — never block the main thread.
 *
 * <p>Schema tables:
 * <ul>
 *   <li>{@code zerac_players}    — UUID/name/first-join/last-seen
 *   <li>{@code zerac_violations} — individual flag records
 *   <li>{@code zerac_bans}       — ban records
 *   <li>{@code zerac_vpn_logs}   — VPN/proxy detection logs
 *   <li>{@code zerac_alerts}     — staff alert history
 * </ul>
 */
public class DatabaseManager {

    private final ZeracPlugin   plugin;
    private final ZeracConfig   config;
    private       HikariDataSource dataSource;

    /** Dedicated thread pool for DB operations — never uses the main thread. */
    private final ExecutorService executor = Executors.newFixedThreadPool(4,
            r -> {
                Thread t = new Thread(r, "ZERAC-DB");
                t.setDaemon(true);
                return t;
            });

    public DatabaseManager(ZeracPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getZeracConfig();
    }

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Opens the connection pool and creates all tables.
     * Runs synchronously during plugin enable.
     */
    public void init() {
        HikariConfig hikari = new HikariConfig();

        if (config.getDatabaseType().equalsIgnoreCase("MYSQL")) {
            hikari.setJdbcUrl("jdbc:mysql://" + config.getMysqlHost()
                    + ":" + config.getMysqlPort()
                    + "/" + config.getMysqlDatabase()
                    + "?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&characterEncoding=UTF-8&autoReconnect=true");
            hikari.setUsername(config.getMysqlUsername());
            hikari.setPassword(config.getMysqlPassword());
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setMaximumPoolSize(config.getMysqlPoolSize());
        } else {
            // SQLite
            File dbFile = new File(plugin.getDataFolder(), config.getSqliteFile());
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1); // SQLite is single-writer
            hikari.addDataSourceProperty("journal_mode", "WAL");
            hikari.addDataSourceProperty("synchronous", "NORMAL");
        }

        hikari.setPoolName("ZERAC-Pool");
        hikari.setConnectionTimeout(30_000);
        hikari.setLeakDetectionThreshold(60_000);
        hikari.setMaxLifetime(config.getDatabaseType().equalsIgnoreCase("MYSQL")
                ? 1_800_000 : 600_000);

        this.dataSource = new HikariDataSource(hikari);
        createTables();
        plugin.getLogger().info("Database connected (" + config.getDatabaseType() + ").");
    }

    /** Creates all required tables if they don't exist. */
    private void createTables() {
        boolean isMySQL = config.getDatabaseType().equalsIgnoreCase("MYSQL");
        String auto     = isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        String text     = isMySQL ? "VARCHAR(255)" : "TEXT";

        String[] ddl = {
            // Players table
            """
            CREATE TABLE IF NOT EXISTS zerac_players (
                uuid        %s  NOT NULL PRIMARY KEY,
                name        %s  NOT NULL,
                first_join  BIGINT NOT NULL,
                last_seen   BIGINT NOT NULL,
                total_flags INT DEFAULT 0,
                total_bans  INT DEFAULT 0
            )
            """.formatted(text, text),

            // Violations table
            """
            CREATE TABLE IF NOT EXISTS zerac_violations (
                id          INTEGER PRIMARY KEY %s,
                uuid        %s  NOT NULL,
                check_name  %s  NOT NULL,
                vl          DOUBLE NOT NULL,
                detail      TEXT,
                server      %s,
                timestamp   BIGINT NOT NULL,
                FOREIGN KEY (uuid) REFERENCES zerac_players(uuid)
            )
            """.formatted(auto, text, text, text),

            // Bans table
            """
            CREATE TABLE IF NOT EXISTS zerac_bans (
                id          INTEGER PRIMARY KEY %s,
                uuid        %s  NOT NULL,
                name        %s  NOT NULL,
                reason      TEXT NOT NULL,
                check_name  %s,
                banned_by   %s  NOT NULL DEFAULT 'ZERAC',
                server      %s,
                duration    BIGINT DEFAULT 0,
                timestamp   BIGINT NOT NULL,
                active      BOOLEAN DEFAULT TRUE,
                global      BOOLEAN DEFAULT FALSE
            )
            """.formatted(auto, text, text, text, text, text),

            // VPN logs table
            """
            CREATE TABLE IF NOT EXISTS zerac_vpn_logs (
                id          INTEGER PRIMARY KEY %s,
                uuid        %s  NOT NULL,
                name        %s  NOT NULL,
                ip          %s  NOT NULL,
                provider    %s,
                country     %s,
                action      %s  NOT NULL,
                timestamp   BIGINT NOT NULL
            )
            """.formatted(auto, text, text, text, text, text, text),

            // Alerts table
            """
            CREATE TABLE IF NOT EXISTS zerac_alerts (
                id          INTEGER PRIMARY KEY %s,
                uuid        %s  NOT NULL,
                name        %s  NOT NULL,
                check_name  %s  NOT NULL,
                vl          DOUBLE NOT NULL,
                server      %s,
                timestamp   BIGINT NOT NULL
            )
            """.formatted(auto, text, text, text, text)
        };

        try (Connection conn = dataSource.getConnection()) {
            for (String sql : ddl) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables!", e);
            throw new RuntimeException("Database table creation failed", e);
        }
    }

    // ── Player operations ─────────────────────────────────────────────────────

    /**
     * Creates or updates a player record. Called async on join.
     *
     * @param uuid player UUID
     * @param name player name
     * @return future that resolves when complete
     */
    public CompletableFuture<Void> upsertPlayer(UUID uuid, String name) {
        return CompletableFuture.runAsync(() -> {
            boolean isMySQL = config.getDatabaseType().equalsIgnoreCase("MYSQL");
            String sql = isMySQL
                    ? "INSERT INTO zerac_players (uuid, name, first_join, last_seen) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE name=?, last_seen=?"
                    : "INSERT OR REPLACE INTO zerac_players (uuid, name, first_join, last_seen) VALUES (?, ?, COALESCE((SELECT first_join FROM zerac_players WHERE uuid=?), ?), ?)";

            long now = System.currentTimeMillis();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (isMySQL) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, name);
                    ps.setLong(3, now);
                    ps.setLong(4, now);
                    ps.setString(5, name);
                    ps.setLong(6, now);
                } else {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, name);
                    ps.setString(3, uuid.toString());
                    ps.setLong(4, now);
                    ps.setLong(5, now);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to upsert player " + name, e);
            }
        }, executor);
    }

    // ── Violation operations ──────────────────────────────────────────────────

    /**
     * Saves a violation record asynchronously.
     *
     * @param record the violation to persist
     * @return future completing when saved
     */
    public CompletableFuture<Void> saveViolation(ViolationRecord record) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO zerac_violations (uuid, check_name, vl, detail, server, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, record.getUuid().toString());
                ps.setString(2, record.getCheckName());
                ps.setDouble(3, record.getVl());
                ps.setString(4, record.getDetail());
                ps.setString(5, record.getServer());
                ps.setLong(6, record.getTimestamp());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save violation", e);
            }
        }, executor);
    }

    /**
     * Returns recent violations for a player (last 50).
     *
     * @param uuid player UUID
     * @return future with list of violations
     */
    public CompletableFuture<List<ViolationRecord>> getViolations(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<ViolationRecord> records = new ArrayList<>();
            String sql = "SELECT * FROM zerac_violations WHERE uuid=? ORDER BY timestamp DESC LIMIT 50";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        records.add(new ViolationRecord(
                                uuid,
                                rs.getString("check_name"),
                                rs.getDouble("vl"),
                                rs.getString("detail"),
                                rs.getString("server"),
                                rs.getLong("timestamp")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get violations for " + uuid, e);
            }
            return records;
        }, executor);
    }

    // ── Ban operations ────────────────────────────────────────────────────────

    /**
     * Saves a ban record to the database.
     *
     * @param record the ban record to persist
     * @return future completing when saved, with the generated ban ID
     */
    public CompletableFuture<Long> saveBan(BanRecord record) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO zerac_bans (uuid, name, reason, check_name, banned_by, server, duration, timestamp, active, global) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, record.getUuid().toString());
                ps.setString(2, record.getName());
                ps.setString(3, record.getReason());
                ps.setString(4, record.getCheckName());
                ps.setString(5, record.getBannedBy());
                ps.setString(6, record.getServer());
                ps.setLong(7, record.getDuration());
                ps.setLong(8, record.getTimestamp());
                ps.setBoolean(9, true);
                ps.setBoolean(10, record.isGlobal());
                ps.executeUpdate();
                try (ResultSet gen = ps.getGeneratedKeys()) {
                    if (gen.next()) return gen.getLong(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save ban for " + record.getName(), e);
            }
            return -1L;
        }, executor);
    }

    /**
     * Saves a VPN detection log asynchronously.
     */
    public CompletableFuture<Void> saveVpnLog(UUID uuid, String name, String ip,
                                              String provider, String country, String action) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO zerac_vpn_logs (uuid, name, ip, provider, country, action, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, ip);
                ps.setString(4, provider);
                ps.setString(5, country);
                ps.setString(6, action);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save VPN log", e);
            }
        }, executor);
    }

    /**
     * Returns aggregate statistics for the plugin.
     *
     * @return future with int array: [total_flags, total_bans, total_players]
     */
    public CompletableFuture<int[]> getStats() {
        return CompletableFuture.supplyAsync(() -> {
            int[] stats = {0, 0, 0};
            try (Connection conn = dataSource.getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM zerac_violations")) {
                        if (rs.next()) stats[0] = rs.getInt(1);
                    }
                    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM zerac_bans WHERE active=TRUE")) {
                        if (rs.next()) stats[1] = rs.getInt(1);
                    }
                    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM zerac_players")) {
                        if (rs.next()) stats[2] = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get stats", e);
            }
            return stats;
        }, executor);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Shuts down the connection pool gracefully.
     * Called from {@link ZeracPlugin#onDisable()}.
     */
    public void close() {
        executor.shutdown();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed.");
        }
    }

    /**
     * Returns a raw connection for ad-hoc queries.
     * Caller MUST close the connection in a try-with-resources block.
     *
     * @return a JDBC Connection from the pool
     * @throws SQLException if the pool is exhausted or closed
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
