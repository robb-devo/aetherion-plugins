package de.aetherion.items.rank;

import org.bukkit.Bukkit;

/**
 * Homie citrus dye — lime, yellow, and orange shimmer.
 * <strong>Citrus only</strong>. Monkey stays on {@link CelestialDye} {@code #B2FFFF}.
 * Beta stays on {@link RainbowDye}.
 * <p>
 * Output uses TAB / LuckPerms hex ({@code &#RRGGBB}) so
 * {@code %aetherion_tab_prefix%} and {@code %aetherion_chat_prefix%} render the same palette.
 */
public final class CitrusDye {

    /** Anchor stop. Bright citrus yellow, not ice-cyan and not rainbow pink. */
    public static final int PRIMARY = 0xFFE14A;

    /** Copy into {@code plugins/TAB/animations.yml}. */
    public static final String TAB_ANIMATION = "Aetherion_Citrus";

    /**
     * Stays in the citrus family: lime → yellow → amber → orange.
     * Index 2 is the exact {@link #PRIMARY} stop.
     */
    private static final int[] PALETTE = {
            0xC6F24A,
            0xD8FF55,
            PRIMARY,
            0xFFD23A,
            0xFFB703,
            0xFF9F1C,
            0xF4E34A
    };

    private CitrusDye() {
    }

    public static int[] palette() {
        return PALETTE.clone();
    }

    public static boolean isCitrusGroup(String group) {
        return group != null && "citrus".equalsIgnoreCase(group);
    }

    public static String badge() {
        return badge(currentFrame());
    }

    public static String badge(int frame) {
        return shimmer("[Citrus]", frame) + " ";
    }

    /** Static frame 0 — safe for LuckPerms group prefix (no tick). */
    public static String prefixStatic() {
        return shimmer("[Citrus]", 0) + "&f ";
    }

    /**
     * Per-letter citrus shimmer. Brackets and whitespace keep the current
     * stop so {@code [Citrus]} reads as a badge.
     */
    public static String shimmer(String text, int shift) {
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
            frames[i] = shimmer("[Citrus]", i);
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
