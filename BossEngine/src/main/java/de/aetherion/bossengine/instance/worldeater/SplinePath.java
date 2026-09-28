package de.aetherion.bossengine.instance.worldeater;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A smooth path for the serpent's head, sampled densely and parameterized by arc length, so a
 * move can say "advance 1.2 blocks this tick" and the head glides at an exact speed along a curve
 * that never kinks. Built from Catmull-Rom control points or a Hermite leg (position + direction
 * at both ends, for joining a move onto the head's current heading). Bukkit-free.
 */
public final class SplinePath {

    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] s;
    private final int n;

    private SplinePath(List<Vector3f> dense) {
        n = dense.size();
        x = new float[n];
        y = new float[n];
        z = new float[n];
        s = new float[n];
        float acc = 0f;
        for (int i = 0; i < n; i++) {
            Vector3f p = dense.get(i);
            if (i > 0) {
                acc += p.distance(dense.get(i - 1));
            }
            x[i] = p.x;
            y[i] = p.y;
            z[i] = p.z;
            s[i] = acc;
        }
    }

    /** Uniform Catmull-Rom through every control point (ends duplicated). */
    public static SplinePath through(List<Vector3f> controls, int perSegment) {
        List<Vector3f> dense = new ArrayList<>();
        int m = controls.size();
        if (m == 1) {
            dense.add(new Vector3f(controls.get(0)));
            dense.add(new Vector3f(controls.get(0)).add(0f, 0f, 0.001f));
            return new SplinePath(dense);
        }
        for (int i = 0; i < m - 1; i++) {
            Vector3f p0 = controls.get(Math.max(0, i - 1));
            Vector3f p1 = controls.get(i);
            Vector3f p2 = controls.get(i + 1);
            Vector3f p3 = controls.get(Math.min(m - 1, i + 2));
            for (int k = 0; k < perSegment; k++) {
                float t = k / (float) perSegment;
                dense.add(catmull(p0, p1, p2, p3, t));
            }
        }
        dense.add(new Vector3f(controls.get(m - 1)));
        return new SplinePath(dense);
    }

    /** Hermite leg from p0 heading d0 to p1 heading d1; {@code bend} scales the handles. */
    public static SplinePath hermite(Vector3f p0, Vector3f d0, Vector3f p1, Vector3f d1, float bend, int samples) {
        float dist = p0.distance(p1);
        Vector3f m0 = new Vector3f(d0).normalize().mul(dist * bend);
        Vector3f m1 = new Vector3f(d1).normalize().mul(dist * bend);
        List<Vector3f> dense = new ArrayList<>();
        for (int i = 0; i <= samples; i++) {
            float t = i / (float) samples;
            float t2 = t * t;
            float t3 = t2 * t;
            float h00 = 2 * t3 - 3 * t2 + 1;
            float h10 = t3 - 2 * t2 + t;
            float h01 = -2 * t3 + 3 * t2;
            float h11 = t3 - t2;
            dense.add(new Vector3f(p0).mul(h00).fma(h10, m0).fma(h01, p1).fma(h11, m1));
        }
        return new SplinePath(dense);
    }

    /** A circle around (cx, cz) at radius r, starting at angle a0, {@code turns} turns (sign = direction). */
    public static SplinePath circle(float cx, float y, float cz, float r, float a0, float turns, float bob, float bobWaves, int samples) {
        List<Vector3f> dense = new ArrayList<>();
        for (int i = 0; i <= samples; i++) {
            float t = i / (float) samples;
            float a = a0 + t * turns * WeMath.TAU;
            float h = y + (float) Math.sin(t * bobWaves * WeMath.TAU) * bob;
            dense.add(new Vector3f(cx + (float) Math.sin(a) * r, h, cz + (float) Math.cos(a) * r));
        }
        return new SplinePath(dense);
    }

    private static Vector3f catmull(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        Vector3f out = new Vector3f();
        out.x = 0.5f * ((2f * p1.x) + (-p0.x + p2.x) * t + (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 + (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3);
        out.y = 0.5f * ((2f * p1.y) + (-p0.y + p2.y) * t + (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 + (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3);
        out.z = 0.5f * ((2f * p1.z) + (-p0.z + p2.z) * t + (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * t2 + (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * t3);
        return out;
    }

    public float length() {
        return s[n - 1];
    }

    public Vector3f start(Vector3f out) {
        return out.set(x[0], y[0], z[0]);
    }

    public Vector3f end(Vector3f out) {
        return out.set(x[n - 1], y[n - 1], z[n - 1]);
    }

    private int find(float at) {
        int lo = 0;
        int hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (s[mid] <= at) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** Position at arc length {@code at} (clamped to the path). */
    public Vector3f at(float at, Vector3f out) {
        if (at <= 0f) {
            return start(out);
        }
        if (at >= s[n - 1]) {
            return end(out);
        }
        int i = find(at);
        float span = s[i + 1] - s[i];
        float t = span < 1e-6f ? 0f : (at - s[i]) / span;
        return out.set(x[i] + (x[i + 1] - x[i]) * t, y[i] + (y[i + 1] - y[i]) * t, z[i] + (z[i + 1] - z[i]) * t);
    }

    /** Unit direction of travel at arc length {@code at}. */
    public Vector3f tangent(float at, Vector3f out) {
        int i = at <= 0f ? 0 : at >= s[n - 1] ? n - 2 : find(at);
        i = Math.max(0, Math.min(n - 2, i));
        out.set(x[i + 1] - x[i], y[i + 1] - y[i], z[i + 1] - z[i]);
        if (out.lengthSquared() < 1e-10f) {
            return out.set(0f, 0f, 1f);
        }
        return out.normalize();
    }

    /** Closest arc length to a point (coarse, over the dense samples). */
    public float nearest(Vector3f p) {
        float best = Float.MAX_VALUE;
        float at = 0f;
        for (int i = 0; i < n; i++) {
            float dx = x[i] - p.x;
            float dy = y[i] - p.y;
            float dz = z[i] - p.z;
            float d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                at = s[i];
            }
        }
        return at;
    }
}
