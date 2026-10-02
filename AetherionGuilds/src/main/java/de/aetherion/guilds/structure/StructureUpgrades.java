package de.aetherion.guilds.structure;

import java.util.List;

/**
 * One or two upgrades for the buildings that matter, paid with what the island already makes: Compressed /
 * Compacted Cobblestone (the quarry chain), Quarry Cores and coins. No new currency.
 *
 * <p>Each tier is its own template with the same footprint as tier 1, so belts keep feeding the same walls:
 * the building grows up (Storage Hut → Loft → Warehouse, Depot → Crate Stack, Mill → Twin Stones,
 * Forge → Bellows Forge) and holds or processes more.
 *
 * <p>Cost keys follow the guild projects: {@code coins}, {@code item:<aetherion id>}. Items are taken from the
 * island's own Storage Huts / Depots first (the hut pays for its loft), then from the player's pack.
 */
public final class StructureUpgrades {

    public record Cost(String key, long amount, String label) {
        public boolean coins() {
            return key.equals("coins");
        }

        public String itemId() {
            return key.startsWith("item:") ? key.substring(5) : null;
        }
    }

    public record Tier(int level, String name, List<String> perks, List<Cost> costs, int minIslandTier) {
    }

    public static final String COMPRESSED_COBBLE = "item:compressed_cobblestone";
    public static final String COMPACTED_COBBLE = "item:compacted_cobblestone";
    public static final String QUARRY_CORE = "item:quarry_core";

    private StructureUpgrades() {
    }

    public static int maxLevel(StructureType type) {
        return switch (type) {
            case STORAGE_HUT -> 3;
            case DEPOT, MILL, FORGE -> 2;
            default -> 1;
        };
    }

    /** Display name of a tier ("Storage Hut", "Loft", "Warehouse"...). */
    public static String tierName(StructureType type, int level) {
        if (level <= 1) {
            return type.display();
        }
        return switch (type) {
            case STORAGE_HUT -> level >= 3 ? "Warehouse" : "Storage Loft";
            case DEPOT -> "Crate Stack";
            case MILL -> "Twin-Stone Mill";
            case FORGE -> "Bellows Forge";
            default -> type.display();
        };
    }

    /** Template for a tier: tier 1 is the type's own, higher tiers add {@code _2} / {@code _3}. */
    public static String templateFor(StructureType type, int level) {
        String base = type.templateId();
        if (base == null || level <= 1) {
            return base;
        }
        return base + "_" + Math.min(level, maxLevel(type));
    }

    /** Storage (sinks) or buffer (processors), raw-equivalent units. */
    public static long capacity(StructureType type, int level) {
        long base = type.capacity();
        return switch (type) {
            case STORAGE_HUT -> level >= 3 ? base * 16L : level == 2 ? base * 4L : base;
            case DEPOT -> level >= 2 ? base * 4L : base;
            case MILL, FORGE -> level >= 2 ? base * 3L : base;
            default -> base;
        };
    }

    /** Processor speed, raw-equivalent per second. */
    public static int ratePerSecond(StructureType type, int level) {
        int base = type.ratePerSecond();
        return (type == StructureType.MILL || type == StructureType.FORGE) && level >= 2 ? base * 3 : base;
    }

    /** The next tier, or null when maxed / not upgradeable. */
    public static Tier next(StructureType type, int level) {
        int to = level + 1;
        if (to > maxLevel(type)) {
            return null;
        }
        return switch (type) {
            case STORAGE_HUT -> to == 2
                    ? new Tier(2, "Storage Loft", List.of(
                    "§7A loft storey goes up on top,",
                    "§7stacked with crates.",
                    "§7Holds §f4×§7: §f" + compact(capacity(type, 2)) + " §7raw-eq"),
                    List.of(new Cost(COMPRESSED_COBBLE, 32L, "Compressed Cobblestone"),
                            new Cost("coins", 15_000L, "Coins")), 1)
                    : new Tier(3, "Warehouse", List.of(
                    "§7Copper band, slate roof, a",
                    "§7lightning rod on the ridge.",
                    "§7Holds §f16×§7: §f" + compact(capacity(type, 3)) + " §7raw-eq"),
                    List.of(new Cost(COMPACTED_COBBLE, 4L, "Compacted Cobblestone"),
                            new Cost(QUARRY_CORE, 1L, "Quarry Core"),
                            new Cost("coins", 60_000L, "Coins")), 2);
            case DEPOT -> new Tier(2, "Crate Stack", List.of(
                    "§7Crates stacked three high.",
                    "§7Holds §f4×§7: §f" + compact(capacity(type, 2)) + " §7raw-eq"),
                    List.of(new Cost(COMPRESSED_COBBLE, 12L, "Compressed Cobblestone"),
                            new Cost("coins", 4_000L, "Coins")), 1);
            case MILL -> new Tier(2, "Twin-Stone Mill", List.of(
                    "§7A second pair of stones and a",
                    "§7taller frame. Grinds §f3×§7 as fast:",
                    "§f" + compact(ratePerSecond(type, 2)) + " §7raw-eq / s"),
                    List.of(new Cost(COMPRESSED_COBBLE, 24L, "Compressed Cobblestone"),
                            new Cost("coins", 12_000L, "Coins")), 1);
            case FORGE -> new Tier(2, "Bellows Forge", List.of(
                    "§7A second blast furnace and a",
                    "§7second stack. Presses §f3×§7 as fast:",
                    "§f" + compact(ratePerSecond(type, 2)) + " §7raw-eq / s"),
                    List.of(new Cost(COMPACTED_COBBLE, 2L, "Compacted Cobblestone"),
                            new Cost(QUARRY_CORE, 1L, "Quarry Core"),
                            new Cost("coins", 30_000L, "Coins")), 1);
            default -> null;
        };
    }

    private static String compact(long value) {
        return de.aetherion.guilds.util.GuildFormat.compact(value);
    }
}
