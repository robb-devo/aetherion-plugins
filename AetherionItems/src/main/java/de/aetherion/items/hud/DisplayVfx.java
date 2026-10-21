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
    private static final int MAX_LIVE = 480;

    private final JavaPlugin plugin;
    private int live;
    /** Held rings by world + centre + radius + colour; each lives until a few ticks after its last hold. */
    private final java.util.Map<String, Held> held = new java.util.HashMap<>();
    private org.bukkit.scheduler.BukkitTask heldSweep;

    private record Held(TextDisplay display, long[] until) {
    }

    /** Lines by key: four cross planes riding one anchor, retargeted on every draw. */
    private final java.util.Map<String, HeldLine> lines = new java.util.HashMap<>();

    private record HeldLine(TextDisplay[] planes, Location anchor, long[] until) {
    }

    public DisplayVfx(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Remove leftovers from a crash or a hard stop. */
    public void sweep() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntitiesByClass(Display.class)) {
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

    @Override
    public void hold(Location centre, Color color, double radius) {
        long now = Bukkit.getCurrentTick();
        String key = centre.getWorld().getName() + '|' + Math.round(centre.getX() * 4) + '|' + Math.round(centre.getY() * 2)
                + '|' + Math.round(centre.getZ() * 4) + '|' + Math.round(radius * 4) + '|' + (color == null ? 0 : color.asRGB());
        Held ring = held.get(key);
        if (ring != null && ring.display().isValid()) {
            ring.until()[0] = now + 4;
            return;
        }
        float s = (float) (radius * 2.0) / GLYPH;
        TextDisplay d = spawn(centre, TELE_EDGE, color, Display.Billboard.FIXED, flat(s, 0f, 0.06f));
        if (d == null) {
            return;
        }
        held.put(key, new Held(d, new long[] {now + 4}));
        if (heldSweep == null) {
            heldSweep = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepHeld, 2L, 2L);
        }
    }

    @Override
    public void line(String key, Location from, Location to, Color color, double width) {
        long now = Bukkit.getCurrentTick();
        HeldLine line = lines.get(key);
        if (line != null && (!line.planes()[0].isValid() || line.anchor().getWorld() != from.getWorld()
                || line.anchor().distanceSquared(from) > 48 * 48)) {
            for (TextDisplay d : line.planes()) {
                kill(d);
            }
            lines.remove(key);
            line = null;
        }
        if (line == null) {
            TextDisplay[] planes = new TextDisplay[4];
            for (int i = 0; i < 4; i++) {
                planes[i] = spawn(from, BEAM, color, Display.Billboard.FIXED, plane(new Quaternionf(), new Vector3f(), 0.01f, 0.01f), 2.0f);
                if (planes[i] == null) {
                    for (int k = 0; k < i; k++) {
                        kill(planes[k]);
                    }
                    return;
                }
            }
            line = new HeldLine(planes, from.clone(), new long[] {now + 4});
            lines.put(key, line);
            if (heldSweep == null) {
                heldSweep = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepHeld, 2L, 2L);
            }
        }
        line.until()[0] = now + 4;
        Vector3f a = between(line.anchor(), from);
        Vector3f path = between(from, to);
        float dist = path.length();
        if (dist < 0.05f) {
            return;
        }
        Vector3f d = new Vector3f(path).normalize();
        Vector3f mid = new Vector3f(a).add(new Vector3f(path).mul(0.5f));
        float sx = dist / W_BEAM;
        float sy = (float) Math.max(0.04, width) / GLYPH;
        Vector3f[] normals = crossNormals(d);
        int rgb = color == null ? 0xFFFFFF : color.asRGB();
        for (int i = 0; i < 4; i++) {
            TextDisplay p = line.planes()[i];
            p.setInterpolationDelay(0);
            p.setInterpolationDuration(2);
            p.setTransformation(plane(basis(d, normals[i]), mid, sx, sy));
            if (!TextColor.color(rgb).equals(p.text().color())) {
                p.text(p.text().color(TextColor.color(rgb)));
            }
        }
    }

    private void sweepHeld() {
        long now = Bukkit.getCurrentTick();
        lines.values().removeIf(line -> {
            if (line.planes()[0].isValid() && line.until()[0] >= now) {
                return false;
            }
            for (TextDisplay d : line.planes()) {
                kill(d);
            }
            return true;
        });
        held.values().removeIf(ring -> {
            if (ring.display().isValid() && ring.until()[0] >= now) {
                return false;
            }
            kill(ring.display());
            return true;
        });
        if (held.isEmpty() && lines.isEmpty() && heldSweep != null) {
            heldSweep.cancel();
            heldSweep = null;
        }
    }

    /* ------------------------------------------------------------------ shapes (gen_vfx_art.py) */

    private static final char STREAK = '';
    private static final char BURST = '';
    private static final char ORB = '';
    private static final char SPARK = '';
    private static final char FLAME = '';
    private static final char FLAME_CORE = '';
    private static final char PILLAR = '';
    private static final char BOLT = '';
    private static final char RIBBON = '';
    private static final char MOTE = '';
    private static final char WAVE = '';
    private static final char BEAM = '';
    private static final char ARC = '';
    private static final float W_ARC = 1.6f;
    /** Glyph widths in blocks at scale 1 (every glyph is 0.8 tall; width follows the image). */
    private static final float W_STREAK = 6.4f;
    private static final float W_BOLT = 6.4f;
    private static final float W_BEAM = 12.8f;
    private static final float W_PILLAR = 0.2f;
    private static final float W_RIBBON = 3.2f;
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    @Override
    public void streak(Location from, Location to, Color color, double width, int ticks) {
        Vector3f path = between(from, to);
        float dist = path.length();
        if (dist < 0.2f) {
            return;
        }
        int life = Math.max(4, ticks);
        Vector3f d = new Vector3f(path).normalize();
        float tail = (float) Math.max(1.2, Math.min(dist * 0.45, 4.0 + width * 10.0));
        float sx = tail / W_STREAK;
        float sy = (float) Math.max(0.05, width) / GLYPH;
        float range = dist > 40f ? 4.0f : 1.5f;
        for (Vector3f n : crossNormals(d)) {
            Quaternionf q = basis(d, n);
            Vector3f startC = new Vector3f(d).mul(-tail * 0.15f);
            Vector3f endC = new Vector3f(path).sub(new Vector3f(d).mul(tail * 0.5f));
            TextDisplay s = spawn(from, STREAK, color, Display.Billboard.FIXED,
                    plane(q, startC, sx * 0.3f, sy), range);
            if (s == null) {
                return;
            }
            later(1, () -> grow(s, plane(q, endC, sx, sy), life - 1));
            fade(s, (int) (life * 0.7), life + 1);
        }
    }

    @Override
    public void beam(Location from, Location to, Color color, double width, int ticks) {
        Vector3f path = between(from, to);
        float dist = path.length();
        if (dist < 0.1f) {
            return;
        }
        int life = Math.max(3, ticks);
        Vector3f d = new Vector3f(path).normalize();
        Vector3f mid = new Vector3f(path).mul(0.5f);
        float sx = dist / W_BEAM;
        float sy = (float) Math.max(0.04, width) / GLYPH;
        for (Vector3f n : crossNormals(d)) {
            Quaternionf q = basis(d, n);
            TextDisplay b = spawn(from, BEAM, color, Display.Billboard.FIXED, plane(q, mid, sx, sy * 0.2f), 1.5f);
            if (b == null) {
                return;
            }
            later(1, () -> grow(b, plane(q, mid, sx, sy), 2));
            fade(b, Math.max(2, life - 4), life);
        }
    }

    @Override
    public void bolt(Location from, Location to, Color color, int ticks) {
        Vector3f path = between(from, to);
        float dist = path.length();
        if (dist < 0.2f) {
            return;
        }
        int life = Math.max(4, ticks);
        Vector3f d = new Vector3f(path).normalize();
        Vector3f mid = new Vector3f(path).mul(0.5f);
        float sx = dist / W_BOLT;
        float sy = (float) Math.min(1.6, 0.35 + dist * 0.05) / GLYPH;
        for (Vector3f n : crossNormals(d)) {
            Quaternionf q = basis(d, n);
            TextDisplay b = spawn(from, BOLT, color, Display.Billboard.FIXED, plane(q, mid, sx, sy), 2.0f);
            if (b == null) {
                return;
            }
            for (int t = 2; t < life; t += 2) {
                byte op = (byte) ((t / 2) % 2 == 0 ? 255 : 110);
                later(t, () -> {
                    if (b.isValid()) {
                        b.setTextOpacity(op);
                    }
                });
            }
            later(life, () -> kill(b));
        }
    }

    @Override
    public void burst(Location at, Color color, double size, int ticks) {
        int life = Math.max(6, ticks);
        float s = (float) Math.max(0.2, size) / GLYPH;
        float spin = (float) Math.toRadians(java.util.concurrent.ThreadLocalRandom.current().nextDouble(0, 45));
        TextDisplay b = spawn(at, BURST, color, Display.Billboard.CENTER,
                transform(new Vector3f(0, -CENTRE_Y * s * 0.25f, 0), new Quaternionf().rotateZ(spin), s * 0.25f), 1.5f);
        if (b == null) {
            return;
        }
        later(1, () -> grow(b, transform(new Vector3f(0, -CENTRE_Y * s, 0), new Quaternionf().rotateZ(spin + 0.35f), s), 3));
        later(4, () -> grow(b, transform(new Vector3f(0, -CENTRE_Y * s * 0.8f, 0), new Quaternionf().rotateZ(spin + 0.6f), s * 0.8f),
                life - 4));
        fade(b, life / 2, life);
    }

    @Override
    public void orb(Location at, Location to, Color color, double size, int ticks) {
        int life = Math.max(4, ticks);
        float s = (float) Math.max(0.1, size) / GLYPH;
        TextDisplay o = spawn(at, ORB, color, Display.Billboard.CENTER,
                transform(new Vector3f(0, -CENTRE_Y * s * 0.4f, 0), new Quaternionf(), s * 0.4f), 2.0f);
        if (o == null) {
            return;
        }
        later(1, () -> grow(o, transform(new Vector3f(0, -CENTRE_Y * s, 0), new Quaternionf(), s), 3));
        for (int t = 5; t < life - 2; t += 4) {
            float k = (t / 4) % 2 == 0 ? 0.86f : 1.0f;
            later(t, () -> grow(o, transform(new Vector3f(0, -CENTRE_Y * s * k, 0), new Quaternionf(), s * k), 4));
        }
        if (to != null) {
            later(1, () -> glide(o, to, life - 1));
        }
        fade(o, life - 3, life);
    }

    @Override
    public void spark(Location at, Color color, double size) {
        float s = (float) Math.max(0.1, size) / GLYPH;
        TextDisplay p = spawn(at, SPARK, color, Display.Billboard.CENTER,
                transform(new Vector3f(), new Quaternionf(), 0.01f), 1.5f);
        if (p == null) {
            return;
        }
        later(1, () -> grow(p, transform(new Vector3f(0, -CENTRE_Y * s, 0), new Quaternionf().rotateZ(0.4f), s), 3));
        later(5, () -> grow(p, transform(new Vector3f(), new Quaternionf().rotateZ(0.8f), 0.01f), 4));
        later(10, () -> kill(p));
    }

    @Override
    public void pillar(Location base, Color color, double radius, double height, int ticks) {
        int life = Math.max(8, ticks);
        float sx = (float) Math.max(0.1, radius * 2.0) / W_PILLAR;
        float sy = (float) Math.max(0.5, height) / GLYPH;
        float h = (float) Math.max(0.5, height);
        Vector3f[][] planes = {
                {new Vector3f(1, 0, 0), new Vector3f(0, 0, 1)},
                {new Vector3f(-1, 0, 0), new Vector3f(0, 0, -1)},
                {new Vector3f(0, 0, 1), new Vector3f(-1, 0, 0)},
                {new Vector3f(0, 0, -1), new Vector3f(1, 0, 0)},
        };
        for (Vector3f[] pl : planes) {
            Quaternionf q = basis(pl[0], pl[1]);
            TextDisplay p = spawn(base, PILLAR, color, Display.Billboard.FIXED,
                    plane(q, new Vector3f(0, h * 0.04f, 0), sx * 0.6f, sy * 0.08f), 2.0f);
            if (p == null) {
                return;
            }
            later(1, () -> grow(p, plane(q, new Vector3f(0, h * 0.5f, 0), sx, sy), 4));
            later(life - 4, () -> grow(p, plane(q, new Vector3f(0, h * 0.55f, 0), sx * 0.3f, sy * 1.1f), 4));
            fade(p, life - 4, life);
        }
    }

    @Override
    public void spiral(Location base, Color color, double radius, double height, int ticks, int arms) {
        int life = Math.max(10, ticks);
        int armCount = Math.max(1, Math.min(4, arms));
        int perArm = 5;
        Color core = mix(color, Color.WHITE, 0.55);
        java.util.List<TextDisplay> flames = new java.util.ArrayList<>();
        java.util.List<double[]> slots = new java.util.ArrayList<>();
        for (int a = 0; a < armCount; a++) {
            for (int k = 0; k < perArm; k++) {
                double f = (k + 0.5) / perArm;
                double phase = a * Math.PI * 2 / armCount + f * Math.PI * 1.6;
                float size = (float) (0.55 + 0.6 * (1.0 - f)) * (float) Math.max(0.6, radius * 0.6);
                Location at = helix(base, radius, height, f, phase);
                TextDisplay fl = spawn(at, k % 2 == 0 ? FLAME : FLAME_CORE, k % 2 == 0 ? color : core,
                        Display.Billboard.VERTICAL, flameTransform(size * 0.3f), 1.5f);
                if (fl == null) {
                    break;
                }
                fl.setTeleportDuration(3);
                flames.add(fl);
                slots.add(new double[] {f, phase, size});
                later(1, () -> grow(fl, flameTransform(size), 4));
            }
        }
        double spin = Math.PI * 2 / 24;   // a full turn in 24 steps of 3 ticks
        for (int t = 3, step = 1; t < life; t += 3, step++) {
            final int n = step;
            later(t, () -> {
                for (int i = 0; i < flames.size(); i++) {
                    TextDisplay fl = flames.get(i);
                    if (fl.isValid()) {
                        double[] sl = slots.get(i);
                        fl.teleport(helix(base, radius, height, sl[0], sl[1] + spin * n));
                    }
                }
            });
        }
        for (int i = 0; i < flames.size(); i++) {
            TextDisplay fl = flames.get(i);
            float size = (float) slots.get(i)[2];
            later(life - 4, () -> grow(fl, flameTransform(size * 0.2f), 4));
            later(life, () -> kill(fl));
        }
    }

    @Override
    public void wave(Location centre, Color color, double radius, int ticks) {
        int life = Math.max(6, ticks);
        float end = (float) (radius * 2.0) / GLYPH;
        TextDisplay w = spawn(centre, WAVE, color, Display.Billboard.FIXED, flat(end * 0.12f, 0f, 0.07f), 1.5f);
        if (w == null) {
            return;
        }
        later(1, () -> grow(w, flat(end, 0.4f, 0.07f), (int) (life * 0.75)));
        fade(w, life / 2, life);
    }

    @Override
    public void flame(Location at, Color color, double size, int ticks) {
        int life = Math.max(6, ticks);
        float s = (float) Math.max(0.2, size);
        Color hot = mix(color, Color.WHITE, 0.55);
        TextDisplay body = spawn(at, FLAME, color, Display.Billboard.VERTICAL, flameTransform(s * 0.3f), 1.5f);
        TextDisplay core = spawn(at.clone().add(0, 0.01, 0), FLAME_CORE, hot, Display.Billboard.VERTICAL,
                flameTransform(s * 0.2f), 1.5f);
        for (TextDisplay fl : new TextDisplay[] {body, core}) {
            if (fl == null) {
                continue;
            }
            float base = fl == core ? s * 0.62f : s;
            later(1, () -> grow(fl, flameTransform(base), 3));
            for (int t = 4; t < life - 3; t += 3) {
                float k = 0.88f + (float) java.util.concurrent.ThreadLocalRandom.current().nextDouble(0.24);
                later(t, () -> grow(fl, flameTransform(base * k), 3));
            }
            later(life - 3, () -> grow(fl, flameTransform(base * 0.15f), 3));
            later(life, () -> kill(fl));
        }
    }

    @Override
    public void ribbon(Location centre, Color color, double width, double height, float yaw, int ticks) {
        int life = Math.max(20, ticks);
        float sx = (float) Math.max(0.5, width) / W_RIBBON;
        float sy = (float) Math.max(0.5, height) / GLYPH;
        double rad = Math.toRadians(yaw);
        Vector3f along = new Vector3f((float) Math.cos(rad), 0, (float) Math.sin(rad));
        for (int side = 0; side < 2; side++) {
            Vector3f d = side == 0 ? new Vector3f(along) : new Vector3f(along).negate();
            Vector3f n = new Vector3f(d).cross(UP);                // n x d = up, so the band hangs upright
            Quaternionf q = basis(d, n);
            TextDisplay r = spawn(centre, RIBBON, color, Display.Billboard.FIXED, plane(q, new Vector3f(), sx, sy * 0.6f), 4.0f);
            if (r == null) {
                return;
            }
            r.setTextOpacity((byte) 40);
            later(1, () -> {
                if (r.isValid()) {
                    r.setTextOpacity((byte) 200);
                }
                grow(r, plane(q, new Vector3f(), sx, sy), 20);
            });
            for (int t = 21, k = 0; t < life - 20; t += 30, k++) {
                float sway = (k % 2 == 0 ? 1f : -1f) * 0.12f;
                Quaternionf qs = new Quaternionf().rotateY(sway).mul(q);
                later(t, () -> grow(r, plane(qs, new Vector3f(0, sway, 0), sx * 1.03f, sy), 30));
            }
            later(life - 20, () -> {
                if (r.isValid()) {
                    r.setTextOpacity((byte) 60);
                }
                grow(r, plane(q, new Vector3f(0, 0.5f, 0), sx, sy * 1.15f), 20);
            });
            later(life, () -> kill(r));
        }
    }

    @Override
    public void motes(Location at, Color color, int count, double spread, org.bukkit.util.Vector drift, int ticks) {
        int life = Math.max(6, Math.min(59, ticks));
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < Math.min(8, count); i++) {
            Location from = at.clone().add(rng.nextGaussian() * spread, rng.nextGaussian() * spread * 0.5, rng.nextGaussian() * spread);
            float s = (float) (0.12 + rng.nextDouble() * 0.1) / GLYPH;
            TextDisplay m = spawn(from, MOTE, color, Display.Billboard.CENTER,
                    transform(new Vector3f(0, -CENTRE_Y * s, 0), new Quaternionf(), s), 1.0f);
            if (m == null) {
                return;
            }
            if (drift != null) {
                Location to = from.clone().add(drift.clone().multiply(0.7 + rng.nextDouble() * 0.6));
                later(1, () -> glide(m, to, life - 1));
            }
            fade(m, life / 2, life);
        }
    }

    @Override
    public void arc(Location centre, Color color, double radius, float yaw, int ticks) {
        int life = Math.max(20, ticks);
        // the arc glyph is 1.6 x 0.8 at scale 1: its ring has radius ~0.74 with the centre on the bottom edge
        float s = (float) Math.max(0.5, radius) / 0.74f;
        double rad = Math.toRadians(yaw);
        Vector3f along = new Vector3f((float) Math.cos(rad), 0, (float) Math.sin(rad));
        for (int side = 0; side < 2; side++) {
            Vector3f d = side == 0 ? new Vector3f(along) : new Vector3f(along).negate();
            Quaternionf q = basis(d, new Vector3f(d).cross(UP));
            // centre of the glyph sits half its height above the ring's centre
            Vector3f c = new Vector3f(0, 0.4f * s, 0);
            TextDisplay a = spawn(centre, ARC, color, Display.Billboard.FIXED, plane(q, c, s, s), 4.0f);
            if (a == null) {
                return;
            }
            a.setTextOpacity((byte) 50);
            later(2, () -> {
                if (a.isValid()) {
                    a.setTextOpacity((byte) 170);
                }
            });
            later(life - 30, () -> {
                if (a.isValid()) {
                    a.setTextOpacity((byte) 80);
                }
            });
            later(life, () -> kill(a));
        }
    }

    @Override
    public void mist(Location at, Color color, double size, org.bukkit.util.Vector drift, int ticks) {
        int life = Math.max(20, ticks);
        float s = (float) Math.max(1.0, size) / GLYPH;
        TextDisplay m = spawn(at, ORB, color, Display.Billboard.CENTER,
                transform(new Vector3f(0, -CENTRE_Y * s * 0.6f, 0), new Quaternionf(), s * 0.6f), 2.5f);
        if (m == null) {
            return;
        }
        m.setTextOpacity((byte) 40);
        later(1, () -> grow(m, transform(new Vector3f(0, -CENTRE_Y * s, 0), new Quaternionf(), s), 40));
        if (drift != null) {
            int legs = Math.max(1, life / 40);
            for (int k = 0; k < legs; k++) {
                Location to = at.clone().add(drift.clone().multiply((k + 1) / (double) legs));
                later(1 + k * 40, () -> glide(m, to, 40));
            }
        }
        later(life - 30, () -> grow(m, transform(new Vector3f(0, -CENTRE_Y * s * 0.7f, 0), new Quaternionf(), s * 0.7f), 30));
        later(life, () -> kill(m));
    }

    @Override
    public void chips(Location at, org.bukkit.block.data.BlockData block, int count, double power) {
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        float p = (float) Math.max(0.3, Math.min(2.5, power));
        for (int i = 0; i < Math.min(12, count); i++) {
            if (live >= MAX_LIVE || at.getWorld() == null || !at.isChunkLoaded()) {
                return;
            }
            float s = (float) (0.14 + rng.nextDouble() * 0.14);
            double yaw = rng.nextDouble(Math.PI * 2);
            float dx = (float) Math.cos(yaw), dz = (float) Math.sin(yaw);
            float out = p * (0.6f + rng.nextFloat() * 0.9f);
            Quaternionf spin0 = new Quaternionf().rotateXYZ(rng.nextFloat() * 3f, rng.nextFloat() * 3f, rng.nextFloat() * 3f);
            Quaternionf spin1 = new Quaternionf(spin0).rotateXYZ(2.2f, 1.4f, 0.8f);
            Quaternionf spin2 = new Quaternionf(spin1).rotateXYZ(2.0f, 1.9f, 1.1f);
            Location spot = at.clone();
            spot.setYaw(0f);
            spot.setPitch(0f);
            org.bukkit.entity.BlockDisplay chip = at.getWorld().spawn(spot, org.bukkit.entity.BlockDisplay.class, e -> {
                e.setBlock(block);
                e.setPersistent(false);
                e.addScoreboardTag(TAG);
                e.setViewRange(0.6f);
                e.setBrightness(null);
                e.setTransformation(new Transformation(new Vector3f(-s / 2, 0, -s / 2), spin0, new Vector3f(s, s, s), new Quaternionf()));
            });
            live++;
            float up = 0.6f + rng.nextFloat() * 0.7f * p;
            later(1, () -> moveChip(chip, new Vector3f(dx * out * 0.55f - s / 2, up, dz * out * 0.55f - s / 2), spin1, s, 5));
            later(6, () -> moveChip(chip, new Vector3f(dx * out - s / 2, -0.15f, dz * out - s / 2), spin2, s * 0.55f, 8));
            later(15, () -> {
                if (chip.isValid()) {
                    chip.remove();
                    live = Math.max(0, live - 1);
                }
            });
        }
    }

    private static void moveChip(org.bukkit.entity.BlockDisplay chip, Vector3f to, Quaternionf spin, float s, int ticks) {
        if (!chip.isValid()) {
            return;
        }
        chip.setInterpolationDelay(0);
        chip.setInterpolationDuration(ticks);
        chip.setTransformation(new Transformation(to, spin, new Vector3f(s, s, s), new Quaternionf()));
    }

    /* ------------------------------------------------------------------ shape helpers */

    private static Vector3f between(Location from, Location to) {
        return new Vector3f((float) (to.getX() - from.getX()), (float) (to.getY() - from.getY()),
                (float) (to.getZ() - from.getZ()));
    }

    /** Four planes around a direction (two crossed, each drawn front and back) so a flat glyph reads from anywhere. */
    private static Vector3f[] crossNormals(Vector3f d) {
        Vector3f side = Math.abs(d.y) > 0.92f ? new Vector3f(1, 0, 0) : new Vector3f(UP);
        Vector3f n1 = new Vector3f(d).cross(side).normalize();
        Vector3f n2 = new Vector3f(d).cross(n1).normalize();
        return new Vector3f[] {n1, new Vector3f(n1).negate(), n2, new Vector3f(n2).negate()};
    }

    /** Rotation taking the glyph's x axis to {@code d} and its front (+z) to {@code n}. */
    private static Quaternionf basis(Vector3f d, Vector3f n) {
        Vector3f y = new Vector3f(n).cross(d);
        org.joml.Matrix3f m = new org.joml.Matrix3f(d.x, d.y, d.z, y.x, y.y, y.z, n.x, n.y, n.z);
        return new Quaternionf().setFromNormalized(m);
    }

    /** A glyph in plane {@code q}, its centre at {@code centre} (relative to the entity), scaled sx by sy. */
    private static Transformation plane(Quaternionf q, Vector3f centre, float sx, float sy) {
        Vector3f lift = q.transform(new Vector3f(0, CENTRE_Y * sy, 0));
        return new Transformation(new Vector3f(centre).sub(lift), q, new Vector3f(sx, sy, 1f), new Quaternionf());
    }

    /** Flame glyph standing on the entity's spot: its foot at the ground, {@code size} blocks tall. */
    private static Transformation flameTransform(float size) {
        float s = size / GLYPH;
        return new Transformation(new Vector3f(0, size * 0.5f - CENTRE_Y * s, 0), new Quaternionf(), new Vector3f(s, s, s),
                new Quaternionf());
    }

    private static Location helix(Location base, double radius, double height, double f, double angle) {
        return base.clone().add(Math.cos(angle) * radius, f * height, Math.sin(angle) * radius);
    }

    private void glide(TextDisplay d, Location to, int ticks) {
        if (!d.isValid()) {
            return;
        }
        d.setTeleportDuration(Math.max(1, Math.min(59, ticks)));
        d.teleport(to);
    }

    private static Color mix(Color a, Color b, double t) {
        return Color.fromRGB((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /* ------------------------------------------------------------------ plumbing */

    private TextDisplay spawn(Location at, char glyph, Color color, Display.Billboard billboard, Transformation start) {
        return spawn(at, glyph, color, billboard, start, 1.5f);
    }

    private TextDisplay spawn(Location at, char glyph, Color color, Display.Billboard billboard, Transformation start,
                              float viewRange) {
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
            e.setViewRange(viewRange);
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
