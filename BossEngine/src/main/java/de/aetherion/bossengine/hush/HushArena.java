package de.aetherion.bossengine.hush;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bell;
import org.bukkit.block.data.type.Candle;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The belfry: a round stone floor hanging in the void, seven lamps on an inner ring and seven bells on
 * the outer one, a low rim, and a small unlit candle in the middle.
 *
 * <p>The layout is pure code and always the same, so a slot is cleared by walking the same cell list
 * again; nothing outside those cells is ever touched. The lamps are real {@code LANTERN} blocks (their
 * light is the arena's light); a snuffed lamp is an unlit black candle on the same post.
 */
final class HushArena {

    static final int RADIUS = 20;
    static final int LAMPS = 7;
    static final int BELLS = 7;
    static final double LAMP_RING = 10.0;
    static final double BELL_RING = 17.0;
    /** Blocks (horizontal) a lit lamp keeps the dark away. */
    static final double LIGHT_RADIUS = 4.6;
    /** Chunk tickets and "is this location in my slot" use this half-width. */
    static final int BOX = 40;

    record Cell(int dx, int dy, int dz, BlockData data) {
    }

    private static List<Cell> layout;
    private static final int[][] LAMP_AT = new int[LAMPS][2];
    private static final int[][] BELL_AT = new int[BELLS][2];

    static {
        for (int i = 0; i < LAMPS; i++) {
            double a = (i + 0.5) * Math.PI * 2 / LAMPS;
            LAMP_AT[i][0] = (int) Math.round(Math.cos(a) * LAMP_RING);
            LAMP_AT[i][1] = (int) Math.round(Math.sin(a) * LAMP_RING);
        }
        for (int i = 0; i < BELLS; i++) {
            double a = i * Math.PI * 2 / BELLS;
            BELL_AT[i][0] = (int) Math.round(Math.cos(a) * BELL_RING);
            BELL_AT[i][1] = (int) Math.round(Math.sin(a) * BELL_RING);
        }
    }

    private final Plugin plugin;
    private final World world;
    private final int cx;
    private final int cy;
    private final int cz;
    private final boolean[] lit = new boolean[LAMPS];
    private boolean nightLight;

    HushArena(Plugin plugin, World world, int cx, int cy, int cz) {
        this.plugin = plugin;
        this.world = world;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
    }

    /* ================================================================== layout */

    static synchronized List<Cell> cells() {
        if (layout != null) {
            return layout;
        }
        Map<Long, Cell> map = new LinkedHashMap<>();
        BlockData tiles = Material.DEEPSLATE_TILES.createBlockData();
        BlockData cracked = Material.CRACKED_DEEPSLATE_TILES.createBlockData();
        BlockData polished = Material.POLISHED_DEEPSLATE.createBlockData();
        BlockData bricks = Material.DEEPSLATE_BRICKS.createBlockData();
        BlockData rim = Material.POLISHED_BLACKSTONE_BRICKS.createBlockData();
        BlockData blackstone = Material.POLISHED_BLACKSTONE.createBlockData();
        BlockData sculk = Material.SCULK.createBlockData();
        BlockData pale = Material.CALCITE.createBlockData();
        Random random = new Random(0x4855_5348L);

        // The hanging underside: an upside-down cone of brick, so the floor does not read as a pancake.
        int[] under = {18, 15, 11, 6, 2};
        for (int layer = 0; layer < under.length; layer++) {
            int r = under[layer];
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r + r) {
                        put(map, x, -2 - layer, z, layer == under.length - 1 ? blackstone : bricks);
                    }
                }
            }
        }
        // The floor itself (its top face is y = 0).
        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > RADIUS + 0.5) {
                    continue;
                }
                BlockData data = tiles;
                if (r <= 1.5) {
                    data = pale;
                } else if (r > RADIUS - 0.5) {
                    data = rim;
                } else if (Math.abs(r - LAMP_RING) <= 0.6) {
                    data = polished;
                } else if (Math.abs(r - BELL_RING) <= 0.6) {
                    data = blackstone;
                } else if (random.nextInt(100) < 9) {
                    data = sculk;
                } else if (random.nextInt(100) < 14) {
                    data = cracked;
                }
                put(map, x, -1, z, data);
                if (r > RADIUS - 0.5) {
                    // A one-block rim: nobody slides off by accident, a hard shove still can.
                    put(map, x, 0, z, rim);
                }
            }
        }
        // Lamp posts: two wall posts and the lamp on top.
        BlockData post = Material.DEEPSLATE_BRICK_WALL.createBlockData();
        for (int i = 0; i < LAMPS; i++) {
            put(map, LAMP_AT[i][0], 0, LAMP_AT[i][1], post);
            put(map, LAMP_AT[i][0], 1, LAMP_AT[i][1], post);
            put(map, LAMP_AT[i][0], 2, LAMP_AT[i][1], Material.LANTERN.createBlockData());
        }
        // Bells: a plinth and a standing bell that faces the middle.
        BlockData plinth = Material.CHISELED_DEEPSLATE.createBlockData();
        for (int i = 0; i < BELLS; i++) {
            put(map, BELL_AT[i][0], 0, BELL_AT[i][1], plinth);
            Bell bell = (Bell) Material.BELL.createBlockData();
            bell.setAttachment(Bell.Attachment.FLOOR);
            bell.setFacing(Math.abs(BELL_AT[i][0]) >= Math.abs(BELL_AT[i][1])
                    ? (BELL_AT[i][0] > 0 ? BlockFace.WEST : BlockFace.EAST)
                    : (BELL_AT[i][1] > 0 ? BlockFace.NORTH : BlockFace.SOUTH));
            put(map, BELL_AT[i][0], 1, BELL_AT[i][1], bell);
        }
        // The night light: one small white candle in the middle. It burns only after the fifth toll.
        put(map, 0, 0, 0, candle(Material.WHITE_CANDLE, false));
        layout = List.copyOf(map.values());
        return layout;
    }

    private static void put(Map<Long, Cell> map, int x, int y, int z, BlockData data) {
        long key = ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
        map.put(key, new Cell(x, y, z, data));
    }

    private static BlockData candle(Material type, boolean burning) {
        Candle c = (Candle) type.createBlockData();
        c.setCandles(1);
        c.setLit(burning);
        return c;
    }

    /* ================================================================== block work */

    void build() {
        hold(plugin, world, cx, cz);
        for (Cell c : cells()) {
            world.getBlockAt(cx + c.dx(), cy + c.dy(), cz + c.dz()).setBlockData(c.data(), false);
        }
        java.util.Arrays.fill(lit, true);
        nightLight = false;
    }

    /** Sets every cell of the layout at this centre back to air and lets the chunks go. */
    static void clear(Plugin plugin, World world, int cx, int cy, int cz) {
        hold(plugin, world, cx, cz);
        for (Cell c : cells()) {
            Block b = world.getBlockAt(cx + c.dx(), cy + c.dy(), cz + c.dz());
            if (b.getType() != Material.AIR) {
                b.setType(Material.AIR, false);
            }
        }
        release(plugin, world, cx, cz);
    }

    private static void hold(Plugin plugin, World world, int cx, int cz) {
        for (int x = (cx - BOX) >> 4; x <= (cx + BOX) >> 4; x++) {
            for (int z = (cz - BOX) >> 4; z <= (cz + BOX) >> 4; z++) {
                world.getChunkAt(x, z).addPluginChunkTicket(plugin);
            }
        }
    }

    private static void release(Plugin plugin, World world, int cx, int cz) {
        for (int x = (cx - BOX) >> 4; x <= (cx + BOX) >> 4; x++) {
            for (int z = (cz - BOX) >> 4; z <= (cz + BOX) >> 4; z++) {
                world.removePluginChunkTicket(x, z, plugin);
            }
        }
    }

    /* ================================================================== coordinates */

    World world() {
        return world;
    }

    /** Arena coordinates (0,0,0 = middle of the floor's top face) to a world location. */
    Location at(double x, double y, double z) {
        return new Location(world, cx + 0.5 + x, cy + y, cz + 0.5 + z);
    }

    double localX(Location l) {
        return l.getX() - (cx + 0.5);
    }

    double localY(Location l) {
        return l.getY() - cy;
    }

    double localZ(Location l) {
        return l.getZ() - (cz + 0.5);
    }

    boolean contains(Location l) {
        return l != null && l.getWorld() == world && Math.abs(localX(l)) <= BOX && Math.abs(localZ(l)) <= BOX;
    }

    /** Pulls an arena point back onto the floor, a little inside the rim. */
    static double[] clamp(double x, double z, double maxR) {
        double r = Math.sqrt(x * x + z * z);
        if (r <= maxR || r < 1e-6) {
            return new double[] {x, z};
        }
        return new double[] {x / r * maxR, z / r * maxR};
    }

    /* ================================================================== lamps */

    static double lampX(int i) {
        return LAMP_AT[i][0];
    }

    static double lampZ(int i) {
        return LAMP_AT[i][1];
    }

    boolean lit(int i) {
        return lit[i];
    }

    int litCount() {
        int n = 0;
        for (boolean b : lit) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    /** @return true if the lamp changed */
    boolean setLamp(int i, boolean on) {
        if (lit[i] == on) {
            return false;
        }
        lit[i] = on;
        Block b = world.getBlockAt(cx + LAMP_AT[i][0], cy + 2, cz + LAMP_AT[i][1]);
        b.setBlockData(on ? Material.LANTERN.createBlockData() : candle(Material.BLACK_CANDLE, false), false);
        return true;
    }

    /** The flame of lamp {@code i} (for particles and sounds). */
    Location lampFlame(int i) {
        return at(LAMP_AT[i][0], 2.45, LAMP_AT[i][1]);
    }

    /** Is this arena point inside the pool of a burning lamp? */
    boolean inLight(double x, double z) {
        for (int i = 0; i < LAMPS; i++) {
            if (!lit[i]) {
                continue;
            }
            double dx = x - LAMP_AT[i][0];
            double dz = z - LAMP_AT[i][1];
            if (dx * dx + dz * dz <= LIGHT_RADIUS * LIGHT_RADIUS) {
                return true;
            }
        }
        return false;
    }

    boolean inLight(Location l) {
        return l != null && l.getWorld() == world && inLight(localX(l), localZ(l));
    }

    /** Nearest lamp to an arena point with the wanted state, or -1. */
    int nearestLamp(double x, double z, boolean wantLit) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < LAMPS; i++) {
            if (lit[i] != wantLit) {
                continue;
            }
            double dx = x - LAMP_AT[i][0];
            double dz = z - LAMP_AT[i][1];
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** The lamp whose post (any of its three blocks) this is, or -1. */
    int lampAt(Block b) {
        if (b == null || b.getWorld() != world || b.getY() < cy || b.getY() > cy + 2) {
            return -1;
        }
        for (int i = 0; i < LAMPS; i++) {
            if (b.getX() == cx + LAMP_AT[i][0] && b.getZ() == cz + LAMP_AT[i][1]) {
                return i;
            }
        }
        return -1;
    }

    /* ================================================================== bells */

    static double bellX(int i) {
        return BELL_AT[i][0];
    }

    static double bellZ(int i) {
        return BELL_AT[i][1];
    }

    /** The mouth of bell {@code i}. */
    Location bellMouth(int i) {
        return at(BELL_AT[i][0], 1.5, BELL_AT[i][1]);
    }

    /** The bell (or its plinth) this block belongs to, or -1. */
    int bellAt(Block b) {
        if (b == null || b.getWorld() != world || b.getY() < cy || b.getY() > cy + 1) {
            return -1;
        }
        for (int i = 0; i < BELLS; i++) {
            if (b.getX() == cx + BELL_AT[i][0] && b.getZ() == cz + BELL_AT[i][1]) {
                return i;
            }
        }
        return -1;
    }

    /* ================================================================== night light */

    void nightLight(boolean on) {
        if (nightLight == on) {
            return;
        }
        nightLight = on;
        world.getBlockAt(cx, cy, cz).setBlockData(candle(Material.WHITE_CANDLE, on), false);
    }

    /** Arena points on a ring, for spawns and telegraphs. */
    static List<double[]> ring(double radius, int count, double phase) {
        List<double[]> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double a = phase + i * Math.PI * 2 / count;
            out.add(new double[] {Math.cos(a) * radius, Math.sin(a) * radius});
        }
        return out;
    }
}
