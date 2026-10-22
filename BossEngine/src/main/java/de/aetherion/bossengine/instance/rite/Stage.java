package de.aetherion.bossengine.instance.rite;

import de.aetherion.bossengine.instance.BossInstance;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Display toolkit for the rite bosses, built the way Seraphine's stage is: block and item displays that never
 * save with the chunk, moved by interpolated transformations. Short pieces of choreography run as {@link Anim}s
 * ticked by the owning kit; {@link #clear()} takes everything down.
 */
final class Stage {

    static final float VIEW = 3.0f;

    interface Anim {
        /** One server tick, {@code t} counted from 1. True when finished. */
        boolean tick(int t);

        default void end() {
        }
    }

    private static final class Run {
        private final Anim anim;
        private int t;

        private Run(Anim anim) {
            this.anim = anim;
        }
    }

    private final BossInstance instance;
    private final List<Run> runs = new ArrayList<>();
    private final List<Display> owned = new ArrayList<>();
    private int age;

    Stage(BossInstance instance) {
        this.instance = instance;
    }

    void play(Anim anim) {
        if (anim != null) {
            runs.add(new Run(anim));
        }
    }

    void tick() {
        age++;
        if (!runs.isEmpty()) {
            for (Run run : new ArrayList<>(runs)) {
                boolean done;
                try {
                    done = run.anim.tick(++run.t);
                } catch (RuntimeException exception) {
                    done = true;
                }
                if (done) {
                    runs.remove(run);
                    try {
                        run.anim.end();
                    } catch (RuntimeException ignored) {
                        // a broken end() must not keep the rest of the show alive
                    }
                }
            }
        }
        if (age % 40 == 0) {
            owned.removeIf(d -> d == null || !d.isValid());
        }
    }

    void clear() {
        for (Run run : new ArrayList<>(runs)) {
            try {
                run.anim.end();
            } catch (RuntimeException ignored) {
                // clearing anyway
            }
        }
        runs.clear();
        for (Display d : owned) {
            kill(d);
        }
        owned.clear();
    }

    /* ------------------------------------------------------------------ spawning */

    BlockDisplay block(Location at, Material material, Matrix4f start, Color glow, int light) {
        return block(at, material.createBlockData(), start, glow, light);
    }

    BlockDisplay block(Location at, BlockData data, Matrix4f start, Color glow, int light) {
        World world = at == null ? null : at.getWorld();
        if (world == null) {
            return null;
        }
        BlockDisplay display = world.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(data);
            prepare(d, start, glow, light);
        });
        owned.add(display);
        return display;
    }

    ItemDisplay item(Location at, Material material, Matrix4f start, Color glow, int light) {
        return item(at, new ItemStack(material), start, glow, light);
    }

    ItemDisplay item(Location at, ItemStack stack, Matrix4f start, Color glow, int light) {
        World world = at == null ? null : at.getWorld();
        if (world == null) {
            return null;
        }
        ItemDisplay display = world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            prepare(d, start, glow, light);
        });
        owned.add(display);
        return display;
    }

    private void prepare(Display d, Matrix4f start, Color glow, int light) {
        d.setPersistent(false);
        d.setGravity(false);
        d.setBillboard(Display.Billboard.FIXED);
        d.setViewRange(VIEW);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(0);
        d.setTeleportDuration(0);
        if (light >= 0) {
            int b = Math.max(0, Math.min(15, light));
            d.setBrightness(new Display.Brightness(b, b));
        }
        if (glow != null) {
            d.setGlowing(true);
            d.setGlowColorOverride(glow);
        }
        d.setTransformationMatrix(start == null ? new Matrix4f().scale(0.001f) : start);
        instance.getKeys().tagBeamFx(d, instance.getInstanceId());
    }

    void remove(Display d) {
        kill(d);
        owned.remove(d);
    }

    /** Fling a display: it flies from {@code start} (relative to its spawn point), falls, rests and fades. */
    Toss toss(Display d, boolean block, float size, Vector3f start, Vector3f vel) {
        Toss toss = new Toss(d, block, size, start, vel);
        play(toss);
        return toss;
    }

    final class Toss implements Anim {
        final Display d;
        final boolean block;
        final float size;
        final Vector3f pos;
        final Vector3f vel;
        double gravity = 0.06;
        double drag = 0.985;
        float floor;
        int rest = 30;
        float spin = 0.35f;
        Vector3f axis = new Vector3f(1f, 0.3f, 0.2f).normalize();
        Quaternionf rot = new Quaternionf();
        boolean flat = true;
        Runnable onLand;
        private boolean landed;
        private int landedAt;

        private Toss(Display d, boolean block, float size, Vector3f start, Vector3f vel) {
            this.d = d;
            this.block = block;
            this.size = size;
            this.pos = new Vector3f(start);
            this.vel = new Vector3f(vel);
        }

        @Override
        public boolean tick(int t) {
            if (!alive(d)) {
                return true;
            }
            if (!landed) {
                vel.y -= (float) gravity;
                vel.mul((float) drag);
                pos.add(vel);
                rot.rotateAxis(spin, axis);
                float rim = block ? size * 0.5f : 0.03f;
                if (pos.y <= floor + rim && vel.y < 0) {
                    pos.y = floor + rim;
                    landed = true;
                    landedAt = t;
                    if (flat) {
                        double yaw = Math.random() * Math.PI * 2;
                        rot = block ? yaw(yaw) : rot(yaw, Math.PI / 2, 0);
                    }
                    if (onLand != null) {
                        onLand.run();
                    }
                }
                push(d, frame(size), 1);
                return t > 160;
            }
            int since = t - landedAt;
            if (since == rest) {
                push(d, frame(0.001f), 10);
            }
            return since > rest + 10;
        }

        private Matrix4f frame(float s) {
            return block ? cube(pos, rot, s, s, s) : model(pos, rot, s);
        }

        @Override
        public void end() {
            remove(d);
        }
    }

    /* ------------------------------------------------------------------ motion */

    static void push(Display d, Matrix4f m, int interp) {
        if (d == null || !d.isValid()) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, interp));
        d.setTransformationMatrix(m);
    }

    static void move(Display d, Location to, int ticks) {
        if (d == null || !d.isValid() || to == null) {
            return;
        }
        d.setTeleportDuration(Math.max(0, Math.min(59, ticks)));
        d.teleport(to);
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

    static void kill(Display d) {
        if (d != null && d.isValid()) {
            d.remove();
        }
    }

    static boolean alive(Display d) {
        return d != null && d.isValid();
    }

    /* ------------------------------------------------------------------ matrices */

    /** Unit block centred on {@code c}. */
    static Matrix4f cube(Vector3f c, Quaternionf rot, float sx, float sy, float sz) {
        return new Matrix4f().translation(c).rotate(rot).scale(sx, sy, sz).translate(-0.5f, -0.5f, -0.5f);
    }

    /** Unit block standing on {@code base}: grows up along its own Y. */
    static Matrix4f post(Vector3f base, Quaternionf rot, float sx, float sy, float sz) {
        return new Matrix4f().translation(base).rotate(rot).scale(sx, sy, sz).translate(-0.5f, 0f, -0.5f);
    }

    /** Item model centred on {@code c}. */
    static Matrix4f model(Vector3f c, Quaternionf rot, float s) {
        return new Matrix4f().translation(c).rotate(rot).scale(s);
    }

    static Matrix4f gone(Vector3f at) {
        return new Matrix4f().translation(at).scale(0.001f);
    }

    /** A thin box stretched from a to b. */
    static Matrix4f beam(Vector3f a, Vector3f b, float width) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1e-4f) {
            return gone(a);
        }
        return new Matrix4f()
                .translation(a)
                .rotate(new Quaternionf().rotationTo(0f, 1f, 0f, dir.x / len, dir.y / len, dir.z / len))
                .scale(width, len, width)
                .translate(-0.5f, 0f, -0.5f);
    }

    static Quaternionf yaw(double radians) {
        return new Quaternionf().rotateY((float) radians);
    }

    static Quaternionf rot(double yaw, double pitch, double roll) {
        return new Quaternionf().rotateY((float) yaw).rotateX((float) pitch).rotateZ((float) roll);
    }

    static Vector3f v(double x, double y, double z) {
        return new Vector3f((float) x, (float) y, (float) z);
    }

    static Vector3f offset(Location origin, Location at) {
        return v(at.getX() - origin.getX(), at.getY() - origin.getY(), at.getZ() - origin.getZ());
    }

    static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return 1.0 - Math.pow(1.0 - t, 3.0);
    }

    static double easeIn(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t;
    }

    /* ------------------------------------------------------------------ particles + sound */

    static void fx(Location at, Particle particle, int count, double spread, double speed) {
        World world = at == null ? null : at.getWorld();
        if (world != null) {
            world.spawnParticle(particle, at, count, spread, spread, spread, speed);
        }
    }

    static void fx(Location at, Particle particle, int count, double sx, double sy, double sz, double speed) {
        World world = at == null ? null : at.getWorld();
        if (world != null) {
            world.spawnParticle(particle, at, count, sx, sy, sz, speed);
        }
    }

    static <T> void fx(Location at, Particle particle, int count, double spread, double speed, T data) {
        World world = at == null ? null : at.getWorld();
        if (world != null) {
            world.spawnParticle(particle, at, count, spread, spread, spread, speed, data);
        }
    }

    static <T> void fx(Location at, Particle particle, int count, double sx, double sy, double sz, double speed, T data) {
        World world = at == null ? null : at.getWorld();
        if (world != null) {
            world.spawnParticle(particle, at, count, sx, sy, sz, speed, data);
        }
    }

    static void dust(Location at, Color color, float size, int count, double spread) {
        fx(at, Particle.DUST, count, spread, 0, new Particle.DustOptions(color, size));
    }

    static void fade(Location at, Color from, Color to, float size, int count, double spread) {
        fx(at, Particle.DUST_COLOR_TRANSITION, count, spread, 0, new Particle.DustTransition(from, to, size));
    }

    /** Particle ring on the floor; {@code data} may be null. */
    static void ring(Location centre, double radius, double spacing, Particle particle, Object data, double lift) {
        World world = centre == null ? null : centre.getWorld();
        if (world == null || radius <= 0) {
            return;
        }
        int points = Math.max(8, (int) Math.round(Math.PI * 2 * radius / Math.max(0.2, spacing)));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            Location p = centre.clone().add(Math.cos(a) * radius, lift, Math.sin(a) * radius);
            if (data == null) {
                world.spawnParticle(particle, p, 1, 0, 0, 0, 0);
            } else {
                world.spawnParticle(particle, p, 1, 0, 0, 0, 0, data);
            }
        }
    }

    static void dustRing(Location centre, double radius, Color color, float size, double spacing) {
        ring(centre, radius, spacing, Particle.DUST, new Particle.DustOptions(color, size), 0.12);
    }

    static void sound(Location at, Sound sound, float volume, float pitch) {
        World world = at == null ? null : at.getWorld();
        if (world != null) {
            world.playSound(at, sound, SoundCategory.HOSTILE, volume, pitch);
        }
    }

    /** A point on the floor under {@code at} (up to four blocks down), lifted a hair. */
    static Location ground(Location at) {
        Location l = at.clone();
        for (int i = 0; i < 4 && l.getBlock().isPassable() && l.getBlockY() > l.getWorld().getMinHeight(); i++) {
            l.add(0, -1, 0);
        }
        if (!l.getBlock().isPassable()) {
            l.setY(l.getBlockY() + 1.0);
        }
        return l.add(0, 0.02, 0);
    }

    static BlockData floor(Location at) {
        BlockData below = at.clone().add(0, -0.5, 0).getBlock().getBlockData();
        return below.getMaterial().isAir() ? Material.STONE.createBlockData() : below;
    }
}
