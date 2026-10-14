package de.aetherion.quests.talk;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * One framed talk panel: the text display itself plus an optional rim behind it — a twin with
 * the same text (made invisible via text opacity, so the box is exactly the same size), a
 * name-coloured background, scaled a hair larger and nudged back. Reads as a thin edge / soft
 * glow around the panel without any resource pack.
 * <p>
 * Both layers always move, pop and collapse together.
 */
final class TalkPanel {

    @FunctionalInterface
    interface Spawner {
        TextDisplay spawn(Location at, Consumer<TextDisplay> setup);
    }

    private static final float COLLAPSED = 0.01f;
    /** Rim sits this far behind the panel (local +Z faces the viewer). */
    private static final float RIM_DEPTH = 0.012f;
    /** Alpha below ~26 is discarded by the text shader (0..3 would render opaque). */
    private static final byte INVISIBLE_TEXT = 12;

    private final Plugin plugin;
    final TextDisplay body;
    final TextDisplay rim;
    private float scale;
    private float current = COLLAPSED;
    private float sx = 1f;
    private float sy = 1f;
    private int gen;

    private TalkPanel(Plugin plugin, TextDisplay body, TextDisplay rim, float scale) {
        this.plugin = plugin;
        this.body = body;
        this.rim = rim;
        this.scale = scale;
    }

    /**
     * Spawn collapsed (invisible to everyone until {@link #show}). {@code rimColor} null =
     * no rim layer at all; {@link TalkChrome#none()} = rim layer present but off (focus rings).
     */
    static TalkPanel open(Plugin plugin, Spawner spawner, Location at, float scale,
                          Consumer<TextDisplay> style, Color rimColor) {
        TextDisplay body = spawner.spawn(at, d -> {
            style.accept(d);
            d.setTransformation(TalkChrome.scaled(COLLAPSED));
        });
        if (body == null) {
            return null;
        }
        TextDisplay rim = null;
        if (rimColor != null) {
            rim = spawner.spawn(at, d -> {
                TalkChrome.base(d);
                d.setShadowed(false);
                d.setAlignment(body.getAlignment());
                d.setLineWidth(body.getLineWidth());
                d.setTextOpacity(INVISIBLE_TEXT);
                d.setBackgroundColor(rimColor);
                d.setTransformation(rimAt(COLLAPSED, 1f, 1f));
            });
        }
        return new TalkPanel(plugin, body, rim, scale);
    }

    boolean valid() {
        return body != null && body.isValid();
    }

    float scale() {
        return scale;
    }

    void show(Player player) {
        if (player == null) {
            return;
        }
        if (valid()) {
            player.showEntity(plugin, body);
        }
        if (rim != null && rim.isValid()) {
            player.showEntity(plugin, rim);
        }
    }

    void hide(Player player) {
        if (player == null) {
            return;
        }
        if (valid()) {
            player.hideEntity(plugin, body);
        }
        if (rim != null && rim.isValid()) {
            player.hideEntity(plugin, rim);
        }
    }

    /**
     * New content. {@code widthPx} / {@code lines}: the box the text produces (widest line in
     * pixels, line count) so the rim can be sized to wrap it evenly.
     */
    void content(Component text, int widthPx, int lines) {
        if (!valid()) {
            return;
        }
        body.text(text);
        if (rim != null && rim.isValid()) {
            rim.text(text);
            float w = Math.max(1, widthPx) + 1f;
            float h = Math.max(1, lines) * (float) TalkChrome.LINE_PX;
            sx = (w + 2f * TalkChrome.RIM_PX) / w;
            sy = (h + 2f * TalkChrome.RIM_PX) / h;
            // Resize with the body, in the body's current state (no animation).
            TalkChrome.animate(rim, rimAt(current, sx, sy), 0);
        }
    }

    /** Typewriter frames: body only — the box (and the rim) keep their size. */
    void body(Component text) {
        if (valid()) {
            body.text(text);
        }
    }

    void background(Color color) {
        if (valid()) {
            body.setBackgroundColor(color);
        }
    }

    /** Fade the rim to {@code color} ({@link TalkChrome#none()} = off). */
    void rimColor(Color color, int ticks) {
        if (rim == null || !rim.isValid()) {
            return;
        }
        rim.setInterpolationDelay(0);
        rim.setInterpolationDuration(Math.max(0, ticks));
        rim.setBackgroundColor(color);
    }

    /** Bloom from collapsed to rest, optionally with a small spring overshoot. */
    void popIn(int delay, boolean spring) {
        final int g = ++gen;
        later(Math.max(0, delay), () -> {
            if (g != gen) {
                return;
            }
            if (spring) {
                apply(scale * 1.07f, 3);
                later(3, () -> {
                    if (g == gen) {
                        apply(scale, 3);
                    }
                });
            } else {
                apply(scale, 4);
            }
        });
    }

    /** New resting scale (focus / unfocus). */
    void scaleTo(float target, int ticks) {
        gen++;
        scale = target;
        apply(target, ticks);
    }

    /** Quick bump and settle (nudge the replies when the NPC is clicked instead). */
    void nudge(float factor) {
        final int g = ++gen;
        apply(scale * factor, 2);
        later(3, () -> {
            if (g == gen) {
                apply(scale, 3);
            }
        });
    }

    /** Collapse (optionally sliding toward {@code tuck}) and remove. */
    void collapse(Location tuck) {
        gen++;
        if (!valid()) {
            kill();
            return;
        }
        apply(COLLAPSED, 3);
        if (tuck != null) {
            teleport(tuck);
        }
        later(4, this::kill);
    }

    void teleport(Location to) {
        if (to == null) {
            return;
        }
        if (valid() && to.getWorld() == body.getWorld()) {
            body.teleport(to);
        }
        if (rim != null && rim.isValid() && to.getWorld() == rim.getWorld()) {
            rim.teleport(to);
        }
    }

    Location location() {
        return valid() ? body.getLocation() : null;
    }

    void kill() {
        if (body != null && body.isValid()) {
            body.remove();
        }
        if (rim != null && rim.isValid()) {
            rim.remove();
        }
    }

    private void apply(float s, int ticks) {
        current = s;
        TalkChrome.animate(body, TalkChrome.scaled(s), ticks);
        if (rim != null) {
            TalkChrome.animate(rim, rimAt(s, sx, sy), ticks);
        }
    }

    /**
     * Rim transform for a body at scale {@code s}: stretched by the rim factors around the
     * shared bottom-centre origin, pulled down by one rim width so it wraps the bottom edge
     * too, and pushed back so it always draws behind.
     */
    private static Transformation rimAt(float s, float sx, float sy) {
        return new Transformation(
                new Vector3f(0f, -TalkChrome.RIM_PX * TalkChrome.PX * s, -RIM_DEPTH),
                new AxisAngle4f(),
                new Vector3f(s * sx, s * sy, s),
                new AxisAngle4f());
    }

    private void later(long ticks, Runnable task) {
        if (ticks <= 0) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }
}
