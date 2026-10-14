package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorQuestFactory;
import de.aetherion.quests.editor.GatherItemCatalog;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;

import org.bukkit.Bukkit;
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

    private static final int PAGE_SIZE = 28;
    private static final int PREV = 45;
    private static final int NEXT = 53;

    private final NpcEditor editor;

    public GatherItemMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc, int page, Purpose purpose) {
        List<GatherItemCatalog.Entry> items = GatherItemCatalog.all();
        int pages = Math.max(1, (int) Math.ceil(items.size() / (double) PAGE_SIZE));
        int safe = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId(), safe, purpose),
                54,
                EditorItems.title(player, "npc_items", "§8Pick an item")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                org.bukkit.Material.ITEM_FRAME,
                EditorItems.ui(player, "editor_pick_item", "§aPick the item"),
                purpose == Purpose.REWARD
                        ? EditorItems.ui(player, "editor_pick_reward", "§7This becomes a quest reward.")
                        : EditorItems.ui(player, "editor_pick_objective", "§7This is what they gather.")
        ));
        int start = safe * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            int slot = 10 + (i % 7) + (i / 7) * 9;
            if (index >= items.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            GatherItemCatalog.Entry entry = items.get(index);
            inventory.setItem(slot, EditorItems.button(
                    entry.icon(),
                    "§e" + entry.label(),
                    "§8" + entry.id()
            ));
        }
        inventory.setItem(PREV, EditorItems.button(org.bukkit.Material.ARROW,
                EditorItems.ui(player, "editor_prev", "§7Previous")));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        inventory.setItem(NEXT, EditorItems.button(org.bukkit.Material.ARROW,
                EditorItems.ui(player, "editor_next", "§7Next")));
        player.openInventory(inventory);
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
            open(player, npc, holder.page() - 1, holder.purpose());
            return;
        }
        if (slot == NEXT) {
            open(player, npc, holder.page() + 1, holder.purpose());
            return;
        }
        List<GatherItemCatalog.Entry> items = GatherItemCatalog.all();
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

    private static int indexFromSlot(int slot, int page) {
        for (int i = 0; i < PAGE_SIZE; i++) {
            int candidate = 10 + (i % 7) + (i / 7) * 9;
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

    public record Holder(String npcId, int page, Purpose purpose) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
