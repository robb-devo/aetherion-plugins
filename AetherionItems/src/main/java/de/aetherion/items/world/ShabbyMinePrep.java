package de.aetherion.items.world;

import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Softlight + ore balance for {@link AreaType#SHABBY_MINE}:
 * majority stone, sparse 15–30 mono clusters (coal / copper / iron).
 */
public final class ShabbyMinePrep {

    private static final Material[] ORE_POOL = {
            Material.COAL_ORE,
            Material.COPPER_ORE,
            Material.IRON_ORE
    };

    private static final Material[] DEEP_POOL = {
            Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.DEEPSLATE_IRON_ORE
    };

    /** ~1 cluster seed per this many columns (after thinning). */
    private static final int SEED_EVERY_COLUMNS = 32;

    private ShabbyMinePrep() {
    }

    /** Reflection entry: {@code ShabbyMinePrep.run(sender)}. */
    public static void run(CommandSender sender) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getAreas() == null) {
            if (sender != null) {
                sender.sendMessage("§cAetherionItems areas offline.");
            }
            return;
        }
        List<AreaZone> zones = plugin.getAreas().zonesOf(AreaType.SHABBY_MINE);
        if (zones.isEmpty()) {
            if (sender != null) {
                sender.sendMessage("§eNo Shabby Mine zones marked yet.");
            }
            return;
        }
        if (sender != null) {
            sender.sendMessage("§eShabby Mine prep: §f" + zones.size()
                    + " §ezones — thin ores → sparse 15–30 clusters…");
        }
        for (AreaZone zone : zones) {
            prepZone(plugin, sender, zone);
        }
    }

    private static void prepZone(AetherionItems plugin, CommandSender sender, AreaZone zone) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            return;
        }
        int cx = (int) Math.floor(zone.getX());
        int cy = (int) Math.floor(zone.getY());
        int cz = (int) Math.floor(zone.getZ());
        int r = Math.max(8, zone.getRadius());

        List<int[]> phase1 = new ArrayList<>();
        List<int[]> phase2 = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x += 2) {
            for (int z = cz - r; z <= cz + r; z += 2) {
                if (dist2(x + 0.5, cy, z + 0.5, zone) > r * r) {
                    continue;
                }
                phase1.add(new int[]{0, x, z}); // light
            }
        }
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (dist2(x + 0.5, cy, z + 0.5, zone) > r * r) {
                    continue;
                }
                phase1.add(new int[]{2, x, z}); // thin ores → stone
                phase1.add(new int[]{1, x, z}); // leftover sponges
                phase2.add(new int[]{3, x, z}); // sparse cluster seeds
            }
        }

        final int[] lights = {0};
        final int[] sponges = {0};
        final int[] spongeOres = {0};
        final int[] thinned = {0};
        final int[] clusters = {0};
        final int[] clusterOres = {0};

        runJobs(plugin, world, zone, phase1, (job, x, z) -> {
            if (job == 0) {
                lights[0] += lightColumn(world, zone, x, z);
            } else if (job == 1) {
                int[] counts = spongeColumn(world, zone, x, z);
                sponges[0] += counts[0];
                spongeOres[0] += counts[1];
            } else if (job == 2) {
                thinned[0] += thinColumn(world, zone, x, z);
            }
        }, () -> runJobs(plugin, world, zone, phase2, (job, x, z) -> {
            if (job == 3) {
                int placed = seedClusterColumn(world, zone, x, z);
                if (placed > 0) {
                    clusters[0]++;
                    clusterOres[0] += placed;
                }
            }
        }, () -> {
            if (sender != null) {
                sender.sendMessage("§aShabby Mine §f" + zone.getType().display()
                        + " §7@ §f" + cx + " " + cy + " " + cz
                        + " §8· §7thinned §f" + thinned[0]
                        + " §8· §e" + clusters[0] + " clusters (§f" + clusterOres[0] + "§e)"
                        + " §8· §7sponges §f" + sponges[0] + "→" + spongeOres[0]
                        + " §8· §a+" + lights[0] + " lights");
            }
        }));
    }

    @FunctionalInterface
    private interface ColumnJob {
        void accept(int job, int x, int z);
    }

    private static void runJobs(
            AetherionItems plugin,
            World world,
            AreaZone zone,
            List<int[]> jobs,
            ColumnJob handler,
            Runnable onDone
    ) {
        final int[] index = {0};
        final int batch = 48;
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(index[0] + batch, jobs.size());
            for (int i = index[0]; i < end; i++) {
                int[] job = jobs.get(i);
                int x = job[1];
                int z = job[2];
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    world.getChunkAt(x >> 4, z >> 4);
                }
                handler.accept(job[0], x, z);
            }
            index[0] = end;
            if (index[0] >= jobs.size()) {
                task.cancel();
                onDone.run();
            }
        }, 1L, 1L);
    }

    private static int lightColumn(World world, AreaZone zone, int x, int z) {
        int placed = 0;
        int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        int minY = Math.max(world.getMinHeight() + 1, (int) Math.floor(zone.getY()) - zone.getRadius());
        int maxY = Math.min(world.getMaxHeight() - 2, Math.max(surface + 4, (int) Math.floor(zone.getY()) + zone.getRadius()));
        for (int y = minY; y <= maxY; y += 2) {
            if (dist2(x + 0.5, y + 0.5, z + 0.5, zone) > zone.getRadius() * zone.getRadius()) {
                continue;
            }
            if (tryLight(world, x, y, z)) {
                placed++;
            }
        }
        return placed;
    }

    private static boolean tryLight(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        if (type == Material.LIGHT) {
            return false;
        }
        if (!type.isAir() && type != Material.CAVE_AIR && type != Material.VOID_AIR) {
            return false;
        }
        if (block.getLightFromSky() >= 12 && block.getLightFromBlocks() == 0) {
            return false;
        }
        if (block.getLightLevel() > 10) {
            return false;
        }
        if (!nearSolid(world, x, y, z)) {
            return false;
        }
        block.setType(Material.LIGHT, false);
        if (block.getBlockData() instanceof Light data) {
            data.setLevel(14);
            block.setBlockData(data, false);
        }
        return true;
    }

    /** Wipe starter/rare ores back to host rock so clusters stay sparse. */
    private static int thinColumn(World world, AreaZone zone, int x, int z) {
        int changed = 0;
        int minY = Math.max(world.getMinHeight() + 1, (int) Math.floor(zone.getY()) - zone.getRadius());
        int maxY = Math.min(world.getMaxHeight() - 2, (int) Math.floor(zone.getY()) + zone.getRadius());
        for (int y = minY; y <= maxY; y++) {
            if (dist2(x + 0.5, y + 0.5, z + 0.5, zone) > zone.getRadius() * zone.getRadius()) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (!isStarterOre(type) && !isRareOre(type)) {
                continue;
            }
            block.setType(hostRockFor(type), false);
            changed++;
        }
        return changed;
    }

    /** Rare leftover sponges → one 15–30 mono cluster. */
    private static int[] spongeColumn(World world, AreaZone zone, int x, int z) {
        int sponges = 0;
        int ores = 0;
        int minY = Math.max(world.getMinHeight() + 1, (int) Math.floor(zone.getY()) - zone.getRadius());
        int maxY = Math.min(world.getMaxHeight() - 2, (int) Math.floor(zone.getY()) + zone.getRadius());
        for (int y = minY; y <= maxY; y++) {
            if (dist2(x + 0.5, y + 0.5, z + 0.5, zone) > zone.getRadius() * zone.getRadius()) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type != Material.SPONGE && type != Material.WET_SPONGE) {
                continue;
            }
            sponges++;
            Material ore = pickOre(y);
            block.setType(variantFor(type, ore), false);
            ores += 1 + growCluster(world, zone, x, y, z, ore, clusterSize() - 1);
        }
        return new int[]{sponges, ores};
    }

    /** Sparse seeds: most columns skip; hits place one 15–30 mono clump. */
    private static int seedClusterColumn(World world, AreaZone zone, int x, int z) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextInt(SEED_EVERY_COLUMNS) != 0) {
            return 0;
        }
        int minY = Math.max(world.getMinHeight() + 1, (int) Math.floor(zone.getY()) - zone.getRadius());
        int maxY = Math.min(world.getMaxHeight() - 2, (int) Math.floor(zone.getY()) + zone.getRadius());
        if (maxY < minY) {
            return 0;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            int y = minY + rng.nextInt(maxY - minY + 1);
            if (dist2(x + 0.5, y + 0.5, z + 0.5, zone) > zone.getRadius() * zone.getRadius()) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Material host = block.getType();
            if (!replaceableHost(host)) {
                continue;
            }
            Material ore = pickOre(y);
            block.setType(variantFor(host, ore), false);
            return 1 + growCluster(world, zone, x, y, z, ore, clusterSize() - 1);
        }
        return 0;
    }

    private static int clusterSize() {
        return 15 + ThreadLocalRandom.current().nextInt(16); // 15–30
    }

    /** Place up to {@code extra} mono ore blocks near the seed (stone hosts only). */
    private static int growCluster(World world, AreaZone zone, int ox, int oy, int oz, Material ore, int extra) {
        if (extra <= 0) {
            return 0;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int placed = 0;
        int attempts = Math.max(extra * 24, 80);
        for (int i = 0; i < attempts && placed < extra; i++) {
            // Tight vein blob around seed
            int x = ox + rng.nextInt(5) - 2;
            int y = oy + rng.nextInt(5) - 2;
            int z = oz + rng.nextInt(5) - 2;
            if (x == ox && y == oy && z == oz) {
                continue;
            }
            if (dist2(x + 0.5, y + 0.5, z + 0.5, zone) > zone.getRadius() * zone.getRadius()) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Material host = block.getType();
            if (!replaceableHost(host)) {
                continue;
            }
            block.setType(variantFor(host, ore), false);
            placed++;
        }
        return placed;
    }

    private static boolean isRareOre(Material type) {
        return switch (type) {
            case GOLD_ORE, REDSTONE_ORE, LAPIS_ORE, DIAMOND_ORE, EMERALD_ORE,
                    DEEPSLATE_GOLD_ORE, DEEPSLATE_REDSTONE_ORE, DEEPSLATE_LAPIS_ORE,
                    DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE -> true;
            default -> false;
        };
    }

    private static boolean isStarterOre(Material type) {
        return switch (type) {
            case COAL_ORE, COPPER_ORE, IRON_ORE,
                    DEEPSLATE_COAL_ORE, DEEPSLATE_COPPER_ORE, DEEPSLATE_IRON_ORE -> true;
            default -> false;
        };
    }

    private static Material hostRockFor(Material ore) {
        if (ore.name().startsWith("DEEPSLATE_")) {
            return Material.DEEPSLATE;
        }
        return Material.STONE;
    }

    private static boolean replaceableHost(Material type) {
        return type == Material.STONE
                || type == Material.DEEPSLATE
                || type == Material.TUFF
                || type == Material.ANDESITE
                || type == Material.DIORITE
                || type == Material.GRANITE
                || type == Material.COBBLESTONE
                || type == Material.MOSSY_COBBLESTONE
                || type == Material.SPONGE
                || type == Material.WET_SPONGE;
    }

    private static Material pickOre(int y) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (y < 0) {
            return DEEP_POOL[rng.nextInt(DEEP_POOL.length)];
        }
        return ORE_POOL[rng.nextInt(ORE_POOL.length)];
    }

    private static Material variantFor(Material host, Material ore) {
        boolean deep = host == Material.DEEPSLATE
                || host == Material.TUFF
                || host.name().startsWith("DEEPSLATE_");
        if (!deep) {
            return switch (ore) {
                case DEEPSLATE_COAL_ORE -> Material.COAL_ORE;
                case DEEPSLATE_COPPER_ORE -> Material.COPPER_ORE;
                case DEEPSLATE_IRON_ORE -> Material.IRON_ORE;
                default -> ore;
            };
        }
        return switch (ore) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
            default -> ore;
        };
    }

    private static boolean nearSolid(World world, int x, int y, int z) {
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Material m = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (m.isSolid() && m != Material.LIGHT) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double dist2(double x, double y, double z, AreaZone zone) {
        double dx = x - zone.getX();
        double dy = y - zone.getY();
        double dz = z - zone.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
