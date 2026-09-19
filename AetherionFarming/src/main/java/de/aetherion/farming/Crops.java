package de.aetherion.farming;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;

import java.util.Locale;
import java.util.Set;

public final class Crops {

    public static final long REGROW_TICKS = 20L * 18;

    /** Admin-placed immature seeds become harvestable after this delay. */
    public static final long FRESH_MATURE_TICKS = 20L * 2;

    public static final Set<Material> TYPES = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH,
            Material.TORCHFLOWER_CROP,
            Material.PITCHER_CROP,
            Material.MELON,
            Material.PUMPKIN,
            Material.SUGAR_CANE
    );

    private Crops() {
    }

    public static boolean isCrop(Material material) {
        return material != null && TYPES.contains(material);
    }

    /**
     * Materials that must be allowed to grow even when WorldGuard build/place is deny.
     * Includes stems so melon/pumpkin fruit can appear, and sugar cane height growth.
     */
    public static boolean allowsNaturalGrowth(Material material) {
        if (material == null) {
            return false;
        }
        if (isCrop(material)) {
            return true;
        }
        return material == Material.MELON_STEM
                || material == Material.ATTACHED_MELON_STEM
                || material == Material.PUMPKIN_STEM
                || material == Material.ATTACHED_PUMPKIN_STEM;
    }

    public static boolean isDungeonWorld(World world) {
        if (world == null) {
            return true;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("aedun_") || name.startsWith("ae_dun");
    }

    /** Personal / guild / test islands — permanent breaks, no crop seal-regen. */
    public static boolean isBuildWorld(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.equals("aether_islands")
                || name.equals("aether_guilds")
                || name.equals("aether_test")
                || name.startsWith("aether_test_");
    }

    public static boolean isMature(Block block) {
        if (block == null || !isCrop(block.getType())) {
            return false;
        }
        Material type = block.getType();
        if (type == Material.MELON || type == Material.PUMPKIN || type == Material.SUGAR_CANE) {
            return true;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable)) {
            return true;
        }
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    public static boolean canRestore(Block block, Material original) {
        if (block == null) {
            return false;
        }
        Material current = block.getType();
        return current.isAir() || current == original || isCrop(current);
    }
}
