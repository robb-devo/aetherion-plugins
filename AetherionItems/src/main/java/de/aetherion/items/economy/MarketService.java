package de.aetherion.items.economy;

import de.aetherion.core.persist.AtomicYaml;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MarketService implements Listener {

    public static final int BACK_SLOT = 45;
    public static final int PREV_SLOT = 46;
    public static final int INFO_SLOT = 47;
    public static final int NEXT_SLOT = 48;
    public static final int LIST_SLOT = 49;
    /** Toggle: everything on the board ↔ only your own listings. */
    public static final int MINE_SLOT = 50;
    /** Hop between Bazaar and Auction House — one market, two counters. */
    public static final int SWITCH_SLOT = 51;
    public static final int COLLECT_SLOT = 53;
    /** Centre of the listing grid — used for the empty-state card. */
    private static final int EMPTY_SLOT = 22;
    public static final int PAGE_SIZE = 45;
    public static final int MAX_LISTINGS = 8;

    private static final long BAZAAR_MS = 1000L * 60 * 60 * 24 * 7;
    private static final long AUCTION_MS = 1000L * 60 * 60 * 24;
    private static final int[] PRICE_PERCENTS = {50, 75, 100, 125, 150, 200, 250, 300};

    private final AetherionItems plugin;
    private final ItemValueService values;
    private final CoinService coins;
    private final File file;
    private final Map<UUID, MarketListing> listings = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> returns = new ConcurrentHashMap<>();
    private final Set<UUID> buying = ConcurrentHashMap.newKeySet();
    private final Object listingLock = new Object();

    public MarketService(AetherionItems plugin, ItemValueService values, CoinService coins) {
        this.plugin = plugin;
        this.values = values;
        this.coins = coins;
        this.file = new File(plugin.getDataFolder(), "market.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireDue, 20L * 30, 20L * 60);
    }

    public static ItemStack createBazaarAnchor() {
        return anchor(
                Material.CHEST,
                "§6Bazaar Anchor",
                ItemKeys.bazaarAnchor(),
                "§7DEV · place a resource bazaar.",
                "§7Players list Compressed / Compacted",
                "§7and vanilla resources here."
        );
    }

    public static ItemStack createAuctionAnchor() {
        return anchor(
                Material.GOLD_BLOCK,
                "§eAuction House Anchor",
                ItemKeys.auctionAnchor(),
                "§7DEV · place an auction house.",
                "§7Gear and tools only. Buyout listings."
        );
    }

    public static boolean isBazaarAnchor(ItemStack item) {
        return hasByte(item, ItemKeys.bazaarAnchor());
    }

    public static boolean isAuctionAnchor(ItemStack item) {
        return hasByte(item, ItemKeys.auctionAnchor());
    }

    public static boolean isMarketNpc(Entity entity) {
        return channelOf(entity) != null;
    }

    public void openBazaar(Player player) {
        if (blockDungeonMarket(player)) {
            return;
        }
        openBrowse(player, MarketChannel.BAZAAR, 0);
    }

    public void openAuction(Player player) {
        if (blockDungeonMarket(player)) {
            return;
        }
        openBrowse(player, MarketChannel.AUCTION, 0);
    }

    /** Capital market only — dungeon hub keeps AH/Bazaar displays stripped and unusable. */
    private static boolean blockDungeonMarket(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        if (!de.aetherion.items.farming.Crops.isDungeonWorld(player.getWorld())) {
            return false;
        }
        player.sendMessage("§7Market's in the Capital — not here on the dungeon hub.");
        return true;
    }

    public boolean despawn(Entity entity) {
        if (!isMarketNpc(entity)) {
            return false;
        }
        entity.remove();
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        MarketChannel channel = isBazaarAnchor(hand) ? MarketChannel.BAZAAR
                : isAuctionAnchor(hand) ? MarketChannel.AUCTION : null;
        if (channel == null) {
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
            despawnNearest(player, channel);
            return;
        }
        if (event.getClickedBlock() == null) {
            player.sendMessage("§cRight-click a block to place this NPC.");
            return;
        }
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        location.setYaw(player.getLocation().getYaw() + 180F);
        spawn(location, channel);
        player.sendMessage("§aSpawned the " + label(channel) + ".");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        MarketChannel channel = channelOf(event.getRightClicked());
        if (channel == null) {
            return;
        }
        if (de.aetherion.items.world.NpcRemoverListener.isRemover(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        if (blockDungeonMarket(event.getPlayer())) {
            return;
        }
        openBrowse(event.getPlayer(), channel, 0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isMarketNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        int topSize = event.getView().getTopInventory().getSize();
        boolean bag = event.getRawSlot() >= topSize
                && event.getClickedInventory() instanceof org.bukkit.inventory.PlayerInventory;
        if (holder instanceof PriceHolder price) {
            event.setCancelled(true);
            if (bag) {
                swapPricing(player, price, event.getSlot(), event.getCurrentItem());
                return;
            }
            handlePrice(player, price, event.getRawSlot());
            return;
        }
        if (holder instanceof ConfirmHolder confirm) {
            event.setCancelled(true);
            handleConfirm(player, confirm, event.getRawSlot());
            return;
        }
        if (holder instanceof BrowseHolder browse) {
            event.setCancelled(true);
            if (bag) {
                // Sell from the bag — no "hold it in your hand" gate.
                listFromBag(player, browse.channel, event.getSlot(), event.getCurrentItem());
                return;
            }
            handleBrowse(player, browse, event.getRawSlot(), event.getCurrentItem());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof BrowseHolder || holder instanceof PriceHolder || holder instanceof ConfirmHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof PriceHolder holder)) {
            return;
        }
        if (holder.committed || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        holder.committed = true;
        giveOrDrop(player, holder.item);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        closeOpenGuis(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        closeOpenGuis(event.getPlayer());
    }

    public void closeOpenGuis(Player player) {
        if (player == null) {
            return;
        }
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof BrowseHolder || holder instanceof PriceHolder || holder instanceof ConfirmHolder) {
            player.closeInventory();
        }
    }

    public void closeOpenGuisForAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            closeOpenGuis(player);
        }
    }

    private void handleBrowse(Player player, BrowseHolder holder, int slot, ItemStack clicked) {
        if (slot == BACK_SLOT) {
            de.aetherion.items.util.ManagerNav.openManager(player);
            return;
        }
        if (slot == PREV_SLOT) {
            if (holder.page > 0) {
                click(player);
                openBrowse(player, holder.channel, holder.page - 1, holder.mine);
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            if (holder.page + 1 < holder.pages) {
                click(player);
                openBrowse(player, holder.channel, holder.page + 1, holder.mine);
            }
            return;
        }
        if (slot == LIST_SLOT) {
            startListing(player, holder.channel);
            return;
        }
        if (slot == MINE_SLOT) {
            click(player);
            openBrowse(player, holder.channel, 0, !holder.mine);
            return;
        }
        if (slot == SWITCH_SLOT) {
            click(player);
            openBrowse(player, other(holder.channel), 0, holder.mine);
            return;
        }
        if (slot == COLLECT_SLOT) {
            collect(player);
            openBrowse(player, holder.channel, holder.page, holder.mine);
            return;
        }
        UUID listingId = listingId(clicked);
        if (listingId == null) {
            return;
        }
        MarketListing listing = listings.get(listingId);
        if (listing == null) {
            player.sendMessage("§cThat listing is gone.");
            openBrowse(player, holder.channel, holder.page, holder.mine);
            return;
        }
        // Own listing: ask first — a stray click must never pull a listing off the board.
        openConfirm(player, holder.channel, holder.page, holder.mine, listing,
                listing.seller().equals(player.getUniqueId()));
    }

    private void handlePrice(Player player, PriceHolder holder, int slot) {
        if (slot == 8) {
            // Cancel: item straight back to the bag, and back to the board you came from.
            if (!holder.committed) {
                holder.committed = true;
                giveOrDrop(player, holder.item);
            }
            click(player);
            openBrowse(player, holder.channel, 0);
            return;
        }
        if (slot < 0 || slot >= PRICE_PERCENTS.length || holder.committed) {
            return;
        }
        long unit = Math.max(1L, values.unitValue(holder.item));
        long base = unit * Math.max(1, holder.item.getAmount());
        long price = Math.max(1L, Math.round(base * (PRICE_PERCENTS[slot] / 100.0d)));
        holder.committed = true;
        createListing(player, holder.channel, holder.item, price);
        openBrowse(player, holder.channel, 0);
    }

    /** Price picker is open and the player clicks another stack in their bag: swap what's being listed. */
    private void swapPricing(Player player, PriceHolder holder, int bagSlot, ItemStack clicked) {
        if (holder.committed || clicked == null || clicked.getType().isAir()) {
            return;
        }
        MarketChannel target = routeFor(clicked, holder.channel);
        if (target == null) {
            reject(player);
            return;
        }
        if (!canList(player, target, clicked)) {
            return;
        }
        ItemStack previous = holder.item;
        holder.committed = true;
        giveOrDrop(player, previous);
        listFromBag(player, holder.channel, bagSlot, clicked);
        // Swap aborted (stack moved / merged): never leave a dead price picker open.
        if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
            openBrowse(player, holder.channel, 0);
        }
    }

    /**
     * Hand shortcut on the List button. The bag is the first-class path: any stack clicked in your
     * own inventory while a market is open goes straight to the price picker.
     */
    private void startListing(Player player, MarketChannel channel) {
        int held = player.getInventory().getHeldItemSlot();
        ItemStack hand = player.getInventory().getItem(held);
        if (hand != null && !hand.getType().isAir() && routeFor(hand, channel) != null) {
            listFromBag(player, channel, held, hand);
            return;
        }
        int ready = sellableStacks(player, channel);
        player.sendMessage(ready > 0
                ? "§eClick any item in your bag below §7to list it §8(§f" + ready + " §8ready here)."
                : "§7Nothing in your bag fits the " + label(channel) + ". "
                        + (channel == MarketChannel.BAZAAR ? "§8It takes resources." : "§8It takes gear and tools."));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, ready > 0 ? 1.4f : 0.8f);
    }

    /** Sell-from-bag core: validate, route to the right counter, lift the exact stack, open the price picker. */
    private void listFromBag(Player player, MarketChannel channel, int bagSlot, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        MarketChannel target = routeFor(clicked, channel);
        if (target == null) {
            reject(player);
            return;
        }
        if (!canList(player, target, clicked)) {
            return;
        }
        ItemStack current = player.getInventory().getItem(bagSlot);
        // The slot must still hold exactly what was clicked (no dupes from a lagging view).
        if (current == null || !current.isSimilar(clicked) || current.getAmount() != clicked.getAmount()) {
            return;
        }
        ItemStack listed = current.clone();
        player.getInventory().setItem(bagSlot, null);
        if (target != channel) {
            player.sendMessage("§7" + (target == MarketChannel.BAZAAR ? "Resources" : "Gear and tools")
                    + " sell on the §f" + label(target) + "§7 — listing it there.");
        }
        openPrice(player, target, listed);
    }

    private void openPrice(Player player, MarketChannel channel, ItemStack listed) {
        Inventory inventory = Bukkit.createInventory(
                new PriceHolder(channel, listed),
                9,
                de.aetherion.items.util.ManagerChrome.MARKET_LIST
        );
        long base = values.valueOf(listed);
        String what = "§f" + listed.getAmount() + "× " + displayName(listed);
        for (int i = 0; i < PRICE_PERCENTS.length; i++) {
            long price = Math.max(1L, Math.round(base * (PRICE_PERCENTS[i] / 100.0d)));
            String tag = PRICE_PERCENTS[i] == 100 ? " §a(fair)" : PRICE_PERCENTS[i] < 100 ? " §7(quick sale)" : "";
            inventory.setItem(i, button(
                    PRICE_PERCENTS[i] == 100 ? Material.GOLD_INGOT : Material.SUNFLOWER,
                    "§e" + PRICE_PERCENTS[i] + "% §8· §6" + format(price) + " coins" + tag,
                    "§7Listing: " + what,
                    "§7Suggested value: §f" + format(base),
                    listed.getAmount() > 1
                            ? "§7Per item: §6" + format(Math.max(1L, price / listed.getAmount())) + " coins"
                            : "§8Single item.",
                    "§7On the " + label(channel) + " for §f"
                            + timeLeft(channel == MarketChannel.AUCTION ? AUCTION_MS : BAZAAR_MS),
                    "",
                    "§eClick to list at this price.",
                    "§8Click another stack in your bag to swap."
            ));
        }
        inventory.setItem(8, button(Material.BARRIER, "§cCancel", "§7Puts " + what + " §7back in your bag."));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
    }

    /** Which counter takes this stack — the open one if it fits, else the other; null when neither does. */
    private MarketChannel routeFor(ItemStack stack, MarketChannel preferred) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        boolean resource = values.isResource(stack);
        boolean gear = values.isGear(stack);
        if (preferred == MarketChannel.BAZAAR && resource || preferred == MarketChannel.AUCTION && gear) {
            return preferred;
        }
        if (resource) {
            return MarketChannel.BAZAAR;
        }
        return gear ? MarketChannel.AUCTION : null;
    }

    private boolean canList(Player player, MarketChannel channel, ItemStack stack) {
        if (countOwn(player.getUniqueId(), channel) >= MAX_LISTINGS) {
            player.sendMessage("§cYou already have " + MAX_LISTINGS + " listings on the " + label(channel)
                    + ". §7Take one back or wait for a sale.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return false;
        }
        if (values.unitValue(stack) <= 0L) {
            player.sendMessage("§7That has no coin value — it can't be listed.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return false;
        }
        return true;
    }

    private void reject(Player player) {
        player.sendMessage("§7That can't be listed. §8Bazaar: resources · Auction House: gear and tools.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
    }

    /** Stacks in the bag (storage + hotbar) this counter would take. */
    private int sellableStacks(Player player, MarketChannel channel) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (channel == MarketChannel.BAZAAR ? values.isResource(stack) : values.isGear(stack)) {
                if (values.unitValue(stack) > 0L) {
                    count++;
                }
            }
        }
        return count;
    }

    private void createListing(Player player, MarketChannel channel, ItemStack item, long price) {
        long now = System.currentTimeMillis();
        MarketListing listing = new MarketListing(
                UUID.randomUUID(),
                channel,
                player.getUniqueId(),
                price,
                now,
                now + (channel == MarketChannel.AUCTION ? AUCTION_MS : BAZAAR_MS),
                item
        );
        listings.put(listing.id(), listing);
        save();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        player.sendMessage("§aListed §f" + item.getAmount() + "× " + displayName(item) + " §afor §6"
                + format(price) + " coins §8· §7" + label(channel) + ", "
                + countOwn(player.getUniqueId(), channel) + "/" + MAX_LISTINGS + " slots used.");
    }

    private void openConfirm(Player player, MarketChannel channel, int page, boolean mine, MarketListing listing,
                             boolean reclaim) {
        Inventory inventory = Bukkit.createInventory(new ConfirmHolder(channel, page, mine, listing.id(), reclaim), 27,
                de.aetherion.items.util.ManagerChrome.MARKET_CONFIRM);
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        long purse = coins.get(player);
        ItemStack preview = listing.item().clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            if (reclaim) {
                lore.add("§7Your listing: §6" + format(listing.price()) + " coins");
                lore.add("§7Time left: §f" + timeLeft(listing.expires() - System.currentTimeMillis()));
            } else {
                lore.add("§7Buyout: §6" + format(listing.price()) + " coins");
                lore.add("§7Your purse: §e" + format(purse) + " coins");
            }
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        inventory.setItem(13, preview);
        if (reclaim) {
            inventory.setItem(11, button(
                    Material.LIME_WOOL,
                    "§aTake it back",
                    "§7Removes the listing and returns",
                    "§7the item to your bag."
            ));
            inventory.setItem(15, button(
                    Material.RED_WOOL,
                    "§cKeep it listed",
                    "§7Back to " + label(channel) + "."
            ));
        } else if (purse < listing.price()) {
            inventory.setItem(11, button(
                    Material.LIME_WOOL,
                    "§cNot enough coins",
                    "§7You're short §6" + format(listing.price() - purse) + " coins§7."
            ));
            inventory.setItem(15, button(
                    Material.RED_WOOL,
                    "§cBack",
                    "§7Back to " + label(channel) + "."
            ));
        } else {
            inventory.setItem(11, button(
                    Material.LIME_WOOL,
                    "§aConfirm",
                    "§7Pay §6" + format(listing.price()) + " coins",
                    "§7and take this listing.",
                    "§8Purse after: §7" + format(purse - listing.price())
            ));
            inventory.setItem(15, button(
                    Material.RED_WOOL,
                    "§cCancel",
                    "§7Back to " + label(channel) + "."
            ));
        }
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private void handleConfirm(Player player, ConfirmHolder holder, int slot) {
        if (slot == 15) {
            click(player);
            openBrowse(player, holder.channel, holder.page, holder.mine);
            return;
        }
        if (slot != 11) {
            return;
        }
        if (!buying.add(player.getUniqueId())) {
            return;
        }
        try {
            MarketListing listing = listings.get(holder.listingId);
            if (listing == null) {
                player.sendMessage("§cThat listing is gone.");
                openBrowse(player, holder.channel, holder.page, holder.mine);
                return;
            }
            if (holder.reclaim) {
                if (listing.seller().equals(player.getUniqueId())) {
                    cancel(player, listing);
                }
            } else if (coins.get(player) < listing.price()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
                return;
            } else {
                buy(player, listing);
            }
            openBrowse(player, holder.channel, holder.page, holder.mine);
        } finally {
            buying.remove(player.getUniqueId());
        }
    }

    private void buy(Player player, MarketListing listing) {
        MarketListing held;
        synchronized (listingLock) {
            held = listings.remove(listing.id());
            if (held == null) {
                player.sendMessage("§cThat listing is gone.");
                return;
            }
            if (!coins.take(player, held.price())) {
                listings.put(held.id(), held);
                player.sendMessage("§cYou need §6" + format(held.price()) + " coins§c.");
                return;
            }
        }
        coins.add(held.seller(), held.price());
        giveOrDrop(player, held.item().clone());
        save();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
        player.sendMessage("§aBought §f" + held.item().getAmount() + "× " + displayName(held.item())
                + " §afor §6" + format(held.price()) + " coins§a.");
        Player seller = Bukkit.getPlayer(held.seller());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage("§6" + label(held.channel()) + " §8» §a+" + format(held.price())
                    + " coins §7· §f" + player.getName() + " §7bought your §f" + held.item().getAmount() + "× "
                    + displayName(held.item()));
            seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.5f);
        }
    }

    private void cancel(Player player, MarketListing listing) {
        MarketListing held;
        synchronized (listingLock) {
            held = listings.remove(listing.id());
        }
        if (held == null) {
            return;
        }
        giveOrDrop(player, held.item().clone());
        save();
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.1f);
        player.sendMessage("§eTook back §f" + held.item().getAmount() + "× " + displayName(held.item())
                + "§e. §7It's in your bag.");
    }

    private void collect(Player player) {
        expireDue();
        List<ItemStack> waiting = returns.remove(player.getUniqueId());
        if (waiting == null || waiting.isEmpty()) {
            player.sendMessage("§7Nothing to collect.");
            return;
        }
        int count = 0;
        for (ItemStack item : waiting) {
            giveOrDrop(player, item);
            count++;
        }
        save();
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.0f);
        player.sendMessage("§aCollected §f" + count + " §aexpired listing" + (count == 1 ? "" : "s") + ".");
    }

    private void openBrowse(Player player, MarketChannel channel, int page) {
        openBrowse(player, channel, page, false);
    }

    private void openBrowse(Player player, MarketChannel channel, int page, boolean mine) {
        expireDue();
        UUID me = player.getUniqueId();
        Comparator<MarketListing> order = channel == MarketChannel.BAZAAR
                // Resources: cheapest per item first, like a real bazaar book.
                ? Comparator.comparingDouble(MarketService::unitPrice)
                        .thenComparing(Comparator.comparingLong(MarketListing::created).reversed())
                : Comparator.comparingLong(MarketListing::created).reversed();
        List<MarketListing> shown = listings.values().stream()
                .filter(listing -> listing.channel() == channel)
                .filter(listing -> !mine || listing.seller().equals(me))
                .sorted(order)
                .toList();
        int pages = Math.max(1, (shown.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.min(Math.max(0, page), pages - 1);
        Inventory inventory = Bukkit.createInventory(
                new BrowseHolder(channel, safePage, pages, mine),
                54,
                channel == MarketChannel.BAZAAR
                        ? de.aetherion.items.util.ManagerChrome.BAZAAR
                        : de.aetherion.items.util.ManagerChrome.AUCTION
        );
        fill(inventory);
        int start = safePage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < shown.size(); i++) {
            inventory.setItem(i, icon(player, shown.get(start + i)));
        }
        int ready = sellableStacks(player, channel);
        if (shown.isEmpty()) {
            inventory.setItem(EMPTY_SLOT, button(
                    channel == MarketChannel.BAZAAR ? Material.BARREL : Material.ITEM_FRAME,
                    mine ? "§7You have nothing listed here" : "§7No listings yet",
                    mine ? "§8Your " + label(channel) + " listings show up here." : "§8Quiet counter today.",
                    "",
                    ready > 0 ? "§eClick an item in your bag below §7to list it." : "§7Nothing in your bag fits here.",
                    mine ? "§8Click the book again to see everyone's." : ""
            ));
        }
        int waiting = returns.getOrDefault(me, List.of()).size();
        int own = countOwn(me, channel);
        inventory.setItem(PREV_SLOT, safePage > 0
                ? button(Material.ARROW, "§ePrevious page", "§7Page " + safePage + "/" + pages)
                : button(Material.GRAY_STAINED_GLASS_PANE, "§8Previous page", "§8You're on the first page."));
        inventory.setItem(INFO_SLOT, button(
                Material.SUNFLOWER,
                "§6" + label(channel) + (mine ? " §8· §7your listings" : ""),
                "§7Balance: §e" + format(coins.get(player)) + " coins",
                channel == MarketChannel.BAZAAR
                        ? "§7Resources. Cheapest per item first."
                        : "§7Gear and tools. Buyout, newest first.",
                "§7Your listings: §f" + own + "/" + MAX_LISTINGS,
                "§7Page §f" + (safePage + 1) + "§7/§f" + pages + " §8· §7" + shown.size() + " listed",
                waiting > 0 ? "§eExpired returns: §f" + waiting + " §8(hopper)" : "§8No expired returns."
        ));
        inventory.setItem(NEXT_SLOT, safePage + 1 < pages
                ? button(Material.ARROW, "§eNext page", "§7Page " + (safePage + 2) + "/" + pages)
                : button(Material.GRAY_STAINED_GLASS_PANE, "§8Next page", "§8You're on the last page."));
        inventory.setItem(LIST_SLOT, button(
                Material.WRITABLE_BOOK,
                "§aSell from your bag",
                "§7Click any item in your inventory",
                "§7below — you pick the price next.",
                ready > 0 ? "§a" + ready + " §7stack" + (ready == 1 ? "" : "s") + " ready for this counter."
                        : "§8Nothing in your bag fits here.",
                "",
                "§8Click here to list what's in your hand."
        ));
        inventory.setItem(MINE_SLOT, button(
                mine ? Material.ENDER_EYE : Material.NAME_TAG,
                mine ? "§eShowing: your listings" : "§eShowing: everything",
                "§7Your listings here: §f" + own + "/" + MAX_LISTINGS,
                "",
                mine ? "§eClick to see the whole board." : "§eClick to see only yours."
        ));
        MarketChannel other = other(channel);
        inventory.setItem(SWITCH_SLOT, button(
                other == MarketChannel.BAZAAR ? Material.CHEST : Material.GOLD_INGOT,
                "§eGo to the " + label(other),
                other == MarketChannel.BAZAAR ? "§7Resources by the stack." : "§7Gear and tools, buyout.",
                "§7Your listings there: §f" + countOwn(me, other) + "/" + MAX_LISTINGS
        ));
        inventory.setItem(BACK_SLOT, de.aetherion.items.util.ManagerNav.button());
        inventory.setItem(COLLECT_SLOT, button(
                waiting > 0 ? Material.HOPPER : Material.GRAY_STAINED_GLASS_PANE,
                waiting > 0 ? "§eCollect returns" : "§8Collect returns",
                waiting > 0 ? "§a" + waiting + " expired listing" + (waiting == 1 ? "" : "s") + " waiting."
                        : "§8Nothing waiting.",
                waiting > 0 ? "§eClick to take them back." : "§8Unsold listings come back here."
        ));
        player.openInventory(inventory);
    }

    private static double unitPrice(MarketListing listing) {
        return listing.price() / (double) Math.max(1, listing.item().getAmount());
    }

    private ItemStack icon(Player viewer, MarketListing listing) {
        ItemStack icon = listing.item().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller());
            String name = seller.getName() == null ? "Unknown" : seller.getName();
            lore.add("");
            lore.add("§7Seller: §f" + name);
            lore.add("§7Buyout: §6" + format(listing.price()) + " coins");
            lore.add("§7Amount: §f" + listing.item().getAmount());
            if (listing.channel() == MarketChannel.BAZAAR && listing.item().getAmount() > 1) {
                lore.add("§7Per item: §6" + format(Math.max(1L, Math.round(unitPrice(listing)))) + " coins");
            }
            lore.add("§7Time left: §f" + timeLeft(listing.expires() - System.currentTimeMillis()));
            lore.add("");
            if (listing.seller().equals(viewer.getUniqueId())) {
                lore.add("§a✔ Your listing");
                lore.add("§eClick to take it back.");
            } else {
                lore.add("§eClick to review and buy.");
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(ItemKeys.listingId(), PersistentDataType.STRING, listing.id().toString());
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private void expireDue() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (MarketListing listing : List.copyOf(listings.values())) {
            if (!listing.expired(now)) {
                continue;
            }
            listings.remove(listing.id());
            returns.computeIfAbsent(listing.seller(), key -> new ArrayList<>()).add(listing.item().clone());
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    private int countOwn(UUID seller, MarketChannel channel) {
        int count = 0;
        for (MarketListing listing : listings.values()) {
            if (listing.seller().equals(seller) && listing.channel() == channel) {
                count++;
            }
        }
        return count;
    }

    private void spawn(Location location, MarketChannel channel) {
        String title = channel == MarketChannel.BAZAAR ? "§6Bazaar" : "§eAuction House";
        Villager villager = location.getWorld().spawn(location, Villager.class, spawned -> {
            spawned.setCustomName(title);
            spawned.setCustomNameVisible(true);
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setCollidable(false);
            spawned.setSilent(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setProfession(channel == MarketChannel.BAZAAR
                    ? Villager.Profession.FARMER
                    : Villager.Profession.WEAPONSMITH);
            spawned.getPersistentDataContainer().set(
                    channel == MarketChannel.BAZAAR ? ItemKeys.bazaarNpc() : ItemKeys.auctionNpc(),
                    PersistentDataType.STRING,
                    channel.name()
            );
        });
        villager.setProfession(channel == MarketChannel.BAZAAR
                ? Villager.Profession.FARMER
                : Villager.Profession.WEAPONSMITH);
    }

    private void despawnNearest(Player player, MarketChannel channel) {
        Entity closest = null;
        double best = 8.0;
        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (channelOf(entity) != channel) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < best * best) {
                best = Math.sqrt(distance);
                closest = entity;
            }
        }
        if (closest == null) {
            player.sendMessage("§cNo " + label(channel) + " nearby.");
            return;
        }
        closest.remove();
        player.sendMessage("§eDespawned the " + label(channel) + ".");
    }

    private static MarketChannel channelOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getPersistentDataContainer().has(ItemKeys.bazaarNpc(), PersistentDataType.STRING)) {
            return MarketChannel.BAZAAR;
        }
        if (entity.getPersistentDataContainer().has(ItemKeys.auctionNpc(), PersistentDataType.STRING)) {
            return MarketChannel.AUCTION;
        }
        return null;
    }

    private UUID listingId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.listingId(), PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (MarketListing listing : listings.values()) {
            String path = "listings." + listing.id();
            config.set(path + ".channel", listing.channel().name());
            config.set(path + ".seller", listing.seller().toString());
            config.set(path + ".price", listing.price());
            config.set(path + ".created", listing.created());
            config.set(path + ".expires", listing.expires());
            config.set(path + ".item", encode(listing.item()));
        }
        returns.forEach((id, stacks) -> {
            List<String> encoded = new ArrayList<>();
            for (ItemStack stack : stacks) {
                encoded.add(encode(stack));
            }
            config.set("returns." + id, encoded);
        });
        try {
            AtomicYaml.save(config, file, plugin.getLogger());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save market.yml: " + exception.getMessage());
        }
    }

    private void load() {
        AtomicYaml.recoverTemp(file, plugin.getLogger());
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("listings");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    MarketChannel channel = MarketChannel.valueOf(config.getString("listings." + key + ".channel", "BAZAAR"));
                    ItemStack item = decode(config.getString("listings." + key + ".item"));
                    if (item == null) {
                        continue;
                    }
                    listings.put(id, new MarketListing(
                            id,
                            channel,
                            UUID.fromString(config.getString("listings." + key + ".seller")),
                            config.getLong("listings." + key + ".price"),
                            config.getLong("listings." + key + ".created"),
                            config.getLong("listings." + key + ".expires"),
                            item
                    ));
                } catch (RuntimeException ignored) {
                }
            }
        }
        ConfigurationSection returnRoot = config.getConfigurationSection("returns");
        if (returnRoot != null) {
            for (String key : returnRoot.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    List<ItemStack> stacks = new ArrayList<>();
                    for (String encoded : config.getStringList("returns." + key)) {
                        ItemStack item = decode(encoded);
                        if (item != null) {
                            stacks.add(item);
                        }
                    }
                    if (!stacks.isEmpty()) {
                        returns.put(id, stacks);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private static MarketChannel other(MarketChannel channel) {
        return channel == MarketChannel.BAZAAR ? MarketChannel.AUCTION : MarketChannel.BAZAAR;
    }

    private static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.3f);
    }

    /** Display name for chat lines: custom name if set, otherwise the prettified material. */
    private static String displayName(ItemStack item) {
        if (item == null) {
            return "item";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName() + "§r";
        }
        String raw = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(raw.length());
        boolean up = true;
        for (char c : raw.toCharArray()) {
            out.append(up ? Character.toUpperCase(c) : c);
            up = c == ' ';
        }
        return out.toString();
    }

    private static String label(MarketChannel channel) {
        return channel == MarketChannel.BAZAAR ? "Bazaar" : "Auction House";
    }

    private static String timeLeft(long millis) {
        if (millis <= 0L) {
            return "expired";
        }
        long hours = millis / (1000L * 60 * 60);
        long minutes = (millis / (1000L * 60)) % 60;
        if (hours >= 24) {
            return (hours / 24) + "d " + (hours % 24) + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private static boolean hasByte(ItemStack item, org.bukkit.NamespacedKey key) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static ItemStack anchor(Material material, String name, org.bukkit.NamespacedKey key, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lines = new ArrayList<>(List.of(lore));
            lines.add("");
            lines.add("§eRight-click a block to spawn.");
            lines.add("§eSneak + right-click §7despawns nearby.");
            meta.setLore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 45; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String format(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    public static final class BrowseHolder implements InventoryHolder {
        private final MarketChannel channel;
        private final int page;
        private final int pages;
        /** Only the viewer's own listings. */
        private final boolean mine;

        private BrowseHolder(MarketChannel channel, int page, int pages, boolean mine) {
            this.channel = channel;
            this.page = page;
            this.pages = pages;
            this.mine = mine;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static final class PriceHolder implements InventoryHolder {
        private final MarketChannel channel;
        private final ItemStack item;
        private volatile boolean committed;

        private PriceHolder(MarketChannel channel, ItemStack item) {
            this.channel = channel;
            this.item = item;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static final class ConfirmHolder implements InventoryHolder {
        private final MarketChannel channel;
        private final int page;
        private final boolean mine;
        private final UUID listingId;
        /** True: the seller is taking their own listing back (not buying). */
        private final boolean reclaim;

        private ConfirmHolder(MarketChannel channel, int page, boolean mine, UUID listingId, boolean reclaim) {
            this.channel = channel;
            this.page = page;
            this.mine = mine;
            this.listingId = listingId;
            this.reclaim = reclaim;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
