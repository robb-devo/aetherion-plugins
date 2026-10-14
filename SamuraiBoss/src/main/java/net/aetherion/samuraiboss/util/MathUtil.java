package net.aetherion.samuraiboss.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Kleine Mathe-Helfer. Konventionen im ganzen Plugin:
 * <ul>
 *   <li>Modelle schauen in Ruhe nach +Z (Süden).</li>
 *   <li>Euler-Winkel sind in GRAD und werden als q = Rz * Ry * Rx angewendet
 *       (erst X = Nicken/Schwingen, dann Y = Drehen, dann Z = Seitlich kippen).</li>
 *   <li>Minecraft-Yaw: 0 = Süden (+Z), 90 = Westen (-X), 180 = Norden, -90 = Osten (+X).</li>
 * </ul>
 */
public final class MathUtil {

    public static final float DEG = (float) (Math.PI / 180.0);
    public static final float TAU = (float) (Math.PI * 2.0);

    private MathUtil() {
    }

    /** Euler (Grad) -> Quaternion, Reihenfolge Rz * Ry * Rx. */
    public static Quaternionf euler(float xDeg, float yDeg, float zDeg) {
        return new Quaternionf().rotateZ(zDeg * DEG).rotateY(yDeg * DEG).rotateX(xDeg * DEG);
    }

    public static Quaternionf euler(Quaternionf dest, float xDeg, float yDeg, float zDeg) {
        return dest.identity().rotateZ(zDeg * DEG).rotateY(yDeg * DEG).rotateX(xDeg * DEG);
    }

    /** Rotation, die das Modell (Ruhe: +Z) in Richtung des Minecraft-Yaws dreht. */
    public static Quaternionf yawRotation(float mcYaw) {
        return new Quaternionf().rotationY(-mcYaw * DEG);
    }

    /** Minecraft-Yaw, der von (dx,dz) aus gesehen in diese Richtung schaut. */
    public static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static float yawTo(Location from, Location to) {
        return yawOf(to.getX() - from.getX(), to.getZ() - from.getZ());
    }

    /** Einheitsvektor (horizontal) für einen Minecraft-Yaw. */
    public static Vector dirOfYaw(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    /** Normalisiert einen Winkel auf (-180, 180]. */
    public static float wrapDeg(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        if (a <= -180f) a += 360f;
        return a;
    }

    /** Dreht current maximal um maxStep Grad in Richtung target. */
    public static float approachAngle(float current, float target, float maxStep) {
        float d = wrapDeg(target - current);
        if (Math.abs(d) <= maxStep) return target;
        return current + Math.signum(d) * maxStep;
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** 0..1 Position von v zwischen a und b (geklemmt). */
    public static float inverseLerp(float a, float b, float v) {
        if (a == b) return 0f;
        return clamp((v - a) / (b - a), 0f, 1f);
    }

    public static Vector3f vec(Vector v) {
        return new Vector3f((float) v.getX(), (float) v.getY(), (float) v.getZ());
    }

    public static Vector vec(Vector3f v) {
        return new Vector(v.x, v.y, v.z);
    }

    /** Vektor von a nach b als JOML-Vektor. */
    public static Vector3f delta(Location a, Location b) {
        return new Vector3f((float) (b.getX() - a.getX()), (float) (b.getY() - a.getY()), (float) (b.getZ() - a.getZ()));
    }

    /**
     * Quaternion, das die lokale +X-Achse auf die Richtung dir dreht (und dabei "oben" möglichst
     * nach +Y zeigen lässt). Praktisch für Balken/Linien, die als gestreckter Block entlang X liegen.
     */
    public static Quaternionf lookAlongX(Vector3f dir) {
        Vector3f d = new Vector3f(dir);
        if (d.lengthSquared() < 1e-8f) return new Quaternionf();
        d.normalize();
        // Yaw um Y, dann Pitch um Z
        float yaw = (float) Math.atan2(-d.z, d.x);
        float pitch = (float) Math.asin(clamp(d.y, -1f, 1f));
        return new Quaternionf().rotateY(yaw).rotateZ(pitch);
    }

    /** Quaternion, das +Z auf dir dreht (Roll = 0). */
    public static Quaternionf lookAlongZ(Vector3f dir) {
        Vector3f d = new Vector3f(dir);
        if (d.lengthSquared() < 1e-8f) return new Quaternionf();
        d.normalize();
        float yaw = (float) Math.atan2(d.x, d.z);
        float pitch = (float) -Math.asin(clamp(d.y, -1f, 1f));
        return new Quaternionf().rotateY(yaw).rotateX(pitch);
    }

    /** Abstand eines Punktes zu einer Strecke (alles in XYZ). */
    public static double distPointSegment(Vector p, Vector a, Vector b) {
        Vector ab = b.clone().subtract(a);
        double len2 = ab.lengthSquared();
        if (len2 < 1e-9) return p.distance(a);
        double t = clamp(p.clone().subtract(a).dot(ab) / len2, 0.0, 1.0);
        Vector proj = a.clone().add(ab.multiply(t));
        return p.distance(proj);
    }

    /** Abstand eines Punktes zu einer Strecke, nur horizontal (XZ). */
    public static double distPointSegmentXZ(double px, double pz, double ax, double az, double bx, double bz) {
        double abx = bx - ax, abz = bz - az;
        double len2 = abx * abx + abz * abz;
        double t = len2 < 1e-9 ? 0 : clamp(((px - ax) * abx + (pz - az) * abz) / len2, 0.0, 1.0);
        double cx = ax + abx * t, cz = az + abz * t;
        double dx = px - cx, dz = pz - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double distXZ(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double distXZ(double ax, double az, double bx, double bz) {
        double dx = ax - bx, dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
