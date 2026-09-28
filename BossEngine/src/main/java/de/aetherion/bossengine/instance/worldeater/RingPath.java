package de.aetherion.bossengine.instance.worldeater;

import org.joml.Vector3f;

/**
 * A rounded square, the shape of the edge of the world: the Ouroboros lies along it.
 *
 * <p>Corners are indexed 0 = (+x, -z), 1 = (-x, -z), 2 = (-x, +z), 3 = (+x, +z). Arc length
 * {@code u} starts at the middle of the head's corner and walks corner 0 -> 1 -> 2 -> 3 order
 * (starting from the head's corner); {@code u = perimeter} arrives back at the head from the other
 * side. {@link #at} returns the point and the direction back toward the head along the body
 * (the way a vertebra there faces). Bukkit-free.
 */
public final class RingPath {

    public float cx;
    public float cz;
    public float side = 16f;
    public float floor;
    public int corner;

    public RingPath set(float cx, float cz, float side, float floor, int corner) {
        this.cx = cx;
        this.cz = cz;
        this.side = side;
        this.floor = floor;
        this.corner = corner & 3;
        return this;
    }

    public float radius() {
        return Math.min(2.4f, side * 0.18f);
    }

    public float perimeter() {
        float r = radius();
        return 4f * (side - 2f * r) + 2f * WeMath.PI * r;
    }

    private float cornerX(int k) {
        float c = side * 0.5f - radius();
        return (k == 0 || k == 3) ? c : -c;
    }

    private float cornerZ(int k) {
        float c = side * 0.5f - radius();
        return (k == 0 || k == 1) ? -c : c;
    }

    /** Arc entry angle of corner k (points are center + r(cos, sin); the walk decreases the angle). */
    private static float thetaIn(int k) {
        return -k * WeMath.HALF_PI;
    }

    private void arc(int k, float phi, Vector3f outPos, Vector3f outToHead) {
        float r = radius();
        float th = thetaIn(k) - phi;
        float c = (float) Math.cos(th);
        float s = (float) Math.sin(th);
        outPos.set(cx + cornerX(k) + r * c, floor, cz + cornerZ(k) + r * s);
        // Walk tangent is (sin, -cos); the body faces back along it.
        outToHead.set(-s, 0f, c);
    }

    private void edge(int k, float t, Vector3f outPos, Vector3f outToHead) {
        int kn = (k + 1) & 3;
        float r = radius();
        float th = thetaIn(k) - WeMath.HALF_PI;
        float c = (float) Math.cos(th);
        float s = (float) Math.sin(th);
        float ax = cx + cornerX(k) + r * c;
        float az = cz + cornerZ(k) + r * s;
        float bx = cx + cornerX(kn) + r * c;
        float bz = cz + cornerZ(kn) + r * s;
        outPos.set(ax + (bx - ax) * t, floor, az + (bz - az) * t);
        outToHead.set(ax - bx, 0f, az - bz);
        if (outToHead.lengthSquared() < 1e-8f) {
            outToHead.set(-s, 0f, c);
        } else {
            outToHead.normalize();
        }
    }

    /** Point at arc length {@code u} (wrapped) and the direction back toward the head. */
    public void at(float u, Vector3f outPos, Vector3f outToHead) {
        float r = radius();
        float edgeLen = Math.max(0f, side - 2f * r);
        float arcLen = WeMath.HALF_PI * r;
        float per = 4f * edgeLen + 4f * arcLen;
        if (per <= 1e-4f) {
            outPos.set(cx, floor, cz);
            outToHead.set(0f, 0f, 1f);
            return;
        }
        u = ((u % per) + per) % per;
        int k0 = corner & 3;
        float halfArc = arcLen * 0.5f;
        if (u < halfArc) {
            arc(k0, WeMath.HALF_PI * 0.5f + (r > 1e-4f ? u / r : 0f), outPos, outToHead);
            return;
        }
        float acc = halfArc;
        for (int i = 0; i < 4; i++) {
            int k = (k0 + i) & 3;
            int kn = (k + 1) & 3;
            if (u < acc + edgeLen) {
                edge(k, edgeLen <= 1e-4f ? 0f : (u - acc) / edgeLen, outPos, outToHead);
                return;
            }
            acc += edgeLen;
            float thisArc = i < 3 ? arcLen : halfArc;
            if (u < acc + thisArc) {
                arc(kn, r > 1e-4f ? (u - acc) / r : 0f, outPos, outToHead);
                return;
            }
            acc += thisArc;
        }
        arc(k0, WeMath.HALF_PI * 0.5f, outPos, outToHead);
    }
}
