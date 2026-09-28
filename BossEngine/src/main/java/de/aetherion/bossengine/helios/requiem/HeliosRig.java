package de.aetherion.bossengine.helios.requiem;

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
 * HELIOS: what the star became. No limbs, no armor: an armillary sphere of four gold rings turning on
 * their own axes around a white-hot heart, and in front of it a porcelain mask with a gold brow,
 * two burning eye slits, gold tears and a crown of seven rays.
 *
 * <p>The four rings are the Herald's blades: each ring still carries its blade, riding the ring like
 * the hand of a clock. The rig can draw its rings in (interlude), crack its mask, turn its heart black
 * (singularity) and collapse into a point (death).
 */
public final class HeliosRig {

    public static final Color HEART = Color.fromRGB(255, 236, 190);
    public static final Color GOLD = Color.fromRGB(255, 205, 90);
    public static final Color RIM = Color.fromRGB(235, 240, 255);

    private static final float[] RING_R = {2.3f, 2.8f, 3.3f, 3.8f};

    private final HeliosStage stage;
    private final Tempo tempo;
    private final HeliosStage.Group group;

    private final BlockDisplay[] heart = new BlockDisplay[3];
    private final Shapes.Ring[] rings = new Shapes.Ring[4];
    private final BlockDisplay[] blades = new BlockDisplay[4];
    private final List<BlockDisplay> mask = new ArrayList<>();
    private final List<Vector3f> maskOffset = new ArrayList<>();
    private final List<Vector3f> maskSize = new ArrayList<>();
    private final List<Quaternionf> maskRot = new ArrayList<>();
    private final List<BlockDisplay> cracks = new ArrayList<>();

    /** Heart position in stage space. */
    public final Vector3f center = new Vector3f(0f, 10f, 0f);
    /** Where the mask looks (yaw on XZ, pitch). */
    public float yaw;
    public float pitch;
    private float ringScale = 1f;
    private float ringScaleTarget = 1f;
    private float spin = 1f;
    private final float[] ringDrawn = {1f, 1f, 1f, 1f};
    private float maskAssembled = 1f;
    private float crack;
    private float collapse;
    private boolean black;
    private boolean visible = true;
    private float heartSize = 1f;
    private float t;
    private final float[] ringAngle = new float[4];

    public HeliosRig(HeliosStage stage, Tempo tempo) {
        this.stage = stage;
        this.tempo = tempo;
        this.group = stage.group();
        build();
    }

    private void build() {
        heart[0] = group.block(Material.PEARLESCENT_FROGLIGHT.createBlockData(), HEART, 15, true);
        heart[1] = group.block(Material.OCHRE_FROGLIGHT.createBlockData(), null, 15, true);
        heart[2] = group.block(Material.ORANGE_STAINED_GLASS.createBlockData(), null, 15, true);
        int segs = stage.budget().scaled(16, 10);
        for (int i = 0; i < rings.length; i++) {
            rings[i] = new Shapes.Ring(stage, group, segs, Material.GOLD_BLOCK, i == 0 ? GOLD : null, 13, true);
            blades[i] = group.block(Material.SMOOTH_QUARTZ.createBlockData(), GOLD, 15, true);
        }
        // The mask: face, cheeks, brow, eyes, tears, mouth, crown.
        maskPart(Material.SMOOTH_QUARTZ, 0f, 0f, 0f, 1.3f, 1.7f, 0.14f, 0f, 0f, null, 14);
        maskPart(Material.CALCITE, -0.74f, -0.05f, -0.12f, 0.36f, 1.3f, 0.12f, 0f, 0.55f, null, 14);
        maskPart(Material.CALCITE, 0.74f, -0.05f, -0.12f, 0.36f, 1.3f, 0.12f, 0f, -0.55f, null, 14);
        maskPart(Material.GOLD_BLOCK, 0f, 0.38f, 0.06f, 1.46f, 0.16f, 0.18f, 0f, 0f, null, 14);
        maskPart(Material.WHITE_CONCRETE, -0.3f, 0.18f, 0.09f, 0.34f, 0.07f, 0.03f, 0f, 0f, GOLD, 15);
        maskPart(Material.WHITE_CONCRETE, 0.3f, 0.18f, 0.09f, 0.34f, 0.07f, 0.03f, 0f, 0f, GOLD, 15);
        maskPart(Material.GOLD_BLOCK, -0.3f, -0.18f, 0.085f, 0.04f, 0.5f, 0.03f, 0f, 0f, null, 15);
        maskPart(Material.GOLD_BLOCK, 0.3f, -0.18f, 0.085f, 0.04f, 0.5f, 0.03f, 0f, 0f, null, 15);
        maskPart(Material.BLACK_CONCRETE, 0f, -0.52f, 0.08f, 0.42f, 0.04f, 0.03f, 0f, 0f, null, -1);
        for (int i = 0; i < 7; i++) {
            float a = (i - 3) * 0.33f;
            float len = 1.45f - Math.abs(i - 3) * 0.17f;
            Vector3f base = new Vector3f(0f, 0.8f, -0.1f);
            Vector3f dir = new Vector3f(-(float) Math.sin(a), (float) Math.cos(a), 0f);
            Vector3f mid = new Vector3f(dir).mul(len * 0.5f + 0.1f).add(base);
            maskPart(Material.GOLD_BLOCK, mid.x, mid.y, mid.z, 0.07f, len, 0.07f, a, 0f, GOLD, 15);
        }
    }

    private void maskPart(Material m, float x, float y, float z, float sx, float sy, float sz, float roll, float yawOff, Color glow, int bright) {
        BlockDisplay d = group.block(m.createBlockData(), glow, bright, true);
        mask.add(d);
        maskOffset.add(new Vector3f(x, y, z));
        maskSize.add(new Vector3f(sx, sy, sz));
        maskRot.add(new Quaternionf().rotateY(yawOff).rotateZ(roll));
    }

    /* ================================================================== controls */

    public void lookAt(Vector3f p, float maxStep) {
        Vector3f d = new Vector3f(p).sub(center);
        float wantYaw = HMath.angleOf(d.x, d.z);
        float wantPitch = (float) Math.atan2(d.y, Math.max(0.1f, HMath.horizontal(d)));
        yaw = HMath.approachAngle(yaw, wantYaw, maxStep);
        pitch = HMath.approachAngle(pitch, HMath.clamp(wantPitch, -0.8f, 0.6f), maxStep);
    }

    public void ringScale(float s) {
        this.ringScaleTarget = s;
    }

    public void spin(float s) {
        this.spin = s;
    }

    /** Interlude: how much of ring {@code i} is drawn (0..1). */
    public void ringDrawn(int i, float f) {
        ringDrawn[i] = HMath.clamp01(f);
    }

    public void maskAssembled(float f) {
        maskAssembled = HMath.clamp01(f);
    }

    public void crack(float f) {
        crack = HMath.clamp01(f);
    }

    /** Death: 0 whole … 1 everything pulled into the heart. */
    public void collapse(float f) {
        collapse = HMath.clamp01(f);
    }

    public void black(boolean on) {
        if (black == on) {
            return;
        }
        black = on;
        HeliosStage.material(heart[0], on ? Material.BLACK_CONCRETE : Material.PEARLESCENT_FROGLIGHT);
        HeliosStage.glow(heart[0], on ? RIM : HEART);
        HeliosStage.material(heart[1], on ? Material.CRYING_OBSIDIAN : Material.OCHRE_FROGLIGHT);
        HeliosStage.material(heart[2], on ? Material.PURPLE_STAINED_GLASS : Material.ORANGE_STAINED_GLASS);
    }

    public void heartSize(float s) {
        this.heartSize = s;
    }

    public void visible(boolean on) {
        this.visible = on;
    }

    /** Stage point of blade {@code i}'s tip right now (for attacks that launch from the rings). */
    public Vector3f bladePoint(int i) {
        return ringPoint(i, ringAngle[i]);
    }

    public Vector3f maskPoint() {
        return new Vector3f(forward()).mul(1.7f * (1f - collapse)).add(center).add(0f, 0.3f, 0f);
    }

    public Vector3f forward() {
        return new Vector3f((float) (Math.cos(yaw) * Math.cos(pitch)), (float) Math.sin(pitch), (float) (Math.sin(yaw) * Math.cos(pitch)));
    }

    /* ================================================================== render */

    public void render(int interp) {
        t += 0.05f * spin;
        ringScale = HMath.lerp(ringScale, ringScaleTarget, 0.08f);
        float beat = HMath.heartbeat(tempo.phase());
        float c = 1f - collapse;
        float hs = heartSize * (1f + 0.25f * beat) * Math.max(0.05f, c);
        if (!visible) {
            for (BlockDisplay d : heart) {
                stage.push(d, HeliosStage.gone(center), interp);
            }
        } else {
            stage.push(heart[0], HeliosStage.cube(center, 0.8f * hs, new Quaternionf().rotateXYZ(t * 1.3f, t, t * 0.7f)), interp);
            stage.push(heart[1], HeliosStage.cube(center, 1.0f * hs, new Quaternionf().rotateXYZ(-t, t * 0.8f, t * 0.4f)), interp);
            stage.push(heart[2], HeliosStage.cube(center, 1.45f * hs, new Quaternionf().rotateXYZ(t * 0.5f, -t * 0.6f, t * 0.9f)), interp);
        }
        for (int i = 0; i < rings.length; i++) {
            Quaternionf orient = ringOrient(i);
            float r = RING_R[i] * ringScale * c;
            ringAngle[i] = t * (1.4f - i * 0.25f) * (i % 2 == 0 ? 1f : -1f);
            if (!visible || ringDrawn[i] <= 0.001f || r < 0.05f) {
                rings[i].hide(center, interp);
                stage.push(blades[i], HeliosStage.gone(center), interp);
                continue;
            }
            float from = ringAngle[i];
            rings[i].arc(center, orient, r, 0.1f, 0.1f, from, from + HMath.TAU * ringDrawn[i], interp);
            // The Herald's blade rides its ring like a clock hand, edge outward.
            Vector3f at = ringPoint(i, from + HMath.TAU * ringDrawn[i]);
            Vector3f tangent = new Quaternionf(orient).transform(new Vector3f(-(float) Math.sin(from), 0f, (float) Math.cos(from)));
            Quaternionf rot = HMath.alignY(tangent);
            stage.push(blades[i], HeliosStage.box(at, new Vector3f(0.14f, 1.9f * c, 0.035f), rot), interp);
        }
        renderMask(interp);
    }

    private Quaternionf ringOrient(int i) {
        return switch (i) {
            case 0 -> new Quaternionf().rotateX(HMath.HALF_PI + 0.3f * (float) Math.sin(t * 0.3f)).rotateY(t * 0.4f);
            case 1 -> new Quaternionf().rotateZ(HMath.HALF_PI * 0.7f).rotateX(t * 0.5f);
            case 2 -> new Quaternionf().rotateY(t * 0.25f).rotateX(0.35f);
            default -> new Quaternionf().rotateY(-t * 0.35f).rotateZ(1.1f + 0.2f * (float) Math.sin(t * 0.2f));
        };
    }

    private Vector3f ringPoint(int i, float a) {
        float r = RING_R[i] * ringScale * (1f - collapse);
        return ringOrient(i).transform(new Vector3f((float) Math.cos(a) * r, 0f, (float) Math.sin(a) * r)).add(center);
    }

    private void renderMask(int interp) {
        Quaternionf face = new Quaternionf().rotateY(-yaw + HMath.HALF_PI).rotateX(-pitch);
        Vector3f anchor = maskPoint();
        for (int i = 0; i < mask.size(); i++) {
            BlockDisplay d = mask.get(i);
            if (!visible) {
                stage.push(d, HeliosStage.gone(anchor), interp);
                continue;
            }
            Vector3f off = new Vector3f(maskOffset.get(i));
            Vector3f size = new Vector3f(maskSize.get(i));
            Vector3f at = new Quaternionf(face).transform(new Vector3f(off)).add(anchor);
            Quaternionf rot = new Quaternionf(face).mul(maskRot.get(i));
            if (maskAssembled < 1f) {
                // Plates fly in from the heart, the crown last.
                float order = i / (float) mask.size();
                float f = HMath.inOutCubic(HMath.clamp01((maskAssembled - order * 0.6f) / 0.4f));
                at = new Vector3f(center).lerp(at, f);
                size.mul(Math.max(0.001f, f));
            }
            if (collapse > 0f) {
                float order = 1f - i / (float) mask.size();
                float f = HMath.inCubic(HMath.clamp01((collapse - order * 0.4f) / 0.6f));
                at.lerp(center, f);
                size.mul(1f - f * 0.98f);
            }
            stage.push(d, HeliosStage.box(at, size, rot), interp);
        }
        renderCracks(face, anchor, interp);
    }

    private void renderCracks(Quaternionf face, Vector3f anchor, int interp) {
        int want = crack <= 0f ? 0 : 5;
        while (cracks.size() < want) {
            BlockDisplay d = group.block(Material.BLACK_CONCRETE.createBlockData(), null, 0, true);
            if (d == null) {
                break;
            }
            cracks.add(d);
        }
        for (int i = 0; i < cracks.size(); i++) {
            if (crack <= 0f || !visible) {
                stage.push(cracks.get(i), HeliosStage.gone(anchor), interp);
                continue;
            }
            // Jagged lines running from the brow down across the porcelain.
            float x = -0.5f + i * 0.25f;
            Vector3f a = new Vector3f(x, 0.35f, 0.09f);
            Vector3f b = new Vector3f(x + (HMath.hash(i, 3) - 0.5f) * 0.5f, 0.35f - 1.2f * crack * (0.5f + HMath.hash(i, 7) * 0.5f), 0.09f);
            Vector3f wa = new Quaternionf(face).transform(a).add(anchor);
            Vector3f wb = new Quaternionf(face).transform(b).add(anchor);
            stage.push(cracks.get(i), HeliosStage.beam(wa, wb, 0.035f), interp);
        }
    }

    public void clear() {
        group.clear();
    }
}
