package de.aetherion.items.item;

/**
 * Single source of truth for the Skyblock-style progression curve (REV 6 / Wave 2).
 *
 * <p>Stacking budget at <b>endgame comfortable</b> (hobby-server, Hypixel-inspired — not
 * Hypixel-endgame saturation). Full stack = best T5 set + T3 domain charm (pad)
 * + max relevant domain skills @100 + typical booster load.
 *
 * <pre>
 *   Set (tool + 4 armor)     45%
 *   Pad (T3 domain charm)    14%
 *   Skills (domain @100)     21%
 *   Booster (typical load)   20%
 * </pre>
 *
 * <p>Typical booster load: 10 Diamond on the tool, 6 Diamond on the chest,
 * 4 Gold on each other armor piece. Whale ceiling (14 Diamond × 5 pieces) may
 * overshoot comfortable by ~25–35%; that is intentional, not the target.
 *
 * <p>Comfortable full-stack bands:
 * <ul>
 *   <li>Mining Fortune 1100 · Mining Power 480</li>
 *   <li>Farming Fortune 1000 · Harvest 1200</li>
 *   <li>Foraging Fortune 900</li>
 *   <li>Fishing Fortune 900 · Fish Catch 1400</li>
 *   <li>Combat Damage 320 · Health 560 · Defense 420</li>
 * </ul>
 *
 * <p>T5 set alone is strong (~45% of the band) but must not already be endgame.
 * REV5 T5 Mining set was 860 Fortune set-alone — that is the whole comfortable
 * stack before pad/skills/boosters. Hypixel-raw ~8900 set-alone is rejected.
 *
 * <p>Inside the set: tool carries ~50–55% of the primary; armor the rest.
 * Charm T3 ≈ 14% of the full stack (a real pad, not a garnish).
 * Growth ≈ ×1.70–1.80 per tier on primaries. Crit% climbs slowly; Crit Damage steeper.
 */
public final class BalanceTargets {

    /** REV6: Wave 2 absolute-stat + stacking-budget rebase. */
    public static final int LADDER_REV = 6;

    /** Soft ceiling so invested gear still leaves room for the next tier. */
    public static final int BOOSTER_MAX_TOTAL = 14;

    private BalanceTargets() {
    }

    /* ── Combat sword: Dmg / AS / CC / CD ── */
    public static final double[][] COMBAT_SWORD = {
            // T1 must beat Simple Sword (10/2/4/38) on damage + crit
            {14, 3, 5, 42},
            {22, 5, 6, 46},
            {36, 8, 8, 62},
            {56, 12, 11, 80},
            {88, 18, 15, 102}
    };

    /* ── Combat armor: Def / HP / AS / Damage [/ CC / CD on chest] ── */
    public static final double[][] COMBAT_HELM = {
            {4, 8, 2, 1}, {7, 14, 3, 2}, {12, 24, 5, 4}, {20, 36, 8, 7}, {34, 46, 12, 10}
    };
    public static final double[][] COMBAT_CHEST = {
            {8, 12, 3, 2, 4, 25},
            {14, 22, 5, 4, 6, 36},
            {24, 38, 8, 8, 8, 52},
            {38, 56, 12, 14, 11, 72},
            {58, 78, 18, 22, 14, 94}
    };
    public static final double[][] COMBAT_LEGS = {
            {6, 10, 2, 1}, {10, 18, 4, 3}, {18, 30, 6, 5}, {30, 44, 9, 9}, {46, 58, 14, 14}
    };
    public static final double[][] COMBAT_BOOTS = {
            {4, 8, 1, 1}, {7, 14, 3, 2}, {12, 24, 4, 4}, {20, 34, 7, 7}, {34, 40, 10, 10}
    };

    /* ── Mining pick: MP / Fortune / Spread ── */
    public static final double[][] MINING_PICK = {
            {14, 24, 2},
            {26, 42, 3},
            {48, 74, 5},
            {96, 128, 8},
            {130, 250, 12}
    };

    /* ── Mining armor: Def / MP / Fortune / Spread ── */
    public static final double[][] MINING_HELM = {
            {3, 6, 8, 1}, {5, 10, 16, 1}, {9, 16, 26, 2}, {15, 22, 38, 3}, {24, 24, 52, 4}
    };
    public static final double[][] MINING_CHEST = {
            {6, 10, 12, 1}, {10, 16, 24, 2}, {16, 26, 38, 3}, {26, 36, 56, 5}, {42, 42, 80, 7}
    };
    public static final double[][] MINING_LEGS = {
            {5, 8, 10, 1}, {8, 14, 20, 1}, {14, 22, 32, 2}, {22, 30, 46, 4}, {34, 32, 64, 5}
    };
    public static final double[][] MINING_BOOTS = {
            {3, 6, 8, 1}, {5, 10, 16, 1}, {9, 16, 26, 2}, {15, 22, 38, 3}, {24, 24, 49, 4}
    };

    /* ── Farming hoe: Fortune / Harvest ── */
    public static final double[][] FARMING_HOE = {
            {18, 28}, {32, 50}, {56, 88}, {100, 155}, {210, 260}
    };
    /* armor: Def / Fortune / Harvest [/ Speed boots] */
    public static final double[][] FARMING_HELM = {
            {3, 8, 10}, {5, 14, 18}, {9, 24, 32}, {15, 36, 48}, {24, 48, 58}
    };
    public static final double[][] FARMING_CHEST = {
            {6, 12, 14}, {10, 20, 24}, {16, 34, 42}, {26, 52, 64}, {40, 76, 88}
    };
    public static final double[][] FARMING_LEGS = {
            {5, 10, 12}, {8, 16, 20}, {14, 28, 34}, {22, 42, 52}, {34, 60, 70}
    };
    public static final double[][] FARMING_BOOTS = {
            {3, 8, 10, 2}, {5, 12, 16, 3}, {9, 22, 28, 5}, {14, 34, 42, 8}, {22, 48, 56, 12}
    };

    /* ── Foraging axe: MP / Fortune / Spread ── */
    public static final double[][] FORAGING_AXE = {
            {10, 16, 2}, {18, 28, 3}, {32, 50, 5}, {56, 88, 8}, {96, 180, 14}
    };
    public static final double[][] FORAGING_HELM = {
            {3, 8, 3}, {5, 14, 5}, {9, 24, 8}, {15, 36, 12}, {24, 46, 12}
    };
    public static final double[][] FORAGING_CHEST = {
            {6, 12, 4}, {10, 20, 7}, {16, 34, 12}, {26, 52, 18}, {40, 72, 18}
    };
    public static final double[][] FORAGING_LEGS = {
            {5, 10, 3}, {8, 16, 6}, {14, 28, 10}, {22, 42, 14}, {34, 58, 14}
    };
    public static final double[][] FORAGING_BOOTS = {
            {3, 8, 2, 2}, {5, 12, 4, 3}, {9, 22, 7, 5}, {14, 34, 10, 8}, {22, 49, 10, 12}
    };

    /* ── Fishing rod: Fortune / FishSpeed / FishCatch ── */
    public static final double[][] FISHING_ROD = {
            {18, 4, 30}, {32, 7, 52}, {56, 12, 90}, {100, 20, 160}, {180, 32, 320}
    };
    /* armor: Def / Fortune / FS / FC [/ Speed boots] */
    public static final double[][] FISHING_HELM = {
            {3, 8, 2, 10}, {5, 14, 3, 18}, {9, 24, 6, 32}, {15, 36, 10, 48}, {24, 46, 16, 62}
    };
    public static final double[][] FISHING_CHEST = {
            {6, 12, 3, 14}, {10, 20, 5, 24}, {16, 34, 8, 42}, {26, 52, 13, 68}, {40, 72, 22, 98}
    };
    public static final double[][] FISHING_LEGS = {
            {5, 10, 2, 12}, {8, 16, 4, 20}, {14, 28, 7, 34}, {22, 42, 11, 54}, {34, 58, 18, 78}
    };
    public static final double[][] FISHING_BOOTS = {
            {3, 8, 2, 10, 2}, {5, 12, 3, 16, 3}, {9, 22, 6, 28, 5}, {14, 34, 10, 46, 8}, {22, 49, 16, 62, 12}
    };

    /* ── Charms T1–T3 (pad ≈ 14% of comfortable full stack) ── */
    public static final double[][] CHARM_COMBAT = {
            {14, 2, 12}, {26, 4, 22}, {42, 7, 36}
    }; // Dmg / CC / CD
    public static final double[][] CHARM_MINING = {
            {8, 52, 2}, {15, 92, 4}, {24, 150, 6}
    }; // MP / Fort / Spread
    public static final double[][] CHARM_FORAGING = {
            {42, 2, 2}, {74, 4, 3}, {120, 7, 5}
    }; // Fort / Speed / Spread
    public static final double[][] CHARM_FARMING = {
            {46, 54}, {84, 98}, {135, 160}
    }; // Fort / Harvest
    public static final double[][] CHARM_FISHING = {
            {42, 5, 64}, {74, 10, 116}, {120, 16, 190}
    }; // Fort / FS / FC
    public static final double[][] CHARM_UTILITY = {
            {3, 4, 18}, {5, 7, 32}, {8, 12, 52}
    }; // Speed / Catch / HP

    public static double at(double[][] table, int tier, int index) {
        int t = Math.max(1, Math.min(table.length, tier)) - 1;
        return table[t][index];
    }
}
