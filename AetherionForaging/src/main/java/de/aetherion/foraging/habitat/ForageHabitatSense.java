package de.aetherion.foraging.habitat;

import de.aetherion.foraging.AetherionForaging;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forage-isle-only: infer habitat from nearby wood / snow / mushrooms (no Area Tool writes).
 */
public final class ForageHabitatSense {

    private final AetherionForaging plugin;
    private final List<Rule> rules = new ArrayList<>();
    private final Map<Material, List<Rule>> byMaterial = new HashMap<>();
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();
    private boolean enabled = true;
    private int radius = 8;
    private int step = 4;
    private int yRange = 4;
    private long cacheMs = 4000L;

    public ForageHabitatSense(AetherionForaging plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        rules.clear();
        byMaterial.clear();
        cache.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("habitat-sense");
        if (root == null) {
            enabled = true;
            loadDefaults();
            indexRules();
            return;
        }
        enabled = root.getBoolean("enabled", true);
        radius = Math.max(4, Math.min(24, root.getInt("radius", 8)));
        step = Math.max(1, Math.min(4, root.getInt("step", 4)));
        yRange = Math.max(2, Math.min(16, root.getInt("y-range", 4)));
        cacheMs = Math.max(250L, root.getLong("cache-ms", 4000L));
        ConfigurationSection rulesSec = root.getConfigurationSection("rules");
        if (rulesSec == null || rulesSec.getKeys(false).isEmpty()) {
            loadDefaults();
            indexRules();
            return;
        }
        for (String id : rulesSec.getKeys(false)) {
            ConfigurationSection sec = rulesSec.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }
            Set<Material> mats = EnumSet.noneOf(Material.class);
            for (String raw : sec.getStringList("materials")) {
                addMaterialToken(mats, raw);
            }
            if (mats.isEmpty()) {
                continue;
            }
            rules.add(new Rule(
                    id.toLowerCase(Locale.ROOT),
                    sec.getString("display", pretty(id)),
                    sec.getInt("priority", 0),
                    mats
            ));
        }
        rules.sort(Comparator.comparingInt(Rule::priority).reversed());
        indexRules();
        plugin.getLogger().info("Forage habitat-sense: " + rules.size()
                + " rules, radius=" + radius + " (isle footprint only)");
    }

    private void indexRules() {
        byMaterial.clear();
        for (Rule rule : rules) {
            for (Material mat : rule.materials()) {
                byMaterial.computeIfAbsent(mat, ignored -> new ArrayList<>(2)).add(rule);
            }
        }
    }

    private void loadDefaults() {
        rules.add(rule("snow", "Snow", 55,
                "SNOW", "SNOW_BLOCK", "POWDER_SNOW", "ICE", "PACKED_ICE", "BLUE_ICE",
                "SPRUCE_LOG", "SPRUCE_WOOD", "STRIPPED_SPRUCE_LOG", "STRIPPED_SPRUCE_WOOD",
                "SPRUCE_LEAVES", "SPRUCE_SAPLING"));
        rules.add(rule("dark_thicket", "Dark Thicket", 50,
                "DARK_OAK_LOG", "DARK_OAK_WOOD", "STRIPPED_DARK_OAK_LOG", "STRIPPED_DARK_OAK_WOOD",
                "DARK_OAK_LEAVES", "DARK_OAK_SAPLING",
                "RED_MUSHROOM", "BROWN_MUSHROOM", "RED_MUSHROOM_BLOCK", "BROWN_MUSHROOM_BLOCK",
                "MUSHROOM_STEM", "MYCELIUM"));
        rules.add(rule("flower", "Flower Fields", 45,
                "CHERRY_LOG", "CHERRY_WOOD", "STRIPPED_CHERRY_LOG", "STRIPPED_CHERRY_WOOD",
                "CHERRY_LEAVES", "CHERRY_SAPLING", "PINK_PETALS", "CHERRY_BUTTON"));
        rules.add(rule("jungle", "Jungle", 40,
                "JUNGLE_LOG", "JUNGLE_WOOD", "STRIPPED_JUNGLE_LOG", "STRIPPED_JUNGLE_WOOD",
                "JUNGLE_LEAVES", "JUNGLE_SAPLING", "BAMBOO", "BAMBOO_BLOCK", "BAMBOO_MOSAIC", "COCOA"));
        rules.add(rule("swamp", "Swamp", 40,
                "MANGROVE_LOG", "MANGROVE_WOOD", "STRIPPED_MANGROVE_LOG", "STRIPPED_MANGROVE_WOOD",
                "MANGROVE_LEAVES", "MANGROVE_ROOTS", "MUDDY_MANGROVE_ROOTS", "MANGROVE_PROPAGULE",
                "MUD", "PACKED_MUD", "LILY_PAD", "FROGLIGHT"));
        rules.add(rule("savanna", "Savanna", 38,
                "ACACIA_LOG", "ACACIA_WOOD", "STRIPPED_ACACIA_LOG", "STRIPPED_ACACIA_WOOD",
                "ACACIA_LEAVES", "ACACIA_SAPLING",
                "TERRACOTTA", "RED_SAND", "COARSE_DIRT"));
        rules.add(rule("lush", "Lush", 35,
                "MOSS_BLOCK", "MOSS_CARPET", "AZALEA", "FLOWERING_AZALEA",
                "AZALEA_LEAVES", "FLOWERING_AZALEA_LEAVES", "CAVE_VINES", "CAVE_VINES_PLANT",
                "BIG_DRIPLEAF", "SMALL_DRIPLEAF", "SPORE_BLOSSOM"));
        rules.add(rule("plains", "Plains", 15,
                "OAK_LOG", "OAK_WOOD", "STRIPPED_OAK_LOG", "STRIPPED_OAK_WOOD", "OAK_LEAVES", "OAK_SAPLING",
                "BIRCH_LOG", "BIRCH_WOOD", "STRIPPED_BIRCH_LOG", "STRIPPED_BIRCH_WOOD",
                "BIRCH_LEAVES", "BIRCH_SAPLING"));
        rules.sort(Comparator.comparingInt(Rule::priority).reversed());
        plugin.getLogger().info("Forage habitat-sense defaults loaded (" + rules.size() + " rules)");
        indexRules();
    }

    private static Rule rule(String id, String display, int priority, String... mats) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String m : mats) {
            addMaterialToken(set, m);
        }
        return new Rule(id, display, priority, set);
    }

    private static void addMaterialToken(Set<Material> out, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        if (key.startsWith("TAG:")) {
            // optional future
            return;
        }
        try {
            out.add(Material.valueOf(key));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String detectDisplay(Location at) {
        Rule hit = detect(at);
        return hit == null ? null : hit.display();
    }

    public Rule detect(Location at) {
        if (!enabled || at == null || at.getWorld() == null) {
            return null;
        }
        if (!ForageHabitatService.inIsleFootprint(at)) {
            return null;
        }
        long key = cacheKey(at);
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(key);
        if (cached != null && now - cached.atMs < cacheMs) {
            return cached.rule;
        }
        Rule found = scan(at);
        cache.put(key, new CacheEntry(found, now));
        if (cache.size() > 4000) {
            cache.clear();
        }
        return found;
    }

    private Rule scan(Location at) {
        World world = at.getWorld();
        int cx = at.getBlockX();
        int cy = at.getBlockY();
        int cz = at.getBlockZ();
        Map<String, Integer> scores = new HashMap<>();
        Map<String, Rule> byId = new HashMap<>();
        for (Rule rule : rules) {
            byId.put(rule.id(), rule);
            scores.put(rule.id(), 0);
        }
        int r = radius;
        for (int dx = -r; dx <= r; dx += step) {
            for (int dz = -r; dz <= r; dz += step) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                for (int dy = -yRange; dy <= yRange; dy += step) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material mat = block.getType();
                    if (mat.isAir() || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) {
                        continue;
                    }
                    List<Rule> matched = byMaterial.get(mat);
                    if (matched == null || matched.isEmpty()) {
                        continue;
                    }
                    int weight = weightOf(mat);
                    for (Rule rule : matched) {
                        scores.merge(rule.id(), weight, Integer::sum);
                    }
                }
            }
        }
        String bestId = null;
        int bestScore = 0;
        int bestPriority = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            int score = e.getValue();
            if (score <= 0) {
                continue;
            }
            Rule rule = byId.get(e.getKey());
            if (rule == null) {
                continue;
            }
            if (score > bestScore || (score == bestScore && rule.priority() > bestPriority)) {
                bestScore = score;
                bestPriority = rule.priority();
                bestId = e.getKey();
            }
        }
        // Need a little evidence so open water / bare rock doesn't flicker.
        int minHits = plugin.getConfig().getInt("habitat-sense.min-score", 3);
        if (bestId == null || bestScore < minHits) {
            return null;
        }
        return byId.get(bestId);
    }

    private static int weightOf(Material mat) {
        // Avoid Tag lookups — they are expensive and habitat sense runs from TAB.
        String n = mat.name();
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_LEAVES")
                || n.endsWith("_STEM") || n.endsWith("_HYPHAE") || n.contains("LEAVES")) {
            return 3;
        }
        if (n.contains("MUSHROOM") || n.contains("MYCELIUM") || n.contains("NYLIUM")
                || n.contains("FUNGUS") || n.contains("WART_BLOCK") || n.equals("SHROOMLIGHT")) {
            return 4;
        }
        if (n.contains("SNOW") || n.equals("ICE") || n.equals("PACKED_ICE") || n.equals("BLUE_ICE")) {
            return 3;
        }
        if (n.contains("MANGROVE") || n.equals("MUD") || n.equals("LILY_PAD")) {
            return 3;
        }
        if (n.contains("CHERRY") || n.equals("PINK_PETALS")) {
            return 3;
        }
        return 1;
    }

    private static long cacheKey(Location at) {
        // Quantize to ~4-block cells so walking doesn't rescan every step.
        int x = at.getBlockX() >> 2;
        int y = at.getBlockY() >> 2;
        int z = at.getBlockZ() >> 2;
        return (((long) x) << 42) ^ (((long) y) << 21) ^ (z & 0x1fffffL);
    }

    private static String pretty(String id) {
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    public record Rule(String id, String display, int priority, Set<Material> materials) {
        boolean matches(Material mat) {
            return materials.contains(mat);
        }
    }

    private record CacheEntry(Rule rule, long atMs) {
    }
}
