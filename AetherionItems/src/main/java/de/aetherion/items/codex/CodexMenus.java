package de.aetherion.items.codex;

import de.aetherion.items.AetherionItems;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Opens Codex pages and routes every click on them. Pages are {@link CodexView}s, so this is the
 * only listener they need; the Manager listener steps aside for them.
 */
public final class CodexMenus implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CodexView view)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (slot == CodexChrome.CLOSE && view.size() == 54 && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == org.bukkit.Material.BARRIER) {
            player.closeInventory();
            return;
        }
        view.click(player, slot, event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CodexView) {
            event.setCancelled(true);
        }
    }

    /** Existing players arrive with back pay (retroactive tiers and seals) — say so once per join. */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            int ready = CodexRewards.claimableTotal(player);
            if (ready <= 0) {
                return;
            }
            var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
            player.sendMessage(legacy.deserialize("§d✦ Codex §7» §e" + ready + " reward" + (ready == 1 ? "" : "s")
                            + " §7waiting to be claimed. ")
                    .append(legacy.deserialize("§e§l[OPEN CODEX]")
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/codex"))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    legacy.deserialize("§7Collection, Bestiary and skill seals")))));
        }, 20L * 8);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CodexBook.forget(event.getPlayer().getUniqueId());
        CodexBrowser.forget(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ openers

    public static void open(Player player, CodexChrome.Tab tab) {
        switch (tab) {
            case HUB -> openHub(player);
            case COLLECTION -> openCollection(player, null);
            case BESTIARY -> openBestiary(player, null);
            case JOURNAL -> openJournal(player);
            case SKILLS -> openSkills(player);
            case MILESTONES -> openMilestones(player);
        }
    }

    public static void openHub(Player player) {
        new CodexHubGUI.Holder().open(player);
    }

    public static void openCollection(Player player, String category) {
        CodexBrowser.forLedger(player, CodexBook.Ledger.COLLECTION, category).open(player);
    }

    public static void openBestiary(Player player, String category) {
        CodexBrowser.forLedger(player, CodexBook.Ledger.BESTIARY, category).open(player);
    }

    public static void openJournal(Player player) {
        new DungeonJournalGUI.Holder(1).open(player);
    }

    public static void openMilestones(Player player) {
        new CodexMilestonesGUI.Holder().open(player);
    }

    public static void openSkills(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.getSkillMenu() != null) {
            plugin.getSkillMenu().open(player);
        }
    }

    /** The tier ladder of one entry; {@code back} is the page to return to (may be null). */
    public static void openDetail(Player player, String key, CodexView back) {
        CodexBook.Card card = CodexBook.card(key);
        if (card == null) {
            player.sendMessage("§cUnknown Codex entry.");
            return;
        }
        new CodexDetailGUI.Holder(key, back).open(player);
    }

    /** Opens a ledger page if progression allows it, else prints the hint. */
    public static boolean openGated(Player player, CodexChrome.Tab tab) {
        String locked = CodexChrome.gate(player, tab);
        if (locked != null) {
            player.sendMessage(locked);
            CodexChrome.deny(player);
            return false;
        }
        open(player, tab);
        return true;
    }
}
