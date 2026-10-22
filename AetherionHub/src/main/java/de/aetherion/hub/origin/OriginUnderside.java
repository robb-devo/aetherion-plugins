package de.aetherion.hub.origin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code /origin dev underside [apply]} (also from the console): closes the holes under Anker Harbour.
 * <p>
 * Where a still water block of the bay sits on air, the harbour shows a hole from below (swim over the island's edge
 * and drop), and the first block update would pour it out. The air under each such block gets up to three blocks of
 * the rock around it (the most common solid neighbour, never a falling block; stone when there is none). Only air is
 * ever replaced, and without physics. A dry run (no {@code apply}) only counts and names a few spots.
 * The box is {@code bay.underside-box} ({@code "x1 z1 x2 z2"}), by default the whole bay around {@code bay.centre}.
 */
final class OriginUnderside {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final int CHUNKS_PER_TICK = 6;
    private static final int DEPTH = 3;

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
        int top = (int) Math.floor(bay.getDouble("surface-y", 60.72d)) + 1;
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
                + " (" + chunks.size() + " chunks, water up to y " + top + ")";
        isle.plugin().getLogger().info(head);
        final int fx1 = x1;
        final int fx2 = x2;
        final int fz1 = z1;
        final int fz2 = z2;
        int[] at = {0};
        int[] holes = {0};
        int[] placed = {0};
        List<String> sample = new ArrayList<>();
        Map<Material, Integer> used = new EnumMap<>(Material.class);
        Bukkit.getScheduler().runTaskTimer(isle.plugin(), task -> {
            try {
                for (int n = 0; n < CHUNKS_PER_TICK && at[0] < chunks.size(); n++, at[0]++) {
                    int[] c = chunks.get(at[0]);
                    world.getChunkAt(c[0], c[1]);
                    for (int x = Math.max(fx1, c[0] << 4); x <= Math.min(fx2, (c[0] << 4) + 15); x++) {
                        for (int z = Math.max(fz1, c[1] << 4); z <= Math.min(fz2, (c[1] << 4) + 15); z++) {
                            for (int y = top; y > world.getMinHeight() + DEPTH; y--) {
                                Block water = world.getBlockAt(x, y, z);
                                if (water.getType() != Material.WATER || !(water.getBlockData() instanceof Levelled l) || l.getLevel() != 0) {
                                    continue;
                                }
                                Block below = water.getRelative(0, -1, 0);
                                if (!below.getType().isAir()) {
                                    continue;
                                }
                                holes[0]++;
                                if (sample.size() < 6) {
                                    sample.add(x + " " + (y - 1) + " " + z);
                                }
                                Material rock = rockAround(below);
                                used.merge(rock, 1, Integer::sum);
                                for (int d = 0; d < DEPTH; d++) {
                                    Block fill = water.getRelative(0, -1 - d, 0);
                                    if (!fill.getType().isAir()) {
                                        break;
                                    }
                                    if (apply) {
                                        fill.setType(rock, false);
                                    }
                                    placed[0]++;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ex) {
                isle.plugin().getLogger().warning("[Underside] stopped: " + ex);
                at[0] = chunks.size();
            }
            if (at[0] >= chunks.size()) {
                task.cancel();
                RUNNING.set(false);
                String done = "[Underside] " + (apply ? "filled " : "would fill ") + placed[0] + " block(s) under " + holes[0]
                        + " hanging water block(s)" + (sample.isEmpty() ? "" : ", e.g. " + String.join(" · ", sample))
                        + (used.isEmpty() ? "" : ", rock " + used);
                isle.plugin().getLogger().info(done);
                if (sender != null) {
                    sender.sendMessage("§8" + done);
                }
            }
        }, 1L, 1L);
        return "§7" + head + " §8· result in chat/console when done";
    }

    /** The most common solid, non-falling block beside {@code air} (the four sides, one up and one down). */
    private static Material rockAround(Block air) {
        Map<Material, Integer> count = new EnumMap<>(Material.class);
        int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}};
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
