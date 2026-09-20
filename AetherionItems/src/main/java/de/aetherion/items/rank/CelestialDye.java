package de.aetherion.items.rank;

import org.bukkit.Bukkit;

/**
 * Reusable Hypixel SkyBlock–style celestial dye. Anchored on Peter's
 * primary hex {@link #PRIMARY} ({@code #B2FFFF}) with cyan shimmer variants —
 * not a rainbow. Shared by Monkey, Beta Tester, and any future ultra rank.
 * <p>
 * Output uses TAB / LuckPerms hex ({@code &#RRGGBB}) so
 * {@code %aetherion_tab_prefix%} and TAB animations render the same palette.
 */
public final class CelestialDye {

    /** Hypixel Celestial Dye — Peter: {@code #B2FFFF} / {@code B2FFFF}. */
    public static final int PRIMARY = 0xB2FFFF;

    /** Copy into {@code plugins/TAB/animations.yml}. */
    public static final String TAB_ANIMATION = "Aetherion_Celestial";

    /**
     * @deprecated use {@link #TAB_ANIMATION}; kept so older TAB configs
     * still name-match docs that said {@code Aetherion_Monkey}.
     */
    @Deprecated
    public static final String TAB_ANIMATION_MONKEY = TAB_ANIMATION;

    /**
     * Shimmer variants of {@link #PRIMARY}. Stays in the ice-cyan family.
     * Index 2 is the exact B2FFFF stop so lettering hits the true dye.
     */
    private static final int[] PALETTE = {
            0x6EE8F2,
            0x8CF4FC,
            PRIMARY,
            0xC8FFFF,
            0xDCFFFF,
            0xA6FBFF,
            0x7EF0F8
    };

    private CelestialDye() {
    }

    public static int[] palette() {
        return PALETTE.clone();
    }

    public static boolean isCelestialGroup(String group) {
        if (group == null || group.isBlank()) {
            return false;
        }
        return switch (group.toLowerCase(java.util.Locale.ROOT)) {
            case "monkey", "beta" -> true;
            default -> false;
        };
    }

    /** Animated badge for a known ultra group, e.g. {@code monkey} → {@code [Monkey]}. */
    public static String badgeForGroup(String group) {
        return badge(labelForGroup(group));
    }

    public static String prefixStaticForGroup(String group) {
        return prefixStatic(labelForGroup(group));
    }

    public static String labelForGroup(String group) {
        if (group == null || group.isBlank()) {
            return "[Celestial]";
        }
        return switch (group.toLowerCase(java.util.Locale.ROOT)) {
            case "monkey" -> "[Monkey]";
            case "beta" -> "[Beta]";
            default -> "[" + Character.toUpperCase(group.charAt(0)) + group.substring(1).toLowerCase(java.util.Locale.ROOT) + "]";
        };
    }

    public static String badge(String label) {
        return badge(label, currentFrame());
    }

    public static String badge(String label, int frame) {
        return shimmer(label == null ? "" : label, frame) + " ";
    }

    /** Static frame 0 — safe for LuckPerms group prefix (no tick). */
    public static String prefixStatic(String label) {
        return shimmer(label == null ? "" : label, 0) + "&f ";
    }

    public static String monkeyBadge() {
        return badge("[Monkey]");
    }

    public static String monkeyBadge(int frame) {
        return badge("[Monkey]", frame);
    }

    public static String monkeyPrefixStatic() {
        return prefixStatic("[Monkey]");
    }

    public static String betaBadge() {
        return badge("[Beta]");
    }

    public static String betaBadge(int frame) {
        return badge("[Beta]", frame);
    }

    public static String betaPrefixStatic() {
        return prefixStatic("[Beta]");
    }

    /**
     * Per-letter celestial shimmer. Brackets and whitespace keep the current
     * stop so {@code [Name]} reads as a badge, not a hue-eaten frame.
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

    /** @deprecated use {@link #shimmer(String, int)} — palette is cyan, not rainbow. */
    @Deprecated
    public static String rainbow(String text, int shift) {
        return shimmer(text, shift);
    }

    public static String[] animationFrames(String label) {
        String text = label == null ? "" : label;
        String[] frames = new String[PALETTE.length];
        for (int i = 0; i < PALETTE.length; i++) {
            frames[i] = shimmer(text, i);
        }
        return frames;
    }

    public static String[] animationFrames() {
        return animationFrames("[Monkey]");
    }

    private static int currentFrame() {
        int tick = 0;
        try {
            tick = Bukkit.getCurrentTick();
        } catch (IllegalStateException ignored) {
        }
        return Math.floorDiv(Math.max(0, tick), 4);
    }

    public static String hex(int rgb) {
        return String.format("&#%06X", rgb & 0xFFFFFF);
    }
}
