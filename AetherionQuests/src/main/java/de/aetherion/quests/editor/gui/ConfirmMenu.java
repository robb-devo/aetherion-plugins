package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.NpcCheck;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.model.QuestState;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * "Are you sure?" for the two big deletes (NPC, page). Keep is on the left and green; Delete is red.
 */
public final class ConfirmMenu {

    private static final int INFO = 4;
    private static final int KEEP = 11;
    private static final int DELETE = 15;

    private ConfirmMenu() {
    }

    public static void open(Player player, String title, ItemStack info, String deleteLabel,
                            Runnable onConfirm, Runnable onCancel) {
        Menu menu = Menu.of(3, title);
        menu.set(INFO, info);
        menu.set(KEEP, EditorItems.icon(Material.LIME_CONCRETE)
                .name("§a§l◀ Keep it")
                .lore("§7Nothing changes.")
                .build(), click -> onCancel.run());
        menu.set(DELETE, EditorItems.icon(Material.RED_CONCRETE)
                .name("§c§l" + deleteLabel)
                .lore("§7You can still undo it afterwards", "§7with §e↶ Undo §7or §f/npc undo§7.")
                .build(), click -> onConfirm.run());
        Frame.show(menu, player);
    }

    public static void deleteNpc(Player player, String npcId, Runnable onCancel) {
        NpcEditor editor = NpcEditor.get();
        CustomNpc npc = Frame.require(player, npcId);
        if (npc == null) {
            return;
        }
        EditorItems.Builder info = EditorItems.icon(Material.LAVA_BUCKET)
                .name("§cDelete §f" + npc.getName() + "§c?")
                .text("Removes the NPC from the world and from the studio (editor-npcs.yml).")
                .blank()
                .lore(EditorItems.BULLET + "§7" + EditorItems.plural(npc.pages().size(), "page")
                        + ", " + Frame.replies(npc.totalChoices()));
        if (npc.hasLinkedQuest()) {
            info.lore(EditorItems.BULLET + "§7Quest: §f" + editor.quests().title(npc.getLinkedQuestId())
                    + " §8(the quest itself stays)");
        }
        open(player, Frame.title("Delete " + npc.getName() + "?"), info.build(), "Delete NPC", () -> {
            CustomNpc live = editor.npc(npcId);
            if (live != null) {
                editor.delete(player, live);
            }
            HomeMenu.open(player);
        }, onCancel);
    }

    public static void deletePage(Player player, String npcId, String pageId) {
        NpcEditor editor = NpcEditor.get();
        CustomNpc npc = Frame.require(player, npcId);
        CustomNpc.DialoguePage page = npc == null ? null : npc.page(pageId);
        if (page == null) {
            return;
        }
        if (npc.pages().size() <= 1) {
            editor.error(player, "That's the only page — an NPC needs at least one.");
            return;
        }
        List<CustomNpc.Link> links = npc.linksTo(pageId);
        QuestState stage = npc.stageOpening(pageId);
        EditorItems.Builder info = EditorItems.icon(Material.LAVA_BUCKET)
                .name("§cDelete page §f" + page.title() + "§c?")
                .lore(EditorItems.BULLET + "§7" + EditorItems.plural(page.lines().size(), "line")
                        + ", " + Frame.replies(page.choices().size()));
        if (!links.isEmpty()) {
            info.lore(EditorItems.BULLET + "§e" + Frame.replies(links.size()) + " lead here §7— they'll end the chat instead");
        }
        if (npc.isStartPage(pageId)) {
            info.lore(EditorItems.BULLET + "§eIt's the first page §7— another page becomes first");
        }
        if (stage != null) {
            info.lore(EditorItems.BULLET + "§7Quest stage §f" + NpcCheck.stageName(stage) + " §7falls back to the first page");
        }
        String title = page.title();
        open(player, Frame.title("Delete " + title + "?"), info.build(), "Delete page", () -> {
            int[] rewired = {0};
            editor.change(player, editor.npc(npcId), "Delete page " + title, n -> rewired[0] = n.deletePage(pageId));
            if (rewired[0] > 0) {
                player.sendMessage("§7" + Frame.replies(rewired[0]) + " that led to " + title + " now end the conversation.");
            }
            DialogueMenu.open(player, npcId);
        }, () -> PageMenu.open(player, npcId, pageId));
    }
}
