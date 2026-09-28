package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.Snowable;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Wall;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The whole World Eater site as a pure function of position: what block belongs at (x, y, z).
 * The layout is the single source of truth. Building, repairing after a crash, restoring eaten
 * chunks and resetting the gate are all "make the world match the layout" inside the site boxes,
 * so nothing ever needs a persistent restore journal and nothing outside the boxes is touched.
 *
 * <pre>
 *   z -128  THE FRAYED ROAD   the landing, then fragments of eaten worlds stitched with void:
 *           |                 a stone road, a village cut in half, desert, snow, a last bridge
 *   z  -43  THE THRESHOLD     round plaza; the Unbroken kneels at its center as a statue
 *   z  -12  THE BULWARK       a wall of bedrock and deepslate; the bedrock gate in its middle
 *   z   -9  bridge
 *   z 0..47 THE LAST SEED     a 3x3-chunk slab of the default world: grass, oaks, a pond,
 *                             flowers, strata and ores down to bedrock, cut clean at chunk borders
 * </pre>
 * Every walkable deck has its top block at y = {@value #FLOOR}; players stand at y = 100.
 */
public final class SiteLayout {

    public static final int FLOOR = 99;

    public static final int ISLAND_MIN = 0;
    public static final int ISLAND_MAX = 47;
    public static final int ISLAND_BOTTOM = 70;
    public static final double CENTER_X = 24.0;
    public static final double CENTER_Z = 24.0;

    public static final double PLAZA_X = 24.0;
    public static final double PLAZA_Z = -28.0;
    public static final double PLAZA_R = 15.5;

    public static final int GATE_X0 = 18;
    public static final int GATE_X1 = 29;
    public static final int GATE_Y0 = 100;
    public static final int GATE_Y1 = 113;
    public static final int DOOR_Z = -11;
    public static final int WALL_Z0 = -12;
    public static final int WALL_Z1 = -10;

    /** Iteration boxes (inclusive). Site edits never leave them. */
    public record Box(int x0, int y0, int z0, int x1, int y1, int z1) {
        public boolean contains(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }

        public long volume() {
            return (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        }
    }

    public static final Box ISLAND = new Box(0, 66, 0, 47, 116, 47);
    public static final Box BRIDGE = new Box(20, 94, -9, 27, 101, -1);
    public static final Box WALL = new Box(-6, 84, -13, 53, 128, -10);
    public static final Box PLAZA = new Box(6, 76, -46, 42, 112, -14);
    public static final Box ROAD = new Box(8, 84, -130, 40, 112, -44);
    /** The sky beyond the island where Nihil tears through: end gateway blocks live here, only while it does. */
    public static final Box RIFT = new Box(14, 120, 56, 34, 160, 60);
    public static final Box[] SITE = {ISLAND, BRIDGE, WALL, PLAZA, ROAD, RIFT};

    /** Chunk the approach-road glimpse bites a corner out of (the desert fragment). */
    public static final Box ROAD_BITE = new Box(25, 93, -81, 28, 101, -78);

    private static SiteLayout instance;

    private final Map<Material, BlockData> cache = new EnumMap<>(Material.class);
    private final BlockData air;
    private final Map<Long, BlockData> built = new HashMap<>();
    private final Map<Long, BlockData> islandFeatures = new HashMap<>();
    private final Set<Long> statue = new HashSet<>();
    private final Set<Long> doors = new HashSet<>();
    private final Map<Long, String[]> signs = new LinkedHashMap<>();
    private final List<long[]> trees = new ArrayList<>();

    private SiteLayout() {
        air = Material.AIR.createBlockData();
        paintRoad();
        paintPlaza();
        paintWall();
        paintBridge();
        paintIsland();
    }

    public static synchronized SiteLayout get() {
        if (instance == null) {
            instance = new SiteLayout();
        }
        return instance;
    }

    /* ================================================================== anchors */

    public static Location arrival(World world) {
        return new Location(world, 24.5, FLOOR + 1.0, -119.5, 0f, 0f);
    }

    public static Location islandAnchor(World world) {
        return new Location(world, CENTER_X, FLOOR + 1.0, CENTER_Z);
    }

    public static Location plazaAnchor(World world) {
        return new Location(world, PLAZA_X, FLOOR + 1.0, PLAZA_Z);
    }

    /** Safe landing on the plaza's north lip, facing the gate. */
    public static Location plazaSafe(World world) {
        return new Location(world, 24.5, FLOOR + 1.0, -41.5, 0f, 0f);
    }

    /** Safe landing on the island (center chunk: the last to be eaten). */
    public static Location islandSafe(World world, int k) {
        double a = k * 2.39996;
        return new Location(world, CENTER_X + Math.sin(a) * 4.5, FLOOR + 1.0, CENTER_Z + Math.cos(a) * 4.5);
    }

    public static boolean inSite(int x, int y, int z) {
        for (Box b : SITE) {
            if (b.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public static boolean onIsland(double x, double z) {
        return x >= ISLAND_MIN && x < ISLAND_MAX + 1 && z >= ISLAND_MIN && z < ISLAND_MAX + 1;
    }

    public static boolean onPlaza(double x, double z) {
        double dx = x - PLAZA_X;
        double dz = z - PLAZA_Z;
        return dx * dx + dz * dz <= (PLAZA_R + 0.5) * (PLAZA_R + 0.5);
    }

    /** Island chunk index 0..2 for a block coordinate, or -1 off the island. */
    public static int islandChunk(int v) {
        return v < ISLAND_MIN || v > ISLAND_MAX ? -1 : v >> 4;
    }

    /* ================================================================== queries */

    /** The block that belongs at (x, y, z), or null for air. Outside the site: null. */
    public BlockData expected(int x, int y, int z) {
        long key = key(x, y, z);
        if (ISLAND.contains(x, y, z)) {
            BlockData f = islandFeatures.get(key);
            if (f != null) {
                return f.getMaterial() == Material.AIR ? null : f;
            }
            return islandBase(x, y, z);
        }
        return built.get(key);
    }

    public boolean isStatue(int x, int y, int z) {
        return statue.contains(key(x, y, z));
    }

    public boolean isDoor(int x, int y, int z) {
        return doors.contains(key(x, y, z));
    }

    public List<int[]> statueCells() {
        return cells(statue);
    }

    public List<int[]> doorCells() {
        return cells(doors);
    }

    public Map<Long, String[]> signs() {
        return signs;
    }

    public BlockData air() {
        return air;
    }

    private static List<int[]> cells(Set<Long> keys) {
        List<int[]> out = new ArrayList<>();
        for (long k : keys) {
            out.add(unkey(k));
        }
        return out;
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int[] unkey(long k) {
        int x = (int) (k >> 38);
        int z = (int) ((k << 26) >> 38);
        int y = (int) (k & 0xFFF);
        if (y >= 2048) {
            y -= 4096;
        }
        return new int[]{x, y, z};
    }

    /* ================================================================== palette */

    private BlockData data(Material m) {
        return cache.computeIfAbsent(m, Material::createBlockData);
    }

    private void put(int x, int y, int z, Material m) {
        built.put(key(x, y, z), data(m));
    }

    private void put(int x, int y, int z, BlockData d) {
        built.put(key(x, y, z), d);
    }

    private void feature(int x, int y, int z, Material m) {
        islandFeatures.put(key(x, y, z), data(m));
    }

    private void feature(int x, int y, int z, BlockData d) {
        islandFeatures.put(key(x, y, z), d);
    }

    private boolean hasFeature(int x, int y, int z) {
        return islandFeatures.containsKey(key(x, y, z));
    }

    private static float h01(int x, int y, int z, int salt) {
        return WeMath.hash01(x, y, z, salt);
    }

    private BlockData leaves(Material m) {
        BlockData d = m.createBlockData();
        if (d instanceof Leaves l) {
            l.setPersistent(true);
        }
        return d;
    }

    private BlockData lantern(Material m, boolean hanging) {
        BlockData d = m.createBlockData();
        if (d instanceof Lantern l) {
            l.setHanging(hanging);
        }
        return d;
    }

    private BlockData sign(BlockFace facing) {
        BlockData d = Material.OAK_SIGN.createBlockData();
        if (d instanceof Rotatable r) {
            r.setRotation(facing);
        }
        return d;
    }

    private BlockData railZ() {
        BlockData d = Material.DEEPSLATE_BRICK_WALL.createBlockData();
        if (d instanceof Wall w) {
            w.setHeight(BlockFace.NORTH, Wall.Height.LOW);
            w.setHeight(BlockFace.SOUTH, Wall.Height.LOW);
            w.setUp(false);
        }
        return d;
    }

    private BlockData railPost() {
        BlockData d = Material.DEEPSLATE_BRICK_WALL.createBlockData();
        if (d instanceof Wall w) {
            w.setHeight(BlockFace.NORTH, Wall.Height.LOW);
            w.setHeight(BlockFace.SOUTH, Wall.Height.LOW);
            w.setUp(true);
        }
        return d;
    }

    private void signAt(int x, int y, int z, BlockFace facing, String... lines) {
        put(x, y, z, sign(facing));
        signs.put(key(x, y, z), lines);
    }

    /* ================================================================== the road */

    private void paintRoad() {
        // The Landing: a round scrap of meadow where everyone arrives.
        for (int x = 16; x <= 32; x++) {
            for (int z = -129; z <= -113; z++) {
                double d = Math.hypot(x + 0.5 - 24.0, z + 0.5 + 121.0);
                if (d > 6.4) {
                    continue;
                }
                put(x, FLOOR, z, Material.GRASS_BLOCK);
                int depth = 3 + (int) Math.round((6.4 - d) * 1.1 + h01(x, 0, z, 3) * 2);
                underside(x, z, depth, Material.DIRT, Material.STONE, Material.COBBLESTONE);
                float f = h01(x, 1, z, 4);
                if (f < 0.10f && d < 5.5) {
                    put(x, FLOOR + 1, z, Material.SHORT_GRASS);
                } else if (f < 0.14f && d < 5.5) {
                    put(x, FLOOR + 1, z, flower(x, z));
                }
            }
        }
        post(20, -121, Material.LANTERN);
        post(28, -123, Material.LANTERN);
        signAt(24, FLOOR + 1, -117, BlockFace.NORTH,
                "The Last Seed", "", "Every world", "ends here.");
        built.remove(key(24, FLOOR + 1, -118));
        built.remove(key(24, FLOOR + 1, -119));
        built.remove(key(24, FLOOR + 1, -120));

        // A: a stone road that frays at the edges.
        for (int x = 22; x <= 26; x++) {
            for (int z = -114; z <= -104; z++) {
                boolean edge = x == 22 || x == 26;
                if (edge && h01(x, 2, z, 5) < 0.22f) {
                    continue;
                }
                float r = h01(x, 3, z, 6);
                put(x, FLOOR, z, r < 0.6f ? Material.STONE_BRICKS : r < 0.85f ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS);
                underside(x, z, edge ? 2 : 3 + (int) (h01(x, 4, z, 7) * 3), Material.STONE_BRICKS, Material.COBBLESTONE, Material.STONE);
                if (edge && Math.floorMod(z, 3) == 0) {
                    put(x, FLOOR + 1, z, Material.STONE_BRICK_WALL);
                }
            }
        }

        // B: half a village, cut clean along a chunk border.
        seam(17, 31, -103);
        for (int x = 17; x <= 31; x++) {
            for (int z = -102; z <= -89; z++) {
                boolean road = x >= 23 && x <= 25;
                put(x, FLOOR, z, road ? (h01(x, 5, z, 8) < 0.7f ? Material.GRAVEL : Material.DIRT_PATH) : Material.GRASS_BLOCK);
                underside(x, z, 3 + (int) (h01(x, 6, z, 9) * 4), Material.DIRT, Material.STONE, Material.ANDESITE);
                if (!road && x > 21 && h01(x, 7, z, 10) < 0.12f) {
                    put(x, FLOOR + 1, z, Material.SHORT_GRASS);
                }
            }
        }
        // The house: floor, walls, a door toward the road; its west side is gone.
        for (int x = 17; x <= 21; x++) {
            for (int z = -100; z <= -94; z++) {
                put(x, FLOOR, z, Material.OAK_PLANKS);
                built.remove(key(x, FLOOR + 1, z));
                boolean wallZ = z == -100 || z == -94;
                boolean wallX = x == 21;
                for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                    if (wallX && (z == -100 || z == -94)) {
                        put(x, y, z, Material.OAK_LOG);
                    } else if (wallZ || wallX) {
                        boolean door = wallX && z == -97 && y <= FLOOR + 2;
                        boolean window = wallZ && x == 19 && y == FLOOR + 2;
                        if (!door && !window) {
                            put(x, y, z, h01(x, y, z, 11) < 0.8f ? Material.COBBLESTONE : Material.MOSSY_COBBLESTONE);
                        }
                    }
                }
                if (h01(x, FLOOR + 4, z, 12) < 0.65f && x >= 18) {
                    put(x, FLOOR + 4, z, Material.OAK_PLANKS);
                }
            }
        }
        put(19, FLOOR + 1, -99, Material.CRAFTING_TABLE);
        put(18, FLOOR + 1, -95, Material.TORCH);
        signAt(22, FLOOR + 1, -96, BlockFace.EAST,
                "Day 1:", "The edges of", "our world", "are gone.");
        put(28, FLOOR + 1, -100, Material.HAY_BLOCK);
        put(29, FLOOR + 1, -100, Material.HAY_BLOCK);
        put(28, FLOOR + 2, -100, Material.HAY_BLOCK);
        post(27, -92, Material.LANTERN);

        // C: a square of desert.
        seam(20, 28, -88);
        for (int x = 20; x <= 28; x++) {
            for (int z = -87; z <= -78; z++) {
                put(x, FLOOR, z, Material.SAND);
                put(x, FLOOR - 1, z, Material.SAND);
                underside(x, z, 4 + (int) (h01(x, 8, z, 13) * 3), Material.SAND, Material.SANDSTONE, Material.SMOOTH_SANDSTONE);
            }
        }
        for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
            put(21, y, -84, Material.CACTUS);
        }
        put(27, FLOOR + 1, -81, Material.DEAD_BUSH);
        put(22, FLOOR + 1, -79, Material.DEAD_BUSH);
        signAt(26, FLOOR + 1, -86, BlockFace.WEST,
                "Day 9:", "The stars went", "out. All of", "them at once.");

        // D: a square of snow.
        seam(20, 29, -77);
        BlockData snowyGrass = Material.GRASS_BLOCK.createBlockData();
        if (snowyGrass instanceof Snowable s) {
            s.setSnowy(true);
        }
        BlockData layer = Material.SNOW.createBlockData();
        if (layer instanceof Snow sn) {
            sn.setLayers(1);
        }
        for (int x = 20; x <= 29; x++) {
            for (int z = -76; z <= -66; z++) {
                put(x, FLOOR, z, snowyGrass);
                underside(x, z, 3 + (int) (h01(x, 9, z, 14) * 4), Material.DIRT, Material.STONE, Material.PACKED_ICE);
                if (h01(x, 10, z, 15) < 0.8f) {
                    put(x, FLOOR + 1, z, layer);
                }
            }
        }
        spruce(27, -72);
        signAt(21, FLOOR + 1, -70, BlockFace.EAST,
                "Day 12:", "The border", "moves now.", "Every night.");

        // E: the last bridge, deepslate over nothing.
        seam(22, 26, -65);
        for (int x = 22; x <= 26; x++) {
            for (int z = -64; z <= -44; z++) {
                put(x, FLOOR, z, h01(x, 11, z, 16) < 0.8f ? Material.DEEPSLATE_TILES : Material.CRACKED_DEEPSLATE_TILES);
                put(x, FLOOR - 1, z, Material.DEEPSLATE_BRICKS);
                boolean edge = x == 22 || x == 26;
                if (!edge && Math.floorMod(z, 6) > 1) {
                    put(x, FLOOR - 2, z, Material.DEEPSLATE_BRICKS);
                }
                if (edge && h01(x, 12, z, 17) > 0.15f) {
                    boolean postHere = Math.floorMod(z, 6) == 0;
                    put(x, FLOOR + 1, z, postHere ? railPost() : railZ());
                    if (postHere) {
                        put(x, FLOOR + 2, z, lantern(Material.SOUL_LANTERN, false));
                    }
                }
            }
        }
        built.remove(key(25, FLOOR + 1, -47));
        signAt(24, FLOOR + 1, -47, BlockFace.NORTH,
                "Do not wake", "the floor.", "It is all that", "holds it out.");
    }

    /** A row of crying obsidian where two eaten worlds were stitched together. */
    private void seam(int x0, int x1, int z) {
        for (int x = x0; x <= x1; x++) {
            put(x, FLOOR, z, Material.CRYING_OBSIDIAN);
            put(x, FLOOR - 1, z, Material.OBSIDIAN);
            put(x, FLOOR - 2, z, Material.CRYING_OBSIDIAN);
        }
    }

    private void underside(int x, int z, int depth, Material a, Material b, Material c) {
        for (int i = 1; i <= depth; i++) {
            int y = FLOOR - i;
            if (built.containsKey(key(x, y, z))) {
                continue;
            }
            Material m = i <= 1 ? a : i <= 3 ? b : c;
            put(x, y, z, m);
        }
    }

    private void post(int x, int z, Material lamp) {
        put(x, FLOOR + 1, z, Material.OAK_FENCE);
        put(x, FLOOR + 2, z, lantern(lamp, false));
    }

    private Material flower(int x, int z) {
        Material[] set = {Material.POPPY, Material.DANDELION, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.AZURE_BLUET};
        return set[(int) (h01(x, 77, z, 18) * set.length) % set.length];
    }

    private void spruce(int tx, int tz) {
        for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
            put(tx, y, tz, Material.SPRUCE_LOG);
        }
        BlockData l = leaves(Material.SPRUCE_LEAVES);
        float[] radius = {2.2f, 1.6f, 2.0f, 1.2f, 0.6f};
        for (int i = 0; i < radius.length; i++) {
            int y = FLOOR + 2 + i;
            int r = (int) Math.ceil(radius[i]);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > radius[i] * radius[i] + 0.2f) {
                        continue;
                    }
                    if (dx == 0 && dz == 0 && y <= FLOOR + 5) {
                        continue;
                    }
                    put(tx + dx, y, tz + dz, l);
                }
            }
        }
        put(tx, FLOOR + 7, tz, l);
    }

    /* ================================================================== the threshold */

    private void paintPlaza() {
        for (int x = 7; x <= 41; x++) {
            for (int z = -45; z <= -14; z++) {
                double dx = x + 0.5 - PLAZA_X;
                double dz = z + 0.5 - PLAZA_Z;
                double d = Math.hypot(dx, dz);
                if (d > PLAZA_R) {
                    continue;
                }
                double ang = Math.toDegrees(Math.atan2(dx, dz));
                double spoke = Math.abs(((ang % 45) + 45) % 45 - 22.5);
                Material top;
                if (d <= 3.6) {
                    top = Material.BEDROCK;
                } else if (d <= 4.4) {
                    top = Material.CHISELED_DEEPSLATE;
                } else if (d <= 8.5) {
                    top = spoke > 21.0 ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE;
                } else if (d <= 9.4) {
                    top = Material.CHISELED_TUFF;
                } else if (d <= 14.4) {
                    top = h01(x, 20, z, 21) < 0.2f ? Material.CRACKED_DEEPSLATE_TILES : Material.DEEPSLATE_TILES;
                } else {
                    double mod = ((ang % 15) + 15) % 15;
                    top = mod < 2.5 ? Material.BEDROCK : Material.POLISHED_BLACKSTONE_BRICKS;
                }
                put(x, FLOOR, z, top);
                // The keel: an inverted cone of the world's floor, bedrock at its tip.
                int depth = 2 + (int) Math.round((PLAZA_R - d) * 1.05 + h01(x, 21, z, 22) * 2.0);
                for (int i = 1; i <= depth; i++) {
                    int y = FLOOR - i;
                    Material m;
                    if (i == 1) {
                        m = Material.DEEPSLATE;
                    } else if (i >= depth - 1 && d < 6) {
                        m = Material.BEDROCK;
                    } else {
                        float r = h01(x, y, z, 23);
                        m = r < 0.45f ? Material.DEEPSLATE : r < 0.7f ? Material.COBBLED_DEEPSLATE
                                : r < 0.88f ? Material.BLACKSTONE : Material.BEDROCK;
                    }
                    put(x, y, z, m);
                }
            }
        }
        // Chains and soul lanterns hanging from the keel.
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + Math.PI / 6;
            int x = (int) Math.floor(PLAZA_X + Math.sin(a) * 8.5);
            int z = (int) Math.floor(PLAZA_Z + Math.cos(a) * 8.5);
            int bottom = FLOOR;
            while (built.containsKey(key(x, bottom - 1, z)) && bottom > 70) {
                bottom--;
            }
            int len = 3 + k % 3;
            for (int i = 1; i <= len; i++) {
                put(x, bottom - i, z, Material.CHAIN);
            }
            put(x, bottom - len - 1, z, lantern(Material.SOUL_LANTERN, true));
        }
        // Four broken pillars.
        int[] heights = {5, 8, 4, 7};
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + k * 90);
            int cx = (int) Math.floor(PLAZA_X + Math.sin(a) * 12.0);
            int cz = (int) Math.floor(PLAZA_Z + Math.cos(a) * 12.0);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int top = FLOOR + heights[k] - (h01(cx + dx, 30, cz + dz, 24) < 0.4f ? 1 : 0);
                    for (int y = FLOOR + 1; y <= top; y++) {
                        Material m = y == top ? Material.CHISELED_DEEPSLATE
                                : h01(cx + dx, y, cz + dz, 25) < 0.2f ? Material.CRACKED_DEEPSLATE_BRICKS : Material.DEEPSLATE_BRICKS;
                        put(cx + dx, y, cz + dz, m);
                    }
                }
            }
            if (heights[k] >= 7) {
                put(cx, FLOOR + heights[k] + 1, cz, lantern(Material.SOUL_LANTERN, false));
            }
        }
        paintStatue();
    }

    /** The Unbroken, kneeling at the center: the same rig the boss uses, voxelized. */
    private void paintStatue() {
        UnbrokenBody body = new UnbrokenBody();
        body.rig.rootPos.set(0f, UnbrokenBody.KNEEL, 0f);
        body.rig.yaw = UnbrokenBody.facingNorth();
        body.poseKneel(0f);
        body.rig.snapAll();
        body.rig.solve();
        List<WeRig.Piece> solid = new ArrayList<>();
        for (WeRig.Piece p : body.rig.pieces()) {
            if (!p.hidden && p.material != null && p.material.isSolid()) {
                solid.add(p);
            }
        }
        for (int x = 14; x <= 34; x++) {
            for (int y = FLOOR + 1; y <= FLOOR + 12; y++) {
                for (int z = -38; z <= -18; z++) {
                    Vector3f c = new Vector3f((float) (x + 0.5 - PLAZA_X), (float) (y + 0.5 - (FLOOR + 1)), (float) (z + 0.5 - PLAZA_Z));
                    Material best = null;
                    float bestVolume = Float.MAX_VALUE;
                    for (WeRig.Piece p : solid) {
                        Vector3f size = body.rig.sizeOf(p);
                        Quaternionf rot = body.rig.rotationOf(p);
                        Vector3f local = new Quaternionf(rot).conjugate().transform(new Vector3f(c).sub(body.rig.centerOf(p)));
                        if (Math.abs(local.x) <= size.x * 0.5f + 0.1f
                                && Math.abs(local.y) <= size.y * 0.5f + 0.1f
                                && Math.abs(local.z) <= size.z * 0.5f + 0.1f) {
                            float v = size.x * size.y * size.z;
                            if (v < bestVolume) {
                                bestVolume = v;
                                best = p.material;
                            }
                        }
                    }
                    if (best != null) {
                        put(x, y, z, best);
                        statue.add(key(x, y, z));
                    }
                }
            }
        }
    }

    /* ================================================================== the bulwark */

    private void paintWall() {
        for (int x = -6; x <= 53; x++) {
            float endTaper = Math.min(1f, Math.min(x + 6, 53 - x) / 10f);
            int bottom = 90 + (int) (h01(x, 40, 0, 26) * 3) + (int) ((1f - endTaper) * 6);
            int top = 118 + (int) (endTaper * 6) + (int) (h01(x, 41, 0, 27) * 3);
            for (int z = WALL_Z0; z <= WALL_Z1; z++) {
                for (int y = bottom; y <= top; y++) {
                    if (inGate(x, y)) {
                        continue;
                    }
                    Material m;
                    if (y < FLOOR) {
                        m = h01(x, y, z, 28) < 0.75f ? Material.BEDROCK : Material.DEEPSLATE;
                    } else if (y >= top - 2) {
                        m = h01(x, y, z, 29) < 0.6f ? Material.BEDROCK : Material.DEEPSLATE_TILES;
                    } else if (y == 106 && z == WALL_Z0) {
                        m = Material.CHISELED_DEEPSLATE;
                    } else {
                        float r = h01(x, y, z, 30);
                        m = r < 0.55f ? Material.DEEPSLATE_BRICKS : r < 0.7f ? Material.CRACKED_DEEPSLATE_BRICKS
                                : r < 0.85f ? Material.DEEPSLATE_TILES : Material.POLISHED_BLACKSTONE_BRICKS;
                    }
                    put(x, y, z, m);
                }
            }
        }
        // The threshold through the wall, and the landing between the plaza's lip and the gate.
        for (int x = GATE_X0; x <= GATE_X1; x++) {
            for (int z = WALL_Z0 - 1; z <= WALL_Z1; z++) {
                put(x, FLOOR, z, Material.POLISHED_DEEPSLATE);
                put(x, FLOOR - 1, z, Material.BEDROCK);
            }
        }
        // The doors: one plane of bedrock, a ring of crying obsidian, an obsidian seam.
        for (int x = GATE_X0; x <= GATE_X1; x++) {
            for (int y = GATE_Y0; y <= GATE_Y1 + 2; y++) {
                if (!inGate(x, y)) {
                    continue;
                }
                double ring = Math.hypot(x + 0.5 - 24.0, y + 0.5 - 107.0);
                Material m;
                if (x == 23 || x == 24) {
                    m = Material.OBSIDIAN;
                } else if (Math.abs(ring - 4.5) < 0.55) {
                    m = Material.CRYING_OBSIDIAN;
                } else {
                    m = Material.BEDROCK;
                }
                put(x, y, DOOR_Z, m);
                doors.add(key(x, y, DOOR_Z));
            }
        }
        // The frame: pillars and a lintel on the plaza side.
        for (int y = FLOOR; y <= 117; y++) {
            for (int x : new int[]{16, 17, 30, 31}) {
                put(x, y, WALL_Z0 - 1, y % 4 == 0 ? Material.CHISELED_DEEPSLATE : Material.POLISHED_DEEPSLATE);
            }
        }
        for (int x = 16; x <= 31; x++) {
            for (int y = 116; y <= 117; y++) {
                boolean key = (x == 23 || x == 24) && y == 117;
                put(x, y, WALL_Z0 - 1, key ? Material.CRYING_OBSIDIAN : Material.CHISELED_DEEPSLATE);
            }
        }
        put(18, 115, WALL_Z0 - 1, lantern(Material.SOUL_LANTERN, true));
        put(29, 115, WALL_Z0 - 1, lantern(Material.SOUL_LANTERN, true));
    }

    static boolean inGate(int x, int y) {
        if (y >= GATE_Y0 && y <= GATE_Y1) {
            return x >= GATE_X0 && x <= GATE_X1;
        }
        if (y == GATE_Y1 + 1) {
            return x >= GATE_X0 + 2 && x <= GATE_X1 - 2;
        }
        if (y == GATE_Y1 + 2) {
            return x >= GATE_X0 + 4 && x <= GATE_X1 - 4;
        }
        return false;
    }

    /* ================================================================== bridge */

    private void paintBridge() {
        for (int x = 21; x <= 26; x++) {
            for (int z = -9; z <= -1; z++) {
                put(x, FLOOR, z, Material.POLISHED_DEEPSLATE);
                put(x, FLOOR - 1, z, Material.DEEPSLATE_BRICKS);
                if ((x == 21 || x == 26)) {
                    put(x, FLOOR + 1, z, z == -5 ? railPost() : railZ());
                } else if (z >= -7 && z <= -3) {
                    put(x, FLOOR - 2, z, Material.DEEPSLATE_BRICKS);
                }
            }
        }
    }

    /* ================================================================== the last seed */

    /** Strata of an untouched world: grass, dirt, stone with ores, deepslate, bedrock. */
    private BlockData islandBase(int x, int y, int z) {
        if (x < ISLAND_MIN || x > ISLAND_MAX || z < ISLAND_MIN || z > ISLAND_MAX) {
            return null;
        }
        if (y > FLOOR || y < ISLAND_BOTTOM) {
            return null;
        }
        if (y == FLOOR) {
            return data(Material.GRASS_BLOCK);
        }
        if (y >= FLOOR - 3) {
            return data(Material.DIRT);
        }
        if (y == FLOOR - 4) {
            return data(h01(x, y, z, 31) < 0.5f ? Material.DIRT : Material.STONE);
        }
        if (y >= 80) {
            return data(stone(x, y, z));
        }
        if (y >= 72) {
            if (y == 79 && h01(x, y, z, 32) < 0.5f) {
                return data(stone(x, y, z));
            }
            return data(deepslate(x, y, z));
        }
        if (y == 71) {
            return data(Material.BEDROCK);
        }
        return h01(x, y, z, 33) < 0.6f ? data(Material.BEDROCK) : null;
    }

    private static Material stone(int x, int y, int z) {
        float vein = WeMath.hash01(x >> 1, y >> 1, z >> 1, 40);
        float pick = WeMath.hash01(x, y, z, 41);
        if (vein < 0.035f && y > 84) {
            return Material.COAL_ORE;
        }
        if (vein < 0.06f && pick < 0.8f) {
            return Material.IRON_ORE;
        }
        if (vein < 0.075f && y > 86) {
            return Material.COPPER_ORE;
        }
        if (vein < 0.083f && y < 86) {
            return Material.GOLD_ORE;
        }
        if (vein < 0.092f && y < 84) {
            return Material.REDSTONE_ORE;
        }
        if (vein < 0.098f && y < 88) {
            return Material.LAPIS_ORE;
        }
        float blob = WeMath.noise2((x + y * 0.7f) / 5f, (z - y * 0.5f) / 5f, 42);
        if (blob > 0.8f) {
            return Material.ANDESITE;
        }
        if (blob < 0.14f) {
            return Material.DIORITE;
        }
        if (blob > 0.74f && blob <= 0.8f) {
            return Material.GRANITE;
        }
        return Material.STONE;
    }

    private static Material deepslate(int x, int y, int z) {
        float vein = WeMath.hash01(x >> 1, y >> 1, z >> 1, 43);
        if (vein < 0.02f) {
            return Material.DEEPSLATE_DIAMOND_ORE;
        }
        if (vein < 0.04f) {
            return Material.DEEPSLATE_REDSTONE_ORE;
        }
        if (vein < 0.05f) {
            return Material.DEEPSLATE_GOLD_ORE;
        }
        return WeMath.hash01(x, y, z, 44) < 0.12f ? Material.TUFF : Material.DEEPSLATE;
    }

    private void paintIsland() {
        // A dirt path from the bridge to the heart of the island.
        float[][] path = {{24f, 0f}, {24.6f, 5f}, {23.2f, 10f}, {24.4f, 15f}, {24f, 19.5f}};
        for (int x = ISLAND_MIN; x <= ISLAND_MAX; x++) {
            for (int z = ISLAND_MIN; z <= 20; z++) {
                float best = Float.MAX_VALUE;
                for (int i = 0; i + 1 < path.length; i++) {
                    Vector3f p = new Vector3f(x + 0.5f, 0f, z + 0.5f);
                    float d = WeMath.segmentDistanceXZ(p, new Vector3f(path[i][0], 0f, path[i][1]), new Vector3f(path[i + 1][0], 0f, path[i + 1][1]));
                    best = Math.min(best, d);
                }
                if (best < 1.25f) {
                    feature(x, FLOOR, z, Material.DIRT_PATH);
                    islandFeatures.put(key(x, FLOOR + 1, z), air);
                }
            }
        }
        // The heart: a fairy ring of flowers around a patch of podzol, where the seed will fall.
        for (int x = 16; x <= 31; x++) {
            for (int z = 16; z <= 31; z++) {
                double d = Math.hypot(x + 0.5 - CENTER_X, z + 0.5 - CENTER_Z);
                if (d <= 1.5) {
                    feature(x, FLOOR, z, Material.PODZOL);
                    islandFeatures.put(key(x, FLOOR + 1, z), air);
                } else if (Math.abs(d - 5.0) < 0.5 && !hasFeature(x, FLOOR, z)) {
                    feature(x, FLOOR + 1, z, flower(x, z));
                } else if (d < 7.5 && !hasFeature(x, FLOOR + 1, z)) {
                    islandFeatures.put(key(x, FLOOR + 1, z), air);
                }
            }
        }
        // The pond, in the east edge chunk, well clear of every chunk border.
        for (int x = 34; x <= 46; x++) {
            for (int z = 18; z <= 30; z++) {
                double ex = (x + 0.5 - 40.0) / 4.3;
                double ez = (z + 0.5 - 24.0) / 3.3;
                double e = Math.sqrt(ex * ex + ez * ez);
                if (e <= 1.0) {
                    feature(x, FLOOR, z, Material.WATER);
                    if (e <= 0.62) {
                        feature(x, FLOOR - 1, z, Material.WATER);
                        feature(x, FLOOR - 2, z, h01(x, 50, z, 45) < 0.5f ? Material.CLAY : Material.SAND);
                    } else {
                        feature(x, FLOOR - 1, z, Material.SAND);
                    }
                    islandFeatures.put(key(x, FLOOR + 1, z), air);
                    if (e < 0.8 && h01(x, 51, z, 46) < 0.1f) {
                        feature(x, FLOOR + 1, z, Material.LILY_PAD);
                    }
                } else if (e <= 1.35) {
                    feature(x, FLOOR, z, Material.SAND);
                    if (e <= 1.18 && h01(x, 52, z, 47) < 0.18f) {
                        int h = 1 + (int) (h01(x, 53, z, 48) * 3);
                        for (int i = 1; i <= h; i++) {
                            feature(x, FLOOR + i, z, Material.SUGAR_CANE);
                        }
                    } else {
                        islandFeatures.put(key(x, FLOOR + 1, z), air);
                    }
                }
            }
        }
        // Trees: an old oak in the north-west corner chunk, oaks and birches around the edges.
        oak(8, 8, 7, true, Material.OAK_LOG, Material.OAK_LEAVES);
        oak(39, 10, 5, false, Material.OAK_LOG, Material.OAK_LEAVES);
        oak(9, 39, 6, false, Material.OAK_LOG, Material.OAK_LEAVES);
        oak(38, 40, 6, false, Material.BIRCH_LOG, Material.BIRCH_LEAVES);
        oak(6, 21, 5, false, Material.BIRCH_LOG, Material.BIRCH_LEAVES);
        oak(28, 41, 5, false, Material.OAK_LOG, Material.OAK_LEAVES);
        // Lantern posts: light for when the sky is gone.
        int[][] lamps = {{21, 5}, {27, 12}, {18, 18}, {29, 18}, {18, 29}, {29, 29}, {4, 30}, {43, 42}, {44, 6}};
        for (int[] l : lamps) {
            feature(l[0], FLOOR + 1, l[1], Material.OAK_FENCE);
            feature(l[0], FLOOR + 2, l[1], lantern(Material.LANTERN, false));
        }
        // Grass and flowers everywhere else.
        BlockData tallLower = Material.TALL_GRASS.createBlockData();
        BlockData tallUpper = Material.TALL_GRASS.createBlockData();
        if (tallUpper instanceof Bisected b) {
            b.setHalf(Bisected.Half.TOP);
        }
        for (int x = ISLAND_MIN + 1; x < ISLAND_MAX; x++) {
            for (int z = ISLAND_MIN + 1; z < ISLAND_MAX; z++) {
                if (hasFeature(x, FLOOR, z) || hasFeature(x, FLOOR + 1, z)) {
                    continue;
                }
                float r = h01(x, 60, z, 49);
                if (r < 0.12f) {
                    feature(x, FLOOR + 1, z, Material.SHORT_GRASS);
                } else if (r < 0.145f) {
                    feature(x, FLOOR + 1, z, flower(x, z));
                } else if (r < 0.155f && !hasFeature(x, FLOOR + 2, z)) {
                    feature(x, FLOOR + 1, z, tallLower);
                    feature(x, FLOOR + 2, z, tallUpper);
                }
            }
        }
    }

    private void oak(int tx, int tz, int height, boolean old, Material log, Material leafType) {
        trees.add(new long[]{tx, tz, height});
        BlockData l = leaves(leafType);
        int base = FLOOR + 1;
        for (int y = base; y < base + height; y++) {
            feature(tx, y, tz, log);
        }
        feature(tx, FLOOR, tz, Material.DIRT);
        int top = base + height - 1;
        float[][] layers = old
                ? new float[][]{{top - 3, 3.3f}, {top - 2, 3.4f}, {top - 1, 2.6f}, {top, 2.0f}, {top + 1, 1.2f}}
                : new float[][]{{top - 2, 2.4f}, {top - 1, 2.4f}, {top, 1.5f}, {top + 1, 1.0f}};
        for (float[] layer : layers) {
            int y = (int) layer[0];
            float r = layer[1];
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    float d2 = dx * dx + dz * dz;
                    if (d2 > r * r) {
                        continue;
                    }
                    if (d2 > (r - 0.8f) * (r - 0.8f) && h01(tx + dx, y, tz + dz, 55) < 0.35f) {
                        continue;
                    }
                    int x = tx + dx;
                    int z = tz + dz;
                    if (x < ISLAND_MIN || x > ISLAND_MAX || z < ISLAND_MIN || z > ISLAND_MAX) {
                        continue;
                    }
                    if (dx == 0 && dz == 0 && y < base + height) {
                        continue;
                    }
                    feature(x, y, z, l);
                }
            }
        }
        if (old) {
            feature(tx + 1, top - 2, tz, log);
            feature(tx - 1, top - 1, tz + 1, log);
        }
        // Keep the ground under the canopy clear of tall plants.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !hasFeature(tx + dx, base, tz + dz)) {
                    islandFeatures.put(key(tx + dx, base, tz + dz), air);
                }
            }
        }
    }
}
