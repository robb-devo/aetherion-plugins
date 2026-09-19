package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorSessions;
import de.aetherion.quests.editor.NpcEditor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class EditMenu implements Listener {

    private static final int NAME = 10;
    private static final int SUBTITLE = 11;
    private static final int APPEAR = 12;
    private static final int DIALOGUE = 13;
    private static final int QUEST = 14;
    private static final int MOVE = 15;
    private static final int LOOK = 16;
    private static final int DUPLICATE = 21;
    private static final int DELETE = 22;
    private static final int BACK = 23;

    private final NpcEditor editor;

    public EditMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId()),
                27,
                EditorItems.title(player, "npc_edit", "§8Edit NPC")
        );
        EditorItems.fill(inventory);
        inventory.setItem(4, EditorItems.head(
                npc.getSkinUsername(),
                "§b" + npc.getName(),
                "§7id §f" + npc.getId(),
                "§7" + npc.getSubtitle(),
                npc.hasLinkedQuest() ? "§aQuest §f" + npc.getLinkedQuestId() : "§8No quest linked"
        ));
        inventory.setItem(NAME, EditorItems.button(Material.NAME_TAG, "§eRename", "§7Currently §f" + npc.getName()));
        inventory.setItem(SUBTITLE, EditorItems.button(Material.PAPER, "§eSubtitle", "§7Currently §f" + npc.getSubtitle()));
        inventory.setItem(APPEAR, EditorItems.button(Material.LEATHER_CHESTPLATE, "§6Appearance",
                "§7Preset §f" + npc.getPreset().label(),
                "§7Skin §f" + npc.getSkinUsername()));
        inventory.setItem(DIALOGUE, EditorItems.button(Material.WRITABLE_BOOK, "§dDialogue",
                "§7" + npc.pages().size() + " page(s)",
                "§7Start §f" + npc.getStartPage()));
        inventory.setItem(QUEST, EditorItems.button(Material.MAP, "§aQuest link",
                npc.hasLinkedQuest() ? "§f" + npc.getLinkedQuestId() : "§7None — pick an existing quest"));
        inventory.setItem(MOVE, EditorItems.button(Material.ENDER_PEARL, "§bMove here", "§7Teleport the NPC to your feet."));
        inventory.setItem(LOOK, EditorItems.button(Material.ENDER_EYE, "§bLook at me", "§7Face your current position."));
        inventory.setItem(DUPLICATE, EditorItems.button(Material.PAPER, "§eDuplicate", "§7Clone beside you."));
        inventory.setItem(DELETE, EditorItems.button(Material.BARRIER, "§cDelete", "§7Asks first."));
        inventory.setItem(BACK, EditorItems.button(Material.ARROW, "§7Back"));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.15f);
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
            player.closeInventory();
            player.sendMessage("§cThat NPC is gone.");
            return;
        }
        editor.sessions().of(player).setNpcId(npc.getId());
        switch (event.getRawSlot()) {
            case NAME -> editor.prompt(player, EditorSessions.Prompt.RENAME, "Type the new display name");
            case SUBTITLE -> editor.prompt(player, EditorSessions.Prompt.SUBTITLE, "Type a short subtitle");
            case APPEAR -> AppearanceMenu.open(player, npc);
            case DIALOGUE -> DialogueMenu.openTree(player, npc);
            case QUEST -> QuestLinkMenu.open(player, npc, 0);
            case MOVE -> {
                editor.moveHere(player, npc);
                open(player, npc);
            }
            case LOOK -> {
                editor.lookAt(player, npc);
                open(player, npc);
            }
            case DUPLICATE -> editor.duplicate(player, npc);
            case DELETE -> ConfirmMenu.open(player, npc);
            case BACK -> MainMenu.open(player);
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
