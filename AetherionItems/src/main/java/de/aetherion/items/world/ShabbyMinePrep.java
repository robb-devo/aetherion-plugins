package de.aetherion.items.world;

import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Air-exposed ore veins for {@link AreaType#SHABBY_MINE} and {@link AreaType#ELDERVALE}.
 * Clusters are 20–40 blocks and only replace host rock that already touches air,
 * so cave walls and tunnel floors get ore and buried stone stays stone.
 * <p>
 * One-shot: {@code plugins/AetherionItems/worldgen-flags.yml} keys
 * {@code shabby-mine-ores} and {@code eldervale-ores}. Delete one key and restart
 * to re-run only that pass, or {@code /hubadmin oregen reset <shabby|eldervale|both>}.
 */
public final class ShabbyMinePrep {

    private static final Material[] SHABBY_POOL = {
            Material.COAL_ORE,
            Material.COPPER_ORE,
            Material.IRON_ORE
    };

    private static final Material[] SHABBY_DEEP_POOL = {
            Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.DEEPSLATE_IRON_ORE
    };

    /** Eldervale wall veins — iron/gold common, diamond and emerald present. */
    private static final Material[] ELDER_POOL = {
            Material.COAL_ORE,
            Material.COPPER_ORE,
            Material.IRON_ORE,
            Material.IRON_ORE,
            Material.GOLD_ORE,
            Material.GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.LAPIS_ORE,
            Material.DIAMOND_ORE,
            Material.EMERALD_ORE
    };

    private static final int[][] DIRS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    /** One in four columns may start a vein, and only if that column has air-exposed host rock. */
    private static final int SEED_CHANCE = 4;
    private static final int NEAR_ORE_RADIUS = 4;

    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private ShabbyMinePrep() {
    }

    /** Reflection entry: {@code ShabbyMinePrep.run(sender)} — force Shabby Mine. */
    public static void run(CommandSender sender) {
        admin(sender, "shabby", false);
    }

    /**
     * @param which {@code shabby}, {@code eldervale}, or {@code both}
     * @param reset clear that flag before the pass so a later boot will not skip it if this run aborts
     */
    public static void admin(CommandSender sender, String which, boolean reset) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            if (sender != null) {
                sender.sendMessage("§cAetherionItems is not loaded.");
            }
            return;
        }
        List<AreaType> types = parseWhich(which);
        if (types == null) {
            if (sender != null) {
                sender.sendMessage("§cUsage: /hubadmin oregen [shabby|eldervale|both]");
                sender.sendMessage("§c       /hubadmin oregen reset <shabby|eldervale|both>");
            }
            return;
        }
        if (reset) {
            for (AreaType type : types) {
                WorldgenFlags.clear(plugin, WorldgenFlags.key(type));
            }
            if (sender != null) {
                sender.sendMessage("§7Cleared ore-pass flag" + (types.size() == 1 ? "" : "s")
                        + " for §f" + label(types) + "§7.");
            }
        }
        begin(sender, types);
    }

    /** Boot hook. Runs only the passes whose flag is still missing. */
    public static void schedulePending(AetherionItems plugin) {
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<AreaType> pending = new ArrayList<>();
            if (!WorldgenFlags.done(plugin, WorldgenFlags.SHABBY)) {
                pending.add(AreaType.SHABBY_MINE);
            }
            if (!WorldgenFlags.done(plugin, WorldgenFlags.ELDERVALE)) {
                pending.add(AreaType.ELDERVALE);
            }
            if (pending.isEmpty()) {
                return;
            }
            plugin.getLogger().info("Ore pass queued (air-exposed 20–40): " + label(pending));
            begin(Bukkit.getConsoleSender(), pending);
        }, 200L);
    }

    private static void begin(CommandSender sender, List<AreaType> types) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getAreas() == null) {
            if (sender != null) {
                sender.sendMessage("§cAetherionItems areas offline.");
            }
            return;
        }
        if (!BUSY.compareAndSet(false, true)) {
            if (sender != null) {
                sender.sendMessage("§eAn ore pass is already running.");
            }
            return;
        }
        runNext(plugin, sender, types, 0);
    }

    private static void runNext(AetherionItems plugin, CommandSender sender, List<AreaType> types, int index) {
        if (index >= types.size()) {
            BUSY.set(false);
            if (sender != null) {
                sender.sendMessage("§aOre pass finished.");
            }
            return;
        }
        AreaType type = types.get(index);
        List<AreaZone> zones = plugin.getAreas().zonesOf(type);
        if (zones.isEmpty()) {
            if (sender != null) {
                sender.sendMessage("§eNo " + type.display() + " zones marked — flag left unset.");
            }
            runNext(plugin, sender, types, index + 1);
            return;
        }
        if (sender != null) {
            sender.sendMessage("§e" + type.display() + "§7: §f" + zones.size()
                    + " §ezone" + (zones.size() == 1 ? "" : "s")
                    + " — thin buried ore, place air-exposed 20–40 clusters…");
        }
        boolean[] blocked = {false};
        int[] left = {zones.size()};
        for (AreaZone zone : zones) {
            prepZone(plugin, sender, zone, type == AreaType.ELDERVALE, blocked, () -> {
                left[0]--;
                if (left[0] > 0) {
                    return;
                }
                String key = WorldgenFlags.key(type);
                if (!blocked[0] && key != null) {
                    WorldgenFlags.mark(plugin, key);
                    plugin.getLogger().info("Ore pass marked " + key + " in worldgen-flags.yml");
                } else if (sender != null) {
                    sender.sendMessage("§e" + type.display()
                            + " world was not loaded — flag left unset, will retry next boot.");
                }
                runNext(plugin, sender, types, index + 1);
            });
        }
    }

    private static void prepZone(
            AetherionItems plugin,
            CommandSender sender,
            AreaZone zone,
            boolean eldervale,
            boolean[] blocked,
            Runnable onDone
    ) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            blocked[0] = true;
            if (sender != null) {
                sender.sendMessage("§cWorld not loaded: " + zone.getWorldName());
            }
            onDone.run();
            return;
        }
        int[] range = yRange(world, zone);
        int minY = range[0];
        int maxY = range[1];
        int cx = (int) Math.floor(zone.getX());
        int cz = (int) Math.floor(zone.getZ());
        int r = Math.max(8, zone.getRadius());

        List<int[]> phase1 = new ArrayList<>();
        List<int[]> phase2 = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!inZone(zone, x + 0.5, zone.getY(), z + 0.5)) {
                    continue;
                }
                if (!eldervale && ((x + z) & 1) == 0) {
                    phase1.add(new int[]{0, x, z});
                }
                phase1.add(new int[]{2, x, z});
                if (!eldervale) {
                    phase2.add(new int[]{1, x, z});
                }
                phase2.add(new int[]{3, x, z});
            }
        }

        final int[] lights = {0};
        final int[] sponges = {0};
        final int[] spongeOres = {0};
        final int[] thinned = {0};
        final int[] clusters = {0};
        final int[] clusterOres = {0};

        runJobs(plugin, world, phase1, (job, x, z) -> {
            if (job == 0) {
                lights[0] += lightColumn(world, zone, minY, maxY, x, z);
            } else if (job == 2) {
                thinned[0] += thinColumn(world, zone, minY, maxY, x, z);
            }
        }, () -> runJobs(plugin, world, phase2, (job, x, z) -> {
            if (job == 1) {
                int[] counts = spongeColumn(world, zone, minY, maxY, x, z, eldervale);
                sponges[0] += counts[0];
                spongeOres[0] += counts[1];
            } else if (job == 3) {
                int placed = seedClusterColumn(world, zone, minY, maxY, x, z, eldervale);
                if (placed > 0) {
                    clusters[0]++;
                    clusterOres[0] += placed;
                }
            }
        }, () -> {
            if (sender != null) {
                sender.sendMessage("§a" + zone.getType().display()
                        + " §7@ §f" + cx + " " + (int) zone.getY() + " " + cz
                        + " §8· §7thinned §f" + thinned[0]
                        + " §8· §e" + clusters[0] + " wall clusters (§f" + clusterOres[0] + "§e)"
                        + (eldervale ? "" : " §8· §7sponges §f" + sponges[0] + "→" + spongeOres[0]
                        + " §8· §a+" + lights[0] + " lights"));
            }
            onDone.run();
        }));
    }

    @FunctionalInterface
    private interface ColumnJob {
        void accept(int job, int x, int z);
    }

    private static void runJobs(
            AetherionItems plugin,
            World world,
            List<int[]> jobs,
            ColumnJob handler,
            Runnable onDone
    ) {
        if (jobs.isEmpty()) {
            onDone.run();
            return;
        }
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

    private static int lightColumn(World world, AreaZone zone, int minY, int maxY, int x, int z) {
        int placed = 0;
        for (int y = minY; y <= maxY; y += 2) {
            if (!inWork(zone, minY, maxY, x, y, z)) {
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

    /** Wipe existing ore back to host rock so buried blobs do not survive the re-pass. */
    private static int thinColumn(World world, AreaZone zone, int minY, int maxY, int x, int z) {
        int changed = 0;
        for (int y = minY; y <= maxY; y++) {
            if (!inWork(zone, minY, maxY, x, y, z)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (!isPassOre(type)) {
                continue;
            }
            block.setType(hostRockFor(type), false);
            changed++;
        }
        return changed;
    }

    /** Leftover sponges on an open face become one wall cluster. Buried sponges become stone. */
    private static int[] spongeColumn(
            World world,
            AreaZone zone,
            int minY,
            int maxY,
            int x,
            int z,
            boolean eldervale
    ) {
        int sponges = 0;
        int ores = 0;
        for (int y = minY; y <= maxY; y++) {
            if (!inWork(zone, minY, maxY, x, y, z)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type != Material.SPONGE && type != Material.WET_SPONGE) {
                continue;
            }
            sponges++;
            if (!facesCaveAir(world, x, y, z)) {
                block.setType(Material.STONE, false);
                continue;
            }
            Material ore = pickOre(eldervale, y);
            block.setType(variantFor(type, ore), false);
            ores += 1 + growExposed(world, zone, minY, maxY, x, y, z, ore, clusterSize() - 1);
        }
        return new int[]{sponges, ores};
    }

    /** Exposed host only. Solid columns are skipped. */
    private static int seedClusterColumn(
            World world,
            AreaZone zone,
            int minY,
            int maxY,
            int x,
            int z,
            boolean eldervale
    ) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextInt(SEED_CHANCE) != 0) {
            return 0;
        }
        List<Integer> exposed = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            if (!inWork(zone, minY, maxY, x, y, z)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            if (!replaceableHost(block.getType()) || !facesCaveAir(world, x, y, z)) {
                continue;
            }
            exposed.add(y);
        }
        if (exposed.isEmpty()) {
            return 0;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            int y = exposed.get(rng.nextInt(exposed.size()));
            if (nearOre(world, x, y, z)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Material host = block.getType();
            if (!replaceableHost(host) || !facesCaveAir(world, x, y, z)) {
                continue;
            }
            Material ore = pickOre(eldervale, y);
            block.setType(variantFor(host, ore), false);
            return 1 + growExposed(world, zone, minY, maxY, x, y, z, ore, clusterSize() - 1);
        }
        return 0;
    }

    private static int clusterSize() {
        return 20 + ThreadLocalRandom.current().nextInt(21);
    }

    /** Walk air-touching host rock. Never places a block that does not face air. */
    private static int growExposed(
            World world,
            AreaZone zone,
            int minY,
            int maxY,
            int ox,
            int oy,
            int oz,
            Material ore,
            int extra
    ) {
        if (extra <= 0) {
            return 0;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<int[]> frontier = new ArrayList<>();
        frontier.add(new int[]{ox, oy, oz});
        int placed = 0;
        int guard = 0;
        int guardMax = extra * 48;
        while (placed < extra && !frontier.isEmpty() && guard++ < guardMax) {
            int fromIndex = rng.nextInt(frontier.size());
            int[] from = frontier.get(fromIndex);
            int[] order = {0, 1, 2, 3, 4, 5};
            for (int n = 0; n < order.length; n++) {
                int swap = n + rng.nextInt(order.length - n);
                int tmp = order[n];
                order[n] = order[swap];
                order[swap] = tmp;
            }
            boolean grew = false;
            for (int dirIndex : order) {
                int[] dir = DIRS[dirIndex];
                int x = from[0] + dir[0];
                int y = from[1] + dir[1];
                int z = from[2] + dir[2];
                if (!inWork(zone, minY, maxY, x, y, z)) {
                    continue;
                }
                Block block = world.getBlockAt(x, y, z);
                Material host = block.getType();
                if (!replaceableHost(host) || !facesCaveAir(world, x, y, z)) {
                    continue;
                }
                block.setType(variantFor(host, ore), false);
                frontier.add(new int[]{x, y, z});
                placed++;
                grew = true;
                break;
            }
            if (!grew) {
                frontier.remove(fromIndex);
            }
        }
        return placed;
    }

    /**
     * Open face that is not sunlit. Cave walls and covered tunnels qualify.
     * The outside of a floating island (sky light on the air) does not.
     */
    private static boolean facesCaveAir(World world, int x, int y, int z) {
        for (int[] dir : DIRS) {
            Block neighbor = world.getBlockAt(x + dir[0], y + dir[1], z + dir[2]);
            if (!isOpen(neighbor.getType())) {
                continue;
            }
            if (neighbor.getLightFromSky() <= 4) {
                return true;
            }
        }
        return false;
    }

    /** Air, cave air, and the soft-light blocks this pass places in open pockets. */
    private static boolean isOpen(Material type) {
        return type.isAir()
                || type == Material.CAVE_AIR
                || type == Material.VOID_AIR
                || type == Material.LIGHT;
    }

    private static boolean nearOre(World world, int x, int y, int z) {
        int r = NEAR_ORE_RADIUS;
        int r2 = r * r;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) {
                        continue;
                    }
                    if (isPassOre(world.getBlockAt(x + dx, y + dy, z + dz).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isPassOre(Material type) {
        return isStarterOre(type) || isRareOre(type);
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

    private static Material pickOre(boolean eldervale, int y) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (eldervale) {
            return ELDER_POOL[rng.nextInt(ELDER_POOL.length)];
        }
        if (y < 0) {
            return SHABBY_DEEP_POOL[rng.nextInt(SHABBY_DEEP_POOL.length)];
        }
        return SHABBY_POOL[rng.nextInt(SHABBY_POOL.length)];
    }

    private static Material variantFor(Material host, Material ore) {
        boolean deep = host == Material.DEEPSLATE
                || host == Material.TUFF
                || host.name().startsWith("DEEPSLATE_");
        return deep ? deepForm(ore) : stoneForm(ore);
    }

    private static Material stoneForm(Material ore) {
        return switch (ore) {
            case DEEPSLATE_COAL_ORE -> Material.COAL_ORE;
            case DEEPSLATE_COPPER_ORE -> Material.COPPER_ORE;
            case DEEPSLATE_IRON_ORE -> Material.IRON_ORE;
            case DEEPSLATE_GOLD_ORE -> Material.GOLD_ORE;
            case DEEPSLATE_REDSTONE_ORE -> Material.REDSTONE_ORE;
            case DEEPSLATE_LAPIS_ORE -> Material.LAPIS_ORE;
            case DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND_ORE;
            case DEEPSLATE_EMERALD_ORE -> Material.EMERALD_ORE;
            default -> ore;
        };
    }

    private static Material deepForm(Material ore) {
        return switch (ore) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> Material.DEEPSLATE_LAPIS_ORE;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> Material.DEEPSLATE_DIAMOND_ORE;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> Material.DEEPSLATE_EMERALD_ORE;
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
                    Material material = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (material.isSolid() && material != Material.LIGHT) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int[] yRange(World world, AreaZone zone) {
        if (zone.getType().horizontalDisk()) {
            int mid = (int) Math.floor(zone.getY());
            int min = Math.max(world.getMinHeight() + 1, mid - 72);
            int max = Math.min(world.getMaxHeight() - 2, mid + 48);
            if (max < min) {
                max = min;
            }
            return new int[]{min, max};
        }
        int min = Math.max(world.getMinHeight() + 1, (int) Math.floor(zone.getY()) - zone.getRadius());
        int max = Math.min(world.getMaxHeight() - 2, (int) Math.floor(zone.getY()) + zone.getRadius());
        if (max < min) {
            max = min;
        }
        return new int[]{min, max};
    }

    private static boolean inWork(AreaZone zone, int minY, int maxY, int x, int y, int z) {
        if (y < minY || y > maxY) {
            return false;
        }
        return inZone(zone, x + 0.5, y + 0.5, z + 0.5);
    }

    private static boolean inZone(AreaZone zone, double x, double y, double z) {
        double dx = x - zone.getX();
        double dy = y - zone.getY();
        double dz = z - zone.getZ();
        double r2 = (double) zone.getRadius() * zone.getRadius();
        if (zone.getType().horizontalDisk()) {
            return dx * dx + dz * dz <= r2;
        }
        return dx * dx + dy * dy + dz * dz <= r2;
    }

    private static List<AreaType> parseWhich(String which) {
        String key = which == null ? "both" : which.toLowerCase(Locale.ROOT).replace('-', ' ').trim();
        return switch (key) {
            case "shabby", "shabby mine", "shabbymine", "mines" -> List.of(AreaType.SHABBY_MINE);
            case "eldervale", "elder", "vale" -> List.of(AreaType.ELDERVALE);
            case "both", "all" -> List.of(AreaType.SHABBY_MINE, AreaType.ELDERVALE);
            default -> null;
        };
    }

    private static String label(List<AreaType> types) {
        StringBuilder out = new StringBuilder();
        for (AreaType type : types) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(type.display());
        }
        return out.toString();
    }
}
