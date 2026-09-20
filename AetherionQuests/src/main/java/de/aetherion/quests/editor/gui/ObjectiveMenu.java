package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorQuestFactory;
import de.aetherion.quests.editor.EditorSessions;
import de.aetherion.quests.editor.GatherItemCatalog;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
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

public final class ObjectiveMenu implements Listener {

    private static final int TALK = 19;
    private static final int COLLECT = 20;
    private static final int MINE = 21;
    private static final int HARVEST = 22;
    private static final int DELIVER = 23;
    private static final int FISH = 24;
    private static final int PICK = 29;
    private static final int AMOUNT = 31;
    private static final int BACK = 49;

    private final NpcEditor editor;

    public ObjectiveMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        NpcEditor editor = playerEditor();
        Quest quest = editor == null ? null : editor.editorQuests().get(npc.getLinkedQuestId());
        Objective objective = quest == null || quest.getObjectives().isEmpty() ? null : quest.getObjectives().get(0);
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId()),
                54,
                EditorItems.title(player, "npc_objective", "§8Objective")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.CHEST,
                "§bQuest objective",
                objective == null ? "§7None" : "§7" + objective.getType().name(),
                objective == null ? "§8Pick a type" : "§f" + objective.getAmount() + "× " + objective.getTarget()
        ));
        inventory.setItem(8, EditorItems.saved(false));
        inventory.setItem(18, EditorItems.section("Type", "§7Talk or gather."));
        inventory.setItem(TALK, typeButton(ObjectiveType.TALK, Material.NAME_TAG, objective, "Talk to this NPC"));
        inventory.setItem(COLLECT, typeButton(ObjectiveType.COLLECT, Material.HOPPER, objective, "Pick up items"));
        inventory.setItem(MINE, typeButton(ObjectiveType.MINE, Material.IRON_PICKAXE, objective, "Break / mine blocks"));
        inventory.setItem(HARVEST, typeButton(ObjectiveType.HARVEST, Material.IRON_HOE, objective, "Harvest crops"));
        inventory.setItem(DELIVER, typeButton(ObjectiveType.DELIVER, Material.CHEST, objective, "Bring items to this NPC"));
        inventory.setItem(FISH, typeButton(ObjectiveType.FISH, Material.FISHING_ROD, objective, "Catch fish"));
        inventory.setItem(27, EditorItems.section("Target", "§7Click an item — no IDs."));
        boolean gather = objective != null && EditorQuestFactory.isGatherType(objective.getType());
        inventory.setItem(PICK, EditorItems.button(
                gather ? Material.ITEM_FRAME : Material.GRAY_DYE,
                gather ? "§aPick gather item" : "§8Pick gather item",
                gather ? (objective.getTarget() == null ? "§7None yet" : "§f" + objective.getTarget()) : "§7Talk uses this NPC.",
                "§8Aetherion items + vanilla"
        ));
        inventory.setItem(AMOUNT, EditorItems.button(
                Material.REPEATER,
                "§eAmount §f" + (objective == null ? 1 : objective.getAmount()),
                "§7Left +1 · Right −1 · Shift ±8",
                "§8Shift-right · type in chat"
        ));
        inventory.setItem(BACK, EditorItems.button(Material.ARROW, "§7Back"));
        player.openInventory(inventory);
    }

    private static org.bukkit.inventory.ItemStack typeButton(
            ObjectiveType type,
            Material icon,
            Objective current,
            String hint
    ) {
        boolean selected = current != null && current.getType() == type;
        return EditorItems.button(
                icon,
                (selected ? "§a" : "§e") + type.name(),
                "§7" + hint,
                selected ? "§aSelected" : "§7Click to use"
        );
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
        Objective current = quest.getObjectives().isEmpty() ? null : quest.getObjectives().get(0);
        int slot = event.getRawSlot();
        if (slot == BACK) {
            QuestEditMenu.open(player, npc);
            return;
        }
        ObjectiveType picked = switch (slot) {
            case TALK -> ObjectiveType.TALK;
            case COLLECT -> ObjectiveType.COLLECT;
            case MINE -> ObjectiveType.MINE;
            case HARVEST -> ObjectiveType.HARVEST;
            case DELIVER -> ObjectiveType.DELIVER;
            case FISH -> ObjectiveType.FISH;
            default -> null;
        };
        if (picked != null) {
            String target = picked == ObjectiveType.TALK
                    ? npc.getId()
                    : (current != null && EditorQuestFactory.isGatherType(current.getType())
                    ? current.getTarget()
                    : "");
            int amount = picked == ObjectiveType.TALK ? 1 : (current == null ? 10 : Math.max(1, current.getAmount()));
            EditorQuestFactory.setObjective(quest, picked, target, amount);
            persist(quest);
            if (EditorQuestFactory.isGatherType(picked) && (target == null || target.isBlank())) {
                GatherItemMenu.open(player, npc, 0, GatherItemMenu.Purpose.OBJECTIVE, GatherItemCatalog.Kind.CUSTOM);
                return;
            }
            open(player, npc);
            return;
        }
        if (slot == PICK) {
            if (current == null || !EditorQuestFactory.isGatherType(current.getType())) {
                player.sendMessage("§ePick a gather type first (Collect / Mine / Harvest / Deliver / Fish).");
                return;
            }
            GatherItemMenu.open(player, npc, 0, GatherItemMenu.Purpose.OBJECTIVE, GatherItemCatalog.Kind.CUSTOM);
            return;
        }
        if (slot == AMOUNT) {
            if (event.isShiftClick() && event.isRightClick()) {
                editor.sessions().of(player).setNpcId(npc.getId());
                editor.prompt(player, EditorSessions.Prompt.OBJECTIVE_AMOUNT, "Type the objective amount");
                return;
            }
            int amount = current == null ? 1 : current.getAmount();
            int delta = event.isRightClick() ? -1 : 1;
            if (event.isShiftClick()) {
                delta *= 8;
            }
            ObjectiveType type = current == null ? ObjectiveType.TALK : current.getType();
            String target = current == null ? npc.getId() : current.getTarget();
            EditorQuestFactory.setObjective(quest, type, target, Math.max(1, amount + delta));
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
