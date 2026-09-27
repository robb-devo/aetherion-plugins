package de.aetherion.quests.ui;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.util.OpenRoads;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Miss Ledger's desk after the stamp: "Open Roads".
 * <p>
 * One small board instead of a void — the four doors the harbour already has plus the
 * wider map. The suggested road glints; filed ones read as filed. Clicking a road points
 * the yellow arrow at that NPC ({@link OpenRoads#pin}) and closes; nothing is accepted,
 * nothing is forced. The old topic briefing sits one click away.
 */
public final class OpenRoadsGUI implements Listener {

    private static final int SIZE = 27;
    private static final int SLOT_HEADER = 4;
    private static final int[] ROAD_SLOTS = {10, 12, 14, 16};
    private static final int SLOT_WILDS = 22;
    private static final int SLOT_BRIEFING = 18;
    private static final int SLOT_CLOSE = 26;

    public OpenRoadsGUI(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        QuestManager qm = questManager();
        OpenRoads.Road next = OpenRoads.next(player, qm);
        Inventory inventory = Bukkit.createInventory(
                new Holder(),
                SIZE,
                LangPack.ui(player, "roads.gui_title", "§8Miss Ledger · Open Roads")
        );
        fill(inventory);

        int filed = 0;
        for (OpenRoads.Road road : OpenRoads.Road.values()) {
            if (road != OpenRoads.Road.WILDS && OpenRoads.done(player, qm, road)) {
                filed++;
            }
        }
        inventory.setItem(SLOT_HEADER, item(
                Material.FILLED_MAP,
                LangPack.ui(player, "roads.gui_header", "§dOpen Roads"),
                false,
                LangPack.ui(player, "roads.gui_header_1", "§7Orientation's filed. None of this is homework."),
                LangPack.ui(player, "roads.gui_header_2", "§7Pick a road — I'll point the arrow."),
                "",
                "§8" + filed + "/4 " + LangPack.ui(player, "roads.gui_filed", "roads filed")
        ));

        OpenRoads.Road[] doors = {
                OpenRoads.Road.STEEL,
                OpenRoads.Road.RITES,
                OpenRoads.Road.CRAFT,
                OpenRoads.Road.SURVEY
        };
        for (int i = 0; i < doors.length; i++) {
            inventory.setItem(ROAD_SLOTS[i], roadCard(player, qm, doors[i], doors[i] == next));
        }
        inventory.setItem(SLOT_WILDS, roadCard(player, qm, OpenRoads.Road.WILDS, next == OpenRoads.Road.WILDS));

        inventory.setItem(SLOT_BRIEFING, item(
                Material.WRITABLE_BOOK,
                LangPack.ui(player, "roads.gui_briefing", "§eBriefing topics"),
                false,
                LangPack.ui(player, "roads.gui_briefing_1", "§7Manager, boosters, pets, storage…"),
                LangPack.ui(player, "roads.gui_briefing_2", "§7The refresher desk.")
        ));
        inventory.setItem(SLOT_CLOSE, item(
                Material.BARRIER,
                LangPack.ui(player, "close", "§cClose"),
                false
        ));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);
    }

    private static ItemStack roadCard(Player player, QuestManager qm, OpenRoads.Road road, boolean suggested) {
        boolean filed = road != OpenRoads.Road.WILDS && OpenRoads.done(player, qm, road);
        List<String> lore = new ArrayList<>();
        lore.add("§8" + road.where(player));
        lore.add("");
        lore.add("§7" + road.pitch(player));
        lore.add("");
        if (filed) {
            lore.add(LangPack.ui(player, "roads.card_filed", "§a✔ Filed. Go back anytime."));
        } else if (suggested) {
            lore.add(LangPack.ui(player, "roads.card_next", "§e➜ Suggested next"));
        }
        if (road.npcId() != null) {
            lore.add(LangPack.ui(player, "roads.card_click", "§eClick §7to point the arrow here."));
        } else {
            lore.add(LangPack.ui(player, "roads.card_click_wilds", "§eClick §7— no arrow. Just go."));
        }
        String name = filed
                ? "§8✔ " + road.label()
                : (suggested ? road.color() + "§l" + road.label() : road.color() + road.label());
        return item(filed ? Material.PAPER : road.icon(), name, suggested && !filed, lore.toArray(String[]::new));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BRIEFING) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            EgonBriefingGUI.open(player, "Miss Ledger");
            return;
        }
        OpenRoads.Road road = roadAt(slot);
        if (road == null) {
            return;
        }
        player.closeInventory();
        OpenRoads.pin(player, road);
        String line = road.npcId() == null
                ? LangPack.ui(player, "roads.ledger_wilds", "No arrow for that one. Look for a glint off the path.")
                : LangPack.format(player, "ui.roads.ledger_pin", "The arrow is on {0}. Off you go.", road.label());
        LivingNpcProfile.say(player, "ledger", "Miss Ledger", line);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static OpenRoads.Road roadAt(int slot) {
        if (slot == SLOT_WILDS) {
            return OpenRoads.Road.WILDS;
        }
        OpenRoads.Road[] doors = {
                OpenRoads.Road.STEEL,
                OpenRoads.Road.RITES,
                OpenRoads.Road.CRAFT,
                OpenRoads.Road.SURVEY
        };
        for (int i = 0; i < ROAD_SLOTS.length; i++) {
            if (ROAD_SLOTS[i] == slot) {
                return doors[i];
            }
        }
        return null;
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", false);
        ItemStack rail = item(Material.BLACK_STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < inventory.getSize(); i++) {
            // Top and bottom rows darker — the road row reads as the page.
            inventory.setItem(i, (i < 9 || i >= 18) ? rail.clone() : pane.clone());
        }
    }

    private static ItemStack item(Material material, String name, boolean glint, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            if (glint) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static QuestManager questManager() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getQuestManager();
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
