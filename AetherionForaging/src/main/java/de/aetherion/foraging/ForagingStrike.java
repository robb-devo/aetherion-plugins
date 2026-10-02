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

    /**
     * Stable green window for a tree — preview and live chop must match.
     * Hash of treeId; no ThreadLocalRandom so looking does not reshuffle.
     */
    static int zoneStartForTree(String treeId, int zoneSize) {
        int z = zoneSize(zoneSize);
        int minStart = Math.max(1, (int) Math.ceil(0.25d * (SIZE - 1)));
        int maxStart = SIZE - z - 1;
        if (maxStart < minStart) {
            return Math.max(1, maxStart);
        }
        if (maxStart == minStart) {
            return minStart;
        }
        int span = maxStart - minStart + 1;
        int h = treeId == null ? 0 : treeId.hashCode();
        // Mix bits so nearby tree keys do not clump into the same slot.
        h ^= (h >>> 16);
        h *= 0x45d9f3b;
        h ^= (h >>> 16);
        return minStart + Math.floorMod(h, span);
    }

    static boolean inZone(int marker, int zoneStart, int zoneSize) {
        return marker >= zoneStart && marker < zoneStart + zoneSize;
    }

    static String title(int marker, int zoneStart, int zoneSize, boolean hot) {
        StringBuilder out = new StringBuilder("§2Fell  §8[");
        paintBar(out, marker, zoneStart, zoneSize, hot);
        out.append("§8]  ");
        out.append(hot ? "§aCHOP" : "§7hit green");
        return out.toString();
    }

    /** Look-ahead bar: stationary marker, same green window as the live chop. */
    static String previewTitle(int zoneStart, int zoneSize) {
        StringBuilder out = new StringBuilder("§2Fell  §8[");
        paintBar(out, 0, zoneStart, zoneSize, false);
        out.append("§8]  §aREADY §8· §7LMB");
        return out.toString();
    }

    /** Same window, but this tree is on miss cooldown for this player. */
    static String coolingTitle(int zoneStart, int zoneSize, long secondsLeft) {
        StringBuilder out = new StringBuilder("§2Fell  §8[");
        paintBar(out, 0, zoneStart, zoneSize, false);
        long sec = Math.max(1L, secondsLeft);
        out.append("§8]  §c").append(sec).append("s §8· §7other trees OK");
        return out.toString();
    }

    private static void paintBar(StringBuilder out, int marker, int zoneStart, int zoneSize, boolean hot) {
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
    }

    static double progress(int marker) {
        if (SIZE <= 1) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, marker / (double) (SIZE - 1)));
    }
}
