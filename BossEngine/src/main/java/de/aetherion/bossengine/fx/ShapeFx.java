package de.aetherion.bossengine.fx;

import de.aetherion.core.api.Vfx;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Boss effects as shapes, not particle clouds: one visual language for every active boss (Core {@link Vfx},
 * drawn by Items' display renderer). Each helper falls back to the old particles when no renderer runs.
 *
 * <ul>
 *   <li>{@link #ring}: a held ground ring for code that redraws a circle every tick or two.</li>
 *   <li>{@link #puff}: what a particle burst meant, as a shape: smoke and dust become a ground wave, fire a ring of
 *   flame tongues, souls and embers rising motes, sparks a twinkle or arcs, explosions a star burst and a shockwave,
 *   magic a glowing orb, block crumbs real flying chips.</li>
 * </ul>
 */
public final class ShapeFx {

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


    private ShapeFx() {
    }

    /** A held ring in this colour; false when the caller should draw its particles. */
    public static boolean ring(Location centre, Color color, double radius) {
        return Vfx.hold(centre, color, radius);
    }

    /** A held ring in the colour the particle stood for. */
    public static boolean ring(Location centre, Particle particle, double radius) {
        return Vfx.hold(centre, tint(particle), radius);
    }

    /** {@link Vfx#puff}: the shared particle-to-shape translator. */
    public static void puff(Location at, Particle particle, int count, double spread, Object data) {
        Vfx.puff(at, particle, count, spread, data);
    }

    /** {@link #puff} without particle data. */
    public static void puff(Location at, Particle particle, int count, double spread) {
        puff(at, particle, count, spread, null);
    }

    /** {@link Vfx#tint}. */
    public static Color tint(Particle particle) {
        return Vfx.tint(particle);
    }
}
