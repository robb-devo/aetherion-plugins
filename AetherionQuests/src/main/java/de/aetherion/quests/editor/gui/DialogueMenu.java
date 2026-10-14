package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.NpcCheck;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.editor.TextInput;
import de.aetherion.quests.model.QuestState;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * NPC workspace — Dialogue tab: the conversation map. Every page as a card showing what the NPC says,
 * how the player can reply and where each reply leads.
 */
public final class DialogueMenu {

    private static final int FIRST = 18;
    private static final int PER_SCREEN = 27;

    private DialogueMenu() {
    }

    public static void open(Player player, String npcId) {
        open(player, npcId, 0);
    }

    public static void open(Player player, String npcId, int requested) {
        CustomNpc npc = Frame.require(player, npcId);
        if (npc == null) {
            return;
        }
        NpcEditor editor = Frame.editor();
        List<CustomNpc.DialoguePage> pages = npc.orderedPages();
        boolean canAdd = pages.size() < CustomNpc.MAX_PAGES;
        int tiles = pages.size() + (canAdd ? 1 : 0);
        int screens = Math.max(1, (tiles + PER_SCREEN - 1) / PER_SCREEN);
        int screen = Math.max(0, Math.min(requested, screens - 1));
        Runnable reopen = () -> open(player, npcId, screen);
        Menu menu = Frame.workspace(player, npc, Frame.Tab.DIALOGUE, reopen);

        List<NpcCheck.Issue> issues = NpcCheck.run(npc, editor.quests());
        Set<String> reachable = NpcCheck.reachable(npc);
        int start = screen * PER_SCREEN;
        for (int i = start; i < tiles && i < start + PER_SCREEN; i++) {
            int slot = FIRST + (i - start);
            if (i >= pages.size()) {
                menu.set(slot, EditorItems.icon(Material.LIME_DYE)
                        .name("§a+ New page")
                        .text("Another part of the conversation. Replies with \"Continue talking\" lead to it.")
                        .blank()
                        .click("Click", "and type a short title")
                        .build(), click -> newPage(click.player(), npcId, null, p -> open(p, npcId)));
                continue;
            }
            CustomNpc.DialoguePage page = pages.get(i);
            String pageId = page.id();
            menu.set(slot, card(player, npc, page, NpcCheck.forPage(issues, pageId), reachable.contains(pageId)), click -> {
                if (click.drop()) {
                    ConfirmMenu.deletePage(click.player(), npcId, pageId);
                } else if (click.right()) {
                    editor.preview(click.player(), editor.npc(npcId), pageId, null, reopen);
                } else {
                    PageMenu.open(click.player(), npcId, pageId);
                }
            });
        }

        menu.set(Frame.LEFT, EditorItems.icon(Material.ENDER_EYE)
                .name("§a▶ Preview from the start")
                .text("Plays the conversation to you. Commands and quests are only described.")
                .build(), click -> editor.preview(click.player(), editor.npc(npcId), null, null, reopen));
        Frame.pages(menu, screen, screens, next -> open(player, npcId, next));
        Frame.help(menu, reopen,
                "§7A conversation is a set of §fpages§7.",
                "§7On each page the NPC says its lines,",
                "§7then the player picks a §freply§7.",
                "§7Replies can continue to another page,",
                "§7end the chat, handle a quest or run",
                "§7a command.",
                "",
                "§a★ §7marks the page every chat starts on.",
                "§eRight-click §7a page to preview from it.");
        Frame.show(menu, player);
    }

    /**
     * Asks for a title, creates the page and opens it — or hands the new id to {@code linkFrom} (e.g. a reply).
     */
    static void newPage(Player player, String npcId, BiConsumer<Player, String> linkFrom, Consumer<Player> onCancel) {
        NpcEditor editor = Frame.editor();
        String[] created = new String[1];
        editor.ask(player, TextInput.builder("Title for the new page")
                .hint("A short name only you see, e.g. §fShop§7, §fAbout the mine§7, §fGoodbye§7.")
                .max(24)
                .handler((p, text) -> {
                    CustomNpc npc = editor.npc(npcId);
                    if (npc == null) {
                        return "That NPC doesn't exist anymore.";
                    }
                    if (npc.pages().size() >= CustomNpc.MAX_PAGES) {
                        return "This NPC already has " + CustomNpc.MAX_PAGES + " pages.";
                    }
                    editor.change(p, npc, "New page " + text, n -> created[0] = n.createPage(text).id());
                    return null;
                })
                .then(p -> {
                    if (linkFrom != null) {
                        linkFrom.accept(p, created[0]);
                    } else {
                        PageMenu.open(p, npcId, created[0]);
                    }
                })
                .onCancel(onCancel)
                .build());
    }

    private static ItemStack card(Player player, CustomNpc npc, CustomNpc.DialoguePage page,
                                  List<NpcCheck.Issue> issues, boolean reachable) {
        boolean first = npc.isStartPage(page.id());
        QuestState stage = npc.stageOpening(page.id());
        EditorItems.Builder icon = EditorItems.icon(first ? Material.WRITTEN_BOOK : stage != null ? Material.KNOWLEDGE_BOOK : Material.BOOK)
                .name((first ? "§a§l★ " : "§e§l") + page.title())
                .glow(first);
        if (first) {
            icon.lore("§8Every conversation starts here");
        } else if (stage != null && npc.hasLinkedQuest()) {
            icon.lore("§8Opens when the quest is " + NpcCheck.stageName(stage).toLowerCase());
        }
        icon.blank().lore("§7NPC says:");
        List<String> lines = page.spokenLines();
        if (lines.isEmpty()) {
            icon.lore("§8  (nothing)");
        }
        for (int i = 0; i < lines.size() && i < 3; i++) {
            icon.lore("§f  " + EditorItems.quote(lines.get(i).replace("{player}", player.getName()), 32));
        }
        if (lines.size() > 3) {
            icon.lore("§8  +" + (lines.size() - 3) + " more");
        }
        icon.blank().lore("§7Player replies:");
        if (page.choices().isEmpty()) {
            icon.lore(npc.hasLinkedQuest() ? "§8  (none — offers the main quest)" : "§8  (none — the chat ends)");
        }
        for (int i = 0; i < page.choices().size() && i < 4; i++) {
            CustomNpc.DialogueChoice choice = page.choices().get(i);
            icon.lore("§e  ▸ " + EditorItems.truncate(choice.text(), 18) + " §8→ " + Frame.describe(npc, choice));
        }
        if (page.choices().size() > 4) {
            icon.lore("§8  +" + (page.choices().size() - 4) + " more");
        }
        List<CustomNpc.Link> links = npc.linksTo(page.id());
        if (!first && stage == null) {
            icon.blank();
            if (!reachable || links.isEmpty()) {
                icon.lore("§e⚠ Nothing leads here yet");
            } else {
                CustomNpc.Link link = links.get(0);
                icon.lore("§7Reached from §f" + EditorItems.quote(link.choice().text(), 18)
                        + " §8on " + CustomNpc.pageTitle(link.pageId())
                        + (links.size() > 1 ? " §8+" + (links.size() - 1) : ""));
            }
        }
        long problems = NpcCheck.problems(issues);
        if (problems > 0) {
            icon.lore("§c✖ " + EditorItems.plural((int) problems, "problem") + " on this page");
        }
        return icon.blank()
                .click("Click", "to edit")
                .click("Right-click", "to preview from here")
                .danger("Press Q", "to delete…")
                .build();
    }
}
