package de.aetherion.items.codex;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.progress.ProgressionService;
import de.aetherion.items.util.GuiItems;
import de.aetherion.items.util.ManagerNav;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Manager → Journal: two books on one shelf. The Boss Journal (unchanged, still gated by its
 * own progression) and the Chronicle (story finds).
 */
public final class JournalHubGUI {

    public static final String TITLE = "§8Journal";

    private static final int BOSS_SLOT = 11;
    private static final int CHRONICLE_SLOT = 15;
    private static final int CLOSE_SLOT = 22;

    private JournalHubGUI() {
    }

    public static void open(Player player) {
        if (player != null) {
            new Holder().open(player);
        }
    }

    /** True when the Manager's Journal tab has anything to show. */
    public static boolean available(Player player) {
        return bossJournalOpen(player) || !ChronicleGUI.pages(player).isEmpty();
    }

    static boolean bossJournalOpen(Player player) {
        ProgressionService progress = progress();
        return progress == null || progress.journal(player);
    }

    private static ProgressionService progress() {
        AetherionItems plugin = AetherionItems.getInstance();
        return plugin == null ? null : plugin.progress();
    }

    public static final class Holder extends CodexView {

        @Override
        protected String title() {
            return TITLE;
        }

        @Override
        protected int size() {
            return 27;
        }

        @Override
        public void render(Player player) {
            ItemStack pane = CodexChrome.pane(Material.BLACK_STAINED_GLASS_PANE);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, pane.clone());
            }
            if (bossJournalOpen(player)) {
                inventory.setItem(BOSS_SLOT, GuiItems.named(Material.WRITABLE_BOOK, "§5Boss Journal",
                        "§7Boss kills and what they",
                        "§7can drop. One page per boss.",
                        "",
                        "§eClick to open"));
            } else {
                ProgressionService progress = progress();
                inventory.setItem(BOSS_SLOT, GuiItems.named(Material.GRAY_DYE, "§8Boss Journal §7· Locked",
                        progress == null ? "§7A dungeon boss. Then the receipts." : progress.journalHint()));
            }
            Set<String> pages = ChronicleGUI.pages(player);
            if (pages.isEmpty()) {
                inventory.setItem(CHRONICLE_SLOT, GuiItems.named(Material.GRAY_DYE, "§8Chronicle §7· empty",
                        "§7Old stories, old finds.",
                        "§7Mother Berit knows where it starts."));
            } else {
                inventory.setItem(CHRONICLE_SLOT, GuiItems.named(Material.LECTERN, "§6Chronicle",
                        "§7The old lullaby, as you found it.",
                        "§7Pages: §f" + pages.size(),
                        "",
                        "§eClick to open"));
            }
            inventory.setItem(ManagerNav.SLOT_27, ManagerNav.button());
            inventory.setItem(CLOSE_SLOT, GuiItems.named(Material.BARRIER, "§cClose", "§8Esc works too."));
        }

        @Override
        public void click(Player player, int slot, ClickType click) {
            if (slot == ManagerNav.SLOT_27) {
                CodexChrome.click(player);
                ManagerNav.openManager(player);
                return;
            }
            if (slot == CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            if (slot == BOSS_SLOT) {
                if (!bossJournalOpen(player)) {
                    ProgressionService progress = progress();
                    if (progress != null) {
                        player.sendMessage(progress.journalHint());
                    }
                    CodexChrome.deny(player);
                    return;
                }
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.0f);
                CodexMenus.openJournal(player);
                return;
            }
            if (slot == CHRONICLE_SLOT) {
                if (ChronicleGUI.pages(player).isEmpty()) {
                    CodexChrome.deny(player);
                    return;
                }
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.0f);
                ChronicleGUI.open(player);
            }
        }
    }
}
