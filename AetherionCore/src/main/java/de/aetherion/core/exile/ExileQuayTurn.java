package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * {@code /exile turnquay [apply]}: finds the Veil Quay where it really stands and turns it 180° so the ferry casts
 * off east, away from the Borderlands and the main island.
 * <p>
 * The recorded origin can be wrong (the islet was pasted at several places over the beta), so nothing trusts it:
 * the rarest blocks of {@code veil_quay.schem} (the ferry bell, the lectern, the lightning rod, ...) are looked up
 * around the recorded spot, every hit votes for an origin and an orientation (four quarter turns, mirrored or not),
 * and the best few are checked block for block against the whole schematic. Under {@value #MIN_MATCH_PCT}% in
 * place: nothing changes.
 * <p>
 * Found as authored (ferry west): the live islet itself is copied (with whatever was built on it since), taken out
 * and pasted back turned around its walkable deck's middle, so the deck stays where it is and the mooring chain
 * hangs east into the Veil. West of {@link ExileSaintClear#BL_SAFE_WEST} only blocks that are still the schematic's
 * own move (the chain); the Borderlands stay. Found already turned: only the record is corrected. Either way the
 * causeway is laid again to the landing and the Ferryman, the bell, the deck and the skyway follow the new origin.
 * Without {@code apply} it only reports.
 */
public final class ExileQuayTurn {

    private static final int SEARCH_WEST = 170;
    private static final int SEARCH_EAST = 200;
    private static final int SEARCH_Z = 130;
    private static final int SEARCH_DOWN = 30;
    private static final int SEARCH_UP = 35;
    private static final int CHUNKS_PER_TICK = 8;
    private static final int MIN_MATCH_PCT = 55;
    /** A block this common inside the search box is no anchor. */
    private static final int ANCHOR_CAP = 48;
    /** Middle of the walkable deck in the authored frame (x -64 .. +6): the turn keeps it in place. */
    private static final int DECK_MIDDLE_X = ExileQuayPaste.TURN_SHIFT_X / 2;

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private record Rel(int x, int y, int z, Material material) {
    }

    private record Pose(int ox, int oy, int oz, int k) {
    }

    private record Scored(Pose pose, int votes, int match) {
    }

    private ExileQuayTurn() {
    }

    // ------------------------------------------------------------------ orientation (k: quarter turns, +4 = mirrored x first)

    /** x of the authored offset {@code (x, z)} under orientation {@code k} (WorldEdit's rotateY quarter turns). */
    static int tx(int k, int x, int z) {
        int mx = k >= 4 ? -x : x;
        return switch (k & 3) {
            case 0 -> mx;
            case 1 -> z;
            case 2 -> -mx;
            default -> -z;
        };
    }

    static int tz(int k, int x, int z) {
        int mx = k >= 4 ? -x : x;
        return switch (k & 3) {
            case 0 -> z;
            case 1 -> -mx;
            case 2 -> -z;
            default -> mx;
        };
    }

    private static String describe(int k) {
        return (k & 3) * 90 + "°" + (k >= 4 ? " mirrored" : "");
    }

    // ------------------------------------------------------------------ entry

    public static void run(AetherionCore plugin, CommandSender sender, boolean apply) {
        String worldName = plugin.getConfig().getString("exile.quay.world", "");
        World w = Bukkit.getWorld(worldName == null || worldName.isBlank() ? "world" : worldName);
        if (w == null) {
            report(plugin, sender, "§c[QuayTurn] world '" + worldName + "' not loaded. Nothing changed.");
            return;
        }
        int[] recorded = parse(plugin.getConfig().getString("exile.quay.origin", ""));
        int[] centre = recorded != null ? recorded
                : new int[] {ExileQuayPaste.DEFAULT_X, ExileQuayPaste.DEFAULT_Y, ExileQuayPaste.DEFAULT_Z};
        File schem = ExileQuayPaste.resolve(plugin, "veil_quay.schem");
        if (schem == null || (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null
                && Bukkit.getPluginManager().getPlugin("WorldEdit") == null)) {
            report(plugin, sender, "§c[QuayTurn] needs FastAsyncWorldEdit/WorldEdit and veil_quay.schem. Nothing changed.");
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            report(plugin, sender, "§c[QuayTurn] already running.");
            return;
        }
        report(plugin, sender, "§7[QuayTurn] " + (apply ? "APPLY" : "dry run") + ": looking for the islet around "
                + centre[0] + " " + centre[1] + " " + centre[2] + " (recorded facing "
                + (ExileQuay.turnedIn(plugin) ? "east" : "west") + ")");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Clipboard clipboard;
            List<Rel> rels = new ArrayList<>();
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(schem);
                if (format == null) {
                    throw new IllegalStateException("unknown schematic format");
                }
                try (ClipboardReader reader = format.getReader(new FileInputStream(schem))) {
                    clipboard = reader.read();
                }
                BlockVector3 co = clipboard.getOrigin();
                Map<BlockType, Material> materials = new HashMap<>();
                for (BlockVector3 p : clipboard.getRegion()) {
                    BaseBlock block = clipboard.getFullBlock(p);
                    if (block.getBlockType().getMaterial().isAir()) {
                        continue;
                    }
                    Material m = materials.computeIfAbsent(block.getBlockType(), BukkitAdapter::adapt);
                    if (m != null && !m.isAir()) {
                        BlockVector3 r = p.subtract(co);
                        rels.add(new Rel(r.x(), r.y(), r.z(), m));
                    }
                }
            } catch (Throwable t) {
                fail(plugin, sender, null, t);
                return;
            }
            Clipboard schematic = clipboard;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    locate(plugin, sender, w, centre, schematic, rels, apply);
                } catch (Throwable t) {
                    fail(plugin, sender, null, t);
                }
            });
        });
    }

    // ------------------------------------------------------------------ locate

    private static void locate(AetherionCore plugin, CommandSender sender, World w, int[] centre, Clipboard schematic,
                               List<Rel> rels, boolean apply) {
        Map<Material, List<Rel>> byMaterial = new EnumMap<>(Material.class);
        for (Rel r : rels) {
            byMaterial.computeIfAbsent(r.material(), m -> new ArrayList<>()).add(r);
        }
        Map<Material, List<Rel>> anchors = new EnumMap<>(Material.class);
        for (Map.Entry<Material, List<Rel>> e : byMaterial.entrySet()) {
            if (e.getValue().size() <= 2) {
                anchors.put(e.getKey(), e.getValue());
            }
        }
        if (anchors.isEmpty()) {
            fail(plugin, sender, null, new IllegalStateException("the schematic has no rare blocks to look for"));
            return;
        }
        List<int[]> chunks = new ArrayList<>();
        for (int cx = (centre[0] - SEARCH_WEST) >> 4; cx <= (centre[0] + SEARCH_EAST) >> 4; cx++) {
            for (int cz = (centre[2] - SEARCH_Z) >> 4; cz <= (centre[2] + SEARCH_Z) >> 4; cz++) {
                chunks.add(new int[] {cx, cz});
            }
        }
        int yMin = Math.max(w.getMinHeight(), centre[1] - SEARCH_DOWN);
        int yMax = Math.min(w.getMaxHeight() - 1, centre[1] + SEARCH_UP);
        Map<Material, List<int[]>> hits = new EnumMap<>(Material.class);
        int[] next = {0};
        int[] skipped = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            try {
                for (int n = 0; n < CHUNKS_PER_TICK && next[0] < chunks.size(); n++, next[0]++) {
                    int[] c = chunks.get(next[0]);
                    if (!w.isChunkGenerated(c[0], c[1])) {
                        skipped[0]++; // never generate the void around the quay
                        continue;
                    }
                    ChunkSnapshot snap = w.getChunkAt(c[0], c[1]).getChunkSnapshot(false, false, false);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = yMin; y <= yMax; y++) {
                                Material m = snap.getBlockType(x, y, z);
                                if (anchors.containsKey(m)) {
                                    hits.computeIfAbsent(m, k -> new ArrayList<>()).add(new int[] {(c[0] << 4) + x, y, (c[1] << 4) + z});
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                task.cancel();
                fail(plugin, sender, null, t);
                return;
            }
            if (next[0] < chunks.size()) {
                return;
            }
            task.cancel();
            try {
                decide(plugin, sender, w, schematic, rels, anchors, hits, chunks.size() - skipped[0], apply);
            } catch (Throwable t) {
                fail(plugin, sender, null, t);
            }
        }, 1L, 1L);
    }

    private static void decide(AetherionCore plugin, CommandSender sender, World w, Clipboard schematic, List<Rel> rels,
                               Map<Material, List<Rel>> anchors, Map<Material, List<int[]>> hits, int scanned, boolean apply) {
        Map<Pose, Integer> votes = new HashMap<>();
        List<String> used = new ArrayList<>();
        for (Map.Entry<Material, List<int[]>> e : hits.entrySet()) {
            if (e.getValue().size() > ANCHOR_CAP) {
                continue; // too common out there to say anything
            }
            used.add(e.getKey().name().toLowerCase(java.util.Locale.ROOT) + "×" + e.getValue().size());
            for (int[] at : e.getValue()) {
                for (Rel r : anchors.get(e.getKey())) {
                    for (int k = 0; k < 8; k++) {
                        Pose pose = new Pose(at[0] - tx(k, r.x(), r.z()), at[1] - r.y(), at[2] - tz(k, r.x(), r.z()), k);
                        votes.merge(pose, 1, Integer::sum);
                    }
                }
            }
        }
        List<Map.Entry<Pose, Integer>> ranked = new ArrayList<>(votes.entrySet());
        ranked.sort(Map.Entry.<Pose, Integer>comparingByValue().reversed());
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < Math.min(8, ranked.size()); i++) {
            Pose p = ranked.get(i).getKey();
            if (ranked.get(i).getValue() < 2 && !scored.isEmpty()) {
                break;
            }
            scored.add(new Scored(p, ranked.get(i).getValue(), match(w, p, rels)));
        }
        scored.sort(Comparator.comparingInt(Scored::match).reversed());
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(3, scored.size()); i++) {
            Scored s = scored.get(i);
            top.append(i == 0 ? "" : " | ").append(s.pose().ox()).append(' ').append(s.pose().oy()).append(' ').append(s.pose().oz())
                    .append(' ').append(describe(s.pose().k())).append(", ").append(s.votes()).append(" votes, ")
                    .append(pct(s.match(), rels.size())).append('%');
        }
        report(plugin, sender, "§7[QuayTurn] scanned " + scanned + " chunks, anchors " + used + "; best: "
                + (top.length() == 0 ? "none" : top));
        Scored best = scored.isEmpty() ? null : scored.get(0);
        if (best == null || pct(best.match(), rels.size()) < MIN_MATCH_PCT) {
            RUNNING.set(false);
            report(plugin, sender, "§c[QuayTurn] islet not found (needs " + MIN_MATCH_PCT + "% of " + rels.size()
                    + " blocks in place). Nothing changed.");
            return;
        }
        Pose o = best.pose();
        String found = "§a[QuayTurn] islet found at " + o.ox() + " " + o.oy() + " " + o.oz() + ", " + describe(o.k())
                + " from the schematic, " + best.match() + "/" + rels.size() + " blocks in place";
        if (o.k() == 2) {
            alreadyTurned(plugin, sender, w, o, found, apply);
            return;
        }
        plan(plugin, sender, w, schematic, rels, o, found, apply);
    }

    private static int match(World w, Pose p, List<Rel> rels) {
        int n = 0;
        for (Rel r : rels) {
            if (w.getBlockAt(p.ox() + tx(p.k(), r.x(), r.z()), p.oy() + r.y(), p.oz() + tz(p.k(), r.x(), r.z())).getType() == r.material()) {
                n++;
            }
        }
        return n;
    }

    private static int pct(int part, int whole) {
        return whole <= 0 ? 0 : (int) Math.floor(part * 100.0 / whole);
    }

    // ------------------------------------------------------------------ already turned: fix the record

    private static void alreadyTurned(AetherionCore plugin, CommandSender sender, World w, Pose o, String found, boolean apply) {
        int[] now = parse(plugin.getConfig().getString("exile.quay.origin", ""));
        boolean right = now != null && now[0] == o.ox() && now[1] == o.oy() && now[2] == o.oz() && ExileQuay.turnedIn(plugin)
                && w.getName().equals(plugin.getConfig().getString("exile.quay.world", ""));
        report(plugin, sender, found + "; it already sails east" + (right ? " and the record matches. Nothing to do." : "."));
        if (right || !apply) {
            RUNNING.set(false);
            if (!right) {
                report(plugin, sender, "§7[QuayTurn] dry run: apply records origin " + o.ox() + " " + o.oy() + " " + o.oz() + " facing east.");
            }
            return;
        }
        String causeway = plugin.getConfig().getString("exile.quay.causeway", "");
        ExileCauseway.undo(plugin, null);
        record(plugin, sender, w, new int[] {o.ox(), o.oy(), o.oz()}, causeway, "record corrected (no blocks moved)");
    }

    // ------------------------------------------------------------------ turn

    private static void plan(AetherionCore plugin, CommandSender sender, World w, Clipboard schematic, List<Rel> rels, Pose o,
                             String found, boolean apply) {
        int k = o.k();
        BlockVector3 co = schematic.getOrigin();
        BlockVector3 lo = schematic.getMinimumPoint().subtract(co);
        BlockVector3 hi = schematic.getMaximumPoint().subtract(co);
        // the islet's box in the world as it stands
        int[] xs = {tx(k, lo.x(), lo.z()), tx(k, hi.x(), lo.z()), tx(k, lo.x(), hi.z()), tx(k, hi.x(), hi.z())};
        int[] zs = {tz(k, lo.x(), lo.z()), tz(k, hi.x(), lo.z()), tz(k, lo.x(), hi.z()), tz(k, hi.x(), hi.z())};
        BlockVector3 min = BlockVector3.at(o.ox() + minOf(xs), o.oy() + lo.y(), o.oz() + minOf(zs));
        BlockVector3 max = BlockVector3.at(o.ox() + maxOf(xs), o.oy() + hi.y(), o.oz() + maxOf(zs));
        // the walkable deck's middle stays where it is: n = o + T(c) - R180(c)
        int nx = o.ox() + tx(k, DECK_MIDDLE_X, 0) + DECK_MIDDLE_X;
        int nz = o.oz() + tz(k, DECK_MIDDLE_X, 0);
        int[] n = {nx, o.oy(), nz};
        int westEdge = n[0] - hi.x();
        if (westEdge <= ExileSaintClear.BL_SAFE_WEST) {
            RUNNING.set(false);
            report(plugin, sender, found + "; turned it would reach x=" + westEdge + " (Borderlands safe line "
                    + ExileSaintClear.BL_SAFE_WEST + "). Nothing changed.");
            return;
        }
        // west of the safe line only the schematic's own blocks move (the chain); the Borderlands stay
        List<BlockVector3> westOwn = new ArrayList<>();
        int westOther = 0;
        for (Rel r : rels) {
            int x = o.ox() + tx(k, r.x(), r.z());
            if (x > ExileSaintClear.BL_SAFE_WEST) {
                continue;
            }
            int y = o.oy() + r.y();
            int z = o.oz() + tz(k, r.x(), r.z());
            if (w.getBlockAt(x, y, z).getType() == r.material()) {
                westOwn.add(BlockVector3.at(x, y, z));
            } else {
                westOther++;
            }
        }
        // what already stands where the turned islet goes but outside its old box: left in place, counted
        int destMinX = n[0] - hi.x();
        int destMaxX = n[0] - lo.x();
        int destMinZ = n[2] - hi.z();
        int destMaxZ = n[2] - lo.z();
        int foreign = 0;
        for (int x = destMinX; x <= destMaxX; x++) {
            for (int z = destMinZ; z <= destMaxZ; z++) {
                boolean inOld = x >= min.x() && x <= max.x() && z >= min.z() && z <= max.z();
                if (inOld) {
                    continue;
                }
                for (int y = min.y(); y <= max.y(); y++) {
                    if (!w.getBlockAt(x, y, z).getType().isAir()) {
                        foreign++;
                    }
                }
            }
        }
        report(plugin, sender, found + "; turning it to " + n[0] + " " + n[1] + " " + n[2] + " facing east (old box x "
                + min.x() + ".." + max.x() + ", new x " + destMinX + ".." + destMaxX + "; " + westOwn.size()
                + " chain blocks west of x " + ExileSaintClear.BL_SAFE_WEST + " move, " + westOther
                + " there stay; " + foreign + " other blocks already stand in the new box and stay)");
        if (!apply) {
            RUNNING.set(false);
            report(plugin, sender, "§7[QuayTurn] dry run done. /exile turnquay apply does it.");
            return;
        }
        String causeway = plugin.getConfig().getString("exile.quay.causeway", "");
        // lift the causeway first (its record has to match the world); it is laid again to the new landing
        ExileCauseway.undo(plugin, null);
        long start = System.currentTimeMillis();
        int[] m = turnMatrix(k);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int changed;
            try {
                CuboidRegion region = new CuboidRegion(BukkitAdapter.adapt(w), min, max);
                BlockArrayClipboard live = new BlockArrayClipboard(region);
                live.setOrigin(BlockVector3.at(o.ox(), o.oy(), o.oz()));
                try (EditSession read = WorldEdit.getInstance().newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(w)).maxBlocks(-1).build()) {
                    ForwardExtentCopy copy = new ForwardExtentCopy(read, region, live, region.getMinimumPoint());
                    copy.setCopyingEntities(false);
                    Operations.complete(copy);
                }
                java.util.Set<BlockVector3> chain = new java.util.HashSet<>(westOwn);
                if (min.x() <= ExileSaintClear.BL_SAFE_WEST) {
                    for (int x = min.x(); x <= Math.min(max.x(), ExileSaintClear.BL_SAFE_WEST); x++) {
                        for (int y = min.y(); y <= max.y(); y++) {
                            for (int z = min.z(); z <= max.z(); z++) {
                                BlockVector3 p = BlockVector3.at(x, y, z);
                                if (!chain.contains(p)) {
                                    live.setBlock(p, BlockTypes.AIR.getDefaultState());
                                }
                            }
                        }
                    }
                }
                try (EditSession edit = WorldEdit.getInstance().newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(w)).maxBlocks(-1).build()) {
                    try {
                        edit.setFastMode(true);
                    } catch (Throwable ignored) {
                    }
                    int eastMin = Math.max(min.x(), ExileSaintClear.BL_SAFE_WEST + 1);
                    if (eastMin <= max.x()) {
                        edit.setBlocks(new CuboidRegion(BlockVector3.at(eastMin, min.y(), min.z()), max), BlockTypes.AIR.getDefaultState());
                    }
                    for (BlockVector3 p : westOwn) {
                        edit.setBlock(p, BlockTypes.AIR.getDefaultState());
                    }
                    ClipboardHolder holder = new ClipboardHolder(live);
                    holder.setTransform(k == 0 ? new AffineTransform().rotateY(180)
                            : new AffineTransform(m[0], 0, m[1], 0, 0, 1, 0, 0, m[2], 0, m[3], 0));
                    Operations.complete(holder.createPaste(edit)
                            .to(BlockVector3.at(n[0], n[1], n[2]))
                            .ignoreAirBlocks(true)
                            .copyEntities(false)
                            .copyBiomes(false)
                            .build());
                    changed = edit.getBlockChangeCount();
                }
            } catch (Throwable t) {
                fail(plugin, sender, causeway, t);
                return;
            }
            long ms = System.currentTimeMillis() - start;
            Bukkit.getScheduler().runTask(plugin, () -> record(plugin, sender, w, n, causeway,
                    "islet turned (" + changed + " block changes, " + ms + " ms)"));
        });
    }

    /** The paste transform R180 · T⁻¹ as {x←x, x←z, z←x, z←z}: the live islet, turned 180° from the authored frame. */
    private static int[] turnMatrix(int k) {
        int t00 = tx(k, 1, 0);
        int t01 = tx(k, 0, 1);
        int t10 = tz(k, 1, 0);
        int t11 = tz(k, 0, 1);
        int det = t00 * t11 - t01 * t10;
        // inverse of a signed permutation: (1/det) [t11 -t01; -t10 t00]; then R180 negates it
        return new int[] {-t11 * det, t01 * det, t10 * det, -t00 * det};
    }

    private static void record(AetherionCore plugin, CommandSender sender, World w, int[] n, String causeway, String what) {
        RUNNING.set(false);
        plugin.getConfig().set("exile.quay.world", w.getName());
        plugin.getConfig().set("exile.quay.origin", n[0] + " " + n[1] + " " + n[2]);
        plugin.getConfig().set("exile.quay.facing", "east");
        plugin.getConfig().set("exile.quay.causeway", causeway == null ? "" : causeway);
        plugin.saveConfig();
        ExileQuay quay = plugin.exileQuay();
        if (quay != null) {
            quay.setOrigin(w, n);
        }
        if (causeway != null && !causeway.isBlank()) {
            ExileCauseway.relay(plugin, sender);
        }
        boolean bell = quay != null && quay.bellInPlace();
        report(plugin, sender, "§a[QuayTurn] done: " + what + "; origin " + n[0] + " " + n[1] + " " + n[2]
                + " facing east, ferry bell " + (bell ? "in place" : "NOT at its spot") + (causeway == null || causeway.isBlank()
                ? ", no causeway recorded" : ", causeway laid to the landing"));
    }

    private static void fail(AetherionCore plugin, CommandSender sender, String causeway, Throwable t) {
        plugin.getLogger().log(Level.SEVERE, "[QuayTurn] failed", t);
        Runnable back = () -> {
            RUNNING.set(false);
            if (causeway != null && !causeway.isBlank() && plugin.getConfig().getString("exile.quay.causeway", "").isBlank()) {
                // the islet was not turned: the causeway goes back where it was
                plugin.getConfig().set("exile.quay.causeway", causeway);
                plugin.saveConfig();
                ExileCauseway.relay(plugin, sender);
            }
            report(plugin, sender, "§c[QuayTurn] failed: §7" + t);
        };
        if (Bukkit.isPrimaryThread()) {
            back.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, back);
        }
    }

    /** Chat for the sender, and the log too (RCON loses async replies). */
    private static void report(AetherionCore plugin, CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
        plugin.getLogger().info(line.replaceAll("§.", ""));
    }

    private static int minOf(int[] v) {
        int m = Integer.MAX_VALUE;
        for (int x : v) {
            m = Math.min(m, x);
        }
        return m;
    }

    private static int maxOf(int[] v) {
        int m = Integer.MIN_VALUE;
        for (int x : v) {
            m = Math.max(m, x);
        }
        return m;
    }

    private static int[] parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] p = raw.trim().replace(",", " ").split("\\s+");
        if (p.length < 3) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
