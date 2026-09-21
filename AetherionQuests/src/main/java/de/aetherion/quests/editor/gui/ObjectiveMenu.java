package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorQuestFactory;
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
                EditorItems.title(player, "npc_objective", "§8The job")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.CHEST,
                EditorItems.ui(player, "editor_objective", "§bThe job"),
                objective == null
                        ? EditorItems.ui(player, "editor_objective_none", "§7Talk to this NPC")
                        : "§7" + objective.getType().name() + " · §f" + objective.getAmount() + "× " + objective.getTarget()
        ));
        inventory.setItem(18, EditorItems.section(
                EditorItems.ui(player, "editor_sec_type", "Type"),
                EditorItems.ui(player, "editor_sec_type_hint", "§7Talk is the simple path.")
        ));
        inventory.setItem(TALK, typeButton(player, ObjectiveType.TALK, Material.NAME_TAG, objective,
                EditorItems.ui(player, "editor_obj_talk", "Talk to this NPC")));
        inventory.setItem(COLLECT, typeButton(player, ObjectiveType.COLLECT, Material.HOPPER, objective,
                EditorItems.ui(player, "editor_obj_collect", "Pick up items")));
        inventory.setItem(MINE, typeButton(player, ObjectiveType.MINE, Material.IRON_PICKAXE, objective,
                EditorItems.ui(player, "editor_obj_mine", "Mine blocks")));
        inventory.setItem(HARVEST, typeButton(player, ObjectiveType.HARVEST, Material.IRON_HOE, objective,
                EditorItems.ui(player, "editor_obj_harvest", "Harvest crops")));
        inventory.setItem(DELIVER, typeButton(player, ObjectiveType.DELIVER, Material.CHEST, objective,
                EditorItems.ui(player, "editor_obj_deliver", "Bring items back here")));
        inventory.setItem(FISH, typeButton(player, ObjectiveType.FISH, Material.FISHING_ROD, objective,
                EditorItems.ui(player, "editor_obj_fish", "Catch fish")));
        inventory.setItem(27, EditorItems.section(
                EditorItems.ui(player, "editor_sec_target", "What"),
                EditorItems.ui(player, "editor_sec_target_hint", "§7Click an item — no typing ids.")
        ));
        boolean gather = objective != null && EditorQuestFactory.isGatherType(objective.getType());
        inventory.setItem(PICK, EditorItems.button(
                gather ? Material.ITEM_FRAME : Material.GRAY_DYE,
                gather
                        ? EditorItems.ui(player, "editor_pick_item", "§aPick the item")
                        : EditorItems.ui(player, "editor_pick_item_off", "§8Pick the item"),
                gather
                        ? (objective.getTarget() == null ? "§7—" : "§f" + objective.getTarget())
                        : EditorItems.ui(player, "editor_obj_talk", "Talk to this NPC")
        ));
        inventory.setItem(AMOUNT, EditorItems.button(
                Material.REPEATER,
                EditorItems.ui(player, "editor_amount", "§eAmount") + " §f" + (objective == null ? 1 : objective.getAmount()),
                EditorItems.ui(player, "editor_amount_hint", "§7Left +1 · Right −1 · Shift ±8")
        ));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        player.openInventory(inventory);
    }

    private static org.bukkit.inventory.ItemStack typeButton(
            Player player,
            ObjectiveType type,
            Material icon,
            Objective current,
            String hint
    ) {
        boolean selected = current != null && current.getType() == type;
        return EditorItems.button(
                icon,
                (selected ? "§a" : "§e") + hint,
                selected
                        ? EditorItems.ui(player, "editor_selected", "§aSelected")
                        : EditorItems.ui(player, "editor_preset_click", "§7Click to apply")
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
        Player player = EditorItems.editorClick(event);
        if (player == null) {
            return;
        }
        CustomNpc npc = editor.storage().get(holder.npcId());
        Quest quest = npc == null ? null : editor.editorQuests().get(npc.getLinkedQuestId());
        if (npc == null || quest == null) {
            if (npc != null) {
                QuestHubMenu.open(player, npc);
            }
            return;
        }
        Objective current = quest.getObjectives().isEmpty() ? null : quest.getObjectives().get(0);
        int slot = event.getRawSlot();
        if (slot == EditorItems.BACK) {
            QuestHubMenu.open(player, npc);
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
                GatherItemMenu.open(player, npc, 0, GatherItemMenu.Purpose.OBJECTIVE);
                return;
            }
            open(player, npc);
            return;
        }
        if (slot == PICK) {
            if (current == null || !EditorQuestFactory.isGatherType(current.getType())) {
                player.sendMessage(EditorItems.ui(player, "editor_pick_need_type",
                        "§ePick Collect / Mine / Harvest / Bring / Fish first."));
                return;
            }
            GatherItemMenu.open(player, npc, 0, GatherItemMenu.Purpose.OBJECTIVE);
            return;
        }
        if (slot == AMOUNT && current != null) {
            int delta = event.isRightClick() ? -1 : 1;
            if (event.isShiftClick()) {
                delta *= 8;
            }
            int next = Math.max(1, Math.min(10_000, current.getAmount() + delta));
            EditorQuestFactory.setObjective(quest, current.getType(), current.getTarget(), next);
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
