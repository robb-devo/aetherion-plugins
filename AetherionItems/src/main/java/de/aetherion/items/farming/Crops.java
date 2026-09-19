package de.aetherion.items.farming;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Set;

public final class Crops {

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

    private static final Set<Material> PRIMARY_YIELDS = Set.of(
            Material.WHEAT,
            Material.CARROT,
            Material.POTATO,
            Material.BEETROOT,
            Material.NETHER_WART,
            Material.COCOA_BEANS,
            Material.SWEET_BERRIES,
            Material.MELON_SLICE,
            Material.MELON,
            Material.PUMPKIN,
            Material.SUGAR_CANE,
            Material.TORCHFLOWER
    );

    private Crops() {
    }

    public static boolean isCrop(Material material) {
        return material != null && TYPES.contains(material);
    }

    public static boolean isPrimaryYield(Material material) {
        return material != null && PRIMARY_YIELDS.contains(material);
    }

    public static boolean isDungeonWorld(World world) {
        if (world == null) {
            return true;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("aedun_") || name.startsWith("ae_dun");
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

    /**
     * Farm worlds auto-replant. Strip seed drops so they never inflate yield.
     * Pitcher pod is the crop product (kept); only seed-like drops are cleared.
     */
    public static ItemStack millSeeds(ItemStack drop) {
        if (drop == null) {
            return null;
        }
        Material type = drop.getType();
        if (type == Material.WHEAT_SEEDS
                || type == Material.BEETROOT_SEEDS
                || type == Material.TORCHFLOWER_SEEDS) {
            return null;
        }
        return drop;
    }

    /** One crop item per broken block before Fortune. Vanilla multi-drops are ignored. */
    public static ItemStack normalizeYield(ItemStack drop) {
        if (drop == null || drop.getType().isAir()) {
            return null;
        }
        ItemStack milled = millSeeds(drop);
        if (milled == null || milled.getType().isAir()) {
            return null;
        }
        if (!isPrimaryYield(milled.getType())) {
            return milled;
        }
        ItemStack out = milled.clone();
        out.setAmount(1);
        return out;
    }
}
