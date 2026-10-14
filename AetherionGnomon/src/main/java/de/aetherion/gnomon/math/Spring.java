package de.aetherion.gnomon.math;

import org.joml.Vector3d;

/**
 * Critically damped spring (the classic "smooth damp"), stepped once per tick. Gives every moving part weight: it
 * accelerates, decelerates and never snaps. {@code omega} = 2 / smoothTime in 1/ticks (0.15 = heavy, 0.6 = snappy).
 * {@code maxSpeed} caps travel in blocks/tick.
 */
public final class Spring {

    private final Vector3d pos = new Vector3d();
    private final Vector3d vel = new Vector3d();
    private double omega;
    private double maxSpeed = Double.MAX_VALUE;

    public Spring(double omega) {
        this.omega = omega;
    }

    public Spring omega(double omega) {
        this.omega = Math.max(1e-4, omega);
        return this;
    }

    public double omega() {
        return omega;
    }

    public Spring maxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed <= 0 ? Double.MAX_VALUE : maxSpeed;
        return this;
    }

    public Spring snap(Vector3d to) {
        pos.set(to);
        vel.zero();
        return this;
    }

    public Vector3d pos() {
        return pos;
    }

    public Vector3d vel() {
        return vel;
    }

    /** Adds an impulse (blocks/tick) - recoil, plants, shoves. */
    public void kick(double x, double y, double z) {
        vel.add(x, y, z);
    }

    /** One tick toward {@code target}. */
    public Vector3d step(Vector3d target) {
        double w = omega;
        double exp = 1.0 / (1.0 + w + 0.48 * w * w + 0.235 * w * w * w);
        double cx = pos.x - target.x;
        double cy = pos.y - target.y;
        double cz = pos.z - target.z;
        double maxChange = maxSpeed * (2.0 / w);
        double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len > maxChange && len > 1e-9) {
            double f = maxChange / len;
            cx *= f;
            cy *= f;
            cz *= f;
        }
        double gx = pos.x - cx;
        double gy = pos.y - cy;
        double gz = pos.z - cz;
        double tx = vel.x + w * cx;
        double ty = vel.y + w * cy;
        double tz = vel.z + w * cz;
        vel.set((vel.x - w * tx) * exp, (vel.y - w * ty) * exp, (vel.z - w * tz) * exp);
        double nx = gx + (cx + tx) * exp;
        double ny = gy + (cy + ty) * exp;
        double nz = gz + (cz + tz) * exp;
        if (!Double.isFinite(nx) || !Double.isFinite(ny) || !Double.isFinite(nz)) {
            pos.set(target);
            vel.zero();
            return pos;
        }
        pos.set(nx, ny, nz);
        return pos;
    }

    /** Scalar version for heights, scales, lean. */
    public static final class Scalar {
        private double value;
        private double velocity;
        private double omega;

        public Scalar(double omega, double value) {
            this.omega = Math.max(1e-4, omega);
            this.value = value;
        }

        public double value() {
            return value;
        }

        public Scalar omega(double omega) {
            this.omega = Math.max(1e-4, omega);
            return this;
        }

        public void snap(double v) {
            value = v;
            velocity = 0;
        }

        public void kick(double dv) {
            velocity += dv;
        }

        public double step(double target) {
            double w = omega;
            double exp = 1.0 / (1.0 + w + 0.48 * w * w + 0.235 * w * w * w);
            double change = value - target;
            double temp = velocity + w * change;
            velocity = (velocity - w * temp) * exp;
            double next = target + (change + temp) * exp;
            if (!Double.isFinite(next)) {
                value = target;
                velocity = 0;
                return value;
            }
            value = next;
            return value;
        }

        /** Angle-aware step (radians): short way around, at most {@code maxStep} per tick. */
        public double stepAngle(double target, double maxStep) {
            double goal = value + Geo.wrap(target - value);
            double before = value;
            step(goal);
            double moved = value - before;
            if (Math.abs(moved) > maxStep) {
                value = before + Math.copySign(maxStep, moved);
                velocity = Math.copySign(Math.min(Math.abs(velocity), maxStep), velocity);
            }
            value = Geo.wrap(value);
            return value;
        }
    }
}
