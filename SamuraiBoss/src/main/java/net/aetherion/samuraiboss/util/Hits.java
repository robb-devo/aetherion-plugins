package net.aetherion.samuraiboss.util;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Trefferprüfungen gegen die echte Hitbox (BoundingBox) eines Ziels.
 * Alle Formen sind großzügig, aber fair: Was der Telegraph zeigt, trifft.
 */
public final class Hits {

    private Hits() {
    }

    /** Nächster Punkt der Hitbox zu p. */
    private static Vector closest(BoundingBox b, Vector p) {
        return new Vector(MathUtil.clamp(p.getX(), b.getMinX(), b.getMaxX()),
                MathUtil.clamp(p.getY(), b.getMinY(), b.getMaxY()),
                MathUtil.clamp(p.getZ(), b.getMinZ(), b.getMaxZ()));
    }

    /**
     * Linie von a nach b mit halber Breite r (horizontal) und Höhe [a.y + yMin, a.y + yMax].
     */
    public static boolean inLine(LivingEntity e, Location a, Location b, double r, double yMin, double yMax) {
        BoundingBox box = e.getBoundingBox();
        double baseY = Math.min(a.getY(), b.getY());
        if (box.getMaxY() < baseY + yMin || box.getMinY() > Math.max(a.getY(), b.getY()) + yMax) return false;
        double cx = box.getCenterX(), cz = box.getCenterZ();
        double half = Math.max(box.getWidthX(), box.getWidthZ()) / 2.0;
        double d = MathUtil.distPointSegmentXZ(cx, cz, a.getX(), a.getZ(), b.getX(), b.getZ());
        return d <= r + half;
    }

    /** Kreis (horizontal) um c mit Radius r und Höhe [c.y + yMin, c.y + yMax]. */
    public static boolean inCircle(LivingEntity e, Location c, double r, double yMin, double yMax) {
        BoundingBox box = e.getBoundingBox();
        if (box.getMaxY() < c.getY() + yMin || box.getMinY() > c.getY() + yMax) return false;
        Vector p = closest(box, c.toVector());
        double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
        return dx * dx + dz * dz <= r * r;
    }

    /** Ring zwischen r0 und r1 (z. B. Schockwellen). */
    public static boolean inRing(LivingEntity e, Location c, double r0, double r1, double yMin, double yMax) {
        BoundingBox box = e.getBoundingBox();
        if (box.getMaxY() < c.getY() + yMin || box.getMinY() > c.getY() + yMax) return false;
        double dx = box.getCenterX() - c.getX(), dz = box.getCenterZ() - c.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double half = Math.max(box.getWidthX(), box.getWidthZ()) / 2.0;
        return d + half >= r0 && d - half <= r1;
    }

    /** Kegel/Sektor: Ursprung o, Blickrichtung yaw (Grad), halber Öffnungswinkel, Radius. */
    public static boolean inCone(LivingEntity e, Location o, float yaw, float halfAngleDeg, double radius, double yMin, double yMax) {
        BoundingBox box = e.getBoundingBox();
        if (box.getMaxY() < o.getY() + yMin || box.getMinY() > o.getY() + yMax) return false;
        double dx = box.getCenterX() - o.getX(), dz = box.getCenterZ() - o.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double half = Math.max(box.getWidthX(), box.getWidthZ()) / 2.0;
        if (dist - half > radius) return false;
        if (dist < 1.0) return true;
        float ang = MathUtil.yawOf(dx, dz);
        float diff = Math.abs(MathUtil.wrapDeg(ang - yaw));
        // Toleranz durch die Breite der Hitbox
        double tol = Math.toDegrees(Math.atan2(half, dist));
        return diff <= halfAngleDeg + tol;
    }

    /** Kugel um p. */
    public static boolean inSphere(LivingEntity e, Location p, double r) {
        BoundingBox box = e.getBoundingBox();
        Vector c = closest(box, p.toVector());
        return c.distanceSquared(p.toVector()) <= r * r;
    }

    /** Liegt die Oberkante der Hitbox unter der Höhe y? (Für die Ultimate: "geduckt".) */
    public static boolean below(LivingEntity e, double y) {
        return e.getBoundingBox().getMaxY() < y;
    }
}
