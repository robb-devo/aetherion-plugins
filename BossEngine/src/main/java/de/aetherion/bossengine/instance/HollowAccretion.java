package de.aetherion.bossengine.instance;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Collapse: the black star eats the forge.
 *
 * <p>Plates of the real Bloodstone floor are torn up (a display that starts as an exact overlay of
 * the block, while the hole underneath is painted molten client-side), lifted, and pulled into a
 * tilted accretion disk around the core. They heat as they spiral in and are swallowed at the
 * center. The disk throws what it cannot swallow: {@link #fling} sends one plate down on a player.
 * When the star dies, every plate falls back into its own hole and the floor heals.
 *
 * <p>Nothing here edits the world. Each plate display stays on its home block and moves only by
 * interpolated transformation; every painted hole is tracked and restored.
 */
final class HollowAccretion {

    /** The director's side of a thrown plate landing: damage, knockback, dressing. */
    interface Landing {
        void land(Location at, BlockData plate);
    }

    private static final int MAX_PLATES = 14;
    private static final int PUSH_EVERY = 2;
    private static final float DISK_IN = 1.15f;
    private static final float DISK_OUT = 3.9f;
    private static final BlockData HOLE = Material.MAGMA_BLOCK.createBlockData();

    private enum State { LIFT, ORBIT, THROWN, LANDED, SETTLE, EATEN }

    private final BossInstance instance;
    private final List<Plate> plates = new ArrayList<>();
    private final Map<Location, BlockData> holes = new HashMap<>();
    private final Set<Location> claimed = new HashSet<>();
    private final Set<UUID> painted = new HashSet<>();

    private World world;
    private double floorY;
    private Location diskCenter;
    private Quaternionf tilt = new Quaternionf();
    private boolean active;
    private boolean releasing;
    private int age;
    private int tearCd;

    HollowAccretion(BossInstance instance) {
        this.instance = instance;
    }

    boolean isActive() {
        return active;
    }

    boolean isReleasing() {
        return releasing;
    }

    /** Plates currently in the disk (not thrown, not settling). */
    int orbiting() {
        int n = 0;
        for (Plate p : plates) {
            if (p.state == State.ORBIT) {
                n++;
            }
        }
        return n;
    }

    /** True for a floor block this disk has torn up (or is about to): other floor paints keep off it. */
    boolean owns(Location block) {
        return claimed.contains(block) || holes.containsKey(block);
    }

    boolean hasThrown() {
        for (Plate p : plates) {
            if (p.state == State.THROWN) {
                return true;
            }
        }
        return false;
    }

    void start(World world, double floorY, Location core) {
        clear();
        this.world = world;
        this.floorY = floorY;
        this.diskCenter = core.clone();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.tilt = new Quaternionf().rotateY((float) random.nextDouble(Math.PI * 2)).rotateX(0.36f).rotateZ(0.12f);
        this.active = true;
        this.releasing = false;
        this.age = 0;
        this.tearCd = 60;
    }

    /**
     * Rip {@code count} floor plates up from around {@code near}, staggered so the crater tears open
     * outward rather than all at once.
     */
    void tear(Location near, int count, double within, double keepOut) {
        if (!active || world == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int made = 0;
        for (int attempt = 0; attempt < count * 6 && made < count && plates.size() < MAX_PLATES; attempt++) {
            double a = random.nextDouble(Math.PI * 2);
            double r = keepOut + Math.sqrt(random.nextDouble()) * Math.max(0.5, within - keepOut);
            Block block = floorBlock(near.getX() + Math.cos(a) * r, near.getZ() + Math.sin(a) * r);
            if (block == null) {
                continue;
            }
            Location home = block.getLocation();
            if (!claimed.add(home)) {
                continue;
            }
            plates.add(new Plate(block, made * 2 + random.nextInt(2), r));
            made++;
        }
    }

    /**
     * Throw one disk plate at {@code target}; it lands after {@code ticks}. Returns the landing spot,
     * or null when the disk has nothing to throw.
     */
    Location fling(Location landing, int ticks) {
        if (!active || releasing) {
            return null;
        }
        Plate best = null;
        for (Plate p : plates) {
            if (p.state == State.ORBIT && (best == null || p.radius > best.radius)) {
                best = p;
            }
        }
        if (best == null) {
            return null;
        }
        best.state = State.THROWN;
        best.t = 0;
        best.flight = Math.max(12, ticks);
        best.from = new Vector3f(best.pos);
        best.landing = landing.clone();
        best.to = local(best.home, landing.clone().add(0, 0.45, 0));
        HollowProps.glow(best.display, HollowProps.HOT);
        world.playSound(diskCenter, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.5f);
        return landing;
    }

    /** The star has died: every plate falls back into its own hole, one after another. */
    void release() {
        if (!active || world == null) {
            return;
        }
        releasing = true;
        int i = 0;
        for (Plate p : plates) {
            if (p.state == State.EATEN) {
                continue;
            }
            p.state = State.SETTLE;
            p.t = -(i * 3);
            p.from = new Vector3f(p.pos);
            HollowProps.glow(p.display, null);
            i++;
        }
    }

    void tick(Location core, Landing landing, Runnable swallowed) {
        if (!active || world == null) {
            return;
        }
        age++;
        if (core != null && !releasing) {
            // The disk swarms after the core instead of snapping to it (folds, the Nova).
            diskCenter.add(core.toVector().subtract(diskCenter.toVector()).multiply(0.22));
        }
        if (!releasing && core != null && --tearCd <= 0 && orbiting() < MAX_PLATES - 4) {
            tear(core, 2, 7.5, 2.0);
            tearCd = 70;
        }
        boolean push = age % PUSH_EVERY == 0;
        Iterator<Plate> it = plates.iterator();
        while (it.hasNext()) {
            Plate p = it.next();
            if (!p.display.isValid()) {
                restore(p.home);
                it.remove();
                continue;
            }
            p.t++;
            switch (p.state) {
                case LIFT -> tickLift(p);
                case ORBIT -> tickOrbit(p, push, swallowed);
                case THROWN -> tickThrown(p, landing);
                case LANDED -> tickLanded(p);
                case SETTLE -> tickSettle(p);
                default -> {
                }
            }
            if (p.state == State.EATEN && p.t > 4) {
                HollowProps.kill(p.display);
                it.remove();
            }
        }
        if (releasing && plates.isEmpty()) {
            clear();
        }
    }

    /* ------------------------------------------------------------------ plate life */

    private void tickLift(Plate p) {
        if (p.t < 0) {
            return;
        }
        if (p.t == 1) {
            paintHole(p.home);
            world.playSound(p.home, Sound.BLOCK_DEEPSLATE_BREAK, 0.9f, 0.55f);
            world.spawnParticle(Particle.BLOCK, p.home.clone().add(0.5, 1.05, 0.5), 10, 0.3, 0.05, 0.3, 0, p.data);
        }
        if (p.t == 2) {
            // Break loose: up, a little tumble, still the size of the block it was.
            p.pos.set(0.5f, 1.9f, 0.5f);
            p.q = new Quaternionf().rotateX((float) (p.spin * 0.8)).rotateZ((float) (p.spin * 0.5));
            HollowProps.push(p.display, HollowProps.cube(p.pos, p.q, 0.96f), 10);
        }
        if (p.t >= 14) {
            p.state = State.ORBIT;
            p.t = 0;
            p.from = new Vector3f(p.pos);
            HollowProps.glow(p.display, HollowProps.EMBER);
        }
    }

    private void tickOrbit(Plate p, boolean push, Runnable swallowed) {
        double speed = 0.085 * Math.pow(2.8 / Math.max(0.8, p.radius), 1.5);
        p.angle += speed;
        p.radius = Math.max(0.0, p.radius - 0.0045);
        p.tumble += p.spin * 0.05;
        if (p.radius < DISK_IN) {
            p.state = State.EATEN;
            p.t = 0;
            HollowProps.push(p.display, HollowProps.cube(local(p.home, diskCenter), new Quaternionf(), 0.001f), 3);
            world.playSound(diskCenter, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.5f);
            world.playSound(diskCenter, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.5f);
            if (swallowed != null) {
                swallowed.run();
            }
            return;
        }
        Color heat = p.radius < 1.8 ? HollowProps.HOT : p.radius < 2.8 ? HollowProps.GOLD : HollowProps.EMBER;
        if (heat != p.heat) {
            p.heat = heat;
            HollowProps.glow(p.display, heat);
        }
        if (!push) {
            return;
        }
        Vector3f slot = tilt.transform(new Vector3f(
                (float) (Math.cos(p.angle) * p.radius), (float) p.lift, (float) (Math.sin(p.angle) * p.radius)));
        Vector3f want = local(p.home, diskCenter).add(slot);
        float blend = Math.min(1f, p.t / 16f);
        p.pos.set(new Vector3f(p.from).lerp(want, blend * blend * (3f - 2f * blend)));
        p.q = new Quaternionf().rotateY((float) p.tumble).rotateX((float) (p.tumble * 0.7));
        float size = (float) (0.42 + 0.22 * Math.min(1.0, p.radius / DISK_OUT));
        HollowProps.push(p.display, HollowProps.cube(p.pos, p.q, size), PUSH_EVERY);
    }

    private void tickThrown(Plate p, Landing landing) {
        int wind = 8;
        if (p.t <= wind) {
            // Swung out to the rim of the disk first: the star winds up.
            float u = p.t / (float) wind;
            Vector3f out = new Vector3f(p.from).sub(local(p.home, diskCenter));
            if (out.lengthSquared() > 1.0e-4f) {
                out.normalize(1.6f * u);
            }
            p.pos.set(new Vector3f(p.from).add(out).add(0f, 1.2f * u, 0f));
            p.tumble += 0.3;
            p.q = new Quaternionf().rotateY((float) p.tumble).rotateX((float) (p.tumble * 0.8));
            HollowProps.push(p.display, HollowProps.cube(p.pos, p.q, 0.7f), 1);
            if (p.t == wind) {
                p.from = new Vector3f(p.pos);
                world.playSound(diskCenter, Sound.ITEM_TRIDENT_THROW, 1.2f, 0.5f);
                world.playSound(diskCenter, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.5f, 1.6f);
            }
            return;
        }
        int flight = p.flight - wind;
        float u = Math.min(1f, (p.t - wind) / (float) flight);
        float apex = 3.5f + p.from.distance(p.to) * 0.12f;
        p.pos.set(new Vector3f(p.from).lerp(p.to, u).add(0f, (float) Math.sin(u * Math.PI) * apex, 0f));
        p.tumble += 0.45;
        p.q = new Quaternionf().rotateY((float) p.tumble).rotateX((float) (p.tumble * 0.9));
        HollowProps.push(p.display, HollowProps.cube(p.pos, p.q, 0.9f), 1);
        if (u >= 1f) {
            p.state = State.LANDED;
            p.t = 0;
            // Buried a third deep and knocked flat, still glowing.
            p.pos.set(new Vector3f(p.to).add(0f, -0.55f, 0f));
            p.q = new Quaternionf().rotateY((float) p.tumble).rotateX(0.35f).rotateZ(0.2f);
            HollowProps.push(p.display, HollowProps.cube(p.pos, p.q, 1.0f), 1);
            if (landing != null && p.landing != null) {
                landing.land(p.landing, p.data);
            }
        }
    }

    private void tickLanded(Plate p) {
        if (p.t == 12) {
            HollowProps.glow(p.display, null);
        }
        if (p.t == 30) {
            HollowProps.push(p.display, HollowProps.cube(new Vector3f(p.pos).add(0f, -0.6f, 0f), p.q, 0.001f), 16);
        }
        if (p.t >= 47) {
            p.state = State.EATEN;
            p.t = 5;
        }
    }

    private void tickSettle(Plate p) {
        if (p.t < 0) {
            return;
        }
        if (p.t == 1) {
            // Lifted above its hole, turned square again...
            p.pos.set(0.5f, 2.6f, 0.5f);
            HollowProps.push(p.display, HollowProps.cube(p.pos, new Quaternionf(), 1.0f), 12);
        }
        if (p.t == 14) {
            // ...and dropped home.
            p.pos.set(0.5f, 0.5f, 0.5f);
            HollowProps.push(p.display, HollowProps.cube(p.pos, new Quaternionf(), 1.004f), 4);
        }
        if (p.t == 18) {
            restore(p.home);
            world.playSound(p.home, Sound.BLOCK_DEEPSLATE_PLACE, 1.0f, 0.6f);
            world.spawnParticle(Particle.BLOCK, p.home.clone().add(0.5, 1.02, 0.5), 8, 0.35, 0.02, 0.35, 0, p.data);
        }
        if (p.t >= 20) {
            p.state = State.EATEN;
            p.t = 5;
        }
    }

    /* ------------------------------------------------------------------ floor */

    private Block floorBlock(double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) Math.floor(floorY) + 1;
        for (int y = top; y >= top - 4; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            Block above = world.getBlockAt(bx, y + 1, bz);
            if (!above.isPassable() || above.isLiquid()) {
                continue;
            }
            Material type = block.getType();
            if (!type.isOccluding() || type == Material.BEDROCK || type == Material.BARRIER
                    || type == Material.SPAWNER || block.getState() instanceof Container) {
                continue;
            }
            return block;
        }
        return null;
    }

    private void paintHole(Location home) {
        holes.putIfAbsent(home, home.getBlock().getBlockData());
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(home) <= 96 * 96) {
                player.sendBlockChange(home, HOLE);
                painted.add(player.getUniqueId());
            }
        }
    }

    private void restore(Location home) {
        claimed.remove(home);
        if (holes.remove(home) == null || world == null) {
            return;
        }
        BlockData real = home.getBlock().getBlockData();
        for (UUID id : painted) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getWorld().equals(world)) {
                player.sendBlockChange(home, real);
            }
        }
    }

    /** Remove every plate and give every hole its real block back. Idempotent. */
    void clear() {
        for (Plate p : plates) {
            HollowProps.kill(p.display);
        }
        plates.clear();
        if (world != null) {
            for (Location home : new ArrayList<>(holes.keySet())) {
                restore(home);
            }
        }
        holes.clear();
        claimed.clear();
        painted.clear();
        active = false;
        releasing = false;
    }

    private static Vector3f local(Location home, Location world) {
        return new Vector3f(
                (float) (world.getX() - home.getX()),
                (float) (world.getY() - home.getY()),
                (float) (world.getZ() - home.getZ()));
    }

    /* ------------------------------------------------------------------ plate */

    private final class Plate {
        final Location home;
        final BlockData data;
        final BlockDisplay display;
        final Vector3f pos = new Vector3f(0.5f, 0.5f, 0.5f);
        final double spin;
        final double lift;
        State state = State.LIFT;
        int t;
        double angle;
        double radius;
        double tumble;
        Quaternionf q = new Quaternionf();
        Vector3f from = new Vector3f(0.5f, 0.5f, 0.5f);
        Vector3f to;
        Location landing;
        int flight;
        Color heat;

        Plate(Block block, int delay, double seedRadius) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            this.home = block.getLocation();
            this.data = block.getBlockData();
            this.t = -delay;
            this.spin = random.nextDouble(-1.0, 1.0);
            this.lift = random.nextDouble(-0.18, 0.18);
            this.angle = random.nextDouble(Math.PI * 2);
            this.radius = Math.min(DISK_OUT, 2.4 + seedRadius * 0.12 + random.nextDouble(0.0, 1.0));
            Location at = home.clone();
            at.setYaw(0f);
            at.setPitch(0f);
            this.display = world.spawn(at, BlockDisplay.class, d -> {
                d.setBlock(data);
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.FIXED);
                d.setViewRange(2.0f);
                d.setShadowRadius(0f);
                d.setShadowStrength(0f);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(0);
                d.setTeleportDuration(0);
                d.setTransformationMatrix(new Matrix4f().translate(-0.002f, -0.002f, -0.002f).scale(1.004f));
                instance.getKeys().tagBeamFx(d, instance.getInstanceId());
            });
        }
    }
}
