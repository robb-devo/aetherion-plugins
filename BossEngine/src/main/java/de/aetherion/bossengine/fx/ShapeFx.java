package de.aetherion.bossengine.fx;

import de.aetherion.core.api.Vfx;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

/**
 * Boss rings as shapes, not particle dots: the ring helpers of the directors ask for a held display ring first
 * ({@link Vfx#hold}) and only fall back to their particle loop when no display renderer runs.
 */
public final class ShapeFx {

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

    /** The colour a particle-drawn ring reads as. */
    static Color tint(Particle particle) {
        if (particle == null) {
            return Color.fromRGB(0xF2EEE6);
        }
        return switch (particle) {
            case FLAME, SMALL_FLAME, LAVA, DRIPPING_LAVA, FALLING_LAVA, LANDING_LAVA -> Color.fromRGB(0xFF7A2A);
            case SOUL_FIRE_FLAME, SOUL, SCULK_SOUL -> Color.fromRGB(0x5FD8E8);
            case WITCH, PORTAL, REVERSE_PORTAL, DRAGON_BREATH, ENCHANT -> Color.fromRGB(0xB46BFF);
            case CRIT, ENCHANTED_HIT, WAX_ON, WAX_OFF -> Color.fromRGB(0xFFD86B);
            case ELECTRIC_SPARK, FIREWORK -> Color.fromRGB(0xFFF1A0);
            case SNOWFLAKE, ITEM_SNOWBALL -> Color.fromRGB(0xBFE8FF);
            case HAPPY_VILLAGER, COMPOSTER, SPORE_BLOSSOM_AIR -> Color.fromRGB(0x7EE07A);
            case DAMAGE_INDICATOR, ANGRY_VILLAGER, DUST -> Color.fromRGB(0xFF5A4A);
            case SMOKE, LARGE_SMOKE, ASH, SQUID_INK -> Color.fromRGB(0x5A5560);
            default -> Color.fromRGB(0xF2EEE6);
        };
    }
}
