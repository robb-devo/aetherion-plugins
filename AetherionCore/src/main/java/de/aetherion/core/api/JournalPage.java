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

    /* =========================================================
     * Sub-pages: the book opened at a menu
     * ========================================================= */

    private static final int LEAF = 0xE450;
    private static final int TAIL = 0xE460;
    private static final int TAIL_X = 147;
    private static final int TAIL_ADVANCE = 11;
    private static final int MARK = 0xE200;
    private static final int MARK_ADVANCE = 21;
    private static final int MARK_SEAL = 16;
    private static final int MARK_BUTTON = 17;
    private static final int MARK_PICKED_POCKET = 18;
    private static final int MARK_PICKED_SEAL = 19;
    private static final int MARK_ARROW = 20;
    private static final Key[] CAPTION_FONTS = {
            Key.key("aetherion", "mgr_caption_1"),
            Key.key("aetherion", "mgr_caption_2"),
            Key.key("aetherion", "mgr_caption_3"),
    };
    /** Captions stay on the paper (inside the leather). */
    private static final int CAPTION_MIN_X = 6;
    private static final int CAPTION_MAX_X = 169;

    /** The menu frame of a chapter's pages (its sub-pages wear the same). */
    public static ChromePage.Theme theme(Chapter chapter) {
        if (chapter == null) {
            return ChromePage.Theme.AETHER;
        }
        return switch (chapter) {
            case JOURNEY -> ChromePage.Theme.QUEST;
            case ATLAS -> ChromePage.Theme.ISLAND;
            case WORKSHOP -> ChromePage.Theme.FORGE;
            case MARKET -> ChromePage.Theme.TRADE;
            case COMPANY -> ChromePage.Theme.GUILD;
            case SETTINGS -> ChromePage.Theme.BETA;
            default -> ChromePage.Theme.AETHER;
        };
    }

    /** Starts a sub-page in its chapter's frame: a menu the journal opens (Skills, Storage, ...), {@code rows} 1..6. */
    public static Leaf leaf(Chapter chapter, int rows, String label) {
        return new Leaf(theme(chapter), rows, chapter, label);
    }

    /** Starts a sub-page in a frame of its own. */
    public static Leaf leaf(ChromePage.Theme theme, int rows, Chapter chapter, String label) {
        return new Leaf(theme, rows, chapter, label);
    }

    /**
     * {@link ChromePage#open} for a menu the journal leads to: while the journal is on ({@link JournalText#enabled})
     * the page is a journal sub-page of {@code chapter} with these marks, else the kit page in {@code fallback}.
     * Fillers turn invisible and plain navigation buttons get their icons either way. The title carries glyphs only
     * (no captions): it goes out as a legacy string like ChromePage's.
     */
    public static org.bukkit.inventory.InventoryView open(org.bukkit.entity.Player player,
                                                          org.bukkit.inventory.Inventory inventory,
                                                          ChromePage.Theme fallback, Chapter chapter, String label,
                                                          Map<Integer, ChromePage.Mark> marks) {
        if (!JournalText.enabled() || inventory == null || inventory.getSize() % 9 != 0
                || inventory.getType() != org.bukkit.event.inventory.InventoryType.CHEST) {
            return ChromePage.open(player, inventory, fallback, label, marks);
        }
        Map<Integer, ChromePage.Mark> page = marks == null ? ChromePage.marks(inventory) : marks;
        ChromePage.hideFillers(inventory);
        ChromePage.dressNav(inventory);
        org.bukkit.inventory.InventoryView view = player.openInventory(inventory);
        if (view == null || view.getTopInventory() != inventory) {
            return view;
        }
        Leaf leaf = leaf(chapter, inventory.getSize() / 9, label == null ? view.getTitle() : label);
        page.forEach(leaf::mark);
        view.setTitle(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(leaf.title()));
        return view;
    }

    /**
     * A sub-page in one inventory title: the chapter's menu frame, the same leather cover, two pages and spine as the
     * journal's spread (the paper reaching up to row 0, the folded corner under the bottom-left slot, where Back
     * lives), the chapter's ribbon hanging out under the book, a pocket or seal under every slot that holds something
     * (gold under the one picked), the title plate with the path ("CHARACTER · SKILLS") and captions in ink under
     * tiles on rows 0, 2 and 4.
     *
     * <p>Art: {@code AetherionItems/tools/gen_journal_art.py} (leaf 0xE451..0xE456, tails 0xE460 + 16 * (rows - 1) +
     * chapter) and {@code gen_gui_kit.py} (marks 0..20 per row). A button mark on the folded corner is left out.
     */
    public static final class Leaf {
        private final ChromePage.Theme theme;
        private final int rows;
        private final Chapter chapter;
        private final String label;
        private final Map<Integer, ChromePage.Mark> marks = new java.util.HashMap<>();
        private final java.util.Set<Integer> picked = new java.util.HashSet<>();
        private final java.util.Set<Integer> arrows = new java.util.HashSet<>();
        private final List<BookPage.Caption> captions = new java.util.ArrayList<>();

        private Leaf(ChromePage.Theme theme, int rows, Chapter chapter, String label) {
            this.theme = theme == null ? ChromePage.Theme.AETHER : theme;
            this.rows = Math.max(1, Math.min(6, rows));
            this.chapter = chapter;
            this.label = label;
        }

        /**
         * Marks from a picture of the page, one word per row, one letter per slot: {@code P} pocket, {@code S} seal,
         * {@code B} small seal, {@code A} arrow, anything else bare paper ("BBBBBBBBB .PPPPPPP. ...").
         */
        public Leaf layout(String rowsPicture) {
            if (rowsPicture == null) {
                return this;
            }
            String[] words = rowsPicture.trim().split("\\s+");
            for (int row = 0; row < words.length && row < rows; row++) {
                String word = words[row];
                for (int col = 0; col < 9 && col < word.length(); col++) {
                    int slot = 9 * row + col;
                    switch (word.charAt(col)) {
                        case 'P' -> marks.put(slot, ChromePage.Mark.POCKET);
                        case 'S' -> marks.put(slot, ChromePage.Mark.SEAL);
                        case 'B' -> marks.put(slot, ChromePage.Mark.BUTTON);
                        case 'A' -> arrows.add(slot);
                        default -> {
                        }
                    }
                }
            }
            return this;
        }

        public Leaf mark(int slot, ChromePage.Mark mark) {
            if (mark == null) {
                marks.remove(slot);
            } else {
                marks.put(slot, mark);
            }
            return this;
        }

        /** The slot that is chosen on this page (the open tab, the shown category): gold under it. */
        public Leaf pick(int slot) {
            if (slot >= 0) {
                picked.add(slot);
            }
            return this;
        }

        public Leaf captions(List<BookPage.Caption> list) {
            if (list != null) {
                captions.addAll(list);
            }
            return this;
        }

        public Component title() {
            StringBuilder glyphs = new StringBuilder();
            int[] cursor = {8};
            ChromePage.moveTo(glyphs, cursor, -40);
            glyphs.append(ChromePage.frameGlyph(theme, rows));
            cursor[0] += PAGE_ADVANCE;
            ChromePage.moveTo(glyphs, cursor, -40);
            glyphs.append((char) (LEAF + rows));
            cursor[0] += PAGE_ADVANCE;
            if (chapter != null) {
                ChromePage.moveTo(glyphs, cursor, TAIL_X);
                glyphs.append((char) (TAIL + 16 * (rows - 1) + chapter.ordinal()));
                cursor[0] += TAIL_ADVANCE;
            }
            int fold = rows >= 2 ? 9 * (rows - 1) : -1;
            for (int slot = 0; slot < rows * 9; slot++) {
                int glyph = glyph(slot, fold);
                if (glyph < 0) {
                    continue;
                }
                ChromePage.moveTo(glyphs, cursor, 6 + 18 * (slot % 9));
                glyphs.append((char) (MARK + 32 * (slot / 9) + glyph));
                cursor[0] += MARK_ADVANCE;
            }
            ChromePage.plate(glyphs, cursor, theme, ChromePage.label(label));

            TextComponent.Builder out = Component.text().append(Component.text(glyphs.toString(), NamedTextColor.WHITE));
            for (BookPage.Caption caption : captions) {
                int row = caption.slot() / 9;
                String text = caption.text() == null ? "" : caption.text().replaceAll("§.", "");
                int width = TextWidth.legacy(text);
                if (width == TextWidth.UNKNOWN || width <= 0 || row % 2 != 0 || row / 2 >= CAPTION_FONTS.length
                        || row >= rows) {
                    continue;
                }
                int centre = 16 + 18 * (caption.slot() % 9);
                int left = Math.max(CAPTION_MIN_X, Math.min(CAPTION_MAX_X - (width - 1), centre - (width - 1) / 2));
                out.append(Component.text(BookPage.space(left - cursor[0]), NamedTextColor.WHITE));
                out.append(Component.text(text, captionInk(caption.state())).font(CAPTION_FONTS[row / 2]));
                cursor[0] = left + width;
            }
            return out.build();
        }

        private int glyph(int slot, int fold) {
            if (arrows.contains(slot)) {
                return MARK_ARROW;
            }
            ChromePage.Mark mark = marks.get(slot);
            if (picked.contains(slot)) {
                return mark == ChromePage.Mark.POCKET ? MARK_PICKED_POCKET : MARK_PICKED_SEAL;
            }
            if (mark == null || (slot == fold && mark == ChromePage.Mark.BUTTON)) {
                return -1;
            }
            int row = slot / 9;
            int col = slot % 9;
            return switch (mark) {
                case SEAL -> MARK_SEAL;
                case BUTTON -> MARK_BUTTON;
                case POCKET -> (pocket(slot - 9) ? 1 : 0)
                        | (row < rows - 1 && pocket(slot + 9) ? 2 : 0)
                        | (col > 0 && pocket(slot - 1) ? 4 : 0)
                        | (col < 8 && pocket(slot + 1) ? 8 : 0);
            };
        }

        private boolean pocket(int slot) {
            return slot >= 0 && marks.get(slot) == ChromePage.Mark.POCKET && !picked.contains(slot);
        }
    }

    private static TextColor captionInk(BookPage.State state) {
        return switch (state == null ? BookPage.State.OPEN : state) {
            case OPEN -> Ink.INK.colour;
            case LOCKED -> Ink.FADED.colour;
            case NEXT -> Ink.AMBER.colour;
        };
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
