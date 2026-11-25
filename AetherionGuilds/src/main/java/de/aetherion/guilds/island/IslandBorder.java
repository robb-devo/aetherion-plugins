package de.aetherion.guilds.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The island's border, always there: a slim rail of pale light (a glass sleeve round a shimmering core) along the
 * edge of the land you may build on, at knee height, and a small crystal post at every corner. It follows the
 * land exactly: the tier square and every bought parcel, so buying land moves it outward as the ground rises.
 *
 * <p>Display entities, never saved. A rim stands while someone is on the island, grows in when it is (re)built,
 * respawns its pieces when their chunks come back, and is taken down a minute after the last person left.
 */
public final class IslandBorder {

    private static final String TAG = "aeg_border";
    /** Top of the island's ground (blocks at {@link HostService#SURFACE_Y}, players stand on y + 1). */
    private static final double GROUND = HostService.SURFACE_Y + 1.0;
    private static final double RAIL_Y = GROUND + 0.45;
    private static final float SLEEVE = 0.14f;
    private static final float CORE = 0.045f;
    private static final float POST = 0.18f;
    private static final float POST_HEIGHT = 0.95f;
    /** Longest single rail piece (shorter pieces cull and track like the rest of the island). */
    private static final int PIECE = 16;
    private static final int GROW_TICKS = 18;
    private static final long IDLE_MS = 60_000L;

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final Map<String, Rim> rims = new HashMap<>();
    private boolean enabled = true;

    /** One display of the rim: what it is and where it goes (spawned when its chunk is loaded). */
    private record Part(Material material, double x, double y, double z, Vector3f translation, Vector3f scale) {
    }

    private static final class Rim {
        World world;
        long signature;
        long lastSeen;
        List<Part> plan = List.of();
        final Map<Integer, BlockDisplay> shown = new HashMap<>();
    }

    public IslandBorder(JavaPlugin plugin, HostService hosts) {
        this.plugin = plugin;
        this.hosts = hosts;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    /** Every two seconds: a rim for each island someone is on, in step with its land. */
    public void tick() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<String> seen = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            IslandHost host = hosts.at(player.getLocation());
            if (host == null || !seen.add(host.key()) || !hosts.exists(host)) {
                continue;
            }
            World world = hosts.world(host);
            if (world == null || world != player.getWorld()) {
                continue;
            }
            long signature = signature(host);
            Rim rim = rims.get(host.key());
            if (rim == null || rim.signature != signature || rim.world != world) {
                if (rim != null) {
                    take(rim); // the land changed: the old line goes, the new one grows in
                }
                rim = new Rim();
                rim.world = world;
                rim.signature = signature;
                rim.plan = plan(host);
                rims.put(host.key(), rim);
            }
            rim.lastSeen = now;
            show(rim);
        }
        for (Iterator<Map.Entry<String, Rim>> it = rims.entrySet().iterator(); it.hasNext(); ) {
            Rim rim = it.next().getValue();
            if (now - rim.lastSeen > IDLE_MS) {
                take(rim);
                it.remove();
            }
        }
    }

    /** The land as one number: radius and parcels (it changes when either does). */
    private long signature(IslandHost host) {
        long signature = 31L * hosts.legacyRadius(host) + hosts.originX(host) * 7L + hosts.originZ(host);
        for (Long parcel : hosts.parcels(host)) {
            signature += parcel * 0x9E3779B97F4A7C15L;
        }
        return signature;
    }

    /** Spawns the pieces that aren't standing (new rim, or their chunk unloaded and came back). */
    private void show(Rim rim) {
        for (int i = 0; i < rim.plan.size(); i++) {
            BlockDisplay have = rim.shown.get(i);
            if (have != null && have.isValid()) {
                continue;
            }
            Part part = rim.plan.get(i);
            if (!rim.world.isChunkLoaded((int) Math.floor(part.x()) >> 4, (int) Math.floor(part.z()) >> 4)) {
                rim.shown.remove(i);
                continue;
            }
            rim.shown.put(i, spawn(rim.world, part));
        }
    }

    private BlockDisplay spawn(World world, Part part) {
        BlockDisplay display = world.spawn(new Location(world, part.x(), part.y(), part.z()), BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setBlock(part.material().createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.5f);
            d.setShadowRadius(0f);
            // born as a sliver at its anchor, then it grows to full length (the line draws itself)
            d.setTransformation(new Transformation(new Vector3f(part.translation()), new Quaternionf(),
                    new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(GROW_TICKS);
                display.setTransformation(new Transformation(new Vector3f(part.translation()), new Quaternionf(),
                        new Vector3f(part.scale()), new Quaternionf()));
            }
        }, 2L);
        return display;
    }

    private static void take(Rim rim) {
        for (BlockDisplay display : rim.shown.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        rim.shown.clear();
    }

    /** Shutdown / switched off: every rim comes down. */
    public void clear() {
        for (Rim rim : rims.values()) {
            take(rim);
        }
        rims.clear();
    }

    // ------------------------------------------------------------------------------------------------
    // the line
    // ------------------------------------------------------------------------------------------------

    /** Rails along every straight run of the edge, a crystal post at every corner. */
    private List<Part> plan(IslandHost host) {
        int ox = hosts.originX(host);
        int oz = hosts.originZ(host);
        int radius = hosts.legacyRadius(host);
        Set<Long> parcels = new HashSet<>(hosts.parcels(host));
        int reach = Math.max(LandService.RING * LandService.SIZE + 8, radius) + 1;
        List<int[]> runs = new ArrayList<>();
        // edges between rows (lines of constant z): walk x, collect the stretches where inside meets outside
        for (int z = -reach; z <= reach + 1; z++) {
            int start = Integer.MIN_VALUE;
            for (int x = -reach; x <= reach + 1; x++) {
                boolean edge = x <= reach && in(radius, parcels, x, z - 1) != in(radius, parcels, x, z);
                if (edge && start == Integer.MIN_VALUE) {
                    start = x;
                } else if (!edge && start != Integer.MIN_VALUE) {
                    runs.add(new int[]{start, z, x, z});
                    start = Integer.MIN_VALUE;
                }
            }
        }
        // edges between columns (lines of constant x)
        for (int x = -reach; x <= reach + 1; x++) {
            int start = Integer.MIN_VALUE;
            for (int z = -reach; z <= reach + 1; z++) {
                boolean edge = z <= reach && in(radius, parcels, x - 1, z) != in(radius, parcels, x, z);
                if (edge && start == Integer.MIN_VALUE) {
                    start = z;
                } else if (!edge && start != Integer.MIN_VALUE) {
                    runs.add(new int[]{x, start, x, z});
                    start = Integer.MIN_VALUE;
                }
            }
        }
        List<Part> parts = new ArrayList<>();
        Set<Long> corners = new HashSet<>();
        for (int[] run : runs) {
            boolean alongX = run[1] == run[3];
            int from = alongX ? run[0] : run[1];
            int to = alongX ? run[2] : run[3];
            for (int a = from; a < to; a += PIECE) {
                int b = Math.min(to, a + PIECE);
                float length = b - a;
                double x = ox + (alongX ? a : run[0]);
                double z = oz + (alongX ? run[1] : a);
                parts.add(rail(Material.LIGHT_BLUE_STAINED_GLASS, x, z, alongX, length, SLEEVE));
                parts.add(rail(Material.SEA_LANTERN, x, z, alongX, length, CORE));
            }
            corners.add(corner(run[0], run[1]));
            corners.add(corner(run[2], run[3]));
        }
        for (long corner : corners) {
            double x = ox + (int) (corner >> 32);
            double z = oz + (int) corner;
            parts.add(new Part(Material.LIGHT_BLUE_STAINED_GLASS, x, GROUND, z,
                    new Vector3f(-POST / 2f, 0f, -POST / 2f), new Vector3f(POST, POST_HEIGHT, POST)));
            parts.add(new Part(Material.SEA_LANTERN, x, GROUND, z,
                    new Vector3f(-CORE, 0f, -CORE), new Vector3f(CORE * 2f, POST_HEIGHT - 0.04f, CORE * 2f)));
            parts.add(new Part(Material.AMETHYST_CLUSTER, x, GROUND + POST_HEIGHT - 0.06, z,
                    new Vector3f(-0.22f, 0f, -0.22f), new Vector3f(0.44f, 0.44f, 0.44f)));
        }
        return parts;
    }

    private static Part rail(Material material, double x, double z, boolean alongX, float length, float thick) {
        Vector3f translation = alongX ? new Vector3f(0f, -thick / 2f, -thick / 2f) : new Vector3f(-thick / 2f, -thick / 2f, 0f);
        Vector3f scale = alongX ? new Vector3f(length, thick, thick) : new Vector3f(thick, thick, length);
        return new Part(material, x, RAIL_Y, z, translation, scale);
    }

    private static long corner(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static boolean in(int radius, Set<Long> parcels, int x, int z) {
        return LandService.inZone(0, 0, radius, parcels, x, z);
    }
}
