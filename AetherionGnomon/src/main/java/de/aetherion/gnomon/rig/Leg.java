package de.aetherion.gnomon.rig;

import de.aetherion.gnomon.math.Ease;
import de.aetherion.gnomon.math.Spring;

import org.joml.Vector3d;

import java.util.function.Consumer;

/**
 * One needle leg. Either the gait owns it (planted / stepping) or an attack does (held in the air / slamming).
 */
public final class Leg {

    public enum Mode {
        PLANTED, STEPPING, HELD, SLAM, LIMP
    }

    final int index;
    final double offset;
    final Vector3d foot = new Vector3d();
    final Vector3d knee = new Vector3d();
    final Vector3d hip = new Vector3d();
    Mode mode = Mode.PLANTED;

    // stepping / slamming
    final Vector3d from = new Vector3d();
    final Vector3d to = new Vector3d();
    int t;
    int duration;
    double lift;
    Consumer<Vector3d> onLand;

    // held
    final Spring hold = new Spring(0.28);
    final Vector3d holdTarget = new Vector3d();

    Leg(int index, double offset) {
        this.index = index;
        this.offset = offset;
    }

    public int index() {
        return index;
    }

    public Mode mode() {
        return mode;
    }

    public Vector3d foot() {
        return foot;
    }

    public boolean planted() {
        return mode == Mode.PLANTED;
    }

    void step(Vector3d target, int ticks, double lift, Consumer<Vector3d> onLand) {
        from.set(foot);
        to.set(target);
        t = 0;
        duration = Math.max(2, ticks);
        this.lift = lift;
        this.onLand = onLand;
        mode = Mode.STEPPING;
    }

    void hold(Vector3d target) {
        if (mode != Mode.HELD) {
            hold.snap(foot);
        }
        holdTarget.set(target);
        mode = Mode.HELD;
    }

    void slam(Vector3d target, int ticks, Consumer<Vector3d> onLand) {
        from.set(foot);
        to.set(target);
        t = 0;
        duration = Math.max(1, ticks);
        lift = 0;
        this.onLand = onLand;
        mode = Mode.SLAM;
    }

    /** Advances the foot. Returns the landing point if it planted this tick. */
    Vector3d advance() {
        switch (mode) {
            case STEPPING -> {
                t++;
                double k = Ease.inOutCubic(t / (double) duration);
                foot.set(from).lerp(to, k);
                foot.y += lift * Ease.arc(t / (double) duration);
                if (t >= duration) {
                    foot.set(to);
                    mode = Mode.PLANTED;
                    return land();
                }
            }
            case SLAM -> {
                t++;
                double k = Ease.inCubic(t / (double) duration);
                foot.set(from).lerp(to, k);
                if (t >= duration) {
                    foot.set(to);
                    mode = Mode.PLANTED;
                    return land();
                }
            }
            case HELD -> foot.set(hold.step(holdTarget));
            default -> {
            }
        }
        return null;
    }

    private Vector3d land() {
        Consumer<Vector3d> cb = onLand;
        onLand = null;
        if (cb != null) {
            cb.accept(new Vector3d(foot));
        }
        return new Vector3d(foot);
    }

    /**
     * Two-bone IK: knee position for hip -> foot with bone lengths a, b, bending toward {@code pole}.
     */
    static void solve(Vector3d hip, Vector3d foot, double a, double b, Vector3d pole, Vector3d outKnee) {
        Vector3d d = new Vector3d(foot).sub(hip);
        double dist = d.length();
        if (dist < 1e-6) {
            outKnee.set(hip).add(pole.x * a, pole.y * a, pole.z * a);
            return;
        }
        Vector3d u = new Vector3d(d).div(dist);
        double reach = Math.max(Math.abs(a - b) + 1e-3, Math.min(a + b - 1e-3, dist));
        double cosA = (a * a + reach * reach - b * b) / (2 * a * reach);
        cosA = Math.max(-1, Math.min(1, cosA));
        double sinA = Math.sqrt(Math.max(0, 1 - cosA * cosA));
        // Bend direction: pole minus its component along u.
        Vector3d w = new Vector3d(pole).sub(new Vector3d(u).mul(pole.dot(u)));
        if (w.lengthSquared() < 1e-8) {
            w.set(0, 1, 0).sub(new Vector3d(u).mul(u.y));
            if (w.lengthSquared() < 1e-8) {
                w.set(1, 0, 0);
            }
        }
        w.normalize();
        outKnee.set(hip)
                .add(u.x * a * cosA, u.y * a * cosA, u.z * a * cosA)
                .add(w.x * a * sinA, w.y * a * sinA, w.z * a * sinA);
    }
}
