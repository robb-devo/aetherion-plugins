package de.aetherion.items.listener;

import de.aetherion.items.combat.ScriptedHits;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Echo Blade (echo_blade) — Afterimage Slash.
 * The hit rings like struck crystal and a spectral copy of the blade fades in beside the target, facing the caster.
 * It draws back while a faint crescent pre-draws the cut it is about to make and a lock ring tightens on the target.
 * Then it swings: each frame leaves a fading ghost of the blade behind it and a violet trail along the edge, and as
 * it passes through the target the cut flashes as a white crescent, the delayed hit lands, and the echo dissolves into
 * soul wisps. Successive echoes alternate sides. Delay, 40% damage and the gate between echoes match the old echo.
 */
public final class EchoBladeAfterimage {

    static final int DELAY = 8;
    private static final int LINGER = 8;
    private static final float SCALE = 1.8f;
    /** Sword sprite half-length along its diagonal, in blocks at scale 1. */
    private static final float HALF = 0.64f;
    private static final double WIND_FROM = Math.toRadians(75.0);
    private static final double WIND_TO = Math.toRadians(50.0);
    private static final double[] SWING = {Math.toRadians(130.0), Math.toRadians(205.0), Math.toRadians(265.0)};
    private static final double CUT_FROM = Math.toRadians(100.0);
    private static final double CUT_TO = Math.toRadians(255.0);
    private static final int CRESCENT = 7;
    /** Past this many live displays (all players), echoes skip ghost frames and the crescent flash. */
    private static final int MAX_LIVE = 120;

    private static final Color GHOST = Color.fromRGB(200, 160, 255);
    private static final Color PALE = Color.fromRGB(240, 230, 255);
    private static final Color DEEP = Color.fromRGB(120, 70, 210);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    private static final Display.Brightness DIM = new Display.Brightness(8, 8);
    private static final BlockData CUT = Material.WHITE_CONCRETE.createBlockData();

    /** Local sprite axes of a sword in {@link ItemDisplay.ItemDisplayTransform#NONE}: tip diagonal, cross, face. */
    private static final Vector3f SPRITE_TIP = new Vector3f(1f, 1f, 0f).normalize();
    private static final Vector3f SPRITE_CROSS = new Vector3f(-1f, 1f, 0f).normalize();
    private static final Vector3f SPRITE_FACE = new Vector3f(0f, 0f, 1f);

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Boolean> FLIP = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final Player player;
    private final LivingEntity victim;
    private final World world;
    private final double damage;
    private final Vector side;
    private final Vector face;
    private final Vector up = new Vector(0, 1, 0);
    private final double pivotOffset;
    private final List<Frame> frames = new ArrayList<>();
    private final List<BlockDisplay> crescent = new ArrayList<>();
    private ItemDisplay blade;
    private Location chest;
    private double angle = WIND_FROM;
    private int tick;

    /** Plugin disable: removes every ghost blade, afterimage and crescent still showing. */
    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
        FLIP.clear();
    }

    /** Starts the echo right after the real hit; the delayed {@code echoDamage} lands {@link #DELAY} ticks later. */
    static void cast(JavaPlugin plugin, Player player, LivingEntity victim, double echoDamage) {
        new EchoBladeAfterimage(plugin, player, victim, echoDamage).start();
    }

    private EchoBladeAfterimage(JavaPlugin plugin, Player player, LivingEntity victim, double echoDamage) {
        this.plugin = plugin;
        this.player = player;
        this.victim = victim;
        this.world = victim.getWorld();
        this.damage = echoDamage;
        Vector toward = victim.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (toward.lengthSquared() < 1.0E-4) {
            toward = player.getLocation().getDirection().setY(0);
        }
        if (toward.lengthSquared() < 1.0E-4) {
            toward = new Vector(0, 0, 1);
        }
        toward.normalize();
        boolean flip = FLIP.merge(player.getUniqueId(), Boolean.TRUE, (a, b) -> !a);
        Vector right = new Vector(-toward.getZ(), 0, toward.getX());
        this.side = flip ? right : right.multiply(-1);
        this.face = toward.clone().multiply(-1);
        this.pivotOffset = 0.75 + victim.getWidth() * 0.5;
    }

    private void start() {
        chest = victim.getLocation().add(0, victim.getHeight() * 0.55, 0);
        world.spawnParticle(Particle.ENCHANTED_HIT, chest, 6, 0.2, 0.25, 0.2, 0.05);
        world.playSound(chest, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.55f, 1.8f);
        blade = spawnBlade(LIT, GHOST);
        if (blade != null) {
            easeItem(blade, pose(angle, 0.001f), 0);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || victim.getWorld() != world) {
                    clearAll();
                    cancel();
                    return;
                }
                if (step()) {
                    clearAll();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean step() {
        tick++;
        if (victim.isValid() && !victim.isDead()) {
            chest = victim.getLocation().add(0, victim.getHeight() * 0.55, 0);
        }
        Location pivot = pivot();
        if (blade != null && blade.isValid()) {
            blade.teleport(pivot);
        }

        if (tick <= 5) {
            telegraph(pivot);
        } else if (tick <= DELAY) {
            swing(pivot, tick - 6);
        } else {
            dissolve(pivot, tick - DELAY);
        }
        tickFrames();
        return tick >= DELAY + LINGER;
    }

    // ------------------------------------------------------------------ beats

    /** The echo fades in, draws back, pre-draws its cut and locks onto the target. */
    private void telegraph(Location pivot) {
        double t = tick / 5.0;
        angle = WIND_FROM + (WIND_TO - WIND_FROM) * t;
        float grow = (float) Math.min(1.0, tick / 3.0);
        easeItem(blade, pose(angle, SCALE * grow), 1);

        Particle.DustOptions faint = new Particle.DustOptions(DEEP, 0.5f);
        double reach = tipReach() * 0.95;
        int points = 16;
        int shown = (int) Math.ceil(points * t);
        for (int i = 0; i < shown; i++) {
            double a = CUT_FROM + (CUT_TO - CUT_FROM) * i / (points - 1);
            world.spawnParticle(Particle.DUST, onArc(pivot, a, reach), 1, 0, 0, 0, 0, faint);
        }
        if (tick % 2 == 1) {
            double r = 1.1 - 0.6 * t + victim.getWidth() * 0.3;
            Particle.DustOptions lock = new Particle.DustOptions(GHOST, 0.7f);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12 + tick * 0.3;
                world.spawnParticle(Particle.DUST, chest.clone().add(Math.cos(a) * r, -0.1, Math.sin(a) * r), 1,
                        0, 0, 0, 0, lock);
            }
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, onArc(pivot, angle, tipReach()), 1, 0.03, 0.03, 0.03, 0.0);
        if (tick == 1) {
            world.playSound(chest, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.6f, 1.5f);
        }
        if (tick == 4) {
            world.playSound(chest, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.9f);
        }
    }

    /** Three frames of swing; each leaves a ghost behind and trails the edge. The last frame is the hit. */
    private void swing(Location pivot, int frame) {
        double from = angle;
        angle = SWING[frame];
        boolean crowded = LIVE.size() > MAX_LIVE;
        if (!crowded) {
            ItemDisplay ghost = spawnBlade(DIM, DEEP);
            if (ghost != null) {
                ghost.teleport(pivot);
                easeItem(ghost, pose(from, SCALE), 0);
                frames.add(new Frame(ghost, from));
            }
        }
        easeItem(blade, pose(angle, SCALE), 1);
        trail(pivot, from, angle);

        if (frame == 0) {
            world.playSound(chest, Sound.ENTITY_PHANTOM_SWOOP, 0.45f, 1.8f);
            world.playSound(chest, Sound.ITEM_TRIDENT_THROW, 0.45f, 1.5f);
        }
        if (frame == 2) {
            impact(pivot, crowded);
        }
    }

    /** Violet edge trail between two swing angles: brightest at the edge, fainter toward the hilt. */
    private void trail(Location pivot, double from, double to) {
        double reach = tipReach();
        Particle.DustOptions edge = new Particle.DustOptions(PALE, 1.0f);
        Particle.DustOptions body = new Particle.DustOptions(GHOST, 0.8f);
        Particle.DustOptions inner = new Particle.DustOptions(DEEP, 0.6f);
        int steps = Math.max(4, (int) (Math.abs(to - from) / Math.toRadians(6.0)));
        for (int i = 0; i <= steps; i++) {
            double a = from + (to - from) * i / steps;
            world.spawnParticle(Particle.DUST, onArc(pivot, a, reach * 0.97), 1, 0, 0, 0, 0, edge);
            world.spawnParticle(Particle.DUST, onArc(pivot, a, reach * 0.78), 1, 0, 0, 0, 0, body);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.DUST, onArc(pivot, a, reach * 0.58), 1, 0, 0, 0, 0, inner);
            }
        }
        world.spawnParticle(Particle.SOUL, onArc(pivot, to, reach * 0.9), 1, 0.05, 0.05, 0.05, 0.01);
    }

    /** The delayed hit: crescent flash across the target, crystal ring, damage through ScriptedHits. */
    private void impact(Location pivot, boolean crowded) {
        if (!crowded) {
            double reach = tipReach() * 0.8;
            double step = (CUT_TO - CUT_FROM) / CRESCENT;
            float length = (float) (step * reach * 1.08);
            for (int i = 0; i < CRESCENT; i++) {
                double a = CUT_FROM + step * (i + 0.5);
                float width = (float) (0.2 * Math.sin(Math.PI * (i + 0.5) / CRESCENT) + 0.03);
                BlockDisplay tile = spawnCut(pivot, cutTile(a, reach, length, width));
                if (tile != null) {
                    crescent.add(tile);
                }
            }
        }

        boolean alive = victim.isValid() && !victim.isDead();
        Location at = chest.clone();
        Vector down = up.clone().multiply(-1);
        world.spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.ENCHANTED_HIT, at, 14, 0.3, 0.35, 0.3, 0.25);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 8, 0.2, 0.25, 0.2, 0.06);
        world.spawnParticle(Particle.DUST, at, 10, 0.3, 0.35, 0.3, 0, new Particle.DustOptions(PALE, 1.3f));
        for (int i = 0; i < 4; i++) {
            Vector dir = down.clone().add(side.clone().multiply(-0.4 + i * 0.25)).normalize();
            world.spawnParticle(Particle.END_ROD, at, 0, dir.getX(), dir.getY(), dir.getZ(), 0.18);
        }

        float volume = alive ? 1.0f : 0.5f;
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f * volume, 1.45f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f * volume, 1.35f);
        world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.5f * volume, 1.7f);
        if (!alive) {
            return;
        }
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.3f);
        strike();
    }

    /**
     * The echo lands inside the real hit's invulnerability window, so it clears the window for its own hit and then
     * restores it; the caster's next real swing keeps its normal timing.
     */
    private void strike() {
        int iframes = victim.getNoDamageTicks();
        double last = victim.getLastDamage();
        victim.setNoDamageTicks(0);
        ScriptedHits.run(() -> victim.damage(damage, player));
        if (victim.isValid() && !victim.isDead()) {
            victim.setNoDamageTicks(iframes);
            victim.setLastDamage(last);
        }
    }

    /** {@code t} counts from 1 after the hit: follow-through, crescent cools and thins, the echo breaks into wisps. */
    private void dissolve(Location pivot, int t) {
        if (t == 1) {
            angle = Math.toRadians(285.0);
            if (blade != null && blade.isValid()) {
                blade.setGlowColorOverride(DEEP);
                blade.setBrightness(DIM);
            }
            easeItem(blade, pose(angle, 0.001f), 5);
            for (BlockDisplay tile : crescent) {
                if (tile.isValid()) {
                    tile.setGlowColorOverride(DEEP);
                }
            }
        }
        if (t == 2) {
            double reach = tipReach() * 0.8;
            double step = (CUT_TO - CUT_FROM) / CRESCENT;
            float length = (float) (step * reach * 1.08);
            for (int i = 0; i < crescent.size(); i++) {
                ease(crescent.get(i), cutTile(CUT_FROM + step * (i + 0.5), reach, length * 1.1f, 0.001f), 5);
            }
        }
        if (t <= 5) {
            world.spawnParticle(Particle.SOUL, chest.clone().add(0, 0.2 * t, 0), 1, 0.25, 0.2, 0.25, 0.02);
            world.spawnParticle(Particle.DUST, onArc(pivot, angle, tipReach() * 0.6), 1, 0.2, 0.2, 0.2, 0,
                    new Particle.DustOptions(GHOST, 0.6f));
        }
        if (t == 3) {
            world.playSound(chest, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.3f, 1.9f);
        }
        if (t == 7) {
            removeCrescent();
        }
    }

    private void tickFrames() {
        Iterator<Frame> it = frames.iterator();
        while (it.hasNext()) {
            Frame frame = it.next();
            frame.age++;
            if (frame.age == 2) {
                easeItem(frame.display, pose(frame.angle, 0.001f), 3);
            }
            if (frame.age >= 5) {
                discard(frame.display);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ geometry

    /** The ghost's hand: beside the target on the swing side, pulled toward the caster so the cut reads in front. */
    private Location pivot() {
        Location at = chest.clone()
                .add(side.clone().multiply(pivotOffset))
                .add(face.clone().multiply(0.55))
                .add(0, -0.1, 0);
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    private double tipReach() {
        return 1.19 * SCALE;
    }

    /** Blade direction in the cut plane; 180° points from the pivot straight through the target. */
    private Vector dir(double a) {
        return side.clone().multiply(Math.cos(a)).add(up.clone().multiply(Math.sin(a)));
    }

    private Location onArc(Location pivot, double a, double r) {
        return pivot.clone().add(dir(a).multiply(r));
    }

    /** Sword sprite laid in the cut plane, flat side to the caster, hilt at the pivot, tip along {@code a}. */
    private Transformation pose(double a, float scale) {
        Vector d = dir(a);
        Vector3f tip = new Vector3f((float) d.getX(), (float) d.getY(), (float) d.getZ());
        Vector3f normal = new Vector3f((float) face.getX(), (float) face.getY(), (float) face.getZ());
        Vector3f cross = new Vector3f(normal).cross(tip);
        Matrix3f target = new Matrix3f(tip, cross, normal);
        Matrix3f local = new Matrix3f(SPRITE_TIP, SPRITE_CROSS, SPRITE_FACE).transpose();
        Quaternionf rot = new Quaternionf().setFromNormalized(target.mul(local));
        float s = Math.max(0.001f, scale);
        Vector3f center = new Vector3f(tip).mul(HALF * 0.86f * SCALE);
        return new Transformation(center, rot, new Vector3f(s, s, s), new Quaternionf());
    }

    /** One crescent tile tangent to the cut arc: long side along the arc, width radial, paper-thin toward the caster. */
    private Transformation cutTile(double a, double r, float length, float width) {
        Vector radial = dir(a);
        Vector tangent = side.clone().multiply(-Math.sin(a)).add(up.clone().multiply(Math.cos(a)));
        Vector3f x = new Vector3f((float) tangent.getX(), (float) tangent.getY(), (float) tangent.getZ());
        Vector3f y = new Vector3f((float) radial.getX(), (float) radial.getY(), (float) radial.getZ());
        Vector3f z = new Vector3f(x).cross(y);
        Quaternionf rot = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
        float l = Math.max(0.001f, length);
        float w = Math.max(0.001f, width);
        float th = 0.03f;
        Vector3f half = new Quaternionf(rot).transform(new Vector3f(l / 2f, w / 2f, th / 2f));
        Vector3f center = new Vector3f(y).mul((float) r);
        return new Transformation(center.sub(half), rot, new Vector3f(l, w, th), new Quaternionf());
    }

    // ------------------------------------------------------------------ displays

    private ItemDisplay spawnBlade(Display.Brightness brightness, Color glow) {
        try {
            ItemDisplay display = world.spawn(pivot(), ItemDisplay.class, spawned -> {
                spawned.setItemStack(new ItemStack(Material.NETHERITE_SWORD));
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                spawned.setPersistent(false);
                spawned.setBrightness(brightness);
                spawned.setTeleportDuration(1);
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(glow);
                spawned.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private BlockDisplay spawnCut(Location pivot, Transformation shape) {
        try {
            BlockDisplay display = world.spawn(pivot, BlockDisplay.class, spawned -> {
                spawned.setBlock(CUT);
                spawned.setPersistent(false);
                spawned.setBrightness(LIT);
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(PALE);
                spawned.setInterpolationDuration(0);
                spawned.setTransformation(shape);
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void easeItem(ItemDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static void ease(BlockDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private void removeCrescent() {
        for (BlockDisplay tile : crescent) {
            discard(tile);
        }
        crescent.clear();
    }

    private void clearAll() {
        discard(blade);
        blade = null;
        for (Frame frame : frames) {
            discard(frame.display);
        }
        frames.clear();
        removeCrescent();
    }

    private static void discard(Display display) {
        if (display == null) {
            return;
        }
        LIVE.remove(display);
        if (display.isValid()) {
            display.remove();
        }
    }

    private static final class Frame {
        final ItemDisplay display;
        final double angle;
        int age;

        Frame(ItemDisplay display, double angle) {
            this.display = display;
            this.angle = angle;
        }
    }
}
