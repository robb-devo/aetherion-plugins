package de.aetherion.bossengine.instance.saint;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.joml.Vector3f;

/**
 * One golden marionette thread: a short chain of thin glowing displays bent into a catenary.
 *
 * <p>Sag is the tell. A slack thread droops; the instant before she is yanked it snaps straight.
 * Wobble makes a plucked thread hum. A thread can be severed: the upper half recoils into
 * the sky, the lower half falls away.
 */
final class ThreadLine {

    static final Color GOLD = Color.fromRGB(255, 214, 110);
    static final Color WHITE = Color.fromRGB(255, 255, 255);
    static final Color CRIMSON = Color.fromRGB(235, 40, 60);

    private final BlockDisplay[] segs;
    private final float width;
    final Vector3f a = new Vector3f();
    final Vector3f b = new Vector3f();
    /** Current and target sag in blocks at the midpoint. */
    float sag;
    float sagTarget;
    float wobble;
    private float wobblePhase;
    boolean visible = true;
    /** 0..1: fraction of the line drawn from a toward b (for threads shooting out). */
    float reach = 1f;

    ThreadLine(SaintFx fx, int segments, float width, Material material, Color glow) {
        this.width = width;
        this.segs = new BlockDisplay[segments];
        for (int i = 0; i < segments; i++) {
            segs[i] = fx.block(material, glow, 15);
        }
    }

    void setGlow(Color color) {
        for (BlockDisplay d : segs) {
            SaintFx.glow(d, color);
        }
    }

    void setMaterial(Material m) {
        for (BlockDisplay d : segs) {
            if (d != null && d.isValid()) {
                d.setBlock(m.createBlockData());
            }
        }
    }

    boolean intact() {
        for (BlockDisplay d : segs) {
            if (d == null || !d.isValid()) {
                return false;
            }
        }
        return true;
    }

    Vector3f pointAt(float t) {
        Vector3f p = SaintMath.lerp(a, b, t, new Vector3f());
        p.y -= sag * 4f * t * (1f - t);
        if (wobble > 0.001f) {
            Vector3f dir = new Vector3f(b).sub(a);
            Vector3f side = new Vector3f(-dir.z, 0, dir.x);
            if (side.lengthSquared() < 1e-4f) {
                side.set(1, 0, 0);
            }
            side.normalize();
            float w = (float) Math.sin(wobblePhase + t * 9f) * wobble * (float) Math.sin(Math.PI * t);
            p.fma(w, side);
        }
        return p;
    }

    void update(Vector3f from, Vector3f to, int interp) {
        a.set(from);
        b.set(to);
        sag += (sagTarget - sag) * 0.35f;
        wobblePhase += 1.7f;
        wobble *= 0.9f;
        render(interp);
    }

    void render(int interp) {
        int n = segs.length;
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n;
            float t1 = (i + 1) / (float) n;
            if (!visible || t0 >= reach) {
                SaintFx.push(segs[i], SaintFx.gone(pointAt(Math.min(t0, 1f))), interp);
                continue;
            }
            Vector3f p0 = pointAt(t0);
            Vector3f p1 = pointAt(Math.min(t1, reach));
            SaintFx.push(segs[i], SaintFx.beam(p0, p1, width), interp);
        }
    }

    /** Snap taut instantly (the tell). */
    void taut() {
        sagTarget = 0f;
        sag = 0f;
    }

    void slack(float amount) {
        sagTarget = amount;
    }

    void pluck(float amount) {
        wobble = Math.max(wobble, amount);
    }

    /**
     * Sever at fraction {@code t}: pieces above the cut fly up {@code recoil} blocks, pieces
     * below drop to {@code floorY} and fold. Uses long client interpolation, then hides.
     */
    void sever(float t, float recoil, float floorY, int ticks) {
        int n = segs.length;
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n;
            float t1 = (i + 1) / (float) n;
            Vector3f p0 = pointAt(t0);
            Vector3f p1 = pointAt(t1);
            if (t1 <= t) {
                p0.y += recoil;
                p1.y += recoil;
                SaintFx.push(segs[i], SaintFx.beam(p0, p1, width * 0.3f), ticks);
            } else {
                float drop = Math.max(0f, Math.min(p0.y, p1.y) - floorY - 0.05f);
                Vector3f q0 = new Vector3f(p0.x, floorY + 0.05f, p0.z);
                Vector3f q1 = new Vector3f(p1.x + (p1.y - p0.y) * 0.3f, floorY + 0.05f, p1.z);
                if (drop < 0.2f) {
                    q0.set(p0);
                    q1.set(p1);
                }
                SaintFx.push(segs[i], SaintFx.beam(q0, q1, width), ticks);
            }
        }
        visible = false;
    }

    void remove() {
        for (BlockDisplay d : segs) {
            SaintFx.kill(d);
        }
    }
}
