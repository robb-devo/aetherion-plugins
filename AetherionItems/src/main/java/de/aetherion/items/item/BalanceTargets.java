package de.aetherion.items.item;

/**
 * Single source of truth for the Skyblock-style progression curve (REV 5).
 *
 * <p>Budgets (no boosters):
 * <ul>
 *   <li>Tool carries ~60% of a skill's primary stats; armor set ~40%.</li>
 *   <li>Charm T3 ≈ one mid armor piece of that domain.</li>
 *   <li>7 skills @ L100 ≈ 15–25% of a T5 full set.</li>
 *   <li>Common pet core ≈ 10–20% of a T1 set piece; Mythic pet &lt; one T4 piece.</li>
 *   <li>Full Mythic booster socket ≈ +20–35% on that piece (not 3×).</li>
 * </ul>
 *
 * <p>Growth ≈ ×1.80 per tier on primary stats. Crit% climbs slowly; Crit Damage steeper.
 */
public final class BalanceTargets {

    /** REV5: heal combat/mining tools stuck on pre-curve factory stats after a bad migrate. */
    public static final int LADDER_REV = 5;

    /** Soft ceiling so invested gear still leaves room for the next tier. */
    public static final int BOOSTER_MAX_TOTAL = 14;

    private BalanceTargets() {
    }

    /* ── Combat sword: Dmg / AS / CC / CD ── */
    public static final double[][] COMBAT_SWORD = {
            // T1 must beat Simple Sword (10/2/4/38) on damage + crit
            {14, 3, 5, 42},
            {22, 5, 6, 40},
            {40, 9, 9, 58},
            {72, 14, 13, 82},
            {130, 22, 18, 110}
    };

    /* ── Combat armor: Def / HP / AS / Damage [/ CC / CD on chest] ── */
    public static final double[][] COMBAT_HELM = {
            {4, 8, 2, 1}, {8, 14, 3, 2}, {14, 26, 5, 4}, {26, 46, 9, 7}, {46, 80, 14, 12}
    };
    public static final double[][] COMBAT_CHEST = {
            {8, 12, 3, 2, 4, 25},
            {16, 22, 5, 4, 6, 38},
            {30, 42, 9, 8, 9, 55},
            {54, 74, 14, 14, 12, 78},
            {95, 130, 22, 24, 16, 105}
    };
    public static final double[][] COMBAT_LEGS = {
            {6, 10, 2, 1}, {12, 18, 4, 3}, {22, 34, 7, 6}, {40, 60, 11, 10}, {70, 105, 17, 18}
    };
    public static final double[][] COMBAT_BOOTS = {
            {4, 8, 1, 1}, {8, 14, 3, 2}, {14, 26, 5, 4}, {26, 46, 8, 7}, {46, 80, 12, 12}
    };

    /* ── Mining pick: MP / Fortune / Spread ── */
    public static final double[][] MINING_PICK = {
            {14, 24, 2},
            {26, 44, 4},
            {48, 80, 7},
            {88, 145, 13},
            {160, 260, 22}
    };

    /* ── Mining armor: Def / MP / Fortune / Spread ── */
    public static final double[][] MINING_HELM = {
            {3, 6, 10, 1}, {6, 12, 20, 2}, {11, 22, 38, 4}, {20, 40, 70, 8}, {36, 72, 125, 14}
    };
    public static final double[][] MINING_CHEST = {
            {6, 10, 16, 2}, {12, 20, 32, 4}, {22, 36, 58, 7}, {40, 65, 105, 13}, {70, 120, 190, 22}
    };
    public static final double[][] MINING_LEGS = {
            {5, 8, 12, 1}, {10, 16, 26, 3}, {18, 30, 48, 5}, {32, 54, 88, 10}, {56, 98, 160, 18}
    };
    public static final double[][] MINING_BOOTS = {
            {3, 6, 10, 1}, {6, 12, 20, 2}, {11, 22, 38, 4}, {20, 40, 70, 8}, {36, 72, 125, 14}
    };

    /* ── Farming hoe: Fortune / Harvest ── */
    public static final double[][] FARMING_HOE = {
            {18, 28}, {34, 52}, {62, 95}, {110, 170}, {200, 300}
    };
    /* armor: Def / Fortune / Harvest [/ Speed boots] */
    public static final double[][] FARMING_HELM = {
            {3, 8, 10}, {6, 16, 20}, {11, 30, 38}, {20, 55, 70}, {36, 100, 125}
    };
    public static final double[][] FARMING_CHEST = {
            {6, 12, 14}, {11, 22, 28}, {20, 42, 52}, {36, 76, 95}, {64, 140, 170}
    };
    public static final double[][] FARMING_LEGS = {
            {5, 10, 12}, {9, 18, 24}, {16, 34, 44}, {30, 62, 80}, {52, 110, 145}
    };
    public static final double[][] FARMING_BOOTS = {
            {3, 8, 10, 2}, {5, 14, 18, 4}, {10, 26, 34, 7}, {18, 48, 62, 11}, {32, 88, 110, 16}
    };

    /* ── Foraging axe: MP / Fortune / Spread ── */
    public static final double[][] FORAGING_AXE = {
            {10, 18, 2}, {18, 34, 4}, {34, 62, 7}, {62, 110, 13}, {110, 200, 22}
    };
    public static final double[][] FORAGING_HELM = {
            {3, 8, 4}, {6, 16, 8}, {11, 30, 14}, {20, 55, 26}, {36, 100, 46}
    };
    public static final double[][] FORAGING_CHEST = {
            {6, 12, 5}, {11, 22, 10}, {20, 42, 18}, {36, 76, 34}, {64, 140, 60}
    };
    public static final double[][] FORAGING_LEGS = {
            {5, 10, 4}, {9, 18, 9}, {16, 34, 16}, {30, 62, 30}, {52, 110, 52}
    };
    public static final double[][] FORAGING_BOOTS = {
            {3, 8, 3, 2}, {5, 14, 7, 4}, {10, 26, 12, 7}, {18, 48, 22, 11}, {32, 88, 40, 16}
    };

    /* ── Fishing rod: Fortune / FishSpeed / FishCatch ── */
    public static final double[][] FISHING_ROD = {
            {18, 4, 32}, {34, 8, 58}, {62, 14, 105}, {110, 24, 190}, {200, 38, 340}
    };
    /* armor: Def / Fortune / FS / FC [/ Speed boots] */
    public static final double[][] FISHING_HELM = {
            {3, 8, 2, 10}, {6, 16, 4, 20}, {11, 30, 7, 38}, {20, 55, 12, 70}, {36, 100, 20, 125}
    };
    public static final double[][] FISHING_CHEST = {
            {6, 12, 3, 14}, {11, 22, 5, 28}, {20, 42, 10, 52}, {36, 76, 16, 95}, {64, 140, 28, 170}
    };
    public static final double[][] FISHING_LEGS = {
            {5, 10, 2, 12}, {9, 18, 4, 24}, {16, 34, 7, 44}, {30, 62, 12, 80}, {52, 110, 20, 145}
    };
    public static final double[][] FISHING_BOOTS = {
            {3, 8, 2, 10, 2}, {5, 14, 4, 20, 4}, {10, 26, 7, 38, 7}, {18, 48, 12, 70, 11}, {32, 88, 20, 125, 16}
    };

    /* ── Charms T1–T3 (≈ one armor piece) ── */
    public static final double[][] CHARM_COMBAT = {
            {6, 2, 8}, {12, 4, 14}, {22, 7, 24}
    }; // Dmg / CC / CD
    public static final double[][] CHARM_MINING = {
            {5, 8, 1}, {10, 16, 2}, {18, 30, 4}
    }; // MP / Fort / Spread
    public static final double[][] CHARM_FORAGING = {
            {8, 2, 1}, {16, 4, 2}, {30, 7, 4}
    }; // Fort / Speed / Spread
    public static final double[][] CHARM_FARMING = {
            {8, 10}, {16, 20}, {30, 38}
    }; // Fort / Harvest
    public static final double[][] CHARM_FISHING = {
            {6, 3, 10}, {12, 6, 20}, {22, 11, 38}
    }; // Fort / FS / FC
    public static final double[][] CHARM_UTILITY = {
            {3, 3, 6}, {5, 6, 12}, {9, 10, 22}
    }; // Speed / Catch / HP

    public static double at(double[][] table, int tier, int index) {
        int t = Math.max(1, Math.min(table.length, tier)) - 1;
        return table[t][index];
    }
}
