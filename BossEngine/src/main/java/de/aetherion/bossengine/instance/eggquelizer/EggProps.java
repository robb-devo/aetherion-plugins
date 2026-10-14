package de.aetherion.bossengine.instance.eggquelizer;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Short-lived physical things: pressure waves, lane walls, floor telegraphs, launch columns,
 * treble balls, the signal packet, the popped-off lid. Each animates with one long client-side
 * interpolation where possible (spawn, then a single push), never a per-tick spawn storm.
 *
 * <p>Props may spawn children from {@link Prop#tick()} (ball land, packet hop). Tick uses a
 * snapshot so adds never ConcurrentModification-stall cleanup — that used to leave floor rings
 * and ring segments alive forever and pile into tens of thousands of displays.
 */
final class EggProps {

    /** Hard ceiling on concurrent short-lived props (Lid exempt from cull). */
    private static final int MAX_LIVE = 48;
    /** Absolute max ticks any ephemeral prop may linger. */
    private static final int MAX_LIFE = 48;
    /** Cap ring segment spam (full 360° used to spawn ~28 each). */
    private static final int MAX_RING_SEGS = 16;

    interface Prop {
        /** @return false when finished (then removed) */
        boolean tick();

        void remove();

        /** Persistent props (lid on the floor) skip the live-count cull. */
        default boolean persistent() {
            return false;
        }
    }

    private final List<Prop> live = new ArrayList<>();

    <T extends Prop> T add(T p) {
        live.add(p);
        cullIfNeeded();
        return p;
    }

    void tick() {
        // Snapshot: onLand / onArrive may add new props mid-tick.
        List<Prop> snapshot = new ArrayList<>(live);
        List<Prop> dead = new ArrayList<>(8);
        for (Prop p : snapshot) {
            boolean alive;
            try {
                alive = p.tick();
            } catch (RuntimeException ex) {
                alive = false;
            }
            if (!alive) {
                dead.add(p);
            }
        }
        for (Prop p : dead) {
            try {
                p.remove();
            } catch (RuntimeException ignored) {
            }
            live.remove(p);
        }
        cullIfNeeded();
    }

    private void cullIfNeeded() {
        int ephemeral = 0;
        for (Prop p : live) {
            if (!p.persistent()) {
                ephemeral++;
            }
        }
        if (ephemeral <= MAX_LIVE) {
            return;
        }
        int drop = ephemeral - MAX_LIVE;
        for (int i = 0; i < live.size() && drop > 0; ) {
            Prop p = live.get(i);
            if (p.persistent()) {
                i++;
                continue;
            }
            try {
                p.remove();
            } catch (RuntimeException ignored) {
            }
            live.remove(i);
            drop--;
        }
    }

    List<Prop> live() {
        return live;
    }

    void clear() {
        for (Prop p : live) {
            try {
                p.remove();
            } catch (RuntimeException ignored) {
            }
        }
        live.clear();
    }

    /* ================================================================== ring wave */

    /**
     * A low pressure wave rolling outward along the floor. Jump it (it is short) or stand outside
     * its arc. The server knows exactly where the front is; the client sees one smooth push.
     */
    static final class RingWave implements Prop {
        final Vector3f center;
        final float aim;
        final float halfArc;
        final float r0;
        final float r1;
        final float height;
        final int life;
        final Set<UUID> hit = new HashSet<>();
        double power;
        float knock = 1.3f;
        float lift = 0.45f;
        private final List<BlockDisplay> segs = new ArrayList<>();
        private int age;

        boolean done() {
            return age >= life;
        }

        RingWave(EggFx fx, Vector3f center, float aim, float halfArc, float r0, float r1, float height, int life,
                 Material material, Color glow) {
            this.center = new Vector3f(center.x, 0.02f, center.z);
            this.aim = aim;
            this.halfArc = halfArc;
            this.r0 = r0;
            this.r1 = r1;
            this.height = height;
            this.life = Math.min(MAX_LIFE, Math.max(4, life));
            int n = Math.min(MAX_RING_SEGS, Math.max(6, Math.round(28 * halfArc / EggMath.PI)));
            for (int i = 0; i < n; i++) {
                BlockDisplay d = fx.prop(material, glow, 15);
                EggFx.push(d, seg(i, n, r0, height), 0);
                segs.add(d);
            }
        }

        private Matrix4f seg(int i, int n, float r, float h) {
            float a = aim - halfArc + (i + 0.5f) * (2f * halfArc / n);
            float chord = 2f * r * (float) Math.sin(halfArc / n) + 0.12f;
            Vector3f c = new Vector3f(center.x + (float) Math.sin(a) * r, center.y + h * 0.5f, center.z + (float) Math.cos(a) * r);
            return EggFx.box(c, Math.max(0.01f, chord), Math.max(0.01f, h), 0.22f, a + EggMath.HALF_PI);
        }

        float radiusNow() {
            float t = EggMath.clamp01((age - 2) / (float) (life - 2));
            return EggMath.lerp(r0, r1, t);
        }

        boolean active() {
            return age >= 2 && age < life - 1;
        }

        /** Is a player whose feet are at {@code feet} (stage space) caught by the wave front now? */
        boolean catches(Vector3f feet, float band) {
            if (!active() || feet.y > height * 0.85f + 0.05f) {
                return false;
            }
            float dx = feet.x - center.x;
            float dz = feet.z - center.z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - radiusNow()) > band) {
                return false;
            }
            if (halfArc >= EggMath.PI - 0.01f) {
                return true;
            }
            float a = (float) Math.atan2(dx, dz);
            return Math.abs(EggMath.wrap(a - aim)) <= halfArc + 0.08f;
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                for (int i = 0; i < segs.size(); i++) {
                    EggFx.push(segs.get(i), seg(i, segs.size(), r1, height * 0.9f), life - 2);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            for (BlockDisplay d : segs) {
                EggFx.kill(d);
            }
            segs.clear();
        }
    }

    /* ================================================================== lane wall */

    /** A standing wall of air sliding down a lane. Cannot be jumped; sidestep out of the lane. */
    static final class LaneWall implements Prop {
        final Vector3f start;
        final Vector3f dir;
        final float length;
        final float width;
        final int life;
        final Set<UUID> hit = new HashSet<>();
        double power;
        private final BlockDisplay wall;
        private final BlockDisplay core;
        private int age;

        LaneWall(EggFx fx, Vector3f start, Vector3f dir, float length, float width, int life, Color glow) {
            this.start = new Vector3f(start.x, 0f, start.z);
            this.dir = new Vector3f(dir.x, 0f, dir.z).normalize();
            this.length = length;
            this.width = width;
            this.life = Math.min(MAX_LIFE, Math.max(4, life));
            float yaw = (float) Math.atan2(this.dir.x, this.dir.z);
            wall = fx.prop(Material.WHITE_STAINED_GLASS, glow, 15);
            core = fx.prop(Material.LIGHT_GRAY_STAINED_GLASS, null, 15);
            EggFx.push(wall, slab(0f, yaw, 1f), 0);
            EggFx.push(core, slab(0f, yaw, 0.55f), 0);
        }

        private Matrix4f slab(float s, float yaw, float k) {
            Vector3f c = new Vector3f(start).fma(s, dir).add(0f, 1.15f * k, 0f);
            return EggFx.box(c, width * (0.9f + 0.1f * k), 2.3f * k, 0.35f + 0.25f * (1f - k), yaw);
        }

        boolean done() {
            return age >= life;
        }

        float frontNow() {
            return length * EggMath.clamp01((age - 2) / (float) (life - 2));
        }

        boolean active() {
            return age >= 2 && age < life;
        }

        boolean catches(Vector3f feet) {
            if (!active() || feet.y > 3.2f) {
                return false;
            }
            Vector3f rel = new Vector3f(feet.x - start.x, 0f, feet.z - start.z);
            float along = rel.dot(dir);
            float across = Math.abs(rel.x * dir.z - rel.z * dir.x);
            float s = frontNow();
            return across <= width * 0.5f + 0.3f && along >= s - 1.2f && along <= s + 0.7f;
        }

        @Override
        public boolean tick() {
            age++;
            float yaw = (float) Math.atan2(dir.x, dir.z);
            if (age == 2) {
                EggFx.push(wall, slab(length, yaw, 0.75f), life - 2);
                EggFx.push(core, slab(length, yaw, 0.45f), life - 2);
            }
            return age < life;
        }

        @Override
        public void remove() {
            EggFx.kill(wall);
            EggFx.kill(core);
        }
    }

    /* ================================================================== floor telegraphs */

    /** A flat floor stripe (lane telegraph). Grows in, holds, snaps off. */
    static final class Stripe implements Prop {
        private final BlockDisplay d;
        private final Matrix4f full;
        private final int life;
        private int age;

        Stripe(EggFx fx, Vector3f a, Vector3f b, float width, Material material, Color glow, int life) {
            this.life = Math.min(MAX_LIFE, Math.max(4, life));
            Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
            mid.y = 0.03f;
            float len = EggMath.horizontal(a, b);
            float yaw = (float) Math.atan2(b.x - a.x, b.z - a.z);
            full = EggFx.box(mid, width, 0.04f, len, yaw);
            d = fx.prop(material, glow, 15);
            EggFx.push(d, EggFx.box(new Vector3f(a.x, 0.03f, a.z), width * 0.2f, 0.04f, 0.1f, yaw), 0);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                EggFx.push(d, full, 3);
            }
            return age < life;
        }

        @Override
        public void remove() {
            EggFx.kill(d);
        }
    }

    /** A ring outline on the floor (sub zone, tweeter target). Optional slow shrink = timer. */
    static final class FloorRing implements Prop {
        private final List<BlockDisplay> segs = new ArrayList<>();
        private final Vector3f center;
        private final float r;
        private final float rEnd;
        private final int life;
        private int age;

        FloorRing(EggFx fx, Vector3f center, float r, float rEnd, Material material, Color glow, int life) {
            this.center = new Vector3f(center.x, 0.04f, center.z);
            this.r = r;
            this.rEnd = rEnd;
            this.life = Math.min(MAX_LIFE, Math.max(4, life));
            int n = Math.min(MAX_RING_SEGS, Math.max(10, Math.round(r * 5)));
            for (int i = 0; i < n; i++) {
                BlockDisplay d = fx.prop(material, glow, 15);
                EggFx.push(d, seg(i, n, r), 0);
                segs.add(d);
            }
        }

        private Matrix4f seg(int i, int n, float rad) {
            float a = (i + 0.5f) * EggMath.TAU / n;
            float chord = 2f * rad * (float) Math.sin(Math.PI / n) + 0.05f;
            Vector3f c = new Vector3f(center.x + (float) Math.sin(a) * rad, center.y, center.z + (float) Math.cos(a) * rad);
            return EggFx.box(c, chord, 0.05f, 0.22f, a + EggMath.HALF_PI);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2 && Math.abs(rEnd - r) > 0.01f) {
                for (int i = 0; i < segs.size(); i++) {
                    EggFx.push(segs.get(i), seg(i, segs.size(), rEnd), Math.max(1, life - 2));
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            for (BlockDisplay d : segs) {
                EggFx.kill(d);
            }
            segs.clear();
        }
    }

    /* ================================================================== launch column */

    static final class Column implements Prop {
        private final BlockDisplay d;
        private final Vector3f base;
        private final float width;
        private int age;

        Column(EggFx fx, Vector3f base, float width) {
            this.base = new Vector3f(base.x, 0.1f, base.z);
            this.width = width;
            d = fx.prop(Material.WHITE_STAINED_GLASS, Color.fromRGB(200, 230, 255), 15);
            EggFx.push(d, EggFx.box(new Vector3f(this.base).add(0f, 0.1f, 0f), width, 0.2f, width, 0f), 0);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                EggFx.push(d, EggFx.box(new Vector3f(base).add(0f, 4.5f, 0f), width * 0.85f, 9f, width * 0.85f, 0.4f), 4);
            } else if (age == 7) {
                EggFx.push(d, EggFx.box(new Vector3f(base).add(0f, 9f, 0f), width * 0.05f, 6f, width * 0.05f, 0.8f), 6);
            }
            return age < 13;
        }

        @Override
        public void remove() {
            EggFx.kill(d);
        }
    }

    /* ================================================================== flying things */

    /** A small glowing body flying a parabola from a to b. Fires {@code onLand} at arrival. */
    static final class Ball implements Prop {
        private final BlockDisplay d;
        private final Vector3f a;
        private final Vector3f b;
        private final float apex;
        private final int flight;
        private final float size;
        private final Runnable onLand;
        private int age;

        Ball(EggFx fx, Vector3f a, Vector3f b, float apex, int flight, float size, Material m, Color glow, Runnable onLand) {
            this.a = new Vector3f(a);
            this.b = new Vector3f(b);
            this.apex = apex;
            this.flight = Math.max(2, flight);
            this.size = size;
            this.onLand = onLand;
            d = fx.prop(m, glow, 15);
            EggFx.push(d, at(0f), 0);
        }

        private Matrix4f at(float t) {
            Vector3f p = EggMath.lerpVec(a, b, t);
            p.y += EggMath.arc(t) * apex;
            return new Matrix4f().translation(p).rotateY(t * 9f).rotateX(t * 7f).scale(size).translate(-0.5f, -0.5f, -0.5f);
        }

        @Override
        public boolean tick() {
            age++;
            float t = EggMath.clamp01(age / (float) flight);
            EggFx.push(d, at(t), 1);
            if (age >= flight) {
                if (onLand != null) {
                    onLand.run();
                }
                return false;
            }
            return true;
        }

        @Override
        public void remove() {
            EggFx.kill(d);
        }
    }

    /** The signal: a bright packet running down a cable in one smooth interpolation. */
    static final class Packet implements Prop {
        private final BlockDisplay d;
        private final BlockDisplay halo;
        private final Vector3f b;
        private final int hop;
        private final Runnable onArrive;
        private int age;

        Packet(EggFx fx, Vector3f a, Vector3f b, int hop, Color color, Runnable onArrive) {
            this.b = new Vector3f(b).add(0f, 0.25f, 0f);
            this.hop = Math.max(2, hop);
            this.onArrive = onArrive;
            Vector3f s = new Vector3f(a).add(0f, 0.25f, 0f);
            d = fx.prop(Material.WHITE_CONCRETE, color, 15);
            halo = fx.prop(Material.WHITE_STAINED_GLASS, color, 15);
            EggFx.push(d, EggFx.box(s, 0.38f, 0.38f, 0.38f, 0f), 0);
            EggFx.push(halo, EggFx.box(s, 0.7f, 0.7f, 0.7f, 0.7f), 0);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                EggFx.push(d, EggFx.box(b, 0.38f, 0.38f, 0.38f, 3f), hop - 2);
                EggFx.push(halo, EggFx.box(b, 0.7f, 0.7f, 0.7f, 3.7f), hop - 2);
            }
            if (age >= hop) {
                if (onArrive != null) {
                    onArrive.run();
                }
                return false;
            }
            return true;
        }

        @Override
        public void remove() {
            EggFx.kill(d);
            EggFx.kill(halo);
        }
    }

    /**
     * The lid of the egg pops off (Distortion) and clatters onto the floor, where it stays for the
     * rest of the fight. Pieces fly as one rigid body: same flight transform for every piece.
     */
    static final class Lid implements Prop {
        private final List<BlockDisplay> pieces = new ArrayList<>();
        private final List<Matrix4f> start = new ArrayList<>();
        private final Vector3f from;
        private final Vector3f to;
        private final int flight = 16;
        private int age;
        private final EggFx fx;

        Lid(EggFx fx, List<Matrix4f> matrices, List<Material> materials, Vector3f from, Vector3f to) {
            this.fx = fx;
            this.from = new Vector3f(from);
            this.to = new Vector3f(to);
            for (int i = 0; i < matrices.size(); i++) {
                BlockDisplay d = fx.block(materials.get(i), null, 12);
                EggFx.push(d, matrices.get(i), 0);
                pieces.add(d);
                start.add(new Matrix4f(matrices.get(i)));
            }
        }

        private Matrix4f flightAt(float t) {
            // Rigid transform: move the pivot along the arc, tumble it, then land upside down.
            Vector3f p = EggMath.lerpVec(from, to, t);
            p.y = EggMath.lerp(from.y, to.y, t) + EggMath.arc(t) * 5f;
            float spin = EggMath.outCubic(t) * EggMath.PI * 1.0f;
            return new Matrix4f().translation(p).rotateX(spin).rotateZ(spin * 0.4f).translate(-from.x, -from.y, -from.z);
        }

        @Override
        public boolean tick() {
            age++;
            if (age <= flight) {
                float t = age / (float) flight;
                Matrix4f f = flightAt(t);
                for (int i = 0; i < pieces.size(); i++) {
                    EggFx.push(pieces.get(i), new Matrix4f(f).mul(start.get(i)), 1);
                }
                if (age == flight) {
                    fx.sound(to, org.bukkit.Sound.BLOCK_DECORATED_POT_BREAK, 1.6f, 0.6f);
                    fx.sound(to, org.bukkit.Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.5f);
                    fx.particle(org.bukkit.Particle.CLOUD, to, 10, 0.6, 0.05);
                }
            }
            return true;
        }

        @Override
        public boolean persistent() {
            return true;
        }

        @Override
        public void remove() {
            for (Display d : pieces) {
                EggFx.kill(d);
            }
            pieces.clear();
        }
    }
}
