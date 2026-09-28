package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Short-lived choreography props. Most push one start and one end transform and let client
 * interpolation do the rest.
 *
 * <p>Floor language, shared by both World Eater fights:
 * <ul>
 *   <li><b>violet</b> = the void will be here. Get out.</li>
 *   <li><b>white flash</b> = it happens now.</li>
 *   <li><b>yellow posts and cyan rails</b> (the F3+G chunk border every player knows) = this chunk
 *       is about to be eaten.</li>
 *   <li><b>ember cracks</b> (the Unbroken only) = the floor is about to break here.</li>
 * </ul>
 */
public final class WeProps {

    public static final Color VOID = Color.fromRGB(150, 60, 255);
    public static final Color VOID_DEEP = Color.fromRGB(70, 20, 130);
    public static final Color WHITE = Color.fromRGB(255, 255, 255);
    public static final Color CHUNK_YELLOW = Color.fromRGB(255, 235, 60);
    public static final Color CHUNK_CYAN = Color.fromRGB(70, 200, 255);
    public static final Color EMBER = Color.fromRGB(255, 120, 40);
    public static final Color BEDROCK = Color.fromRGB(120, 120, 130);

    public interface Prop {
        /** @return false when finished (then {@link #remove()} is called). */
        boolean tick();

        void remove();
    }

    private final List<Prop> props = new ArrayList<>();

    public <T extends Prop> T add(T prop) {
        props.add(prop);
        return prop;
    }

    public void tick() {
        for (Iterator<Prop> it = props.iterator(); it.hasNext(); ) {
            Prop p = it.next();
            boolean alive;
            try {
                alive = p.tick();
            } catch (RuntimeException e) {
                alive = false;
            }
            if (!alive) {
                try {
                    p.remove();
                } catch (RuntimeException ignored) {
                    // a prop that fails to clean up must not stop the others
                }
                it.remove();
            }
        }
    }

    public void clear() {
        for (Prop p : props) {
            try {
                p.remove();
            } catch (RuntimeException ignored) {
                // keep clearing
            }
        }
        props.clear();
    }

    public void removeProp(Prop prop) {
        if (props.remove(prop)) {
            prop.remove();
        }
    }

    public int size() {
        return props.size();
    }

    /* ================================================================== circle */

    /** Ground circle: a fixed rim plus a disc that fills to the rim exactly when it lands. */
    public static final class Circle implements Prop {
        final Vector3f center;
        final float radius;
        final int life;
        private final WeFx fx;
        private final List<BlockDisplay> rim = new ArrayList<>();
        private final BlockDisplay fillA;
        private final BlockDisplay fillB;
        private int age;

        public Circle(WeFx fx, Vector3f center, float radius, int life) {
            this(fx, center, radius, life, Material.PURPLE_STAINED_GLASS, VOID);
        }

        public Circle(WeFx fx, Vector3f center, float radius, int life, Material fill, Color glow) {
            this.fx = fx;
            this.center = new Vector3f(center.x, center.y + 0.04f, center.z);
            this.radius = radius;
            this.life = life;
            int segs = radius > 5f ? 20 : radius > 3f ? 16 : 12;
            for (int i = 0; i < segs; i++) {
                BlockDisplay d = fx.block(Material.BLACK_CONCRETE, glow, 15);
                float a0 = i * WeMath.TAU / segs;
                float a1 = (i + 1) * WeMath.TAU / segs;
                Vector3f p0 = new Vector3f((float) Math.sin(a0) * radius, 0.02f, (float) Math.cos(a0) * radius).add(this.center);
                Vector3f p1 = new Vector3f((float) Math.sin(a1) * radius, 0.02f, (float) Math.cos(a1) * radius).add(this.center);
                WeFx.push(d, rimBeam(p0, p1), 0);
                rim.add(d);
            }
            fillA = fx.block(fill, null, 15);
            fillB = fx.block(fill, null, 15);
            WeFx.push(fillA, WeFx.flat(this.center, 0.01f, 0.02f, 0.01f, 0f), 0);
            WeFx.push(fillB, WeFx.flat(this.center, 0.01f, 0.02f, 0.01f, WeMath.PI / 4f), 0);
        }

        static org.bukkit.util.Transformation rimBeam(Vector3f a, Vector3f b) {
            Vector3f dir = new Vector3f(b).sub(a);
            float len = dir.length();
            float yaw = WeMath.yawToward(dir.x, dir.z);
            Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
            return WeFx.flat(mid, 0.14f, 0.04f, len + 0.06f, yaw);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                float d = radius * 2f * 0.924f;
                WeFx.push(fillA, WeFx.flat(center, d, 0.02f, d, 0f), Math.max(1, life - 2));
                WeFx.push(fillB, WeFx.flat(center, d, 0.021f, d, WeMath.PI / 4f), Math.max(1, life - 2));
            }
            if (age == life - 5) {
                for (BlockDisplay d : rim) {
                    WeFx.glow(d, WHITE);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            for (BlockDisplay d : rim) {
                fx.kill(d);
            }
            fx.kill(fillA);
            fx.kill(fillB);
        }
    }

    /* ================================================================== lane */

    /**
     * A flat strip on the floor from a to b that grows toward b over {@code grow} ticks, flashes
     * white at {@code flashAt}, and stays until {@code life}.
     */
    public static final class Lane implements Prop {
        private final WeFx fx;
        private final BlockDisplay strip;
        private final BlockDisplay edgeL;
        private final BlockDisplay edgeR;
        private final int grow;
        private final int flashAt;
        private final int life;
        private final org.bukkit.util.Transformation full;
        private final org.bukkit.util.Transformation fullL;
        private final org.bukkit.util.Transformation fullR;
        private int age;

        public Lane(WeFx fx, Vector3f a, Vector3f b, float width, int grow, int flashAt, int life) {
            this(fx, a, b, width, grow, flashAt, life, Material.PURPLE_STAINED_GLASS, VOID);
        }

        public Lane(WeFx fx, Vector3f a, Vector3f b, float width, int grow, int flashAt, int life, Material fill, Color edge) {
            this.fx = fx;
            this.grow = Math.max(1, grow);
            this.flashAt = flashAt;
            this.life = life;
            strip = fx.block(fill, null, 15);
            edgeL = fx.block(Material.BLACK_CONCRETE, edge, 15);
            edgeR = fx.block(Material.BLACK_CONCRETE, edge, 15);
            Vector3f from = new Vector3f(a.x, a.y + 0.05f, a.z);
            Vector3f to = new Vector3f(b.x, b.y + 0.05f, b.z);
            float yaw = WeMath.yawToward(to.x - from.x, to.z - from.z);
            float len = Math.max(0.1f, WeMath.horizontal(from, to));
            Quaternionf rot = new Quaternionf().rotateY(yaw);
            Vector3f side = rot.transform(new Vector3f(1f, 0f, 0f));
            full = strip(from, rot, width, len, 0.02f);
            fullL = strip(new Vector3f(from).fma(width * 0.5f, side), rot, 0.12f, len, 0.04f);
            fullR = strip(new Vector3f(from).fma(-width * 0.5f, side), rot, 0.12f, len, 0.04f);
            WeFx.push(strip, strip(from, rot, width, 0.01f, 0.02f), 0);
            WeFx.push(edgeL, strip(new Vector3f(from).fma(width * 0.5f, side), rot, 0.12f, 0.01f, 0.04f), 0);
            WeFx.push(edgeR, strip(new Vector3f(from).fma(-width * 0.5f, side), rot, 0.12f, 0.01f, 0.04f), 0);
        }

        /** Strip whose near edge starts at {@code from} and runs {@code len} along rot's +Z. */
        private static org.bukkit.util.Transformation strip(Vector3f from, Quaternionf rot, float width, float len, float thick) {
            Vector3f corner = rot.transform(new Vector3f(-width * 0.5f, 0f, 0f));
            return new org.bukkit.util.Transformation(new Vector3f(from).add(corner), new Quaternionf(rot),
                    new Vector3f(width, thick, len), new Quaternionf());
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                WeFx.push(strip, full, grow);
                WeFx.push(edgeL, fullL, grow);
                WeFx.push(edgeR, fullR, grow);
            }
            if (age == flashAt) {
                WeFx.glow(edgeL, WHITE);
                WeFx.glow(edgeR, WHITE);
                WeFx.glow(strip, WHITE);
            }
            return age < life;
        }

        @Override
        public void remove() {
            fx.kill(strip);
            fx.kill(edgeL);
            fx.kill(edgeR);
        }
    }

    /* ================================================================== chunk border */

    /**
     * The F3+G chunk border, drawn in the world: four tall yellow corner posts and cyan rails along
     * the chunk edges. Every Minecraft player reads it instantly as "this chunk".
     * Lives until {@link #expire()} (or {@code life} ticks).
     */
    public static final class ChunkMark implements Prop {
        private final WeFx fx;
        private final List<BlockDisplay> posts = new ArrayList<>();
        private final List<BlockDisplay> rails = new ArrayList<>();
        private final float x0;
        private final float z0;
        private final float floor;
        private int life;
        private int age;
        private boolean flashing;

        public ChunkMark(WeFx fx, float x0, float z0, float floor, int life) {
            this.fx = fx;
            this.x0 = x0;
            this.z0 = z0;
            this.floor = floor;
            this.life = life;
            float[][] corners = {{0, 0}, {16, 0}, {16, 16}, {0, 16}};
            for (float[] c : corners) {
                BlockDisplay post = fx.block(Material.YELLOW_CONCRETE, CHUNK_YELLOW, 15);
                Vector3f bottom = new Vector3f(x0 + c[0], floor - 32f, z0 + c[1]);
                WeFx.push(post, WeFx.beam(bottom, new Vector3f(bottom).add(0f, 0.01f, 0f), 0.08f), 0);
                posts.add(post);
            }
            float[] heights = {0.04f, 2f, 4f, 8f};
            for (float h : heights) {
                for (int e = 0; e < 4; e++) {
                    BlockDisplay rail = fx.block(Material.LIGHT_BLUE_CONCRETE, CHUNK_CYAN, 15);
                    Vector3f a = new Vector3f(x0 + corners[e][0], floor + h, z0 + corners[e][1]);
                    WeFx.push(rail, WeFx.beam(a, new Vector3f(a).add(0.01f, 0f, 0f), 0.05f), 0);
                    rails.add(rail);
                }
            }
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                float[][] corners = {{0, 0}, {16, 0}, {16, 16}, {0, 16}};
                for (int i = 0; i < posts.size(); i++) {
                    Vector3f bottom = new Vector3f(x0 + corners[i][0], floor - 32f, z0 + corners[i][1]);
                    WeFx.push(posts.get(i), WeFx.beam(bottom, new Vector3f(bottom).add(0f, 64f, 0f), 0.08f), 8);
                }
                float[] heights = {0.04f, 2f, 4f, 8f};
                int k = 0;
                for (float h : heights) {
                    for (int e = 0; e < 4; e++) {
                        Vector3f a = new Vector3f(x0 + corners[e][0], floor + h, z0 + corners[e][1]);
                        Vector3f b = new Vector3f(x0 + corners[(e + 1) % 4][0], floor + h, z0 + corners[(e + 1) % 4][1]);
                        WeFx.push(rails.get(k++), WeFx.beam(a, b, 0.05f), 10);
                    }
                }
            }
            if (!flashing && age % 10 == 0) {
                boolean bright = (age / 10) % 2 == 0;
                for (BlockDisplay r : rails) {
                    WeFx.brightness(r, bright ? 15 : 9);
                }
            }
            return age < life;
        }

        /** Everything goes white: it happens now. */
        public void flash() {
            flashing = true;
            for (BlockDisplay d : posts) {
                WeFx.glow(d, WHITE);
                WeFx.material(d, Material.WHITE_CONCRETE);
            }
            for (BlockDisplay d : rails) {
                WeFx.glow(d, WHITE);
                WeFx.material(d, Material.WHITE_CONCRETE);
            }
        }

        public void expire(int inTicks) {
            life = Math.min(life, age + Math.max(1, inTicks));
        }

        @Override
        public void remove() {
            for (BlockDisplay d : posts) {
                fx.kill(d);
            }
            for (BlockDisplay d : rails) {
                fx.kill(d);
            }
        }
    }

    /* ================================================================== shockwave */

    /** An expanding, collapsing ring wall. One start push, one end push. */
    public static final class Shockwave implements Prop {
        private final WeFx fx;
        private final List<BlockDisplay> segs = new ArrayList<>();
        private final Vector3f center;
        private final float r1;
        private final int life;
        private final float height;
        private int age;

        public Shockwave(WeFx fx, Vector3f center, float r0, float r1, float height, int life, Material material, Color glow) {
            this.fx = fx;
            this.center = new Vector3f(center.x, center.y + 0.02f, center.z);
            this.r1 = r1;
            this.life = life;
            this.height = height;
            int n = r1 > 10f ? 28 : 20;
            for (int i = 0; i < n; i++) {
                BlockDisplay d = fx.block(material, glow, 15);
                WeFx.push(d, seg(i, n, r0, height), 0);
                segs.add(d);
            }
        }

        private org.bukkit.util.Transformation seg(int i, int n, float r, float h) {
            float a = (i + 0.5f) * WeMath.TAU / n;
            float chord = 2f * r * (float) Math.sin(Math.PI / n) + 0.1f;
            Vector3f c = new Vector3f(center.x + (float) Math.sin(a) * r, center.y + h * 0.5f, center.z + (float) Math.cos(a) * r);
            return WeFx.flat(c, Math.max(0.01f, chord), Math.max(0.01f, h), 0.12f, a + WeMath.HALF_PI);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                for (int i = 0; i < segs.size(); i++) {
                    WeFx.push(segs.get(i), seg(i, segs.size(), r1, 0.01f), Math.max(1, life - 2));
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            for (BlockDisplay d : segs) {
                fx.kill(d);
            }
        }
    }

    /* ================================================================== debris */

    /** A block fragment on a two-leg arc (up to a peak, down to a landing), then rests and shrinks. */
    public static final class Debris implements Prop {
        private final WeFx fx;
        private final Display d;
        private final Vector3f peak;
        private final Vector3f land;
        private final int up;
        private final int down;
        private final int rest;
        private final float size;
        private final float spin;
        private int age;

        public Debris(WeFx fx, Material material, Vector3f from, Vector3f peak, Vector3f land, int up, int down, int rest, float size) {
            this.fx = fx;
            this.peak = new Vector3f(peak);
            this.land = new Vector3f(land);
            this.up = Math.max(1, up);
            this.down = Math.max(1, down);
            this.rest = rest;
            this.size = size;
            this.spin = (float) ThreadLocalRandom.current().nextDouble(1.5, 4.5);
            d = fx.block(material, null, 15);
            WeFx.push(d, WeFx.cube(from, size, new Quaternionf()), 0);
        }

        /** Thrown from {@code from} with a velocity-like offset; lands at {@code floorY}. */
        public static Debris toss(WeFx fx, Material material, Vector3f from, Vector3f push, float floorY, float size) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vector3f peak = new Vector3f(from).add(push.x * 0.5f, 1.2f + Math.abs(push.y), push.z * 0.5f);
            Vector3f land = new Vector3f(from.x + push.x, floorY + size * 0.5f, from.z + push.z);
            int up = 6 + r.nextInt(5);
            int down = 8 + r.nextInt(6);
            return new Debris(fx, material, from, peak, land, up, down, 20 + r.nextInt(20), size);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 1) {
                WeFx.push(d, WeFx.cube(peak, size, new Quaternionf().rotateXYZ(spin, spin * 1.3f, spin * 0.7f)), up);
            } else if (age == up + 1) {
                WeFx.push(d, WeFx.cube(land, size, new Quaternionf().rotateXYZ(spin * 2f, spin * 2.6f, spin * 1.4f)), down);
            } else if (age == up + down + rest - 8) {
                WeFx.push(d, WeFx.cube(new Vector3f(land).sub(0f, size * 0.5f, 0f), 0.01f, new Quaternionf()), 8);
            }
            return age < up + down + rest;
        }

        @Override
        public void remove() {
            fx.kill(d);
        }
    }

    /** A block that falls from its spot into the void, tumbling. */
    public static final class Plunge implements Prop {
        private final WeFx fx;
        private final Display d;
        private final int life;
        private int age;
        private final Vector3f from;
        private final Vector3f to;
        private final Quaternionf endRot;
        private final Vector3f size;

        public Plunge(WeFx fx, Material material, Vector3f from, Vector3f size, float drop, int life) {
            this.fx = fx;
            this.life = Math.max(2, life);
            this.from = new Vector3f(from);
            this.size = new Vector3f(size);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            this.to = new Vector3f(from).add((float) r.nextGaussian() * 1.5f, -drop, (float) r.nextGaussian() * 1.5f);
            this.endRot = new Quaternionf().rotateXYZ((float) r.nextDouble(-2, 2), (float) r.nextDouble(-2, 2), (float) r.nextDouble(-2, 2));
            d = fx.block(material, null, 15);
            WeFx.push(d, WeFx.box(from, size, new Quaternionf()), 0);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                WeFx.push(d, WeFx.box(to, new Vector3f(size).mul(0.6f), endRot), life - 2);
            }
            return age < life;
        }

        @Override
        public void remove() {
            fx.kill(d);
        }
    }

    /* ================================================================== beam */

    /** A thick beam: a dark core inside a glowing shell. Grows from a to b, holds, then thins out. */
    public static final class Beam implements Prop {
        private final WeFx fx;
        private final BlockDisplay core;
        private final BlockDisplay shell;
        private final Vector3f a;
        private final Vector3f b;
        private final float width;
        private final int grow;
        private final int hold;
        private final int fade;
        private int age;

        public Beam(WeFx fx, Vector3f a, Vector3f b, float width, int grow, int hold, int fade) {
            this.fx = fx;
            this.a = new Vector3f(a);
            this.b = new Vector3f(b);
            this.width = width;
            this.grow = Math.max(1, grow);
            this.hold = hold;
            this.fade = Math.max(1, fade);
            core = fx.block(Material.BLACK_CONCRETE, null, 15);
            shell = fx.block(Material.PURPLE_STAINED_GLASS, VOID, 15);
            WeFx.push(core, WeFx.beam(a, new Vector3f(a).add(0f, 0.01f, 0f), width * 0.55f), 0);
            WeFx.push(shell, WeFx.beam(a, new Vector3f(a).add(0f, 0.01f, 0f), width), 0);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                WeFx.push(core, WeFx.beam(a, b, width * 0.55f), grow);
                WeFx.push(shell, WeFx.beam(a, b, width), grow);
            }
            if (age == 2 + grow + hold) {
                WeFx.push(core, WeFx.beam(a, b, 0.01f), fade);
                WeFx.push(shell, WeFx.beam(a, b, 0.02f), fade);
            }
            return age < 2 + grow + hold + fade;
        }

        @Override
        public void remove() {
            fx.kill(core);
            fx.kill(shell);
        }
    }

    /* ================================================================== crack */

    /** A dark hairline crack on the floor that glows before the floor breaks. */
    public static final class Crack implements Prop {
        private final WeFx fx;
        private final List<BlockDisplay> lines = new ArrayList<>();
        private final int life;
        private final int glowAt;
        private final List<Runnable> pending = new ArrayList<>();
        private int age;

        public Crack(WeFx fx, List<Vector3f> path, float width, int life, int glowAt) {
            this.fx = fx;
            this.life = life;
            this.glowAt = glowAt;
            for (int i = 0; i + 1 < path.size(); i++) {
                Vector3f a = new Vector3f(path.get(i)).add(0f, 0.03f, 0f);
                Vector3f b = new Vector3f(path.get(i + 1)).add(0f, 0.03f, 0f);
                BlockDisplay d = fx.block(Material.BLACK_CONCRETE, null, 15);
                WeFx.push(d, WeFx.plank(a, a, width, 0.02f, WeMath.UP), 0);
                lines.add(d);
                final int index = i;
                pending.add(() -> WeFx.push(d, WeFx.plank(a, b, width, 0.02f, WeMath.UP), 2 + index));
            }
        }

        /** A jagged crack from a to b in {@code steps} kinks. */
        public static List<Vector3f> jagged(Vector3f a, Vector3f b, int steps, float jitter) {
            List<Vector3f> out = new ArrayList<>();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vector3f dir = new Vector3f(b).sub(a);
            Vector3f side = new Vector3f(-dir.z, 0f, dir.x);
            if (side.lengthSquared() > 1e-6f) {
                side.normalize();
            }
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                Vector3f p = WeMath.lerp(a, b, t, new Vector3f());
                if (i > 0 && i < steps) {
                    p.fma((float) r.nextDouble(-jitter, jitter), side);
                }
                out.add(p);
            }
            return out;
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 1) {
                for (Runnable r : pending) {
                    r.run();
                }
                pending.clear();
            }
            if (age == glowAt) {
                for (BlockDisplay d : lines) {
                    WeFx.material(d, Material.MAGMA_BLOCK);
                    WeFx.glow(d, EMBER);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            for (BlockDisplay d : lines) {
                fx.kill(d);
            }
        }
    }

    /* ================================================================== column */

    /** A column of stone that bursts up out of the floor and sinks back. */
    public static final class Column implements Prop {
        private final WeFx fx;
        private final BlockDisplay d;
        private final Vector3f base;
        private final float width;
        private final float height;
        private final int rise;
        private final int stay;
        private final int sink;
        private final float yaw;
        private int age;

        public Column(WeFx fx, Material material, Vector3f base, float width, float height, int rise, int stay, int sink) {
            this.fx = fx;
            this.base = new Vector3f(base);
            this.width = width;
            this.height = height;
            this.rise = Math.max(1, rise);
            this.stay = stay;
            this.sink = Math.max(1, sink);
            this.yaw = (float) ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
            d = fx.block(material, null, 15);
            WeFx.push(d, shape(0.01f), 0);
        }

        private org.bukkit.util.Transformation shape(float h) {
            Vector3f c = new Vector3f(base).add(0f, h * 0.5f - 0.35f, 0f);
            return WeFx.box(c, new Vector3f(width, Math.max(0.01f, h), width), new Quaternionf().rotateY(yaw));
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 1) {
                WeFx.push(d, shape(height), rise);
            }
            if (age == 1 + rise + stay) {
                WeFx.push(d, shape(0.01f), sink);
            }
            return age < 1 + rise + stay + sink;
        }

        @Override
        public void remove() {
            fx.kill(d);
        }
    }

    /* ================================================================== flying item */

    /** An item icon that pops up and flies to a moving point (loot to a claimant, a flower into a maw). */
    public static final class FlyingItem implements Prop {
        private final WeFx fx;
        private final ItemDisplay d;
        private final java.util.function.Supplier<Vector3f> target;
        private final Vector3f pop;
        private final int popTicks;
        private final int flyTicks;
        private final float size;
        private int age;

        public FlyingItem(WeFx fx, ItemStack stack, Vector3f from, Vector3f pop, java.util.function.Supplier<Vector3f> target,
                          int popTicks, int flyTicks, float size) {
            this.fx = fx;
            this.target = target;
            this.pop = new Vector3f(pop);
            this.popTicks = Math.max(1, popTicks);
            this.flyTicks = Math.max(1, flyTicks);
            this.size = size;
            d = fx.item(stack, 15);
            WeFx.push(d, itemAt(from, 0.01f, 0f), 0);
        }

        private static org.bukkit.util.Transformation itemAt(Vector3f at, float s, float spin) {
            return new org.bukkit.util.Transformation(new Vector3f(at), new Quaternionf().rotateY(spin),
                    new Vector3f(s), new Quaternionf());
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 1) {
                WeFx.push(d, itemAt(pop, size, 3f), popTicks);
            }
            if (age > popTicks && age <= popTicks + flyTicks) {
                float t = (age - popTicks) / (float) flyTicks;
                Vector3f goal = target.get();
                Vector3f at = WeMath.lerp(pop, goal, WeMath.inQuad(t), new Vector3f());
                at.y += WeMath.arc(t) * 0.8f;
                WeFx.push(d, itemAt(at, size * (1f - 0.6f * t), 3f + t * 8f), 1);
            }
            return age <= popTicks + flyTicks;
        }

        @Override
        public void remove() {
            fx.kill(d);
        }
    }
}
