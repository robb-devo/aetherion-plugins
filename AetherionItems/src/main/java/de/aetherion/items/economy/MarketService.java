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
            openBrowse(player, holder.channel, Math.max(0, holder.page - 1));
            return;
        }
        if (slot == NEXT_SLOT) {
            openBrowse(player, holder.channel, holder.page + 1);
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
            player.sendMessage("§cThat listing is gone.");
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
        if (slot == 8) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= PRICE_PERCENTS.length) {
            return;
        }
        long unit = Math.max(1L, values.unitValue(holder.item));
        long base = unit * Math.max(1, holder.item.getAmount());
        long price = Math.max(1L, Math.round(base * (PRICE_PERCENTS[slot] / 100.0d)));
        holder.committed = true;
        createListing(player, holder.channel, holder.item, price);
        openBrowse(player, holder.channel, 0);
    }

    private void startListing(Player player, MarketChannel channel) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage("§cHold the item you want to list.");
            return;
        }
        if (channel == MarketChannel.BAZAAR && !values.isResource(hand)) {
            player.sendMessage("§cThe Bazaar is for resources. Gear goes to the Auction House.");
            return;
        }
        if (channel == MarketChannel.AUCTION && !values.isGear(hand)) {
            player.sendMessage("§cThe Auction House is for gear and tools. Resources go to the Bazaar.");
            return;
        }
        if (countOwn(player.getUniqueId(), channel) >= MAX_LISTINGS) {
            player.sendMessage("§cYou already have " + MAX_LISTINGS + " listings here.");
            return;
        }
        if (values.unitValue(hand) <= 0L) {
            player.sendMessage("§cThat item has no coin value.");
            return;
        }
        ItemStack listed = hand.clone();
        player.getInventory().setItemInMainHand(null);
        Inventory inventory = Bukkit.createInventory(
                new PriceHolder(channel, listed),
                9,
                "§8List · " + label(channel)
        );
        long base = values.valueOf(listed);
        for (int i = 0; i < PRICE_PERCENTS.length; i++) {
            long price = Math.max(1L, Math.round(base * (PRICE_PERCENTS[i] / 100.0d)));
            inventory.setItem(i, button(
                    i == 2 ? Material.GOLD_INGOT : Material.SUNFLOWER,
                    "§e" + PRICE_PERCENTS[i] + "% §8· §6" + format(price) + " coins",
                    "§7Suggested value: §f" + format(base),
                    "",
                    "§eClick to list at this price."
            ));
        }
        inventory.setItem(8, button(Material.BARRIER, "§cCancel", "§7Returns the item."));
        player.openInventory(inventory);
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
        player.sendMessage("§aListed for §6" + format(price) + " coins§a.");
    }

    private void openConfirm(Player player, MarketChannel channel, int page, MarketListing listing) {
        Inventory inventory = Bukkit.createInventory(new ConfirmHolder(channel, page, listing.id()), 27, "§8Confirm purchase");
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        ItemStack preview = listing.item().clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7Buyout: §6" + format(listing.price()) + " coins");
            lore.add("§7Your purse: §e" + format(coins.get(player)) + " coins");
            lore.add("");
            lore.add("§eConfirm on the green wool.");
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        inventory.setItem(13, preview);
        inventory.setItem(11, button(
                Material.LIME_WOOL,
                "§aConfirm",
                "§7Pay §6" + format(listing.price()) + " coins",
                "§7and take this listing."
        ));
        inventory.setItem(15, button(
                Material.RED_WOOL,
                "§cCancel",
                "§7Back to " + label(channel) + "."
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
                player.sendMessage("§cThat listing is gone.");
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
        player.sendMessage("§aBought for §6" + format(held.price()) + " coins§a.");
        Player seller = Bukkit.getPlayer(held.seller());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage("§6" + label(held.channel()) + " §8» §a+" + format(held.price())
                    + " coins §7from §f" + player.getName());
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
        player.sendMessage("§eListing cancelled. Item returned.");
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
        player.sendMessage("§aCollected §f" + count + " §aexpired listing(s).");
    }

    private void openBrowse(Player player, MarketChannel channel, int page) {
        expireDue();
        List<MarketListing> shown = listings.values().stream()
                .filter(listing -> listing.channel() == channel)
                .sorted(Comparator.comparingLong(MarketListing::created).reversed())
                .toList();
        int pages = Math.max(1, (shown.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.min(Math.max(0, page), pages - 1);
        Inventory inventory = Bukkit.createInventory(
                new BrowseHolder(channel, safePage),
                54,
                (channel == MarketChannel.BAZAAR ? "§8Bazaar" : "§8Auction House")
                        + " §8· " + (safePage + 1) + "/" + pages
        );
        fill(inventory);
        int start = safePage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < shown.size(); i++) {
            inventory.setItem(i, icon(player, shown.get(start + i)));
        }
        int waiting = returns.getOrDefault(player.getUniqueId(), List.of()).size();
        inventory.setItem(PREV_SLOT, button(Material.ARROW, "§ePrevious page"));
        inventory.setItem(INFO_SLOT, button(
                Material.SUNFLOWER,
                "§6" + label(channel),
                "§7Balance: §e" + format(coins.get(player)) + " coins",
                channel == MarketChannel.BAZAAR
                        ? "§7Resources only. Trader also buys these."
                        : "§7Gear and tools. Buyout only.",
                "§7Your listings: §f" + countOwn(player.getUniqueId(), channel) + "/" + MAX_LISTINGS,
                waiting > 0 ? "§eExpired returns: §f" + waiting : "§8No expired returns."
        ));
        inventory.setItem(NEXT_SLOT, button(Material.ARROW, "§eNext page"));
        inventory.setItem(LIST_SLOT, button(
                Material.WRITABLE_BOOK,
                "§aList item in hand",
                "§7Hold the stack, then click here.",
                "§7You pick the price next."
        ));
        inventory.setItem(BACK_SLOT, de.aetherion.items.util.ManagerNav.button());
        inventory.setItem(COLLECT_SLOT, button(
                Material.HOPPER,
                "§eCollect returns",
                waiting > 0 ? "§a" + waiting + " expired listing(s)." : "§7Nothing waiting."
        ));
        player.openInventory(inventory);
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
            lore.add("§7Time left: §f" + timeLeft(listing.expires() - System.currentTimeMillis()));
            lore.add("");
            if (listing.seller().equals(viewer.getUniqueId())) {
                lore.add("§eClick to cancel and take it back.");
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

        private BrowseHolder(MarketChannel channel, int page) {
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
