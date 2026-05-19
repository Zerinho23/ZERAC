package me.zerinho23.zerac.checks.movement;

import me.zerinho23.zerac.ZeracPlugin;
import me.zerinho23.zerac.checks.AbstractCheck;
import me.zerinho23.zerac.models.PlayerData;
import me.zerinho23.zerac.utils.MathUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/**
 * NoSlow detection check.
 *
 * <p>Detects players who bypass the movement speed penalty applied when:
 * <ul>
 *   <li>Using (right-clicking) a bow
 *   <li>Eating/drinking food or potions
 *   <li>Using a shield
 *   <li>Sneaking (in some configurations)
 * </ul>
 *
 * <p>Legitimate penalty: speed is reduced to ~31% of normal while using items.
 * NoSlow mods remove this reduction, allowing full-speed movement during item use.
 *
 * <p>Detection: if the player is actively using an item that should slow them
 * and their horizontal speed exceeds the slowed threshold, flag them.
 */
public class NoSlow extends AbstractCheck {

    /** Expected speed ratio when using a slow item (31% of base sprint speed). */
    private static final double SLOW_SPEED_RATIO = 0.31;
    private static final double BASE_SPRINT_SPEED = 0.286;

    public NoSlow(ZeracPlugin plugin) {
        super(plugin, "NoSlow");
    }

    @Override
    public void process(PlayerData data) {
        if (!isEnabled()) return;

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Player must be actively using an item
        if (!player.isHandRaised()) {
            decayVL(data);
            return;
        }

        ItemStack activeItem = player.getActiveItem();
        if (activeItem == null || activeItem.getType() == Material.AIR) {
            decayVL(data);
            return;
        }

        // Check if the item type applies a slowness
        if (!isSlowItem(activeItem.getType())) {
            decayVL(data);
            return;
        }

        // Calculate expected max speed while using item
        double expectedMax = BASE_SPRINT_SPEED * SLOW_SPEED_RATIO;

        if (data.getLastLocation() == null) return;
        double actualSpeed = MathUtil.getHorizontalDistance(
                player.getLocation(), data.getLastLocation());

        if (actualSpeed > expectedMax + 0.01) {
            flag(data, String.format("Speed %.4f while using %s (max %.4f)",
                    actualSpeed, activeItem.getType().name(), expectedMax));
        } else {
            decayVL(data);
        }
    }

    private boolean isSlowItem(Material material) {
        return switch (material) {
            case BOW, CROSSBOW, SHIELD,
                 APPLE, BREAD, CARROT, BAKED_POTATO,
                 COOKED_BEEF, COOKED_CHICKEN, COOKED_COD,
                 COOKED_MUTTON, COOKED_PORKCHOP, COOKED_RABBIT,
                 COOKED_SALMON, COOKIE, DRIED_KELP, ENCHANTED_GOLDEN_APPLE,
                 GOLDEN_APPLE, GOLDEN_CARROT, MELON_SLICE,
                 MUSHROOM_STEW, PUMPKIN_PIE, RABBIT_STEW,
                 PORKCHOP, BEEF, CHICKEN, COD, SALMON, MUTTON,
                 SWEET_BERRIES, SUSPICIOUS_STEW,
                 POTION -> true;
            default -> false;
        };
    }

    @Override
    protected String getCategory() { return "movement"; }
}
