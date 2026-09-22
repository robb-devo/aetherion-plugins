package de.aetherion.items.menu;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.DungeonCore;
import de.aetherion.items.item.DungeonCoreInfusion;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.BoosterApplier;
import de.aetherion.items.model.BoosterSockets;
import de.aetherion.items.model.BoosterType;
import de.aetherion.items.util.QuestProgressHook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;

/**
 * Manager → Anvil. Place a tool, weapon, or armor and socket up to 14 boosters.
 * Boosters apply only while they sit in a slot and can be swapped out.
 */
public final class BoosterSocketMenu implements Listener {

    public static final int SIZE = 54;
    public static final int GEAR_SLOT = 13;
    public static final int CLOSE_SLOT = 49;

    private static final int[] SOCKET_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static BoosterSocketMenu instance;

    private final ItemManager items;

    public BoosterSocketMenu(ItemManager items) {
        this.items = items;
        instance = this;
    }

    public static void open(Player player) {
        if (instance == null || player == null) {
            return;
        }
        instance.openMenu(player);
    }

    private void openMenu(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, "§8Booster Sockets");
        holder.inventory = inventory;
        paint(holder);
        player.openInventory(inventory);
        QuestProgressHook.noteUsed(player, "AETHER_ANVIL");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.NUMBER_KEY) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        boolean inTop = event.getClickedInventory() == top;
        if (!inTop) {
            if (event.isShiftClick()) {
                shiftFromPlayer(player, holder, event.getCurrentItem(), event.getSlot());
            }
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == GEAR_SLOT) {
            clickGear(player, holder, event.getCursor());
            return;
        }
        int socket = socketIndex(slot);
        if (socket >= 0) {
            clickSocket(player, holder, socket, event.getCursor());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            for (int raw : event.getRawSlots()) {
                if (raw < SIZE) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        ItemStack gear = holder.inventory == null ? null : holder.inventory.getItem(GEAR_SLOT);
        if (isFiller(gear)) {
            gear = null;
        }
        if (holder.inventory != null) {
            holder.inventory.setItem(GEAR_SLOT, null);
        }
        give(player, gear);
    }

    private void shiftFromPlayer(Player player, Holder holder, ItemStack stack, int eventSlot) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemStack gear = gear(holder);
        if (items.getBoosterType(stack) != null) {
            if (gear == null) {
                player.sendMessage("§7Place a tool, weapon, or armor first.");
                return;
            }
            if (insert(player, holder, gear, items.getBoosterType(stack), -1)) {
                consumeOne(player, stack, eventSlot);
            }
            return;
        }
        if (holder.inventory == null) {
            return;
        }
        if (gear == null && items.isAetherionItem(stack) && items.getBoosterType(stack) == null) {
            ItemStack placed = stack.clone();
            placed.setAmount(1);
            holder.inventory.setItem(GEAR_SLOT, placed);
            consumeOne(player, stack, eventSlot);
            paint(holder);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.6f, 1.2f);
        }
    }

    private void clickGear(Player player, Holder holder, ItemStack cursor) {
        if (holder.inventory == null) {
            return;
        }
        ItemStack gear = gear(holder);
        if (cursor != null && !cursor.getType().isAir() && DungeonCore.isCore(cursor) && gear != null) {
            ItemStack infused = DungeonCoreInfusion.infuse(items, gear, cursor);
            if (infused == null) {
                player.sendMessage("§7That core does not fit this piece.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.4f, 1f);
                return;
            }
            holder.inventory.setItem(GEAR_SLOT, infused);
            shrinkCursor(player, cursor);
            paint(holder);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.4f);
            return;
        }
        if (cursor != null && !cursor.getType().isAir() && items.getBoosterType(cursor) != null && gear != null) {
            if (insert(player, holder, gear, items.getBoosterType(cursor), -1)) {
                shrinkCursor(player, cursor);
            }
            return;
        }
        ItemStack nextCursor = gear;
        ItemStack nextGear = null;
        if (cursor != null && !cursor.getType().isAir()) {
            if (!items.isAetherionItem(cursor) || items.getBoosterType(cursor) != null) {
                player.sendMessage("§7Sockets take tools, weapons, and armor.");
                return;
            }
            nextGear = cursor.clone();
            nextGear.setAmount(1);
            if (cursor.getAmount() > 1) {
                player.sendMessage("§7Place one item at a time.");
                return;
            }
        }
        holder.inventory.setItem(GEAR_SLOT, nextGear);
        player.setItemOnCursor(nextCursor);
        paint(holder);
    }

    private void clickSocket(Player player, Holder holder, int index, ItemStack cursor) {
        ItemStack gear = gear(holder);
        if (gear == null) {
            player.sendMessage("§7Place a tool, weapon, or armor first.");
            return;
        }
        BoosterType[] slots = BoosterSockets.read(items, gear);
        BoosterType sitting = index < slots.length ? slots[index] : null;
        BoosterType incoming = cursor == null || cursor.getType().isAir() ? null : items.getBoosterType(cursor);
        if (incoming == null && sitting == null) {
            return;
        }
        if (incoming == null) {
            if (takeOut(player, holder, gear, slots, index, sitting)) {
                player.setItemOnCursor(boosterItem(sitting));
            }
            return;
        }
        if (sitting != null) {
            if (!takeOut(player, holder, gear, slots, index, sitting)) {
                return;
            }
            ItemStack returned = boosterItem(sitting);
            if (!insert(player, holder, gear(holder), incoming, index)) {
                give(player, returned);
                return;
            }
            shrinkCursor(player, cursor);
            give(player, returned);
            return;
        }
        if (insert(player, holder, gear, incoming, index)) {
            shrinkCursor(player, cursor);
        }
    }

    private boolean insert(Player player, Holder holder, ItemStack gear, BoosterType type, int index) {
        if (holder == null || holder.inventory == null || gear == null || type == null) {
            return false;
        }
        BoosterType[] slots = BoosterSockets.read(items, gear);
        int target = index >= 0 ? index : BoosterSockets.firstEmpty(slots);
        if (target < 0 || target >= BoosterSockets.COUNT || slots[target] != null) {
            player.sendMessage("§7All §f14 §7booster slots are full.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.4f, 1f);
            return false;
        }
        BoosterApplier.Status status = BoosterApplier.apply(items, gear, type);
        if (status != BoosterApplier.Status.APPLIED) {
            player.sendMessage(switch (status) {
                case NOT_ALLOWED -> "§7That booster does not fit this item.";
                case FULL -> "§7All §f14 §7booster slots are full.";
                case NO_RARITY -> "§7This item has no rarity to scale a booster.";
                default -> "§7Could not socket that booster.";
            });
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.4f, 1f);
            return false;
        }
        slots[target] = type;
        BoosterSockets.write(gear, slots);
        holder.inventory.setItem(GEAR_SLOT, gear);
        paint(holder);
        QuestProgressHook.noteUsed(player, "APPLY_BOOSTER");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
        return true;
    }

    private boolean takeOut(Player player, Holder holder, ItemStack gear, BoosterType[] slots, int index, BoosterType sitting) {
        if (BoosterApplier.remove(items, gear, sitting) != BoosterApplier.Status.APPLIED) {
            slots[index] = null;
            BoosterSockets.write(gear, slots);
            holder.inventory.setItem(GEAR_SLOT, gear);
            paint(holder);
            player.sendMessage("§7Cleared an empty socket.");
            return false;
        }
        slots[index] = null;
        BoosterSockets.write(gear, slots);
        holder.inventory.setItem(GEAR_SLOT, gear);
        paint(holder);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.1f);
        return true;
    }

    private void paint(Holder holder) {
        Inventory inventory = holder.inventory;
        if (inventory == null) {
            return;
        }
        ItemStack gear = gear(holder);
        BoosterType[] slots = BoosterSockets.read(items, gear);
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, pane());
        }
        inventory.setItem(4, info(gear, slots));
        inventory.setItem(GEAR_SLOT, gear == null ? gearHint() : gear);
        for (int i = 0; i < SOCKET_SLOTS.length; i++) {
            BoosterType type = i < slots.length ? slots[i] : null;
            inventory.setItem(SOCKET_SLOTS[i], type == null ? emptySocket(i + 1) : socketIcon(type, i + 1));
        }
        inventory.setItem(CLOSE_SLOT, close());
    }

    private ItemStack gear(Holder holder) {
        if (holder.inventory == null) {
            return null;
        }
        ItemStack gear = holder.inventory.getItem(GEAR_SLOT);
        if (gear == null || gear.getType().isAir() || isFiller(gear)) {
            return null;
        }
        return gear;
    }

    private static boolean isFiller(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        Byte mark = stack.getItemMeta().getPersistentDataContainer().get(ItemKeys.guiDisplay(), PersistentDataType.BYTE);
        return mark != null && mark == (byte) 1;
    }

    private ItemStack boosterItem(BoosterType type) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getCustomItem() == null) {
            return new ItemStack(Material.COAL);
        }
        return plugin.getCustomItem().booster(type);
    }

    private static void consumeOne(Player player, ItemStack stack, int slot) {
        if (stack == null) {
            return;
        }
        if (stack.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }
    }

    private static void shrinkCursor(Player player, ItemStack cursor) {
        if (cursor.getAmount() <= 1) {
            player.setItemOnCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(cursor);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return;
        }
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private static int socketIndex(int rawSlot) {
        for (int i = 0; i < SOCKET_SLOTS.length; i++) {
            if (SOCKET_SLOTS[i] == rawSlot) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack info(ItemStack gear, BoosterType[] slots) {
        int filled = BoosterSockets.filled(slots);
        ItemStack item = named(Material.BOOK, "§dBooster Sockets",
                "§7Place a tool, weapon, or armor.",
                "§7" + filled + "§8/§f" + BoosterSockets.COUNT + " §7socketed.",
                "§7Insert or swap anytime — they apply while socketed.",
                "§7Upgrades and crafts keep these sockets.",
                gear == null ? "§8Empty" : "§aItem in the center slot.");
        return item;
    }

    private ItemStack gearHint() {
        return named(Material.HOPPER, "§eItem",
                "§7Click with a tool, weapon, or armor.",
                "§7Hold a dungeon core and click to infuse.");
    }

    private ItemStack emptySocket(int number) {
        return named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Socket " + number,
                "§8Empty",
                "§7Click with a booster to socket it.");
    }

    private ItemStack socketIcon(BoosterType type, int number) {
        ItemStack icon = boosterItem(type);
        if (icon == null) {
            icon = new ItemStack(Material.AMETHYST_SHARD);
        }
        icon = icon.clone();
        icon.setAmount(1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    "§7Socket §f" + number + "§8/§f" + BoosterSockets.COUNT,
                    "§aActive while socketed.",
                    "§eClick to remove."
            ));
            meta.getPersistentDataContainer().set(ItemKeys.guiDisplay(), PersistentDataType.BYTE, (byte) 1);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack pane() {
        return named(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private ItemStack close() {
        return named(Material.BARRIER, "§cClose", "§7Your item returns to you.");
    }

    private ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.guiDisplay(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
