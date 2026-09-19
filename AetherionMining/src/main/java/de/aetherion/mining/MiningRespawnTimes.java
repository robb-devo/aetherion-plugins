package de.aetherion.mining;

import org.bukkit.Material;

/**
 * Shared-world ore/mineral regen delays. Single source of truth for
 * {@link MiningListener} seal restore and {@link de.aetherion.core.api.MiningAccess#respawnSeconds}.
 */
public final class MiningRespawnTimes {

    private MiningRespawnTimes() {
    }

    public static long seconds(Material material) {
        if (material == null) {
            return 10L;
        }
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 10L;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> 15L;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 20L;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> 25L;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 15L;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 20L;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 40L;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 45L;
            case ANCIENT_DEBRIS -> 60L;
            case NETHER_QUARTZ_ORE, NETHER_GOLD_ORE -> 20L;
            case AMETHYST_CLUSTER -> 30L;
            case COAL_BLOCK -> 25L;
            case RAW_COPPER_BLOCK, COPPER_BLOCK -> 30L;
            case RAW_IRON_BLOCK, IRON_BLOCK -> 35L;
            case REDSTONE_BLOCK -> 30L;
            case RAW_GOLD_BLOCK, GOLD_BLOCK, QUARTZ_BLOCK -> 40L;
            case LAPIS_BLOCK -> 40L;
            case DIAMOND_BLOCK -> 70L;
            case EMERALD_BLOCK -> 80L;
            case NETHERITE_BLOCK -> 100L;
            case DEEPSLATE, STONE -> 10L;
            default -> 10L;
        };
    }
}
