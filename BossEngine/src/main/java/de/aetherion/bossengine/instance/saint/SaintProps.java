package de.aetherion.bossengine.instance.saint;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Short-lived choreography props. Each one is authored to cost as few metadata packets as
 * possible: most push one start and one end transform and let client interpolation do the rest.
 *
 * <p>Floor language, shared by every telegraph in the fight:
 * gold = something lands here, get out. Crimson = stay low / you are marked.
 * White flash = it happens now.
 */
final class SaintProps {

    static final Color GOLD = Color.fromRGB(255, 200, 70);
    static final Color CRIMSON = Color.fromRGB(235, 35, 55);
    static final Color WHITE = Color.fromRGB(255, 255, 255);

    interface Prop {
        /** @return false when finished (then {@link #remove()} is called). */
        boolean tick();

        void remove();
    }

    private final List<Prop> props = new ArrayList<>();

    <T extends Prop> T add(T prop) {
        props.add(prop);
        return prop;
    }

    void tick() {
        for (Iterator<Prop> it = props.iterator(); it.hasNext(); ) {
            Prop p = it.next();
            boolean alive;
            try {
                alive = p.tick();
            } catch (RuntimeException e) {
                alive = false;
            }
            if (!alive) {
                p.remove();
                it.remove();
            }
        }
    }

    void clear() {
        for (Prop p : props) {
            p.remove();
        }
        props.clear();
    }

    <T extends Prop> List<T> all(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Prop p : props) {
            if (type.isInstance(p)) {
                out.add(type.cast(p));
            }
        }
        return out;
    }

    /* ------------------------------------------------------------------ telegraph */

    /** Ground circle: a fixed rim plus a disc that fills to the rim exactly when it lands. */
    static final class Telegraph implements Prop {
        final Vector3f center;
        final float radius;
        final int life;
        private final List<BlockDisplay> rim = new ArrayList<>();
        private final BlockDisplay fillA;
        private final BlockDisplay fillB;
        private int age;

        Telegraph(SaintFx fx, Vector3f center, float radius, int life, boolean crimson) {
            this.center = new Vector3f(center.x, 0.04f, center.z);
            this.radius = radius;
            this.life = life;
            Material glass = crimson ? Material.RED_STAINED_GLASS : Material.YELLOW_STAINED_GLASS;
            Color glow = crimson ? CRIMSON : GOLD;
            int segs = radius > 4f ? 16 : 12;
            for (int i = 0; i < segs; i++) {
                BlockDisplay d = fx.block(Material.GOLD_BLOCK, glow, 15);
                float a0 = i * SaintMath.TAU / segs;
                float a1 = (i + 1) * SaintMath.TAU / segs;
                Vector3f p0 = new Vector3f((float) Math.sin(a0) * radius, 0.05f, (float) Math.cos(a0) * radius).add(this.center.x, 0, this.center.z);
                Vector3f p1 = new Vector3f((float) Math.sin(a1) * radius, 0.05f, (float) Math.cos(a1) * radius).add(this.center.x, 0, this.center.z);
                SaintFx.push(d, rimBeam(p0, p1), 0);
                rim.add(d);
            }
            fillA = fx.block(glass, null, 15);
            fillB = fx.block(glass, null, 15);
            SaintFx.push(fillA, SaintFx.flat(this.center, 0.01f, 0.02f, 0.01f, 0), 0);
            SaintFx.push(fillB, SaintFx.flat(this.center, 0.01f, 0.02f, 0.01f, SaintMath.PI / 4f), 0);
        }

        private static Matrix4f rimBeam(Vector3f a, Vector3f b) {
            Vector3f dir = new Vector3f(b).sub(a);
            float len = dir.length();
            float yaw = SaintMath.yawToward(dir.x, dir.z);
            Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
            return SaintFx.flat(mid, 0.12f, 0.03f, len + 0.05f, yaw);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                float d = radius * 2f * 0.924f;
                SaintFx.push(fillA, SaintFx.flat(center, d, 0.02f, d, 0), life - 2);
                SaintFx.push(fillB, SaintFx.flat(center, d, 0.021f, d, SaintMath.PI / 4f), life - 2);
            }
            if (age == life - 5) {
                for (BlockDisplay d : rim) {
                    SaintFx.glow(d, WHITE);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            for (BlockDisplay d : rim) {
                SaintFx.kill(d);
            }
            SaintFx.kill(fillA);
            SaintFx.kill(fillB);
        }
    }

    /* ------------------------------------------------------------------ lane */

    /** A flat strip on the floor from a to b that grows toward b, then flashes. */
    static final class Lane implements Prop {
        private final BlockDisplay strip;
        private final int life;
        private int age;

        Lane(SaintFx fx, Vector3f a, Vector3f b, float width, int life, boolean crimson) {
            this.life = life;
            strip = fx.block(crimson ? Material.RED_STAINED_GLASS : Material.YELLOW_STAINED_GLASS,
                    crimson ? CRIMSON : GOLD, 15);
            Vector3f from = new Vector3f(a.x, 0.05f, a.z);
            Vector3f to = new Vector3f(b.x, 0.05f, b.z);
            float yaw = SaintMath.yawToward(to.x - from.x, to.z - from.z);
            float len = SaintMath.horizontal(from, to);
            SaintFx.push(strip, new Matrix4f().translation(from).rotateY(yaw).scale(width, 0.02f, 0.01f).translate(-0.5f, 0, 0), 0);
            Matrix4f full = new Matrix4f().translation(from).rotateY(yaw).scale(width, 0.02f, len).translate(-0.5f, 0, 0);
            this.pending = full;
        }

        private Matrix4f pending;

        @Override
        public boolean tick() {
            age++;
            if (age == 2 && pending != null) {
                SaintFx.push(strip, pending, Math.max(1, life / 2));
                pending = null;
            }
            if (age == life - 4) {
                SaintFx.glow(strip, WHITE);
            }
            return age < life;
        }

        @Override
        public void remove() {
            SaintFx.kill(strip);
        }
    }

    /* ------------------------------------------------------------------ shockwave */

    /** An expanding, collapsing ring wall. One start push, one end push. */
    static final class Shockwave implements Prop {
        private final List<BlockDisplay> segs = new ArrayList<>();
        private final Vector3f center;
        private final float r1;
        private final int life;
        private final float height;
        private int age;

        Shockwave(SaintFx fx, Vector3f center, float r0, float r1, float height, int life, Material material, Color glow) {
            this.center = new Vector3f(center.x, 0.02f, center.z);
            this.r1 = r1;
            this.life = life;
            this.height = height;
            int n = 20;
            for (int i = 0; i < n; i++) {
                BlockDisplay d = fx.block(material, glow, 15);
                SaintFx.push(d, seg(i, n, r0, height), 0);
                segs.add(d);
            }
        }

        private Matrix4f seg(int i, int n, float r, float h) {
            float a = (i + 0.5f) * SaintMath.TAU / n;
            float chord = 2f * r * (float) Math.sin(Math.PI / n) + 0.1f;
            Vector3f c = new Vector3f(center.x + (float) Math.sin(a) * r, center.y + h * 0.5f, center.z + (float) Math.cos(a) * r);
            return SaintFx.flat(c, Math.max(0.01f, chord), Math.max(0.01f, h), 0.12f, a + SaintMath.HALF_PI);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                for (int i = 0; i < segs.size(); i++) {
                    SaintFx.push(segs.get(i), seg(i, segs.size(), r1, 0.01f), life - 2);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            for (BlockDisplay d : segs) {
                SaintFx.kill(d);
            }
        }
    }

    /* ------------------------------------------------------------------ giant needle */

    /**
     * A sewing needle as tall as a house, dropped from the Hand and left quivering in the boards.
     * The eye sits on top, where a thread can pass through.
     */
    static final class GiantNeedle implements Prop {
        final Vector3f base;
        final float height = 5.2f;
        private final BlockDisplay shaft;
        private final BlockDisplay eye;
        private final int fall;
        private final int stay;
        private int age;
        private final float tiltX;
        private final float tiltZ;
        boolean landed;

        GiantNeedle(SaintFx fx, Vector3f base, int fall, int stay) {
            this.base = new Vector3f(base.x, 0f, base.z);
            this.fall = fall;
            this.stay = stay;
            this.tiltX = (float) (Math.random() - 0.5) * 0.18f;
            this.tiltZ = (float) (Math.random() - 0.5) * 0.18f;
            shaft = fx.block(Material.IRON_BLOCK, null, 15);
            eye = fx.block(Material.GOLD_BLOCK, GOLD, 15);
            push(26f, 0);
        }

        private void push(float lift, int interp) {
            Matrix4f root = new Matrix4f().translation(base.x, base.y - 1.2f + lift, base.z).rotateX(tiltX).rotateZ(tiltZ);
            SaintFx.push(shaft, new Matrix4f(root).scale(0.22f, height + 1.2f, 0.22f).translate(-0.5f, 0, -0.5f), interp);
            SaintFx.push(eye, new Matrix4f(root).translate(0, height + 1.2f, 0).scale(0.46f, 0.9f, 0.1f).translate(-0.5f, 0, -0.5f), interp);
        }

        /** Top of the needle (where a rope threads through). */
        Vector3f eyePoint() {
            return new Vector3f(base.x, height - 0.2f, base.z);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 1) {
                push(0f, fall);
            }
            if (age == fall) {
                landed = true;
            }
            if (age == fall + stay) {
                push(-7f, 18);
            }
            return age < fall + stay + 18;
        }

        void expire() {
            age = Math.max(age, fall + stay - 1);
        }

        @Override
        public void remove() {
            SaintFx.kill(shaft);
            SaintFx.kill(eye);
        }
    }

    /* ------------------------------------------------------------------ rope */

    /**
     * A stitch between two needles. Low gold rope = jump it. High crimson rope = duck under it.
     * Rises from the boards, hums, flashes white, then plucks.
     */
    static final class Rope implements Prop {
        final Vector3f a;
        final Vector3f b;
        final boolean high;
        final float height;
        private final ThreadLine line;
        final int armAt;
        final int pluckAt;
        final int life;
        int age;

        Rope(SaintFx fx, Vector3f a, Vector3f b, boolean high, int armAt, int pluckAt) {
            this.a = new Vector3f(a.x, 0f, a.z);
            this.b = new Vector3f(b.x, 0f, b.z);
            this.high = high;
            this.height = high ? 1.62f : 0.32f;
            this.armAt = armAt;
            this.pluckAt = pluckAt;
            this.life = pluckAt + 10;
            this.line = new ThreadLine(fx, 3, high ? 0.09f : 0.08f,
                    high ? Material.REDSTONE_BLOCK : Material.GOLD_BLOCK, high ? CRIMSON : GOLD);
            line.sag = 0f;
            line.sagTarget = 0f;
            line.update(new Vector3f(this.a).add(0, 0.05f, 0), new Vector3f(this.b).add(0, 0.05f, 0), 0);
        }

        boolean plucking() {
            return age >= pluckAt && age < pluckAt + 3;
        }

        @Override
        public boolean tick() {
            age++;
            float rise = SaintMath.outBack(SaintMath.window(age, 0, armAt));
            float y = 0.05f + (height - 0.05f) * rise;
            if (age == pluckAt - 6) {
                line.setGlow(WHITE);
            }
            if (age == pluckAt) {
                line.pluck(0.6f);
            }
            if (age > pluckAt + 3) {
                y *= 1f - SaintMath.window(age, pluckAt + 3, life);
            }
            line.update(new Vector3f(a).add(0, y, 0), new Vector3f(b).add(0, y, 0), 1);
            return age < life;
        }

        @Override
        public void remove() {
            line.remove();
        }
    }

    /* ------------------------------------------------------------------ bolt */

    /** A thrown needle on a precomputed straight path. Two pushes total. */
    static final class Bolt implements Prop {
        final Vector3f from;
        final Vector3f to;
        final int flight;
        private final BlockDisplay d;
        private int age;
        private final boolean sticks;

        Bolt(SaintFx fx, Vector3f from, Vector3f to, int flight, boolean sticks) {
            this.from = new Vector3f(from);
            this.to = new Vector3f(to);
            this.flight = Math.max(1, flight);
            this.sticks = sticks;
            d = fx.block(Material.IRON_BLOCK, GOLD, 15);
            SaintFx.push(d, needle(this.from), 0);
        }

        private Matrix4f needle(Vector3f at) {
            Vector3f dir = new Vector3f(to).sub(from).normalize();
            return new Matrix4f().translation(at)
                    .rotate(new Quaternionf().rotationTo(0, 1, 0, dir.x, dir.y, dir.z))
                    .scale(0.09f, 1.8f, 0.09f).translate(-0.5f, -0.5f, -0.5f);
        }

        Vector3f positionAt(int tick) {
            return SaintMath.lerp(from, to, SaintMath.clamp01(tick / (float) flight), new Vector3f());
        }

        int age() {
            return age;
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 1) {
                SaintFx.push(d, needle(to), flight);
            }
            return age < flight + (sticks ? 40 : 1);
        }

        @Override
        public void remove() {
            SaintFx.kill(d);
        }
    }

    /* ------------------------------------------------------------------ afterimage */

    /** A glass ghost of a pose, left behind by stop-motion movement. Fades by shrinking. */
    static final class Afterimage implements Prop {
        private final List<BlockDisplay> ghosts = new ArrayList<>();
        private final List<Matrix4f> ends = new ArrayList<>();
        private final int life;
        private int age;

        Afterimage(SaintFx fx, Skeleton rig, int life, boolean crimson) {
            this.life = life;
            rig.solve();
            int i = 0;
            for (Skeleton.Piece p : rig.pieces()) {
                if (p.hidden || !"body".equals(p.group) && !"needle".equals(p.group)) {
                    continue;
                }
                if (i++ % 2 == 1 && !"needle".equals(p.group)) {
                    continue;
                }
                Matrix4f m = rig.matrixOf(p);
                BlockDisplay g = fx.block(crimson ? Material.RED_STAINED_GLASS : Material.WHITE_STAINED_GLASS, null, 15);
                SaintFx.push(g, m, 0);
                Vector3f c = m.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
                ends.add(new Matrix4f().translation(c.x, c.y + 0.4f, c.z).scale(0.001f));
                ghosts.add(g);
            }
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 3) {
                for (int i = 0; i < ghosts.size(); i++) {
                    SaintFx.push(ghosts.get(i), ends.get(i), life - 3);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            for (BlockDisplay g : ghosts) {
                SaintFx.kill(g);
            }
        }
    }

    /* ------------------------------------------------------------------ shard */

    /** A porcelain fragment on a two-leg arc (up, then down to the boards), then lies still. */
    static final class Shard implements Prop {
        private final BlockDisplay d;
        private final Vector3f from;
        private final Vector3f peak;
        private final Vector3f land;
        private final int up;
        private final int down;
        private final int rest;
        private final float size;
        private int age;

        Shard(SaintFx fx, Material material, Vector3f from, Vector3f velocity, int up, int down, int rest, float size) {
            this.from = new Vector3f(from);
            this.peak = new Vector3f(from).fma(up, velocity).add(0, up * 0.25f, 0);
            this.land = new Vector3f(peak.x + velocity.x * down, 0.04f + size * 0.5f, peak.z + velocity.z * down);
            this.up = up;
            this.down = down;
            this.rest = rest;
            this.size = size;
            d = fx.block(material, null, 15);
            SaintFx.push(d, cube(this.from, 0), 0);
        }

        private Matrix4f cube(Vector3f at, float spin) {
            return new Matrix4f().translation(at).rotateXYZ(spin, spin * 1.3f, spin * 0.7f)
                    .scale(size).translate(-0.5f, -0.5f, -0.5f);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 1) {
                SaintFx.push(d, cube(peak, 2.5f), up);
            } else if (age == up + 1) {
                SaintFx.push(d, cube(land, 5.2f), down);
            }
            return age < up + down + rest;
        }

        @Override
        public void remove() {
            SaintFx.kill(d);
        }
    }
}
