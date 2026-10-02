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
 * What makes a row of plates read as a conveyor. The real tile stays the iron plate (frame / walkway / data);
 * on top of each straight run go a few display entities, only while someone is on the island and only near
 * them:
 * <ul>
 *   <li>a dark rubber <b>belt</b> down the middle (one display per run, however long),</li>
 *   <li>two raised steel <b>side rails</b>, opened where another belt feeds in from the side,</li>
 *   <li>a <b>roller</b> across each end, a short belt <b>stub</b> where a side belt joins,</li>
 *   <li>gold <b>chevrons</b> pointing the way, gliding along while goods actually move on that run.</li>
 * </ul>
 * Motion is client-side interpolation: a chevron gets two updates per trip down its run, nothing per tick.
 * Runs are merged, culled by distance, capped per island and server-wide, and never saved.
 */
final class BeltVisuals {

    static final String TAG = "aeg_belt";

    private static final float BELT_Y = 0.19f;
    private static final float BELT_H = 0.055f;
    private static final float BELT_W = 0.62f;
    private static final float RAIL_H = 0.13f;
    private static final float RAIL_W = 0.10f;
    private static final float TOP = BELT_Y + BELT_H;
    private static final int TICKS_PER_TILE = 10;
    private static final int CHEVRON_SPACING = 2;
    private static final float CHEVRON_SCALE = 2.1f;
    private static final TextColor CHEVRON = TextColor.color(0xE8B23A);

    private static final BlockData BELT = Material.BLACK_CONCRETE.createBlockData();
    private static final BlockData RAIL = Material.IRON_BLOCK.createBlockData();
    private static final BlockData ROLLER_X = Bukkit.createBlockData("minecraft:chain[axis=x]");
    private static final BlockData ROLLER_Z = Bukkit.createBlockData("minecraft:chain[axis=z]");

    /** A straight stretch of tiles with one direction. {@code minX/minZ} = its lowest tile corner. */
    record Run(int minX, int y, int minZ, BlockFace dir, int length, List<Long> tiles, boolean[] leftOpen,
               boolean[] rightOpen, List<int[]> stubs) {

        boolean alongX() {
            return dir == BlockFace.EAST || dir == BlockFace.WEST;
        }

        String key() {
            return minX + "," + y + "," + minZ + "," + dir.ordinal() + "," + length + "," + stubs.size()
                    + "," + open(leftOpen) + "," + open(rightOpen);
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
        boolean moving;
    }

    private final Map<String, Map<String, RunView>> views = new HashMap<>();
    private final Map<String, List<Run>> runs = new HashMap<>();
    private final Map<String, Integer> runsVersion = new HashMap<>();
    private int entities;
    private int radius = 40;
    private int perHostRuns = 80;
    private int globalEntities = 1800;
    private boolean enabled = true;

    void configure(boolean enabled, int radius, int perHostRuns, int globalEntities) {
        this.enabled = enabled;
        this.radius = Math.max(12, radius);
        this.perHostRuns = Math.max(4, perHostRuns);
        this.globalEntities = Math.max(64, globalEntities);
    }

    // ------------------------------------------------------------------------------------------------
    // runs
    // ------------------------------------------------------------------------------------------------

    private List<Run> runs(String hostKey, Map<Long, Belt> belts, int version) {
        Integer cached = runsVersion.get(hostKey);
        List<Run> known = runs.get(hostKey);
        if (known != null && cached != null && cached == version) {
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
            out.add(build(tiles, belts));
        }
        runs.put(hostKey, out);
        runsVersion.put(hostKey, version);
        return out;
    }

    private static Run build(List<Belt> tiles, Map<Long, Belt> belts) {
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
        // left/right = the -/+ side across the run's axis (north/south for x runs, west/east for z runs)
        boolean alongX = dir == BlockFace.EAST || dir == BlockFace.WEST;
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
            }
        }
        return new Run(minX, first.y(), minZ, dir, n, keys, leftOpen, rightOpen, stubs);
    }

    // ------------------------------------------------------------------------------------------------
    // render (every second, per active island)
    // ------------------------------------------------------------------------------------------------

    void render(String hostKey, World world, Map<Long, Belt> belts, int version, Collection<Location> viewers,
                Set<Long> flowing, long tick) {
        if (!enabled || world == null || belts.isEmpty() || viewers.isEmpty()) {
            clearHost(hostKey);
            return;
        }
        List<Run> all = runs(hostKey, belts, version);
        double r2 = (double) radius * radius;
        List<Run> wanted = new ArrayList<>();
        for (Run run : all) {
            double best = Double.MAX_VALUE;
            for (Location viewer : viewers) {
                best = Math.min(best, run.distanceSq(viewer.getX(), viewer.getZ()));
            }
            if (best <= r2) {
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
                if (entities >= globalEntities
                        || !world.isChunkLoaded(run.minX() >> 4, run.minZ() >> 4)) {
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
                for (Chevron chevron : view.chevrons) {
                    // back to evenly spaced; a moving belt glides on from there, a stopped one rests there
                    chevron.offset = chevron.home;
                    chevron.resetNext = false;
                    chevron.nextAt = tick + 1;
                    if (!moving && chevron.entity != null && chevron.entity.isValid()) {
                        place(chevron.entity, run, chevron.home, 0);
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
    // chevrons (every tick; only chevrons whose trip ended do anything)
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
                int length = view.run.length();
                for (Chevron chevron : view.chevrons) {
                    if (tick < chevron.nextAt || chevron.entity == null || !chevron.entity.isValid()) {
                        continue;
                    }
                    if (chevron.resetNext) {
                        // snap back to the tail, then glide the whole run next tick
                        chevron.offset = 0f;
                        place(chevron.entity, view.run, 0f, 0);
                        chevron.resetNext = false;
                        chevron.nextAt = tick + 1;
                        continue;
                    }
                    int duration = Math.max(1, Math.round((length - chevron.offset) * TICKS_PER_TILE));
                    place(chevron.entity, view.run, length, duration);
                    chevron.resetNext = true;
                    chevron.nextAt = tick + duration;
                }
            }
        }
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
        float inset = (1f - BELT_W) / 2f;
        // belt
        view.parts.add(block(corner, BELT, box(alongX, 0f, len, inset, 1f - inset, BELT_Y, BELT_H)));
        // rails, broken where a side belt feeds in
        for (int side = -1; side <= 1; side += 2) {
            boolean[] open = side < 0 ? run.leftOpen() : run.rightOpen();
            float across = side < 0 ? 0.02f : 1f - 0.02f - RAIL_W;
            int start = -1;
            for (int i = 0; i <= run.length(); i++) {
                boolean solid = i < run.length() && !open[i];
                if (solid && start < 0) {
                    start = i;
                } else if (!solid && start >= 0) {
                    view.parts.add(block(corner, RAIL, box(alongX, start, i, across, across + RAIL_W, BELT_Y,
                            RAIL_H)));
                    start = -1;
                }
            }
        }
        // side stubs: a short belt tongue from the edge to the main belt
        for (int[] stub : run.stubs()) {
            float a = stub[0] + inset;
            float b = stub[0] + 1f - inset;
            float c0 = stub[1] < 0 ? 0f : 1f - inset;
            float c1 = stub[1] < 0 ? inset : 1f;
            view.parts.add(block(corner, BELT, box(alongX, a, b, c0, c1, BELT_Y, BELT_H)));
        }
        // rollers across both ends
        BlockData roller = alongX ? ROLLER_Z : ROLLER_X;
        for (float at : new float[]{0.06f, len - 0.06f}) {
            view.parts.add(block(corner, roller, rollerBox(alongX, at)));
        }
        // chevrons every other tile
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

    /** The chain model is a 3px bar through the block centre along its axis; lay it across the belt surface. */
    private static Transformation rollerBox(boolean alongX, float at) {
        float inset = (1f - BELT_W) / 2f;
        float y = TOP - 0.5f + 0.01f;
        Vector3f translation = alongX ? new Vector3f(at - 0.5f, y, inset) : new Vector3f(inset, y, at - 0.5f);
        Vector3f scale = alongX ? new Vector3f(1f, 1f, BELT_W) : new Vector3f(BELT_W, 1f, 1f);
        return new Transformation(translation, new Quaternionf(), scale, new Quaternionf());
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

    private TextDisplay chevron(Location corner, Run run, float offset) {
        TextDisplay display = corner.getWorld().spawn(corner, TextDisplay.class, d -> {
            d.text(Component.text("›", CHEVRON).decorate(TextDecoration.BOLD));
            d.setBillboard(Display.Billboard.FIXED);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setTextOpacity((byte) 210);
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
        // the glyph sits above its origin: shift so its middle lands on the belt centre line
        Vector3f glyph = rotation.transform(new Vector3f(0f, 0.11f * CHEVRON_SCALE, 0f));
        boolean alongX = run.alongX();
        boolean forward = run.dir() == BlockFace.EAST || run.dir() == BlockFace.SOUTH;
        float along = forward ? offset : run.length() - offset;
        float px = alongX ? along : 0.5f;
        float pz = alongX ? 0.5f : along;
        Vector3f translation = new Vector3f(px, TOP + 0.004f, pz).sub(glyph);
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
