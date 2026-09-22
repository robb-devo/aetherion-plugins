package de.aetherion.mining.veins;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

/**
 * One solid Crystal Hollows dig volume with four themed quadrants.
 * BreadBuilds schematic stays 1:1 — never overwrites hub solids or enclosed hub air.
 * Dig stone presses flush against schematic outer voxels (no clearance air gap).
 */
public final class VeinsDigZones {

    public enum Style {
        CRYSTAL, LUSH, FORGE, CINDER
    }

    private final JavaPlugin plugin;
    private final World world;
    private final int spawnX;
    private final int spawnY;
    private final int spawnZ;
    private final int half;
    private final int yMin;
    private final int yMax;
    private final long seed;
    private final CommandSender progress;
    private final File dataFolder;
    private final Map<Long, VeinsDigSnapshot.Entry> recorded = new LinkedHashMap<>(65536);
    private final Set<Long> hubMask = new HashSet<>();

    private VeinsDigZones(
            JavaPlugin plugin,
            World world,
            int spawnX,
            int spawnY,
            int spawnZ,
            int half,
            int digDepth,
            int digHeight,
            long seed,
            CommandSender progress,
            File dataFolder
    ) {
        this.plugin = plugin;
        this.world = world;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.half = Math.max(48, half);
        this.yMin = Math.max(world.getMinHeight() + 1, spawnY - Math.max(16, digDepth));
        this.yMax = Math.min(world.getMaxHeight() - 2, spawnY + Math.max(8, digHeight));
        this.seed = seed;
        this.progress = progress;
        this.dataFolder = dataFolder;
    }

    /**
     * Paint the solid dig cube asynchronously (batched), then run {@code onDone}.
     */
    public static void paintAsync(
            JavaPlugin plugin,
            World world,
            int spawnX,
            int spawnY,
            int spawnZ,
            int half,
            int digDepth,
            int digHeight,
            long seed,
            CommandSender progress,
            File dataFolder,
            Runnable onDone
    ) {
        if (world == null || plugin == null) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        new VeinsDigZones(
                plugin, world, spawnX, spawnY, spawnZ, half, digDepth, digHeight, seed, progress, dataFolder
        ).runAsync(onDone);
    }

    public static Style styleAt(int x, int z, int spawnX, int spawnZ) {
        boolean east = x >= spawnX;
        boolean south = z >= spawnZ;
        if (east && !south) {
            return Style.CRYSTAL;
        }
        if (!east && !south) {
            return Style.LUSH;
        }
        if (east) {
            return Style.FORGE;
        }
        return Style.CINDER;
    }

    private void runAsync(Runnable onDone) {
        msg("§eAmethyst dig: solid volume flush to schematic (§f"
                + (half * 2) + "§ex§f" + (yMax - yMin + 1) + "§e)…");
        preload();
        buildHubMask();
        msg("§7Hub footprint protected: §f" + hubMask.size() + " §7voxels (solids + enclosed air).");

        List<int[]> fillJobs = new ArrayList<>();
        for (int x = spawnX - half; x <= spawnX + half; x++) {
            for (int z = spawnZ - half; z <= spawnZ + half; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    if (hubMask.contains(key(x, y, z))) {
                        continue;
                    }
                    fillJobs.add(new int[]{x, y, z});
                }
            }
        }
        msg("§7Filling §f" + fillJobs.size() + " §7dig cells…");

        final int batch = 6000;
        final int[] index = {0};
        final int[] written = {0};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(index[0] + batch, fillJobs.size());
            for (int i = index[0]; i < end; i++) {
                int[] p = fillJobs.get(i);
                Style style = styleAt(p[0], p[2], spawnX, spawnZ);
                Random cellRng = rng((((long) p[0]) << 20) ^ (((long) p[1]) << 10) ^ p[2]);
                written[0] += set(p[0], p[1], p[2], oreOrBase(style, p[1], cellRng));
            }
            index[0] = end;
            if (index[0] < fillJobs.size()) {
                if (index[0] % (batch * 20) < batch) {
                    int pct = (int) ((index[0] * 100L) / Math.max(1, fillJobs.size()));
                    msg("§7Dig fill… §f" + pct + "%");
                }
                return;
            }
            task.cancel();
            written[0] += carveAll();
            saveSnapshot();
            msg("§aDig volume ready. §f" + written[0] + " §awrites. Schematic flush / untouched.");
            if (onDone != null) {
                onDone.run();
            }
        }, 1L, 1L);
    }

    private void preload() {
        int minCx = (spawnX - half) >> 4;
        int maxCx = (spawnX + half) >> 4;
        int minCz = (spawnZ - half) >> 4;
        int maxCz = (spawnZ + half) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                world.getChunkAt(cx, cz).load(true);
            }
        }
    }

    /**
     * Protect schematic solids and air enclosed by them (rooms).
     * Exterior air (including old clearance gaps) is diggable fill — flush to solids.
     */
    private void buildHubMask() {
        hubMask.clear();
        int minX = spawnX - half;
        int maxX = spawnX + half;
        int minZ = spawnZ - half;
        int maxZ = spawnZ + half;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (!type.isAir()) {
                        hubMask.add(key(x, y, z));
                    }
                }
            }
        }

        // Flood exterior air from the dig-volume boundary.
        Set<Long> exteriorAir = new HashSet<>();
        Queue<long[]> queue = new ArrayDeque<>();
        seedBoundaryAir(minX, maxX, minZ, maxZ, queue, exteriorAir);
        while (!queue.isEmpty()) {
            long[] n = queue.poll();
            int x = (int) n[0];
            int y = (int) n[1];
            int z = (int) n[2];
            tryEnqueue(x + 1, y, z, minX, maxX, minZ, maxZ, exteriorAir, queue);
            tryEnqueue(x - 1, y, z, minX, maxX, minZ, maxZ, exteriorAir, queue);
            tryEnqueue(x, y + 1, z, minX, maxX, minZ, maxZ, exteriorAir, queue);
            tryEnqueue(x, y - 1, z, minX, maxX, minZ, maxZ, exteriorAir, queue);
            tryEnqueue(x, y, z + 1, minX, maxX, minZ, maxZ, exteriorAir, queue);
            tryEnqueue(x, y, z - 1, minX, maxX, minZ, maxZ, exteriorAir, queue);
        }

        // Enclosed air (schematic interiors) joins the hub mask — never fill rooms.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    long k = key(x, y, z);
                    if (hubMask.contains(k)) {
                        continue;
                    }
                    if (world.getBlockAt(x, y, z).getType().isAir() && !exteriorAir.contains(k)) {
                        hubMask.add(k);
                    }
                }
            }
        }

        // Only rewrite prior dig filler when a snapshot already exists (force re-paint).
        // First paint only fills exterior air — flush to schematic without eating hub stone.
        if (dataFolder != null && VeinsDigSnapshot.exists(dataFolder)) {
            loosenPriorDigFill(minX, maxX, minZ, maxZ);
        }
    }

    private void seedBoundaryAir(
            int minX, int maxX, int minZ, int maxZ, Queue<long[]> queue, Set<Long> exteriorAir
    ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = yMin; y <= yMax; y++) {
                offerAir(x, y, minZ, exteriorAir, queue);
                offerAir(x, y, maxZ, exteriorAir, queue);
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = yMin; y <= yMax; y++) {
                offerAir(minX, y, z, exteriorAir, queue);
                offerAir(maxX, y, z, exteriorAir, queue);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                offerAir(x, yMin, z, exteriorAir, queue);
                offerAir(x, yMax, z, exteriorAir, queue);
            }
        }
    }

    private void offerAir(int x, int y, int z, Set<Long> exteriorAir, Queue<long[]> queue) {
        if (!inVolume(x, y, z)) {
            return;
        }
        if (!world.getBlockAt(x, y, z).getType().isAir()) {
            return;
        }
        long k = key(x, y, z);
        if (exteriorAir.add(k)) {
            queue.add(new long[]{x, y, z});
        }
    }

    private void tryEnqueue(
            int x, int y, int z,
            int minX, int maxX, int minZ, int maxZ,
            Set<Long> exteriorAir,
            Queue<long[]> queue
    ) {
        if (x < minX || x > maxX || z < minZ || z > maxZ || y < yMin || y > yMax) {
            return;
        }
        if (hubMask.contains(key(x, y, z))) {
            return;
        }
        if (!world.getBlockAt(x, y, z).getType().isAir()) {
            return;
        }
        long k = key(x, y, z);
        if (exteriorAir.add(k)) {
            queue.add(new long[]{x, y, z});
        }
    }

    /**
     * Unmask prior dig filler (stone/ores we place) so force re-paint rebuilds the solid
     * volume. Leaves schematic materials (amethyst, bricks, wood, etc.) protected.
     */
    private void loosenPriorDigFill(int minX, int maxX, int minZ, int maxZ) {
        List<Long> drop = new ArrayList<>();
        for (Long k : hubMask) {
            int[] pos = decode(k);
            Material type = world.getBlockAt(pos[0], pos[1], pos[2]).getType();
            if (!type.isAir() && isPriorDigFill(type)) {
                drop.add(k);
            }
        }
        hubMask.removeAll(drop);
    }

    /** Bulk dig materials this painter places — safe to rewrite on force. Not schematic amethyst. */
    private static boolean isPriorDigFill(Material type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case STONE, DEEPSLATE, COBBLESTONE, ANDESITE, DIORITE, GRANITE, TUFF, CALCITE,
                    SMOOTH_BASALT, MOSS_BLOCK, MOSSY_COBBLESTONE, CLAY, ROOTED_DIRT,
                    NETHERRACK, BLACKSTONE, BASALT, SOUL_SOIL, SPRUCE_PLANKS,
                    COAL_ORE, DEEPSLATE_COAL_ORE, IRON_ORE, DEEPSLATE_IRON_ORE,
                    COPPER_ORE, DEEPSLATE_COPPER_ORE, GOLD_ORE, DEEPSLATE_GOLD_ORE,
                    REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE, LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                    DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                    NETHER_QUARTZ_ORE, NETHER_GOLD_ORE, ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    private int carveAll() {
        int written = 0;
        for (Style style : Style.values()) {
            written += carveGalleries(style);
        }
        written += paintCorridors();
        return written;
    }

    private int paintCorridors() {
        int written = 0;
        int roadY = Math.max(yMin + 2, spawnY - 2);
        // Start flush against schematic: first dig cell outside hub mask along each diagonal.
        for (Style style : Style.values()) {
            int[] dir = direction(style);
            Random rng = rng(900L + style.ordinal());
            int start = 1;
            for (int along = 1; along < half - 4; along++) {
                int cx = spawnX + dir[0] * along;
                int cz = spawnZ + dir[1] * along;
                if (!hubMask.contains(key(cx, roadY, cz))) {
                    start = along;
                    break;
                }
            }
            int end = half - 6;
            for (int along = start; along <= end; along++) {
                int cx = spawnX + dir[0] * along;
                int cz = spawnZ + dir[1] * along;
                int drop = Math.min(8, Math.max(0, (along - start) / 7));
                int floor = roadY - drop;
                for (int w = -1; w <= 1; w++) {
                    int x = cx - dir[1] * w;
                    int z = cz + dir[0] * w;
                    if (!inVolume(x, floor, z) || hubMask.contains(key(x, floor, z))) {
                        continue;
                    }
                    for (int head = 0; head <= 3; head++) {
                        written += set(x, floor + head, z, Material.AIR);
                    }
                    written += set(x, floor - 1, z, along % 7 == 0 ? Material.SPRUCE_PLANKS : Material.COBBLESTONE);
                    if (w != 0 && along % 5 == 0 && rng.nextBoolean()) {
                        written += set(x, floor + 3, z, Material.LANTERN);
                    }
                }
            }
        }
        return written;
    }

    private int carveGalleries(Style style) {
        int[] offset = offset(style);
        int zx = spawnX + offset[0];
        int zz = spawnZ + offset[1];
        int zoneR = Math.max(24, half / 2 - 8);
        Random rng = rng(style.ordinal() * 31L + 7L);
        int yMid = Math.max(yMin + 8, spawnY - 6);
        int written = 0;
        int tunnels = 8;
        for (int t = 0; t < tunnels; t++) {
            double angle = (Math.PI * 2.0 * t) / tunnels + style.ordinal() * 0.35;
            int length = zoneR - 4 - rng.nextInt(6);
            int floor = yMid - 4 - rng.nextInt(5);
            for (int along = 0; along < length; along++) {
                int x = zx + (int) Math.round(Math.cos(angle) * along);
                int z = zz + (int) Math.round(Math.sin(angle) * along);
                x += (int) Math.round(Math.sin(along * 0.31 + t) * 1.4);
                z += (int) Math.round(Math.cos(along * 0.27 + t) * 1.4);
                if (styleAt(x, z, spawnX, spawnZ) != style || !inVolume(x, floor, z)) {
                    continue;
                }
                for (int w = -1; w <= 1; w++) {
                    for (int d = -1; d <= 1; d++) {
                        if (Math.abs(w) + Math.abs(d) > 2) {
                            continue;
                        }
                        int bx = x + w;
                        int bz = z + d;
                        if (!inVolume(bx, floor, bz) || hubMask.contains(key(bx, floor, bz))) {
                            continue;
                        }
                        for (int head = 0; head <= 3; head++) {
                            written += set(bx, floor + head, bz, Material.AIR);
                        }
                        if (w == 0 && d == 0) {
                            written += set(bx, floor - 1, bz, floorFlavor(style, rng));
                        } else if (rng.nextInt(8) == 0) {
                            Material ore = style == Style.CRYSTAL && rng.nextInt(3) == 0
                                    ? Material.AMETHYST_CLUSTER
                                    : featured(style, rng);
                            written += set(bx, floor + 1, bz, ore);
                        }
                    }
                }
                if (along > 8 && along % 14 == 0) {
                    written += carveRoom(x, floor, z, style, rng);
                }
            }
        }
        written += scatterPockets(zx, zz, zoneR, yMid, style, rng);
        return written;
    }

    private int carveRoom(int cx, int floor, int cz, Style style, Random rng) {
        int written = 0;
        int r = 3 + rng.nextInt(2);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                int dx = x - cx;
                int dz = z - cz;
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                if (!inVolume(x, floor, z) || hubMask.contains(key(x, floor, z))) {
                    continue;
                }
                for (int head = 0; head <= 4; head++) {
                    written += set(x, floor + head, z, Material.AIR);
                }
                written += set(x, floor - 1, z, floorFlavor(style, rng));
                if (dx * dx + dz * dz >= (r - 1) * (r - 1) && rng.nextInt(3) == 0) {
                    written += set(x, floor + 1, z, featured(style, rng));
                }
            }
        }
        return written;
    }

    private int scatterPockets(int zx, int zz, int zoneR, int yMid, Style style, Random rng) {
        int written = 0;
        for (int i = 0; i < 56; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = 6 + rng.nextDouble() * (zoneR - 8);
            int x = zx + (int) Math.round(Math.cos(angle) * dist);
            int z = zz + (int) Math.round(Math.sin(angle) * dist);
            int y = yMid - 12 + rng.nextInt(18);
            if (styleAt(x, z, spawnX, spawnZ) != style || !inVolume(x, y, z)) {
                continue;
            }
            if (hubMask.contains(key(x, y, z))) {
                continue;
            }
            int size = 2 + rng.nextInt(3);
            Material ore = featured(style, rng);
            for (int dx = -size; dx <= size; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -size; dz <= size; dz++) {
                        if (dx * dx + dy * dy * 2 + dz * dz > size * size) {
                            continue;
                        }
                        if (!inVolume(x + dx, y + dy, z + dz) || hubMask.contains(key(x + dx, y + dy, z + dz))) {
                            continue;
                        }
                        written += set(x + dx, y + dy, z + dz, ore);
                    }
                }
            }
        }
        return written;
    }

    private boolean inVolume(int x, int y, int z) {
        return x >= spawnX - half && x <= spawnX + half
                && z >= spawnZ - half && z <= spawnZ + half
                && y >= yMin && y <= yMax;
    }

    private int set(int x, int y, int z, Material material) {
        if (!inVolume(x, y, z) || hubMask.contains(key(x, y, z))) {
            return 0;
        }
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            block.setType(material, false);
        }
        recorded.put(key(x, y, z), VeinsDigSnapshot.Entry.of(x, y, z, material));
        return 1;
    }

    private void saveSnapshot() {
        if (dataFolder == null) {
            return;
        }
        try {
            VeinsDigSnapshot.save(dataFolder, new ArrayList<>(recorded.values()));
            msg("§7Snapshot saved (§f" + recorded.size() + "§7 blocks) for identical 24h reset.");
        } catch (IOException e) {
            msg("§cCould not save dig snapshot: " + e.getMessage());
        }
    }

    private Random rng(long salt) {
        return new Random(seed ^ (salt * 0x9E3779B97F4A7C15L) ^ ((long) spawnX << 21) ^ spawnZ);
    }

    private void msg(String line) {
        if (progress != null) {
            progress.sendMessage(line);
        }
    }

    private static long key(int x, int y, int z) {
        return (((long) (x + 1_048_576) & 0x3FFFFFL) << 42)
                | (((long) (y + 512) & 0xFFFFFL) << 22)
                | ((long) (z + 1_048_576) & 0x3FFFFFL);
    }

    private static int[] decode(long k) {
        int x = (int) ((k >> 42) & 0x3FFFFFL) - 1_048_576;
        int y = (int) ((k >> 22) & 0xFFFFFL) - 512;
        int z = (int) (k & 0x3FFFFFL) - 1_048_576;
        return new int[]{x, y, z};
    }

    private static int[] offset(Style style) {
        int d = 70;
        return switch (style) {
            case CRYSTAL -> new int[]{d, -d};
            case LUSH -> new int[]{-d, -d};
            case FORGE -> new int[]{d, d};
            case CINDER -> new int[]{-d, d};
        };
    }

    private static int[] direction(Style style) {
        return switch (style) {
            case CRYSTAL -> new int[]{1, -1};
            case LUSH -> new int[]{-1, -1};
            case FORGE -> new int[]{1, 1};
            case CINDER -> new int[]{-1, 1};
        };
    }

    private static Material oreOrBase(Style style, int y, Random rng) {
        double roll = rng.nextDouble();
        boolean deep = y < 0;
        if (roll < 0.10) {
            return deep(featured(style, rng), deep && style != Style.CINDER);
        }
        if (roll < 0.26) {
            return deep(mix(style, rng), deep && style != Style.CINDER);
        }
        return filler(style, deep, rng);
    }

    private static Material featured(Style style, Random rng) {
        return switch (style) {
            case CRYSTAL -> rng.nextInt(5) == 0 ? Material.AMETHYST_BLOCK : Material.DIAMOND_ORE;
            case LUSH -> rng.nextInt(4) == 0 ? Material.COPPER_ORE : Material.EMERALD_ORE;
            case FORGE -> rng.nextInt(3) == 0 ? Material.COAL_ORE : Material.IRON_ORE;
            case CINDER -> {
                int n = rng.nextInt(5);
                if (n == 0) {
                    yield Material.ANCIENT_DEBRIS;
                }
                if (n == 1) {
                    yield Material.NETHER_GOLD_ORE;
                }
                yield Material.NETHER_QUARTZ_ORE;
            }
        };
    }

    private static Material mix(Style style, Random rng) {
        int pick = rng.nextInt(100);
        return switch (style) {
            case CRYSTAL -> {
                if (pick < 30) {
                    yield Material.AMETHYST_BLOCK;
                }
                if (pick < 48) {
                    yield Material.BUDDING_AMETHYST;
                }
                if (pick < 65) {
                    yield Material.IRON_ORE;
                }
                if (pick < 80) {
                    yield Material.GOLD_ORE;
                }
                yield Material.DIAMOND_ORE;
            }
            case LUSH -> {
                if (pick < 30) {
                    yield Material.COPPER_ORE;
                }
                if (pick < 55) {
                    yield Material.COAL_ORE;
                }
                if (pick < 75) {
                    yield Material.IRON_ORE;
                }
                yield Material.EMERALD_ORE;
            }
            case FORGE -> {
                if (pick < 35) {
                    yield Material.COAL_ORE;
                }
                if (pick < 65) {
                    yield Material.IRON_ORE;
                }
                if (pick < 80) {
                    yield Material.COPPER_ORE;
                }
                yield Material.REDSTONE_ORE;
            }
            case CINDER -> {
                if (pick < 40) {
                    yield Material.NETHER_QUARTZ_ORE;
                }
                if (pick < 70) {
                    yield Material.NETHER_GOLD_ORE;
                }
                yield Material.ANCIENT_DEBRIS;
            }
        };
    }

    private static Material filler(Style style, boolean deep, Random rng) {
        int pick = rng.nextInt(100);
        return switch (style) {
            case CRYSTAL -> {
                if (pick < 32) {
                    yield Material.CALCITE;
                }
                if (pick < 52) {
                    yield Material.TUFF;
                }
                if (pick < 64) {
                    yield Material.SMOOTH_BASALT;
                }
                yield deep ? Material.DEEPSLATE : Material.STONE;
            }
            case LUSH -> {
                if (pick < 22) {
                    yield Material.MOSS_BLOCK;
                }
                if (pick < 42) {
                    yield Material.MOSSY_COBBLESTONE;
                }
                if (pick < 55) {
                    yield Material.CLAY;
                }
                yield deep ? Material.DEEPSLATE : Material.STONE;
            }
            case FORGE -> {
                if (pick < 24) {
                    yield Material.ANDESITE;
                }
                if (pick < 44) {
                    yield Material.GRANITE;
                }
                if (pick < 60) {
                    yield Material.DIORITE;
                }
                yield deep ? Material.DEEPSLATE : Material.STONE;
            }
            case CINDER -> {
                if (pick < 40) {
                    yield Material.NETHERRACK;
                }
                if (pick < 65) {
                    yield Material.BLACKSTONE;
                }
                if (pick < 82) {
                    yield Material.BASALT;
                }
                yield Material.SOUL_SOIL;
            }
        };
    }

    private static Material floorFlavor(Style style, Random rng) {
        return switch (style) {
            case CRYSTAL -> rng.nextInt(3) == 0 ? Material.CALCITE : Material.COBBLESTONE;
            case LUSH -> rng.nextInt(3) == 0 ? Material.MOSS_BLOCK : Material.MOSSY_COBBLESTONE;
            case FORGE -> rng.nextInt(3) == 0 ? Material.ANDESITE : Material.COBBLESTONE;
            case CINDER -> rng.nextInt(3) == 0 ? Material.BLACKSTONE : Material.SOUL_SOIL;
        };
    }

    private static Material deep(Material ore, boolean deepslate) {
        if (!deepslate) {
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
}
