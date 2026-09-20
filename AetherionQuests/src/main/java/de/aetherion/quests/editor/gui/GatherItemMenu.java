package de.aetherion.quests.editor.gui;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ItemFactoryAccess;
import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorQuestFactory;
import de.aetherion.quests.editor.GatherItemCatalog;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class GatherItemMenu implements Listener {

    public enum Purpose {
        OBJECTIVE,
        REQUIRE_ITEM,
        REWARD
    }

    private static final int PAGE_SIZE = 36;
    private static final int TAB_CUSTOM = 2;
    private static final int TAB_VANILLA = 3;
    private static final int TAB_ALL = 4;
    private static final int PREV = 48;
    private static final int BACK = 49;
    private static final int NEXT = 50;

    private final NpcEditor editor;

    public GatherItemMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc, int page, Purpose purpose, GatherItemCatalog.Kind kind) {
        List<GatherItemCatalog.Entry> items = GatherItemCatalog.filtered(kind);
        int pages = Math.max(1, (int) Math.ceil(items.size() / (double) PAGE_SIZE));
        int safe = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId(), safe, purpose, kind),
                54,
                EditorItems.title(player, "npc_gather", "§8Pick Item")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.ITEM_FRAME,
                "§bPick an item",
                purposeLabel(purpose),
                "§7" + items.size() + " in this tab",
                "§8Click — no typing IDs"
        ));
        inventory.setItem(TAB_CUSTOM, tab(kind == GatherItemCatalog.Kind.CUSTOM, Material.PAPER, "§dAetherion items"));
        inventory.setItem(TAB_VANILLA, tab(kind == GatherItemCatalog.Kind.VANILLA, Material.OAK_LOG, "§aVanilla"));
        inventory.setItem(TAB_ALL, tab(kind == null, Material.CHEST, "§fAll"));
        int start = safe * PAGE_SIZE;
        ItemFactoryAccess factory = AetherServices.items();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            int slot = 9 + i;
            if (index >= items.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            GatherItemCatalog.Entry entry = items.get(index);
            inventory.setItem(slot, icon(factory, entry));
        }
        inventory.setItem(PREV, EditorItems.button(Material.ARROW, "§7Previous", "§8Page " + (safe + 1) + "/" + pages));
        inventory.setItem(BACK, EditorItems.button(Material.ARROW, "§7Back"));
        inventory.setItem(NEXT, EditorItems.button(Material.ARROW, "§7Next", "§8Page " + (safe + 1) + "/" + pages));
        player.openInventory(inventory);
    }

    private static org.bukkit.inventory.ItemStack tab(boolean selected, Material icon, String name) {
        return EditorItems.button(
                selected ? Material.LIME_CONCRETE : icon,
                name,
                selected ? "§aSelected" : "§7Click to filter"
        );
    }

    private static String purposeLabel(Purpose purpose) {
        return switch (purpose) {
            case OBJECTIVE -> "§7Set gather / deliver target";
            case REQUIRE_ITEM -> "§7Required item to accept";
            case REWARD -> "§7Add as a quest reward";
        };
    }

    private static ItemStack icon(ItemFactoryAccess factory, GatherItemCatalog.Entry entry) {
        if (factory != null && entry.kind() == GatherItemCatalog.Kind.CUSTOM) {
            ItemStack created = factory.create(entry.id());
            if (created != null) {
                created.setAmount(1);
                var meta = created.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
                    lore.add("§8" + entry.id());
                    lore.add("§eClick to select");
                    meta.setLore(lore);
                    created.setItemMeta(meta);
                }
                return created;
            }
        }
        return EditorItems.button(
                entry.icon() == null ? Material.PAPER : entry.icon(),
                (entry.kind() == GatherItemCatalog.Kind.CUSTOM ? "§d" : "§a") + entry.label(),
                "§8" + entry.id(),
                entry.kind() == GatherItemCatalog.Kind.CUSTOM ? "§7Aetherion item" : "§7Vanilla",
                "§eClick to select"
        );
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
        int slot = event.getRawSlot();
        if (slot == BACK) {
            reopenParent(player, npc, holder.purpose());
            return;
        }
        if (slot == PREV) {
            open(player, npc, holder.page() - 1, holder.purpose(), holder.kind());
            return;
        }
        if (slot == NEXT) {
            open(player, npc, holder.page() + 1, holder.purpose(), holder.kind());
            return;
        }
        if (slot == TAB_CUSTOM) {
            open(player, npc, 0, holder.purpose(), GatherItemCatalog.Kind.CUSTOM);
            return;
        }
        if (slot == TAB_VANILLA) {
            open(player, npc, 0, holder.purpose(), GatherItemCatalog.Kind.VANILLA);
            return;
        }
        if (slot == TAB_ALL) {
            open(player, npc, 0, holder.purpose(), null);
            return;
        }
        if (slot < 9 || slot > 44) {
            return;
        }
        List<GatherItemCatalog.Entry> items = GatherItemCatalog.filtered(holder.kind());
        int index = holder.page() * PAGE_SIZE + (slot - 9);
        if (index < 0 || index >= items.size()) {
            return;
        }
        apply(player, npc, holder.purpose(), items.get(index));
    }

    private void apply(Player player, CustomNpc npc, Purpose purpose, GatherItemCatalog.Entry entry) {
        Quest quest = editor.editorQuests().get(npc.getLinkedQuestId());
        if (quest == null) {
            player.sendMessage("§cNo editor quest on this NPC.");
            EditMenu.open(player, npc);
            return;
        }
        switch (purpose) {
            case OBJECTIVE -> {
                Objective current = quest.getObjectives().isEmpty() ? null : quest.getObjectives().get(0);
                ObjectiveType type = current != null && EditorQuestFactory.isGatherType(current.getType())
                        ? current.getType()
                        : ObjectiveType.COLLECT;
                int amount = current == null ? 10 : current.getAmount();
                EditorQuestFactory.setObjective(quest, type, entry.id(), amount);
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                player.sendMessage("§aGather target §f" + entry.label());
                ObjectiveMenu.open(player, npc);
            }
            case REQUIRE_ITEM -> {
                int amount = quest.hasItemRequirement() ? quest.getRequiredItemAmount() : 1;
                quest.requireItem(entry.id(), amount);
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                player.sendMessage("§aNeed §f" + amount + "× " + entry.label() + " §ato accept.");
                RequirementsMenu.open(player, npc);
            }
            case REWARD -> {
                List<Reward> rewards = new ArrayList<>(quest.getRewards());
                rewards.add(new Reward(entry.id(), 1));
                EditorQuestFactory.setRewards(quest, rewards);
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                player.sendMessage("§aAdded reward §f" + entry.label());
                RewardsMenu.open(player, npc);
            }
        }
    }

    private static void reopenParent(Player player, CustomNpc npc, Purpose purpose) {
        switch (purpose) {
            case OBJECTIVE -> ObjectiveMenu.open(player, npc);
            case REQUIRE_ITEM -> RequirementsMenu.open(player, npc);
            case REWARD -> RewardsMenu.open(player, npc);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public record Holder(String npcId, int page, Purpose purpose, GatherItemCatalog.Kind kind) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
