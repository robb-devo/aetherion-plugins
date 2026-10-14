package net.aetherion.samuraiboss.render.anim;

import net.aetherion.samuraiboss.util.Ease;
import net.aetherion.samuraiboss.util.MathUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Ein Animations-Clip: Keyframes pro Knochen + benannte Ereignisse (z. B. "hit", "click").
 *
 * <p>Drehungen werden als Euler-Winkel (Grad) gespeichert und linear/eased zwischen Keyframes
 * interpoliert – dadurch sind auch Drehungen über 180° (Wirbel-Schnitte) möglich.
 * Erst nach der Interpolation wird in ein Quaternion umgerechnet.</p>
 */
public final class Clip {

    public record Event(float time, String name) {
    }

    /** Spuren eines Knochens. Arrays sind nach Zeit sortiert. */
    static final class BoneTrack {
        float[] rT;
        float[][] rV; // x,y,z Grad
        Ease[] rE;
        float[] pT;
        float[][] pV; // x,y,z Pixel
        Ease[] pE;
        float[] sT;
        float[] sV;
        Ease[] sE;

        boolean hasRot() {
            return rT != null && rT.length > 0;
        }

        boolean hasPos() {
            return pT != null && pT.length > 0;
        }

        boolean hasScale() {
            return sT != null && sT.length > 0;
        }
    }

    public final String name;
    public final float length;
    public final boolean loop;
    /** Hält am Ende den letzten Frame (z. B. Kampfhaltung), bis eine andere Animation kommt. */
    public final boolean hold;
    /** Empfohlener Update-Abstand in Ticks (1 = sehr schnelle Bewegung). */
    public final int sampleInterval;
    final BoneTrack[] tracks;
    public final List<Event> events;

    Clip(String name, float length, boolean loop, boolean hold, int sampleInterval, BoneTrack[] tracks, List<Event> events) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.hold = hold;
        this.sampleInterval = sampleInterval;
        this.tracks = tracks;
        this.events = events;
    }

    public boolean affects(int bone) {
        return bone < tracks.length && tracks[bone] != null;
    }

    /**
     * Tastet einen Knochen zur Zeit t ab.
     *
     * @return true, wenn der Clip diesen Knochen animiert
     */
    boolean sample(int bone, float t, Quaternionf outRot, Vector3f outPosBlocks, float[] outScale) {
        BoneTrack tr = bone < tracks.length ? tracks[bone] : null;
        if (tr == null) return false;
        if (tr.hasRot()) {
            float[] e = sampleVec(tr.rT, tr.rV, tr.rE, t);
            MathUtil.euler(outRot, e[0], e[1], e[2]);
        } else {
            outRot.identity();
        }
        if (tr.hasPos()) {
            float[] p = sampleVec(tr.pT, tr.pV, tr.pE, t);
            outPosBlocks.set(p[0], p[1], p[2]).mul(1f / 16f);
        } else {
            outPosBlocks.zero();
        }
        if (tr.hasScale()) {
            outScale[0] = sampleScalar(tr.sT, tr.sV, tr.sE, t);
        } else {
            outScale[0] = 1f;
        }
        return true;
    }

    private static float[] sampleVec(float[] times, float[][] vals, Ease[] eases, float t) {
        int n = times.length;
        if (t <= times[0]) return vals[0];
        if (t >= times[n - 1]) return vals[n - 1];
        int i = 1;
        while (i < n && times[i] < t) i++;
        float t0 = times[i - 1], t1 = times[i];
        float u = t1 > t0 ? (t - t0) / (t1 - t0) : 1f;
        u = eases[i].apply(Ease.clamp01(u));
        float[] a = vals[i - 1], b = vals[i];
        return new float[]{a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u};
    }

    private static float sampleScalar(float[] times, float[] vals, Ease[] eases, float t) {
        int n = times.length;
        if (t <= times[0]) return vals[0];
        if (t >= times[n - 1]) return vals[n - 1];
        int i = 1;
        while (i < n && times[i] < t) i++;
        float t0 = times[i - 1], t1 = times[i];
        float u = t1 > t0 ? (t - t0) / (t1 - t0) : 1f;
        u = eases[i].apply(Ease.clamp01(u));
        return vals[i - 1] + (vals[i] - vals[i - 1]) * u;
    }
}
