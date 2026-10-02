package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorSessions;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QuestLinkMenu implements Listener {

    private static final int PAGE_SIZE = 36;
    private static final int CLEAR = 45;
    private static final int TYPE = 47;
    private static final int PREV = 48;
    private static final int BACK = 49;
    private static final int NEXT = 50;

    private final NpcEditor editor;

    public QuestLinkMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc, int page) {
        NpcEditor editor = playerEditor();
        List<Quest> quests = listQuests(editor);
        int pages = Math.max(1, (int) Math.ceil(quests.size() / (double) PAGE_SIZE));
        int safe = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId(), safe),
                54,
                EditorItems.title(player, "npc_quest", "§8Link Quest")
        );
        EditorItems.fill(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.MAP,
                "§aLink an existing quest",
                npc.hasLinkedQuest() ? "§7Current §f" + npc.getLinkedQuestId() : "§7None linked",
                "§7Offer/start/turn-in from dialogue.",
                "§8No visual quest designer in this tool."
        ));
        int start = safe * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            int slot = 9 + i;
            if (index >= quests.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            Quest quest = quests.get(index);
            boolean linked = quest.getId().equalsIgnoreCase(npc.getLinkedQuestId());
            inventory.setItem(slot, EditorItems.button(
                    linked ? Material.LIME_CONCRETE : Material.PAPER,
                    (linked ? "§a" : "§e") + quest.getTitle(),
                    "§8" + quest.getId(),
                    linked ? "§aLinked" : "§7Click to link"
            ));
        }
        inventory.setItem(CLEAR, EditorItems.button(Material.BARRIER, "§cClear link"));
        inventory.setItem(TYPE, EditorItems.button(Material.NAME_TAG, "§eType quest id", "§7If you know the id."));
        inventory.setItem(PREV, EditorItems.button(Material.ARROW, "§7Previous"));
        inventory.setItem(BACK, EditorItems.button(Material.ARROW, "§7Back"));
        inventory.setItem(NEXT, EditorItems.button(Material.ARROW, "§7Next"));
        player.openInventory(inventory);
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
        int slot = event.getRawSlot();
        if (slot == BACK) {
            EditMenu.open(player, npc);
            return;
        }
        if (slot == PREV) {
            open(player, npc, holder.page() - 1);
            return;
        }
        if (slot == NEXT) {
            open(player, npc, holder.page() + 1);
            return;
        }
        if (slot == CLEAR) {
            npc.setLinkedQuestId(null);
            editor.persistQuiet(npc);
            open(player, npc, holder.page());
            return;
        }
        if (slot == TYPE) {
            editor.sessions().of(player).setNpcId(npc.getId());
            editor.sessions().of(player).setChoiceIndex(-1);
            editor.prompt(player, EditorSessions.Prompt.QUEST_ID, "Type an existing quest id");
            return;
        }
        if (slot < 9 || slot > 44) {
            return;
        }
        List<Quest> quests = listQuests(editor);
        int index = holder.page() * PAGE_SIZE + (slot - 9);
        if (index < 0 || index >= quests.size()) {
            return;
        }
        Quest quest = quests.get(index);
        npc.setLinkedQuestId(quest.getId());
        editor.persistQuiet(npc);
        player.sendMessage("§aLinked §f" + quest.getTitle() + " §7(§f" + quest.getId() + "§7).");
        player.sendMessage("§7Empty-choice pages will offer this quest. Or add an Offer-quest choice.");
        open(player, npc, holder.page());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public record Holder(String npcId, int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
