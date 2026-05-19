package me.zerinho23.zerac.models;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Immutable record representing a ban applied by ZERAC.
 *
 * <p>Used for both local (server-level) bans and global blacklist entries.
 */
@Getter
@Builder
public class BanRecord {

    private final UUID   uuid;
    private final String name;
    private final String reason;
    private final String checkName;
    private final String bannedBy;
    private final String server;
    /** Duration in milliseconds — 0 means permanent. */
    private final long   duration;
    private final long   timestamp;
    private final boolean global;

    /**
     * Returns a hex ban ID string derived from the record's timestamp.
     * Format: #A91D2F (uppercase hex, 6 chars).
     *
     * @return formatted ban ID string
     */
    public String getBanId() {
        return "#" + Long.toHexString(timestamp).toUpperCase().substring(0, 6);
    }

    /**
     * Returns true if this ban is permanent.
     */
    public boolean isPermanent() {
        return duration == 0;
    }

    /**
     * Returns true if this ban has expired (non-permanent bans only).
     */
    public boolean isExpired() {
        if (isPermanent()) return false;
        return System.currentTimeMillis() > timestamp + duration;
    }
}
