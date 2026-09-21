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

public final class ConfirmMenu implements Listener {

    private static final int KEEP = 20;
    private static final int DELETE = 24;

    private final NpcEditor editor;

    public ConfirmMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId()),
                54,
                EditorItems.title(player, "npc_confirm", "§8Delete?")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.TNT,
                EditorItems.ui(player, "editor_delete_ask", "§cDelete this NPC?"),
                "§f" + npc.getName(),
                EditorItems.ui(player, "editor_delete_undo", "§cThis cannot be undone.")
        ));
        inventory.setItem(KEEP, EditorItems.button(
                Material.LIME_CONCRETE,
                EditorItems.ui(player, "editor_keep", "§a§lKeep them")
        ));
        inventory.setItem(DELETE, EditorItems.button(
                Material.RED_CONCRETE,
                EditorItems.ui(player, "editor_delete_confirm", "§c§lDelete")
        ));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
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
        CustomNpc npc = editor.storage().get(holder.npcId());
        int slot = event.getRawSlot();
        if (slot == KEEP || slot == EditorItems.BACK) {
            if (npc != null) {
                EditMenu.open(player, npc);
            } else {
                MainMenu.open(player);
            }
            return;
        }
        if (slot == DELETE) {
            editor.delete(player, npc);
            MainMenu.open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public record Holder(String npcId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
