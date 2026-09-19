package de.aetherion.foraging;

import java.util.concurrent.ThreadLocalRandom;

final class ForagingStrike {

    static final int SIZE = 21;

    private ForagingStrike() {
    }

    static int zoneSize(int configured) {
        return Math.max(3, Math.min(6, configured));
    }

    /**
     * Green CHOP window never starts before ~25% of the bar (~1s prep at default speed).
     */
    static int randomZoneStart(int zoneSize) {
        int minStart = Math.max(1, (int) Math.ceil(0.25d * (SIZE - 1)));
        int maxStart = SIZE - zoneSize - 1;
        if (maxStart < minStart) {
            return Math.max(1, maxStart);
        }
        if (maxStart == minStart) {
            return minStart;
        }
        return minStart + ThreadLocalRandom.current().nextInt(maxStart - minStart + 1);
    }

    static boolean inZone(int marker, int zoneStart, int zoneSize) {
        return marker >= zoneStart && marker < zoneStart + zoneSize;
    }

    static String title(int marker, int zoneStart, int zoneSize, boolean hot) {
        StringBuilder out = new StringBuilder("§2Fell  §8[");
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
        out.append(hot ? "§aCHOP" : "§7hit green");
        return out.toString();
    }

    static double progress(int marker) {
        if (SIZE <= 1) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, marker / (double) (SIZE - 1)));
    }
}
