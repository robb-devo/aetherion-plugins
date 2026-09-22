package de.aetherion.dungeons.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Short briefing desk for how dungeons work — opened by the Dungeon Scribe NPC.
 */
public final class DungeonGuideGUI implements Listener {

    public static final String TITLE = "§5Dungeon Briefing";
    public static final String DETAIL_TITLE = "§5Briefing · Topic";

    private static final int SLOT_FLOW = 10;
    private static final int SLOT_FLOORS = 11;
    private static final int SLOT_PARTY = 12;
    private static final int SLOT_LEAVE = 13;
    private static final int SLOT_LOOT = 14;
    private static final int SLOT_TIPS = 15;
    private static final int SLOT_CLOSE = 22;
    private static final int DETAIL_BOOK = 13;
    private static final int DETAIL_BACK = 22;

    public DungeonGuideGUI(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(Player player) {
        open(player, "Dungeon Scribe");
    }

    public static void open(Player player, String speaker) {
        String who = speaker == null || speaker.isBlank() ? "Dungeon Scribe" : speaker;
        Inventory inventory = Bukkit.createInventory(new Holder(Mode.TOPICS, -1, who), 27, TITLE);
        fill(inventory);
        inventory.setItem(4, button(
                Material.BOOK,
                "§5Dungeon Briefing",
                "§7Pick a topic. Short answers only.",
                "§8" + who + " keeps the clipboard tidy."
        ));
        inventory.setItem(SLOT_FLOW, button(
                Material.END_PORTAL_FRAME,
                "§eHow a run works",
                "§7Talk to the Keeper → pick a floor.",
                "§7Clear trash → boss → loot → leave."
        ));
        inventory.setItem(SLOT_FLOORS, button(
                Material.IRON_BARS,
                "§dFloors",
                "§7Floor 1 Prison · Floor 2 Frost · Floor 3 Ashes.",
                "§7Harder floors, better cores."
        ));
        inventory.setItem(SLOT_PARTY, button(
                Material.PLAYER_HEAD,
                "§aParties",
                "§7Party leader starts. Max 4.",
                "§7Extra players scale the mobs."
        ));
        inventory.setItem(SLOT_LEAVE, button(
                Material.OAK_DOOR,
                "§bLeave & hub",
                "§7/dungeon leave §7exits. Gear stays.",
                "§7Exit portal after the boss also works."
        ));
        inventory.setItem(SLOT_LOOT, button(
                Material.CHEST,
                "§6Loot & cores",
                "§7Caches + victory chest. One claim each.",
                "§7Cores upgrade dungeon gear later."
        ));
        inventory.setItem(SLOT_TIPS, button(
                Material.LANTERN,
                "§6Tips",
                "§7Slot 9 map · Gate Warden ready-up.",
                "§7Die less. Loot more. Don't AFK the portal."
        ));
        inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, "§cGot it", "§7Close the desk."));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.2f);
    }

    private static void openDetail(Player player, String speaker, int topicSlot) {
        Inventory inventory = Bukkit.createInventory(new Holder(Mode.DETAIL, topicSlot, speaker), 27, DETAIL_TITLE);
        fill(inventory);
        inventory.setItem(DETAIL_BOOK, detailItem(topicSlot));
        inventory.setItem(DETAIL_BACK, button(Material.ARROW, "§eBack", "§7Return to topics."));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
    }

    private static ItemStack detailItem(int topicSlot) {
        return switch (topicSlot) {
            case SLOT_FLOW -> button(
                    Material.END_PORTAL_FRAME,
                    "§eHow a run works",
                    "§7Click the §5Dungeon Keeper§7.",
                    "§7Pick Floor 1 / 2 / 3.",
                    "§7Fill the clearance bar (or clear rooms),",
                    "§7beat the boss, grab the chest,",
                    "§7then walk the §5exit portal§7 — or §e/dungeon leave§7."
            );
            case SLOT_FLOORS -> button(
                    Material.IRON_BARS,
                    "§dFloors",
                    "§5Floor 1 · Prison§7 — halls, glass gates, Sentinel.",
                    "§bFloor 2 · Frostbound§7 — clearance bar → snowman.",
                    "§5Floor 3 · Throne of Ashes§7 — Aetherion.",
                    "§7One active run at a time."
            );
            case SLOT_PARTY -> button(
                    Material.PLAYER_HEAD,
                    "§aParties",
                    "§7Be in a party. Leader opens the floor.",
                    "§7Everyone in range comes along.",
                    "§7Max §f4§7. Mobs scale with party size."
            );
            case SLOT_LEAVE -> button(
                    Material.OAK_DOOR,
                    "§bLeave & hub",
                    "§e/dungeon leave§7 — exit the instance. Gear stays.",
                    "§e/dungeon return§7 — main world, then Capital.",
                    "§7Neither command clears your inventory.",
                    "§7Hub return portal does the same handoff."
            );
            case SLOT_LOOT -> button(
                    Material.CHEST,
                    "§6Loot & cores",
                    "§7Combat caches during the floor.",
                    "§7Victory chest after the boss — one pull each.",
                    "§7Dungeon Cores drop for gear upgrades."
            );
            default -> button(
                    Material.LANTERN,
                    "§6Tips",
                    "§7Hold the map in slot 9.",
                    "§7Talk to the Gate Warden to ready up.",
                    "§7Don't stand in fire. Or do — comedy gold."
            );
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (holder.mode == Mode.TOPICS) {
            if (slot == SLOT_CLOSE) {
                player.closeInventory();
                return;
            }
            if (slot == SLOT_FLOW || slot == SLOT_FLOORS || slot == SLOT_PARTY
                    || slot == SLOT_LEAVE || slot == SLOT_LOOT || slot == SLOT_TIPS) {
                openDetail(player, holder.speaker, slot);
            }
            return;
        }
        if (slot == DETAIL_BACK) {
            open(player, holder.speaker);
        }
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane.clone());
        }
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) {
                    if (line != null) {
                        lines.add(line);
                    }
                }
                meta.setLore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private enum Mode { TOPICS, DETAIL }

    public record Holder(Mode mode, int topicSlot, String speaker) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
