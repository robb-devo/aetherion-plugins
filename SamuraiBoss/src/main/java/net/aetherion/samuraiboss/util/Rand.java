package net.aetherion.samuraiboss.util;

import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Zufalls-Helfer. */
public final class Rand {

    private Rand() {
    }

    public static ThreadLocalRandom r() {
        return ThreadLocalRandom.current();
    }

    public static float f() {
        return r().nextFloat();
    }

    public static float range(float lo, float hi) {
        return lo + (hi - lo) * r().nextFloat();
    }

    public static double range(double lo, double hi) {
        return lo + (hi - lo) * r().nextDouble();
    }

    public static int range(int lo, int hiInclusive) {
        return lo + r().nextInt(hiInclusive - lo + 1);
    }

    /** -1..1 */
    public static float signed() {
        return r().nextFloat() * 2f - 1f;
    }

    public static boolean chance(double p) {
        return r().nextDouble() < p;
    }

    public static <T> T pick(List<T> list) {
        return list.get(r().nextInt(list.size()));
    }

    @SafeVarargs
    public static <T> T pick(T... arr) {
        return arr[r().nextInt(arr.length)];
    }

    /** Zufälliger Einheitsvektor. */
    public static Vector3f unit() {
        double u = r().nextDouble() * 2 - 1;
        double phi = r().nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        return new Vector3f((float) (s * Math.cos(phi)), (float) u, (float) (s * Math.sin(phi)));
    }

    /** Gaussian mit Mittelwert 0. */
    public static float gauss(float sigma) {
        return (float) (r().nextGaussian() * sigma);
    }

    /** Glatter, deterministischer Pseudo-Noise (1D) für Wind & Schwanken. */
    public static float noise(float x, int seed) {
        int i = (int) Math.floor(x);
        float f = x - i;
        float a = hash(i, seed), b = hash(i + 1, seed);
        float t = f * f * (3 - 2 * f);
        return a + (b - a) * t;
    }

    /** -1..1 */
    private static float hash(int i, int seed) {
        int h = i * 374761393 + seed * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return ((h & 0xFFFF) / 32767.5f) - 1f;
    }
}
