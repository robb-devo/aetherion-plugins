package de.aetherion.guilds.model;

public final class BankTiers {

    public static final int MAX_LEVEL = 7;
    public static final long BASE_CAP = 5_000_000L;

    private BankTiers() {
    }

    public static int clamp(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    public static long coinCap(int bankLevel) {
        return switch (clamp(bankLevel)) {
            case 1 -> 12_500_000L;
            case 2 -> 25_000_000L;
            case 3 -> 50_000_000L;
            case 4 -> 100_000_000L;
            case 5 -> 250_000_000L;
            case 6 -> 500_000_000L;
            case 7 -> 1_000_000_000L;
            default -> BASE_CAP;
        };
    }

    public static int itemSlots(int bankLevel) {
        return Math.min(Guild.BANK_MAX_SLOTS, 27 + 9 * Math.min(2, clamp(bankLevel)));
    }

    public static UpgradeCost upgradeCost(int fromLevel) {
        return switch (fromLevel) {
            case 0 -> new UpgradeCost("compressed_cobblestone", 16, 0, "16 Compressed Cobblestone");
            case 1 -> new UpgradeCost("compressed_cobblestone", 48, 0, "48 Compressed Cobblestone");
            case 2 -> new UpgradeCost("compacted_cobblestone", 8, 0, "8 Compacted Cobblestone");
            case 3 -> new UpgradeCost("compacted_cobblestone", 16, 0, "16 Compacted Cobblestone");
            case 4 -> new UpgradeCost("compacted_cobblestone", 32, 0, "32 Compacted Cobblestone");
            case 5 -> new UpgradeCost("compacted_cobblestone", 48, 0, "48 Compacted Cobblestone");
            case 6 -> new UpgradeCost("compacted_cobblestone", 64, 0, "64 Compacted Cobblestone");
            default -> UpgradeCost.NONE;
        };
    }

    public record UpgradeCost(String itemId, int amount, long coins, String label) {
        public static final UpgradeCost NONE = new UpgradeCost("", 0, 0L, "");

        public boolean isEmpty() {
            return amount <= 0 && coins <= 0L;
        }
    }
}
