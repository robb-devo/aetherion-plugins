package de.aetherion.items.skill;

import de.aetherion.items.model.Rarity;

public final class SkillProgression {

    public static final int MAX_LEVEL = 100;
    /** Six rarities Common→Mythic spaced evenly across 1–100 (Mythic at 100). */
    public static final int RARITY_EVERY = 20;
    /**
     * Effect curve (base bonuses stay flat; this scales them).
     * Wave 2: early levels stay modest so T1 gear is the power spike;
     * mid is a slow climb; 75–100 is where skills become a real stack slice.
     * Curve only: ~1.00× @1 · ~1.24× @25 · ~1.50× @50 · ~2.60× @75 · ~6.10× @100.
     * With rarity: ~1.33× @25 · ~1.66× @50 · ~2.84× @75 · ~6.50× @100.
     * Midgame pass kept these numbers (Wave-2 intent holds) and made them readable
     * instead: see {@link Stage} — Apprentice / Journeyman / Master in the menu.
     */
    public static final double PER_LEVEL = 0.0102d;
    public static final double PER_LEVEL_AFTER_50 = 0.044d;
    public static final double PER_LEVEL_AFTER_75 = 0.140d;
    public static final double PER_RARITY = 0.08d;

    /** Compact proc chance at skill level 1 — modest early helper. Wave 1. */
    public static final double COMPACT_CHANCE_BASE = 0.006d;
    /** Compact proc chance at skill level {@link #MAX_LEVEL} — rewarding, not a printer. Wave 1. */
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
        int mid = Math.min(Math.max(0, clamped - 50), 25);
        int late = Math.max(0, clamped - 75);
        return 1.0d
                + ((early - 1) * PER_LEVEL)
                + (mid * PER_LEVEL_AFTER_50)
                + (late * PER_LEVEL_AFTER_75)
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

    /**
     * XP to reach {@code level + 1}. Early is still snack-sized; 50–100 is the wall
     * so late levels feel earned and the 6.5× multiplier is not a free gift.
     */
    public static int xpToNext(int level) {
        if (level >= MAX_LEVEL) {
            return 0;
        }
        int current = clampLevel(level);
        int xp = 32 + (11 * current) + ((current * current) / 2);
        if (current >= 50) {
            xp += (current - 49) * 70;
        }
        if (current >= 75) {
            xp += (current - 74) * 110;
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

    /** Where the curve starts to bend — MID uses {@link #PER_LEVEL_AFTER_50}. */
    public static final int MID_FROM = 50;
    /** Steepest stretch — LATE uses {@link #PER_LEVEL_AFTER_75}. */
    public static final int LATE_FROM = 75;

    /**
     * Player-facing read of the effect curve so nobody has to know the constants.
     * Apprentice climbs gently, Journeyman bends upward, Master is the steep wall.
     */
    public enum Stage {
        APPRENTICE("§f", "Apprentice", "gentle climb — gear is your power spike"),
        JOURNEYMAN("§e", "Journeyman", "bonuses climb faster from here"),
        MASTER("§6", "Master", "steepest stretch — every level shows"),
        MASTERED("§d", "Mastered", "nothing left to prove");

        private final String color;
        private final String title;
        private final String hint;

        Stage(String color, String title, String hint) {
            this.color = color;
            this.title = title;
            this.hint = hint;
        }

        public String color() {
            return color;
        }

        public String title() {
            return title;
        }

        public String hint() {
            return hint;
        }

        public String colored() {
            return color + title;
        }
    }

    public static Stage stage(int level) {
        int clamped = clampLevel(level);
        if (clamped >= MAX_LEVEL) {
            return Stage.MASTERED;
        }
        if (clamped >= LATE_FROM) {
            return Stage.MASTER;
        }
        if (clamped >= MID_FROM) {
            return Stage.JOURNEYMAN;
        }
        return Stage.APPRENTICE;
    }

    /** Level range label for a stage, e.g. {@code 50–74}. */
    public static String stageRange(Stage stage) {
        return switch (stage) {
            case APPRENTICE -> "1–" + (MID_FROM - 1);
            case JOURNEYMAN -> MID_FROM + "–" + (LATE_FROM - 1);
            case MASTER -> LATE_FROM + "–" + (MAX_LEVEL - 1);
            case MASTERED -> Integer.toString(MAX_LEVEL);
        };
    }

    /** Level that unlocks the next rarity, or {@code -1} at Mythic. */
    public static int nextRarityLevel(int level) {
        int tier = rarityTier(level);
        if (tier >= 5) {
            return -1;
        }
        return Math.min(MAX_LEVEL, (tier + 1) * RARITY_EVERY);
    }

    /** XP still missing from {@code level}/{@code xp} to reach {@code target}. */
    public static long xpUntil(int level, int xp, int target) {
        int from = clampLevel(level);
        int to = clampLevel(target);
        if (to <= from) {
            return 0L;
        }
        long total = -Math.max(0, xp);
        for (int current = from; current < to; current++) {
            total += xpToNext(current);
        }
        return Math.max(0L, total);
    }

    /** 0–1 fill of the current level. */
    public static double levelFill(int level, int xp) {
        if (isMax(level)) {
            return 1.0d;
        }
        int needed = xpToNext(level);
        if (needed <= 0) {
            return 1.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, xp / (double) needed));
    }

    /** Pane color that matches a rarity — used for the loadout strip. */
    public static org.bukkit.Material rarityPane(Rarity rarity) {
        if (rarity == null) {
            return org.bukkit.Material.GRAY_STAINED_GLASS_PANE;
        }
        return switch (rarity) {
            case UNCOMMON -> org.bukkit.Material.LIME_STAINED_GLASS_PANE;
            case RARE -> org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case EPIC -> org.bukkit.Material.PURPLE_STAINED_GLASS_PANE;
            case LEGENDARY -> org.bukkit.Material.ORANGE_STAINED_GLASS_PANE;
            case MYTHIC -> org.bukkit.Material.MAGENTA_STAINED_GLASS_PANE;
            case AETHERED -> org.bukkit.Material.RED_STAINED_GLASS_PANE;
            default -> org.bukkit.Material.WHITE_STAINED_GLASS_PANE;
        };
    }

    /** Rarity name for copy: {@code LEGENDARY} → {@code Legendary}. */
    public static String rarityName(Rarity rarity) {
        if (rarity == null) {
            return "Common";
        }
        String raw = rarity.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /** Compact 10-cell bar for action bars / lore: {@code ▮▮▮▮▯▯▯▯▯▯}. */
    public static String miniBar(double fill, String onColor) {
        int filled = (int) Math.floor(Math.max(0.0d, Math.min(1.0d, fill)) * 10.0d);
        return onColor + "▮".repeat(filled) + "§8" + "▮".repeat(10 - filled);
    }
}
