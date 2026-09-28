package de.aetherion.fishing.isle;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bait from the Bait Shack. Made from what the lake already gave you (the fish sink), carried as
 * charges on your profile, and spent when a fish takes it — a missed bite still eats the bait.
 */
public enum Bait {

    BREADCRUMB("Breadcrumb Mix", Material.BREAD, 16, 0L,
            recipe(Material.COD, 16),
            List.of("§7Bites come §f20% §7faster.")),
    GLOW_GRUBS("Glow Grubs", Material.GLOW_BERRIES, 12, 0L,
            recipe(Material.KELP, 8, Material.SALMON, 6),
            List.of("§7Night species bite §f2× §7as often.", "§7After dark: rare bites §f+30%§7.")),
    SILVER_SPINNER("Silver Spinner", Material.PRISMARINE_SHARD, 10, 0L,
            recipe(Material.PRISMARINE_SHARD, 3, Material.TROPICAL_FISH, 6),
            List.of("§7Rare bites §f+50%§7.", "§7Trophies turn up §f25% §7more.")),
    HEAVY_SINKER("Heavy Sinker", Material.HEAVY_CORE, 10, 0L,
            recipe(Material.PUFFERFISH, 2),
            List.of("§7Every fish rolls its weight §fone more time§7.", "§7Deep species (§fGar§7, §fSturgeon§7, §fWhiskers§7) §f1.6×§7."),
            "compressed_cod", 1),
    EMPERORS_FEAST("Emperor's Feast", Material.GOLDEN_CARROT, 4, 600L,
            recipe(),
            List.of("§6Legendary §7bites §f3×§7.", "§7The §6Goldscale Emperor §7will look at you", "§7without a Boiling streak."),
            "compressed_salmon", 1, "compressed_prismarine_shard", 1);

    private final String display;
    private final Material icon;
    private final int charges;
    private final long coins;
    private final Map<Material, Integer> plain;
    private final Map<String, Integer> custom;
    private final List<String> effect;

    Bait(String display, Material icon, int charges, long coins, Map<Material, Integer> plain,
         List<String> effect, Object... custom) {
        this.display = display;
        this.icon = icon;
        this.charges = charges;
        this.coins = coins;
        this.plain = plain;
        this.effect = effect;
        Map<String, Integer> extra = new LinkedHashMap<>();
        for (int i = 0; i + 1 < custom.length; i += 2) {
            extra.put((String) custom[i], (Integer) custom[i + 1]);
        }
        this.custom = extra;
    }

    private static Map<Material, Integer> recipe(Object... pairs) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put((Material) pairs[i], (Integer) pairs[i + 1]);
        }
        return out;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    /** Charges one batch makes. */
    public int charges() {
        return charges;
    }

    public long coins() {
        return coins;
    }

    /** Plain (vanilla) ingredients. */
    public Map<Material, Integer> plain() {
        return plain;
    }

    /** Aetherion item ingredients by item id (compressed resources). */
    public Map<String, Integer> custom() {
        return custom;
    }

    public List<String> effect() {
        return effect;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Bait byId(String id) {
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
