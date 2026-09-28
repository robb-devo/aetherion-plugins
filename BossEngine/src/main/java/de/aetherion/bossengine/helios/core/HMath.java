package de.aetherion.bossengine.helios.core;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Easing, timing, vector and solid-geometry helpers for the whole Helios encounter.
 * Every timeline is authored in integer ticks and shaped through these curves.
 * Bukkit-free on purpose: the geometry (portal chains, polyhedra, orbits) can be exercised offline.
 */
public final class HMath {

    public static final float PI = (float) Math.PI;
    public static final float TAU = PI * 2f;
    public static final float HALF_PI = PI * 0.5f;
    public static final float PHI = (1f + (float) Math.sqrt(5.0)) * 0.5f;
    public static final Vector3fc UP = new Vector3f(0f, 1f, 0f);

    private HMath() {
    }

    /* ------------------------------------------------------------------ scalars */

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** 0..1 progress of {@code tick} inside the window [from, to). */
    public static float window(int tick, int from, int to) {
        if (to <= from) {
            return tick >= to ? 1f : 0f;
        }
        return clamp01((tick - from) / (float) (to - from));
    }

    /** 1 inside [from, to), 0 outside. */
    public static boolean inside(int tick, int from, int to) {
        return tick >= from && tick < to;
    }

    public static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    public static float smoother(float t) {
        t = clamp01(t);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    public static float inQuad(float t) {
        t = clamp01(t);
        return t * t;
    }

    public static float outQuad(float t) {
        t = clamp01(t);
        return 1f - (1f - t) * (1f - t);
    }

    public static float inCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float outCubic(float t) {
        t = clamp01(t) - 1f;
        return t * t * t + 1f;
    }

    public static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    public static float inExpo(float t) {
        t = clamp01(t);
        return t == 0f ? 0f : (float) Math.pow(2, 10 * t - 10);
    }

    public static float outExpo(float t) {
        t = clamp01(t);
        return t == 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
    }

    public static float outBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    public static float inBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return c3 * t * t * t - c1 * t * t;
    }

    public static float outElastic(float t) {
        t = clamp01(t);
        if (t == 0f || t == 1f) {
            return t;
        }
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (TAU / 3f)) + 1);
    }

    /** Parabolic hop: 0 at both ends, 1 at the middle. */
    public static float arc(float t) {
        t = clamp01(t);
        return 4f * t * (1f - t);
    }

    /** Rises then falls: 0 → 1 over [0, peak], 1 → 0 over [peak, 1]. */
    public static float tent(float t, float peak) {
        t = clamp01(t);
        if (t <= peak) {
            return peak <= 0f ? 1f : t / peak;
        }
        return peak >= 1f ? 1f : 1f - (t - peak) / (1f - peak);
    }

    /** Damped shake: 1 at t = 0, decaying oscillation to 0 at t = 1. */
    public static float shake(float t, float cycles) {
        t = clamp01(t);
        return (float) Math.sin(t * cycles * TAU) * (1f - t);
    }

    /** A heartbeat envelope over one beat: a hard "lub", a softer "dub", then rest. */
    public static float heartbeat(float t) {
        t = t - (float) Math.floor(t);
        float lub = (float) Math.exp(-Math.pow((t - 0.06f) / 0.05f, 2));
        float dub = 0.6f * (float) Math.exp(-Math.pow((t - 0.28f) / 0.06f, 2));
        return clamp01(lub + dub);
    }

    public static float wrap(float angle) {
        while (angle > PI) {
            angle -= TAU;
        }
        while (angle < -PI) {
            angle += TAU;
        }
        return angle;
    }

    public static float approachAngle(float from, float to, float maxStep) {
        float d = wrap(to - from);
        if (Math.abs(d) <= maxStep) {
            return to;
        }
        return from + Math.signum(d) * maxStep;
    }

    /** Deterministic 0..1 hash noise for a seed pair (stable jitter without Random state). */
    public static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    /* ------------------------------------------------------------------ vectors */

    public static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    public static Vector3f lerp(Vector3fc a, Vector3fc b, float t, Vector3f out) {
        return out.set(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t);
    }

    /** Point on a horizontal circle of radius r at angle a, height y. */
    public static Vector3f ring(float r, float a, float y) {
        return new Vector3f((float) Math.cos(a) * r, y, (float) Math.sin(a) * r);
    }

    /** Minecraft yaw (radians) facing along +x/+z direction (dx, dz). */
    public static float yawToward(float dx, float dz) {
        return (float) Math.atan2(-dx, dz);
    }

    /** Angle on the XZ plane (atan2(z, x)), matching {@link #ring}. */
    public static float angleOf(float x, float z) {
        return (float) Math.atan2(z, x);
    }

    public static float horizontal(Vector3fc v) {
        return (float) Math.sqrt(v.x() * v.x() + v.z() * v.z());
    }

    /** Rotation whose local +Z points along {@code dir} and local +Y leans toward {@code up}. */
    public static Quaternionf frame(Vector3fc dir, Vector3fc up) {
        Vector3f z = new Vector3f(dir).normalize();
        Vector3f x = new Vector3f(up).cross(z);
        if (x.lengthSquared() < 1e-6f) {
            x = new Vector3f(1f, 0f, 0f).cross(z);
            if (x.lengthSquared() < 1e-6f) {
                x.set(0f, 0f, 1f).cross(z);
            }
        }
        x.normalize();
        Vector3f y = new Vector3f(z).cross(x).normalize();
        Matrix3f m = new Matrix3f(x, y, z);
        return new Quaternionf().setFromNormalized(m);
    }

    /** Rotation taking +Y onto {@code dir}. */
    public static Quaternionf alignY(Vector3fc dir) {
        Vector3f d = new Vector3f(dir);
        float len = d.length();
        if (len < 1e-6f) {
            return new Quaternionf();
        }
        d.div(len);
        return new Quaternionf().rotationTo(0f, 1f, 0f, d.x, d.y, d.z);
    }

    /** Squared distance from p to the segment a-b. */
    public static float segmentDistSq(Vector3fc p, Vector3fc a, Vector3fc b) {
        float abx = b.x() - a.x();
        float aby = b.y() - a.y();
        float abz = b.z() - a.z();
        float apx = p.x() - a.x();
        float apy = p.y() - a.y();
        float apz = p.z() - a.z();
        float len2 = abx * abx + aby * aby + abz * abz;
        float t = len2 < 1e-8f ? 0f : clamp01((apx * abx + apy * aby + apz * abz) / len2);
        float dx = apx - abx * t;
        float dy = apy - aby * t;
        float dz = apz - abz * t;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Parameter t (0..1) of the point on segment a-b closest to p. */
    public static float segmentT(Vector3fc p, Vector3fc a, Vector3fc b) {
        Vector3f ab = new Vector3f(b).sub(a);
        float len2 = ab.lengthSquared();
        if (len2 < 1e-8f) {
            return 0f;
        }
        return clamp01(new Vector3f(p).sub(a).dot(ab) / len2);
    }

    /**
     * Distance from a player's body (a vertical capsule from feet to feet + height) to a segment.
     * Sampled at feet, waist and head, which is what a beam can visibly touch.
     */
    public static float bodyDistSq(Vector3fc feet, float height, Vector3fc a, Vector3fc b) {
        Vector3f p = new Vector3f(feet);
        float best = segmentDistSq(p, a, b);
        p.y += height * 0.5f;
        best = Math.min(best, segmentDistSq(p, a, b));
        p.y = feet.y() + height * 0.9f;
        best = Math.min(best, segmentDistSq(p, a, b));
        return best;
    }

    /**
     * Ray-plane intersection. Plane through {@code c} with normal {@code n}.
     *
     * @return distance along the (normalized) ray, or -1 when parallel / behind
     */
    public static float rayPlane(Vector3fc origin, Vector3fc dir, Vector3fc c, Vector3fc n) {
        float denom = n.dot(dir);
        if (Math.abs(denom) < 1e-5f) {
            return -1f;
        }
        float t = new Vector3f(c).sub(origin).dot(n) / denom;
        return t < 0f ? -1f : t;
    }

    /** Ray against a disc (center c, normal n, radius r). @return distance or -1 */
    public static float rayDisc(Vector3fc origin, Vector3fc dir, Vector3fc c, Vector3fc n, float r) {
        float t = rayPlane(origin, dir, c, n);
        if (t < 0f) {
            return -1f;
        }
        Vector3f hit = new Vector3f(dir).mul(t).add(origin);
        return hit.distanceSquared(c) <= r * r ? t : -1f;
    }

    /** Ray to the horizontal plane y = h. @return distance or -1 */
    public static float rayFloor(Vector3fc origin, Vector3fc dir, float h) {
        if (Math.abs(dir.y()) < 1e-5f) {
            return -1f;
        }
        float t = (h - origin.y()) / dir.y();
        return t < 0f ? -1f : t;
    }

    /** Reflects {@code d} through a portal: the in-direction relative to the entry is re-expressed at the exit. */
    public static Vector3f throughPortal(Vector3fc d, Quaternionf entry, Quaternionf exit) {
        // Enter along -entryNormal means leave along +exitNormal (portals face outward).
        Vector3f local = new Quaternionf(entry).conjugate().transform(new Vector3f(d));
        local.z = -local.z;
        local.x = -local.x;
        return new Quaternionf(exit).transform(local).normalize();
    }

    /* ------------------------------------------------------------------ solids */

    /** The 12 vertices of a unit-circumradius icosahedron. */
    public static Vector3f[] icosahedron() {
        float p = PHI;
        float[][] raw = {
                {-1, p, 0}, {1, p, 0}, {-1, -p, 0}, {1, -p, 0},
                {0, -1, p}, {0, 1, p}, {0, -1, -p}, {0, 1, -p},
                {p, 0, -1}, {p, 0, 1}, {-p, 0, -1}, {-p, 0, 1}
        };
        Vector3f[] out = new Vector3f[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = new Vector3f(raw[i][0], raw[i][1], raw[i][2]).normalize();
        }
        return out;
    }

    /** Edges (index pairs) of a solid: every vertex pair at the minimum distance. */
    public static int[][] edges(Vector3f[] verts) {
        float min = Float.MAX_VALUE;
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 1; j < verts.length; j++) {
                min = Math.min(min, verts[i].distance(verts[j]));
            }
        }
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 1; j < verts.length; j++) {
                if (verts[i].distance(verts[j]) < min * 1.05f) {
                    out.add(new int[]{i, j});
                }
            }
        }
        return out.toArray(new int[0][]);
    }

    /** Evenly spread points on a unit sphere (Fibonacci lattice). */
    public static Vector3f[] sphere(int n) {
        Vector3f[] out = new Vector3f[Math.max(1, n)];
        float golden = PI * (3f - (float) Math.sqrt(5.0));
        for (int i = 0; i < out.length; i++) {
            float y = 1f - (i / (float) Math.max(1, out.length - 1)) * 2f;
            float r = (float) Math.sqrt(Math.max(0f, 1f - y * y));
            float a = golden * i;
            out[i] = new Vector3f((float) Math.cos(a) * r, y, (float) Math.sin(a) * r);
        }
        return out;
    }

    /** Quadratic Bezier. */
    public static Vector3f bezier(Vector3fc a, Vector3fc c, Vector3fc b, float t, Vector3f out) {
        float u = 1f - t;
        return out.set(
                u * u * a.x() + 2 * u * t * c.x() + t * t * b.x(),
                u * u * a.y() + 2 * u * t * c.y() + t * t * b.y(),
                u * u * a.z() + 2 * u * t * c.z() + t * t * b.z());
    }

    /** Kepler-ish angular speed for an orbit of radius r (inner orbits run faster). */
    public static float orbitSpeed(float r, float base) {
        return base / (float) Math.pow(Math.max(0.5f, r), 1.5);
    }
}
