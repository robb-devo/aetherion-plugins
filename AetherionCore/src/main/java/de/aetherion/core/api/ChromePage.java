package de.aetherion.core.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Manager's book page for any chest menu, built in its title from the pack font {@code aetherion:kit}
 * ({@code AetherionItems/tools/gen_gui_kit.py}; referenced from {@code minecraft:default}, so plain legacy titles
 * reach it): the menu frame in a theme's accent, the parchment, a ledger pocket, seal or small seal under the slots
 * that hold something, and the title plate with the menu's name in the plate's caps.
 *
 * <p>{@link #open} reads the marks off the inventory as it is: glass fillers (any stained pane with a blank name)
 * turn invisible and leave bare parchment, navigation buttons get their pack icons ({@link #BACK} and friends) and
 * sit on small seals, a handful of other items on seals, a list of them in pockets. Menus that need something
 * else (lights made of glass, a fixed layout) pass their own marks.
 *
 * <p>Titles are display-only: route by {@code InventoryHolder}, never by title text.
 */
public final class ChromePage {

    /** Accents; order = {@code THEMES} in gen_gui_kit.py. */
    public enum Theme {
        AETHER, PETS, CASINO, ROULETTE, GUILD, ISLAND, TRADE, DUNGEON, QUEST, FARM, FISH, FORAGE, MINE, FORGE, BETA,
        STORAGE
    }

    /** What sits under a slot. */
    public enum Mark { POCKET, SEAL, BUTTON }

    /** Pack icons (CustomModelData, tools/gen_gui_icons.py): the spacer, on every stained glass pane. */
    public static final int SPACER = 7700;
    /** On {@link Material#BARRIER}. */
    public static final int CLOSE = 7720;
    /** On {@link Material#ARROW}: the round back hook, the page chevrons. */
    public static final int BACK = 7724;
    public static final int PREV = 7725;
    public static final int NEXT = 7726;
    /** On {@link Material#GRAY_STAINED_GLASS_PANE}: dimmed page chevrons. */
    public static final int PREV_OFF = 7727;
    public static final int NEXT_OFF = 7728;

    private static final Set<Integer> NAV = Set.of(CLOSE, BACK, PREV, NEXT, PREV_OFF, NEXT_OFF);
    /** More single items than this on one page read as a list: pockets instead of seals. */
    private static final int MAX_SEALS = 12;

    private static final int FRAME = 0xE100;
    private static final int PAGE = 0xE180;
    private static final int MARK = 0xE200;
    private static final int PLATE = 0xE300;
    private static final int LETTER = 0xE500;
    private static final int SEAL_GLYPH = 16;
    private static final int BUTTON_GLYPH = 17;
    /** Frame and page glyphs measure 256 (pinned last column): advance 257. Marks measure 20. */
    private static final int PAGE_ADVANCE = 257;
    private static final int MARK_ADVANCE = 21;
    /** Plate end widths per wing level (none, short, long, full) and the fills, as the generator prints them. */
    private static final int[] LEFT_W = {25, 30, 37, 46};
    private static final int[] RIGHT_W = {23, 28, 35, 44};
    private static final int[] REACH = {6, 11, 18, 27};
    private static final int[] FILLS = {1, 2, 4, 8, 16, 32, 64};
    /** Label start from the plate's left border; plate width = label + 36; wings stay inside GUI x 8..167. */
    private static final int LABEL_DX = 19;
    private static final int PLATE_EXTRA = 36;
    private static final int MAX_LABEL = 124;
    private static final String LETTERS = "!#%&'()*+,-./0123456789:?ABCDEFGHIJKLMNOPQRSTUVWXYZ·";
    private static final String LETTER_WIDTHS = "1555122552315535555555515555555553555555555555555553";
    private static final int SPACE_W = 3;

    private ChromePage() {
    }

    // ------------------------------------------------------------------------------------------------ opening

    /** Opens {@code inventory} as a book page, marks read off its contents, the plate named after its title. */
    public static InventoryView open(Player player, Inventory inventory, Theme theme) {
        return open(player, inventory, theme, null, null);
    }

    /** Opens {@code inventory} as a book page, marks read off its contents. */
    public static InventoryView open(Player player, Inventory inventory, Theme theme, String label) {
        return open(player, inventory, theme, label, null);
    }

    /** Opens {@code inventory} as a book page with these marks ({@code null}: read off its contents). */
    public static InventoryView open(Player player, Inventory inventory, Theme theme, String label,
                                     Map<Integer, Mark> marks) {
        return open(player, inventory, theme, label, marks, true);
    }

    /**
     * Opens {@code inventory} as a book page; {@code hideFillers} false keeps neutral glass as it is (a menu whose
     * panes are lights or other art of its own).
     */
    public static InventoryView open(Player player, Inventory inventory, Theme theme, String label,
                                     Map<Integer, Mark> marks, boolean hideFillers) {
        if (inventory == null) {
            return null;
        }
        Map<Integer, Mark> page = marks == null ? marks(inventory) : marks;
        if (hideFillers) {
            hideFillers(inventory);
        }
        dressNav(inventory);
        InventoryView view = player.openInventory(inventory);
        if (view == null || view.getTopInventory() != inventory || !chest(inventory)) {
            return view;
        }
        view.setTitle(title(theme, inventory.getSize() / 9, label == null ? view.getTitle() : label, page));
        return view;
    }

    /** Redraws the page of the menu {@code player} has open (after it moved its items about). */
    public static void retitle(Player player, Theme theme, String label) {
        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (!chest(top)) {
            return;
        }
        hideFillers(top);
        view.setTitle(title(theme, top.getSize() / 9, label, marks(top)));
    }

    private static boolean chest(Inventory inventory) {
        return inventory != null && inventory.getType() == InventoryType.CHEST
                && inventory.getSize() % 9 == 0 && inventory.getSize() >= 9 && inventory.getSize() <= 54;
    }

    // ------------------------------------------------------------------------------------------------ items

    /** An empty, tooltip-less filler that the pack draws as nothing: the page shows through. */
    public static ItemStack spacer() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.setCustomModelData(SPACER);
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Puts pack icon {@code cmd} on {@code item} (the icon's own material) and returns it. */
    public static ItemStack icon(ItemStack item, int cmd) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Stained glass with a blank name: a filler (its colour was the old menu's frame; the page draws that now). */
    public static boolean isFiller(ItemStack item) {
        if (item == null || !item.getType().name().endsWith("STAINED_GLASS_PANE")) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null || !meta.hasDisplayName() || meta.getDisplayName().isBlank() || meta.isHideTooltip();
    }

    private static boolean isNav(ItemStack item) {
        if (navIcon(item) > 0) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && NAV.contains(meta.getCustomModelData());
    }

    /**
     * The pack icon a plain navigation button should wear, or 0: a single arrow is Back, Previous or Next by its
     * name; a barrier named Close / Leave / Exit (or the German words) is Close. Anything with a model already, a
     * stack, or a name about arrows (a shop's ammunition) is left alone.
     */
    static int navIcon(ItemStack item) {
        if (item == null || item.getAmount() != 1
                || item.getType() != Material.ARROW && item.getType() != Material.BARRIER) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.hasCustomModelData() || !meta.hasDisplayName()) {
            return 0;
        }
        String name = meta.getDisplayName().replaceAll("§.", "").toLowerCase(Locale.ROOT);
        if (item.getType() == Material.BARRIER) {
            return name.matches(".*\\b(close|leave|exit|cancel|schlie(ss|ß)en|verlassen|abbrechen)\\b.*") ? CLOSE : 0;
        }
        if (name.contains("arrow") || name.contains("pfeil")) {
            return 0;
        }
        if (name.matches(".*(→|▶|»|\\bnext\\b|\\bweiter\\b|\\bmore\\b|nächste).*")) {
            return NEXT;
        }
        if (name.matches(".*(◀|\\bprev|\\bprevious\\b|\\bpage\\b|\\bseite\\b|vorherige).*")) {
            return PREV;
        }
        return BACK;
    }

    /** Puts the navigation icons ({@link #navIcon}) on plain back, page and close buttons. */
    public static void dressNav(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            int icon = navIcon(item);
            if (icon > 0) {
                inventory.setItem(slot, icon(item.clone(), icon));
            }
        }
    }

    /** Turns every filler of {@code inventory} invisible (same item, the spacer model). */
    public static void hideFillers(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isFiller(item)) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == SPACER) {
                continue;
            }
            ItemStack hidden = item.clone();
            ItemMeta hiddenMeta = hidden.getItemMeta();
            if (hiddenMeta != null) {
                hiddenMeta.setCustomModelData(SPACER);
                hiddenMeta.setHideTooltip(true);
                hidden.setItemMeta(hiddenMeta);
            }
            inventory.setItem(slot, hidden);
        }
    }

    /**
     * Marks as the contents suggest: nothing under fillers, small seals under navigation buttons; up to
     * {@value #MAX_SEALS} other items get seals (small ones where they touch a neighbour), more get pockets. Empty
     * slots in a menu laid out with fillers are where the player puts things: pockets (up to {@value #MAX_SEALS}
     * of them; more empty space is just space).
     */
    public static Map<Integer, Mark> marks(Inventory inventory) {
        Map<Integer, Mark> marks = new HashMap<>();
        Map<Integer, Boolean> items = new HashMap<>();
        java.util.List<Integer> empty = new java.util.ArrayList<>();
        boolean fillers = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                empty.add(slot);
                continue;
            }
            if (isFiller(item)) {
                fillers = true;
                continue;
            }
            if (isNav(item)) {
                marks.put(slot, Mark.BUTTON);
            } else {
                items.put(slot, true);
            }
        }
        boolean list = items.size() > MAX_SEALS;
        for (int slot : items.keySet()) {
            if (list) {
                marks.put(slot, Mark.POCKET);
                continue;
            }
            boolean touches = false;
            for (int other : new int[]{slot - 9, slot + 9, slot % 9 > 0 ? slot - 1 : -1, slot % 9 < 8 ? slot + 1 : -1}) {
                touches |= other >= 0 && (items.containsKey(other) || marks.containsKey(other));
            }
            marks.put(slot, touches ? Mark.BUTTON : Mark.SEAL);
        }
        if (fillers && !empty.isEmpty() && empty.size() <= MAX_SEALS) {
            for (int slot : empty) {
                marks.put(slot, Mark.POCKET);
            }
        }
        return marks;
    }

    /** Marks for a block of slots: {@code mark} on every slot from {@code from} to {@code to} (inclusive, by row). */
    public static Map<Integer, Mark> block(Map<Integer, Mark> marks, int from, int to, Mark mark) {
        for (int slot = from; slot <= to; slot++) {
            if (slot % 9 >= from % 9 && slot % 9 <= to % 9) {
                marks.put(slot, mark);
            }
        }
        return marks;
    }

    // ------------------------------------------------------------------------------------------------ title

    /** The legacy title that draws the page: frame, parchment, marks, plate, label. */
    public static String title(Theme theme, int rows, String label, Map<Integer, Mark> marks) {
        rows = Math.max(1, Math.min(6, rows));
        StringBuilder out = new StringBuilder("§f");
        int[] cursor = {8};
        moveTo(out, cursor, -40);
        out.append((char) (FRAME + 8 * theme.ordinal() + rows));
        cursor[0] += PAGE_ADVANCE;
        moveTo(out, cursor, -40);
        out.append((char) (PAGE + rows));
        cursor[0] += PAGE_ADVANCE;
        for (int slot = 0; slot < rows * 9; slot++) {
            Mark mark = marks == null ? null : marks.get(slot);
            if (mark == null) {
                continue;
            }
            int row = slot / 9;
            int col = slot % 9;
            int glyph = switch (mark) {
                case SEAL -> SEAL_GLYPH;
                case BUTTON -> BUTTON_GLYPH;
                case POCKET -> (pocket(marks, slot - 9) ? 1 : 0)
                        | (row < rows - 1 && pocket(marks, slot + 9) ? 2 : 0)
                        | (col > 0 && pocket(marks, slot - 1) ? 4 : 0)
                        | (col < 8 && pocket(marks, slot + 1) ? 8 : 0);
            };
            moveTo(out, cursor, 6 + 18 * col);
            out.append((char) (MARK + 32 * row + glyph));
            cursor[0] += MARK_ADVANCE;
        }
        plate(out, cursor, theme, label(label));
        return out.toString();
    }

    private static boolean pocket(Map<Integer, Mark> marks, int slot) {
        return slot >= 0 && marks.get(slot) == Mark.POCKET;
    }

    /** A label too wide for the plate keeps what follows its last "·" ("MISS LEDGER · OPEN ROADS"), then whole words. */
    static String fit(String text) {
        if (width(text) > MAX_LABEL && text.contains("·")) {
            text = text.substring(text.lastIndexOf('·') + 1).trim();
        }
        while (width(text) > MAX_LABEL && text.contains(" ")) {
            text = text.substring(0, text.lastIndexOf(' ')).trim();
        }
        while (!text.isEmpty() && width(text) > MAX_LABEL) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text;
    }

    private static void plate(StringBuilder out, int[] cursor, Theme theme, String text) {
        text = fit(text);
        int tw = Math.max(1, width(text));
        int x0 = 88 - (tw + PLATE_EXTRA) / 2;
        int x1 = x0 + tw + PLATE_EXTRA - 1;
        int level = REACH.length - 1;
        while (level > 0 && (x0 - REACH[level] < 8 || x1 + REACH[level] > 167)) {
            level--;
        }
        int base = PLATE + 16 * theme.ordinal();
        int lx = x0 + LABEL_DX;
        moveTo(out, cursor, lx - LEFT_W[level]);
        out.append((char) (base + level));
        cursor[0] += LEFT_W[level] + 1;
        int x = lx;
        int rest = tw;
        for (int i = FILLS.length - 1; i >= 0; i--) {
            while (rest >= FILLS[i]) {
                moveTo(out, cursor, x);
                out.append((char) (base + 8 + i));
                cursor[0] += FILLS[i] + 1;
                x += FILLS[i];
                rest -= FILLS[i];
            }
        }
        moveTo(out, cursor, lx + tw);
        out.append((char) (base + 4 + level));
        cursor[0] += RIGHT_W[level] + 1;
        moveTo(out, cursor, lx);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                moveTo(out, cursor, cursor[0] + SPACE_W + 1);
                continue;
            }
            int index = LETTERS.indexOf(c);
            out.append((char) (LETTER + index));
            cursor[0] += LETTER_WIDTHS.charAt(index) - '0' + 1;
        }
    }

    private static void moveTo(StringBuilder out, int[] cursor, int x) {
        out.append(BookPage.space(x - cursor[0]));
        cursor[0] = x;
    }

    /** Plate text of a title: colour codes and symbols the plate has no letter for dropped, umlauts spelled out. */
    public static String label(String title) {
        if (title == null) {
            return "";
        }
        String text = title.replaceAll("§.", "").toUpperCase(Locale.ROOT)
                .replace("Ä", "AE").replace("Ö", "OE").replace("Ü", "UE").replace("ẞ", "SS")
                .replace("ß", "SS").replace('—', '-').replace('–', '-').replace('•', '·')
                .replace('→', '-').replace('←', '-');
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || LETTERS.indexOf(c) >= 0) {
                out.append(c);
            }
        }
        return out.toString().replaceAll(" {2,}", " ").trim().replaceAll("^[·\\-:]+|[·\\-:]+$", "").trim();
    }

    /** Width of plate text in the caps (letters 1..5 px, 1 px apart; a space is 3). */
    public static int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            width += (c == ' ' ? SPACE_W : LETTER_WIDTHS.charAt(LETTERS.indexOf(c)) - '0') + 1;
        }
        return Math.max(0, width - 1);
    }
}
