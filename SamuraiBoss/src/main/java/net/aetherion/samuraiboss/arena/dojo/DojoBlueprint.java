package net.aetherion.samuraiboss.arena.dojo;

import net.aetherion.samuraiboss.util.MathUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Der prozedurale Bauplan des Tempelhofs „千年桜道場" (Dojo der Tausendjährigen Kirsche).
 *
 * <p>Koordinaten relativ zum Ursprung (Mitte des Hofes, Laufhöhe y=0, Boden bei y=-1).
 * Norden = -Z (Haupthalle), Süden = +Z (Tor & Torii), Westen = -X (Kirschbaum),
 * Osten = +X (Teich, Glockenturm).</p>
 *
 * <p>Der Plan ist deterministisch (fester Zufalls-Seed): Das Plugin kann ihn jederzeit neu
 * erzeugen und weiß damit, welcher Block zu welchem Bauteil gehört – ohne Extra-Datei.</p>
 */
public final class DojoBlueprint {

    public static final int WALL_X = 25;
    public static final int WALL_N = -32;
    public static final int WALL_S = 24;
    public static final int GATE_HALF = 3;

    private final BuildPlan plan;
    private final Random rnd = new Random(1000L);

    private DojoBlueprint() {
        plan = new BuildPlan(-27, -6, -35, 27, 27, 27,
                -WALL_X + 1, WALL_N + 1, WALL_X - 1, WALL_S - 1, 0, 27);
    }

    public static BuildPlan generate() {
        DojoBlueprint b = new DojoBlueprint();
        b.build();
        return b.plan;
    }

    private void build() {
        foundation();
        ground();
        pond();
        outerWall();
        gate();
        mainHall();
        torii();
        bellTower();
        lanterns();
        tree();
        treeFence();
        bridge();
        nobori();
        garden();
        points();
    }

    // =====================================================================================
    // Helfer

    private void set(int x, int y, int z, String b, El e) {
        plan.set(x, y, z, b, e);
    }

    private static String stairs(String block, String facing, String half) {
        return block + "[facing=" + facing + ",half=" + half + "]";
    }

    private static String slab(String block, String type) {
        return block + "[type=" + type + "]";
    }

    private static String log(String block, char axis) {
        return block + "[axis=" + axis + "]";
    }

    private static String opposite(String f) {
        return switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    // =====================================================================================
    // Fundament & Boden

    /** Steinsockel unter dem ganzen Tempel – so schwebt er auch auf unebenem Gelände nicht. */
    private void foundation() {
        for (int x = -26; x <= 26; x++) {
            for (int z = -33; z <= 25; z++) {
                boolean edge = x == -26 || x == 26 || z == -33 || z == 25;
                for (int y = -5; y <= -2; y++) {
                    String b;
                    if (edge) {
                        b = (y == -2) ? "stone_bricks" : (rnd.nextFloat() < 0.25f ? "mossy_stone_bricks"
                                : rnd.nextFloat() < 0.15f ? "cracked_stone_bricks" : "stone_bricks");
                    } else {
                        b = "stone";
                    }
                    set(x, y, z, b, El.FOUNDATION);
                }
            }
        }
    }

    private boolean onWallLine(int x, int z) {
        return (Math.abs(x) == WALL_X && z >= WALL_N && z <= WALL_S) || ((z == WALL_N || z == WALL_S) && Math.abs(x) <= WALL_X);
    }

    /** Laufschicht y=-1: gerechter Zen-Kies, Wege, Moos. */
    private void ground() {
        float[][] rocks = {{-8.5f, 15.5f}, {10.5f, 17.5f}};
        for (int x = -26; x <= 26; x++) {
            for (int z = -33; z <= 25; z++) {
                String b;
                El el = El.GROUND;
                if (Math.abs(x) >= WALL_X || z <= WALL_N || z >= WALL_S) {
                    b = rnd.nextFloat() < 0.2f ? "mossy_stone_bricks" : "stone_bricks";
                } else {
                    // Rechen-Linien (Ost-West) oder Kreis-Wellen um die Steininseln
                    b = ((z + 99) % 3 == 0) ? "light_gray_concrete_powder" : "white_concrete_powder";
                    for (float[] r : rocks) {
                        double d = Math.hypot(x + 0.5 - r[0], z + 0.5 - r[1]);
                        if (d < 4.6) b = (Math.round(d) % 2 == 0) ? "light_gray_concrete_powder" : "white_concrete_powder";
                    }
                    // Randstreifen an den Mauern: Moos
                    if (Math.abs(x) >= WALL_X - 2 || z <= WALL_N + 2) b = rnd.nextFloat() < 0.8f ? "moss_block" : "rooted_dirt";
                }
                set(x, -1, z, b, el);
            }
        }
        // Steininseln (Karesansui-Felsen)
        for (float[] r : rocks) {
            int cx = (int) Math.floor(r[0]), cz = (int) Math.floor(r[1]);
            set(cx, -1, cz, "mossy_cobblestone", El.GROUND);
            set(cx, 0, cz, "mossy_cobblestone", El.GARDEN);
            set(cx + 1, 0, cz, "cobblestone", El.GARDEN);
            set(cx, 0, cz + 1, "andesite", El.GARDEN);
            set(cx, 1, cz, "andesite_slab[type=bottom]", El.GARDEN);
            set(cx + 1, -1, cz, "moss_block", El.GROUND);
            set(cx - 1, -1, cz, "moss_block", El.GROUND);
            set(cx, -1, cz - 1, "moss_block", El.GROUND);
            set(cx + 1, 0, cz + 1, "moss_carpet", El.GARDEN);
        }
        // Hauptweg vom Tor zur Halle: große Steinplatten
        for (int z = -13; z <= WALL_S + 1; z++) {
            for (int x = -2; x <= 2; x++) {
                String b = Math.abs(x) == 2 ? "stone_bricks"
                        : (Math.floorMod(Math.floorDiv(z, 3), 2) == 0 ? "smooth_stone" : "polished_andesite");
                set(x, -1, z, b, El.PATH);
            }
        }
        // Duell-Kreis in der Hofmitte mit eingelegter Kirschblüte
        float cx = 0.5f, cz = 3.5f;
        for (int x = -5; x <= 5; x++) {
            for (int z = -2; z <= 9; z++) {
                double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (d < 4.3) {
                    String b = d < 1.0 ? "chiseled_stone_bricks" : d < 3.4 ? "smooth_stone" : "polished_andesite";
                    set(x, -1, z, b, El.PATH);
                }
            }
        }
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(90 + i * 72);
            int px = (int) Math.floor(cx + Math.cos(a) * 2.2), pz = (int) Math.floor(cz - Math.sin(a) * 2.2);
            set(px, -1, pz, "pink_terracotta", El.PATH);
        }
        // Trittsteine zum Baum (West) und zur Brücke (Ost)
        for (int x = -12; x <= -4; x += 2) set(x, -1, 3 + (x % 4 == 0 ? 1 : 0), "smooth_stone", El.PATH);
        for (int x = 5; x <= 9; x += 2) set(x, -1, 3, "smooth_stone", El.PATH);
        // Weg zum Glockenturm
        for (int x = 3; x <= 15; x += 2) set(x, -1, 18, "smooth_stone", El.PATH);
        // Kiesstreifen um die Plattform der Halle
        for (int x = -14; x <= 14; x++) {
            set(x, -1, -13, "gravel", El.GROUND);
            if (Math.abs(x) <= 2) set(x, -1, -13, "stone_bricks", El.PATH);
        }
    }

    // =====================================================================================
    // Teich (Osten)

    private static final float POND_X = 14.5f, POND_Z = 0.5f, POND_RX = 6.6f, POND_RZ = 6.2f;

    private double pondD(double x, double z) {
        double dx = (x - POND_X) / POND_RX, dz = (z - POND_Z) / POND_RZ;
        double ang = Math.atan2(dz, dx);
        double wobble = 1 + 0.08 * Math.sin(ang * 3 + 1.3) + 0.05 * Math.sin(ang * 5);
        return Math.sqrt(dx * dx + dz * dz) / wobble;
    }

    private void pond() {
        for (int x = 6; x <= 23; x++) {
            for (int z = -8; z <= 9; z++) {
                double d = pondD(x + 0.5, z + 0.5);
                if (d < 1.0) {
                    set(x, -1, z, "water[level=0]", El.POND);
                    set(x, -2, z, "water[level=0]", El.POND);
                    set(x, -3, z, rnd.nextFloat() < 0.5f ? "mud" : "gravel", El.POND);
                    if (rnd.nextFloat() < 0.07f && d < 0.85) set(x, 0, z, "lily_pad", El.POND);
                } else if (d < 1.22) {
                    float r = rnd.nextFloat();
                    String b = r < 0.35f ? "mossy_cobblestone" : r < 0.6f ? "andesite" : r < 0.8f ? "stone" : "moss_block";
                    set(x, -1, z, b, El.POND);
                    set(x, -2, z, "stone", El.POND);
                    if (rnd.nextFloat() < 0.22f && d < 1.15) {
                        set(x, 0, z, rnd.nextBoolean() ? "mossy_cobblestone" : "andesite", El.GARDEN);
                    }
                }
            }
        }
    }

    // =====================================================================================
    // Umfassungsmauer (Tsuijibei) mit Ziegeldach

    private void outerWall() {
        for (int x = -WALL_X; x <= WALL_X; x++) {
            for (int z = WALL_N; z <= WALL_S; z++) {
                if (!onWallLine(x, z)) continue;
                if (z == WALL_S && Math.abs(x) <= GATE_HALF) continue; // Tor-Öffnung
                boolean post = (Math.abs(x) == WALL_X ? Math.floorMod(z, 6) == 0 : Math.floorMod(x, 6) == 0)
                        || (Math.abs(x) == WALL_X && (z == WALL_N || z == WALL_S));
                set(x, 0, z, "stone_bricks", El.WALL);
                for (int y = 1; y <= 2; y++) {
                    set(x, y, z, post ? log("stripped_dark_oak_log", 'y') : "calcite", El.WALL);
                }
                set(x, 3, z, "dark_oak_planks", El.WALL);
                set(x, 4, z, "deepslate_tiles", El.WALL_CAP);
                set(x, 5, z, slab("deepslate_tile_slab", "bottom"), El.WALL_CAP);
            }
        }
        // Dachziegel-Stufen beidseitig
        for (int x = -WALL_X - 1; x <= WALL_X + 1; x++) {
            for (int z = WALL_N - 1; z <= WALL_S + 1; z++) {
                if (plan.get(x, 4, z) != null) continue;
                String facing = null;
                if (isWallCap(x + 1, z)) facing = "east";
                else if (isWallCap(x - 1, z)) facing = "west";
                else if (isWallCap(x, z + 1)) facing = "south";
                else if (isWallCap(x, z - 1)) facing = "north";
                if (facing != null) {
                    if (Math.abs(x) <= WALL_X + 1 && z >= WALL_N - 1 && z <= WALL_S + 1) {
                        set(x, 4, z, stairs("deepslate_tile_stairs", facing, "bottom"), El.WALL_CAP);
                        set(x, 3, z, slab("dark_oak_slab", "top"), El.WALL_CAP);
                    }
                }
            }
        }
    }

    private boolean isWallCap(int x, int z) {
        BuildPlan.Placement p = plan.get(x, 4, z);
        return p != null && p.block().equals("minecraft:deepslate_tiles") && p.element() == El.WALL_CAP;
    }

    /** Torpfosten (höher als die Mauer) beiderseits der Öffnung. */
    private void gate() {
        for (int sx = -1; sx <= 1; sx += 2) {
            int x = sx * (GATE_HALF + 1);
            for (int y = 0; y <= 5; y++) set(x, y, WALL_S, log("stripped_dark_oak_log", 'y'), El.GATE);
            set(x, 6, WALL_S, "polished_blackstone_bricks", El.GATE);
            set(x, 7, WALL_S, slab("polished_blackstone_slab", "bottom"), El.GATE);
            // Laterne am Torpfosten
            set(x - sx, 4, WALL_S - 1, "lantern[hanging=true]", El.LANTERN_TOP);
            set(x - sx, 5, WALL_S - 1, "chain[axis=y]", El.LANTERN_TOP);
        }
    }

    // =====================================================================================
    // Haupthalle mit geschwungenem Irimoya-Dach

    private static final int HALL_X0 = -10, HALL_X1 = 10, HALL_Z0 = -26, HALL_Z1 = -18;

    private void mainHall() {
        // Steinplattform (2 hoch), Holzboden oben
        for (int x = -13; x <= 13; x++) {
            for (int z = -30; z <= -14; z++) {
                set(x, 0, z, "stone_bricks", El.HALL_PLATFORM);
                boolean rim = x == -13 || x == 13 || z == -30 || z == -14;
                set(x, 1, z, rim ? "stone_bricks" : "spruce_planks", rim ? El.HALL_PLATFORM : El.HALL_FLOOR);
            }
        }
        // Tatami im Inneren
        for (int x = HALL_X0 + 1; x <= HALL_X1 - 1; x++) {
            for (int z = HALL_Z0 + 1; z <= HALL_Z1 - 1; z++) {
                boolean border = x == HALL_X0 + 1 || x == HALL_X1 - 1 || z == HALL_Z0 + 1 || z == HALL_Z1 - 1;
                String b = border ? "dark_oak_planks"
                        : (Math.floorMod(Math.floorDiv(x + 20, 2) + z, 2) == 0 ? log("hay_block", 'x') : log("hay_block", 'z'));
                set(x, 1, z, b, El.HALL_FLOOR);
            }
        }
        // Treppe vorne (Mitte)
        for (int x = -3; x <= 3; x++) {
            set(x, 0, -13, stairs("stone_brick_stairs", "north", "bottom"), El.HALL_PLATFORM);
            set(x, 1, -14, stairs("stone_brick_stairs", "north", "bottom"), El.HALL_PLATFORM);
        }
        set(-4, 0, -13, "stone_bricks", El.HALL_PLATFORM);
        set(4, 0, -13, "stone_bricks", El.HALL_PLATFORM);

        // Wände & Pfeiler
        for (int x = HALL_X0; x <= HALL_X1; x++) {
            for (int z = HALL_Z0; z <= HALL_Z1; z++) {
                boolean onWall = x == HALL_X0 || x == HALL_X1 || z == HALL_Z0 || z == HALL_Z1;
                if (!onWall) continue;
                boolean pillar = (z == HALL_Z0 || z == HALL_Z1) ? Math.floorMod(x - HALL_X0, 4) == 0
                        : Math.floorMod(z - HALL_Z0, 4) == 0;
                if (x == HALL_X0 || x == HALL_X1) pillar |= (z == HALL_Z0 || z == HALL_Z1);
                if (pillar) {
                    for (int y = 2; y <= 7; y++) set(x, y, z, log("stripped_dark_oak_log", 'y'), El.HALL_PILLAR);
                    continue;
                }
                boolean front = z == HALL_Z1;
                if (front) {
                    if (Math.abs(x) <= 1) {
                        // offene Eingangsmitte
                        set(x, 7, z, "dark_oak_planks", El.HALL_WALL);
                        set(x, 6, z, slab("dark_oak_slab", "top"), El.HALL_WALL);
                        continue;
                    }
                    set(x, 2, z, slab("dark_oak_slab", "bottom"), El.HALL_SHOJI);
                    set(x, 3, z, "white_wool", El.HALL_SHOJI);
                    set(x, 4, z, (Math.floorMod(x, 2) == 0) ? "white_wool" : "white_wool", El.HALL_SHOJI);
                    set(x, 5, z, "dark_oak_planks", El.HALL_SHOJI);
                    set(x, 6, z, "white_wool", El.HALL_SHOJI);
                    set(x, 7, z, "dark_oak_planks", El.HALL_WALL);
                } else {
                    set(x, 2, z, "dark_oak_planks", El.HALL_WALL);
                    for (int y = 3; y <= 5; y++) set(x, y, z, "calcite", El.HALL_WALL);
                    set(x, 6, z, "dark_oak_planks", El.HALL_WALL);
                    set(x, 7, z, "dark_oak_planks", El.HALL_WALL);
                }
            }
        }
        // Engawa-Pfosten an den Ecken der Veranda (tragen das Vordach)
        for (int x : new int[]{-12, -6, 6, 12}) {
            for (int y = 2; y <= 7; y++) set(x, y, -15, log("stripped_dark_oak_log", 'y'), El.HALL_PILLAR);
            set(x, 7, -16, "dark_oak_planks", El.HALL_WALL);
            set(x, 7, -17, "dark_oak_planks", El.HALL_WALL);
        }
        for (int x = -12; x <= 12; x++) set(x, 8, -15, "dark_oak_planks", El.HALL_WALL);
        // Hängelaternen unter dem Vordach
        for (int x : new int[]{-9, -3, 3, 9}) {
            set(x, 7, -16, "chain[axis=y]", El.HALL_INTERIOR);
            set(x, 6, -16, "lantern[hanging=true]", El.HALL_INTERIOR);
        }
        hallInterior();
        roof(-13, 13, -30, -14, 8, 0.5f, 0.075f, true, El.HALL_ROOF);
    }

    private void hallInterior() {
        // Kamidana (Hausschrein-Regal) an der Rückwand
        for (int x = -3; x <= 3; x++) set(x, 5, HALL_Z0 + 1, slab("dark_oak_slab", "top"), El.HALL_INTERIOR);
        set(-1, 6, HALL_Z0 + 1, "stripped_cherry_wood[axis=y]", El.HALL_INTERIOR);
        set(0, 6, HALL_Z0 + 1, "stripped_cherry_wood[axis=y]", El.HALL_INTERIOR);
        set(1, 6, HALL_Z0 + 1, "stripped_cherry_wood[axis=y]", El.HALL_INTERIOR);
        set(0, 7, HALL_Z0 + 1, stairs("polished_blackstone_stairs", "north", "bottom"), El.HALL_INTERIOR);
        set(-1, 7, HALL_Z0 + 1, slab("polished_blackstone_slab", "bottom"), El.HALL_INTERIOR);
        set(1, 7, HALL_Z0 + 1, slab("polished_blackstone_slab", "bottom"), El.HALL_INTERIOR);
        set(-3, 6, HALL_Z0 + 1, "candle[candles=3,lit=true]", El.HALL_INTERIOR);
        set(3, 6, HALL_Z0 + 1, "candle[candles=3,lit=true]", El.HALL_INTERIOR);
        // Taiko-Trommel (Fass auf der Seite) auf Gestell
        set(7, 2, -23, "dark_oak_fence", El.HALL_INTERIOR);
        set(7, 3, -23, "barrel[facing=south]", El.HALL_INTERIOR);
        set(-7, 2, -23, "dark_oak_fence", El.HALL_INTERIOR);
        set(-7, 3, -23, "barrel[facing=south]", El.HALL_INTERIOR);
        // Bodenlaternen innen
        set(-8, 2, -20, "lantern[hanging=false]", El.HALL_INTERIOR);
        set(8, 2, -20, "lantern[hanging=false]", El.HALL_INTERIOR);
    }

    /**
     * Geschwungenes Dach als Höhenfeld: flach an der Traufe, steil zum First (konkav),
     * angehobene Ecken (Sori). gable=true -> Irimoya: oben Giebel, unten Walm.
     */
    private void roof(int x0, int x1, int z0, int z1, int eaveY, float a, float b, boolean gable, El el) {
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        double[][] h = new double[w][d];
        float gableInset = 2.0f;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                double dx = Math.min(i, w - 1 - i);
                double dz = Math.min(j, d - 1 - j);
                double hip = curve(Math.min(dx, dz), a, b);
                double hh;
                if (gable && dx >= gableInset) hh = curve(dz, a, b);
                else hh = hip;
                double lift = Math.max(0, 1.3 - 0.45 * (dx + dz));
                h[i][j] = eaveY + hh + lift;
            }
        }
        double top = 0;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                double s = Math.round(h[i][j] * 2) / 2.0;
                top = Math.max(top, s);
                int x = x0 + i, z = z0 + j;
                boolean edge = i == 0 || j == 0 || i == w - 1 || j == d - 1;
                int fy = (int) Math.floor(s);
                boolean half = s - fy >= 0.49;
                // Unterkante: so tief wie der tiefste Nachbar (dichtes Dach ohne Löcher)
                double minN = s;
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ni = i + o[0], nj = j + o[1];
                    if (ni < 0 || nj < 0 || ni >= w || nj >= d) continue;
                    minN = Math.min(minN, Math.round(h[ni][nj] * 2) / 2.0);
                }
                int bottomY = (int) Math.floor(minN) - (edge ? 0 : 1);
                if (half) {
                    set(x, fy, z, edge ? slab("deepslate_tile_slab", "top") : slab("deepslate_tile_slab", "bottom"), el);
                    for (int y = fy - 1; y >= bottomY && y >= eaveY - 1; y--) set(x, y, z, "deepslate_tiles", el);
                } else {
                    if (edge) set(x, fy - 1, z, slab("deepslate_tile_slab", "top"), el);
                    else set(x, fy - 1, z, "deepslate_tiles", el);
                    for (int y = fy - 2; y >= bottomY && y >= eaveY - 1; y--) set(x, y, z, "deepslate_tiles", el);
                }
            }
        }
        if (!gable) {
            // Spitze mit Zierknauf
            int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
            int ty = (int) Math.floor(top);
            set(cx, ty, cz, "chiseled_polished_blackstone", el);
            set(cx, ty + 1, cz, "polished_blackstone_wall", el);
            set(cx, ty + 2, cz, "gold_block", el);
            return;
        }
        // Giebeldreiecke (Tsuma) und First
        int gi0 = (int) gableInset, gi1 = w - 1 - (int) gableInset;
        for (int gi : new int[]{gi0, gi1}) {
            int x = x0 + gi;
            for (int j = 1; j < d - 1; j++) {
                int z = z0 + j;
                double low = h[(int) gableInset - 1][j];
                double high = h[gi][j];
                for (int y = (int) Math.floor(low) + 1; y < (int) Math.floor(Math.round(high * 2) / 2.0) - 1; y++) {
                    boolean lattice = Math.floorMod(z, 2) == 0;
                    set(x, y, z, lattice ? "dark_oak_planks" : "calcite", el);
                }
            }
        }
        // First (Muna) über die ganze Giebelbreite
        int ridgeJ = d / 2;
        int ridgeZ = z0 + ridgeJ;
        for (int i = gi0; i <= gi1; i++) {
            int x = x0 + i;
            double s = Math.round(h[i][ridgeJ] * 2) / 2.0;
            int y = (int) Math.ceil(s);
            set(x, y, ridgeZ, "polished_blackstone_bricks", el);
            if (d % 2 == 0) set(x, y, ridgeZ - 1, "polished_blackstone_bricks", el);
            if (i == gi0 || i == gi1) {
                set(x, y + 1, ridgeZ, "chiseled_polished_blackstone", el);
                set(x, y + 2, ridgeZ, slab("polished_blackstone_slab", "bottom"), el);
            } else {
                set(x, y + 1, ridgeZ, slab("polished_blackstone_brick_slab", "bottom"), el);
            }
        }
        plan.point("ridge", x0 + gi0 + 0.5f, (float) Math.ceil(Math.round(h[gi0][ridgeJ] * 2) / 2.0) + 2f, ridgeZ + 0.5f);
        plan.point("ridge2", x0 + gi1 + 0.5f, (float) Math.ceil(Math.round(h[gi1][ridgeJ] * 2) / 2.0) + 2f, ridgeZ + 0.5f);
    }

    private static double curve(double u, float a, float b) {
        return a * u + b * u * u;
    }

    // =====================================================================================
    // Torii (Süden, innen am Tor)

    private void torii() {
        int z = 20;
        for (int sx = -1; sx <= 1; sx += 2) {
            int x = sx * 4;
            set(x, 0, z, "black_concrete", El.TORII);
            for (int y = 1; y <= 7; y++) set(x, y, z, "red_concrete", El.TORII);
        }
        // Nuki (Querbalken), Gakuzuka (Mittelstrebe), Shimaki + Kasagi (oberer Balken, schwarz, Enden nach oben)
        for (int x = -6; x <= 6; x++) set(x, 5, z, "red_concrete", El.TORII_TOP);
        set(0, 6, z, "red_concrete", El.TORII_TOP);
        for (int x = -6; x <= 6; x++) set(x, 7, z, "red_concrete", El.TORII_TOP);
        for (int x = -6; x <= 6; x++) set(x, 8, z, "black_concrete", El.TORII_TOP);
        for (int sx = -1; sx <= 1; sx += 2) {
            set(sx * 7, 8, z, slab("polished_blackstone_slab", "top"), El.TORII_TOP);
            set(sx * 8, 9, z, slab("polished_blackstone_slab", "bottom"), El.TORII_TOP);
            set(sx * 7, 9, z, slab("polished_blackstone_slab", "bottom"), El.TORII_TOP);
        }
        // Plakette mit Gold-Schrift
        plan.addDeco(DecoSpec.block("torii_plaque", El.TORII_TOP, 0.5, 6.0, z + 0.5, "minecraft:black_concrete")
                .box(new Vector3f(0, 0.5f, 0.56f), new Quaternionf(), new Vector3f(0.9f, 1.25f, 0.08f)).build());
        plan.addDeco(DecoSpec.text("torii_text", El.TORII_TOP, 0.5, 6.0, z + 0.5, "<#f5c542>千年桜</#f5c542>")
                .at(new Vector3f(0, 0.06f, 0.61f), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f)).bright().build());
        plan.addDeco(DecoSpec.text("torii_text_b", El.TORII_TOP, 0.5, 6.0, z + 0.5, "<#f5c542>千年桜</#f5c542>")
                .at(new Vector3f(0, 0.06f, 0.39f), new Quaternionf().rotationY((float) Math.PI), new Vector3f(1.0f, 1.0f, 1.0f)).bright().build());
    }

    // =====================================================================================
    // Glockenturm (Shōrō) mit Bonshō

    private void bellTower() {
        int x0 = 16, x1 = 20, z0 = 12, z1 = 16;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) set(x, 0, z, "stone_bricks", El.BELL_TOWER);
        for (int x : new int[]{x0, x1})
            for (int z : new int[]{z0, z1})
                for (int y = 1; y <= 6; y++) set(x, y, z, log("stripped_dark_oak_log", 'y'), El.BELL_TOWER);
        for (int x = x0; x <= x1; x++) {
            set(x, 7, z0, log("dark_oak_log", 'x'), El.BELL_TOWER);
            set(x, 7, z1, log("dark_oak_log", 'x'), El.BELL_TOWER);
            set(x, 7, 14, log("dark_oak_log", 'x'), El.BELL_TOWER);
        }
        for (int z = z0; z <= z1; z++) {
            set(x0, 7, z, log("dark_oak_log", 'z'), El.BELL_TOWER);
            set(x1, 7, z, log("dark_oak_log", 'z'), El.BELL_TOWER);
        }
        roof(x0 - 1, x1 + 1, z0 - 1, z1 + 1, 8, 0.62f, 0.11f, false, El.BELL_TOWER);

        // Glocke als Display-Deko (achteckiger Bronzekörper)
        double bx = 18.5, by = 3.2, bz = 14.5;
        String bronze = "minecraft:oxidized_copper";
        for (int i = 0; i < 2; i++) {
            Quaternionf r = new Quaternionf().rotationY((float) Math.toRadians(i * 45));
            plan.addDeco(DecoSpec.block("bell_body" + i, El.BELL_TOWER, bx, by, bz, bronze)
                    .box(new Vector3f(0, 1.2f, 0), r, new Vector3f(1.25f, 2.1f, 1.25f)).anim("bell", 0).build());
            plan.addDeco(DecoSpec.block("bell_lip" + i, El.BELL_TOWER, bx, by, bz, "minecraft:weathered_copper")
                    .box(new Vector3f(0, 0.2f, 0), r, new Vector3f(1.42f, 0.32f, 1.42f)).anim("bell", 0).build());
            plan.addDeco(DecoSpec.block("bell_band" + i, El.BELL_TOWER, bx, by, bz, "minecraft:weathered_copper")
                    .box(new Vector3f(0, 1.55f, 0), r, new Vector3f(1.32f, 0.16f, 1.32f)).anim("bell", 0).build());
        }
        plan.addDeco(DecoSpec.block("bell_top", El.BELL_TOWER, bx, by, bz, bronze)
                .box(new Vector3f(0, 2.4f, 0), new Quaternionf(), new Vector3f(0.85f, 0.35f, 0.85f)).anim("bell", 0).build());
        plan.addDeco(DecoSpec.block("bell_chain", El.BELL_TOWER, bx, by, bz, "minecraft:chain[axis=y]")
                .box(new Vector3f(0, 3.2f, 0), new Quaternionf(), new Vector3f(1f, 1.3f, 1f)).build());
        // Glockenklöppel (Shumoku) – ein hängender Balken, der gegen die Glocke schwingt
        plan.addDeco(DecoSpec.block("bell_striker", El.BELL_TOWER, bx + 1.9, by + 1.4, bz, "minecraft:stripped_spruce_log[axis=x]")
                .box(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.9f, 0.34f, 0.34f)).anim("striker", 0).build());
        plan.addDeco(DecoSpec.block("bell_striker_rope1", El.BELL_TOWER, bx + 1.9, by + 1.4, bz, "minecraft:chain[axis=y]")
                .box(new Vector3f(0.5f, 1.15f, 0), new Quaternionf(), new Vector3f(1f, 2.0f, 1f)).anim("striker", 0).build());
        plan.addDeco(DecoSpec.block("bell_striker_rope2", El.BELL_TOWER, bx + 1.9, by + 1.4, bz, "minecraft:chain[axis=y]")
                .box(new Vector3f(-0.5f, 1.15f, 0), new Quaternionf(), new Vector3f(1f, 2.0f, 1f)).anim("striker", 0).build());
        plan.addDeco(DecoSpec.interaction("bell_touch", El.BELL_TOWER, bx, by, bz, 1.6f, 2.6f).build());
        plan.point("bell", (float) bx, (float) by + 1.2f, (float) bz);
    }

    // =====================================================================================
    // Steinlaternen (Tōrō)

    private void lanterns() {
        int[] zs = {17, 11, -5, -10};
        for (int z : zs) {
            for (int sx = -1; sx <= 1; sx += 2) {
                int x = sx * 5;
                set(x, 0, z, "polished_andesite", El.LANTERN);
                set(x, 1, z, "andesite_wall", El.LANTERN);
                set(x, 2, z, "lantern[hanging=false]", El.LANTERN_TOP);
                set(x, 3, z, slab("andesite_slab", "bottom"), El.LANTERN_TOP);
            }
        }
        // Zwei große Laternen an der Hallentreppe
        for (int sx = -1; sx <= 1; sx += 2) bigLantern(sx * 7, -11);
        bigLantern(19, -26);
        bigLantern(-20, 19);
    }

    private void bigLantern(int cx, int cz) {
        set(cx, 0, cz, "stone_bricks", El.LANTERN);
        set(cx, 1, cz, "stone_brick_wall", El.LANTERN);
        set(cx, 2, cz, "stone_brick_wall", El.LANTERN);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) set(cx + dx, 3, cz + dz, slab("stone_brick_slab", "top"), El.LANTERN_TOP);
        set(cx, 4, cz, "shroomlight", El.LANTERN_TOP);
        for (int dx = -1; dx <= 1; dx += 2)
            for (int dz = -1; dz <= 1; dz += 2) set(cx + dx, 4, cz + dz, "stone_brick_wall", El.LANTERN_TOP);
        set(cx, 5, cz - 1, stairs("stone_brick_stairs", "south", "bottom"), El.LANTERN_TOP);
        set(cx, 5, cz + 1, stairs("stone_brick_stairs", "north", "bottom"), El.LANTERN_TOP);
        set(cx - 1, 5, cz, stairs("stone_brick_stairs", "east", "bottom"), El.LANTERN_TOP);
        set(cx + 1, 5, cz, stairs("stone_brick_stairs", "west", "bottom"), El.LANTERN_TOP);
        for (int dx = -1; dx <= 1; dx += 2)
            for (int dz = -1; dz <= 1; dz += 2) set(cx + dx, 5, cz + dz, slab("stone_brick_slab", "bottom"), El.LANTERN_TOP);
        set(cx, 5, cz, "stone_bricks", El.LANTERN_TOP);
        set(cx, 6, cz, "stone_brick_wall", El.LANTERN_TOP);
    }

    // =====================================================================================
    // Die Tausendjährige Kirsche (Shidarezakura – Hängekirsche)

    private static final float TREE_X = -14.5f, TREE_Z = 1.5f;
    private final List<float[]> branchEnds = new ArrayList<>();

    private void tree() {
        // Boden unter dem Baum: Moos, Wurzelerde, Blütenteppich
        for (int x = -24; x <= -5; x++) {
            for (int z = -9; z <= 12; z++) {
                double d = Math.hypot(x + 0.5 - TREE_X, z + 0.5 - TREE_Z);
                if (d > 8.5 + rnd.nextFloat() * 1.5) continue;
                if (plan.get(x, -1, z) != null && plan.get(x, -1, z).element() == El.PATH) continue;
                set(x, -1, z, d < 2.5 ? "rooted_dirt" : (rnd.nextFloat() < 0.75f ? "moss_block" : "coarse_dirt"), El.GROUND);
                if (d > 2.6 && rnd.nextFloat() < 0.45f) {
                    String facing = new String[]{"north", "south", "east", "west"}[rnd.nextInt(4)];
                    set(x, 0, z, "pink_petals[facing=" + facing + ",flower_amount=" + (1 + rnd.nextInt(4)) + "]", El.GARDEN);
                }
            }
        }
        // Stamm: dick, leicht geneigt, mit Wurzelansatz
        double leanX = 0.10, leanZ = 0.05;
        for (int y = 0; y <= 6; y++) {
            double r = y == 0 ? 2.3 : y <= 1 ? 1.9 : 1.6 - y * 0.06;
            double cx = TREE_X + leanX * y, cz = TREE_Z + leanZ * y;
            for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r + 1); x++) {
                for (int z = (int) Math.floor(cz - r - 1); z <= (int) Math.ceil(cz + r + 1); z++) {
                    double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                    if (d <= r) set(x, y, z, "cherry_wood[axis=y]", El.TREE_TRUNK);
                }
            }
        }
        // Wurzeln
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.4;
            for (int s = 2; s <= 4; s++) {
                int x = (int) Math.floor(TREE_X + Math.cos(a) * s), z = (int) Math.floor(TREE_Z + Math.sin(a) * s);
                if (s <= 3 || rnd.nextBoolean()) set(x, 0, z, "cherry_log[axis=" + (Math.abs(Math.cos(a)) > 0.7 ? "x" : "z") + "]", El.TREE_TRUNK);
            }
        }
        // Hauptäste: weit ausladend, leicht ansteigend
        double baseX = TREE_X + leanX * 6, baseZ = TREE_Z + leanZ * 6;
        double[][] dirs = {{1.0, 0.15}, {0.55, 0.85}, {-0.35, 0.9}, {-0.9, 0.1}, {-0.3, -0.95}, {0.6, -0.8}};
        for (int i = 0; i < dirs.length; i++) {
            double len = 4.0 + rnd.nextDouble() * 1.6;
            if (dirs[i][0] > 0.3) len += 0.8; // Richtung Hof etwas länger
            branch(baseX, 6.5, baseZ, dirs[i][0], dirs[i][1], len, 0.30 + rnd.nextDouble() * 0.12, 1);
        }
        // Krone: Blütenwolken um die Astenden + ein dichter Kern
        for (float[] e : branchEnds) blossomBlob(e[0], e[1], e[2], 2.5f + rnd.nextFloat() * 0.8f, 1.7f + rnd.nextFloat() * 0.5f);
        blossomBlob((float) baseX, 10.6f, (float) baseZ, 4.6f, 2.2f);
        blossomBlob((float) baseX + 1.5f, 12.2f, (float) baseZ - 0.5f, 3.2f, 1.6f);
        // Hängende Blütenzweige (Blöcke)
        int strands = 0;
        for (int x = -26; x <= -2; x++) {
            for (int z = -10; z <= 13; z++) {
                int lowest = -1;
                for (int y = 4; y <= 16; y++) {
                    BuildPlan.Placement p = plan.get(x, y, z);
                    if (p != null && p.element() == El.TREE_LEAVES) {
                        lowest = y;
                        break;
                    }
                }
                if (lowest < 0) continue;
                double d = Math.hypot(x + 0.5 - TREE_X, z + 0.5 - TREE_Z);
                if (d < 4 || rnd.nextFloat() > 0.34f) continue;
                int len = 1 + rnd.nextInt(3);
                for (int k = 1; k <= len && lowest - k >= 3; k++) {
                    plan.setIfEmpty(x, lowest - k, z, "cherry_leaves[persistent=true,distance=1]", El.TREE_LEAVES);
                }
                strands++;
            }
        }
        // Deko: weiche Blütenwolken (gedrehte, große Blätter-Displays) + wehende Hängezweige
        int clusters = 0;
        for (float[] e : branchEnds) {
            for (int k = 0; k < 2; k++) {
                float s = 1.7f + rnd.nextFloat() * 1.2f;
                Quaternionf r = MathUtil.euler(rnd.nextFloat() * 40 - 20, rnd.nextFloat() * 90, rnd.nextFloat() * 40 - 20);
                float ox = (rnd.nextFloat() - 0.5f) * 3.4f, oy = (rnd.nextFloat() - 0.2f) * 2f, oz = (rnd.nextFloat() - 0.5f) * 3.4f;
                if (e[0] + ox > -4.5f || e[0] + ox < -24f || e[2] + oz < -11f || e[2] + oz > 12f) continue;
                plan.addDeco(DecoSpec.block("blossom" + (clusters++), El.TREE_LEAVES, e[0] + ox, e[1] + oy, e[2] + oz,
                                "minecraft:cherry_leaves[persistent=true,distance=1]")
                        .box(new Vector3f(), r, new Vector3f(s, s * 0.8f, s)).anim("sway", rnd.nextFloat() * 6.28f).range(2f).build());
            }
        }
        for (int i = 0; i < 24; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double rr = 5.0 + rnd.nextDouble() * 3.2;
            float sx = (float) (TREE_X + Math.cos(a) * rr), sz = (float) (TREE_Z + Math.sin(a) * rr);
            if (sx > -4.5f || sx < -24.5f || sz < -11 || sz > 12.5f) continue;
            float topY = 8.4f + rnd.nextFloat() * 1.4f;
            float len = 2.6f + rnd.nextFloat() * 2.4f;
            plan.addDeco(DecoSpec.block("strand" + i, El.TREE_LEAVES, sx, topY, sz, "minecraft:cherry_leaves[persistent=true,distance=1]")
                    .box(new Vector3f(0, -len / 2f, 0), new Quaternionf(), new Vector3f(0.42f, len, 0.42f))
                    .anim("strand", (float) a).range(2f).build());
        }
        // Shimenawa (heiliges Seil) mit Shide-Papierstreifen um den Stamm
        int seg = 14;
        double ry = 2.4, rad = 1.95;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg;
            float px = (float) (TREE_X + 0.25 + Math.cos(a0) * rad), pz = (float) (TREE_Z + 0.12 + Math.sin(a0) * rad);
            Quaternionf r = new Quaternionf().rotationY((float) (-a0 - Math.PI / 2));
            plan.addDeco(DecoSpec.block("shimenawa" + i, El.TREE_TRUNK, px, ry, pz, "minecraft:hay_block[axis=x]")
                    .box(new Vector3f(), r, new Vector3f(0.95f, 0.26f, 0.26f)).build());
            if (i % 3 == 0) {
                plan.addDeco(DecoSpec.block("shide" + i, El.TREE_TRUNK, px, ry - 0.42, pz, "minecraft:white_concrete")
                        .box(new Vector3f(), new Quaternionf(r).rotateZ(0.25f), new Vector3f(0.16f, 0.55f, 0.03f)).build());
            }
        }
        plan.point("tree", TREE_X, 0, TREE_Z);
        plan.point("tree_top", (float) baseX, 12, (float) baseZ);
    }

    /** Rekursiver Ast aus Kirschholz. */
    private void branch(double x, double y, double z, double dx, double dz, double len, double rise, int depth) {
        double norm = Math.hypot(dx, dz);
        dx /= norm;
        dz /= norm;
        int steps = (int) Math.ceil(len * 2);
        double cx = x, cy = y, cz = z;
        double thick = depth >= 2 ? 1.1 : 0.5;
        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps;
            cx = x + dx * len * t;
            cz = z + dz * len * t;
            cy = y + rise * len * t + Math.sin(t * Math.PI) * 0.8;
            double th = thick * (1 - t * 0.6);
            for (int ox = -1; ox <= 1; ox++)
                for (int oy = -1; oy <= 1; oy++)
                    for (int oz = -1; oz <= 1; oz++) {
                        if (Math.sqrt(ox * ox + oy * oy + oz * oz) > th + 0.2) continue;
                        int bx = (int) Math.floor(cx + ox), by = (int) Math.floor(cy + oy), bz = (int) Math.floor(cz + oz);
                        char axis = Math.abs(dx) > Math.abs(dz) ? 'x' : 'z';
                        set(bx, by, bz, "cherry_log[axis=" + axis + "]", El.TREE_TRUNK);
                    }
        }
        if (depth > 0) {
            for (int k = 0; k < 2; k++) {
                double ang = Math.atan2(dz, dx) + (k == 0 ? 0.6 : -0.6) + (rnd.nextDouble() - 0.5) * 0.4;
                branch(cx, cy, cz, Math.cos(ang), Math.sin(ang), len * 0.45, rise + 0.15, depth - 1);
            }
        } else {
            branchEnds.add(new float[]{(float) cx, (float) cy + 0.5f, (float) cz});
        }
    }

    private void blossomBlob(float cx, float cy, float cz, float rh, float rv) {
        for (int x = (int) Math.floor(cx - rh - 1); x <= (int) Math.ceil(cx + rh + 1); x++) {
            for (int y = (int) Math.floor(cy - rv - 1); y <= (int) Math.ceil(cy + rv + 1); y++) {
                for (int z = (int) Math.floor(cz - rh - 1); z <= (int) Math.ceil(cz + rh + 1); z++) {
                    double dx = (x + 0.5 - cx) / rh, dy = (y + 0.5 - cy) / rv, dz = (z + 0.5 - cz) / rh;
                    double d = dx * dx + dy * dy + dz * dz;
                    double noise = 0.85 + rnd.nextDouble() * 0.3;
                    if (d < noise && y >= 5) {
                        // Krone begrenzen: nicht über die Westmauer, nicht über die Hofmitte, nicht ins Hallendach
                        if (x < -24 || x > -5 || z < -11 || z > 12) continue;
                        plan.setIfEmpty(x, y, z, "cherry_leaves[persistent=true,distance=1]", El.TREE_LEAVES);
                    }
                }
            }
        }
    }

    /** Niedriger roter Zaun (Tamagaki) um den heiligen Baum, offen zum Hof. */
    private void treeFence() {
        double r = 3.6;
        List<int[]> posts = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            if (Math.cos(a) > 0.88) continue; // Öffnung nach Osten
            int x = (int) Math.floor(TREE_X + Math.cos(a) * r), z = (int) Math.floor(TREE_Z + Math.sin(a) * r);
            BuildPlan.Placement p = plan.get(x, 0, z);
            if (p != null && p.element() == El.TREE_TRUNK) continue;
            boolean dup = false;
            for (int[] q : posts) if (q[0] == x && q[1] == z) dup = true;
            if (!dup) posts.add(new int[]{x, z});
        }
        for (int[] p : posts) {
            boolean n = has(posts, p[0], p[1] - 1), s = has(posts, p[0], p[1] + 1), e = has(posts, p[0] + 1, p[1]), w = has(posts, p[0] - 1, p[1]);
            set(p[0], 0, p[1], "mangrove_fence[north=" + n + ",south=" + s + ",east=" + e + ",west=" + w + "]", El.FENCE);
        }
    }

    private static boolean has(List<int[]> l, int x, int z) {
        for (int[] q : l) if (q[0] == x && q[1] == z) return true;
        return false;
    }

    // =====================================================================================
    // Rote Bogenbrücke (Taikobashi) über den Teich

    private void bridge() {
        int x0 = 13, x1 = 15;
        int z0 = -6, z1 = 7;
        int len = z1 - z0;
        for (int z = z0; z <= z1; z++) {
            double t = (z - z0) / (double) len;
            double hgt = Math.sin(t * Math.PI) * 2.2;
            double s = Math.round(hgt * 2) / 2.0;
            int fy = (int) Math.floor(s);
            boolean half = s - fy >= 0.49;
            for (int x = x0; x <= x1; x++) {
                if (half) {
                    set(x, fy, z, slab("spruce_slab", "bottom"), El.BRIDGE);
                    if (fy > 0) set(x, fy - 1, z, "spruce_planks", El.BRIDGE);
                } else if (fy > 0) {
                    set(x, fy - 1, z, slab("spruce_slab", "top"), El.BRIDGE);
                } else {
                    set(x, -1, z, "spruce_planks", El.BRIDGE);
                }
            }
            // Geländer
            int ry = half ? fy + 1 : fy;
            for (int x : new int[]{x0 - 1, x1 + 1}) {
                boolean post = (z - z0) % 3 == 0 || z == z1;
                if (post) {
                    set(x, ry, z, "red_concrete", El.BRIDGE);
                    set(x, ry - 1, z, "red_concrete", El.BRIDGE);
                } else {
                    set(x, ry, z, "mangrove_fence[north=true,south=true]", El.BRIDGE);
                }
                if (ry - 1 >= 0 && plan.get(x, ry - 1, z) == null) set(x, ry - 1, z, "red_concrete", El.BRIDGE);
            }
        }
    }

    // =====================================================================================
    // Nobori – lange Kanji-Banner an Bambusstangen

    private void nobori() {
        String[][] texts = {{"剣", "聖", "道"}, {"千", "年", "桜"}, {"一", "刀", "斎"}, {"散", "華", "心"}, {"無", "明", "剣"}};
        int[][] pos = {{-22, -9}, {-22, 13}, {-22, 20}, {22, -10}, {22, 20}};
        for (int i = 0; i < pos.length; i++) {
            int x = pos[i][0], z = pos[i][1];
            for (int y = 0; y <= 7; y++) set(x, y, z, "bamboo_block[axis=y]", El.NOBORI);
            set(x, 8, z, slab("dark_oak_slab", "bottom"), El.NOBORI);
            float side = x < 0 ? 1f : -1f; // Stoff hängt zur Hofmitte
            double cx = x + 0.5 + side * 0.85, cz = z + 0.5, cy = 4.6;
            Quaternionf face = new Quaternionf().rotationY(x < 0 ? (float) (Math.PI / 2) : (float) (-Math.PI / 2));
            plan.addDeco(DecoSpec.block("nobori_cloth" + i, El.NOBORI, cx, cy, cz, "minecraft:white_wool")
                    .box(new Vector3f(), face, new Vector3f(1.1f, 5.0f, 0.06f)).anim("flag", i).build());
            plan.addDeco(DecoSpec.block("nobori_border" + i, El.NOBORI, cx, cy, cz, "minecraft:red_wool")
                    .box(new Vector3f(0, 2.35f, 0), face, new Vector3f(1.16f, 0.36f, 0.08f)).anim("flag", i).build());
            plan.addDeco(DecoSpec.block("nobori_bar" + i, El.NOBORI, cx, cy, cz, "minecraft:bamboo_block[axis=x]")
                    .box(new Vector3f(0, 2.6f, 0), face, new Vector3f(1.35f, 0.14f, 0.14f)).build());
            String t = "<#1a1a1a>" + texts[i][0] + "\n" + texts[i][1] + "\n" + texts[i][2] + "</#1a1a1a>";
            Vector3f off = face.transform(new Vector3f(0, -0.95f, 0.045f));
            plan.addDeco(DecoSpec.text("nobori_text" + i, El.NOBORI, cx, cy, cz, t)
                    .at(off, face, new Vector3f(2.3f, 2.3f, 2.3f)).anim("flag", i).build());
            Vector3f off2 = face.transform(new Vector3f(0, -0.95f, -0.045f));
            plan.addDeco(DecoSpec.text("nobori_text_b" + i, El.NOBORI, cx, cy, cz, t)
                    .at(off2, new Quaternionf(face).rotateY((float) Math.PI), new Vector3f(2.3f, 2.3f, 2.3f)).anim("flag", i).build());
        }
    }

    // =====================================================================================
    // Garten: Bambushain, Büsche, Koi, Schriftrolle, Waffenständer, Shachihoko

    private void garden() {
        // Bambushain in der Nordwest-Ecke
        for (int x = -24; x <= -15; x++) {
            for (int z = -31; z <= -16; z++) {
                if (plan.get(x, 0, z) != null) continue;
                if (rnd.nextFloat() > 0.22f) continue;
                set(x, -1, z, "moss_block", El.GROUND);
                int h = 6 + rnd.nextInt(6);
                for (int y = 0; y < h; y++) {
                    String leaves = y >= h - 2 ? "large" : y == h - 3 ? "small" : "none";
                    set(x, y, z, "bamboo[age=1,leaves=" + leaves + ",stage=0]", El.GARDEN);
                }
            }
        }
        // Azaleen-Büsche entlang der Mauern
        for (int i = 0; i < 26; i++) {
            int x, z;
            if (rnd.nextBoolean()) {
                x = rnd.nextBoolean() ? -23 - rnd.nextInt(2) : 23 + rnd.nextInt(2);
                z = -12 + rnd.nextInt(34);
            } else {
                x = -20 + rnd.nextInt(41);
                z = WALL_N + 1 + rnd.nextInt(2);
                if (Math.abs(x) < 14) continue;
            }
            if (plan.get(x, 0, z) != null) continue;
            String leaf = rnd.nextFloat() < 0.4f ? "flowering_azalea_leaves[persistent=true,distance=1]" : "azalea_leaves[persistent=true,distance=1]";
            set(x, 0, z, leaf, El.GARDEN);
            if (rnd.nextFloat() < 0.4f && plan.get(x, 1, z) == null) set(x, 1, z, leaf, El.GARDEN);
        }
        // Koi im Teich
        for (int i = 0; i < 4; i++) {
            String body = i % 2 == 0 ? "minecraft:orange_concrete" : "minecraft:white_concrete";
            String spot = i % 2 == 0 ? "minecraft:white_concrete" : "minecraft:red_concrete";
            float phase = i * 1.57f;
            plan.addDeco(DecoSpec.block("koi" + i + "_body", El.POND, POND_X, -1.32, POND_Z, body)
                    .box(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(0.18f, 0.12f, 0.5f)).anim("koi", phase).build());
            plan.addDeco(DecoSpec.block("koi" + i + "_spot", El.POND, POND_X, -1.32, POND_Z, spot)
                    .box(new Vector3f(0, 0.04f, 0.08f), new Quaternionf(), new Vector3f(0.13f, 0.08f, 0.16f)).anim("koi", phase).build());
            plan.addDeco(DecoSpec.block("koi" + i + "_tail", El.POND, POND_X, -1.32, POND_Z, body)
                    .box(new Vector3f(0, 0, -0.32f), new Quaternionf(), new Vector3f(0.04f, 0.16f, 0.2f)).anim("koi", phase).build());
        }
        // Schriftrolle (Kakejiku) 「無心」 an der Rückwand der Halle
        double sx = 0.5, sy = 4.4, sz = HALL_Z0 + 1.08;
        plan.addDeco(DecoSpec.block("scroll", El.HALL_INTERIOR, sx, sy, sz, "minecraft:white_wool")
                .box(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.0f, 2.2f, 0.04f)).build());
        plan.addDeco(DecoSpec.block("scroll_rod_t", El.HALL_INTERIOR, sx, sy, sz, "minecraft:dark_oak_planks")
                .box(new Vector3f(0, 1.12f, 0.02f), new Quaternionf(), new Vector3f(1.2f, 0.08f, 0.08f)).build());
        plan.addDeco(DecoSpec.block("scroll_rod_b", El.HALL_INTERIOR, sx, sy, sz, "minecraft:dark_oak_planks")
                .box(new Vector3f(0, -1.12f, 0.02f), new Quaternionf(), new Vector3f(1.2f, 0.08f, 0.08f)).build());
        plan.addDeco(DecoSpec.text("scroll_text", El.HALL_INTERIOR, sx, sy, sz, "<#111111>無\n心</#111111>")
                .at(new Vector3f(0, -0.62f, 0.035f), new Quaternionf(), new Vector3f(2.6f, 2.6f, 2.6f)).build());
        // Waffenständer mit drei Katana an der Westwand
        for (int i = 0; i < 3; i++) {
            Quaternionf r = new Quaternionf().rotationY((float) (Math.PI / 2)).rotateX((float) Math.toRadians(-90)).rotateZ((float) Math.toRadians(-45));
            // Pack-Katana: Klinge waagrecht entlang der Wand (Nord-Süd), Schneide nach oben – wie im Schwertständer
            Quaternionf rp = new Quaternionf().rotationZ((float) Math.PI);
            plan.addDeco(DecoSpec.item("rack_katana" + i, El.HALL_INTERIOR, HALL_X0 + 1.15, 3.2 + i * 0.55, -22.0, "IRON_SWORD")
                    .at(new Vector3f(), r, new Vector3f(1.1f, 1.1f, 1.1f)).pack("katana")
                    .packAt(new Vector3f(0, 0, -0.35f), rp, new Vector3f(1.25f, 1.25f, 1.25f)).build());
        }
        // Shachihoko – goldene Fische an den Firstenden
        for (String key : new String[]{"ridge", "ridge2"}) {
            Vector3f p = plan.point(key);
            boolean east = key.equals("ridge2");
            float dir = east ? 1f : -1f;
            Quaternionf curl = new Quaternionf().rotationZ((float) Math.toRadians(dir * 28));
            plan.addDeco(DecoSpec.block("shachi_body_" + key, El.HALL_ROOF, p.x, p.y, p.z, "minecraft:gold_block")
                    .box(new Vector3f(0, 0.3f, 0), curl, new Vector3f(0.42f, 1.0f, 0.42f)).build());
            plan.addDeco(DecoSpec.block("shachi_tail_" + key, El.HALL_ROOF, p.x, p.y, p.z, "minecraft:gold_block")
                    .box(new Vector3f(dir * 0.32f, 0.95f, 0), new Quaternionf().rotationZ((float) Math.toRadians(dir * 70)), new Vector3f(0.32f, 0.62f, 0.1f)).build());
            plan.addDeco(DecoSpec.block("shachi_fin_" + key, El.HALL_ROOF, p.x, p.y, p.z, "minecraft:gold_block")
                    .box(new Vector3f(-dir * 0.18f, 0.15f, 0), new Quaternionf().rotationZ((float) Math.toRadians(-dir * 35)), new Vector3f(0.5f, 0.12f, 0.36f)).build());
        }
    }

    private void points() {
        plan.point("boss", 0.5f, 0f, 3.5f);
        plan.point("ult", 0.5f, 9.5f, 3.5f);
        plan.point("gate", 0.5f, 0f, 22.5f);
        plan.point("moon", 0.5f, 26f, 1.5f);
        plan.point("hall", 0.5f, 2f, -22f);
        plan.point("center", 0.5f, 0f, 4.5f);
        // Kampfzone (für Sprünge/Dashes)
        plan.point("zone_min", -21f, 0f, -11f);
        plan.point("zone_max", 21f, 0f, 21f);
    }
}
