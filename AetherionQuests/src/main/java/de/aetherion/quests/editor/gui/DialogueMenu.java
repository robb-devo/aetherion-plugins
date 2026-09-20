package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.DialogueAction;
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
                "§dDialogue tree",
                "§7Start page §f" + npc.getStartPage(),
                "§7Click a page to edit lines & choices."
        ));
        int slot = 9;
        for (CustomNpc.DialoguePage page : npc.pages().values()) {
            if (slot >= 44) {
                break;
            }
            boolean start = page.id().equalsIgnoreCase(npc.getStartPage());
            inventory.setItem(slot++, EditorItems.button(
                    start ? Material.WRITTEN_BOOK : Material.BOOK,
                    (start ? "§a" : "§e") + page.id(),
                    "§7" + page.lines().size() + " line(s)",
                    "§7" + page.choices().size() + " choice(s)",
                    start ? "§aStart page" : "§7Click to edit"
            ));
        }
        inventory.setItem(45, EditorItems.button(Material.EMERALD, "§aAdd page", "§7Type a short id in chat."));
        inventory.setItem(49, EditorItems.button(Material.ARROW, "§7Back"));
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
                "§8Page · " + page.id()
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.BOOK,
                "§e" + page.id(),
                "§7Lines then player choices."
        ));
        for (int i = 0; i < Math.min(page.lines().size(), 7); i++) {
            inventory.setItem(10 + i, EditorItems.button(
                    Material.PAPER,
                    "§f" + page.lines().get(i),
                    "§cShift-click to remove"
            ));
        }
        inventory.setItem(17, EditorItems.button(Material.EMERALD, "§aAdd line", "§7Type the line in chat."));
        for (int i = 0; i < Math.min(page.choices().size(), 7); i++) {
            CustomNpc.DialogueChoice choice = page.choices().get(i);
            inventory.setItem(28 + i, EditorItems.button(
                    Material.OAK_SIGN,
                    "§e" + choice.text(),
                    "§7" + choice.action().label(),
                    choice.target().isBlank() ? "§8no target" : "§f" + choice.target()
            ));
        }
        inventory.setItem(35, EditorItems.button(Material.LIME_DYE, "§aAdd choice"));
        boolean start = page.id().equalsIgnoreCase(npc.getStartPage());
        inventory.setItem(45, EditorItems.button(
                start ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE,
                start ? "§aStart page" : "§eMake start page"
        ));
        if (!CustomNpc.START_PAGE.equalsIgnoreCase(page.id()) || npc.pages().size() > 1) {
            inventory.setItem(47, EditorItems.button(Material.BARRIER, "§cDelete page"));
        }
        inventory.setItem(49, EditorItems.button(Material.ARROW, "§7Back"));
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
                "§8Choice"
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(Material.OAK_SIGN, "§e" + choice.text(), "§7Click to rewrite."));
        inventory.setItem(8, EditorItems.saved(false));
        inventory.setItem(18, EditorItems.section("Choice", "§7What the player clicks."));
        inventory.setItem(20, EditorItems.button(Material.REPEATER, "§6Action", "§f" + choice.action().label(), "§7Click to cycle."));
        inventory.setItem(22, EditorItems.button(
                Material.COMPASS,
                "§bTarget",
                choice.target().isBlank() ? "§8empty" : "§f" + choice.target(),
                targetHint(choice.action())
        ));
        inventory.setItem(24, EditorItems.button(Material.BARRIER, "§cRemove choice"));
        inventory.setItem(49, EditorItems.button(Material.ARROW, "§7Back"));
        player.openInventory(inventory);
    }

    private static String targetHint(DialogueAction action) {
        return switch (action) {
            case PAGE -> "§7Page id to open.";
            case RUN_CONSOLE, RUN_PLAYER -> "§7Command without leading /";
            case OFFER_QUEST, START_QUEST, TURN_IN_QUEST -> "§7Existing quest id (or empty = linked).";
            case CLOSE -> "§7No target.";
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !NpcEditor.allowed(player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
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
        }
    }

    private void clickTree(Player player, CustomNpc npc, int slot) {
        if (slot == 49) {
            EditMenu.open(player, npc);
            return;
        }
        if (slot == 45) {
            editor.sessions().of(player).setChoiceIndex(-1);
            editor.prompt(player, EditorSessions.Prompt.PAGE_ID, "Type a page id (e.g. more, goodbye)");
            return;
        }
        if (slot < 9 || slot > 44) {
            return;
        }
        List<CustomNpc.DialoguePage> pages = new ArrayList<>(npc.pages().values());
        int index = slot - 9;
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
        if (slot == 49) {
            openTree(player, npc);
            return;
        }
        if (slot == 17) {
            editor.sessions().of(player).setPageId(page.id());
            editor.prompt(player, EditorSessions.Prompt.LINE, "Type a dialogue line");
            return;
        }
        if (slot == 35) {
            page.choices().add(new CustomNpc.DialogueChoice("…", DialogueAction.CLOSE, ""));
            editor.persistQuiet(npc);
            openPage(player, npc, page.id());
            return;
        }
        if (slot == 45) {
            npc.setStartPage(page.id());
            editor.persistQuiet(npc);
            openPage(player, npc, page.id());
            return;
        }
        if (slot == 47) {
            npc.removePage(page.id());
            editor.persistQuiet(npc);
            openTree(player, npc);
            return;
        }
        if (slot >= 10 && slot <= 16) {
            int index = slot - 10;
            if (index < page.lines().size() && event.isShiftClick()) {
                page.lines().remove(index);
                if (page.lines().isEmpty()) {
                    page.lines().add("…");
                }
                editor.persistQuiet(npc);
                openPage(player, npc, page.id());
            }
            return;
        }
        if (slot >= 28 && slot <= 34) {
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
        if (slot == 49) {
            openPage(player, npc, page.id());
            return;
        }
        if (slot == 4) {
            editor.prompt(player, EditorSessions.Prompt.CHOICE_TEXT, "Type the choice label the player sees");
            return;
        }
        if (slot == 20) {
            DialogueAction[] all = DialogueAction.values();
            int next = (choice.action().ordinal() + 1) % all.length;
            choice.setAction(all[next]);
            editor.persistQuiet(npc);
            openChoice(player, npc, page.id(), holder.choiceIndex());
            return;
        }
        if (slot == 22) {
            switch (choice.action()) {
                case PAGE -> editor.prompt(player, EditorSessions.Prompt.PAGE_ID, "Type the page id to open");
                case RUN_CONSOLE, RUN_PLAYER ->
                        editor.prompt(player, EditorSessions.Prompt.COMMAND, "Type the command (no /). {player} works.");
                case OFFER_QUEST, START_QUEST, TURN_IN_QUEST ->
                        editor.prompt(player, EditorSessions.Prompt.QUEST_ID, "Type an existing quest id");
                case CLOSE -> player.sendMessage("§7Close needs no target.");
            }
            return;
        }
        if (slot == 24) {
            page.choices().remove(holder.choiceIndex());
            editor.persistQuiet(npc);
            openPage(player, npc, page.id());
        }
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
        CHOICE
    }

    public record Holder(Kind kind, String npcId, String pageId, int choiceIndex) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
