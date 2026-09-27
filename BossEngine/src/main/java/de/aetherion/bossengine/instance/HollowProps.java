package de.aetherion.bossengine.instance;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Hollow Sun's floor language, built from displays instead of dust.
 *
 * <p>Every prop spawns its pieces on one fixed floor anchor and never teleports them: it pushes a
 * start pose, then an end pose, and lets client interpolation do the motion. That keeps tells crisp
 * for players on reduced particle settings and costs a handful of packets per telegraph.
 *
 * <p>The vocabulary, identical in every phase:
 * <ul>
 *   <li><b>Sun glyph</b> (rim, rays, an eight-point star fill): something lands here. The star's
 *       points reach the rim on the tick it lands. Rays point out while the star burns and point in
 *       once it has collapsed: its light is falling inward now.</li>
 *   <li><b>White</b> rim: it happens now.</li>
 *   <li><b>Crimson</b>: the one place you never stand.</li>
 *   <li><b>Green</b> ring at his feet: an opening. It closes as the window does.</li>
 *   <li><b>A glowing ridge</b> rolling across the floor: a shockwave. Jump it.</li>
 * </ul>
 */
final class HollowProps {

    static final Color GOLD = Color.fromRGB(255, 196, 64);
    static final Color EMBER = Color.fromRGB(255, 122, 32);
    static final Color FLARE = Color.fromRGB(255, 64, 24);
    static final Color BLUESHIFT = Color.fromRGB(96, 168, 255);
    static final Color CRIMSON = Color.fromRGB(205, 18, 40);
    static final Color OPENING = Color.fromRGB(110, 255, 140);
    static final Color HOT = Color.fromRGB(255, 255, 255);

    static final float PI = (float) Math.PI;
    static final float TAU = PI * 2f;

    /** Rim block, fill glass and glow for one tell family. */
    enum Palette {
        SUN(GOLD, Material.SHROOMLIGHT, Material.YELLOW_STAINED_GLASS),
        GIANT(FLARE, Material.ORANGE_CONCRETE, Material.ORANGE_STAINED_GLASS),
        INFALL(BLUESHIFT, Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS),
        LETHAL(CRIMSON, Material.RED_CONCRETE, Material.RED_STAINED_GLASS),
        OPEN(OPENING, Material.LIME_CONCRETE, Material.LIME_STAINED_GLASS);

        final Color glow;
        final BlockData rim;
        final BlockData fill;

        Palette(Color glow, Material rim, Material fill) {
            this.glow = glow;
            this.rim = rim.createBlockData();
            this.fill = fill.createBlockData();
        }
    }

    interface Prop {
        /** @return false once finished; {@link #remove()} is called right after. */
        boolean tick();

        void remove();
    }

    private final BossInstance instance;
    private final List<Prop> live = new ArrayList<>();

    HollowProps(BossInstance instance) {
        this.instance = instance;
    }

    <T extends Prop> T add(T prop) {
        live.add(prop);
        return prop;
    }

    void tick() {
        for (Iterator<Prop> it = live.iterator(); it.hasNext(); ) {
            Prop prop = it.next();
            boolean alive;
            try {
                alive = prop.tick();
            } catch (RuntimeException exception) {
                alive = false;
            }
            if (!alive) {
                prop.remove();
                it.remove();
            }
        }
    }

    void clear() {
        for (Prop prop : live) {
            prop.remove();
        }
        live.clear();
    }

    /* ------------------------------------------------------------------ displays */

    BlockDisplay block(Location at, Material material, Color glow) {
        return block(at, material.createBlockData(), glow);
    }

    BlockDisplay block(Location at, BlockData data, Color glow) {
        World world = at.getWorld();
        Location spawn = at.clone();
        spawn.setYaw(0f);
        spawn.setPitch(0f);
        return world.spawn(spawn, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(2.0f);
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(0);
            d.setTeleportDuration(0);
            d.setBrightness(new Display.Brightness(15, 15));
            if (glow != null) {
                d.setGlowing(true);
                d.setGlowColorOverride(glow);
            }
            d.setTransformationMatrix(new Matrix4f().scale(0.001f));
            instance.getKeys().tagBeamFx(d, instance.getInstanceId());
        });
    }

    static void push(Display d, Matrix4f m, int interp) {
        if (d == null || !d.isValid()) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, interp));
        d.setTransformationMatrix(m);
    }

    static void glow(Display d, Color color) {
        if (d == null || !d.isValid()) {
            return;
        }
        if (color == null) {
            d.setGlowing(false);
            return;
        }
        d.setGlowColorOverride(color);
        d.setGlowing(true);
    }

    static void swap(BlockDisplay d, BlockData data) {
        if (d != null && d.isValid()) {
            d.setBlock(data);
        }
    }

    static void kill(Display d) {
        if (d != null && d.isValid()) {
            d.remove();
        }
    }

    static void killAll(List<? extends Display> displays) {
        for (Display d : displays) {
            kill(d);
        }
        displays.clear();
    }

    /* ------------------------------------------------------------------ matrices (local to the anchor) */

    /** Unit block centered on (cx, cy, cz), sized, turned so its local +Z faces {@code yaw}. */
    static Matrix4f box(float cx, float cy, float cz, float sx, float sy, float sz, float yaw) {
        return new Matrix4f()
                .translation(cx, cy, cz)
                .rotateY(yaw)
                .scale(Math.max(0.001f, sx), Math.max(0.001f, sy), Math.max(0.001f, sz))
                .translate(-0.5f, -0.5f, -0.5f);
    }

    /** A square rod from a to b. */
    static Matrix4f rod(Vector3f a, Vector3f b, float width) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1.0e-4f) {
            return gone(a);
        }
        dir.div(len);
        float w = Math.max(0.001f, width);
        return new Matrix4f()
                .translation(a)
                .rotate(new Quaternionf().rotationTo(0f, 1f, 0f, dir.x, dir.y, dir.z))
                .scale(w, len, w)
                .translate(-0.5f, 0f, -0.5f);
    }

    /** A cube centered on {@code c}, turned by {@code q}. */
    static Matrix4f cube(Vector3f c, Quaternionf q, float size) {
        float s = Math.max(0.001f, size);
        return new Matrix4f().translation(c).rotate(q).scale(s).translate(-0.5f, -0.5f, -0.5f);
    }

    static Matrix4f gone(Vector3f at) {
        return new Matrix4f().translation(at).scale(0.001f);
    }

    /** Yaw that turns local +Z toward (dx, dz). */
    static float yawOf(float dx, float dz) {
        return (float) Math.atan2(dx, dz);
    }

    static Location floorAnchor(Location at) {
        Location out = at.clone();
        out.setYaw(0f);
        out.setPitch(0f);
        return out;
    }

    /* ================================================================== sun glyph */

    /**
     * Ground telegraph: a rim, a ring of rays and an eight-point star that grows from the center.
     * The star's points touch the rim on the tick it lands; the rim goes white five ticks before.
     */
    static final class Glyph implements Prop {
        private final List<BlockDisplay> edge = new ArrayList<>();
        private final BlockDisplay fillA;
        private final BlockDisplay fillB;
        private final float radius;
        private final int life;
        private int age;
        private boolean dead;
        private boolean frozen;

        Glyph(HollowProps fx, Location center, float radius, int life, Palette palette, boolean inward) {
            Location at = floorAnchor(center);
            this.radius = radius;
            this.life = Math.max(6, life);
            int segs = radius < 2.0f ? 10 : radius < 3.5f ? 12 : radius < 6.0f ? 16 : 22;
            float y = 0.03f;
            for (int i = 0; i < segs; i++) {
                float a0 = i * TAU / segs;
                float a1 = (i + 1) * TAU / segs;
                float x0 = (float) Math.sin(a0) * radius;
                float z0 = (float) Math.cos(a0) * radius;
                float x1 = (float) Math.sin(a1) * radius;
                float z1 = (float) Math.cos(a1) * radius;
                float len = (float) Math.hypot(x1 - x0, z1 - z0) + 0.06f;
                BlockDisplay d = fx.block(at, palette.rim, palette.glow);
                push(d, box((x0 + x1) * 0.5f, y, (z0 + z1) * 0.5f, 0.13f, 0.035f, len, yawOf(x1 - x0, z1 - z0)), 0);
                edge.add(d);
            }
            int rays = radius < 2.5f ? 4 : 8;
            float rayLen = Math.max(0.35f, Math.min(1.3f, radius * 0.26f));
            for (int i = 0; i < rays; i++) {
                float a = i * TAU / rays;
                float start = inward ? radius - 0.2f - rayLen : radius + 0.2f;
                float mid = start + rayLen * 0.5f;
                BlockDisplay d = fx.block(at, palette.rim, palette.glow);
                push(d, box((float) Math.sin(a) * mid, y, (float) Math.cos(a) * mid, 0.11f, 0.035f, rayLen, a), 0);
                edge.add(d);
            }
            fillA = fx.block(at, palette.fill, null);
            fillB = fx.block(at, palette.fill, null);
            push(fillA, box(0f, 0.02f, 0f, 0.01f, 0.02f, 0.01f, 0f), 0);
            push(fillB, box(0f, 0.022f, 0f, 0.01f, 0.02f, 0.01f, PI / 4f), 0);
        }

        /** Slide the whole glyph to follow a target (only while nothing is animating). */
        void follow(Location center) {
            Location at = floorAnchor(center);
            for (BlockDisplay d : edge) {
                slide(d, at);
            }
            slide(fillA, at);
            slide(fillB, at);
        }

        private static void slide(BlockDisplay d, Location at) {
            if (d != null && d.isValid()) {
                d.setTeleportDuration(1);
                d.teleport(at);
            }
        }

        /** Hold the fill at zero until {@link #start()}: used while the glyph is still tracking. */
        Glyph hold() {
            frozen = true;
            return this;
        }

        /** Begin the countdown now; the star reaches the rim {@code life} ticks from here. */
        void start() {
            frozen = false;
            age = 0;
        }

        void expire() {
            dead = true;
        }

        @Override
        public boolean tick() {
            if (dead) {
                return false;
            }
            if (frozen) {
                return true;
            }
            age++;
            if (age == 2) {
                float d = radius * 1.4f;
                push(fillA, box(0f, 0.02f, 0f, d, 0.02f, d, 0f), life - 2);
                push(fillB, box(0f, 0.022f, 0f, d, 0.02f, d, PI / 4f), life - 2);
            }
            if (age == Math.max(3, life - 5)) {
                for (BlockDisplay d : edge) {
                    glow(d, HOT);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            killAll(edge);
            kill(fillA);
            kill(fillB);
        }
    }

    /* ================================================================== lane */

    /** A straight strip: two rails and a cap at once, a fill that runs to the cap by the time it fires. */
    static final class Lane implements Prop {
        private final List<BlockDisplay> rails = new ArrayList<>();
        private final BlockDisplay fill;
        private final Matrix4f full;
        private final int life;
        private int age;
        private boolean dead;

        Lane(HollowProps fx, Location origin, Vector dir, float length, float halfWidth, int life, Palette palette) {
            Location at = floorAnchor(origin);
            this.life = Math.max(4, life);
            Vector f = dir.clone().setY(0);
            if (f.lengthSquared() < 1.0e-4) {
                f = new Vector(0, 0, 1);
            }
            f.normalize();
            float dx = (float) f.getX();
            float dz = (float) f.getZ();
            float yaw = yawOf(dx, dz);
            float sx = dz;
            float sz = -dx;
            float y = 0.03f;
            for (int s = -1; s <= 1; s += 2) {
                BlockDisplay d = fx.block(at, palette.rim, palette.glow);
                push(d, box(sx * halfWidth * s + dx * length * 0.5f, y, sz * halfWidth * s + dz * length * 0.5f,
                        0.12f, 0.035f, length, yaw), 0);
                rails.add(d);
            }
            BlockDisplay cap = fx.block(at, palette.rim, palette.glow);
            push(cap, box(dx * length, y, dz * length, halfWidth * 2f + 0.12f, 0.035f, 0.12f, yaw), 0);
            rails.add(cap);
            fill = fx.block(at, palette.fill, null);
            push(fill, box(dx * 0.01f, 0.02f, dz * 0.01f, halfWidth * 2f, 0.02f, 0.02f, yaw), 0);
            full = box(dx * length * 0.5f, 0.02f, dz * length * 0.5f, halfWidth * 2f, 0.02f, length, yaw);
        }

        void expire() {
            dead = true;
        }

        @Override
        public boolean tick() {
            if (dead) {
                return false;
            }
            age++;
            if (age == 2) {
                push(fill, full, life - 2);
            }
            if (age == Math.max(3, life - 5)) {
                for (BlockDisplay d : rails) {
                    glow(d, HOT);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            killAll(rails);
            kill(fill);
        }
    }

    /* ================================================================== sector */

    /** A cone in front of him: edges and rim at once, a fan that fills outward from his feet. */
    static final class Sector implements Prop {
        private final List<BlockDisplay> edge = new ArrayList<>();
        private final List<BlockDisplay> fan = new ArrayList<>();
        private final List<Matrix4f> fanFull = new ArrayList<>();
        private final int life;
        private int age;
        private boolean dead;

        Sector(HollowProps fx, Location origin, Vector dir, float radius, float halfDeg, int life, Palette palette) {
            Location at = floorAnchor(origin);
            this.life = Math.max(4, life);
            Vector f = dir.clone().setY(0);
            if (f.lengthSquared() < 1.0e-4) {
                f = new Vector(0, 0, 1);
            }
            f.normalize();
            float base = yawOf((float) f.getX(), (float) f.getZ());
            float half = (float) Math.toRadians(halfDeg);
            float y = 0.03f;
            float inner = 0.7f;
            for (int s = -1; s <= 1; s += 2) {
                float a = base + half * s;
                float mid = (inner + radius) * 0.5f;
                BlockDisplay d = fx.block(at, palette.rim, palette.glow);
                push(d, box((float) Math.sin(a) * mid, y, (float) Math.cos(a) * mid, 0.12f, 0.035f, radius - inner, a), 0);
                edge.add(d);
            }
            int arcs = Math.max(4, (int) Math.ceil(2f * half * radius / 1.1f));
            for (int i = 0; i < arcs; i++) {
                float a0 = base - half + 2f * half * i / arcs;
                float a1 = base - half + 2f * half * (i + 1) / arcs;
                float x0 = (float) Math.sin(a0) * radius;
                float z0 = (float) Math.cos(a0) * radius;
                float x1 = (float) Math.sin(a1) * radius;
                float z1 = (float) Math.cos(a1) * radius;
                float len = (float) Math.hypot(x1 - x0, z1 - z0) + 0.06f;
                BlockDisplay d = fx.block(at, palette.rim, palette.glow);
                push(d, box((x0 + x1) * 0.5f, y, (z0 + z1) * 0.5f, 0.12f, 0.035f, len, yawOf(x1 - x0, z1 - z0)), 0);
                edge.add(d);
            }
            int wedges = Math.max(3, Math.round(2f * half / 0.3f));
            float step = 2f * half / wedges;
            float width = 2f * radius * (float) Math.sin(step * 0.5f) * 1.08f;
            for (int j = 0; j < wedges; j++) {
                float a = base - half + (j + 0.5f) * step;
                float fy = 0.02f + j * 0.0015f;
                BlockDisplay d = fx.block(at, palette.fill, null);
                push(d, box((float) Math.sin(a) * 0.01f, fy, (float) Math.cos(a) * 0.01f, width, 0.02f, 0.02f, a), 0);
                fan.add(d);
                fanFull.add(box((float) Math.sin(a) * radius * 0.5f, fy, (float) Math.cos(a) * radius * 0.5f, width, 0.02f, radius, a));
            }
        }

        void expire() {
            dead = true;
        }

        @Override
        public boolean tick() {
            if (dead) {
                return false;
            }
            age++;
            if (age == 2) {
                for (int i = 0; i < fan.size(); i++) {
                    push(fan.get(i), fanFull.get(i), life - 2);
                }
            }
            if (age == Math.max(3, life - 5)) {
                for (BlockDisplay d : edge) {
                    glow(d, HOT);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            killAll(edge);
            killAll(fan);
        }
    }

    /* ================================================================== ring wall (shockwave) */

    /** An expanding ring wall: one start pose, one end pose. Tall and bright = jump it; flat = only light. */
    static final class RingWall implements Prop {
        private final List<BlockDisplay> segs = new ArrayList<>();
        private final float r1;
        private final float h1;
        private final int life;
        private int age;

        RingWall(HollowProps fx, Location center, float r0, float r1, float h0, float h1, int life,
                 Material material, Color glow, int count) {
            Location at = floorAnchor(center);
            this.r1 = r1;
            this.h1 = h1;
            this.life = Math.max(4, life);
            for (int i = 0; i < count; i++) {
                BlockDisplay d = fx.block(at, material, glow);
                push(d, seg(i, count, r0, h0), 0);
                segs.add(d);
            }
        }

        private static Matrix4f seg(int i, int n, float r, float h) {
            float a = (i + 0.5f) * TAU / n;
            float chord = 2f * r * (float) Math.sin(PI / n) + 0.08f;
            return box((float) Math.sin(a) * r, h * 0.5f + 0.01f, (float) Math.cos(a) * r, 0.16f, h, chord, a + PI * 0.5f);
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                for (int i = 0; i < segs.size(); i++) {
                    push(segs.get(i), seg(i, segs.size(), r1, h1), life - 2);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            killAll(segs);
        }
    }

    /* ================================================================== seams (the floor cracking) */

    /**
     * Branching cracks that race outward one depth per tick, glow, then cool: white-gold, ember,
     * red, black, gone. Cold seams (Collapse) run white-blue to black instead.
     */
    static final class Seams implements Prop {
        private static final BlockData[] WARM = {
                Material.OCHRE_FROGLIGHT.createBlockData(), Material.ORANGE_CONCRETE.createBlockData(),
                Material.RED_CONCRETE.createBlockData(), Material.BLACK_CONCRETE.createBlockData()};
        private static final BlockData[] COLD = {
                Material.SEA_LANTERN.createBlockData(), Material.LIGHT_BLUE_CONCRETE.createBlockData(),
                Material.BLUE_CONCRETE.createBlockData(), Material.BLACK_CONCRETE.createBlockData()};

        private final List<BlockDisplay> tiles = new ArrayList<>();
        private final List<float[]> segs = new ArrayList<>();
        private final boolean cold;
        private final int life;
        private int age;

        Seams(HollowProps fx, Location floor, float heading, float spread, int branches, float reach, float width,
              boolean cold, int life) {
            Location at = floorAnchor(floor);
            this.cold = cold;
            this.life = Math.max(20, life);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int perBranch = Math.max(4, Math.min(64, branches * 7) / Math.max(1, branches));
            for (int b = 0; b < branches; b++) {
                float h = branches == 1 ? heading
                        : heading - spread * 0.5f + spread * (b + 0.5f) / branches;
                h += (float) random.nextDouble(-0.18, 0.18);
                grow(0f, 0f, h, width, reach / 3.2f, 5, 0, true, segs.size() + perBranch, random);
            }
            Color first = cold ? BLUESHIFT : GOLD;
            BlockData hot = (cold ? COLD : WARM)[0];
            for (int i = 0; i < segs.size(); i++) {
                float[] s = segs.get(i);
                BlockDisplay d = fx.block(at, hot, first);
                push(d, tile(s, 0.001f, i), 0);
                tiles.add(d);
            }
        }

        private void grow(float x, float z, float heading, float width, float len, int steps, int depth0,
                          boolean fork, int cap, ThreadLocalRandom random) {
            for (int d = 0; d < steps && segs.size() < cap; d++) {
                heading += (float) random.nextDouble(-0.45, 0.45);
                float nx = x + (float) Math.sin(heading) * len;
                float nz = z + (float) Math.cos(heading) * len;
                segs.add(new float[]{x, z, nx, nz, width, depth0 + d});
                if (fork && d >= 1 && random.nextDouble() < 0.4) {
                    float side = random.nextBoolean() ? 1f : -1f;
                    grow(nx, nz, heading + side * (float) random.nextDouble(0.5, 1.0), width * 0.6f, len * 0.7f, 2,
                            depth0 + d + 1, false, cap, random);
                }
                x = nx;
                z = nz;
                len *= 0.88f;
                width *= 0.8f;
            }
        }

        private static Matrix4f tile(float[] s, float grow, int index) {
            float dx = s[2] - s[0];
            float dz = s[3] - s[1];
            float len = (float) Math.hypot(dx, dz) + s[4] * 0.5f;
            float y = 0.014f + (index % 5) * 0.0012f;
            float mx = s[0] + dx * 0.5f * grow;
            float mz = s[1] + dz * 0.5f * grow;
            return box(mx, y, mz, s[4] * Math.min(1f, grow * 4f), 0.012f, len * grow, yawOf(dx, dz));
        }

        @Override
        public boolean tick() {
            age++;
            for (int i = 0; i < tiles.size(); i++) {
                float[] s = segs.get(i);
                if (age == (int) s[5] + 2) {
                    push(tiles.get(i), tile(s, 1f, i), 1);
                }
            }
            BlockData[] ramp = cold ? COLD : WARM;
            int cool1 = (int) (life * 0.3f);
            int cool2 = (int) (life * 0.55f);
            int cool3 = (int) (life * 0.75f);
            if (age == cool1) {
                for (BlockDisplay d : tiles) {
                    swap(d, ramp[1]);
                    glow(d, cold ? BLUESHIFT : EMBER);
                }
            } else if (age == cool2) {
                for (BlockDisplay d : tiles) {
                    swap(d, ramp[2]);
                    glow(d, null);
                }
            } else if (age == cool3) {
                for (BlockDisplay d : tiles) {
                    swap(d, ramp[3]);
                }
            } else if (age == life - 6) {
                for (int i = 0; i < tiles.size(); i++) {
                    float[] s = segs.get(i);
                    float[] thin = {s[0], s[1], s[2], s[3], 0.001f, s[5]};
                    push(tiles.get(i), tile(thin, 1f, i), 6);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            killAll(tiles);
        }
    }

    /* ================================================================== slash trail */

    /** The mace's path through the air: a white-hot arc that thins to nothing. Shown full on the hit tick. */
    static final class Slash implements Prop {
        private final List<BlockDisplay> plates = new ArrayList<>();
        private final List<float[]> shape = new ArrayList<>();
        private final int life;
        private int age;

        Slash(HollowProps fx, Location pivot, Vector fwd, float radius, float halfDeg, int life, Color glow) {
            Location at = floorAnchor(pivot);
            this.life = Math.max(4, life);
            Vector f = fwd.clone().setY(0);
            if (f.lengthSquared() < 1.0e-4) {
                f = new Vector(0, 0, 1);
            }
            f.normalize();
            float base = yawOf((float) f.getX(), (float) f.getZ());
            float half = (float) Math.toRadians(halfDeg);
            int n = 7;
            for (int i = 0; i < n; i++) {
                float a0 = base + half - 2f * half * i / n;
                float a1 = base + half - 2f * half * (i + 1) / n;
                float x0 = (float) Math.sin(a0) * radius;
                float z0 = (float) Math.cos(a0) * radius;
                float x1 = (float) Math.sin(a1) * radius;
                float z1 = (float) Math.cos(a1) * radius;
                float len = (float) Math.hypot(x1 - x0, z1 - z0) + 0.12f;
                float drop = -0.25f * i / n;
                float[] s = {(x0 + x1) * 0.5f, drop, (z0 + z1) * 0.5f, len, yawOf(x1 - x0, z1 - z0)};
                shape.add(s);
                BlockDisplay d = fx.block(at, Material.WHITE_CONCRETE, glow);
                push(d, box(s[0], s[1], s[2], 0.07f, 0.34f - 0.03f * i, s[3], s[4]), 0);
                plates.add(d);
            }
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                for (int i = 0; i < plates.size(); i++) {
                    float[] s = shape.get(i);
                    push(plates.get(i), box(s[0], s[1], s[2], 0.01f, 0.01f, s[3] * 0.6f, s[4]), life - 2);
                }
            }
            return age < life;
        }

        @Override
        public void remove() {
            killAll(plates);
        }
    }

    /* ================================================================== rays (corona discharge) */

    /** Light rods thrown out of the core: they lance out in three ticks and thin to threads. */
    static final class Rays implements Prop {
        private final List<BlockDisplay> rods = new ArrayList<>();
        private final List<Vector3f> dirs = new ArrayList<>();
        private final float reach;
        private int age;

        Rays(HollowProps fx, Location core, int count, float reach, float pitch, Material material, Color glow) {
            Location at = floorAnchor(core);
            this.reach = reach;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            float offset = (float) random.nextDouble(TAU);
            for (int i = 0; i < count; i++) {
                float a = offset + i * TAU / count;
                float p = (float) random.nextDouble(-pitch, pitch);
                Vector3f dir = new Vector3f((float) (Math.sin(a) * Math.cos(p)), (float) Math.sin(p),
                        (float) (Math.cos(a) * Math.cos(p))).normalize();
                dirs.add(dir);
                BlockDisplay d = fx.block(at, material, glow);
                push(d, rod(new Vector3f(dir).mul(0.3f), new Vector3f(dir).mul(1.1f), 0.2f), 0);
                rods.add(d);
            }
        }

        @Override
        public boolean tick() {
            age++;
            if (age == 2) {
                for (int i = 0; i < rods.size(); i++) {
                    Vector3f dir = dirs.get(i);
                    push(rods.get(i), rod(new Vector3f(dir).mul(0.4f), new Vector3f(dir).mul(reach), 0.24f), 3);
                }
            }
            if (age == 5) {
                for (int i = 0; i < rods.size(); i++) {
                    Vector3f dir = dirs.get(i);
                    push(rods.get(i), rod(new Vector3f(dir).mul(reach * 0.85f), new Vector3f(dir).mul(reach * 1.15f), 0.01f), 6);
                }
            }
            return age < 12;
        }

        @Override
        public void remove() {
            killAll(rods);
        }
    }

    /* ================================================================== single rod */

    /** One glowing rod the director aims by hand (the lance's searching light). */
    static final class Rod implements Prop {
        private final BlockDisplay rod;
        private boolean dead;

        Rod(HollowProps fx, Location anchor, Material material, Color glow) {
            rod = fx.block(floorAnchor(anchor), material, glow);
        }

        void set(Vector3f a, Vector3f b, float width, int interp) {
            push(rod, HollowProps.rod(a, b, width), interp);
        }

        void color(Color glow) {
            HollowProps.glow(rod, glow);
        }

        void expire() {
            dead = true;
        }

        @Override
        public boolean tick() {
            return !dead;
        }

        @Override
        public void remove() {
            kill(rod);
        }
    }

    /* ================================================================== ring */

    /**
     * A thin ring of plates lying on the floor: resized, spun, recolored or eaten away by the
     * director (event-horizon rim, the Nova's integrity, the opening window).
     */
    static final class Ring implements Prop {
        private final List<BlockDisplay> segs = new ArrayList<>();
        private final float y;
        private final float thick;
        private final float height;
        private float radius;
        private float turn;
        private int shown;
        private int lifeLeft;
        private int age;
        private float closeTo = -1f;
        private int closeTicks;
        private boolean dead;

        Ring(HollowProps fx, Location center, float radius, float y, int count, Material material, Color glow,
             float thick, float height, int life) {
            Location at = floorAnchor(center);
            this.radius = radius;
            this.y = y;
            this.thick = thick;
            this.height = height;
            this.lifeLeft = life;
            this.shown = count;
            for (int i = 0; i < count; i++) {
                BlockDisplay d = fx.block(at, material, glow);
                push(d, seg(i, count, radius, turn), 0);
                segs.add(d);
            }
        }

        private Matrix4f seg(int i, int n, float r, float spin) {
            float a = spin + (i + 0.5f) * TAU / n;
            float chord = 2f * r * (float) Math.sin(PI / n) + 0.05f;
            return box((float) Math.sin(a) * r, y, (float) Math.cos(a) * r, thick, height, chord, a + PI * 0.5f);
        }

        void resize(float r, int ticks) {
            radius = r;
            repose(ticks);
        }

        /** Shrink (or grow) to {@code r} over {@code ticks}, starting once the client has the ring. */
        Ring closeTo(float r, int ticks) {
            closeTo = r;
            closeTicks = ticks;
            return this;
        }

        void spin(float delta, int ticks) {
            turn += delta;
            repose(ticks);
        }

        /** Keep only the first {@code fraction} of the ring lit; the rest collapses into nothing. */
        void showFraction(float fraction) {
            int want = Math.max(0, Math.min(segs.size(), Math.round(segs.size() * fraction)));
            if (want >= shown) {
                return;
            }
            for (int i = want; i < shown; i++) {
                push(segs.get(i), gone(new Vector3f(0f, y, 0f)), 3);
            }
            shown = want;
        }

        void color(Color glow) {
            for (BlockDisplay d : segs) {
                HollowProps.glow(d, glow);
            }
        }

        private void repose(int ticks) {
            for (int i = 0; i < shown; i++) {
                push(segs.get(i), seg(i, segs.size(), radius, turn), ticks);
            }
        }

        void expire() {
            dead = true;
        }

        @Override
        public boolean tick() {
            if (dead) {
                return false;
            }
            age++;
            if (age == 2 && closeTo >= 0f) {
                resize(closeTo, Math.max(1, closeTicks - 2));
            }
            if (lifeLeft > 0) {
                lifeLeft--;
                return lifeLeft > 0;
            }
            return true;
        }

        @Override
        public void remove() {
            killAll(segs);
        }
    }

    /* ================================================================== loop (prominence) */

    /**
     * A magnetic loop of plasma drawn from the arena edge toward a target, one segment at a time.
     * When it touches down, it is complete; the director then sends the plasma down it.
     */
    static final class Loop implements Prop {
        private final List<BlockDisplay> rods = new ArrayList<>();
        private final List<Vector3f> points = new ArrayList<>();
        private final int grow;
        private final float width;
        private int age;
        private int fadeAt = -1;
        private int endAt = -1;

        Loop(HollowProps fx, Location from, Location to, double apex, int count, int grow, float width,
             Material material, Color glow) {
            Location at = floorAnchor(from);
            this.grow = Math.max(1, grow);
            this.width = width;
            for (int k = 0; k <= count; k++) {
                double t = k / (double) count;
                points.add(new Vector3f(
                        (float) ((to.getX() - from.getX()) * t),
                        (float) ((to.getY() - from.getY()) * t + Math.sin(t * Math.PI) * apex),
                        (float) ((to.getZ() - from.getZ()) * t)));
            }
            for (int k = 0; k < count; k++) {
                BlockDisplay d = fx.block(at, material, glow);
                push(d, gone(points.get(k)), 0);
                rods.add(d);
            }
        }

        /** Point on the loop, 0..1, relative to its anchor. */
        Vector3f at(float t) {
            float f = Math.max(0f, Math.min(1f, t)) * (points.size() - 1);
            int i = Math.min(points.size() - 2, (int) f);
            return new Vector3f(points.get(i)).lerp(points.get(i + 1), f - i);
        }

        void fade(int ticks) {
            fadeAt = age + 1;
            endAt = age + 1 + Math.max(1, ticks);
        }

        @Override
        public boolean tick() {
            age++;
            int n = rods.size();
            for (int k = 0; k < n; k++) {
                if (age == 2 + (k * grow) / n) {
                    push(rods.get(k), rod(points.get(k), points.get(k + 1), width), Math.max(1, grow / n));
                }
            }
            if (age == fadeAt) {
                for (int k = 0; k < n; k++) {
                    push(rods.get(k), rod(points.get(k), points.get(k + 1), 0.01f), endAt - fadeAt);
                }
            }
            return endAt < 0 || age < endAt;
        }

        @Override
        public void remove() {
            killAll(rods);
        }
    }
}
