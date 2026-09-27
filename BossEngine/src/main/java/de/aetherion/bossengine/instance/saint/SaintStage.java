package de.aetherion.bossengine.instance.saint;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Gilded Proscenium: Seraphine's stage, a theatre torn loose and left floating in the sky.
 *
 * <ul>
 *   <li>A round parquet stage (r 16) with a gold compass inlay, footlights sunk in the rim, and
 *       four trapdoors over glowing pits (her escape hatches in the last act).</li>
 *   <li>A hanging rock keel beneath with chained lanterns, so it reads as an island from below.</li>
 *   <li>A quartz-and-blackstone proscenium arch behind the stage with drawn red curtains,
 *       a scalloped valance and hanging lamps. Through the arch: open sky.</li>
 *   <li>Three tiers of floating balconies and two side boxes facing the stage, seats laid out for
 *       the audience (skull puppets placed by {@link StageDressing}).</li>
 * </ul>
 *
 * <p>Safety: the builder only ever places into air, records every block it placed, and strikes
 * exactly those blocks when the show ends. A stage built with {@link #buildPermanent} carries a
 * chunk marker and is reused by every later fight instead of being rebuilt or struck.
 */
public final class SaintStage {

    public static final int RADIUS = 16;
    static final float FLOOR_R = 16.5f;
    static final float TRAP_R = 9f;
    static final int[][] TRAP_SIGNS = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};

    private static final String MARKER = "saint_stage";
    private static final Map<String, SaintStage> ACTIVE = new HashMap<>();

    record Cell(int x, int y, int z, BlockData data, float order) {
    }

    private final Location center;
    private final World world;
    private final boolean permanent;
    private final boolean preexisting;
    private final List<Cell> layout;
    private final List<Block> placed = new ArrayList<>();
    private final List<Material> placedAs = new ArrayList<>();
    private final Map<String, Block> lights = new HashMap<>();
    private final Set<Block> barriers = new HashSet<>();
    private final boolean[] trapOpen = new boolean[4];
    private int raiseCursor;
    private boolean built;

    private SaintStage(Location center, boolean permanent, boolean preexisting) {
        this.center = center;
        this.world = center.getWorld();
        this.permanent = permanent;
        this.preexisting = preexisting;
        this.layout = layout();
        this.built = preexisting;
        if (preexisting) {
            raiseCursor = layout.size();
        }
    }

    /* ------------------------------------------------------------------ public API */

    /**
     * Builds the stage instantly with its center (floor surface) at {@code at} and marks it
     * permanent: later Hanging Saint fights spawned on it reuse it and never strike it.
     * Only fills air. Intended for a deployment engineer placing a dedicated arena.
     */
    public static Location buildPermanent(Plugin plugin, Location at) {
        Location c = snap(at);
        SaintStage stage = new SaintStage(c, true, false);
        stage.raiseAll();
        stage.writeMarker(plugin, true);
        return c.clone();
    }

    /** Removes a stage built by {@link #buildPermanent} (layout-matched, never touches other blocks). */
    public static void strikePermanent(Plugin plugin, Location at) {
        Location c = snap(at);
        SaintStage stage = new SaintStage(c, true, true);
        stage.strikeByLayout();
        stage.clearMarker(plugin);
    }

    /* ------------------------------------------------------------------ claim */

    /**
     * Find or plan the stage for a fight spawned at {@code spawn}. Reuses a marked stage in the
     * spawn chunk; otherwise picks the first height at or above the spawn where the stage fits
     * in open air.
     */
    static SaintStage claim(Plugin plugin, Location spawn) {
        Location marked = readMarker(plugin, spawn);
        if (marked != null) {
            boolean perm = readPermanent(plugin, spawn);
            SaintStage stage = new SaintStage(marked, perm, true);
            ACTIVE.put(key(marked), stage);
            return stage;
        }
        Location c = snap(spawn);
        World world = c.getWorld();
        int top = world.getMaxHeight() - 24;
        Location pick = c.clone();
        for (int lift = 0; lift <= 72 && c.getBlockY() + lift < top; lift += 3) {
            Location probe = c.clone().add(0, lift, 0);
            if (clearance(probe) >= 0.93) {
                pick = probe;
                break;
            }
        }
        SaintStage stage = new SaintStage(pick, false, false);
        stage.writeMarker(plugin, false);
        ACTIVE.put(key(pick), stage);
        return stage;
    }

    private static double clearance(Location c) {
        World w = c.getWorld();
        int total = 0;
        int air = 0;
        for (int x = -RADIUS; x <= RADIUS; x += 2) {
            for (int z = -RADIUS; z <= RADIUS; z += 2) {
                if (x * x + z * z > RADIUS * RADIUS) {
                    continue;
                }
                for (int y = -1; y <= 6; y += (y < 1 ? 1 : 2)) {
                    total++;
                    if (w.getBlockAt(c.getBlockX() + x, c.getBlockY() + y, c.getBlockZ() + z).getType().isAir()) {
                        air++;
                    }
                }
            }
        }
        return total == 0 ? 0 : air / (double) total;
    }

    private static Location snap(Location at) {
        return new Location(at.getWorld(), at.getBlockX() + 0.5, at.getBlockY(), at.getBlockZ() + 0.5);
    }

    private static String key(Location c) {
        return c.getWorld().getName() + ":" + c.getBlockX() + ":" + c.getBlockY() + ":" + c.getBlockZ();
    }

    /* ------------------------------------------------------------------ marker */

    private void writeMarker(Plugin plugin, boolean perm) {
        Chunk chunk = center.getChunk();
        chunk.getPersistentDataContainer().set(new NamespacedKey(plugin, MARKER), PersistentDataType.STRING,
                center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + "," + (perm ? 1 : 0));
    }

    private void clearMarker(Plugin plugin) {
        center.getChunk().getPersistentDataContainer().remove(new NamespacedKey(plugin, MARKER));
    }

    private static String rawMarker(Plugin plugin, Location at) {
        return at.getChunk().getPersistentDataContainer().get(new NamespacedKey(plugin, MARKER), PersistentDataType.STRING);
    }

    private static Location readMarker(Plugin plugin, Location at) {
        String raw = rawMarker(plugin, at);
        if (raw == null) {
            return null;
        }
        try {
            String[] p = raw.split(",");
            return new Location(at.getWorld(), Integer.parseInt(p[0]) + 0.5, Integer.parseInt(p[1]), Integer.parseInt(p[2]) + 0.5);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean readPermanent(Plugin plugin, Location at) {
        String raw = rawMarker(plugin, at);
        return raw != null && raw.endsWith(",1");
    }

    /* ------------------------------------------------------------------ geometry */

    Location center() {
        return center.clone();
    }

    boolean built() {
        return built;
    }

    boolean permanent() {
        return permanent;
    }

    /** Stage-space center of trapdoor {@code i} (floor surface). */
    static Vector3f trapCenter(int i) {
        int[] s = TRAP_SIGNS[i];
        // Trapdoor cells are offsets 6 and 7 (block centers), so the pair's middle is 6.5.
        return new Vector3f(s[0] * 6.5f, 0f, s[1] * 6.5f);
    }

    /** Seat positions (stage space, seat surface) for the skull audience. */
    List<Vector3f> seats() {
        List<Vector3f> out = new ArrayList<>();
        for (Cell c : layout) {
            if (c.data() instanceof Stairs) {
                out.add(new Vector3f(c.x(), c.y() + 0.5f, c.z()));
            }
        }
        return out;
    }

    /* ------------------------------------------------------------------ raise / strike */

    /** Places the next slice of the stage. Returns progress 0..1. */
    float raiseStep(int blocksPerTick) {
        int end = Math.min(layout.size(), raiseCursor + blocksPerTick);
        Cell last = null;
        for (int i = raiseCursor; i < end; i++) {
            Cell c = layout.get(i);
            place(c);
            last = c;
        }
        raiseCursor = end;
        if (last != null && world != null) {
            Location l = at(last.x(), last.y(), last.z());
            world.playSound(l, Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.4f, 0.6f + (float) Math.random() * 0.3f);
            world.playSound(l, Sound.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.7f, 0.7f);
            world.spawnParticle(Particle.CLOUD, l.add(0.5, 1.0, 0.5), 3, 0.4, 0.2, 0.4, 0.01);
        }
        if (raiseCursor >= layout.size()) {
            built = true;
            return 1f;
        }
        return raiseCursor / (float) layout.size();
    }

    void raiseAll() {
        while (raiseStep(4096) < 1f) {
            // keep placing
        }
    }

    private void place(Cell c) {
        Block b = world.getBlockAt(center.getBlockX() + c.x(), center.getBlockY() + c.y(), center.getBlockZ() + c.z());
        if (!b.getType().isAir()) {
            return;
        }
        b.setBlockData(c.data(), false);
        if (!permanent) {
            placed.add(b);
            placedAs.add(c.data().getMaterial());
        }
    }

    /** End of show. Temporary stages come down; permanent ones only lose their temp blocks. */
    void strike(boolean instant, Plugin plugin) {
        clearTemp();
        ACTIVE.remove(key(center));
        if (permanent) {
            return;
        }
        if (preexisting && placed.isEmpty()) {
            // Leftover from a fight the server never finished: strike by layout match.
            strikeByLayout();
            clearMarker(plugin);
            return;
        }
        clearMarker(plugin);
        if (instant || plugin == null || !plugin.isEnabled()) {
            for (int i = placed.size() - 1; i >= 0; i--) {
                unplace(i);
            }
            placed.clear();
            placedAs.clear();
            return;
        }
        catchFallers();
        List<Block> blocks = new ArrayList<>(placed);
        List<Material> mats = new ArrayList<>(placedAs);
        placed.clear();
        placedAs.clear();
        int perTick = Math.max(200, blocks.size() / 50);
        int[] cursor = {blocks.size() - 1};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!plugin.isEnabled()) {
                task.cancel();
                return;
            }
            int stop = Math.max(-1, cursor[0] - perTick);
            Block lastBlock = null;
            for (int i = cursor[0]; i > stop; i--) {
                Block b = blocks.get(i);
                if (b.getType() == mats.get(i)) {
                    b.setType(Material.AIR, false);
                    lastBlock = b;
                }
            }
            cursor[0] = stop;
            if (lastBlock != null) {
                Location l = lastBlock.getLocation().add(0.5, 0.5, 0.5);
                world.playSound(l, Sound.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.2f, 0.55f);
                world.spawnParticle(Particle.BLOCK, l, 12, 0.6, 0.6, 0.6, 0, Material.DARK_OAK_PLANKS.createBlockData());
            }
            if (cursor[0] < 0) {
                task.cancel();
            }
        }, 1L, 1L);
    }

    private void unplace(int i) {
        Block b = placed.get(i);
        if (b.getType() == placedAs.get(i)) {
            b.setType(Material.AIR, false);
        }
    }

    private void strikeByLayout() {
        clearTemp();
        for (int i = layout.size() - 1; i >= 0; i--) {
            Cell c = layout.get(i);
            Block b = world.getBlockAt(center.getBlockX() + c.x(), center.getBlockY() + c.y(), center.getBlockZ() + c.z());
            Material now = b.getType();
            if (now == c.data().getMaterial()) {
                b.setType(Material.AIR, false);
            }
        }
        // Temp blocks the crashed fight may have left in the stage volume.
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                for (int y = 0; y <= 5; y++) {
                    Block b = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (b.getType() == Material.BARRIER || b.getType() == Material.LIGHT) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    /** Anyone still standing on the stage floats down instead of dropping. */
    private void catchFallers() {
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            double dx = l.getX() - center.getX();
            double dz = l.getZ() - center.getZ();
            if (dx * dx + dz * dz < 40 * 40 && Math.abs(l.getY() - center.getY()) < 24) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 30, 0, false, false, true));
            }
        }
    }

    /* ------------------------------------------------------------------ temp blocks */

    /** A moving light source. Each named slot owns one LIGHT block placed only into air. */
    void light(String slot, Vector3f stagePos, int level) {
        Block want = stagePos == null ? null
                : world.getBlockAt(center.getBlockX() + (int) Math.floor(stagePos.x + 0.5f),
                center.getBlockY() + (int) Math.floor(stagePos.y),
                center.getBlockZ() + (int) Math.floor(stagePos.z + 0.5f));
        Block old = lights.get(slot);
        if (old != null && old.equals(want)) {
            return;
        }
        if (old != null && old.getType() == Material.LIGHT) {
            old.setType(Material.AIR, false);
        }
        lights.remove(slot);
        if (want == null || !want.getType().isAir()) {
            return;
        }
        BlockData data = Material.LIGHT.createBlockData();
        if (data instanceof Levelled lv) {
            lv.setLevel(Math.max(1, Math.min(15, level)));
        }
        want.setBlockData(data, false);
        lights.put(slot, want);
    }

    /** Make a set of stage-space cells solid (the fallen Hand). Only fills air. */
    void barrier(Vector3f stagePos) {
        Block b = world.getBlockAt(center.getBlockX() + (int) Math.floor(stagePos.x + 0.5f),
                center.getBlockY() + (int) Math.floor(stagePos.y),
                center.getBlockZ() + (int) Math.floor(stagePos.z + 0.5f));
        if (b.getY() < center.getBlockY() || !b.getType().isAir() || barriers.contains(b)) {
            return;
        }
        b.setType(Material.BARRIER, false);
        barriers.add(b);
    }

    void clearBarriers() {
        for (Block b : barriers) {
            if (b.getType() == Material.BARRIER) {
                b.setType(Material.AIR, false);
            }
        }
        barriers.clear();
    }

    void clearTemp() {
        for (Block b : lights.values()) {
            if (b.getType() == Material.LIGHT) {
                b.setType(Material.AIR, false);
            }
        }
        lights.clear();
        clearBarriers();
        for (int i = 0; i < 4; i++) {
            setTrap(i, false);
        }
    }

    boolean trapOpen(int i) {
        return trapOpen[i];
    }

    void setTrap(int i, boolean open) {
        if (world == null) {
            return;
        }
        trapOpen[i] = open;
        int[] s = TRAP_SIGNS[i];
        int[] xs = s[0] > 0 ? new int[]{6, 7} : new int[]{-7, -6};
        int[] zs = s[1] > 0 ? new int[]{6, 7} : new int[]{-7, -6};
        for (int x : xs) {
            for (int z : zs) {
                Block b = world.getBlockAt(center.getBlockX() + x, center.getBlockY() - 1, center.getBlockZ() + z);
                if (b.getBlockData() instanceof TrapDoor door && door.isOpen() != open) {
                    door.setOpen(open);
                    b.setBlockData(door, false);
                }
            }
        }
    }

    private Location at(int x, int y, int z) {
        return new Location(world, center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
    }

    /* ------------------------------------------------------------------ layout */

    private static List<Cell> layout() {
        List<Cell> cells = new ArrayList<>();
        floor(cells);
        keel(cells);
        apron(cells);
        proscenium(cells);
        for (int k = 0; k < 3; k++) {
            balcony(cells, k);
        }
        box(cells, 1);
        box(cells, -1);
        // Unfold from the center outward, bottom-up within a ring.
        cells.sort(Comparator.comparingDouble(Cell::order));
        return cells;
    }

    private static void add(List<Cell> cells, int x, int y, int z, BlockData data) {
        float d = (float) Math.sqrt(x * x + z * z);
        cells.add(new Cell(x, y, z, data, d + (y < -2 ? 0.4f : 0f) + Math.max(0, y) * 0.08f));
    }

    private static void add(List<Cell> cells, int x, int y, int z, Material m) {
        add(cells, x, y, z, m.createBlockData());
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ (h >>> 15)) & 0x7fffffff;
    }

    private static boolean inTrap(int x, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        return (ax == 6 || ax == 7) && (az == 6 || az == 7);
    }

    private static boolean inTrapFrame(int x, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        return ax >= 5 && ax <= 8 && az >= 5 && az <= 8 && !inTrap(x, z);
    }

    private static void floor(List<Cell> cells) {
        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > FLOOR_R) {
                    continue;
                }
                Material top;
                double angle = Math.toDegrees(Math.atan2(x, z));
                if (d > 15.4) {
                    double mod = ((angle % 15) + 15) % 15;
                    top = (mod < 2.2 || mod > 12.8) ? Material.OCHRE_FROGLIGHT : Material.POLISHED_BLACKSTONE_BRICKS;
                } else if (d <= 1.2) {
                    top = Material.GOLD_BLOCK;
                } else if (d <= 2.6) {
                    top = Material.POLISHED_BLACKSTONE;
                } else if (d <= 3.4) {
                    top = Material.CHISELED_POLISHED_BLACKSTONE;
                } else if (Math.abs(d - 7.5) < 0.5 || Math.abs(d - 12.5) < 0.5) {
                    top = Material.POLISHED_BLACKSTONE_BRICKS;
                } else if ((x == 0 || z == 0) && d > 7.5 && d < 12.5) {
                    top = Material.GOLD_BLOCK;
                } else if (x == 0 || z == 0) {
                    top = Material.POLISHED_BLACKSTONE;
                } else if (Math.floorMod(z, 4) == 0) {
                    top = Material.SPRUCE_PLANKS;
                } else {
                    top = Material.DARK_OAK_PLANKS;
                }
                if (inTrapFrame(x, z)) {
                    top = Material.POLISHED_BLACKSTONE_BRICKS;
                }
                if (inTrap(x, z)) {
                    TrapDoor door = (TrapDoor) Material.SPRUCE_TRAPDOOR.createBlockData();
                    door.setHalf(Bisected.Half.TOP);
                    door.setOpen(false);
                    door.setFacing(Math.abs(x) == 6 ? (x > 0 ? BlockFace.EAST : BlockFace.WEST)
                            : (x > 0 ? BlockFace.WEST : BlockFace.EAST));
                    add(cells, x, -1, z, door);
                    add(cells, x, -2, z, Material.SHROOMLIGHT);
                } else {
                    add(cells, x, -1, z, top);
                    add(cells, x, -2, z, d > 15.4 ? Material.POLISHED_BLACKSTONE : Material.BLACKSTONE);
                }
            }
        }
    }

    private static double keelRadius(int dy) {
        double t = (-dy - 2) / 18.0;
        if (t <= 0) {
            return RADIUS;
        }
        if (t >= 1) {
            return 0;
        }
        return RADIUS * (1 - Math.pow(t, 1.35));
    }

    private static void keel(List<Cell> cells) {
        for (int dy = -3; dy >= -20; dy--) {
            double rr = keelRadius(dy);
            int ri = (int) Math.ceil(rr);
            for (int x = -ri; x <= ri; x++) {
                for (int z = -ri; z <= ri; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d > rr || d <= rr - 1.9) {
                        continue;
                    }
                    int h = hash(x, dy, z) % 100;
                    Material m = h < 52 ? Material.BLACKSTONE
                            : h < 72 ? Material.POLISHED_BLACKSTONE
                            : h < 88 ? Material.COBBLED_DEEPSLATE
                            : h < 97 ? Material.DEEPSLATE_TILES
                            : Material.GILDED_BLACKSTONE;
                    add(cells, x, dy, z, m);
                }
            }
        }
        add(cells, 0, -21, 0, Material.BLACKSTONE);
        chainLantern(cells, 0, -22, 0, 7);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int x = (int) Math.round(Math.sin(a) * 10.5);
            int z = (int) Math.round(Math.cos(a) * 10.5);
            double d = Math.sqrt(x * x + z * z);
            int bottom = -3;
            for (int dy = -3; dy >= -20; dy--) {
                if (keelRadius(dy) >= d && d > keelRadius(dy) - 1.9) {
                    bottom = dy;
                }
            }
            chainLantern(cells, x, bottom - 1, z, 3 + (k % 3));
        }
    }

    private static void chainLantern(List<Cell> cells, int x, int topY, int z, int length) {
        for (int i = 0; i < length; i++) {
            add(cells, x, topY - i, z, Material.CHAIN);
        }
        Lantern lantern = (Lantern) Material.LANTERN.createBlockData();
        lantern.setHanging(true);
        add(cells, x, topY - length, z, lantern);
    }

    private static void apron(List<Cell> cells) {
        for (int x = -13; x <= 13; x++) {
            for (int z = -20; z <= -14; z++) {
                if (Math.sqrt(x * x + z * z) <= FLOOR_R) {
                    continue;
                }
                add(cells, x, -1, z, Math.abs(x) == 13 || z == -20 ? Material.POLISHED_BLACKSTONE
                        : Material.POLISHED_BLACKSTONE_BRICKS);
                add(cells, x, -2, z, Material.BLACKSTONE);
            }
        }
    }

    private static int archUnder(int x) {
        return 15 + (int) Math.round(5 * Math.sqrt(Math.max(0, 1 - (x / 12.5) * (x / 12.5))));
    }

    private static void proscenium(List<Cell> cells) {
        int[] pillarXs = {-12, -11, 11, 12};
        for (int x : pillarXs) {
            for (int z = -19; z <= -18; z++) {
                add(cells, x, 0, z, Material.CHISELED_QUARTZ_BLOCK);
                for (int y = 1; y <= 12; y++) {
                    add(cells, x, y, z, Material.QUARTZ_PILLAR);
                }
                add(cells, x, 13, z, Material.CHISELED_QUARTZ_BLOCK);
                add(cells, x, 14, z, Material.GOLD_BLOCK);
                for (int y = 15; y < archUnder(x); y++) {
                    add(cells, x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        for (int x = -12; x <= 12; x++) {
            int u = archUnder(x);
            for (int z = -19; z <= -18; z++) {
                for (int y = u; y <= u + 2; y++) {
                    Material m = x == 0 ? Material.CHISELED_QUARTZ_BLOCK
                            : y == u ? Material.GOLD_BLOCK
                            : Material.POLISHED_BLACKSTONE_BRICKS;
                    add(cells, x, y, z, m);
                }
            }
            if (x == 0) {
                add(cells, 0, u + 3, -19, Material.CHISELED_QUARTZ_BLOCK);
                add(cells, 0, u + 3, -18, Material.CHISELED_QUARTZ_BLOCK);
            }
            // Scalloped valance just in front of the arch.
            if (Math.abs(x) <= 10) {
                int depth = Math.floorMod(x, 4) == 0 ? 2 : 1;
                for (int y = u - 1; y >= u - depth; y--) {
                    add(cells, x, y, -17, Material.RED_WOOL);
                }
            }
        }
        // Drawn curtains: floor-length at the pillars, lifting toward the center.
        for (int side = -1; side <= 1; side += 2) {
            for (int ax = 5; ax <= 10; ax++) {
                int x = ax * side;
                int top = archUnder(x) - 2;
                int bottom = Math.max(0, Math.round((10 - ax) * 1.6f));
                int z = ax % 2 == 0 ? -17 : -16;
                for (int y = bottom; y <= top; y++) {
                    add(cells, x, y, z, Material.RED_WOOL);
                }
            }
            add(cells, 9 * side, 4, -15, Material.GOLD_BLOCK);
            // Spot lamps on the pillar capitals.
            add(cells, 11 * side, 14, -17, Material.SHROOMLIGHT);
        }
        for (int x : new int[]{-7, -3, 3, 7}) {
            int u = archUnder(x);
            chainLantern(cells, x, u - 1, -18, 3 + Math.abs(x) % 3);
        }
    }

    private static void balcony(List<Cell> cells, int tier) {
        double rin = 24 + tier * 3;
        int fy = -5 + tier * 3;
        int reach = (int) Math.ceil(rin + 3.3);
        for (int x = -reach; x <= reach; x++) {
            for (int z = 0; z <= reach; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < rin || d >= rin + 3.2) {
                    continue;
                }
                double angle = Math.toDegrees(Math.atan2(x, z));
                if (Math.abs(angle) > 55) {
                    continue;
                }
                double depth = d - rin;
                boolean edge = Math.abs(angle) > 53;
                add(cells, x, fy, z, depth < 0.9 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DARK_OAK_PLANKS);
                add(cells, x, fy - 1, z, Material.POLISHED_BLACKSTONE);
                if (depth < 0.9 || edge) {
                    double mod = ((angle % 11) + 11) % 11;
                    add(cells, x, fy + 1, z, mod < 1.4 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                } else if (depth < 2.1) {
                    Stairs seat = (Stairs) Material.DARK_OAK_STAIRS.createBlockData();
                    seat.setFacing(outward(x, z));
                    add(cells, x, fy + 1, z, seat);
                } else {
                    boolean stripe = ((int) Math.floor((angle + 60) / 5)) % 2 == 0;
                    add(cells, x, fy + 1, z, Material.DARK_OAK_PLANKS);
                    add(cells, x, fy + 2, z, stripe ? Material.RED_WOOL : Material.DARK_OAK_PLANKS);
                    add(cells, x, fy + 3, z, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        for (int k = -2; k <= 2; k++) {
            double a = Math.toRadians(k * 22);
            int x = (int) Math.round(Math.sin(a) * (rin + 1.5));
            int z = (int) Math.round(Math.cos(a) * (rin + 1.5));
            chainLantern(cells, x, fy - 2, z, 2 + Math.abs(k));
        }
        if (tier == 2) {
            add(cells, 0, fy + 4, (int) Math.round(rin + 2.5), Material.SHROOMLIGHT);
        }
    }

    private static void box(List<Cell> cells, int side) {
        int x0 = 24 * side;
        int fy = 3;
        for (int dx = 0; dx <= 3; dx++) {
            int x = x0 + dx * side;
            for (int z = -7; z <= -1; z++) {
                boolean front = dx == 0;
                boolean back = dx == 3;
                add(cells, x, fy, z, front ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DARK_OAK_PLANKS);
                add(cells, x, fy - 1, z, Material.POLISHED_BLACKSTONE);
                if (front || z == -7 || z == -1) {
                    add(cells, x, fy + 1, z, z == -4 && front ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                } else if (dx == 1 && (z == -5 || z == -3)) {
                    Stairs seat = (Stairs) Material.DARK_OAK_STAIRS.createBlockData();
                    seat.setFacing(side > 0 ? BlockFace.EAST : BlockFace.WEST);
                    add(cells, x, fy + 1, z, seat);
                } else if (back) {
                    for (int y = fy + 1; y <= fy + 4; y++) {
                        add(cells, x, y, z, y == fy + 4 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.RED_WOOL);
                    }
                }
            }
        }
        chainLantern(cells, x0 + side, fy - 2, -4, 3);
    }

    private static BlockFace outward(int x, int z) {
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return z > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }
}
