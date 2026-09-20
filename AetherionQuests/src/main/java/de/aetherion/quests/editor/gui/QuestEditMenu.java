package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.model.Objective;
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

public final class QuestEditMenu implements Listener {

    private static final int OBJECTIVE = 20;
    private static final int REWARDS = 22;
    private static final int REQUIRE = 24;
    private static final int BACK = 49;

    private final NpcEditor editor;

    public QuestEditMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        NpcEditor editor = playerEditor();
        Quest quest = editor == null ? null : editor.editorQuests().get(npc.getLinkedQuestId());
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId()),
                54,
                EditorItems.title(player, "npc_quest_edit", "§8Edit Quest")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.MAP,
                quest == null ? "§cNo editor quest" : "§a" + quest.getTitle(),
                quest == null ? "§7Create a path first." : "§8" + quest.getId(),
                "§7Talk stub by default.",
                "§7Then rewards / gather / gates."
        ));
        inventory.setItem(8, EditorItems.saved(false));
        inventory.setItem(18, EditorItems.section("Quest path", "§7Saved in editor-quests.yml"));
        inventory.setItem(OBJECTIVE, EditorItems.button(
                Material.CHEST,
                "§bObjective",
                objectiveLine(quest),
                "§8Talk or gather — click an item"
        ));
        inventory.setItem(REWARDS, EditorItems.button(
                Material.GOLD_INGOT,
                "§6Rewards",
                quest == null ? "§7—" : "§7" + quest.getRewards().size() + " reward(s)",
                "§8Coins / XP / items"
        ));
        inventory.setItem(REQUIRE, EditorItems.button(
                Material.IRON_BARS,
                "§eRequirements",
                quest != null && quest.hasRequirement() ? "§aSet" : "§7Optional",
                "§8Prior quest / level / item"
        ));
        inventory.setItem(BACK, EditorItems.button(Material.ARROW, "§7Back"));
        player.openInventory(inventory);
    }

    private static String objectiveLine(Quest quest) {
        if (quest == null || quest.getObjectives().isEmpty()) {
            return "§7None";
        }
        Objective objective = quest.getObjectives().get(0);
        return "§7" + objective.getType().name() + " §f" + objective.getAmount() + "× " + objective.getTarget();
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
        if (npc == null) {
            return;
        }
        switch (event.getRawSlot()) {
            case OBJECTIVE -> ObjectiveMenu.open(player, npc);
            case REWARDS -> RewardsMenu.open(player, npc);
            case REQUIRE -> RequirementsMenu.open(player, npc);
            case BACK -> EditMenu.open(player, npc);
            default -> {
            }
        }
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
