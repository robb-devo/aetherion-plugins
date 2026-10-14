package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.DialogueAction;
import de.aetherion.quests.editor.DialogueRuntime;
import de.aetherion.quests.editor.NpcCheck;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.editor.QuestCatalog;
import de.aetherion.quests.editor.TextInput;
import de.aetherion.quests.model.Quest;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * One reply: the text on the button and — picked from a labelled grid instead of cycling enums — what it does.
 * Picking an action that needs a target (page / quest / command) asks for it right away.
 */
public final class ReplyMenu {

    private static final int CARD = 4;
    private static final Map<DialogueAction, Integer> OPTION_SLOTS = Map.of(
            DialogueAction.PAGE, 18,
            DialogueAction.CLOSE, 19,
            DialogueAction.OFFER_QUEST, 21,
            DialogueAction.START_QUEST, 22,
            DialogueAction.TURN_IN_QUEST, 23,
            DialogueAction.RUN_PLAYER, 25,
            DialogueAction.RUN_CONSOLE, 26
    );
    private static final int DETAIL_MAIN = 39;
    private static final int DETAIL_SIDE = 41;
    private static final int DETAIL_SOLO = 40;

    private ReplyMenu() {
    }

    public static void open(Player player, String npcId, String pageId, int index) {
        CustomNpc npc = Frame.require(player, npcId);
        if (npc == null) {
            return;
        }
        CustomNpc.DialoguePage page = npc.page(pageId);
        if (page == null) {
            DialogueMenu.open(player, npcId);
            return;
        }
        if (index < 0 || index >= page.choices().size()) {
            PageMenu.open(player, npcId, pageId);
            return;
        }
        NpcEditor editor = Frame.editor();
        CustomNpc.DialogueChoice choice = page.choices().get(index);
        String id = page.id();
        Runnable reopen = () -> open(player, npcId, id, index);
        Menu menu = Frame.screen(Frame.title(npc.getName(), page.title(), "Reply " + (index + 1)));

        menu.set(CARD, EditorItems.icon(Material.OAK_SIGN)
                .name("§e▸ " + EditorItems.quote(choice.text(), 30))
                .text("The button the player sees on page " + page.title() + ".")
                .blank()
                .lore("§7Then it " + Frame.describe(npc, choice))
                .blank()
                .click("Click", "to change the text")
                .build(), click -> editor.ask(click.player(), TextInput.builder("Reply text")
                .hint("What the button says, e.g. §fTell me more§7.")
                .current(choice.text())
                .max(48)
                .handler((p, text) -> {
                    CustomNpc.DialogueChoice live = live(editor, npcId, id, index);
                    if (live == null) {
                        return "That reply doesn't exist anymore.";
                    }
                    editor.change(p, editor.npc(npcId), "Reply text " + EditorItems.quote(text, 20),
                            n -> n.page(id).choices().get(index).setText(text));
                    return null;
                })
                .then(p -> open(p, npcId, id, index))
                .build()));

        menu.set(9, Frame.label(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b§lConversation", "§7Keep talking or stop."));
        menu.set(10, Frame.label(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b§lConversation", "§7Keep talking or stop."));
        for (int slot : new int[]{12, 13, 14}) {
            menu.set(slot, Frame.label(Material.LIME_STAINED_GLASS_PANE, "§a§lQuest", "§7Give, start or take a quest."));
        }
        menu.set(16, Frame.label(Material.ORANGE_STAINED_GLASS_PANE, "§6§lCommand", "§7Run a server command."));
        menu.set(17, Frame.label(Material.ORANGE_STAINED_GLASS_PANE, "§6§lCommand", "§7Run a server command."));

        for (Map.Entry<DialogueAction, Integer> entry : OPTION_SLOTS.entrySet()) {
            DialogueAction action = entry.getKey();
            boolean selected = choice.action() == action;
            EditorItems.Builder option = EditorItems.icon(action.icon())
                    .name((selected ? "§a✔ " : "§f") + action.title())
                    .lore(action.description().stream().map(line -> "§7" + line).toList())
                    .glow(selected)
                    .blank();
            if (selected) {
                option.lore("§a▸ Selected");
            } else {
                option.click("Click", "to choose");
            }
            menu.set(entry.getValue(), option.build(), selected ? null : click -> choose(click.player(), npcId, id, index, action));
        }

        details(menu, player, npc, choice, npcId, id, index);

        Frame.back(menu, "page " + page.title(), () -> PageMenu.open(player, npcId, id));
        Frame.undo(menu, player, npcId, reopen);
        menu.set(Frame.RIGHT, EditorItems.icon(Material.LAVA_BUCKET)
                .name("§cDelete this reply")
                .text("Removes the button. ↶ Undo brings it back.")
                .build(), click -> {
            editor.change(click.player(), editor.npc(npcId), "Delete reply " + EditorItems.quote(choice.text(), 20), n -> {
                if (index < n.page(id).choices().size()) {
                    n.page(id).choices().remove(index);
                }
            });
            PageMenu.open(click.player(), npcId, id);
        });
        Frame.help(menu, reopen,
                "§7Top: the text on the button.",
                "§7Middle: what happens when it's clicked —",
                "§bConversation§7, §aQuest §7or §6Command§7.",
                "§7Bottom: the one setting that action needs.",
                "",
                "§8Quest actions without their own quest",
                "§8use the NPC's main quest (Quest tab).");
        Frame.show(menu, player);
    }

    private static void details(Menu menu, Player player, CustomNpc npc, CustomNpc.DialogueChoice choice,
                                String npcId, String pageId, int index) {
        NpcEditor editor = Frame.editor();
        String problem = NpcCheck.replyProblem(npc, choice, editor.quests());
        switch (choice.action()) {
            case CLOSE -> menu.set(DETAIL_SOLO, EditorItems.icon(Material.LIGHT_GRAY_DYE)
                    .name("§7Nothing to set up")
                    .text("The chat closes when the player clicks this reply.")
                    .build());
            case PAGE -> {
                String target = choice.target();
                boolean exists = !target.isBlank() && npc.page(target) != null;
                EditorItems.Builder icon = EditorItems.icon(exists ? Material.BOOK : Material.WRITABLE_BOOK)
                        .name(exists ? "§bGoes to: §f" + CustomNpc.pageTitle(target) : "§cWhich page? §7(none picked)");
                if (exists) {
                    CustomNpc.DialoguePage next = npc.page(target);
                    String first = next.spokenLines().isEmpty() ? null : next.spokenLines().get(0);
                    icon.lore(first == null ? "§8(that page has no lines yet)" : "§7Starts with §f" + EditorItems.quote(first, 28));
                }
                icon.blank().click("Click", exists ? "to pick another page" : "to pick a page");
                menu.set(DETAIL_MAIN, icon.build(), click -> pickPage(click.player(), npcId, pageId, index));
                if (exists) {
                    menu.set(DETAIL_SIDE, EditorItems.icon(Material.SPECTRAL_ARROW)
                            .name("§fOpen " + CustomNpc.pageTitle(target) + " ▸")
                            .text("Jump to that page to edit what the NPC says there.")
                            .build(), click -> PageMenu.open(click.player(), npcId, target));
                }
            }
            case OFFER_QUEST, START_QUEST, TURN_IN_QUEST -> {
                QuestCatalog quests = editor.quests();
                String questId = npc.questFor(choice);
                Quest quest = quests.get(questId);
                EditorItems.Builder icon;
                if (questId == null) {
                    icon = EditorItems.icon(Material.MAP).name("§cWhich quest? §7(none picked)")
                            .text("Pick one here, or set the NPC's main quest in the Quest tab.");
                } else if (quest == null) {
                    icon = EditorItems.icon(Material.MAP).name("§cUnknown quest '" + questId + "'")
                            .text("It doesn't exist (anymore). Pick another one.");
                } else {
                    icon = EditorItems.icon(Material.FILLED_MAP)
                            .name("§aQuest: §f" + QuestCatalog.title(quest))
                            .lore(choice.target().isBlank() ? "§8The NPC's main quest" : "§8Just for this reply")
                            .blank()
                            .lore(quests.describe(quest, 34));
                }
                icon.blank().click("Click", "to pick " + (questId == null ? "a quest" : "another quest"));
                menu.set(DETAIL_MAIN, icon.build(), click -> QuestPickerMenu.forReply(click.player(), npcId, pageId, index));
                menu.set(DETAIL_SIDE, questInfo(choice.action()));
            }
            case RUN_PLAYER, RUN_CONSOLE -> {
                boolean console = choice.action() == DialogueAction.RUN_CONSOLE;
                String command = Frame.stripSlash(choice.target());
                EditorItems.Builder icon = EditorItems.icon(Material.NAME_TAG)
                        .name(command.isEmpty() ? "§cWhich command? §7(none yet)" : "§6Command: §f/" + EditorItems.truncate(command, 32))
                        .lore(console ? "§8Runs as the server" : "§8Runs as the player");
                if (problem != null) {
                    icon.lore(EditorItems.paragraph("§c", "✖ " + problem, 34));
                }
                icon.blank().click("Click", command.isEmpty() ? "to type it" : "to change it");
                menu.set(DETAIL_MAIN, icon.build(), click -> askCommand(click.player(), npcId, pageId, index));
                EditorItems.Builder info = EditorItems.icon(Material.PAPER)
                        .name("§7Placeholders")
                        .lore("§f{player} §8· §7the player's name",
                                "§f{uuid} §8· §7their UUID",
                                "§f{world} {x} {y} {z} §8· §7where they stand")
                        .blank()
                        .lore("§8Blocked for safety: op, stop, reload,", "§8lp, execute, ban, … and chaining (&&, |).");
                if (console) {
                    info.blank().lore("§cServer commands run with full rights —", "§cdouble-check what you type.");
                }
                menu.set(DETAIL_SIDE, info.build());
            }
        }
    }

    private static ItemStack questInfo(DialogueAction action) {
        return switch (action) {
            case OFFER_QUEST -> EditorItems.icon(Material.BOOK).name("§7What players get")
                    .text("The quest with Accept / Decline. Already on it or done? They get a short note instead.")
                    .blank().lore("§a✔ Recommended way to hand out quests").build();
            case START_QUEST -> EditorItems.icon(Material.BOOK).name("§7What players get")
                    .text("The quest starts at once, no questions asked.")
                    .blank()
                    .lore(EditorItems.paragraph("§c", "Players hold one quest at a time — their current quest is cancelled. "
                            + "\"Offer a quest\" lets them choose.", 34))
                    .build();
            default -> EditorItems.icon(Material.BOOK).name("§7What players get")
                    .text("If they finished the objectives: quest complete + rewards. If not: \"You're not done yet\".")
                    .build();
        };
    }

    private static CustomNpc.DialogueChoice live(NpcEditor editor, String npcId, String pageId, int index) {
        CustomNpc npc = editor.npc(npcId);
        CustomNpc.DialoguePage page = npc == null ? null : npc.page(pageId);
        return page == null || index < 0 || index >= page.choices().size() ? null : page.choices().get(index);
    }

    /** Switches the action; then asks for the one missing setting, if any. */
    private static void choose(Player player, String npcId, String pageId, int index, DialogueAction action) {
        NpcEditor editor = Frame.editor();
        if (live(editor, npcId, pageId, index) == null) {
            open(player, npcId, pageId, index);
            return;
        }
        editor.change(player, editor.npc(npcId), "Reply: " + action.title(), n -> {
            CustomNpc.DialogueChoice choice = n.page(pageId).choices().get(index);
            boolean keepTarget = choice.action().sharesTargetWith(action);
            choice.setAction(action);
            if (!keepTarget) {
                choice.setTarget("");
            }
        });
        CustomNpc npc = editor.npc(npcId);
        CustomNpc.DialogueChoice choice = live(editor, npcId, pageId, index);
        if (choice == null) {
            return;
        }
        if (action == DialogueAction.PAGE && choice.target().isBlank()) {
            pickPage(player, npcId, pageId, index);
        } else if (action.isQuest() && npc.questFor(choice) == null) {
            QuestPickerMenu.forReply(player, npcId, pageId, index);
        } else if (action.isCommand() && choice.target().isBlank()) {
            askCommand(player, npcId, pageId, index);
        } else {
            open(player, npcId, pageId, index);
        }
    }

    private static void pickPage(Player player, String npcId, String pageId, int index) {
        NpcEditor editor = Frame.editor();
        CustomNpc.DialogueChoice choice = live(editor, npcId, pageId, index);
        if (choice == null) {
            return;
        }
        PagePickerMenu.open(player, npcId, new PagePickerMenu.Request(
                "Which page should " + EditorItems.quote(choice.text(), 20) + " open?",
                choice.action() == DialogueAction.PAGE ? choice.target() : null,
                null,
                (p, target) -> {
                    if (live(editor, npcId, pageId, index) != null) {
                        editor.change(p, editor.npc(npcId), "Reply goes to " + CustomNpc.pageTitle(target), n -> {
                            CustomNpc.DialogueChoice c = n.page(pageId).choices().get(index);
                            c.setAction(DialogueAction.PAGE);
                            c.setTarget(target);
                        });
                    }
                    open(p, npcId, pageId, index);
                },
                p -> open(p, npcId, pageId, index),
                "reply " + (index + 1)));
    }

    private static void askCommand(Player player, String npcId, String pageId, int index) {
        NpcEditor editor = Frame.editor();
        CustomNpc.DialogueChoice choice = live(editor, npcId, pageId, index);
        if (choice == null) {
            return;
        }
        boolean console = choice.action() == DialogueAction.RUN_CONSOLE;
        editor.ask(player, TextInput.builder("Command for " + EditorItems.quote(choice.text(), 24))
                .hint("Type it " + (console ? "as the server would run it" : "as the player would run it")
                        + " — with or without the /.")
                .hint("Example: §f" + (console ? "give {player} bread 3" : "warp mine") + "§7. {player} = the player's name.")
                .current(choice.target().isBlank() ? null : Frame.stripSlash(choice.target()))
                .max(128)
                .acceptsCommand()
                .handler((p, text) -> {
                    String command = Frame.stripSlash(text);
                    String problem = DialogueRuntime.commandProblem(command);
                    if (problem != null) {
                        return "Can't use that — " + problem;
                    }
                    if (live(editor, npcId, pageId, index) == null) {
                        return "That reply doesn't exist anymore.";
                    }
                    editor.change(p, editor.npc(npcId), "Command /" + EditorItems.truncate(command, 24),
                            n -> n.page(pageId).choices().get(index).setTarget(command));
                    return null;
                })
                .then(p -> open(p, npcId, pageId, index))
                .build());
    }
}
