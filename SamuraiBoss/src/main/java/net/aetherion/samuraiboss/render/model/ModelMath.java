package net.aetherion.samuraiboss.render.model;

import net.aetherion.samuraiboss.render.Xf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Reine Mathematik der Knochen-Hierarchie (ohne Bukkit) – wird vom lebenden Modell und von
 * Werkzeugen wie Vorschau-Renderern benutzt.
 */
public final class ModelMath {

    private ModelMath() {
    }

    /**
     * Berechnet die Weltlage (relativ zum Modell-Ursprung) aller Knochen.
     *
     * @param root Transformation des Modells (Drehung/Skalierung/Versatz)
     * @param out  Ausgabe, Länge = Anzahl Knochen
     */
    public static void solve(ModelDef def, Pose pose, Xf root, Xf[] out) {
        Xf local = new Xf();
        for (BoneDef b : def.bones) {
            int i = b.index;
            local.t.set(b.restOffset).add(pose.pos[i]);
            local.q.set(pose.rot[i]);
            local.s = pose.scale[i];
            Xf parent = b.parent < 0 ? root : out[b.parent];
            parent.mul(local, out[i]);
        }
    }

    /** Transformation eines Teils aus der Knochenlage: (t, q, s) -> Display-Transformation. */
    public static void cube(CubeDef c, Xf bone, Vector3f outT, Quaternionf outQ, Vector3f outS) {
        bone.apply(c.localT, outT);
        bone.q.mul(c.localQ, outQ);
        outS.set(c.localS).mul(bone.s);
    }
}
