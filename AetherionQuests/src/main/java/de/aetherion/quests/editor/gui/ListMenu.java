package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.NpcEditor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

public final class ListMenu implements Listener {

    private static final int PAGE_SIZE = 36;
    private static final int PREV = 48;
    private static final int BACK = 49;
    private static final int NEXT = 50;

    private final NpcEditor editor;

    public ListMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, int page) {
        NpcEditor editor = de.aetherion.quests.AetherionQuests.getInstance() == null
                ? null
                : de.aetherion.quests.AetherionQuests.getInstance().getNpcEditor();
        if (editor == null) {
            return;
        }
        List<CustomNpc> all = new ArrayList<>(editor.storage().all());
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        int safe = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = Bukkit.createInventory(
                new Holder(safe),
                54,
                EditorItems.title(player, "npc_list", "§8NPC List")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.BOOK,
                "§6Editor NPCs",
                "§7" + all.size() + " saved",
                "§8Dialog vs Quest on each head"
        ));
        int start = safe * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            int slot = 9 + i;
            if (index >= all.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            CustomNpc npc = all.get(index);
            String where = npc.getWorld() == null
                    ? "§8unplaced"
                    : "§7" + npc.getWorld() + " §f" + (int) npc.getX() + " " + (int) npc.getY() + " " + (int) npc.getZ();
            inventory.setItem(slot, EditorItems.head(
                    npc.getSkinUsername(),
                    "§b" + npc.getName(),
                    "§8" + npc.getId(),
                    where,
                    npc.isQuestNpc() ? "§aQuest NPC" : "§bDialog-only",
                    npc.hasLinkedQuest() ? "§7" + npc.getLinkedQuestId() : "§8no quest",
                    "§eClick to edit"
            ));
        }
        inventory.setItem(PREV, EditorItems.button(Material.ARROW, "§7Previous", "§8Page " + (safe + 1) + "/" + pages));
        inventory.setItem(BACK, EditorItems.button(Material.BARRIER, "§cBack"));
        inventory.setItem(NEXT, EditorItems.button(Material.ARROW, "§7Next", "§8Page " + (safe + 1) + "/" + pages));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !NpcEditor.allowed(player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK) {
            MainMenu.open(player);
            return;
        }
        if (slot == PREV) {
            open(player, holder.page() - 1);
            return;
        }
        if (slot == NEXT) {
            open(player, holder.page() + 1);
            return;
        }
        if (slot < 9 || slot > 44) {
            return;
        }
        List<CustomNpc> all = new ArrayList<>(editor.storage().all());
        int index = holder.page() * PAGE_SIZE + (slot - 9);
        if (index < 0 || index >= all.size()) {
            return;
        }
        editor.openEdit(player, all.get(index));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public record Holder(int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
