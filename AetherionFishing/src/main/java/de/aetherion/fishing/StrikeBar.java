package de.aetherion.fishing;

import java.util.Locale;

/**
 * Strike bar vocabulary — shared shape with the Fell bar in AetherionForaging:
 * {@code Label  [──██▓██──]  WORD}. Green is the window, the gold cell in the middle
 * is the perfect spot, ◆/◇ is the marker. A {@code ✦N} tag shows the running streak.
 */
final class StrikeBar {

    static final int SIZE = 21;
    /** Streak that earns "hot water": one extra lure fish and one extra green cell. */
    static final int HOT_STREAK = 5;
    private static final int MAX_ZONE = 8;

    private StrikeBar() {
    }

    static int zoneSize(double catchStat) {
        return Math.max(3, Math.min(MAX_ZONE, 3 + (int) Math.floor(catchStat / 28.0d)));
    }

    /** Hot-water streak widens the window by one cell (still capped). */
    static int zoneSize(double catchStat, int streak) {
        int base = zoneSize(catchStat);
        return streak >= HOT_STREAK ? Math.min(MAX_ZONE + 1, base + 1) : base;
    }

    static int randomZoneStart(int zoneSize, java.util.Random random) {
        int max = SIZE - zoneSize - 1;
        if (max <= 1) {
            return 1;
        }
        return 1 + random.nextInt(max);
    }

    static boolean inZone(int marker, int zoneStart, int zoneSize) {
        return marker >= zoneStart && marker < zoneStart + zoneSize;
    }

    /** Single gold cell at the heart of the window. */
    static int perfectCell(int zoneStart, int zoneSize) {
        return zoneStart + Math.max(0, zoneSize) / 2;
    }

    static String waitTitle(int remainingTicks, int streak) {
        String body = remainingTicks < 0
                ? "§3waiting"
                : "§f" + String.format(Locale.US, "%.1fs", remainingTicks / 20.0d);
        return "§bFishing §8• " + body + streakTag(streak);
    }

    static String approachTitle(boolean ready, int streak) {
        return "§bFishing §8• " + (ready ? "§eready…" : "§3something's circling") + streakTag(streak);
    }

    static String strikeTitle(int marker, int zoneStart, int zoneSize, boolean hot, int streak) {
        StringBuilder out = new StringBuilder("§bFishing  §8[");
        int zoneEnd = zoneStart + zoneSize;
        int perfect = perfectCell(zoneStart, zoneSize);
        boolean onPerfect = marker == perfect;
        for (int i = 0; i < SIZE; i++) {
            if (i == marker) {
                out.append(onPerfect ? "§6◆" : hot ? "§f◆" : "§e◇");
            } else if (i == perfect) {
                out.append(hot ? "§6█" : "§e█");
            } else if (i >= zoneStart && i < zoneEnd) {
                out.append(hot ? "§a█" : "§2█");
            } else {
                out.append("§8─");
            }
        }
        out.append("§8]  ");
        out.append(onPerfect ? "§6NOW" : hot ? "§eREEL" : "§7hit green");
        out.append(streakTag(streak));
        return out.toString();
    }

    static String streakTag(int streak) {
        if (streak < 2) {
            return "";
        }
        return (streak >= HOT_STREAK ? "  §6✦" : "  §e✦") + streak;
    }

    static double strikeProgress(int marker) {
        if (SIZE <= 1) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, marker / (double) (SIZE - 1)));
    }
}
