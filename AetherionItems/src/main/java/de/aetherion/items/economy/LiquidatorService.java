package de.aetherion.items.economy;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
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

/**
 * Guild-Wars-style liquidator: turn resources / coins into Aether Crystals
 * (account shards), or cash crystals back to coins at a harsh spread.
 *
 * <p>Crystals feed the Aether Shop. Intentionally not first-hour reachable.
 */
public final class LiquidatorService implements Listener {

    public static final String TITLE = "§bCrystal Liquidator";
    public static final String NPC_ID = "liquidator";

    /** Coins to buy one crystal — mid/late progression. */
    public static final long BUY_COINS = 20_000L;
    /** Coins paid when selling one crystal — intentional sink vs buy. */
    public static final long SELL_COINS = 7_500L;
    /** Listed resource value needed per crystal when liquidating mats. */
    public static final long MAT_VALUE_PER_CRYSTAL = 40_000L;

    public static final int INFO_SLOT = 4;
    public static final int BUY_1 = 19;
    public static final int BUY_10 = 20;
    public static final int BUY_50 = 21;
    public static final int SELL_1 = 23;
    public static final int SELL_10 = 24;
    public static final int SELL_50 = 25;
    public static final int CONVERT_SLOT = 40;
    public static final int CLOSE_SLOT = 49;

    private static final int[] DEPOSIT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            28, 29, 30, 31, 32, 33, 34
    };

    private final AetherionItems plugin;
    private final ItemValueService values;
    private final CoinService coins;
    private final ShardService crystals;

    public LiquidatorService(
            AetherionItems plugin,
            ItemValueService values,
            CoinService coins,
            ShardService crystals
    ) {
        this.plugin = plugin;
        this.values = values;
        this.coins = coins;
        this.crystals = crystals;
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.AMETHYST_CLUSTER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bCrystal Liquidator Anchor");
            meta.setLore(List.of(
                    "§7DEV · place the crystal liquidator.",
                    "",
                    "§eRight-click a block to spawn him.",
                    "§eSneak + right-click §7despawns",
                    "§7the nearest liquidator.",
                    "",
                    "§8Buys mats / coins → crystals.",
                    "§8Also cashes crystals → coins."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.liquidatorAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.liquidatorAnchor(), PersistentDataType.BYTE);
    }

    public static boolean isLiquidator(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return false;
        }
        String id = entity.getPersistentDataContainer().get(ItemKeys.traderNpc(), PersistentDataType.STRING);
        return NPC_ID.equals(id);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE);
        paintChrome(inventory);
        refreshChrome(player, inventory);
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
            player.sendMessage("§cRight-click a block to place the Crystal Liquidator.");
            return;
        }
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        location.setYaw(player.getLocation().getYaw() + 180F);
        if (spawn(location)) {
            player.sendMessage("§aSpawned Crystal Liquidator.");
        } else {
            player.sendMessage("§cFailed to spawn Crystal Liquidator (FancyNPC / Quests missing?).");
        }
    }

    /** Replace leftover villager liquidators with FancyNPC hosts after boot. */
    public void migrateLegacyVillagers() {
        int converted = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            if (world == null) {
                continue;
            }
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (!isLiquidator(entity)) {
                    continue;
                }
                Location at = entity.getLocation().clone();
                entity.remove();
                if (spawn(at)) {
                    converted++;
                }
            }
        }
        if (converted > 0) {
            plugin.getLogger().info("Migrated " + converted + " Crystal Liquidator villager(s) → FancyNPC.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isLiquidator(event.getRightClicked())) {
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
        if (isLiquidator(event.getEntity())) {
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

        // Allow moving items into/out of the deposit panes and the player inventory.
        if (raw >= top.getSize() || isDepositSlot(raw)) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshChrome(player, top));
            return;
        }

        event.setCancelled(true);
        if (raw < 0) {
            return;
        }

        if (raw == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (raw == BUY_1) {
            buyCrystals(player, 1L);
        } else if (raw == BUY_10) {
            buyCrystals(player, 10L);
        } else if (raw == BUY_50) {
            buyCrystals(player, 50L);
        } else if (raw == SELL_1) {
            sellCrystals(player, 1L);
        } else if (raw == SELL_10) {
            sellCrystals(player, 10L);
        } else if (raw == SELL_50) {
            sellCrystals(player, 50L);
        } else if (raw == CONVERT_SLOT) {
            liquidateDeposit(player, top);
        }
        refreshChrome(player, top);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize() && !isDepositSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshChrome(player, event.getView().getTopInventory()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        returnDeposit(player, event.getInventory());
    }

    public boolean despawn(org.bukkit.entity.Entity entity) {
        if (!isLiquidator(entity)) {
            return false;
        }
        entity.remove();
        return true;
    }

    private void buyCrystals(Player player, long amount) {
        long cost = Math.multiplyExact(BUY_COINS, amount);
        if (!coins.take(player, cost)) {
            player.sendMessage("§cNeed §f" + format(cost) + " coins§c for §b" + amount + " §ccrystal"
                    + (amount == 1L ? "" : "s") + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.9f);
            return;
        }
        crystals.add(player, amount);
        player.sendMessage("§bLiquidator §8» §7Bought §b" + amount + " crystal" + (amount == 1L ? "" : "s")
                + " §8· §c-" + format(cost) + " coins");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.35f);
    }

    private void sellCrystals(Player player, long amount) {
        if (!crystals.take(player, amount)) {
            player.sendMessage("§cYou only have §b" + crystals.formatted(player) + " §ccrystals.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.9f);
            return;
        }
        long payout = Math.multiplyExact(SELL_COINS, amount);
        coins.add(player, payout);
        player.sendMessage("§bLiquidator §8» §7Cashed §b" + amount + " crystal" + (amount == 1L ? "" : "s")
                + " §8· §a+" + format(payout) + " coins");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.1f);
    }

    private void liquidateDeposit(Player player, Inventory inventory) {
        long totalValue = 0L;
        int stacks = 0;
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!isLiquidatable(stack)) {
                player.sendMessage("§cOnly compressed / compacted / refined resources.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.85f);
                return;
            }
            totalValue += values.valueOf(stack);
            stacks++;
        }
        if (stacks == 0) {
            player.sendMessage("§7Put compressed, compacted, or refined resources in the panes.");
            return;
        }
        long gained = totalValue / MAT_VALUE_PER_CRYSTAL;
        if (gained <= 0L) {
            player.sendMessage("§cToo thin. Need about §f" + format(MAT_VALUE_PER_CRYSTAL)
                    + " §clisted value per crystal.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);
            return;
        }
        for (int slot : DEPOSIT_SLOTS) {
            inventory.setItem(slot, null);
        }
        crystals.add(player, gained);
        player.sendMessage("§bLiquidator §8» §7Liquidated §f" + stacks + " §7stack"
                + (stacks == 1 ? "" : "s") + " §8· §b+" + gained + " crystal" + (gained == 1L ? "" : "s")
                + " §8(§7" + format(totalValue) + " value§8)");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.85f, 1.4f);
    }

    private boolean isLiquidatable(ItemStack stack) {
        String id = plugin.getItemManager() == null ? null : plugin.getItemManager().getItemId(stack);
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        return CompressedResource.isTradeable(lower)
                && (lower.startsWith("compressed_")
                || lower.startsWith("compacted_")
                || lower.startsWith("refined_"));
    }

    private void returnDeposit(Player player, Inventory inventory) {
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, null);
            player.getInventory().addItem(stack).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private void paintChrome(Inventory inventory) {
        ItemStack pane = button(Material.BLACK_STAINED_GLASS_PANE, " ", "");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        for (int slot : DEPOSIT_SLOTS) {
            inventory.setItem(slot, null);
        }
    }

    private void refreshChrome(Player player, Inventory inventory) {
        long previewValue = 0L;
        int previewStacks = 0;
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (isLiquidatable(stack)) {
                previewValue += values.valueOf(stack);
                previewStacks++;
            }
        }
        long previewCrystals = previewValue / MAT_VALUE_PER_CRYSTAL;

        inventory.setItem(INFO_SLOT, button(Material.AMETHYST_SHARD, "§bAether Crystals",
                "§f" + crystals.formatted(player) + " §7crystals",
                "§6" + format(coins.get(player)) + " §7coins",
                "",
                "§7Buy: §f" + format(BUY_COINS) + " §7coins / crystal",
                "§7Sell: §f" + format(SELL_COINS) + " §7coins / crystal",
                "§7Mats: §f" + format(MAT_VALUE_PER_CRYSTAL) + " §7value / crystal",
                "",
                "§8Brutal on purpose. Not a day-one grind."));

        inventory.setItem(BUY_1, button(Material.EMERALD, "§aBuy 1 Crystal",
                "§7Cost: §f" + format(BUY_COINS) + " coins",
                "§eClick to buy."));
        inventory.setItem(BUY_10, button(Material.EMERALD, "§aBuy 10 Crystals",
                "§7Cost: §f" + format(BUY_COINS * 10L) + " coins",
                "§eClick to buy."));
        inventory.setItem(BUY_50, button(Material.EMERALD_BLOCK, "§aBuy 50 Crystals",
                "§7Cost: §f" + format(BUY_COINS * 50L) + " coins",
                "§8A serious pile.",
                "§eClick to buy."));

        inventory.setItem(SELL_1, button(Material.GOLD_NUGGET, "§6Sell 1 Crystal",
                "§7Payout: §f" + format(SELL_COINS) + " coins",
                "§eClick to sell."));
        inventory.setItem(SELL_10, button(Material.GOLD_INGOT, "§6Sell 10 Crystals",
                "§7Payout: §f" + format(SELL_COINS * 10L) + " coins",
                "§eClick to sell."));
        inventory.setItem(SELL_50, button(Material.GOLD_BLOCK, "§6Sell 50 Crystals",
                "§7Payout: §f" + format(SELL_COINS * 50L) + " coins",
                "§eClick to sell."));

        List<String> convertLore = new ArrayList<>();
        convertLore.add("§7Deposit compressed / compacted /");
        convertLore.add("§7refined resources above.");
        convertLore.add("");
        if (previewStacks > 0) {
            convertLore.add("§7Stacks: §f" + previewStacks);
            convertLore.add("§7Value: §f" + format(previewValue));
            convertLore.add("§bCrystals: §f" + previewCrystals);
        } else {
            convertLore.add("§8Empty deposit.");
        }
        convertLore.add("");
        convertLore.add("§eClick to liquidate.");
        inventory.setItem(CONVERT_SLOT, button(Material.HOPPER, "§bLiquidate Deposit",
                convertLore.toArray(String[]::new)));

        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "§cClose", "§7Deposited items come back."));
    }

    private boolean spawn(Location location) {
        // Strip legacy villager hosts near the spot.
        if (location.getWorld() != null) {
            for (org.bukkit.entity.Entity entity : location.getWorld().getNearbyEntities(location, 2.5, 2.5, 2.5)) {
                if (isLiquidator(entity)) {
                    entity.remove();
                }
            }
        }
        try {
            Object spawned = Class.forName("de.aetherion.quests.service.QuestNPCSpawnService")
                    .getDeclaredConstructor()
                    .newInstance();
            Object result = spawned.getClass()
                    .getMethod("spawnNPC", String.class, Location.class)
                    .invoke(spawned, NPC_ID, location);
            return result != null;
        } catch (ReflectiveOperationException | NoClassDefFoundError ex) {
            plugin.getLogger().warning("Crystal Liquidator FancyNPC spawn failed: " + ex.getMessage());
            return false;
        }
    }

    private void despawnNearest(Player player) {
        // Legacy villager hosts first.
        org.bukkit.entity.Entity closest = null;
        double best = 8.0;
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (!isLiquidator(entity)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < best * best) {
                best = Math.sqrt(distance);
                closest = entity;
            }
        }
        if (closest != null) {
            closest.remove();
            player.sendMessage("§eDespawned legacy Crystal Liquidator villager.");
            return;
        }
        try {
            Object service = Class.forName("de.aetherion.quests.service.QuestNPCSpawnService")
                    .getDeclaredConstructor()
                    .newInstance();
            Object ok = service.getClass().getMethod("unload", String.class).invoke(service, NPC_ID);
            if (ok instanceof Boolean b && b) {
                player.sendMessage("§eDespawned Crystal Liquidator.");
                return;
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
        }
        player.sendMessage("§cNo liquidator nearby.");
    }

    private static boolean isDepositSlot(int slot) {
        for (int deposit : DEPOSIT_SLOTS) {
            if (deposit == slot) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) {
                    if (line != null && !line.isEmpty()) {
                        lines.add(line);
                    }
                }
                meta.setLore(lines);
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
