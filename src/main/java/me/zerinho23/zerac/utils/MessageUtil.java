package me.zerinho23.zerac.utils;

import me.zerinho23.zerac.ZeracPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static utility for MiniMessage-based text operations.
 *
 * <p>Must be initialized with {@link #init(ZeracPlugin)} before use.
 * All methods are thread-safe.
 */
public final class MessageUtil {

    private static ZeracPlugin plugin;
    private static String      prefix;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Matches <placeholder> patterns for simple string replacement. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("<([^>]+)>");

    private MessageUtil() {}

    /**
     * Initializes the utility with the plugin instance.
     * Must be called once during plugin startup before any message methods.
     *
     * @param pluginInstance the plugin
     */
    public static void init(ZeracPlugin pluginInstance) {
        plugin = pluginInstance;
        reload();
    }

    /**
     * Reloads the cached prefix from config.
     */
    public static void reload() {
        if (plugin != null) {
            prefix = plugin.getZeracConfig().getPrefix();
        }
    }

    // ── Conversion ────────────────────────────────────────────────────────────

    /**
     * Parses a MiniMessage string into an Adventure {@link Component}.
     *
     * @param text MiniMessage-formatted string
     * @return parsed Component
     */
    public static Component toComponent(String text) {
        if (text == null || text.isBlank()) return Component.empty();
        return MM.deserialize(text.replace("<prefix>", prefix != null ? prefix : "[ZERAC]"));
    }

    /**
     * Parses a MiniMessage string with a map of placeholder substitutions.
     *
     * @param text MiniMessage-formatted string with <key> placeholders
     * @param placeholders map of key → value replacements
     * @return the string with all placeholders replaced
     */
    public static String parseMessages(String text, Map<String, String> placeholders) {
        if (text == null) return "";
        String result = text.replace("<prefix>", prefix != null ? prefix : "[ZERAC]");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }

    /**
     * Parses a MiniMessage string with placeholder substitutions and
     * converts it directly to a Component.
     *
     * @param text         MiniMessage-formatted string
     * @param placeholders key → value replacement map
     * @return parsed Component
     */
    public static Component parseAndConvert(String text, Map<String, String> placeholders) {
        return toComponent(parseMessages(text, placeholders));
    }

    /**
     * Builds an array of {@link TagResolver} from a string map,
     * for use with MiniMessage's resolver-based API.
     *
     * @param map key → value map
     * @return array of Placeholder resolvers
     */
    public static TagResolver[] resolvers(Map<String, String> map) {
        List<TagResolver> resolvers = new ArrayList<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            resolvers.add(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }
        return resolvers.toArray(new TagResolver[0]);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns the cached prefix string (MiniMessage format).
     *
     * @return prefix
     */
    public static String getPrefix() {
        return prefix != null ? prefix : "<gradient:#6C63FF:#A78BFA>[ZERAC]</gradient>";
    }

    /**
     * Returns the MiniMessage instance.
     *
     * @return MiniMessage singleton
     */
    public static MiniMessage getMiniMessage() {
        return MM;
    }
}
