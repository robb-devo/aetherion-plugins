package de.aetherion.items.codex;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class DungeonJournalGUI {

    public static final String TITLE = "§8Dungeon Journal";
    public static final int BACK_SLOT = CodexGui.BACK_SLOT;

    private final CodexService service;

    public DungeonJournalGUI(CodexService service) {
        this.service = service;
    }

    public void open(Player player) {
        open(player, 1);
    }

    public void open(Player player, int page) {
        List<BossJournal.Entry> entries = BossJournal.entries();
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) CodexGui.ENTRY_SLOTS.length));
        int safePage = Math.min(Math.max(page, 1), pages);
        Holder holder = new Holder(safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54, TITLE);
        CodexGui.fill(inventory);

        inventory.setItem(4, CodexGui.named(
                Material.WRITABLE_BOOK,
                "§5Dungeon Journal",
                "§7Bosses you have fought",
                "§7and the loot they can drop."
        ));

        int start = (safePage - 1) * CodexGui.ENTRY_SLOTS.length;
        for (int i = 0; i < CodexGui.ENTRY_SLOTS.length; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                inventory.setItem(CodexGui.ENTRY_SLOTS[i], CodexGui.named(Material.BLACK_STAINED_GLASS_PANE, " "));
                continue;
            }
            inventory.setItem(CodexGui.ENTRY_SLOTS[i], icon(player, entries.get(index)));
        }

        if (safePage > 1) {
            inventory.setItem(CodexGui.PREV_SLOT, CodexGui.named(Material.ARROW, "§ePrevious", "§7Page " + (safePage - 1)));
        }
        inventory.setItem(BACK_SLOT, de.aetherion.items.util.ManagerNav.button());
        if (safePage < pages) {
            inventory.setItem(CodexGui.NEXT_SLOT, CodexGui.named(Material.ARROW, "§eNext", "§7Page " + (safePage + 1)));
        }

        if (entries.isEmpty()) {
            inventory.setItem(22, CodexGui.named(
                    Material.BARRIER,
                    "§cNo bosses loaded",
                    "§7BossEngine is missing",
                    "§7or has no templates."
            ));
        }

        player.openInventory(inventory);
    }

    public void handleClick(Player player, int slot) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        if (slot == BACK_SLOT) {
            return;
        }
        if (slot == CodexGui.PREV_SLOT) {
            open(player, Math.max(1, holder.page - 1));
            return;
        }
        if (slot == CodexGui.NEXT_SLOT) {
            open(player, holder.page + 1);
        }
    }

    private ItemStack icon(Player player, BossJournal.Entry entry) {
        long kills = service.bossKills(player, entry.id());
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Kills: §f" + kills);
        if (entry.experience() > 0) {
            lore.add("§7XP: §e" + entry.experience());
        }
        lore.add("");
        lore.add("§6Drops");
        if (entry.drops().isEmpty()) {
            lore.add("§8No listed loot.");
        } else {
            String lastRole = "";
            int shown = 0;
            for (BossJournal.Drop drop : entry.drops()) {
                if (shown >= 12) {
                    lore.add("§8...");
                    break;
                }
                if (!drop.role().equals(lastRole)) {
                    lore.add("§8" + drop.role());
                    lastRole = drop.role();
                }
                lore.add(drop.line());
                shown++;
            }
        }
        ItemStack item = new ItemStack(entry.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(entry.displayName());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static final class Holder implements InventoryHolder {
        private final int page;

        public Holder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
