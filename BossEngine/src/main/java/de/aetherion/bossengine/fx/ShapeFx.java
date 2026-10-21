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

    /**
     * The burst a particle call stood for, drawn as a shape sized from its spread; the particles themselves when no
     * renderer runs. {@code data} is the particle's data (block data for crumbs, dust options), may be null.
     */
    public static void puff(Location at, Particle particle, int count, double spread, Object data) {
        if (at == null || at.getWorld() == null || particle == null) {
            return;
        }
        if (!Vfx.active()) {
            fallback(at, particle, count, spread, data);
            return;
        }
        double size = Math.max(0.5, Math.min(4.0, spread * 2.0 + Math.sqrt(Math.max(1, count)) * 0.15));
        Color color = data instanceof Particle.DustOptions dust ? dust.getColor() : tint(particle);
        switch (particle) {
            case EXPLOSION, EXPLOSION_EMITTER, FLASH, SONIC_BOOM -> {
                Vfx.burst(at, particle == Particle.FLASH ? PALE : Color.fromRGB(0xFFC070), size * 1.4, 10);
                Vfx.wave(ground(at), particle == Particle.SONIC_BOOM ? SOUL : PALE, size * 1.3, 12);
            }
            case FLAME, SMALL_FLAME, SOUL_FIRE_FLAME, LAVA -> {
                int n = Math.max(1, Math.min(4, count / 6));
                for (int i = 0; i < n; i++) {
                    Location f = jitter(at, spread * 0.6);
                    Vfx.flame(f, color, 0.5 + size * 0.25, 12 + i * 2);
                }
            }
            case SOUL, SCULK_SOUL, WHITE_ASH, ASH, END_ROD, GLOW, WAX_ON, WAX_OFF, ENCHANT, TOTEM_OF_UNDYING, SNOWFLAKE,
                 SPORE_BLOSSOM_AIR, FALLING_DUST, MYCELIUM ->
                    Vfx.motes(at, color, Math.max(2, Math.min(6, count / 4)), Math.max(0.2, spread * 0.7),
                            new Vector(0, 1.2 + spread, 0), 22);
            case PORTAL, REVERSE_PORTAL, WITCH, DRAGON_BREATH, SQUID_INK -> {
                Vfx.orb(at, null, color, size * 0.8, 12);
                Vfx.motes(at, color, 3, Math.max(0.2, spread * 0.6), new Vector(0, 1.0, 0), 18);
            }
            case CRIT, ENCHANTED_HIT, DAMAGE_INDICATOR, ELECTRIC_SPARK, FIREWORK -> Vfx.spark(at, color, Math.max(0.6, size * 0.6));
            case CLOUD, POOF, SMOKE, LARGE_SMOKE, CAMPFIRE_COSY_SMOKE, CAMPFIRE_SIGNAL_SMOKE, GUST, SMALL_GUST, SWEEP_ATTACK ->
                    Vfx.wave(ground(at), color, size, 10);
            case SPLASH, BUBBLE, BUBBLE_POP, BUBBLE_COLUMN_UP, FALLING_WATER, DRIPPING_WATER ->
                    Vfx.wave(ground(at), WATER, size, 10);
            case BLOCK, DUST_PILLAR -> {
                if (data instanceof BlockData block) {
                    Vfx.chips(at, block, Math.max(3, Math.min(10, count / 4)), Math.max(0.6, spread * 1.6));
                }
            }
            case DUST, DUST_COLOR_TRANSITION -> Vfx.spark(at, color, Math.max(0.5, size * 0.5));
            default -> Vfx.spark(at, color, Math.max(0.5, size * 0.5));
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
