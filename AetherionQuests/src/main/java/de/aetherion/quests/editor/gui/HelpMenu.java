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

    private static final int BACK = 31;

    public HelpMenu(NpcEditor editor) {
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(),
                36,
                EditorItems.title(player, "npc_help", "§8NPC Help")
        );
        EditorItems.fill(inventory);
        inventory.setItem(10, EditorItems.button(
                Material.COMMAND_BLOCK,
                "§eCommands",
                "§f/aethernpc §7— this menu",
                "§f/aethernpc create [name]",
                "§f/aethernpc edit §7| §fnearby §7| §flist",
                "§f/aethernpc move §7| §fduplicate §7| §fdelete",
                "§f/aethernpc wand §7| §fhelp",
                "§8Alias: /npceditor",
                "§8FancyNpcs keeps §f/npc"
        ));
        inventory.setItem(12, EditorItems.button(
                Material.BLAZE_ROD,
                "§6Wand",
                "§7Right-click air — menu.",
                "§7Right-click an editor NPC — edit.",
                "§7Sneak + right-click — delete confirm.",
                "§8Story NPCs are never deleted here."
        ));
        inventory.setItem(14, EditorItems.button(
                Material.CHEST,
                "§bStorage",
                "§7plugins/AetherionQuests/editor-npcs.yml",
                "§7Survives restart. Jar never overwrites it.",
                "§7Story cast stays in npcs.yml."
        ));
        inventory.setItem(16, EditorItems.button(
                Material.NAME_TAG,
                "§aPermission",
                "§faetherion.npc.editor",
                "§7LuckPerms §fmonkey §7(Homie ultra, weight 95)",
                "§7Grant: Dev Menu → Ranks → Monkey",
                "§8Fallback: /lp user <name> parent set monkey",
                "§7Beta Tester is rainbow cosmetics only.",
                "§7Not the same as §faetherionquests.admin",
                "§8Monkey Content Kit cannot open Ranks.")
        ));
        inventory.setItem(22, EditorItems.button(
                Material.WRITABLE_BOOK,
                "§dDialogue pages",
                "§7A page is one conversation screen:",
                "§7NPC text, then the player's choices.",
                "§7Each choice can open another page,",
                "§7run a command, or offer / start /",
                "§7turn in a linked quest.",
                "§7Start page is usually §fgreeting§7.",
                "§7Add page → type an id → wire choices."
        ));
        inventory.setItem(BACK, EditorItems.button(Material.ARROW, "§7Back"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == BACK) {
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
