package de.aetherion.core.api;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;

/**
 * Display-entity effects and the pack's custom sounds, shared by every plugin.
 *
 * <p>AetherionItems registers the renderer (slashes, rings, runes, ground telegraphs drawn with display
 * entities). Without it every call is a no-op, so callers keep their own particles as the baseline. Sound
 * keys live in the server pack ({@code assets/aetherion/sounds.json}); a client without the pack hears nothing.
 */
public final class Vfx {

    public static final String UI_CLICK = "aetherion:ui.click";
    public static final String UI_OPEN = "aetherion:ui.open";
    public static final String UI_CONFIRM = "aetherion:ui.confirm";
    public static final String UI_DENY = "aetherion:ui.deny";
    public static final String UI_QUEST_PING = "aetherion:ui.quest_ping";
    public static final String UI_LEVELUP = "aetherion:ui.levelup";
    public static final String COMBAT_SLASH = "aetherion:combat.slash";
    public static final String COMBAT_HEAVY = "aetherion:combat.heavy";
    public static final String COMBAT_RING = "aetherion:combat.ring";
    public static final String BOSS_TELEGRAPH = "aetherion:boss.telegraph";
    public static final String BOSS_IMPACT = "aetherion:boss.impact";
    public static final String BOSS_RUNE = "aetherion:boss.rune";
    public static final String BOSS_APPEAR = "aetherion:boss.appear";

    /** Draws the effects. */
    public interface Renderer {
        /** Crescent in front of {@code eye}, facing every viewer, tilted by {@code roll} degrees. */
        void slash(Location eye, Color color, double size, float roll);

        /** Flat ring on the ground that widens to {@code radius} and fades. */
        void ring(Location centre, Color color, double radius, int ticks);

        /** Flat rune circle that turns slowly for {@code ticks}. */
        void rune(Location centre, Color color, double radius, int ticks);

        /** Ground warning: dashed edge at {@code radius} plus a fill that grows to it over {@code ticks}. */
        void telegraph(Location centre, double radius, int ticks, Color color);

        /**
         * Flat ring on the ground that stays while it is drawn again within a few ticks. Lets code that redraws a
         * shape every tick or two (boss telegraphs, arena edges) draw one display instead of a ring of particles.
         */
        default void hold(Location centre, Color color, double radius) {
        }

        /** {@link #hold} in any plane: {@code plane} turns the flat ring (orbits, halos, tilted rims). */
        default void holdPlane(Location centre, Color color, double radius, org.joml.Quaternionf plane) {
        }

        /** A comet: glowing head, tapering tail, flying from {@code from} to {@code to} in {@code ticks}. */
        default void streak(Location from, Location to, Color color, double width, int ticks) {
        }

        /** A straight glowing bar between two points, held for {@code ticks}. */
        default void beam(Location from, Location to, Color color, double width, int ticks) {
        }

        /** A jagged lightning bolt between two points that flickers out over {@code ticks}. */
        default void bolt(Location from, Location to, Color color, int ticks) {
        }

        /** An eight-point star flash that blooms to {@code size} blocks across and fades. */
        default void burst(Location at, Color color, double size, int ticks) {
        }

        /** A soft glowing orb, pulsing for {@code ticks}; it glides to {@code to} when that is given. */
        default void orb(Location at, Location to, Color color, double size, int ticks) {
        }

        /** A four-point twinkle that pops in and out. */
        default void spark(Location at, Color color, double size) {
        }

        /** A column of light rising from {@code base}. */
        default void pillar(Location base, Color color, double radius, double height, int ticks) {
        }

        /** Flame tongues turning in a helix around {@code base}. */
        default void spiral(Location base, Color color, double radius, double height, int ticks, int arms) {
        }

        /** A thick shockwave band racing out over the ground to {@code radius}. */
        default void wave(Location centre, Color color, double radius, int ticks) {
        }

        /** A flame that burns on a spot for {@code ticks}. */
        default void flame(Location at, Color color, double size, int ticks) {
        }

        /** An aurora curtain: a hanging band of light that sways and fades. */
        default void ribbon(Location centre, Color color, double width, double height, float yaw, int ticks) {
        }

        /** A few glowing motes drifting from {@code at} by {@code drift} (embers, fireflies, glints). */
        default void motes(Location at, Color color, int count, double spread, org.bukkit.util.Vector drift, int ticks) {
        }

        /** An upright half ring (a rainbow band, a halo over a horizon) facing {@code yaw}, for {@code ticks}. */
        default void arc(Location centre, Color color, double radius, float yaw, int ticks) {
        }

        /** A faint bank of mist {@code size} blocks across, drifting by {@code drift} over {@code ticks}. */
        default void mist(Location at, Color color, double size, org.bukkit.util.Vector drift, int ticks) {
        }

        /**
         * A glowing line that stays while it is drawn again within a few ticks and follows its ends: energy blades,
         * tracking beams, tethers. {@code key} names it (one line per key); redraw it every tick or two.
         */
        default void line(String key, Location from, Location to, Color color, double width) {
        }

        /** Small chips of {@code block} thrown out and falling (impact debris), {@code power} ~1 for a hit. */
        default void chips(Location at, org.bukkit.block.data.BlockData block, int count, double power) {
        }
    }

    private static volatile Renderer renderer;

    private Vfx() {
    }

    public static void register(Renderer owner) {
        renderer = owner;
    }

    public static void unregister(Renderer owner) {
        if (renderer == owner) {
            renderer = null;
        }
    }

    public static boolean active() {
        return renderer != null;
    }

    public static void slash(Location eye, Color color, double size, float roll) {
        Renderer r = renderer;
        if (r != null && eye != null && eye.getWorld() != null) {
            r.slash(eye, color, size, roll);
        }
    }

    public static void ring(Location centre, Color color, double radius, int ticks) {
        Renderer r = renderer;
        if (r != null && centre != null && centre.getWorld() != null) {
            r.ring(centre, color, radius, ticks);
        }
    }

    public static void rune(Location centre, Color color, double radius, int ticks) {
        Renderer r = renderer;
        if (r != null && centre != null && centre.getWorld() != null) {
            r.rune(centre, color, radius, ticks);
        }
    }

    public static boolean holdPlane(Location centre, Color color, double radius, org.joml.Quaternionf plane) {
        Renderer r = renderer;
        if (r == null || centre == null || centre.getWorld() == null || radius <= 0.0 || plane == null) {
            return false;
        }
        r.holdPlane(centre, color, radius, plane);
        return true;
    }

    /** {@link Renderer#hold}: true when a renderer drew it, false when the caller should fall back to particles. */
    public static boolean hold(Location centre, Color color, double radius) {
        Renderer r = renderer;
        if (r == null || centre == null || centre.getWorld() == null || radius <= 0.0) {
            return false;
        }
        r.hold(centre, color, radius);
        return true;
    }

    // Each returns true when a renderer drew it, false when the caller should fall back to its own particles.

    public static boolean streak(Location from, Location to, Color color, double width, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(from) || !placed(to) || from.getWorld() != to.getWorld()) {
            return false;
        }
        r.streak(from, to, color, width, ticks);
        return true;
    }

    public static boolean beam(Location from, Location to, Color color, double width, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(from) || !placed(to) || from.getWorld() != to.getWorld()) {
            return false;
        }
        r.beam(from, to, color, width, ticks);
        return true;
    }

    public static boolean bolt(Location from, Location to, Color color, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(from) || !placed(to) || from.getWorld() != to.getWorld()) {
            return false;
        }
        r.bolt(from, to, color, ticks);
        return true;
    }

    public static boolean burst(Location at, Color color, double size, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(at)) {
            return false;
        }
        r.burst(at, color, size, ticks);
        return true;
    }

    public static boolean orb(Location at, Location to, Color color, double size, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(at) || (to != null && to.getWorld() != at.getWorld())) {
            return false;
        }
        r.orb(at, to, color, size, ticks);
        return true;
    }

    public static boolean spark(Location at, Color color, double size) {
        Renderer r = renderer;
        if (r == null || !placed(at)) {
            return false;
        }
        r.spark(at, color, size);
        return true;
    }

    public static boolean pillar(Location base, Color color, double radius, double height, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(base)) {
            return false;
        }
        r.pillar(base, color, radius, height, ticks);
        return true;
    }

    public static boolean spiral(Location base, Color color, double radius, double height, int ticks, int arms) {
        Renderer r = renderer;
        if (r == null || !placed(base)) {
            return false;
        }
        r.spiral(base, color, radius, height, ticks, arms);
        return true;
    }

    public static boolean wave(Location centre, Color color, double radius, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(centre)) {
            return false;
        }
        r.wave(centre, color, radius, ticks);
        return true;
    }

    public static boolean flame(Location at, Color color, double size, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(at)) {
            return false;
        }
        r.flame(at, color, size, ticks);
        return true;
    }

    public static boolean ribbon(Location centre, Color color, double width, double height, float yaw, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(centre)) {
            return false;
        }
        r.ribbon(centre, color, width, height, yaw, ticks);
        return true;
    }

    public static boolean motes(Location at, Color color, int count, double spread, org.bukkit.util.Vector drift, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(at) || count <= 0) {
            return false;
        }
        r.motes(at, color, count, spread, drift, ticks);
        return true;
    }

    public static boolean arc(Location centre, Color color, double radius, float yaw, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(centre)) {
            return false;
        }
        r.arc(centre, color, radius, yaw, ticks);
        return true;
    }

    public static boolean mist(Location at, Color color, double size, org.bukkit.util.Vector drift, int ticks) {
        Renderer r = renderer;
        if (r == null || !placed(at)) {
            return false;
        }
        r.mist(at, color, size, drift, ticks);
        return true;
    }

    public static boolean line(String key, Location from, Location to, Color color, double width) {
        Renderer r = renderer;
        if (r == null || key == null || !placed(from) || !placed(to) || from.getWorld() != to.getWorld()) {
            return false;
        }
        r.line(key, from, to, color, width);
        return true;
    }

    public static boolean chips(Location at, org.bukkit.block.data.BlockData block, int count, double power) {
        Renderer r = renderer;
        if (r == null || !placed(at) || block == null || count <= 0) {
            return false;
        }
        r.chips(at, block, count, power);
        return true;
    }

    private static boolean placed(Location at) {
        return at != null && at.getWorld() != null;
    }

    public static void telegraph(Location centre, double radius, int ticks, Color color) {
        Renderer r = renderer;
        if (r != null && centre != null && centre.getWorld() != null) {
            r.telegraph(centre, radius, ticks, color);
        }
    }

    /** Pack sound at a spot, for everyone near it. */
    public static void sound(Location at, String key, SoundCategory category, float volume, float pitch) {
        if (at != null && at.getWorld() != null && key != null) {
            at.getWorld().playSound(at, key, category, volume, pitch);
        }
    }

    /** Pack sound for one player only (UI). */
    public static void sound(Player player, String key, float volume, float pitch) {
        if (player != null && key != null) {
            player.playSound(player.getLocation(), key, SoundCategory.MASTER, volume, pitch);
        }
    }

    /* ------------------------------------------------------------------ the particle-to-shape translator */

    /*
     * One visual language for every plugin: what a particle burst meant, drawn as a shape. Smoke and dust become a
     * ground wave, fire a few flame tongues, souls, embers and glints rising motes, magic a glowing orb, sparks a
     * twinkle, explosions a star burst and a shockwave, block crumbs real chips. Without a renderer the particle is
     * spawned as before. Repeats of the same kind on the same spot within five ticks are skipped (code that redrew a
     * burst every tick).
     */
    public static final Color FIRE = Color.fromRGB(0xFF7A2A);
    public static final Color SOUL = Color.fromRGB(0x5FD8E8);
    public static final Color VOID = Color.fromRGB(0xB46BFF);
    public static final Color GOLD = Color.fromRGB(0xFFD86B);
    public static final Color SPARK = Color.fromRGB(0xFFF1A0);
    public static final Color ICE = Color.fromRGB(0xBFE8FF);
    public static final Color LIFE = Color.fromRGB(0x7EE07A);
    public static final Color BLOOD = Color.fromRGB(0xFF5A4A);
    public static final Color ASH = Color.fromRGB(0x6A6470);
    public static final Color WATER = Color.fromRGB(0x6FB6FF);
    public static final Color PALE = Color.fromRGB(0xECE8DE);

    private static final java.util.Map<String, Long> RECENT = new java.util.HashMap<>();
    /**
     * The burst a particle call stood for, drawn as a shape sized from its spread; the particles themselves when no
     * renderer runs. {@code data} is the particle's data (block data for crumbs, dust options), may be null.
     */
    public static void puff(Location at, Particle particle, int count, double spread, Object data) {
        if (at == null || at.getWorld() == null || particle == null) {
            return;
        }
        if (!active()) {
            fallback(at, particle, count, spread, data);
            return;
        }
        // Code that redrew a burst every tick: one shape per kind and spot every few ticks is plenty.
        long now = org.bukkit.Bukkit.getCurrentTick();
        String key = particle.ordinal() + "|" + (at.getBlockX() >> 1) + "|" + (at.getBlockY() >> 1) + "|" + (at.getBlockZ() >> 1);
        Long last = RECENT.get(key);
        // single-particle whispers (ambience that ran every tick) become a rare accent; bursts may repeat sooner
        if (last != null && now - last < (count <= 2 ? 20 : 5)) {
            return;
        }
        if (RECENT.size() > 4096) {
            RECENT.clear();
        }
        RECENT.put(key, now);
        double size = Math.max(0.5, Math.min(4.0, spread * 2.0 + Math.sqrt(Math.max(1, count)) * 0.15));
        Color color = data instanceof Particle.DustOptions dust ? dust.getColor() : tint(particle);
        switch (particle) {
            case EXPLOSION, EXPLOSION_EMITTER, FLASH, SONIC_BOOM -> {
                burst(at, particle == Particle.FLASH ? PALE : Color.fromRGB(0xFFC070), size * 1.4, 10);
                wave(ground(at), particle == Particle.SONIC_BOOM ? SOUL : PALE, size * 1.3, 12);
            }
            case FLAME, SMALL_FLAME, SOUL_FIRE_FLAME, LAVA -> {
                int n = Math.max(1, Math.min(4, count / 6));
                for (int i = 0; i < n; i++) {
                    Location f = jitter(at, spread * 0.6);
                    flame(f, color, 0.5 + size * 0.25, 12 + i * 2);
                }
            }
            case SOUL, SCULK_SOUL, WHITE_ASH, ASH, END_ROD, GLOW, WAX_ON, WAX_OFF, ENCHANT, TOTEM_OF_UNDYING, SNOWFLAKE,
                 SPORE_BLOSSOM_AIR, FALLING_DUST, MYCELIUM ->
                    motes(at, color, count <= 2 ? 1 : Math.max(2, Math.min(6, count / 4)), Math.max(0.2, spread * 0.7),
                            new Vector(0, 1.2 + spread, 0), 22);
            case PORTAL, REVERSE_PORTAL, WITCH, DRAGON_BREATH, SQUID_INK -> {
                orb(at, null, color, size * 0.8, 12);
                motes(at, color, 3, Math.max(0.2, spread * 0.6), new Vector(0, 1.0, 0), 18);
            }
            case CRIT, ENCHANTED_HIT, DAMAGE_INDICATOR, ELECTRIC_SPARK, FIREWORK -> spark(at, color, Math.max(0.6, size * 0.6));
            case CLOUD, POOF, SMOKE, LARGE_SMOKE, CAMPFIRE_COSY_SMOKE, CAMPFIRE_SIGNAL_SMOKE, GUST, SMALL_GUST, SWEEP_ATTACK ->
                    wave(ground(at), color, size, 10);
            case SPLASH, BUBBLE, BUBBLE_POP, BUBBLE_COLUMN_UP, FALLING_WATER, DRIPPING_WATER ->
                    wave(ground(at), WATER, size, 10);
            case BLOCK, DUST_PILLAR -> {
                if (data instanceof BlockData block) {
                    chips(at, block, Math.max(3, Math.min(10, count / 4)), Math.max(0.6, spread * 1.6));
                }
            }
            case DUST, DUST_COLOR_TRANSITION -> spark(at, color, Math.max(0.5, size * 0.5));
            default -> spark(at, color, Math.max(0.5, size * 0.5));
        }
    }

    /** {@link #puff} without particle data. */
    public static void puff(Location at, Particle particle, int count, double spread) {
        puff(at, particle, count, spread, null);
    }

    /** The colour a particle-drawn effect reads as. */
    public static Color tint(Particle particle) {
        if (particle == null) {
            return PALE;
        }
        return switch (particle) {
            case FLAME, SMALL_FLAME, LAVA, DRIPPING_LAVA, FALLING_LAVA, LANDING_LAVA -> FIRE;
            case SOUL_FIRE_FLAME, SOUL, SCULK_SOUL -> SOUL;
            case WITCH, PORTAL, REVERSE_PORTAL, DRAGON_BREATH, ENCHANT -> VOID;
            case CRIT, ENCHANTED_HIT, WAX_ON, WAX_OFF, TOTEM_OF_UNDYING -> GOLD;
            case ELECTRIC_SPARK, FIREWORK, END_ROD, GLOW -> SPARK;
            case SNOWFLAKE, ITEM_SNOWBALL -> ICE;
            case HAPPY_VILLAGER, COMPOSTER, SPORE_BLOSSOM_AIR, MYCELIUM -> LIFE;
            case DAMAGE_INDICATOR, ANGRY_VILLAGER -> BLOOD;
            case SMOKE, LARGE_SMOKE, ASH, SQUID_INK, WHITE_ASH -> ASH;
            case SPLASH, BUBBLE, BUBBLE_POP, BUBBLE_COLUMN_UP, FALLING_WATER, DRIPPING_WATER -> WATER;
            default -> PALE;
        };
    }

    private static Location ground(Location at) {
        return at.clone().add(0, 0.05, 0);
    }

    private static Location jitter(Location at, double spread) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        return at.clone().add(rng.nextGaussian() * spread, 0, rng.nextGaussian() * spread);
    }

    private static void fallback(Location at, Particle particle, int count, double spread, Object data) {
        World world = at.getWorld();
        try {
            if (data != null && particle.getDataType().isInstance(data)) {
                world.spawnParticle(particle, at, count, spread, spread * 0.6, spread, 0.02, data);
            } else if (particle.getDataType() == Void.class) {
                world.spawnParticle(particle, at, count, spread, spread * 0.6, spread, 0.02);
            }
        } catch (IllegalArgumentException ignored) {
            // a particle that needs data it was not given: nothing to draw
        }
    }
}
