package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.NpcCheck;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.editor.NpcTemplates;
import de.aetherion.quests.editor.QuestCatalog;
import de.aetherion.quests.editor.gui.QuestHubMenu;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * NPC workspace — Quest tab. Think "quest interaction", not enums:
 * which quest this NPC hands out, what it says at each stage of that quest, and which replies touch it.
 */
public final class QuestMenu {

    private static final int MAIN = 20;
    private static final int SETUP = 22;
    private static final int PREVIEW = 24;
    private static final int[] STAGE_SLOTS = {28, 30, 32, 34};
    private static final int[] ARROW_SLOTS = {29, 31, 33};
    private static final QuestState[] STAGES = {QuestState.AVAILABLE, QuestState.ACTIVE, QuestState.READY, QuestState.COMPLETED};
    private static final Material[] STAGE_ICONS = {Material.WRITABLE_BOOK, Material.COMPASS, Material.BELL, Material.CAKE};
    private static final String[] STAGE_NUMBERS = {"①", "②", "③", "④"};
    private static final int REPLIES_LABEL = 36;
    private static final int FIRST_REPLY = 37;

    private QuestMenu() {
    }

    public static void open(Player player, String npcId) {
        CustomNpc npc = Frame.require(player, npcId);
        if (npc == null) {
            return;
        }
        NpcEditor editor = Frame.editor();
        QuestCatalog quests = editor.quests();
        Runnable reopen = () -> open(player, npcId);
        Menu menu = Frame.workspace(player, npc, Frame.Tab.QUEST, reopen);
        Quest quest = npc.hasLinkedQuest() ? quests.get(npc.getLinkedQuestId()) : null;

        // ---- main quest
        EditorItems.Builder main;
        if (!npc.hasLinkedQuest()) {
            main = EditorItems.icon(Material.MAP)
                    .name("§e+ Pick this NPC's quest")
                    .text("The quest this NPC hands out. Replies that offer, start or turn in a quest use it "
                            + "unless they pick their own.")
                    .blank()
                    .click("Click", "to choose a quest");
        } else if (quest == null) {
            main = EditorItems.icon(Material.MAP)
                    .name("§cUnknown quest '" + npc.getLinkedQuestId() + "'")
                    .text("It doesn't exist (anymore). Pick another one.")
                    .blank()
                    .click("Click", "to choose a quest")
                    .danger("Press Q", "to remove it");
        } else {
            main = EditorItems.icon(Material.FILLED_MAP)
                    .name("§a§l" + QuestCatalog.title(quest))
                    .lore("§8Main quest · id " + quest.getId())
                    .blank()
                    .lore(quests.describe(quest, 34));
            if (quests.isTutorial(quest.getId())) {
                main.blank().lore(EditorItems.paragraph("§6", "⚠ Tutorial quest — handing it out here can skip Harbour steps.", 34));
            }
            main.blank().click("Click", "to pick another quest").danger("Press Q", "to remove it");
        }
        menu.set(MAIN, main.build(), click -> {
            if (click.drop()) {
                if (editor.npc(npcId).hasLinkedQuest()) {
                    editor.change(click.player(), editor.npc(npcId), "Remove main quest", n -> n.setLinkedQuestId(null));
                }
                open(click.player(), npcId);
                return;
            }
            QuestPickerMenu.forMainQuest(click.player(), npcId, false);
        });

        // ---- Hypixel quest designer (create job / objectives / rewards)
        boolean designer = npc.hasLinkedQuest() && editor.editorQuests().isEditorQuest(npc.getLinkedQuestId());
        menu.set(21, EditorItems.icon(Material.EMERALD)
                .name(designer ? "§a§lDesign this job" : "§a§lCreate / design a job")
                .text(designer
                        ? "Open the quest designer — change the objective, rewards, or auto-offer."
                        : "Create a brand-new job for this NPC (talk / gather + coins / XP), or link an existing one.")
                .blank()
                .click("Click", "to open the designer")
                .build(), click -> QuestHubMenu.open(click.player(), editor.npc(npcId)));

        // ---- one-click quest giver
        boolean alreadySetUp = !NpcTemplates.buildQuestGiver(npc.snapshot()).changed();
        EditorItems.Builder setup = EditorItems.icon(Material.LIGHTNING_ROD).glow(alreadySetUp);
        if (alreadySetUp) {
            setup.name("§a✔ Quest giver is set up")
                    .text("Offer reply on the first page and its own page for every quest stage. Edit them in Dialogue.");
        } else {
            setup.name("§e⚡ Set up as quest giver")
                    .lore("§7Builds a ready-to-use quest chat:")
                    .lore(EditorItems.BULLET + "§7an §fOffer quest §7reply on the first page",
                            EditorItems.BULLET + "§7pages for §fIn progress§7, §fReady§7, §fCompleted",
                            EditorItems.BULLET + "§7a §fTurn in §7reply on the Ready page")
                    .blank()
                    .lore("§8Existing lines are kept. Undo-able.")
                    .blank()
                    .click("Click", "to build it");
        }
        menu.set(SETUP, setup.build(), alreadySetUp ? null : click -> {
            if (!editor.npc(npcId).hasLinkedQuest()) {
                click.player().sendMessage("§7First, pick the quest this NPC hands out.");
                QuestPickerMenu.forMainQuest(click.player(), npcId, true);
                return;
            }
            buildQuestGiver(click.player(), npcId);
            open(click.player(), npcId);
        });

        // ---- preview with the editor's real stage
        QuestState mine = editor.runtime().stageOf(player, npc);
        menu.set(PREVIEW, EditorItems.icon(Material.ENDER_EYE)
                .name("§a▶ Preview as you")
                .text("Plays the chat for your own quest stage. Right-click a stage below to preview any stage.")
                .blank()
                .lore("§7Your stage: §f" + (npc.hasLinkedQuest() ? NpcCheck.stageName(mine) : "—"))
                .build(), click -> editor.preview(click.player(), editor.npc(npcId), null, null, reopen));

        // ---- stage timeline
        for (int i = 0; i < STAGES.length; i++) {
            QuestState stage = STAGES[i];
            menu.set(STAGE_SLOTS[i], stageItem(npc, quests, stage, i), click -> onStage(click, npcId, stage, reopen));
        }
        for (int slot : ARROW_SLOTS) {
            menu.set(slot, Frame.label(Material.GRAY_STAINED_GLASS_PANE, "§8→", "§8Quest stages, left to right."));
        }

        // ---- replies that touch quests
        List<CustomNpc.Link> links = npc.questReplies();
        menu.set(REPLIES_LABEL, Frame.label(Material.MAGENTA_STAINED_GLASS_PANE, "§d§lQuest replies ▸",
                "§7Every reply that offers, starts", "§7or turns in a quest."));
        if (links.isEmpty()) {
            menu.set(40, EditorItems.icon(Material.LIGHT_GRAY_DYE)
                    .name("§7No quest replies yet")
                    .text("Use ⚡ Set up as quest giver, or give any reply the action \"Offer a quest\".")
                    .build());
        }
        for (int i = 0; i < links.size() && i < 7; i++) {
            CustomNpc.Link link = links.get(i);
            menu.set(FIRST_REPLY + i, EditorItems.icon(link.choice().action().icon())
                    .name("§e▸ " + EditorItems.quote(link.choice().text(), 24))
                    .lore("§7On page §f" + CustomNpc.pageTitle(link.pageId()),
                            "§7Then it " + Frame.describe(npc, link.choice()))
                    .blank()
                    .click("Click", "to edit this reply")
                    .build(), click -> ReplyMenu.open(click.player(), npcId, link.pageId(), link.index()));
        }

        Frame.help(menu, reopen,
                "§fMain quest §7— what this NPC hands out.",
                "",
                "§fCreate / design a job §7— brand-new",
                "§7editor quests (objectives + rewards).",
                "",
                "§fStages §7— where the chat starts for a",
                "§7player who hasn't started, is working on,",
                "§7has finished, or turned in the quest.");
        Frame.show(menu, player);
    }

    private static org.bukkit.inventory.ItemStack stageItem(CustomNpc npc, QuestCatalog quests, QuestState stage, int index) {
        String name = "§f" + STAGE_NUMBERS[index] + " " + NpcCheck.stageName(stage);
        if (!npc.hasLinkedQuest()) {
            return EditorItems.icon(Material.GRAY_DYE)
                    .name("§8" + STAGE_NUMBERS[index] + " " + NpcCheck.stageName(stage))
                    .text("Pick the NPC's quest first.")
                    .blank()
                    .click("Click", "to choose a quest")
                    .build();
        }
        String quest = quests.title(npc.getLinkedQuestId());
        String when = switch (stage) {
            case AVAILABLE -> "hasn't started " + quest;
            case ACTIVE -> "is working on " + quest;
            case READY -> "finished " + quest + " but hasn't turned it in";
            case COMPLETED -> "already turned in " + quest;
        };
        String own = stage == QuestState.AVAILABLE ? npc.getStartPage() : npc.questPage(stage);
        EditorItems.Builder icon = EditorItems.icon(STAGE_ICONS[index])
                .name(name)
                .text("When the player " + when + ", the chat opens on:")
                .lore(own != null
                        ? "§a  " + CustomNpc.pageTitle(own)
                        : "§8  the first page (" + CustomNpc.pageTitle(npc.getStartPage()) + ")")
                .glow(own != null && stage != QuestState.AVAILABLE)
                .blank()
                .click("Click", "to choose the page")
                .click("Right-click", "to preview as this stage");
        if (own != null && stage != QuestState.AVAILABLE) {
            icon.danger("Press Q", "to use the first page again");
        }
        return icon.build();
    }

    private static void onStage(Menu.Click click, String npcId, QuestState stage, Runnable reopen) {
        NpcEditor editor = Frame.editor();
        Player player = click.player();
        CustomNpc npc = editor.npc(npcId);
        if (npc == null) {
            return;
        }
        if (!npc.hasLinkedQuest()) {
            QuestPickerMenu.forMainQuest(player, npcId, false);
            return;
        }
        if (click.right()) {
            editor.preview(player, npc, null, stage, reopen);
            return;
        }
        if (click.drop()) {
            if (stage != QuestState.AVAILABLE && npc.questPage(stage) != null) {
                editor.change(player, npc, NpcCheck.stageName(stage) + ": first page", n -> n.setQuestPage(stage, null));
            }
            open(player, npcId);
            return;
        }
        boolean first = stage == QuestState.AVAILABLE;
        PagePickerMenu.open(player, npcId, new PagePickerMenu.Request(
                "Where does the chat start when the quest is " + NpcCheck.stageName(stage).toLowerCase() + "?",
                first ? npc.getStartPage() : npc.questPage(stage),
                first ? null : "Use the first page",
                (p, pageId) -> {
                    if (first) {
                        editor.change(p, editor.npc(npcId), "First page: " + CustomNpc.pageTitle(pageId),
                                n -> n.setStartPage(pageId));
                    } else {
                        editor.change(p, editor.npc(npcId), NpcCheck.stageName(stage) + ": "
                                        + (pageId == null ? "first page" : CustomNpc.pageTitle(pageId)),
                                n -> n.setQuestPage(stage, pageId));
                    }
                    open(p, npcId);
                },
                p -> open(p, npcId),
                "Quest"));
    }

    static void buildQuestGiver(Player player, String npcId) {
        NpcEditor editor = Frame.editor();
        NpcTemplates.Setup[] result = new NpcTemplates.Setup[1];
        editor.change(player, editor.npc(npcId), "Set up as quest giver", n -> result[0] = NpcTemplates.buildQuestGiver(n));
        NpcTemplates.Setup setup = result[0];
        if (setup != null && setup.changed()) {
            player.sendMessage("§a✔ Quest giver ready §7— " + EditorItems.plural(setup.pagesAdded(), "page") + " and "
                    + Frame.replies(setup.repliesAdded()) + " added, " + setup.stagesLinked() + " stages linked. "
                    + "§8Tweak the lines in the Dialogue tab.");
        }
    }
}
