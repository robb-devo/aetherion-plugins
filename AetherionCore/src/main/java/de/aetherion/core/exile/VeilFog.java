package de.aetherion.core.exile;

import net.kyori.adventure.text.Component;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * The Veil: translucent sheets of mist (text-display backgrounds, the only see-through, fully lit surface a
 * vanilla client draws) hung across the ferry's line, plus soft dust "breath" around the camera.
 *
 * <p>Each sheet is two halves. {@link #tear} slides the halves apart and thins them, so the fog opens like a
 * curtain in front of the ferry instead of popping away.
 */
public final class VeilFog {

    public static final String TAG = "aetherion_exile_veil";

    /** Text-display background of " " is about 1/8 block wide and 1/4 block high at scale 1. */
    private static final float BASE_W = 0.125f;
    private static final float BASE_H = 0.25f;

    private static final int[] TINTS = {0xD8DCE6, 0xE4E2EC, 0xCFD6E2, 0xE8E6EE, 0xD2D0DE};

    private record Sheet(TextDisplay display, double x, int side, float width, float height) {
    }

    private final Plugin plugin;
    private final List<Sheet> sheets = new ArrayList<>();

    private VeilFog(Plugin plugin) {
        this.plugin = plugin;
    }

    static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "exile_veil");
    }

    /**
     * Planes perpendicular to x (facing east, toward an arriving camera), one every {@code spacing} blocks from
     * {@code xEast} down to {@code xWest}, each {@code width} (along z) by {@code height}.
     *
     * @param viewers null = everyone may see them (the curtain on the islet); otherwise only these players
     */
    public static VeilFog corridor(Plugin plugin, World world, Collection<? extends Player> viewers, boolean persistent,
                                   double xEast, double xWest, double spacing, double zCenter, double yCenter,
                                   double width, double height, int alphaMin, int alphaMax, long seed) {
        return corridor(plugin, world, viewers, persistent, xEast, xWest, spacing, zCenter, x -> yCenter, width, height,
                alphaMin, alphaMax, seed);
    }

    /** As above, the sheets' height centre follows {@code yAt(x)} (a descending ferry line). */
    public static VeilFog corridor(Plugin plugin, World world, Collection<? extends Player> viewers, boolean persistent,
                                   double xEast, double xWest, double spacing, double zCenter,
                                   java.util.function.DoubleUnaryOperator yAt,
                                   double width, double height, int alphaMin, int alphaMax, long seed) {
        VeilFog fog = new VeilFog(plugin);
        Random rnd = new Random(seed);
        for (double x = xEast; x >= xWest; x -= spacing) {
            double jitterX = (rnd.nextDouble() - 0.5) * spacing * 0.5;
            double jz = (rnd.nextDouble() - 0.5) * width * 0.25;
            double jy = (rnd.nextDouble() - 0.5) * height * 0.25;
            float w = (float) (width * (0.8 + rnd.nextDouble() * 0.4));
            float h = (float) (height * (0.8 + rnd.nextDouble() * 0.4));
            int alpha = alphaMin + rnd.nextInt(Math.max(1, alphaMax - alphaMin + 1));
            int tint = TINTS[rnd.nextInt(TINTS.length)];
            Location at = new Location(world, x + jitterX, yAt.applyAsDouble(x) + jy, zCenter + jz);
            for (int side = -1; side <= 1; side += 2) {
                fog.sheet(at, side, w, h, alpha, tint, viewers, persistent);
            }
        }
        return fog;
    }

    private void sheet(Location at, int side, float w, float h, int alpha, int rgb, Collection<? extends Player> viewers, boolean persistent) {
        NamespacedKey key = key(plugin);
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setPersistent(persistent);
            t.setVisibleByDefault(viewers == null);
            t.addScoreboardTag(TAG);
            t.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            t.setRotation(0f, 0f);
            t.text(Component.text(" "));
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(alpha, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
            t.setShadowed(false);
            t.setSeeThrough(false);
            t.setBillboard(Display.Billboard.FIXED);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setViewRange(3.0f);
            t.setTransformation(rest(side, w, h));
        });
        if (viewers != null) {
            for (Player p : viewers) {
                p.showEntity(plugin, d);
            }
        }
        sheets.add(new Sheet(d, at.getX(), side, w, h));
    }

    /** Plane faces +x; its width runs along z. Half {@code side} covers z on that side of the sheet's center. */
    private static Transformation rest(int side, float w, float h) {
        Quaternionf face = new Quaternionf().rotateY((float) (Math.PI / 2));
        float half = w / 2f;
        Vector3f scale = new Vector3f(half / BASE_W, h / BASE_H, 1f);
        // local x maps to world -z after the 90 degree turn
        Vector3f t = new Vector3f(0f, -h / 2f, side * half / 2f);
        return new Transformation(t, face, scale, new Quaternionf());
    }

    /** Opens every sheet west of {@code westOf}: halves slide apart and thin out over {@code ticks}. */
    public void tear(double westOf, int ticks) {
        for (Sheet s : sheets) {
            if (s.x() > westOf || !s.display().isValid()) {
                continue;
            }
            TextDisplay d = s.display();
            Transformation now = d.getTransformation();
            Vector3f t = new Vector3f(now.getTranslation());
            t.z += s.side() * s.width() * 0.75f;
            t.y += s.height() * 0.08f;
            Vector3f scale = new Vector3f(now.getScale());
            scale.x *= 0.12f;
            scale.y *= 0.55f;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(new Transformation(t, now.getLeftRotation(), scale, now.getRightRotation()));
        }
    }

    /** Adds the sheets of {@code other} to this fog (one handle to tear / remove). */
    public VeilFog with(VeilFog other) {
        if (other != null) {
            sheets.addAll(other.sheets);
            other.sheets.clear();
        }
        return this;
    }

    public boolean isEmpty() {
        return sheets.isEmpty();
    }

    public void remove() {
        for (Sheet s : sheets) {
            try {
                s.display().remove();
            } catch (Throwable ignored) {
            }
        }
        sheets.clear();
    }

    /** Removes left-over Veil sheets (crash, reload) in loaded chunks of {@code world}. */
    public static int purge(Plugin plugin, World world) {
        if (world == null) {
            return 0;
        }
        NamespacedKey key = key(plugin);
        int n = 0;
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains(TAG) || e.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                e.remove();
                n++;
            }
        }
        return n;
    }

    /** Big soft dust blobs drifting around {@code at}: the Veil breathing, for one viewer. */
    public static void breathe(Player viewer, Location at, double radius, int count, float size) {
        Particle.DustOptions mist = new Particle.DustOptions(Color.fromRGB(226, 228, 238), size);
        Particle.DustOptions shade = new Particle.DustOptions(Color.fromRGB(196, 200, 216), size * 0.8f);
        for (int i = 0; i < count; i++) {
            double dx = (Math.random() - 0.5) * 2 * radius;
            double dy = (Math.random() - 0.5) * radius * 0.8;
            double dz = (Math.random() - 0.5) * 2 * radius;
            viewer.spawnParticle(Particle.DUST, at.getX() + dx, at.getY() + dy, at.getZ() + dz, 1, 0, 0, 0, 0,
                    i % 3 == 0 ? shade : mist);
        }
    }
}
