package net.aetherion.samuraiboss.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Eine "Ähnlichkeits-Transformation": Verschiebung + Drehung + gleichmäßige Skalierung.
 *
 * <p>Warum keine volle 4x4-Matrix? Display-Entities bekommen ihre Transformation als
 * (Translation, linke Rotation, Skalierung, rechte Rotation). Wenn wir Knochen nur gleichmäßig
 * skalieren, lässt sich jede verkettete Transformation exakt in genau diese Form bringen –
 * ohne Matrix-Zerlegung (die würde zwischen zwei Frames die Rotationen "springen" lassen und
 * beim Client-Interpolieren hässliche Verdrehungen erzeugen).</p>
 *
 * <p>Anwendung auf einen Punkt p:  p' = t + s * (q * p)</p>
 */
public final class Xf {

    public final Vector3f t = new Vector3f();
    public final Quaternionf q = new Quaternionf();
    public float s = 1f;

    public Xf() {
    }

    public Xf(Vector3f t, Quaternionf q, float s) {
        this.t.set(t);
        this.q.set(q);
        this.s = s;
    }

    public static Xf identity() {
        return new Xf();
    }

    public Xf set(Xf o) {
        t.set(o.t);
        q.set(o.q);
        s = o.s;
        return this;
    }

    public Xf identitySelf() {
        t.zero();
        q.identity();
        s = 1f;
        return this;
    }

    public Xf copy() {
        return new Xf().set(this);
    }

    /**
     * dest = this ∘ b  (erst b, dann this). dest darf weder this noch b sein.
     */
    public Xf mul(Xf b, Xf dest) {
        // t = t_a + s_a * (q_a * t_b)
        q.transform(b.t, dest.t);
        dest.t.mul(s).add(t);
        q.mul(b.q, dest.q);
        dest.s = s * b.s;
        return dest;
    }

    /** Wendet die Transformation auf einen Punkt an (dest darf p sein). */
    public Vector3f apply(Vector3f p, Vector3f dest) {
        q.transform(p, dest);
        return dest.mul(s).add(t);
    }

    /** Wendet nur Drehung + Skalierung an (für Richtungen/Größen). */
    public Vector3f applyDir(Vector3f d, Vector3f dest) {
        q.transform(d, dest);
        return dest.mul(s);
    }

    @Override
    public String toString() {
        return "Xf{t=" + t + ", q=" + q + ", s=" + s + '}';
    }
}
