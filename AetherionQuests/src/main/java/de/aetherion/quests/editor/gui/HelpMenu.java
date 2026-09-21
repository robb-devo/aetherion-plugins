package de.aetherion.quests.editor.gui;

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

public final class HelpMenu implements Listener {

    public HelpMenu(NpcEditor editor) {
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(),
                54,
                EditorItems.title(player, "npc_help", "§8Tips")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.KNOWLEDGE_BOOK,
                EditorItems.ui(player, "editor_tips", "§fTips"),
                EditorItems.ui(player, "editor_tips_head", "§7You probably don't need this.")
        ));
        inventory.setItem(19, EditorItems.button(
                Material.EMERALD_BLOCK,
                EditorItems.ui(player, "editor_help_simple", "§aSimple path"),
                "§e1. §7Create NPC",
                "§e2. §7Write what they say",
                "§e3. §7Walk away — they're live."
        ));
        inventory.setItem(21, EditorItems.button(
                Material.MAP,
                EditorItems.ui(player, "editor_help_quest", "§aGive them a job"),
                EditorItems.ui(player, "editor_help_quest_l1", "§7Switch to Gives a quest."),
                EditorItems.ui(player, "editor_help_quest_l2", "§7Create a job or pick an existing one."),
                EditorItems.ui(player, "editor_help_quest_l3", "§7Players get it after talking.")
        ));
        inventory.setItem(23, EditorItems.button(
                Material.BLAZE_ROD,
                EditorItems.ui(player, "editor_wand", "§6Wand"),
                EditorItems.ui(player, "editor_wand_l1", "§7Right-click air — this menu."),
                EditorItems.ui(player, "editor_wand_l2", "§7Right-click an editor NPC — edit."),
                EditorItems.ui(player, "editor_story_safe",
                        "§7Story NPCs (Egon, Twig, Miss Canopy) stay untouched.")
        ));
        inventory.setItem(25, EditorItems.button(
                Material.OAK_SIGN,
                EditorItems.ui(player, "editor_help_chat", "§eChat boxes"),
                EditorItems.ui(player, "editor_help_chat_l1", "§7Always type §fcancel §7to go back."),
                EditorItems.ui(player, "editor_help_chat_l2", "§7Or wait 60 seconds.")
        ));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        Player player = EditorItems.editorClick(event);
        if (player == null) {
            return;
        }
        if (event.getRawSlot() == EditorItems.BACK) {
            MainMenu.open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public record Holder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
