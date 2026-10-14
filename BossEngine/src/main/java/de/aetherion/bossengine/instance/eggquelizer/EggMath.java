package de.aetherion.bossengine.instance.eggquelizer;

import org.joml.Vector3f;

/** Easing, angles and spring helpers. Every Eggquelizer timeline is authored in 0..1 time. */
final class EggMath {

    static final float PI = (float) Math.PI;
    static final float TAU = PI * 2f;
    static final float HALF_PI = PI * 0.5f;

    private EggMath() {
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

    /** 0..1 progress of {@code tick} inside [from, to). */
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

    static float outBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Parabolic hop: 0 at both ends, 1 in the middle. */
    static float arc(float t) {
        t = clamp01(t);
        return 4f * t * (1f - t);
    }

    static float wrap(float a) {
        while (a > PI) {
            a -= TAU;
        }
        while (a < -PI) {
            a += TAU;
        }
        return a;
    }

    static float approachAngle(float from, float to, float maxStep) {
        float d = wrap(to - from);
        if (Math.abs(d) <= maxStep) {
            return to;
        }
        return from + Math.signum(d) * maxStep;
    }

    /** Yaw that turns local +Z toward (dx, dz). */
    static float yawToward(float dx, float dz) {
        return (float) Math.atan2(dx, dz);
    }

    static float horizontal(Vector3f a, Vector3f b) {
        float dx = a.x - b.x;
        float dz = a.z - b.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    static float radius(Vector3f v) {
        return (float) Math.sqrt(v.x * v.x + v.z * v.z);
    }

    static Vector3f flat(float yaw, float r) {
        return new Vector3f((float) Math.sin(yaw) * r, 0f, (float) Math.cos(yaw) * r);
    }

    static Vector3f lerpVec(Vector3f a, Vector3f b, float t) {
        return new Vector3f(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t));
    }

    static float semitone(int n) {
        return clamp((float) Math.pow(2.0, n / 12.0) * 0.5f, 0.5f, 2f);
    }

    static float rnd() {
        return (float) Math.random();
    }

    static float rnd(float lo, float hi) {
        return lo + (hi - lo) * (float) Math.random();
    }

    /** A critically-ish damped spring on one float. */
    static final class Spring {
        float value;
        float vel;
        float target;
        float k;
        float d;

        Spring(float rest, float k, float d) {
            this.value = rest;
            this.target = rest;
            this.k = k;
            this.d = d;
        }

        void tick() {
            vel += (target - value) * k;
            vel *= 1f - d;
            value += vel;
        }

        void kick(float v) {
            vel += v;
        }

        void snap(float v) {
            value = v;
            target = v;
            vel = 0f;
        }
    }
}
