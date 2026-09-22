package de.aetherion.items.menu;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.listener.LoadoutListener;
import de.aetherion.items.listener.StorageListener;
import de.aetherion.items.progress.ProgressionService;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AetherionManagerListener implements Listener {

    public static final int HOTBAR_SLOT = 8;

    /**
     * Hub applies a transfer at tick 12 and rewrites the inventory 30 ticks later (tick 42).
     * A later pass puts the manager back without racing that rewrite.
     */
    private static final long POST_TRANSFER_GIVE_TICKS = 50L;

    private static AetherionManagerListener instance;

    private final AetherionManager manager;

    /** Real stacks taken out of slot 8 before the transfer rewrite has landed. */
    private final Map<UUID, ItemStack> parkedSlot = new HashMap<>();

    private final Map<UUID, Integer> joinedTick = new HashMap<>();

    public AetherionManagerListener(AetherionManager manager) {
        this.manager = manager;
        instance = this;
    }

    /** Soft refresh from Quests (e.g. lesson_manager accepted → glow star). */
    public static void refreshManagerItem(Player player) {
        if (instance != null && player != null) {
            instance.giveManager(player);
        }
    }

    public ItemStack createManagerItem() {
        return createManagerItem(null);
    }

    public ItemStack createManagerItem(Player player) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            boolean skillsQuest = player != null
                    && de.aetherion.items.util.QuestProgressHook.isQuestActive(player, "lesson_manager");
            boolean petsQuest = !skillsQuest && player != null
                    && de.aetherion.items.util.QuestProgressHook.shouldGuidePetsEquip(player);
            boolean craftQuest = !skillsQuest && !petsQuest && player != null
                    && de.aetherion.items.util.QuestProgressHook.isQuestActive(player, "a_simple_craft");
            boolean spawnTip = !skillsQuest && !petsQuest && !craftQuest && player != null && hasHomesteadMarkerTip(player);
            meta.setDisplayName(skillsQuest || petsQuest || craftQuest || spawnTip
                    ? "§6§l★ Aetherion Manager ★"
                    : "§6§lAetherion Manager");
            if (skillsQuest) {
                meta.setDisplayName("§d§l★ Aetherion Manager ★");
                meta.setLore(List.of(
                        "§d§lQUEST TIP",
                        "§fOpen me → click Skills → equip one.",
                        "§7Miss Ledger is watching.",
                        "",
                        "§eRecipes §8• §eStats §8• §eSkills",
                        "§eBestiary §8• §eCollection",
                        "",
                        "§eClick to open"
                ));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            } else if (petsQuest) {
                meta.setDisplayName("§d§l★ Aetherion Manager ★");
                meta.setLore(List.of(
                        "§d§lPET TIP",
                        "§fOpen me → Pets (blinking) → Equip.",
                        "§7Lark wants your catch equipped.",
                        "",
                        "§eRecipes §8• §eStats §8• §eSkills",
                        "§eBestiary §8• §eCollection",
                        "§eLoadouts §8• §ePets §8• §eSpawns",
                        "",
                        "§eClick to open"
                ));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            } else if (craftQuest) {
                meta.setDisplayName("§e§l★ Aetherion Manager ★");
                meta.setLore(List.of(
                        "§e§lCRAFT TIP",
                        "§fGreen Recipe Book unlocked in the Manager.",
                        "§7Craft a Mining Pickaxe for the Craftsman.",
                        "",
                        "§eRecipes §8• §eStats §8• §eSkills",
                        "§eBestiary §8• §eCollection",
                        "§eLoadouts §8• §ePets §8• §eSpawns",
                        "",
                        "§eClick to open"
                ));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            } else if (spawnTip) {
                meta.setLore(List.of(
                        "§6§lSPAWN TIP",
                        "§fRight-click the campfire Marker,",
                        "§fthen open Spawns (blinking).",
                        "",
                        "§eRecipes §8• §eStats §8• §eSkills",
                        "§eBestiary §8• §eCollection",
                        "§eLoadouts §8• §ePets §8• §eSpawns",
                        "",
                        "§eClick to open"
                ));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            } else {
                meta.setLore(List.of(
                        "§7Open your central Aetherion hub.",
                        "§7Tabs unlock as you quest.",
                        "",
                        "§eRecipes §8• §eStats §8• §eSkills",
                        "§eBestiary §8• §eCollection",
                        "§eLoadouts §8• §ePets §8• §eSpawns",
                        "",
                        "§eClick to open"
                ));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(
                    ItemKeys.manager(),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            item.setItemMeta(meta);
        }

        return item;
    }

    private static boolean hasHomesteadMarkerTip(Player player) {
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        return hub != null && hub.hasUnlockItem(player);
    }

    public boolean isManagerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(ItemKeys.manager(), PersistentDataType.BYTE);
    }

    public ItemStack createDungeonMapItem() {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§5§lDungeon Map");
            meta.setLore(List.of(
                    "§7Floor layout while you are inside.",
                    "§7Chambers, boss and the exit portal.",
                    "",
                    "§eClick to open"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(
                    ItemKeys.manager(),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            item.setItemMeta(meta);
        }

        return item;
    }

    public void giveManager(Player player) {
        var inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == HOTBAR_SLOT) {
                continue;
            }

            if (isManagerItem(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }

        if (isManagerItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }

        ItemStack previous = inventory.getItem(HOTBAR_SLOT);
        boolean dungeon = player.getWorld() != null && player.getWorld().getName().startsWith("aedun_");
        inventory.setItem(HOTBAR_SLOT, dungeon ? dungeonMapOrFallback(player) : createManagerItem(player));
        relocateDisplaced(player, previous);
    }

    /**
     * Slot 8 is overwritten with the star or the dungeon map. Anything else goes back
     * into the inventory. During the first 50 ticks after join the stack is only parked:
     * a transfer rewrite (hub tick 12, plus 30) would otherwise duplicate a returned copy.
     * The live dungeon map carries the manager marker, so it is replaced in place.
     */
    private void relocateDisplaced(Player player, ItemStack previous) {
        boolean empty = previous == null || previous.getType().isAir();
        UUID id = player.getUniqueId();
        if (!ManagerSlotGuard.displace(empty, isManagerItem(previous))) {
            if (!rewriteMayStillLand(player)) {
                giveBack(player, parkedSlot.remove(id));
            }
            return;
        }
        if (rewriteMayStillLand(player)) {
            parkedSlot.put(id, previous.clone());
            return;
        }
        ItemStack parked = parkedSlot.remove(id);
        if (ManagerSlotGuard.parkedAlreadyInSlot(false, false, stacksMatch(parked, previous), true)) {
            parked = null;
        }
        giveBack(player, previous);
        giveBack(player, parked);
    }

    private boolean rewriteMayStillLand(Player player) {
        Integer tick = joinedTick.get(player.getUniqueId());
        return tick != null && Bukkit.getCurrentTick() - tick < POST_TRANSFER_GIVE_TICKS;
    }

    private void giveBack(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack extra : leftover.values()) {
            drop(player, extra);
        }
    }

    private static boolean stacksMatch(ItemStack parked, ItemStack slot) {
        if (parked == null || slot == null || slot.getType().isAir()) {
            return false;
        }
        return parked.getAmount() == slot.getAmount() && parked.isSimilar(slot);
    }

    private static ItemStack firstReal(Map<Integer, ItemStack> stacks) {
        for (ItemStack stack : stacks.values()) {
            if (stack != null && !stack.getType().isAir()) {
                return stack;
            }
        }
        return null;
    }

    private static void drop(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || player.getWorld() == null) {
            return;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), stack);
    }

    private ItemStack dungeonMapOrFallback(Player player) {
        try {
            var plugin = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
            if (plugin != null && plugin.isEnabled()) {
                Object created = plugin.getClass().getMethod("createDungeonMap", Player.class).invoke(plugin, player);
                if (created instanceof ItemStack stack && stack.getType() != Material.AIR) {
                    return stack;
                }
            }
        } catch (Exception ignored) {
        }
        return createDungeonMapItem();
    }

    @EventHandler
    public void onManagerClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof AetherionManagerGUI.Holder) {
            AetherionManagerGUI.stopSkillsBlink(player);
        }
        // Always kill ghost manager on the cursor when any inventory closes.
        if (isManagerItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            giveManager(player);
            player.updateInventory();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        joinedTick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
        scheduleManager(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleManager(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleManager(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        joinedTick.remove(player.getUniqueId());
        ItemStack waiting = parkedSlot.remove(player.getUniqueId());
        if (waiting == null || waiting.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(waiting);
        ItemStack rest = firstReal(left);
        if (rest != null) {
            player.getInventory().setItem(HOTBAR_SLOT, rest);
        }
    }

    private void scheduleManager(Player player) {
        giveManagerLater(player, 5L);
        giveManagerLater(player, 20L);
        giveManagerLater(player, POST_TRANSFER_GIVE_TICKS);
    }

    private void giveManagerLater(Player player, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(
                AetherionItems.getInstance(),
                () -> {
                    if (player.isOnline()) {
                        giveManager(player);
                    }
                },
                delayTicks
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!isManagerItem(event.getItem())) {
            return;
        }

        if (event.getItem().getType() == Material.FILLED_MAP && !event.getPlayer().isSneaking()) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        restore(event.getPlayer());
        manager.open(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isManagerItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof AetherionManagerGUI.Holder) {
            event.setCancelled(true);
            handleManagerClick(player, event.getRawSlot());
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof StatsOverviewGUI.Holder) {
            event.setCancelled(true);

            if (event.getRawSlot() == StatsOverviewGUI.BACK_SLOT) {
                manager.open(player);
            }

            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof de.aetherion.items.codex.BestiaryGUI.Holder) {
            event.setCancelled(true);
            if (event.getRawSlot() == de.aetherion.items.codex.CodexGui.BACK_SLOT) {
                manager.open(player);
                return;
            }
            if (manager.getBestiaryGUI() != null) {
                manager.getBestiaryGUI().handleClick(player, event.getRawSlot());
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof de.aetherion.items.codex.CollectionGUI.Holder) {
            event.setCancelled(true);
            if (event.getRawSlot() == de.aetherion.items.codex.CodexGui.BACK_SLOT) {
                manager.open(player);
                return;
            }
            if (manager.getCollectionGUI() != null) {
                manager.getCollectionGUI().handleClick(player, event.getRawSlot());
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof de.aetherion.items.codex.DungeonJournalGUI.Holder) {
            event.setCancelled(true);
            if (event.getRawSlot() == de.aetherion.items.codex.DungeonJournalGUI.BACK_SLOT) {
                manager.open(player);
                return;
            }
            if (manager.getJournalGUI() != null) {
                manager.getJournalGUI().handleClick(player, event.getRawSlot());
            }
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (isManagerItem(current)) {
            event.setCancelled(true);
            // Clear cursor first — cancelled clicks still leave a client-side ghost star.
            restore(player);
            // Open while another inventory (chest, player inv view, etc.) is open —
            // players often click the hotbar Nether Star without closing first.
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(player.getInventory())
                    && current.getType() != Material.FILLED_MAP) {
                Bukkit.getScheduler().runTask(AetherionItems.getInstance(), () -> {
                    if (player.isOnline()) {
                        manager.open(player);
                    }
                });
            }
            return;
        }

        if (isManagerItem(cursor)) {
            event.setCancelled(true);
            restore(player);
            return;
        }

        if (event.getClick().isKeyboardClick() && event.getHotbarButton() == HOTBAR_SLOT) {
            event.setCancelled(true);
            restore(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AetherionManagerGUI.Holder
                || event.getView().getTopInventory().getHolder() instanceof StatsOverviewGUI.Holder
                || event.getView().getTopInventory().getHolder() instanceof de.aetherion.items.codex.BestiaryGUI.Holder
                || event.getView().getTopInventory().getHolder() instanceof de.aetherion.items.codex.CollectionGUI.Holder) {
            event.setCancelled(true);
            return;
        }

        if (isManagerItem(event.getOldCursor())) {
            event.setCancelled(true);

            if (event.getWhoClicked() instanceof Player player) {
                restore(player);
            }
        }
    }

    private void handleManagerClick(Player player, int slot) {
        ProgressionService progress = AetherionItems.getInstance() == null
                ? null
                : AetherionItems.getInstance().progress();
        switch (slot) {
            case AetherionManagerGUI.BAZAAR_SLOT -> {
                if (progress != null && !progress.bazaar(player)) {
                    player.sendMessage(progress.hint(ProgressionService.Flag.TRADER));
                    return;
                }
                var market = AetherionItems.getInstance().getMarket();
                if (market == null) {
                    player.sendMessage("§cBazaar is not loaded.");
                    return;
                }
                market.openBazaar(player);
            }
            case AetherionManagerGUI.AUCTION_SLOT -> {
                if (progress != null && !progress.auction(player)) {
                    player.sendMessage(progress.hint(ProgressionService.Flag.TRADER));
                    return;
                }
                var market = AetherionItems.getInstance().getMarket();
                if (market == null) {
                    player.sendMessage("§cAuction House is not loaded.");
                    return;
                }
                market.openAuction(player);
            }
            case AetherionManagerGUI.SKILLS_SLOT -> {
                if (progress != null && !progress.skills(player)) {
                    player.sendMessage(progress.hint(ProgressionService.Flag.SKILLS));
                    return;
                }
                var menu = AetherionItems.getInstance().getSkillMenu();
                if (menu == null) {
                    player.sendMessage("§cSkills are not loaded.");
                    return;
                }
                menu.open(player);
            }
            case AetherionManagerGUI.SHOP_SLOT -> {
                var shop = AetherionItems.getInstance().getShardShop();
                if (shop == null) {
                    player.sendMessage("§cShop is not loaded.");
                    return;
                }
                shop.open(player);
            }
            case AetherionManagerGUI.RECIPE_SLOT -> {
                if (progress != null && !progress.recipeBook(player)) {
                    player.sendMessage(progress.hint(ProgressionService.Flag.WORKBENCH));
                    return;
                }
                if (manager.getRecipeBookGUI() != null) {
                    manager.getRecipeBookGUI().open(player);
                }
            }
            case AetherionManagerGUI.STATS_SLOT -> manager.getStatsGUI().open(player);
            case AetherionManagerGUI.BESTIARY_SLOT -> {
                if (progress != null && !progress.bestiary(player)) {
                    player.sendMessage(progress.bestiaryHint());
                    return;
                }
                if (manager.getBestiaryGUI() != null) {
                    manager.getBestiaryGUI().open(player);
                }
            }
            case AetherionManagerGUI.COLLECTION_SLOT -> {
                if (progress != null && !progress.collection(player)) {
                    player.sendMessage(progress.collectionHint());
                    return;
                }
                if (manager.getCollectionGUI() != null) {
                    manager.getCollectionGUI().open(player);
                }
            }
            case AetherionManagerGUI.JOURNAL_SLOT -> {
                if (progress != null && !progress.journal(player)) {
                    player.sendMessage(progress.journalHint());
                    return;
                }
                if (manager.getJournalGUI() != null) {
                    manager.getJournalGUI().open(player);
                }
            }
            case AetherionManagerGUI.ISLAND_SLOT -> {
                if (!Bukkit.getPluginManager().isPluginEnabled("AetherionGuilds")) {
                    player.sendMessage("§cIslands are not loaded.");
                    return;
                }
                if (progress != null && !progress.island(player)) {
                    player.sendMessage(progress.islandHint());
                    return;
                }
                player.closeInventory();
                player.performCommand("island");
            }
            case AetherionManagerGUI.GUILD_SLOT -> {
                if (!Bukkit.getPluginManager().isPluginEnabled("AetherionGuilds")) {
                    player.sendMessage("§cGuilds are not loaded.");
                    return;
                }
                if (progress != null && !progress.guild(player)) {
                    player.sendMessage(progress.guildHint());
                    return;
                }
                player.closeInventory();
                player.performCommand("guild");
            }
            case AetherionManagerGUI.STORAGE_SLOT -> {
                StorageListener storageListener = AetherionItems.getInstance().getStorageListener();

                if (storageListener != null) {
                    storageListener.openStorage(player);
                }
            }
            case AetherionManagerGUI.LOADOUT_SLOT -> {
                LoadoutListener loadoutListener = AetherionItems.getInstance().getLoadoutListener();

                if (loadoutListener != null) {
                    loadoutListener.openLoadoutMenu(player);
                }
            }
            case AetherionManagerGUI.SPAWN_SLOT -> {
                if (!Bukkit.getPluginManager().isPluginEnabled("AetherionHub")) {
                    player.sendMessage("§cSpawn menu is not loaded.");
                    return;
                }
                if (progress != null && !progress.spawns(player)) {
                    player.sendMessage(progress.hint(ProgressionService.Flag.SPAWN_UNLOCKER));
                    return;
                }
                player.closeInventory();
                player.performCommand("spawns");
            }
            case AetherionManagerGUI.PETS_SLOT -> {
                if (progress != null && !progress.pets(player)) {
                    player.sendMessage(progress.hint(ProgressionService.Flag.PETS));
                    return;
                }
                if (!manager.hasPetMenu()) {
                    player.sendMessage("§cAetherMobs is not loaded.");
                    return;
                }

                manager.openPets(player);
            }
            case AetherionManagerGUI.CRAFT_SLOT -> {
                if (progress != null && !progress.craftingTable(player)) {
                    player.sendMessage(progress.hint(ProgressionService.Flag.WORKBENCH));
                    return;
                }
                player.closeInventory();
                Bukkit.getScheduler().runTask(AetherionItems.getInstance(), () ->
                        player.openWorkbench(player.getLocation(), true));
            }
            case AetherionManagerGUI.ANVIL_SLOT -> {
                if (progress != null && !progress.anvil(player)) {
                    player.sendMessage(progress.hint(ProgressionService.Flag.ANVIL));
                    return;
                }
                player.closeInventory();
                Bukkit.getScheduler().runTask(AetherionItems.getInstance(), () ->
                        de.aetherion.items.menu.BoosterSocketMenu.open(player));
            }
            case AetherionManagerGUI.DEV_SLOT -> {
                var dev = AetherionItems.getInstance().getDevMenu();
                if (dev == null || !de.aetherion.items.menu.dev.DevMenu.canUse(player)) {
                    player.sendMessage("§cDEV only.");
                    return;
                }
                dev.open(player);
            }
            case AetherionManagerGUI.CLOSE_SLOT -> player.closeInventory();
            default -> {
            }
        }
    }

    private void restore(Player player) {
        player.setItemOnCursor(null);
        giveManager(player);
        player.updateInventory();
    }
}
