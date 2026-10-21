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

    /** {@link Renderer#hold}: true when a renderer drew it, false when the caller should fall back to particles. */
    public static boolean hold(Location centre, Color color, double radius) {
        Renderer r = renderer;
        if (r == null || centre == null || centre.getWorld() == null || radius <= 0.0) {
            return false;
        }
        r.hold(centre, color, radius);
        return true;
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
