package de.aetherion.items.item;

/**
 * Shared skill-tool level caps and XP curve.
 * T1 → 50, T2 → 75, T3 → 100, T4 → 125, T5 → 150.
 * Gains and XP cost climb hard past ~60.
 */
public final class SkillToolCaps {

    private SkillToolCaps() {
    }

    public static int tierFromId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 1;
        }
        if (itemId.endsWith("_5")) {
            return 5;
        }
        if (itemId.endsWith("_4")) {
            return 4;
        }
        if (itemId.endsWith("_3")) {
            return 3;
        }
        if (itemId.endsWith("_2")) {
            return 2;
        }
        return 1;
    }

    public static int maxLevel(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 5 -> 150;
            case 4 -> 125;
            case 3 -> 100;
            case 2 -> 75;
            default -> 50;
        };
    }

    public static int maxLevel(String itemId) {
        return maxLevel(tierFromId(itemId));
    }

    /**
     * XP needed to go from {@code level} → {@code level + 1}.
     * Steepens after 40, then sharply after 60.
     */
    public static int xpToNext(int level, int maxLevel) {
        if (level >= maxLevel) {
            return 0;
        }
        int base = 14 + level * 7;
        if (level >= 60) {
            base += (level - 59) * 18;
        } else if (level >= 40) {
            base += (level - 39) * 5;
        }
        return Math.max(1, base);
    }

    /**
     * Multiplier on per-level flat stats for the level just reached.
     */
    public static double gainMult(int reachedLevel) {
        if (reachedLevel >= 80) {
            return 2.0d;
        }
        if (reachedLevel >= 60) {
            return 1.5d;
        }
        return 1.0d;
    }
}
