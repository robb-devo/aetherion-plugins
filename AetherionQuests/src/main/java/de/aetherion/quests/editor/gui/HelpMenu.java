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

    private static final int BACK = 22;

    public HelpMenu(NpcEditor editor) {
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(),
                27,
                EditorItems.title(player, "npc_help", "§8NPC Help")
        );
        EditorItems.fill(inventory);
        inventory.setItem(10, EditorItems.button(
                Material.COMMAND_BLOCK,
                "§eCommands",
                "§f/npc §7— this menu",
                "§f/npc create [name]",
                "§f/npc edit §7| §fnearby §7| §flist",
                "§f/npc move §7| §fduplicate §7| §fdelete",
                "§f/npc wand §7| §fhelp",
                "§8Aliases: /aethernpc /npceditor"
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
                "§7LuckPerms group §fmoderator",
                "§7LuckPerms group §fMonkey §8+ §faetherion.dev.menu",
                "§7Not the same as §faetherionquests.admin"
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
