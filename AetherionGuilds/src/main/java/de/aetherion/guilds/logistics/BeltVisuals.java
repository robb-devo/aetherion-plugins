package de.aetherion.guilds.logistics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
 * with display entities, only while someone is on the island and only near them:
 * <ul>
 *   <li>a dark <b>belt bed</b> between two <b>timber side guards</b> capped with iron (opened where a side belt
 *   merges in, with a short belt <b>tongue</b> there),</li>
 *   <li>a <b>tread</b> of pale slats across the belt that actually runs downstream while goods move (one keyframe
 *   per slat spacing, client-interpolated, seamless),</li>
 *   <li>steel <b>drums</b> at both ends, a copper <b>turn hub</b> where the line bends,</li>
 *   <li>a copper <b>inlet hood</b> with a green lip where the belt plugs into a machine, an <b>orange lip</b> where
 *   it starts under a chute,</li>
 *   <li><b>chevrons</b> pointing the way: gold and gliding while goods move, dim and still when the line idles.</li>
 * </ul>
 * Runs are merged, culled by distance, capped per island and server-wide, and never saved.
 */
final class BeltVisuals {

    static final String TAG = "aeg_belt";

    private static final float BED_Y = 0.19f;
    private static final float BED_H = 0.05f;
    private static final float BED_W = 0.66f;
    private static final float GUARD_W = 0.09f;
    private static final float GUARD_H = 0.2f;
    private static final float TOP = BED_Y + BED_H;
    private static final int GLIDE_TICKS_PER_TILE = 6;
    private static final int TREAD_TICKS = 8;
    private static final int CHEVRON_SPACING = 3;
    private static final float CHEVRON_SCALE = 2.1f;
    private static final TextColor LIVE = TextColor.color(0xF2C14E);
    private static final TextColor IDLE = TextColor.color(0x8A8A8A);

    private static final BlockData BED = Material.BLACK_CONCRETE.createBlockData();
    private static final BlockData GUARD = Material.STRIPPED_DARK_OAK_WOOD.createBlockData();
    private static final BlockData CAP = Material.IRON_BLOCK.createBlockData();
    private static final BlockData SLAT = Material.SMOOTH_STONE.createBlockData();
    private static final BlockData DRUM = Material.POLISHED_DEEPSLATE.createBlockData();
    private static final BlockData AXLE_X = Bukkit.createBlockData("minecraft:chain[axis=x]");
    private static final BlockData AXLE_Z = Bukkit.createBlockData("minecraft:chain[axis=z]");
    private static final BlockData COPPER = Material.WAXED_CUT_COPPER.createBlockData();
    private static final BlockData HUB = Material.WAXED_COPPER_BLOCK.createBlockData();
    private static final BlockData LIP_IN = Material.LIME_CONCRETE.createBlockData();
    private static final BlockData LIP_OUT = Material.ORANGE_CONCRETE.createBlockData();

    /** A straight stretch of tiles with one direction. {@code minX/minZ} = its lowest tile corner. */
    record Run(int minX, int y, int minZ, BlockFace dir, int length, List<Long> tiles, boolean[] leftOpen,
               boolean[] rightOpen, List<int[]> stubs, boolean turn, boolean plugged, boolean chute) {

        boolean alongX() {
            return dir == BlockFace.EAST || dir == BlockFace.WEST;
        }

        boolean forward() {
            return dir == BlockFace.EAST || dir == BlockFace.SOUTH;
        }

        /** Box coordinate along the run of a flow position (0 = tail, length = head). */
        float at(float flow) {
            return forward() ? flow : length - flow;
        }

        String key() {
            return minX + "," + y + "," + minZ + "," + dir.ordinal() + "," + length + "," + stubs.size()
                    + "," + open(leftOpen) + "," + open(rightOpen) + "," + turn + plugged + chute;
        }

        private static String open(boolean[] flags) {
            StringBuilder out = new StringBuilder(flags.length);
            for (boolean flag : flags) {
                out.append(flag ? '1' : '0');
            }
            return out.toString();
        }

        double distanceSq(double x, double z) {
            double maxX = minX + (alongX() ? length : 1);
            double maxZ = minZ + (alongX() ? 1 : length);
            double dx = x < minX ? minX - x : Math.max(0, x - maxX);
            double dz = z < minZ ? minZ - z : Math.max(0, z - maxZ);
            return dx * dx + dz * dz;
        }
    }

    private static final class Chevron {
        TextDisplay entity;
        float home;
        float offset;
        boolean resetNext;
        long nextAt;
    }

    private static final class RunView {
        Run run;
        final List<Entity> parts = new ArrayList<>();
        final List<Chevron> chevrons = new ArrayList<>();
        final List<BlockDisplay> slats = new ArrayList<>();
        final List<Float> slatHome = new ArrayList<>();
        boolean moving;
        long treadAt;
        boolean treadSnap;
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
                view = spawn(world, run, tick);
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
                view.treadAt = tick + 1;
                view.treadSnap = true;
                for (Chevron chevron : view.chevrons) {
                    chevron.offset = chevron.home;
                    chevron.resetNext = false;
                    chevron.nextAt = tick + 1;
                    if (chevron.entity != null && chevron.entity.isValid()) {
                        chevron.entity.text(Component.text("›", moving ? LIVE : IDLE).decorate(TextDecoration.BOLD));
                        chevron.entity.setBrightness(moving ? new Display.Brightness(15, 15) : null);
                        if (!moving) {
                            place(chevron.entity, run, chevron.home, 0);
                        }
                    }
                }
                if (!moving) {
                    for (int i = 0; i < view.slats.size(); i++) {
                        slat(view.slats.get(i), run, view.slatHome.get(i), 0);
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
    // motion (every tick; only parts whose keyframe is due do anything)
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
                treads(view, tick);
                int length = view.run.length();
                for (Chevron chevron : view.chevrons) {
                    if (tick < chevron.nextAt || chevron.entity == null || !chevron.entity.isValid()) {
                        continue;
                    }
                    if (chevron.resetNext) {
                        chevron.offset = 0f;
                        place(chevron.entity, view.run, 0f, 0);
                        chevron.resetNext = false;
                        chevron.nextAt = tick + 1;
                        continue;
                    }
                    int duration = Math.max(1, Math.round((length - chevron.offset) * GLIDE_TICKS_PER_TILE));
                    place(chevron.entity, view.run, length, duration);
                    chevron.resetNext = true;
                    chevron.nextAt = tick + duration;
                }
            }
        }
    }

    /**
     * The tread runs: every slat slides one spacing downstream, then all snap back together. The pattern is
     * periodic, so the snap is invisible and the belt looks like it runs forever.
     */
    private static void treads(RunView view, long tick) {
        if (view.slats.isEmpty() || tick < view.treadAt) {
            return;
        }
        if (view.treadSnap) {
            for (int i = 0; i < view.slats.size(); i++) {
                slat(view.slats.get(i), view.run, view.slatHome.get(i), 0);
            }
            view.treadSnap = false;
            view.treadAt = tick + 1;
            return;
        }
        for (int i = 0; i < view.slats.size(); i++) {
            slat(view.slats.get(i), view.run, view.slatHome.get(i) + 1f, TREAD_TICKS - 1);
        }
        view.treadSnap = true;
        view.treadAt = tick + TREAD_TICKS - 1;
    }

    // ------------------------------------------------------------------------------------------------
    // building the displays of one run
    // ------------------------------------------------------------------------------------------------

    private RunView spawn(World world, Run run, long tick) {
        RunView view = new RunView();
        view.run = run;
        Location corner = new Location(world, run.minX(), run.y(), run.minZ());
        boolean alongX = run.alongX();
        float len = run.length();
        float inset = (1f - BED_W) / 2f;
        // belt bed
        view.parts.add(block(corner, BED, box(alongX, 0f, len, inset, 1f - inset, BED_Y, BED_H)));
        // timber guards with an iron cap, broken where a side belt merges in
        for (int side = -1; side <= 1; side += 2) {
            boolean[] open = side < 0 ? run.leftOpen() : run.rightOpen();
            float across = side < 0 ? 0.03f : 1f - 0.03f - GUARD_W;
            int start = -1;
            for (int i = 0; i <= run.length(); i++) {
                boolean solid = i < run.length() && !open[i];
                if (solid && start < 0) {
                    start = i;
                } else if (!solid && start >= 0) {
                    view.parts.add(block(corner, GUARD, box(alongX, start, i, across, across + GUARD_W, BED_Y - 0.02f,
                            GUARD_H)));
                    view.parts.add(block(corner, CAP, box(alongX, start, i, across - 0.01f, across + GUARD_W + 0.01f,
                            BED_Y - 0.02f + GUARD_H, 0.03f)));
                    start = -1;
                }
            }
        }
        // a short belt tongue where a side belt merges in
        for (int[] stub : run.stubs()) {
            float a = stub[0] + inset;
            float b = stub[0] + 1f - inset;
            float c0 = stub[1] < 0 ? 0f : 1f - inset;
            float c1 = stub[1] < 0 ? inset : 1f;
            view.parts.add(block(corner, BED, box(alongX, a, b, c0, c1, BED_Y, BED_H)));
        }
        // drums at both ends, their axle showing through the guards
        for (float at : new float[]{0.08f, len - 0.08f}) {
            view.parts.add(block(corner, DRUM, box(alongX, at - 0.07f, at + 0.07f, inset - 0.02f, 1f - inset + 0.02f,
                    BED_Y - 0.03f, 0.13f)));
            view.parts.add(block(corner, alongX ? AXLE_Z : AXLE_X, axleBox(alongX, at)));
        }
        // copper hub where the line bends
        if (run.turn()) {
            float a = run.at(0.5f);
            view.parts.add(block(corner, HUB, box(alongX, a - 0.16f, a + 0.16f, 0.34f, 0.66f, TOP, 0.04f)));
        }
        // inlet hood where it plugs into a machine (copper arch + green lip), orange lip under a chute
        if (run.plugged()) {
            float head = run.at(len);
            float inward = run.forward() ? -0.12f : 0.12f;
            float a0 = Math.min(head, head + inward);
            float a1 = Math.max(head, head + inward);
            view.parts.add(block(corner, COPPER, box(alongX, a0, a1, 0.02f, 0.14f, BED_Y, 0.55f)));
            view.parts.add(block(corner, COPPER, box(alongX, a0, a1, 0.86f, 0.98f, BED_Y, 0.55f)));
            view.parts.add(block(corner, COPPER, box(alongX, a0, a1, 0.02f, 0.98f, BED_Y + 0.55f, 0.1f)));
            view.parts.add(glow(corner, LIP_IN, box(alongX, a0, a1, inset, 1f - inset, TOP, 0.025f)));
        }
        if (run.chute()) {
            float tail = run.at(0f);
            float inward = run.forward() ? 0.1f : -0.1f;
            view.parts.add(glow(corner, LIP_OUT, box(alongX, Math.min(tail, tail + inward), Math.max(tail, tail + inward),
                    inset, 1f - inset, TOP, 0.025f)));
        }
        // the tread: one pale slat per tile, running while goods move
        for (int i = 0; i < run.length() - 1; i++) {
            float home = i + 0.5f;
            BlockDisplay slat = block(corner, SLAT, slatBox(run, home));
            view.slats.add(slat);
            view.slatHome.add(home);
            view.parts.add(slat);
        }
        // chevrons
        for (int i = 0; i < run.length(); i += CHEVRON_SPACING) {
            Chevron chevron = new Chevron();
            chevron.home = i + 0.5f;
            chevron.offset = chevron.home;
            chevron.entity = chevron(corner, run, chevron.offset);
            chevron.nextAt = tick + 1 + i;
            if (chevron.entity != null) {
                view.chevrons.add(chevron);
                view.parts.add(chevron.entity);
            }
        }
        entities += view.parts.size();
        return view;
    }

    /** A box along the run: [a, b] along it, [c0, c1] across it, y from {@code y} with height {@code h}. */
    private static Transformation box(boolean alongX, float a, float b, float c0, float c1, float y, float h) {
        Vector3f translation = alongX ? new Vector3f(a, y, c0) : new Vector3f(c0, y, a);
        Vector3f scale = alongX ? new Vector3f(b - a, h, c1 - c0) : new Vector3f(c1 - c0, h, b - a);
        return new Transformation(translation, new Quaternionf(), scale, new Quaternionf());
    }

    /** The chain model is a 3px bar through the block centre along its axis; lay it through the drum. */
    private static Transformation axleBox(boolean alongX, float at) {
        float y = BED_Y + 0.035f - 0.5f;
        Vector3f translation = alongX ? new Vector3f(at - 0.5f, y, 0f) : new Vector3f(0f, y, at - 0.5f);
        return new Transformation(translation, new Quaternionf(), new Vector3f(1f, 1f, 1f), new Quaternionf());
    }

    /** A slat at flow position {@code flow} (tiles from the tail). */
    private static Transformation slatBox(Run run, float flow) {
        float inset = (1f - BED_W) / 2f + 0.04f;
        float a = run.at(flow);
        return box(run.alongX(), a - 0.05f, a + 0.05f, inset, 1f - inset, TOP, 0.012f);
    }

    private static void slat(BlockDisplay slat, Run run, float flow, int duration) {
        if (slat == null || !slat.isValid()) {
            return;
        }
        slat.setInterpolationDelay(0);
        slat.setInterpolationDuration(duration);
        slat.setTransformation(slatBox(run, flow));
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

    private BlockDisplay glow(Location corner, BlockData data, Transformation transformation) {
        BlockDisplay display = block(corner, data, transformation);
        display.setBrightness(new Display.Brightness(15, 15));
        return display;
    }

    private TextDisplay chevron(Location corner, Run run, float offset) {
        TextDisplay display = corner.getWorld().spawn(corner, TextDisplay.class, d -> {
            d.text(Component.text("›", IDLE).decorate(TextDecoration.BOLD));
            d.setBillboard(Display.Billboard.FIXED);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setTextOpacity((byte) 220);
            d.setViewRange(0.4f);
            d.setPersistent(false);
            d.setTeleportDuration(0);
            d.addScoreboardTag(TAG);
        });
        place(display, run, offset, 0);
        return display;
    }

    /** Chevron lying flat on the belt at {@code offset} tiles from the run's tail, pointing downstream. */
    private static void place(TextDisplay display, Run run, float offset, int duration) {
        float angle = switch (run.dir()) {
            case NORTH -> (float) (Math.PI / 2);
            case SOUTH -> (float) (-Math.PI / 2);
            case WEST -> (float) Math.PI;
            default -> 0f;
        };
        Quaternionf rotation = new Quaternionf().rotateY(angle).rotateX((float) (-Math.PI / 2));
        Vector3f glyph = rotation.transform(new Vector3f(0f, 0.11f * CHEVRON_SCALE, 0f));
        float along = run.at(offset);
        float px = run.alongX() ? along : 0.5f;
        float pz = run.alongX() ? 0.5f : along;
        Vector3f translation = new Vector3f(px, TOP + 0.02f, pz).sub(glyph);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(duration);
        display.setTransformation(new Transformation(translation, rotation,
                new Vector3f(CHEVRON_SCALE, CHEVRON_SCALE, CHEVRON_SCALE), new Quaternionf()));
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
        view.chevrons.clear();
        view.slats.clear();
        view.slatHome.clear();
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
