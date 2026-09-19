package de.aetherion.items.codex;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public final class BestiaryGUI {

    public static final String TITLE = "§8Bestiary";

    private final CodexService service;

    public BestiaryGUI(CodexService service) {
        this.service = service;
    }

    public void open(Player player) {
        open(player, CodexCatalog.MOB_HOSTILE, 1);
    }

    public void open(Player player, String category, int page) {
        String selected = CodexCatalog.MOB_CATEGORIES.contains(category) ? category : CodexCatalog.MOB_HOSTILE;
        List<CodexCatalog.Entry> entries = CodexCatalog.mobs(selected);
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) CodexGui.ENTRY_SLOTS.length));
        int safePage = Math.min(Math.max(page, 1), pages);
        Holder holder = new Holder(selected, safePage);
        CodexGui.openPaged(
                player,
                holder,
                TITLE,
                org.bukkit.Material.BONE,
                "§6Bestiary",
                "§7Kills, including dungeon and boss kills.",
                CodexCatalog.MOB_CATEGORIES,
                selected,
                entries,
                safePage,
                service,
                true
        );
    }

    public void handleClick(Player player, int slot) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        if (slot == CodexGui.BACK_SLOT) {
            return;
        }
        if (slot == CodexGui.PREV_SLOT) {
            open(player, holder.category, Math.max(1, holder.page - 1));
            return;
        }
        if (slot == CodexGui.NEXT_SLOT) {
            open(player, holder.category, holder.page + 1);
            return;
        }
        for (int i = 0; i < CodexGui.CATEGORY_SLOTS.length && i < CodexCatalog.MOB_CATEGORIES.size(); i++) {
            if (slot == CodexGui.CATEGORY_SLOTS[i]) {
                open(player, CodexCatalog.MOB_CATEGORIES.get(i), 1);
                return;
            }
        }
    }

    public static final class Holder implements InventoryHolder {
        private final String category;
        private final int page;

        public Holder(String category, int page) {
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
