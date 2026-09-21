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

    private static final int CREATE = 22;
    private static final int NEARBY = 20;
    private static final int LIST = 24;
    private static final int WAND = 38;
    private static final int STEPS = 40;
    private static final int HELP = 42;

    private final NpcEditor editor;

    public MainMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(),
                54,
                EditorItems.title(player, "npc_editor", "§8NPC & Quest Editor")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(4, EditorItems.button(
                Material.NETHER_STAR,
                EditorItems.ui(player, "editor_home_title", "§bNPC & Quest Editor"),
                EditorItems.ui(player, "editor_home_l1", "§7Create a talking NPC in under a minute."),
                EditorItems.ui(player, "editor_home_l2", "§7Story NPCs stay untouched."),
                "§8/aethernpc"
        ));
        inventory.setItem(18, EditorItems.section(
                EditorItems.ui(player, "editor_sec_start", "Start"),
                EditorItems.ui(player, "editor_sec_start_hint", "§7Create, find, or list.")
        ));
        inventory.setItem(CREATE, EditorItems.button(
                Material.EMERALD_BLOCK,
                EditorItems.ui(player, "editor_create", "§a§lCreate NPC"),
                EditorItems.ui(player, "editor_create_l1", "§7Name them in chat."),
                EditorItems.ui(player, "editor_create_l2", "§7They appear at your feet."),
                EditorItems.ui(player, "editor_create_l3", "§eClick to start.")
        ));
        inventory.setItem(NEARBY, EditorItems.button(
                Material.COMPASS,
                EditorItems.ui(player, "editor_nearby", "§eEdit nearby"),
                EditorItems.ui(player, "editor_nearby_l1", "§7Closest editor NPC"),
                EditorItems.ui(player, "editor_nearby_l2", "§7within 8 blocks.")
        ));
        inventory.setItem(LIST, EditorItems.button(
                Material.BOOK,
                EditorItems.ui(player, "editor_list", "§6Your NPCs"),
                EditorItems.ui(player, "editor_list_l1", "§7Every NPC this tool created.")
        ));
        inventory.setItem(36, EditorItems.section(
                EditorItems.ui(player, "editor_sec_tools", "Tools"),
                EditorItems.ui(player, "editor_sec_tools_hint", "§7Wand and a 3-step recap.")
        ));
        inventory.setItem(WAND, EditorItems.button(
                Material.BLAZE_ROD,
                EditorItems.ui(player, "editor_wand", "§6Get wand"),
                EditorItems.ui(player, "editor_wand_l1", "§7Right-click air — this menu."),
                EditorItems.ui(player, "editor_wand_l2", "§7Right-click an editor NPC — edit.")
        ));
        inventory.setItem(STEPS, EditorItems.button(
                Material.MAP,
                EditorItems.ui(player, "editor_steps", "§f60-second start"),
                EditorItems.ui(player, "editor_steps_l1", "§e1. §7Create NPC"),
                EditorItems.ui(player, "editor_steps_l2", "§e2. §7Write what they say"),
                EditorItems.ui(player, "editor_steps_l3", "§e3. §7Optional: give them a quest"),
                EditorItems.ui(player, "editor_steps_l4", "§8That's the whole simple path.")
        ));
        inventory.setItem(HELP, EditorItems.button(
                Material.KNOWLEDGE_BOOK,
                EditorItems.ui(player, "editor_tips", "§fTips"),
                EditorItems.ui(player, "editor_tips_l1", "§7Wand, commands, what's safe.")
        ));
        inventory.setItem(EditorItems.BACK, EditorItems.close(player));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        Player player = EditorItems.editorClick(event);
        if (player == null) {
            return;
        }
        switch (event.getRawSlot()) {
            case CREATE -> editor.beginCreate(player);
            case NEARBY -> {
                CustomNpc npc = editor.service().nearby(player, 8);
                if (npc == null) {
                    player.sendMessage(EditorItems.ui(player, "editor_none_nearby", "§eNo editor NPC nearby."));
                    player.sendMessage(EditorItems.ui(player, "editor_story_safe",
                            "§7Story NPCs (Egon, Twig, Miss Canopy) stay untouched."));
                    return;
                }
                editor.openEdit(player, npc);
            }
            case LIST -> ListMenu.open(player, 0);
            case WAND -> editor.giveWand(player);
            case STEPS -> {
                player.sendMessage(EditorItems.ui(player, "editor_steps_l1", "§e1. §7Create NPC"));
                player.sendMessage(EditorItems.ui(player, "editor_steps_l2", "§e2. §7Write what they say"));
                player.sendMessage(EditorItems.ui(player, "editor_steps_l3", "§e3. §7Optional: give them a quest"));
            }
            case HELP -> HelpMenu.open(player);
            case EditorItems.BACK -> player.closeInventory();
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
