package de.aetherion.items.mining;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class HarvestRules {

    private HarvestRules() {
    }

    public static boolean openMine(World world) {
        if (world == null) {
            return false;
        }
        de.aetherion.core.api.MiningAccess mining = de.aetherion.core.api.AetherServices.mining();
        if (mining != null) {
            return mining.isVeinsWorld(world);
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionMining");
        if (plugin instanceof JavaPlugin javaPlugin && plugin.isEnabled()) {
            String name = javaPlugin.getConfig().getString("veins.world", "aether_veins");
            return world.getName().equalsIgnoreCase(name);
        }
        return "aether_veins".equalsIgnoreCase(world.getName());
    }

    public static boolean tracked(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        if (material == Material.AMETHYST_CLUSTER) {
            return true;
        }
        if (log(material)) {
            return true;
        }
        return switch (material) {
            case STONE,
                 DEEPSLATE,
                 COAL_ORE, DEEPSLATE_COAL_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 ANCIENT_DEBRIS,
                 NETHER_QUARTZ_ORE, NETHER_GOLD_ORE,
                 COAL_BLOCK,
                 RAW_COPPER_BLOCK, COPPER_BLOCK,
                 RAW_IRON_BLOCK, IRON_BLOCK,
                 RAW_GOLD_BLOCK, GOLD_BLOCK,
                 REDSTONE_BLOCK,
                 LAPIS_BLOCK,
                 DIAMOND_BLOCK,
                 EMERALD_BLOCK,
                 NETHERITE_BLOCK,
                 QUARTZ_BLOCK -> true;
            default -> false;
        };
    }

    /** Real ores only — not stone, logs, or dense mineral blocks. */
    public static boolean ore(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        if (material == Material.AMETHYST_CLUSTER || material == Material.ANCIENT_DEBRIS) {
            return true;
        }
        String name = material.name();
        return name.endsWith("_ORE");
    }

    public static boolean fullMineralBlock(Material material) {
        if (material == null) {
            return false;
        }
        return switch (material) {
            case COAL_BLOCK,
                 RAW_COPPER_BLOCK, COPPER_BLOCK,
                 RAW_IRON_BLOCK, IRON_BLOCK,
                 RAW_GOLD_BLOCK, GOLD_BLOCK,
                 REDSTONE_BLOCK,
                 LAPIS_BLOCK,
                 DIAMOND_BLOCK,
                 EMERALD_BLOCK,
                 NETHERITE_BLOCK,
                 QUARTZ_BLOCK -> true;
            default -> false;
        };
    }

    /** Resource dropped from a full mineral block (before fortune). */
    public static Material fullBlockResource(Material material) {
        if (material == null) {
            return null;
        }
        return switch (material) {
            case COAL_BLOCK -> Material.COAL;
            case RAW_COPPER_BLOCK, COPPER_BLOCK -> Material.COPPER_INGOT;
            case RAW_IRON_BLOCK, IRON_BLOCK -> Material.IRON_INGOT;
            case RAW_GOLD_BLOCK, GOLD_BLOCK -> Material.GOLD_INGOT;
            case REDSTONE_BLOCK -> Material.REDSTONE;
            case LAPIS_BLOCK -> Material.LAPIS_LAZULI;
            case DIAMOND_BLOCK -> Material.DIAMOND;
            case EMERALD_BLOCK -> Material.EMERALD;
            case NETHERITE_BLOCK -> Material.NETHERITE_INGOT;
            case QUARTZ_BLOCK -> Material.QUARTZ;
            default -> null;
        };
    }

    /** Base amount from a dense mineral block (fortune applied on top). */
    public static int fullBlockBaseAmount(Material material) {
        if (material == null) {
            return 0;
        }
        return switch (material) {
            case COAL_BLOCK -> 12;
            case RAW_COPPER_BLOCK, COPPER_BLOCK -> 12;
            case RAW_IRON_BLOCK, IRON_BLOCK -> 12;
            case RAW_GOLD_BLOCK, GOLD_BLOCK -> 12;
            case REDSTONE_BLOCK -> 14;
            case LAPIS_BLOCK -> 14;
            case DIAMOND_BLOCK -> 12;
            case EMERALD_BLOCK -> 12;
            case NETHERITE_BLOCK -> 10;
            case QUARTZ_BLOCK -> 12;
            default -> 9;
        };
    }

    public static boolean log(Material material) {
        if (material == null) {
            return false;
        }
        if (Tag.LOGS.isTagged(material)) {
            return true;
        }
        String name = material.name();
        return name.endsWith("_WOOD")
                || name.endsWith("_HYPHAE")
                || name.endsWith("_STEM")
                || name.equals("MUSHROOM_STEM");
    }

    public static double requiredPower(Material material) {
        if (material == null) {
            return 0;
        }
        if (log(material)) {
            return 0;
        }
        return switch (material) {
            case STONE, DEEPSLATE -> 0;
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 8;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> 14;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 24;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE, NETHER_GOLD_ORE -> 30;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_QUARTZ_ORE -> 42;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 48;
            case AMETHYST_CLUSTER -> 55;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 95;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 140;
            case ANCIENT_DEBRIS -> 250;
            case COAL_BLOCK -> 22;
            case RAW_COPPER_BLOCK, COPPER_BLOCK -> 36;
            case RAW_IRON_BLOCK, IRON_BLOCK -> 55;
            case REDSTONE_BLOCK -> 48;
            case RAW_GOLD_BLOCK, GOLD_BLOCK, QUARTZ_BLOCK -> 70;
            case LAPIS_BLOCK -> 78;
            case DIAMOND_BLOCK -> 160;
            case EMERALD_BLOCK -> 210;
            case NETHERITE_BLOCK -> 320;
            default -> 0;
        };
    }

    public static boolean canHarvest(Material material, double miningPower) {
        if (material == null || log(material)) {
            return true;
        }
        double required = requiredPower(material);
        return required <= 0.01 || miningPower + 0.001 >= required;
    }

    /** Ores and dense mineral blocks — pickaxe required, bare hand never. */
    public static boolean requiresPickaxe(Material material) {
        return material != null && requiredPower(material) > 0.01;
    }

    public static int ticks(Material material, double miningPower) {
        int base = comfortableTicks(material);
        double required = requiredPower(material);
        double factor;
        if (required <= 0.01) {
            factor = 1.0 + Math.min(0.35, Math.max(0.0, miningPower) / 200.0);
        } else {
            double ratio = Math.max(0.0, miningPower) / required;
            if (ratio <= 1.0) {
                factor = 0.08 + 0.92 * ratio;
            } else {
                factor = 1.0 + Math.min(0.35, (ratio - 1.0) * 0.35);
            }
        }
        int ticks = (int) Math.round(base / Math.max(0.05, factor));
        return Math.max(4, ticks);
    }

    private static int comfortableTicks(Material material) {
        if (log(material)) {
            return 9;
        }
        return switch (material) {
            case STONE, DEEPSLATE -> 7;
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 10;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> 11;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 13;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE, NETHER_GOLD_ORE -> 12;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_QUARTZ_ORE -> 15;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 16;
            case AMETHYST_CLUSTER -> 14;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 20;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 24;
            case ANCIENT_DEBRIS -> 36;
            case COAL_BLOCK -> 22;
            case RAW_COPPER_BLOCK, COPPER_BLOCK -> 26;
            case RAW_IRON_BLOCK, IRON_BLOCK -> 30;
            case REDSTONE_BLOCK -> 28;
            case RAW_GOLD_BLOCK, GOLD_BLOCK, QUARTZ_BLOCK -> 34;
            case LAPIS_BLOCK -> 36;
            case DIAMOND_BLOCK -> 48;
            case EMERALD_BLOCK -> 54;
            case NETHERITE_BLOCK -> 70;
            default -> 10;
        };
    }
}
