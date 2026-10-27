package de.aetherion.mining.titan;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The swallowed isles: the six islands Ossarath ate, each with its own surface and buildings, built once per day
 * into a sparse overlay the generator lays over the isle rock. Every isle has one Relic Chest (TitanRelics fills it).
 */
final class IsleBuilder {

    static final String[] NAMES = {"Frostpine Hollow", "Saltreach", "Cinderforge", "Verdant Shrine", "Gearwick", "Mycel Hamlet"};
    static final String[] LORE = {
            "A pine isle that froze the moment it was swallowed. The chapel bell still hangs, nobody rings it.",
            "A harbour town. Ossarath took it with the tide, ships and all.",
            "The dwarves' foundry isle. The furnaces never cooled, the smiths are long gone.",
            "A jungle shrine whose door was shut before the jaws closed. Something inside wanted to be eaten.",
            "A clockmakers' town. Every clock stopped at the same minute.",
            "A mushroom village. It was already strange before it was swallowed."};

    private final TitanShape shape;
    private final TitanShape.Isle isle;
    private final Map<Long, BlockData> out;
    private final List<int[]> relics;
    private final Map<String, BlockData> cache;
    private final Random random;
    private final int cx, cz, y0;

    private IsleBuilder(TitanShape shape, TitanShape.Isle isle, Map<Long, BlockData> out, List<int[]> relics,
                        Map<String, BlockData> cache) {
        this.shape = shape;
        this.isle = isle;
        this.out = out;
        this.relics = relics;
        this.cache = cache;
        this.random = new Random(shape.seed * 31 + isle.theme() * 7919L + isle.site());
        this.cx = isle.x();
        this.cz = isle.z();
        this.y0 = shape.isleSurface(isle, cx, cz);
    }

    static Map<Long, BlockData> buildAll(TitanShape shape, List<TitanShape.Isle> isles, List<int[]> relics) {
        Map<Long, BlockData> out = new HashMap<>();
        Map<String, BlockData> cache = new HashMap<>();
        for (TitanShape.Isle isle : isles) {
            new IsleBuilder(shape, isle, out, relics, cache).build();
        }
        return out;
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 2048) << 40) | ((long) (y + 2048) << 20) | (z + 2048);
    }

    private void build() {
        switch (isle.theme()) {
            case 0 -> frostpine();
            case 1 -> saltreach();
            case 2 -> cinderforge();
            case 3 -> verdant();
            case 4 -> gearwick();
            default -> mycel();
        }
    }

    // ------------------------------------------------------------------ primitives

    private BlockData b(String state) {
        return cache.computeIfAbsent(state, s -> Bukkit.createBlockData("minecraft:" + s));
    }

    private void set(int x, int y, int z, BlockData data) {
        out.put(key(x, y, z), data);
    }

    private void set(int x, int y, int z, String state) {
        set(x, y, z, b(state));
    }

    private boolean has(int x, int y, int z) {
        return out.containsKey(key(x, y, z));
    }

    /** Top block of the isle at (x, z), or Integer.MIN_VALUE off the isle. */
    private int ground(int x, int z) {
        return shape.isleSurface(isle, x, z);
    }

    private boolean onIsle(int x, int z) {
        return ground(x, z) > TitanShape.SEA - 2;
    }

    private void box(int x0, int y0, int z0, int x1, int y1, int z1, String state) {
        BlockData data = b(state);
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z, data);
                }
            }
        }
    }

    private void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    set(x, y, z, shape.p.cave);
                }
            }
        }
    }

    /** A plinth from the building floor down to the ground so nothing floats on a slope. */
    private void plinth(int x0, int z0, int x1, int z1, int floorY, String state) {
        BlockData data = b(state);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = ground(x, z);
                if (g == Integer.MIN_VALUE) {
                    g = TitanShape.SEA - 3;
                }
                for (int y = floorY - 1; y > g - 1 && y > TitanShape.SEA - 6; y--) {
                    set(x, y, z, data);
                }
            }
        }
    }

    /** Walls of a rectangular room, corners in {@code corner}, a cleared inside. */
    private void room(int x0, int z0, int x1, int z1, int floorY, int height, String wall, String corner, String floor) {
        plinth(x0, z0, x1, z1, floorY, floor);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, floorY - 1, z, floor);
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                boolean post = (x == x0 || x == x1) && (z == z0 || z == z1);
                for (int y = floorY; y < floorY + height; y++) {
                    if (edge) {
                        set(x, y, z, post ? corner : wall);
                    } else {
                        set(x, y, z, shape.p.cave);
                    }
                }
            }
        }
    }

    /** A gable roof of stairs along z (ridge runs north-south), gable ends closed with {@code fill}. */
    private void gableZ(int x0, int z0, int x1, int z1, int baseY, String stair, String fill) {
        int half = (x1 - x0 + 2) / 2;
        for (int i = 0; i <= half; i++) {
            int y = baseY + i;
            int west = x0 - 1 + i;
            int east = x1 + 1 - i;
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (west < east) {
                    set(west, y, z, stair + "[facing=east,half=bottom]");
                    set(east, y, z, stair + "[facing=west,half=bottom]");
                } else if (west == east) {
                    set(west, y, z, fill);
                }
            }
            for (int x = west + 1; x < east; x++) {
                set(x, y, z0, fill);
                set(x, y, z1, fill);
            }
        }
    }

    /** A gable roof of stairs along x (ridge runs east-west). */
    private void gableX(int x0, int z0, int x1, int z1, int baseY, String stair, String fill) {
        int half = (z1 - z0 + 2) / 2;
        for (int i = 0; i <= half; i++) {
            int y = baseY + i;
            int north = z0 - 1 + i;
            int south = z1 + 1 - i;
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                if (north < south) {
                    set(x, y, north, stair + "[facing=south,half=bottom]");
                    set(x, y, south, stair + "[facing=north,half=bottom]");
                } else if (north == south) {
                    set(x, y, north, fill);
                }
            }
            for (int z = north + 1; z < south; z++) {
                set(x0, y, z, fill);
                set(x1, y, z, fill);
            }
        }
    }

    private void door(int x, int y, int z, String wood, String facing) {
        set(x, y, z, wood + "_door[facing=" + facing + ",half=lower,hinge=left,open=false]");
        set(x, y + 1, z, wood + "_door[facing=" + facing + ",half=upper,hinge=left,open=false]");
    }

    private void relic(int x, int y, int z, String facing) {
        set(x, y, z, "chest[facing=" + facing + ",type=single,waterlogged=false]");
        relics.add(new int[]{x, y, z, isle.theme()});
    }

    private void spruce(int x, int z, int h, boolean snowy) {
        int g = ground(x, z);
        if (g == Integer.MIN_VALUE || has(x, g + 1, z)) {
            return;
        }
        for (int y = g + 1; y <= g + h; y++) {
            set(x, y, z, "spruce_log[axis=y]");
        }
        set(x, g + h + 1, z, "spruce_leaves[persistent=true]");
        for (int j = 0; j < h - 2; j++) {
            int y = g + h - j;
            int r = j % 3 == 0 ? Math.min(1 + j / 3, 3) : Math.min(j / 3 + (j % 3 == 1 ? 1 : 0), 3);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if ((dx != 0 || dz != 0) && dx * dx + dz * dz <= r * r + 1 && !has(x + dx, y, z + dz)) {
                        set(x + dx, y, z + dz, "spruce_leaves[persistent=true]");
                        if (snowy && !has(x + dx, y + 1, z + dz) && random.nextInt(3) == 0) {
                            set(x + dx, y + 1, z + dz, "snow[layers=1]");
                        }
                    }
                }
            }
        }
    }

    private void jungle(int x, int z, int h) {
        int g = ground(x, z);
        if (g == Integer.MIN_VALUE) {
            return;
        }
        for (int y = g + 1; y <= g + h; y++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    set(x + dx, y, z + dz, "jungle_log[axis=y]");
                }
            }
        }
        int top = g + h;
        for (int dx = -5; dx <= 6; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -5; dz <= 6; dz++) {
                    double d = (dx - 0.5) * (dx - 0.5) + dy * dy * 2.2 + (dz - 0.5) * (dz - 0.5);
                    if (d <= 22 && !has(x + dx, top + dy, z + dz)) {
                        set(x + dx, top + dy, z + dz, "jungle_leaves[persistent=true]");
                        if (dy == -2 && random.nextInt(5) == 0) {
                            int len = 1 + random.nextInt(4);
                            for (int v = 1; v <= len; v++) {
                                if (!has(x + dx, top + dy - v, z + dz)) {
                                    set(x + dx, top + dy - v, z + dz, "vine[up=true]");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Frostpine Hollow

    private void frostpine() {
        int base = y0 + 1;
        // Snow on the open ground first; buildings overwrite it.
        for (int x = cx - isle.radius() - 4; x <= cx + isle.radius() + 4; x++) {
            for (int z = cz - isle.radius() - 4; z <= cz + isle.radius() + 4; z++) {
                int g = ground(x, z);
                if (g > TitanShape.SEA && random.nextInt(2) == 0) {
                    set(x, g + 1, z, "snow[layers=" + (1 + random.nextInt(2)) + "]");
                }
            }
        }
        // The frozen chapel, nave along z, door to the south, bell tower behind the altar.
        int x0 = cx - 4, x1 = cx + 4, z0 = cz - 6, z1 = cz + 6;
        room(x0, z0, x1, z1, base, 5, "stone_bricks", "spruce_log[axis=y]", "spruce_planks");
        for (int z = z0; z <= z1; z++) {
            for (int y = base; y < base + 5; y++) {
                if (random.nextInt(5) == 0) {
                    set(x0, y, z, random.nextBoolean() ? "cracked_stone_bricks" : "packed_ice");
                }
                if (random.nextInt(5) == 0) {
                    set(x1, y, z, random.nextBoolean() ? "mossy_stone_bricks" : "packed_ice");
                }
            }
        }
        for (int z : new int[]{cz - 3, cz, cz + 3}) {
            set(x0, base + 2, z, "light_blue_stained_glass_pane[north=true,south=true]");
            set(x1, base + 2, z, "light_blue_stained_glass_pane[north=true,south=true]");
        }
        set(cx, base, z1, shape.p.cave);
        set(cx, base + 1, z1, shape.p.cave);
        door(cx, base, z1, "spruce", "south");
        gableZ(x0, z0, x1, z1, base + 5, "spruce_stairs", "spruce_planks");
        for (int z = cz - 2; z <= cz + 4; z += 2) {
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                if (x != cx) {
                    set(x, base, z, "spruce_stairs[facing=south,half=bottom]");
                }
            }
        }
        set(cx, base, z0 + 1, "quartz_block");
        set(cx, base + 1, z0 + 1, "candle[candles=3,lit=true]");
        relic(cx + 2, base, z0 + 1, "south");
        set(cx - 2, base + 3, cz, "light[level=12]");
        set(cx + 2, base + 3, cz - 3, "light[level=12]");
        // Bell tower.
        int tz0 = z0 - 3, tz1 = z0 - 1;
        room(cx - 1, tz0, cx + 1, tz1, base, 12, "stone_bricks", "stone_bricks", "stone_bricks");
        for (int y = base + 9; y <= base + 11; y++) {
            set(cx, y, tz0, shape.p.cave);
            set(cx, y, tz1, shape.p.cave);
            set(cx - 1, y, cz - 8, shape.p.cave);
        }
        box(cx - 1, base + 12, tz0, cx + 1, base + 12, tz1, "spruce_slab[type=bottom]");
        set(cx, base + 11, tz0 + 1, "bell[attachment=ceiling,facing=south]");
        // Pines in a ring, a frozen pond and a woodcutter's hut.
        for (int k = 0; k < 9; k++) {
            double a = k * Math.PI * 2 / 9 + random.nextDouble() * 0.3;
            double d = isle.radius() * (0.62 + random.nextDouble() * 0.2);
            spruce(cx + (int) Math.round(Math.cos(a) * d), cz + (int) Math.round(Math.sin(a) * d), 9 + random.nextInt(6), true);
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = cx + 8 + dx, z = cz + 8 + dz;
                int g = ground(x, z);
                if (g != Integer.MIN_VALUE && dx * dx + dz * dz <= 10) {
                    set(x, g, z, dx * dx + dz * dz <= 5 ? "ice" : "packed_ice");
                    set(x, g + 1, z, shape.p.cave);
                }
            }
        }
        int hx = cx - 9, hz = cz - 7;
        int hb = ground(hx + 2, hz + 2) + 1;
        room(hx, hz, hx + 4, hz + 4, hb, 3, "spruce_log[axis=y]", "stripped_spruce_log[axis=y]", "spruce_planks");
        box(hx - 1, hb + 3, hz - 1, hx + 5, hb + 3, hz + 5, "spruce_slab[type=bottom]");
        set(hx + 2, hb, hz + 4, shape.p.cave);
        set(hx + 2, hb + 1, hz + 4, shape.p.cave);
        for (int i = 0; i < 3; i++) {
            set(hx + 6, hb + i / 2, hz + 1 + i % 2, "stripped_spruce_log[axis=x]");
        }
    }

    // ------------------------------------------------------------------ Saltreach

    private void saltreach() {
        int dirX = Integer.signum(108 - cx), dirZ = Integer.signum(-cz);
        boolean alongX = Math.abs(108 - cx) >= Math.abs(cz);
        // Pier toward the middle of the sea, a second one sideways.
        pier(alongX ? dirX : 0, alongX ? 0 : dirZ);
        pier(alongX ? 0 : (dirX == 0 ? 1 : dirX), alongX ? (dirZ == 0 ? 1 : dirZ) : 0);
        // Lighthouse on the far side.
        int lx = cx - (alongX ? dirX : 0) * (isle.radius() / 2), lz = cz - (alongX ? 0 : dirZ) * (isle.radius() / 2);
        int g = ground(lx, lz);
        int lb = g + 1;
        for (int y = lb; y <= lb + 17; y++) {
            boolean white = ((y - lb) / 3) % 2 == 0;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double d = dx * dx + dz * dz;
                    if (d <= 9.5) {
                        set(lx + dx, y, lz + dz, d <= 2.5 ? (y == lb ? "smooth_stone" : "air") : (white ? "white_concrete" : "red_concrete"));
                    }
                }
            }
        }
        plinth(lx - 3, lz - 3, lx + 3, lz + 3, lb, "stone_bricks");
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = dx * dx + dz * dz;
                if (d <= 20) {
                    set(lx + dx, lb + 18, lz + dz, "polished_andesite_slab[type=bottom]");
                }
                if (d <= 6.5 && d > 2.5) {
                    for (int y = lb + 19; y <= lb + 21; y++) {
                        set(lx + dx, y, lz + dz, "glass");
                    }
                }
                if (d <= 2.5) {
                    set(lx + dx, lb + 19, lz + dz, "sea_lantern");
                    set(lx + dx, lb + 20, lz + dz, "light[level=15]");
                }
                if (d <= 9.5) {
                    set(lx + dx, lb + 22, lz + dz, "red_concrete");
                }
                if (d <= 4) {
                    set(lx + dx, lb + 23, lz + dz, "red_concrete");
                }
            }
        }
        set(lx, lb + 24, lz, "lightning_rod[facing=up]");
        int ddx = alongX ? dirX : 0, ddz = alongX ? 0 : dirZ;
        set(lx + ddx * 3, lb, lz + ddz * 3, shape.p.cave);
        set(lx + ddx * 3, lb + 1, lz + ddz * 3, shape.p.cave);
        // Fisher huts and the harbour master's house with the Relic Chest.
        hut(cx - 7, cz + 3, false);
        hut(cx + 2, cz - 9, false);
        hut(cx - 2, cz + 6, true);
        wreck(alongX ? -dirX : 0, alongX ? 0 : -dirZ);
    }

    private void pier(int ux, int uz) {
        if (ux == 0 && uz == 0) {
            return;
        }
        // Walk out from the middle until the isle ends, then build 12 blocks over the water.
        int sx = cx, sz = cz, steps = 0;
        while (onIsle(sx + ux, sz + uz) && steps++ < 60) {
            sx += ux;
            sz += uz;
        }
        int deck = TitanShape.SEA + 1;
        for (int t = -2; t <= 12; t++) {
            for (int w = -1; w <= 1; w++) {
                int x = sx + ux * t + uz * w, z = sz + uz * t + ux * w;
                set(x, deck, z, "spruce_planks");
                if (t % 4 == 0 && w != 0) {
                    for (int y = deck - 1; y >= deck - 6; y--) {
                        set(x, y, z, "spruce_fence");
                    }
                    set(x, deck + 1, z, "spruce_fence");
                    if (t == 12) {
                        set(x, deck + 2, z, "lantern[hanging=false]");
                    }
                }
            }
        }
        set(sx + ux * 10, deck + 1, sz + uz * 10, "barrel[facing=up]");
    }

    private void hut(int x0, int z0, boolean master) {
        int g = ground(x0 + 2, z0 + 3);
        if (g == Integer.MIN_VALUE) {
            return;
        }
        int base = g + 1;
        room(x0, z0, x0 + 4, z0 + 5, base, 4, "oak_planks", "oak_log[axis=y]", "spruce_planks");
        gableZ(x0, z0, x0 + 4, z0 + 5, base + 4, "spruce_stairs", "oak_planks");
        set(x0 + 2, base, z0 + 5, shape.p.cave);
        set(x0 + 2, base + 1, z0 + 5, shape.p.cave);
        door(x0 + 2, base, z0 + 5, "oak", "south");
        set(x0, base + 2, z0 + 2, "glass_pane[north=true,south=true]");
        set(x0 + 4, base + 2, z0 + 2, "glass_pane[north=true,south=true]");
        set(x0 + 1, base, z0 + 1, "barrel[facing=up]");
        set(x0 + 3, base, z0 + 1, master ? "lectern[facing=south,has_book=false]" : "water_cauldron[level=3]");
        set(x0 + 2, base + 3, z0 + 2, "light[level=12]");
        if (master) {
            relic(x0 + 1, base, z0 + 3, "east");
        }
    }

    private void wreck(int ux, int uz) {
        if (ux == 0 && uz == 0) {
            ux = 1;
        }
        int sx = cx, sz = cz, steps = 0;
        while (onIsle(sx + ux, sz + uz) && steps++ < 60) {
            sx += ux;
            sz += uz;
        }
        // Hull along the shore (perpendicular to the way out), keel just under the water.
        int px = uz, pz = ux;
        int keel = TitanShape.SEA - 3;
        for (int u = -8; u <= 8; u++) {
            double halfWidth = 4.2 * Math.sqrt(Math.max(0, 1 - (u / 8.6) * (u / 8.6)));
            if (u > 5 && random.nextBoolean()) {
                continue;
            }
            for (int w = -5; w <= 5; w++) {
                for (int v = 0; v <= 5; v++) {
                    double shell = halfWidth - (5 - v) * 0.35;
                    int x = sx + ux * 2 + px * u + ux * w, z = sz + uz * 2 + pz * u + uz * w;
                    int y = keel + v;
                    if (Math.abs(w) <= shell && Math.abs(w) > shell - 1.1) {
                        set(x, y, z, "dark_oak_planks");
                    } else if (v == 0 && Math.abs(w) <= shell) {
                        set(x, y, z, "dark_oak_log[axis=" + (px != 0 ? "x" : "z") + "]");
                    }
                }
            }
        }
        int mx = sx + ux * 2, mz = sz + uz * 2;
        for (int y = keel + 1; y <= keel + 11; y++) {
            set(mx, y, mz, "dark_oak_log[axis=y]");
        }
        for (int i = 1; i <= 4; i++) {
            set(mx + px * i, keel + 8, mz + pz * i, "white_wool");
            set(mx + px * i, keel + 7 - (i % 2), mz + pz * i, "white_wool");
        }
    }

    // ------------------------------------------------------------------ Cinderforge

    private void cinderforge() {
        int base = y0 + 1;
        int x0 = cx - 6, x1 = cx + 6, z0 = cz - 8, z1 = cz + 8;
        room(x0, z0, x1, z1, base, 7, "deepslate_bricks", "polished_blackstone", "polished_blackstone_bricks");
        for (int z = z0; z <= z1; z += 4) {
            for (int y = base; y < base + 7; y++) {
                set(x0, y, z, "polished_blackstone");
                set(x1, y, z, "polished_blackstone");
            }
        }
        box(x0, base + 7, z0, x1, base + 7, z1, "deepslate_tiles");
        box(x0 + 3, base + 8, z0, x1 - 3, base + 8, z1, "deepslate_tile_slab[type=bottom]");
        clear(cx - 2, base, z1, cx + 2, base + 4, z1);
        for (int x = x0 + 2; x <= x1 - 2; x += 4) {
            set(x, base, z0 + 1, "blast_furnace[facing=south,lit=true]");
        }
        set(cx - 3, base, cz, "anvil[facing=east]");
        set(cx + 3, base, cz + 2, "anvil[facing=west]");
        set(cx - 4, base, cz + 4, "smithing_table");
        set(cx + 4, base, cz - 3, "lava_cauldron");
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            set(x, base - 1, cz - 3, "lava");
            set(x, base, cz - 3, "iron_bars[east=true,west=true]");
        }
        for (int[] c : new int[][]{{cx - 3, cz - 5}, {cx + 3, cz + 5}, {cx, cz + 1}}) {
            for (int y = base + 3; y <= base + 6; y++) {
                set(c[0], y, c[1], "chain[axis=y]");
            }
            set(c[0], base + 2, c[1], "lantern[hanging=true]");
        }
        relic(x1 - 1, base, z0 + 2, "west");
        for (int[] c : new int[][]{{x0 + 1, z0 + 1}, {x1 - 1, z0 + 1}}) {
            box(c[0] - 1, base + 8, c[1] - 1, c[0] + 1, base + 17, c[1] + 1, "bricks");
            set(c[0], base + 18, c[1], "campfire[lit=true,signal_fire=true]");
        }
        // Slag heaps and a rail spur out front.
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + 0.6;
            int hx = cx + (int) Math.round(Math.cos(a) * isle.radius() * 0.72);
            int hz = cz + (int) Math.round(Math.sin(a) * isle.radius() * 0.72);
            int g = ground(hx, hz);
            if (g == Integer.MIN_VALUE) {
                continue;
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = 0; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx * dx + dy * dy * 1.6 + dz * dz <= 9 + random.nextInt(3)) {
                            int r = random.nextInt(10);
                            set(hx + dx, g + 1 + dy, hz + dz, r < 4 ? "blackstone" : r < 7 ? "basalt[axis=y]" : r < 9 ? "coal_block" : "magma_block");
                        }
                    }
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            int g = ground(x, z1 + 3);
            if (g != Integer.MIN_VALUE) {
                set(x, g + 1, z1 + 3, "rail[shape=east_west]");
            }
        }
    }

    // ------------------------------------------------------------------ Verdant Shrine

    private void verdant() {
        int base = y0 + 1;
        for (int x = cx - isle.radius() - 4; x <= cx + isle.radius() + 4; x++) {
            for (int z = cz - isle.radius() - 4; z <= cz + isle.radius() + 4; z++) {
                int g = ground(x, z);
                if (g <= TitanShape.SEA) {
                    continue;
                }
                int r = random.nextInt(12);
                if (r == 0) {
                    set(x, g + 1, z, "fern");
                } else if (r == 1) {
                    set(x, g + 1, z, "moss_carpet");
                } else if (r == 2 && random.nextInt(3) == 0) {
                    set(x, g + 1, z, random.nextBoolean() ? "azalea" : "flowering_azalea");
                }
            }
        }
        // Three tiers of mossy stone, a stair up the south face, the shrine room on top.
        String[] stone = {"mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks"};
        plinth(cx - 7, cz - 7, cx + 7, cz + 7, base, "stone_bricks");
        for (int t = 0; t < 3; t++) {
            int half = 7 - 2 * t;
            for (int x = cx - half; x <= cx + half; x++) {
                for (int z = cz - half; z <= cz + half; z++) {
                    for (int y = base + 3 * t; y < base + 3 * t + 3; y++) {
                        set(x, y, z, stone[random.nextInt(stone.length)]);
                    }
                }
            }
        }
        int top = base + 9;
        for (int s = 0; s <= 10; s++) {
            int y = base + s, z = cz + 9 - s;
            for (int x = cx - 1; x <= cx + 1; x++) {
                set(x, y, z, "stone_brick_stairs[facing=north,half=bottom]");
                for (int yy = y - 1; yy >= base - 1; yy--) {
                    if (!has(x, yy, z)) {
                        set(x, yy, z, "stone_bricks");
                    }
                }
            }
        }
        room(cx - 2, cz - 2, cx + 2, cz + 2, top, 4, "chiseled_stone_bricks", "mossy_stone_bricks", "mossy_stone_bricks");
        box(cx - 3, top + 4, cz - 3, cx + 3, top + 4, cz + 3, "mossy_stone_brick_slab[type=bottom]");
        clear(cx - 1, top, cz + 2, cx + 1, top + 2, cz + 2);
        set(cx, top - 1, cz, "ochre_froglight[axis=y]");
        relic(cx, top, cz - 1, "south");
        set(cx - 1, top, cz - 1, "lantern[hanging=false]");
        set(cx + 1, top, cz - 1, "lantern[hanging=false]");
        // The sealed puzzle door on the first tier.
        box(cx - 1, base, cz + 7, cx + 1, base + 2, cz + 7, "chiseled_stone_bricks");
        set(cx - 2, base + 1, cz + 8, "lever[face=wall,facing=south,powered=false]");
        set(cx + 2, base + 1, cz + 8, "lever[face=wall,facing=south,powered=false]");
        int[][] trees = {{-13, -9}, {12, -11}, {-11, 11}, {13, 9}};
        for (int[] t : trees) {
            jungle(cx + t[0], cz + t[1], 13 + random.nextInt(5));
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = cx - 10 + dx, z = cz + 1 + dz;
                int g = ground(x, z);
                if (g != Integer.MIN_VALUE && dx * dx + dz * dz <= 7) {
                    set(x, g, z, "water");
                    set(x, g - 1, z, "clay");
                    set(x, g + 1, z, random.nextInt(3) == 0 ? "lily_pad" : "air");
                }
            }
        }
    }

    // ------------------------------------------------------------------ Gearwick

    private void gearwick() {
        int base = y0 + 1;
        // Paths of stone and cobble out from the tower.
        for (int t = 3; t <= isle.radius() - 2; t++) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int w = -1; w <= 1; w++) {
                    int x = cx + d[0] * t + d[1] * w, z = cz + d[1] * t + d[0] * w;
                    int g = ground(x, z);
                    if (g > TitanShape.SEA) {
                        set(x, g, z, random.nextInt(3) == 0 ? "cobblestone" : "stone_bricks");
                    }
                }
            }
        }
        // The clock tower.
        room(cx - 2, cz - 2, cx + 2, cz + 2, base, 20, "stone_bricks", "polished_andesite", "polished_andesite");
        clear(cx, base, cz + 2, cx, base + 1, cz + 2);
        door(cx, base, cz + 2, "spruce", "south");
        relic(cx - 1, base, cz - 1, "south");
        set(cx + 1, base + 2, cz, "light[level=12]");
        for (int y = base + 3; y <= base + 19; y += 4) {
            box(cx - 1, y, cz - 1, cx + 1, y, cz + 1, "spruce_planks");
        }
        clock(base + 15, true);
        clock(base + 15, false);
        for (int i = 0; i < 4; i++) {
            int half = 3 - i;
            String copper = i % 2 == 0 ? "oxidized_cut_copper" : "weathered_cut_copper";
            box(cx - half, base + 20 + i, cz - half, cx + half, base + 20 + i, cz + half, copper);
        }
        set(cx, base + 24, cz, "lightning_rod[facing=up]");
        set(cx, base + 19, cz, "bell[attachment=floor,facing=south]");
        // Workshops, lamp posts and two half-buried gears.
        double[] angles = {0.5, 2.6, 4.6};
        for (double a : angles) {
            int hx = cx + (int) Math.round(Math.cos(a) * isle.radius() * 0.55);
            int hz = cz + (int) Math.round(Math.sin(a) * isle.radius() * 0.55);
            workshop(hx - 2, hz - 3);
        }
        for (int[] c : new int[][]{{cx + 6, cz + 2}, {cx - 6, cz - 2}, {cx + 2, cz - 7}, {cx - 2, cz + 7}}) {
            int g = ground(c[0], c[1]);
            if (g == Integer.MIN_VALUE) {
                continue;
            }
            set(c[0], g + 1, c[1], "cobblestone_wall");
            set(c[0], g + 2, c[1], "cobblestone_wall");
            set(c[0], g + 3, c[1], "lantern[hanging=false]");
        }
        gear(cx + isle.radius() / 2, cz - isle.radius() / 2 - 2);
        gear(cx - isle.radius() / 2 - 1, cz + isle.radius() / 2);
    }

    private void clock(int y, boolean southFace) {
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                double d = Math.sqrt(a * a + b * b);
                String block = d <= 1.0 ? "black_concrete" : d <= 2.3 ? "quartz_block" : d <= 3.2 ? "gold_block" : null;
                if (block == null) {
                    continue;
                }
                if ((a == 0 && b > 0 && b <= 2) || (b == 0 && a > 0 && a <= 1)) {
                    block = "black_concrete";
                }
                if (southFace) {
                    set(cx + a, y + b, cz + 3, block);
                } else {
                    set(cx + 3, y + b, cz + a, block);
                }
            }
        }
    }

    private void workshop(int x0, int z0) {
        int g = ground(x0 + 2, z0 + 3);
        if (g == Integer.MIN_VALUE) {
            return;
        }
        int base = g + 1;
        room(x0, z0, x0 + 4, z0 + 6, base, 4, "white_terracotta", "stripped_spruce_log[axis=y]", "spruce_planks");
        for (int z = z0; z <= z0 + 6; z++) {
            set(x0, base + 3, z, "stripped_spruce_log[axis=z]");
            set(x0 + 4, base + 3, z, "stripped_spruce_log[axis=z]");
        }
        gableZ(x0, z0, x0 + 4, z0 + 6, base + 4, "exposed_cut_copper_stairs", "exposed_cut_copper");
        clear(x0 + 2, base, z0 + 6, x0 + 2, base + 1, z0 + 6);
        door(x0 + 2, base, z0 + 6, "spruce", "south");
        set(x0, base + 2, z0 + 3, "glass_pane[north=true,south=true]");
        set(x0 + 4, base + 2, z0 + 3, "glass_pane[north=true,south=true]");
        set(x0 + 1, base, z0 + 1, "grindstone[face=floor,facing=south]");
        set(x0 + 3, base, z0 + 1, "smithing_table");
        set(x0 + 3, base, z0 + 3, "crafting_table");
        set(x0 + 2, base + 3, z0 + 3, "light[level=12]");
    }

    private void gear(int x, int z) {
        int g = ground(x, z);
        if (g == Integer.MIN_VALUE) {
            return;
        }
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double angle = Math.atan2(dz, dx);
                boolean tooth = d > 4.6 && d <= 5.8 && Math.abs(Math.sin(angle * 4)) > 0.7;
                if ((d > 3.4 && d <= 4.6) || tooth || d <= 1.2) {
                    set(x + dx, g, z + dz, "cut_copper");
                }
            }
        }
    }

    // ------------------------------------------------------------------ Mycel Hamlet

    private void mycel() {
        int base = y0 + 1;
        // The spore tower in the middle.
        for (int y = base; y <= base + 15; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(cx + dx, y, cz + dz, "mushroom_stem");
                }
            }
        }
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    double d = dx * dx + dz * dz + dy * dy * 4.5;
                    if (d <= 38 && d > 20 - dy * 3) {
                        set(cx + dx, base + 16 + dy, cz + dz, random.nextInt(9) == 0 ? "shroomlight" : "nether_wart_block");
                    }
                }
                if (dx * dx + dz * dz <= 30 && dx * dx + dz * dz > 4 && random.nextInt(6) == 0) {
                    set(cx + dx, base + 15, cz + dz, "spore_blossom");
                }
            }
        }
        // Mushroom houses around it; the biggest keeps the Relic Chest.
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + 0.4 + random.nextDouble() * 0.3;
            int hx = cx + (int) Math.round(Math.cos(a) * isle.radius() * 0.5);
            int hz = cz + (int) Math.round(Math.sin(a) * isle.radius() * 0.5);
            mushroomHouse(hx, hz, k == 0 ? 4 : 3, k == 0);
        }
        for (int k = 0; k < 5; k++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = isle.radius() * (0.72 + random.nextDouble() * 0.15);
            int x = cx + (int) Math.round(Math.cos(a) * d), z = cz + (int) Math.round(Math.sin(a) * d);
            int g = ground(x, z);
            if (g == Integer.MIN_VALUE) {
                continue;
            }
            int h = 4 + random.nextInt(3);
            for (int y = g + 1; y <= g + h; y++) {
                set(x, y, z, "mushroom_stem");
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz <= 10) {
                        set(x + dx, g + h + 1, z + dz, "brown_mushroom_block");
                    }
                }
            }
        }
    }

    private void mushroomHouse(int x, int z, int r, boolean relic) {
        int g = ground(x, z);
        if (g == Integer.MIN_VALUE) {
            return;
        }
        int base = g + 1;
        plinth(x - r, z - r, x + r, z + r, base, "mushroom_stem");
        int doorX = Integer.signum(cx - x), doorZ = Integer.signum(cz - z);
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.3) {
                    continue;
                }
                set(x + dx, base - 1, z + dz, "mycelium");
                for (int y = base; y < base + 5; y++) {
                    set(x + dx, y, z + dz, d > r - 0.8 ? "mushroom_stem" : "air");
                }
            }
        }
        int ex = x + doorX * r, ez = z + (doorX == 0 ? doorZ * r : 0);
        set(ex, base, ez, shape.p.cave);
        set(ex, base + 1, ez, shape.p.cave);
        int capR = r + 2;
        for (int dx = -capR; dx <= capR; dx++) {
            for (int dz = -capR; dz <= capR; dz++) {
                for (int dy = 0; dy <= capR; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.6);
                    if (d <= capR + 0.4 && d > capR - 0.9) {
                        set(x + dx, base + 5 + dy, z + dz, "red_mushroom_block");
                    } else if (d <= capR - 0.9 && dy == 0) {
                        set(x + dx, base + 5, z + dz, "mushroom_stem");
                    }
                }
            }
        }
        set(x, base + 4, z, "shroomlight");
        set(x - 1, base, z - 1, "crafting_table");
        if (relic) {
            relic(x + 1, base, z + 1, doorX > 0 ? "east" : doorX < 0 ? "west" : doorZ > 0 ? "south" : "north");
        }
    }
}
