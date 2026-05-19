package me.zerinho23.zerac.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Typesafe wrapper around a single check's configuration section.
 *
 * <p>Provides null-safe accessors with sensible defaults so checks
 * don't need to guard against missing config keys.
 */
public class CheckConfig {

    private final ConfigurationSection section;

    public CheckConfig(ConfigurationSection section) {
        this.section = section;
    }

    public boolean isEnabled()       { return get("enabled", true); }
    public boolean isExperimental()  { return get("experimental", false); }
    public boolean isDebug()         { return get("debug", false); }
    public boolean isSetback()       { return get("setback", true); }

    public int    getVlIncrement()   { return get("vl-increment", 10); }
    public int    getVlDecay()       { return get("vl-decay", 1); }
    public int    getMaxVl()         { return get("max-vl", 100); }
    public String getPunishment()    { return get("punishment", "BAN"); }

    public double getDouble(String key, double def) {
        return section != null ? section.getDouble(key, def) : def;
    }

    public int getInt(String key, int def) {
        return section != null ? section.getInt(key, def) : def;
    }

    public String getString(String key, String def) {
        return section != null ? section.getString(key, def) : def;
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String key, T def) {
        if (section == null) return def;
        Object val = section.get(key);
        if (val == null) return def;
        try {
            return (T) val;
        } catch (ClassCastException e) {
            return def;
        }
    }
}
