package de.aetherion.quests.editor.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The short studio guide. Every screen also has its own "?" tooltip, so this is rarely needed.
 */
public final class HelpMenu {

    private HelpMenu() {
    }

    public static void open(Player player, Runnable back) {
        Menu menu = Frame.screen(Frame.title("NPC Studio", "Guide"));
        menu.set(4, EditorItems.icon(Material.NETHER_STAR)
                .name("§b§lNPC Studio")
                .text("Make NPCs that talk, hand out quests and run commands — all in-game, no files.")
                .blank()
                .lore("§8Everything saves the moment you change it.")
                .build());

        menu.set(19, EditorItems.icon(Material.LIME_DYE)
                .name("§a① Create")
                .text("Home → + New NPC. Pick a starting point, type a name. It appears where you stand.")
                .build());
        menu.set(21, EditorItems.icon(Material.LEATHER_CHESTPLATE)
                .name("§6② Look")
                .text("Name, subtitle, skin (any player name) and outfit.")
                .build());
        menu.set(23, EditorItems.icon(Material.WRITABLE_BOOK)
                .name("§d③ Dialogue")
                .text("Pages of lines the NPC says. After a page the player picks a reply: continue to another "
                        + "page, end the chat, handle a quest or run a command.")
                .build());
        menu.set(25, EditorItems.icon(Material.MAP)
                .name("§a④ Quest")
                .text("Pick the quest the NPC hands out. ⚡ Set up as quest giver builds the whole chat; "
                        + "stages choose what the NPC says before, during and after the quest.")
                .build());

        menu.set(29, EditorItems.icon(Material.OAK_BUTTON)
                .name("§eControls")
                .lore("§eClick §8→ §7open / edit",
                        "§eShift-click §8→ §7move earlier",
                        "§eShift-right-click §8→ §7move later",
                        "§eRight-click §8→ §7preview (pages, stages)",
                        "§cQ §8→ §7delete (always undo-able)",
                        "",
                        "§7Typing happens in chat — click",
                        "§e[✎ Edit current text] §7to tweak instead",
                        "§7of retyping. §fcancel §7stops typing.")
                .build());
        menu.set(31, EditorItems.icon(Material.BLAZE_ROD)
                .name("§6Wand")
                .lore("§7Right-click §fair §8→ §7open the studio",
                        "§7Right-click §fan NPC §8→ §7edit it",
                        "§7Sneak-right-click §fan NPC §8→ §7talk to it",
                        "§8  for real (quests + commands run)",
                        "",
                        "§7Get one on the studio home screen.")
                .build());
        menu.set(33, EditorItems.icon(Material.SHIELD)
                .name("§aSafe by default")
                .lore("§7• Story NPCs (Egon, Twig, …) never show",
                        "§7  up here and can't be edited.",
                        "§7• ▶ Preview only describes commands",
                        "§7  and quests — nothing runs.",
                        "§7• Dangerous commands are blocked.",
                        "§7• ↶ Undo for every change; a backup",
                        "§7  of the NPC file is made each start.")
                .build());
        menu.set(37, EditorItems.icon(Material.WRITTEN_BOOK)
                .name("§b§lGuides §8(EN + DE)")
                .lore("§7Gives two written books:",
                        "§fEnglish §7+ §fDeutsch",
                        "§7Full step-by-step walkthrough.",
                        "§eClick to receive.")
                .blank()
                .click("Click", "to get both books")
                .build(), click -> {
            GuideBook.giveBoth(click.player());
            click.player().closeInventory();
        });
        menu.set(40, EditorItems.icon(Material.COMMAND_BLOCK)
                .name("§fCommands")
                .lore("§f/aethernpc §8→ §7this studio",
                        "§f/aethernpc create §7[name] §8· §f/aethernpc edit §7[name]",
                        "§f/aethernpc move §8· §f/aethernpc tp §8· §f/aethernpc duplicate",
                        "§f/aethernpc preview §8· §f/aethernpc delete §8· §f/aethernpc undo",
                        "§f/aethernpc wand §8· §f/aethernpc help §8· §f/aethernpc guide",
                        "",
                        "§8Without a name they use the nearest NPC.",
                        "§8Alias: /npceditor · FancyNpcs keeps /npc",
                        "§8Permission: aetherion.npc.editor",
                        "§8Data: plugins/AetherionQuests/editor-npcs.yml")
                .build());

        if (back != null) {
            Frame.back(menu, "where you were", back);
        }
        Frame.show(menu, player);
    }
}
