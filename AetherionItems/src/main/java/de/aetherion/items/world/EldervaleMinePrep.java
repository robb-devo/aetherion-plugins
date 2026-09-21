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
 * Eldervale mining island: separate 10–40 block pockets of every overworld ore
 * the mining loop uses, plus soft lights in dark cuts.
 * v2 left a tight scatter, so this pass thins those overworld ores back to host
 * rock inside the zone, then places the pockets. Lights, stone builds, and
 * nether ores (ancient debris, quartz, nether gold) are left alone.
 */
public final class EldervaleMinePrep {

    /** One seed attempt per this many columns. Spacing comes from {@link #CLUSTER_GAP}. */
    private static final int SEED_EVERY_COLUMNS = 48;
    /** Do not start a pocket when ore is already this close. */
    private static final int CLUSTER_GAP = 18;
    /** Walk stays inside this radius so the pocket reads as one cluster. */
    private static final int CLUSTER_RADIUS = 4;
    private static final int LIGHT_STEP = 4;

    private EldervaleMinePrep() {
    }

    public static void run(CommandSender sender) {
        run(sender, null);
    }

    public static void run(CommandSender sender, Runnable onDone) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getAreas() == null) {
            if (sender != null) {
                sender.sendMessage("§cAetherionItems areas offline.");
            }
            return;
        }
        List<AreaZone> zones = plugin.getAreas().zonesOf(AreaType.ELDERVALE);
        if (zones.isEmpty()) {
            if (sender != null) {
                sender.sendMessage("§eNo Eldervale zones marked yet.");
            }
            return;
        }
        if (sender != null) {
            sender.sendMessage("§eEldervale enrich: §f" + zones.size()
                    + " §ezones — lights, clear scatter, spaced 10–40 progression clusters…");
        }
        int[] left = {zones.size()};
        for (AreaZone zone : zones) {
            enrich(plugin, sender, zone, () -> {
                if (--left[0] == 0 && onDone != null) {
                    onDone.run();
                }
            });
        }
    }

    private static void enrich(AetherionItems plugin, CommandSender sender, AreaZone zone, Runnable onDone) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        int cx = (int) Math.floor(zone.getX());
        int cz = (int) Math.floor(zone.getZ());
        int r = Math.max(8, zone.getRadius());
        List<int[]> columns = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!zone.contains(new org.bukkit.Location(world, x + 0.5, zone.getY(), z + 0.5))) {
                    continue;
                }
                columns.add(new int[]{x, z});
            }
        }
        final int[] lights = {0};
        final int[] thinned = {0};
        final int[] veins = {0};
        final int[] ores = {0};
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(index[0] + 36, columns.size());
            for (int i = index[0]; i < end; i++) {
                int x = columns.get(i)[0];
                int z = columns.get(i)[1];
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    world.getChunkAt(x >> 4, z >> 4);
                }
                lights[0] += lightColumn(world, zone, x, z);
                thinned[0] += thinColumn(world, zone, x, z);
            }
            index[0] = end;
            if (index[0] >= columns.size()) {
                task.cancel();
                seedColumns(plugin, sender, world, zone, columns, cx, cz, lights[0], thinned[0], veins, ores, onDone);
            }
        }, 1L, 1L);
    }

    private static void seedColumns(
            AetherionItems plugin,
            CommandSender sender,
            World world,
            AreaZone zone,
            List<int[]> columns,
            int cx,
            int cz,
            int lights,
            int thinned,
            int[] veins,
            int[] ores,
            Runnable onDone
    ) {
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(index[0] + 36, columns.size());
            for (int i = index[0]; i < end; i++) {
                int x = columns.get(i)[0];
                int z = columns.get(i)[1];
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    world.getChunkAt(x >> 4, z >> 4);
                }
                int placed = seedColumn(world, zone, x, z);
                if (placed > 0) {
                    veins[0]++;
                    ores[0] += placed;
                }
            }
            index[0] = end;
            if (index[0] >= columns.size()) {
                task.cancel();
                if (sender != null) {
                    sender.sendMessage("§aEldervale §f" + zone.getType().display()
                            + " §7@ §f" + cx + " " + (int) zone.getY() + " " + cz
                            + " §8· §7thinned §f" + thinned
                            + " §8· §e" + veins[0] + " clusters (§f" + ores[0] + "§e)"
                            + " §8· §a+" + lights + " lights");
                }
                if (onDone != null) {
                    onDone.run();
                }
            }
        }, 1L, 1L);
    }

    /** Overworld progression ores only. Leaves lights, builds, and nether ores. */
    private static int thinColumn(World world, AreaZone zone, int x, int z) {
        int changed = 0;
        int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int minY = Math.max(world.getMinHeight() + 1, surface - 72);
        int maxY = Math.min(world.getMaxHeight() - 2, surface - 2);
        for (int y = minY; y <= maxY; y++) {
            if (!zone.contains(new org.bukkit.Location(world, x + 0.5, y + 0.5, z + 0.5))) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (!isProgressionOre(type)) {
                continue;
            }
            block.setType(hostRockFor(type), false);
            changed++;
        }
        return changed;
    }

    private static int lightColumn(World world, AreaZone zone, int x, int z) {
        int placed = 0;
        int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        int minY = Math.max(world.getMinHeight() + 1, surface - 72);
        int maxY = Math.min(world.getMaxHeight() - 2, surface - 2);
        for (int y = minY; y <= maxY; y += LIGHT_STEP) {
            if (!zone.contains(new org.bukkit.Location(world, x + 0.5, y + 0.5, z + 0.5))) {
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
        if (block.getLightFromSky() >= 8) {
            return false;
        }
        if (block.getLightLevel() > 7) {
            return false;
        }
        if (!nearSolid(world, x, y, z)) {
            return false;
        }
        block.setType(Material.LIGHT, false);
        if (block.getBlockData() instanceof Light data) {
            data.setLevel(12);
            block.setBlockData(data, false);
        }
        return true;
    }

    private static int seedColumn(World world, AreaZone zone, int x, int z) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextInt(SEED_EVERY_COLUMNS) != 0) {
            return 0;
        }
        int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int minY = Math.max(world.getMinHeight() + 2, surface - 64);
        int maxY = surface - 4;
        if (maxY <= minY) {
            return 0;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            int y = minY + rng.nextInt(maxY - minY + 1);
            if (!zone.contains(new org.bukkit.Location(world, x + 0.5, y + 0.5, z + 0.5))) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            if (!replaceableHost(block.getType())) {
                continue;
            }
            if (oreNearby(world, x, y, z, CLUSTER_GAP)) {
                continue;
            }
            if (openToSky(world, x, y, z, surface)) {
                continue;
            }
            Material ore = pickOre(rng);
            block.setType(variantFor(block.getType(), ore), false);
            int size = 10 + rng.nextInt(31); // 10–40
            return 1 + NaturalVeins.cluster(x, y, z, size - 1, CLUSTER_RADIUS, (px, py, pz) -> {
                if (!zone.contains(new org.bukkit.Location(world, px + 0.5, py + 0.5, pz + 0.5))) {
                    return false;
                }
                if (py >= surface - 2) {
                    return false;
                }
                Block host = world.getBlockAt(px, py, pz);
                if (!replaceableHost(host.getType())) {
                    return false;
                }
                host.setType(variantFor(host.getType(), ore), false);
                return true;
            });
        }
        return 0;
    }

    private static boolean openToSky(World world, int x, int y, int z, int surface) {
        if (y >= surface - 2) {
            return true;
        }
        int open = 0;
        for (int dy = 1; dy <= 4; dy++) {
            if (!world.getBlockAt(x, y + dy, z).getType().isSolid()) {
                open++;
            }
        }
        return open >= 4 && world.getBlockAt(x, y + 1, z).getLightFromSky() >= 10;
    }

    private static boolean oreNearby(World world, int cx, int cy, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dy = -radius; dy <= radius; dy += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    Material type = world.getBlockAt(cx + dx, cy + dy, cz + dz).getType();
                    if (isOre(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isOre(Material type) {
        if (type == null) {
            return false;
        }
        String name = type.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
    }

    /**
     * Every overworld ore the mining loop uses, as a whole cluster.
     * Commons are a bit more frequent; diamond and emerald are still full pockets.
     */
    private static Material pickOre(ThreadLocalRandom rng) {
        int roll = rng.nextInt(100);
        if (roll < 16) {
            return Material.COAL_ORE;
        }
        if (roll < 30) {
            return Material.COPPER_ORE;
        }
        if (roll < 46) {
            return Material.IRON_ORE;
        }
        if (roll < 58) {
            return Material.GOLD_ORE;
        }
        if (roll < 70) {
            return Material.REDSTONE_ORE;
        }
        if (roll < 80) {
            return Material.LAPIS_ORE;
        }
        if (roll < 90) {
            return Material.DIAMOND_ORE;
        }
        return Material.EMERALD_ORE;
    }

    private static boolean isProgressionOre(Material type) {
        return switch (type) {
            case COAL_ORE, COPPER_ORE, IRON_ORE, GOLD_ORE, REDSTONE_ORE, LAPIS_ORE, DIAMOND_ORE, EMERALD_ORE,
                    DEEPSLATE_COAL_ORE, DEEPSLATE_COPPER_ORE, DEEPSLATE_IRON_ORE, DEEPSLATE_GOLD_ORE,
                    DEEPSLATE_REDSTONE_ORE, DEEPSLATE_LAPIS_ORE, DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE -> true;
            default -> false;
        };
    }

    private static Material hostRockFor(Material ore) {
        if (ore.name().startsWith("DEEPSLATE_")) {
            return Material.DEEPSLATE;
        }
        return Material.STONE;
    }

    private static Material variantFor(Material host, Material ore) {
        boolean deep = host == Material.DEEPSLATE
                || host == Material.TUFF
                || host.name().startsWith("DEEPSLATE_");
        if (!deep) {
            return ore;
        }
        return switch (ore) {
            case COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
            case COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
            case IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
            case GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
            case REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
            case LAPIS_ORE -> Material.DEEPSLATE_LAPIS_ORE;
            case DIAMOND_ORE -> Material.DEEPSLATE_DIAMOND_ORE;
            case EMERALD_ORE -> Material.DEEPSLATE_EMERALD_ORE;
            default -> ore;
        };
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
                || type == Material.CALCITE
                || type == Material.SMOOTH_BASALT;
    }

    private static boolean nearSolid(World world, int x, int y, int z) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Material material = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (material.isSolid() && material != Material.LIGHT) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
