package de.aetherion.items.codex;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Collection — everything gathered: ores, stone, wood, dirt, crops, catches, Nether blocks and
 * oddities. The page itself is a {@link CodexBrowser}; this keeps the Manager's entry points.
 */
public final class CollectionGUI {

    public static final String TITLE = "§8Collection";

    private final CodexService service;

    public CollectionGUI(CodexService service) {
        this.service = service;
    }

    public void open(Player player) {
        open(player, null, 1);
    }

    public void open(Player player, String category, int page) {
        if (player == null || service == null) {
            return;
        }
        CodexBrowser browser = CodexBrowser.forLedger(player, CodexBook.Ledger.COLLECTION, category);
        browser.open(player);
    }

    /** Legacy entry point: clicks on the page now route through {@link CodexMenus}. */
    public void handleClick(Player player, int slot) {
        if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
            holder.click(player, slot, ClickType.LEFT);
        }
    }

    public static final class Holder extends CodexBrowser {

        public Holder(String category, int page) {
            super(CodexBook.Ledger.COLLECTION, category, page);
        }
    }
}
