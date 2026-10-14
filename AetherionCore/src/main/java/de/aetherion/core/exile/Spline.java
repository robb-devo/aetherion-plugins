package de.aetherion.core.exile;

import org.bukkit.util.Vector;

/**
 * Time-keyed cubic Hermite path (non-uniform Catmull-Rom tangents, zero velocity at both ends).
 * Position and velocity stay continuous through every key, so a camera or a barge driven by it
 * never stops and starts at a keyframe the way linear segments with per-segment easing do.
 */
public final class Spline {

    private final double[] t;
    private final double[][] p;

    public Spline(double[] times, double[][] points) {
        if (times.length != points.length || times.length < 2) {
            throw new IllegalArgumentException("spline needs >= 2 keys");
        }
        this.t = times.clone();
        this.p = new double[points.length][];
        for (int i = 0; i < points.length; i++) {
            this.p[i] = points[i].clone();
        }
    }

    public double start() {
        return t[0];
    }

    public double end() {
        return t[t.length - 1];
    }

    public Vector at(double time) {
        int n = t.length;
        if (time <= t[0]) {
            return vec(p[0]);
        }
        if (time >= t[n - 1]) {
            return vec(p[n - 1]);
        }
        int i = 0;
        while (i < n - 2 && t[i + 1] <= time) {
            i++;
        }
        double t0 = t[i];
        double t1 = t[i + 1];
        double h = t1 - t0;
        double s = (time - t0) / h;
        double s2 = s * s;
        double s3 = s2 * s;
        double h00 = 2 * s3 - 3 * s2 + 1;
        double h10 = s3 - 2 * s2 + s;
        double h01 = -2 * s3 + 3 * s2;
        double h11 = s3 - s2;
        double[] m0 = tangent(i);
        double[] m1 = tangent(i + 1);
        double[] out = new double[3];
        for (int k = 0; k < 3; k++) {
            out[k] = h00 * p[i][k] + h10 * h * m0[k] + h01 * p[i + 1][k] + h11 * h * m1[k];
        }
        return vec(out);
    }

    private double[] tangent(int j) {
        if (j == 0 || j == t.length - 1) {
            return new double[] {0, 0, 0};
        }
        double dt = t[j + 1] - t[j - 1];
        return new double[] {
                (p[j + 1][0] - p[j - 1][0]) / dt,
                (p[j + 1][1] - p[j - 1][1]) / dt,
                (p[j + 1][2] - p[j - 1][2]) / dt
        };
    }

    private static Vector vec(double[] a) {
        return new Vector(a[0], a[1], a[2]);
    }
}
