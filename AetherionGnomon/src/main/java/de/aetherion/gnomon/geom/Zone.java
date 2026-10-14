package de.aetherion.gnomon.geom;

import de.aetherion.gnomon.math.Geo;

/**
 * A flat danger shape on the arena floor. Every hit test uses exactly the shape the telegraph draws, so what you see is
 * what hits you. Bukkit-free: the brain uses the same shapes to measure dodges.
 */
public interface Zone {

    boolean contains(double x, double z);

    /** A point on the zone the player should read as "the threat" (dodge direction is measured against it). */
    double originX();

    double originZ();

    /** Circle: stomps, plumb drop. */
    record Circle(double cx, double cz, double r) implements Zone {
        @Override
        public boolean contains(double x, double z) {
            return Geo.dist2(x, z, cx, cz) <= r * r;
        }

        @Override
        public double originX() {
            return cx;
        }

        @Override
        public double originZ() {
            return cz;
        }
    }

    /** Ring between two radii: trap shockwave. */
    record Ring(double cx, double cz, double inner, double outer) implements Zone {
        @Override
        public boolean contains(double x, double z) {
            double d2 = Geo.dist2(x, z, cx, cz);
            return d2 >= inner * inner && d2 <= outer * outer;
        }

        @Override
        public double originX() {
            return cx;
        }

        @Override
        public double originZ() {
            return cz;
        }
    }

    /** A segment with a half-width: survey line, interdict walls. */
    record Capsule(double ax, double az, double bx, double bz, double halfWidth) implements Zone {
        @Override
        public boolean contains(double x, double z) {
            return Geo.distToSegment(x, z, ax, az, bx, bz) <= halfWidth;
        }

        @Override
        public double originX() {
            return ax;
        }

        @Override
        public double originZ() {
            return az;
        }
    }

    /**
     * A clock hand: from {@code inner} to {@code outer} along {@code angle} around (cx, cz), {@code halfWidth} blocks
     * to each side.
     */
    record Hand(double cx, double cz, double angle, double inner, double outer, double halfWidth) implements Zone {
        @Override
        public boolean contains(double x, double z) {
            double ux = Math.cos(angle);
            double uz = Math.sin(angle);
            double dx = x - cx;
            double dz = z - cz;
            double along = dx * ux + dz * uz;
            if (along < inner || along > outer) {
                return false;
            }
            double side = Math.abs(dx * (-uz) + dz * ux);
            return side <= halfWidth;
        }

        @Override
        public double originX() {
            return cx;
        }

        @Override
        public double originZ() {
            return cz;
        }
    }

    /** Angular slice of the arena (camping denial). */
    record Sector(double cx, double cz, double fromAngle, double toAngle, double inner, double outer) implements Zone {
        @Override
        public boolean contains(double x, double z) {
            double d2 = Geo.dist2(x, z, cx, cz);
            if (d2 < inner * inner || d2 > outer * outer) {
                return false;
            }
            double a = Geo.angle(x - cx, z - cz);
            double span = Geo.wrap(toAngle - fromAngle);
            if (span < 0) {
                span += Geo.TAU;
            }
            double rel = Geo.wrap(a - fromAngle);
            if (rel < 0) {
                rel += Geo.TAU;
            }
            return rel <= span;
        }

        @Override
        public double originX() {
            double mid = fromAngle + Geo.wrap(toAngle - fromAngle) / 2;
            return cx + Math.cos(mid) * (inner + outer) / 2;
        }

        @Override
        public double originZ() {
            double mid = fromAngle + Geo.wrap(toAngle - fromAngle) / 2;
            return cz + Math.sin(mid) * (inner + outer) / 2;
        }
    }
}
