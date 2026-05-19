package me.zerinho23.zerac.gui;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.MessageUtil;
import me.zerinho23.zerac.utils.TPSUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory-based admin GUI for ZERAC monitoring.
 *
 * <p>Screens:
 * <ul>
 *   <li><b>Main menu</b>    — icons linking to sub-screens + TPS display
 *   <li><b>Players</b>      — list of online players with flag counts
 *   <li><b>Statistics</b>   — global detection/ban counters from DB
 *   <li><b>Blacklist</b>    — view cached global blacklist entries
 *   <li><b>Punishments</b>  — recent ban history
 * </ul>
 *
 * <p>All GUI interactions run on the main thread.
 * DB-backed screens fetch data async then open the inventory on the main thread.
 */
public class GuiManager implements Listener {

    private final ZeracPlugin plugin;

    /** Marker prefix stored in item display names to identify ZERAC GUI items. */
    private static final String GUI_MARKER = "§0ZERAC_GUI|";

    public GuiManager(ZeracPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ── Main menu ─────────────────────────────────────────────────────────────

    /**
     * Opens the ZERAC main admin GUI for a player.
     *
     * @param player the staff member to open the GUI for
     */
    public void openMainGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                MessageUtil.toComponent("<gradient:#6C63FF:#A78BFA><bold>ZERAC</bold></gradient> <gray>— Admin Panel"));

        // Fill border with glass panes
        fillBorder(inv, Material.PURPLE_STAINED_GLASS_PANE);

        // Navigation icons
        setItem(inv, 20, Material.PLAYER_HEAD,         "PLAYERS",     "<gold>Players",      "View online players and their flags");
        setItem(inv, 21, Material.BOOK,                "STATS",       "<aqua>Statistics",   "Global detection and ban statistics");
        setItem(inv, 22, Material.BARRIER,             "BLACKLIST",   "<red>Blacklist",     "View global blacklist (" + plugin.getBlacklistManager().getCacheSize() + " entries)");
        setItem(inv, 23, Material.IRON_SWORD,          "CHECKS",      "<yellow>Checks",     "View all registered checks and their status");
        setItem(inv, 24, Material.TNT,                 "PUNISHMENTS", "<dark_red>Punishments", "Recent punishment history");

        // TPS indicator
        double tps = TPSUtil.getTPS();
        Material tpsMaterial = tps > 18 ? Material.LIME_CONCRETE
                : tps > 15 ? Material.YELLOW_CONCRETE : Material.RED_CONCRETE;
        setItem(inv, 4, tpsMaterial, "TPS_INFO", "<gray>Server TPS",
                String.format("Current: %.1f TPS", tps),
                "Players online: " + Bukkit.getOnlinePlayers().size());

        // Close button
        setItem(inv, 49, Material.BARRIER, "CLOSE", "<red>Close", "Close this menu");

        player.openInventory(inv);
    }

    // ── Players screen ────────────────────────────────────────────────────────

    /**
     * Opens the player monitoring screen showing all online players.
     *
     * @param player the viewer
     */
    public void openPlayersGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                MessageUtil.toComponent("<gradient:#6C63FF:#A78BFA><bold>ZERAC</bold></gradient> <gray>— Players"));

        fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);
        setItem(inv, 49, Material.ARROW, "MAIN", "<gray>Back", "Return to main menu");

        int slot = 10;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 44) break;

            PlayerData data = plugin.getPlayerDataCache().get(online);
            int totalFlags = data != null
                    ? data.getFlagCounts().values().stream().mapToInt(Integer::intValue).sum()
                    : 0;

            List<String> lore = new ArrayList<>();
            lore.add("§7Ping: §f" + online.getPing() + "ms");
            lore.add("§7Flags: §e" + totalFlags);
            lore.add("");
            lore.add("§7Left-click to teleport");
            lore.add("§7Right-click to view checks");

            ItemStack skull = createSkull(online.getName(), lore);
            inv.setItem(slot++, skull);

            // Skip inventory border slots
            if ((slot + 1) % 9 == 0) slot += 2;
        }

        player.openInventory(inv);
    }

    // ── Checks screen ─────────────────────────────────────────────────────────

    /**
     * Opens the check registry screen.
     *
     * @param player the viewer
     */
    public void openChecksGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                MessageUtil.toComponent("<gradient:#6C63FF:#A78BFA><bold>ZERAC</bold></gradient> <gray>— Checks"));

        fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);
        setItem(inv, 49, Material.ARROW, "MAIN", "<gray>Back", "Return to main menu");

        int slot = 10;
        for (AbstractCheck check : plugin.getCheckRegistry().getAll()) {
            if (slot >= 44) break;

            Material mat = check.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE;
            String status = check.isEnabled() ? "<green>Enabled" : "<red>Disabled";

            setItem(inv, slot++, mat, "CHECK_" + check.getCheckName(),
                    "<gold>" + check.getCheckName(),
                    "Status: " + (check.isEnabled() ? "Enabled" : "Disabled"),
                    "Max VL: " + (int) check.getMaxVl(),
                    "Experimental: " + check.isExperimental(),
                    "Setback: " + check.isSetback());

            if ((slot + 1) % 9 == 0) slot += 2;
        }

        player.openInventory(inv);
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) return;

        // Check if this is a ZERAC GUI item
        String displayName = meta.hasDisplayName()
                ? net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .serialize(meta.displayName())
                : "";

        if (!displayName.startsWith(GUI_MARKER)) return;

        event.setCancelled(true);

        String action = displayName.substring(GUI_MARKER.length());

        switch (action) {
            case "CLOSE"       -> player.closeInventory();
            case "MAIN"        -> openMainGui(player);
            case "PLAYERS"     -> openPlayersGui(player);
            case "CHECKS"      -> openChecksGui(player);
            case "STATS"       -> openStatsGui(player);
            case "BLACKLIST"   -> openBlacklistGui(player);
            case "TPS_INFO"    -> { /* info only */ }
            default            -> { /* unknown action */ }
        }
    }

    // ── Statistics screen ─────────────────────────────────────────────────────

    private void openStatsGui(Player player) {
        plugin.getDatabaseManager().getStats().thenAccept(stats -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Inventory inv = Bukkit.createInventory(null, 27,
                        MessageUtil.toComponent("<gradient:#6C63FF:#A78BFA><bold>ZERAC</bold></gradient> <gray>— Statistics"));
                fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);

                setItem(inv, 10, Material.BOOK,            "STAT_FLAGS",   "<yellow>Total Detections", "Count: §e" + stats[0]);
                setItem(inv, 12, Material.IRON_SWORD,      "STAT_BANS",    "<red>Active Bans",         "Count: §c" + stats[1]);
                setItem(inv, 14, Material.PLAYER_HEAD,     "STAT_PLAYERS", "<aqua>Tracked Players",    "Count: §b" + stats[2]);
                setItem(inv, 16, Material.CLOCK,           "STAT_TPS",     "<green>Server TPS",        String.format("§a%.1f TPS", TPSUtil.getTPS()));
                setItem(inv, 22, Material.ARROW,           "MAIN",         "<gray>Back",               "Return to main menu");

                player.openInventory(inv);
            });
        });
    }

    // ── Blacklist screen ──────────────────────────────────────────────────────

    private void openBlacklistGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                MessageUtil.toComponent("<gradient:#6C63FF:#A78BFA><bold>ZERAC</bold></gradient> <gray>— Blacklist"));
        fillBorder(inv, Material.RED_STAINED_GLASS_PANE);
        setItem(inv, 49, Material.ARROW, "MAIN", "<gray>Back", "Return to main menu");

        int slot = 10;
        for (java.util.UUID uuid : plugin.getBlacklistManager().getCachedEntries()) {
            if (slot >= 44) break;
            setItem(inv, slot++, Material.PLAYER_HEAD, "BL_" + uuid, "<red>Blacklisted Player", "UUID: §c" + uuid.toString());
            if ((slot + 1) % 9 == 0) slot += 2;
        }

        if (plugin.getBlacklistManager().getCacheSize() == 0) {
            setItem(inv, 22, Material.LIME_CONCRETE, "EMPTY", "<green>Blacklist is Empty", "No entries cached.");
        }

        player.openInventory(inv);
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    /**
     * Creates a named, clickable GUI item with the ZERAC GUI marker embedded
     * in the display name (invisible via §0 color code).
     *
     * @param inv      target inventory
     * @param slot     inventory slot
     * @param material item material
     * @param action   action identifier
     * @param name     display name (MiniMessage)
     * @param loreLine lore lines
     */
    private void setItem(Inventory inv, int slot, Material material,
                         String action, String name, String... loreLine) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return;

        // Embed action in display name with invisible color
        meta.displayName(Component.text(GUI_MARKER + action)
                .append(MessageUtil.toComponent(name)));

        List<Component> lore = new ArrayList<>();
        for (String line : loreLine) {
            lore.add(MessageUtil.toComponent("<gray>" + line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private ItemStack createSkull(String playerName, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        meta.displayName(MessageUtil.toComponent("<gold>" + playerName));
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(Component.text(line));
        }
        meta.lore(loreComponents);
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
        skull.setItemMeta(meta);
        return skull;
    }

    private void fillBorder(Inventory inv, Material material) {
        int size = inv.getSize();
        ItemStack glass = new ItemStack(material);
        ItemMeta meta   = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.space());
            glass.setItemMeta(meta);
        }

        for (int i = 0; i < 9; i++) inv.setItem(i, glass);                 // top row
        for (int i = size - 9; i < size; i++) inv.setItem(i, glass);        // bottom row
        for (int i = 9; i < size - 9; i += 9) inv.setItem(i, glass);        // left col
        for (int i = 17; i < size - 9; i += 9) inv.setItem(i, glass);       // right col
    }
}
