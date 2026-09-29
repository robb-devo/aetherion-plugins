package de.aetherion.items.codex;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Bestiary — everything fought: Borderlands, Nether, End, wildlife, waters, sea creatures,
 * dungeon floors and every live BossEngine boss. The page itself is a {@link CodexBrowser}.
 */
public final class BestiaryGUI {

    public static final String TITLE = "§8Bestiary";

    private final CodexService service;

    public BestiaryGUI(CodexService service) {
        this.service = service;
    }

    public void open(Player player) {
        open(player, null, 1);
    }

    public void open(Player player, String category, int page) {
        if (player == null || service == null) {
            return;
        }
        CodexBrowser browser = CodexBrowser.forLedger(player, CodexBook.Ledger.BESTIARY, category);
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
            super(CodexBook.Ledger.BESTIARY, category, page);
        }
    }
}
