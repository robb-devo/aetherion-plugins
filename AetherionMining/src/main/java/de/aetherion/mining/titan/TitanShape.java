package de.aetherion.mining.titan;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ossarath, the Isle-Eater: the fixed body plan of the Swallowed Isles map (gullet, ribcage, heart, lungs, spine,
 * belly sea, marrow, tail). The day's seed only moves details: walls, tunnels, veins, the heart a few blocks and which
 * swallowed isles drift in the belly. Pure functions of (x, y, z), safe on the generator's worker threads.
 *
 * <p>Axis: head (Jawcamp, its own world) beyond -X, tail at +X, spine on top. Everything outside the bedrock box is void.
 */
final class TitanShape {

    enum Zone { ROCK, GULLET, RIBVAULT, HEART, LUNGS, SPINE, SEA, MARROW, TAIL }

    /** A breath column: inside it a player rises (sneaking: falls without harm). */
    record Updraft(double x, double z, double radius, int bottom, int top) {
        boolean contains(double px, double py, double pz) {
            double dx = px - x;
            double dz = pz - z;
            return dx * dx + dz * dz <= radius * radius && py >= bottom && py <= top + 1;
        }
    }

    /** A swallowed isle site in the belly sea (step 3 builds the theme on it). */
    record Isle(int index, int x, int z, int radius, int top) {
    }

    static final int MIN_X = -352;
    static final int MAX_X = 368;
    static final int MIN_Z = -176;
    static final int MAX_Z = 176;
    static final int TOP = 214;
    static final int SEA = 50;
    static final int LANDING_FLOOR = 65;

    // Ribvault (ribcage hall) and the throat chamber in front of it.
    private static final double RV_X = -205, RV_Y = 88, RV_RX = 112, RV_RY = 52, RV_RZ = 84;
    private static final double TH_X = -302, TH_Y = 78, TH_RX = 22, TH_RY = 15, TH_RZ = 20;
    // Belly (Churning Sea).
    private static final double BL_X = 108, BL_Y = 80, BL_RX = 130, BL_RY = 64, BL_RZ = 110;
    // Lungs (Breathing Caves), mirrored on z.
    private static final double LG_X = -205, LG_Y = 160, LG_Z = 58, LG_RX = 86, LG_RY = 16, LG_RZ = 34;
    // Spine Road.
    private static final double SP_Y = 186, SP_R = 4.5;
    private static final int SP_X0 = -300, SP_X1 = 300, SP_FLOOR = 182;
    // Gullet shaft.
    static final double GU_X = -306, GU_Z = 0;
    private static final double GU_R = 8;
    private static final int GU_TOP = 205;
    // Marrowdeep band.
    private static final int MW_BOTTOM = 5, MW_TOP = 32, MW_BONE = 36;

    private static final int[][] ISLE_SITES = {{30, -40}, {75, 46}, {120, -28}, {165, 42}, {200, -14}, {98, 64}};

    final long seed;
    final double heartX, heartY, heartZ;
    final List<Isle> isles;
    final List<Updraft> updrafts;
    private final double[][] shafts; // x, z, top, bottom (marrow shafts with ladders)
    private final double[][] tail;
    private final double[][] aorta;
    private final double[][] bronchi;
    private final double[] upA, upB, downA, downB;
    private static final double[] DIA_A = {-104, 56, 0}, DIA_B = {-10, 58, 0};

    private final SimplexNoiseGenerator wall, strata, detail, noodleA, noodleB, pocket, alveoli, marrow, ore, floor;

    final Palette p;

    TitanShape(long seed, Palette palette) {
        this.seed = seed;
        this.p = palette;
        Random random = new Random(seed ^ 0x51A11E5L);
        wall = new SimplexNoiseGenerator(random.nextLong());
        strata = new SimplexNoiseGenerator(random.nextLong());
        detail = new SimplexNoiseGenerator(random.nextLong());
        noodleA = new SimplexNoiseGenerator(random.nextLong());
        noodleB = new SimplexNoiseGenerator(random.nextLong());
        pocket = new SimplexNoiseGenerator(random.nextLong());
        alveoli = new SimplexNoiseGenerator(random.nextLong());
        marrow = new SimplexNoiseGenerator(random.nextLong());
        ore = new SimplexNoiseGenerator(random.nextLong());
        floor = new SimplexNoiseGenerator(random.nextLong());

        heartX = -176 + random.nextInt(9) - 4;
        heartY = 62 + random.nextInt(3) - 1;
        heartZ = -38 + random.nextInt(9) - 4;
        upA = new double[]{heartX, heartY + 16, heartZ};
        upB = new double[]{heartX - 18, 140, heartZ + 8};
        downA = new double[]{heartX + 2, heartY - 16, heartZ};
        downB = new double[]{heartX + 6, 40, heartZ + 4};

        List<Integer> sites = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        java.util.Collections.shuffle(sites, random);
        List<Isle> picked = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int[] site = ISLE_SITES[sites.get(i)];
            picked.add(new Isle(sites.get(i), site[0] + random.nextInt(9) - 4, site[1] + random.nextInt(9) - 4,
                    20 + random.nextInt(5), 59 + random.nextInt(4)));
        }
        isles = List.copyOf(picked);

        List<Updraft> drafts = new ArrayList<>();
        // The windpipe: from the gullet landing up to an alcove beside the Spine Road.
        drafts.add(new Updraft(-290.5, 7.5, 2.3, LANDING_FLOOR, SP_FLOOR + 4));
        for (int side = -1; side <= 1; side += 2) {
            for (double x : new double[]{-262.5, -205.5, -159.5}) {
                double z = 52.5 * side;
                drafts.add(new Updraft(x, z, 2.3, (int) Math.ceil(ribvaultFloor(x, z)) + 1, (int) (LG_Y + LG_RY) - 2));
            }
        }
        updrafts = List.copyOf(drafts);

        shafts = new double[][]{
                {heartX + 14, heartZ + 16, Math.ceil(ribvaultFloor(heartX + 14, heartZ + 16)), 20},
                {-128, -42, Math.ceil(ribvaultFloor(-128, -42)), 20},
                {226, 6, 53, 20}
        };
        tail = new double[][]{{232, 56, 0}, {276, 50, 22}, {314, 42, 6}, {340, 36, -16}};
        aorta = new double[][]{{heartX + 13, heartY - 9, heartZ}, {-60, 62, -14}, {-8, 60, 0}};
        bronchi = new double[][]{
                {-250, 184, 0, -240, 168, 42}, {-250, 184, 0, -240, 168, -42},
                {-170, 184, 0, -160, 168, 42}, {-170, 184, 0, -160, 168, -42}};
    }

    // ------------------------------------------------------------------ palette

    /** Every block state the titan uses, made once on the main thread. */
    static final class Palette {
        final BlockData air = Material.AIR.createBlockData();
        final BlockData cave = Material.CAVE_AIR.createBlockData();
        final BlockData bedrock = Material.BEDROCK.createBlockData();
        final BlockData water = Material.WATER.createBlockData();
        final BlockData lava = Material.LAVA.createBlockData();
        final BlockData boneX = Bukkit.createBlockData("minecraft:bone_block[axis=x]");
        final BlockData boneY = Bukkit.createBlockData("minecraft:bone_block[axis=y]");
        final BlockData boneZ = Bukkit.createBlockData("minecraft:bone_block[axis=z]");
        final BlockData calcite = Material.CALCITE.createBlockData();
        final BlockData dripstone = Material.DRIPSTONE_BLOCK.createBlockData();
        final BlockData tuff = Material.TUFF.createBlockData();
        final BlockData deepslate = Material.DEEPSLATE.createBlockData();
        final BlockData terracotta = Material.TERRACOTTA.createBlockData();
        final BlockData brownTerracotta = Material.BROWN_TERRACOTTA.createBlockData();
        final BlockData redTerracotta = Material.RED_TERRACOTTA.createBlockData();
        final BlockData pinkTerracotta = Material.PINK_TERRACOTTA.createBlockData();
        final BlockData wart = Material.NETHER_WART_BLOCK.createBlockData();
        final BlockData shroomlight = Material.SHROOMLIGHT.createBlockData();
        final BlockData packedIce = Material.PACKED_ICE.createBlockData();
        final BlockData blueIce = Material.BLUE_ICE.createBlockData();
        final BlockData snow = Material.SNOW_BLOCK.createBlockData();
        final BlockData netherrack = Material.NETHERRACK.createBlockData();
        final BlockData blackstone = Material.BLACKSTONE.createBlockData();
        final BlockData basalt = Bukkit.createBlockData("minecraft:basalt[axis=y]");
        final BlockData magma = Material.MAGMA_BLOCK.createBlockData();
        final BlockData moss = Material.MOSS_BLOCK.createBlockData();
        final BlockData mud = Material.MUD.createBlockData();
        final BlockData clay = Material.CLAY.createBlockData();
        final BlockData amethyst = Material.AMETHYST_BLOCK.createBlockData();
        final BlockData budding = Material.BUDDING_AMETHYST.createBlockData();
        final BlockData redstoneBlock = Material.REDSTONE_BLOCK.createBlockData();
        final BlockData redGlass = Material.RED_STAINED_GLASS.createBlockData();
        final BlockData cryingObsidian = Material.CRYING_OBSIDIAN.createBlockData();
        final BlockData sealBricks = Material.POLISHED_BLACKSTONE_BRICKS.createBlockData();
        final BlockData lodestone = Material.LODESTONE.createBlockData();
        final BlockData landing = Material.POLISHED_TUFF.createBlockData();
        final BlockData roadFloor = Material.POLISHED_DEEPSLATE.createBlockData();
        final BlockData rail = Bukkit.createBlockData("minecraft:rail[shape=east_west]");
        final BlockData lantern = Bukkit.createBlockData("minecraft:lantern[hanging=true]");
        final BlockData ladderWest = Bukkit.createBlockData("minecraft:ladder[facing=west]");
        final BlockData light12 = Bukkit.createBlockData("minecraft:light[level=12]");
        final BlockData light10 = Bukkit.createBlockData("minecraft:light[level=10]");
        final BlockData glowVine = Bukkit.createBlockData("minecraft:cave_vines[berries=true]");
        final BlockData vinePlant = Bukkit.createBlockData("minecraft:cave_vines_plant[berries=false]");
        final BlockData vinePlantLit = Bukkit.createBlockData("minecraft:cave_vines_plant[berries=true]");
        final BlockData weeping = Material.WEEPING_VINES.createBlockData();
        final BlockData weepingPlant = Material.WEEPING_VINES_PLANT.createBlockData();
        final BlockData crimsonRoots = Material.CRIMSON_ROOTS.createBlockData();
        final BlockData snowLayer = Bukkit.createBlockData("minecraft:snow[layers=2]");
        final BlockData[] pickles = {
                Bukkit.createBlockData("minecraft:sea_pickle[pickles=1,waterlogged=true]"),
                Bukkit.createBlockData("minecraft:sea_pickle[pickles=2,waterlogged=true]"),
                Bukkit.createBlockData("minecraft:sea_pickle[pickles=3,waterlogged=true]")};
        final BlockData[] dripDown = drip("down");
        final BlockData[] dripUp = drip("up");
        // Ores (stone and deepslate backed), marrow ores.
        final BlockData coal = Material.COAL_ORE.createBlockData(), coalD = Material.DEEPSLATE_COAL_ORE.createBlockData();
        final BlockData iron = Material.IRON_ORE.createBlockData(), ironD = Material.DEEPSLATE_IRON_ORE.createBlockData();
        final BlockData copper = Material.COPPER_ORE.createBlockData(), copperD = Material.DEEPSLATE_COPPER_ORE.createBlockData();
        final BlockData gold = Material.GOLD_ORE.createBlockData(), goldD = Material.DEEPSLATE_GOLD_ORE.createBlockData();
        final BlockData redstone = Material.REDSTONE_ORE.createBlockData(), redstoneD = Material.DEEPSLATE_REDSTONE_ORE.createBlockData();
        final BlockData lapis = Material.LAPIS_ORE.createBlockData(), lapisD = Material.DEEPSLATE_LAPIS_ORE.createBlockData();
        final BlockData diamond = Material.DIAMOND_ORE.createBlockData(), diamondD = Material.DEEPSLATE_DIAMOND_ORE.createBlockData();
        final BlockData emerald = Material.EMERALD_ORE.createBlockData(), emeraldD = Material.DEEPSLATE_EMERALD_ORE.createBlockData();
        final BlockData netherGold = Material.NETHER_GOLD_ORE.createBlockData();
        final BlockData quartz = Material.NETHER_QUARTZ_ORE.createBlockData();
        final BlockData debris = Material.ANCIENT_DEBRIS.createBlockData();
        final BlockData gilded = Material.GILDED_BLACKSTONE.createBlockData();

        private static BlockData[] drip(String way) {
            BlockData[] out = new BlockData[4];
            String[] thickness = {"tip", "frustum", "middle", "base"};
            for (int i = 0; i < 4; i++) {
                out[i] = Bukkit.createBlockData("minecraft:pointed_dripstone[thickness=" + thickness[i]
                        + ",vertical_direction=" + way + "]");
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ geometry helpers

    private static double ell(double x, double y, double z, double cx, double cy, double cz,
                              double rx, double ry, double rz) {
        double dx = (x - cx) / rx;
        double dy = (y - cy) / ry;
        double dz = (z - cz) / rz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Distance from p to segment a-b. */
    private static double segment(double x, double y, double z, double[] a, double[] b) {
        double abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
        double apx = x - a[0], apy = y - a[1], apz = z - a[2];
        double len = abx * abx + aby * aby + abz * abz;
        double t = len <= 0 ? 0 : Math.max(0, Math.min(1, (apx * abx + apy * aby + apz * abz) / len));
        double dx = apx - abx * t, dy = apy - aby * t, dz = apz - abz * t;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double polyline(double x, double y, double z, double[][] points) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < points.length; i++) {
            best = Math.min(best, segment(x, y, z, points[i], points[i + 1]));
        }
        return best;
    }

    /** Stable 0..1 hash of a block position (decorations, ore choice). */
    double hash(int x, int y, int z, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x85157AF5L + z * 0xC2B2AE3D27D4EB4FL + salt;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    // ------------------------------------------------------------------ cavities

    double ribFloor(double x, double z) {
        if (Math.abs(x - TH_X) < 18 && Math.abs(z) < 16) {
            return LANDING_FLOOR - 1;
        }
        return 46 + 4 * floor.noise(x / 40.0, z / 40.0);
    }

    /** The walkable Ribvault floor: the bowl of the ribcage or the rubble floor, whichever is higher. */
    double ribvaultFloor(double x, double z) {
        double f = 1 - Math.pow((x - RV_X) / RV_RX, 2) - Math.pow(z / RV_RZ, 2);
        double bowl = f <= 0 ? RV_Y : RV_Y - RV_RY * Math.sqrt(f);
        return Math.max(ribFloor(x, z), bowl);
    }

    double seaFloor(double x, double z) {
        return 31 + 7 * floor.noise(x / 36.0 + 50, z / 36.0) + 3 * floor.noise(x / 12.0, z / 12.0 + 90);
    }

    boolean inRibvault(double x, double y, double z) {
        double e = Math.min(ell(x, y, z, RV_X, RV_Y, 0, RV_RX, RV_RY, RV_RZ),
                ell(x, y, z, TH_X, TH_Y, 0, TH_RX, TH_RY, TH_RZ));
        if (e > 1.25) {
            return false;
        }
        double w = 0.10 * wall.noise(x / 22.0, y / 18.0, z / 22.0);
        return e < 1 + w && y > ribFloor(x, z);
    }

    boolean inBelly(double x, double y, double z) {
        double e = ell(x, y, z, BL_X, BL_Y, 0, BL_RX, BL_RY, BL_RZ);
        if (e > 1.2) {
            return false;
        }
        return e < 1 + 0.08 * wall.noise(x / 26.0 + 40, y / 20.0, z / 26.0);
    }

    private double lungE(double x, double y, double z) {
        return Math.min(ell(x, y, z, LG_X, LG_Y, LG_Z, LG_RX, LG_RY, LG_RZ),
                ell(x, y, z, LG_X, LG_Y, -LG_Z, LG_RX, LG_RY, LG_RZ));
    }

    boolean inLungs(double x, double y, double z) {
        double e = lungE(x, y, z);
        if (e >= 1) {
            return false;
        }
        return alveoli.noise(x / 9.0, y / 6.0, z / 9.0) > -0.2 + 0.45 * e;
    }

    boolean inSpine(double x, double y, double z) {
        if (x < SP_X0 || x > SP_X1 || y < SP_FLOOR) {
            return false;
        }
        double dy = y - SP_Y;
        return dy * dy + z * z < SP_R * SP_R;
    }

    boolean inGullet(double x, double y, double z) {
        double dx = x - GU_X, dz = z - GU_Z;
        return y <= GU_TOP && y >= 85 && dx * dx + dz * dz < GU_R * GU_R;
    }

    private double heartE(double x, double y, double z) {
        return ell(x, y, z, heartX, heartY, heartZ, 15, 18, 14);
    }

    boolean inMarrowBand(double x, double y, double z) {
        return y >= MW_BOTTOM && y <= MW_TOP && x > -320 && x < 250 && Math.abs(z) < 108;
    }

    boolean marrowCave(double x, double y, double z) {
        if (!inMarrowBand(x, y, z)) {
            return false;
        }
        double edge = Math.min(Math.min(y - MW_BOTTOM, MW_TOP - y), 8) / 8.0;
        return marrow.noise(x / 26.0, y / 9.0, z / 26.0) > 0.42 - 0.3 * edge;
    }

    private boolean inTail(double x, double y, double z) {
        return x > 220 && polyline(x, y, z, tail) < 4.2 && y > tail[tail.length - 1][1] - 4;
    }

    private boolean inAorta(double x, double y, double z) {
        return polyline(x, y, z, aorta) < 3.0;
    }

    private boolean inDiaphragm(double x, double y, double z) {
        if (x < -112 || x > -6) {
            return false;
        }
        double ramp = 46 + 5 * Math.max(0, Math.min(1, (x + 104) / 64.0));
        return y >= ramp && segment(x, y, z, DIA_A, DIA_B) < 7.5;
    }

    private boolean inBronchi(double x, double y, double z) {
        if (y < 160 || y > 190) {
            return false;
        }
        for (double[] b : bronchi) {
            if (segment(x, y, z, new double[]{b[0], b[1], b[2]}, new double[]{b[3], b[4], b[5]}) < 2.6) {
                return true;
            }
        }
        return false;
    }

    private boolean inShaft(double x, double y, double z) {
        for (double[] s : shafts) {
            double dx = x - s[0], dz = z - s[1];
            if (y <= s[2] && y >= s[3] && dx * dx + dz * dz < 2.2 * 2.2) {
                return true;
            }
            // landing chamber at the bottom
            if (ell(x, y, z, s[0], s[3] + 4, s[1], 7, 5, 7) < 1 && y >= s[3] - 1) {
                return true;
            }
        }
        return false;
    }

    private boolean inUpdraft(double x, double y, double z) {
        for (Updraft u : updrafts) {
            if (u.contains(x, y, z)) {
                return true;
            }
            // a little landing alcove at the top
            if (ell(x, y, z, u.x(), u.top() - 1, u.z(), 4.5, 3.2, 4.5) < 1) {
                return true;
            }
        }
        return false;
    }

    private boolean inPlungeHole(double x, double y, double z) {
        if (y < 100 || y > SP_FLOOR) {
            return false;
        }
        for (double px : new double[]{40, 120, 196}) {
            double dx = x - px;
            if (dx * dx + z * z < 2.0 * 2.0) {
                return true;
            }
        }
        return false;
    }

    private boolean inNoodle(double x, double y, double z) {
        if (y < 12 || y > 200) {
            return false;
        }
        // Never under the sea or beside the gullet pool: generated water must not find a way out.
        if (y <= SEA + 3 && ell(x, y, z, BL_X, BL_Y, 0, BL_RX, BL_RY, BL_RZ) < 1.3) {
            return false;
        }
        if (y < 72 && Math.abs(x - TH_X) < 26 && Math.abs(z) < 22) {
            return false;
        }
        double a = noodleA.noise(x / 42.0, y / 30.0, z / 42.0);
        if (Math.abs(a) > 0.05) {
            return pocket.noise(x / 18.0, y / 14.0, z / 18.0) > 0.68;
        }
        return Math.abs(noodleB.noise(x / 42.0, y / 30.0, z / 42.0)) < 0.05;
    }

    // ------------------------------------------------------------------ structures

    private boolean rib(double x, double y, double z) {
        if (x < -300 || x > -104 || y < ribFloor(x, z) + 3) {
            return false;
        }
        int i = (int) Math.round((x + 296) / 21.0);
        if (i < 0 || i > 9) {
            return false;
        }
        double xi = -296 + 21.0 * i;
        double dx = x - xi;
        if (Math.abs(dx) > 2.8) {
            return false;
        }
        double f = 1 - Math.pow((xi - RV_X) / RV_RX, 2);
        if (f <= 0.05) {
            return false;
        }
        double ry = RV_RY * Math.sqrt(f) * 0.96, rz = RV_RZ * Math.sqrt(f) * 0.96;
        double r = Math.sqrt(Math.pow((y - RV_Y) / ry, 2) + Math.pow(z / rz, 2));
        double d = (r - 1) * Math.min(ry, rz);
        return dx * dx + d * d < 2.7 * 2.7;
    }

    private boolean sternum(double x, double y, double z) {
        if (x < -280 || x > -112 || Math.abs(z) > 2.5) {
            return false;
        }
        double base = ribFloor(x, z);
        return y > base && y <= base + 2;
    }

    private boolean vertebra(double x, double y, double z) {
        if (x < SP_X0 || x > SP_X1 || y < SP_FLOOR + 1) {
            return false;
        }
        double k = Math.round((x - SP_X0) / 16.0);
        double dx = x - (SP_X0 + 16 * k);
        if (Math.abs(dx) > 1.4) {
            return false;
        }
        double r = Math.sqrt((y - SP_Y) * (y - SP_Y) + z * z) - 4.9;
        return dx * dx + r * r < 1.3 * 1.3;
    }

    private boolean gulletRidge(double x, double y, double z) {
        if (y < 100 || y > GU_TOP) {
            return false;
        }
        double k = Math.round((y - 100) / 9.0);
        double dy = y - (100 + 9 * k);
        double r = Math.sqrt((x - GU_X) * (x - GU_X) + (z - GU_Z) * (z - GU_Z)) - 8.2;
        return dy * dy + r * r < 1.5 * 1.5;
    }

    private boolean isleRock(double x, double y, double z) {
        for (Isle isle : isles) {
            double dx = x - isle.x(), dz = z - isle.z();
            double d = Math.sqrt(dx * dx + dz * dz);
            double edge = isle.radius() * (1 + 0.14 * detail.noise(x / 9.0, z / 9.0 + isle.index() * 31));
            if (d >= edge) {
                continue;
            }
            double top = isle.top() - Math.pow(d / edge, 2) * 9;
            if (y <= top && y > seaFloor(x, z) - 2) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the block at (x, y, z)

    /** Air codes for decorations and light: what cavity an air block belongs to. */
    static final int SOLID = 0, AIR_RIB = 1, AIR_SEA = 2, AIR_LUNG = 3, AIR_SPINE = 4, AIR_MARROW = 5, AIR_OTHER = 6,
            WATER = 7, LAVA = 8, AIR_HEART = 9;

    /** What stands at a block; {@code code[0]} receives the air/fluid code (SOLID for blocks). */
    BlockData at(int x, int y, int z, int[] code) {
        code[0] = SOLID;
        if (y <= 1 || y >= TOP - 1 || x < MIN_X + 4 || x >= MAX_X - 4 || z < MIN_Z + 4 || z >= MAX_Z - 4) {
            return p.bedrock;
        }
        // Gullet landing: pool under the shaft, a platform with the climbing lodestone.
        double gdx = x - GU_X, gdz = z - GU_Z;
        double g2 = gdx * gdx + gdz * gdz;
        if (y <= LANDING_FLOOR - 1 && y >= LANDING_FLOOR - 5 && g2 < 36) {
            if (y == LANDING_FLOOR - 5) {
                return p.clay;
            }
            code[0] = WATER;
            return p.water;
        }
        if (x == -294 && z == 0 && y == LANDING_FLOOR) {
            return p.lodestone;
        }
        if (y == LANDING_FLOOR - 1 && Math.abs(x - TH_X) < 18 && Math.abs(z) < 16) {
            Updraft pipe = updrafts.getFirst();
            double px = x + 0.5 - pipe.x(), pz = z + 0.5 - pipe.z();
            if (px * px + pz * pz < 3.2 * 3.2) {
                return p.blueIce;
            }
            return g2 < 49 ? p.boneY : p.landing;
        }

        // The heart: crystal shell, glowing core, two ventricle doors.
        double he = heartE(x, y, z);
        if (he < 1.0) {
            double dx = x - heartX, dy = y - heartY, dz = z - heartZ;
            double core = dx * dx + dy * dy + dz * dz;
            if (core <= 2.2 * 2.2) {
                return core <= 1 ? p.shroomlight : p.redstoneBlock;
            }
            boolean door = (dy < -6 && dy > -13 && Math.abs(dz) < 3.2 && dx > 7)
                    || (dy < -6 && dy > -13 && Math.abs(dz) < 3.2 && dx < -7);
            if (he < 0.62 || door) {
                code[0] = AIR_HEART;
                return p.cave;
            }
            double n = detail.noise(x / 5.0, y / 5.0, z / 5.0);
            if (n > 0.45) {
                return p.redGlass;
            }
            if (n < -0.5) {
                return p.redstoneBlock;
            }
            return hash(x, y, z, 7) < 0.06 ? p.cryingObsidian : p.amethyst;
        }
        // Arteries: the aorta is a walkable red tube to the belly, the others are solid.
        double ao = polyline(x, y, z, aorta);
        if (ao < 4.4 && he > 0.9) {
            if (ao < 3.0) {
                code[0] = AIR_OTHER;
                return p.cave;
            }
            return hash(x, y, z, 3) < 0.35 ? p.wart : p.redTerracotta;
        }
        if (segment(x, y, z, upA, upB) < 3.2 || segment(x, y, z, downA, downB) < 3.0) {
            return hash(x, y, z, 4) < 0.4 ? p.wart : p.redTerracotta;
        }

        // Bone: ribs, sternum ridge, vertebra rings, gullet ridges.
        if (rib(x, y, z)) {
            return hash(x, y, z, 11) < 0.025 ? p.shroomlight : p.boneY;
        }
        if (sternum(x, y, z)) {
            return p.boneX;
        }
        if (vertebra(x, y, z)) {
            return p.boneX;
        }
        if (gulletRidge(x, y, z)) {
            return p.boneY;
        }

        // Spine Road furniture.
        if (x >= SP_X0 && x <= SP_X1 && Math.abs(z) <= 4) {
            if (y == SP_FLOOR - 1 && (y - SP_Y) * (y - SP_Y) + z * z < 6.5 * 6.5 && !inPlungeHole(x, 160, z)) {
                return p.roadFloor;
            }
            if (y == SP_FLOOR && z == 0 && x > SP_X0 + 4 && x < SP_X1 - 4 && !inPlungeHole(x, 160, z)) {
                code[0] = AIR_SPINE;
                return p.rail;
            }
            if (y == 190 && z == 2 && Math.floorMod(x, 32) == 0) {
                return p.lantern;
            }
        }
        // Ladders in the marrow shafts.
        for (double[] s : shafts) {
            if (x == (int) Math.floor(s[0]) + 2 && z == (int) Math.floor(s[1]) && y <= s[2] && y >= s[3] + 2) {
                return p.ladderWest;
            }
        }
        // Updraft markers: a blue-ice disc where a breath column meets the floor.
        for (Updraft u : updrafts) {
            double dx = x + 0.5 - u.x(), dz = z + 0.5 - u.z();
            if (y == u.bottom() - 1 && dx * dx + dz * dz < 3.2 * 3.2) {
                return p.blueIce;
            }
        }
        // The sealed tail coil.
        if (ell(x, y, z, 340, 36, -16, 4.6, 4.6, 4.6) < 1) {
            return ell(x, y, z, 340, 36, -16, 2.2, 2.2, 2.2) < 1 ? p.cryingObsidian : p.sealBricks;
        }
        // Swallowed isles rise from the sea floor.
        if (inBelly(x, y, z) && isleRock(x, y, z)) {
            return isleBlock(x, y, z);
        }

        // Cavities.
        if (inGullet(x, y, z) || inUpdraft(x, y, z) || inShaft(x, y, z) || inDiaphragm(x, y, z) || inTail(x, y, z)
                || inPlungeHole(x, y, z) || inBronchi(x, y, z)) {
            code[0] = AIR_OTHER;
            return p.cave;
        }
        if (inSpine(x, y, z)) {
            code[0] = AIR_SPINE;
            return p.cave;
        }
        if (inRibvault(x, y, z)) {
            code[0] = AIR_RIB;
            return p.cave;
        }
        if (inBelly(x, y, z)) {
            if (y <= SEA) {
                if (y < seaFloor(x, z)) {
                    return y > seaFloor(x, z) - 3 ? (hash(x, y, z, 5) < 0.5 ? p.mud : p.clay) : p.tuff;
                }
                code[0] = WATER;
                return p.water;
            }
            code[0] = AIR_SEA;
            return p.cave;
        }
        if (inLungs(x, y, z)) {
            code[0] = AIR_LUNG;
            return p.cave;
        }
        if (marrowCave(x, y, z)) {
            if (y <= MW_BOTTOM + 4) {
                code[0] = LAVA;
                return p.lava;
            }
            code[0] = AIR_MARROW;
            return p.cave;
        }
        if (inNoodle(x, y, z) && !inMarrowBand(x, y + 3, z)) {
            code[0] = AIR_OTHER;
            return p.cave;
        }
        if (geode(x, y, z, code)) {
            return code[0] == AIR_OTHER ? p.cave : (code[1] == 1 ? p.calcite : (hash(x, y, z, 9) < 0.08 ? p.budding : p.amethyst));
        }
        return rockWithOre(x, y, z);
    }

    private BlockData isleBlock(int x, int y, int z) {
        double n = detail.noise(x / 7.0, y / 7.0, z / 7.0);
        if (y >= SEA + 3 && n > -0.3) {
            return p.moss;
        }
        return n > 0.2 ? p.tuff : p.terracotta;
    }

    /** Amethyst geodes in the Ribvault rock: one per 24-block cell at most. code[1] = 1 for the calcite rind. */
    private boolean geode(int x, int y, int z, int[] code) {
        if (y < 50 || y > 150 || x < -310 || x > -100) {
            return false;
        }
        int cx = Math.floorDiv(x, 24), cy = Math.floorDiv(y, 24), cz = Math.floorDiv(z, 24);
        if (hash(cx, cy, cz, 21) > 0.22) {
            return false;
        }
        double gx = cx * 24 + 4 + hash(cx, cy, cz, 22) * 16;
        double gy = cy * 24 + 4 + hash(cx, cy, cz, 23) * 16;
        double gz = cz * 24 + 4 + hash(cx, cy, cz, 24) * 16;
        double d = Math.sqrt((x - gx) * (x - gx) + (y - gy) * (y - gy) + (z - gz) * (z - gz));
        if (d > 4.3) {
            return false;
        }
        if (d < 2.4) {
            code[0] = AIR_OTHER;
        } else {
            code[1] = d > 3.5 ? 1 : 0;
        }
        return true;
    }

    // ------------------------------------------------------------------ rock and veins

    Zone rockZone(double x, double y, double z) {
        if (y <= MW_BONE && x > -320 && x < 250 && Math.abs(z) < 108) {
            return Zone.MARROW;
        }
        double hx = x - heartX, hy = y - heartY, hz = z - heartZ;
        if (hx * hx + hy * hy + hz * hz < 32 * 32) {
            return Zone.HEART;
        }
        if (lungE(x, y, z) < 1.6) {
            return Zone.LUNGS;
        }
        if (y > 172 && Math.abs(z) < 16) {
            return Zone.SPINE;
        }
        if (ell(x, y, z, BL_X, BL_Y, 0, BL_RX, BL_RY, BL_RZ) < 1.35) {
            return Zone.SEA;
        }
        if (ell(x, y, z, RV_X, RV_Y, 0, RV_RX, RV_RY, RV_RZ) < 1.35) {
            return Zone.RIBVAULT;
        }
        return Zone.ROCK;
    }

    private BlockData rockWithOre(int x, int y, int z) {
        Zone zone = rockZone(x, y, z);
        BlockData rock = rock(x, y, z, zone);
        double o = ore.noise(x / 6.5, y / 6.5, z / 6.5);
        if (o < 0.70) {
            return rock;
        }
        boolean deep = rock == p.deepslate || rock == p.tuff;
        int cx = x >> 2, cy = y >> 2, cz = z >> 2;
        double pick = hash(cx, cy, cz, 31);
        return switch (zone) {
            case MARROW -> pick < 0.04 ? p.debris
                    : rock == p.blackstone ? (pick < 0.3 ? p.gilded : rock)
                    : (pick < 0.55 ? p.quartz : p.netherGold);
            case HEART -> pick < 0.65 ? ore(p.redstone, p.redstoneD, deep)
                    : pick < 0.75 ? ore(p.diamond, p.diamondD, deep) : ore(p.iron, p.ironD, deep);
            case LUNGS -> pick < 0.5 ? ore(p.lapis, p.lapisD, deep)
                    : pick < 0.62 ? ore(p.diamond, p.diamondD, deep)
                    : pick < 0.85 ? ore(p.iron, p.ironD, deep) : ore(p.coal, p.coalD, deep);
            case SPINE -> pick < 0.45 ? ore(p.gold, p.goldD, deep)
                    : pick < 0.8 ? ore(p.redstone, p.redstoneD, deep) : ore(p.iron, p.ironD, deep);
            case SEA -> pick < 0.45 ? ore(p.copper, p.copperD, deep)
                    : pick < 0.55 ? ore(p.emerald, p.emeraldD, deep)
                    : pick < 0.75 ? ore(p.gold, p.goldD, deep) : ore(p.coal, p.coalD, deep);
            case RIBVAULT -> pick < 0.35 ? ore(p.coal, p.coalD, deep)
                    : pick < 0.8 ? ore(p.iron, p.ironD, deep)
                    : pick < 0.9 ? ore(p.lapis, p.lapisD, deep) : ore(p.gold, p.goldD, deep);
            default -> pick < 0.4 ? ore(p.coal, p.coalD, deep)
                    : pick < 0.7 ? ore(p.iron, p.ironD, deep) : ore(p.copper, p.copperD, deep);
        };
    }

    private static BlockData ore(BlockData stone, BlockData deepslate, boolean deep) {
        return deep ? deepslate : stone;
    }

    private BlockData rock(int x, int y, int z, Zone zone) {
        double s = strata.noise(x / 48.0, y / 13.0, z / 48.0);
        double d = detail.noise(x / 6.0, y / 6.0, z / 6.0);
        switch (zone) {
            case MARROW -> {
                if (y > MW_TOP + 1) {
                    return d > 0.55 ? p.calcite : p.boneY;
                }
                return s > 0.25 ? p.blackstone : (d > 0.62 ? p.basalt : p.netherrack);
            }
            case LUNGS -> {
                return d > 0.55 ? p.blueIce : d > 0.15 ? p.packedIce : d > -0.35 ? p.snow : p.calcite;
            }
            case HEART -> {
                return d > 0.45 ? p.wart : s > 0.0 ? p.redTerracotta : p.terracotta;
            }
            case SPINE -> {
                return d > 0.5 ? p.boneY : s > -0.1 ? p.calcite : p.tuff;
            }
            default -> {
            }
        }
        if (y < 40) {
            return s > 0.1 ? p.deepslate : p.tuff;
        }
        if (s < -0.4) {
            return p.tuff;
        }
        if (s < -0.12) {
            return p.brownTerracotta;
        }
        if (s < 0.18) {
            return d > 0.6 ? p.dripstone : p.terracotta;
        }
        if (s < 0.42) {
            return p.redTerracotta;
        }
        return d > 0.2 ? p.pinkTerracotta : p.terracotta;
    }

    // ------------------------------------------------------------------ gameplay lookups

    /** Which part of the titan a location is in (heartbeat, updrafts, gates). */
    Zone zoneAt(double x, double y, double z) {
        if (heartE(x, y, z) < 1.6) {
            return Zone.HEART;
        }
        if (inGullet(x, y, z) || Math.abs(x - TH_X) < 18 && Math.abs(z) < 16 && y < 100) {
            return Zone.GULLET;
        }
        if (inSpine(x, y, z)) {
            return Zone.SPINE;
        }
        if (y <= MW_BONE && inMarrowBand(x, Math.min(y, MW_TOP), z)) {
            return Zone.MARROW;
        }
        if (lungE(x, y, z) < 1.05) {
            return Zone.LUNGS;
        }
        if (ell(x, y, z, BL_X, BL_Y, 0, BL_RX, BL_RY, BL_RZ) < 1.05) {
            return Zone.SEA;
        }
        if (ell(x, y, z, RV_X, RV_Y, 0, RV_RX, RV_RY, RV_RZ) < 1.05) {
            return Zone.RIBVAULT;
        }
        if (x > 220) {
            return Zone.TAIL;
        }
        return Zone.ROCK;
    }

    /** Blocks no player breaks: the heart and the climbing stone. */
    boolean guarded(int x, int y, int z) {
        if (x == -294 && z == 0 && y == LANDING_FLOOR) {
            return true;
        }
        return heartE(x, y, z) < 1.0;
    }

    /** Where a swallowed player appears: high in the gullet, falling into the pool. */
    double[] arrival() {
        return new double[]{GU_X + 0.5, 150, GU_Z + 0.5};
    }

    /** The climbing stone back to Jawcamp at the gullet landing. */
    int[] climbStone() {
        return new int[]{-294, LANDING_FLOOR, 0};
    }
}
