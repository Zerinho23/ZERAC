package me.zerinho23.zerac.listeners;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.models.PlayerData;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Core packet interception layer using PacketEvents.
 *
 * <p>Intercepts client-bound and server-bound packets to feed
 * data into the check system. Processing is minimal — only state
 * is updated here. Actual violation logic lives in each check.
 *
 * <p>Runs on the PacketEvents I/O thread — must NEVER call Bukkit API
 * methods that require the main thread. Write to PlayerData fields only.
 *
 * <p>Packets intercepted:
 * <ul>
 *   <li>{@code PLAYER_POSITION} — ground state, position update rate
 *   <li>{@code PLAYER_POSITION_AND_ROTATION} — ground + rotation state
 *   <li>{@code PLAYER_ROTATION} — rotation-only updates
 *   <li>{@code INTERACT_ENTITY} — attack detection for KillAura/AutoClicker
 *   <li>{@code ENTITY_VELOCITY} (outbound) — expected velocity tracking for Velocity check
 *   <li>{@code PLAYER_DIGGING} — FastBreak detection
 *   <li>{@code PLAYER_BLOCK_PLACEMENT} — FastPlace/Scaffold detection
 * </ul>
 */
public class PacketListener extends PacketListenerAbstract {

    private final ZeracPlugin plugin;

    public PacketListener(ZeracPlugin plugin) {
        super(PacketListenerPriority.LOW);
        this.plugin = plugin;
    }

    // ── Incoming packets (client → server) ────────────────────────────────────

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getUser() == null) return;

        UUID uuid = event.getUser().getUUID();
        if (uuid == null) return;

        PlayerData data = plugin.getPlayerDataCache().get(uuid);
        if (data == null || data.isExempt()) return;

        PacketType.Play.Client type = (PacketType.Play.Client) event.getPacketType();

        switch (type) {
            case PLAYER_POSITION                  -> handlePosition(event, data, false, false);
            case PLAYER_POSITION_AND_LOOK_EASY    -> handlePosition(event, data, false, true);
            case PLAYER_LOOK                      -> handleRotation(event, data);
            case INTERACT_ENTITY                  -> handleAttack(event, data);
            case PLAYER_DIGGING                   -> handleDigging(event, data);
            case PLAYER_BLOCK_PLACEMENT           -> handleBlockPlace(event, data);
            case FLYING                           -> handleFlying(event, data);
            default -> { /* not intercepted */ }
        }
    }

    // ── Outgoing packets (server → client) ────────────────────────────────────

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
            UUID uuid = event.getUser().getUUID();
            if (uuid == null) return;

            PlayerData data = plugin.getPlayerDataCache().get(uuid);
            if (data == null) return;

            // Track expected velocity for the Velocity check
            try {
                WrapperPlayServerEntityVelocity pkt = new WrapperPlayServerEntityVelocity(event);
                Player player = data.getPlayer();
                if (player == null) return;
                int entityId = SpigotReflectionUtil.getEntityId(player);

                if (pkt.getEntityId() == entityId) {
                    data.setLastExpectedVelocityX(pkt.getVelocity().x);
                    data.setLastExpectedVelocityY(pkt.getVelocity().y);
                    data.setLastExpectedVelocityZ(pkt.getVelocity().z);
                    data.setVelocityExpected(true);
                    data.setVelocityExpectedTick(data.getServerTick());
                }
            } catch (Exception ignored) { /* packet parse error */ }
        }
    }

    // ── Packet handlers ───────────────────────────────────────────────────────

    private void handlePosition(PacketReceiveEvent event, PlayerData data,
                                boolean onGround, boolean includesRotation) {
        long now = System.currentTimeMillis();

        // Track position update rate (for Timer check)
        data.setPositionUpdates(data.getPositionUpdates() + 1);
        data.setLastPositionTime(now);

        try {
            WrapperPlayClientPlayerPosition pkt = new WrapperPlayClientPlayerPosition(event);
            data.setOnGround(pkt.isOnGround());

            if (includesRotation) {
                WrapperPlayClientPlayerPositionAndRotation rotPkt =
                        new WrapperPlayClientPlayerPositionAndRotation(event);
                float yaw   = rotPkt.getYaw();
                float pitch = rotPkt.getPitch();
                data.setYawDelta(Math.abs(yaw - data.getLastYaw()));
                data.setPitchDelta(Math.abs(pitch - data.getLastPitch()));
                data.setLastYaw(yaw);
                data.setLastPitch(pitch);
            }
        } catch (Exception ignored) { /* parse error — skip */ }
    }

    private void handleRotation(PacketReceiveEvent event, PlayerData data) {
        try {
            WrapperPlayClientPlayerRotation pkt = new WrapperPlayClientPlayerRotation(event);
            float yaw   = pkt.getYaw();
            float pitch = pkt.getPitch();
            data.setYawDelta(Math.abs(yaw - data.getLastYaw()));
            data.setPitchDelta(Math.abs(pitch - data.getLastPitch()));
            data.setLastYaw(yaw);
            data.setLastPitch(pitch);
        } catch (Exception ignored) {}
    }

    private void handleAttack(PacketReceiveEvent event, PlayerData data) {
        try {
            WrapperPlayClientInteractEntity pkt = new WrapperPlayClientInteractEntity(event);
            if (pkt.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                data.setLastAttackTick(data.getServerTick());
                data.setLastAttackMs(System.currentTimeMillis());
                data.recordClick();
            }
        } catch (Exception ignored) {}
    }

    private void handleDigging(PacketReceiveEvent event, PlayerData data) {
        try {
            WrapperPlayClientPlayerDigging pkt = new WrapperPlayClientPlayerDigging(event);
            if (pkt.getAction() == WrapperPlayClientPlayerDigging.Action.FINISHED_DIGGING) {
                data.setLastBlockBreakMs(System.currentTimeMillis());
            }
        } catch (Exception ignored) {}
    }

    private void handleBlockPlace(PacketReceiveEvent event, PlayerData data) {
        data.setLastBlockPlaceMs(System.currentTimeMillis());
        data.setBlocksPlacedThisSecond(data.getBlocksPlacedThisSecond() + 1);
    }

    private void handleFlying(PacketReceiveEvent event, PlayerData data) {
        try {
            WrapperPlayClientPlayerFlying pkt = new WrapperPlayClientPlayerFlying(event);
            data.setOnGround(pkt.isOnGround());
        } catch (Exception ignored) {}
    }
}
