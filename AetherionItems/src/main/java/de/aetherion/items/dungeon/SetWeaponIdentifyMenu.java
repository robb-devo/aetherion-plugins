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
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SetWeaponIdentifyMenu implements Listener {

    public static final String TITLE = "§8Weapon Schematic";

    private static final int[] CHOICE_SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};
    private static final int CANCEL_SLOT = 31;

    private final ItemManager items;
    private final CustomItem customItem;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SetWeaponIdentifyMenu(ItemManager items, CustomItem customItem) {
        this.items = items;
        this.customItem = customItem;
    }

    public void open(Player player, EquipmentSlot hand) {
        if (player == null) {
            return;
        }
        sessions.put(player.getUniqueId(), new Session(hand == null ? EquipmentSlot.HAND : hand));
        Inventory inventory = Bukkit.createInventory(new Holder(), 36, TITLE);
        ItemStack pane = GuiItems.named(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        inventory.setItem(4, GuiItems.named(
                org.bukkit.Material.PAPER,
                "§dWeapon Schematic",
                "§7Pick a weapon that matches an armor set.",
                "§8The rest of the identities burn."
        ));
        SetWeaponKind[] kinds = SetWeaponKind.values();
        for (int i = 0; i < kinds.length && i < CHOICE_SLOTS.length; i++) {
            inventory.setItem(CHOICE_SLOTS[i], icon(kinds[i]));
        }
        inventory.setItem(CANCEL_SLOT, GuiItems.named(
                org.bukkit.Material.BARRIER,
                "§cKeep it blank",
                "§7Walk away. The schematic stays unread.",
                "§8You can decide later."
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
        int index = choiceIndex(slot);
        if (index < 0) {
            return;
        }
        identify(player, index);
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

    private void identify(Player player, int index) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        SetWeaponKind[] kinds = SetWeaponKind.values();
        if (index < 0 || index >= kinds.length) {
            return;
        }
        int slot = findSchematic(player, session);
        if (slot < 0) {
            player.sendMessage("§7You misplaced the schematic.");
            player.closeInventory();
            return;
        }
        SetWeaponKind kind = kinds[index];
        ItemStack identified = kind.create(customItem);
        if (identified == null) {
            player.sendMessage("§cThat identity failed to print.");
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(slot, identified);
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.05f);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.7f, 1.1f);
        player.sendMessage("§d" + kind.setName() + " §7chosen. The schematic remembers.");
    }

    private int findSchematic(Player player, Session session) {
        PlayerInventory inventory = player.getInventory();
        int preferred = session.hand == EquipmentSlot.OFF_HAND ? 40 : inventory.getHeldItemSlot();
        if (SetWeaponKind.isSchematic(items.getItemId(inventory.getItem(preferred)))) {
            return preferred;
        }
        if (SetWeaponKind.isSchematic(items.getItemId(inventory.getItemInOffHand()))) {
            return 40;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (SetWeaponKind.isSchematic(items.getItemId(inventory.getItem(slot)))) {
                return slot;
            }
        }
        return -1;
    }

    private static int choiceIndex(int slot) {
        for (int i = 0; i < CHOICE_SLOTS.length; i++) {
            if (CHOICE_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack icon(SetWeaponKind kind) {
        return GuiItems.named(
                kind.icon(),
                "§d" + kind.setName() + " Weapon",
                "§7" + kind.blurb(),
                "",
                "§8Becomes the " + kind.setName() + " set's weapon.",
                "",
                "§eClick to identify."
        );
    }

    private record Session(EquipmentSlot hand) {
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
