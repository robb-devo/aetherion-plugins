package de.aetherion.mining;

import org.bukkit.Material;

import java.util.Set;

/**
 * Shared-world mining allowlist — stone, ores, and dense mineral blocks only.
 * Diorite / andesite / granite / wool / slime stay protected.
 */
public final class MiningBlocks {

    public static final Set<Material> TYPES = Set.of(
            Material.STONE,
            Material.DEEPSLATE,

            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.COPPER_ORE,
            Material.GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.LAPIS_ORE,
            Material.DIAMOND_ORE,
            Material.EMERALD_ORE,

            Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.DEEPSLATE_EMERALD_ORE,

            Material.ANCIENT_DEBRIS,
            Material.NETHER_QUARTZ_ORE,
            Material.NETHER_GOLD_ORE,

            Material.AMETHYST_CLUSTER,

            Material.COAL_BLOCK,
            Material.RAW_COPPER_BLOCK,
            Material.COPPER_BLOCK,
            Material.RAW_IRON_BLOCK,
            Material.IRON_BLOCK,
            Material.RAW_GOLD_BLOCK,
            Material.GOLD_BLOCK,
            Material.REDSTONE_BLOCK,
            Material.LAPIS_BLOCK,
            Material.DIAMOND_BLOCK,
            Material.EMERALD_BLOCK,
            Material.NETHERITE_BLOCK,
            Material.QUARTZ_BLOCK
    );

    private MiningBlocks() {
    }

    public static boolean allows(Material material) {
        return material != null && TYPES.contains(material);
    }
}
