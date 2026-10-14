package net.aetherion.samuraiboss.render.anim;

import net.aetherion.samuraiboss.render.Xf;
import net.aetherion.samuraiboss.render.model.BoneDef;
import net.aetherion.samuraiboss.render.model.ModelDef;
import net.aetherion.samuraiboss.render.model.ModelMath;
import net.aetherion.samuraiboss.render.model.Pose;
import net.aetherion.samuraiboss.util.MathUtil;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Inverse Kinematik zum Erstellen von Posen: "Die rechte Hand soll HIER sein und die Klinge
 * soll DORTHIN zeigen" – Schulter- und Ellenbogenwinkel werden ausgerechnet.
 *
 * <p>Wird beim Aufbau der Animations-Clips benutzt (nicht pro Tick). Alle Punkte im
 * Modellraum in Pixeln, Ruhe-Konvention wie beim Modell (Blick +Z).</p>
 *
 * <p>Erwartete Knochen pro Seite s ("r"/"l"): arm_s -> forearm_s -> hand_s. Der Greifpunkt
 * liegt {@link #GRIP_PX} Pixel unter dem Handgelenk.</p>
 */
public final class PoseIK {

    public static final float GRIP_PX = 1.5f;
    private static final float PX = 1f / 16f;

    private PoseIK() {
    }

    // ------------------------------------------------------------------ Grundlagen

    /** PoseSpec -> Pose (für Vorwärtskinematik). */
    public static Pose toPose(ModelDef def, PoseSpec spec) {
        Pose p = new Pose(def.boneCount());
        for (Map.Entry<String, float[]> e : spec.rot.entrySet()) {
            if (!def.hasBone(e.getKey())) continue;
            float[] v = e.getValue();
            MathUtil.euler(p.rot[def.bone(e.getKey())], v[0], v[1], v[2]);
        }
        for (Map.Entry<String, float[]> e : spec.pos.entrySet()) {
            if (!def.hasBone(e.getKey())) continue;
            float[] v = e.getValue();
            p.pos[def.bone(e.getKey())].set(v[0], v[1], v[2]).mul(PX);
        }
        for (Map.Entry<String, Float> e : spec.scale.entrySet()) {
            if (!def.hasBone(e.getKey())) continue;
            p.scale[def.bone(e.getKey())] = e.getValue();
        }
        return p;
    }

    /** Weltlage (Modellraum, Blöcke) aller Knochen für eine Pose. */
    public static Xf[] solve(ModelDef def, PoseSpec spec) {
        Xf[] out = new Xf[def.boneCount()];
        for (int i = 0; i < out.length; i++) out[i] = new Xf();
        ModelMath.solve(def, toPose(def, spec), new Xf(), out);
        return out;
    }

    /** Punkt im lokalen Raum eines Knochens (Pixel, relativ zu seinem Drehpunkt) -> Modellraum (Pixel). */
    public static Vector3f pointOnBone(ModelDef def, PoseSpec spec, String bone, Vector3f localPx) {
        Xf[] xs = solve(def, spec);
        Vector3f v = new Vector3f(localPx).mul(PX);
        xs[def.bone(bone)].apply(v, v);
        return v.div(PX);
    }

    /** Weltdrehung (Modellraum) eines Knochens in dieser Pose. */
    public static Quaternionf boneRotation(ModelDef def, PoseSpec spec, String bone) {
        return new Quaternionf(solve(def, spec)[def.bone(bone)].q);
    }

    /** Euler-Winkel (Grad) passend zu MathUtil.euler (q = Rz * Ry * Rx). */
    public static float[] toEuler(Quaternionf q) {
        float w = q.w, x = q.x, y = q.y, z = q.z;
        float r00 = 1 - 2 * (y * y + z * z);
        float r10 = 2 * (x * y + w * z);
        float r20 = 2 * (x * z - w * y);
        float r21 = 2 * (y * z + w * x);
        float r22 = 1 - 2 * (x * x + y * y);
        float ey = (float) Math.asin(MathUtil.clamp(-r20, -1f, 1f));
        float ex, ez;
        if (Math.abs(r20) < 0.9999f) {
            ex = (float) Math.atan2(r21, r22);
            ez = (float) Math.atan2(r10, r00);
        } else {
            // Gimbal-Lock: Z = 0 setzen
            float r01 = 2 * (x * y - w * z);
            float r11 = 1 - 2 * (x * x + z * z);
            ex = (float) Math.atan2(-r01, r11);
            ez = 0f;
        }
        return new float[]{(float) Math.toDegrees(ex), (float) Math.toDegrees(ey), (float) Math.toDegrees(ez)};
    }

    /**
     * Drehung des Katanas: Klinge zeigt nach dir, Klingenrücken (Mune) nach up.
     * (Ruhe des Katana-Knochens: Klinge +Z, Rücken +Y.)
     */
    public static Quaternionf bladeFrame(Vector3f dir, Vector3f up) {
        Vector3f z = new Vector3f(dir).normalize();
        Vector3f y = new Vector3f(up).sub(new Vector3f(z).mul(up.dot(z)));
        if (y.lengthSquared() < 1e-6f) y.set(0, 1, 0).sub(new Vector3f(z).mul(z.y));
        if (y.lengthSquared() < 1e-6f) y.set(1, 0, 0);
        y.normalize();
        Vector3f x = new Vector3f(y).cross(z).normalize();
        Matrix3f m = new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z);
        return m.getNormalizedRotation(new Quaternionf());
    }

    // ------------------------------------------------------------------ Arm-IK

    /**
     * Setzt arm_s, forearm_s und hand_s so, dass der Greifpunkt der Hand bei targetPx liegt
     * und die Hand die Weltdrehung handRot hat. pole = Richtung, in die der Ellenbogen zeigen soll.
     */
    public static PoseSpec reach(ModelDef def, PoseSpec spec, String side, Vector3f targetPx, Quaternionf handRot, Vector3f pole) {
        String armN = "arm_" + side, foreN = "forearm_" + side, handN = "hand_" + side;
        BoneDef arm = def.bones.get(def.bone(armN));
        BoneDef fore = def.bones.get(def.bone(foreN));
        BoneDef hand = def.bones.get(def.bone(handN));
        // Arm-Gelenke vorerst neutral, damit die Rumpf-Lage stimmt
        spec.rot.remove(armN);
        spec.rot.remove(foreN);
        spec.rot.remove(handN);
        Xf[] xs = solve(def, spec);
        Xf parent = xs[arm.parent]; // Rumpf
        // Schulter (Weltraum, Blöcke)
        Vector3f shoulder = parent.apply(new Vector3f(arm.restOffset), new Vector3f());
        // Handgelenk-Ziel = Greifpunkt - Drehung * Greif-Versatz
        Vector3f target = new Vector3f(targetPx).mul(PX);
        Vector3f gripOff = handRot.transform(new Vector3f(0, -GRIP_PX * PX, 0));
        Vector3f wrist = new Vector3f(target).sub(gripOff);

        float a = fore.restOffset.length();
        float b = hand.restOffset.length();
        Vector3f toW = new Vector3f(wrist).sub(shoulder);
        float d = toW.length();
        d = MathUtil.clamp(d, Math.abs(a - b) + 1e-4f, a + b - 1e-4f);
        Vector3f tdir = new Vector3f(toW).normalize();
        // Ellenbogen-Ebene
        Vector3f p = new Vector3f(pole).normalize();
        Vector3f perp = new Vector3f(p).sub(new Vector3f(tdir).mul(p.dot(tdir)));
        if (perp.lengthSquared() < 1e-6f) {
            perp = new Vector3f(0, -1, 0).sub(new Vector3f(tdir).mul(-tdir.y));
            if (perp.lengthSquared() < 1e-6f) perp.set(1, 0, 0);
        }
        perp.normalize();
        float cosA = (a * a + d * d - b * b) / (2 * a * d);
        float alpha = (float) Math.acos(MathUtil.clamp(cosA, -1f, 1f));
        Vector3f elbow = new Vector3f(shoulder)
                .add(new Vector3f(tdir).mul((float) Math.cos(alpha) * a))
                .add(new Vector3f(perp).mul((float) Math.sin(alpha) * a));
        Vector3f wristReal = new Vector3f(shoulder).add(new Vector3f(tdir).mul(d));

        // Oberarm: Ruherichtung (im Rumpfraum) -> gewünschte Richtung
        Vector3f restUpper = new Vector3f(fore.restOffset).normalize();
        Vector3f upperWorld = new Vector3f(elbow).sub(shoulder).normalize();
        Quaternionf parentInv = new Quaternionf(parent.q).conjugate();
        Vector3f upperLocal = parentInv.transform(new Vector3f(upperWorld));
        Quaternionf qu = new Quaternionf().rotationTo(restUpper, upperLocal);

        // Unterarm
        Vector3f restLower = new Vector3f(hand.restOffset).normalize();
        Vector3f lowerWorld = new Vector3f(wristReal).sub(elbow).normalize();
        Quaternionf upperWorldRot = new Quaternionf(parent.q).mul(qu);
        Vector3f lowerLocal = new Quaternionf(upperWorldRot).conjugate().transform(new Vector3f(lowerWorld));
        Quaternionf qf = new Quaternionf().rotationTo(restLower, lowerLocal);

        // Hand: lokale Drehung = (Eltern-Weltdrehung)^-1 * gewünschte Weltdrehung
        Quaternionf foreWorldRot = new Quaternionf(upperWorldRot).mul(qf);
        Quaternionf qh = new Quaternionf(foreWorldRot).conjugate().mul(handRot);

        float[] eu = toEuler(qu), ef = toEuler(qf), eh = toEuler(qh);
        spec.r(armN, eu[0], eu[1], eu[2]);
        spec.r(foreN, ef[0], ef[1], ef[2]);
        spec.r(handN, eh[0], eh[1], eh[2]);
        return spec;
    }

    /**
     * Beidhändiger Katana-Griff: rechte Hand am Stichblatt (gripPx), linke Hand 3,6 px weiter
     * hinten am Griffende. dir = Klingenrichtung, up = Klingenrücken.
     */
    public static PoseSpec twoHand(ModelDef def, PoseSpec spec, Vector3f gripPx, Vector3f dir, Vector3f up,
                                   Vector3f poleR, Vector3f poleL) {
        Quaternionf frame = bladeFrame(dir, up);
        reach(def, spec, "r", gripPx, frame, poleR);
        Vector3f d = new Vector3f(dir).normalize();
        Vector3f gripL = new Vector3f(gripPx).sub(new Vector3f(d).mul(3.7f));
        // linke Hand: gleiche Ausrichtung, leicht um die Griffachse gedreht
        Quaternionf frameL = new Quaternionf().rotationAxis((float) Math.toRadians(-20), d).mul(frame);
        reach(def, spec, "l", gripL, frameL, poleL);
        return spec;
    }

    /** Rechte Hand allein führt das Katana. */
    public static PoseSpec oneHand(ModelDef def, PoseSpec spec, Vector3f gripPx, Vector3f dir, Vector3f up, Vector3f pole) {
        return reach(def, spec, "r", gripPx, bladeFrame(dir, up), pole);
    }

    public static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }
}
