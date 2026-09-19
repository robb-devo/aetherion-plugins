package de.aetherion.foraging.npc;

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
 * Miss Canopy briefing desk — activity categories (Ledger-style).
 */
public final class IsleGuideBriefingGUI implements Listener {

    public static final String TITLE = "§2Miss Canopy · Briefing";
    public static final String DETAIL_TITLE = "§2Briefing · Topic";

    private static final int SLOT_HABITATS = 10;
    private static final int SLOT_CHOP = 11;
    private static final int SLOT_HEARTWOOD = 12;
    private static final int SLOT_GROVE = 13;
    private static final int SLOT_WEATHER = 14;
    private static final int SLOT_ARMOR = 15;
    private static final int SLOT_TIPS = 16;
    private static final int SLOT_CLOSE = 22;
    private static final int DETAIL_BOOK = 13;
    private static final int DETAIL_BACK = 22;

    public IsleGuideBriefingGUI(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(Player player) {
        open(player, IsleGuideNpc.DISPLAY);
    }

    public static void open(Player player, String speaker) {
        String who = speaker == null || speaker.isBlank() ? IsleGuideNpc.DISPLAY : speaker;
        Inventory inventory = Bukkit.createInventory(new Holder(Mode.TOPICS, -1, who), 27, TITLE);
        fill(inventory);
        inventory.setItem(4, button(
                Material.OAK_SAPLING,
                "§aIsle Guide",
                "§7Click a topic. Easy mode.",
                "§8" + who + " · " + IsleGuideNpc.TITLE
        ));
        inventory.setItem(SLOT_HABITATS, button(
                Material.SPRUCE_LEAVES,
                "§2Areas",
                "§7Where you stand = what area it is.",
                "§7Snow trees → snow. Swamp → swamp."
        ));
        inventory.setItem(SLOT_CHOP, button(
                Material.IRON_AXE,
                "§6Chop trees",
                "§7Use a forage axe.",
                "§7Chop wood on this isle."
        ));
        inventory.setItem(SLOT_HEARTWOOD, button(
                Material.ENCHANTED_GOLDEN_APPLE,
                "§dHeartwoods",
                "§7Rare shiny cores from chopping.",
                "§7Keep them for the magic table."
        ));
        inventory.setItem(SLOT_GROVE, button(
                Material.ENCHANTING_TABLE,
                "§5Magic table",
                "§7The enchanting table by the dock.",
                "§7Put in 2 heartwoods → pick a buff."
        ));
        inventory.setItem(SLOT_WEATHER, button(
                Material.WHITE_BANNER,
                "§bWeather",
                "§7Fog, rain, snow — depends on area + time.",
                "§7Look under Area on your TAB."
        ));
        inventory.setItem(SLOT_ARMOR, button(
                Material.LEATHER_CHESTPLATE,
                "§eForage armor",
                "§7Better armor needs better wood.",
                "§7Start with oak, climb the tiers."
        ));
        inventory.setItem(SLOT_TIPS, button(
                Material.LANTERN,
                "§6Tips",
                "§71) Chop  2) Save heartwoods  3) Table",
                "§7Some table buffs change the weather."
        ));
        inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, "§cGot it", "§7Close."));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.15f);
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
            case SLOT_HABITATS -> button(
                    Material.SPRUCE_LEAVES,
                    "§2Areas",
                    "§7The trees around you pick the area.",
                    "§7Spruce → Snow. Mangrove → Swamp.",
                    "§7Acacia → Savanna. Cherry → Flower.",
                    "§7TAB shows your Area."
            );
            case SLOT_CHOP -> button(
                    Material.IRON_AXE,
                    "§6Chop trees",
                    "§7Bring a forage axe.",
                    "§7Chop logs on this isle for wood.",
                    "§7That's it — keep chopping."
            );
            case SLOT_HEARTWOOD -> button(
                    Material.ENCHANTED_GOLDEN_APPLE,
                    "§dHeartwoods",
                    "§7Sometimes a rare core drops while chopping.",
                    "§7Save them for the magic table.",
                    "§7You need two different kinds."
            );
            case SLOT_GROVE -> button(
                    Material.ENCHANTING_TABLE,
                    "§5Magic table",
                    "§7Right-click the enchanting table.",
                    "§7Left: put 2 different heartwoods.",
                    "§7Right: pick one of the 3 offers.",
                    "§7Offers change every few minutes."
            );
            case SLOT_WEATHER -> button(
                    Material.WHITE_BANNER,
                    "§bWeather",
                    "§7Area + time of day = weather.",
                    "§7Only you see yours.",
                    "§7TAB: Area, then weather under it.",
                    "§7Some table buffs change weather for a bit."
            );
            case SLOT_ARMOR -> button(
                    Material.LEATHER_CHESTPLATE,
                    "§eForage armor",
                    "§7T1: oak",
                    "§7T2: birch + spruce",
                    "§7Higher tiers: better woods",
                    "§7Recipe book shows exact costs."
            );
            case SLOT_TIPS -> button(
                    Material.LANTERN,
                    "§6Tips",
                    "§7Chop → keep heartwoods → magic table.",
                    "§7Bigger buffs cost more wood.",
                    "§7Talk to me again anytime."
            );
            default -> button(Material.BOOK, "§7?", "§7Nothing here.");
        };
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane.clone());
        }
    }

    private static ItemStack button(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(line);
            }
            meta.setLore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.mode == Mode.TOPICS) {
            if (slot == SLOT_CLOSE) {
                player.closeInventory();
                return;
            }
            if (slot == SLOT_HABITATS || slot == SLOT_CHOP || slot == SLOT_HEARTWOOD
                    || slot == SLOT_GROVE || slot == SLOT_WEATHER || slot == SLOT_ARMOR || slot == SLOT_TIPS) {
                openDetail(player, holder.speaker, slot);
            }
            return;
        }
        if (holder.mode == Mode.DETAIL) {
            if (slot == DETAIL_BACK) {
                open(player, holder.speaker);
            }
        }
    }

    private enum Mode {
        TOPICS,
        DETAIL
    }

    private record Holder(Mode mode, int topicSlot, String speaker) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
