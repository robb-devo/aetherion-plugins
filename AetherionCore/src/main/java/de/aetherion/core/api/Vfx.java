package de.aetherion.core.api;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
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
}
