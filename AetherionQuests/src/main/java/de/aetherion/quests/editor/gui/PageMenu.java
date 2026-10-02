package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.DialogueAction;
import de.aetherion.quests.editor.NpcCheck;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.editor.TextInput;
import de.aetherion.quests.model.QuestState;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * One page of a conversation: what the NPC says (top) and the replies the player can pick (bottom).
 */
public final class PageMenu {

    private static final int PREVIEW = 2;
    private static final int CARD = 4;
    private static final int FIRST_PAGE = 6;
    private static final int SAYS_LABEL = 9;
    private static final int REPLIES_LABEL = 36;
    private static final int FIRST_REPLY = 37;

    private PageMenu() {
    }

    /** Line {@code index} → slot: lines 1-7 on row 1, 8-14 on row 2 (columns 1-7). */
    private static int lineSlot(int index) {
        return index < 7 ? 10 + index : 19 + (index - 7);
    }

    public static void open(Player player, String npcId, String pageId) {
        CustomNpc npc = Frame.require(player, npcId);
        if (npc == null) {
            return;
        }
        CustomNpc.DialoguePage page = npc.page(pageId);
        if (page == null) {
            DialogueMenu.open(player, npcId);
            return;
        }
        NpcEditor editor = Frame.editor();
        String id = page.id();
        Runnable reopen = () -> open(player, npcId, id);
        Menu menu = Frame.screen(Frame.title(npc.getName(), "Dialogue", page.title()));
        boolean first = npc.isStartPage(id);
        QuestState stage = npc.stageOpening(id);

        menu.set(PREVIEW, EditorItems.icon(Material.ENDER_EYE)
                .name("§a▶ Preview this page")
                .text("Plays this page to you like a player sees it. Nothing runs for real.")
                .build(), click -> editor.preview(click.player(), editor.npc(npcId), id, null, reopen));

        EditorItems.Builder card = EditorItems.icon(first ? Material.WRITTEN_BOOK : Material.BOOK)
                .name((first ? "§a§l★ " : "§e§l") + page.title())
                .glow(first);
        if (first) {
            card.text("Every conversation starts on this page.");
        } else if (stage != null && npc.hasLinkedQuest()) {
            card.text("Opens first when the player's quest is " + NpcCheck.stageName(stage).toLowerCase() + ".");
        } else {
            List<CustomNpc.Link> links = npc.linksTo(id);
            if (links.isEmpty()) {
                card.lore("§e⚠ Nothing leads here yet.", "§7Give a reply §f\"Continue talking\"§7 → this page.");
            } else {
                card.lore("§7Reached from:");
                for (int i = 0; i < links.size() && i < 4; i++) {
                    CustomNpc.Link link = links.get(i);
                    card.lore("§8  • §f" + EditorItems.quote(link.choice().text(), 20) + " §8on " + CustomNpc.pageTitle(link.pageId()));
                }
            }
        }
        menu.set(CARD, card.blank().lore("§8Page id: " + id).blank().click("Click", "to rename").build(),
                click -> renamePage(click.player(), npcId, id));

        menu.set(FIRST_PAGE, first
                ? EditorItems.icon(Material.NETHER_STAR).name("§a★ First page").glow(true)
                .text("Conversations start here" + (npc.hasLinkedQuest() ? " (unless a quest stage has its own page)." : "."))
                .build()
                : EditorItems.icon(Material.NETHER_STAR).name("§e☆ Make this the first page")
                .text("Every conversation will start here instead of on " + CustomNpc.pageTitle(npc.getStartPage()) + ".")
                .blank().click("Click", "to make it first").build(), first ? null : click -> {
            editor.change(click.player(), editor.npc(npcId), "First page: " + page.title(), n -> n.setStartPage(id));
            open(click.player(), npcId, id);
        });

        // ---- NPC says
        menu.set(SAYS_LABEL, Frame.label(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b§lNPC says ▸",
                "§7Sent in chat one by one,", "§7about 2 seconds apart.", "", "§8{player} = the player's name"));
        List<String> lines = page.lines();
        for (int i = 0; i < lines.size() && i < CustomNpc.MAX_LINES; i++) {
            int index = i;
            String line = lines.get(i);
            EditorItems.Builder item = EditorItems.icon(Material.PAPER)
                    .amount(i + 1)
                    .name("§f" + EditorItems.quote(line, 30))
                    .lore("§8Line " + (i + 1) + " · in chat:");
            List<String> wrapped = EditorItems.wrap(line.replace("{player}", player.getName()), 32);
            for (int w = 0; w < wrapped.size(); w++) {
                item.lore(w == 0 ? "§b" + npc.getName() + " §8⟫ §f" + wrapped.get(w) : "§f   " + wrapped.get(w));
            }
            if ("…".equals(line.trim())) {
                item.lore("§e⚠ Placeholder — write the real line");
            }
            item.blank()
                    .click("Click", "to edit")
                    .click("Shift-click", "◀ move earlier")
                    .click("Shift-right-click", "move later ▶")
                    .danger("Press Q", "to delete");
            menu.set(lineSlot(i), item.build(), click -> {
                if (click.drop()) {
                    editor.change(click.player(), editor.npc(npcId), "Delete line " + (index + 1),
                            n -> removeAt(n.page(id).lines(), index));
                    open(click.player(), npcId, id);
                } else if (click.shift()) {
                    int delta = click.shiftLeft() ? -1 : 1;
                    editor.change(click.player(), editor.npc(npcId), "Move line " + (index + 1),
                            n -> CustomNpc.DialoguePage.move(n.page(id).lines(), index, delta));
                    open(click.player(), npcId, id);
                } else {
                    editLine(click.player(), npcId, id, index);
                }
            });
        }
        if (lines.size() < CustomNpc.MAX_LINES) {
            menu.set(lineSlot(lines.size()), EditorItems.icon(Material.LIME_DYE)
                    .name(lines.isEmpty() ? "§a+ Write what the NPC says" : "§a+ Add lines")
                    .text("Type in chat — each message becomes one line. Type done when you're finished.")
                    .blank()
                    .lore("§8Tip: {player} = the player's name")
                    .blank()
                    .click("Click", "to start typing")
                    .build(), click -> addLines(click.player(), npcId, id));
        }

        // ---- Player replies
        menu.set(REPLIES_LABEL, Frame.label(Material.YELLOW_STAINED_GLASS_PANE, "§e§lPlayer replies ▸",
                "§7Buttons shown after the NPC", "§7finishes talking.",
                "", page.choices().isEmpty()
                        ? (npc.hasLinkedQuest() ? "§8None: the main quest is offered." : "§8None: the chat just ends.")
                        : "§8Up to " + CustomNpc.MAX_CHOICES + " replies."));
        List<NpcCheck.Issue> issues = NpcCheck.run(npc, editor.quests());
        for (int i = 0; i < page.choices().size() && i < CustomNpc.MAX_CHOICES; i++) {
            int index = i;
            CustomNpc.DialogueChoice choice = page.choices().get(i);
            EditorItems.Builder item = EditorItems.icon(choice.action().icon())
                    .amount(i + 1)
                    .name("§e▸ " + EditorItems.quote(choice.text(), 26))
                    .lore("§7Then it " + Frame.describe(npc, choice));
            for (NpcCheck.Issue issue : issues) {
                if (issue.where() == NpcCheck.Where.REPLY && id.equals(issue.pageId()) && issue.index() == i) {
                    item.lore(EditorItems.paragraph("§c", "✖ " + issue.text(), 34));
                }
            }
            item.blank()
                    .click("Click", "to edit")
                    .click("Shift-click", "◀ move earlier")
                    .click("Shift-right-click", "move later ▶")
                    .danger("Press Q", "to delete");
            menu.set(FIRST_REPLY + i, item.build(), click -> {
                if (click.drop()) {
                    editor.change(click.player(), editor.npc(npcId), "Delete reply " + EditorItems.quote(choice.text(), 20),
                            n -> removeAt(n.page(id).choices(), index));
                    open(click.player(), npcId, id);
                } else if (click.shift()) {
                    int delta = click.shiftLeft() ? -1 : 1;
                    editor.change(click.player(), editor.npc(npcId), "Move reply " + (index + 1),
                            n -> CustomNpc.DialoguePage.move(n.page(id).choices(), index, delta));
                    open(click.player(), npcId, id);
                } else {
                    ReplyMenu.open(click.player(), npcId, id, index);
                }
            });
        }
        if (page.choices().size() < CustomNpc.MAX_CHOICES) {
            menu.set(FIRST_REPLY + page.choices().size(), EditorItems.icon(Material.LIME_DYE)
                    .name("§a+ Add reply")
                    .text("A button the player can click. You'll type what it says, then pick what it does.")
                    .blank()
                    .click("Click", "to add one")
                    .build(), click -> addReply(click.player(), npcId, id));
        }

        Frame.back(menu, "Dialogue", () -> DialogueMenu.open(player, npcId));
        Frame.undo(menu, player, npcId, reopen);
        if (npc.pages().size() > 1) {
            menu.set(Frame.RIGHT, EditorItems.icon(Material.LAVA_BUCKET)
                    .name("§cDelete this page")
                    .text("Replies that lead here will end the conversation instead. You'll be asked first.")
                    .build(), click -> ConfirmMenu.deletePage(click.player(), npcId, id));
        }
        Frame.help(menu, reopen,
                "§7Top: the lines the NPC says, in order.",
                "§7Bottom: the replies the player can pick.",
                "",
                "§eClick §7a line or reply to edit it.",
                "§eShift-click §7/ §eShift-right-click §7to reorder.",
                "§cQ §7deletes — and §e↶ Undo §7brings it back.");
        Frame.show(menu, player);
    }

    private static void renamePage(Player player, String npcId, String pageId) {
        NpcEditor editor = Frame.editor();
        String[] current = {pageId};
        editor.ask(player, TextInput.builder("New title for page " + CustomNpc.pageTitle(pageId))
                .hint("Only you see it. Replies that lead here are updated automatically.")
                .current(CustomNpc.pageTitle(pageId))
                .max(24)
                .handler((p, text) -> {
                    CustomNpc live = editor.npc(npcId);
                    if (live == null || live.page(pageId) == null) {
                        return "That page doesn't exist anymore.";
                    }
                    editor.change(p, live, "Rename page to " + text, n -> {
                        String renamed = n.renamePage(pageId, text);
                        if (renamed != null) {
                            current[0] = renamed;
                        }
                    });
                    return null;
                })
                .then(p -> open(p, npcId, current[0]))
                .build());
    }

    private static <T> void removeAt(List<T> list, int index) {
        if (index >= 0 && index < list.size()) {
            list.remove(index);
        }
    }

    private static void editLine(Player player, String npcId, String pageId, int index) {
        NpcEditor editor = Frame.editor();
        CustomNpc npc = editor.npc(npcId);
        CustomNpc.DialoguePage page = npc == null ? null : npc.page(pageId);
        if (page == null || index >= page.lines().size()) {
            open(player, npcId, pageId);
            return;
        }
        editor.ask(player, TextInput.builder("Line " + (index + 1) + " of " + page.title())
                .hint("Click §eEdit current text §7to fix a typo instead of retyping.")
                .hint("{player} = the player's name.")
                .current(page.lines().get(index))
                .max(200)
                .handler((p, text) -> {
                    CustomNpc live = editor.npc(npcId);
                    CustomNpc.DialoguePage livePage = live == null ? null : live.page(pageId);
                    if (livePage == null || index >= livePage.lines().size()) {
                        return "That line doesn't exist anymore.";
                    }
                    editor.change(p, live, "Edit line " + (index + 1), n -> n.page(pageId).lines().set(index, text));
                    return null;
                })
                .then(p -> open(p, npcId, pageId))
                .build());
    }

    private static void addLines(Player player, String npcId, String pageId) {
        NpcEditor editor = Frame.editor();
        CustomNpc npc = editor.npc(npcId);
        CustomNpc.DialoguePage page = npc == null ? null : npc.page(pageId);
        if (page == null) {
            return;
        }
        editor.ask(player, TextInput.builder("Adding lines to " + page.title())
                .hint("Type what " + npc.getName() + " says — each message becomes one line.")
                .hint("Type §fdone §7when you're finished. {player} = the player's name.")
                .max(200)
                .multiLine()
                .handler((p, text) -> {
                    CustomNpc live = editor.npc(npcId);
                    CustomNpc.DialoguePage livePage = live == null ? null : live.page(pageId);
                    if (livePage == null) {
                        return "That page doesn't exist anymore.";
                    }
                    if (livePage.lines().size() >= CustomNpc.MAX_LINES) {
                        return "This page is full (" + CustomNpc.MAX_LINES + " lines) — type done, then add a new page.";
                    }
                    editor.change(p, live, "Add line " + EditorItems.quote(text, 20), n -> n.page(pageId).lines().add(text));
                    return null;
                })
                .then(p -> open(p, npcId, pageId))
                .build());
    }

    private static void addReply(Player player, String npcId, String pageId) {
        NpcEditor editor = Frame.editor();
        int[] created = {-1};
        editor.ask(player, TextInput.builder("What does the player say?")
                .hint("The text on the reply button, e.g. §fTell me more§7 or §fGoodbye§7.")
                .hint("Next you'll choose what the reply does.")
                .max(48)
                .handler((p, text) -> {
                    CustomNpc live = editor.npc(npcId);
                    CustomNpc.DialoguePage livePage = live == null ? null : live.page(pageId);
                    if (livePage == null) {
                        return "That page doesn't exist anymore.";
                    }
                    if (livePage.choices().size() >= CustomNpc.MAX_CHOICES) {
                        return "A page can have at most " + CustomNpc.MAX_CHOICES + " replies.";
                    }
                    editor.change(p, live, "Add reply " + EditorItems.quote(text, 20), n -> {
                        List<CustomNpc.DialogueChoice> choices = n.page(pageId).choices();
                        choices.add(new CustomNpc.DialogueChoice(text, DialogueAction.CLOSE, ""));
                        created[0] = choices.size() - 1;
                    });
                    return null;
                })
                .then(p -> {
                    if (created[0] >= 0) {
                        ReplyMenu.open(p, npcId, pageId, created[0]);
                    } else {
                        open(p, npcId, pageId);
                    }
                })
                .onCancel(p -> open(p, npcId, pageId))
                .build());
    }
}
