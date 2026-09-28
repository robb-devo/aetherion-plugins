package de.aetherion.bossengine.instance.worldeater;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Easing, timing and small vector helpers shared by the whole World Eater encounter.
 * Every timeline is authored in integer ticks and shaped through these curves.
 * Bukkit-free on purpose: the spine, rig and path math can be exercised offline.
 */
public final class WeMath {

    public static final float PI = (float) Math.PI;
    public static final float TAU = PI * 2f;
    public static final float HALF_PI = PI * 0.5f;
    public static final Vector3fc UP = new Vector3f(0f, 1f, 0f);

    private WeMath() {
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }

    public static double clamp(double v, double lo, double hi) {
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

    /** Damped shake: 1 at t = 0, decaying oscillation to 0 at t = 1. */
    public static float shake(float t, float cycles) {
        t = clamp01(t);
        return (float) Math.sin(t * cycles * TAU) * (1f - t);
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

    public static float approach(float from, float to, float maxStep) {
        float d = to - from;
        if (Math.abs(d) <= maxStep) {
            return to;
        }
        return from + Math.signum(d) * maxStep;
    }

    public static float approachAngle(float from, float to, float maxStep) {
        float delta = wrap(to - from);
        if (Math.abs(delta) <= maxStep) {
            return from + delta;
        }
        return from + Math.signum(delta) * maxStep;
    }

    /** Yaw that makes local +Z face along (dx, dz). */
    public static float yawToward(float dx, float dz) {
        return (float) Math.atan2(dx, dz);
    }

    public static float horizontal(Vector3f a, Vector3f b) {
        float dx = a.x - b.x;
        float dz = a.z - b.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    public static Vector3f lerp(Vector3f a, Vector3f b, float t, Vector3f out) {
        out.set(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t));
        return out;
    }

    /** Distance from p to segment ab, in the XZ plane only. */
    public static float segmentDistanceXZ(Vector3f p, Vector3f a, Vector3f b) {
        float abx = b.x - a.x;
        float abz = b.z - a.z;
        float len = abx * abx + abz * abz;
        float t = len < 1e-6f ? 0f : clamp01(((p.x - a.x) * abx + (p.z - a.z) * abz) / len);
        float cx = a.x + abx * t - p.x;
        float cz = a.z + abz * t - p.z;
        return (float) Math.sqrt(cx * cx + cz * cz);
    }

    /** Parameter (0..1) of the closest point to p on segment ab, XZ plane. */
    public static float segmentParamXZ(Vector3f p, Vector3f a, Vector3f b) {
        float abx = b.x - a.x;
        float abz = b.z - a.z;
        float len = abx * abx + abz * abz;
        return len < 1e-6f ? 0f : clamp01(((p.x - a.x) * abx + (p.z - a.z) * abz) / len);
    }

    /** Full 3D distance from p to segment ab. */
    public static float segmentDistance(Vector3f p, Vector3f a, Vector3f b) {
        Vector3f ab = new Vector3f(b).sub(a);
        float len = ab.lengthSquared();
        float t = len < 1e-6f ? 0f : clamp01(new Vector3f(p).sub(a).dot(ab) / len);
        return new Vector3f(a).fma(t, ab).distance(p);
    }

    public static float semitone(int n) {
        return (float) Math.pow(2.0, (n - 12) / 12.0);
    }

    /**
     * Rotation whose local +Z points along {@code forward} and local +Y along (the part of) {@code up}
     * orthogonal to it. Falls back to a stable up when the two are nearly parallel.
     */
    public static Quaternionf frame(Vector3fc forward, Vector3fc up) {
        Vector3f f = new Vector3f(forward);
        if (f.lengthSquared() < 1e-8f) {
            f.set(0f, 0f, 1f);
        }
        f.normalize();
        Vector3f u = new Vector3f(up);
        u.fma(-u.dot(f), f);
        if (u.lengthSquared() < 1e-6f) {
            u.set(Math.abs(f.y) < 0.9f ? UP : new Vector3f(1f, 0f, 0f));
            u.fma(-u.dot(f), f);
        }
        u.normalize();
        Vector3f r = new Vector3f(u).cross(f).normalize();
        Matrix3f m = new Matrix3f(r, u, f);
        return new Quaternionf().setFromNormalized(m).normalize();
    }

    /** Deterministic 0..99 hash of a lattice point (world generation, dust patterns). */
    public static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0x7fffffff) % 100;
    }

    /** Deterministic 0..1 hash. */
    public static float hash01(int x, int y, int z, int salt) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ salt * 1640531527;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xffffff) / (float) 0x1000000;
    }

    /** Smooth value noise on the XZ lattice, 0..1. */
    public static float noise2(float x, float z, int salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        float fx = smooth(x - x0);
        float fz = smooth(z - z0);
        float a = hash01(x0, 0, z0, salt);
        float b = hash01(x0 + 1, 0, z0, salt);
        float c = hash01(x0, 0, z0 + 1, salt);
        float d = hash01(x0 + 1, 0, z0 + 1, salt);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fz);
    }
}
