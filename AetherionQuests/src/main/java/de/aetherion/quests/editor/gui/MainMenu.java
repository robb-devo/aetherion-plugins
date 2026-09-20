package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
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

public final class MainMenu implements Listener {

    private static final int CREATE = 20;
    private static final int NEARBY = 22;
    private static final int LIST = 24;
    private static final int WAND = 38;
    private static final int HELP = 40;

    private final NpcEditor editor;

    public MainMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(),
                54,
                EditorItems.title(player, "npc_editor", "§8NPC Editor")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.NETHER_STAR,
                "§bNPC Editor",
                "§7Create FancyNPCs with dialogue.",
                "§7Story NPCs stay untouched.",
                "§8/aethernpc · /npceditor",
                "§8FancyNpcs keeps /npc"
        ));
        inventory.setItem(8, EditorItems.saved(false));
        inventory.setItem(18, EditorItems.section("NPCs", "§7Create, find, list."));
        inventory.setItem(CREATE, EditorItems.button(
                Material.EMERALD_BLOCK,
                "§aCreate NPC",
                "§7Name in chat, then place at your feet.",
                "§7Starts as §bDialog-only§7.",
                "§eClick to start."
        ));
        inventory.setItem(NEARBY, EditorItems.button(
                Material.COMPASS,
                "§eEdit nearby",
                "§7Opens the closest editor NPC",
                "§7within 8 blocks."
        ));
        inventory.setItem(LIST, EditorItems.button(
                Material.BOOK,
                "§6List NPCs",
                "§7Every moderator NPC you created.",
                "§8Dialog vs Quest shown on each row"
        ));
        inventory.setItem(36, EditorItems.section("Tools", "§7Wand + help."));
        inventory.setItem(WAND, EditorItems.button(
                Material.BLAZE_ROD,
                "§6Get wand",
                "§7Right-click air — this menu.",
                "§7Right-click an editor NPC — edit.",
                "§7Sneak-click — delete confirm.",
                "§8Content Kit opens this same wand."
        ));
        inventory.setItem(HELP, EditorItems.button(
                Material.KNOWLEDGE_BOOK,
                "§fHelp",
                "§7Commands, permissions, storage.",
                "§7Mode, quest path, rewards."
        ));
        inventory.setItem(49, EditorItems.button(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !NpcEditor.allowed(player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        switch (event.getRawSlot()) {
            case CREATE -> editor.beginCreate(player);
            case NEARBY -> {
                CustomNpc npc = editor.service().nearby(player, 8);
                if (npc == null) {
                    player.sendMessage("§eNo editor NPC within 8 blocks.");
                    player.sendMessage("§7Story NPCs cannot be edited with this tool.");
                    return;
                }
                editor.openEdit(player, npc);
            }
            case LIST -> ListMenu.open(player, 0);
            case WAND -> editor.giveWand(player);
            case HELP -> HelpMenu.open(player);
            case 49 -> player.closeInventory();
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

    public record Holder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
