package de.aetherion.guilds.logistics;

import de.aetherion.guilds.logistics.BeltBodies.Run;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The conveyor line. The real tile stays the iron plate (frame / walkway / data); each straight run is dressed
 * with display entities (see {@link BeltBodies} for the look), only while someone is on the island and only near
 * them. While goods move, the chevron cleats run at the cargo's pace: each one glides the whole run in one long
 * client-side interpolation, dives into the head drum, jumps back hidden and rises out of the tail drum, so the
 * belt runs smoothly with four packets per cleat and loop. Gold marker cleats glow while the line is live; an idle
 * line stops where it is. Runs are merged, culled by distance, capped per island and server-wide, never saved.
 */
final class BeltVisuals {

    static final String TAG = "aeg_belt";

    private static final int GLIDE = 0;
    private static final int SINK = 1;
    private static final int JUMP = 2;
    private static final int RISE = 3;
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    /** One chevron cleat: two arms moving as one, with the keyframe they are on. */
    private static final class Cleat {
        final BlockDisplay[] arms = new BlockDisplay[2];
        boolean marker;
        int phase = GLIDE;
        float fromFlow;
        float fromSunk;
        float toFlow;
        float toSunk;
        long start;
        int duration;
        long nextAt;
    }

    private static final class RunView {
        Run run;
        final List<Entity> parts = new ArrayList<>();
        final List<Cleat> cleats = new ArrayList<>();
        boolean moving;
    }

    private final Map<String, Map<String, RunView>> views = new HashMap<>();
    private final Map<String, List<Run>> runs = new HashMap<>();
    private final Map<String, String> runsVersion = new HashMap<>();
    private int entities;
    private int radius = 40;
    private int perHostRuns = 80;
    private int globalEntities = 3000;
    private boolean enabled = true;

    void configure(boolean enabled, int radius, int perHostRuns, int globalEntities) {
        this.enabled = enabled;
        this.radius = Math.max(16, radius);
        this.perHostRuns = Math.max(4, perHostRuns);
        this.globalEntities = Math.max(256, globalEntities);
    }

    // ------------------------------------------------------------------------------------------------
    // runs
    // ------------------------------------------------------------------------------------------------

    private List<Run> runs(String hostKey, Map<Long, Belt> belts, int version, Set<Long> plugEnds, Set<Long> chuteStarts) {
        String stamp = version + "|" + plugEnds.hashCode() + "|" + chuteStarts.hashCode();
        String cached = runsVersion.get(hostKey);
        List<Run> known = runs.get(hostKey);
        if (known != null && stamp.equals(cached)) {
            return known;
        }
        List<Run> out = new ArrayList<>();
        for (Belt belt : belts.values()) {
            Belt behind = belts.get(LogisticsService.pos(belt.x() - belt.dir().getModX(), belt.y(),
                    belt.z() - belt.dir().getModZ()));
            if (behind != null && behind.dir() == belt.dir()) {
                continue; // not the tail of its run
            }
            List<Belt> tiles = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            Belt current = belt;
            while (current != null && current.dir() == belt.dir() && seen.add(current.key()) && tiles.size() < 256) {
                tiles.add(current);
                current = belts.get(current.nextKey());
            }
            out.add(build(tiles, belts, plugEnds, chuteStarts));
        }
        runs.put(hostKey, out);
        runsVersion.put(hostKey, stamp);
        return out;
    }

    private static Run build(List<Belt> tiles, Map<Long, Belt> belts, Set<Long> plugEnds, Set<Long> chuteStarts) {
        Belt first = tiles.get(0);
        Belt last = tiles.get(tiles.size() - 1);
        BlockFace dir = first.dir();
        int minX = Math.min(first.x(), last.x());
        int minZ = Math.min(first.z(), last.z());
        int n = tiles.size();
        boolean[] leftOpen = new boolean[n];
        boolean[] rightOpen = new boolean[n];
        List<int[]> stubs = new ArrayList<>();
        List<Long> keys = new ArrayList<>(n);
        boolean alongX = dir == BlockFace.EAST || dir == BlockFace.WEST;
        boolean tailFedFromSide = false;
        for (Belt tile : tiles) {
            keys.add(tile.key());
            int index = alongX ? tile.x() - minX : tile.z() - minZ;
            for (int side = -1; side <= 1; side += 2) {
                int sx = alongX ? tile.x() : tile.x() + side;
                int sz = alongX ? tile.z() + side : tile.z();
                Belt neighbour = belts.get(LogisticsService.pos(sx, tile.y(), sz));
                if (neighbour == null || neighbour.nextX() != tile.x() || neighbour.nextZ() != tile.z()) {
                    continue;
                }
                if (side < 0) {
                    leftOpen[index] = true;
                } else {
                    rightOpen[index] = true;
                }
                stubs.add(new int[]{index, side});
                if (tile == first) {
                    tailFedFromSide = true;
                }
            }
        }
        Belt straightBehind = belts.get(LogisticsService.pos(first.x() - dir.getModX(), first.y(), first.z() - dir.getModZ()));
        boolean turn = tailFedFromSide && (straightBehind == null || straightBehind.nextKey() != first.key());
        return new Run(minX, first.y(), minZ, dir, n, keys, leftOpen, rightOpen, stubs, turn,
                plugEnds.contains(last.key()), chuteStarts.contains(first.key()));
    }

    // ------------------------------------------------------------------------------------------------
    // render (every second, per active island)
    // ------------------------------------------------------------------------------------------------

    void render(String hostKey, World world, Map<Long, Belt> belts, int version, Collection<Location> viewers,
                Set<Long> flowing, Set<Long> plugEnds, Set<Long> chuteStarts, long tick) {
        if (!enabled || world == null || belts.isEmpty() || viewers.isEmpty()) {
            clearHost(hostKey);
            return;
        }
        List<Run> all = runs(hostKey, belts, version, plugEnds, chuteStarts);
        double r2 = (double) radius * radius;
        List<Run> wanted = new ArrayList<>();
        for (Run run : all) {
            if (nearest(run, viewers) <= r2) {
                wanted.add(run);
            }
        }
        if (wanted.size() > perHostRuns) {
            wanted.sort((a, b) -> Double.compare(nearest(a, viewers), nearest(b, viewers)));
            wanted = wanted.subList(0, perHostRuns);
        }
        Map<String, RunView> shown = views.computeIfAbsent(hostKey, k -> new HashMap<>());
        Set<String> keep = new HashSet<>();
        for (Run run : wanted) {
            keep.add(run.key());
        }
        Iterator<Map.Entry<String, RunView>> it = shown.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RunView> entry = it.next();
            if (!keep.contains(entry.getKey()) || !alive(entry.getValue())) {
                kill(entry.getValue());
                it.remove();
            }
        }
        for (Run run : wanted) {
            RunView view = shown.get(run.key());
            if (view == null) {
                if (entities >= globalEntities || !world.isChunkLoaded(run.minX() >> 4, run.minZ() >> 4)) {
                    continue;
                }
                view = spawn(world, run);
                shown.put(run.key(), view);
            }
            boolean moving = false;
            for (Long key : run.tiles()) {
                if (flowing.contains(key)) {
                    moving = true;
                    break;
                }
            }
            if (moving != view.moving) {
                view.moving = moving;
                for (Cleat cleat : view.cleats) {
                    if (moving) {
                        aim(run, cleat, tick + 1);
                    } else {
                        hold(run, cleat, tick);
                    }
                    if (cleat.marker) {
                        for (BlockDisplay arm : cleat.arms) {
                            if (arm != null && arm.isValid()) {
                                arm.setBrightness(moving ? LIT : null);
                            }
                        }
                    }
                }
            }
        }
    }

    private static double nearest(Run run, Collection<Location> viewers) {
        double best = Double.MAX_VALUE;
        for (Location viewer : viewers) {
            best = Math.min(best, run.distanceSq(viewer.getX(), viewer.getZ()));
        }
        return best;
    }

    private static boolean alive(RunView view) {
        for (Entity part : view.parts) {
            if (!part.isValid()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // motion (every tick; only cleats whose keyframe is due do anything)
    // ------------------------------------------------------------------------------------------------

    void tick(long tick) {
        if (views.isEmpty()) {
            return;
        }
        for (Map<String, RunView> host : views.values()) {
            for (RunView view : host.values()) {
                if (!view.moving) {
                    continue;
                }
                for (Cleat cleat : view.cleats) {
                    if (tick >= cleat.nextAt) {
                        cleat.phase = (cleat.phase + 1) % 4;
                        aim(view.run, cleat, tick);
                    }
                }
            }
        }
    }

    /**
     * Send a cleat on toward the end of its phase, from wherever it is now, at belt speed: glide to the head, dive
     * into the drum, jump back hidden, rise out of the tail drum.
     */
    private static void aim(Run run, Cleat cleat, long tick) {
        float[] now = position(cleat, tick);
        float flow;
        float sunk;
        switch (cleat.phase) {
            case SINK -> {
                flow = BeltBodies.gone(run);
                sunk = 1f;
            }
            case JUMP -> {
                flow = BeltBodies.CLEAT_TAIL;
                sunk = 1f;
            }
            case RISE -> {
                flow = BeltBodies.CLEAT_START;
                sunk = 0f;
            }
            default -> {
                flow = BeltBodies.glideEnd(run);
                sunk = 0f;
            }
        }
        int ticks = cleat.phase == JUMP ? 0 : Math.round(Math.abs(flow - now[0]) * BeltBodies.TICKS_PER_TILE);
        cleat.fromFlow = now[0];
        cleat.fromSunk = now[1];
        cleat.toFlow = flow;
        cleat.toSunk = sunk;
        cleat.start = tick;
        cleat.duration = ticks;
        cleat.nextAt = tick + Math.max(1, ticks);
        move(run, cleat, flow, sunk, ticks);
    }

    /** The line stopped: the cleat stays exactly where it is (and picks its phase up again later). */
    private static void hold(Run run, Cleat cleat, long tick) {
        float[] now = position(cleat, tick);
        cleat.fromFlow = now[0];
        cleat.fromSunk = now[1];
        cleat.toFlow = now[0];
        cleat.toSunk = now[1];
        cleat.start = tick;
        cleat.duration = 0;
        move(run, cleat, now[0], now[1], 0);
    }

    /** Where the cleat is at {@code tick} (flow from the tail, how far it is sunk into the belt). */
    private static float[] position(Cleat cleat, long tick) {
        if (cleat.duration <= 0) {
            return new float[]{cleat.toFlow, cleat.toSunk};
        }
        float t = Math.max(0f, Math.min(1f, (tick - cleat.start) / (float) cleat.duration));
        return new float[]{cleat.fromFlow + (cleat.toFlow - cleat.fromFlow) * t,
                cleat.fromSunk + (cleat.toSunk - cleat.fromSunk) * t};
    }

    private static void move(Run run, Cleat cleat, float flow, float sunk, int duration) {
        Transformation[] arms = BeltBodies.cleat(run, flow, sunk);
        for (int i = 0; i < 2; i++) {
            BlockDisplay arm = cleat.arms[i];
            if (arm == null || !arm.isValid()) {
                continue;
            }
            arm.setInterpolationDelay(0);
            arm.setInterpolationDuration(duration);
            arm.setTransformation(arms[i]);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // building the displays of one run
    // ------------------------------------------------------------------------------------------------

    private RunView spawn(World world, Run run) {
        RunView view = new RunView();
        view.run = run;
        Location corner = new Location(world, run.minX(), run.y(), run.minZ());
        for (BeltBodies.Piece piece : BeltBodies.frame(run)) {
            BlockDisplay display = block(corner, piece.block(), piece.transformation());
            if (piece.glow()) {
                display.setBrightness(LIT);
            }
            view.parts.add(display);
        }
        for (int k = 0; k < BeltBodies.cleats(run); k++) {
            Cleat cleat = new Cleat();
            cleat.marker = BeltBodies.marker(run, k);
            cleat.toFlow = BeltBodies.rest(k);
            Transformation[] arms = BeltBodies.cleat(run, cleat.toFlow, 0f);
            for (int i = 0; i < 2; i++) {
                cleat.arms[i] = block(corner, cleat.marker ? BeltBodies.MARKER : BeltBodies.CLEAT, arms[i]);
                view.parts.add(cleat.arms[i]);
            }
            view.cleats.add(cleat);
        }
        entities += view.parts.size();
        return view;
    }

    private BlockDisplay block(Location corner, BlockData data, Transformation transformation) {
        return corner.getWorld().spawn(corner, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(transformation);
            d.setViewRange(0.55f);
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
    }

    // ------------------------------------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------------------------------------

    private void kill(RunView view) {
        for (Entity part : view.parts) {
            if (part.isValid()) {
                part.remove();
            }
        }
        entities = Math.max(0, entities - view.parts.size());
        view.parts.clear();
        view.cleats.clear();
    }

    void clearHost(String hostKey) {
        Map<String, RunView> shown = views.remove(hostKey);
        if (shown != null) {
            for (RunView view : shown.values()) {
                kill(view);
            }
        }
    }

    /** Belts of a host changed: re-merge runs next render. */
    void invalidate(String hostKey) {
        runsVersion.remove(hostKey);
    }

    void clearAll() {
        for (Map<String, RunView> shown : views.values()) {
            for (RunView view : shown.values()) {
                kill(view);
            }
        }
        views.clear();
        runs.clear();
        runsVersion.clear();
        entities = 0;
    }

    int entityCount() {
        return entities;
    }
}
