package de.aetherion.items.recipe;

/**
 * Hypixel-style gear craft curve (no skill/account gates).
 *
 * <p>Shape rule: previous piece in the center, domain resources around it.
 * Unlock rule: soft predecessor only ({@code CraftedPredecessorRequirement}).
 *
 * <pre>
 * T1  Vanilla piece shape (~5–24 mats)
 * T2  Full wrap XXX/XCX/XXX — 8× vanilla or early compressed
 * T3  Full wrap — 8× compressed domain mats
 * T4  Full wrap / mixed wrap — 8× compacted domain mats
 * T5  Dual wrap ACA/ECE/ACA — compacted high-end corners+edges
 * </pre>
 *
 * <p>Volume should feel steeper each tier (Farming Armor feel) without feeling unfair.
 * Stats stay in {@code BalanceTargets}; this only governs craft cost shape.
 */
public final class CraftCurve {

    private CraftCurve() {
    }

    /** Full surround slots for T2–T4 single-mat wraps. */
    public static final int WRAP_SURROUND = 8;

    /** Dual-wrap uses 4 corners + 4 edges. */
    public static final int DUAL_WRAP_CORNERS = 4;
    public static final int DUAL_WRAP_EDGES = 4;
}
