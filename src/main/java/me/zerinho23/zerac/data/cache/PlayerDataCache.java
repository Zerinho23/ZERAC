package me.zerinho23.zerac.data.cache;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.models.PlayerData;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache for per-player runtime data.
 *
 * <p>One {@link PlayerData} entry exists for each connected player.
 * Entries are created on join and removed on quit.
 * All check and alert code uses this cache — never Bukkit's player list.
 */
public class PlayerDataCache {

    private final ZeracPlugin plugin;

    /** UUID → PlayerData map. ConcurrentHashMap for thread-safe access. */
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataCache(ZeracPlugin plugin) {
        this.plugin = plugin;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * Creates a new PlayerData entry for the given player and adds it to cache.
     *
     * @param player the player to create data for
     * @return the new PlayerData instance
     */
    public PlayerData create(Player player) {
        PlayerData data = new PlayerData(player);
        cache.put(player.getUniqueId(), data);
        return data;
    }

    /**
     * Returns the PlayerData for a given UUID, or null if not cached.
     *
     * @param uuid player UUID
     * @return cached data, or null
     */
    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Returns the PlayerData for a given player, or null if not cached.
     *
     * @param player the player
     * @return cached data, or null
     */
    public PlayerData get(Player player) {
        return cache.get(player.getUniqueId());
    }

    /**
     * Removes the player's data from cache (called on quit).
     *
     * @param uuid player UUID
     */
    public void remove(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Returns all currently cached PlayerData entries.
     *
     * @return collection of all active player data
     */
    public Collection<PlayerData> getAll() {
        return cache.values();
    }

    /**
     * Saves all player data to the database asynchronously.
     * Called during plugin shutdown.
     */
    public void saveAll() {
        for (PlayerData data : cache.values()) {
            plugin.getDatabaseManager()
                    .upsertPlayer(data.getUuid(), data.getName());
        }
    }

    /**
     * Returns the number of currently cached players.
     *
     * @return cache size
     */
    public int size() {
        return cache.size();
    }

    /**
     * Returns whether a UUID is currently in the cache.
     *
     * @param uuid player UUID
     * @return true if cached
     */
    public boolean contains(UUID uuid) {
        return cache.containsKey(uuid);
    }
}
