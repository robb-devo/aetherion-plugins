package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorQuestFactory;
import de.aetherion.quests.editor.EditorScreen;
import de.aetherion.quests.editor.EditorSessions;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

public final class RewardsMenu implements Listener {

    private static final int ADD_COINS = 19;
    private static final int ADD_XP = 20;
    private static final int ADD_ITEM = 21;
    private static final int TYPE = 22;
    private static final int LIST_START = 28;
    private static final int LIST_END = 43;

    private final NpcEditor editor;

    public RewardsMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        NpcEditor editor = playerEditor();
        Quest quest = editor == null ? null : editor.editorQuests().get(npc.getLinkedQuestId());
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId()),
                54,
                EditorItems.title(player, "npc_rewards", "§8Rewards")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.GOLD_INGOT,
                EditorItems.ui(player, "editor_rewards", "§6Rewards"),
                quest == null
                        ? EditorItems.ui(player, "editor_create_first", "§7Create a job first.")
                        : "§7" + quest.getTitle(),
                EditorItems.ui(player, "editor_rewards_how", "§7Left +amount · Right − · Shift-click a row to remove")
        ));
        inventory.setItem(18, EditorItems.section(
                EditorItems.ui(player, "editor_sec_add", "Add"),
                EditorItems.ui(player, "editor_sec_add_hint", "§7Same payout as story quests.")
        ));
        inventory.setItem(ADD_COINS, EditorItems.button(
                Material.GOLD_NUGGET,
                EditorItems.ui(player, "editor_add_coins", "§6Add Coins"),
                "§7+50"
        ));
        inventory.setItem(ADD_XP, EditorItems.button(
                Material.EXPERIENCE_BOTTLE,
                EditorItems.ui(player, "editor_add_xp", "§aAdd XP"),
                "§7+25 Aetherion XP"
        ));
        inventory.setItem(ADD_ITEM, EditorItems.button(
                Material.CHEST,
                EditorItems.ui(player, "editor_add_item", "§bAdd an item"),
                EditorItems.ui(player, "editor_add_item_hint", "§7Pick from the list.")
        ));
        inventory.setItem(TYPE, EditorItems.button(
                Material.NAME_TAG,
                EditorItems.ui(player, "editor_type_reward", "§eType an item id"),
                EditorItems.ui(player, "editor_type_reward_hint", "§7Coins, XP, or a vanilla item.")
        ));
        inventory.setItem(27, EditorItems.section(
                EditorItems.ui(player, "editor_sec_current", "Current"),
                EditorItems.ui(player, "editor_sec_current_hint", "§7Click to change the amount.")
        ));
        if (quest != null) {
            List<Reward> rewards = quest.getRewards();
            for (int i = 0; i < rewards.size() && LIST_START + i <= LIST_END; i++) {
                Reward reward = rewards.get(i);
                inventory.setItem(LIST_START + i, EditorItems.button(
                        iconFor(reward.getName()),
                        "§e" + reward.getAmount() + "× §f" + reward.getName(),
                        EditorItems.ui(player, "editor_reward_adjust", "§7Left + · Right −"),
                        EditorItems.ui(player, "editor_reward_remove", "§cShift-click to remove")
                ));
            }
        }
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        player.openInventory(inventory);
    }

    private static Material iconFor(String name) {
        if (name == null) {
            return Material.PAPER;
        }
        if (name.equalsIgnoreCase("Coins") || name.equalsIgnoreCase("Coin")) {
            return Material.GOLD_NUGGET;
        }
        if (EditorQuestFactory.isCurrencyReward(name)) {
            return Material.EXPERIENCE_BOTTLE;
        }
        Material material = Material.matchMaterial(name.replace(' ', '_'));
        if (material != null && material.isItem()) {
            return material;
        }
        return Material.CHEST;
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
        int slot = event.getRawSlot();
        if (slot == EditorItems.BACK) {
            QuestHubMenu.open(player, npc);
            return;
        }
        if (slot == ADD_COINS) {
            add(npc, quest, new Reward("Coins", 50));
            open(player, npc);
            return;
        }
        if (slot == ADD_XP) {
            add(npc, quest, new Reward("XP", 25));
            open(player, npc);
            return;
        }
        if (slot == ADD_ITEM) {
            GatherItemMenu.open(player, npc, 0, GatherItemMenu.Purpose.REWARD);
            return;
        }
        if (slot == TYPE) {
            editor.sessions().of(player).setNpcId(npc.getId());
            editor.prompt(player, EditorSessions.Prompt.REWARD_NAME, EditorScreen.REWARDS,
                    EditorItems.ui(player, "editor_prompt_reward", "Type Coins, XP, or a vanilla item id"),
                    null);
            return;
        }
        if (slot >= LIST_START && slot <= LIST_END) {
            int index = slot - LIST_START;
            List<Reward> rewards = new ArrayList<>(quest.getRewards());
            if (index < 0 || index >= rewards.size()) {
                return;
            }
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                rewards.remove(index);
                EditorQuestFactory.setRewards(quest, rewards);
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                open(player, npc);
                return;
            }
            int delta = event.isRightClick() ? -1 : 1;
            Reward old = rewards.get(index);
            rewards.set(index, new Reward(old.getName(),
                    EditorQuestFactory.bumpAmount(old.getName(), old.getAmount(), delta)));
            EditorQuestFactory.setRewards(quest, rewards);
            editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
            open(player, npc);
        }
    }

    private void add(CustomNpc npc, Quest quest, Reward reward) {
        List<Reward> rewards = new ArrayList<>(quest.getRewards());
        rewards.add(reward);
        EditorQuestFactory.setRewards(quest, rewards);
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
