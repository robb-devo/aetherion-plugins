package de.aetherion.gnomon.math;

/** Easing curves on t in [0,1]. Small, allocation-free. */
public final class Ease {

    private Ease() {
    }

    public static double clamp01(double t) {
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double smooth(double t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    public static double inQuad(double t) {
        t = clamp01(t);
        return t * t;
    }

    public static double outQuad(double t) {
        t = clamp01(t);
        return 1 - (1 - t) * (1 - t);
    }

    public static double inCubic(double t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static double outCubic(double t) {
        t = clamp01(t);
        double u = 1 - t;
        return 1 - u * u * u;
    }

    public static double inOutCubic(double t) {
        t = clamp01(t);
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    /** Overshoots a little past 1 and settles: follow-through. */
    public static double outBack(double t) {
        t = clamp01(t);
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    /** Parabolic arc 0 -> 1 -> 0. */
    public static double arc(double t) {
        t = clamp01(t);
        return 4 * t * (1 - t);
    }
}
