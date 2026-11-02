package de.aetherion.hub.origin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code /origin dev underside [apply]} (also from the console): closes the gap under Anker Harbour.
 * <p>
 * Seen from below (swim over the island's edge and drop), part of the island under the bay is missing: under the
 * water lies only a thin seabed, then open air. Each bay column (water at the surface) whose seabed is
 * {@value #THIN} blocks thick or less over air is filled downwards with the rock around it, down to where the
 * island's underside runs in the columns around it (the median of the normal columns within {@value #RADIUS}
 * blocks), at least {@value #MIN_THICK} blocks thick and never more than {@value #MAX_DEPTH} below the seabed.
 * Water standing directly on air counts as a seabed of 0. Only air is ever replaced, and without physics. A dry run
 * (no {@code apply}) only counts and names a few spots. The box is {@code bay.underside-box} ({@code "x1 z1 x2 z2"}),
 * by default the whole bay around {@code bay.centre}. {@code underside gaps} closes the air layer further down
 * ({@link #gaps}).
 */
final class OriginUnderside {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final int CHUNKS_PER_TICK = 6;
    private static final int SETS_PER_TICK = 4000;
    private static final int THIN = 5;
    private static final int MIN_THICK = 6;
    private static final int MAX_DEPTH = 24;
    private static final int RADIUS = 8;
    /** Column states of the first pass. */
    private static final int DRY = Integer.MIN_VALUE;
    private static final int SOLID = Integer.MIN_VALUE + 1;

    private OriginUnderside() {
    }

    static String run(OriginIsle isle, CommandSender sender, boolean apply) {
        World world = isle.config().world();
        ConfigurationSection bay = isle.config().raw().getConfigurationSection("bay");
        if (world == null || bay == null) {
            return "§cOrigin world or bay section missing.";
        }
        double[] centre = OriginConfig.nums(bay.getString("centre", "300.5 61 -430.5"));
        double[] box = OriginConfig.nums(bay.getString("underside-box", ""));
        int x1;
        int z1;
        int x2;
        int z2;
        if (box != null && box.length >= 4) {
            x1 = (int) Math.floor(Math.min(box[0], box[2]));
            x2 = (int) Math.floor(Math.max(box[0], box[2]));
            z1 = (int) Math.floor(Math.min(box[1], box[3]));
            z2 = (int) Math.floor(Math.max(box[1], box[3]));
        } else if (centre != null) {
            x1 = (int) centre[0] - 110;
            x2 = (int) centre[0] + 110;
            z1 = (int) centre[2] - 150;
            z2 = (int) centre[2] + 150;
        } else {
            return "§cbay.centre unreadable.";
        }
        int surface = (int) Math.floor(bay.getDouble("surface-y", 60.72d));
        if (!RUNNING.compareAndSet(false, true)) {
            return "§7The underside check is already running.";
        }
        List<int[]> chunks = new ArrayList<>();
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) {
            for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        String head = "[Underside] " + (apply ? "APPLY" : "dry run") + " x " + x1 + ".." + x2 + " z " + z1 + ".." + z2
                + " (" + chunks.size() + " chunks, water surface y " + surface + ")";
        isle.plugin().getLogger().info(head);
        int w = x2 - x1 + 1;
        int d = z2 - z1 + 1;
        // first pass: per column the seabed's top and the lowest block of the solid run under it
        int[] bedTop = new int[w * d];
        int[] bottom = new int[w * d];
        Arrays.fill(bedTop, DRY);
        int minY = world.getMinHeight();
        int[] at = {0};
        int fx1 = x1;
        int fz1 = z1;
        int fx2 = x2;
        int fz2 = z2;
        Bukkit.getScheduler().runTaskTimer(isle.plugin(), task -> {
            try {
                for (int n = 0; n < CHUNKS_PER_TICK && at[0] < chunks.size(); n++, at[0]++) {
                    int[] c = chunks.get(at[0]);
                    if (!world.isChunkGenerated(c[0], c[1])) {
                        continue;
                    }
                    world.getChunkAt(c[0], c[1]);
                    for (int x = Math.max(fx1, c[0] << 4); x <= Math.min(fx2, (c[0] << 4) + 15); x++) {
                        for (int z = Math.max(fz1, c[1] << 4); z <= Math.min(fz2, (c[1] << 4) + 15); z++) {
                            int i = (x - fx1) * d + (z - fz1);
                            int y = surface;
                            if (!wet(world.getBlockAt(x, y, z).getType())) {
                                y--; // surface-y is the water's face; its top block can sit one lower
                            }
                            if (!wet(world.getBlockAt(x, y, z).getType())) {
                                continue;
                            }
                            while (y > minY && wet(world.getBlockAt(x, y, z).getType())) {
                                y--;
                            }
                            int top = y;
                            while (y > minY && !world.getBlockAt(x, y, z).getType().isAir()) {
                                y--;
                            }
                            if (y <= minY) {
                                bedTop[i] = SOLID; // solid down to the bottom of the world: nothing missing here
                                continue;
                            }
                            bedTop[i] = top;
                            bottom[i] = y + 1;
                        }
                    }
                }
            } catch (Throwable ex) {
                isle.plugin().getLogger().warning("[Underside] stopped: " + ex);
                task.cancel();
                RUNNING.set(false);
                return;
            }
            if (at[0] < chunks.size()) {
                return;
            }
            task.cancel();
            try {
                plan(isle, sender, world, apply, fx1, fz1, w, d, bedTop, bottom, minY);
            } catch (Throwable ex) {
                isle.plugin().getLogger().warning("[Underside] stopped: " + ex);
                RUNNING.set(false);
            }
        }, 1L, 1L);
        return "§7" + head + " §8· result in chat/console when done";
    }

    /** Second pass: what each thin column gets; then, with {@code apply}, places it a few thousand blocks a tick. */
    private static void plan(OriginIsle isle, CommandSender sender, World world, boolean apply, int x1, int z1, int w, int d,
                             int[] bedTop, int[] bottom, int minY) {
        List<int[]> fills = new ArrayList<>(); // x, z, from y (inclusive, top), to y (inclusive, bottom)
        int thin = 0;
        int placed = 0;
        int deepest = 0;
        List<String> sample = new ArrayList<>();
        int[] around = new int[(2 * RADIUS + 1) * (2 * RADIUS + 1)];
        for (int ix = 0; ix < w; ix++) {
            for (int iz = 0; iz < d; iz++) {
                int i = ix * d + iz;
                int top = bedTop[i];
                if (top == DRY || top == SOLID || top - bottom[i] + 1 > THIN) {
                    continue;
                }
                thin++;
                int count = 0;
                for (int ax = Math.max(0, ix - RADIUS); ax <= Math.min(w - 1, ix + RADIUS); ax++) {
                    for (int az = Math.max(0, iz - RADIUS); az <= Math.min(d - 1, iz + RADIUS); az++) {
                        int j = ax * d + az;
                        if (bedTop[j] == SOLID) {
                            around[count++] = Math.max(minY + 1, bedTop[i] - MAX_DEPTH);
                        } else if (bedTop[j] != DRY && bedTop[j] - bottom[j] + 1 > THIN) {
                            around[count++] = bottom[j];
                        }
                    }
                }
                int target = top - MIN_THICK + 1;
                if (count >= 8) {
                    Arrays.sort(around, 0, count);
                    target = Math.min(target, around[count / 2]);
                }
                target = Math.max(target, Math.max(minY + 1, top - MAX_DEPTH + 1));
                int from = bottom[i] - 1;
                if (from < target) {
                    continue;
                }
                fills.add(new int[]{x1 + ix, z1 + iz, from, target});
                placed += from - target + 1;
                deepest = Math.max(deepest, top - target + 1);
                if (sample.size() < 6) {
                    sample.add((x1 + ix) + " " + from + " " + (z1 + iz));
                }
            }
        }
        String what = placed + " block(s) under " + thin + " thin seabed column(s)"
                + (fills.isEmpty() ? "" : ", down to " + deepest + " below the seabed, e.g. " + String.join(" · ", sample));
        if (!apply || fills.isEmpty()) {
            RUNNING.set(false);
            done(isle, sender, "[Underside] would fill " + what);
            return;
        }
        Map<Material, Integer> used = new EnumMap<>(Material.class);
        int[] next = {0};
        int[] set = {0};
        Bukkit.getScheduler().runTaskTimer(isle.plugin(), task -> {
            try {
                int budget = SETS_PER_TICK;
                while (budget > 0 && next[0] < fills.size()) {
                    int[] f = fills.get(next[0]++);
                    Material rock = rockAround(world.getBlockAt(f[0], f[2], f[1]));
                    for (int y = f[2]; y >= f[3]; y--) {
                        Block b = world.getBlockAt(f[0], y, f[1]);
                        if (!b.getType().isAir()) {
                            continue;
                        }
                        b.setType(rock, false);
                        used.merge(rock, 1, Integer::sum);
                        set[0]++;
                        budget--;
                    }
                }
            } catch (Throwable ex) {
                isle.plugin().getLogger().warning("[Underside] stopped: " + ex);
                next[0] = fills.size();
            }
            if (next[0] >= fills.size()) {
                task.cancel();
                RUNNING.set(false);
                done(isle, sender, "[Underside] filled " + set[0] + " of " + what + ", rock " + used);
            }
        }, 1L, 1L);
    }

    /**
     * {@code /origin dev underside gaps [apply]}: the second half of the job. Under most of the bay the seabed is
     * thick enough, but below it runs a loose layer of air (around y 20..32) and then rock again, so from the side
     * the harbour reads as water, ground, a slot of sky, ground. In every bay column (water at the surface) each air
     * run with natural ground directly above and below is filled with the rock around it, from the seabed down to
     * the lowest rock of that column. The open underside below that stays as it is. Only air is replaced, without
     * physics, and never next to something built; runs taller than {@value #GAP_MAX} are left alone. The box is
     * {@code bay.gaps-box} ({@code "x1 z1 x2 z2"}), by default 150 x 170 blocks around the bay centre.
     */
    static String gaps(OriginIsle isle, CommandSender sender, boolean apply) {
        World world = isle.config().world();
        ConfigurationSection bay = isle.config().raw().getConfigurationSection("bay");
        if (world == null || bay == null) {
            return "§cOrigin world or bay section missing.";
        }
        double[] centre = OriginConfig.nums(bay.getString("centre", "300.5 61 -430.5"));
        double[] box = OriginConfig.nums(bay.getString("gaps-box", ""));
        int x1;
        int z1;
        int x2;
        int z2;
        if (box != null && box.length >= 4) {
            x1 = (int) Math.floor(Math.min(box[0], box[2]));
            x2 = (int) Math.floor(Math.max(box[0], box[2]));
            z1 = (int) Math.floor(Math.min(box[1], box[3]));
            z2 = (int) Math.floor(Math.max(box[1], box[3]));
        } else if (centre != null) {
            x1 = (int) centre[0] - 150;
            x2 = (int) centre[0] + 150;
            z1 = (int) centre[2] - 170;
            z2 = (int) centre[2] + 170;
        } else {
            return "§cbay.centre unreadable.";
        }
        int surface = (int) Math.floor(bay.getDouble("surface-y", 60.72d));
        if (!RUNNING.compareAndSet(false, true)) {
            return "§7The underside check is already running.";
        }
        List<int[]> chunks = new ArrayList<>();
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) {
            for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        String head = "[Underside gaps] " + (apply ? "APPLY" : "dry run") + " x " + x1 + ".." + x2 + " z " + z1 + ".." + z2
                + " (" + chunks.size() + " chunks)";
        isle.plugin().getLogger().info(head);
        List<int[]> fills = new ArrayList<>(); // x, z, top y, bottom y (both inclusive)
        int minY = world.getMinHeight();
        int[] at = {0};
        int[] stats = new int[3]; // bay columns, columns with a gap, runs left alone
        int fx1 = x1;
        int fz1 = z1;
        int fx2 = x2;
        int fz2 = z2;
        Bukkit.getScheduler().runTaskTimer(isle.plugin(), task -> {
            try {
                for (int n = 0; n < CHUNKS_PER_TICK && at[0] < chunks.size(); n++, at[0]++) {
                    int[] c = chunks.get(at[0]);
                    if (!world.isChunkGenerated(c[0], c[1])) {
                        continue;
                    }
                    world.getChunkAt(c[0], c[1]);
                    for (int x = Math.max(fx1, c[0] << 4); x <= Math.min(fx2, (c[0] << 4) + 15); x++) {
                        for (int z = Math.max(fz1, c[1] << 4); z <= Math.min(fz2, (c[1] << 4) + 15); z++) {
                            column(world, x, z, surface, minY, fills, stats);
                        }
                    }
                }
            } catch (Throwable ex) {
                isle.plugin().getLogger().warning("[Underside gaps] stopped: " + ex);
                task.cancel();
                RUNNING.set(false);
                return;
            }
            if (at[0] < chunks.size()) {
                return;
            }
            task.cancel();
            int blocks = 0;
            int top = Integer.MIN_VALUE;
            int bottom = Integer.MAX_VALUE;
            List<String> sample = new ArrayList<>();
            int every = Math.max(1, fills.size() / 5);
            for (int i = 0; i < fills.size(); i++) {
                int[] f = fills.get(i);
                blocks += f[2] - f[3] + 1;
                top = Math.max(top, f[2]);
                bottom = Math.min(bottom, f[3]);
                if (i % every == 0 && sample.size() < 5) {
                    sample.add(f[0] + " " + f[2] + " " + f[1]);
                }
            }
            String what = blocks + " block(s) in " + fills.size() + " air run(s) under " + stats[1] + " of " + stats[0]
                    + " bay column(s)" + (fills.isEmpty() ? "" : ", y " + bottom + ".." + top + ", e.g. " + String.join(" · ", sample))
                    + (stats[2] > 0 ? "; " + stats[2] + " run(s) left alone (built edge or taller than " + GAP_MAX + ")" : "");
            if (!apply || fills.isEmpty()) {
                RUNNING.set(false);
                done(isle, sender, "[Underside gaps] would fill " + what);
                return;
            }
            Map<Material, Integer> used = new EnumMap<>(Material.class);
            int[] next = {0};
            int[] set = {0};
            Bukkit.getScheduler().runTaskTimer(isle.plugin(), place -> {
                try {
                    int budget = SETS_PER_TICK;
                    while (budget > 0 && next[0] < fills.size()) {
                        int[] f = fills.get(next[0]++);
                        Material rock = rockAround(world.getBlockAt(f[0], f[2], f[1]));
                        for (int y = f[2]; y >= f[3]; y--) {
                            Block b = world.getBlockAt(f[0], y, f[1]);
                            if (!b.getType().isAir()) {
                                continue;
                            }
                            b.setType(rock, false);
                            used.merge(rock, 1, Integer::sum);
                            set[0]++;
                            budget--;
                        }
                    }
                } catch (Throwable ex) {
                    isle.plugin().getLogger().warning("[Underside gaps] stopped: " + ex);
                    next[0] = fills.size();
                }
                if (next[0] >= fills.size()) {
                    place.cancel();
                    RUNNING.set(false);
                    done(isle, sender, "[Underside gaps] filled " + set[0] + " of " + what + ", rock " + used);
                }
            }, 1L, 1L);
        }, 1L, 1L);
        return "§7" + head + " §8· result in chat/console when done";
    }

    private static final int GAP_MAX = 48;

    /** One bay column: every air run between natural ground above and natural ground below goes on the list. */
    private static void column(World world, int x, int z, int surface, int minY, List<int[]> fills, int[] stats) {
        int y = surface;
        if (!wet(world.getBlockAt(x, y, z).getType())) {
            y--;
        }
        if (!wet(world.getBlockAt(x, y, z).getType())) {
            return;
        }
        stats[0]++;
        while (y > minY && wet(world.getBlockAt(x, y, z).getType())) {
            y--;
        }
        boolean any = false;
        boolean solidAbove = false;
        while (y > minY) {
            Material m = world.getBlockAt(x, y, z).getType();
            if (!m.isAir()) {
                solidAbove = !wet(m);
                y--;
                continue;
            }
            int runTop = y;
            while (y > minY && world.getBlockAt(x, y, z).getType().isAir()) {
                y--;
            }
            if (y <= minY) {
                break; // open underside down to the bottom of the world
            }
            int runBottom = y + 1;
            Material above = world.getBlockAt(x, runTop + 1, z).getType();
            Material below = world.getBlockAt(x, y, z).getType();
            if (solidAbove && natural(above) && natural(below) && runTop - runBottom + 1 <= GAP_MAX) {
                fills.add(new int[]{x, z, runTop, runBottom});
                any = true;
            } else {
                stats[2]++;
            }
        }
        if (any) {
            stats[1]++;
        }
    }

    /** Ground the island grew from, never something built. */
    private static boolean natural(Material m) {
        if (m == null || m.isAir() || wet(m)) {
            return false;
        }
        return switch (m) {
            case STONE, DEEPSLATE, COBBLED_DEEPSLATE, TUFF, ANDESITE, DIORITE, GRANITE, CALCITE, DIRT, COARSE_DIRT,
                 ROOTED_DIRT, GRASS_BLOCK, MUD, CLAY, GRAVEL, SAND, RED_SAND, SANDSTONE, RED_SANDSTONE, DRIPSTONE_BLOCK,
                 POINTED_DRIPSTONE, MOSS_BLOCK, COBBLESTONE, MOSSY_COBBLESTONE, BEDROCK, SMOOTH_BASALT, BASALT,
                 BLACKSTONE, AMETHYST_BLOCK, BUDDING_AMETHYST, PACKED_ICE, MAGMA_BLOCK, OBSIDIAN, GLOW_LICHEN,
                 HANGING_ROOTS -> true;
            default -> m.name().endsWith("_ORE");
        };
    }

    private static void done(OriginIsle isle, CommandSender sender, String line) {
        isle.plugin().getLogger().info(line);
        if (sender != null) {
            sender.sendMessage("§8" + line);
        }
    }

    /** Water, or what only grows in it. */
    private static boolean wet(Material m) {
        return m == Material.WATER || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS || m == Material.KELP
                || m == Material.KELP_PLANT || m == Material.BUBBLE_COLUMN;
    }

    /** The most common solid, non-falling block beside {@code air} (above, the four sides, and one down). */
    private static Material rockAround(Block air) {
        Map<Material, Integer> count = new EnumMap<>(Material.class);
        int[][] around = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}};
        for (int[] d : around) {
            Material m = air.getRelative(d[0], d[1], d[2]).getType();
            if (m.isSolid() && m.isOccluding() && !m.hasGravity()) {
                count.merge(m, 1, Integer::sum);
            } else if (m == Material.SAND) {
                count.merge(Material.SANDSTONE, 1, Integer::sum);
            } else if (m == Material.RED_SAND) {
                count.merge(Material.RED_SANDSTONE, 1, Integer::sum);
            } else if (m == Material.GRAVEL) {
                count.merge(Material.ANDESITE, 1, Integer::sum);
            }
        }
        Material best = Material.STONE;
        int most = 0;
        for (Map.Entry<Material, Integer> e : count.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }
}
