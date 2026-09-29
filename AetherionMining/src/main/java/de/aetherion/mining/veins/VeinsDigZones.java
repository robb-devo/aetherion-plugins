package de.aetherion.mining.veins;

import de.aetherion.mining.veins.VeinsDigSnapshot;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class VeinsDigZones {
    private final JavaPlugin plugin;
    private final World world;
    private final int spawnX;
    private final int spawnY;
    private final int spawnZ;
    private final int half;
    private final int yMin;
    private final int yMax;
    private final int hubScan;
    private final long seed;
    private final CommandSender progress;
    private final File dataFolder;
    private final Map<Long, VeinsDigSnapshot.Entry> recorded = new LinkedHashMap<Long, VeinsDigSnapshot.Entry>(65536);
    private final Set<Long> hubColumns = new HashSet<Long>();
    private final Set<Long> frozenNonAir = new HashSet<Long>();
    private int digWrites;
    private int schematicSkips;

    private VeinsDigZones(JavaPlugin plugin, World world, int spawnX, int spawnY, int spawnZ, int half, int digDepth, int digHeight, int hubScan, long seed, CommandSender progress, File dataFolder) {
        this.plugin = plugin;
        this.world = world;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.half = Math.max(48, half);
        this.yMin = Math.max(world.getMinHeight() + 1, spawnY - Math.max(16, digDepth));
        this.yMax = Math.min(world.getMaxHeight() - 2, spawnY + Math.max(8, digHeight));
        this.hubScan = Math.max(32, Math.min(hubScan, half));
        this.seed = seed;
        this.progress = progress;
        this.dataFolder = dataFolder;
    }

    public static void paintAsync(JavaPlugin plugin, World world, int spawnX, int spawnY, int spawnZ, int half, int digDepth, int digHeight, int hubScan, long seed, CommandSender progress, File dataFolder, Runnable onDone) {
        if (world == null || plugin == null) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        new VeinsDigZones(plugin, world, spawnX, spawnY, spawnZ, half, digDepth, digHeight, hubScan, seed, progress, dataFolder).runAsync(onDone);
    }

    @Deprecated
    public static void paintAsync(JavaPlugin plugin, World world, int spawnX, int spawnY, int spawnZ, int half, int digDepth, int digHeight, long seed, CommandSender progress, File dataFolder, Runnable onDone) {
        VeinsDigZones.paintAsync(plugin, world, spawnX, spawnY, spawnZ, half, digDepth, digHeight, 120, seed, progress, dataFolder, onDone);
    }

    public static Style styleAt(int x, int z, int spawnX, int spawnZ) {
        boolean south;
        boolean east = x >= spawnX;
        boolean bl = south = z >= spawnZ;
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

    public static boolean isHubColumn(int x, int z, Set<Long> hubColumns) {
        return hubColumns.contains(VeinsDigZones.packColumn(x, z));
    }

    private void runAsync(Runnable onDone) {
        this.digWrites = 0;
        this.schematicSkips = 0;
        this.msg("§eAmethyst dig: exterior columns only \u2014 hub footprint never iterated\u2026");
        this.preload();
        this.freezeSchematicFootprint();
        this.msg("§7Hub columns excluded: §f" + this.hubColumns.size() + " §7| frozen non-air: §f" + this.frozenNonAir.size());
        ArrayList<int[]> fillJobs = new ArrayList<int[]>();
        int exteriorColumns = 0;
        for (int x = this.spawnX - this.half; x <= this.spawnX + this.half; ++x) {
            for (int z = this.spawnZ - this.half; z <= this.spawnZ + this.half; ++z) {
                if (this.hubColumns.contains(VeinsDigZones.packColumn(x, z))) continue;
                ++exteriorColumns;
                for (int y = this.yMin; y <= this.yMax; ++y) {
                    if (this.frozenNonAir.contains(VeinsDigZones.key(x, y, z)) || !VeinsDigZones.isPaintAbleAir(this.world.getBlockAt(x, y, z).getType())) continue;
                    fillJobs.add(new int[]{x, y, z});
                }
            }
        }
        this.msg("§7Exterior columns: §f" + exteriorColumns + " §7| dig air cells: §f" + fillJobs.size());
        int batch = 6000;
        int[] index = new int[]{0};
        Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, task -> {
            int end = Math.min(index[0] + 6000, fillJobs.size());
            for (int i = index[0]; i < end; ++i) {
                int[] p = (int[])fillJobs.get(i);
                Style style = VeinsDigZones.styleAt(p[0], p[2], this.spawnX, this.spawnZ);
                Random cellRng = this.rng((long)p[0] << 20 ^ (long)p[1] << 10 ^ (long)p[2]);
                this.setDig(p[0], p[1], p[2], VeinsDigZones.oreOrBase(style, p[1], cellRng));
            }
            index[0] = end;
            if (index[0] < fillJobs.size()) {
                if (index[0] % 120000 < 6000) {
                    int pct = (int)((long)index[0] * 100L / (long)Math.max(1, fillJobs.size()));
                    this.msg("§7Dig fill\u2026 §f" + pct + "%");
                }
                return;
            }
            task.cancel();
            this.carveAll();
            this.saveSnapshot();
            String status = this.schematicSkips == 0 ? "§a\u2713" : "§c\u2717 FAIL";
            this.msg("§aDig paint done. writes=§f" + this.digWrites + " §aschematicOverwrites=§f" + this.schematicSkips + " " + status);
            if (this.schematicSkips != 0) {
                this.plugin.getLogger().severe("BUG: dig paint attempted " + this.schematicSkips + " hub writes \u2014 mask still wrong.");
            } else {
                this.plugin.getLogger().info("Amethyst dig paint: schematicOverwrites=0 (hub never iterated).");
            }
            if (onDone != null) {
                onDone.run();
            }
        }, 1L, 1L);
    }

    private void preload() {
        int minCx = this.spawnX - this.half >> 4;
        int maxCx = this.spawnX + this.half >> 4;
        int minCz = this.spawnZ - this.half >> 4;
        int maxCz = this.spawnZ + this.half >> 4;
        for (int cx = minCx; cx <= maxCx; ++cx) {
            for (int cz = minCz; cz <= maxCz; ++cz) {
                this.world.getChunkAt(cx, cz).load(true);
            }
        }
    }

    private void freezeSchematicFootprint() {
        this.hubColumns.clear();
        this.frozenNonAir.clear();
        for (int x = this.spawnX - this.half; x <= this.spawnX + this.half; ++x) {
            for (int z = this.spawnZ - this.half; z <= this.spawnZ + this.half; ++z) {
                boolean solid = false;
                for (int y = this.yMin; y <= this.yMax; ++y) {
                    Material type = this.world.getBlockAt(x, y, z).getType();
                    if (!VeinsDigZones.isOccupyingSolid(type)) continue;
                    this.frozenNonAir.add(VeinsDigZones.key(x, y, z));
                    solid = true;
                }
                if (!solid) continue;
                this.hubColumns.add(VeinsDigZones.packColumn(x, z));
            }
        }
    }

    private static boolean isOccupyingSolid(Material type) {
        if (type == null || type.isAir()) {
            return false;
        }
        return type != Material.LIGHT && type != Material.CAVE_AIR && type != Material.VOID_AIR;
    }

    private static boolean isPaintAbleAir(Material type) {
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }

    private void carveAll() {
        for (Style style : Style.values()) {
            this.carveGalleries(style);
        }
        this.paintCorridors();
    }

    private void paintCorridors() {
        int roadY = Math.max(this.yMin + 2, this.spawnY - 2);
        for (Style style : Style.values()) {
            int[] dir = VeinsDigZones.direction(style);
            Random rng = this.rng(900L + (long)style.ordinal());
            int start = 1;
            for (int along = 1; along < this.half - 4; ++along) {
                int cx = this.spawnX + dir[0] * along;
                int cz = this.spawnZ + dir[1] * along;
                if (this.hubColumns.contains(VeinsDigZones.packColumn(cx, cz))) continue;
                start = along;
                break;
            }
            int end = this.half - 6;
            for (int along = start; along <= end; ++along) {
                int cx = this.spawnX + dir[0] * along;
                int cz = this.spawnZ + dir[1] * along;
                int drop = Math.min(8, Math.max(0, (along - start) / 7));
                int floor = roadY - drop;
                for (int w = -1; w <= 1; ++w) {
                    int x = cx - dir[1] * w;
                    int z = cz + dir[0] * w;
                    if (!this.inVolume(x, floor, z) || this.hubColumns.contains(VeinsDigZones.packColumn(x, z))) continue;
                    for (int head = 0; head <= 3; ++head) {
                        this.setDig(x, floor + head, z, Material.AIR);
                    }
                    this.setDig(x, floor - 1, z, along % 7 == 0 ? Material.SPRUCE_PLANKS : Material.COBBLESTONE);
                    if (w == 0 || along % 5 != 0 || !rng.nextBoolean()) continue;
                    this.setDig(x, floor + 3, z, Material.LANTERN);
                }
            }
        }
    }

    private void carveGalleries(Style style) {
        int[] offset = VeinsDigZones.offset(style);
        int zx = this.spawnX + offset[0];
        int zz = this.spawnZ + offset[1];
        if (Math.abs(offset[0]) < this.hubScan + 8) {
            zx = this.spawnX + Integer.signum(offset[0]) * (this.hubScan + 24);
        }
        if (Math.abs(offset[1]) < this.hubScan + 8) {
            zz = this.spawnZ + Integer.signum(offset[1]) * (this.hubScan + 24);
        }
        int zoneR = Math.max(24, this.half / 2 - 8);
        Random rng = this.rng((long)style.ordinal() * 31L + 7L);
        int yMid = Math.max(this.yMin + 8, this.spawnY - 6);
        int tunnels = 8;
        for (int t = 0; t < tunnels; ++t) {
            double angle = Math.PI * 2 * (double)t / (double)tunnels + (double)style.ordinal() * 0.35;
            int length = zoneR - 4 - rng.nextInt(6);
            int floor = yMid - 4 - rng.nextInt(5);
            for (int along = 0; along < length; ++along) {
                int x = zx + (int)Math.round(Math.cos(angle) * (double)along);
                int z = zz + (int)Math.round(Math.sin(angle) * (double)along);
                if (VeinsDigZones.styleAt(x += (int)Math.round(Math.sin((double)along * 0.31 + (double)t) * 1.4), z += (int)Math.round(Math.cos((double)along * 0.27 + (double)t) * 1.4), this.spawnX, this.spawnZ) != style || !this.inVolume(x, floor, z) || this.hubColumns.contains(VeinsDigZones.packColumn(x, z))) continue;
                for (int w = -1; w <= 1; ++w) {
                    for (int d = -1; d <= 1; ++d) {
                        int bz;
                        int bx;
                        if (Math.abs(w) + Math.abs(d) > 2 || !this.inVolume(bx = x + w, floor, bz = z + d) || this.hubColumns.contains(VeinsDigZones.packColumn(bx, bz))) continue;
                        for (int head = 0; head <= 3; ++head) {
                            this.setDig(bx, floor + head, bz, Material.AIR);
                        }
                        if (w == 0 && d == 0) {
                            this.setDig(bx, floor - 1, bz, VeinsDigZones.floorFlavor(style, rng));
                            continue;
                        }
                        if (rng.nextInt(8) != 0) continue;
                        Material ore = style == Style.CRYSTAL && rng.nextInt(3) == 0 ? Material.AMETHYST_CLUSTER : VeinsDigZones.featured(style, rng);
                        this.setDig(bx, floor + 1, bz, ore);
                    }
                }
                if (along <= 8 || along % 14 != 0) continue;
                this.carveRoom(x, floor, z, style, rng);
            }
        }
        this.scatterPockets(zx, zz, zoneR, yMid, style, rng);
    }

    private void carveRoom(int cx, int floor, int cz, Style style, Random rng) {
        int r = 3 + rng.nextInt(2);
        for (int x = cx - r; x <= cx + r; ++x) {
            for (int z = cz - r; z <= cz + r; ++z) {
                int dx = x - cx;
                int dz = z - cz;
                if (dx * dx + dz * dz > r * r || !this.inVolume(x, floor, z) || this.hubColumns.contains(VeinsDigZones.packColumn(x, z))) continue;
                for (int head = 0; head <= 4; ++head) {
                    this.setDig(x, floor + head, z, Material.AIR);
                }
                this.setDig(x, floor - 1, z, VeinsDigZones.floorFlavor(style, rng));
                if (dx * dx + dz * dz < (r - 1) * (r - 1) || rng.nextInt(3) != 0) continue;
                this.setDig(x, floor + 1, z, VeinsDigZones.featured(style, rng));
            }
        }
    }

    private void scatterPockets(int zx, int zz, int zoneR, int yMid, Style style, Random rng) {
        for (int i = 0; i < 56; ++i) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = 6.0 + rng.nextDouble() * (double)(zoneR - 8);
            int x = zx + (int)Math.round(Math.cos(angle) * dist);
            int z = zz + (int)Math.round(Math.sin(angle) * dist);
            int y = yMid - 12 + rng.nextInt(18);
            if (VeinsDigZones.styleAt(x, z, this.spawnX, this.spawnZ) != style || !this.inVolume(x, y, z) || this.hubColumns.contains(VeinsDigZones.packColumn(x, z))) continue;
            int size = 2 + rng.nextInt(3);
            Material ore = VeinsDigZones.featured(style, rng);
            for (int dx = -size; dx <= size; ++dx) {
                for (int dy = -1; dy <= 1; ++dy) {
                    for (int dz = -size; dz <= size; ++dz) {
                        if (dx * dx + dy * dy * 2 + dz * dz > size * size || !this.inVolume(x + dx, y + dy, z + dz) || this.hubColumns.contains(VeinsDigZones.packColumn(x + dx, z + dz))) continue;
                        this.setDig(x + dx, y + dy, z + dz, ore);
                    }
                }
            }
        }
    }

    private boolean inVolume(int x, int y, int z) {
        return x >= this.spawnX - this.half && x <= this.spawnX + this.half && z >= this.spawnZ - this.half && z <= this.spawnZ + this.half && y >= this.yMin && y <= this.yMax;
    }

    private void setDig(int x, int y, int z, Material material) {
        if (!this.inVolume(x, y, z)) {
            return;
        }
        long k = VeinsDigZones.key(x, y, z);
        if (this.hubColumns.contains(VeinsDigZones.packColumn(x, z)) || this.frozenNonAir.contains(k)) {
            ++this.schematicSkips;
            return;
        }
        Block block = this.world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            block.setType(material, false);
        }
        this.recorded.put(k, VeinsDigSnapshot.Entry.of(x, y, z, material));
        ++this.digWrites;
    }

    private void saveSnapshot() {
        if (this.dataFolder == null) {
            return;
        }
        try {
            VeinsDigSnapshot.save(this.dataFolder, new ArrayList<VeinsDigSnapshot.Entry>(this.recorded.values()));
            this.msg("§7Snapshot saved (§f" + this.recorded.size() + "§7 dig-only blocks). Hub excluded.");
        }
        catch (IOException e) {
            this.msg("§cCould not save dig snapshot: " + e.getMessage());
        }
    }

    private Random rng(long salt) {
        return new Random(this.seed ^ salt * -7046029254386353131L ^ (long)this.spawnX << 21 ^ (long)this.spawnZ);
    }

    private void msg(String line) {
        if (this.progress != null) {
            this.progress.sendMessage(line);
        }
        if (this.plugin != null) {
            this.plugin.getLogger().info(line.replace('§', '&').replaceAll("&[0-9a-fk-or]", ""));
        }
    }

    private static long packColumn(int x, int z) {
        return ((long)(x + 0x100000) & 0x3FFFFFL) << 22 | (long)(z + 0x100000) & 0x3FFFFFL;
    }

    private static long key(int x, int y, int z) {
        return ((long)(x + 0x100000) & 0x3FFFFFL) << 42 | ((long)(y + 512) & 0xFFFFFL) << 22 | (long)(z + 0x100000) & 0x3FFFFFL;
    }

    private static int[] offset(Style style) {
        int[] nArray;
        int d = 96;
        switch (style.ordinal()) {
            default: {
                throw new MatchException(null, null);
            }
            case 0: {
                int[] nArray2 = new int[2];
                nArray2[0] = d;
                nArray = nArray2;
                nArray2[1] = -d;
                break;
            }
            case 1: {
                int[] nArray3 = new int[2];
                nArray3[0] = -d;
                nArray = nArray3;
                nArray3[1] = -d;
                break;
            }
            case 2: {
                int[] nArray4 = new int[2];
                nArray4[0] = d;
                nArray = nArray4;
                nArray4[1] = d;
                break;
            }
            case 3: {
                int[] nArray5 = new int[2];
                nArray5[0] = -d;
                nArray = nArray5;
                nArray5[1] = d;
            }
        }
        return nArray;
    }

    private static int[] direction(Style style) {
        int[] nArray;
        switch (style.ordinal()) {
            default: {
                throw new MatchException(null, null);
            }
            case 0: {
                int[] nArray2 = new int[2];
                nArray2[0] = 1;
                nArray = nArray2;
                nArray2[1] = -1;
                break;
            }
            case 1: {
                int[] nArray3 = new int[2];
                nArray3[0] = -1;
                nArray = nArray3;
                nArray3[1] = -1;
                break;
            }
            case 2: {
                int[] nArray4 = new int[2];
                nArray4[0] = 1;
                nArray = nArray4;
                nArray4[1] = 1;
                break;
            }
            case 3: {
                int[] nArray5 = new int[2];
                nArray5[0] = -1;
                nArray = nArray5;
                nArray5[1] = 1;
            }
        }
        return nArray;
    }

    private static Material oreOrBase(Style style, int y, Random rng) {
        boolean deep;
        double roll = rng.nextDouble();
        boolean bl = deep = y < 0;
        if (roll < 0.1) {
            return VeinsDigZones.deep(VeinsDigZones.featured(style, rng), deep && style != Style.CINDER);
        }
        if (roll < 0.26) {
            return VeinsDigZones.deep(VeinsDigZones.mix(style, rng), deep && style != Style.CINDER);
        }
        return VeinsDigZones.filler(style, deep, rng);
    }

    private static Material featured(Style style, Random rng) {
        return switch (style.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> {
                if (rng.nextInt(5) == 0) {
                    yield Material.AMETHYST_BLOCK;
                }
                yield Material.DIAMOND_ORE;
            }
            case 1 -> {
                if (rng.nextInt(4) == 0) {
                    yield Material.COPPER_ORE;
                }
                yield Material.EMERALD_ORE;
            }
            case 2 -> {
                if (rng.nextInt(3) == 0) {
                    yield Material.COAL_ORE;
                }
                yield Material.IRON_ORE;
            }
            case 3 -> {
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
        return switch (style.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> {
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
            case 1 -> {
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
            case 2 -> {
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
            case 3 -> pick < 40 ? Material.NETHER_QUARTZ_ORE : (pick < 70 ? Material.NETHER_GOLD_ORE : Material.ANCIENT_DEBRIS);
        };
    }

    private static Material filler(Style style, boolean deep, Random rng) {
        int pick = rng.nextInt(100);
        return switch (style.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> {
                if (pick < 32) {
                    yield Material.CALCITE;
                }
                if (pick < 52) {
                    yield Material.TUFF;
                }
                if (pick < 64) {
                    yield Material.SMOOTH_BASALT;
                }
                if (deep) {
                    yield Material.DEEPSLATE;
                }
                yield Material.STONE;
            }
            case 1 -> {
                if (pick < 22) {
                    yield Material.MOSS_BLOCK;
                }
                if (pick < 42) {
                    yield Material.MOSSY_COBBLESTONE;
                }
                if (pick < 55) {
                    yield Material.CLAY;
                }
                if (deep) {
                    yield Material.DEEPSLATE;
                }
                yield Material.STONE;
            }
            case 2 -> {
                if (pick < 24) {
                    yield Material.ANDESITE;
                }
                if (pick < 44) {
                    yield Material.GRANITE;
                }
                if (pick < 60) {
                    yield Material.DIORITE;
                }
                if (deep) {
                    yield Material.DEEPSLATE;
                }
                yield Material.STONE;
            }
            case 3 -> pick < 40 ? Material.NETHERRACK : (pick < 65 ? Material.BLACKSTONE : (pick < 82 ? Material.BASALT : Material.SOUL_SOIL));
        };
    }

    private static Material floorFlavor(Style style, Random rng) {
        return switch (style.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> {
                if (rng.nextInt(3) == 0) {
                    yield Material.CALCITE;
                }
                yield Material.COBBLESTONE;
            }
            case 1 -> {
                if (rng.nextInt(3) == 0) {
                    yield Material.MOSS_BLOCK;
                }
                yield Material.MOSSY_COBBLESTONE;
            }
            case 2 -> {
                if (rng.nextInt(3) == 0) {
                    yield Material.ANDESITE;
                }
                yield Material.COBBLESTONE;
            }
            case 3 -> rng.nextInt(3) == 0 ? Material.BLACKSTONE : Material.SOUL_SOIL;
        };
    }

    private static Material deep(Material ore, boolean deepslate) {
        if (!deepslate) {
            return ore;
        }
        return switch (ore) {
            case Material.COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
            case Material.COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
            case Material.IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
            case Material.GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
            case Material.REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
            case Material.LAPIS_ORE -> Material.DEEPSLATE_LAPIS_ORE;
            case Material.DIAMOND_ORE -> Material.DEEPSLATE_DIAMOND_ORE;
            case Material.EMERALD_ORE -> Material.DEEPSLATE_EMERALD_ORE;
            default -> ore;
        };
    }

    public static enum Style {
        CRYSTAL,
        LUSH,
        FORGE,
        CINDER;

    }
}
