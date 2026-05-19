package me.zerinho23.zerac.commands;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.BanRecord;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.MessageUtil;
import me.zerinho23.zerac.utils.TPSUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main command handler for {@code /zerac}.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code alerts}             — toggle personal alert feed
 *   <li>{@code debug <check>}      — toggle debug mode for a check
 *   <li>{@code gui}                — open the admin monitoring GUI
 *   <li>{@code reload}             — reload all configuration
 *   <li>{@code stats}              — display global statistics
 *   <li>{@code blacklist add|remove|list <player>} — manage global blacklist
 *   <li>{@code check <player>}     — display a player's current VL
 *   <li>{@code verbose}            — toggle verbose packet debug
 * </ul>
 */
public class ZeracCommand implements CommandExecutor, TabCompleter {

    private final ZeracPlugin plugin;

    private static final List<String> SUB_COMMANDS = List.of(
            "alerts", "debug", "gui", "reload", "stats",
            "blacklist", "check", "verbose", "help"
    );

    private static final List<String> BLACKLIST_ARGS = List.of("add", "remove", "list", "info");

    public ZeracCommand(ZeracPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("zerac.command")) {
            sender.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.no-permission",
                            "<red>No permission.")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "alerts"     -> handleAlerts(sender, args);
            case "debug"      -> handleDebug(sender, args);
            case "gui"        -> handleGui(sender, args);
            case "reload"     -> handleReload(sender);
            case "stats"      -> handleStats(sender);
            case "blacklist"  -> handleBlacklist(sender, args);
            case "check"      -> handleCheck(sender, args);
            case "verbose"    -> handleVerbose(sender);
            default           -> { sendHelp(sender); yield true; }
        };
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    private boolean handleAlerts(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.player-only", "<red>Players only.")));
            return true;
        }

        PlayerData data = plugin.getPlayerDataCache().get(player);
        if (data == null) return true;

        boolean newState = data.toggleAlerts();
        String msgKey = newState ? "staff.alerts-enabled" : "staff.alerts-disabled";
        player.sendMessage(MessageUtil.toComponent(
                plugin.getZeracConfig().getMessage(msgKey, newState ? "<green>Alerts ON" : "<red>Alerts OFF")));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zerac.debug")) {
            sender.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.no-permission", "<red>No permission.")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent("<red>Usage: /zerac debug <check>"));
            return true;
        }

        String checkName = args[1];
        AbstractCheck check = plugin.getCheckRegistry().getCheck(checkName);
        if (check == null) {
            sender.sendMessage(MessageUtil.toComponent("<red>Check not found: " + checkName));
            return true;
        }

        sender.sendMessage(MessageUtil.toComponent(
                plugin.getZeracConfig().getMessage("staff.debug-enabled",
                        "<green>Debug enabled for <check>")
                        .replace("<check>", checkName)));
        return true;
    }

    private boolean handleGui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.player-only", "<red>Players only.")));
            return true;
        }

        if (!player.hasPermission("zerac.gui")) {
            player.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.no-permission", "<red>No permission.")));
            return true;
        }

        plugin.getGuiManager().openMainGui(player);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("zerac.reload")) {
            sender.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.no-permission", "<red>No permission.")));
            return true;
        }

        long start = System.currentTimeMillis();
        plugin.reload();
        long elapsed = System.currentTimeMillis() - start;

        sender.sendMessage(MessageUtil.toComponent(
                plugin.getZeracConfig().getMessage("general.reload-success",
                        "<green>Config reloaded.")
                        + " <dark_gray>(" + elapsed + "ms)"));
        return true;
    }

    private boolean handleStats(CommandSender sender) {
        if (!sender.hasPermission("zerac.stats")) {
            sender.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.no-permission", "<red>No permission.")));
            return true;
        }

        plugin.getDatabaseManager().getStats().thenAccept(stats -> {
            String msg = plugin.getZeracConfig().getMessage("commands.stats",
                    "<gray>Stats not configured")
                    .replace("<tps>",        TPSUtil.getFormattedTPS())
                    .replace("<players>",    String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("<detections>", String.valueOf(stats[0]))
                    .replace("<bans>",       String.valueOf(stats[1]))
                    .replace("<uptime>",     getUptime())
                    .replace("<version>",    plugin.getDescription().getVersion());

            plugin.getServer().getScheduler().runTask(plugin,
                    () -> sender.sendMessage(MessageUtil.toComponent(msg)));
        });
        return true;
    }

    private boolean handleBlacklist(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zerac.blacklist.manage")) {
            sender.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.no-permission", "<red>No permission.")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent(
                    "<red>Usage: /zerac blacklist <add|remove|list> [player]"));
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(MessageUtil.toComponent("<red>Usage: /zerac blacklist add <player>"));
                    return true;
                }
                String targetName = args[2];
                Player target = Bukkit.getPlayer(targetName);
                UUID targetUuid = target != null ? target.getUniqueId() : null;
                String reason  = args.length > 3
                        ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                        : "Manually blacklisted by staff";

                if (targetUuid == null) {
                    sender.sendMessage(MessageUtil.toComponent(
                            plugin.getZeracConfig().getMessage("general.player-not-found",
                                    "<red>Player not found.")
                                    .replace("<player>", targetName)));
                    return true;
                }

                BanRecord record = BanRecord.builder()
                        .uuid(targetUuid)
                        .name(targetName)
                        .reason(reason)
                        .checkName("Manual")
                        .bannedBy(sender.getName())
                        .server(plugin.getZeracConfig().getServerName())
                        .duration(0)
                        .timestamp(System.currentTimeMillis())
                        .global(true)
                        .build();

                plugin.getBlacklistManager().addToBlacklist(record).thenAccept(id -> {
                    String msg = plugin.getZeracConfig().getMessage("commands.blacklist-added",
                            "<green>Added <player> to blacklist. ID: #<id>")
                            .replace("<player>", targetName)
                            .replace("<id>",     id);
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> sender.sendMessage(MessageUtil.toComponent(msg)));
                });
            }

            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(MessageUtil.toComponent("<red>Usage: /zerac blacklist remove <player>"));
                    return true;
                }
                String targetName = args[2];
                Player target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    sender.sendMessage(MessageUtil.toComponent(
                            plugin.getZeracConfig().getMessage("general.player-not-found",
                                    "<red>Player not found.").replace("<player>", targetName)));
                    return true;
                }

                plugin.getBlacklistManager().removeFromBlacklist(target.getUniqueId())
                        .thenAccept(success -> {
                    String msgKey = success ? "commands.blacklist-removed" : "commands.blacklist-not-found";
                    String msg    = plugin.getZeracConfig().getMessage(msgKey, "")
                            .replace("<player>", targetName);
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> sender.sendMessage(MessageUtil.toComponent(msg)));
                });
            }

            case "list" -> {
                Set<UUID> entries = plugin.getBlacklistManager().getCachedEntries();
                if (entries.isEmpty()) {
                    sender.sendMessage(MessageUtil.toComponent(
                            plugin.getZeracConfig().getMessage("commands.blacklist-empty",
                                    "<gray>Blacklist is empty.")));
                    return true;
                }
                sender.sendMessage(MessageUtil.toComponent(
                        plugin.getZeracConfig().getMessage("commands.blacklist-header", "")));
                int i = 1;
                for (UUID uuid : entries) {
                    String line = plugin.getZeracConfig().getMessage("commands.blacklist-entry", "  <index>. <uuid>")
                            .replace("<index>",  String.valueOf(i++))
                            .replace("<player>", "?")
                            .replace("<uuid>",   uuid.toString())
                            .replace("<reason>", "?");
                    sender.sendMessage(MessageUtil.toComponent(line));
                }
            }

            default -> sender.sendMessage(MessageUtil.toComponent(
                    "<red>Usage: /zerac blacklist <add|remove|list>"));
        }

        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent("<red>Usage: /zerac check <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.player-not-found",
                            "<red>Player not found.").replace("<player>", args[1])));
            return true;
        }

        PlayerData data = plugin.getPlayerDataCache().get(target);
        if (data == null) return true;

        sender.sendMessage(MessageUtil.toComponent(
                plugin.getZeracConfig().getMessage("commands.check-header",
                        "<newline><gradient:#6C63FF:#A78BFA><bold>  ZERAC — <player></bold></gradient><newline>")
                        .replace("<player>", target.getName())));

        boolean hasFlags = false;
        for (AbstractCheck check : plugin.getCheckRegistry().getAll()) {
            double vl  = data.getVL(check.getCheckName());
            int flags  = data.getFlagCounts().getOrDefault(check.getCheckName(), 0);
            if (vl <= 0 && flags == 0) continue;

            hasFlags = true;
            String line = plugin.getZeracConfig().getMessage("commands.check-entry",
                    "  <check>: VL <vl>/<max-vl> (flags: <flags>)")
                    .replace("<check>",  check.getCheckName())
                    .replace("<vl>",     String.format("%.1f", vl))
                    .replace("<max-vl>", String.valueOf((int) check.getMaxVl()))
                    .replace("<flags>",  String.valueOf(flags));
            sender.sendMessage(MessageUtil.toComponent(line));
        }

        if (!hasFlags) {
            sender.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("commands.check-clean",
                            "<green><player> has no active flags.")
                            .replace("<player>", target.getName())));
        }

        return true;
    }

    private boolean handleVerbose(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.player-only", "<red>Players only.")));
            return true;
        }

        if (!player.hasPermission("zerac.verbose")) {
            player.sendMessage(MessageUtil.toComponent(
                    plugin.getZeracConfig().getMessage("general.no-permission", "<red>No permission.")));
            return true;
        }

        player.sendMessage(MessageUtil.toComponent(
                "<yellow>Verbose mode toggled. (Not yet fully implemented in this build)"));
        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(SUB_COMMANDS, args[0]);
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "debug", "check"     -> getOnlinePlayerNames(args[1]);
                case "blacklist"          -> filterStartsWith(BLACKLIST_ARGS, args[1]);
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("blacklist")
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return getOnlinePlayerNames(args[2]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return plugin.getCheckRegistry().getAll().stream()
                    .map(AbstractCheck::getCheckName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.toComponent("""
                <newline>\
                <gradient:#6C63FF:#A78BFA><bold>  ZERAC AntiCheat — Commands</bold></gradient>
                <newline>\
                  <gray>/zerac <gold>alerts</gold>            <dark_gray>— Toggle alert feed
                  <gray>/zerac <gold>debug <check></gold>    <dark_gray>— Toggle check debug
                  <gray>/zerac <gold>gui</gold>               <dark_gray>— Open admin GUI
                  <gray>/zerac <gold>reload</gold>            <dark_gray>— Reload config
                  <gray>/zerac <gold>stats</gold>             <dark_gray>— Global statistics
                  <gray>/zerac <gold>blacklist</gold>         <dark_gray>— Manage blacklist
                  <gray>/zerac <gold>check <player></gold>   <dark_gray>— View player flags
                  <gray>/zerac <gold>verbose</gold>           <dark_gray>— Toggle verbose mode
                <newline>"""));
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getOnlinePlayerNames(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private String getUptime() {
        long ms      = System.currentTimeMillis() - ZeracPlugin.getInstance().getServer().getWorldContainer().lastModified();
        long seconds = (ms / 1000) % 60;
        long minutes = (ms / (1000 * 60)) % 60;
        long hours   = ms / (1000 * 60 * 60);
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }
}
