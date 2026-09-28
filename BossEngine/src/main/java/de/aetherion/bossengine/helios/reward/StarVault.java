package de.aetherion.bossengine.helios.reward;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Score;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.HeliosEncounter;
import de.aetherion.bossengine.helios.world.Arena;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.BlockDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * THE STAR VAULT: the reliquary's body, born out of the dying star.
 *
 * <pre>
 *   BIRTH    the pulsar's point swells and bursts: the vault's plates fly out of the dead star and lock
 *            together around a white-hot seed, two star-matter halos spin up
 *   DESCENT  it sinks from where the star was to float over the middle of the pit
 *   STAIRS   beat by beat the star builds the way up to it: first a dais of light under it, then one step
 *            at a time four staircases grow outward (north, east, south, west) until they touch the Crown.
 *            Every step is star-matter flying out of the vault and turning into real stone on the beat.
 *   OPEN     the reliquary ({@link StarseedReliquary}) lifts the lid and the capsules come out onto the dais
 * </pre>
 *
 * The staircases are real blocks (you walk up them) placed only inside the pit, where the arena layout has
 * no cells, so the arena restore never touches them. {@link #clear()} removes every block and display; the
 * encounter calls it on every exit (victory, abort, leave, disable), and the slot wipe catches a crash.
 */
public final class StarVault {

    /** Where the vault floats: over the middle of the pit, above the dais. */
    public static final Vector3f REST = new Vector3f(0f, 4.7f, 0f);
    /** The dais' blocks sit at this stage height; you stand on {@link #DAIS_TOP}. */
    public static final int DAIS_Y = 2;
    public static final float DAIS_TOP = DAIS_Y + 1f;
    public static final float DAIS_R = 3.3f;

    private static final Color GOLD = Color.fromRGB(255, 205, 90);
    private static final Color SEED = Color.fromRGB(255, 250, 230);
    private static final int BIRTH = 26;
    private static final int DESCENT = 56;
    private static final int[] STEP_R = {4, 5, 6, 7, 8};
    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private final HeliosEncounter enc;
    private final HeliosStage stage;
    private final HeliosStage.Group group;
    private final Vector3f from = new Vector3f();
    private final Vector3f center = new Vector3f();
    private final List<Part> parts = new ArrayList<>();
    private final Shapes.Ring haloA;
    private final Shapes.Ring haloB;
    private final Shapes.Line tether;
    private final List<BlockDisplay> shards = new ArrayList<>();
    private final List<Vector3f> shardTo = new ArrayList<>();
    private final List<int[]> placed = new ArrayList<>();
    private final List<Object[]> waiting = new ArrayList<>();

    private int t = -1;
    private int stairsAt = -1;
    private int stepLen = 12;
    private int layer = -1;
    private boolean stairsDone;
    private float open;
    private float openTarget;
    private boolean cleared;

    private static final class Part {
        final BlockDisplay d;
        final Vector3f off;
        final Vector3f size;
        final Vector3f burst;

        Part(BlockDisplay d, Vector3f off, Vector3f size, Vector3f burst) {
            this.d = d;
            this.off = off;
            this.size = size;
            this.burst = burst;
        }
    }

    public StarVault(HeliosEncounter enc) {
        this.enc = enc;
        this.stage = enc.stage();
        this.group = stage.group();
        part(Material.GOLD_BLOCK, 0f, -0.5f, 0f, 1.5f, 0.22f, 1.05f, 13);
        part(Material.WHITE_STAINED_GLASS, 0f, 0f, 0f, 1.25f, 0.8f, 0.85f, 15);
        part(Material.PEARLESCENT_FROGLIGHT, 0f, 0f, 0f, 0.42f, 0.42f, 0.42f, 15);
        part(Material.GOLD_BLOCK, 0f, 0.5f, 0f, 1.58f, 0.18f, 1.12f, 15);
        part(Material.GOLD_BLOCK, 0f, 0.66f, 0f, 0.5f, 0.12f, 0.5f, 15);
        for (int i = 0; i < 4; i++) {
            float sx = (i & 1) == 0 ? -1f : 1f;
            float sz = (i & 2) == 0 ? -1f : 1f;
            part(Material.GOLD_BLOCK, sx * 0.64f, 0f, sz * 0.44f, 0.1f, 0.9f, 0.1f, 13);
        }
        for (int i = 0; i < 6; i++) {
            float a = i * HMath.TAU / 6f;
            part(Material.GOLD_BLOCK, (float) Math.cos(a) * 0.3f, 0.95f, (float) Math.sin(a) * 0.3f, 0.06f, 0.5f, 0.06f, 15);
        }
        haloA = new Shapes.Ring(stage, group, 16, Material.YELLOW_STAINED_GLASS, GOLD, 15, true);
        haloB = new Shapes.Ring(stage, group, 12, Material.WHITE_STAINED_GLASS, SEED, 15, true);
        tether = new Shapes.Line(stage, group, Material.WHITE_CONCRETE, SEED);
        Vector3f o = new Vector3f(REST);
        haloA.hide(o, 0);
        haloB.hide(o, 0);
        tether.hide(o, 0);
        for (int i = 0; i < 12; i++) {
            BlockDisplay d = group.block(Material.PEARLESCENT_FROGLIGHT.createBlockData(), SEED, 15, true);
            stage.push(d, HeliosStage.gone(o), 0);
            shards.add(d);
            shardTo.add(new Vector3f(o));
        }
        center.set(REST);
    }

    private void part(Material m, float x, float y, float z, float sx, float sy, float sz, int bright) {
        BlockDisplay d = group.block(m.createBlockData(), m == Material.PEARLESCENT_FROGLIGHT ? SEED : null, bright, true);
        int i = parts.size();
        Vector3f burst = new Vector3f(HMath.hash(i, 3) - 0.5f, HMath.hash(i, 5) - 0.5f, HMath.hash(i, 7) - 0.5f).normalize().mul(3.5f);
        parts.add(new Part(d, new Vector3f(x, y, z), new Vector3f(sx, sy, sz), burst));
        stage.push(d, HeliosStage.gone(REST), 0);
    }

    /* ================================================================== control */

    /** The vault is born at {@code at} (the dying star) and starts its descent and the stairs on its own. */
    public void birth(Vector3f at) {
        if (t >= 0 || cleared) {
            return;
        }
        from.set(at);
        center.set(at);
        t = 0;
        for (Part p : parts) {
            stage.push(p.d, HeliosStage.gone(at), 0);
        }
        haloA.hide(at, 0);
        haloB.hide(at, 0);
        tether.hide(at, 0);
        Score score = enc.score();
        score.play(Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.2f, 0.6f, true);
        score.play(Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.2f, 0.6f, true);
        score.play(Sound.ITEM_TOTEM_USE, 0.6f, 1.3f, true);
        score.chord(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, Score.semi(-5), Score.semi(0), Score.semi(4), Score.semi(7));
        enc.camera().flash(1, 6);
        stage.particle(Particle.END_ROD, at, 40, 1.2, 0.15);
    }

    public boolean born() {
        return t >= 0;
    }

    public boolean settled() {
        return t >= BIRTH + DESCENT;
    }

    public boolean stairsDone() {
        return stairsDone;
    }

    /** The chest's center (stage). */
    public Vector3f center() {
        return new Vector3f(center);
    }

    /** Lid and seed: 0 closed … 1 open (the reliquary drives it while the capsules come out). */
    public void open(float f) {
        openTarget = HMath.clamp01(f);
    }

    /* ================================================================== tick */

    public void tick() {
        if (t < 0 || cleared) {
            return;
        }
        t++;
        open = HMath.lerp(open, openTarget, 0.15f);
        float assemble;
        if (t <= BIRTH) {
            // Plates burst out of the star, then lock together around the seed.
            float f = HMath.window(t, 0, BIRTH);
            // 0..6 the plates fly out of the star; then they lock together (a little overshoot).
            assemble = t < 6 ? -HMath.outCubic(t / 6f) : HMath.outBack(HMath.window(t, 6, BIRTH));
            center.set(from);
            if (t == 6) {
                enc.score().play(Sound.BLOCK_VAULT_ACTIVATE, 1.2f, 0.8f, true);
                enc.score().play(Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f, true);
            }
            if (t == BIRTH) {
                enc.score().chordAt(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, Score.semi(0), Score.semi(4), Score.semi(7), Score.semi(12));
                enc.score().at(center, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 1.2f, 1.2f);
            }
            pose(assemble, f, 1);
        } else if (t <= BIRTH + DESCENT) {
            float f = HMath.inOutCubic(HMath.window(t, BIRTH, BIRTH + DESCENT));
            center.set(from).lerp(REST, f);
            center.y += (float) Math.sin(f * HMath.PI) * 0.8f;
            pose(1f, 1f, HeliosStage.SMOOTH_1);
            tether.set(from, new Vector3f(center).add(0f, 0.9f, 0f), 0.05f * (1f - f) + 0.01f, HeliosStage.SMOOTH_1);
            if ((t - BIRTH) % 10 == 0) {
                enc.score().at(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.7f + 0.6f * f);
            }
            if (t == BIRTH + DESCENT) {
                tether.hide(center, 6);
                enc.score().at(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.2f);
                enc.score().at(center, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1f, 1.2f);
                stairsAt = t + 8;
            }
        } else {
            center.set(REST);
            center.y += 0.12f * (float) Math.sin(t * 0.07f);
            pose(1f, 1f, HeliosStage.SMOOTH_1);
        }
        tickStairs();
        retryWaiting();
    }

    private void pose(float assemble, float grow, int interp) {
        float spin = t * 0.03f;
        Quaternionf yaw = new Quaternionf().rotateY(spin);
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            Vector3f off = new Vector3f(p.off);
            Vector3f size = new Vector3f(p.size);
            // Lid, crown and rays lift with the opening; the seed rises and swells.
            if (i == 3 || i == 4 || i >= 9) {
                off.y += open * 0.9f;
            }
            if (i == 2) {
                off.y += open * 0.6f;
                size.mul(1f + 0.5f * open + 0.12f * HMath.heartbeat(t / 20f));
            }
            Vector3f at = yaw.transform(new Vector3f(off)).add(center);
            if (assemble != 1f) {
                // assemble < 0: still flying out (scatter grows); 0..1: coming back together.
                float out = assemble < 0f ? -assemble : 1f - assemble;
                at.add(new Vector3f(p.burst).mul(out));
            }
            size.mul(Math.max(0.001f, Math.min(1f, grow * 1.2f)));
            Quaternionf rot = new Quaternionf(yaw);
            if (assemble != 1f) {
                float out = assemble < 0f ? -assemble : 1f - assemble;
                rot.rotateXYZ(out * 4f * HMath.hash(i, 11), out * 3f, 0f);
            }
            stage.push(p.d, HeliosStage.box(at, size, rot), interp);
        }
        float hs = Math.max(0.05f, grow);
        haloA.pose(center, new Quaternionf().rotateX(0.35f).rotateY(t * 0.02f), 1.35f * hs, 0.06f, 0.06f, t * 0.05f, 0f, 0f, interp);
        haloB.pose(center, new Quaternionf().rotateZ(1.1f).rotateY(-t * 0.03f), 1.05f * hs, 0.04f, 0.04f, -t * 0.07f, 0f, 0f, interp);
    }

    /* ================================================================== stairs */

    private void tickStairs() {
        if (stairsAt < 0 || stairsDone || t < stairsAt) {
            return;
        }
        int local = t - stairsAt;
        int want = local / stepLen;
        int in = local % stepLen;
        if (in == 0 && want != layer && want <= STEP_R.length) {
            layer = want;
            launch(layer);
        }
        if (in >= 1 && in <= 8) {
            // Star-matter in flight: from the vault's belly to its block, on a shallow arc.
            float f = HMath.outCubic(HMath.window(in, 0, 8));
            Vector3f start = new Vector3f(center).add(0f, -0.4f, 0f);
            for (int i = 0; i < shards.size(); i++) {
                Vector3f to = shardTo.get(i);
                Vector3f mid = new Vector3f(start).lerp(to, 0.5f).add(0f, 1.6f, 0f);
                Vector3f at = HMath.bezier(start, mid, to, f, new Vector3f());
                stage.push(shards.get(i), HeliosStage.cube(at, 0.28f + 0.3f * f, new Quaternionf().rotateXYZ(in * 0.4f, in * 0.3f, 0f)), 1);
            }
        }
        if (in == 8) {
            land(layer);
        }
        if (in == 9) {
            for (int i = 0; i < shards.size(); i++) {
                stage.push(shards.get(i), HeliosStage.gone(shardTo.get(i)), 4);
            }
            if (layer >= STEP_R.length) {
                stairsDone = true;
                finale();
            }
        }
    }

    /** Where this layer's star-matter goes: the dais ring (layer 0), else one step on each of the four stairs. */
    private void launch(int layer) {
        if (layer == 0) {
            for (int i = 0; i < shards.size(); i++) {
                float a = i * HMath.TAU / shards.size();
                shardTo.get(i).set(HMath.ring(i % 2 == 0 ? 2.6f : 1.4f, a, DAIS_TOP - 0.2f));
            }
        } else {
            int r = STEP_R[layer - 1];
            int k = 0;
            for (int[] d : DIRS) {
                for (int off = -1; off <= 1; off++) {
                    int dx = d[0] * r + (d[0] == 0 ? off : 0);
                    int dz = d[1] * r + (d[1] == 0 ? off : 0);
                    shardTo.get(k++).set(dx, stepY(r) + 0.6f, dz);
                }
            }
        }
        enc.score().at(center, Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 0.8f + layer * 0.12f);
        enc.score().at(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.8f + layer * 0.1f);
    }

    /** The beat lands: this layer turns into real stone. */
    private void land(int layer) {
        World w = enc.world();
        Arena arena = enc.arena();
        if (layer == 0) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    // Block (dx, dz) is centred on stage point (dx, dz): the stage anchor is the centre block's middle.
                    float r = (float) Math.sqrt(dx * dx + dz * dz);
                    if (r > DAIS_R + 0.2f) {
                        continue;
                    }
                    Material m = r < 1.3f ? Material.GOLD_BLOCK : r < 2.6f ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS;
                    put(w, arena, dx, DAIS_Y, dz, m.createBlockData());
                }
            }
            enc.score().chordAt(new Vector3f(0f, DAIS_TOP, 0f), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, Score.semi(-12), Score.semi(-5), Score.semi(0));
            enc.score().at(new Vector3f(0f, DAIS_TOP, 0f), Sound.BLOCK_VAULT_PLACE, 1.2f, 0.7f);
            stage.particle(Particle.END_ROD, new Vector3f(0f, DAIS_TOP + 0.2f, 0f), 30, 1.6, 0.02);
            enc.camera().shakeFrom(new Vector3f(0f, DAIS_TOP, 0f), 24f, 4);
            return;
        }
        int r = STEP_R[layer - 1];
        for (int[] d : DIRS) {
            BlockFace up = face(-d[0], -d[1]);
            for (int off = -1; off <= 1; off++) {
                int dx = d[0] * r + (d[0] == 0 ? off : 0);
                int dz = d[1] * r + (d[1] == 0 ? off : 0);
                BlockData data;
                if (r <= 5) {
                    data = (off == 0 ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS).createBlockData();
                } else {
                    data = Material.SMOOTH_QUARTZ_STAIRS.createBlockData();
                    if (data instanceof Stairs st) {
                        st.setFacing(up);
                        st.setHalf(Bisected.Half.BOTTOM);
                        st.setShape(Stairs.Shape.STRAIGHT);
                    }
                }
                put(w, arena, dx, stepY(r), dz, data);
            }
            Vector3f at = new Vector3f(d[0] * r, stepY(r) + 1f, d[1] * r);
            stage.particle(Particle.END_ROD, at, 6, 0.6, 0.01);
        }
        // Each step one note higher: the way up is a scale.
        int[] scale = {0, 2, 4, 7, 9, 12};
        enc.score().chord(Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, Score.semi(scale[Math.min(layer, scale.length - 1)]),
                Score.semi(scale[Math.min(layer, scale.length - 1)] - 12));
        enc.score().play(Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1f, 0.6f + 0.1f * layer);
        enc.score().play(Sound.BLOCK_DEEPSLATE_BRICKS_PLACE, 0.8f, 0.7f);
    }

    private void finale() {
        enc.score().chord(Sound.BLOCK_NOTE_BLOCK_BELL, 1f, Score.semi(0), Score.semi(4), Score.semi(7), Score.semi(12));
        enc.score().play(Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        enc.score().play(Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 0.8f);
        enc.camera().flash(0, 5);
        for (int[] d : DIRS) {
            stage.particle(Particle.END_ROD, new Vector3f(d[0] * 8.5f, 1.2f, d[1] * 8.5f), 12, 0.6, 0.02);
        }
        for (org.bukkit.entity.Player p : enc.audience()) {
            p.sendActionBar(de.aetherion.bossengine.util.TextUtil.component("&6The way up is open. &eClimb to the reliquary."));
        }
    }

    /** Height of the block for a step at radius r: stairs climb 0 → 2, then the bridge and dais at 2. */
    private static int stepY(int r) {
        return switch (r) {
            case 8 -> 0;
            case 7 -> 1;
            default -> DAIS_Y;
        };
    }

    private static BlockFace face(int dx, int dz) {
        if (dx > 0) {
            return BlockFace.EAST;
        }
        if (dx < 0) {
            return BlockFace.WEST;
        }
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private void put(World w, Arena arena, int dx, int dy, int dz, BlockData data) {
        if (arena.occupied(dx, dy, dz)) {
            waiting.add(new Object[]{dx, dy, dz, data});
            return;
        }
        Block b = w.getBlockAt(arena.cx() + dx, arena.cy() + dy, arena.cz() + dz);
        if (!b.getType().isAir()) {
            return;
        }
        b.setBlockData(data, false);
        placed.add(new int[]{dx, dy, dz});
    }

    private void retryWaiting() {
        if (waiting.isEmpty() || t % 5 != 0) {
            return;
        }
        List<Object[]> again = new ArrayList<>(waiting);
        waiting.clear();
        for (Object[] o : again) {
            put(enc.world(), enc.arena(), (int) o[0], (int) o[1], (int) o[2], (BlockData) o[3]);
        }
    }

    /* ================================================================== cleanup */

    /** Removes the stairs, the dais and every display. Idempotent; safe on every exit path. */
    public void clear() {
        if (cleared) {
            return;
        }
        cleared = true;
        waiting.clear();
        Arena arena = enc.arena();
        World w = enc.world();
        if (arena != null && w != null) {
            for (int[] p : placed) {
                Block b = w.getBlockAt(arena.cx() + p[0], arena.cy() + p[1], arena.cz() + p[2]);
                if (!b.getType().isAir()) {
                    b.setType(Material.AIR, false);
                }
            }
        }
        placed.clear();
        group.clear();
    }
}
