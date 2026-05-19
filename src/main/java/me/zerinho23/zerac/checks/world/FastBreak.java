package me.zerinho23.zerac.checks.world;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * FastBreak detection check.
 *
 * <p>Detects players who break blocks faster than the server-calculated
 * expected break time.
 *
 * <p>Expected break time is calculated from:
 * <ul>
 *   <li>Block hardness
 *   <li>Tool type (correct vs. incorrect tool)
 *   <li>Efficiency enchantment level
 *   <li>Haste potion effect
 *   <li>Aqua Affinity (underwater penalty)
 * </ul>
 *
 * <p>A configurable speed percent ({@code max-speed-percent}) allows a margin
 * before flagging, absorbing minor timing differences.
 */
public class FastBreak extends AbstractCheck {

    private final double maxSpeedPercent;

    /** Tracks the timestamp and target block when a player starts digging. */
    private long   breakStartMs = 0L;
    private Material targetMaterial = null;

    public FastBreak(ZeracPlugin plugin) {
        super(plugin, "FastBreak");
        this.maxSpeedPercent = config.getDouble("max-speed-percent", 0.85);
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Exempt creative players
        if (player.getGameMode() == GameMode.CREATIVE) return;

        long lastBreak = data.getLastBlockBreakMs();
        if (lastBreak <= 0 || breakStartMs <= 0) return;

        // Only evaluate when a block was just broken (within last 2 ticks)
        if (System.currentTimeMillis() - lastBreak > 100) return;

        if (targetMaterial == null) return;

        // Calculate expected break time
        double expectedMs = getExpectedBreakTimeMs(player, targetMaterial);
        if (expectedMs <= 0) return;

        double actualMs  = lastBreak - breakStartMs;
        double ratio     = actualMs / expectedMs;

        if (ratio < maxSpeedPercent) {
            flag(data, String.format("Break ratio: %.2f (min %.2f) — %s",
                    ratio, maxSpeedPercent, targetMaterial.name()));
        } else {
            decayVL(data);
        }

        breakStartMs = 0L;
        targetMaterial = null;
    }

    /**
     * Calculates the expected break time in milliseconds for a given material
     * with the player's current tool and effects.
     *
     * <p>Formula from wiki.vg: ticks = ceil(hardness * divisor / speedMultiplier)
     *
     * @param player   the breaking player
     * @param material the block material
     * @return expected break time in milliseconds
     */
    private double getExpectedBreakTimeMs(Player player, Material material) {
        // Get block hardness via Material
        float hardness = material.getHardness();
        if (hardness < 0) return -1; // unbreakable (bedrock, etc.)
        if (hardness == 0) return 0; // instant break (flowers, etc.)

        double speedMultiplier = 1.0;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool != null && tool.getType() != Material.AIR) {
            // Efficiency enchantment
            int eff = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
            if (eff > 0) {
                speedMultiplier += eff * eff + 1;
            }
        }

        // Haste potion
        var haste = player.getPotionEffect(PotionEffectType.HASTE);
        if (haste != null) {
            speedMultiplier *= 1.0 + (0.2 * (haste.getAmplifier() + 1));
        }

        // Mining fatigue
        var fatigue = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        if (fatigue != null) {
            speedMultiplier *= Math.pow(0.3, fatigue.getAmplifier() + 1);
        }

        // Underwater penalty (no Aqua Affinity)
        if (player.isInWater()) {
            var aqua = tool == null ? null : tool.getEnchantmentLevel(Enchantment.AQUA_AFFINITY);
            if (aqua == null || aqua == 0) speedMultiplier /= 5.0;
        }

        double ticks = Math.ceil(hardness * 30.0 / speedMultiplier);
        return ticks * 50.0; // ticks → ms
    }

    @Override
    protected String getCategory() { return "world"; }
}
