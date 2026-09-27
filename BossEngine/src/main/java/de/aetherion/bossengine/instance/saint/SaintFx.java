package de.aetherion.bossengine.instance.saint;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.title.Title;

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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Shared stage context: one fixed anchor (stage center, floor surface) that every display
 * is spawned on. Displays never teleport; all motion is transformation interpolation
 * relative to the anchor, which keeps the whole show perfectly smooth on the client.
 */
final class SaintFx {

    static final float VIEW_RANGE = 3.5f;
    static final double AUDIENCE_RADIUS = 72.0;
    static final double COMBAT_RADIUS = 30.0;

    private final BossInstance instance;
    private final Location anchor;
    private final World world;
    /** Loose transient displays owned by props; purged on clear. */
    private final List<Display> loose = new ArrayList<>();

    SaintFx(BossInstance instance, Location anchor) {
        this.instance = instance;
        this.anchor = anchor.clone();
        this.anchor.setYaw(0f);
        this.anchor.setPitch(0f);
        this.world = anchor.getWorld();
    }

    BossInstance instance() {
        return instance;
    }

    Location anchor() {
        return anchor.clone();
    }

    World world() {
        return world;
    }

    Location at(Vector3f stage) {
        return new Location(world, anchor.getX() + stage.x, anchor.getY() + stage.y, anchor.getZ() + stage.z);
    }

    Location at(float x, float y, float z) {
        return new Location(world, anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z);
    }

    Vector3f stage(Location world) {
        return new Vector3f(
                (float) (world.getX() - anchor.getX()),
                (float) (world.getY() - anchor.getY()),
                (float) (world.getZ() - anchor.getZ()));
    }

    /* ------------------------------------------------------------------ displays */

    BlockDisplay block(Material material, Color glow, int brightness) {
        return block(material.createBlockData(), glow, brightness);
    }

    BlockDisplay block(BlockData data, Color glow, int brightness) {
        return world.spawn(anchor, BlockDisplay.class, d -> {
            d.setBlock(data);
            prepare(d, glow, brightness);
            d.setTransformationMatrix(new Matrix4f().scale(0.001f));
        });
    }

    ItemDisplay item(ItemStack stack, int brightness) {
        return world.spawn(anchor, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            prepare(d, null, brightness);
            d.setTransformationMatrix(new Matrix4f().scale(0.001f));
        });
    }

    /** A transient display the fx context removes on clear even if its owner forgets. */
    BlockDisplay looseBlock(Material material, Color glow, int brightness) {
        BlockDisplay d = block(material, glow, brightness);
        loose.add(d);
        return d;
    }

    private void prepare(Display d, Color glow, int brightness) {
        d.setPersistent(false);
        d.setGravity(false);
        d.setBillboard(Display.Billboard.FIXED);
        d.setViewRange(VIEW_RANGE);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(0);
        d.setTeleportDuration(0);
        int b = Math.max(0, Math.min(15, brightness));
        d.setBrightness(new Display.Brightness(b, b));
        if (glow != null) {
            d.setGlowing(true);
            d.setGlowColorOverride(glow);
        }
        instance.getKeys().tagBeamFx(d, instance.getInstanceId());
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

    static void kill(Display d) {
        if (d != null && d.isValid()) {
            d.remove();
        }
    }

    /** Unit block centered on {@code center}, sized, flat on the XZ plane (for floor decals). */
    static Matrix4f flat(Vector3f center, float sx, float sy, float sz, float yaw) {
        return new Matrix4f()
                .translation(center)
                .rotateY(yaw)
                .scale(sx, sy, sz)
                .translate(-0.5f, -0.5f, -0.5f);
    }

    /** A thin box stretched from a to b. */
    static Matrix4f beam(Vector3f a, Vector3f b, float width) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1e-4f) {
            return new Matrix4f().translation(a).scale(0.001f);
        }
        return new Matrix4f()
                .translation(a)
                .rotate(new org.joml.Quaternionf().rotationTo(0f, 1f, 0f, dir.x / len, dir.y / len, dir.z / len))
                .scale(width, len, width)
                .translate(-0.5f, 0f, -0.5f);
    }

    static Matrix4f gone(Vector3f at) {
        return new Matrix4f().translation(at).scale(0.001f);
    }

    void purgeLoose() {
        for (Iterator<Display> it = loose.iterator(); it.hasNext(); ) {
            Display d = it.next();
            if (d == null || !d.isValid()) {
                it.remove();
            }
        }
    }

    void clear() {
        for (Display d : loose) {
            kill(d);
        }
        loose.clear();
    }

    /* ------------------------------------------------------------------ players */

    /** Everyone watching: sound, time-of-day, titles. */
    List<Player> audience() {
        List<Player> out = new ArrayList<>();
        if (world == null) {
            return out;
        }
        double r2 = AUDIENCE_RADIUS * AUDIENCE_RADIUS;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(anchor) <= r2) {
                out.add(p);
            }
        }
        return out;
    }

    private List<Player> targetCache = List.of();
    private int targetCacheTick = Integer.MIN_VALUE;

    /** Players the Saint may hurt: vulnerable, on or around the stage. Cached per server tick. */
    List<Player> targets() {
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (now != targetCacheTick) {
            targetCache = scanTargets();
            targetCacheTick = now;
        }
        return targetCache;
    }

    private List<Player> scanTargets() {
        List<Player> out = new ArrayList<>();
        if (world == null) {
            return out;
        }
        for (Player p : world.getPlayers()) {
            if (!instance.isCombatTarget(p) || p.isDead()) {
                continue;
            }
            Location l = p.getLocation();
            double dx = l.getX() - anchor.getX();
            double dz = l.getZ() - anchor.getZ();
            double dy = l.getY() - anchor.getY();
            if (dx * dx + dz * dz <= COMBAT_RADIUS * COMBAT_RADIUS && dy > -10 && dy < 40) {
                out.add(p);
            }
        }
        return out;
    }

    Player nearestTarget(Vector3f from) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : targets()) {
            double d = stage(p.getLocation()).distanceSquared(from);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /* ------------------------------------------------------------------ sound */

    void sound(Vector3f at, Sound sound, float volume, float pitch) {
        world.playSound(at(at), sound, SoundCategory.HOSTILE, volume, pitch);
    }

    /** Non-positional, heard identically by every audience member (score, bells, silence breaks). */
    void score(Sound sound, float volume, float pitch) {
        for (Player p : audience()) {
            p.playSound(p.getLocation(), sound, SoundCategory.RECORDS, volume, pitch);
        }
    }

    void particle(Particle particle, Vector3f at, int count, double spread, double speed) {
        world.spawnParticle(particle, at(at), count, spread, spread, spread, speed);
    }

    void dust(Vector3f at, Color color, float size, int count, double spread) {
        world.spawnParticle(Particle.DUST, at(at), count, spread, spread, spread, 0,
                new Particle.DustOptions(color, size));
    }

    void title(String main, String sub, int fadeIn, int stay, int fadeOut) {
        Title title = Title.title(
                TextUtil.component(main),
                TextUtil.component(sub),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L)));
        for (Player p : audience()) {
            p.showTitle(title);
        }
    }

    void actionBar(String text) {
        for (Player p : audience()) {
            p.sendActionBar(TextUtil.component(text));
        }
    }

    void say(String text) {
        for (Player p : audience()) {
            p.sendMessage(TextUtil.component(text));
        }
    }
}
