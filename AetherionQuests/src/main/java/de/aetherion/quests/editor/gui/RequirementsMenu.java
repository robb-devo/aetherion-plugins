package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorSessions;
import de.aetherion.quests.editor.GatherItemCatalog;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.model.Quest;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class RequirementsMenu implements Listener {

    private static final int LEVEL = 19;
    private static final int PRIOR = 21;
    private static final int ITEM = 23;
    private static final int CLEAR = 25;
    private static final int BACK = 49;

    private final NpcEditor editor;

    public RequirementsMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        NpcEditor editor = playerEditor();
        Quest quest = editor == null ? null : editor.editorQuests().get(npc.getLinkedQuestId());
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId()),
                54,
                EditorItems.title(player, "npc_requirements", "§8Requirements")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.IRON_BARS,
                "§eOptional gates",
                "§7Checked on accept — same as story.",
                "§8Prior quest · Aetherion level · item"
        ));
        inventory.setItem(8, EditorItems.saved(false));
        inventory.setItem(18, EditorItems.section("Gates", "§7Empty = no gate."));
        int level = quest == null ? 0 : quest.getRequiredAccountLevel();
        inventory.setItem(LEVEL, EditorItems.button(
                Material.EXPERIENCE_BOTTLE,
                "§aAetherion level",
                level > 0 ? "§f" + level : "§7None",
                "§7Left +1 · Right −1 · Shift ±5",
                "§8Or click middle lore to type"
        ));
        inventory.setItem(PRIOR, EditorItems.button(
                Material.MAP,
                "§6Prior quest",
                quest != null && quest.hasPriorQuestRequirement()
                        ? "§f" + quest.getRequiredPriorQuestId()
                        : "§7None",
                "§7Click · type a quest id",
                "§cShift-click · clear"
        ));
        inventory.setItem(ITEM, EditorItems.button(
                Material.CHEST,
                "§bRequired item",
                quest != null && quest.hasItemRequirement()
                        ? "§f" + quest.getRequiredItemAmount() + "× " + quest.getRequiredItemId()
                        : "§7None",
                "§7Click · pick an item",
                "§cShift-click · clear"
        ));
        inventory.setItem(CLEAR, EditorItems.button(Material.BARRIER, "§cClear all gates"));
        inventory.setItem(BACK, EditorItems.button(Material.ARROW, "§7Back"));
        player.openInventory(inventory);
    }

    private static NpcEditor playerEditor() {
        var plugin = de.aetherion.quests.AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getNpcEditor();
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
        Quest quest = npc == null ? null : editor.editorQuests().get(npc.getLinkedQuestId());
        if (npc == null || quest == null) {
            if (npc != null) {
                EditMenu.open(player, npc);
            }
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK) {
            QuestEditMenu.open(player, npc);
            return;
        }
        if (slot == LEVEL) {
            if (event.isShiftClick() && event.isRightClick()) {
                editor.sessions().of(player).setNpcId(npc.getId());
                editor.prompt(player, EditorSessions.Prompt.REQUIRE_LEVEL, "Type the minimum Aetherion level (0 = none)");
                return;
            }
            int delta = event.isRightClick() ? -1 : 1;
            if (event.isShiftClick()) {
                delta *= 5;
            }
            quest.requireAccountLevel(Math.max(0, quest.getRequiredAccountLevel() + delta));
            persist(quest);
            open(player, npc);
            return;
        }
        if (slot == PRIOR) {
            if (event.isShiftClick()) {
                quest.requirePriorQuest(null);
                persist(quest);
                open(player, npc);
                return;
            }
            editor.sessions().of(player).setNpcId(npc.getId());
            editor.prompt(player, EditorSessions.Prompt.REQUIRE_PRIOR, "Type the prior quest id (must be completed)");
            return;
        }
        if (slot == ITEM) {
            if (event.isShiftClick()) {
                quest.clearItemRequirement();
                persist(quest);
                open(player, npc);
                return;
            }
            GatherItemMenu.open(player, npc, 0, GatherItemMenu.Purpose.REQUIRE_ITEM, GatherItemCatalog.Kind.CUSTOM);
            return;
        }
        if (slot == CLEAR) {
            quest.requireAccountLevel(0);
            quest.requirePriorQuest(null);
            quest.clearItemRequirement();
            persist(quest);
            open(player, npc);
        }
    }

    private void persist(Quest quest) {
        editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public record Holder(String npcId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
