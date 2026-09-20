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

    private static final int YES = 20;
    private static final int NO = 24;

    private final NpcEditor editor;

    public ConfirmMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId()),
                54,
                EditorItems.title(player, "npc_confirm", "§8Confirm")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.TNT,
                "§cDelete §f" + npc.getName() + "§c?",
                "§7id §f" + npc.getId(),
                npc.isQuestNpc() ? "§aQuest NPC" : "§bDialog-only",
                "§cThis cannot be undone."
        ));
        inventory.setItem(18, EditorItems.section("Confirm", "§7Asks once. Same as before."));
        inventory.setItem(YES, EditorItems.button(
                Material.LIME_CONCRETE,
                "§a§lDelete",
                "§7Removes the FancyNPC + editor save."
        ));
        inventory.setItem(NO, EditorItems.button(
                Material.RED_CONCRETE,
                "§c§lKeep",
                "§7Back to edit."
        ));
        inventory.setItem(49, EditorItems.button(Material.ARROW, "§7Back"));
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
        CustomNpc npc = editor.storage().get(holder.npcId());
        int slot = event.getRawSlot();
        if (slot == NO || slot == 49) {
            if (npc != null) {
                EditMenu.open(player, npc);
            } else {
                MainMenu.open(player);
            }
            return;
        }
        if (slot == YES) {
            editor.delete(player, npc);
            player.closeInventory();
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
