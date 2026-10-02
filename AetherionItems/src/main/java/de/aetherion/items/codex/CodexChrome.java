package de.aetherion.items.codex;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.progress.ProgressionService;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared page chrome for the Codex (Collection, Bestiary, Journal, Milestones, hub) and the
 * skill overview. Same tab strip on top, same nav row at the bottom, so every page is one click
 * from every other.
 *
 * <pre>
 * row 0  [Codex][Collection][Bestiary][Journal][ HEADER ][Skills][Milestones][tool][tool]
 * row 5  [Back][ ][Prev][Sort][Close][Filter][Next][ ][ ]
 * </pre>
 */
public final class CodexChrome {

    public enum Tab {
        HUB(0),
        COLLECTION(1),
        BESTIARY(2),
        JOURNAL(3),
        SKILLS(5),
        MILESTONES(6);

        private final int slot;

        Tab(int slot) {
            this.slot = slot;
        }

        public int slot() {
            return slot;
        }
    }

    public static final int HEADER = 4;
    public static final int TOOL_A = 7;
    public static final int TOOL_B = 8;
    public static final int BACK = de.aetherion.items.util.ManagerNav.SLOT;
    public static final int PREV = 47;
    public static final int SORT = 48;
    public static final int CLOSE = 49;
    public static final int FILTER = 50;
    public static final int NEXT = 51;

    public static final int[] BODY_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    public static final int[] SIDE_SLOTS = {18, 26, 27, 35, 36, 44};

    private CodexChrome() {
    }

    // ------------------------------------------------------------------ frame

    /** Fills the page: black header row, gray body, the tab strip, and the nav basics. */
    public static void frame(Inventory inventory, Player player, Tab active) {
        ItemStack header = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack body = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, (slot < 9 || slot >= inventory.getSize() - 9 ? header : body).clone());
        }
        for (Tab tab : Tab.values()) {
            inventory.setItem(tab.slot(), tabItem(player, tab, tab == active));
        }
        inventory.setItem(CLOSE, GuiItems.named(Material.BARRIER, "§cClose", "§8Esc works too."));
    }

    /** Tints the side columns of the body in a category's pane color. */
    public static void sides(Inventory inventory, Material paneColor) {
        ItemStack pane = pane(paneColor);
        for (int slot : SIDE_SLOTS) {
            inventory.setItem(slot, pane.clone());
        }
    }

    public static void row(Inventory inventory, int row, Material paneColor) {
        ItemStack pane = pane(paneColor);
        for (int slot = row * 9; slot < row * 9 + 9; slot++) {
            inventory.setItem(slot, pane.clone());
        }
    }

    public static ItemStack pane(Material material) {
        return GuiItems.named(material, " ");
    }

    public static ItemStack back(String to) {
        return GuiItems.named(Material.ARROW, "§eBack", "§7To " + to + ".");
    }

    public static ItemStack prev(int page, int pages) {
        return GuiItems.named(Material.ARROW, "§ePrevious Page", "§7Page §f" + (page - 1) + "§8/§7" + pages);
    }

    public static ItemStack next(int page, int pages) {
        return GuiItems.named(Material.ARROW, "§eNext Page", "§7Page §f" + (page + 1) + "§8/§7" + pages);
    }

    // ------------------------------------------------------------------ tabs

    private static ItemStack tabItem(Player player, Tab tab, boolean active) {
        String locked = gate(player, tab);
        if (locked != null && !active) {
            return GuiItems.named(Material.GRAY_DYE, "§8" + tabName(tab) + " §7· Locked", locked);
        }
        List<String> lore = new ArrayList<>();
        lore.add("§8" + tabBlurb(tab));
        String status = tabStatus(player, tab);
        if (status != null) {
            lore.add(status);
        }
        lore.add("");
        lore.add(active ? "§a▶ You are here" : "§eClick to open");
        ItemStack item = GuiItems.named(tabIcon(tab), (active ? "§a▶ " : tabColor(tab)) + tabName(tab), lore);
        return active ? glint(item) : item;
    }

    private static String tabName(Tab tab) {
        return switch (tab) {
            case HUB -> "Aetherion Codex";
            case COLLECTION -> "Collection";
            case BESTIARY -> "Bestiary";
            case JOURNAL -> "Boss Journal";
            case SKILLS -> "Skills";
            case MILESTONES -> "Milestones";
        };
    }

    private static String tabColor(Tab tab) {
        return switch (tab) {
            case HUB -> "§d";
            case COLLECTION -> "§a";
            case BESTIARY -> "§6";
            case JOURNAL -> "§5";
            case SKILLS -> "§b";
            case MILESTONES -> "§e";
        };
    }

    private static Material tabIcon(Tab tab) {
        return switch (tab) {
            case HUB -> Material.ENCHANTED_BOOK;
            case COLLECTION -> Material.IRON_PICKAXE;
            case BESTIARY -> Material.BONE;
            case JOURNAL -> Material.WRITABLE_BOOK;
            case SKILLS -> Material.EXPERIENCE_BOTTLE;
            case MILESTONES -> Material.BEACON;
        };
    }

    private static String tabBlurb(Tab tab) {
        return switch (tab) {
            case HUB -> "Every ledger on one page.";
            case COLLECTION -> "Everything you gathered, tiered.";
            case BESTIARY -> "Everything you fought, tiered.";
            case JOURNAL -> "Bosses and what they drop.";
            case SKILLS -> "Your loadout and every skill level.";
            case MILESTONES -> "Permanent perks from the ledgers.";
        };
    }

    private static String tabStatus(Player player, Tab tab) {
        CodexService service = CodexRewards.service();
        if (service == null) {
            return null;
        }
        return switch (tab) {
            case HUB -> {
                int ready = CodexRewards.claimableTotal(player);
                yield ready > 0 ? "§e" + ready + " reward" + (ready == 1 ? "" : "s") + " to claim" : null;
            }
            case COLLECTION -> ledgerStatus(service, player, CodexBook.Ledger.COLLECTION);
            case BESTIARY -> ledgerStatus(service, player, CodexBook.Ledger.BESTIARY);
            default -> null;
        };
    }

    private static String ledgerStatus(CodexService service, Player player, CodexBook.Ledger ledger) {
        CodexBook.Summary summary = CodexBook.summary(service, player, ledger);
        String line = "§7Level §f" + summary.level();
        if (summary.claimableTiers() > 0) {
            line += " §8· §e" + summary.claimableTiers() + " to claim";
        }
        return line;
    }

    /** Hint when a tab is still locked by progression, or {@code null} when it's open. */
    public static String gate(Player player, Tab tab) {
        AetherionItems plugin = AetherionItems.getInstance();
        ProgressionService progress = plugin == null ? null : plugin.progress();
        if (progress == null || player == null) {
            return null;
        }
        return switch (tab) {
            case COLLECTION -> progress.collection(player) ? null : progress.collectionHint();
            case BESTIARY -> progress.bestiary(player) ? null : progress.bestiaryHint();
            case JOURNAL -> progress.journal(player) ? null : progress.journalHint();
            case SKILLS -> progress.skills(player) ? null : progress.hint(ProgressionService.Flag.SKILLS);
            default -> null;
        };
    }

    /** Handles a click on the tab strip. Returns true when {@code slot} was a tab. */
    public static boolean clickTab(Player player, int slot, Tab active) {
        for (Tab tab : Tab.values()) {
            if (tab.slot() != slot) {
                continue;
            }
            if (tab == active) {
                return true;
            }
            String locked = gate(player, tab);
            if (locked != null) {
                player.sendMessage(locked);
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 0.8f);
                return true;
            }
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.0f);
            CodexMenus.open(player, tab);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ bits

    public static ItemStack glint(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            GuiItems.hideVanilla(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    public static void deny(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
    }
}
