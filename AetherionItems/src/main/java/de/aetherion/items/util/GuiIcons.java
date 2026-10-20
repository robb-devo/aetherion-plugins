package de.aetherion.items.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Pack icons shared by every chrome menu: invisible spacers and the navigation buttons.
 *
 * <p>Each value is a CustomModelData override in one material's item model (the pack's
 * {@code tools/gen_gui_icons.py} draws them, {@code tools/merge_gui_overrides.py} merges them), so an icon
 * only shows on the material it was made for: spacers on grey, black or light-grey panes, back/prev/next
 * on {@link Material#ARROW}, close on {@link Material#BARRIER}, the dimmed page arrows on a grey pane, section labels on a black pane.
 * All values sit above every item CMD in the pack (max 4206).
 */
public final class GuiIcons {

    public static final int SPACER = 7700;
    public static final int CLOSE = 7720;
    public static final int BACK = 7724;
    public static final int PREV = 7725;
    public static final int NEXT = 7726;
    public static final int PREV_OFF = 7727;
    public static final int NEXT_OFF = 7728;
    /** Row labels on the stats page, on a black pane. */
    public static final int SECTION_COMBAT = 7729;
    public static final int SECTION_GATHERING = 7730;
    public static final int SECTION_FISHING = 7731;

    private GuiIcons() {
    }

    /**
     * An empty, tooltip-less slot filler. The menu's chrome (the font-glyph title texture) draws the
     * panel and wells underneath, so spacers belong only in menus that have one.
     */
    public static ItemStack spacer() {
        ItemStack item = GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(SPACER);
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Puts icon {@code cmd} on {@code item} (which must be the icon's material) and returns it. */
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
}
