package de.aetherion.items.skill;

import de.aetherion.items.model.Rarity;

public final class SkillProgression {

    /** A-61 (E-4): skills run to 150 on one even curve (was 100, with walls at 50 and 75). */
    public static final int MAX_LEVEL = 150;
    /** Six rarities Common→Mythic spaced evenly across 1–150 (Mythic at 150). */
    public static final int RARITY_EVERY = 30;
    /**
     * Effect curve (base bonuses stay flat; this scales them).
     * A-61: one even slope, no bend at 50 or 75. Every level counts the same, every 10th level is a step
     * ({@link #PER_TEN_LEVELS}), every 30th adds the rarity step on top. The end stays where it was
     * (~6.47× at 150, was ~6.48× at 100), so max power doesn't move; the road to it is longer and even:
     * ~1.31× @10 · ~1.79× @25 · ~2.75× @50 · ~3.65× @75 · ~4.61× @100 · ~5.51× @125 · ~6.47× @150.
     */
    public static final double PER_LEVEL = 0.028d;
    public static final double PER_TEN_LEVELS = 0.06d;
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

    /**
     * Boss Grudge (P4 / L3): +10% at level 1 to +30% at {@link #MAX_LEVEL}, linear. Was 10% x
     * {@link #effectMultiplier(int)} (+65% at 100), which stacked with crit into the End blow-up.
     */
    public static double bossGrudge(int level) {
        int clamped = clampLevel(level);
        return 0.10d + 0.20d * (clamped - 1) / (double) (MAX_LEVEL - 1);
    }

    /** Floor Grudge (P4 / L3, design B6): +12% at level 1 to +36% at {@link #MAX_LEVEL}, dungeons only. */
    public static double floorGrudge(int level) {
        int clamped = clampLevel(level);
        return 0.12d + 0.24d * (clamped - 1) / (double) (MAX_LEVEL - 1);
    }

    /**
     * Flat stat bonuses (Damage, Defense, Health, Fortune, Mining Power, Spread …) — the weight of a skill.
     * Linear with a step every 10 levels and per rarity. A-61 stretched it over 150 levels with the same end:
     * ~1.9× @10 · ~3.3× @25 · ~6.4× @50 · ~9.3× @75 · ~12.4× @100 · ~15.3× @125 · ~18.4× @150. A maxed fortune
     * skill (+22 base) is ~+400 Fortune, a quarter of a T5 set; Heavy Hands at max is a T5 sword's damage. Percent
     * stats and special effects stay on {@link #effectMultiplier(int)} so crit, lifesteal and loot rolls don't run away.
     */
    public static double weightMultiplier(int level) {
        int clamped = clampLevel(level);
        return 1.0d + 0.08d * (clamped - 1) + 0.20d * (clamped / 10) + 0.50d * rarityTier(clamped);
    }

    /** The scale a skill's bonus of this kind gets at {@code level}. */
    public static double bonusMultiplier(int level, de.aetherion.items.model.ItemCapability capability) {
        return isPercent(capability) ? effectMultiplier(level) : weightMultiplier(level);
    }

    public static boolean isPercent(de.aetherion.items.model.ItemCapability capability) {
        return capability == de.aetherion.items.model.ItemCapability.SPEED
                || capability == de.aetherion.items.model.ItemCapability.CRIT_CHANCE
                || capability == de.aetherion.items.model.ItemCapability.CRIT_DAMAGE
                || capability == de.aetherion.items.model.ItemCapability.PET_CATCH_RATE;
    }

    public static double effectMultiplier(int level) {
        int clamped = clampLevel(level);
        return 1.0d
                + ((clamped - 1) * PER_LEVEL)
                + ((clamped / 10) * PER_TEN_LEVELS)
                + (rarityTier(clamped) * PER_RARITY);
    }

    /**
     * Skill compact chance: ~0.6% at level 1, ~2.8% at {@link #MAX_LEVEL}.
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
     * XP to reach {@code level + 1}. A-61 (E-4): one smooth curve, each level a little dearer than the one before,
     * no wall at 50 or 75. 40 XP at 1→2, ~1,530 at 50, ~5,030 at 100, ~10,400 at 149→150.
     * Totals: ~30k to 50, ~184k to 100, ~562k to 150 (was ~35k to 50 and ~347k to the old cap 100).
     */
    public static int xpToNext(int level) {
        if (level >= MAX_LEVEL) {
            return 0;
        }
        int current = clampLevel(level);
        return 30 + (10 * current) + ((2 * current * current) / 5);
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

    /** Second third of the road (A-61: the curve is even, the stages are just milestones). */
    public static final int MID_FROM = 50;
    /** Last third of the road. */
    public static final int LATE_FROM = 100;

    /**
     * Player-facing milestones on the road to {@link #MAX_LEVEL}. Since A-61 every level adds the same step,
     * so the stages name the distance, not a change in slope.
     */
    public enum Stage {
        APPRENTICE("§f", "Apprentice", "every level adds a step, gear is your power spike"),
        JOURNEYMAN("§e", "Journeyman", "a third of the way, the steps add up now"),
        MASTER("§6", "Master", "the last fifty levels, the road to Mythic"),
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
