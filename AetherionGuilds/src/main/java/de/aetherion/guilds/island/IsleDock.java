package de.aetherion.guilds.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * The Isle Dock as a place: a small floating isle under the dock spawn instead of the plain quartz square Core
 * lays. Grass and moss on top, a stone path from the arrival stone to the three gates, an inlay in each gate's
 * own colours, lantern posts on the rim, and underneath a rock cone that thins out with roots hanging from it.
 *
 * <p>Real blocks, built once per spot ({@code isle-dock.yml} remembers it). It only ever fills air, except
 * Core's old landing (its quartz floor and corner posts), which it takes up first. A spot someone built on by
 * hand is left alone. {@code starter-gates.build-dock: false} switches it off.
 */
final class IsleDock {

    private static final int VERSION = 1;
    /** The isle's middle lies this far ahead of the spawn (the spawn is near its back edge, the gates ahead). */
    static final double CENTER = 3.5;
    static final double RADIUS = 8.5;
    private static final int DEPTH = 6;

    private final JavaPlugin plugin;

    IsleDock(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds the isle under {@code spot} once. {@code gates} are the gates' (u, w) in the spawn's frame (u to the
     * right, w ahead), {@code inlays} the floor under each gate (the column you walk through, around it) and
     * {@code feet} the block data that stands on either side of each gate.
     */
    void ensure(Location spot, int[] forward, int[] right, double[][] gates, Material[][] inlays, String[] feet) {
        if (!plugin.getConfig().getBoolean("starter-gates.build-dock", true) || spot == null || spot.getWorld() == null) {
            return;
        }
        World world = spot.getWorld();
        int sx = spot.getBlockX();
        int fy = spot.getBlockY() - 1;
        int sz = spot.getBlockZ();
        File file = new File(plugin.getDataFolder(), "isle-dock.yml");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file);
        String key = world.getName() + ":" + sx + "," + fy + "," + sz + ":" + forward[0] + "," + forward[1];
        if (saved.getInt("version", 0) >= VERSION && key.equals(saved.getString("spot"))) {
            return;
        }
        Material under = world.getBlockAt(sx, fy, sz).getType();
        if (under != Material.AIR && under != Material.SMOOTH_QUARTZ && under != Material.CHISELED_STONE_BRICKS) {
            plugin.getLogger().info("[Isle Dock] Something else stands under the dock spawn (" + under
                    + "), the floating isle is left out.");
            remember(file, saved, key);
            return;
        }
        takeUpCoreLanding(world, sx, fy, sz);
        Frame frame = new Frame(world, sx, fy, sz, forward, right);
        int reach = (int) Math.ceil(RADIUS + 1);
        int c0 = (int) Math.floor(CENTER - reach);
        int c1 = (int) Math.ceil(CENTER + reach);
        for (int u = -reach; u <= reach; u++) {
            for (int w = c0; w <= c1; w++) {
                double d = Math.hypot(u, w - CENTER);
                double edge = RADIUS + (noise(u, w, 1) - 0.5) * 1.2;
                if (d > edge) {
                    continue;
                }
                Material top = top(u, w, d, edge, gates, inlays);
                frame.put(u, 0, w, top);
                decorate(frame, u, w, d, edge, top, gates);
                underside(frame, u, w, d);
            }
        }
        // lantern posts on the rim, two behind the spawn and two at the sides, clear of the gates
        for (int[] post : new int[][]{{-5, -2}, {5, -2}, {-7, 5}, {7, 5}}) {
            frame.put(post[0], 1, post[1], Material.SPRUCE_FENCE);
            frame.put(post[0], 2, post[1], Material.SPRUCE_FENCE);
            frame.put(post[0], 3, post[1], Material.LANTERN);
        }
        for (int i = 0; i < gates.length && i < feet.length; i++) {
            if (feet[i] == null) {
                continue;
            }
            try {
                BlockData foot = Bukkit.createBlockData(feet[i]);
                frame.put((int) gates[i][0] - 2, 1, (int) gates[i][1], foot);
                frame.put((int) gates[i][0] + 2, 1, (int) gates[i][1], foot);
            } catch (IllegalArgumentException ignored) {
                // a block this server doesn't know: the gate goes without
            }
        }
        frame.put(-3, 1, -3, Material.FLOWERING_AZALEA);
        frame.put(4, 1, -3, Material.AZALEA);
        remember(file, saved, key);
        plugin.getLogger().info("[Isle Dock] Floating isle built under the dock spawn.");
    }

    private void remember(File file, YamlConfiguration saved, String key) {
        saved.set("version", VERSION);
        saved.set("spot", key);
        try {
            saved.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("[Isle Dock] Could not save isle-dock.yml: " + exception.getMessage());
        }
    }

    /** Core's old landing: a 9 by 9 quartz floor with a deepslate rim and a fence + lantern at each corner. */
    private static void takeUpCoreLanding(World world, int cx, int y, int cz) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                Block block = world.getBlockAt(cx + dx, y, cz + dz);
                if (block.getType() == Material.SMOOTH_QUARTZ || block.getType() == Material.POLISHED_DEEPSLATE) {
                    block.setType(Material.AIR, false);
                }
            }
        }
        for (int[] corner : new int[][]{{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
            Block post = world.getBlockAt(cx + corner[0], y + 1, cz + corner[1]);
            Block lamp = post.getRelative(0, 1, 0);
            if (lamp.getType() == Material.LANTERN) {
                lamp.setType(Material.AIR, false);
            }
            if (post.getType() == Material.DARK_OAK_FENCE) {
                post.setType(Material.AIR, false);
            }
        }
    }

    /** What the isle's top is made of at (u, w). */
    private static Material top(int u, int w, double d, double edge, double[][] gates, Material[][] inlays) {
        if (Math.abs(u) <= 1 && Math.abs(w) <= 1) {
            return u == 0 && w == 0 ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_ANDESITE;
        }
        for (int i = 0; i < gates.length; i++) {
            double gu = gates[i][0];
            double gw = gates[i][1];
            if (Math.abs(u - gu) <= 1.5 && Math.abs(w - gw) <= 1.5) {
                boolean core = Math.abs(u - gu) <= 0.5;
                return inlays[i][core ? 0 : 1];
            }
        }
        if (path(u, w, gates)) {
            double n = noise(u, w, 2);
            return n < 0.12 ? Material.CRACKED_STONE_BRICKS : n < 0.45 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
        }
        if (d > edge - 1.3) {
            return noise(u, w, 3) < 0.5 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK;
        }
        return Material.GRASS_BLOCK;
    }

    /** The path: from the arrival stone straight ahead, then across to every gate. */
    private static boolean path(int u, int w, double[][] gates) {
        double front = gates.length == 0 ? 6 : gates[0][1];
        for (double[] gate : gates) {
            front = Math.min(front, gate[1]);
        }
        if (Math.abs(u) <= 1 && w >= -1 && w <= front - 1) {
            return true;
        }
        double span = 0;
        for (double[] gate : gates) {
            span = Math.max(span, Math.abs(gate[0]));
        }
        return w >= front - 2 && w <= front - 1 && Math.abs(u) <= span + 1;
    }

    private static void decorate(Frame frame, int u, int w, double d, double edge, Material top, double[][] gates) {
        if (top != Material.GRASS_BLOCK && top != Material.MOSS_BLOCK) {
            return;
        }
        if (Math.abs(u) <= 2 && w >= -2 && w <= 2) {
            return; // the arrival stays clear
        }
        for (double[] gate : gates) {
            if (Math.abs(u - gate[0]) <= 2.5 && Math.abs(w - gate[1]) <= 2.5) {
                return; // nothing to trip over in front of a gate
            }
        }
        double n = noise(u, w, 4);
        Material plant = null;
        if (top == Material.MOSS_BLOCK) {
            plant = n < 0.35 ? Material.MOSS_CARPET : null;
        } else if (n < 0.28) {
            plant = Material.SHORT_GRASS;
        } else if (n < 0.34) {
            plant = Material.FERN;
        } else if (n < 0.37) {
            plant = Material.CORNFLOWER;
        } else if (n < 0.40) {
            plant = Material.OXEYE_DAISY;
        } else if (n < 0.42) {
            plant = Material.ALLIUM;
        }
        if (plant != null) {
            frame.put(u, 1, w, plant);
        }
    }

    /** The rock under (u, w): deeper in the middle, dirt with roots first, then stone, a dripstone tip or two. */
    private static void underside(Frame frame, int u, int w, double d) {
        int depth = 0;
        for (int k = 1; k <= DEPTH; k++) {
            double r = RADIUS * Math.pow(1.0 - k / (DEPTH + 1.0), 0.75) + (noise(u, w, 10 + k) - 0.5) * 1.4;
            if (d > r) {
                break;
            }
            depth = k;
        }
        for (int k = 1; k <= depth; k++) {
            double n = noise(u, w, 20 + k);
            Material rock;
            if (k == 1) {
                rock = n < 0.25 ? Material.ROOTED_DIRT : n < 0.4 ? Material.COARSE_DIRT : Material.DIRT;
            } else if (k == 2) {
                rock = n < 0.35 ? Material.DIRT : n < 0.55 ? Material.ROOTED_DIRT : Material.STONE;
            } else {
                rock = n < 0.3 ? Material.ANDESITE : n < 0.5 ? Material.TUFF : n < 0.62 ? Material.COBBLESTONE
                        : n < 0.7 ? Material.MOSSY_COBBLESTONE : Material.STONE;
            }
            frame.put(u, -k, w, rock);
        }
        int below = -depth - 1;
        double n = noise(u, w, 40);
        if (depth <= 2 && n < 0.4) {
            frame.put(u, below, w, Material.HANGING_ROOTS);
        } else if (depth >= DEPTH - 1 && n < 0.3) {
            frame.put(u, below, w, Bukkit.createBlockData("minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]"));
        }
    }

    /** 0..1, the same every time for the same cell. */
    private static double noise(int u, int w, int salt) {
        long h = u * 0x9E3779B97F4A7C15L ^ w * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** The spawn's frame: u to the right, w ahead, y up from the floor (0 = the floor the spawn stands on). */
    private static final class Frame {
        final World world;
        final int sx;
        final int fy;
        final int sz;
        final int[] forward;
        final int[] right;

        Frame(World world, int sx, int fy, int sz, int[] forward, int[] right) {
            this.world = world;
            this.sx = sx;
            this.fy = fy;
            this.sz = sz;
            this.forward = forward;
            this.right = right;
        }

        void put(int u, int y, int w, Material material) {
            put(u, y, w, material.createBlockData());
        }

        void put(int u, int y, int w, BlockData data) {
            Block block = world.getBlockAt(sx + u * right[0] + w * forward[0], fy + y, sz + u * right[1] + w * forward[1]);
            if (block.getType().isAir()) {
                block.setBlockData(data, false);
            }
        }
    }
}
