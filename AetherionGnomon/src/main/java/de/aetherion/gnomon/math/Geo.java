package de.aetherion.gnomon.math;

/**
 * Flat (x/z) geometry in Minecraft's frame: +x east, +z south, yaw 0 = facing +z, yaw grows clockwise seen from above.
 * Angles here are math angles on the x/z plane: {@code atan2(z, x)} (0 = east, +pi/2 = south).
 */
public final class Geo {

    public static final double TAU = Math.PI * 2;

    private Geo() {
    }

    /** Wrap to (-pi, pi]. */
    public static double wrap(double a) {
        a %= TAU;
        if (a <= -Math.PI) {
            a += TAU;
        } else if (a > Math.PI) {
            a -= TAU;
        }
        return a;
    }

    public static double angle(double dx, double dz) {
        return Math.atan2(dz, dx);
    }

    public static double dist(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double dist2(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }

    /**
     * Signed lateral component of displacement {@code (dx,dz)} for someone facing {@code (fx,fz)}:
     * positive = moved to their right, negative = to their left.
     */
    public static double lateral(double fx, double fz, double dx, double dz) {
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 1e-9) {
            return 0;
        }
        fx /= len;
        fz /= len;
        // Right-hand vector of facing f in MC's frame: (-fz, fx).
        return dx * (-fz) + dz * fx;
    }

    /** Distance from point P to segment AB on the plane. */
    public static double distToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double abx = bx - ax;
        double abz = bz - az;
        double len2 = abx * abx + abz * abz;
        double t = len2 < 1e-9 ? 0 : ((px - ax) * abx + (pz - az) * abz) / len2;
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        double cx = ax + abx * t;
        double cz = az + abz * t;
        return dist(px, pz, cx, cz);
    }

    /** Projection parameter of P on AB (unclamped, 0 = A, 1 = B). */
    public static double along(double px, double pz, double ax, double az, double bx, double bz) {
        double abx = bx - ax;
        double abz = bz - az;
        double len2 = abx * abx + abz * abz;
        return len2 < 1e-9 ? 0 : ((px - ax) * abx + (pz - az) * abz) / len2;
    }

    /** Smallest absolute difference between two angles. */
    public static double angleDiff(double a, double b) {
        return Math.abs(wrap(a - b));
    }

    /** Minecraft yaw (degrees) of a math angle on the plane. */
    public static float yawOf(double mathAngle) {
        // direction (cos a, sin a) -> MC yaw = atan2(-dx, dz)
        return (float) Math.toDegrees(Math.atan2(-Math.cos(mathAngle), Math.sin(mathAngle)));
    }
}
