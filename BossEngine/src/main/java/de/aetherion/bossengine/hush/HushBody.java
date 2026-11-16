package de.aetherion.bossengine.hush;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hush's shape: a bell with no tongue, sewn out of dark. Seven rings of thin black plates, wide at
 * the hem and narrow at the crown, with a pale spot where a clapper would hang.
 *
 * <p>Every plate is its own block display. Its turn around the axis is baked into its transformation
 * once; everything that moves afterwards (drift, bob, the swaying hem) is plain teleports with client
 * interpolation, so nothing here depends on entity yaw or on passengers.
 */
final class HushBody {

    /** height, radius, plates, plate height. */
    private static final float[][] RINGS = {
            {0.35f, 2.05f, 11, 0.95f},
            {1.05f, 1.80f, 10, 0.90f},
            {1.80f, 1.50f, 9, 0.90f},
            {2.55f, 1.25f, 8, 0.90f},
            {3.30f, 1.10f, 7, 0.90f},
            {4.05f, 0.95f, 6, 0.90f},
            {4.70f, 0.60f, 5, 0.75f},
    };
    static final float HEIGHT = 5.3f;
    private static final Color REVEAL_GLOW = Color.fromRGB(255, 214, 140);
    private static final Display.Brightness DARK = new Display.Brightness(0, 0);
    private static final Display.Brightness SEEN = new Display.Brightness(10, 0);

    private static final class Plate {
        BlockDisplay display;
        int ring;
        float angle;
        float radius;
        float y;
        Vector3f size;
        Quaternionf turn;
    }

    private final HushArena arena;
    private final boolean echo;
    private final List<Plate> plates = new ArrayList<>();
    private BlockDisplay heart;
    private double x;
    private double y;
    private double z;
    private float grown;
    private boolean revealed;
    private boolean spawned;
    private int clock;

    /**
     * @param echo a thinner copy (every second plate, no heart) for the Compline echoes
     */
    HushBody(HushArena arena, boolean echo) {
        this.arena = arena;
        this.echo = echo;
    }

    /* ================================================================== life */

    /** Spawns the plates folded to nothing at this arena point; {@link #grow} unfolds them. */
    void spawn(double ax, double ay, double az) {
        if (spawned) {
            return;
        }
        spawned = true;
        x = ax;
        y = ay;
        z = az;
        grown = 0f;
        Location base = arena.at(ax, ay, az);
        for (int r = 0; r < RINGS.length; r++) {
            int n = (int) RINGS[r][2];
            float radius = RINGS[r][1];
            float width = (float) (Math.PI * 2 * radius / n) * 1.3f;
            Material skin = r == 0 ? Material.TINTED_GLASS : r % 2 == 0 ? Material.BLACK_CONCRETE : Material.BLACK_WOOL;
            for (int i = 0; i < n; i++) {
                if (echo && (i + r) % 2 == 1) {
                    continue;
                }
                Plate p = new Plate();
                p.ring = r;
                p.angle = (float) (i * Math.PI * 2 / n + r * 0.31);
                p.radius = radius;
                p.y = RINGS[r][0];
                p.size = new Vector3f(width, RINGS[r][3], 0.14f);
                // Model Z (the thin side) points out along the radius.
                p.turn = new Quaternionf().rotateY((float) (Math.PI / 2 - p.angle));
                p.display = display(base, skin);
                p.display.setTransformation(shape(p, 0.001f));
                plates.add(p);
            }
        }
        if (!echo) {
            heart = display(base, Material.SEA_LANTERN);
            heart.setBrightness(new Display.Brightness(15, 15));
            heart.setTransformation(cube(0.001f));
        }
    }

    private BlockDisplay display(Location at, Material skin) {
        return arena.world().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(skin.createBlockData());
            d.setPersistent(false);
            d.setBrightness(DARK);
            d.setViewRange(1.6f);
            d.setTeleportDuration(3);
            d.setInterpolationDuration(0);
            d.setShadowRadius(0f);
            d.addScoreboardTag(HushModule.TAG);
        });
    }

    private static Transformation shape(Plate p, float scale) {
        Vector3f size = new Vector3f(p.size).mul(scale);
        Vector3f offset = p.turn.transform(new Vector3f(size).mul(-0.5f));
        return new Transformation(offset, new Quaternionf(p.turn), size, new Quaternionf());
    }

    private static Transformation cube(float edge) {
        return new Transformation(new Vector3f(-edge / 2f), new Quaternionf(), new Vector3f(edge), new Quaternionf());
    }

    /** Unfolds (1) or folds away (0) over {@code ticks}. */
    void grow(float to, int ticks) {
        grown = to;
        for (Plate p : plates) {
            if (p.display.isValid()) {
                p.display.setInterpolationDelay(0);
                p.display.setInterpolationDuration(Math.max(1, ticks));
                p.display.setTransformation(shape(p, Math.max(0.001f, to)));
            }
        }
        heart(revealed ? 0.55f : 0.001f, ticks);
    }

    private void heart(float edge, int ticks) {
        if (heart != null && heart.isValid()) {
            heart.setInterpolationDelay(0);
            heart.setInterpolationDuration(Math.max(1, ticks));
            heart.setTransformation(cube(Math.max(0.001f, edge * grown)));
        }
    }

    /** In the light the plates catch it and the pale spot inside shows. */
    void revealed(boolean on) {
        if (revealed == on) {
            return;
        }
        revealed = on;
        for (Plate p : plates) {
            if (p.display.isValid()) {
                p.display.setBrightness(on ? SEEN : DARK);
                p.display.setGlowColorOverride(REVEAL_GLOW);
                p.display.setGlowing(on && p.ring == RINGS.length - 1);
            }
        }
        heart(on ? 0.55f : 0.001f, 6);
    }

    boolean revealed() {
        return revealed;
    }

    void remove() {
        for (Plate p : plates) {
            if (p.display != null) {
                p.display.remove();
            }
        }
        plates.clear();
        if (heart != null) {
            heart.remove();
            heart = null;
        }
        spawned = false;
    }

    boolean spawned() {
        return spawned;
    }

    /* ================================================================== motion */

    void moveTo(double ax, double ay, double az) {
        x = ax;
        y = ay;
        z = az;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    /** The middle of the shape (for particles and sounds). */
    Location chest() {
        return arena.at(x, y + 2.6, z);
    }

    /**
     * Places the plates. Call every tick; the plates only move every second tick (the client blends).
     *
     * @param shiver 0 = calm, 1 = shaking (staggered, lulled, dying)
     */
    void tick(float shiver) {
        clock++;
        if (!spawned || clock % 2 != 0) {
            return;
        }
        double bob = Math.sin(clock * 0.07) * 0.12;
        for (Plate p : plates) {
            if (!p.display.isValid()) {
                continue;
            }
            // The hem breathes and trails; the crown barely moves.
            float loose = (RINGS.length - p.ring) / (float) RINGS.length;
            double sway = 1.0 + 0.07 * loose * Math.sin(clock * 0.11 + p.angle * 3.0 + p.ring);
            double shake = shiver <= 0f ? 0.0 : (Math.random() - 0.5) * 0.18 * shiver;
            double r = p.radius * sway * grown;
            double px = x + Math.cos(p.angle) * r + shake;
            double pz = z + Math.sin(p.angle) * r + shake;
            double py = y + (p.y + bob * (1.0 - loose * 0.5)) * grown;
            p.display.teleport(arena.at(px, py, pz));
        }
        if (heart != null && heart.isValid()) {
            heart.teleport(arena.at(x, y + (2.6 + bob) * grown, z));
        }
    }
}
