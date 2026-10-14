package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.DialogueAction;
import de.aetherion.quests.editor.EditorSessions;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.editor.QuestCatalog;
import de.aetherion.quests.editor.TextInput;
import de.aetherion.quests.model.Quest;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * "Which quest?" — browse registered quests with their objectives and rewards instead of typing ids.
 * Tutorial (Harbour) quests are hidden by default.
 */
public final class QuestPickerMenu {

    private static final int SEARCH = 2;
    private static final int CARD = 4;
    private static final int TUTORIAL = 6;
    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;

    /** What's being picked and what happens with the answer (quest id, or null = "clear"). */
    private record Request(String npcId, String question, String current, String mainQuestTitle,
                           BiConsumer<Player, String> onPick, Consumer<Player> onClear, String clearLabel,
                           Consumer<Player> onBack, String backLabel) {
    }

    private QuestPickerMenu() {
    }

    /** Picks the NPC's main quest. {@code thenSetUp}: continue with the one-click quest-giver setup. */
    public static void forMainQuest(Player player, String npcId, boolean thenSetUp) {
        NpcEditor editor = NpcEditor.get();
        CustomNpc npc = Frame.require(player, npcId);
        if (npc == null) {
            return;
        }
        Request request = new Request(npcId,
                "Which quest does " + npc.getName() + " hand out?",
                npc.getLinkedQuestId(),
                null,
                (p, questId) -> {
                    editor.change(p, editor.npc(npcId), "Quest: " + editor.quests().title(questId),
                            n -> n.setLinkedQuestId(questId));
                    warnTutorial(p, editor, questId);
                    if (thenSetUp) {
                        QuestMenu.buildQuestGiver(p, npcId);
                    }
                    QuestMenu.open(p, npcId);
                },
                npc.hasLinkedQuest() ? p -> {
                    editor.change(p, editor.npc(npcId), "Remove main quest", n -> n.setLinkedQuestId(null));
                    QuestMenu.open(p, npcId);
                } : null,
                "No main quest",
                p -> QuestMenu.open(p, npcId),
                "Quest");
        open(player, request, 0);
    }

    /** Picks the quest one reply acts on (or "use the main quest"). */
    public static void forReply(Player player, String npcId, String pageId, int index) {
        NpcEditor editor = NpcEditor.get();
        CustomNpc npc = Frame.require(player, npcId);
        CustomNpc.DialoguePage page = npc == null ? null : npc.page(pageId);
        if (page == null || index < 0 || index >= page.choices().size()) {
            return;
        }
        CustomNpc.DialogueChoice choice = page.choices().get(index);
        Request request = new Request(npcId,
                "Which quest should " + EditorItems.quote(choice.text(), 20) + " " + verb(choice.action()) + "?",
                choice.target().isBlank() ? null : choice.target(),
                npc.hasLinkedQuest() ? editor.quests().title(npc.getLinkedQuestId()) : null,
                (p, questId) -> {
                    editor.change(p, editor.npc(npcId), "Reply quest: "
                                    + (questId == null ? "main quest" : editor.quests().title(questId)),
                            n -> {
                                CustomNpc.DialoguePage livePage = n.page(pageId);
                                if (livePage != null && index < livePage.choices().size()) {
                                    livePage.choices().get(index).setTarget(questId == null ? "" : questId);
                                }
                            });
                    warnTutorial(p, editor, questId);
                    ReplyMenu.open(p, npcId, pageId, index);
                },
                null,
                null,
                p -> ReplyMenu.open(p, npcId, pageId, index),
                "reply " + (index + 1));
        open(player, request, 0);
    }

    private static String verb(DialogueAction action) {
        return switch (action) {
            case START_QUEST -> "start";
            case TURN_IN_QUEST -> "turn in";
            default -> "offer";
        };
    }

    private static void open(Player player, Request request, int requested) {
        NpcEditor editor = NpcEditor.get();
        CustomNpc npc = Frame.require(player, request.npcId());
        if (npc == null) {
            return;
        }
        QuestCatalog quests = editor.quests();
        EditorSessions.Session session = editor.sessions().of(player);
        String search = session.questSearch();
        boolean tutorials = session.showTutorialQuests();
        List<Quest> list = quests.search(search, tutorials);
        boolean mainTile = request.mainQuestTitle() != null;
        int tiles = list.size() + (mainTile ? 1 : 0);
        int pages = Math.max(1, (tiles + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(0, Math.min(requested, pages - 1));
        Menu menu = Frame.screen(Frame.title(npc.getName(), "Pick a quest"));

        EditorItems.Builder searchIcon = EditorItems.icon(Material.SPYGLASS);
        if (search == null) {
            searchIcon.name("§eSearch").text("Find a quest by title, id or description.").blank().click("Click", "to search");
        } else {
            searchIcon.name("§eSearch: §f" + search).glow(true)
                    .lore("§7" + list.size() + (list.size() == 1 ? " match" : " matches"))
                    .blank().click("Click", "to search again").danger("Press Q", "to clear the search");
        }
        menu.set(SEARCH, searchIcon.build(), click -> {
            if (click.drop()) {
                session.setQuestSearch(null);
                open(click.player(), request, 0);
                return;
            }
            editor.ask(click.player(), TextInput.builder("Search quests")
                    .hint("A word from the title, id or description.")
                    .current(search)
                    .max(32)
                    .handler((p, text) -> {
                        session.setQuestSearch(text);
                        return null;
                    })
                    .then(p -> open(p, request, 0))
                    .build());
        });

        menu.set(CARD, EditorItems.icon(Material.MAP)
                .name("§f" + request.question())
                .lore(request.current() == null ? "§8Now: none" : "§7Now: §f" + quests.title(request.current()))
                .blank()
                .lore("§8Quests are defined by the server; here you", "§8only choose which one this NPC uses.")
                .build());

        int hidden = quests.tutorialCount();
        menu.set(TUTORIAL, EditorItems.icon(tutorials ? Material.REDSTONE_TORCH : Material.LEVER)
                .name("§7Tutorial quests: " + (tutorials ? "§eshown" : "§fhidden"))
                .text(hidden + " quests belong to the Harbour tutorial. Handing them out elsewhere can skip steps.")
                .blank()
                .click("Click", tutorials ? "to hide them" : "to show them")
                .build(), click -> {
            session.setShowTutorialQuests(!tutorials);
            open(click.player(), request, 0);
        });

        int start = page * PER_PAGE;
        for (int t = start; t < tiles && t < start + PER_PAGE; t++) {
            int slot = FIRST + (t - start);
            if (mainTile && t == 0) {
                boolean selected = request.current() == null;
                menu.set(slot, EditorItems.icon(Material.NETHER_STAR)
                        .name((selected ? "§a✔ " : "§f↺ ") + "Use the NPC's main quest")
                        .lore("§7Now: §f" + request.mainQuestTitle())
                        .text("Follows the Quest tab — change it there and this reply follows.")
                        .glow(selected)
                        .blank()
                        .click("Click", "to use it")
                        .build(), click -> request.onPick().accept(click.player(), null));
                continue;
            }
            Quest quest = list.get(t - (mainTile ? 1 : 0));
            boolean selected = quest.getId().equalsIgnoreCase(request.current());
            boolean tutorial = quests.isTutorial(quest.getId());
            EditorItems.Builder icon = EditorItems.icon(selected ? Material.FILLED_MAP : Material.MAP)
                    .name((selected ? "§a✔ " : tutorial ? "§6" : "§e") + QuestCatalog.title(quest))
                    .lore("§8id " + quest.getId() + (tutorial ? " · tutorial" : ""))
                    .glow(selected)
                    .blank()
                    .lore(quests.describe(quest, 34));
            icon.blank().click("Click", selected ? "to keep it" : "to choose it");
            String questId = quest.getId();
            menu.set(slot, icon.build(), click -> request.onPick().accept(click.player(), questId));
        }
        if (list.isEmpty()) {
            menu.set(22, EditorItems.icon(Material.BARRIER)
                    .name("§7No quests found")
                    .text(search != null ? "Nothing matches \"" + search + "\". Press Q on the search to clear it."
                            : "The quest system has no quests loaded.")
                    .build());
        }

        Frame.back(menu, request.backLabel(), () -> request.onBack().accept(player));
        Frame.pages(menu, page, pages, next -> open(player, request, next));
        if (request.onClear() != null) {
            menu.set(Frame.RIGHT, EditorItems.icon(Material.STRUCTURE_VOID)
                    .name("§c✖ " + request.clearLabel())
                    .text("Unlinks the quest from this NPC. Replies that relied on it will need a quest again.")
                    .build(), click -> request.onClear().accept(click.player()));
        }
        Frame.help(menu, () -> open(player, request, page),
                "§7Pick a quest. Hover to see its",
                "§7objectives and rewards.",
                "",
                "§eSearch §7filters the list.",
                "§7Tutorial quests are hidden unless you",
                "§7turn them on (top right).");
        Frame.show(menu, player);
    }

    private static void warnTutorial(Player player, NpcEditor editor, String questId) {
        if (questId != null && editor.quests().isTutorial(questId)) {
            player.sendMessage("§6⚠ §7" + editor.quests().title(questId)
                    + " is a Harbour tutorial quest — handing it out here can let players skip steps.");
        }
    }
}
