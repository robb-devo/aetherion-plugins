package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorQuestFactory;
import de.aetherion.quests.editor.EditorSessions;
import de.aetherion.quests.editor.GatherItemCatalog;
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
    private static final int BACK = 49;
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
                "§6Quest rewards",
                quest == null ? "§cNo editor quest" : "§7" + quest.getTitle(),
                "§8Left +amount · Right − · Shift bigger",
                "§8Shift-left a row to remove"
        ));
        inventory.setItem(8, EditorItems.saved(false));
        inventory.setItem(18, EditorItems.section("Add", "§7Coins, XP, or an item."));
        inventory.setItem(ADD_COINS, EditorItems.button(Material.GOLD_NUGGET, "§6Add Coins", "§7+50 Coins", "§8Same payout as story quests"));
        inventory.setItem(ADD_XP, EditorItems.button(Material.EXPERIENCE_BOTTLE, "§aAdd XP", "§7+25 Aetherion XP"));
        inventory.setItem(ADD_ITEM, EditorItems.button(
                Material.CHEST,
                "§bAdd item",
                "§7Picker: custom + vanilla.",
                "§8Uses the quest reward name."
        ));
        inventory.setItem(TYPE, EditorItems.button(
                Material.NAME_TAG,
                "§eType item id",
                "§7If you know the id.",
                "§8Vanilla MATERIAL or custom id"
        ));
        inventory.setItem(27, EditorItems.section("Current", "§7Click to change amount."));
        if (quest != null) {
            List<Reward> rewards = quest.getRewards();
            for (int i = 0; i < rewards.size() && LIST_START + i <= LIST_END; i++) {
                Reward reward = rewards.get(i);
                inventory.setItem(LIST_START + i, EditorItems.button(
                        iconFor(reward.getName()),
                        "§e" + reward.getAmount() + "× §f" + reward.getName(),
                        "§7Left §a+ §7· Right §c−",
                        "§7Shift · bigger step",
                        "§cShift-left · remove"
                ));
            }
        }
        inventory.setItem(BACK, EditorItems.button(Material.ARROW, "§7Back"));
        player.openInventory(inventory);
    }

    private static Material iconFor(String name) {
        if (name == null) {
            return Material.PAPER;
        }
        if (name.equalsIgnoreCase("Coins") || name.equalsIgnoreCase("Coin")) {
            return Material.GOLD_NUGGET;
        }
        if (name.equalsIgnoreCase("XP") || name.equalsIgnoreCase("Exp") || name.equalsIgnoreCase("Experience")) {
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
        if (slot == ADD_COINS) {
            add(player, npc, quest, new Reward("Coins", 50));
            return;
        }
        if (slot == ADD_XP) {
            add(player, npc, quest, new Reward("XP", 25));
            return;
        }
        if (slot == ADD_ITEM) {
            GatherItemMenu.open(player, npc, 0, GatherItemMenu.Purpose.REWARD, GatherItemCatalog.Kind.CUSTOM);
            return;
        }
        if (slot == TYPE) {
            editor.sessions().of(player).setNpcId(npc.getId());
            editor.prompt(player, EditorSessions.Prompt.REWARD_NAME, "Type a reward name (Coins, XP, or item id)");
            return;
        }
        if (slot >= LIST_START && slot <= LIST_END) {
            int index = slot - LIST_START;
            List<Reward> rewards = new ArrayList<>(quest.getRewards());
            if (index < 0 || index >= rewards.size()) {
                return;
            }
            if (event.getClick() == ClickType.SHIFT_LEFT) {
                rewards.remove(index);
                EditorQuestFactory.setRewards(quest, rewards);
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                open(player, npc);
                return;
            }
            int delta = event.isRightClick() ? -1 : 1;
            if (event.isShiftClick()) {
                delta *= 10;
            }
            Reward old = rewards.get(index);
            rewards.set(index, new Reward(old.getName(), EditorQuestFactory.bumpAmount(old.getName(), old.getAmount(), delta)));
            EditorQuestFactory.setRewards(quest, rewards);
            editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
            open(player, npc);
        }
    }

    private void add(Player player, CustomNpc npc, Quest quest, Reward reward) {
        List<Reward> rewards = new ArrayList<>(quest.getRewards());
        for (int i = 0; i < rewards.size(); i++) {
            Reward existing = rewards.get(i);
            if (existing.getName().equalsIgnoreCase(reward.getName())) {
                rewards.set(i, new Reward(existing.getName(), existing.getAmount() + reward.getAmount()));
                EditorQuestFactory.setRewards(quest, rewards);
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                open(player, npc);
                return;
            }
        }
        rewards.add(reward);
        EditorQuestFactory.setRewards(quest, rewards);
        editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
        open(player, npc);
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
