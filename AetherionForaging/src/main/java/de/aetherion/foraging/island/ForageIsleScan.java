package de.aetherion.foraging.island;

import de.aetherion.foraging.AetherionForaging;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Surface scan of the forage-isle light AABB — biomes + flora tags for habitat boxes.
 * Does not write Area Tools / main-island zones.
 */
public final class ForageIsleScan {

    private ForageIsleScan() {
    }

    public static void run(AetherionForaging plugin, CommandSender sender) {
        String worldName = plugin.getConfig().getString("forage-isle.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("§cWorld not loaded: " + worldName);
            return;
        }
        int minX = plugin.getConfig().getInt("forage-isle.light.min-x", 500);
        int maxX = plugin.getConfig().getInt("forage-isle.light.max-x", 1070);
        int minZ = plugin.getConfig().getInt("forage-isle.light.min-z", -500);
        int maxZ = plugin.getConfig().getInt("forage-isle.light.max-z", 30);
        int step = Math.max(4, plugin.getConfig().getInt("forage-isle.scan-step", 4));

        sender.sendMessage("§eForage surface scan (step=" + step + ")…");
        plugin.getLogger().info("Forage scan surface: " + minX + ".." + maxX + " / " + minZ + ".." + maxZ);

        List<int[]> columns = new ArrayList<>();
        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                columns.add(new int[]{x, z});
            }
        }

        Map<Material, Integer> mats = new EnumMap<>(Material.class);
        Map<String, Integer> biomes = new HashMap<>();
        Map<String, Cluster> tags = new HashMap<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            final int batch = 400;
            final int[] idx = {0};
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                int end = Math.min(idx[0] + batch, columns.size());
                for (int k = idx[0]; k < end; k++) {
                    int x = columns.get(k)[0];
                    int z = columns.get(k)[1];
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        world.getChunkAt(x >> 4, z >> 4);
                    }
                    int y = world.getHighestBlockYAt(x, z);
                    if (y < 40) {
                        continue;
                    }
                    Material ground = world.getBlockAt(x, y, z).getType();
                    if (ground.isAir()) {
                        ground = world.getBlockAt(x, Math.max(0, y - 1), z).getType();
                    }
                    if (!ground.isAir() && ground != Material.CAVE_AIR && ground != Material.VOID_AIR) {
                        mats.merge(ground, 1, Integer::sum);
                        bumpTag(tags, ground.name(), x, y, z);
                    }
                    // Sample a short column for flora (trees / mushrooms) above ground.
                    for (int dy = 0; dy <= 8; dy++) {
                        Material m = world.getBlockAt(x, y + dy, z).getType();
                        if (m.isAir() || m == Material.CAVE_AIR || m == Material.VOID_AIR || m == Material.LIGHT) {
                            continue;
                        }
                        mats.merge(m, 1, Integer::sum);
                        bumpTag(tags, m.name(), x, y + dy, z);
                    }
                    try {
                        Biome b = world.getBiome(x, y, z);
                        String key = b.getKey().getKey();
                        biomes.merge(key, 1, Integer::sum);
                        bumpTag(tags, "BIOME:" + key.toUpperCase(Locale.ROOT), x, y, z);
                    } catch (Throwable ignored) {
                    }
                }
                idx[0] = end;
                if (idx[0] >= columns.size()) {
                    task.cancel();
                    report(plugin, sender, mats, biomes, tags, columns.size());
                }
            }, 1L, 1L);
        });
    }

    private static void bumpTag(Map<String, Cluster> tags, String name, int x, int y, int z) {
        String tag = tagFor(name);
        if (tag == null) {
            return;
        }
        Cluster c = tags.computeIfAbsent(tag, k -> new Cluster());
        c.count++;
        c.minX = Math.min(c.minX, x);
        c.maxX = Math.max(c.maxX, x);
        c.minY = Math.min(c.minY, y);
        c.maxY = Math.max(c.maxY, y);
        c.minZ = Math.min(c.minZ, z);
        c.maxZ = Math.max(c.maxZ, z);
        c.sumX += x;
        c.sumZ += z;
    }

    private static String tagFor(String name) {
        if (name.startsWith("BIOME:")) {
            String b = name.substring(6);
            if (contains(b, "MUSHROOM", "MYCELIUM")) {
                return "mushroom";
            }
            if (contains(b, "SWAMP", "MANGROVE")) {
                return "swamp";
            }
            if (contains(b, "CHERRY", "FLOWER", "MEADOW", "SUNFLOWER")) {
                return "flower";
            }
            if (contains(b, "JUNGLE", "BAMBOO")) {
                return "jungle";
            }
            if (contains(b, "DARK_FOREST", "TAIGA", "GROVE", "SNOW")) {
                return "dark";
            }
            if (contains(b, "DESERT", "BADLANDS", "BEACH", "OCEAN", "RIVER", "WARM_OCEAN")) {
                return "shore";
            }
            if (contains(b, "LUSH", "DRIPSTONE", "CAVE")) {
                return "lush";
            }
            if (contains(b, "FOREST", "BIRCH", "PLAINS")) {
                return "forest";
            }
            return null;
        }
        if (contains(name, "MUSHROOM", "FUNGUS", "MYCELIUM", "CRIMSON", "WARPED", "NYLIUM",
                "NETHER_WART", "WEEPING", "TWISTING", "SHROOMLIGHT", "STEM", "HYPHAE")) {
            return "mushroom";
        }
        if (contains(name, "JUNGLE", "BAMBOO", "COCOA")) {
            return "jungle";
        }
        if (contains(name, "MANGROVE", "MUD", "FROGLIGHT", "LILY_PAD")) {
            return "swamp";
        }
        if (contains(name, "CHERRY_", "PINK_PETALS", "AZALEA")) {
            return "flower";
        }
        if (contains(name, "SPRUCE_", "DARK_OAK_")) {
            return "dark";
        }
        if (contains(name, "MOSS", "DRIPLEAF", "CAVE_VINES", "SPORE")) {
            return "lush";
        }
        if (contains(name, "SAND", "TERRACOTTA", "SANDSTONE", "CACTUS", "PRISMARINE", "KELP", "SEAGRASS")) {
            return "shore";
        }
        if (contains(name, "OAK_", "BIRCH_")) {
            return "forest";
        }
        if (contains(name, "SCULK", "PALE_OAK", "PALE_MOSS", "EYEBLOSSOM")) {
            return "dark";
        }
        return null;
    }

    private static boolean contains(String name, String... keys) {
        for (String k : keys) {
            if (name.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static void report(
            AetherionForaging plugin,
            CommandSender sender,
            Map<Material, Integer> mats,
            Map<String, Integer> biomes,
            Map<String, Cluster> tags,
            int samples
    ) {
        List<Map.Entry<Material, Integer>> top = new ArrayList<>(mats.entrySet());
        top.sort(Comparator.<Map.Entry<Material, Integer>>comparingInt(Map.Entry::getValue).reversed());
        plugin.getLogger().info("==== Forage scan samples=" + samples + " solids=" + mats.size() + " ====");
        sender.sendMessage("§aForage scan done. §7columns=" + samples + " solids=" + mats.size());
        StringBuilder sb = new StringBuilder("Top blocks: ");
        for (int i = 0; i < Math.min(20, top.size()); i++) {
            Map.Entry<Material, Integer> e = top.get(i);
            sb.append(e.getKey().name()).append('=').append(e.getValue()).append(' ');
        }
        for (Map.Entry<Material, Integer> e : top) {
            plugin.getLogger().info("  BLOCK " + e.getValue() + " " + e.getKey().name());
        }
        sender.sendMessage("§7" + sb);
        plugin.getLogger().info("---- habitat tags (suggested AABB) ----");
        tags.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Cluster>>comparingInt(e -> e.getValue().count).reversed())
                .forEach(e -> {
                    Cluster c = e.getValue();
                    int cx = c.count == 0 ? 0 : (int) Math.round(c.sumX / (double) c.count);
                    int cz = c.count == 0 ? 0 : (int) Math.round(c.sumZ / (double) c.count);
                    plugin.getLogger().info("  TAG " + e.getKey()
                            + " n=" + c.count
                            + " center=" + cx + "," + cz
                            + " box=" + c.minX + ".." + c.maxX
                            + "," + c.minY + ".." + c.maxY
                            + "," + c.minZ + ".." + c.maxZ);
                    sender.sendMessage("§e" + e.getKey() + " §7n=" + c.count
                            + " §8@ §f" + cx + " " + cz
                            + " §8[" + c.minX + ".." + c.maxX + " / " + c.minZ + ".." + c.maxZ + "]");
                });
        plugin.getLogger().info("---- biomes ----");
        biomes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(30)
                .forEach(e -> plugin.getLogger().info("  BIOME " + e.getValue() + " " + e.getKey()));
        sender.sendMessage("§7Full list in latest.log — use tags to place §e/forageadmin habitat§7 boxes.");
        if (plugin.getConfig().getBoolean("forage-isle.auto-apply-habitats-from-scan", true)) {
            applyHabitatsFromTags(plugin, sender, tags);
        }
    }

    /** Write pet-style habitat AABBs from tag clusters (forage footprint only). */
    private static void applyHabitatsFromTags(
            AetherionForaging plugin,
            CommandSender sender,
            Map<String, Cluster> tags
    ) {
        // Prefer real flora tags; skip huge sparse biome-only boxes.
        String[] order = {"swamp", "mushroom", "flower", "jungle", "dark", "lush", "forest", "shore"};
        Map<String, String> displays = Map.of(
                "swamp", "Swamp",
                "mushroom", "Mushroom",
                "flower", "Flower",
                "jungle", "Jungle",
                "dark", "Dark Grove",
                "lush", "Lush",
                "forest", "Forest",
                "shore", "Shore"
        );
        Map<String, Integer> priority = Map.of(
                "swamp", 40,
                "mushroom", 45,
                "flower", 42,
                "jungle", 38,
                "dark", 35,
                "lush", 36,
                "forest", 20,
                "shore", 15
        );
        double pad = 8;
        double minXBound = plugin.getConfig().getDouble("forage-isle.light.min-x", 500);
        double maxXBound = plugin.getConfig().getDouble("forage-isle.light.max-x", 1070);
        double minZBound = plugin.getConfig().getDouble("forage-isle.light.min-z", -500);
        double maxZBound = plugin.getConfig().getDouble("forage-isle.light.max-z", 30);
        String world = plugin.getConfig().getString("forage-isle.world", "world");

        plugin.getConfig().set("habitats", null);
        // Dock around return pad / spawn
        plugin.getConfig().set("habitats.dock.display", "Eldervale Dock");
        plugin.getConfig().set("habitats.dock.priority", 55);
        plugin.getConfig().set("habitats.dock.world", world);
        plugin.getConfig().set("habitats.dock.min.x", 540.0);
        plugin.getConfig().set("habitats.dock.min.y", 70.0);
        plugin.getConfig().set("habitats.dock.min.z", -250.0);
        plugin.getConfig().set("habitats.dock.max.x", 620.0);
        plugin.getConfig().set("habitats.dock.max.y", 120.0);
        plugin.getConfig().set("habitats.dock.max.z", -170.0);

        int written = 1;
        for (String id : order) {
            Cluster c = tags.get(id);
            if (c == null || c.count < 12) {
                continue;
            }
            double minX = Math.max(minXBound, c.minX - pad);
            double maxX = Math.min(maxXBound, c.maxX + pad);
            double minZ = Math.max(minZBound, c.minZ - pad);
            double maxZ = Math.min(maxZBound, c.maxZ + pad);
            double minY = Math.max(40, c.minY - 6);
            double maxY = Math.min(220, c.maxY + 16);
            // Shrink absurd full-island boxes — keep local pockets.
            if ((maxX - minX) > 180) {
                double cx = c.sumX / (double) c.count;
                minX = Math.max(minXBound, cx - 70);
                maxX = Math.min(maxXBound, cx + 70);
            }
            if ((maxZ - minZ) > 180) {
                double cz = c.sumZ / (double) c.count;
                minZ = Math.max(minZBound, cz - 70);
                maxZ = Math.min(maxZBound, cz + 70);
            }
            if (maxX <= minX || maxZ <= minZ) {
                continue;
            }
            String path = "habitats." + id;
            plugin.getConfig().set(path + ".display", displays.get(id));
            plugin.getConfig().set(path + ".priority", priority.get(id));
            plugin.getConfig().set(path + ".world", world);
            plugin.getConfig().set(path + ".min.x", minX);
            plugin.getConfig().set(path + ".min.y", minY);
            plugin.getConfig().set(path + ".min.z", minZ);
            plugin.getConfig().set(path + ".max.x", maxX);
            plugin.getConfig().set(path + ".max.y", maxY);
            plugin.getConfig().set(path + ".max.z", maxZ);
            written++;
            plugin.getLogger().info("Applied habitat " + id + " box "
                    + (int) minX + ".." + (int) maxX + " / " + (int) minZ + ".." + (int) maxZ
                    + " (n=" + c.count + ")");
        }
        plugin.getConfig().set("forage-isle.auto-scan-if-empty", false);
        plugin.saveConfig();
        if (plugin.habitats() != null) {
            plugin.habitats().reload();
        }
        sender.sendMessage("§aApplied §f" + written + " §aforage habitats (pet-style). Main Area Tools untouched.");
        plugin.getLogger().info("Applied " + written + " forage habitats from scan.");
    }

    private static final class Cluster {
        int count;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        long sumX;
        long sumZ;
    }
}
