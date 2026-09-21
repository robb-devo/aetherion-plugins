package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.DialogueAction;
import de.aetherion.quests.editor.EditorScreen;
import de.aetherion.quests.editor.EditorSessions;
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

import java.util.ArrayList;
import java.util.List;

public final class DialogueMenu implements Listener {

    private static final int ADD_PAGE = 45;
    private static final int ADD_LINE = 16;
    private static final int ADD_CHOICE = 43;
    private static final int MAKE_START = 45;
    private static final int DELETE_PAGE = 47;
    private static final int REWRITE = 10;
    private static final int ACTION = 12;
    private static final int TARGET = 14;
    private static final int REMOVE_CHOICE = 16;

    private final NpcEditor editor;

    public DialogueMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void openTree(Player player, CustomNpc npc) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(Kind.TREE, npc.getId(), null, -1),
                54,
                EditorItems.title(player, "npc_dialogue", "§8Dialogue")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.WRITABLE_BOOK,
                EditorItems.ui(player, "editor_dialogue_tree", "§dWhat they say"),
                EditorItems.ui(player, "editor_dialogue_tree_l1", "§7Click a page to edit lines & replies."),
                EditorItems.ui(player, "editor_dialogue_tree_l2", "§7One page is enough for a greeter.")
        ));
        int slot = 19;
        for (CustomNpc.DialoguePage page : npc.pages().values()) {
            if (slot >= 44) {
                break;
            }
            boolean start = page.id().equalsIgnoreCase(npc.getStartPage());
            String preview = page.lines().isEmpty() ? "…" : page.lines().get(0);
            if (preview.length() > 28) {
                preview = preview.substring(0, 28) + "…";
            }
            inventory.setItem(slot++, EditorItems.button(
                    start ? Material.WRITTEN_BOOK : Material.BOOK,
                    (start ? "§a" : "§e") + prettyPage(page.id()),
                    "§7" + preview,
                    start
                            ? EditorItems.ui(player, "editor_start_page", "§aOpens first")
                            : EditorItems.ui(player, "editor_open_page", "§7Click to edit")
            ));
        }
        inventory.setItem(ADD_PAGE, EditorItems.button(
                Material.EMERALD,
                EditorItems.ui(player, "editor_add_page", "§aAdd page"),
                EditorItems.ui(player, "editor_add_page_hint", "§7For a second conversation branch.")
        ));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        player.openInventory(inventory);
    }

    public static void openPage(Player player, CustomNpc npc, String pageId) {
        CustomNpc.DialoguePage page = npc.page(pageId);
        if (page == null) {
            openTree(player, npc);
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                new Holder(Kind.PAGE, npc.getId(), page.id(), -1),
                54,
                EditorItems.title(player, "npc_page", "§8Write lines")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.BOOK,
                "§e" + prettyPage(page.id()),
                EditorItems.ui(player, "editor_page_hint", "§7Lines they say, then player replies.")
        ));
        inventory.setItem(9, EditorItems.section(
                EditorItems.ui(player, "editor_sec_lines", "They say"),
                EditorItems.ui(player, "editor_sec_lines_hint", "§7Click to rewrite · Shift-click to remove")
        ));
        for (int i = 0; i < Math.min(page.lines().size(), 6); i++) {
            inventory.setItem(10 + i, EditorItems.button(
                    Material.PAPER,
                    "§f" + page.lines().get(i),
                    EditorItems.ui(player, "editor_line_edit", "§7Click to rewrite"),
                    EditorItems.ui(player, "editor_line_remove", "§cShift-click to remove")
            ));
        }
        inventory.setItem(ADD_LINE, EditorItems.button(
                Material.EMERALD,
                EditorItems.ui(player, "editor_add_line", "§aAdd a line"),
                EditorItems.ui(player, "editor_add_line_hint", "§7Type the next sentence in chat.")
        ));
        inventory.setItem(27, EditorItems.section(
                EditorItems.ui(player, "editor_sec_replies", "Player replies"),
                EditorItems.ui(player, "editor_sec_replies_hint", "§7Buttons the player clicks.")
        ));
        for (int i = 0; i < Math.min(page.choices().size(), 6); i++) {
            CustomNpc.DialogueChoice choice = page.choices().get(i);
            inventory.setItem(28 + i, EditorItems.button(
                    Material.OAK_SIGN,
                    "§e" + choice.text(),
                    "§7" + choice.action().label(),
                    choice.target().isBlank()
                            ? EditorItems.ui(player, "editor_no_target", "§8no extra target")
                            : "§f" + choice.target()
            ));
        }
        inventory.setItem(ADD_CHOICE, EditorItems.button(
                Material.LIME_DYE,
                EditorItems.ui(player, "editor_add_reply", "§aAdd a reply")
        ));
        boolean start = page.id().equalsIgnoreCase(npc.getStartPage());
        inventory.setItem(MAKE_START, EditorItems.button(
                start ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE,
                start
                        ? EditorItems.ui(player, "editor_is_start", "§aOpens first")
                        : EditorItems.ui(player, "editor_make_start", "§eMake this the first page")
        ));
        if (!CustomNpc.START_PAGE.equalsIgnoreCase(page.id()) || npc.pages().size() > 1) {
            inventory.setItem(DELETE_PAGE, EditorItems.button(
                    Material.BARRIER,
                    EditorItems.ui(player, "editor_delete_page", "§cDelete this page")
            ));
        }
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        player.openInventory(inventory);
    }

    public static void openChoice(Player player, CustomNpc npc, String pageId, int index) {
        CustomNpc.DialoguePage page = npc.page(pageId);
        if (page == null || index < 0 || index >= page.choices().size()) {
            openPage(player, npc, pageId);
            return;
        }
        CustomNpc.DialogueChoice choice = page.choices().get(index);
        Inventory inventory = Bukkit.createInventory(
                new Holder(Kind.CHOICE, npc.getId(), page.id(), index),
                54,
                EditorItems.title(player, "npc_choice", "§8Reply")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.OAK_SIGN,
                "§e" + choice.text(),
                EditorItems.ui(player, "editor_choice_head", "§7What the player clicks.")
        ));
        inventory.setItem(REWRITE, EditorItems.button(
                Material.NAME_TAG,
                EditorItems.ui(player, "editor_rewrite_reply", "§eRewrite the button"),
                "§7" + choice.text()
        ));
        inventory.setItem(ACTION, EditorItems.button(
                Material.REPEATER,
                EditorItems.ui(player, "editor_what_happens", "§6What happens"),
                "§f" + choice.action().label(),
                "§7" + choice.action().hint(),
                EditorItems.ui(player, "editor_pick_action", "§8Click to pick")
        ));
        inventory.setItem(TARGET, EditorItems.button(
                Material.COMPASS,
                EditorItems.ui(player, "editor_target", "§bExtra target"),
                choice.target().isBlank()
                        ? EditorItems.ui(player, "editor_target_empty", "§8None — uses the linked quest / page")
                        : "§f" + choice.target(),
                targetHint(player, choice.action())
        ));
        inventory.setItem(REMOVE_CHOICE, EditorItems.button(
                Material.BARRIER,
                EditorItems.ui(player, "editor_remove_reply", "§cRemove this reply")
        ));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        player.openInventory(inventory);
    }

    public static void openActionPick(Player player, CustomNpc npc, String pageId, int index) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(Kind.ACTION, npc.getId(), pageId, index),
                54,
                EditorItems.title(player, "npc_action", "§8What happens")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.REPEATER,
                EditorItems.ui(player, "editor_what_happens", "§6What happens"),
                EditorItems.ui(player, "editor_action_pick_hint", "§7Simple first. Commands are optional.")
        ));
        inventory.setItem(19, actionItem(player, DialogueAction.CLOSE, Material.BARRIER));
        inventory.setItem(20, actionItem(player, DialogueAction.PAGE, Material.WRITABLE_BOOK));
        inventory.setItem(21, actionItem(player, DialogueAction.OFFER_QUEST, Material.MAP));
        inventory.setItem(22, actionItem(player, DialogueAction.START_QUEST, Material.LIME_DYE));
        inventory.setItem(23, actionItem(player, DialogueAction.TURN_IN_QUEST, Material.EXPERIENCE_BOTTLE));
        inventory.setItem(28, EditorItems.section(
                EditorItems.ui(player, "editor_sec_advanced", "Advanced"),
                EditorItems.ui(player, "editor_sec_advanced_hint", "§7Safe commands only.")
        ));
        inventory.setItem(29, actionItem(player, DialogueAction.RUN_PLAYER, Material.COMMAND_BLOCK));
        inventory.setItem(30, actionItem(player, DialogueAction.RUN_CONSOLE, Material.REPEATING_COMMAND_BLOCK));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        player.openInventory(inventory);
    }

    private static org.bukkit.inventory.ItemStack actionItem(Player player, DialogueAction action, Material icon) {
        return EditorItems.button(
                icon,
                "§e" + action.label(),
                "§7" + action.hint()
        );
    }

    private static String prettyPage(String id) {
        if (id == null || id.isBlank()) {
            return "greeting";
        }
        if ("greeting".equalsIgnoreCase(id)) {
            return "Greeting";
        }
        return id;
    }

    private static String targetHint(Player player, DialogueAction action) {
        return switch (action) {
            case PAGE -> EditorItems.ui(player, "editor_target_page", "§7Name of the page to open.");
            case RUN_CONSOLE, RUN_PLAYER -> EditorItems.ui(player, "editor_target_cmd", "§7Command without a leading /");
            case OFFER_QUEST, START_QUEST, TURN_IN_QUEST ->
                    EditorItems.ui(player, "editor_target_quest", "§7Quest id, or empty to use the linked one.");
            case CLOSE -> EditorItems.ui(player, "editor_target_none", "§7Nothing extra needed.");
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        Player player = EditorItems.editorClick(event);
        if (player == null) {
            return;
        }
        CustomNpc npc = editor.storage().get(holder.npcId());
        if (npc == null) {
            return;
        }
        editor.sessions().of(player).setNpcId(npc.getId());
        editor.sessions().of(player).setPageId(holder.pageId());
        switch (holder.kind()) {
            case TREE -> clickTree(player, npc, event.getRawSlot());
            case PAGE -> clickPage(player, npc, holder.pageId(), event);
            case CHOICE -> clickChoice(player, npc, holder, event.getRawSlot());
            case ACTION -> clickAction(player, npc, holder, event.getRawSlot());
        }
    }

    private void clickTree(Player player, CustomNpc npc, int slot) {
        if (slot == EditorItems.BACK) {
            EditMenu.open(player, npc);
            return;
        }
        if (slot == ADD_PAGE) {
            editor.sessions().of(player).setChoiceIndex(-1);
            editor.prompt(player, EditorSessions.Prompt.PAGE_ID, EditorScreen.DIALOGUE_TREE,
                    EditorItems.ui(player, "editor_prompt_page", "Type a short page name (shop, goodbye…)"),
                    null);
            return;
        }
        List<CustomNpc.DialoguePage> pages = new ArrayList<>(npc.pages().values());
        if (slot < 19) {
            return;
        }
        int index = slot - 19;
        if (index >= 0 && index < pages.size()) {
            openPage(player, npc, pages.get(index).id());
        }
    }

    private void clickPage(Player player, CustomNpc npc, String pageId, InventoryClickEvent event) {
        CustomNpc.DialoguePage page = npc.page(pageId);
        if (page == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == EditorItems.BACK) {
            openTree(player, npc);
            return;
        }
        if (slot == ADD_LINE) {
            editor.sessions().of(player).setPageId(page.id());
            editor.prompt(player, EditorSessions.Prompt.LINE, EditorScreen.DIALOGUE_PAGE,
                    EditorItems.ui(player, "editor_prompt_line", "Type the next line they say"),
                    null);
            return;
        }
        if (slot == ADD_CHOICE) {
            page.choices().add(new CustomNpc.DialogueChoice(
                    EditorItems.ui(player, "editor_new_reply", "Okay"),
                    DialogueAction.CLOSE,
                    ""
            ));
            editor.persistQuiet(npc);
            openPage(player, npc, page.id());
            return;
        }
        if (slot == MAKE_START) {
            npc.setStartPage(page.id());
            editor.persistQuiet(npc);
            openPage(player, npc, page.id());
            return;
        }
        if (slot == DELETE_PAGE) {
            npc.removePage(page.id());
            editor.persistQuiet(npc);
            openTree(player, npc);
            return;
        }
        if (slot >= 10 && slot <= 15) {
            int index = slot - 10;
            if (index >= page.lines().size()) {
                return;
            }
            if (event.isShiftClick()) {
                page.lines().remove(index);
                if (page.lines().isEmpty()) {
                    page.lines().add("…");
                }
                editor.persistQuiet(npc);
                openPage(player, npc, page.id());
                return;
            }
            editor.sessions().of(player).setPageId(page.id());
            editor.sessions().of(player).setChoiceIndex(index);
            editor.prompt(player, EditorSessions.Prompt.LINE_EDIT, EditorScreen.DIALOGUE_PAGE,
                    EditorItems.ui(player, "editor_prompt_line_edit", "Type the new line"),
                    page.lines().get(index));
            return;
        }
        if (slot >= 28 && slot <= 33) {
            int index = slot - 28;
            if (index < page.choices().size()) {
                openChoice(player, npc, page.id(), index);
            }
        }
    }

    private void clickChoice(Player player, CustomNpc npc, Holder holder, int slot) {
        CustomNpc.DialoguePage page = npc.page(holder.pageId());
        if (page == null || holder.choiceIndex() < 0 || holder.choiceIndex() >= page.choices().size()) {
            return;
        }
        CustomNpc.DialogueChoice choice = page.choices().get(holder.choiceIndex());
        editor.sessions().of(player).setChoiceIndex(holder.choiceIndex());
        if (slot == EditorItems.BACK) {
            openPage(player, npc, page.id());
            return;
        }
        if (slot == REWRITE) {
            editor.prompt(player, EditorSessions.Prompt.CHOICE_TEXT, EditorScreen.CHOICE,
                    EditorItems.ui(player, "editor_prompt_choice", "Type the button the player sees"),
                    choice.text());
            return;
        }
        if (slot == ACTION) {
            openActionPick(player, npc, page.id(), holder.choiceIndex());
            return;
        }
        if (slot == TARGET) {
            switch (choice.action()) {
                case PAGE -> editor.prompt(player, EditorSessions.Prompt.PAGE_ID, EditorScreen.CHOICE,
                        EditorItems.ui(player, "editor_prompt_page_target", "Type the page name to open"),
                        choice.target());
                case RUN_CONSOLE, RUN_PLAYER ->
                        editor.prompt(player, EditorSessions.Prompt.COMMAND, EditorScreen.CHOICE,
                                EditorItems.ui(player, "editor_prompt_cmd",
                                        "Type the command (no /). {player} works."),
                                choice.target());
                case OFFER_QUEST, START_QUEST, TURN_IN_QUEST ->
                        editor.prompt(player, EditorSessions.Prompt.QUEST_ID, EditorScreen.CHOICE,
                                EditorItems.ui(player, "editor_prompt_quest_id",
                                        "Type a quest id, or cancel to keep the linked one"),
                                choice.target());
                case CLOSE -> player.sendMessage(EditorItems.ui(player, "editor_target_none",
                        "§7Nothing extra needed."));
            }
            return;
        }
        if (slot == REMOVE_CHOICE) {
            page.choices().remove(holder.choiceIndex());
            editor.persistQuiet(npc);
            openPage(player, npc, page.id());
        }
    }

    private void clickAction(Player player, CustomNpc npc, Holder holder, int slot) {
        CustomNpc.DialoguePage page = npc.page(holder.pageId());
        if (page == null || holder.choiceIndex() < 0 || holder.choiceIndex() >= page.choices().size()) {
            return;
        }
        if (slot == EditorItems.BACK) {
            openChoice(player, npc, page.id(), holder.choiceIndex());
            return;
        }
        DialogueAction picked = switch (slot) {
            case 19 -> DialogueAction.CLOSE;
            case 20 -> DialogueAction.PAGE;
            case 21 -> DialogueAction.OFFER_QUEST;
            case 22 -> DialogueAction.START_QUEST;
            case 23 -> DialogueAction.TURN_IN_QUEST;
            case 29 -> DialogueAction.RUN_PLAYER;
            case 30 -> DialogueAction.RUN_CONSOLE;
            default -> null;
        };
        if (picked == null) {
            return;
        }
        page.choices().get(holder.choiceIndex()).setAction(picked);
        editor.persistQuiet(npc);
        openChoice(player, npc, page.id(), holder.choiceIndex());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    enum Kind {
        TREE,
        PAGE,
        CHOICE,
        ACTION
    }

    public record Holder(Kind kind, String npcId, String pageId, int choiceIndex) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
