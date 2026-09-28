package de.aetherion.fishing.isle;

import de.aetherion.fishing.LureHead;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What lives in Eldervale's waters. Every landed cast on the isle is one of these, weighed on the
 * spot. Species live in particular waters (walk the map), some only rise at night or in the rain
 * (come back), and two are legendary (earn the bite). The Angler's Log keeps count.
 */
public enum Species {

    SILVER_PERCH("Silver Perch", Rarity.COMMON, 0.2d, 1.6d, LureHead.Look.COD, "LAKE",
            1.0d, 1.0d, 1.0d, "The lake's small change."),
    MUDGILL_CARP("Mudgill Carp", Rarity.COMMON, 1.0d, 9.0d, LureHead.Look.COD, "LAKE",
            1.0d, 1.0d, 1.0d, "Eats anything. Mostly regrets."),
    BLUEGILL_BREAM("Bluegill Bream", Rarity.COMMON, 0.3d, 1.4d, LureHead.Look.TROPICAL,
            "LAKE,wishing_fountain:1.5,tower_pools:1.0", 1.3d, 0.6d, 1.0d, "Flat, blue and smug about it."),
    BROOK_TROUT("Brook Trout", Rarity.COMMON, 0.3d, 2.5d, LureHead.Look.SALMON, "TARN",
            1.0d, 1.0d, 1.0d, "Cold water, bad attitude."),

    REED_PIKE("Reed Pike", Rarity.UNCOMMON, 2.0d, 12.0d, LureHead.Look.COD,
            "reedwater:1.5,westwater:1.2,boathouse_reach:0.8", 1.0d, 1.0d, 1.0d, "All teeth, no manners."),
    LANTERN_EEL("Lantern Eel", Rarity.UNCOMMON, 1.0d, 6.0d, LureHead.Look.TROPICAL,
            "lantern_cove:1.5,bellwater_cove:1.0", 0.35d, 2.5d, 1.0d, "Glows when it's angry. It's always angry."),
    GLASSWING_DACE("Glasswing Dace", Rarity.UNCOMMON, 0.2d, 1.2d, LureHead.Look.TROPICAL,
            "mirror_reach:1.6,lantern_cove:0.5", 1.5d, 0.5d, 1.0d, "You can read the lakebed through it."),
    DEEPWATER_GAR("Deepwater Gar", Rarity.UNCOMMON, 3.0d, 15.0d, LureHead.Look.COD,
            "south_deep:1.5,mirror_reach:0.9", 1.0d, 1.0d, 1.0d, "Older than the pier. Longer, too."),
    BELL_SNAPPER("Bell Snapper", Rarity.UNCOMMON, 1.0d, 7.0d, LureHead.Look.SALMON,
            "bellwater_cove:1.8,lantern_cove:0.6", 1.0d, 1.0d, 1.0d, "Bites on the hour. Every hour."),
    CLIFF_GRAYLING("Cliff Grayling", Rarity.UNCOMMON, 0.4d, 2.8d, LureHead.Look.SALMON,
            "westcliff_pools:1.6,skyfall_tarns:0.8,eastridge_tarns:0.6", 1.0d, 1.0d, 1.0d,
            "Waterfalls are just stairs to it."),

    EMBER_KOI("Ember Koi", Rarity.RARE, 1.0d, 8.0d, LureHead.Look.TROPICAL,
            "eastridge_tarns:1.6,tower_pools:1.2", 1.0d, 1.0d, 1.0d, "Warm springs, warmer temper."),
    CLOUDSCALE_CHAR("Cloudscale Char", Rarity.RARE, 2.0d, 10.0d, LureHead.Look.SALMON,
            "skyfall_tarns:1.5,westcliff_pools:0.5", 1.0d, 1.0d, 3.0d, "Only rises when the sky is crying."),
    MIRRORBACK_STURGEON("Mirrorback Sturgeon", Rarity.RARE, 15.0d, 70.0d, LureHead.Look.COD,
            "mirror_reach:1.5", 1.0d, 1.0d, 1.0d, "Wears the sky on its back."),
    OLD_WHISKERS("Old Whiskers", Rarity.RARE, 10.0d, 48.0d, LureHead.Look.COD,
            "south_deep:1.4,boathouse_reach:0.8", 0.7d, 2.0d, 1.0d, "A catfish with a name. Won't tell you it."),
    WISHING_KOI("Wishing Koi", Rarity.RARE, 0.8d, 4.5d, LureHead.Look.TROPICAL,
            "wishing_fountain:4.0", 1.0d, 1.0d, 1.0d, "Somebody wished for it. Somebody else caught it."),

    SILVERRUN_HERRING("Silverrun Herring", Rarity.RUN, 0.2d, 0.9d, LureHead.Look.COD, "LAKE",
            1.0d, 1.0d, 1.0d, "They only ever run together."),

    GOLDSCALE_EMPEROR("Goldscale Emperor", Rarity.LEGENDARY, 25.0d, 90.0d, LureHead.Look.SALMON,
            "mirror_reach:1.4,south_deep:1.4,LAKE:0.6", 1.0d, 1.0d, 1.0d,
            "Lake royalty. Takes a tax on every bite."),
    PALE_GHOST("The Pale Ghost", Rarity.LEGENDARY, 4.0d, 22.0d, LureHead.Look.COD, "TARN:1.0,LAKE:0.5",
            0.0d, 1.0d, 1.0d, "Seen by many. Landed by none. Until now.");

    public enum Rarity {
        COMMON("Common", "§f", 100.0d, 1, 60L, 20),
        UNCOMMON("Uncommon", "§a", 30.0d, 2, 140L, 40),
        RARE("Rare", "§9", 6.0d, 4, 450L, 90),
        LEGENDARY("Legendary", "§6", 0.35d, 8, 2_400L, 300),
        RUN("Run", "§b", 0.0d, 3, 90L, 40);

        private final String label;
        private final String color;
        private final double weight;
        private final int points;
        private final long trophyBase;
        private final int firstXp;

        Rarity(String label, String color, double weight, int points, long trophyBase, int firstXp) {
            this.label = label;
            this.color = color;
            this.weight = weight;
            this.points = points;
            this.trophyBase = trophyBase;
            this.firstXp = firstXp;
        }

        public String label() {
            return label;
        }

        public String color() {
            return color;
        }

        /** Base bite weight before place / heat / bait. */
        public double weight() {
            return weight;
        }

        /** Angler's Log points for the first catch. */
        public int points() {
            return points;
        }

        public long trophyBase() {
            return trophyBase;
        }

        /** Fishing XP for the first catch. */
        public int firstXp() {
            return firstXp;
        }

        /** Rare and legendary fish are always trophies; the rest only when heavy. */
        public boolean alwaysTrophy() {
            return this == RARE || this == LEGENDARY;
        }
    }

    private final String display;
    private final Rarity rarity;
    private final double minKg;
    private final double maxKg;
    private final LureHead.Look look;
    private final Map<String, Double> homes;
    private final double dayMul;
    private final double nightMul;
    private final double rainMul;
    private final String line;

    Species(String display, Rarity rarity, double minKg, double maxKg, LureHead.Look look, String homes,
            double dayMul, double nightMul, double rainMul, String line) {
        this.display = display;
        this.rarity = rarity;
        this.minKg = minKg;
        this.maxKg = maxKg;
        this.look = look;
        this.homes = parseHomes(homes);
        this.dayMul = dayMul;
        this.nightMul = nightMul;
        this.rainMul = rainMul;
        this.line = line;
    }

    private static Map<String, Double> parseHomes(String raw) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String token : raw.split(",")) {
            String[] parts = token.trim().split(":");
            out.put(parts[0].trim(), parts.length > 1 ? Double.parseDouble(parts[1]) : 1.0d);
        }
        return out;
    }

    public String display() {
        return display;
    }

    public String colored() {
        return rarity.color() + display;
    }

    public Rarity rarity() {
        return rarity;
    }

    public double minKg() {
        return minKg;
    }

    public double maxKg() {
        return maxKg;
    }

    public LureHead.Look look() {
        return look;
    }

    public String line() {
        return line;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * How at home this species is in {@code water} (0 = never bites there). A named water wins over
     * the LAKE / TARN group; {@code null} water = open lake-level water on the isle.
     */
    public double home(Waters.Water water) {
        if (water != null) {
            Double exact = homes.get(water.id());
            if (exact != null) {
                return exact;
            }
            Double group = switch (water.kind()) {
                case LAKE -> homes.get("LAKE");
                case TARN -> homes.get("TARN");
                case FOUNTAIN -> null;
            };
            return group == null ? 0.0d : group;
        }
        Double lake = homes.get("LAKE");
        return lake == null ? 0.0d : lake * 0.8d;
    }

    /** Day / night / rain factor (0 = not now). The Pale Ghost wants night and rain together. */
    public double when(boolean night, boolean rain) {
        if (this == PALE_GHOST && !(night && rain)) {
            return 0.0d;
        }
        double factor = night ? nightMul : dayMul;
        if (rain) {
            factor *= rainMul;
        }
        return factor;
    }

    /** Home keys: water ids plus the LAKE / TARN groups. */
    java.util.Set<String> homeIds() {
        return homes.keySet();
    }

    /** Where it lives, for Log hints ("Reedwater · Westwater"). */
    public String haunts(Waters waters) {
        StringBuilder out = new StringBuilder();
        for (String key : homes.keySet()) {
            String name = switch (key) {
                case "LAKE" -> "any lake water";
                case "TARN" -> "the highland tarns";
                default -> {
                    Waters.Water water = waters.byId(key);
                    yield water == null ? key.replace('_', ' ') : water.name();
                }
            };
            if (out.length() > 0) {
                out.append(" · ");
            }
            out.append(name);
        }
        return out.toString();
    }

    /** Condition hint for the Log ("night", "rain", …) or {@code null}. */
    public String timeHint() {
        if (this == PALE_GHOST) {
            return "rainy nights only";
        }
        if (this == SILVERRUN_HERRING) {
            return "only during a Silver Run";
        }
        if (this == GOLDSCALE_EMPEROR) {
            return "a Boiling streak, a shoal or a feast";
        }
        if (nightMul >= 2.0d) {
            return "rises at night";
        }
        if (dayMul >= 1.5d) {
            return "likes daylight";
        }
        if (rainMul >= 2.0d) {
            return "rises in the rain";
        }
        return null;
    }

    /** Share of the weight range, 0–1. */
    public double share(double kg) {
        return Math.max(0.0d, Math.min(1.0d, (kg - minKg) / Math.max(0.01d, maxKg - minKg)));
    }

    public static Species byId(String id) {
        if (id == null) {
            return null;
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
