package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GEOMETRY CAGES. Helios thinks in solids.
 *
 * <ul>
 *   <li><b>Icosahedron</b>: a giant icosahedron of laser edges forms around a player (or the party),
 *       tumbling on two axes, and shrinks. Its faces are wide: slip out through a face while it turns,
 *       before it closes. At the end it collapses into a point and bursts: inside means a heavy hit.</li>
 *   <li><b>Cross</b>: three bars as long as the arena spin around Helios at alternating heights:
 *       amber bars at knee height (jump), cyan bars at head height (duck).</li>
 * </ul>
 * Tell: the edges draw in as hairlines for two beats; they go live on the beat with a glass chord.
 */
final class GeometryCage extends Attack {

    enum Kind { ICOSA, CROSS }

    private static final Color WHITE = Color.fromRGB(240, 245, 255);
    private static final Color AMBER = Color.fromRGB(255, 176, 60);
    private static final Color CYAN = Color.fromRGB(80, 230, 255);
    private static final Vector3f[] ICO = HMath.icosahedron();
    private static final int[][] EDGES = HMath.edges(ICO);

    private final HeliosScript h;
    private Kind kind;
    private final int beats;
    private final Vector3f center = new Vector3f();
    private final List<BlockDisplay> edges = new ArrayList<>();
    private final List<Vector3f[]> live = new ArrayList<>();
    private int tell;
    private int run;
    private float r0;
    private final Quaternionf rot = new Quaternionf();
    private final Vector3f axisA = new Vector3f();
    private final Vector3f axisB = new Vector3f();
    private BlockDisplay core;

    GeometryCage(HeliosScript h, Kind kind, int beats) {
        super(h);
        this.h = h;
        this.kind = kind;
        this.beats = Math.max(4, beats);
    }

    @Override
    public String id() {
        return "cage";
    }

    @Override
    public Family family() {
        return Family.SWEEP;
    }

    @Override
    public void start() {
        if (kind == null) {
            kind = ThreadLocalRandom.current().nextBoolean() ? Kind.ICOSA : Kind.CROSS;
        }
        tell = enc.tempo().ticks(2);
        run = enc.tempo().ticks(beats);
        if (kind == Kind.ICOSA) {
            List<Player> f = enc.fighters();
            if (f.size() == 1) {
                center.set(stage.feet(f.get(0)));
            } else {
                center.set(h.partyCentroid());
            }
            center.y = 1.5f;
            r0 = 10f;
            axisA.set(ThreadLocalRandom.current().nextFloat() - 0.5f, 1f, ThreadLocalRandom.current().nextFloat() - 0.5f).normalize();
            axisB.set(1f, 0.2f, ThreadLocalRandom.current().nextFloat() - 0.5f).normalize();
            for (int i = 0; i < EDGES.length; i++) {
                edges.add(g.block(Material.WHITE_CONCRETE.createBlockData(), WHITE, 15, true));
            }
            core = g.block(Material.WHITE_STAINED_GLASS.createBlockData(), WHITE, 15, true);
            stage.push(core, HeliosStage.gone(center), 0);
        } else {
            center.set(h.rig().center.x, 0f, h.rig().center.z);
            for (int i = 0; i < 3; i++) {
                edges.add(g.block((i % 2 == 0 ? Material.ORANGE_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS).createBlockData(),
                        i % 2 == 0 ? AMBER : CYAN, 15, true));
            }
        }
        for (BlockDisplay d : edges) {
            stage.push(d, HeliosStage.gone(center), 0);
        }
        enc.score().sweep(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.1f, 0.6f, 1.2f, tell, 4);
    }

    @Override
    protected boolean tick() {
        boolean armed = t >= tell;
        if (t == tell) {
            enc.score().chordAt(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f, 1f, 1.26f);
            enc.score().at(center, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
        }
        boolean done = kind == Kind.ICOSA ? icosa(armed) : cross(armed);
        if (armed && t < tell + run) {
            hurt();
        }
        return done;
    }

    private boolean icosa(boolean armed) {
        int end = tell + run;
        float f = HMath.window(t, tell, end);
        float r = t < tell ? r0 : HMath.lerp(r0, 2.2f, HMath.inOutCubic(f));
        rot.set(new Quaternionf().rotateAxis(t * 0.03f, axisA.x, axisA.y, axisA.z).rotateAxis(t * 0.021f, axisB.x, axisB.y, axisB.z));
        live.clear();
        float w = armed ? 0.2f + 0.08f * enc.star().beat() : 0.04f;
        for (int i = 0; i < EDGES.length; i++) {
            Vector3f a = new Quaternionf(rot).transform(new Vector3f(ICO[EDGES[i][0]])).mul(r).add(center);
            Vector3f b = new Quaternionf(rot).transform(new Vector3f(ICO[EDGES[i][1]])).mul(r).add(center);
            live.add(new Vector3f[]{a, b});
            if (t % 2 == 0 || t == tell) {
                stage.push(edges.get(i), HeliosStage.beam(a, b, w), 2);
            }
        }
        if (t == end) {
            // Collapse into a point and burst.
            for (BlockDisplay d : edges) {
                stage.push(d, HeliosStage.gone(center), 3);
            }
            stage.push(core, HeliosStage.cube(center, 0.2f, new Quaternionf()), 0);
            stage.push(core, HeliosStage.cube(center, 5f, new Quaternionf()), 4);
            enc.score().at(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.4f);
            enc.score().at(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
            double power = h.power("cage", 40) * 1.8;
            for (Player p : enc.fighters()) {
                if (stage.feet(p).add(0f, 1f, 0f).distance(center) < 3.2f) {
                    enc.hit(p, power, "cage_burst", 10, h.away(center, stage.feet(p), 1.0));
                }
            }
        }
        if (t == end + 5) {
            stage.push(core, HeliosStage.gone(center), 4);
        }
        return t > end + 10;
    }

    private boolean cross(boolean armed) {
        int end = tell + run;
        float spin = t < tell ? 0f : HMath.inOutCubic(HMath.window(t, tell, end)) * HMath.TAU * 0.9f;
        live.clear();
        float w = armed ? 0.3f + 0.1f * enc.star().beat() : 0.05f;
        for (int i = 0; i < edges.size(); i++) {
            float a = spin + i * HMath.PI / 3f;
            float y = i % 2 == 0 ? 0.5f : 2.0f;
            Vector3f p0 = new Vector3f(center).add(HMath.ring(33f, a, y));
            Vector3f p1 = new Vector3f(center).add(HMath.ring(33f, a + HMath.PI, y));
            live.add(new Vector3f[]{p0, p1});
            stage.push(edges.get(i), HeliosStage.beam(p0, p1, w), 1);
        }
        if (t == end) {
            for (BlockDisplay d : edges) {
                stage.push(d, HeliosStage.gone(center), 4);
            }
        }
        return t > end + 6;
    }

    private void hurt() {
        double power = h.power("cage", 40);
        for (Player p : enc.fighters()) {
            Vector3f f = stage.feet(p);
            float height = (float) p.getBoundingBox().getHeight();
            for (int i = 0; i < live.size(); i++) {
                Vector3f[] e = live.get(i);
                if (kind == Kind.CROSS) {
                    boolean low = i % 2 == 0;
                    boolean vertical = low ? f.y < 0.8f : f.y + height > 1.65f && f.y < 2.4f;
                    Vector3f flat = new Vector3f(f.x, e[0].y, f.z);
                    if (vertical && HMath.segmentDistSq(flat, e[0], e[1]) < 0.5f * 0.5f) {
                        enc.hit(p, power, "cage_bar", 10, null);
                        break;
                    }
                } else if (HMath.bodyDistSq(f, height, e[0], e[1]) < 0.45f * 0.45f) {
                    enc.hit(p, power, "cage_edge", 10, h.away(center, f, 0.4));
                    break;
                }
            }
        }
    }
}
