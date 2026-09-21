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

    private static final int PAGE_SIZE = 28;
    private static final int PREV = 45;
    private static final int NEXT = 53;

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
                EditorItems.title(player, "npc_list", "§8Your NPCs")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.BOOK,
                EditorItems.ui(player, "editor_list", "§6Your NPCs"),
                all.isEmpty()
                        ? EditorItems.ui(player, "editor_list_empty", "§7None yet. Create one from the home screen.")
                        : "§7" + all.size()
        ));
        int start = safe * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            int slot = 10 + (i % 7) + (i / 7) * 9;
            if (index >= all.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            CustomNpc npc = all.get(index);
            String where = npc.getWorld() == null
                    ? EditorItems.ui(player, "editor_unplaced", "§8unplaced")
                    : "§7" + npc.getWorld() + " §f" + (int) npc.getX() + " " + (int) npc.getY() + " " + (int) npc.getZ();
            inventory.setItem(slot, EditorItems.head(
                    npc.getSkinUsername(),
                    "§b" + npc.getName(),
                    npc.isQuestNpc()
                            ? EditorItems.ui(player, "editor_mode_quest", "§aGives a quest")
                            : EditorItems.ui(player, "editor_mode_talk", "§bJust talks"),
                    where,
                    EditorItems.ui(player, "editor_click_edit", "§eClick to edit")
            ));
        }
        inventory.setItem(PREV, EditorItems.button(Material.ARROW,
                EditorItems.ui(player, "editor_prev", "§7Previous"),
                "§8" + (safe + 1) + "/" + pages));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        inventory.setItem(NEXT, EditorItems.button(Material.ARROW,
                EditorItems.ui(player, "editor_next", "§7Next"),
                "§8" + (safe + 1) + "/" + pages));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        Player player = EditorItems.editorClick(event);
        if (player == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == EditorItems.BACK) {
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
        List<CustomNpc> all = new ArrayList<>(editor.storage().all());
        int index = indexFromSlot(slot, holder.page());
        if (index < 0 || index >= all.size()) {
            return;
        }
        editor.openEdit(player, all.get(index));
    }

    private static int indexFromSlot(int slot, int page) {
        for (int i = 0; i < PAGE_SIZE; i++) {
            int candidate = 10 + (i % 7) + (i / 7) * 9;
            if (candidate == slot) {
                return page * PAGE_SIZE + i;
            }
        }
        return -1;
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
