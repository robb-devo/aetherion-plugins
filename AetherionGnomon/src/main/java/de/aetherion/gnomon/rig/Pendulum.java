package de.aetherion.gnomon.rig;

import org.joml.Vector3d;

/**
 * The plumb bob: a heavy weight on a chain. Verlet integration with a rope constraint (a chain only pulls, it never
 * pushes), so when the chain is let out the bob genuinely free-falls, and when the hub moves the bob lags and swings.
 */
public final class Pendulum {

    private final Vector3d pos = new Vector3d();
    private final Vector3d prev = new Vector3d();
    private double length;
    private double lengthGoal;
    private double reelSpeed = 0.35;
    private double gravity;
    private double damping;
    private double floorY;
    private double bottomClearance = 0.75;
    private boolean pinned;
    private boolean landed;
    private Runnable onLand;

    public Pendulum(double gravity, double damping) {
        this.gravity = gravity;
        this.damping = damping;
    }

    public void place(Vector3d anchor, double length, double floorY) {
        this.length = length;
        this.lengthGoal = length;
        this.floorY = floorY;
        pos.set(anchor.x, anchor.y - length, anchor.z);
        prev.set(pos);
    }

    public Vector3d pos() {
        return pos;
    }

    public Vector3d velocity() {
        return new Vector3d(pos).sub(prev);
    }

    public double length() {
        return length;
    }

    public boolean landed() {
        return landed;
    }

    public void floor(double floorY) {
        this.floorY = floorY;
    }

    /** Lets the chain out / reels it in toward {@code goal} at {@code speed} blocks per tick. */
    public void reel(double goal, double speed) {
        this.lengthGoal = Math.max(0.5, goal);
        this.reelSpeed = Math.max(0.01, speed);
    }

    /** Instantly lets the chain go slack to {@code goal}: the bob falls under gravity (plus {@code push} down). */
    public void drop(double goal, double push, Runnable onLand) {
        this.length = Math.max(0.5, goal);
        this.lengthGoal = this.length;
        this.onLand = onLand;
        this.landed = false;
        // velocity = pos - prev, so a downward push (negative) raises prev.
        prev.y -= push;
    }

    /** Holds the bob where it is (embedded in the floor). */
    public void pin(boolean pinned) {
        this.pinned = pinned;
        if (pinned) {
            prev.set(pos);
        }
    }

    public boolean pinned() {
        return pinned;
    }

    /** Impulse in blocks per tick. */
    public void kick(double x, double y, double z) {
        prev.sub(x, y, z);
    }

    public void step(Vector3d anchor) {
        if (Math.abs(length - lengthGoal) > 1e-3) {
            double d = lengthGoal - length;
            length += Math.copySign(Math.min(Math.abs(d), reelSpeed), d);
        }
        if (pinned) {
            prev.set(pos);
            return;
        }
        double vx = (pos.x - prev.x) * damping;
        double vy = (pos.y - prev.y) * damping;
        double vz = (pos.z - prev.z) * damping;
        prev.set(pos);
        pos.add(vx, vy - gravity, vz);

        // Rope: only pulls.
        double dx = pos.x - anchor.x;
        double dy = pos.y - anchor.y;
        double dz = pos.z - anchor.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > length && len > 1e-6) {
            double f = length / len;
            pos.set(anchor.x + dx * f, anchor.y + dy * f, anchor.z + dz * f);
        }

        // Floor.
        double bottom = floorY + bottomClearance;
        if (pos.y < bottom) {
            boolean falling = prev.y - pos.y > 0.05;
            pos.y = bottom;
            prev.y = bottom;
            // friction on the floor
            prev.x = pos.x - (pos.x - prev.x) * 0.5;
            prev.z = pos.z - (pos.z - prev.z) * 0.5;
            if (!landed && falling) {
                landed = true;
                Runnable cb = onLand;
                onLand = null;
                if (cb != null) {
                    cb.run();
                }
            }
        } else if (pos.y > bottom + 0.3) {
            landed = false;
        }
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) {
            pos.set(anchor.x, anchor.y - length, anchor.z);
            prev.set(pos);
        }
    }
}
