package de.aetherion.guilds.island;

import de.aetherion.guilds.structure.StructureType;

import java.util.List;

/**
 * The stage ladder of a personal island (the Hub board). Stage 0 is the tour: Hub, hut, quarry, belt, a
 * hand-pressed Compressed Cobble, the Mill, the Dock and the first ship. Every stage after that is reached by
 * shipping that stage's phase contract at the Dock; the Hub tier and the Island Tier follow the stage on their
 * own (no coin upgrade for either on a personal island).
 *
 * <ul>
 *   <li><b>0 Landing</b>: Mill, Dock. Quarries Lv1 (but the first quarry digs ten times as fast while you learn).</li>
 *   <li><b>1 Werkhof</b>: Splitter, a second quarry of your starter's trade, quarries up to Lv3, the Storage Loft.</li>
 *   <li><b>2 Foreman's Hall</b>: Forge, Sorter, Overflow Gate, quarries up to Lv5, the Warehouse.</li>
 *   <li><b>3 Grand Hub</b>: every Mill and Forge twice as fast, quarries up to Lv7, buying land.</li>
 * </ul>
 */
public final class Stages {

    public static final int MAX = 3;

    private Stages() {
    }

    public static int clamp(int stage) {
        return Math.max(0, Math.min(MAX, stage));
    }

    public static String name(int stage) {
        return switch (clamp(stage)) {
            case 1 -> "Werkhof";
            case 2 -> "Foreman's Hall";
            case 3 -> "Grand Hub";
            default -> "Landing";
        };
    }

    /** "Stage 1 · Werkhof" */
    public static String label(int stage) {
        return "Stage " + clamp(stage) + " · " + name(stage);
    }

    /** The Hub's tier at this stage (Hub, Foreman's Hall, Grand Hub). */
    public static int hubLevel(int stage) {
        return Math.max(1, Math.min(3, clamp(stage)));
    }

    /** The Island Tier at this stage (build radius, belt cap, extra machines). */
    public static int islandTier(int stage) {
        return switch (clamp(stage)) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 5;
            default -> 1;
        };
    }

    /** Highest quarry level allowed at this stage. */
    public static int quarryCap(int stage) {
        return switch (clamp(stage)) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            default -> 1;
        };
    }

    /** The stage a building's tier needs on a personal island (0 = always). */
    public static int upgradeStage(StructureType type, int toLevel) {
        return switch (type) {
            case STORAGE_HUT -> toLevel >= 3 ? 2 : 1;
            case MILL -> 1;
            case FORGE -> 3;
            default -> 0;
        };
    }

    public static boolean landOpen(int stage) {
        return clamp(stage) >= 3;
    }

    /** What reaching this stage opens, for the Hub board and the stage-up beat. */
    public static List<String> perks(int stage) {
        return switch (clamp(stage)) {
            case 1 -> List.of("§7Unlocks the §fSplitter", "§7A second quarry of your trade",
                    "§7Quarries up to §fLv3§7, the §fStorage Loft", "§7Island Tier §f2 §8(more belts, +1 machine)");
            case 2 -> List.of("§7Unlocks §fForge§7, §fSorter§7, §fOverflow Gate", "§7Quarries up to §fLv5§7, the §fWarehouse",
                    "§7Island Tier §f3");
            case 3 -> List.of("§7Every Mill and Forge runs §f2× §7as fast", "§7Quarries up to §fLv7§7, the §fBellows Forge",
                    "§7Buy land next to your island", "§7Island Tier §f5");
            default -> List.of("§7Mill and Dock", "§7Your first quarry digs §f10× §7as fast");
        };
    }
}
