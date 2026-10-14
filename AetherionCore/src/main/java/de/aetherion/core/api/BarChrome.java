package de.aetherion.core.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Typed frames for the top-of-screen boss bars (pack glyphs, see {@code AetherionItems/tools/gen_bars.py}).
 *
 * <p>Vanilla 1.21.1 draws a bar at {@code guiWidth/2 - 91} and then its title, centred, 9px above it. A title
 * that starts with a run of net-zero glyphs (space shifts, a frame, an icon) can therefore paint straight onto
 * the bar: the shifts are worked out from the title's exact width ({@link TextWidth}), so the frame sits on the
 * bar at every GUI scale and window size. The text itself is untouched and stays centred.
 *
 * <ul>
 *   <li>{@link Family} picks the frame (boss medallions, quest scroll socket, event banner, ...),</li>
 *   <li>{@link Icon} fills the family's socket,</li>
 *   <li>bar colour stays the state (HP, thirds, hot / slipping); PINK is the ghost hairline used by hints.</li>
 * </ul>
 *
 * If the width can't be known exactly (a glyph outside the vanilla pages) or the pack glyphs are switched off,
 * the title goes out unchanged.
 */
public final class BarChrome {

    private static final int BAR_W = 182;
    private static final int HALF_BAR = 91;
    private static final char FRAME_BASE = '';
    private static final char NEG = '';
    private static final char POS = '';
    /** Largest single shift glyph is 2^8. */
    private static final int SHIFT_BITS = 8;

    private static volatile boolean enabled = true;

    private BarChrome() {
    }

    /** One frame language per kind of bar. Caps are the px the frame reaches past the bar's ends. */
    public enum Family {
        /** Epic: gold rails, medallion with the boss sigil on the left, a ruby on the right. */
        BOSS(0, 20, 20, -10),
        /** Second bar under a boss (Helios' movement bar): thin gold rails, brackets, no medallion. */
        PHASE(1, 3, 3, -2),
        /** Bronze rails, scroll socket. */
        QUEST(2, 13, 4, -8),
        /** No frame at all: a small dim icon by the ghost hairline. */
        HINT(-1, 0, 0, -7),
        /** Loud and temporary: amber hazard rails, banner socket, pennant. */
        EVENT(3, 15, 7, -9),
        /** Rhythmic mini-games: teal rails, round socket, beat dot. */
        GATHER(4, 13, 8, -8),
        /** Calm: one leaf rail under the bar, octagon badge. */
        ISLAND(5, 14, 5, -8),
        /** Studded stone rails, gate arch socket. */
        DUNGEON(6, 13, 5, -8),
        /** Crafting rites (millstone, blueprint forge): dotted arcane rails, rune circle. */
        RITUAL(7, 15, 7, -9);

        final int frame;
        final int capLeft;
        final int capRight;
        final int socketCentre;

        Family(int frame, int capLeft, int capRight, int socketCentre) {
            this.frame = frame;
            this.capLeft = capLeft;
            this.capRight = capRight;
            this.socketCentre = socketCentre;
        }
    }

    /** Socket icons. 9px boss sigils, 7px family icons, 5px quiet hint marks. */
    public enum Icon {
        SKULL(9, 0), SUN(9, 1), CROWN(9, 2), SIGIL(9, 3),

        QUEST(7, 0), QUEST_READY(7, 1), QUEST_SPECIAL(7, 2),
        WHEAT(7, 3), PICK(7, 4), FISH(7, 5), LEAF(7, 6), BIRD(7, 7),
        BOBBER(7, 8), AXE(7, 9), CRYSTAL(7, 10), COMPASS(7, 11), PAW(7, 12),
        HOUSE(7, 13), FLAG(7, 14), STAR(7, 15), WARN(7, 16), ARROW(7, 17), EYE(7, 18),
        GATE(7, 19), GATE_OPEN(7, 20), CHAMBER(7, 21),
        MILL(7, 22), ANVIL(7, 23), HOURGLASS(7, 24),

        HINT(5, 0), HINT_HERE(5, 1), HINT_AWAY(5, 2);

        final int size;
        final char glyph;

        Icon(int size, int index) {
            this.size = size;
            this.glyph = (char) ((size == 9 ? 0xE940 : size == 5 ? 0xE950 : 0xE920) + index);
        }
    }

    /** Off = every title goes out unchanged (served pack older than the plugins). */
    public static void enabled(boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** A {@code §}-coded title with the family frame and icon in front. */
    public static String legacy(Family family, Icon icon, String title) {
        String safe = title == null ? "" : title;
        if (!enabled || family == null) {
            return safe;
        }
        int width = TextWidth.legacy(safe);
        if (width == TextWidth.UNKNOWN) {
            return safe;
        }
        return "§f" + prefix(family, icon, width) + "§r" + safe;
    }

    /** Same for a component title; its own style stays on its own subtree. */
    public static Component component(Family family, Icon icon, Component title) {
        Component safe = title == null ? Component.empty() : title;
        if (!enabled || family == null) {
            return safe;
        }
        int width = TextWidth.of(safe);
        if (width == TextWidth.UNKNOWN) {
            return safe;
        }
        Component chrome = Component.text(prefix(family, icon, width), NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, false);
        return Component.text().append(chrome).append(safe).build();
    }

    /**
     * Net-zero glyph run. Positions are in bar pixels (0 = the bar's left end): the title pen starts at
     * {@code 91 - width/2}, the same integer maths the client uses for both bar and title.
     */
    static String prefix(Family family, Icon icon, int titleWidth) {
        StringBuilder out = new StringBuilder(24);
        int start = HALF_BAR - titleWidth / 2;
        int pen = start;
        if (family.frame >= 0) {
            int left = -family.capLeft;
            shift(out, left - pen);
            out.append((char) (FRAME_BASE + family.frame));
            pen = left + family.capLeft + BAR_W + family.capRight + 1;
        }
        if (icon != null) {
            int left = family.socketCentre - icon.size / 2;
            shift(out, left - pen);
            out.append(icon.glyph);
            pen = left + icon.size + 1;
        }
        shift(out, start - pen);
        return out.toString();
    }

    private static void shift(StringBuilder out, int px) {
        char base = px < 0 ? NEG : POS;
        int left = Math.abs(px);
        while (left > 0) {
            int bit = Math.min(SHIFT_BITS, 31 - Integer.numberOfLeadingZeros(left));
            out.append((char) (base + bit));
            left -= 1 << bit;
        }
    }
}
