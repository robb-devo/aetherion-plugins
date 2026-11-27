package de.aetherion.items.menu;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.BoosterLimits;
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
    /** A book page (ManagerChrome.ANVIL): the gear on its seal, the sockets as ledger pockets below. */
    public static final int INFO_SLOT = 0;
    public static final int GEAR_SLOT = 13;
    public static final int CLOSE_SLOT = 49;
    /** Top-right of the chest. Opens the normal Minecraft anvil. */
    public static final int VANILLA_ANVIL_SLOT = 8;

    private static final int[] SOCKET_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            38, 39, 41, 42
    };
    /** Between the four Bonegraft sockets: what they are and where they open. */
    private static final int GRAFT_INFO_SLOT = 40;

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
        Inventory inventory = Bukkit.createInventory(holder, SIZE, de.aetherion.items.util.ManagerChrome.title(de.aetherion.items.util.ManagerChrome.Page.ANVIL));
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
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        boolean inTop = event.getClickedInventory() == top;
        if (!inTop) {
            if (event.isShiftClick()) {
                shiftFromPlayer(player, holder, event.getCurrentItem(), event.getSlot());
                resync(player);
            }
            return;
        }
        int slot = event.getRawSlot();
        if (event.getClick() == ClickType.NUMBER_KEY) {
            if (slot == GEAR_SLOT) {
                hotbarSwap(player, holder, event.getHotbarButton());
                resync(player);
            }
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == VANILLA_ANVIL_SLOT) {
            openVanillaAnvil(player);
            return;
        }
        if (slot == GEAR_SLOT) {
            if (event.isShiftClick() && (event.getCursor() == null || event.getCursor().getType().isAir())) {
                gearToInventory(player, holder);
            } else {
                clickGear(player, holder, event.getCursor());
            }
            resync(player);
            return;
        }
        int socket = socketIndex(slot);
        if (socket >= 0) {
            if (event.isShiftClick() && (event.getCursor() == null || event.getCursor().getType().isAir())) {
                socketToInventory(player, holder, socket);
            } else {
                clickSocket(player, holder, socket, event.getCursor());
            }
            resync(player);
        }
    }

    /**
     * 2026-11-27: putting things in has to feel like any inventory. A drag that lands on exactly one slot of the
     * page (the item seal or a socket) counts as a click there with what was on the cursor; any other drag into the
     * page is refused as before. Drags inside the player's own inventory stay normal.
     */

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        int target = -1;
        int topSlots = 0;
        for (int raw : event.getRawSlots()) {
            if (raw < SIZE) {
                topSlots++;
                target = raw;
            }
        }
        if (topSlots == 0) {
            return;
        }
        event.setCancelled(true);
        if (topSlots != 1 || !(event.getWhoClicked() instanceof Player player)
                || (target != GEAR_SLOT && socketIndex(target) < 0)) {
            return;
        }
        int slot = target;
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        // The cancelled drag hands the stack back to the cursor first; act on it one tick later.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder open)
                    || open != holder) {
                return;
            }
            ItemStack cursor = player.getItemOnCursor();
            if (slot == GEAR_SLOT) {
                clickGear(player, holder, cursor);
            } else {
                clickSocket(player, holder, socketIndex(slot), cursor);
            }
            player.updateInventory();
        });
    }

    /** The client guesses what a click does; after we rebuild the page, send it the truth. */
    private static void resync(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.updateInventory();
                }
            });
        }
    }

    /** Shift-click on the seal: the item goes back into the inventory (not onto the cursor). */
    private void gearToInventory(Player player, Holder holder) {
        ItemStack gear = gear(holder);
        if (gear == null || holder.inventory == null) {
            return;
        }
        holder.inventory.setItem(GEAR_SLOT, null);
        give(player, gear);
        paint(holder);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.6f, 0.9f);
    }

    /** Shift-click on a socket: the booster comes out into the inventory. */
    private void socketToInventory(Player player, Holder holder, int index) {
        ItemStack gear = gear(holder);
        if (gear == null) {
            return;
        }
        BoosterType[] slots = BoosterSockets.read(items, gear);
        BoosterType sitting = index < slots.length ? slots[index] : null;
        if (sitting == null) {
            return;
        }
        if (takeOut(player, holder, gear, slots, index, sitting)) {
            give(player, boosterItem(sitting));
        }
    }

    /** Number key on the seal: swap the item with that hotbar slot, like any inventory. */
    private void hotbarSwap(Player player, Holder holder, int hotbar) {
        if (holder.inventory == null || hotbar < 0 || hotbar > 8) {
            return;
        }
        ItemStack inHand = player.getInventory().getItem(hotbar);
        ItemStack gear = gear(holder);
        if (inHand != null && !inHand.getType().isAir()) {
            if (!items.isAetherionItem(inHand) || items.getBoosterType(inHand) != null || inHand.getAmount() > 1) {
                bar(player, onlyGear(player));
                return;
            }
        }
        holder.inventory.setItem(GEAR_SLOT, inHand == null || inHand.getType().isAir() ? null : inHand.clone());
        player.getInventory().setItem(hotbar, gear);
        paint(holder);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.6f, 1.2f);
    }

    private static void bar(Player player, String legacy) {
        if (player != null && legacy != null && !legacy.isBlank()) {
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize(legacy));
        }
    }

    private static String t(Player player, String en, String german) {
        return de.aetherion.core.exile.Say.t(player, en, german);
    }

    private static String onlyGear(Player player) {
        return t(player, "§7Only Aetherion gear takes boosters. §8Make it in the Recipe Book.",
                "§7Nur Aetherion-Ausrüstung nimmt Booster. §8Herstellen im Rezeptbuch.");
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
                bar(player, t(player, "§7Put a tool, weapon or armour on the seal first.",
                        "§7Leg zuerst Werkzeug, Waffe oder Rüstung auf das Siegel."));
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
        if (DungeonCore.isCore(stack)) {
            bar(player, t(player, "§7Pick the core up and click the item on the seal to infuse it.",
                    "§7Nimm den Kern auf und klick damit auf das Teil auf dem Siegel."));
            return;
        }
        if (gear != null) {
            bar(player, t(player, "§7Take the item on the seal out first (shift-click it).",
                    "§7Nimm zuerst den Gegenstand vom Siegel (Shift-Klick)."));
            return;
        }
        if (!items.isAetherionItem(stack) || stack.getAmount() > 1) {
            bar(player, onlyGear(player));
            return;
        }
        if (items.isAetherionItem(stack) && items.getBoosterType(stack) == null) {
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
                bar(player, t(player, "§7That core does not fit this piece.", "§7Dieser Kern passt nicht zu dem Teil."));
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
                bar(player, onlyGear(player));
                return;
            }
            nextGear = cursor.clone();
            nextGear.setAmount(1);
            if (cursor.getAmount() > 1) {
                bar(player, t(player, "§7One item at a time.", "§7Immer nur ein Gegenstand."));
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
            bar(player, t(player, "§7Put a tool, weapon or armour on the seal first.",
                    "§7Leg zuerst Werkzeug, Waffe oder Rüstung auf das Siegel."));
            return;
        }
        BoosterType[] slots = BoosterSockets.read(items, gear);
        BoosterType sitting = index < slots.length ? slots[index] : null;
        BoosterType incoming = cursor == null || cursor.getType().isAir() ? null : items.getBoosterType(cursor);
        if (sitting == null && index >= capacity(gear)) {
            bar(player, t(player, "§6Bonegraft socket §7· opens with a graft at the §6Heartforge §7(Jawcamp).",
                    "§6Bonegraft-Platz §7· öffnet mit einem Graft an der §6Heartforge §7(Jawcamp)."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.4f, 1f);
            return;
        }
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
        int open = capacity(gear);
        if (target < 0 || target >= open || slots[target] != null) {
            bar(player, t(player, "§7All §f" + open + " §7booster slots are full.", "§7Alle §f" + open + " §7Booster-Plätze sind voll."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.4f, 1f);
            return false;
        }
        BoosterApplier.Status status = BoosterApplier.apply(items, gear, type);
        if (status != BoosterApplier.Status.APPLIED) {
            bar(player, switch (status) {
                case NOT_ALLOWED -> t(player, "§7That booster does not fit this item.", "§7Dieser Booster passt nicht auf das Teil.");
                case FULL -> t(player, "§7All booster slots are full.", "§7Alle Booster-Plätze sind voll.");
                case NO_RARITY -> t(player, "§7This item has no rarity to scale a booster.", "§7Das Teil hat keine Seltenheit für einen Booster.");
                default -> t(player, "§7Could not socket that booster.", "§7Der Booster ging nicht rein.");
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
            bar(player, t(player, "§7Cleared an empty socket.", "§7Leeren Platz aufgeräumt."));
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
        inventory.setItem(INFO_SLOT, info(gear, slots));
        inventory.setItem(GEAR_SLOT, gear == null ? gearHint() : gear);
        int open = capacity(gear);
        for (int i = 0; i < SOCKET_SLOTS.length; i++) {
            BoosterType type = i < slots.length ? slots[i] : null;
            inventory.setItem(SOCKET_SLOTS[i], type != null ? socketIcon(type, i + 1)
                    : i < open ? emptySocket(i + 1) : graftSocket(i + 1));
        }
        inventory.setItem(GRAFT_INFO_SLOT, de.aetherion.items.util.GuiIcons.icon(named(Material.BONE, "§6Bonegrafts",
                "§7Four more sockets, one per graft.",
                "§7Graft at the §6Heartforge §7in Jawcamp",
                "§7with Titan Bone, Titan Marrow and Heart Embers.",
                gear == null ? "§8No item placed." : "§7This item: §f" + (open - BoosterLimits.MAX_TOTAL) + "§8/§f"
                        + BoosterLimits.MAX_GRAFTS + " §7grafts."), de.aetherion.items.util.GuiIcons.SPACER));
        inventory.setItem(VANILLA_ANVIL_SLOT, vanillaAnvil());
        inventory.setItem(CLOSE_SLOT, close());
    }

    private void openVanillaAnvil(Player player) {
        player.closeInventory();
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openAnvil(player.getLocation(), true);
            }
        });
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
                "§c§lDO NOT EDIT §c§lin a vanilla anvil",
                "§cRenaming custom items there breaks",
                "§ctheir stats and sockets.",
                "",
                "§7Place a tool, weapon, or armor.",
                "§7" + filled + "§8/§f" + capacity(gear) + " §7socketed.",
                "§7Insert or swap anytime — they apply while socketed.",
                "§7Upgrades and crafts keep these sockets.",
                gear == null ? "§8Empty" : "§aItem in the center slot.");
        return item;
    }

    private ItemStack gearHint() {
        return named(Material.HOPPER, "§eItem",
                "§7Click, drag or shift-click a tool,",
                "§7weapon or armour here.",
                "§7Shift-click it again to take it back.",
                "§7Hold a dungeon core and click to infuse.");
    }

    private int capacity(ItemStack gear) {
        return gear == null ? BoosterLimits.MAX_TOTAL : items.getItemStats(gear).boosterCapacity();
    }

    private ItemStack graftSocket(int number) {
        return de.aetherion.items.util.GuiIcons.icon(named(Material.BLACK_STAINED_GLASS_PANE, "§8Socket " + number,
                "§6Bonegraft socket",
                "§7Opens with a graft at the Heartforge (Jawcamp)."), de.aetherion.items.util.GuiIcons.SPACER);
    }

    private ItemStack emptySocket(int number) {
        return de.aetherion.items.util.GuiIcons.icon(named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Socket " + number,
                "§8Empty",
                "§7Click or drag a booster here.",
                "§8Shift-click a booster in your bag: next free slot."), de.aetherion.items.util.GuiIcons.SPACER);
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
                    "§7Socket §f" + number,
                    "§aActive while socketed.",
                    "§eClick §7to take it on the cursor",
                    "§eShift-click §7to put it in your bag"
            ));
            meta.getPersistentDataContainer().set(ItemKeys.guiDisplay(), PersistentDataType.BYTE, (byte) 1);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack pane() {
        return de.aetherion.items.util.GuiIcons.spacer();
    }

    private ItemStack close() {
        return de.aetherion.items.util.GuiIcons.icon(named(Material.BARRIER, "§cClose", "§7Your item returns to you."),
                de.aetherion.items.util.GuiIcons.CLOSE);
    }

    private ItemStack vanillaAnvil() {
        return named(Material.ANVIL, "§fVanilla Anvil",
                "§c§lDO NOT EDIT",
                "§c§lDon't rename or modify custom",
                "§c§litems, stats or sockets here —",
                "§c§lit breaks them.",
                "",
                "§7Only for: dungeon cores, repair,",
                "§7vanilla rename of plain items.",
                "§8Boosters stay in their sockets.");
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
