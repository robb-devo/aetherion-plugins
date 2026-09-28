package de.aetherion.bossengine.helios.star;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.core.Tempo;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The dying star over the pit. Built only from geometry:
 *
 * <pre>
 *   heart         three white-hot froglight cubes, each turning on its own axis: a rounded, boiling core
 *   photosphere   four orange glass cubes counter-rotating around it
 *   chromosphere  four red glass cubes, larger, turning the other way
 *   corona        three thin segmented rings on tilted, precessing planes
 *   debris        a belt of arena rock on inclined Kepler orbits (inner rocks run faster)
 * </pre>
 *
 * Its heartbeat is the fight's {@link Tempo}: every beat the heart swells ("lub"), then a smaller "dub".
 * Scripts drive its moods (dormant, burning, agitated), crack it, tear it open, collapse it and finally
 * hand its debris out as meteors. Pushed every second tick (the odd ones, so it never shares a packet
 * burst with the bodies) with a 3-tick interpolation: one tick of overlap, so a late packet never freezes
 * the belt. Spin changes glide instead of jumping, so the orbiting rock never lurches.
 */
public final class DyingStar {

    public static final Vector3f CENTER = new Vector3f(0f, 10f, 0f);

    public static final Color GOLD = Color.fromRGB(255, 196, 70);
    public static final Color EMBER = Color.fromRGB(255, 110, 40);
    public static final Color WHITE_HOT = Color.fromRGB(255, 244, 214);

    private static final Material[] ROCKS = {
            Material.BLACKSTONE, Material.BASALT, Material.MAGMA_BLOCK, Material.GILDED_BLACKSTONE,
            Material.DEEPSLATE_TILES, Material.POLISHED_BLACKSTONE_BRICKS
    };

    private final HeliosStage stage;
    private final Tempo tempo;
    private final HeliosStage.Group group;
    private final BlockDisplay[] heart = new BlockDisplay[3];
    private final BlockDisplay[] photo = new BlockDisplay[4];
    private final BlockDisplay[] chromo = new BlockDisplay[4];
    private final Shapes.Ring[] corona = new Shapes.Ring[3];
    private final List<Debris> debris = new ArrayList<>();
    private final List<BlockDisplay> seams = new ArrayList<>();
    private final Vector3f center = new Vector3f(CENTER);

    private int clock;
    /** Accumulated spin phase. (It used to be clock * spinRate: any spin change made the whole star jump.) */
    private float phase;
    private float scale = 0f;
    private float scaleTarget = 1f;
    private float scaleRate = 0.02f;
    private float pulseAmp = 0.35f;
    private float spinRate = 1f;
    private float spinTarget = 1f;
    private float coronaRadius = 1f;
    private float open;
    private float openTarget;
    private float crack;
    private boolean heartVisible = true;
    private boolean shellsVisible = true;
    private boolean coronaVisible = true;
    private int interp = HeliosStage.SMOOTH_2;

    public DyingStar(HeliosStage stage, Tempo tempo) {
        this.stage = stage;
        this.tempo = tempo;
        this.group = stage.group();
        build();
    }

    private void build() {
        for (int i = 0; i < heart.length; i++) {
            heart[i] = group.block(Material.PEARLESCENT_FROGLIGHT.createBlockData(), WHITE_HOT, 15, true);
        }
        for (int i = 0; i < photo.length; i++) {
            photo[i] = group.block(Material.ORANGE_STAINED_GLASS.createBlockData(), null, 15, true);
        }
        for (int i = 0; i < chromo.length; i++) {
            chromo[i] = group.block(Material.RED_STAINED_GLASS.createBlockData(), null, 15, true);
        }
        int segs = stage.budget().scaled(22, 12);
        Material[] ringMat = {Material.YELLOW_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.OCHRE_FROGLIGHT};
        for (int i = 0; i < corona.length; i++) {
            corona[i] = new Shapes.Ring(stage, group, segs, ringMat[i], i == 2 ? GOLD : null, 15, true);
        }
        int rocks = stage.budget().scaled(22, 10);
        for (int i = 0; i < rocks; i++) {
            float h = HMath.hash(i, 11);
            Debris d = new Debris();
            d.r = 11f + h * 7f;
            d.incl = (HMath.hash(i, 23) - 0.5f) * 0.9f;
            d.node = HMath.hash(i, 37) * HMath.TAU;
            d.theta = HMath.hash(i, 41) * HMath.TAU;
            d.size = 0.5f + HMath.hash(i, 53) * 0.9f;
            d.spin = (HMath.hash(i, 61) - 0.5f) * 0.2f;
            Material m = ROCKS[(int) (HMath.hash(i, 71) * ROCKS.length) % ROCKS.length];
            d.display = group.block(m.createBlockData(), null, m == Material.MAGMA_BLOCK ? 15 : 10, true);
            debris.add(d);
        }
    }

    /* ================================================================== controls */

    public Vector3f center() {
        return new Vector3f(center);
    }

    public void center(Vector3f at) {
        center.set(at);
    }

    /** Size of the whole star (1 = normal), eased at {@code rate} per tick. */
    public void scale(float target, float rate) {
        this.scaleTarget = Math.max(0f, target);
        this.scaleRate = Math.max(0.001f, rate);
    }

    public void scaleNow(float s) {
        this.scale = s;
        this.scaleTarget = s;
    }

    public float scale() {
        return scale;
    }

    public void pulse(float amp) {
        this.pulseAmp = amp;
    }

    /** Target spin; the star eases into it (a jump would make every orbit lurch). */
    public void spin(float rate) {
        this.spinTarget = rate;
    }

    public void spinNow(float rate) {
        this.spinRate = rate;
        this.spinTarget = rate;
    }

    public void coronaRadius(float r) {
        this.coronaRadius = r;
    }

    /** 0 = whole, 1 = torn open (shell halves pushed apart). */
    public void open(float target) {
        this.openTarget = HMath.clamp01(target);
    }

    /** Glowing seams across the photosphere, 0..1. */
    public void crack(float amount) {
        this.crack = HMath.clamp01(amount);
    }

    public void heartVisible(boolean on) {
        this.heartVisible = on;
    }

    public void shellsVisible(boolean on) {
        this.shellsVisible = on;
    }

    public void coronaVisible(boolean on) {
        this.coronaVisible = on;
    }

    public void interp(int ticks) {
        this.interp = Math.max(1, ticks);
    }

    public void heartMaterial(Material m) {
        for (BlockDisplay d : heart) {
            HeliosStage.material(d, m);
        }
    }

    public void shellMaterial(Material photoMat, Material chromoMat) {
        for (BlockDisplay d : photo) {
            HeliosStage.material(d, photoMat);
        }
        for (BlockDisplay d : chromo) {
            HeliosStage.material(d, chromoMat);
        }
    }

    public void heartGlow(Color c) {
        for (BlockDisplay d : heart) {
            HeliosStage.glow(d, c);
        }
    }

    /** How strongly the heart is swelling right now (0..1), for sounds and light synced to it. */
    public float beat() {
        return HMath.heartbeat(tempo.phase());
    }

    /* ================================================================== debris hand-out */

    public int debrisLeft() {
        int n = 0;
        for (Debris d : debris) {
            if (!d.taken) {
                n++;
            }
        }
        return n;
    }

    /**
     * Takes one rock out of the belt: the caller now owns and moves it (meteors, the singularity).
     *
     * @return the rock's display and where it is now, or null when the belt is empty
     */
    public Taken take() {
        for (Debris d : debris) {
            if (!d.taken && d.display != null && d.display.isValid()) {
                d.taken = true;
                return new Taken(d.display, position(d, new Vector3f()), d.size);
            }
        }
        return null;
    }

    public record Taken(BlockDisplay display, Vector3f at, float size) {
    }

    /* ================================================================== tick */

    public void tick() {
        clock++;
        if (spinRate != spinTarget) {
            float d = spinTarget - spinRate;
            spinRate = Math.abs(d) <= 0.02f ? spinTarget : spinRate + Math.signum(d) * 0.02f;
        }
        phase += 0.05f * spinRate;
        if (scale != scaleTarget) {
            float d = scaleTarget - scale;
            scale = Math.abs(d) <= scaleRate ? scaleTarget : scale + Math.signum(d) * scaleRate;
        }
        if (open != openTarget) {
            float d = openTarget - open;
            open = Math.abs(d) <= 0.015f ? openTarget : open + Math.signum(d) * 0.015f;
        }
        for (Debris d : debris) {
            if (!d.taken) {
                d.theta += HMath.orbitSpeed(d.r, 1.3f) * spinRate;
                d.tumble += d.spin * spinRate;
            }
        }
        if (clock % 2 == 1) {
            render();
        }
    }

    private void render() {
        float t = phase;
        float beat = HMath.heartbeat(tempo.phase()) * pulseAmp;
        float s = scale;
        for (int i = 0; i < heart.length; i++) {
            Quaternionf rot = new Quaternionf().rotateXYZ(t * (0.9f + i * 0.4f), t * (1.3f - i * 0.3f), t * 0.7f * (i - 1));
            float size = heartVisible ? 1.9f * s * (1f + 0.35f * beat) : 0.001f;
            stage.push(heart[i], HeliosStage.cube(center, size, rot), interp);
        }
        for (int i = 0; i < photo.length; i++) {
            Quaternionf rot = new Quaternionf().rotateXYZ(-t * 0.5f + i * 0.8f, -t * (0.6f + i * 0.15f), t * 0.3f + i);
            float size = shellsVisible ? 3.7f * s * (1f + 0.12f * beat) : 0.001f;
            Vector3f at = splitOffset(i, 3.5f);
            stage.push(photo[i], HeliosStage.cube(at, size, rot), interp);
        }
        for (int i = 0; i < chromo.length; i++) {
            Quaternionf rot = new Quaternionf().rotateXYZ(t * 0.35f + i * 1.1f, t * (0.4f + i * 0.1f), -t * 0.25f + i * 0.6f);
            float size = shellsVisible ? 5.3f * s * (1f + 0.07f * beat) : 0.001f;
            Vector3f at = splitOffset(i, 5.5f);
            stage.push(chromo[i], HeliosStage.cube(at, size, rot), interp);
        }
        float[] radii = {6.2f, 7.5f, 8.8f};
        float[] tilt = {0.35f, -0.55f, 0.95f};
        for (int i = 0; i < corona.length; i++) {
            Quaternionf orient = new Quaternionf().rotateY(t * 0.2f * (i + 1)).rotateX(tilt[i]).rotateZ(t * 0.07f * (i - 1));
            float r = coronaVisible ? radii[i] * s * coronaRadius : 0.01f;
            float thick = 0.12f + 0.05f * i;
            corona[i].pose(center, orient, r, thick, 0.12f, t * (i % 2 == 0 ? 1.1f : -0.8f), 0f, 0f, interp);
        }
        for (Debris d : debris) {
            if (d.taken) {
                continue;
            }
            Vector3f p = position(d, new Vector3f());
            Quaternionf rot = new Quaternionf().rotateXYZ(d.tumble, d.tumble * 0.7f, d.tumble * 1.3f);
            stage.push(d.display, HeliosStage.cube(p, d.size * Math.max(0.001f, Math.min(1f, s * 1.2f)), rot), interp);
        }
        renderSeams(t);
    }

    /** Photosphere/chromosphere halves drift apart along a tilted axis when the star tears open. */
    private Vector3f splitOffset(int i, float dist) {
        if (open <= 0f) {
            return new Vector3f(center);
        }
        float sign = (i % 2 == 0) ? 1f : -1f;
        Vector3f axis = new Vector3f(0.85f, 0.25f, 0.45f).normalize();
        return new Vector3f(axis).mul(sign * HMath.outCubic(open) * dist * scale).add(center);
    }

    private void renderSeams(float t) {
        int want = crack <= 0f ? 0 : 6;
        while (seams.size() < want) {
            BlockDisplay d = group.block(Material.WHITE_CONCRETE.createBlockData(), WHITE_HOT, 15, true);
            if (d == null) {
                break;
            }
            seams.add(d);
        }
        for (int i = 0; i < seams.size(); i++) {
            if (crack <= 0f) {
                stage.push(seams.get(i), HeliosStage.gone(center), interp);
                continue;
            }
            // A seam is a thin hot plank lying on the photosphere, growing with the crack.
            float a = i * HMath.TAU / seams.size() + t * 0.1f;
            Vector3f dir = new Vector3f((float) Math.cos(a), (float) Math.sin(a * 1.7f) * 0.6f, (float) Math.sin(a)).normalize();
            Vector3f from = new Vector3f(dir).mul(1.95f * scale).add(center);
            Vector3f tangent = new Vector3f(dir).cross(0f, 1f, 0f).normalize();
            if (tangent.lengthSquared() < 1e-4f) {
                tangent.set(1f, 0f, 0f);
            }
            Vector3f to = new Vector3f(from).add(new Vector3f(tangent).mul(3.2f * crack * scale));
            stage.push(seams.get(i), HeliosStage.plank(from, to, 0.08f + 0.1f * crack, 0.05f, dir), interp);
        }
    }

    private Vector3f position(Debris d, Vector3f out) {
        out.set((float) Math.cos(d.theta) * d.r, 0f, (float) Math.sin(d.theta) * d.r);
        new Quaternionf().rotateY(d.node).rotateX(d.incl).transform(out);
        return out.add(center);
    }

    /** Removes everything the star spawned. */
    public void clear() {
        group.clear();
        seams.clear();
        debris.clear();
    }

    private static final class Debris {
        BlockDisplay display;
        float r;
        float incl;
        float node;
        float theta;
        float size;
        float spin;
        float tumble;
        boolean taken;
    }
}
