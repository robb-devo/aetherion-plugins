package net.aetherion.samuraiboss.render;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Helfer zum Bauen von Display-Transformationen.
 *
 * <p>Wichtig: Ein BlockDisplay zeichnet seinen Block von (0,0,0) bis (1,1,1) – der Ursprung
 * ist also eine Ecke. Für mittige Platzierung muss die Translation um die halbe (gedrehte)
 * Größe zurückversetzt werden – genau das macht {@link #box}.</p>
 */
public final class Tf {

    private Tf() {
    }

    public static Transformation of(Vector3f t, Quaternionf q, Vector3f s) {
        return new Transformation(new Vector3f(t), new Quaternionf(q), new Vector3f(s), new Quaternionf());
    }

    public static Transformation of(float tx, float ty, float tz, Quaternionf q, float sx, float sy, float sz) {
        return new Transformation(new Vector3f(tx, ty, tz), new Quaternionf(q), new Vector3f(sx, sy, sz), new Quaternionf());
    }

    /** Block-Quader mit Mittelpunkt center (relativ zum Entity), Drehung q und Größe size. */
    public static Transformation box(Vector3f center, Quaternionf q, Vector3f size) {
        Vector3f half = new Vector3f(size).mul(0.5f);
        q.transform(half);
        return new Transformation(new Vector3f(center).sub(half), new Quaternionf(q), new Vector3f(size), new Quaternionf());
    }

    public static Transformation box(float cx, float cy, float cz, Quaternionf q, float sx, float sy, float sz) {
        return box(new Vector3f(cx, cy, cz), q, new Vector3f(sx, sy, sz));
    }

    /**
     * Balken entlang der lokalen X-Achse: beginnt bei 'start', Länge 'len', Querschnitt w x h,
     * gedreht mit q (q dreht +X in die gewünschte Richtung). Der Querschnitt ist zentriert.
     */
    public static Transformation beam(Vector3f start, Quaternionf q, float len, float w, float h) {
        Vector3f off = new Vector3f(0f, -h / 2f, -w / 2f);
        q.transform(off);
        return new Transformation(new Vector3f(start).add(off), new Quaternionf(q), new Vector3f(len, h, w), new Quaternionf());
    }

    /** Item/Text-Display: Ursprung ist bereits die Mitte. */
    public static Transformation centered(Vector3f center, Quaternionf q, float scale) {
        return new Transformation(new Vector3f(center), new Quaternionf(q), new Vector3f(scale, scale, scale), new Quaternionf());
    }

    public static Transformation centered(Vector3f center, Quaternionf q, Vector3f scale) {
        return new Transformation(new Vector3f(center), new Quaternionf(q), new Vector3f(scale), new Quaternionf());
    }

    /**
     * Skaliert eine Block-Transformation um ihren eigenen Mittelpunkt (für "Zusammenschrumpfen"
     * ohne Wegrutschen).
     */
    public static Transformation shrinkBox(Transformation t, float k) {
        Vector3f s = t.getScale();
        Quaternionf q = t.getLeftRotation();
        Vector3f halfOld = q.transform(new Vector3f(s).mul(0.5f));
        Vector3f center = new Vector3f(t.getTranslation()).add(halfOld);
        return box(center, q, new Vector3f(s).mul(k));
    }

    /** Mittelpunkt einer Block-Transformation (relativ zum Entity). */
    public static Vector3f boxCenter(Transformation t) {
        Vector3f half = t.getLeftRotation().transform(new Vector3f(t.getScale()).mul(0.5f));
        return new Vector3f(t.getTranslation()).add(half);
    }
}
