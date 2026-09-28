package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Color;
import org.bukkit.Material;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static de.aetherion.bossengine.instance.worldeater.WeMath.PI;

/**
 * THE UNBROKEN: a colossus of bedrock, about thirteen blocks tall standing.
 *
 * <p>It is the floor of the world given a body. Bedrock plate over a hollow of pure void: where it
 * cracks, crying obsidian shows the violet dark inside. On its head it carries the layers of the
 * world it holds up, stacked like a crown: deepslate, tuff, stone, dirt, and one tuft of grass.
 * Two chains run from its back to the gate it keeps.
 *
 * <p>Facing: local +Z. Its right side is local -X. Root = pelvis. Units are blocks.
 * Angle conventions: for chains that point up (spine), +X leans forward; for limbs that hang
 * down, -X swings them forward.
 */
final class UnbrokenBody {

    static final float STAND = 5.3f;
    static final float KNEEL = 3.2f;
    static final float PRESS = 3.4f;
    static final float HAMMER = 4.66f;

    static final Color VOID = WeProps.VOID;
    static final Color CRACK = Color.fromRGB(190, 90, 255);

    /** Index 0 = right (-X), 1 = left (+X). */
    static final int R = 0;
    static final int L = 1;

    final WeRig rig = new WeRig();

    final WeRig.Bone pelvis;
    final WeRig.Bone spine;
    final WeRig.Bone chest;
    final WeRig.Bone neck;
    final WeRig.Bone head;
    final WeRig.Bone[] upper = new WeRig.Bone[2];
    final WeRig.Bone[] fore = new WeRig.Bone[2];
    final WeRig.Bone[] fist = new WeRig.Bone[2];
    final WeRig.Bone[] thigh = new WeRig.Bone[2];
    final WeRig.Bone[] shin = new WeRig.Bone[2];
    final WeRig.Bone[] foot = new WeRig.Bone[2];

    private int cracksShown;
    private boolean armGone;

    UnbrokenBody() {
        Material rock = Material.BEDROCK;
        Material plate = Material.POLISHED_DEEPSLATE;
        Material tiles = Material.DEEPSLATE_TILES;
        Material trim = Material.OBSIDIAN;

        pelvis = rig.bone(null, 0, 0, 0);
        spine = rig.bone(pelvis, 0, 0.55f, 0);
        chest = rig.bone(spine, 0, 1.25f, 0);
        neck = rig.bone(chest, 0, 2.75f, 0.1f);
        head = rig.bone(neck, 0, 0.4f, 0);

        rig.box(pelvis, rock, "body", 0, 0, 0, 2.7f, 1.3f, 1.8f);
        rig.box(pelvis, tiles, "body", 0, -0.95f, 0.95f, 1.7f, 1.3f, 0.2f);
        rig.box(pelvis, tiles, "body", 0, -0.95f, -0.95f, 1.7f, 1.3f, 0.2f);
        rig.box(spine, rock, "body", 0, 0.55f, 0, 2.3f, 1.3f, 1.6f);
        rig.box(chest, rock, "body", 0, 1.45f, 0, 4.1f, 2.9f, 2.5f);
        rig.box(chest, plate, "body", 0, 1.65f, 1.28f, 2.7f, 1.8f, 0.22f);
        rig.box(chest, trim, "body", 0, 2.85f, 0, 4.3f, 0.3f, 2.7f);
        rig.box(chest, trim, "body", 0, 0.05f, 0, 3.4f, 0.25f, 2.3f);
        // The seam down its sternum: a hairline of void that widens as it breaks.
        rig.box(chest, Material.CRYING_OBSIDIAN, "seam", 0, 1.35f, 1.41f, 0.2f, 2.1f, 0.06f);
        // Chain mounts on its back.
        rig.box(chest, trim, "mount", -1.2f, 2.0f, -1.3f, 0.55f, 0.55f, 0.3f);
        rig.box(chest, trim, "mount", 1.2f, 2.0f, -1.3f, 0.55f, 0.55f, 0.3f);
        rig.box(neck, rock, "body", 0, 0.1f, 0, 1.05f, 0.7f, 1.05f);
        rig.box(head, rock, "body", 0, 0.8f, 0.05f, 1.75f, 1.65f, 1.75f);
        rig.box(head, rock, "body", 0, 0.55f, 0.72f, 1.35f, 0.9f, 0.5f);
        // Visor: one slit of void light.
        rig.box(head, Material.CRYING_OBSIDIAN, "visor", 0, 0.98f, 0.935f, 1.3f, 0.17f, 0.06f);
        // The crown is the world it holds up.
        rig.box(head, Material.DEEPSLATE, "crown", 0, 1.74f, 0.05f, 1.85f, 0.26f, 1.85f);
        rig.box(head, Material.TUFF, "crown", 0, 1.98f, 0.05f, 1.55f, 0.24f, 1.55f);
        rig.box(head, Material.STONE, "crown", 0, 2.2f, 0.05f, 1.25f, 0.22f, 1.25f);
        rig.box(head, Material.DIRT, "crown", 0, 2.4f, 0.05f, 0.95f, 0.2f, 0.95f);
        rig.box(head, Material.GRASS_BLOCK, "crown", 0, 2.6f, 0.05f, 0.62f, 0.22f, 0.62f);

        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s] = rig.bone(chest, 2.45f * side, 2.15f, 0);
            fore[s] = rig.bone(upper[s], 0, -2.9f, 0);
            fist[s] = rig.bone(fore[s], 0, -2.75f, 0);
            rig.box(upper[s], rock, "arm" + s, 0.1f * side, 0.2f, 0, 1.95f, 1.35f, 1.95f,
                    new Quaternionf().rotateZ(-0.12f * side));
            rig.box(upper[s], trim, "arm" + s, 0.1f * side, -0.5f, 0, 2.05f, 0.25f, 2.05f,
                    new Quaternionf().rotateZ(-0.12f * side));
            rig.box(upper[s], rock, "arm" + s, 0, -1.5f, 0, 1.1f, 2.7f, 1.1f);
            rig.box(fore[s], rock, "arm" + s, 0, -1.35f, 0, 1.3f, 2.7f, 1.3f);
            rig.box(fore[s], tiles, "arm" + s, 0, -0.55f, 0, 1.45f, 0.85f, 1.45f);
            rig.box(fist[s], rock, "arm" + s, 0, -0.85f, 0.05f, 1.9f, 1.7f, 1.9f);
            rig.box(fist[s], Material.POLISHED_BLACKSTONE, "arm" + s, 0, -1.55f, 0.55f, 2.0f, 0.45f, 0.55f);
            // Forearm crack, shown while the arm is exposed.
            rig.box(fore[s], Material.CRYING_OBSIDIAN, "armcrack" + s, 0.66f * side, -1.35f, 0.1f, 0.05f, 2.1f, 0.14f,
                    new Quaternionf().rotateX(0.15f));

            thigh[s] = rig.bone(pelvis, 0.95f * side, -0.3f, 0);
            shin[s] = rig.bone(thigh[s], 0, -2.2f, 0);
            foot[s] = rig.bone(shin[s], 0, -2.15f, 0);
            rig.box(thigh[s], rock, "body", 0, -1.1f, 0, 1.45f, 2.3f, 1.45f);
            rig.box(shin[s], rock, "body", 0, -1.05f, 0, 1.3f, 2.2f, 1.3f);
            rig.box(shin[s], tiles, "body", 0, -0.85f, 0.7f, 1.4f, 1.3f, 0.2f);
            rig.box(foot[s], rock, "body", 0, -0.35f, 0.35f, 1.55f, 0.7f, 2.2f);
        }

        // Void cracks, pre-authored and revealed as it breaks.
        crack(chest, 0.9f, 1.9f, 1.26f, 1.1f, 0.6f);
        crack(chest, -1.1f, 0.9f, 1.26f, 0.9f, -0.5f);
        crack(spine, 0.5f, 0.55f, 0.81f, 0.7f, 0.9f);
        crack(head, 0.45f, 0.5f, 0.93f, 0.55f, -0.8f);
        crack(chest, 1.5f, 1.0f, 1.26f, 0.8f, 1.2f);
        crack(pelvis, -0.6f, 0.1f, 0.91f, 0.6f, 0.4f);
        crack(chest, -0.4f, 2.3f, 1.26f, 0.7f, -1.3f);
        crack(thigh[R], 0, -1.2f, 0.73f, 1.1f, 0.3f);
        crack(thigh[L], 0, -0.9f, 0.73f, 0.9f, -0.35f);
        crack(shin[R], 0, -1.3f, 0.81f, 0.8f, 0.2f);
        crack(chest, 1.8f, 2.2f, 0.5f, 0.9f, 0.1f);
        crack(head, -0.5f, 1.2f, 0.93f, 0.4f, 0.7f);

        rig.setGroupHidden("crack", true);
        rig.setGroupHidden("armcrack0", true);
        rig.setGroupHidden("armcrack1", true);
        for (WeRig.Piece p : rig.pieces()) {
            p.brightness = p.material == Material.CRYING_OBSIDIAN ? 15 : 12;
        }
        springs();
    }

    private void crack(WeRig.Bone bone, float x, float y, float z, float len, float roll) {
        rig.box(bone, Material.CRYING_OBSIDIAN, "crack", x, y, z, 0.07f, len, 0.05f, new Quaternionf().rotateZ(roll));
    }

    /* ------------------------------------------------------------------ springs */

    /** Heavy: everything moves like tons of stone. */
    void springs() {
        for (WeRig.Bone b : rig.bones()) {
            b.spring(0.12f, 0.3f);
        }
        pelvis.spring(0.14f, 0.34f);
        head.spring(0.1f, 0.26f);
    }

    /** Committed: a strike that has already left. */
    void springsFast() {
        for (WeRig.Bone b : rig.bones()) {
            b.spring(0.36f, 0.42f);
        }
    }

    /** Barely holding itself together. */
    void springsSlack() {
        for (WeRig.Bone b : rig.bones()) {
            b.spring(0.05f, 0.1f);
        }
    }

    /** Near-critically damped: heavy, deliberate, no ringing (its last moments). */
    void springsHeavy() {
        for (WeRig.Bone b : rig.bones()) {
            b.spring(0.06f, 0.45f);
        }
    }

    /* ------------------------------------------------------------------ poses */

    void clearPose() {
        for (WeRig.Bone b : rig.bones()) {
            b.target.zero();
        }
    }

    /** The statue: kneeling on both knees, head bowed, fists planted before it. */
    void poseKneel(float breath) {
        clearPose();
        spine.aim(0.28f + breath * 0.01f, 0, 0);
        chest.aim(0.22f, 0, 0);
        neck.aim(0.25f, 0, 0);
        head.aim(0.38f, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-0.64f, 0, 0.1f * side);
            fore[s].aim(-0.22f, 0, 0);
            fist[s].aim(0.45f, 0, 0);
            thigh[s].aim(-0.12f, 0, 0.06f * side);
            shin[s].aim(1.62f, 0, 0);
            foot[s].aim(1.05f, 0, 0);
        }
    }

    /** Standing guard: a forward hunch, fists hanging heavy, a slow breath. */
    void poseStand(float t) {
        clearPose();
        float breath = (float) Math.sin(t * 0.05f);
        spine.aim(0.12f + breath * 0.02f, 0, 0);
        chest.aim(0.08f - breath * 0.03f, 0, 0);
        neck.aim(0.12f, 0, 0);
        head.aim(0.05f, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-0.12f, 0, 0.18f * side + breath * 0.02f * side);
            fore[s].aim(-0.25f, 0, 0);
            fist[s].aim(0.1f, 0, 0);
            thigh[s].aim(-0.1f, 0, 0.05f * side);
            shin[s].aim(0.22f, 0, 0);
            foot[s].aim(-0.12f, 0, 0);
        }
    }

    /**
     * One heavy stride cycle ({@code phase} 0..1 covers a step with each leg). The swinging leg
     * lifts while it passes under the body; {@link #walkDip(float)} lowers the pelvis so the
     * planted foot stays on the floor at full stride.
     */
    void poseWalk(float phase) {
        poseStand(0f);
        float th = phase * WeMath.TAU;
        float sin = (float) Math.sin(th);
        float cos = (float) Math.cos(th);
        for (int s = 0; s < 2; s++) {
            float sign = s == R ? 1f : -1f;
            thigh[s].target.x = -0.1f - 0.4f * sin * sign;
            float lift = Math.max(0f, cos * sign);
            shin[s].target.x = 0.22f + lift * 0.85f;
            foot[s].target.x = -0.12f - lift * 0.3f;
            upper[s].target.x = -0.12f + 0.28f * sin * sign;
        }
        chest.target.y = sin * 0.1f;
        spine.target.z = cos * 0.05f;
        head.target.y = -sin * 0.06f;
    }

    /** Pelvis drop for a stride phase, so the planted foot stays grounded. */
    static float walkDip(float phase) {
        return 0.1f * Math.abs((float) Math.sin(phase * WeMath.TAU));
    }

    /** Both fists raised overhead, arching back. {@code w} 0..1 windup. */
    void poseHammerRaise(float w) {
        poseStand(0f);
        spine.aim(-0.18f * w, 0, 0);
        chest.aim(-0.12f * w, 0, 0);
        head.aim(-0.3f * w, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(WeMath.lerp(-0.12f, -2.85f, w), 0, 0.22f * side * (1f - w) + 0.12f * side * w);
            fore[s].aim(WeMath.lerp(-0.25f, -0.35f, w), 0, 0);
            fist[s].aim(0.1f, 0, 0);
            shin[s].target.x = 0.22f + 0.2f * w;
            thigh[s].target.x = -0.1f - 0.15f * w;
        }
    }

    /** Folded forward over the impact, both fists driven into the floor ahead. */
    void poseHammerDown() {
        clearPose();
        spine.aim(0.6f, 0, 0);
        chest.aim(0.5f, 0, 0);
        neck.aim(-0.3f, 0, 0);
        head.aim(-0.35f, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-1.25f, 0, -0.1f * side);
            fore[s].aim(-0.25f, 0, 0);
            fist[s].aim(0.3f, 0, 0);
            thigh[s].aim(-0.75f, 0, 0.04f * side);
            shin[s].aim(1.05f, 0, 0);
            foot[s].aim(-0.3f, 0, 0);
        }
    }

    /** One arm sweeps down and forward, dragging its fist through the floor; torso twisted. */
    void poseDrag(int arm, float w) {
        poseStand(0f);
        float side = arm == R ? -1f : 1f;
        spine.aim(0.8f * w, 0.25f * side * w, 0);
        chest.aim(0.45f * w, 0.3f * side * w, 0);
        neck.aim(-0.3f * w, 0, 0);
        upper[arm].aim(WeMath.lerp(-0.12f, -1.05f, w), 0, WeMath.lerp(0.18f * side, -0.4f * side, w));
        fore[arm].aim(-0.1f, 0, 0);
        int other = 1 - arm;
        upper[other].aim(0.35f * w, 0, -0.35f * side);
        for (int s = 0; s < 2; s++) {
            thigh[s].target.x = WeMath.lerp(-0.1f, -0.7f, w);
            shin[s].target.x = WeMath.lerp(0.22f, 1.05f, w);
            foot[s].target.x = WeMath.lerp(-0.12f, -0.3f, w);
        }
    }

    /** Pelvis height that keeps the feet on the floor while crouched in {@link #poseDrag}. */
    static float dragHeight(float w) {
        return WeMath.lerp(STAND, 4.7f, w);
    }

    /** Driving a fist into the floor ahead to tear a slab loose (the drag's reach, both knees bent). */
    void poseRip(int arm, float w) {
        poseDrag(arm, w);
        chest.target.y = 0f;
        spine.target.y = 0f;
    }

    /** Slab lifted over the shoulder ({@code w} = 0) through the throw ({@code w} = 1). */
    void poseThrow(int arm, float w) {
        poseStand(0f);
        float side = arm == R ? -1f : 1f;
        float back = 1f - w;
        spine.aim(WeMath.lerp(-0.25f, 0.45f, w), WeMath.lerp(0.35f, -0.25f, w) * side, 0);
        chest.aim(WeMath.lerp(-0.15f, 0.3f, w), 0, 0);
        upper[arm].aim(WeMath.lerp(-2.6f, -1.1f, w), 0, 0.2f * side * back);
        fore[arm].aim(WeMath.lerp(-1.3f, -0.1f, w), 0, 0);
        int other = 1 - arm;
        upper[other].aim(WeMath.lerp(-0.6f, 0.3f, w), 0, -0.3f * side);
        thigh[arm].target.x = WeMath.lerp(0.2f, -0.5f, w);
        thigh[other].target.x = WeMath.lerp(-0.45f, 0.15f, w);
    }

    /** One leg raised high for a stomp ({@code w} 0..1 lift). */
    void poseStomp(int leg, float w) {
        poseStand(0f);
        thigh[leg].target.x = WeMath.lerp(-0.1f, -1.35f, w);
        shin[leg].target.x = WeMath.lerp(0.22f, 1.45f, w);
        foot[leg].target.x = -0.2f;
        spine.target.x = 0.12f - 0.15f * w;
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].target.z = (0.18f + 0.6f * w) * side;
        }
    }

    /** Down on one knee, both fists pressing the floor: the Weight. */
    void posePress(float t) {
        clearPose();
        float strain = (float) Math.sin(t * 0.9f) * 0.03f;
        spine.aim(0.5f + strain, 0, 0);
        chest.aim(0.3f, 0, 0);
        neck.aim(-0.35f, 0, 0);
        head.aim(-0.45f, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-0.7f, 0, 0.5f * side);
            fore[s].aim(-0.15f, 0, 0);
            fist[s].aim(0.3f, 0, 0);
        }
        thigh[R].aim(-1.3f, 0, -0.1f);
        shin[R].aim(1.0f, 0, 0);
        foot[R].aim(0.2f, 0, 0);
        thigh[L].aim(-0.1f, 0, 0.1f);
        shin[L].aim(1.55f, 0, 0);
        foot[L].aim(1.05f, 0, 0);
    }

    /** Head thrown back, arms wide: a roar with no voice. */
    void poseRoar(float w) {
        poseStand(0f);
        spine.aim(-0.15f * w, 0, 0);
        chest.aim(-0.2f * w, 0, 0);
        neck.aim(-0.3f * w, 0, 0);
        head.aim(-0.55f * w, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-0.4f * w, 0, (0.18f + 1.1f * w) * side);
            fore[s].aim(-0.5f * w, 0, 0);
        }
    }

    /** Recoil: struck, staggering back. */
    void poseStagger(float w) {
        poseStand(0f);
        spine.aim(-0.3f * w, 0, 0.1f * w);
        chest.aim(-0.2f * w, 0, 0);
        head.aim(-0.35f * w, 0.2f * w, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-0.5f * w, 0, 0.5f * side * w);
            thigh[s].target.x = -0.3f * w;
            shin[s].target.x = 0.5f * w;
        }
    }

    /** Falling backward into the dark, arms reaching up. */
    /**
     * Still kneeling as it knelt for an age, it reaches up for the gate it kept: arms overhead,
     * face lifted. Built on the kneel so the legs never unfold.
     */
    void poseReach(float w) {
        poseKneel(0f);
        spine.aim(WeMath.lerp(0.28f, -0.12f, w), 0, 0);
        chest.aim(WeMath.lerp(0.22f, -0.08f, w), 0, 0);
        neck.aim(WeMath.lerp(0.25f, -0.2f, w), 0, 0);
        head.aim(WeMath.lerp(0.38f, -0.4f, w), 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(WeMath.lerp(-0.64f, -2.45f, w), 0, 0.18f * side * w + 0.1f * side * (1f - w));
            fore[s].aim(WeMath.lerp(-0.22f, -0.3f, w), 0, 0);
            fist[s].aim(WeMath.lerp(0.45f, 0.15f, w), 0, 0);
        }
    }

    void poseFall(float w) {
        clearPose();
        spine.aim(-0.4f * w, 0, 0);
        chest.aim(-0.3f * w, 0, 0);
        head.aim(0.4f * w, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-2.4f * w, 0, 0.25f * side);
            fore[s].aim(-0.4f * w, 0, 0);
            thigh[s].aim(-0.8f * w, 0, 0.15f * side);
            shin[s].aim(0.9f * w, 0, 0);
        }
    }

    /* ------------------------------------------------------------------ damage state */

    /** Reveal void cracks up to {@code damage} (0..1). @return true if a new crack opened */
    boolean crackTo(float damage) {
        java.util.List<WeRig.Piece> cracks = rig.group("crack");
        int want = Math.min(cracks.size(), Math.round(damage * cracks.size()));
        boolean opened = false;
        while (cracksShown < want) {
            cracks.get(cracksShown++).hidden = false;
            opened = true;
        }
        return opened;
    }

    void armCrack(int arm, boolean shown) {
        if (arm == L && armGone) {
            return;
        }
        rig.setGroupHidden("armcrack" + arm, !shown);
    }

    void exposeGlow(boolean on) {
        Color c = on ? CRACK : null;
        rig.setGroupGlow("crack", c);
        rig.setGroupGlow("seam", c);
        rig.setGroupGlow("armcrack0", c);
        rig.setGroupGlow("armcrack1", c);
    }

    boolean armGone() {
        return armGone;
    }

    /** The left arm is gone: its pieces are cut loose (the director throws them). */
    java.util.List<WeRig.Piece> breakArm() {
        armGone = true;
        rig.solve();
        java.util.List<WeRig.Piece> out = new java.util.ArrayList<>();
        out.addAll(rig.group("arm" + L));
        out.addAll(rig.group("armcrack" + L));
        for (WeRig.Piece p : out) {
            rig.free(p);
        }
        return out;
    }

    /* ------------------------------------------------------------------ points */

    Vector3f fistPoint(int s) {
        return rig.point(fist[s], 0f, -1.1f, 0.1f);
    }

    Vector3f chestPoint() {
        return rig.point(chest, 0f, 1.5f, 1.2f);
    }

    Vector3f headPoint() {
        return rig.point(head, 0f, 0.9f, 0.4f);
    }

    Vector3f mountPoint(int s) {
        return rig.point(chest, s == R ? -1.2f : 1.2f, 2.0f, -1.45f);
    }

    Vector3f footPoint(int s) {
        return rig.point(foot[s], 0f, -0.7f, 0.35f);
    }

    /** Local facing as a unit vector. */
    Vector3f forward() {
        return new Vector3f((float) Math.sin(rig.yaw), 0f, (float) Math.cos(rig.yaw));
    }

    static float facingNorth() {
        return PI;
    }
}
