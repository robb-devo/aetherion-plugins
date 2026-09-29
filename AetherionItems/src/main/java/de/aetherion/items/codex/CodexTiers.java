package de.aetherion.items.codex;

/**
 * Tier ladders and reward tables for the Codex (Collection, Bestiary, Skill seals).
 *
 * <p>Every ladder has nine tiers (I–IX) so every detail page reads the same. The scale decides how
 * far apart the rungs are: bulk stone climbs in the tens of thousands, a boss in single digits.
 * Rewards grow with the square of the tier, so the first rungs are snacks and IX is a trophy.
 */
public final class CodexTiers {

    public static final int MAX_TIER = 9;
    /** Collection / Bestiary levels per milestone (each milestone is one permanent perk step). */
    public static final int MILESTONE_EVERY = 10;

    /** How far apart a ladder's rungs are. The first five are Collection, the rest Bestiary. */
    public enum Scale {
        BULK("Bulk", 1.0d, 100, 500, 1_500, 5_000, 10_000, 25_000, 50_000, 100_000, 250_000),
        COMMON("Common", 1.0d, 50, 250, 750, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000),
        UNCOMMON("Uncommon", 1.2d, 25, 100, 250, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000),
        RARE("Rare", 1.5d, 10, 50, 150, 500, 1_000, 2_500, 5_000, 10_000, 25_000),
        PRECIOUS("Precious", 2.0d, 5, 25, 50, 150, 300, 750, 1_500, 3_000, 6_000),

        MOB_COMMON("Common", 1.0d, 1, 10, 25, 50, 100, 250, 500, 1_000, 2_500),
        MOB_TOUGH("Tough", 1.3d, 1, 5, 15, 30, 60, 125, 250, 500, 1_000),
        MOB_RARE("Rare", 2.0d, 1, 2, 3, 5, 10, 20, 35, 50, 100),
        ANIMAL("Wildlife", 0.8d, 1, 5, 15, 30, 60, 100, 200, 350, 500),
        SEA("Sea Creature", 1.6d, 1, 3, 10, 25, 50, 100, 200, 350, 500),
        BOSS("Boss", 3.0d, 1, 2, 3, 5, 10, 15, 25, 50, 100);

        private final String label;
        private final double weight;
        private final long[] rungs;

        Scale(String label, double weight, long... rungs) {
            this.label = label;
            this.weight = weight;
            this.rungs = rungs;
        }

        public String label() {
            return label;
        }

        public double weight() {
            return weight;
        }

        public long rung(int tier) {
            if (tier < 1) {
                return 0L;
            }
            return rungs[Math.min(tier, rungs.length) - 1];
        }

        public boolean bestiary() {
            return ordinal() >= MOB_COMMON.ordinal();
        }
    }

    /** What one tier pays when claimed. */
    public record Reward(long coins, long xp, long shards) {

        public static final Reward NONE = new Reward(0L, 0L, 0L);

        public Reward plus(Reward other) {
            if (other == null) {
                return this;
            }
            return new Reward(coins + other.coins, xp + other.xp, shards + other.shards);
        }

        public boolean empty() {
            return coins <= 0L && xp <= 0L && shards <= 0L;
        }

        /** {@code +5,000 coins · +80 Aetherion XP · +10 Shards}, colored. */
        public String line() {
            StringBuilder out = new StringBuilder();
            if (coins > 0L) {
                out.append("§6+").append(CodexText.number(coins)).append(" coins");
            }
            if (xp > 0L) {
                if (out.length() > 0) {
                    out.append(" §8· ");
                }
                out.append("§d+").append(CodexText.number(xp)).append(" Aetherion XP");
            }
            if (shards > 0L) {
                if (out.length() > 0) {
                    out.append(" §8· ");
                }
                out.append("§b+").append(CodexText.number(shards)).append(" Shards");
            }
            return out.length() == 0 ? "§8Nothing" : out.toString();
        }
    }

    private CodexTiers() {
    }

    /** Tier reached with {@code amount} (0 = not even tier I). */
    public static int tier(Scale scale, long amount) {
        if (scale == null || amount <= 0L) {
            return 0;
        }
        int tier = 0;
        for (int i = 1; i <= MAX_TIER; i++) {
            if (amount >= scale.rung(i)) {
                tier = i;
            } else {
                break;
            }
        }
        return tier;
    }

    /** Amount needed for the next tier, or {@code -1} once IX is reached. */
    public static long next(Scale scale, long amount) {
        int tier = tier(scale, amount);
        return tier >= MAX_TIER ? -1L : scale.rung(tier + 1);
    }

    /** 0–1 progress from the current rung to the next. 1 at IX. */
    public static double fill(Scale scale, long amount) {
        int tier = tier(scale, amount);
        if (tier >= MAX_TIER) {
            return 1.0d;
        }
        long from = tier == 0 ? 0L : scale.rung(tier);
        long to = scale.rung(tier + 1);
        if (to <= from) {
            return 1.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, (amount - from) / (double) (to - from)));
    }

    /** Reward for reaching {@code tier} on a Collection or Bestiary ladder. */
    public static Reward reward(Scale scale, int tier) {
        if (scale == null || tier < 1 || tier > MAX_TIER) {
            return Reward.NONE;
        }
        double weight = scale.weight();
        long square = (long) tier * tier;
        long coins;
        long xp;
        long shards = 0L;
        if (scale == Scale.BOSS) {
            coins = 1_500L * tier;
            xp = 40L * tier;
            shards = 5L * tier;
        } else if (scale.bestiary()) {
            coins = Math.round(100.0d * weight * square);
            xp = 15L * tier;
            if (tier == 5 || tier == 7 || tier == 9) {
                shards = Math.round((tier == 9 ? 40.0d : tier == 7 ? 20.0d : 8.0d) * weight);
            }
        } else {
            coins = Math.round(150.0d * weight * square);
            xp = 20L * tier;
            if (tier == 5 || tier == 7 || tier == 9) {
                shards = Math.round((tier == 9 ? 50.0d : tier == 7 ? 25.0d : 10.0d) * weight);
            }
        }
        return new Reward(roundCoins(coins), xp, shards);
    }

    /** Skill rarity seal: first time a skill reaches Uncommon (1) … Mythic (5). */
    public static Reward seal(int rarityTier) {
        return switch (rarityTier) {
            case 1 -> new Reward(2_500L, 0L, 10L);
            case 2 -> new Reward(10_000L, 0L, 25L);
            case 3 -> new Reward(22_500L, 0L, 60L);
            case 4 -> new Reward(40_000L, 0L, 150L);
            case 5 -> new Reward(62_500L, 0L, 400L);
            default -> Reward.NONE;
        };
    }

    /** Roman numeral for tiers I–IX (and up to 39 for safety). */
    public static String roman(int value) {
        if (value <= 0) {
            return "0";
        }
        String[] tens = {"", "X", "XX", "XXX"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        int clamped = Math.min(39, value);
        return tens[clamped / 10] + ones[clamped % 10];
    }

    private static long roundCoins(long coins) {
        if (coins >= 1_000L) {
            return Math.round(coins / 50.0d) * 50L;
        }
        return Math.max(10L, Math.round(coins / 10.0d) * 10L);
    }
}
