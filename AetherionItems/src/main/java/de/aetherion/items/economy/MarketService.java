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
    public static final int COLLECT_SLOT = 53;
    public static final int PAGE_SIZE = 45;
    public static final int MAX_LISTINGS = 8;

    private static final long BAZAAR_MS = 1000L * 60 * 60 * 24 * 7;
    private static final long AUCTION_MS = 1000L * 60 * 60 * 24;
    private static final int[] PRICE_PERCENTS = {50, 75, 100, 125, 150, 200, 250, 300};
    private static final int FAIR_INDEX = 2;

    private static final int SORT_SLOT = 50;
    private static final int MINE_SLOT = 51;
    private static final int EMPTY_SLOT = 22;
    private static final int PRICE_PREVIEW_SLOT = 4;
    private static final int PRICE_FIRST_SLOT = 9;
    private static final int PRICE_CANCEL_SLOT = 22;

    private static final String PREFIX = "§6Market §8» ";
    private static final String RULE = "§8────────────────";

    private final AetherionItems plugin;
    private final ItemValueService values;
    private final CoinService coins;
    private final File file;
    private final Map<UUID, MarketListing> listings = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> returns = new ConcurrentHashMap<>();
    private final Set<UUID> buying = ConcurrentHashMap.newKeySet();
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
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
        openBrowse(player, MarketChannel.BAZAAR, 0);
    }

    public void openAuction(Player player) {
        openBrowse(player, MarketChannel.AUCTION, 0);
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
        if (holder instanceof PriceHolder price) {
            event.setCancelled(true);
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
        views.remove(event.getPlayer().getUniqueId());
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
                pageTurn(player);
                openBrowse(player, holder.channel, holder.page - 1);
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            if (holder.page + 1 < holder.pages) {
                pageTurn(player);
                openBrowse(player, holder.channel, holder.page + 1);
            }
            return;
        }
        if (slot == SORT_SLOT) {
            View view = view(player);
            views.put(player.getUniqueId(), new View(view.sort().next(), view.mine()));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
            openBrowse(player, holder.channel, 0);
            return;
        }
        if (slot == MINE_SLOT) {
            View view = view(player);
            views.put(player.getUniqueId(), new View(view.sort(), !view.mine()));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, view.mine() ? 1.1f : 1.4f);
            openBrowse(player, holder.channel, 0);
            return;
        }
        if (slot == LIST_SLOT) {
            startListing(player, holder.channel);
            return;
        }
        if (slot == COLLECT_SLOT) {
            collect(player);
            openBrowse(player, holder.channel, holder.page);
            return;
        }
        UUID listingId = listingId(clicked);
        if (listingId == null) {
            return;
        }
        MarketListing listing = listings.get(listingId);
        if (listing == null) {
            player.sendMessage(PREFIX + "§cThat listing is gone.");
            openBrowse(player, holder.channel, holder.page);
            return;
        }
        if (listing.seller().equals(player.getUniqueId())) {
            cancel(player, listing);
            openBrowse(player, holder.channel, holder.page);
            return;
        }
        openConfirm(player, holder.channel, holder.page, listing);
    }

    private void handlePrice(Player player, PriceHolder holder, int slot) {
        if (slot == PRICE_CANCEL_SLOT) {
            player.closeInventory();
            return;
        }
        int index = slot - PRICE_FIRST_SLOT;
        if (index < 0 || index >= PRICE_PERCENTS.length) {
            return;
        }
        long price = priceAt(holder.item, index);
        holder.committed = true;
        createListing(player, holder.channel, holder.item, price);
        openBrowse(player, holder.channel, 0);
    }

    /** Same formula the listing is created with — what the button says is what gets listed. */
    private long priceAt(ItemStack item, int index) {
        long unit = Math.max(1L, values.unitValue(item));
        long base = unit * Math.max(1, item.getAmount());
        return Math.max(1L, Math.round(base * (PRICE_PERCENTS[index] / 100.0d)));
    }

    private void startListing(Player player, MarketChannel channel) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String refusal = listRefusal(player, channel, hand);
        if (refusal != null) {
            player.sendMessage(PREFIX + "§c" + refusal);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        ItemStack listed = hand.clone();
        player.getInventory().setItemInMainHand(null);
        Inventory inventory = Bukkit.createInventory(
                new PriceHolder(channel, listed),
                27,
                accent(channel) + "■ §0List · " + label(channel)
        );
        rails(inventory);

        int amount = Math.max(1, listed.getAmount());
        long fair = priceAt(listed, FAIR_INDEX);
        ItemStack preview = listed.clone();
        ItemMeta previewMeta = preview.getItemMeta();
        if (previewMeta != null) {
            List<String> lore = previewMeta.hasLore() ? new ArrayList<>(previewMeta.getLore()) : new ArrayList<>();
            lore.add(RULE);
            lore.add("§7Fair value  §6" + format(fair) + " coins"
                    + (amount > 1 ? " §8(" + format(Math.max(1L, fair / amount)) + " ea)" : ""));
            lore.add("§7Runs for    §f" + (channel == MarketChannel.AUCTION ? "24 hours" : "7 days"));
            lore.add("§8Unsold items wait under Collect.");
            previewMeta.setLore(lore);
            preview.setItemMeta(previewMeta);
        }
        inventory.setItem(PRICE_PREVIEW_SLOT, preview);

        for (int i = 0; i < PRICE_PERCENTS.length; i++) {
            int percent = PRICE_PERCENTS[i];
            long price = priceAt(listed, i);
            Material material = percent < 100 ? Material.IRON_INGOT
                    : percent == 100 ? Material.GOLD_INGOT
                    : percent <= 150 ? Material.RAW_GOLD
                    : Material.GOLD_BLOCK;
            String tone = percent < 100 ? "§7Quick sale — undercuts fair value."
                    : percent == 100 ? "§aFair value §7— the suggested price."
                    : percent <= 150 ? "§7A little above fair value."
                    : "§7Premium ask — may sit a while.";
            ItemStack choice = button(
                    material,
                    "§6" + format(price) + " coins §8· " + percent + "%",
                    tone,
                    amount > 1 ? "§8" + format(Math.max(1L, price / amount)) + " each" : "§8",
                    "",
                    "§e▸ Click to list at this price"
            );
            if (percent == 100) {
                glint(choice);
            }
            inventory.setItem(PRICE_FIRST_SLOT + i, choice);
        }
        inventory.setItem(PRICE_CANCEL_SLOT, button(Material.BARRIER, "§cCancel", "§7The item goes back to you."));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.2f);
    }

    /** Null when the held stack can be listed here, otherwise the player-facing reason. */
    private String listRefusal(Player player, MarketChannel channel, ItemStack hand) {
        if (hand == null || hand.getType().isAir()) {
            return "Hold the item you want to list.";
        }
        if (channel == MarketChannel.BAZAAR && !values.isResource(hand)) {
            return "The Bazaar is for resources. Gear goes to the Auction House.";
        }
        if (channel == MarketChannel.AUCTION && !values.isGear(hand)) {
            return "The Auction House is for gear and tools. Resources go to the Bazaar.";
        }
        if (countOwn(player.getUniqueId(), channel) >= MAX_LISTINGS) {
            return "You already have " + MAX_LISTINGS + " listings here.";
        }
        if (values.unitValue(hand) <= 0L) {
            return "That item has no coin value.";
        }
        return null;
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
        player.sendMessage(PREFIX + "§aListed §f" + displayName(item) + " §afor §6" + format(price) + " coins§a.");
    }

    private void openConfirm(Player player, MarketChannel channel, int page, MarketListing listing) {
        Inventory inventory = Bukkit.createInventory(
                new ConfirmHolder(channel, page, listing.id()),
                27,
                accent(channel) + "■ §0Confirm purchase"
        );
        rails(inventory);
        long purse = coins.get(player);
        boolean affordable = purse >= listing.price();

        ItemStack preview = listing.item().clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(RULE);
            lore.add("§7Seller      §f" + sellerName(listing));
            lore.add("§7Buyout      §6" + format(listing.price()) + " coins");
            lore.add("§7Ends in     §f" + timeLeft(listing.expires() - System.currentTimeMillis()));
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        inventory.setItem(13, preview);
        inventory.setItem(11, affordable
                ? button(
                        Material.LIME_CONCRETE,
                        "§aBuy now",
                        "§7Pay §6" + format(listing.price()) + " coins",
                        "§7Purse after: §e" + format(purse - listing.price()),
                        "",
                        "§e▸ Click to confirm")
                : button(
                        Material.GRAY_CONCRETE,
                        "§cNot enough coins",
                        "§7Price: §6" + format(listing.price()),
                        "§7Purse: §e" + format(purse),
                        "§7Short by §c" + format(listing.price() - purse)));
        inventory.setItem(15, button(
                Material.RED_CONCRETE,
                "§cBack",
                "§7Return to the " + label(channel) + "."
        ));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private void handleConfirm(Player player, ConfirmHolder holder, int slot) {
        if (slot == 15) {
            openBrowse(player, holder.channel, holder.page);
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
                player.sendMessage(PREFIX + "§cThat listing is gone.");
                openBrowse(player, holder.channel, holder.page);
                return;
            }
            buy(player, listing);
            openBrowse(player, holder.channel, holder.page);
        } finally {
            buying.remove(player.getUniqueId());
        }
    }

    private void buy(Player player, MarketListing listing) {
        MarketListing held;
        synchronized (listingLock) {
            held = listings.remove(listing.id());
            if (held == null) {
                player.sendMessage(PREFIX + "§cThat listing is gone.");
                return;
            }
            if (!coins.take(player, held.price())) {
                listings.put(held.id(), held);
                player.sendMessage(PREFIX + "§cYou need §6" + format(held.price()) + " coins§c.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                return;
            }
        }
        coins.add(held.seller(), held.price());
        giveOrDrop(player, held.item().clone());
        save();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
        player.sendMessage(PREFIX + "§aBought §f" + displayName(held.item()) + " §afor §6" + format(held.price()) + " coins§a.");
        Player seller = Bukkit.getPlayer(held.seller());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(accent(held.channel()) + label(held.channel()) + " §8» §a+" + format(held.price())
                    + " coins §7· §f" + player.getName() + " §7bought §f" + displayName(held.item()));
            seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
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
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 1.2f);
        player.sendMessage(PREFIX + "§eListing cancelled §7— §f" + displayName(held.item()) + " §7is back in your bag.");
    }

    private void collect(Player player) {
        expireDue();
        List<ItemStack> waiting = returns.remove(player.getUniqueId());
        if (waiting == null || waiting.isEmpty()) {
            player.sendMessage(PREFIX + "§7Nothing to collect.");
            return;
        }
        int count = 0;
        for (ItemStack item : waiting) {
            giveOrDrop(player, item);
            count++;
        }
        save();
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.1f);
        player.sendMessage(PREFIX + "§aCollected §f" + count + " §aunsold listing" + (count == 1 ? "" : "s") + ".");
    }

    private void openBrowse(Player player, MarketChannel channel, int page) {
        expireDue();
        View view = view(player);
        UUID self = player.getUniqueId();
        List<MarketListing> shown = listings.values().stream()
                .filter(listing -> listing.channel() == channel)
                .filter(listing -> !view.mine() || listing.seller().equals(self))
                .sorted(view.sort().order)
                .toList();
        int pages = Math.max(1, (shown.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.min(Math.max(0, page), pages - 1);
        Inventory inventory = Bukkit.createInventory(
                new BrowseHolder(channel, safePage, pages),
                54,
                accent(channel) + "■ §0" + label(channel)
                        + (view.mine() ? " §8· yours" : "")
                        + (pages > 1 ? " §8· " + (safePage + 1) + "/" + pages : "")
        );
        fill(inventory);
        int start = safePage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < shown.size(); i++) {
            inventory.setItem(i, icon(player, shown.get(start + i)));
        }
        if (shown.isEmpty()) {
            inventory.setItem(EMPTY_SLOT, emptyState(channel, view.mine()));
        }

        int waiting = returns.getOrDefault(self, List.of()).size();
        int own = countOwn(self, channel);
        if (safePage > 0) {
            inventory.setItem(PREV_SLOT, button(Material.ARROW, "§ePrevious page", "§8Page " + safePage + " of " + pages));
        }
        if (safePage + 1 < pages) {
            inventory.setItem(NEXT_SLOT, button(Material.ARROW, "§eNext page", "§8Page " + (safePage + 2) + " of " + pages));
        }
        inventory.setItem(INFO_SLOT, button(
                channel == MarketChannel.BAZAAR ? Material.BARREL : Material.GOLD_BLOCK,
                accent(channel) + "§l" + label(channel),
                channel == MarketChannel.BAZAAR
                        ? "§7Resources — compressed, compacted, vanilla."
                        : "§7Gear and tools. Buyout listings.",
                RULE,
                "§7Purse         §e" + format(coins.get(player)) + " coins",
                "§7Your listings §f" + own + "§8/" + MAX_LISTINGS,
                "§7Listings run  §f" + (channel == MarketChannel.AUCTION ? "24 hours" : "7 days"),
                "§7On the board  §f" + shown.size()
        ));
        inventory.setItem(LIST_SLOT, listButton(player, channel));
        inventory.setItem(SORT_SLOT, sortButton(view.sort()));
        inventory.setItem(MINE_SLOT, button(
                view.mine() ? Material.NAME_TAG : Material.PAPER,
                view.mine() ? "§aShowing: your listings" : "§7Showing: everyone",
                "§7You have §f" + own + " §7listed here.",
                "§8Click your own listing to take it back.",
                "",
                "§e▸ Click to " + (view.mine() ? "show everyone" : "show only yours")
        ));
        inventory.setItem(BACK_SLOT, de.aetherion.items.util.ManagerNav.button());
        ItemStack collectButton = waiting > 0
                ? button(Material.HOPPER, "§aCollect returns",
                        "§f" + waiting + " §7unsold listing" + (waiting == 1 ? "" : "s") + " waiting.",
                        "", "§e▸ Click to collect")
                : button(Material.HOPPER, "§7Collect returns", "§8Nothing waiting.",
                        "§8Unsold listings land here when they end.");
        if (waiting > 0) {
            glint(collectButton);
        }
        inventory.setItem(COLLECT_SLOT, collectButton);
        player.openInventory(inventory);
    }

    private ItemStack listButton(Player player, MarketChannel channel) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String refusal = listRefusal(player, channel, hand);
        if (refusal == null) {
            long fair = priceAt(hand, FAIR_INDEX);
            ItemStack ready = button(
                    Material.WRITABLE_BOOK,
                    "§aList item in hand",
                    "§f" + displayName(hand) + (hand.getAmount() > 1 ? " §8×" + hand.getAmount() : ""),
                    "§7Fair value §6" + format(fair) + " coins",
                    "",
                    "§e▸ Click to pick a price"
            );
            glint(ready);
            return ready;
        }
        return button(
                Material.BOOK,
                "§7List item in hand",
                "§8" + refusal,
                "",
                channel == MarketChannel.BAZAAR
                        ? "§7Hold a resource stack, then click."
                        : "§7Hold a weapon, armor piece or tool, then click."
        );
    }

    private ItemStack sortButton(Sort sort) {
        List<String> lore = new ArrayList<>();
        for (Sort option : Sort.values()) {
            lore.add((option == sort ? "§a▸ " : "§8  ") + option.label);
        }
        lore.add("");
        lore.add("§e▸ Click to cycle");
        return de.aetherion.items.util.GuiItems.named(Material.COMPARATOR, "§6Sort", lore);
    }

    private ItemStack emptyState(MarketChannel channel, boolean mine) {
        if (mine) {
            return button(
                    Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "§7You have nothing listed",
                    "§8Hold a stack and use §7List item in hand§8.",
                    "§8Your listings show up here."
            );
        }
        return button(
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                "§7The " + label(channel) + " is quiet",
                "§8No listings right now.",
                channel == MarketChannel.BAZAAR
                        ? "§8Be first — list some resources."
                        : "§8Be first — list a piece of gear."
        );
    }

    private ItemStack icon(Player viewer, MarketListing listing) {
        ItemStack icon = listing.item().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            boolean own = listing.seller().equals(viewer.getUniqueId());
            int amount = Math.max(1, listing.item().getAmount());
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(RULE);
            lore.add("§7Seller   " + (own ? "§aYou" : "§f" + sellerName(listing)));
            lore.add("§7Price    §6" + format(listing.price()) + " coins"
                    + (amount > 1 ? " §8(" + format(Math.max(1L, listing.price() / amount)) + " ea)" : ""));
            lore.add("§7Ends in  §f" + timeLeft(listing.expires() - System.currentTimeMillis()));
            lore.add("");
            lore.add(own ? "§c▸ Click to cancel and take it back" : "§e▸ Click to review and buy");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(ItemKeys.listingId(), PersistentDataType.STRING, listing.id().toString());
            if (own) {
                meta.setEnchantmentGlintOverride(true);
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private View view(Player player) {
        return views.getOrDefault(player.getUniqueId(), View.DEFAULT);
    }

    private static String sellerName(MarketListing listing) {
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller());
        return seller.getName() == null ? "Unknown" : seller.getName();
    }

    private static void pageTurn(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.25f);
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

    /** Browse page: listing grid stays open, a dark rail carries the controls (Manager panel look). */
    private static void fill(Inventory inventory) {
        ItemStack rail = button(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 45; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, rail.clone());
        }
    }

    /** 27-slot dialogs: dark top and bottom rails, calm grey middle row. */
    private static void rails(Inventory inventory) {
        ItemStack rail = button(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack body = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            inventory.setItem(slot, row == 1 ? body.clone() : rail.clone());
        }
    }

    private static ItemStack button(Material material, String name, String... lore) {
        return de.aetherion.items.util.GuiItems.named(material, name, lore);
    }

    private static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
    }

    private static String accent(MarketChannel channel) {
        return channel == MarketChannel.BAZAAR ? "§6" : "§3";
    }

    private static String displayName(ItemStack item) {
        if (item == null) {
            return "item";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName().replaceAll("§.", "");
        }
        String raw = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(raw.length());
        boolean upper = true;
        for (char c : raw.toCharArray()) {
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return out.toString();
    }

    private enum Sort {
        NEWEST("Newest first", Comparator.comparingLong(MarketListing::created).reversed()),
        CHEAPEST("Lowest price", Comparator.comparingLong(MarketListing::price)
                .thenComparing(Comparator.comparingLong(MarketListing::created).reversed())),
        ENDING("Ending soon", Comparator.comparingLong(MarketListing::expires));

        final String label;
        final Comparator<MarketListing> order;

        Sort(String label, Comparator<MarketListing> order) {
            this.label = label;
            this.order = order;
        }

        Sort next() {
            Sort[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private record View(Sort sort, boolean mine) {
        static final View DEFAULT = new View(Sort.NEWEST, false);
    }

    private static String format(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    public static final class BrowseHolder implements InventoryHolder {
        private final MarketChannel channel;
        private final int page;
        private final int pages;

        private BrowseHolder(MarketChannel channel, int page, int pages) {
            this.pages = pages;
            this.channel = channel;
            this.page = page;
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
        private final UUID listingId;

        private ConfirmHolder(MarketChannel channel, int page, UUID listingId) {
            this.channel = channel;
            this.page = page;
            this.listingId = listingId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
