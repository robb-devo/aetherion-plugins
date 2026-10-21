package de.aetherion.bossengine.instance.saint;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Finds what is left of Gilded Proscenium stages (Seraphine's arena) inside a box and strikes them by layout:
 * only blocks that are exactly the stage's material at the stage's own position go, so terrain and the Veil Quay
 * stay. Stage centers come from the chunk markers, or, when a marker is gone, from a vote of the stage's
 * distinctive blocks (gold, froglight, quartz, red wool, shroomlight) over every possible center.
 * <p>
 * {@code /boss stage sweep <world> <x1> <z1> <x2> <z2> [apply]}: without {@code apply} it only reports.
 */
public final class StageSweep {

    private static final Set<Material> DISTINCT = EnumSet.of(Material.GOLD_BLOCK, Material.OCHRE_FROGLIGHT,
            Material.CHISELED_QUARTZ_BLOCK, Material.QUARTZ_PILLAR, Material.RED_WOOL, Material.SHROOMLIGHT);
    /** A vote center needs this many distinctive blocks in place, and this many stage blocks in all. */
    private static final int MIN_VOTES = 12;
    private static final int MIN_STANDING = 40;

    private record Hit(int x, int y, int z, int votes, boolean marked) {
    }

    private StageSweep() {
    }

    public static void run(Plugin plugin, CommandSender sender, World world, int x1, int z1, int x2, int z2,
                           boolean apply) {
        int cx1 = Math.min(x1, x2) >> 4;
        int cx2 = Math.max(x1, x2) >> 4;
        int cz1 = Math.min(z1, z2) >> 4;
        int cz2 = Math.max(z1, z2) >> 4;
        if ((cx2 - cx1 + 1) * (cz2 - cz1 + 1) > 400) {
            sender.sendMessage("§cBox too large (max 400 chunks).");
            return;
        }
        Map<Long, ChunkSnapshot> snaps = new HashMap<>();
        List<Hit> marked = new ArrayList<>();
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                snaps.put(key(cx, cz), chunk.getChunkSnapshot(false, false, false));
                Location m = SaintStage.markerIn(plugin, chunk);
                if (m != null) {
                    marked.add(new Hit(m.getBlockX(), m.getBlockY(), m.getBlockZ(), 0, true));
                }
            }
        }
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        sender.sendMessage("§7Sweeping " + snaps.size() + " chunks for stage leftovers…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<SaintStage.Cell> layout = SaintStage.layoutCells();
            Map<Material, List<SaintStage.Cell>> byMat = new HashMap<>();
            for (SaintStage.Cell c : layout) {
                byMat.computeIfAbsent(c.data().getMaterial(), k -> new ArrayList<>()).add(c);
            }
            Map<Long, Integer> votes = new HashMap<>();
            Map<Long, int[]> centers = new HashMap<>();
            for (Map.Entry<Long, ChunkSnapshot> e : snaps.entrySet()) {
                ChunkSnapshot s = e.getValue();
                int bx = s.getX() << 4;
                int bz = s.getZ() << 4;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y < maxY; y++) {
                            Material m = s.getBlockType(x, y, z);
                            if (!DISTINCT.contains(m) || !byMat.containsKey(m)) {
                                continue;
                            }
                            for (SaintStage.Cell c : byMat.get(m)) {
                                int ox = bx + x - c.x();
                                int oy = y - c.y();
                                int oz = bz + z - c.z();
                                long k = pack(ox, oy, oz);
                                votes.merge(k, 1, Integer::sum);
                                centers.putIfAbsent(k, new int[] {ox, oy, oz});
                            }
                        }
                    }
                }
            }
            List<Hit> hits = new ArrayList<>(marked);
            votes.entrySet().stream()
                    .filter(e -> e.getValue() >= MIN_VOTES)
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .limit(12)
                    .forEach(e -> {
                        int[] c = centers.get(e.getKey());
                        boolean near = hits.stream().anyMatch(h -> Math.abs(h.x() - c[0]) <= 8
                                && Math.abs(h.y() - c[1]) <= 8 && Math.abs(h.z() - c[2]) <= 8);
                        if (!near) {
                            hits.add(new Hit(c[0], c[1], c[2], e.getValue(), false));
                        }
                    });
            List<String> lines = new ArrayList<>();
            List<Location> strike = new ArrayList<>();
            for (Hit h : hits) {
                Map<String, Integer> mats = new TreeMap<>();
                int standing = 0;
                int below = 0;
                for (SaintStage.Cell c : layout) {
                    int wx = h.x() + c.x();
                    int wy = h.y() + c.y();
                    int wz = h.z() + c.z();
                    ChunkSnapshot s = snaps.get(key(wx >> 4, wz >> 4));
                    if (s == null || wy < minY || wy >= maxY) {
                        continue;
                    }
                    Material m = c.data().getMaterial();
                    if (s.getBlockType(wx & 15, wy, wz & 15) == m) {
                        standing++;
                        if (c.y() < -2) {
                            below++;
                        }
                        mats.merge(m.name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
                    }
                }
                boolean take = h.marked() ? standing > 0 : standing >= MIN_STANDING;
                lines.add((take ? "§a" : "§8") + "stage at " + h.x() + " " + h.y() + " " + h.z()
                        + (h.marked() ? " (marker)" : " (" + h.votes() + " votes)") + ": " + standing
                        + " blocks standing, " + below + " in the rock under it §7" + mats);
                if (take) {
                    strike.add(new Location(world, h.x() + 0.5, h.y(), h.z() + 0.5));
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (lines.isEmpty()) {
                    sender.sendMessage("§7No stage leftovers in that box.");
                    plugin.getLogger().info("[StageSweep] no stage leftovers in " + world.getName() + " " + x1 + " " + z1
                            + " " + x2 + " " + z2);
                }
                for (String line : lines) {
                    sender.sendMessage(line);
                    plugin.getLogger().info("[StageSweep] " + line.replaceAll("§.", ""));
                }
                if (!apply) {
                    sender.sendMessage("§7Dry run. Add §fapply §7to strike the §agreen §7ones.");
                    plugin.getLogger().info("[StageSweep] done: " + strike.size() + " to strike (dry run)");
                    return;
                }
                for (Location at : strike) {
                    SaintStage.strikePermanent(plugin, at);
                }
                sender.sendMessage("§aStruck " + strike.size() + " stage(s) by layout.");
                plugin.getLogger().info("[StageSweep] done: struck " + strike.size() + " stage(s)");
            });
        });
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
