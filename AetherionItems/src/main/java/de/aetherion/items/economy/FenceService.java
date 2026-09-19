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

public final class FenceService implements Listener {

    public static final String TITLE = "§8Silas Markup";
    public static final String NPC_ID = "fence";
    public static final int CLOSE_SLOT = 49;
    public static final long MARKUP = 8L;

    private static final int[] BUY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

    private final AetherionItems plugin;
    private final ItemValueService values;
    private final CoinService coins;
    private final CustomItem items;

    public FenceService(AetherionItems plugin, ItemValueService values, CoinService coins, CustomItem items) {
        this.plugin = plugin;
        this.values = values;
        this.coins = coins;
        this.items = items;
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Silas Markup Anchor");
            meta.setLore(List.of(
                    "§7DEV · place the boss-weapon fence.",
                    "",
                    "§eRight-click a block to spawn him.",
                    "§eSneak + right-click §7despawns",
                    "§7the nearest Silas.",
                    "",
                    "§8Sells boss uniques at extortion rates."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.fenceAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.fenceAnchor(), PersistentDataType.BYTE);
    }

    public static boolean isFence(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return false;
        }
        String id = entity.getPersistentDataContainer().get(ItemKeys.traderNpc(), PersistentDataType.STRING);
        return NPC_ID.equals(id);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE);
        fill(inventory);
        stock(player, inventory);
        inventory.setItem(4, button(Material.GOLD_INGOT, "§6Silas Markup",
                "§7Boss uniques. Receipts required.",
                "§7Prices are a war crime. On purpose."));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "§cClose", "§7He keeps the markup."));
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
            player.sendMessage("§cRight-click a block to place Silas Markup.");
            return;
        }
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        location.setYaw(player.getLocation().getYaw() + 180F);
        spawn(location);
        player.sendMessage("§aSpawned Silas Markup.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isFence(event.getRightClicked())) {
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
        if (isFence(event.getEntity())) {
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
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (raw == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        Offer offer = offerAt(raw);
        if (offer == null) {
            return;
        }
        if (!unlocked(player, offer.bossId)) {
            player.sendMessage("§6Silas Markup §8» §7Kill §f" + offer.bossName + " §7once. Then we overcharge you.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);
            return;
        }
        buy(player, offer);
        stock(player, event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public boolean despawn(org.bukkit.entity.Entity entity) {
        if (!isFence(entity)) {
            return false;
        }
        entity.remove();
        return true;
    }

    private void spawn(Location location) {
        Villager villager = location.getWorld().spawn(location, Villager.class, spawned -> {
            spawned.setCustomName("§6Silas Markup");
            spawned.setCustomNameVisible(true);
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setCollidable(false);
            spawned.setSilent(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setProfession(Villager.Profession.LIBRARIAN);
            spawned.getPersistentDataContainer().set(ItemKeys.traderNpc(), PersistentDataType.STRING, NPC_ID);
        });
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setRecipes(List.of());
    }

    private void despawnNearest(Player player) {
        org.bukkit.entity.Entity closest = null;
        double best = 8.0;
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (!isFence(entity)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < best * best) {
                best = Math.sqrt(distance);
                closest = entity;
            }
        }
        if (closest == null) {
            player.sendMessage("§cNo Silas nearby.");
            return;
        }
        closest.remove();
        player.sendMessage("§eDespawned Silas Markup.");
    }

    private void buy(Player player, Offer offer) {
        if (!coins.take(player, offer.price)) {
            player.sendMessage("§cNeed §f" + format(offer.price) + " coins§c. He does not do payment plans.");
            return;
        }
        ItemStack bought = offer.create.get();
        player.getInventory().addItem(bought).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 0.7f);
        player.sendMessage("§6Silas Markup §8» §7" + offer.label + " §8· §c-" + format(offer.price)
                + " coins§7. Pleasure doing predatory business.");
    }

    private void fill(Inventory inventory) {
        ItemStack pane = button(Material.BLACK_STAINED_GLASS_PANE, " ", "");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private void stock(Player player, Inventory inventory) {
        Offer[] offers = offers();
        for (int i = 0; i < BUY_SLOTS.length; i++) {
            if (i >= offers.length) {
                break;
            }
            inventory.setItem(BUY_SLOTS[i], shopIcon(player, offers[i]));
        }
    }

    private Offer[] offers() {
        return new Offer[]{
                offer("warped_blade", "Warped Blade", "hollow_lurker", "Hollow Lurker", items::createWarpedBlade),
                offer("hollow_longbow", "Hollow Longbow", "hollow_lurker", "Hollow Lurker", items::createHollowLongbow),
                offer("gravwell_cleaver", "Gravwell Cleaver", "pathwarden", "Pathwarden", items::createGravwellCleaver),
                offer("aetherblade", "Aetherblade", "mcnugget", "McNugget", items::createAetherblade),
                offer("bridged_axe", "Bridged Axe", "bridge_troll", "Bridge Troll", items::createBridgedAxe),
                offer("squids_boot", "Squid's Boot", "squidward", "Squidward", items::createSquidsBoot),
                offer("skuldugery_shortbow", "Skuldugery Shortbow", "skuldugery", "Skuldugery", items::createSkuldugeryShortbow),
                offer("aetherion_void_stick", "Void Stick", "aetherion", "Aetherion", items::createAetherionVoidStick),
                offer("staff_of_technical_difficulties", "Staff of Technical Difficulties",
                        "sir_balthazar", "Sir Balthazar", items::createStaffOfTechnicalDifficulties),
                offer("void_vacuum_charm", "Void Vacuum Charm", "lobby_cleaner", "The Lobby Cleaner", items::createVoidVacuumCharm),
                offer("thermal_core", "Thermal Core", "sparky", "Sparky", items::createThermalCore),
                offer("pickaxe_core_of_the_burrower", "Pickaxe Core of the Burrower",
                        "baron_von_wurm", "Baron von Wurm", items::createPickaxeCoreOfTheBurrower),
                offer("insolvent_ledger", "Insolvent Ledger", "insolvent_wither", "The Insolvent Wither",
                        items::createInsolventLedger)
        };
    }

    private Offer offer(String id, String label, String bossId, String bossName, Supplier<ItemStack> create) {
        long listed = Math.max(1L, values.unitValue(id));
        return new Offer(id, label, listed * MARKUP, listed, bossId, bossName, create);
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

    private ItemStack shopIcon(Player player, Offer offer) {
        boolean open = unlocked(player, offer.bossId);
        ItemStack item = offer.create.get();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add("§8Listed value: §7" + format(offer.listed) + " coins");
            lore.add("§6Silas price: §c" + format(offer.price) + " coins §8(×" + MARKUP + ")");
            lore.add("§8Receipt: §f" + offer.bossName);
            lore.add("");
            if (open) {
                lore.add("§eClick to buy. No refunds. Especially not morally.");
            } else {
                lore.add("§cLocked §8· §7Defeat §f" + offer.bossName + " §7once.");
                meta.setDisplayName("§8" + offer.label);
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(ItemKeys.fenceShop(), PersistentDataType.STRING, offer.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean unlocked(Player player, String bossId) {
        var codex = plugin.getCodex();
        return codex != null && codex.bossKills(player, bossId) > 0L;
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String format(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private record Offer(
            String id,
            String label,
            long price,
            long listed,
            String bossId,
            String bossName,
            Supplier<ItemStack> create
    ) {
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
