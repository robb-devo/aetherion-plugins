package de.aetherion.guilds.model;

public final class IslandTiers {

    public static final int MAX_LEVEL = 5;

    private IslandTiers() {
    }

    public static int clamp(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    public static int platformRadius(int level) {
        return 8 + (clamp(level) - 1) * 4;
    }

    public static int buildRadius(int base, int level) {
        return Math.max(8, base) + (clamp(level) - 1) * 8;
    }

    public static int bankSlots(int level, int members) {
        int extra = members >= 8 ? 9 : 0;
        return Math.min(45, 9 * clamp(level) + extra);
    }

    public static long coinCap(int level, int members) {
        return 25_000L * clamp(level) * Math.max(1, members);
    }

    public static UpgradeCost upgradeCost(int fromLevel) {
        return switch (fromLevel) {
            case 1 -> new UpgradeCost(8, 0, 2_500L);
            case 2 -> new UpgradeCost(24, 1, 10_000L);
            case 3 -> new UpgradeCost(48, 3, 35_000L);
            case 4 -> new UpgradeCost(64, 8, 100_000L);
            default -> UpgradeCost.NONE;
        };
    }

    public record UpgradeCost(int compactedCobble, int cores, long coins) {
        public static final UpgradeCost NONE = new UpgradeCost(0, 0, 0L);

        public boolean isEmpty() {
            return compactedCobble <= 0 && cores <= 0 && coins <= 0L;
        }
    }
}
