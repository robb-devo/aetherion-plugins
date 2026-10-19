package de.aetherion.items.hud;

import de.aetherion.core.api.Vfx;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * {@link Vfx.Renderer} built from text displays carrying one glyph of the pack font {@code aetherion:vfx}
 * (white art, tinted by the text colour). Short-lived, never saved with the chunk, and swept on enable.
 *
 * <p>A one-line text display draws its glyph 0.8 blocks tall with the centre 0.1 blocks above the entity
 * (glyph height 32, ascent 18), so flat effects lift by that much after the scale.
 */
public final class DisplayVfx implements Vfx.Renderer {

    static final String TAG = "aetherion_vfx";
    private static final Key FONT = Key.key("aetherion", "vfx");
    private static final char SLASH = '';
    private static final char RING = '';
    private static final char RUNE = '';
    private static final char TELE_EDGE = '';
    private static final char TELE_FILL = '';
    private static final float GLYPH = 0.8f;
    private static final float CENTRE_Y = 0.1f;
    /** Hard cap so a broken caller cannot flood a chunk. */
    private static final int MAX_LIVE = 160;

    private final JavaPlugin plugin;
    private int live;

    public DisplayVfx(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Remove leftovers from a crash or a hard stop. */
    public void sweep() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntitiesByClass(TextDisplay.class)) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
    }

    @Override
    public void slash(Location eye, Color color, double size, float roll) {
        float s = (float) Math.max(0.2, size) / GLYPH;
        Quaternionf tilt = new Quaternionf().rotateZ((float) Math.toRadians(roll));
        TextDisplay d = spawn(eye, SLASH, color, Display.Billboard.CENTER,
                transform(new Vector3f(0, -CENTRE_Y * s * 0.6f, 0), tilt, s * 0.6f));
        if (d == null) {
            return;
        }
        later(1, () -> grow(d, transform(new Vector3f(0, -CENTRE_Y * s, 0), tilt, s), 3));
        fade(d, 4, 7);
    }

    @Override
    public void ring(Location centre, Color color, double radius, int ticks) {
        int life = Math.max(4, ticks);
        float end = (float) (radius * 2.0) / GLYPH;
        TextDisplay d = spawn(centre, RING, color, Display.Billboard.FIXED, flat(end * 0.25f, 0f, 0.05f));
        if (d == null) {
            return;
        }
        later(1, () -> grow(d, flat(end, 0f, 0.05f), life - 2));
        fade(d, life / 2, life);
    }

    @Override
    public void rune(Location centre, Color color, double radius, int ticks) {
        int life = Math.max(10, ticks);
        float s = (float) (radius * 2.0) / GLYPH;
        TextDisplay d = spawn(centre, RUNE, color, Display.Billboard.FIXED, flat(s * 0.6f, 0f, 0.04f));
        if (d == null) {
            return;
        }
        later(1, () -> grow(d, flat(s, (float) (Math.PI / 2), 0.04f), life - 4));
        fade(d, life - 6, life);
    }

    @Override
    public void telegraph(Location centre, double radius, int ticks, Color color) {
        int life = Math.max(6, ticks);
        float s = (float) (radius * 2.0) / GLYPH;
        TextDisplay edge = spawn(centre, TELE_EDGE, color, Display.Billboard.FIXED, flat(s, 0f, 0.06f));
        TextDisplay fill = spawn(centre, TELE_FILL, color, Display.Billboard.FIXED, flat(0.05f, 0f, 0.05f));
        if (fill != null) {
            later(1, () -> grow(fill, flat(s, 0f, 0.05f), life - 1));
            later(life, () -> kill(fill));
        }
        if (edge != null) {
            later(life, () -> kill(edge));
        }
    }

    /* ------------------------------------------------------------------ plumbing */

    private TextDisplay spawn(Location at, char glyph, Color color, Display.Billboard billboard, Transformation start) {
        if (live >= MAX_LIVE || at.getWorld() == null || !at.isChunkLoaded()) {
            return null;
        }
        int rgb = color == null ? 0xFFFFFF : color.asRGB();
        Location spot = at.clone();
        spot.setYaw(0f);
        spot.setPitch(0f);
        TextDisplay d = at.getWorld().spawn(spot, TextDisplay.class, e -> {
            e.text(Component.text(String.valueOf(glyph)).font(FONT).color(TextColor.color(rgb)));
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(false);
            e.setSeeThrough(false);
            e.setBillboard(billboard);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.addScoreboardTag(TAG);
            e.setViewRange(1.5f);
            e.setInterpolationDuration(0);
            e.setTransformation(start);
        });
        live++;
        later(200, () -> kill(d));
        return d;
    }

    private static Transformation transform(Vector3f translation, Quaternionf left, float scale) {
        return new Transformation(translation, left, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** Lying on the ground, turned {@code spin} rad about the vertical, lifted a hair to dodge z-fighting. */
    private static Transformation flat(float scale, float spin, float lift) {
        Quaternionf left = new Quaternionf(new AxisAngle4f(spin, 0, 1, 0)).rotateX((float) (-Math.PI / 2));
        // Glyph centre sits CENTRE_Y up in display space; after the flat turn that is -z, so push it back.
        Vector3f t = new Quaternionf(new AxisAngle4f(spin, 0, 1, 0)).transform(new Vector3f(0, 0, CENTRE_Y * scale));
        t.y += lift;
        return new Transformation(t, left, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private static void grow(TextDisplay d, Transformation to, int ticks) {
        if (!d.isValid()) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(1, ticks));
        d.setTransformation(to);
    }

    private void fade(TextDisplay d, int from, int until) {
        int span = Math.max(1, until - from);
        later(from, () -> {
            if (d.isValid()) {
                d.setTextOpacity((byte) 170);
            }
        });
        later(from + span / 2, () -> {
            if (d.isValid()) {
                d.setTextOpacity((byte) 90);
            }
        });
        later(until, () -> kill(d));
    }

    private void kill(TextDisplay d) {
        if (d != null && d.isValid()) {
            d.remove();
            live = Math.max(0, live - 1);
        }
    }

    private void later(long ticks, Runnable task) {
        if (ticks <= 0) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }
}
