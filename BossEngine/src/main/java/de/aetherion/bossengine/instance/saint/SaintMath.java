package de.aetherion.bossengine.instance.saint;

import org.joml.Vector3f;

/**
 * Easing and small vector helpers. Every timeline in the encounter is authored in
 * normalized 0..1 time and shaped through these curves.
 */
final class SaintMath {

    static final float PI = (float) Math.PI;
    static final float TAU = PI * 2f;
    static final float HALF_PI = PI * 0.5f;

    private SaintMath() {
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** 0..1 progress of {@code tick} inside the window [from, to). */
    static float window(int tick, int from, int to) {
        if (to <= from) {
            return tick >= to ? 1f : 0f;
        }
        return clamp01((tick - from) / (float) (to - from));
    }

    static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    static float inCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    static float outCubic(float t) {
        t = clamp01(t) - 1f;
        return t * t * t + 1f;
    }

    static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    static float outBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    static float outElastic(float t) {
        t = clamp01(t);
        if (t == 0f || t == 1f) {
            return t;
        }
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (TAU / 3f)) + 1);
    }

    /** Parabolic hop: 0 at both ends, 1 at the middle. */
    static float arc(float t) {
        t = clamp01(t);
        return 4f * t * (1f - t);
    }

    static float wrap(float angle) {
        while (angle > PI) {
            angle -= TAU;
        }
        while (angle < -PI) {
            angle += TAU;
        }
        return angle;
    }

    static float approachAngle(float from, float to, float maxStep) {
        float delta = wrap(to - from);
        if (Math.abs(delta) <= maxStep) {
            return to;
        }
        return from + Math.signum(delta) * maxStep;
    }

    /** Yaw that makes local +Z face along (dx, dz). */
    static float yawToward(float dx, float dz) {
        return (float) Math.atan2(dx, dz);
    }

    static float horizontal(Vector3f a, Vector3f b) {
        float dx = a.x - b.x;
        float dz = a.z - b.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    static Vector3f lerp(Vector3f a, Vector3f b, float t, Vector3f out) {
        out.set(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t));
        return out;
    }

    /** Distance from p to segment ab, in the XZ plane only. */
    static float segmentDistanceXZ(Vector3f p, Vector3f a, Vector3f b) {
        float abx = b.x - a.x;
        float abz = b.z - a.z;
        float len = abx * abx + abz * abz;
        float t = len < 1e-6f ? 0f : clamp01(((p.x - a.x) * abx + (p.z - a.z) * abz) / len);
        float cx = a.x + abx * t - p.x;
        float cz = a.z + abz * t - p.z;
        return (float) Math.sqrt(cx * cx + cz * cz);
    }

    /** Full 3D distance from p to segment ab. */
    static float segmentDistance(Vector3f p, Vector3f a, Vector3f b) {
        Vector3f ab = new Vector3f(b).sub(a);
        float len = ab.lengthSquared();
        float t = len < 1e-6f ? 0f : clamp01(new Vector3f(p).sub(a).dot(ab) / len);
        return new Vector3f(a).fma(t, ab).distance(p);
    }

    static float semitone(int n) {
        return (float) Math.pow(2.0, (n - 12) / 12.0);
    }
}
