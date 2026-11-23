package de.aetherion.foraging;

/**
 * Fell vocabulary (A-51: the timing bar is gone). A fell is aimed: the cutter looks where the tree should
 * fall. The bar up top reads {@code Fall  ➜ lane clear · on the gold ring · hit again  ✦N}; {@code ✦N} is
 * the running streak of clean fells. The Perfect ring's size is counted in steps (the old window cells),
 * so every skill, mark and streak bonus that widened the window now widens the ring.
 */
final class ForagingStrike {

    /** Streak that widens the Perfect ring by one step — same number as fishing's hot water. */
    static final int HOT_STREAK = 5;
    /** Best Foraging skill level that permanently widens the Perfect ring by one step. */
    static final int WIDE_WINDOW_LEVEL = 50;
    private static final int MAX_ZONE = 8;
    /** Degrees of Perfect cone per step, plus a fixed base. Step 4 (default) = ±18°. */
    private static final double DEGREES_PER_STEP = 3.5d;
    private static final double BASE_DEGREES = 4.0d;

    private ForagingStrike() {
    }

    static int zoneSize(int configured) {
        return Math.max(3, Math.min(MAX_ZONE, configured));
    }

    /** Half-width of the Perfect cone around the gold ring's direction, in degrees. */
    static double tolerance(int zone) {
        return BASE_DEGREES + DEGREES_PER_STEP * zoneSize(zone);
    }

    /** Live aim: lane state, whether it's on the ring, and the time left as a warning. */
    static String aimTitle(boolean de, boolean clear, boolean onRing, boolean slipping, int streak) {
        StringBuilder out = new StringBuilder("§2").append(de ? "Fällen" : "Fell").append("  ");
        if (!clear) {
            out.append(de ? "§c✖ trifft etwas §8· §7schau woanders hin" : "§c✖ it would hit something §8· §7look elsewhere");
        } else if (onRing) {
            out.append(de ? "§a➜ frei §8· §6✦ im goldenen Ring §8· §fschlag zu!" : "§a➜ clear §8· §6✦ on the gold ring §8· §fhit now!");
        } else {
            out.append(de ? "§a➜ frei §8· §7zielt neben den §6goldenen Ring" : "§a➜ clear §8· §7off the §6gold ring");
        }
        if (slipping) {
            out.append(de ? " §8· §cschnell!" : " §8· §churry!");
        }
        out.append(streakTag(streak));
        return out.toString();
    }

    /** Look-ahead: an axe on a living trunk, before the first hit. */
    static String previewTitle(boolean de, int streak) {
        return "§2" + (de ? "Fällen" : "Fell") + "  §aREADY §8· §7"
                + (de ? "Stamm schlagen, dann hinschauen, wohin er fällt" : "hit the trunk, then look where it should fall")
                + streakTag(streak);
    }

    /** Same tree, but it is still resting for this player. */
    static String coolingTitle(boolean de, long secondsLeft) {
        long sec = Math.max(1L, secondsLeft);
        return "§2" + (de ? "Fällen" : "Fell") + "  §c" + sec + "s §8· §7" + (de ? "andere Bäume sind frei" : "other trees free");
    }

    static String streakTag(int streak) {
        if (streak < 2) {
            return "";
        }
        return (streak >= HOT_STREAK ? "  §6✦" : "  §e✦") + streak;
    }
}
