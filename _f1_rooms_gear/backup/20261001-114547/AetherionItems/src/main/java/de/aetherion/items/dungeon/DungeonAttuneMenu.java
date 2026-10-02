package de.aetherion.items.dungeon;

import de.aetherion.items.item.CustomItem;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonAttuneMenu implements Listener {

    public static final String TITLE = "§8Dungeon Calling";

    private static final int[] CALLING_SLOTS = {11, 12, 13, 14, 15};
    private static final int CANCEL_SLOT = 22;

    private final ItemManager items;
    private final CustomItem customItem;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public DungeonAttuneMenu(ItemManager items, CustomItem customItem) {
        this.items = items;
        this.customItem = customItem;
    }

    public void open(Player player, DungeonPiece piece, EquipmentSlot hand) {
        if (player == null || piece == null) {
            return;
        }
        sessions.put(player.getUniqueId(), new Session(piece, hand == null ? EquipmentSlot.HAND : hand));
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, TITLE);
        ItemStack pane = GuiItems.named(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        inventory.setItem(4, GuiItems.named(
                piece.leather(),
                "§7Unattuned Vestige · " + piece.display(),
                "§7Pick a calling. This piece commits.",
                "§8There is no undo. The dungeon does not do refunds."
        ));
        DungeonCalling[] callings = DungeonCalling.values();
        for (int i = 0; i < callings.length; i++) {
            inventory.setItem(CALLING_SLOTS[i], icon(callings[i], piece));
        }
        inventory.setItem(CANCEL_SLOT, GuiItems.named(
                org.bukkit.Material.BARRIER,
                "§cKeep it blank",
                "§7Walk away. The vestige stays empty.",
                "§8You can decide later. The dungeon can wait."
        ));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CANCEL_SLOT) {
            player.closeInventory();
            return;
        }
        DungeonCalling calling = callingAt(slot);
        if (calling == null) {
            return;
        }
        attune(player, calling);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
    }

    private void attune(Player player, DungeonCalling calling) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        ItemStack attuned = customItem.createDungeonArmor(calling, session.piece);
        if (!DungeonArmor.consumeAndReplace(
                player,
                session.hand,
                session.piece,
                item -> isVestigePiece(item, session.piece),
                attuned
        )) {
            player.sendMessage("§7You misplaced the vestige. The dungeon notices.");
            player.closeInventory();
            return;
        }
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.15f);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.7f, 1.05f);
        player.sendMessage(calling.color() + calling.display() + " §7chosen. The vestige remembers.");
    }

    private boolean isVestigePiece(ItemStack item, DungeonPiece piece) {
        String id = items.getItemId(item);
        return DungeonArmor.isVestige(id) && piece == DungeonPiece.fromItemId(id);
    }

    private static DungeonCalling callingAt(int slot) {
        DungeonCalling[] callings = DungeonCalling.values();
        for (int i = 0; i < callings.length; i++) {
            if (CALLING_SLOTS[i] == slot) {
                return callings[i];
            }
        }
        return null;
    }

    private static ItemStack icon(DungeonCalling calling, DungeonPiece piece) {
        return GuiItems.named(
                calling.icon(),
                calling.color() + calling.display(),
                "§7" + calling.specialty(),
                "",
                "§8Becomes: " + calling.setName() + " " + piece.display(),
                "§8Boosters: " + calling.boosters(),
                "",
                "§eClick to attune this piece."
        );
    }

    private record Session(DungeonPiece piece, EquipmentSlot hand) {
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
