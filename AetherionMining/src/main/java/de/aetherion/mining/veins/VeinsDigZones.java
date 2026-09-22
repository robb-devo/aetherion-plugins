package de.aetherion.mining.veins;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Four Crystal Hollows-style dig zones around the finished Amethyst Mines hub.
 * Never paints inside {@code hubClearance} of spawn — BreadBuilds center stays 1:1.
 * Deterministic from {@code seed}: re-running paints the same tunnels/ores (24h reset).
 *
 * <p>Does not replace megavolumes of terrain — comfortable galleries + wall ores only.
 */
public final class VeinsDigZones {

    public enum Style {
        CRYSTAL,  // NE — calcite, amethyst, diamond
        LUSH,     // NW — moss, emerald, copper
        FORGE,    // SE — andesite, iron, coal
        CINDER    // SW — blackstone, quartz, gold, debris
    }

    private final World world;
    private final int spawnX;
    private final int spawnY;
    private final int spawnZ;
    private final int hubClearance;
    private final int digRadius;
    private final long seed;
    private final CommandSender progress;
    private final File dataFolder;
    private final Map<Long, VeinsDigSnapshot.Entry> recorded;

    private VeinsDigZones(
            World world,
            int spawnX,
            int spawnY,
            int spawnZ,
            int hubClearance,
            int digRadius,
            long seed,
            CommandSender progress,
            File dataFolder
    ) {
        this.world = world;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.hubClearance = Math.max(24, hubClearance);
        this.digRadius = Math.max(hubClearance + 32, digRadius);
        this.seed = seed;
        this.progress = progress;
        this.dataFolder = dataFolder;
        this.recorded = new LinkedHashMap<>(8192);
    }

    /**
     * Paint (or re-paint) all four dig zones and write the identical-reset snapshot.
     *
     * @return blocks written
     */
    public static int paint(
            World world,
            int spawnX,
            int spawnY,
            int spawnZ,
            int hubClearance,
            int digRadius,
            long seed,
            CommandSender progress,
            File dataFolder
    ) {
        if (world == null) {
            return 0;
        }
        return new VeinsDigZones(
                world, spawnX, spawnY, spawnZ, hubClearance, digRadius, seed, progress, dataFolder
        ).run();
    }

    /** @deprecated Prefer {@link #paint(World, int, int, int, int, int, long, CommandSender, File)}. */
    @Deprecated
    public static int paint(
            World world,
            int spawnX,
            int spawnY,
            int spawnZ,
            int hubClearance,
            int digRadius,
            long seed,
            CommandSender progress
    ) {
        return paint(world, spawnX, spawnY, spawnZ, hubClearance, digRadius, seed, progress, null);
    }

    public static boolean inDigRing(int x, int z, int spawnX, int spawnZ, int hubClearance, int digRadius) {
        int dx = x - spawnX;
        int dz = z - spawnZ;
        int dist2 = dx * dx + dz * dz;
        int inner = hubClearance * hubClearance;
        int outer = digRadius * digRadius;
        return dist2 >= inner && dist2 <= outer;
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

    private int run() {
        msg("§eAmethyst dig zones: tunnels + ores (hub clearance §f"
                + hubClearance + "§e, outer §f" + digRadius + "§e)…");
        preloadRing();
        int written = 0;
        written += paintCorridors();
        for (Style style : Style.values()) {
            written += paintZone(style);
        }
        if (dataFolder != null) {
            try {
                VeinsDigSnapshot.save(dataFolder, new java.util.ArrayList<>(recorded.values()));
                msg("§7Snapshot saved (§f" + recorded.size() + "§7 blocks) for identical 24h reset.");
            } catch (IOException e) {
                msg("§cCould not save dig snapshot: " + e.getMessage());
            }
        }
        msg("§aDig zones ready. §f" + written + " §ablocks written. Hub untouched.");
        return written;
    }

    private void preloadRing() {
        int minChunkX = (spawnX - digRadius) >> 4;
        int maxChunkX = (spawnX + digRadius) >> 4;
        int minChunkZ = (spawnZ - digRadius) >> 4;
        int maxChunkZ = (spawnZ + digRadius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                world.getChunkAt(cx, cz).load(true);
            }
        }
    }

    private int paintZone(Style style) {
        int[] offset = offset(style);
        int zx = spawnX + offset[0];
        int zz = spawnZ + offset[1];
        int zoneR = Math.max(28, (digRadius - hubClearance) / 2);
        Random rng = rng(style.ordinal() * 31L + 7L);
        int yMid = spawnY - 2;
        return carveGalleries(zx, zz, zoneR, yMid, style, rng);
    }

    private int paintCorridors() {
        int written = 0;
        int roadY = spawnY - 1;
        int start = hubClearance + 2;
        int end = Math.min(digRadius - 8, hubClearance + 55);
        for (Style style : Style.values()) {
            int[] dir = direction(style);
            Random rng = rng(900L + style.ordinal());
            for (int along = start; along <= end; along++) {
                int cx = spawnX + dir[0] * along;
                int cz = spawnZ + dir[1] * along;
                int drop = Math.min(6, Math.max(0, (along - start) / 8));
                int floor = roadY - drop;
                for (int w = -1; w <= 1; w++) {
                    int x;
                    int z;
                    if (dir[0] != 0 && dir[1] != 0) {
                        x = cx - dir[1] * w;
                        z = cz + dir[0] * w;
                    } else if (dir[0] != 0) {
                        x = cx;
                        z = spawnZ + w;
                    } else {
                        x = spawnX + w;
                        z = cz;
                    }
                    if (hubBlocked(x, z)) {
                        continue;
                    }
                    for (int head = 0; head <= 3; head++) {
                        written += set(x, floor + head, z, Material.AIR);
                    }
                    Material floorMat = along % 7 == 0 ? Material.SPRUCE_PLANKS : Material.COBBLESTONE;
                    written += set(x, floor - 1, z, floorMat);
                    if (w != 0 && along % 5 == 0 && rng.nextBoolean()) {
                        written += set(x, floor + 3, z, Material.LANTERN);
                    }
                    if (Math.abs(w) == 1) {
                        written += set(x + (x - cx), floor + 1, z + (z - cz), wallShell(style, rng));
                    }
                }
            }
        }
        return written;
    }

    private int carveGalleries(int zx, int zz, int zoneR, int yMid, Style style, Random rng) {
        int written = 0;
        int tunnels = 7;
        for (int t = 0; t < tunnels; t++) {
            double angle = (Math.PI * 2.0 * t) / tunnels + style.ordinal() * 0.35;
            int length = zoneR - 6 - rng.nextInt(8);
            int floor = yMid - 4 - rng.nextInt(5);
            for (int along = 0; along < length; along++) {
                int x = zx + (int) Math.round(Math.cos(angle) * along);
                int z = zz + (int) Math.round(Math.sin(angle) * along);
                x += (int) Math.round(Math.sin(along * 0.31 + t) * 1.4);
                z += (int) Math.round(Math.cos(along * 0.27 + t) * 1.4);
                if (!inDigRing(x, z, spawnX, spawnZ, hubClearance, digRadius)) {
                    continue;
                }
                if (styleAt(x, z, spawnX, spawnZ) != style) {
                    continue;
                }
                for (int w = -1; w <= 1; w++) {
                    for (int d = -1; d <= 1; d++) {
                        if (Math.abs(w) + Math.abs(d) > 2) {
                            continue;
                        }
                        int bx = x + w;
                        int bz = z + d;
                        if (hubBlocked(bx, bz)) {
                            continue;
                        }
                        for (int head = 0; head <= 3; head++) {
                            written += set(bx, floor + head, bz, Material.AIR);
                        }
                        if (w == 0 && d == 0) {
                            written += set(bx, floor - 1, bz, floorFlavor(style, rng));
                        } else if (rng.nextInt(9) == 0) {
                            Material ore = style == Style.CRYSTAL && rng.nextInt(3) == 0
                                    ? Material.AMETHYST_CLUSTER
                                    : featured(style, rng);
                            written += set(bx, floor + 1, bz, ore);
                        } else if (rng.nextInt(4) == 0) {
                            written += set(bx, floor + 1, bz, wallShell(style, rng));
                        }
                    }
                }
                if (along > 8 && along % 14 == 0) {
                    written += carveRoom(x, floor, z, style, rng);
                }
            }
        }
        // Sparse buried pockets away from tunnels for discoverability.
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
                if (!inDigRing(x, z, spawnX, spawnZ, hubClearance, digRadius)) {
                    continue;
                }
                if (hubBlocked(x, z)) {
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
        int pockets = 48;
        for (int i = 0; i < pockets; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = 8 + rng.nextDouble() * (zoneR - 10);
            int x = zx + (int) Math.round(Math.cos(angle) * dist);
            int z = zz + (int) Math.round(Math.sin(angle) * dist);
            int y = yMid - 10 + rng.nextInt(16);
            if (!inDigRing(x, z, spawnX, spawnZ, hubClearance, digRadius)) {
                continue;
            }
            if (styleAt(x, z, spawnX, spawnZ) != style || hubBlocked(x, z)) {
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
                        if (hubBlocked(x + dx, z + dz)) {
                            continue;
                        }
                        written += set(x + dx, y + dy, z + dz, ore);
                    }
                }
            }
        }
        return written;
    }

    private boolean hubBlocked(int x, int z) {
        int dx = x - spawnX;
        int dz = z - spawnZ;
        return dx * dx + dz * dz < hubClearance * hubClearance;
    }

    private int set(int x, int y, int z, Material material) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return 0;
        }
        if (hubBlocked(x, z)) {
            return 0;
        }
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            block.setType(material, false);
        }
        long key = (((long) x & 0x3FFFFFL) << 42) | (((long) y & 0xFFFFFL) << 22) | ((long) z & 0x3FFFFFL);
        recorded.put(key, VeinsDigSnapshot.Entry.of(x, y, z, material));
        return 1;
    }

    private Random rng(long salt) {
        return new Random(seed ^ (salt * 0x9E3779B97F4A7C15L) ^ ((long) spawnX << 21) ^ spawnZ);
    }

    private void msg(String line) {
        if (progress != null) {
            progress.sendMessage(line);
        }
    }

    private static int[] offset(Style style) {
        int d = 95;
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

    private static Material wallShell(Style style, Random rng) {
        return switch (style) {
            case CRYSTAL -> rng.nextBoolean() ? Material.CALCITE : Material.TUFF;
            case LUSH -> rng.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.MOSS_BLOCK;
            case FORGE -> rng.nextBoolean() ? Material.ANDESITE : Material.COBBLESTONE;
            case CINDER -> rng.nextBoolean() ? Material.BLACKSTONE : Material.BASALT;
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
}
