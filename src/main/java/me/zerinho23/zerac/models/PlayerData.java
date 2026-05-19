package me.zerinho23.zerac.models;

import lombok.Getter;
import lombok.Setter;
import me.zerinho23.zerac.checks.AbstractCheck;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-player runtime data container.
 *
 * <p>Holds all mutable state for a connected player: check VL totals,
 * movement snapshots, combat state, setback position, alert subscriptions, etc.
 *
 * <p>Accessed from both the main thread (Bukkit events) and the packet
 * thread (PacketEvents). Fields that are read/written from multiple threads
 * use thread-safe types. All others are main-thread-only.
 */
@Getter
@Setter
public class PlayerData {

    // ── Identity ──────────────────────────────────────────────────────────────

    private final UUID uuid;
    private final String name;
    private final Player player;

    // ── Check violations — thread-safe map ────────────────────────────────────

    /** Maps check class simple name → current violation level (VL). */
    private final Map<String, Double> violationLevels = new ConcurrentHashMap<>();

    /** Maps check class simple name → total number of flags since login. */
    private final Map<String, Integer> flagCounts = new ConcurrentHashMap<>();

    /** Maps check class simple name → last alert broadcast tick. */
    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();

    // ── Position / Movement tracking (main thread) ────────────────────────────

    private Location lastLocation;
    private Location setbackLocation;   // last safe position for setbacks

    /** Ground status in the previous tick. */
    private boolean wasOnGround;
    /** Current ground status. */
    private boolean onGround;
    /** How many consecutive ticks the player has been in the air. */
    private int airTicks;
    /** How many consecutive ticks the player has been on ground. */
    private int groundTicks;

    // ── Velocity tracking ─────────────────────────────────────────────────────

    /** Last velocity packet sent by the server to this player. */
    private double lastExpectedVelocityX;
    private double lastExpectedVelocityY;
    private double lastExpectedVelocityZ;
    /** Whether server sent a velocity packet this tick. */
    private boolean velocityExpected;
    /** Tick when last velocity packet was sent. */
    private long velocityExpectedTick;

    // ── Combat tracking ───────────────────────────────────────────────────────

    /** Tick of the last attack swing. */
    private long lastAttackTick;
    /** Attacks performed in the current second. */
    private int attacksThisSecond;
    /** Timestamp of the last attack in ms. */
    private long lastAttackMs;
    /** Recent CPS samples for AutoClicker detection. */
    private final Deque<Long> clickTimestamps = new ArrayDeque<>(50);

    // ── Rotation tracking ─────────────────────────────────────────────────────

    private float lastYaw;
    private float lastPitch;
    private float yawDelta;
    private float pitchDelta;
    /** GCD (greatest common divisor) accumulator for aim assist detection. */
    private double gcdAccumulator;

    // ── Timer / packet timing ─────────────────────────────────────────────────

    /** Server-side tick counter incremented every tick. */
    private long serverTick;
    /** Client position update count in the current second. */
    private int positionUpdates;
    /** Timestamp of the last position packet. */
    private long lastPositionTime;

    // ── Block interaction ─────────────────────────────────────────────────────

    private long lastBlockBreakMs;
    private long lastBlockPlaceMs;
    private int  blocksPlacedThisSecond;

    // ── State flags ───────────────────────────────────────────────────────────

    /** Whether the player is currently receiving staff alerts. */
    private final AtomicBoolean alertsEnabled = new AtomicBoolean(true);
    /** Whether the player has explicit debug mode enabled. */
    private boolean debugMode;
    /** Whether this player is exempt from checks (e.g. creative, vanished). */
    private boolean exempt;
    /** Whether the player is on a Bedrock client (Geyser/Floodgate). */
    private boolean bedrockPlayer;

    // ── Client info ───────────────────────────────────────────────────────────

    /** Client protocol version (provided by ViaVersion if present). */
    private int protocolVersion = -1;
    /** Client brand string (sent in plugin channel). */
    private String clientBrand = "vanilla";

    // ── Constructor ───────────────────────────────────────────────────────────

    public PlayerData(Player player) {
        this.player    = player;
        this.uuid      = player.getUniqueId();
        this.name      = player.getName();
        this.lastLocation = player.getLocation();
        this.setbackLocation = player.getLocation();
    }

    // ── VL helpers ────────────────────────────────────────────────────────────

    /**
     * Adds {@code amount} VL to the given check and returns the new total.
     *
     * @param checkName the check class simple name
     * @param amount    VL to add
     * @return new total VL
     */
    public double addVL(String checkName, double amount) {
        double current = violationLevels.getOrDefault(checkName, 0.0);
        double next    = current + amount;
        violationLevels.put(checkName, next);
        flagCounts.merge(checkName, 1, Integer::sum);
        return next;
    }

    /**
     * Reduces VL for a check by {@code amount}, clamped to 0.
     *
     * @param checkName the check class simple name
     * @param amount    VL to remove
     */
    public void decayVL(String checkName, double amount) {
        double current = violationLevels.getOrDefault(checkName, 0.0);
        violationLevels.put(checkName, Math.max(0.0, current - amount));
    }

    /**
     * Returns the current VL for a check.
     *
     * @param checkName the check class simple name
     * @return current VL (0.0 if no violations)
     */
    public double getVL(String checkName) {
        return violationLevels.getOrDefault(checkName, 0.0);
    }

    /**
     * Resets all violation data. Called on player quit or manual clear.
     */
    public void resetAll() {
        violationLevels.clear();
        flagCounts.clear();
        lastAlertTime.clear();
    }

    /**
     * Records a click timestamp for AutoClicker CPS calculation.
     * Keeps only the last 50 timestamps.
     */
    public void recordClick() {
        long now = System.currentTimeMillis();
        clickTimestamps.addLast(now);
        if (clickTimestamps.size() > 50) {
            clickTimestamps.pollFirst();
        }
    }

    /**
     * Calculates clicks-per-second from the recent timestamp buffer.
     *
     * @return estimated CPS (0 if insufficient data)
     */
    public double getCPS() {
        if (clickTimestamps.size() < 2) return 0;
        long oldest = clickTimestamps.peekFirst();
        long newest = clickTimestamps.peekLast();
        double seconds = (newest - oldest) / 1000.0;
        if (seconds <= 0) return 0;
        return (clickTimestamps.size() - 1) / seconds;
    }

    /**
     * Whether the player currently has alerts enabled.
     */
    public boolean isAlertsEnabled() {
        return alertsEnabled.get();
    }

    /**
     * Toggles alert subscription.
     *
     * @return new state after toggle
     */
    public boolean toggleAlerts() {
        return alertsEnabled.getAndSet(!alertsEnabled.get());
    }

    /**
     * Updates the setback position if the player is on the ground and safe.
     */
    public void updateSetback() {
        if (player != null && player.isOnGround() && !player.isInWater()) {
            setbackLocation = player.getLocation().clone();
        }
    }
}
