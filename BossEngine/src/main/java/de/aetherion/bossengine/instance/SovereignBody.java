package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.instance.worldeater.WeFx;
import de.aetherion.bossengine.instance.worldeater.WeMath;
import de.aetherion.bossengine.instance.worldeater.WeRig;
import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Aetherion's own body: a dragon of ash and ember built from displays on a spring rig, flown by
 * {@link AshenSovereignDirector} in place of the vanilla Ender Dragon. Blackstone scales, a belly
 * of live embers, basalt horns, black glass wings with burning trailing edges. The wings beat with
 * its speed and fold when it lands, it banks into turns, the neck carries the head toward whoever
 * it watches, the jaw opens for breath and roars, the tail trails every turn. A broken guard (or
 * the death) throws it down sprawled. Block displays only: it needs no pack.
 */
final class SovereignBody {

    private static final Material SCALE = Material.BLACKSTONE;
    private static final Material SCALE_DARK = Material.POLISHED_BLACKSTONE;
    private static final Material HORN = Material.BASALT;
    private static final Material EMBER = Material.MAGMA_BLOCK;
    private static final Material MEMBRANE = Material.BLACK_STAINED_GLASS;
    private static final Color EYE = Color.fromRGB(255, 122, 32);
    /** Feet to body center at scale 1. */
    private static final float STANCE = 2.6f;

    private final WeFx fx;
    private final WeRig rig = new WeRig();
    private final WeRig.Bone chest;
    private final WeRig.Bone hips;
    private final WeRig.Bone[] neck = new WeRig.Bone[3];
    private final WeRig.Bone head;
    private final WeRig.Bone jaw;
    private final WeRig.Bone wingL;
    private final WeRig.Bone tipL;
    private final WeRig.Bone wingR;
    private final WeRig.Bone tipR;
    private final WeRig.Bone[] tail = new WeRig.Bone[4];
    private final WeRig.Bone[] legs = new WeRig.Bone[4];
    private float flap;
    private float lastYaw = Float.NaN;
    private float turn;
    private int clock;
    private boolean spawned;

    SovereignBody(Location anchor, BossKeys keys, UUID owner) {
        fx = new WeFx(anchor, keys, owner);
        chest = rig.bone(null, 0f, 0f, 0.9f);
        hips = rig.bone(null, 0f, -0.1f, -1.4f);
        neck[0] = rig.bone(chest, 0f, 0.5f, 1.5f);
        neck[1] = rig.bone(neck[0], 0f, 0.25f, 1.3f);
        neck[2] = rig.bone(neck[1], 0f, 0.2f, 1.2f);
        head = rig.bone(neck[2], 0f, 0.1f, 1.15f);
        jaw = rig.bone(head, 0f, -0.3f, 0.35f);
        wingL = rig.bone(chest, 1.1f, 0.8f, 0.1f);
        tipL = rig.bone(wingL, 3.4f, 0f, 0f);
        wingR = rig.bone(chest, -1.1f, 0.8f, 0.1f);
        tipR = rig.bone(wingR, -3.4f, 0f, 0f);
        tail[0] = rig.bone(hips, 0f, 0f, -1.6f);
        tail[1] = rig.bone(tail[0], 0f, -0.05f, -1.5f);
        tail[2] = rig.bone(tail[1], 0f, -0.05f, -1.35f);
        tail[3] = rig.bone(tail[2], 0f, 0f, -1.2f);
        legs[0] = rig.bone(chest, 0.85f, -0.9f, 0.3f);
        legs[1] = rig.bone(chest, -0.85f, -0.9f, 0.3f);
        legs[2] = rig.bone(hips, 0.95f, -0.85f, -0.1f);
        legs[3] = rig.bone(hips, -0.95f, -0.85f, -0.1f);
        build();
        springs();
    }

    /* ================================================================== the model */

    private void build() {
        Quaternionf none = new Quaternionf();
        // Chest and hips: scales over a belly of embers, a dorsal ridge of basalt spines.
        rig.box(chest, SCALE, "body", 0f, 0f, 0f, 2.6f, 2.0f, 2.6f);
        rig.box(chest, EMBER, "ember", 0f, -1.02f, 0.1f, 1.8f, 0.12f, 2.2f);
        rig.box(chest, SCALE_DARK, "body", 0f, 1.05f, 0f, 0.5f, 0.35f, 2.2f);
        for (float z : new float[] {-0.8f, 0f, 0.8f}) {
            rig.box(chest, HORN, "spine", 0f, 1.4f, z, 0.18f, 0.65f, 0.18f, new Quaternionf().rotateX(-0.5f));
        }
        rig.box(hips, SCALE, "body", 0f, 0f, 0f, 2.2f, 1.7f, 2.2f);
        rig.box(hips, EMBER, "ember", 0f, -0.86f, 0f, 1.4f, 0.1f, 1.8f);
        rig.box(hips, HORN, "spine", 0f, 1.1f, 0f, 0.16f, 0.55f, 0.16f, new Quaternionf().rotateX(-0.5f));
        // Neck: three plates narrowing to the head, embers along the throat.
        float[][] neckSize = {{1.4f, 1.3f, 1.6f}, {1.2f, 1.1f, 1.5f}, {1.05f, 1.0f, 1.4f}};
        for (int i = 0; i < 3; i++) {
            float[] s = neckSize[i];
            rig.box(neck[i], SCALE, "body", 0f, 0f, 0.5f, s[0], s[1], s[2]);
            rig.box(neck[i], EMBER, "ember", 0f, -s[1] * 0.5f - 0.03f, 0.5f, s[0] * 0.6f, 0.07f, s[2] * 0.85f);
            rig.box(neck[i], HORN, "spine", 0f, s[1] * 0.5f + 0.25f, 0.4f, 0.14f, 0.5f, 0.14f, new Quaternionf().rotateX(-0.6f));
        }
        // Head: skull, snout, brow ridges, swept horns, burning eyes, fangs, a glowing gullet.
        rig.box(head, SCALE, "body", 0f, 0.05f, 0.55f, 1.5f, 1.05f, 1.6f);
        rig.box(head, SCALE_DARK, "body", 0f, -0.05f, 1.75f, 1.05f, 0.65f, 1.3f);
        rig.box(head, EMBER, "mouth", 0f, -0.38f, 1.5f, 0.85f, 0.05f, 1.4f);
        for (float side : new float[] {1f, -1f}) {
            rig.box(head, HORN, "horn", 0.5f * side, 0.55f, 0.9f, 0.3f, 0.2f, 1.0f);
            rig.box(head, HORN, "horn", 0.55f * side, 0.8f, -0.4f, 0.22f, 0.22f, 1.7f,
                    new Quaternionf().rotateY(0.22f * side).rotateX(0.5f));
            rig.box(head, HORN, "horn", 0.62f * side, 0.2f, -0.2f, 0.14f, 0.14f, 1.0f,
                    new Quaternionf().rotateY(0.5f * side).rotateX(0.25f));
            rig.box(head, Material.SHROOMLIGHT, "eye", 0.63f * side, 0.25f, 1.15f, 0.12f, 0.14f, 0.32f, none);
            rig.box(head, Material.BONE_BLOCK, "fang", 0.36f * side, -0.45f, 2.2f, 0.12f, 0.28f, 0.12f, none);
        }
        rig.box(jaw, SCALE, "body", 0f, -0.18f, 1.2f, 0.95f, 0.35f, 1.9f);
        rig.box(jaw, EMBER, "mouth", 0f, 0.0f, 1.2f, 0.75f, 0.04f, 1.6f);
        for (float side : new float[] {1f, -1f}) {
            rig.box(jaw, Material.BONE_BLOCK, "fang", 0.33f * side, 0.12f, 1.9f, 0.1f, 0.24f, 0.1f, none);
        }
        // Wings: a basalt-dark arm and finger, black glass membrane, a burning trailing edge, a thumb claw.
        wing(wingL, tipL, 1f);
        wing(wingR, tipR, -1f);
        // Tail: four plates narrowing to a blade.
        float[][] tailSize = {{1.3f, 1.1f, 1.7f}, {1.0f, 0.9f, 1.6f}, {0.75f, 0.7f, 1.45f}, {0.5f, 0.5f, 1.3f}};
        for (int i = 0; i < 4; i++) {
            float[] s = tailSize[i];
            rig.box(tail[i], SCALE, "body", 0f, 0f, -s[2] * 0.48f, s[0], s[1], s[2]);
            rig.box(tail[i], HORN, "spine", 0f, s[1] * 0.5f + 0.2f, -s[2] * 0.4f, 0.12f, 0.4f, 0.12f, new Quaternionf().rotateX(-0.6f));
        }
        rig.box(tail[3], HORN, "horn", 0f, 0.1f, -1.5f, 0.1f, 0.75f, 0.95f, new Quaternionf().rotateX(0.3f));
        rig.box(tail[3], EMBER, "ember", 0f, -0.26f, -0.6f, 0.3f, 0.04f, 1.1f);
        // Legs: thick scaled limbs, broad feet, three basalt claws each.
        for (WeRig.Bone leg : legs) {
            rig.box(leg, SCALE, "leg", 0f, -0.7f, 0f, 0.55f, 1.5f, 0.6f);
            rig.box(leg, SCALE_DARK, "leg", 0f, -1.45f, 0.25f, 0.62f, 0.25f, 0.95f);
            for (float x : new float[] {-0.2f, 0f, 0.2f}) {
                rig.box(leg, HORN, "claw", x, -1.5f, 0.78f, 0.1f, 0.12f, 0.35f, new Quaternionf().rotateX(0.5f));
            }
        }
        for (WeRig.Piece p : rig.group("eye")) {
            rig.setGlow(p, EYE);
        }
    }

    private void wing(WeRig.Bone arm, WeRig.Bone tip, float side) {
        rig.box(arm, SCALE_DARK, "wing", 1.7f * side, 0f, 0f, 3.4f, 0.28f, 0.32f);
        rig.box(arm, MEMBRANE, "membrane", 1.7f * side, 0f, -1.5f, 3.4f, 0.05f, 2.9f);
        rig.box(arm, EMBER, "ember", 1.7f * side, 0f, -2.95f, 3.4f, 0.04f, 0.1f);
        rig.box(tip, SCALE_DARK, "wing", 1.6f * side, 0f, 0f, 3.2f, 0.22f, 0.26f);
        rig.box(tip, MEMBRANE, "membrane", 1.5f * side, 0f, -1.25f, 3.0f, 0.05f, 2.5f);
        rig.box(tip, EMBER, "ember", 1.5f * side, 0f, -2.5f, 3.0f, 0.04f, 0.1f);
        rig.box(tip, HORN, "claw", 0f, 0.28f, 0.15f, 0.15f, 0.55f, 0.15f, new Quaternionf().rotateX(-0.4f));
    }

    private void springs() {
        for (WeRig.Bone b : rig.bones()) {
            b.spring(0.2f, 0.3f);
        }
        for (WeRig.Bone b : new WeRig.Bone[] {wingL, wingR, tipL, tipR}) {
            b.spring(0.34f, 0.3f);
        }
        for (WeRig.Bone b : tail) {
            b.spring(0.1f, 0.2f);
        }
        jaw.spring(0.4f, 0.4f);
    }

    /* ================================================================== lifecycle */

    void spawn() {
        rig.spawn(fx);
        spawned = true;
    }

    boolean spawned() {
        return spawned;
    }

    void remove() {
        rig.remove(fx);
        fx.clear();
        spawned = false;
    }

    /* ================================================================== motion */

    /**
     * One frame.
     *
     * @param feet     where it stands (or would, in the air)
     * @param heading  the way it faces
     * @param vel      this tick's flight velocity (banking, pitch, the wing beat)
     * @param grounded wings folded, standing
     * @param look     where the head turns to, or null
     * @param jawOpen  0 shut .. 1 roaring / breathing
     * @param sprawled thrown down: guard broken or dying
     */
    void tick(Location feet, Vector heading, Vector vel, boolean grounded, Location look, float jawOpen, boolean sprawled) {
        if (!spawned) {
            return;
        }
        clock++;
        float yaw = (float) Math.atan2(heading.getX(), heading.getZ());
        if (Float.isNaN(lastYaw)) {
            lastYaw = yaw;
        }
        turn = turn * 0.8f + WeMath.wrap(yaw - lastYaw) * 0.2f;
        lastYaw = yaw;
        float speed = (float) vel.length();
        boolean airborne = !grounded && !sprawled;

        Vector3f f = new Vector3f((float) heading.getX(), 0f, (float) heading.getZ());
        if (f.lengthSquared() < 1e-4f) {
            f.set(0f, 0f, 1f);
        }
        float pitch = airborne ? WeMath.clamp((float) -vel.getY() * 0.7f, -0.45f, 0.45f) : 0f;
        float bank = airborne ? WeMath.clamp(-turn * 7f, -0.55f, 0.55f) : 0f;
        rig.tilt.set(WeMath.frame(f, WeMath.UP)).rotateX(pitch).rotateZ(bank);
        float stance = sprawled ? 1.1f : STANCE;
        rig.rootPos.set(fx.stage(feet)).add(0f, stance, 0f);

        if (airborne) {
            flap += 0.2f + Math.min(0.25f, speed * 0.3f);
            float a = (float) Math.sin(flap);
            float b = (float) Math.sin(flap - 0.9f);
            wingL.aim(0f, 0.15f, 0.1f + 0.6f * a);
            wingR.aim(0f, -0.15f, -0.1f - 0.6f * a);
            tipL.aim(0f, 0f, 0.05f + 0.45f * b);
            tipR.aim(0f, 0f, -0.05f - 0.45f * b);
        } else if (sprawled) {
            wingL.aim(0f, 0.25f, -0.3f);
            wingR.aim(0f, -0.25f, 0.3f);
            tipL.aim(0f, 0.35f, -0.12f);
            tipR.aim(0f, -0.35f, 0.12f);
        } else {
            float breathe = (float) Math.sin(clock * 0.06) * 0.04f;
            wingL.aim(0f, 1.15f, -0.35f + breathe);
            wingR.aim(0f, -1.15f, 0.35f - breathe);
            tipL.aim(0f, 2.5f, 0f);
            tipR.aim(0f, -2.5f, 0f);
        }

        for (int i = 0; i < legs.length; i++) {
            float step = grounded && speed > 0.03f ? (float) Math.sin(clock * 0.35 + (i % 2 == 0 ? 0 : Math.PI) + (i >= 2 ? Math.PI : 0)) * 0.35f : 0f;
            legs[i].aim(airborne ? 0.95f : sprawled ? -0.6f : step, 0f, 0f);
        }

        // The neck carries the look: a quarter to each neck plate, the last quarter in the head.
        float lookYaw = 0f;
        float lookPitch = 0f;
        if (look != null && look.getWorld() == fx.world()) {
            Vector3f to = fx.stage(look).sub(rig.point(neck[2], 0f, 0f, 0f));
            Quaternionf inv = rig.rootRot().conjugate();
            inv.transform(to);
            lookYaw = WeMath.clamp((float) Math.atan2(to.x, to.z), -1.2f, 1.2f);
            float flat = (float) Math.sqrt(to.x * to.x + to.z * to.z);
            lookPitch = WeMath.clamp((float) Math.atan2(-to.y, flat), -0.8f, 0.9f);
        }
        float raise = sprawled ? 0.5f : grounded ? -0.4f : 0f;
        for (WeRig.Bone n : neck) {
            n.aim(lookPitch * 0.25f + raise / 3f, lookYaw * 0.25f, 0f);
        }
        head.aim(lookPitch * 0.25f - raise, lookYaw * 0.25f, 0f);
        jaw.aim(WeMath.clamp01(jawOpen) * 0.7f, 0f, 0f);

        for (int i = 0; i < tail.length; i++) {
            float sway = (float) Math.sin(clock * 0.09 + i * 0.7) * 0.1f;
            tail[i].aim(airborne ? pitch * 0.2f : sprawled ? 0.05f : -0.05f, -turn * 2.5f + sway, 0f);
        }

        rig.step(1f);
        rig.render();
    }

    /* ================================================================== points */

    Location snout() {
        return fx.at(rig.point(head, 0f, -0.1f, 2.3f));
    }

    Location neckPoint() {
        return fx.at(rig.point(neck[1], 0f, 0f, 0.5f));
    }

    Location chestPoint() {
        return fx.at(rig.point(chest, 0f, 0f, 0f));
    }

    /** The heart hangs under the chest, in the ember belly. */
    Location heart() {
        return fx.at(rig.point(chest, 0f, -0.85f, 1.0f));
    }

    Location tailPoint(int i) {
        return fx.at(rig.point(tail[Math.max(0, Math.min(3, i))], 0f, 0f, -0.7f));
    }

    Location wingTip(boolean left) {
        return left ? fx.at(rig.point(tipL, 3.0f, 0f, -1.0f)) : fx.at(rig.point(tipR, -3.0f, 0f, -1.0f));
    }

    Location crown() {
        return fx.at(rig.point(head, 0f, 0.9f, 0.3f));
    }
}
