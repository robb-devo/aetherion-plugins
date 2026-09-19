package de.aetherion.items.economy;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;

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

public final class TraderService implements Listener {

    public static final String TITLE = "§8Trader";
    public static final int SELL_SLOT = 48;
    public static final int VANILLA_SLOT = 46;
    public static final int ALL_SLOT = 47;
    public static final int INFO_SLOT = 45;
    public static final int CLOSE_SLOT = 49;

    private final AetherionItems plugin;
    private final ItemValueService values;
    private final CoinService coins;

    public TraderService(AetherionItems plugin, ItemValueService values, CoinService coins) {
        this.plugin = plugin;
        this.values = values;
        this.coins = coins;
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Trader Anchor");
            meta.setLore(List.of(
                    "§7DEV · place a sell-only trader.",
                    "",
                    "§eRight-click a block to spawn him.",
                    "§eSneak + right-click §7despawns",
                    "§7the nearest trader.",
                    "",
                    "§8Buys resources, not gear."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.traderAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.traderAnchor(), PersistentDataType.BYTE);
    }

    public static boolean isTrader(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return false;
        }
        String id = entity.getPersistentDataContainer().get(ItemKeys.traderNpc(), PersistentDataType.STRING);
        return "trader".equals(id);
    }

    public void open(Player player) {
        if (player == null) {
            return;
        }
        if (de.aetherion.items.farming.Crops.isDungeonWorld(player.getWorld())) {
            player.sendMessage("§cTrading is disabled inside dungeons.");
            return;
        }
        var progress = plugin.progress();
        if (progress != null && progress.unlock(player, de.aetherion.items.progress.ProgressionService.Flag.TRADER)) {
            de.aetherion.items.progress.UnlockToast.show(
                    player,
                    "Bazaar & Auction House",
                    "Market tabs unlocked in the Manager"
            );
        }
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE);
        fill(inventory);
        inventory.setItem(INFO_SLOT, info(player, 0L));
        inventory.setItem(VANILLA_SLOT, vanillaButton(0L));
        inventory.setItem(ALL_SLOT, allButton(0L));
        inventory.setItem(SELL_SLOT, sellButton(0L));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "§cClose", "§7Unsold items come back."));
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
            player.sendMessage("§cRight-click a block to place the trader.");
            return;
        }
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        location.setYaw(player.getLocation().getYaw() + 180F);
        spawn(location);
        player.sendMessage("§aSpawned the trader.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isTrader(event.getRightClicked())) {
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
        if (isTrader(event.getEntity())) {
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
        if (raw == VANILLA_SLOT) {
            sellInventory(player, top, true);
            return;
        }
        if (raw == ALL_SLOT) {
            sellInventory(player, top, false);
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
        if (!isTrader(entity)) {
            return false;
        }
        entity.remove();
        return true;
    }

    private void spawn(Location location) {
        Villager villager = location.getWorld().spawn(location, Villager.class, spawned -> {
            spawned.setCustomName("§6Trader");
            spawned.setCustomNameVisible(true);
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setCollidable(false);
            spawned.setSilent(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setProfession(Villager.Profession.NITWIT);
            spawned.getPersistentDataContainer().set(ItemKeys.traderNpc(), PersistentDataType.STRING, "trader");
        });
        villager.setProfession(Villager.Profession.NITWIT);
    }

    private void despawnNearest(Player player) {
        org.bukkit.entity.Entity closest = null;
        double best = 8.0;
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (!isTrader(entity)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < best * best) {
                best = Math.sqrt(distance);
                closest = entity;
            }
        }
        if (closest == null) {
            player.sendMessage("§cNo trader nearby.");
            return;
        }
        closest.remove();
        player.sendMessage("§eDespawned the trader.");
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
            if (!values.traderTakes(item)) {
                skipped++;
                continue;
            }
            total += values.valueOf(item);
            sold += item.getAmount();
            inventory.setItem(slot, null);
        }
        if (total <= 0L) {
            player.sendMessage(skipped > 0
                    ? "§cThe trader does not buy those items yet."
                    : "§cPut items in the empty slots first.");
            refresh(player, inventory);
            return;
        }
        coins.add(player, total);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.15f);
        player.sendMessage("§6Trader §8» §a+" + format(total) + " coins §8(§f" + sold + " items§8)");
        if (skipped > 0) {
            player.sendMessage("§7Left " + skipped + " stacks. Gear sells on the Auction House.");
        }
        refresh(player, inventory);
    }

    private void sellInventory(Player player, Inventory inventory, boolean vanillaOnly) {
        long total = 0L;
        int sold = 0;
        var bag = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = bag.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (vanillaOnly ? !values.traderTakesVanilla(item) : !values.traderTakes(item)) {
                continue;
            }
            total += values.valueOf(item);
            sold += item.getAmount();
            bag.setItem(slot, null);
        }
        if (total <= 0L) {
            player.sendMessage(vanillaOnly
                    ? "§cNo vanilla resources in your inventory."
                    : "§cNothing the trader buys is in your inventory.");
            refresh(player, inventory);
            return;
        }
        coins.add(player, total);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.15f);
        player.sendMessage("§6Trader §8» §a+" + format(total) + " coins §8(§f" + sold + " items§8)");
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
            if (item != null && values.traderTakes(item)) {
                preview += values.valueOf(item);
            }
        }
        inventory.setItem(INFO_SLOT, info(player, preview));
        inventory.setItem(VANILLA_SLOT, vanillaButton(previewInventory(player, true)));
        inventory.setItem(ALL_SLOT, allButton(previewInventory(player, false)));
        inventory.setItem(SELL_SLOT, sellButton(preview));
    }

    private long previewInventory(Player player, boolean vanillaOnly) {
        long total = 0L;
        var bag = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = bag.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (vanillaOnly ? values.traderTakesVanilla(item) : values.traderTakes(item)) {
                total += values.valueOf(item);
            }
        }
        return total;
    }

    private ItemStack vanillaButton(long preview) {
        return button(
                Material.COBBLESTONE,
                preview <= 0L ? "§eSell All Vanilla" : "§eSell All Vanilla §8· §a" + format(preview),
                "§7Sells vanilla resources from",
                "§7your inventory. Not gear.",
                "",
                preview <= 0L ? "§8Nothing vanilla to sell." : "§aPayout: §f" + format(preview) + " coins",
                "§8Compressed items stay."
        );
    }

    private ItemStack allButton(long preview) {
        return button(
                Material.CHEST,
                preview <= 0L ? "§6Sell All" : "§6Sell All §8· §a" + format(preview),
                "§7Sells everything this trader buys",
                "§7from your inventory.",
                "§7Vanilla, compressed, compacted.",
                "",
                preview <= 0L ? "§8Nothing he takes." : "§aPayout: §f" + format(preview) + " coins",
                "§8Gear still goes to the Auction House."
        );
    }

    private ItemStack sellButton(long preview) {
        if (preview <= 0L) {
            return button(
                    Material.GOLD_INGOT,
                    "§6Sell",
                    "§7Put resources in the empty slots.",
                    "§7This button shows the coin payout",
                    "§7before you confirm.",
                    "",
                    "§8Nothing to sell yet."
            );
        }
        return button(
                Material.GOLD_INGOT,
                "§6Sell §8· §a" + format(preview) + " coins",
                "§7Click to sell everything in the grid.",
                "",
                "§aYou will receive: §f" + format(preview) + " coins",
                "",
                "§8This is the payout. Click to confirm."
        );
    }

    private ItemStack info(Player player, long preview) {
        return button(
                Material.SUNFLOWER,
                "§6Purse",
                "§7Balance: §e" + format(coins.get(player)) + " coins",
                "§7This sale: §a" + format(preview) + " coins",
                preview <= 0L ? "§8Put items in the grid first." : "§eConfirm on the Sell button.",
                "",
                "§8Values are low on purpose."
        );
    }

    private static boolean isSellSlot(int slot) {
        int row = slot / 9;
        int column = slot % 9;
        return row >= 1 && row <= 3 && column >= 1 && column <= 7;
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

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
