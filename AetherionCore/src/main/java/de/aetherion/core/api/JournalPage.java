package de.aetherion.core.api;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Map;

/**
 * The Aether Journal's open book in one inventory title (six rows): the menu frame in a theme's accent, the two-page
 * spread, one silk ribbon per chapter in row 0, the gold wash under the page's one strong line, the title plate with
 * the path ("JOURNAL · CHARACTER") and the line texts in ink on rows 1..4.
 *
 * <p>Art: {@code AetherionItems/tools/gen_journal_art.py} (glyphs in {@code aetherion:kit}, 0xE400..0xE443) and the
 * fonts {@code aetherion:jrn_line_1..4} (vanilla text moved onto rows 1..4). Codepoints, advances and the
 * {@link Chapter} order are mirrored there; change both together.
 *
 * <p>Each line is an icon in column 0 (left page) or 5 (right page) and its text across the three slots beside it;
 * the menu puts the same tooltip on all four slots so the whole line answers the mouse.
 */
public final class JournalPage {

    /** The ribbons, left to right in row 0 (column = ordinal). */
    public enum Chapter { TODAY, CHARACTER, JOURNEY, ATLAS, WORKSHOP, MARKET, CODEX, COMPANY, SETTINGS }

    /** A ribbon's look; a chapter without one is not drawn (not found yet). */
    public enum Look { OPEN, ACTIVE, NEW, NEXT }

    /** Line colours: ink = open, amber = opens next, faded = waits, fresh = new since you last looked. */
    public enum Ink {
        INK(0x3B2A1A), AMBER(0xA8590A), FADED(0x9C8B6E), FRESH(0x8E2B2B);

        private final TextColor colour;

        Ink(int rgb) {
            this.colour = TextColor.color(rgb);
        }
    }

    /** One line of a page: row 1..4, left or right page. */
    public record Line(int row, boolean right, String text, Ink ink) {
    }

    private static final int SPREAD = 0xE400;
    private static final int RIBBON = 0xE410;
    private static final int HIGHLIGHT = 0xE440;
    private static final int PAGE_ADVANCE = 257;
    private static final int RIBBON_ADVANCE = 21;
    private static final int HIGHLIGHT_ADVANCE = 77;
    /** Text starts two pixels into the slot beside the icon and stops two short of the page edge (86, 171). */
    private static final int LEFT_TEXT = 28;
    private static final int RIGHT_TEXT = 118;
    private static final int LEFT_MAX = 56;
    private static final int RIGHT_MAX = 51;
    private static final Key[] LINE_FONTS = {
            Key.key("aetherion", "jrn_line_1"),
            Key.key("aetherion", "jrn_line_2"),
            Key.key("aetherion", "jrn_line_3"),
            Key.key("aetherion", "jrn_line_4"),
    };

    private JournalPage() {
    }

    /** True when {@code text} fits a line of the left or right page without being cut. */
    public static boolean fits(String text, boolean right) {
        int width = TextWidth.legacy(text == null ? "" : text.replaceAll("§.", ""));
        return width != TextWidth.UNKNOWN && width <= (right ? RIGHT_MAX : LEFT_MAX);
    }

    /** First slot of a line: column 0 or 5 of its row. */
    public static int slot(int row, boolean right) {
        return 9 * row + (right ? 5 : 0);
    }

    /**
     * The title that draws the page.
     *
     * @param label   plate text, plate caps only ({@link ChromePage#label} cleans it)
     * @param ribbons look per chapter; chapters left out are not drawn
     * @param strong  slot of the line under the gold wash ({@link #slot}), or -1
     */
    public static Component title(ChromePage.Theme theme, String label, Map<Chapter, Look> ribbons, int strong,
                                  List<Line> lines) {
        StringBuilder glyphs = new StringBuilder();
        int[] cursor = {8};
        ChromePage.moveTo(glyphs, cursor, -40);
        glyphs.append(ChromePage.frameGlyph(theme, 6));
        cursor[0] += PAGE_ADVANCE;
        ChromePage.moveTo(glyphs, cursor, -40);
        glyphs.append((char) SPREAD);
        cursor[0] += PAGE_ADVANCE;
        for (Chapter chapter : Chapter.values()) {
            Look look = ribbons == null ? null : ribbons.get(chapter);
            if (look == null) {
                continue;
            }
            ChromePage.moveTo(glyphs, cursor, 6 + 18 * chapter.ordinal());
            glyphs.append((char) (RIBBON + 4 * chapter.ordinal() + look.ordinal()));
            cursor[0] += RIBBON_ADVANCE;
        }
        if (strong >= 9 && strong < 45) {
            int row = strong / 9;
            ChromePage.moveTo(glyphs, cursor, strong % 9 >= 5 ? 96 : 6);
            glyphs.append((char) (HIGHLIGHT + row - 1));
            cursor[0] += HIGHLIGHT_ADVANCE;
        }
        ChromePage.plate(glyphs, cursor, theme, ChromePage.label(label));

        TextComponent.Builder out = Component.text().append(Component.text(glyphs.toString(), NamedTextColor.WHITE));
        if (lines != null) {
            for (Line line : lines) {
                if (line == null || line.row() < 1 || line.row() > 4 || line.text() == null) {
                    continue;
                }
                String text = fit(line.text(), line.right() ? RIGHT_MAX : LEFT_MAX);
                int width = TextWidth.legacy(text);
                if (text.isEmpty() || width == TextWidth.UNKNOWN) {
                    continue;
                }
                int x = line.right() ? RIGHT_TEXT : LEFT_TEXT;
                out.append(Component.text(BookPage.space(x - cursor[0]), NamedTextColor.WHITE));
                Ink ink = line.ink() == null ? Ink.INK : line.ink();
                out.append(Component.text(text, ink.colour).font(LINE_FONTS[line.row() - 1]));
                cursor[0] = x + width;
            }
        }
        return out.build();
    }

    /** Cuts {@code text} to {@code max} pixels, ending in ".." when it had to cut. Legacy colour codes are dropped. */
    static String fit(String text, int max) {
        String plain = text.replaceAll("§.", "").trim();
        int width = TextWidth.legacy(plain);
        if (width == TextWidth.UNKNOWN || width <= max) {
            return plain;
        }
        int dots = TextWidth.legacy("..");
        while (!plain.isEmpty()) {
            plain = plain.substring(0, plain.length() - 1).trim();
            int w = TextWidth.legacy(plain);
            if (w != TextWidth.UNKNOWN && w + dots <= max) {
                return plain + "..";
            }
        }
        return "";
    }
}
