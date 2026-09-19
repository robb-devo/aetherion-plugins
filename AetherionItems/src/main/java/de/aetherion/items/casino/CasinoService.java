package de.aetherion.items.casino;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.CoinService;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CasinoService implements Listener {

    public static final String NPC_NAME = "§6Lucky Vince";
    public static final String PREFIX = "§6Lucky Vince §8» §f";
    public static final String LOBBY_TITLE = "§8Casino";

    static final int LOBBY_SLOTS_SLOT = 11;
    static final int LOBBY_INFO_SLOT = 13;
    static final int LOBBY_ROULETTE_SLOT = 15;
    static final int LOBBY_CLOSE_SLOT = 22;

    private static final String[] FLAVOR = {
            "Casino's right behind me. Pick a machine. Don't cry on the felt.",
            "Slots and roulette. Coins go in. Dignity stays out.",
            "I don't deal. I commentate. Machines do the dirty work.",
            "Hungry? The glass is. Feed it. Then blame me."
    };

    private static final String[] BIG_WIN_TAUNTS = {
            "Oh look — a temporary millionaire. Don't spend it all on therapy.",
            "The house blinked. I'll allow it. Once.",
            "Nice. Now do it again so I can laugh properly.",
            "Congrats. The felt is filing a complaint.",
            "Keep that energy. The next spin already hates you."
    };

    private final AetherionItems plugin;
    private final CoinService coins;
    private final CasinoGames games;
    private final Set<UUID> speaking = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> lastClickTick = new ConcurrentHashMap<>();

    public CasinoService(AetherionItems plugin, CoinService coins) {
        this.plugin = plugin;
        this.coins = coins;
        this.games = new CasinoGames(plugin, coins, this);
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Casino Anchor");
            meta.setLore(List.of(
                    "§7DEV · place Lucky Vince (FancyNPC).",
                    "",
                    "§eRight-click a block to spawn him.",
                    "§eSneak + right-click §7despawns",
                    "§7the nearest Vince / old villager host.",
                    "",
                    "§8Slots, roulette, coin bets.",
                    "§8Friends table. Joke floor."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.casinoAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.casinoAnchor(), PersistentDataType.BYTE);
    }

    public static boolean isCasinoNpc(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return false;
        }
        String id = entity.getPersistentDataContainer().get(ItemKeys.casinoNpc(), PersistentDataType.STRING);
        return "casino".equals(id);
    }

    public static void openFor(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getCasino() == null || player == null) {
            return;
        }
        plugin.getCasino().open(player);
    }

    public void open(Player player) {
        open(player, null);
    }

    public void open(Player player, String table) {
        if (player == null) {
            return;
        }
        if (de.aetherion.items.farming.Crops.isDungeonWorld(player.getWorld())) {
            player.sendMessage("§cThe house does not operate in dungeons.");
            return;
        }
        if (table != null) {
            switch (table.toLowerCase()) {
                case "slots", "slot", "slotmachine" -> {
                    games.openSlots(player);
                    return;
                }
                case "roulette", "wheel" -> {
                    games.openRoulette(player);
                    return;
                }
                default -> {
                }
            }
        }
        openLobby(player);
    }

    void openLobby(Player player) {
        Inventory inventory = Bukkit.createInventory(new LobbyHolder(), 27, LOBBY_TITLE);
        ItemStack pane = GuiItems.named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        inventory.setItem(LOBBY_SLOTS_SLOT, GuiItems.named(
                Material.JUKEBOX,
                "§eSlots",
                "§7Seven reels. Add colored lines.",
                "§7More lines, more coins on the table.",
                "§7Crowns if you hate money correctly.",
                "",
                "§eClick to sit down."
        ));
        inventory.setItem(LOBBY_INFO_SLOT, GuiItems.named(
                Material.SUNFLOWER,
                "§6Purse",
                "§7Balance: §e" + coins.formatted(player) + " coins",
                "",
                "§8Lucky Vince runs the floor.",
                "§8Coins only. Friends table."
        ));
        inventory.setItem(LOBBY_ROULETTE_SLOT, GuiItems.named(
                Material.ENDER_PEARL,
                "§cR§8o§cu§8l§ce§8t§ct§8e",
                "§7The wheel is a circle. Ball runs it.",
                "§7Red or black pays §f2x§7.",
                "§7Zero pays §a14x§7. Don't.",
                "",
                "§eClick to sit down."
        ));
        inventory.setItem(LOBBY_CLOSE_SLOT, GuiItems.named(Material.BARRIER, "§cClose", "§7Walk away with dignity."));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.35f, 1.35f);
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
            player.sendMessage("§cRight-click a block to place Lucky Vince.");
            return;
        }
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        location.setYaw(player.getLocation().getYaw() + 180F);
        if (!spawn(location)) {
            player.sendMessage("§cCould not spawn Lucky Vince — is AetherionQuests + FancyNpcs up?");
            return;
        }
        player.sendMessage("§aSpawned Lucky Vince (FancyNPC).");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isCasinoNpc(event.getRightClicked())) {
            return;
        }
        if (de.aetherion.items.world.NpcRemoverListener.isRemover(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        talk(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isCasinoNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCasinoFirework(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Firework firework)) {
            return;
        }
        if (firework.getPersistentDataContainer().has(ItemKeys.casinoFirework(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        speaking.remove(id);
        lastClickTick.remove(id);
        games.clear(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuiClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof LobbyHolder) {
            return;
        }
        games.handleClose(player, holder);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof LobbyHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            int slot = event.getRawSlot();
            if (slot == LOBBY_CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            if (slot == LOBBY_SLOTS_SLOT) {
                games.openSlots(player);
                return;
            }
            if (slot == LOBBY_ROULETTE_SLOT) {
                games.openRoulette(player);
            }
            return;
        }
        games.onClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof LobbyHolder || games.isTable(holder)) {
            event.setCancelled(true);
        }
    }

    public boolean despawn(org.bukkit.entity.Entity entity) {
        if (!isCasinoNpc(entity)) {
            return false;
        }
        entity.remove();
        return true;
    }

    private void talk(Player player) {
        int tick = Bukkit.getCurrentTick();
        UUID id = player.getUniqueId();
        Integer last = lastClickTick.get(id);
        if (last != null && tick - last < 10) {
            return;
        }
        lastClickTick.put(id, tick);
        if (speaking.contains(id)) {
            return;
        }
        speaking.add(id);
        say(player, FLAVOR[Math.floorMod(tick / 40, FLAVOR.length)]);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> speaking.remove(id), 20L);
    }

    /** Cheeky floor comment after a fat win — dialog only, no menu. */
    public static void razzBigWin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String line = BIG_WIN_TAUNTS[Math.floorMod(Bukkit.getCurrentTick() / 17, BIG_WIN_TAUNTS.length)];
        say(player, line);
    }

    static void say(Player player, String line) {
        player.sendMessage(PREFIX + line);
    }

    private boolean spawn(Location location) {
        // Strip legacy villager hosts near the spot.
        if (location.getWorld() != null) {
            for (org.bukkit.entity.Entity entity : location.getWorld().getNearbyEntities(location, 2.5, 2.5, 2.5)) {
                if (isCasinoNpc(entity)) {
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
                    .invoke(spawned, "vince", location);
            return result != null;
        } catch (ReflectiveOperationException | NoClassDefFoundError ex) {
            plugin.getLogger().warning("Lucky Vince FancyNPC spawn failed: " + ex.getMessage());
            return false;
        }
    }

    private void despawnNearest(Player player) {
        // Legacy villager hosts first.
        org.bukkit.entity.Entity closest = null;
        double best = 8.0;
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (!isCasinoNpc(entity)) {
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
            player.sendMessage("§eDespawned legacy Lucky Vince villager.");
            return;
        }
        try {
            Object service = Class.forName("de.aetherion.quests.service.QuestNPCSpawnService")
                    .getDeclaredConstructor()
                    .newInstance();
            Object ok = service.getClass().getMethod("unload", String.class).invoke(service, "vince");
            if (ok instanceof Boolean b && b) {
                player.sendMessage("§eDespawned Lucky Vince.");
                return;
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
        }
        player.sendMessage("§cNo casino NPC nearby.");
    }

    public static final class LobbyHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
