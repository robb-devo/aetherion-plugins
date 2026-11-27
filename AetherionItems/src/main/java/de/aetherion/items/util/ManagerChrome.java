package de.aetherion.items.util;

import de.aetherion.core.api.BookPage;
import de.aetherion.core.api.JournalPage;
import de.aetherion.core.api.JournalPage.Chapter;
import de.aetherion.core.api.JournalText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

/**
 * Aetherion Manager GUI chrome — inventory titles that draw a full-panel overlay from the
 * resource pack font ({@code assets/minecraft/font/default.json}).
 *
 * <p>Each title is {@code } (space, advance -48) followed by one 256x256 bitmap glyph: a book page in the
 * Manager's language (tools/gen_gui_chrome.py, {@code PAGES}), drawn for the menu's own slots.
 * Glyphs use {@code ascent 23, height 256}. Colour must stay WHITE.
 *
 * <p>While the Aether Journal is on ({@code journal.enabled}), {@link #title(Page)} draws the same menus as pages of
 * the journal instead ({@link JournalPage#leaf}): the chapter's frame, the journal's leather and paper, the chapter's
 * ribbon, the path on the plate ("CHARACTER · SKILLS"). The marks of each {@link Page} are the old page's pockets and
 * seals, so every slot keeps its place. Off, the old overlay comes back unchanged.
 *
 * <p>Titles are display-only. Route by {@code InventoryHolder}, not title text.
 * See GUI_NOTES.md in the resource pack for the glyph map.
 */
public final class ManagerChrome {

    private ManagerChrome() {
    }

    private static Component overlay(char glyph) {
        return Component.text("" + glyph, NamedTextColor.WHITE);
    }

    // 54-slot unless noted
    public static final Component MANAGER = overlay('');
    public static final Component SHOP = overlay('');            // 45
    public static final Component SHOP_BOOSTS = overlay('');     // 45
    public static final Component SHOP_MATERIALS = overlay('');  // 45
    public static final Component SHOP_EXCLUSIVE = overlay('');  // 45
    public static final Component BAZAAR = overlay('');
    public static final Component AUCTION = overlay('');
    public static final Component MARKET_LIST = overlay('');     // 9
    public static final Component MARKET_CONFIRM = overlay('');  // 27
    public static final Component SKILLS = overlay('');
    public static final Component SKILLS_OVERVIEW = overlay('');
    public static final Component SKILLS_PRESETS = overlay('');  // 27
    public static final Component RECIPES = overlay('');
    public static final Component BESTIARY = overlay('');
    public static final Component COLLECTION = overlay('');
    public static final Component CODEX = overlay('');
    public static final Component MILESTONES = overlay('');
    public static final Component JOURNAL = overlay('');
    public static final Component STATS = overlay('');
    public static final Component STORAGE = overlay('');
    public static final Component LOADOUTS = overlay('');
    public static final Component SPAWNS = overlay('');
    public static final Component PETS = overlay('');
    public static final Component CHRONICLE = overlay('');
    public static final Component JOURNAL_HUB = overlay('');  // 27
    public static final Component RECIPES_CRAFT = overlay('');
    public static final Component COLLECTION_ENTRY = overlay('');
    public static final Component BESTIARY_ENTRY = overlay('');
    public static final Component ANVIL = overlay('');

    /** Book pages with captions under their tiles go through {@code BookPage.title(glyph, captions)}. */
    public static final char SHOP_GLYPH = '';
    public static final char SHOP_BOOSTS_GLYPH = '';
    public static final char SHOP_MATERIALS_GLYPH = '';
    public static final char SHOP_EXCLUSIVE_GLYPH = '';
    public static final char CODEX_GLYPH = '';
    public static final char RECIPES_INDEX_GLYPH = '';

    /**
     * Every Manager page: its old glyph, rows, the journal chapter it belongs to, the path on the journal's plate and
     * its marks, one word per row ({@code P} pocket, {@code S} seal, {@code B} small seal, {@code A} arrow), copied
     * from {@code PAGES} in gen_gui_chrome.py.
     */
    public enum Page {
        SHOP('', 5, Chapter.MARKET, "Market · Shop",
                "....S.... ......... .S..S..S. ......... B........"),
        SHOP_BOOSTS('', 5, Chapter.MARKET, "Shop · Boosts",
                "....S.... ......... .S..S..S. ......... B...B...."),
        SHOP_MATERIALS('', 5, Chapter.MARKET, "Shop · Materials",
                "....S.... ......... .S.S.S.S. ......... B...B...."),
        SHOP_EXCLUSIVE('', 5, Chapter.MARKET, "Shop · Exclusive",
                "....S.... ......... .S..S..S. ......... B...B...."),
        BAZAAR('', 6, Chapter.MARKET, "Market · Bazaar",
                "PPPPPPPPP PPPPPPPPP PPPPPPPPP PPPPPPPPP PPPPPPPPP BBBBBBB.B"),
        AUCTION('', 6, Chapter.MARKET, "Market · Auction House",
                "PPPPPPPPP PPPPPPPPP PPPPPPPPP PPPPPPPPP PPPPPPPPP BBBBBBB.B"),
        MARKET_LIST('', 1, Chapter.MARKET, "Market · List item",
                "BBBBBBBBB"),
        MARKET_CONFIRM('', 3, Chapter.MARKET, "Market · Confirm",
                "......... ..B.S.B.. ........."),
        SKILLS('', 6, Chapter.CHARACTER, "Character · Skills",
                "BBBBBBBBB .PPPPPPP. .PPPPPPP. .BBBBBBB. .PPPPPPP. BPPPPPPP."),
        SKILLS_OVERVIEW('', 6, Chapter.CHARACTER, "Skills · Overview",
                "BBBBBBBBB PPPPPPPPP PPPPPPPPP PPPPPPPPP PPPPPPPPP B.BBBBB.B", 5),
        SKILLS_PRESETS('', 3, Chapter.CHARACTER, "Skills · Presets",
                "....B.... .S.S.S.S. B...B...."),
        RECIPES('', 6, Chapter.WORKSHOP, "Workshop · Recipes",
                "....B.... .PPPPPPP. .PPPPPPP. .PPPPPPP. .PPPPPPP. B...B..BB"),
        RECIPES_INDEX('', 6, Chapter.WORKSHOP, "Workshop · Recipes",
                "S.S.S.S.S ......... S.S.S.S.S ......... ..S.S.S.. B........"),
        RECIPES_CRAFT('', 6, Chapter.WORKSHOP, "Recipes · Recipe",
                "....B.... ...PPP... ...PPPAS. ...PPP... ......... B........"),
        BESTIARY('', 6, Chapter.CODEX, "Codex · Bestiary",
                "BBBBBBBBB BBBBBBBBB .PPPPPPP. .PPPPPPP. .PPPPPPP. B.BBBBB..", 2),
        COLLECTION('', 6, Chapter.CODEX, "Codex · Collection",
                "BBBBBBBBB BBBBBBBBB .PPPPPPP. .PPPPPPP. .PPPPPPP. B.BBBBB..", 1),
        COLLECTION_ENTRY('', 6, Chapter.CODEX, "Codex · Collection",
                "BBBBBBB.. ......... PPPPPPPPP ..B.B.B.. ....B.... B.B.B.B..", 1),
        BESTIARY_ENTRY('', 6, Chapter.CODEX, "Codex · Bestiary",
                "BBBBBBB.. ......... PPPPPPPPP ..B.B.B.. ....B.... B.B.B.B..", 2),
        CODEX('', 6, Chapter.CODEX, "Journal · Codex",
                "BBBBBBB.. ......... S.S.S.S.S ......... B.PPPPP.S B........", 0),
        MILESTONES('', 6, Chapter.CODEX, "Codex · Milestones",
                "BBBBBBB.. ......... BPPPPPPPB BPPPPPPPB B.PPPPP.B B...B....", 6),
        JOURNAL('', 6, Chapter.CODEX, "Codex · Boss Journal",
                "BBBBBBB.B ..B.B.B.. .PPPPPPP. .PPPPPPP. .PPPPPPP. B.B.B.B..", 3),
        CHRONICLE('', 6, Chapter.CODEX, "Codex · Chronicle",
                "....B.... ......... .PPPPPPP. .PPPPPPP. .PPPPPPP. B...B...."),
        JOURNAL_HUB('', 3, Chapter.CODEX, "Codex · Bosses",
                "......... ..S...S.. B...B...."),
        STATS('', 6, Chapter.CHARACTER, "Character · Stats",
                "S.S.S.S.S ......... S.S.S.S.S ......... ..S.S.S.S B........"),
        STORAGE('', 6, Chapter.CHARACTER, "Character · Storage",
                "PPPPPPPPP PPPPPPPPP PPPPPPPPP PPPPPPPPP PPPPPPPPP BB..B.B.B"),
        LOADOUTS('', 6, Chapter.CHARACTER, "Character · Loadouts",
                ".BBB.BBB. .PPP.PPP. .PPP.PPP. .PPP.PPP. .PPP.PPP. BBBB.BBB."),
        PETS('', 6, Chapter.CHARACTER, "Character · Pets",
                "PPPPPPPPP PPPPPPPPP PPPPPPPPP PPPPPPPPP PPPPPPPPP BBBBBBBBB"),
        ANVIL('', 6, Chapter.WORKSHOP, "Workshop · Anvil",
                "........B ....S.... .PPPPPPP. .PPPPPPP. ......... ....B....");

        private final char glyph;
        private final int rows;
        private final Chapter chapter;
        private final String label;
        private final String layout;
        /** The slot that is always the chosen one on this page (a Codex page's own tab), or -1. */
        private final int pick;

        Page(char glyph, int rows, Chapter chapter, String label, String layout) {
            this(glyph, rows, chapter, label, layout, -1);
        }

        Page(char glyph, int rows, Chapter chapter, String label, String layout, int pick) {
            this.glyph = glyph;
            this.rows = rows;
            this.chapter = chapter;
            this.label = label;
            this.layout = layout;
            this.pick = pick;
        }

        public int rows() {
            return rows;
        }

        public String label() {
            return label;
        }

        /** The marks as one word per row (tests and tools/render_title.py). */
        public String layoutPicture() {
            return layout.replace(' ', '/');
        }
    }

    /** The page's title: a journal page while the journal is on, else the old Manager overlay. */
    public static Component title(Page page) {
        return title(page, null, null, (int[]) null);
    }

    /** With captions under its tiles (rows 0, 2 and 4). */
    public static Component title(Page page, List<BookPage.Caption> captions) {
        return title(page, captions, null, (int[]) null);
    }

    /**
     * With captions and a plate path of its own ({@code label} null = the page's): "Codex · Oak Log". {@code picked}
     * slots wear gold on the journal page (the open tab, the shown category); the old overlay has no such mark.
     */
    public static Component title(Page page, List<BookPage.Caption> captions, String label, int... picked) {
        if (!JournalText.enabled()) {
            return captions == null || captions.isEmpty() ? overlay(page.glyph) : BookPage.title(page.glyph, captions);
        }
        JournalPage.Leaf leaf = JournalPage.leaf(page.chapter, page.rows, label == null ? page.label : label)
                .layout(page.layout)
                .captions(captions)
                .pick(page.pick);
        if (picked != null) {
            for (int slot : picked) {
                leaf.pick(slot);
            }
        }
        return leaf.title();
    }
}
