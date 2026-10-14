package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditHistory;
import de.aetherion.quests.editor.NpcCheck;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.editor.QuestCatalog;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Shared layout for every studio screen.
 * <pre>
 * NPC screens (tabs)                         Sub-screens
 *  row 0    Overview · Look · Dialogue · Quest   row 0     context card in the middle
 *  row 1    green marker under the open tab      rows 1-4  content
 *  rows 2-4 content                              row 5     same bottom bar
 *  row 5    ◀ Back · ↶ Undo · ◀ ✖ ▶ · ? Help
 * </pre>
 */
final class Frame {

    static final int BACK = 45;
    static final int LEFT = 46;
    static final int UNDO = 47;
    static final int CLOSE = 49;
    static final int RIGHT = 51;
    static final int RIGHT_2 = 52;
    static final int HELP = 53;

    enum Tab {
        OVERVIEW(1, "Overview"),
        LOOK(3, "Look"),
        DIALOGUE(5, "Dialogue"),
        QUEST(7, "Quest");

        final int slot;
        final String label;

        Tab(int slot, String label) {
            this.slot = slot;
            this.label = label;
        }
    }

    private Frame() {
    }

    static NpcEditor editor() {
        return NpcEditor.get();
    }

    /** Breadcrumb title: "Bob › Dialogue › Greeting". */
    static String title(String... parts) {
        StringBuilder out = new StringBuilder("§8");
        for (int i = 0; i < parts.length; i++) {
            String part = ChatColor.stripColor(parts[i] == null ? "" : parts[i]);
            if (i > 0) {
                out.append(" §7› §8");
            }
            boolean crumb = i < parts.length - 1;
            out.append(EditorItems.truncate(part, crumb ? 14 : 20));
        }
        return out.toString();
    }

    /** NPC workspace: tab row + marker row + bottom bar. Content goes in rows 2-4 (slots 18-44). */
    static Menu workspace(Player player, CustomNpc npc, Tab active, Runnable reopen) {
        Menu menu = Menu.of(6, title(npc.getName(), active.label));
        List<NpcCheck.Issue> issues = NpcCheck.run(npc, editor().quests());
        String id = npc.getId();
        for (Tab tab : Tab.values()) {
            menu.set(tab.slot, tabIcon(npc, tab, tab == active, issues),
                    tab == active ? null : click -> openTab(click.player(), id, tab));
        }
        menu.set(active.slot + 9, EditorItems.pane(Material.LIME_STAINED_GLASS_PANE, "§a▲ " + active.label));
        back(menu, "all NPCs", () -> HomeMenu.open(player));
        undo(menu, player, id, reopen);
        close(menu);
        return menu;
    }

    static void openTab(Player player, String npcId, Tab tab) {
        switch (tab) {
            case OVERVIEW -> OverviewMenu.open(player, npcId);
            case LOOK -> LookMenu.open(player, npcId);
            case DIALOGUE -> DialogueMenu.open(player, npcId);
            case QUEST -> QuestMenu.open(player, npcId);
        }
    }

    private static ItemStack tabIcon(CustomNpc npc, Tab tab, boolean active, List<NpcCheck.Issue> issues) {
        EditorItems.Builder icon;
        switch (tab) {
            case OVERVIEW -> {
                long problems = NpcCheck.problems(issues);
                icon = EditorItems.icon(Material.PLAYER_HEAD).skull(npc.getSkinUsername())
                        .name("§b§l" + npc.getName())
                        .text("Overview — preview the chat, place the NPC, check for problems.")
                        .blank()
                        .lore(problems > 0
                                ? "§c✖ " + EditorItems.plural((int) problems, "problem") + " to fix"
                                : issues.isEmpty() ? "§a✔ Ready for players" : "§e⚠ " + EditorItems.plural(issues.size(), "thing") + " to check")
                        .lore("§8id " + npc.getId() + (npc.getCreatedBy() != null ? " · made by " + npc.getCreatedBy() : ""));
            }
            case LOOK -> icon = EditorItems.icon(EditorItems.dyed(Material.LEATHER_CHESTPLATE, npc.getPreset().color()))
                    .name("§6§lLook")
                    .text("Name tag, skin and outfit.")
                    .blank()
                    .lore(EditorItems.BULLET + "§7Name: §f" + npc.getName(),
                            EditorItems.BULLET + "§7Subtitle: " + (npc.hasSubtitle() ? "§f" + npc.getSubtitle() : "§8none"),
                            EditorItems.BULLET + "§7Outfit: §f" + npc.getPreset().label(),
                            EditorItems.BULLET + "§7Skin: §f" + npc.getSkinUsername());
            case DIALOGUE -> {
                icon = EditorItems.icon(Material.WRITABLE_BOOK)
                        .name("§d§lDialogue")
                        .text("What the NPC says and how players can reply.")
                        .blank()
                        .lore(EditorItems.BULLET + "§f" + EditorItems.plural(npc.pages().size(), "page")
                                + " §8· §f" + replies(npc.totalChoices()));
                String first = npc.firstLine();
                if (first != null) {
                    icon.lore(EditorItems.BULLET + "§7Opens with §f" + EditorItems.quote(first, 24));
                }
            }
            default -> {
                QuestCatalog quests = editor().quests();
                icon = EditorItems.icon(npc.hasLinkedQuest() ? Material.FILLED_MAP : Material.MAP)
                        .name("§a§lQuest")
                        .text("Hand out a quest, react to each quest stage, take the turn-in.")
                        .blank()
                        .lore(npc.hasLinkedQuest()
                                ? EditorItems.BULLET + "§7Quest: §f" + quests.title(npc.getLinkedQuestId())
                                : EditorItems.BULLET + "§8No quest yet");
                int used = npc.questReplies().size();
                if (used > 0) {
                    icon.lore(EditorItems.BULLET + "§7Used by §f" + replies(used));
                }
            }
        }
        icon.blank();
        if (active) {
            icon.lore("§a▸ You are here").glow(true);
        } else {
            icon.click("Click", "to open");
        }
        return icon.build();
    }

    /** A sub-screen (no tabs) with Close in the bottom bar. */
    static Menu screen(String title) {
        Menu menu = Menu.of(6, title);
        close(menu);
        return menu;
    }

    static void back(Menu menu, String to, Runnable action) {
        menu.set(menu.size() - 9, EditorItems.icon(Material.ARROW)
                .name("§7◀ Back")
                .lore("§8To " + to)
                .build(), click -> action.run());
    }

    static void close(Menu menu) {
        menu.set(menu.size() - 5, EditorItems.icon(Material.BARRIER).name("§cClose").build(),
                click -> click.player().closeInventory());
    }

    /** Context help: hover shows the tips for this screen, click opens the full guide. */
    static void help(Menu menu, Runnable reopen, String... tips) {
        menu.set(menu.size() - 1, EditorItems.icon(Material.KNOWLEDGE_BOOK)
                .name("§e? How this screen works")
                .lore(tips)
                .blank()
                .click("Click", "for the full studio guide")
                .build(), click -> HelpMenu.open(click.player(), reopen));
    }

    /** Undo for one NPC ({@code npcId}) or for anything (null, used on Home). */
    static void undo(Menu menu, Player player, String npcId, Runnable reopen) {
        int slot = menu.size() - 7;
        NpcEditor editor = editor();
        EditHistory.Entry entry = npcId == null ? editor.history().peek(player) : editor.history().peek(player, npcId);
        if (entry == null) {
            menu.set(slot, EditorItems.icon(Material.GRAY_DYE)
                    .name("§8↶ Undo")
                    .lore("§8Nothing to undo yet.", "", "§8Every change saves instantly —", "§8slipped? Undo it here.")
                    .build());
            return;
        }
        menu.set(slot, EditorItems.icon(Material.CLOCK)
                .name("§e↶ Undo")
                .lore("§f" + entry.label(), "§8" + EditorItems.ago(entry.at())
                        + (npcId == null ? " · " + entry.npcName() : ""))
                .blank()
                .click("Click", "to undo it")
                .build(), click -> {
            editor.undo(click.player(), npcId);
            reopen.run();
        });
    }

    /** ◀ / ▶ page arrows either side of Close; only shown when needed. */
    static void pages(Menu menu, int page, int pages, IntConsumer open) {
        if (pages <= 1) {
            return;
        }
        if (page > 0) {
            menu.set(menu.size() - 6, EditorItems.icon(Material.ARROW).name("§f◀ Previous page")
                    .lore("§8Page " + (page + 1) + " of " + pages).build(), click -> open.accept(page - 1));
        }
        if (page < pages - 1) {
            menu.set(menu.size() - 4, EditorItems.icon(Material.ARROW).name("§fNext page ▶")
                    .lore("§8Page " + (page + 1) + " of " + pages).build(), click -> open.accept(page + 1));
        }
    }

    /** Coloured glass used as a section label ("NPC says ▸"). */
    static ItemStack label(Material pane, String name, String... lore) {
        return EditorItems.icon(pane).name(name).lore(lore).build();
    }

    static void show(Menu menu, Player player) {
        menu.fill(EditorItems.filler());
        menu.open(player);
    }

    static String replies(int count) {
        return count + (count == 1 ? " reply" : " replies");
    }

    /** Loads the NPC or, if it's gone (deleted meanwhile), says so and returns to Home. */
    static CustomNpc require(Player player, String npcId) {
        NpcEditor editor = editor();
        CustomNpc npc = editor == null ? null : editor.npc(npcId);
        if (npc == null && editor != null) {
            editor.error(player, "That NPC doesn't exist anymore.");
            HomeMenu.open(player);
        }
        return npc;
    }

    /** Plain-words summary of what a reply does: "goes to page Shop", "offers Gather Wood (main quest)". */
    static String describe(CustomNpc npc, CustomNpc.DialogueChoice choice) {
        QuestCatalog quests = editor().quests();
        String target = choice.target();
        return switch (choice.action()) {
            case CLOSE -> "§7ends the conversation";
            case PAGE -> target.isBlank() ? "§cgoes to… (no page picked)"
                    : npc.page(target) == null ? "§cgoes to missing page '" + target + "'"
                    : "§7goes to page §f" + CustomNpc.pageTitle(target);
            case OFFER_QUEST, START_QUEST, TURN_IN_QUEST -> {
                String verb = switch (choice.action()) {
                    case OFFER_QUEST -> "offers";
                    case START_QUEST -> "starts";
                    default -> "turns in";
                };
                String questId = npc.questFor(choice);
                if (questId == null) {
                    yield "§c" + verb + "… (no quest picked)";
                }
                yield "§7" + verb + " §f" + quests.title(questId) + (target.isBlank() ? " §8(main quest)" : "");
            }
            case RUN_PLAYER -> target.isBlank() ? "§cruns… (no command yet)" : "§7runs §f/" + EditorItems.truncate(stripSlash(target), 26) + " §8(player)";
            case RUN_CONSOLE -> target.isBlank() ? "§cruns… (no command yet)" : "§7runs §f/" + EditorItems.truncate(stripSlash(target), 26) + " §8(server)";
        };
    }

    static String stripSlash(String command) {
        String trimmed = command == null ? "" : command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }
}
