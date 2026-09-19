package de.aetherion.items.skill;

/**
 * Account level — Skyblock-simple: {@link #XP_PER_LEVEL} Aetherion XP = 1 level.
 * Skill grind is a main leveling path (with pets): skill XP feeds the pool at
 * {@link #SKILL_XP_DIVISOR}. Direct bonus XP (pets, quests, …) is 1:1.
 *
 * Soft ceiling {@link #MAX_LEVEL} can be raised again. Ranks at the marks below;
 * later ranks are spaced farther apart so they keep weight.
 */
public final class AetherionLevel {

    /** Soft ceiling — room for long-term grind; raise again when content needs it. */
    public static final int MAX_LEVEL = 5000;
    /** Flat: 100 Aetherion XP → 1 account level. */
    public static final long XP_PER_LEVEL = 100L;
    /**
     * Compress skill grind XP into Aetherion XP.
     * ~40 skill XP → 1 Aetherion XP, so a few focused maxed skills carry you
     * deep into the ladder; pets/quests fill the rest without needing every skill.
     */
    public static final long SKILL_XP_DIVISOR = 40L;

    public static final int TITLE_EVERY = 25;
    public static final long SHARD_MILESTONE = 650L;
    /** Flat HP + Damage every this many account levels. */
    public static final int STAT_EVERY = 5;

    /**
     * Rank unlock marks. Early ladder stays dense; post-500 gaps grow so late
     * titles feel rare (750 → 1250 → 2500 → 5000).
     */
    private static final int[] RANK_MARKS = {
            25, 50, 75, 100, 150, 200, 250, 350, 500, 750, 1250, 2500, 5000
    };

    private static final long[] CUMULATIVE = new long[MAX_LEVEL + 1];

    static {
        long total = 0L;
        CUMULATIVE[1] = 0L;
        for (int level = 1; level < MAX_LEVEL; level++) {
            total += xpToNext(level);
            CUMULATIVE[level + 1] = total;
        }
    }

    private AetherionLevel() {
    }

    public static long xpToNext(int level) {
        if (level < 1 || level >= MAX_LEVEL) {
            return 0L;
        }
        return XP_PER_LEVEL;
    }

    public static long fromSkillXp(long skillXp) {
        if (skillXp <= 0L || SKILL_XP_DIVISOR <= 0L) {
            return 0L;
        }
        return skillXp / SKILL_XP_DIVISOR;
    }

    public static long xpForLevel(int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        return CUMULATIVE[clamped];
    }

    public static int of(long xp) {
        if (xp <= 0L) {
            return 1;
        }
        long uncapped = 1L + (xp / XP_PER_LEVEL);
        if (uncapped >= MAX_LEVEL) {
            return MAX_LEVEL;
        }
        return (int) uncapped;
    }

    public static long intoLevel(long xp) {
        int level = of(xp);
        return Math.max(0L, xp - CUMULATIVE[level]);
    }

    public static int titleTier(int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        int tier = 0;
        for (int mark : RANK_MARKS) {
            if (clamped >= mark) {
                tier++;
            } else {
                break;
            }
        }
        return tier;
    }

    public static String title(int level) {
        return switch (titleTier(level)) {
            case 1 -> "Veteran";
            case 2 -> "Champion";
            case 3, 4 -> "Legend";
            case 5 -> "Mythwright";
            case 6 -> "Aetherborn";
            case 7 -> "Celestine";
            case 8, 9 -> "Sovereign";
            case 10 -> "Ascendant";
            case 11 -> "Empyrean";
            case 12 -> "Eternal";
            case 13 -> "Aetherion";
            default -> "Adventurer";
        };
    }

    public static String rankGroup(int level) {
        return switch (titleTier(level)) {
            case 1 -> "veteran";
            case 2 -> "champion";
            case 3, 4 -> "legend";
            case 5 -> "mythwright";
            case 6 -> "aetherborn";
            case 7 -> "celestine";
            case 8, 9 -> "sovereign";
            case 10 -> "ascendant";
            case 11 -> "empyrean";
            case 12 -> "eternal";
            case 13 -> "aetherion";
            default -> "adventurer";
        };
    }

    public static String color(int level) {
        return switch (titleTier(level)) {
            case 1 -> "§a";
            case 2 -> "§9";
            case 3 -> "§6";
            case 4 -> "§6§l";
            case 5 -> "§d";
            case 6 -> "§5§l";
            case 7 -> "§b";
            case 8 -> "§e";
            case 9 -> "§e§l";
            case 10 -> "§3";
            case 11 -> "§c§l";
            case 12 -> "§4§l";
            case 13 -> "§5§l";
            default -> "§f";
        };
    }

    public static String tag(int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        return color(clamped) + "[" + clamped + "]";
    }

    public static String coloredLevel(int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        return color(clamped) + clamped;
    }

    public static String coloredTitle(int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        return color(clamped) + title(clamped);
    }

    /** +1 Damage / +1 Health stacks every {@link #STAT_EVERY} account levels. */
    public static int milestoneStatBonus(int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        return clamped / STAT_EVERY;
    }

    public static String bar(long xp) {
        int level = of(xp);
        if (level >= MAX_LEVEL) {
            return "§6MAX";
        }
        long current = intoLevel(xp);
        long needed = xpToNext(level);
        int filled = needed <= 0L ? 10 : (int) Math.min(10L, (current * 10L) / needed);
        return "§a" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled)
                + " §7" + current + "§8/§7" + needed;
    }

    public static int nextMilestone(int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        for (int mark : RANK_MARKS) {
            if (clamped < mark) {
                return mark;
            }
        }
        return MAX_LEVEL;
    }

    /** Rank marks for UI copy (copy-safe). */
    public static int[] rankMarks() {
        return RANK_MARKS.clone();
    }
}
