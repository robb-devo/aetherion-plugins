package de.aetherion.items.rank;

import org.bukkit.Bukkit;

/**
 * Original Homie multi-letter rainbow (cyan → pink → purple → gold).
 * Used by <strong>Beta Tester only</strong>. Monkey stays on
 * {@link CelestialDye} {@code #B2FFFF}. Citrus stays on {@link CitrusDye}.
 */
public final class RainbowDye {

    public static final String TAB_ANIMATION = "Aetherion_Rainbow";

    /** First Monkey rainbow — flashy cyan / pink / purple / gold. */
    private static final int[] PALETTE = {
            0x55FFFF,
            0x7CEBFF,
            0xB89AFF,
            0xFF7AEE,
            0xFF8A88,
            0xFFC15A,
            0xFFE08A
    };

    private RainbowDye() {
    }

    public static int[] palette() {
        return PALETTE.clone();
    }

    public static boolean isRainbowGroup(String group) {
        return group != null && "beta".equalsIgnoreCase(group);
    }

    public static String badge() {
        return badge(currentFrame());
    }

    public static String badge(int frame) {
        return rainbow("[Beta]", frame) + " ";
    }

    public static String prefixStatic() {
        return rainbow("[Beta]", 0) + "&f ";
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
            frames[i] = rainbow("[Beta]", i);
        }
        return frames;
    }

    private static int currentFrame() {
        int tick = 0;
        try {
            tick = Bukkit.getCurrentTick();
        } catch (IllegalStateException | ExceptionInInitializerError | NullPointerException ignored) {
        }
        return Math.floorDiv(Math.max(0, tick), 4);
    }

    public static String hex(int rgb) {
        return String.format("&#%06X", rgb & 0xFFFFFF);
    }
}
