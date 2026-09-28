package de.aetherion.bossengine.instance.saint;

import org.bukkit.Color;
import org.bukkit.Material;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Hand Above: the puppeteer. A colossal ivory hand reaching down out of the clouds,
 * forearm vanishing into the sky, one gold band at the wrist and a ring on the middle finger.
 *
 * <p>Root = wrist. Hangs fingers-down; the palm faces local -Z and fingers curl toward it.
 * Each fingertip carries one of Seraphine's threads, so every tug on her limbs is visible as
 * a flex of the finger that owns it.
 */
final class HandBody {

    static final Color IVORY_GLOW = Color.fromRGB(255, 236, 190);
    /** Wrist to fingertip, relaxed. */
    static final float REACH = 6.0f + 2.8f + 2.3f + 1.95f;

    /** 0 index, 1 middle, 2 ring, 3 pinky, 4 thumb. */
    static final int INDEX = 0;
    static final int MIDDLE = 1;
    static final int RING = 2;
    static final int PINKY = 3;
    static final int THUMB = 4;

    final Skeleton rig = new Skeleton();
    final Skeleton.Bone arm;
    final Skeleton.Bone wrist;
    final Skeleton.Bone[][] finger = new Skeleton.Bone[5][];

    HandBody() {
        Material skin = Material.SMOOTH_SANDSTONE;
        Material gold = Material.GOLD_BLOCK;
        arm = rig.bone(null, 0, 0, 0);
        wrist = rig.bone(arm, 0, 0, 0);

        rig.box(arm, skin, "arm", 0, 20f, 0.3f, 4.8f, 40f, 4.2f);
        rig.box(arm, gold, "arm", 0, 1.1f, 0.3f, 5.5f, 1.3f, 4.9f);
        rig.box(arm, gold, "arm", 0, 2.3f, 0.3f, 5.3f, 0.35f, 4.7f);
        rig.box(wrist, skin, "palm", 0, -3.0f, 0, 6.6f, 6.0f, 2.5f);
        rig.box(wrist, skin, "palm", 0, -5.7f, 0.1f, 6.9f, 0.9f, 2.6f);

        float[] fx = {2.4f, 0.8f, -0.8f, -2.4f};
        float[] len = {1.0f, 1.08f, 1.0f, 0.82f};
        for (int f = 0; f < 4; f++) {
            float l = len[f];
            Skeleton.Bone prox = rig.bone(wrist, fx[f], -6.0f, 0);
            Skeleton.Bone mid = rig.bone(prox, 0, -2.8f * l, 0);
            Skeleton.Bone dist = rig.bone(mid, 0, -2.3f * l, 0);
            finger[f] = new Skeleton.Bone[]{prox, mid, dist};
            rig.box(prox, skin, "finger", 0, -1.4f * l, 0, 1.45f, 2.9f * l, 1.6f);
            rig.box(mid, skin, "finger", 0, -1.15f * l, 0, 1.35f, 2.4f * l, 1.5f);
            rig.box(dist, skin, "finger", 0, -0.95f * l, 0, 1.25f, 1.95f * l, 1.4f);
            if (f == MIDDLE) {
                rig.box(prox, gold, "ring", 0, -1.8f * l, 0, 1.62f, 0.45f, 1.76f);
            }
        }
        Skeleton.Bone t1 = rig.bone(wrist, 3.2f, -2.0f, -0.6f, new Quaternionf().rotateZ(0.6f).rotateX(0.3f));
        Skeleton.Bone t2 = rig.bone(t1, 0, -2.3f, 0);
        finger[THUMB] = new Skeleton.Bone[]{t1, t2};
        rig.box(t1, skin, "finger", 0, -1.15f, 0, 1.6f, 2.4f, 1.7f);
        rig.box(t2, skin, "finger", 0, -1.0f, 0, 1.45f, 2.0f, 1.55f);

        for (Skeleton.Piece p : rig.pieces()) {
            p.brightness = 12;
        }
        for (Skeleton.Bone b : rig.bones()) {
            b.spring(0.16f, 0.24f);
        }
        arm.spring(0.1f, 0.2f);
        wrist.spring(0.12f, 0.22f);
    }

    private float flexAmount;
    private float thumbCurl = 0.12f;
    private final float[] fingerCurl = new float[4];

    /**
     * Curl one finger, 0 = open, 1 = fully closed toward the palm. With the palm flat on the
     * stage a relaxed curl would dig into the boards, so flexing straightens the fingers.
     */
    void curl(int f, float amount) {
        Skeleton.Bone[] bones = finger[f];
        if (f == THUMB) {
            thumbCurl = amount;
            applyThumb();
            return;
        }
        fingerCurl[f] = amount;
        float a = Math.max(-0.05f, amount - flexAmount * 0.14f);
        bones[0].target.x = a * 1.35f;
        bones[1].target.x = a * 1.55f;
        bones[2].target.x = a * 1.2f;
    }

    /** The thumb rests angled toward the palm; with the palm flat it lifts into the palm plane. */
    private void applyThumb() {
        Skeleton.Bone[] bones = finger[THUMB];
        bones[0].target.x = thumbCurl * 0.9f - flexAmount * 0.45f;
        bones[0].target.z = -thumbCurl * 0.6f;
        bones[1].target.x = thumbCurl * 1.1f * (1f - flexAmount * 0.7f);
    }

    /** Fan fingers apart (splayed palm slam, pinned hand). Index fans toward +X, pinky toward -X. */
    void spread(float amount) {
        float[] fan = {0.22f, 0.07f, -0.07f, -0.22f};
        for (int f = 0; f < 4; f++) {
            finger[f][0].target.z = fan[f] * amount * 2f;
        }
    }

    void fist() {
        for (int f = 0; f < 5; f++) {
            curl(f, 1f);
        }
        spread(0f);
    }

    void open() {
        for (int f = 0; f < 5; f++) {
            curl(f, 0.12f);
        }
        spread(0.3f);
    }

    /** Wrist flex: 0 = hanging, 1 = palm flat to the ground, fingers forward. */
    void flex(float amount) {
        flexAmount = amount;
        wrist.target.x = -SaintMath.HALF_PI * amount;
        for (int f = 0; f < 4; f++) {
            curl(f, fingerCurl[f]);
        }
        applyThumb();
    }

    Vector3f fingertip(int f) {
        Skeleton.Bone tip = finger[f][finger[f].length - 1];
        float l = f == THUMB ? 2.0f : 1.95f * (f == MIDDLE ? 1.08f : f == PINKY ? 0.82f : 1.0f);
        return rig.point(tip, 0, -l, 0);
    }

    Vector3f palmCenter() {
        return rig.point(wrist, 0, -3.0f, 0);
    }

    /** Point where the palm meets the ground in a flat slam (palm underside center). */
    Vector3f palmUnder() {
        return rig.point(wrist, 0, -3.5f, -1.25f);
    }
}
