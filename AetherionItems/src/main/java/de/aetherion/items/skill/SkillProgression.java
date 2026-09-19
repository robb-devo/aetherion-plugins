package de.aetherion.items.skill;

import de.aetherion.items.model.Rarity;

public final class SkillProgression {

    public static final int MAX_LEVEL = 100;
    /** Six rarities Common→Mythic spaced evenly across 1–100 (Mythic at 100). */
    public static final int RARITY_EVERY = 20;
    /**
     * Effect curve (base bonuses stay flat; this scales them).
     * Gentle to ~50, then climbs hard so skills matter past ~60 and feel earned at 100.
     * ~1.0× @1 · ~2.5× @50 · ~3.3× @60 · ~6.3× @100.
     */
    public static final double PER_LEVEL = 0.024d;
    public static final double PER_LEVEL_AFTER_50 = 0.068d;
    public static final double PER_RARITY = 0.14d;

    /** Compact proc chance at skill level 1 — modest early helper. */
    public static final double COMPACT_CHANCE_BASE = 0.006d;
    /** Compact proc chance at skill level {@link #MAX_LEVEL} — rewarding, not a printer. */
    public static final double COMPACT_CHANCE_MAX = 0.028d;

    private SkillProgression() {
    }

    public static int clampLevel(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    public static int rarityTier(int level) {
        int clamped = clampLevel(level);
        if (clamped < RARITY_EVERY) {
            return 0;
        }
        return Math.min(5, clamped / RARITY_EVERY);
    }

    public static Rarity rarity(int level) {
        return switch (rarityTier(level)) {
            case 1 -> Rarity.UNCOMMON;
            case 2 -> Rarity.RARE;
            case 3 -> Rarity.EPIC;
            case 4 -> Rarity.LEGENDARY;
            case 5 -> Rarity.MYTHIC;
            default -> Rarity.COMMON;
        };
    }

    public static double effectMultiplier(int level) {
        int clamped = clampLevel(level);
        int early = Math.min(clamped, 50);
        int late = Math.max(0, clamped - 50);
        return 1.0d
                + ((early - 1) * PER_LEVEL)
                + (late * PER_LEVEL_AFTER_50)
                + (rarityTier(clamped) * PER_RARITY);
    }

    /**
     * Skill compact chance: ~0.6% at level 1, ~2.8% at level 100.
     * Kept flatter than {@link #effectMultiplier(int)} so compact stays a gentle helper.
     */
    public static double compactChance(int level) {
        int clamped = clampLevel(level);
        if (MAX_LEVEL <= 1) {
            return COMPACT_CHANCE_BASE;
        }
        double t = (clamped - 1) / (double) (MAX_LEVEL - 1);
        return COMPACT_CHANCE_BASE + ((COMPACT_CHANCE_MAX - COMPACT_CHANCE_BASE) * t);
    }

    public static double compactChancePercent(int level) {
        return compactChance(level) * 100.0d;
    }

    public static int rarityBonusPercent(int level) {
        return (int) Math.round(rarityTier(level) * PER_RARITY * 100.0d);
    }

    public static int xpToNext(int level) {
        if (level >= MAX_LEVEL) {
            return 0;
        }
        int current = clampLevel(level);
        int xp = 30 + (12 * current) + ((current * current * 2) / 3);
        if (current >= 50) {
            xp += (current - 49) * 45;
        }
        return xp;
    }

    public static long spentXp(int level) {
        int clamped = clampLevel(level);
        long total = 0L;
        for (int current = 1; current < clamped; current++) {
            total += xpToNext(current);
        }
        return total;
    }

    public static boolean isMax(int level) {
        return clampLevel(level) >= MAX_LEVEL;
    }
}
