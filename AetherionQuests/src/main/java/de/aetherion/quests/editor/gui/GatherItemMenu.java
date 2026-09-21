package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorQuestFactory;
import de.aetherion.quests.editor.EditorScreen;
import de.aetherion.quests.editor.EditorSessions;
import de.aetherion.quests.editor.GatherItemCatalog;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;

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

public final class GatherItemMenu implements Listener {

    public enum Purpose {
        OBJECTIVE,
        REWARD
    }

    private static final int SEARCH = 8;
    private static final int PAGE_SIZE = 21;
    private static final int PREV = 45;
    private static final int CLEAR = 47;
    private static final int NEXT = 53;
    private static final GatherItemCatalog.Kind[] TABS = {
            GatherItemCatalog.Kind.ALL,
            GatherItemCatalog.Kind.MEAT,
            GatherItemCatalog.Kind.FISH,
            GatherItemCatalog.Kind.CROP,
            GatherItemCatalog.Kind.FOOD,
            GatherItemCatalog.Kind.LOG,
            GatherItemCatalog.Kind.ORE,
            GatherItemCatalog.Kind.DROP
    };

    private final NpcEditor editor;

    public GatherItemMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc, int page, Purpose purpose) {
        open(player, npc, page, purpose, GatherItemCatalog.Kind.ALL, null);
    }

    public static void open(
            Player player,
            CustomNpc npc,
            int page,
            Purpose purpose,
            GatherItemCatalog.Kind kind,
            String query
    ) {
        GatherItemCatalog.Kind safeKind = kind == null ? GatherItemCatalog.Kind.ALL : kind;
        String filter = query == null ? "" : query.trim();
        List<GatherItemCatalog.Entry> items = GatherItemCatalog.find(safeKind, filter);
        int pages = Math.max(1, (int) Math.ceil(items.size() / (double) PAGE_SIZE));
        int safe = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId(), safe, purpose, safeKind, filter),
                54,
                EditorItems.title(player, "npc_items", "§8Pick an item")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.ITEM_FRAME,
                EditorItems.ui(player, "editor_pick_item", "§aPick the item"),
                purpose == Purpose.REWARD
                        ? EditorItems.ui(player, "editor_pick_reward", "§7This becomes a quest reward.")
                        : EditorItems.ui(player, "editor_pick_objective", "§7This is what they gather."),
                filter.isBlank()
                        ? EditorItems.ui(player, "editor_pick_tabs", "§8Meats, fish, crops… or search.")
                        : "§e" + filter
        ));
        inventory.setItem(SEARCH, EditorItems.button(
                Material.NAME_TAG,
                EditorItems.ui(player, "editor_search", "§eSearch"),
                filter.isBlank()
                        ? EditorItems.ui(player, "editor_search_hint", "§7Type beef, pork, meat…")
                        : EditorItems.ui(player, "editor_search_current", "§7Now: §f") + filter,
                EditorItems.ui(player, "editor_search_clear_hint", "§8cancel keeps this list")
        ));
        for (int i = 0; i < TABS.length; i++) {
            GatherItemCatalog.Kind tab = TABS[i];
            boolean selected = tab == safeKind && filter.isBlank();
            inventory.setItem(10 + i, EditorItems.button(
                    selected ? Material.LIME_STAINED_GLASS_PANE : tab.icon(),
                    (selected ? "§a" : "§e") + EditorItems.ui(player, tab.langKey(), tab.label()),
                    selected
                            ? EditorItems.ui(player, "editor_selected", "§aSelected")
                            : EditorItems.ui(player, "editor_preset_click", "§7Click to apply")
            ));
        }
        int start = safe * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            int slot = 19 + (i % 7) + (i / 7) * 9;
            if (index >= items.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            GatherItemCatalog.Entry entry = items.get(index);
            inventory.setItem(slot, EditorItems.button(
                    entry.icon(),
                    "§e" + entry.label(),
                    "§8" + entry.id(),
                    "§7" + EditorItems.ui(player, entry.kind().langKey(), entry.kind().label())
            ));
        }
        if (items.isEmpty()) {
            inventory.setItem(31, EditorItems.button(
                    Material.BARRIER,
                    EditorItems.ui(player, "editor_search_empty", "§cNothing matches"),
                    EditorItems.ui(player, "editor_search_empty_hint", "§7Try meat, beef, or Meats.")
            ));
        }
        inventory.setItem(PREV, EditorItems.button(Material.ARROW,
                EditorItems.ui(player, "editor_prev", "§7Previous"),
                "§8" + (safe + 1) + "/" + pages));
        if (!filter.isBlank()) {
            inventory.setItem(CLEAR, EditorItems.button(
                    Material.STRUCTURE_VOID,
                    EditorItems.ui(player, "editor_search_clear", "§eClear search")
            ));
        }
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        inventory.setItem(NEXT, EditorItems.button(Material.ARROW,
                EditorItems.ui(player, "editor_next", "§7Next"),
                "§8" + (safe + 1) + "/" + pages));
        player.openInventory(inventory);
        NpcEditor live = playerEditor();
        if (live != null) {
            EditorSessions.Session session = live.sessions().of(player);
            session.setNpcId(npc.getId());
            session.setListPage(safe);
            session.setGatherPurpose(purpose.name());
            session.setGatherKind(safeKind.name());
            session.setItemFilter(filter);
            session.setReturnTo(EditorScreen.GATHER);
        }
    }

    public static void reopen(Player player, CustomNpc npc, EditorSessions.Session session) {
        Purpose purpose = Purpose.REWARD.name().equals(session.gatherPurpose())
                ? Purpose.REWARD
                : Purpose.OBJECTIVE;
        open(
                player,
                npc,
                session.listPage(),
                purpose,
                GatherItemCatalog.Kind.parse(session.gatherKind()),
                session.itemFilter()
        );
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
        remember(player, holder);
        int slot = event.getRawSlot();
        if (slot == EditorItems.BACK) {
            if (holder.purpose() == Purpose.REWARD) {
                RewardsMenu.open(player, npc);
            } else {
                ObjectiveMenu.open(player, npc);
            }
            return;
        }
        if (slot == PREV) {
            open(player, npc, holder.page() - 1, holder.purpose(), holder.kind(), holder.query());
            return;
        }
        if (slot == NEXT) {
            open(player, npc, holder.page() + 1, holder.purpose(), holder.kind(), holder.query());
            return;
        }
        if (slot == CLEAR) {
            open(player, npc, 0, holder.purpose(), holder.kind(), null);
            return;
        }
        if (slot == SEARCH) {
            editor.prompt(player, EditorSessions.Prompt.ITEM_SEARCH, EditorScreen.GATHER,
                    EditorItems.ui(player, "editor_prompt_search", "Type what to find (beef, pork, meat…)"),
                    holder.query());
            return;
        }
        int tabIndex = slot - 10;
        if (tabIndex >= 0 && tabIndex < TABS.length) {
            open(player, npc, 0, holder.purpose(), TABS[tabIndex], null);
            return;
        }
        List<GatherItemCatalog.Entry> items = GatherItemCatalog.find(holder.kind(), holder.query());
        int index = indexFromSlot(slot, holder.page());
        if (index < 0 || index >= items.size()) {
            return;
        }
        GatherItemCatalog.Entry entry = items.get(index);
        Quest quest = editor.editorQuests().get(npc.getLinkedQuestId());
        if (quest == null) {
            QuestHubMenu.open(player, npc);
            return;
        }
        if (holder.purpose() == Purpose.REWARD) {
            List<Reward> rewards = new ArrayList<>(quest.getRewards());
            rewards.add(new Reward(entry.id(), 1));
            EditorQuestFactory.setRewards(quest, rewards);
            editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
            RewardsMenu.open(player, npc);
            return;
        }
        Objective current = quest.getObjectives().isEmpty() ? null : quest.getObjectives().get(0);
        EditorQuestFactory.setObjective(
                quest,
                current == null ? de.aetherion.quests.model.ObjectiveType.COLLECT : current.getType(),
                entry.id(),
                current == null || current.getAmount() <= 1 ? 10 : current.getAmount()
        );
        editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
        ObjectiveMenu.open(player, npc);
    }

    private void remember(Player player, Holder holder) {
        EditorSessions.Session session = editor.sessions().of(player);
        session.setNpcId(holder.npcId());
        session.setListPage(holder.page());
        session.setGatherPurpose(holder.purpose().name());
        session.setGatherKind(holder.kind().name());
        session.setItemFilter(holder.query());
        session.setReturnTo(EditorScreen.GATHER);
    }

    private static NpcEditor playerEditor() {
        var plugin = de.aetherion.quests.AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getNpcEditor();
    }

    private static int indexFromSlot(int slot, int page) {
        for (int i = 0; i < PAGE_SIZE; i++) {
            int candidate = 19 + (i % 7) + (i / 7) * 9;
            if (candidate == slot) {
                return page * PAGE_SIZE + i;
            }
        }
        return -1;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public record Holder(
            String npcId,
            int page,
            Purpose purpose,
            GatherItemCatalog.Kind kind,
            String query
    ) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
