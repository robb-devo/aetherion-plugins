package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.editor.NpcTemplates;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * "New NPC" step 1: pick a starting point. Step 2 is typing the name; the NPC then appears at your feet.
 */
public final class TemplateMenu {

    private static final int[] SLOTS = {20, 22, 24, 30, 32};

    private TemplateMenu() {
    }

    public static void open(Player player) {
        NpcEditor editor = NpcEditor.get();
        if (editor == null) {
            return;
        }
        Menu menu = Frame.screen(Frame.title("NPC Studio", "New NPC"));
        menu.set(4, EditorItems.icon(Material.LIME_DYE)
                .name("§a§lNew NPC")
                .text("Pick a starting point. Next you'll type a name, and the NPC appears where you're standing.")
                .blank()
                .lore("§8You can change everything later.")
                .build());
        NpcTemplates.Template[] templates = NpcTemplates.Template.values();
        for (int i = 0; i < templates.length && i < SLOTS.length; i++) {
            NpcTemplates.Template template = templates[i];
            CustomNpc sample = new CustomNpc("sample", "NPC");
            NpcTemplates.apply(sample, template);
            EditorItems.Builder icon = EditorItems.icon(template.icon())
                    .name("§f§l" + template.title())
                    .lore(template.description().stream().map(line -> "§7" + line).toList())
                    .blank()
                    .lore("§8Starts with " + EditorItems.plural(sample.pages().size(), "page")
                            + ", " + Frame.replies(sample.totalChoices()) + ":");
            String first = sample.firstLine();
            if (first != null) {
                icon.lore("§8  " + EditorItems.quote(first.replace("{player}", "Steve"), 30));
            }
            icon.blank().click("Click", "to pick it");
            menu.set(SLOTS[i], icon.build(), click -> editor.beginCreate(click.player(), template));
        }
        Frame.back(menu, "all NPCs", () -> HomeMenu.open(player));
        Frame.help(menu, () -> open(player),
                "§7Templates are just ready-made pages",
                "§7and replies — edit or delete any of it.",
                "",
                "§fQuest giver §7is the fastest way to a",
                "§7working quest NPC: pick a quest and",
                "§7you're done.");
        Frame.show(menu, player);
    }
}
