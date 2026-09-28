package de.aetherion.bossengine.instance.worldeater;

import de.aetherion.bossengine.util.BossKeys;
import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
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
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One fixed stage anchor that every display of a scene is spawned on. Displays never teleport:
 * all motion is transformation interpolation relative to the anchor, so the whole show stays
 * smooth on the client and a single metadata packet moves a piece.
 *
 * <p>Every display spawned here is tracked, so {@link #clear()} can never leak one. Transforms
 * are always built from explicit translation / rotation / scale (never a raw matrix), so the
 * server never SVD-decomposes and rotations cannot flip between frames.
 */
public final class WeFx {

    public static final float VIEW_RANGE = 3.5f;

    private final Location anchor;
    private final World world;
    private final BossKeys keys;
    private final UUID owner;
    private final List<Display> spawned = new ArrayList<>();
    private double audienceRadius = 80.0;
    private double combatRadius = 36.0;
    private double combatBelow = 14.0;
    private double combatAbove = 40.0;
    private Predicate<Player> combatFilter = p -> true;

    public WeFx(Location anchor, BossKeys keys, UUID owner) {
        this.anchor = anchor.clone();
        this.anchor.setYaw(0f);
        this.anchor.setPitch(0f);
        this.world = anchor.getWorld();
        this.keys = keys;
        this.owner = owner;
    }

    public WeFx audience(double radius) {
        this.audienceRadius = radius;
        return this;
    }

    public WeFx combat(double radius, double below, double above) {
        this.combatRadius = radius;
        this.combatBelow = below;
        this.combatAbove = above;
        return this;
    }

    public WeFx combatFilter(Predicate<Player> filter) {
        this.combatFilter = filter == null ? p -> true : filter;
        return this;
    }

    public Location anchor() {
        return anchor.clone();
    }

    public World world() {
        return world;
    }

    public Location at(Vector3f stage) {
        return new Location(world, anchor.getX() + stage.x, anchor.getY() + stage.y, anchor.getZ() + stage.z);
    }

    public Location at(float x, float y, float z) {
        return new Location(world, anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z);
    }

    public Vector3f stage(Location loc) {
        return new Vector3f(
                (float) (loc.getX() - anchor.getX()),
                (float) (loc.getY() - anchor.getY()),
                (float) (loc.getZ() - anchor.getZ()));
    }

    /* ------------------------------------------------------------------ displays */

    public BlockDisplay block(Material material, Color glow, int brightness) {
        return block(material.createBlockData(), glow, brightness);
    }

    public BlockDisplay block(BlockData data, Color glow, int brightness) {
        if (world == null) {
            return null;
        }
        BlockDisplay display = world.spawn(anchor, BlockDisplay.class, d -> {
            d.setBlock(data);
            prepare(d, glow, brightness);
        });
        spawned.add(display);
        return display;
    }

    public ItemDisplay item(ItemStack stack, int brightness) {
        if (world == null) {
            return null;
        }
        ItemDisplay display = world.spawn(anchor, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            prepare(d, null, brightness);
        });
        spawned.add(display);
        return display;
    }

    public TextDisplay text(Vector3f at, String text, float viewRange) {
        if (world == null) {
            return null;
        }
        TextDisplay display = world.spawn(at(at), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            d.setLineWidth(240);
            d.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            d.setViewRange(viewRange);
            d.text(TextUtil.component(text));
            if (keys != null) {
                keys.tagBeamFx(d, owner);
            }
        });
        spawned.add(display);
        return display;
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
        d.setTransformation(gone(new Vector3f()));
        if (glow != null) {
            d.setGlowing(true);
            d.setGlowColorOverride(glow);
        }
        if (keys != null) {
            keys.tagBeamFx(d, owner);
        }
    }

    public static void push(Display d, Transformation t, int interp) {
        if (d == null || !d.isValid()) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, interp));
        d.setTransformation(t);
    }

    public static void glow(Display d, Color color) {
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

    public static void brightness(Display d, int level) {
        if (d != null && d.isValid()) {
            int b = Math.max(0, Math.min(15, level));
            d.setBrightness(new Display.Brightness(b, b));
        }
    }

    public static void material(BlockDisplay d, Material material) {
        if (d != null && d.isValid()) {
            d.setBlock(material.createBlockData());
        }
    }

    public void kill(Display d) {
        if (d == null) {
            return;
        }
        spawned.remove(d);
        if (d.isValid()) {
            d.remove();
        }
    }

    /** Drop references to displays that are already gone (chunk unload, external removal). */
    public void purge() {
        for (Iterator<Display> it = spawned.iterator(); it.hasNext(); ) {
            Display d = it.next();
            if (d == null || !d.isValid()) {
                it.remove();
            }
        }
    }

    public int count() {
        return spawned.size();
    }

    public void clear() {
        for (Display d : spawned) {
            if (d != null && d.isValid()) {
                d.remove();
            }
        }
        spawned.clear();
    }

    /* ------------------------------------------------------------------ transforms */

    /** A box of {@code size} centered on {@code center}, rotated by {@code rot} about its center. */
    public static Transformation box(Vector3f center, Vector3f size, Quaternionf rot) {
        Vector3f corner = new Vector3f(size).mul(-0.5f);
        rot.transform(corner);
        return new Transformation(new Vector3f(center).add(corner), new Quaternionf(rot), new Vector3f(size), new Quaternionf());
    }

    public static Transformation box(Vector3f center, Vector3f size) {
        return new Transformation(new Vector3f(center).sub(new Vector3f(size).mul(0.5f)),
                new Quaternionf(), new Vector3f(size), new Quaternionf());
    }

    public static Transformation cube(Vector3f center, float size, Quaternionf rot) {
        return box(center, new Vector3f(size), rot);
    }

    /** A flat slab lying on the XZ plane, yawed about Y. */
    public static Transformation flat(Vector3f center, float sx, float sy, float sz, float yaw) {
        return box(center, new Vector3f(sx, sy, sz), new Quaternionf().rotateY(yaw));
    }

    /** A thin square rod from a to b. */
    public static Transformation beam(Vector3f a, Vector3f b, float width) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1e-4f) {
            return gone(a);
        }
        Quaternionf rot = new Quaternionf().rotationTo(0f, 1f, 0f, dir.x / len, dir.y / len, dir.z / len);
        Vector3f corner = rot.transform(new Vector3f(-width * 0.5f, 0f, -width * 0.5f));
        return new Transformation(new Vector3f(a).add(corner), rot, new Vector3f(width, len, width), new Quaternionf());
    }

    /** A rod from a to b whose cross-section is {@code width} x {@code depth}, rolled so depth faces {@code up}. */
    public static Transformation plank(Vector3f a, Vector3f b, float width, float depth, org.joml.Vector3fc up) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1e-4f) {
            return gone(a);
        }
        Quaternionf rot = WeMath.frame(dir, up);
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        return box(mid, new Vector3f(width, depth, len), rot);
    }

    public static Transformation gone(Vector3f at) {
        return new Transformation(new Vector3f(at), new Quaternionf(), new Vector3f(0.001f), new Quaternionf());
    }

    /* ------------------------------------------------------------------ players */

    /** Everyone close enough to watch: sounds, titles, sky. */
    public List<Player> audience() {
        List<Player> out = new ArrayList<>();
        if (world == null) {
            return out;
        }
        double r2 = audienceRadius * audienceRadius;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(anchor) <= r2) {
                out.add(p);
            }
        }
        return out;
    }

    private List<Player> targetCache = List.of();
    private int targetCacheTick = Integer.MIN_VALUE;

    /** Players the scene may hurt: vulnerable and inside the combat cylinder. Cached per server tick. */
    public List<Player> targets() {
        int now = Bukkit.getCurrentTick();
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
            if (!vulnerable(p) || !combatFilter.test(p)) {
                continue;
            }
            Location l = p.getLocation();
            double dx = l.getX() - anchor.getX();
            double dz = l.getZ() - anchor.getZ();
            double dy = l.getY() - anchor.getY();
            if (dx * dx + dz * dz <= combatRadius * combatRadius && dy > -combatBelow && dy < combatAbove) {
                out.add(p);
            }
        }
        return out;
    }

    public static boolean vulnerable(Player p) {
        return p != null && p.isValid() && !p.isDead()
                && p.getGameMode() != GameMode.CREATIVE
                && p.getGameMode() != GameMode.SPECTATOR;
    }

    public Player nearestTarget(Vector3f from) {
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

    public Player farthestTarget(Vector3f from) {
        Player best = null;
        double bestD = -1;
        for (Player p : targets()) {
            double d = stage(p.getLocation()).distanceSquared(from);
            if (d > bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /* ------------------------------------------------------------------ sound */

    public void sound(Vector3f at, Sound sound, float volume, float pitch) {
        if (world != null) {
            world.playSound(at(at), sound, SoundCategory.HOSTILE, volume, pitch);
        }
    }

    /** Non-positional, heard identically by every audience member (score, stingers, silence breaks). */
    public void score(Sound sound, float volume, float pitch) {
        for (Player p : audience()) {
            p.playSound(p.getLocation(), sound, SoundCategory.RECORDS, volume, pitch);
        }
    }

    /**
     * Heard from the direction of {@code from} even when it is far above or away: played on each
     * listener's side of the source, so huge sources still read as directional and loud.
     */
    public void scoreFrom(Vector3f from, Sound sound, float volume, float pitch) {
        Location src = at(from);
        for (Player p : audience()) {
            Location ear = p.getEyeLocation();
            Vector to = src.toVector().subtract(ear.toVector());
            double d = to.length();
            Location play = d > 8.0 ? ear.clone().add(to.multiply(8.0 / d)) : src;
            p.playSound(play, sound, SoundCategory.HOSTILE, volume, pitch);
        }
    }

    public void silence() {
        for (Player p : audience()) {
            p.stopAllSounds();
        }
    }

    /* ------------------------------------------------------------------ particles */

    public void particle(Particle particle, Vector3f at, int count, double spread, double speed) {
        if (world != null) {
            world.spawnParticle(particle, at(at), count, spread, spread, spread, speed, null, true);
        }
    }

    public void particle(Particle particle, Vector3f at, int count, double sx, double sy, double sz, double speed) {
        if (world != null) {
            world.spawnParticle(particle, at(at), count, sx, sy, sz, speed, null, true);
        }
    }

    public void dust(Vector3f at, Color color, float size, int count, double spread) {
        if (world != null) {
            world.spawnParticle(Particle.DUST, at(at), count, spread, spread, spread, 0,
                    new Particle.DustOptions(color, size), true);
        }
    }

    public void blockDust(Vector3f at, Material material, int count, double spread) {
        if (world != null) {
            world.spawnParticle(Particle.BLOCK, at(at), count, spread, spread * 0.5, spread, 0,
                    material.createBlockData(), true);
        }
    }

    /** Dust that sifts down slowly off something heavy (FALLING_DUST needs block data). */
    public void fallingDust(Vector3f at, Material material, int count, double spread) {
        if (world != null) {
            world.spawnParticle(Particle.FALLING_DUST, at(at), count, spread, spread * 0.3, spread, 0,
                    material.createBlockData(), true);
        }
    }

    /** A particle that flies from {@code from} toward {@code to} (directional spawn, count 0). */
    public void stream(Particle particle, Vector3f from, Vector3f to, double speed) {
        if (world == null) {
            return;
        }
        Vector3f d = new Vector3f(to).sub(from);
        world.spawnParticle(particle, at(from), 0, d.x, d.y, d.z, speed, null, true);
    }

    /* ------------------------------------------------------------------ text */

    public void title(String main, String sub, int fadeIn, int stay, int fadeOut) {
        Title title = Title.title(
                TextUtil.component(main),
                TextUtil.component(sub),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L)));
        for (Player p : audience()) {
            p.showTitle(title);
        }
    }

    public void actionBar(String text) {
        // Last Seed arenas: boss HP only. No teach tips / mechanic hints on the action bar.
    }

    /** Death / arrival vanilla parody lines only — not mechanic teach tips. */
    public void cinematicBar(String text) {
        for (Player p : audience()) {
            p.sendActionBar(TextUtil.component(text));
        }
    }

    public void say(String text) {
        for (Player p : audience()) {
            p.sendMessage(TextUtil.component(text));
        }
    }
}
