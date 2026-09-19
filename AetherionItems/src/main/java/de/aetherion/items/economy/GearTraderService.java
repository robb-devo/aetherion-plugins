package de.aetherion.items.economy;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.CustomItem;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class GearTraderService implements Listener {

    public static final String TITLE = "§8Gear Trader";
    public static final String NPC_ID = "gear";
    public static final int SELL_SLOT = 48;
    public static final int INV_SLOT = 47;
    public static final int INFO_SLOT = 45;
    public static final int CLOSE_SLOT = 49;

    private static final int[] BUY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 4};

    private final AetherionItems plugin;
    private final ItemValueService values;
    private final CoinService coins;
    private final CustomItem items;

    public GearTraderService(AetherionItems plugin, ItemValueService values, CoinService coins, CustomItem items) {
        this.plugin = plugin;
        this.values = values;
        this.coins = coins;
        this.items = items;
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Gear Trader Anchor");
            meta.setLore(List.of(
                    "§7DEV · place a starter gear trader.",
                    "",
                    "§eRight-click a block to spawn him.",
                    "§eSneak + right-click §7despawns",
                    "§7the nearest gear trader.",
                    "",
                    "§8Buys gear cheap. Sells first tools."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.gearTraderAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.gearTraderAnchor(), PersistentDataType.BYTE);
    }

    public static boolean isGearTrader(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return false;
        }
        String id = entity.getPersistentDataContainer().get(ItemKeys.traderNpc(), PersistentDataType.STRING);
        return NPC_ID.equals(id);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE);
        fill(inventory);
        stockShop(inventory);
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "§cClose", "§7Unsold gear comes back."));
        refresh(player, inventory);
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!isAnchor(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("aetherion.dev")) {
            player.sendMessage("§cDEV only.");
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            despawnNearest(player);
            return;
        }
        if (event.getClickedBlock() == null) {
            player.sendMessage("§cRight-click a block to place the gear trader.");
            return;
        }
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        location.setYaw(player.getLocation().getYaw() + 180F);
        spawn(location);
        player.sendMessage("§aSpawned the gear trader.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isGearTrader(event.getRightClicked())) {
            return;
        }
        if (de.aetherion.items.world.NpcRemoverListener.isRemover(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isGearTrader(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        if (raw >= top.getSize()) {
            return;
        }
        if (isSellSlot(raw)) {
            refreshLater(player);
            return;
        }
        event.setCancelled(true);
        if (raw == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (raw == SELL_SLOT) {
            sell(player, top);
            return;
        }
        if (raw == INV_SLOT) {
            sellInventory(player, top);
            return;
        }
        Offer offer = offerAt(raw);
        if (offer != null) {
            buy(player, offer);
            refresh(player, top);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize() && !isSellSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        returnItems(player, event.getInventory());
    }

    public boolean despawn(org.bukkit.entity.Entity entity) {
        if (!isGearTrader(entity)) {
            return false;
        }
        entity.remove();
        return true;
    }

    private void spawn(Location location) {
        Villager villager = location.getWorld().spawn(location, Villager.class, spawned -> {
            spawned.setCustomName("§6Gear Trader");
            spawned.setCustomNameVisible(true);
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setCollidable(false);
            spawned.setSilent(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setProfession(Villager.Profession.WEAPONSMITH);
            spawned.getPersistentDataContainer().set(ItemKeys.traderNpc(), PersistentDataType.STRING, NPC_ID);
        });
        villager.setProfession(Villager.Profession.WEAPONSMITH);
    }

    private void despawnNearest(Player player) {
        org.bukkit.entity.Entity closest = null;
        double best = 8.0;
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (!isGearTrader(entity)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < best * best) {
                best = Math.sqrt(distance);
                closest = entity;
            }
        }
        if (closest == null) {
            player.sendMessage("§cNo gear trader nearby.");
            return;
        }
        closest.remove();
        player.sendMessage("§eDespawned the gear trader.");
    }

    private void buy(Player player, Offer offer) {
        if (!coins.take(player, offer.price)) {
            player.sendMessage("§cNeed §f" + format(offer.price) + " coins§c.");
            return;
        }
        ItemStack bought = offer.create.get();
        player.getInventory().addItem(bought).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        player.sendMessage("§6Gear Trader §8» §7" + offer.label + " §8· §c-" + format(offer.price) + " coins");
    }

    private void sell(Player player, Inventory inventory) {
        long total = 0L;
        int sold = 0;
        int skipped = 0;
        for (int slot = 0; slot < 45; slot++) {
            if (!isSellSlot(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            long payout = values.gearBuyback(item);
            if (payout <= 0L) {
                skipped++;
                continue;
            }
            total += payout;
            sold += item.getAmount();
            inventory.setItem(slot, null);
        }
        if (total <= 0L) {
            player.sendMessage(skipped > 0
                    ? "§cHe only buys gear. Resources go to the other trader."
                    : "§cPut gear in the empty slots first.");
            refresh(player, inventory);
            return;
        }
        coins.add(player, total);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.85f);
        player.sendMessage("§6Gear Trader §8» §a+" + format(total) + " coins §8(§f" + sold + " items§8)");
        player.sendMessage("§7Lowball on purpose. The Auction House pays more.");
        refresh(player, inventory);
    }

    private void sellInventory(Player player, Inventory inventory) {
        long total = 0L;
        int sold = 0;
        var bag = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = bag.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            long payout = values.gearBuyback(item);
            if (payout <= 0L) {
                continue;
            }
            total += payout;
            sold += item.getAmount();
            bag.setItem(slot, null);
        }
        if (total <= 0L) {
            player.sendMessage("§cNo gear he wants in your inventory.");
            refresh(player, inventory);
            return;
        }
        coins.add(player, total);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.85f);
        player.sendMessage("§6Gear Trader §8» §a+" + format(total) + " coins §8(§f" + sold + " items§8)");
        player.sendMessage("§7Lowball on purpose. The Auction House pays more.");
        refresh(player, inventory);
    }

    private void returnItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < 45; slot++) {
            if (!isSellSlot(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.gearShop(), PersistentDataType.STRING)) {
                inventory.setItem(slot, null);
                continue;
            }
            inventory.setItem(slot, null);
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private void refreshLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                refresh(player, player.getOpenInventory().getTopInventory());
            }
        });
    }

    private void refresh(Player player, Inventory inventory) {
        long preview = 0L;
        for (int slot = 0; slot < 45; slot++) {
            if (!isSellSlot(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            long payout = values.gearBuyback(item);
            if (payout > 0L) {
                preview += payout;
            }
        }
        inventory.setItem(INFO_SLOT, info(player, preview));
        inventory.setItem(INV_SLOT, invButton(previewInventory(player)));
        inventory.setItem(SELL_SLOT, sellButton(preview));
        stockShop(inventory);
    }

    private long previewInventory(Player player) {
        long total = 0L;
        var bag = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            total += values.gearBuyback(bag.getItem(slot));
        }
        return total;
    }

    private void stockShop(Inventory inventory) {
        Offer[] offers = offers();
        for (int i = 0; i < BUY_SLOTS.length; i++) {
            if (i >= offers.length) {
                break;
            }
            inventory.setItem(BUY_SLOTS[i], shopIcon(offers[i]));
        }
    }

    private Offer[] offers() {
        return new Offer[]{
                new Offer("arrows", "Arrows x16", 6L, () -> new ItemStack(Material.ARROW, 16)),
                new Offer("simple_pickaxe", "Simple Pickaxe", 18L, items::createSimplePickaxe),
                new Offer("simple_axe", "Simple Axe", 15L, items::createSimpleAxe),
                new Offer("simple_sword", "Simple Sword", 18L, items::createSimpleSword),
                new Offer("simple_hoe", "Simple Hoe", 12L, items::createSimpleHoe),
                new Offer("wooden_rod", "Wooden Rod", 20L, items::createWoodenRod),
                new Offer("simple_shortbow", "Simple Shortbow", 22L, items::createSimpleShortbow)
        };
    }

    private Offer offerAt(int slot) {
        Offer[] offers = offers();
        for (int i = 0; i < BUY_SLOTS.length && i < offers.length; i++) {
            if (BUY_SLOTS[i] == slot) {
                return offers[i];
            }
        }
        return null;
    }

    private ItemStack shopIcon(Offer offer) {
        ItemStack item = offer.create.get();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add("§eBuy §8· §6" + format(offer.price) + " coins");
            lore.add("§8Starter stock. Go outside.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(ItemKeys.gearShop(), PersistentDataType.STRING, offer.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack invButton(long preview) {
        return button(
                Material.CHEST,
                preview <= 0L ? "§6Sell Inventory Gear" : "§6Sell Inventory Gear §8· §a" + format(preview),
                "§7Sells gear from your inventory.",
                "§7Pays about a third of listed value.",
                "",
                preview <= 0L ? "§8Nothing he takes." : "§aPayout: §f" + format(preview) + " coins",
                "§8Auction House still pays more."
        );
    }

    private ItemStack sellButton(long preview) {
        if (preview <= 0L) {
            return button(
                    Material.GOLD_NUGGET,
                    "§6Sell Gear",
                    "§7Put gear in the empty slots.",
                    "§7He pays less than the Auction House.",
                    "",
                    "§8Nothing to sell yet."
            );
        }
        return button(
                Material.GOLD_NUGGET,
                "§6Sell Gear §8· §a" + format(preview) + " coins",
                "§7Click to sell everything in the grid.",
                "",
                "§aYou will receive: §f" + format(preview) + " coins",
                "§8Lowball. Use the AH if you care."
        );
    }

    private ItemStack info(Player player, long preview) {
        return button(
                Material.SUNFLOWER,
                "§6Purse",
                "§7Balance: §e" + format(coins.get(player)) + " coins",
                "§7This sale: §a" + format(preview) + " coins",
                "",
                "§7Top row: starter tools and arrows.",
                "§7Cheap on purpose. Go explore.",
                "§8Gear buyback is a bad deal. That is the point."
        );
    }

    private static boolean isSellSlot(int slot) {
        for (int buy : BUY_SLOTS) {
            if (buy == slot) {
                return false;
            }
        }
        int row = slot / 9;
        int column = slot % 9;
        return row >= 2 && row <= 3 && column >= 1 && column <= 7;
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, " ", new String[0]);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!isSellSlot(slot)) {
                inventory.setItem(slot, pane.clone());
            }
        }
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0 && !(lore.length == 1 && lore[0].isEmpty())) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) {
                    if (line != null && !line.isEmpty()) {
                        lines.add(line);
                    }
                }
                if (!lines.isEmpty()) {
                    meta.setLore(lines);
                }
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String format(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private record Offer(String id, String label, long price, Supplier<ItemStack> create) {
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
