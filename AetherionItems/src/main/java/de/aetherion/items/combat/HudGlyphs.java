package de.aetherion.items.combat;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;

/**
 * Pack font {@code aetherion:hud} (see {@code AetherionItems/tools/gen_hud.py}, which owns the art and
 * mirrors these numbers).
 *
 * <p>The ActionBar draws at {@code guiHeight - 72}, centred. The HUD string has net advance 0, so its pen
 * starts on the screen centre and every glyph lands on a fixed GUI pixel: x is relative to the centre,
 * rows are baked into each glyph's ascent. That holds at any GUI scale and window size.
 *
 * <pre>
 *   ROW 2  frame top guiHeight-65   (third cooldown)
 *   ROW 1  frame top guiHeight-53   DEF badge (left) · second cooldown (right)
 *   ROW 0  frame top guiHeight-41   HP bar over the hearts · cooldown over the food
 * </pre>
 */
public final class HudGlyphs {

    public static final Key FONT = Key.key("aetherion", "hud");

    public static final int ROWS = 3;
    private static final int[] ROW_BASE = {0xE100, 0xE200, 0xE300};
    private static final int FRAME_OFF = 0x00;
    private static final int ICON_OFF = 0x10;
    private static final int FILL_OFF = 0x20;
    private static final int TEXT_OFF = 0x80;

    /** Frame glyphs (81 x 11, DEF 45 x 11). Advance = width + 1. */
    public enum Frame {
        HP(81), COOLDOWN(81), COOLDOWN_DENY(81), COOLDOWN_READY(81), DEFENSE(45);

        final int width;

        Frame(int width) {
            this.width = width;
        }
    }

    /** 7 x 7 icons drawn into a frame's socket. */
    public enum Icon {
        HEART, HEART_POISON, HEART_WITHER, HEART_FROZEN, HEART_GOLD, HOURGLASS, CHECK, CROSS, SHIELD, STAR
    }

    public static final int FRAME_W = 81;
    /** Fill steps 1..WELL px; the well is 7 px tall (frame rows 2..8). */
    public static final int WELL = 67;
    /** Socket interior offset inside a frame. */
    public static final int ICON_INSET = 2;
    public static final int ICON_ADVANCE = 8;

    /** Left column = the hearts (x -91..-11), right column = the food (x 10..90). */
    public static final int LEFT_X = -91;
    public static final int RIGHT_X = 10;
    /** Well start inside an HP frame (socket left) and a cooldown frame (socket right). */
    public static final int HP_WELL = 12;
    public static final int CD_WELL = 2;
    public static final int CD_SOCKET = 70;
    /** DEF number plate inside the 45px badge: x 11..42. */
    static final int DEF_TEXT = 11;
    static final int DEF_TEXT_W = 32;

    private static final char NEG = '';
    private static final char POS = '';
    private static final int TEXT_SPACE = 3;

    private HudGlyphs() {
    }

    /* ------------------------------------------------------------------ text metrics */

    /** Core width of a pixel-font glyph (cell = core + 1px outline each side). */
    static int core(char c) {
        return switch (c) {
            case '!', '\'', ',', '.', ':' -> 1;
            case '(', ')' -> 2;
            case 'N' -> 4;
            case 'M', 'W', 'm' -> 5;
            default -> 3;
        };
    }

    static boolean drawable(char c) {
        return c == ' ' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || c == 's' || c == 'm' || c == 'k'
                || ".,:!'?/+-%()".indexOf(c) >= 0;
    }

    /** Pen advance of a pixel-font string (glyph = core + 1, space = 3). */
    static int advance(String text) {
        int px = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            px += c == ' ' ? TEXT_SPACE : core(c) + 1;
        }
        return px;
    }

    /** Painted width including the outline on both ends. */
    public static int width(String text) {
        return text.isEmpty() ? 0 : advance(text) + 1;
    }

    /** Upper-case label in the pixel font's alphabet (umlauts folded, anything else dropped). */
    public static String label(String raw) {
        if (raw == null) {
            return "";
        }
        String up = raw.toUpperCase(Locale.ROOT)
                .replace("Ä", "A").replace("Ö", "O").replace("Ü", "U").replace("ß", "SS");
        StringBuilder out = new StringBuilder(up.length());
        for (int i = 0; i < up.length(); i++) {
            char c = up.charAt(i);
            if (drawable(c) && c != 's' && c != 'm' && c != 'k') {
                out.append(c);
            }
        }
        return out.toString().trim();
    }

    /** Longest prefix of {@code text} whose painted width fits {@code px}. */
    public static String fit(String text, int px) {
        String out = text;
        while (!out.isEmpty() && width(out) > px) {
            out = out.substring(0, out.length() - 1).stripTrailing();
        }
        return out;
    }

    /* ------------------------------------------------------------------ spaces */

    /** Space-provider chars totalling {@code px} (powers of two, either sign). */
    public static String space(int px) {
        if (px == 0) {
            return "";
        }
        char base = px < 0 ? NEG : POS;
        int left = Math.abs(px);
        StringBuilder out = new StringBuilder();
        for (int bit = 7; bit >= 0; bit--) {
            int step = 1 << bit;
            while (left >= step) {
                out.append((char) (base + bit));
                left -= step;
            }
        }
        return out.toString();
    }

    /* ------------------------------------------------------------------ pen */

    /**
     * Builds one HUD string; {@link #build()} walks the pen back to the centre (net advance 0).
     *
     * <p>Anchored pen ({@link #Pen(Anchor)}): every glyph comes from the row-0 font and its colour carries
     * the row and tint instead, so the pack's text shader can move it (status panel, radar).
     */
    public static final class Pen {

        /** Maps (row, tint) to the colour the shader decodes. */
        public interface Anchor {
            TextColor encode(int row, TextColor tint);
        }

        private final TextComponent.Builder out = Component.text();
        private final StringBuilder run = new StringBuilder();
        private final Anchor anchor;
        private TextColor runColor;
        private Key runFont = FONT;
        private int x;

        public Pen() {
            this(null);
        }

        public Pen(Anchor anchor) {
            this.anchor = anchor;
        }

        private int base(int row) {
            return anchor != null ? ROW_BASE[0] : ROW_BASE[row];
        }

        public Pen at(int target) {
            run.append(space(target - x));
            x = target;
            return this;
        }

        public Pen frame(int row, Frame frame) {
            return glyph(FONT, base(row) + FRAME_OFF + frame.ordinal(), frame.width + 1, tint(row, null));
        }

        public Pen icon(int row, Icon icon) {
            return glyph(FONT, base(row) + ICON_OFF + icon.ordinal(), ICON_ADVANCE, tint(row, null));
        }

        /** {@code px} wide fill strip (1..WELL), tinted. */
        public Pen fill(int row, int px, TextColor tint) {
            int clamped = Math.max(0, Math.min(WELL, px));
            if (clamped == 0) {
                return this;
            }
            return glyph(FONT, base(row) + FILL_OFF + clamped - 1, clamped + 1, tint(row, tint));
        }

        /** Pixel-font text: glyph advance is core + 3, the -2 kern shares the outline column. */
        public Pen text(int row, String text, TextColor color) {
            TextColor tint = tint(row, color);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ') {
                    at(x + TEXT_SPACE);
                    continue;
                }
                if (!drawable(c)) {
                    continue;
                }
                glyph(FONT, base(row) + TEXT_OFF + (c - 0x20), core(c) + 3, tint);
                at(x - 2);
            }
            return this;
        }

        /** One glyph from another font, coloured exactly (that font must carry the same space chars). */
        public Pen raw(Key font, int codepoint, int advance, TextColor exact) {
            return glyph(font, codepoint, advance, exact);
        }

        /** A glyph from another font on an anchored row, tinted like the HUD glyphs. */
        public Pen art(Key font, int row, int codepoint, int advance, TextColor tint) {
            return glyph(font, codepoint, advance, tint(row, tint));
        }

        public int x() {
            return x;
        }

        private TextColor tint(int row, TextColor color) {
            TextColor tint = color == null ? WHITE : color;
            return anchor == null ? tint : anchor.encode(row, tint);
        }

        private Pen glyph(Key font, int codepoint, int advance, TextColor tint) {
            if (runColor != null && (!runColor.equals(tint) || !runFont.equals(font))) {
                flush();
            }
            runColor = tint;
            runFont = font;
            run.append((char) codepoint);
            x += advance;
            return this;
        }

        public Component build() {
            at(0);
            flush();
            return out.build();
        }

        private void flush() {
            if (run.length() == 0) {
                return;
            }
            out.append(Component.text(run.toString(), runColor == null ? WHITE : runColor).font(runFont));
            run.setLength(0);
        }
    }

    public static final TextColor WHITE = TextColor.color(0xFFFFFF);
}
