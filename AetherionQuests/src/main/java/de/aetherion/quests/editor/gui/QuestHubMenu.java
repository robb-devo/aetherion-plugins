package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorScreen;
import de.aetherion.quests.editor.EditorSessions;
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
import java.util.Comparator;
import java.util.List;

public final class QuestHubMenu implements Listener {

    private static final int CREATE = 20;
    private static final int PICK = 22;
    private static final int AUTO = 24;
    private static final int OBJECTIVE = 29;
    private static final int REWARDS = 31;
    private static final int CLEAR = 33;
    private static final int PAGE_SIZE = 28;
    private static final int TYPE_ID = 45;
    private static final int PREV = 48;
    private static final int NEXT = 50;

    private final NpcEditor editor;

    public QuestHubMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        NpcEditor editor = playerEditor();
        Quest quest = linked(editor, npc);
        boolean editorQuest = editor != null && npc.hasLinkedQuest()
                && editor.editorQuests().isEditorQuest(npc.getLinkedQuestId());
        Inventory inventory = Bukkit.createInventory(
                new Holder(Kind.HOME, npc.getId(), 0),
                54,
                EditorItems.title(player, "npc_quest", "§8Quest")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.MAP,
                EditorItems.ui(player, "editor_quest_hub", "§aThis NPC's quest"),
                quest == null
                        ? EditorItems.ui(player, "editor_quest_none", "§7None yet — create or pick one")
                        : "§f" + quest.getTitle(),
                quest == null ? "§8" : "§8" + quest.getId(),
                npc.isAutoQuest()
                        ? EditorItems.ui(player, "editor_auto_on", "§7Players get it after talking.")
                        : EditorItems.ui(player, "editor_auto_off", "§7Only via a dialogue button.")
        ));
        inventory.setItem(18, EditorItems.section(
                EditorItems.ui(player, "editor_sec_job", "Job"),
                EditorItems.ui(player, "editor_sec_job_hint", "§7Create a new path or link an existing quest.")
        ));
        inventory.setItem(CREATE, EditorItems.button(
                Material.EMERALD,
                EditorItems.ui(player, "editor_quest_create", "§aCreate a job"),
                EditorItems.ui(player, "editor_quest_create_l1", "§7Name it in chat."),
                EditorItems.ui(player, "editor_quest_create_l2", "§7Starts as talk-to-this-NPC + coins/XP.")
        ));
        inventory.setItem(PICK, EditorItems.button(
                Material.BOOKSHELF,
                EditorItems.ui(player, "editor_quest_pick", "§ePick an existing quest"),
                EditorItems.ui(player, "editor_quest_pick_l1", "§7Story quests are link-only.")
        ));
        inventory.setItem(AUTO, EditorItems.button(
                npc.isAutoQuest() ? Material.LIME_DYE : Material.GRAY_DYE,
                npc.isAutoQuest()
                        ? EditorItems.ui(player, "editor_auto_title_on", "§aAfter they finish talking")
                        : EditorItems.ui(player, "editor_auto_title_off", "§7A dialogue button"),
                npc.isAutoQuest()
                        ? EditorItems.ui(player, "editor_auto_on", "§7Players get it after talking.")
                        : EditorItems.ui(player, "editor_auto_off", "§7Only via a dialogue button."),
                EditorItems.ui(player, "editor_auto_click", "§eClick to switch.")
        ));
        inventory.setItem(27, EditorItems.section(
                EditorItems.ui(player, "editor_sec_details", "Details"),
                editorQuest
                        ? EditorItems.ui(player, "editor_details_edit", "§7You can edit this job.")
                        : EditorItems.ui(player, "editor_details_story", "§7Story quests stay as written.")
        ));
        inventory.setItem(OBJECTIVE, objectiveButton(player, quest, editorQuest));
        inventory.setItem(REWARDS, rewardsButton(player, quest, editorQuest));
        inventory.setItem(CLEAR, EditorItems.button(
                Material.BARRIER,
                EditorItems.ui(player, "editor_quest_clear", "§cUnlink quest"),
                EditorItems.ui(player, "editor_quest_clear_hint", "§7NPC keeps talking. Job is not deleted.")
        ));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        player.openInventory(inventory);
    }

    public static void openPick(Player player, CustomNpc npc, int page) {
        NpcEditor editor = playerEditor();
        List<Quest> quests = listQuests(editor);
        int pages = Math.max(1, (int) Math.ceil(quests.size() / (double) PAGE_SIZE));
        int safe = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = Bukkit.createInventory(
                new Holder(Kind.PICK, npc.getId(), safe),
                54,
                EditorItems.title(player, "npc_quest_pick", "§8Pick a quest")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.BOOKSHELF,
                EditorItems.ui(player, "editor_quest_pick", "§ePick an existing quest"),
                npc.hasLinkedQuest()
                        ? "§7" + npc.getLinkedQuestId()
                        : EditorItems.ui(player, "editor_quest_none", "§7None yet — create or pick one")
        ));
        int start = safe * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            int slot = 10 + (i % 7) + (i / 7) * 9;
            if (index >= quests.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            Quest quest = quests.get(index);
            boolean linked = quest.getId().equalsIgnoreCase(npc.getLinkedQuestId());
            boolean editorOwned = editor != null && editor.editorQuests().isEditorQuest(quest.getId());
            inventory.setItem(slot, EditorItems.button(
                    linked ? Material.LIME_CONCRETE : (editorOwned ? Material.WRITABLE_BOOK : Material.PAPER),
                    (linked ? "§a" : "§e") + quest.getTitle(),
                    "§8" + quest.getId(),
                    editorOwned
                            ? EditorItems.ui(player, "editor_quest_yours", "§7Created in this editor")
                            : EditorItems.ui(player, "editor_quest_story", "§7Story quest · link only"),
                    linked
                            ? EditorItems.ui(player, "editor_linked", "§aLinked")
                            : EditorItems.ui(player, "editor_click_link", "§7Click to link")
            ));
        }
        inventory.setItem(TYPE_ID, EditorItems.button(
                Material.NAME_TAG,
                EditorItems.ui(player, "editor_type_id", "§eType a quest id"),
                EditorItems.ui(player, "editor_type_id_hint", "§7If you already know it.")
        ));
        inventory.setItem(PREV, EditorItems.button(Material.ARROW, EditorItems.ui(player, "editor_prev", "§7Previous")));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        inventory.setItem(NEXT, EditorItems.button(Material.ARROW, EditorItems.ui(player, "editor_next", "§7Next")));
        player.openInventory(inventory);
    }

    private static org.bukkit.inventory.ItemStack objectiveButton(Player player, Quest quest, boolean editorQuest) {
        if (quest == null) {
            return EditorItems.button(
                    Material.GRAY_DYE,
                    EditorItems.ui(player, "editor_objective", "§8The job"),
                    EditorItems.ui(player, "editor_create_first", "§7Create a job first.")
            );
        }
        Objective objective = quest.getObjectives().isEmpty() ? null : quest.getObjectives().get(0);
        String line = objective == null
                ? EditorItems.ui(player, "editor_objective_none", "§7Talk to this NPC")
                : "§7" + prettyType(objective) + " §f" + objective.getAmount() + "× " + objective.getTarget();
        return EditorItems.button(
                editorQuest ? Material.CHEST : Material.BOOK,
                EditorItems.ui(player, "editor_objective", "§bThe job"),
                line,
                editorQuest
                        ? EditorItems.ui(player, "editor_objective_edit", "§8Talk or gather")
                        : EditorItems.ui(player, "editor_read_only", "§8Story quest · read-only")
        );
    }

    private static org.bukkit.inventory.ItemStack rewardsButton(Player player, Quest quest, boolean editorQuest) {
        if (quest == null) {
            return EditorItems.button(
                    Material.GRAY_DYE,
                    EditorItems.ui(player, "editor_rewards", "§8Rewards"),
                    EditorItems.ui(player, "editor_create_first", "§7Create a job first.")
            );
        }
        List<Reward> rewards = quest.getRewards();
        String preview = rewards.isEmpty()
                ? EditorItems.ui(player, "editor_rewards_none", "§7None")
                : "§f" + rewards.get(0).getAmount() + " " + rewards.get(0).getName();
        return EditorItems.button(
                editorQuest ? Material.GOLD_INGOT : Material.GOLD_NUGGET,
                EditorItems.ui(player, "editor_rewards", "§6Rewards"),
                "§7" + rewards.size() + " · " + preview,
                editorQuest
                        ? EditorItems.ui(player, "editor_rewards_edit", "§8Coins, XP, items")
                        : EditorItems.ui(player, "editor_read_only", "§8Story quest · read-only")
        );
    }

    private static String prettyType(Objective objective) {
        return switch (objective.getType()) {
            case TALK -> "Talk";
            case COLLECT -> "Collect";
            case MINE -> "Mine";
            case HARVEST -> "Harvest";
            case DELIVER -> "Bring";
            case FISH -> "Fish";
            default -> objective.getType().name();
        };
    }

    private static List<Quest> listQuests(NpcEditor editor) {
        List<Quest> out = new ArrayList<>();
        if (editor == null || editor.plugin().getQuestManager() == null) {
            return out;
        }
        out.addAll(editor.plugin().getQuestManager().getQuests());
        out.sort(Comparator.comparing(quest -> quest.getTitle() == null ? quest.getId() : quest.getTitle()));
        return out;
    }

    private static Quest linked(NpcEditor editor, CustomNpc npc) {
        if (editor == null || npc == null || !npc.hasLinkedQuest() || editor.plugin().getQuestManager() == null) {
            return null;
        }
        return editor.plugin().getQuestManager().getQuest(npc.getLinkedQuestId());
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
        if (npc == null) {
            return;
        }
        if (holder.kind() == Kind.PICK) {
            clickPick(player, npc, holder, event.getRawSlot());
            return;
        }
        clickHome(player, npc, event.getRawSlot());
    }

    private void clickHome(Player player, CustomNpc npc, int slot) {
        if (slot == EditorItems.BACK) {
            EditMenu.open(player, npc);
            return;
        }
        if (slot == CREATE) {
            editor.sessions().of(player).setNpcId(npc.getId());
            editor.prompt(player, EditorSessions.Prompt.QUEST_TITLE, EditorScreen.QUEST_HUB,
                    EditorItems.ui(player, "editor_prompt_quest_title", "Type a short job title"),
                    null);
            return;
        }
        if (slot == PICK) {
            openPick(player, npc, 0);
            return;
        }
        if (slot == AUTO) {
            npc.setAutoQuest(!npc.isAutoQuest());
            editor.persistQuiet(npc);
            open(player, npc);
            return;
        }
        boolean editorQuest = npc.hasLinkedQuest() && editor.editorQuests().isEditorQuest(npc.getLinkedQuestId());
        if (slot == OBJECTIVE) {
            if (!editorQuest) {
                player.sendMessage(EditorItems.ui(player, "editor_read_only", "§8Story quest · read-only"));
                return;
            }
            ObjectiveMenu.open(player, npc);
            return;
        }
        if (slot == REWARDS) {
            if (!editorQuest) {
                player.sendMessage(EditorItems.ui(player, "editor_read_only", "§8Story quest · read-only"));
                return;
            }
            RewardsMenu.open(player, npc);
            return;
        }
        if (slot == CLEAR) {
            npc.setLinkedQuestId(null);
            editor.persistQuiet(npc);
            open(player, npc);
        }
    }

    private void clickPick(Player player, CustomNpc npc, Holder holder, int slot) {
        if (slot == EditorItems.BACK) {
            open(player, npc);
            return;
        }
        if (slot == PREV) {
            openPick(player, npc, holder.page() - 1);
            return;
        }
        if (slot == NEXT) {
            openPick(player, npc, holder.page() + 1);
            return;
        }
        if (slot == TYPE_ID) {
            editor.sessions().of(player).setNpcId(npc.getId());
            editor.sessions().of(player).setChoiceIndex(-1);
            editor.prompt(player, EditorSessions.Prompt.QUEST_ID, EditorScreen.QUEST_PICK,
                    EditorItems.ui(player, "editor_prompt_quest_id",
                            "Type a quest id, or cancel to keep the linked one"),
                    npc.getLinkedQuestId());
            return;
        }
        List<Quest> quests = listQuests(editor);
        int index = indexFromSlot(slot, holder.page());
        if (index < 0 || index >= quests.size()) {
            return;
        }
        Quest quest = quests.get(index);
        npc.setLinkedQuestId(quest.getId());
        editor.persistQuiet(npc);
        player.sendMessage("§a" + quest.getTitle());
        open(player, npc);
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

    enum Kind {
        HOME,
        PICK
    }

    public record Holder(Kind kind, String npcId, int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
