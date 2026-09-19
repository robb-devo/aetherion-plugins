package de.aetherion.items.economy;

/**
 * Documented coin curve for Aetherion (Skyblock-feel).
 *
 * <p>Listed values live in {@code economy.yml}. This class is the formula source of truth
 * used when generating that table and as a safety-net for new compressed resources.
 *
 * <pre>
 * compressed = round(unit × 128 × 1.4)
 * compacted  = round(compressed × 128 × 1.5)
 * refined    = round(compacted × 4)          // millstone crops
 * crafted    = round(ingredientSum × craftMargin(tier))
 * </pre>
 */
public final class EconomyCurve {

    public static final double COMPRESS_MULT = 1.4d;
    public static final double COMPACT_MULT = 1.5d;
    public static final double REFINE_MULT = 4.0d;
    public static final int STACK_RAW = 128; // 2×64

    public static final double MARGIN_T1 = 1.20d;
    public static final double MARGIN_T2 = 1.30d;
    public static final double MARGIN_T3 = 1.45d;
    public static final double MARGIN_T4 = 1.65d;
    public static final double MARGIN_T5 = 1.90d;
    public static final double MARGIN_CHARM_T1 = 1.25d;
    public static final double MARGIN_CHARM_T2 = 1.45d;
    public static final double MARGIN_CHARM_T3 = 1.70d;
    public static final double MARGIN_BOOSTER = 1.35d;
    public static final double MARGIN_MATERIAL = 1.55d;

    public static final long DEFAULT_QUARRY = 450L;
    public static final long QUARRY_SHARD = 180L;
    public static final long QUARRY_CORE = 1_400L;
    public static final long QUARRY_COMPRESSOR = 450L;
    public static final long QUARRY_COMPACTOR = 1_400L;

    private EconomyCurve() {
    }

    public static long compressed(long unitValue) {
        long raw = Math.max(1L, unitValue) * STACK_RAW;
        return Math.max(raw + 1L, Math.round(raw * COMPRESS_MULT));
    }

    public static long compacted(long compressedValue) {
        long packed = Math.max(1L, compressedValue) * STACK_RAW;
        return Math.max(packed + 1L, Math.round(packed * COMPACT_MULT));
    }

    public static long refined(long compactedValue) {
        return Math.max(compactedValue + 1L, Math.round(compactedValue * REFINE_MULT));
    }

    public static long craft(long ingredientSum, double margin) {
        if (ingredientSum <= 0L) {
            return 1L;
        }
        return Math.max(1L, Math.round(ingredientSum * margin));
    }

    public static double skillMargin(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1 -> MARGIN_T1;
            case 2 -> MARGIN_T2;
            case 3 -> MARGIN_T3;
            case 4 -> MARGIN_T4;
            default -> MARGIN_T5;
        };
    }

    public static double charmMargin(int tier) {
        return switch (Math.max(1, Math.min(3, tier))) {
            case 1 -> MARGIN_CHARM_T1;
            case 2 -> MARGIN_CHARM_T2;
            default -> MARGIN_CHARM_T3;
        };
    }
}
