package de.aetherion.fishing;

import java.util.Locale;

final class StrikeBar {

    static final int SIZE = 21;

    private StrikeBar() {
    }

    static int zoneSize(double catchStat) {
        return Math.max(3, Math.min(8, 3 + (int) Math.floor(catchStat / 28.0d)));
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

    static String waitTitle(int remainingTicks) {
        if (remainingTicks < 0) {
            return "§bFishing §8• §3waiting";
        }
        return "§bFishing §8• §f" + String.format(Locale.US, "%.1fs", remainingTicks / 20.0d);
    }

    static String approachTitle() {
        return "§bFishing §8• §3closing in";
    }

    static String strikeTitle(int marker, int zoneStart, int zoneSize, boolean hot) {
        StringBuilder out = new StringBuilder("§bFishing  §8[");
        int zoneEnd = zoneStart + zoneSize;
        for (int i = 0; i < SIZE; i++) {
            if (i == marker) {
                out.append(hot ? "§f◆" : "§e◇");
            } else if (i >= zoneStart && i < zoneEnd) {
                out.append(hot ? "§a█" : "§2█");
            } else {
                out.append("§8─");
            }
        }
        out.append("§8]  ");
        out.append(hot ? "§eREEL" : "§7hit green");
        return out.toString();
    }

    static double strikeProgress(int marker) {
        if (SIZE <= 1) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, marker / (double) (SIZE - 1)));
    }
}
