package de.aetherion.items.hud;

import de.aetherion.items.combat.HudGlyphs;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;

/**
 * Builds the HUD carrier's title from pack font {@code aetherion:carrier} ({@code tools/gen_carrier.py} owns
 * the art and mirrors every number here).
 *
 * <p>The pen walks in art pixels. Its x is a glyph's left edge; where that lands on screen, and how far down,
 * comes from the glyph's colour ({@link PanelPalette}), which the pack's text shader decodes. {@link #build()}
 * walks back to 0, so the title has no width and the shader always knows where it starts.
 */
final class HudPen {

    static final Key FONT = Key.key("aetherion", "carrier");

    static final int PLATE = 0xE700;
    static final int CREST = 0xE704;
    static final int RADAR = 0xE740;
    private static final int FRAME = 0xE705;
    private static final int[] ICON = {0xE710, 0xE760};
    private static final int MARK = 0xE741;
    private static final int LOC = 0xE750;
    private static final int CHIP = 0xE770;
    private static final int[] SMALL = {0xE780, 0xE800};
    private static final int[] BIG = {0xE880, 0xE900};

    static final int PLATE_W = 124;
    static final int CREST_W = 26;
    static final int RADAR_W = 64;
    static final int ICON_W = 7;
    static final int LOC_CAP = 8;
    static final int CHIP_CAP = 4;

    enum Frame {
        HP(87), STAMINA(87), XP(87), CD(116), CD_READY(116), CD_DENY(116);

        final int width;

        Frame(int width) {
            this.width = width;
        }
    }

    enum Icon { HEART, HEART_POISON, HEART_WITHER, HEART_FROZEN, HEART_GOLD, SHIELD, COIN, SHARD, HOURGLASS, CHECK, CROSS, STAR, CLOUD, DIVIDER }

    /** Fill strips start one pixel under the frame top they are drawn for. */
    enum Fill {
        HP(0xE720), STAMINA(0xE728), XP(0xE730), CD(0xE738);

        final int base;

        Fill(int base) {
            this.base = base;
        }
    }

    enum Mark {
        QUEST(7), QUEST_UP(7), QUEST_DOWN(7), NPC(5), PARTY(5), BOSS(7), NORTH(7);

        final int size;

        Mark(int size) {
            this.size = size;
        }
    }

    private static final int SMALL_SPACE = 3;
    private static final int BIG_SPACE = 4;

    private final TextComponent.Builder out = Component.text();
    private final StringBuilder run = new StringBuilder();
    private TextColor runColor;
    private int x;

    HudPen at(int target) {
        run.append(HudGlyphs.space(target - x));
        x = target;
        return this;
    }

    int x() {
        return x;
    }

    /** One glyph; {@code advance} = its width + 1, as Minecraft measures bitmap glyphs. */
    HudPen glyph(int codepoint, int advance, TextColor place) {
        if (runColor != null && !runColor.equals(place)) {
            flush();
        }
        runColor = place;
        run.append((char) codepoint);
        x += advance;
        return this;
    }

    HudPen frame(Frame frame, TextColor place) {
        return glyph(FRAME + frame.ordinal(), frame.width + 1, place);
    }

    /** A 7x7 icon; {@code phase} 1 draws it one pixel lower than the code's row. */
    HudPen icon(Icon icon, int phase, TextColor place) {
        return glyph(ICON[phase & 1] + icon.ordinal(), ICON_W + 1, place);
    }

    HudPen mark(Mark mark, TextColor place) {
        return glyph(MARK + mark.ordinal(), mark.size + 1, place);
    }

    /** {@code px} wide fill from the pen, out of power-of-two strips. */
    HudPen fill(Fill fill, int px, TextColor place) {
        int left = Math.max(0, px);
        for (int bit = 6; bit >= 0 && left > 0; bit--) {
            int step = 1 << bit;
            while (left >= step) {
                int from = x;
                glyph(fill.base + bit, step + 1, place);
                at(from + step);
                left -= step;
            }
        }
        return this;
    }

    /** Location plate {@code width} px wide (caps included). */
    HudPen plate(int width, TextColor place) {
        return strip(LOC, LOC_CAP, width, place);
    }

    /** Small chip {@code width} px wide (caps included). */
    HudPen chip(int width, TextColor place) {
        return strip(CHIP, CHIP_CAP, width, place);
    }

    private HudPen strip(int base, int cap, int width, TextColor place) {
        int x0 = x;
        glyph(base, cap + 1, place);
        at(x0 + cap);
        int mid = Math.max(0, width - 2 * cap);
        for (int bit = 6; bit >= 0 && mid > 0; bit--) {
            int step = 1 << bit;
            while (mid >= step) {
                int from = x;
                glyph(base + 2 + bit, step + 1, place);
                at(from + step);
                mid -= step;
            }
        }
        at(x0 + Math.max(width, 2 * cap) - cap);
        return glyph(base + 1, cap + 1, place);
    }

    /** 3x5 caps and digits ({@link HudGlyphs#label} alphabet). */
    HudPen small(String text, int phase, TextColor place) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                at(x + SMALL_SPACE);
            } else if (c >= 0x21 && c < 0x80 && HudGlyphs.drawable(c)) {
                glyph(SMALL[phase & 1] + (c - 0x20), HudGlyphs.core(c) + 3, place);
                at(x - 2);
            }
        }
        return this;
    }

    /** 5x7 caps and digits, the menu-title lettering. */
    HudPen big(String text, int phase, TextColor place) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                at(x + BIG_SPACE);
            } else if (bigDrawable(c)) {
                glyph(BIG[phase & 1] + (c - 0x20), bigCore(c) + 3, place);
                at(x - 2);
            }
        }
        return this;
    }

    static int smallWidth(String text) {
        return HudGlyphs.width(text);
    }

    /** Painted width of a 5x7 string, shadow column included. */
    static int bigWidth(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        int px = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            px += c == ' ' ? BIG_SPACE : bigCore(c) + 1;
        }
        return px + 1;
    }

    static boolean bigDrawable(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "'-.()".indexOf(c) >= 0;
    }

    static int bigCore(char c) {
        return switch (c) {
            case '\'', '.' -> 1;
            case '(', ')' -> 2;
            case 'I', '-', '1' -> 3;
            default -> 5;
        };
    }

    /** Upper case in the 5x7 alphabet: umlauts folded, anything else dropped, runs of spaces kept single. */
    static String bigLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String up = raw.toUpperCase(Locale.ROOT)
                .replace("Ä", "A").replace("Ö", "O").replace("Ü", "U").replace("ß", "SS");
        StringBuilder out = new StringBuilder(up.length());
        for (int i = 0; i < up.length(); i++) {
            char c = up.charAt(i);
            if (c == ' ' || c == '_') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
            } else if (bigDrawable(c)) {
                out.append(c);
            }
        }
        return out.toString().trim();
    }

    /** Longest prefix of a 5x7 string that fits {@code px}. */
    static String bigFit(String text, int px) {
        String out = text;
        while (!out.isEmpty() && bigWidth(out) > px) {
            out = out.substring(0, out.length() - 1).stripTrailing();
        }
        return out;
    }

    Component build() {
        at(0);
        flush();
        return out.build();
    }

    private void flush() {
        if (run.length() == 0) {
            return;
        }
        out.append(Component.text(run.toString(), runColor == null ? TextColor.color(0xFFFFFF) : runColor).font(FONT));
        run.setLength(0);
    }
}
