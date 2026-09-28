package de.aetherion.bossengine.instance.saint;

import org.bukkit.Color;
import org.bukkit.Material;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

import static de.aetherion.bossengine.instance.saint.SaintMath.HALF_PI;
import static de.aetherion.bossengine.instance.saint.SaintMath.PI;

/**
 * Seraphine's body: a jointed porcelain doll roughly six blocks tall.
 *
 * <p>Cool white porcelain, gold ball joints, a bell skirt of eight hinged panels that flare
 * when she spins, a glass veil, a gold halo, and two sewing needles as long as a player is tall.
 * Hairline kintsugi seams are pre-authored and revealed as she breaks; in the last act they glow.
 *
 * <p>Facing: local +Z. Her right side is local -X. Limbs rest hanging along -Y.
 */
final class SaintBody {

    static final float SCALE = 1.6f;
    /** Pelvis height above the floor when toes just graze it. */
    static final float HANG = 1.95f * SCALE + 0.22f;
    /** Pelvis height standing on pointe. */
    static final float STAND = 1.95f * SCALE;
    /** Pelvis height kneeling in {@link #poseSlump()}. */
    static final float SLUMP = (0.12f + 0.78f) * SCALE + 0.14f;

    static final Color GOLD = Color.fromRGB(255, 205, 90);
    static final Color CRIMSON = Color.fromRGB(230, 30, 60);
    static final Color WHITE = Color.fromRGB(255, 250, 240);

    final Skeleton rig = new Skeleton();

    final Skeleton.Bone pelvis;
    final Skeleton.Bone spine;
    final Skeleton.Bone chest;
    final Skeleton.Bone neck;
    final Skeleton.Bone head;
    final Skeleton.Bone halo;
    final Skeleton.Bone veil;
    final Skeleton.Bone[] upper = new Skeleton.Bone[2];
    final Skeleton.Bone[] fore = new Skeleton.Bone[2];
    final Skeleton.Bone[] hand = new Skeleton.Bone[2];
    final Skeleton.Bone[] needle = new Skeleton.Bone[2];
    final Skeleton.Bone[] thigh = new Skeleton.Bone[2];
    final Skeleton.Bone[] shin = new Skeleton.Bone[2];
    final Skeleton.Bone[] foot = new Skeleton.Bone[2];
    final Skeleton.Bone[] skirt = new Skeleton.Bone[8];

    /** Index 0 = right (-X), 1 = left (+X). */
    static final int R = 0;
    static final int L = 1;

    private int cracksShown;
    private float haloSpin;

    SaintBody() {
        rig.scale = SCALE;
        Material porcelain = Material.SMOOTH_QUARTZ;
        Material gold = Material.GOLD_BLOCK;

        pelvis = rig.bone(null, 0, 0, 0);
        spine = rig.bone(pelvis, 0, 0.18f, 0);
        chest = rig.bone(spine, 0, 0.42f, 0);
        neck = rig.bone(chest, 0, 0.6f, 0);
        head = rig.bone(neck, 0, 0.16f, 0);
        halo = rig.bone(head, 0, 0.8f, -0.1f, new Quaternionf().rotateX(0.28f));
        veil = rig.bone(head, 0, 0.5f, -0.25f);

        rig.box(pelvis, porcelain, "body", 0, 0, 0, 0.62f, 0.36f, 0.42f);
        rig.box(spine, porcelain, "body", 0, 0.21f, 0, 0.46f, 0.46f, 0.34f);
        rig.box(spine, gold, "trim", 0, 0.4f, 0, 0.5f, 0.07f, 0.38f);
        rig.box(chest, porcelain, "body", 0, 0.3f, 0, 0.74f, 0.6f, 0.42f);
        rig.box(chest, Material.WHITE_GLAZED_TERRACOTTA, "body", 0, 0.3f, 0.205f, 0.5f, 0.44f, 0.04f);
        rig.box(chest, gold, "trim", 0, 0.6f, 0, 0.52f, 0.08f, 0.36f);
        rig.box(neck, porcelain, "body", 0, 0.08f, 0, 0.15f, 0.2f, 0.15f);
        rig.box(neck, gold, "joint", 0, 0, 0, 0.17f, 0.17f, 0.17f, diamond());
        rig.box(head, porcelain, "body", 0, 0.28f, 0, 0.5f, 0.54f, 0.48f);
        rig.box(head, porcelain, "body", 0, 0.3f, 0, 0.44f, 0.5f, 0.44f, new Quaternionf().rotateY(PI / 4f));
        rig.box(head, Material.WHITE_CONCRETE, "body", 0, 0.26f, 0.245f, 0.4f, 0.38f, 0.02f);
        rig.box(head, gold, "eyesClosed", -0.1f, 0.3f, 0.258f, 0.1f, 0.018f, 0.012f);
        rig.box(head, gold, "eyesClosed", 0.1f, 0.3f, 0.258f, 0.1f, 0.018f, 0.012f);
        rig.box(head, Material.REDSTONE_BLOCK, "eyesOpen", -0.1f, 0.3f, 0.258f, 0.07f, 0.06f, 0.014f);
        rig.box(head, Material.REDSTONE_BLOCK, "eyesOpen", 0.1f, 0.3f, 0.258f, 0.07f, 0.06f, 0.014f);
        rig.box(head, Material.RED_CONCRETE, "body", 0, 0.14f, 0.258f, 0.08f, 0.02f, 0.012f);
        rig.box(veil, Material.WHITE_STAINED_GLASS, "veil", 0, -0.62f, -0.02f, 0.56f, 1.3f, 0.03f);

        for (int k = 0; k < 8; k++) {
            float b = k * PI / 4f;
            rig.box(halo, gold, "halo", (float) Math.sin(b) * 0.36f, 0, (float) Math.cos(b) * 0.36f,
                    0.29f, 0.05f, 0.05f, new Quaternionf().rotateY(b + HALF_PI));
        }

        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s] = rig.bone(chest, 0.46f * side, 0.5f, 0);
            fore[s] = rig.bone(upper[s], 0, -0.6f, 0);
            hand[s] = rig.bone(fore[s], 0, -0.56f, 0);
            needle[s] = rig.bone(hand[s], 0, -0.14f, 0.02f);
            rig.box(upper[s], gold, "joint", 0, 0, 0, 0.2f, 0.2f, 0.2f, diamond());
            rig.box(upper[s], porcelain, "body", 0, -0.3f, 0, 0.17f, 0.56f, 0.17f);
            rig.box(fore[s], gold, "joint", 0, 0, 0, 0.16f, 0.16f, 0.16f, diamond());
            rig.box(fore[s], porcelain, "body", 0, -0.28f, 0, 0.14f, 0.52f, 0.14f);
            rig.box(fore[s], gold, "trim", 0, -0.5f, 0, 0.17f, 0.05f, 0.17f);
            rig.box(hand[s], porcelain, "body", 0, -0.1f, 0.02f, 0.12f, 0.2f, 0.08f);
            rig.box(needle[s], Material.IRON_BLOCK, "needle", 0, -1.4f, 0, 0.06f, 3.1f, 0.06f);
            rig.box(needle[s], gold, "needle", 0, 0.2f, 0, 0.13f, 0.28f, 0.03f);

            thigh[s] = rig.bone(pelvis, 0.17f * side, -0.12f, 0);
            shin[s] = rig.bone(thigh[s], 0, -0.78f, 0);
            foot[s] = rig.bone(shin[s], 0, -0.76f, 0);
            rig.box(thigh[s], porcelain, "body", 0, -0.39f, 0, 0.19f, 0.78f, 0.19f);
            rig.box(shin[s], gold, "joint", 0, 0, 0, 0.17f, 0.17f, 0.17f, diamond());
            rig.box(shin[s], porcelain, "body", 0, -0.38f, 0, 0.16f, 0.76f, 0.16f);
            rig.box(foot[s], gold, "trim", 0, -0.12f, 0.03f, 0.12f, 0.26f, 0.14f);
        }

        for (int k = 0; k < skirt.length; k++) {
            float a = k * PI / 4f + PI / 8f;
            skirt[k] = rig.bone(pelvis, (float) Math.sin(a) * 0.28f, 0.02f, (float) Math.cos(a) * 0.24f,
                    new Quaternionf().rotateY(a));
            Material cloth = k % 2 == 0 ? Material.WHITE_WOOL : Material.WHITE_CONCRETE;
            rig.box(skirt[k], cloth, "skirt", 0, -0.56f, 0, 0.36f, 1.12f, 0.035f);
            rig.box(skirt[k], gold, "trim", 0, -1.1f, 0.005f, 0.37f, 0.07f, 0.045f);
        }

        // Kintsugi seams: hairline gold, revealed one by one as she breaks.
        crack(chest, 0.12f, 0.38f, 0.226f, 0.34f, 0.5f);
        crack(head, -0.08f, 0.4f, 0.262f, 0.2f, -0.7f);
        crack(spine, -0.1f, 0.2f, 0.176f, 0.3f, 0.3f);
        crack(upper[R], 0, -0.3f, 0.09f, 0.4f, 0.2f);
        crack(chest, -0.22f, 0.2f, 0.215f, 0.26f, -0.4f);
        crack(head, 0.12f, 0.18f, 0.262f, 0.16f, 0.9f);
        crack(fore[L], 0, -0.25f, 0.075f, 0.36f, -0.25f);
        crack(pelvis, 0.1f, 0, 0.215f, 0.28f, 1.1f);
        crack(chest, 0.05f, 0.1f, 0.216f, 0.22f, -1.2f);
        crack(upper[L], 0, -0.25f, 0.09f, 0.34f, -0.3f);
        crack(head, 0.0f, 0.46f, 0.18f, 0.18f, 0.2f);
        crack(fore[R], 0, -0.3f, 0.075f, 0.3f, 0.35f);

        rig.setGroupHidden("eyesOpen", true);
        rig.setGroupHidden("crack", true);
        for (Skeleton.Piece p : rig.group("halo")) {
            p.glow = GOLD;
        }
        springs();
    }

    private static Quaternionf diamond() {
        return new Quaternionf().rotateY(PI / 4f).rotateX(PI / 4f);
    }

    private void crack(Skeleton.Bone bone, float x, float y, float z, float len, float roll) {
        rig.box(bone, Material.GOLD_BLOCK, "crack", x, y, z, 0.018f, len, 0.012f,
                new Quaternionf().rotateZ(roll));
    }

    /* ------------------------------------------------------------------ springs */

    /** Default marionette springs: firm core, loose limbs, very loose cloth. */
    void springs() {
        for (Skeleton.Bone b : rig.bones()) {
            b.spring(0.2f, 0.26f);
        }
        pelvis.spring(0.3f, 0.34f);
        spine.spring(0.28f, 0.34f);
        chest.spring(0.28f, 0.34f);
        neck.spring(0.18f, 0.24f);
        head.spring(0.18f, 0.24f);
        veil.spring(0.06f, 0.1f);
        halo.spring(0.4f, 0.5f);
        for (int s = 0; s < 2; s++) {
            upper[s].spring(0.14f, 0.18f);
            fore[s].spring(0.14f, 0.18f);
            hand[s].spring(0.2f, 0.24f);
            needle[s].spring(0.22f, 0.26f);
            thigh[s].spring(0.13f, 0.18f);
            shin[s].spring(0.12f, 0.17f);
            foot[s].spring(0.14f, 0.2f);
        }
        for (Skeleton.Bone b : skirt) {
            b.spring(0.09f, 0.13f);
        }
    }

    /** Everything snappy: used for attacks and the unstrung act. */
    void springsSharp() {
        for (Skeleton.Bone b : rig.bones()) {
            b.spring(0.42f, 0.42f);
        }
        veil.spring(0.12f, 0.16f);
        for (Skeleton.Bone b : skirt) {
            b.spring(0.16f, 0.2f);
        }
    }

    /** No muscle at all: threads went slack. */
    void springsLimp() {
        for (Skeleton.Bone b : rig.bones()) {
            b.spring(0.05f, 0.08f);
        }
        halo.spring(0.4f, 0.5f);
    }

    /* ------------------------------------------------------------------ per-tick */

    /**
     * Secondary motion: root acceleration swings the limbs, cloth and veil like a real
     * suspended doll. Called before the spring step.
     */
    void inertia() {
        Vector3f a = rig.localAccel();
        float gain = 0.55f;
        for (int s = 0; s < 2; s++) {
            upper[s].kick(a.z * gain, 0, -a.x * gain + (s == R ? 1f : -1f) * a.y * 0.35f);
            thigh[s].kick(a.z * gain * 0.6f, 0, -a.x * gain * 0.6f);
            shin[s].kick(a.z * gain * 0.4f, 0, 0);
        }
        for (int k = 0; k < skirt.length; k++) {
            skirt[k].kick(a.y * 0.9f - Math.abs(a.z) * 0.3f, 0, 0);
        }
        veil.kick(a.z * 0.9f + a.y * 0.4f, 0, -a.x * 0.9f);
    }

    void spinHalo(float speed) {
        haloSpin += speed;
        halo.target.y = haloSpin;
        halo.angle.y = haloSpin;
    }

    /* ------------------------------------------------------------------ poses */

    void clearPose() {
        for (Skeleton.Bone b : rig.bones()) {
            if (b != halo) {
                b.target.zero();
            }
        }
    }

    /** Suspended idle: weight in the threads, toes grazing the boards, a slow breath. */
    void poseHang(float t) {
        clearPose();
        float breath = (float) Math.sin(t * 0.07f);
        float sway = (float) Math.sin(t * 0.045f);
        spine.aim(0.04f + breath * 0.02f, sway * 0.05f, 0);
        chest.aim(0.02f, 0, sway * 0.03f);
        neck.aim(0.18f, 0, 0.1f + sway * 0.05f);
        head.aim(0.12f, 0, 0.14f);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-0.25f, 0, side * 0.2f);
            fore[s].aim(-0.35f, 0, 0);
            // Needles trail behind and outward, tips grazing the boards.
            needle[s].aim(1.2f, 0, side * 0.25f);
            thigh[s].aim(0.05f, 0, 0);
            foot[s].aim(0.15f, 0, 0);
        }
        thigh[L].aim(-0.12f, 0, 0);
        shin[L].aim(0.25f, 0, 0);
        flare(0.02f);
    }

    /** No threads, no muscle, still suspended high. Head lolls forward. */
    void poseLimp() {
        clearPose();
        neck.aim(0.55f, 0, 0.3f);
        head.aim(0.25f, 0, 0.2f);
        spine.aim(0.15f, 0, 0);
        for (int s = 0; s < 2; s++) {
            upper[s].aim(0.05f, 0, (s == R ? -1f : 1f) * 0.05f);
            fore[s].aim(-0.1f, 0, 0);
            needle[s].aim(1.1f, 0, (s == R ? -1f : 1f) * 0.3f);
            foot[s].aim(0.4f, 0, 0);
        }
    }

    /**
     * Collapsed onto the boards with nobody holding her: kneeling, shins folded flat behind her,
     * torso slumped over, needles lying out to either side. Pelvis belongs at {@link #SLUMP}.
     */
    void poseSlump() {
        clearPose();
        spine.aim(0.5f, 0, 0.08f);
        chest.aim(0.3f, 0, 0);
        neck.aim(0.8f, 0, 0.35f);
        head.aim(0.3f, 0, 0.2f);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-0.35f, 0, side * 0.35f);
            fore[s].aim(-0.3f, 0, 0);
            needle[s].aim(0.5f, 0, side * 1.25f);
            thigh[s].aim(0.05f, 0, side * 0.12f);
            shin[s].aim(SaintMath.HALF_PI, 0, 0);
            foot[s].aim(0.35f, 0, 0);
        }
        flare(0.35f);
    }

    /** Hoisted by head and wrists: arms dragged up in a V. */
    void poseHoisted() {
        clearPose();
        neck.aim(-0.1f, 0, 0);
        head.aim(-0.15f, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-0.2f, 0, side * 2.5f);
            fore[s].aim(0, 0, side * 0.3f);
            needle[s].aim(0, 0, 0);
            foot[s].aim(0.5f, 0, 0);
        }
        shin[R].aim(0.3f, 0, 0);
        flare(0.1f);
    }

    /** Falling: needles plunged point-down beneath her, knees tucked. */
    void poseDive() {
        clearPose();
        spine.aim(0.3f, 0, 0);
        neck.aim(-0.2f, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-0.35f, 0, side * 0.35f);
            fore[s].aim(-0.4f, 0, 0);
            needle[s].aim(0.7f, 0, -side * 0.2f);
            thigh[s].aim(-1.2f, 0, side * 0.2f);
            shin[s].aim(1.9f, 0, 0);
            foot[s].aim(0.6f, 0, 0);
        }
        flare(-0.9f);
    }

    /** Landed: crouched deep, both needles driven into the boards. */
    void poseLanded() {
        clearPose();
        spine.aim(0.55f, 0, 0);
        chest.aim(0.3f, 0, 0);
        neck.aim(0.35f, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-0.5f, 0, side * 0.55f);
            fore[s].aim(-0.2f, 0, 0);
            needle[s].aim(0.8f, 0, 0);
            thigh[s].aim(-1.5f, 0, side * 0.35f);
            shin[s].aim(2.3f, 0, 0);
            foot[s].aim(0.3f, 0, 0);
        }
        flare(-0.55f);
    }

    /** Arms flung wide, needles continuing the line of the arms. */
    void poseCrucifix() {
        clearPose();
        neck.aim(-0.15f, 0, 0);
        head.aim(-0.2f, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(0, 0, side * HALF_PI);
            fore[s].aim(0, 0, 0);
            needle[s].aim(0, 0, 0);
            foot[s].aim(0.6f, 0, 0);
        }
        flare(-0.1f);
    }

    /**
     * Pendulum reap: legs tucked up out of the way, arms and needles angled down and out in a
     * cone so the blades scythe through at chest height at the bottom of the swing.
     */
    void poseReap(float lean) {
        clearPose();
        spine.aim(0.1f, 0, -lean * 0.3f);
        chest.aim(0, lean * 0.4f, 0);
        neck.aim(-0.1f, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(0, 0, side * 0.6f);
            fore[s].aim(0, 0, 0);
            needle[s].aim(0, 0, 0);
            thigh[s].aim(-1.15f, 0, lean * 0.25f);
            shin[s].aim(1.9f, 0, 0);
            foot[s].aim(0.6f, 0, 0);
        }
        flare(-0.6f);
    }

    /**
     * On pointe, one leg drawn up, skirt blown flat by the spin. Arms and needles form one
     * downward-angled line each, so the blades sweep a cone at chest height around her.
     */
    void posePirouette(float flare) {
        clearPose();
        neck.aim(-0.1f, 0, 0);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(0, 0, side * 1.0f);
            fore[s].aim(0, 0, 0);
            needle[s].aim(0, 0, 0);
            foot[s].aim(0.1f, 0, 0);
        }
        thigh[L].aim(-1.25f, 0, 0.5f);
        shin[L].aim(1.9f, 0, 0);
        flare(-flare);
    }

    /** Coiled, twisted against the coming spin: the music box being wound. */
    void poseWind(float twist) {
        posePirouette(0.1f);
        spine.aim(0.1f, -twist * 0.5f, 0);
        chest.aim(0, -twist * 0.6f, 0);
        head.aim(0, twist * 0.9f, 0);
        for (int s = 0; s < 2; s++) {
            upper[s].aim(-0.9f, 0, (s == R ? -1f : 1f) * 0.8f);
        }
        thigh[L].aim(-0.3f, 0, 0.1f);
        shin[L].aim(0.4f, 0, 0);
    }

    /** A curtsey: the one gesture she was never strung for. */
    void poseBow(float depth) {
        clearPose();
        spine.aim(depth * 0.45f, 0, 0);
        chest.aim(depth * 0.35f, 0, 0);
        neck.aim(depth * 0.3f, 0, 0);
        head.aim(depth * 0.2f, 0, 0);
        upper[R].aim(-0.2f * depth, 0, -0.55f * depth);
        fore[R].aim(-0.4f * depth, 0, 0);
        upper[L].aim(-0.9f * depth, 0, 0.2f);
        fore[L].aim(-1.2f * depth, 0, 0);
        needle[R].aim(1.2f, 0, -0.25f);
        needle[L].aim(0.4f, 0, 0);
        thigh[R].aim(0.45f * depth, 0, 0);
        shin[R].aim(0.5f * depth, 0, 0);
        thigh[L].aim(-0.15f * depth, 0, 0);
        shin[L].aim(0.35f * depth, 0, 0);
        flare(0.12f * depth);
    }

    /** Needle drawn back for a thrust. */
    void poseDrawBack(int arm, float amount) {
        clearPose();
        int other = 1 - arm;
        float side = arm == R ? -1f : 1f;
        // Torso winds toward the drawing arm's side.
        spine.aim(0.15f, side * 0.5f * amount, 0);
        chest.aim(0.1f, side * 0.35f * amount, 0);
        upper[arm].aim(0.9f * amount, 0, side * 0.35f);
        fore[arm].aim(-1.6f * amount, 0, 0);
        needle[arm].aim(-0.8f * amount - 0.3f, 0, 0);
        upper[other].aim(-1.1f * amount, 0, -side * 0.6f);
        fore[other].aim(-0.4f, 0, 0);
        needle[other].aim(-0.2f, 0, 0);
        thigh[arm].aim(0.5f * amount, 0, 0);
        shin[arm].aim(0.7f * amount, 0, 0);
        thigh[other].aim(-0.7f * amount, 0, 0);
        shin[other].aim(0.4f * amount, 0, 0);
        flare(-0.2f);
    }

    /** Full extension: arm and needle one straight spear forward. */
    void poseThrust(int arm) {
        clearPose();
        int other = 1 - arm;
        float side = arm == R ? -1f : 1f;
        // Torso unwinds so the thrusting shoulder drives forward; the arm aims back to center.
        spine.aim(0.3f, -side * 0.45f, 0);
        chest.aim(0.2f, -side * 0.35f, 0);
        upper[arm].aim(-HALF_PI + 0.15f, side * 0.55f, 0);
        fore[arm].aim(0, 0, 0);
        needle[arm].aim(0, 0, 0);
        upper[other].aim(0.7f, 0, -side * 0.9f);
        fore[other].aim(-0.3f, 0, 0);
        thigh[arm].aim(-0.9f, 0, 0);
        shin[arm].aim(0.5f, 0, 0);
        thigh[other].aim(0.7f, 0, 0);
        shin[other].aim(0.2f, 0, 0);
        flare(-0.45f);
    }

    /** Horizontal needle sweep: sweep runs -1 (wound) .. +1 (follow-through). */
    void poseSweep(int arm, float sweep) {
        clearPose();
        float side = arm == R ? -1f : 1f;
        int other = 1 - arm;
        spine.aim(0.15f, -side * sweep * 0.4f, 0);
        chest.aim(0.05f, -side * sweep * 0.35f, 0);
        // Arm slightly below horizontal, blade angled down so the tip skims past knee height.
        upper[arm].aim(-HALF_PI + 0.35f, -side * sweep * 0.85f, 0);
        fore[arm].aim(0, 0, 0);
        needle[arm].aim(0.3f, 0, 0);
        upper[other].aim(-0.3f, 0, -side * 1.1f);
        needle[other].aim(-0.4f, 0, 0);
        thigh[arm].aim(-0.5f, 0, 0);
        shin[arm].aim(0.4f, 0, 0);
        thigh[other].aim(0.4f, 0, 0);
        flare(-0.4f - Math.abs(sweep) * 0.3f);
    }

    /** Unstrung: reassembled wrong. Head turned backwards, knees bent the wrong way. */
    void poseWrong(float t) {
        clearPose();
        float twitch = (float) Math.sin(t * 0.9f) * 0.08f;
        spine.aim(0.45f, 0, twitch);
        chest.aim(0.35f, 0, 0);
        neck.aim(-0.2f, PI, 0);
        head.aim(-0.35f, 0, 0.3f + twitch);
        for (int s = 0; s < 2; s++) {
            float side = s == R ? -1f : 1f;
            upper[s].aim(-1.0f, 0, side * 0.45f);
            fore[s].aim(-0.7f, 0, 0);
            needle[s].aim(-0.5f, 0, 0);
            thigh[s].aim(0.35f, 0, side * 0.25f);
            shin[s].aim(-0.9f, 0, 0);
            foot[s].aim(0.8f, 0, 0);
        }
        flare(-0.25f);
    }

    /** Reaching up to cut one of her own threads. */
    void poseCut(int arm, float reach) {
        clearPose();
        float side = arm == R ? -1f : 1f;
        neck.aim(-0.6f, 0, 0);
        head.aim(-0.4f, 0, 0);
        upper[arm].aim(-2.6f * reach, 0, side * 0.35f);
        fore[arm].aim(-0.3f, 0, 0);
        needle[arm].aim(-0.6f * reach, 0, 0);
        upper[1 - arm].aim(-0.3f, 0, -side * 0.5f);
        flare(0.05f);
    }

    void flare(float amount) {
        for (Skeleton.Bone b : skirt) {
            b.target.x = -amount;
        }
    }

    /* ------------------------------------------------------------------ look */

    void eyesOpen(boolean open) {
        rig.setGroupHidden("eyesOpen", !open);
        rig.setGroupHidden("eyesClosed", open);
        rig.setGroupGlow("eyesOpen", open ? CRIMSON : null);
    }

    /** Halo lit = she is dangerous. Halo dark = opening. */
    void haloLit(boolean lit) {
        for (Skeleton.Piece p : rig.group("halo")) {
            rig.setGlow(p, lit ? GOLD : null);
            rig.setBrightness(p, lit ? 15 : 4);
        }
    }

    /** Reveal kintsugi seams proportional to damage taken (0..1). */
    boolean crackTo(float broken) {
        List<Skeleton.Piece> cracks = rig.group("crack");
        int want = Math.min(cracks.size(), Math.round(broken * cracks.size()));
        if (want <= cracksShown) {
            return false;
        }
        for (int i = cracksShown; i < want; i++) {
            cracks.get(i).hidden = false;
        }
        cracksShown = want;
        return true;
    }

    /** Show every piece again, respecting eye state and how many seams have opened. */
    void restoreVisibility(boolean eyesOpen) {
        List<Skeleton.Piece> cracks = rig.group("crack");
        for (Skeleton.Piece p : rig.pieces()) {
            switch (p.group) {
                case "eyesOpen" -> p.hidden = !eyesOpen;
                case "eyesClosed" -> p.hidden = eyesOpen;
                case "crack" -> p.hidden = cracks.indexOf(p) >= cracksShown;
                default -> p.hidden = false;
            }
        }
    }

    void cracksGlow(Color color) {
        rig.setGroupGlow("crack", color);
    }

    /* ------------------------------------------------------------------ anchors */

    Vector3f headTop() {
        return rig.point(head, 0, 0.58f, 0);
    }

    Vector3f wrist(int s) {
        return rig.point(hand[s], 0, -0.08f, 0);
    }

    Vector3f knee(int s) {
        return rig.point(shin[s], 0, 0, 0.1f);
    }

    Vector3f needleTip(int s) {
        return rig.point(needle[s], 0, -2.95f, 0);
    }

    Vector3f needleBase(int s) {
        return rig.point(needle[s], 0, 0.1f, 0);
    }

    Vector3f toe(int s) {
        return rig.point(foot[s], 0, -0.25f, 0.03f);
    }

    Vector3f chestPoint() {
        return rig.point(chest, 0, 0.3f, 0);
    }
}
