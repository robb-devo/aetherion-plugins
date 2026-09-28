package de.aetherion.bossengine.instance.worldeater;

import org.joml.Vector3f;

/**
 * The path the serpent's head has travelled, with the head's "up" at every sample and the
 * cumulative arc length. The body is laid along it: a vertebra sits at a fixed arc distance
 * behind the head and inherits the orientation the head had when it passed that point, so the
 * whole body follows the head exactly, rolls included, with no per-segment physics.
 *
 * <p>Samples live in a ring buffer. The trail can also be cut back from the head end
 * ({@link #retract(float)}): the serpent then slides backward through its own history.
 * Bukkit-free.
 */
public final class SpineTrail {

    private final float[] px;
    private final float[] py;
    private final float[] pz;
    private final float[] ux;
    private final float[] uy;
    private final float[] uz;
    private final float[] s;
    private final int cap;
    private int head = -1;
    private int count;
    private final float keep;

    /**
     * @param capacity maximum samples kept
     * @param keepLength arc length to keep behind the newest sample (body length plus rewind room)
     */
    public SpineTrail(int capacity, float keepLength) {
        this.cap = capacity;
        this.keep = keepLength;
        px = new float[capacity];
        py = new float[capacity];
        pz = new float[capacity];
        ux = new float[capacity];
        uy = new float[capacity];
        uz = new float[capacity];
        s = new float[capacity];
    }

    public int size() {
        return count;
    }

    public boolean empty() {
        return count == 0;
    }

    public void clear() {
        head = -1;
        count = 0;
    }

    private int index(int back) {
        return Math.floorMod(head - back, cap);
    }

    /** Arc length at the newest sample. */
    public float newestS() {
        return count == 0 ? 0f : s[head];
    }

    /** Arc length at the oldest sample. */
    public float oldestS() {
        return count == 0 ? 0f : s[index(count - 1)];
    }

    public Vector3f newest(Vector3f out) {
        if (count == 0) {
            return out.zero();
        }
        return out.set(px[head], py[head], pz[head]);
    }

    /** Appends a sample. Tiny moves only refresh the newest sample instead of adding one. */
    public void push(Vector3f p, Vector3f up) {
        if (count > 0) {
            float dx = p.x - px[head];
            float dy = p.y - py[head];
            float dz = p.z - pz[head];
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 0.02f) {
                ux[head] = up.x;
                uy[head] = up.y;
                uz[head] = up.z;
                return;
            }
            float ns = s[head] + d;
            head = (head + 1) % cap;
            write(head, p, up, ns);
            count = Math.min(cap, count + 1);
        } else {
            head = 0;
            write(0, p, up, 0f);
            count = 1;
        }
        prune();
    }

    private void write(int i, Vector3f p, Vector3f up, float arc) {
        px[i] = p.x;
        py[i] = p.y;
        pz[i] = p.z;
        ux[i] = up.x;
        uy[i] = up.y;
        uz[i] = up.z;
        s[i] = arc;
    }

    /** Drops samples older than the kept length (always keeps two). */
    private void prune() {
        float limit = s[head] - keep;
        while (count > 2 && s[index(count - 2)] < limit) {
            count--;
        }
    }

    /**
     * Seeds a straight trail of {@code length} blocks ending at {@code end}, arriving along
     * {@code dir}: the body starts laid out behind the head instead of emerging.
     */
    public void seedStraight(Vector3f end, Vector3f dir, Vector3f up, float length, float step) {
        clear();
        Vector3f d = new Vector3f(dir).normalize();
        int n = Math.max(2, (int) Math.ceil(length / step));
        for (int i = n; i >= 0; i--) {
            push(new Vector3f(end).fma(-i * step, d), up);
        }
    }

    /**
     * Samples the trail at arc length {@code at}.
     *
     * @return false (and the oldest sample) when the trail does not reach that far back yet
     */
    public boolean sample(float at, Vector3f outPos, Vector3f outUp, Vector3f outTangent) {
        if (count == 0) {
            outPos.zero();
            outUp.set(0f, 1f, 0f);
            outTangent.set(0f, 0f, 1f);
            return false;
        }
        if (count == 1) {
            outPos.set(px[head], py[head], pz[head]);
            outUp.set(ux[head], uy[head], uz[head]);
            outTangent.set(0f, 0f, 1f);
            return at >= s[head] - 1e-3f;
        }
        int oldest = index(count - 1);
        if (at <= s[oldest]) {
            int next = index(count - 2);
            outPos.set(px[oldest], py[oldest], pz[oldest]);
            outUp.set(ux[oldest], uy[oldest], uz[oldest]);
            tangent(oldest, next, outTangent);
            return at >= s[oldest] - 1e-3f;
        }
        if (at >= s[head]) {
            int prev = index(1);
            outPos.set(px[head], py[head], pz[head]);
            outUp.set(ux[head], uy[head], uz[head]);
            tangent(prev, head, outTangent);
            return true;
        }
        // Binary search over "back" offsets: s decreases as back grows.
        int lo = 0;
        int hi = count - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (s[index(mid)] >= at) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        int newer = index(lo);
        int older = index(hi);
        float span = s[newer] - s[older];
        float t = span < 1e-6f ? 0f : (at - s[older]) / span;
        outPos.set(
                px[older] + (px[newer] - px[older]) * t,
                py[older] + (py[newer] - py[older]) * t,
                pz[older] + (pz[newer] - pz[older]) * t);
        outUp.set(
                ux[older] + (ux[newer] - ux[older]) * t,
                uy[older] + (uy[newer] - uy[older]) * t,
                uz[older] + (uz[newer] - uz[older]) * t);
        if (outUp.lengthSquared() < 1e-6f) {
            outUp.set(0f, 1f, 0f);
        } else {
            outUp.normalize();
        }
        tangent(older, newer, outTangent);
        return true;
    }

    private void tangent(int from, int to, Vector3f out) {
        out.set(px[to] - px[from], py[to] - py[from], pz[to] - pz[from]);
        if (out.lengthSquared() < 1e-8f) {
            out.set(0f, 0f, 1f);
        } else {
            out.normalize();
        }
    }

    /**
     * Cuts the trail back to arc length {@code at} (the head slides backward through its history).
     *
     * @return the new newest position
     */
    public Vector3f retract(float at) {
        Vector3f pos = new Vector3f();
        Vector3f up = new Vector3f();
        Vector3f tan = new Vector3f();
        if (count < 2 || at >= s[head]) {
            return newest(pos);
        }
        sample(at, pos, up, tan);
        while (count > 2 && s[index(1)] >= at) {
            head = index(1);
            count--;
        }
        write(head, pos, up, Math.max(at, s[index(1)] + 1e-4f));
        return pos;
    }
}
