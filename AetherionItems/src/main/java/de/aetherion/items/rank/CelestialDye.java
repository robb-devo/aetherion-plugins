package de.aetherion.items.rank;

import org.bukkit.Bukkit;

/**
 * Hypixel SkyBlock–style celestial dye: cyan → pink → purple → gold, shifting.
 * Output uses TAB / LuckPerms hex ({@code &#RRGGBB}) so %aetherion_tab_prefix%
 * and TAB animations can render the same palette.
 */
public final class CelestialDye {

    /** Cyan → pink → purple → gold loop (one color per letter of "Monkey"). */
    private static final int[] PALETTE = {
            0x55FFFF,
            0x7CEBFF,
            0xB89AFF,
            0xFF7AEE,
            0xFF8A88,
            0xFFC15A,
            0xFFE08A
    };

    public static final String TAB_ANIMATION = "Aetherion_Monkey";

    private CelestialDye() {
    }

    /** Current animated {@code [Monkey]} badge (TAB hex). */
    public static String monkeyBadge() {
        int tick = 0;
        try {
            tick = Bukkit.getCurrentTick();
        } catch (IllegalStateException ignored) {
        }
        return monkeyBadge(Math.floorDiv(Math.max(0, tick), 4));
    }

    public static String monkeyBadge(int frame) {
        return rainbow("[Monkey]", frame) + " ";
    }

    /** Static frame 0 — safe for LuckPerms group prefix (no tick). */
    public static String monkeyPrefixStatic() {
        return rainbow("[Monkey]", 0) + "&f ";
    }

    public static String rainbow(String text, int shift) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int colorIndex = Math.floorMod(shift, PALETTE.length);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(hex(PALETTE[colorIndex])).append(c);
            if (!Character.isWhitespace(c) && c != '[' && c != ']') {
                colorIndex = (colorIndex + 1) % PALETTE.length;
            }
        }
        return out.toString();
    }

    public static String[] animationFrames() {
        String[] frames = new String[PALETTE.length];
        for (int i = 0; i < PALETTE.length; i++) {
            frames[i] = rainbow("[Monkey]", i);
        }
        return frames;
    }

    private static String hex(int rgb) {
        return String.format("&#%06X", rgb & 0xFFFFFF);
    }
}
