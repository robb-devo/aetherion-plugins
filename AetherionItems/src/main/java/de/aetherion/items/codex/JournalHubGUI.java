package de.aetherion.items.codex;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.progress.ProgressionService;

import org.bukkit.entity.Player;

/**
 * What used to be the Manager's Journal shelf. The Boss Journal and the Chronicle both live in the Codex now
 * (the journal tab, the Chronicle button on its page); this only answers whether either has anything yet.
 */
public final class JournalHubGUI {

    private JournalHubGUI() {
    }

    /** True when the Boss Journal is open or the Chronicle has a page. */
    public static boolean available(Player player) {
        return bossJournalOpen(player) || !ChronicleGUI.pages(player).isEmpty();
    }

    static boolean bossJournalOpen(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        ProgressionService progress = plugin == null ? null : plugin.progress();
        return progress == null || progress.journal(player);
    }
}
