package de.aetherion.bossengine.instance.eggquelizer;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * One fixed anchor (stage center, floor surface) that every Eggquelizer display spawns on.
 * Displays never teleport: all motion is transformation interpolation relative to the anchor.
 */
final class EggFx {

    static final float VIEW_RANGE = 3.5f;
    static final double AUDIENCE_RADIUS = 72.0;
    static final double COMBAT_RADIUS = 26.0;

    /** Marks short-lived attack telegraphs so clear/scrub never confuses them with body/speakers. */
    private static final NamespacedKey PROP_KEY = new NamespacedKey("bossengine", "egg_prop");

    private final BossInstance instance;
    private final Location anchor;
    private final World world;
    /** Pitch jitter on every sound once the shell has cracked (0 = clean signal). */
    float warble;

    EggFx(BossInstance instance, Location anchor) {
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

    Location at(Vector3f p) {
        return new Location(world, anchor.getX() + p.x, anchor.getY() + p.y, anchor.getZ() + p.z);
    }

    Location at(float x, float y, float z) {
        return new Location(world, anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z);
    }

    Vector3f stage(Location l) {
        return new Vector3f(
                (float) (l.getX() - anchor.getX()),
                (float) (l.getY() - anchor.getY()),
                (float) (l.getZ() - anchor.getZ()));
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

    /** Attack telegraph / wave / packet — tagged so orphans can be scrubbed without killing the cast. */
    BlockDisplay prop(Material material, Color glow, int brightness) {
        BlockDisplay d = block(material, glow, brightness);
        d.getPersistentDataContainer().set(PROP_KEY, PersistentDataType.BYTE, (byte) 1);
        return d;
    }

    ItemDisplay item(ItemStack stack, int brightness) {
        return world.spawn(anchor, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            prepare(d, null, brightness);
            d.setTransformationMatrix(new Matrix4f().scale(0.001f));
        });
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
        if (brightness >= 0) {
            int b = Math.max(0, Math.min(15, brightness));
            d.setBrightness(new Display.Brightness(b, b));
        }
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

    static void light(Display d, int level) {
        if (d != null && d.isValid()) {
            int b = Math.max(0, Math.min(15, level));
            d.setBrightness(new Display.Brightness(b, b));
        }
    }

    static void kill(Display d) {
        if (d == null) {
            return;
        }
        try {
            d.remove();
        } catch (RuntimeException ignored) {
        }
    }

    /** Remove orphaned attack displays (white rings etc.) still tagged as egg props near the stage. */
    void scrubProps() {
        if (world == null) {
            return;
        }
        String mine = instance.getInstanceId().toString();
        var beamKey = instance.getKeys().beamFxKey();
        // World-wide: leftover rings from a broken tick loop can pile into tens of thousands.
        for (BlockDisplay bd : world.getEntitiesByClass(BlockDisplay.class)) {
            boolean taggedProp = bd.getPersistentDataContainer().has(PROP_KEY, PersistentDataType.BYTE);
            String beam = bd.getPersistentDataContainer().get(beamKey, PersistentDataType.STRING);
            boolean mineBeam = mine.equals(beam);
            // Also wipe untagged stained-glass beam_fx from ANY egg instance — those are only attack FX.
            boolean stainedGlass = false;
            Material m = bd.getBlock().getMaterial();
            if (m == Material.WHITE_STAINED_GLASS
                    || m == Material.RED_STAINED_GLASS
                    || m == Material.ORANGE_STAINED_GLASS
                    || m == Material.LIGHT_GRAY_STAINED_GLASS) {
                stainedGlass = true;
            }
            boolean anyEggBeam = beam != null && !beam.isEmpty() && stainedGlass;
            if (!taggedProp && !mineBeam && !anyEggBeam) {
                continue;
            }
            if (!taggedProp && mineBeam && !stainedGlass) {
                continue;
            }
            try {
                bd.remove();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Unit block centered on {@code c}, sized, yawed (floor decals, ring segments). */
    static Matrix4f box(Vector3f c, float sx, float sy, float sz, float yaw) {
        return new Matrix4f().translation(c).rotateY(yaw).scale(sx, sy, sz).translate(-0.5f, -0.5f, -0.5f);
    }

    /** A thin box stretched from a to b. */
    static Matrix4f beam(Vector3f a, Vector3f b, float width) {
        return beam(a, b, width, width);
    }

    static Matrix4f beam(Vector3f a, Vector3f b, float width, float depth) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1e-4f) {
            return gone(a);
        }
        return new Matrix4f()
                .translation(a)
                .rotate(new Quaternionf().rotationTo(0f, 1f, 0f, dir.x / len, dir.y / len, dir.z / len))
                .scale(width, len, depth)
                .translate(-0.5f, 0f, -0.5f);
    }

    static Matrix4f gone(Vector3f at) {
        return new Matrix4f().translation(at).scale(0.001f);
    }

    /* ------------------------------------------------------------------ players */

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
    private int targetTick = Integer.MIN_VALUE;

    /** Vulnerable players on or around the stage. Cached per server tick. */
    List<Player> targets() {
        int now = Bukkit.getCurrentTick();
        if (now != targetTick) {
            targetCache = scanTargets();
            targetTick = now;
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
            if (dx * dx + dz * dz <= COMBAT_RADIUS * COMBAT_RADIUS && dy > -8 && dy < 30) {
                out.add(p);
            }
        }
        return out;
    }

    Player nearestTarget(Vector3f from) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : targets()) {
            double d = EggMath.horizontal(stage(p.getLocation()), from);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /* ------------------------------------------------------------------ sound */

    private float pitch(float p) {
        if (warble > 0f) {
            p *= 1f + (EggMath.rnd() * 2f - 1f) * warble;
        }
        return EggMath.clamp(p, 0.5f, 2f);
    }

    /** Positional: you hear WHICH speaker it came from. */
    void sound(Vector3f at, Sound sound, float volume, float pitch) {
        world.playSound(at(at), sound, SoundCategory.MASTER, volume, pitch(pitch));
    }

    /** Clean positional sound that ignores the warble (death, UI-ish ticks). */
    void clean(Vector3f at, Sound sound, float volume, float pitch) {
        world.playSound(at(at), sound, SoundCategory.MASTER, volume, EggMath.clamp(pitch, 0.5f, 2f));
    }

    /** Non-positional, identical for every listener. */
    void score(Sound sound, float volume, float pitch) {
        float p = pitch(pitch);
        for (Player pl : audience()) {
            pl.playSound(pl.getLocation(), sound, SoundCategory.MASTER, volume, p);
        }
    }

    void particle(Particle particle, Vector3f at, int count, double spread, double speed) {
        world.spawnParticle(particle, at(at), count, spread, spread, spread, speed);
    }

    void particle(Particle particle, Vector3f at, int count, double sx, double sy, double sz, double speed) {
        world.spawnParticle(particle, at(at), count, sx, sy, sz, speed);
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
}
