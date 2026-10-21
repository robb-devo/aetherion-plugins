package de.aetherion.items.rank;

import de.aetherion.items.skill.AetherionLevel;

import java.util.List;
import java.util.Locale;

/**
 * Pixel-art rank badges from the pack font {@code aetherion:badges} (tools/gen_rank_badges.py), which the served
 * pack references from {@code minecraft:default}. They are plain characters, so every legacy string that carries
 * the prefix (TAB, chat, nametags, display name) shows them. {@code §f} keeps the art in its own colours.
 * <p>
 * Level badge = left cap with the tier emblem, the digits, right cap, joined by a -1 space so the pill is seamless.
 */
public final class RankGlyphs {

    private static final char NEG = '';
    private static final char COSMETIC_BASE = '';
    private static final char LEVEL_BASE = '';
    /** Same order as COSMETIC in gen_rank_badges.py (admin is drawn as OWNER). */
    private static final List<String> COSMETIC = List.of("admin", "coowner", "mvpplusplus", "monkey", "citrus", "beta");

    private static volatile boolean enabled = true;

    private RankGlyphs() {
    }

    static void enabled(boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** Cosmetic rank pill plus a space, or "" for none. */
    public static String cosmetic(String group) {
        if (group == null) {
            return "";
        }
        int index = COSMETIC.indexOf(group.toLowerCase(Locale.ROOT));
        return index < 0 ? "" : "§f" + (char) (COSMETIC_BASE + index) + " ";
    }

    /** Level pill in the colours of the level's title tier. */
    public static String level(int level) {
        int clamped = Math.max(1, Math.min(AetherionLevel.MAX_LEVEL, level));
        char base = (char) (LEVEL_BASE + 16 * AetherionLevel.titleTier(clamped));
        StringBuilder out = new StringBuilder("§f").append(base);
        for (char digit : String.valueOf(clamped).toCharArray()) {
            out.append(NEG).append((char) (base + 2 + digit - '0'));
        }
        return out.append(NEG).append((char) (base + 1)).toString();
    }
}
