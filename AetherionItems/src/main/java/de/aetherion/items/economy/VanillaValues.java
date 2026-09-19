package de.aetherion.items.economy;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Low default coin values for every vanilla item so coins stay scarce.
 * Explicit {@code economy.yml} entries always win.
 */
public final class VanillaValues {

    private VanillaValues() {
    }

    public static long of(Material material) {
        if (material == null || material.isAir() || !material.isItem()) {
            return 0L;
        }
        String name = material.name();
        if (isTechnical(name)) {
            return 0L;
        }
        if (name.contains("NETHERITE")) {
            if (name.contains("BLOCK") || name.contains("INGOT")) {
                return 25L;
            }
            return 18L;
        }
        if (name.contains("DIAMOND")) {
            if (name.contains("BLOCK")) {
                return 72L;
            }
            if (name.contains("ORE") || name.contains("HORSE")) {
                return 6L;
            }
            return 8L;
        }
        if (name.contains("EMERALD")) {
            return name.contains("BLOCK") ? 54L : 6L;
        }
        if (name.contains("GOLD")) {
            if (name.contains("BLOCK")) {
                return 36L;
            }
            if (name.contains("INGOT") || name.contains("NUGGET")) {
                return name.contains("NUGGET") ? 1L : 4L;
            }
            if (name.contains("ORE") || name.contains("RAW")) {
                return 3L;
            }
            return 2L;
        }
        if (name.contains("IRON")) {
            if (name.contains("BLOCK")) {
                return 27L;
            }
            if (name.contains("INGOT")) {
                return 3L;
            }
            return 1L;
        }
        if (name.contains("COPPER")) {
            return name.contains("BLOCK") ? 9L : 1L;
        }
        if (name.contains("LAPIS")) {
            return name.contains("BLOCK") ? 18L : 2L;
        }
        if (name.contains("REDSTONE")) {
            return name.contains("BLOCK") ? 9L : 1L;
        }
        if (name.contains("COAL")) {
            return name.contains("BLOCK") ? 18L : 2L;
        }
        if (name.contains("ANCIENT_DEBRIS") || name.contains("SHULKER") || name.contains("ELYTRA")) {
            return 12L;
        }
        if (name.contains("ENDER_PEARL") || name.contains("ENDER_EYE") || name.contains("GHAST_TEAR")) {
            return 4L;
        }
        if (name.contains("NETHER_STAR") || name.contains("DRAGON_EGG") || name.contains("TOTEM")) {
            return 40L;
        }
        if (name.endsWith("_SPAWN_EGG") || name.contains("COMMAND") || name.contains("STRUCTURE")) {
            return 0L;
        }
        if (name.contains("LOG") || name.contains("STEM") || name.contains("HYPHAE")) {
            return 1L;
        }
        if (name.endsWith("_ORE")) {
            return 2L;
        }
        if (name.startsWith("RAW_")) {
            return 1L;
        }
        return 1L;
    }

    private static boolean isTechnical(String name) {
        String key = name.toUpperCase(Locale.ROOT);
        return key.contains("COMMAND")
                || key.contains("STRUCTURE")
                || key.contains("JIGSAW")
                || key.contains("BARRIER")
                || key.contains("LIGHT") && key.contains("BLOCK")
                || key.contains("DEBUG")
                || key.contains("KNOWLEDGE_BOOK")
                || key.contains("END_PORTAL")
                || key.contains("NETHER_PORTAL")
                || key.equals("BEDROCK")
                || key.equals("SPAWNER")
                || key.equals("BUDDING_AMETHYST")
                || key.contains("AIR");
    }
}
