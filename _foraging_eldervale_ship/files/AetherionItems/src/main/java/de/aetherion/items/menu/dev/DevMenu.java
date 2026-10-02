package de.aetherion.items.menu.dev;

import de.aetherion.items.AetherionItems;
import de.aetherion.core.api.TestBotReport;
import de.aetherion.core.api.TestBotRoleView;
import de.aetherion.core.api.TestBotView;
import de.aetherion.items.core.BoosterLimits;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.ShardService;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.BoosterApplier;
import de.aetherion.items.model.BoosterStats;
import de.aetherion.items.model.BoosterType;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;
import de.aetherion.items.rank.RankBadgeService;
import de.aetherion.items.storage.AetherionStorage;
import de.aetherion.items.world.AnimalZoneService;
import de.aetherion.items.world.AreaService;
import de.aetherion.items.world.AreaType;
import de.aetherion.items.world.MobZoneService;
import de.aetherion.items.world.PetHabitatKind;
import de.aetherion.items.world.PetHabitatZoneService;
import de.aetherion.items.world.BuildingBannerKind;
import de.aetherion.items.world.BuildingBannerService;
import de.aetherion.items.world.NpcRemoverListener;
import de.aetherion.items.world.CryptHologramService;
import de.aetherion.items.world.WorldMapService;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DevMenu {

    public static final String TITLE = "§8Aetherion DEV";
    public static final int BOOSTER_WELL_SLOT = 13;

    public enum Page {
        ROOT,
        COMBAT,
        MINING,
        WEAPONS,
        WEAPONS_STARTER,
        WEAPONS_BOWS,
        WEAPONS_PROGRESSION,
        WEAPONS_T1,
        WEAPONS_T2,
        WEAPONS_DUNGEON,
        WEAPONS_SPECIAL,
        WEAPONS_GOD,
        SETS,
        BOOSTERS,
        BOOSTER_LAB,
        TOOLS,
        BOSS_ANCHORS,
        BOSS_CORES,
        PETS,
        SPHERES,
        NPCS,
        NPCS_STARTER,
        NPCS_BOSSES,
        NPCS_WORLD,
        NPCS_SERVICES,
        NPC_EDITOR,
        RESOURCES,
        AREAS,
        RANKS,
        SHARDS,
        DUNGEONS,
        SPAWN_MARKERS,
        FARMING,
        FORAGING,
        FISHING,
        CATCHER,
        CHARMS,
        TEST_ARENA,
        TEST_GEAR,
        TESTBOTS,
        TESTBOTS_LIST,
        BORDERLANDS_SPIRITS,
        PORTALS,
        PET_HABITATS,
        BLUEPRINTS,
        MILLSTONE,
        ISLE_WEATHER,
        FARM_ISLE,
        FARM_ISLE_SPOTS,
        FARM_ISLE_NPCS,
        FARM_ISLE_PROPS,
        FARM_ISLE_EVENTS,
        FARM_ISLE_PROGRESS,
        FISH_ISLE,
        FISH_ISLE_SPOTS,
        FISH_ISLE_NPCS,
        FISH_ISLE_EVENTS,
        FISH_ISLE_PROGRESS,
        MINE_ISLE,
        MINE_ISLE_SPOTS,
        MINE_ISLE_NPCS,
        MINE_ISLE_EVENTS,
        MINE_ISLE_PROGRESS,
        AMBIENT,
        PLAYER_WIPE,
        // DEV // AETHERION command center — category hubs + power pages.
        CAT_CONTENT,
        CAT_COMBAT,
        CAT_WORLDS,
        CAT_PROGRESS,
        CAT_ADMIN,
        CAT_DANGER,
        SEARCH,
        LOADOUTS,
        SKILL_GEAR,
        STATUS,
        CONFIRM,
        CONTENT_KIT
    }

    private final CustomItem customItem;
    private final AetherionStorage storage;
    private final AnimalZoneService animalZones;
    private final MobZoneService mobZones;
    private final PetHabitatZoneService petHabitats;
    private final AreaService areas;
    private final WorldMapService worldMaps;
    private final CryptHologramService cryptHolograms;
    private final BuildingBannerService buildingBanners;
    private final FarmIsleDevPages farmIsle;
    private final FishIsleDevPages fishIsle;
    private final MineIsleDevPages mineIsle;

    public DevMenu(
            CustomItem customItem,
            AetherionStorage storage,
            AnimalZoneService animalZones,
            MobZoneService mobZones,
            PetHabitatZoneService petHabitats,
            AreaService areas,
            WorldMapService worldMaps,
            CryptHologramService cryptHolograms,
            BuildingBannerService buildingBanners
    ) {
        this.customItem = customItem;
        this.storage = storage;
        this.animalZones = animalZones;
        this.mobZones = mobZones;
        this.petHabitats = petHabitats;
        this.areas = areas;
        this.worldMaps = worldMaps;
        this.cryptHolograms = cryptHolograms;
        this.buildingBanners = buildingBanners;
        this.farmIsle = new FarmIsleDevPages(customItem);
        this.fishIsle = new FishIsleDevPages(customItem);
        this.mineIsle = new MineIsleDevPages(customItem);
    }

    public static boolean canUse(Player player) {
        return isFullDev(player) || hasContentKit(player);
    }

    public static boolean isFullDev(Player player) {
        return player != null && (player.isOp() || player.hasPermission("aetherion.dev"));
    }

    public static boolean hasContentKit(Player player) {
        return player != null && player.hasPermission("aetherion.dev.content");
    }

    /**
     * Monkey / Homie content role sees Content Kit, never the full admin tree —
     * even if a leftover {@code aetherion.dev} node still exists. Ops and Robb Admin keep full DEV.
     */
    public static boolean preferContentKit(Player player) {
        if (player == null || player.isOp()) {
            return false;
        }
        if (DevRankBridge.isRobb(player.getUniqueId())) {
            return false;
        }
        AetherionItems items = AetherionItems.getInstance();
        if (items != null && items.ranks() != null) {
            RankBadgeService.Rank extra = items.ranks().extraRank(player);
            if (extra != null && "admin".equalsIgnoreCase(extra.group())) {
                return false;
            }
        }
        if (isContentRole(player)) {
            return true;
        }
        return hasContentKit(player) && !player.hasPermission("aetherion.dev");
    }

    private static boolean isContentRole(Player player) {
        if (player.hasPermission("group.monkey") || player.hasPermission("aetherion.rank.monkey")) {
            return true;
        }
        AetherionItems items = AetherionItems.getInstance();
        if (items != null && items.ranks() != null) {
            RankBadgeService.Rank extra = items.ranks().extraRank(player);
            return extra != null && "monkey".equalsIgnoreCase(extra.group());
        }
        return false;
    }

    private static boolean contentOnly(Player player) {
        return preferContentKit(player) || !isFullDev(player);
    }

    private static boolean canOpenPage(Player player, Page page) {
        if (page == null) {
            return false;
        }
        if (preferContentKit(player)) {
            return isContentPage(page);
        }
        if (isFullDev(player)) {
            return true;
        }
        return hasContentKit(player) && isContentPage(page);
    }

    private static boolean isContentPage(Page page) {
        return page == Page.ROOT
                || page == Page.RESOURCES
                || page == Page.SHARDS
                || page == Page.AMBIENT
                || page == Page.NPC_EDITOR;
    }

    private static boolean contentActionAllowed(Player player, String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        if (action.equals("close") || action.equals("back") || action.equals("noop")
                || action.equals("root") || action.equals("npc-wand") || action.equals("flight-toggle")
                || action.equals("npc-editor") || action.startsWith("npc-editor:") || action.equals("open:aethernpc")) {
            return true;
        }
        if (action.startsWith("page:")) {
            try {
                return canOpenPage(player, Page.valueOf(action.substring(5)));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        if (action.startsWith("pageidx:")) {
            String name = action.substring("pageidx:".length()).split(":")[0];
            try {
                return canOpenPage(player, Page.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return action.startsWith("shard-player:")
                || action.startsWith("shard-add:")
                || action.equals("give-page")
                || action.startsWith("item:")
                || action.startsWith("ambient:")
                || action.equals("farmtool:scarecrow")
                || action.equals("farmtool:haywagon");
    }

    /** Opens the FancyNPC + quest creator ({@code /npc}). */
    private void giveNpcWand(Player player) {
        if (!player.hasPermission("aetherion.npc.editor") && !isFullDev(player) && !player.isOp()) {
            player.sendMessage("§cNeed §faetherion.npc.editor §c(Monkey / admin).");
            return;
        }
        player.closeInventory();
        if (player.performCommand("npc")) {
            return;
        }
        if (player.performCommand("aethernpc") || player.performCommand("npceditor")) {
            return;
        }
        if (player.performCommand("npc wand")) {
            player.sendMessage("§eWand given. Use §f/npc §efor the creator menu.");
            return;
        }
        player.sendMessage("§c/npc failed. Is AetherionQuests loaded?");
    }

    /** NPC / Quest editor section (cursor monkey-dev-menu): {@code /npc <sub>} from AetherionQuests. */
    private void runNpcEditor(Player player, String sub) {
        if (!player.hasPermission("aetherion.npc.editor") && !isFullDev(player)) {
            player.sendMessage("§cYou need §faetherion.npc.editor §cto use the NPC editor.");
            return;
        }
        player.closeInventory();
        String command = sub == null || sub.isBlank() ? "npc" : "npc " + sub;
        if (!player.performCommand(command)) {
            player.sendMessage("§c/" + command + " failed. Is AetherionQuests loaded?");
        }
    }

    public void open(Player player) {
        open(player, Page.ROOT, null);
    }

    public void open(Player player, Page page) {
        open(player, page, null);
    }

    public void open(Player player, Page page, UUID target) {
        open(player, page, target, 0);
    }

    public void open(Player player, Page page, UUID target, int index) {
        open(player, new Holder(page, target, index));
    }

    /** Opens a page from a full spec (search query, confirm payload, wipe arm timer ride along). */
    void open(Player player, Holder spec) {
        if (!canUse(player)) {
            player.sendMessage("§cDEV only.");
            return;
        }
        Page page = spec.page();
        if (!canOpenPage(player, page)) {
            player.sendMessage("§cContent kit cannot open that page.");
            return;
        }
        if (page == Page.SHARDS && contentOnly(player) && spec.target() == null) {
            spec = new Holder(page, player.getUniqueId(), spec.index(), spec.query(), spec.payload(), spec.armedUntil());
        }
        Holder previous = currentHolder(player);
        Page titlePage = page == Page.ROOT && contentOnly(player) ? Page.CONTENT_KIT : page;
        Inventory inventory = Bukkit.createInventory(spec, 54, DevTheme.title(titlePage, titleSuffix(spec)));
        render(inventory, spec, player);
        player.openInventory(inventory);
        if (previous == null || previous.page() != page) {
            DevTheme.sound(player, page);
        }
    }

    private String titleSuffix(Holder spec) {
        if (spec.page() == Page.SEARCH && spec.query() != null) {
            String query = spec.query().length() > 12 ? spec.query().substring(0, 12) + "…" : spec.query();
            return "\"" + query + "\"";
        }
        if (spec.target() != null && (spec.page() == Page.RANKS || spec.page() == Page.SHARDS
                || spec.page() == Page.PLAYER_WIPE)) {
            String name = nameOf(Bukkit.getOfflinePlayer(spec.target()));
            return "· " + (name.length() > 16 ? name.substring(0, 16) : name);
        }
        return null;
    }

    /** Draws a page into any inventory — used by {@link #open} and by the search harvester. */
    void render(Inventory inventory, Holder spec, Player player) {
        Page page = spec.page();
        UUID target = spec.target();
        int index = Math.max(0, spec.index());
        boolean kitRoot = page == Page.CONTENT_KIT || (page == Page.ROOT && contentOnly(player));
        DevTheme.paintFrame(inventory, kitRoot ? DevTheme.Cat.KIT : DevTheme.info(page).cat());
        if (kitRoot) {
            drawContentRoot(inventory);
        } else if (page == Page.ROOT) {
            DevHubs.drawDashboard(this, inventory, player);
        } else if (page.name().startsWith("CAT_")) {
            DevHubs.drawCategory(this, inventory, player, DevTheme.info(page).cat());
        } else if (page == Page.SEARCH) {
            DevHubs.drawSearch(this, inventory, player, spec);
        } else if (page == Page.LOADOUTS) {
            DevHubs.drawLoadouts(this, inventory, loadouts());
        } else if (page == Page.SKILL_GEAR) {
            DevHubs.drawSkillGear(inventory);
        } else if (page == Page.STATUS) {
            DevHubs.drawStatus(inventory, player);
        } else if (page == Page.CONFIRM) {
            DevHubs.drawConfirm(inventory, spec);
        } else if (page == Page.ISLE_WEATHER) {
            drawIsleWeather(inventory, player);
        } else if (FarmIsleDevPages.owns(page)) {
            farmIsle.draw(inventory, page, player);
        } else if (FishIsleDevPages.owns(page)) {
            fishIsle.draw(inventory, page, player);
        } else if (MineIsleDevPages.owns(page)) {
            mineIsle.draw(inventory, page, player);
        } else if (page == Page.PLAYER_WIPE) {
            drawPlayerWipe(inventory, target, spec.armedUntil());
        } else {
            drawPage(inventory, page, target, index);
        }
        applyChrome(inventory, spec, player, kitRoot);
    }

    /**
     * Uniform chrome after every draw: header fallback at 4, Go Back 45, Close 49, Search 53.
     * Pages keep their own content; only the nav slots are normalised.
     */
    private void applyChrome(Inventory inventory, Holder spec, Player player, boolean kitRoot) {
        Page page = spec.page();
        DevTheme.Info info = DevTheme.info(page);
        ItemStack header = inventory.getItem(DevTheme.HEADER);
        // Only fill the header when the page left the frame's bar pane there (player heads stay).
        if (header == null || header.getType().name().endsWith("STAINED_GLASS_PANE")) {
            List<String> lore = new ArrayList<>();
            lore.add("§8" + DevTheme.breadcrumb(page));
            lore.add("");
            lore.addAll(DevTheme.controls());
            inventory.setItem(DevTheme.HEADER, DevItems.button(info.cat().icon,
                    info.cat().accent + "§l" + DevTheme.pageName(page), "noop", lore.toArray(String[]::new)));
        }
        if (kitRoot) {
            inventory.setItem(DevTheme.BACK, DevItems.button(Material.ARROW, "§a← Aetherion Manager", "close",
                    "§7Leave the Content Kit."));
            inventory.setItem(DevTheme.CLOSE, DevItems.button(Material.BARRIER, "§cClose", "close"));
            if (page == Page.CONTENT_KIT) {
                inventory.setItem(DevTheme.BACK, backButton(Page.CAT_ADMIN));
            }
            return;
        }
        if (page == Page.ROOT) {
            inventory.setItem(DevTheme.BACK, DevItems.button(Material.ARROW, "§a← Aetherion Manager", "close",
                    "§7Back to the player menu."));
        } else if (contentOnly(player)) {
            inventory.setItem(DevTheme.BACK, DevItems.button(Material.ARROW, "§a← Go Back", "back",
                    "§7To §fContent Kit"));
        } else {
            inventory.setItem(DevTheme.BACK, backButton(backTarget(spec)));
        }
        inventory.setItem(DevTheme.CLOSE, DevItems.button(Material.BARRIER, "§cClose", "close",
                "§7Back to the Aetherion Manager."));
        if (!contentOnly(player)) {
            inventory.setItem(DevTheme.SEARCH, DevItems.button(Material.OAK_SIGN, "§d§lSearch",
                    "search",
                    "§7Find any item, set, page, NPC or tool.",
                    "§7Type the words in chat.",
                    "",
                    "§8Also: §f/devmenu <words>"));
        }
    }

    private ItemStack backButton(Page parent) {
        return DevItems.button(Material.ARROW, "§a← Go Back", "back",
                "§7To §f" + DevTheme.pageName(parent == null ? Page.ROOT : parent));
    }

    /** Where Go Back lands: pickers step back to their list, confirm returns to its origin. */
    private Page backTarget(Holder spec) {
        Page page = spec.page();
        if (spec.target() != null && (page == Page.RANKS || page == Page.SHARDS || page == Page.PLAYER_WIPE)) {
            return page;
        }
        if (page == Page.CONFIRM) {
            return confirmReturn(spec);
        }
        Page parent = DevTheme.info(page).parent();
        return parent == null ? Page.ROOT : parent;
    }

    private void goBack(Player player) {
        Holder current = currentHolder(player);
        if (current == null || contentOnly(player)) {
            open(player, Page.ROOT);
            return;
        }
        open(player, backTarget(current));
    }

    Holder currentHolder(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder ? holder : null;
    }

    CustomItem customItem() {
        return customItem;
    }

    public void handle(Player player, ItemStack clicked, int slot) {
        handle(player, clicked, slot, ClickType.LEFT);
    }

    public void handle(Player player, ItemStack clicked, int slot, ClickType click) {
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        if (click == null) {
            click = ClickType.LEFT;
        }
        String action = clicked.getItemMeta().getPersistentDataContainer().get(ItemKeys.devAction(), PersistentDataType.STRING);
        if (action == null || action.isBlank()) {
            if (slot == 45) {
                open(player, Page.ROOT);
            } else if (slot == 49) {
                player.closeInventory();
            }
            return;
        }
        if (contentOnly(player) && !contentActionAllowed(player, action)) {
            player.sendMessage("§cContent kit cannot use that.");
            return;
        }
        // Favorite / recent / search tiles are decorated clones — act on the pristine shelf item,
        // never hand out (or cache) the clone with the extra lore.
        if (DevItems.viaOf(clicked) != null) {
            ItemStack pristine = DevIndex.icon(action);
            if (pristine == null && action.startsWith("item:")) {
                ItemStack resolved = resolveItem(action.substring("item:".length()));
                pristine = resolved == null ? null : DevItems.tag(resolved.clone(), action);
            }
            if (pristine != null) {
                clicked = pristine;
            } else if (action.startsWith("item:") && click != ClickType.SWAP_OFFHAND) {
                player.sendMessage("§7That pin is cold — open its shelf once (or §fSystem Status › rebuild index§7).");
                return;
            }
        }
        boolean pristineClick = DevItems.viaOf(clicked) == null;
        if (click == ClickType.SWAP_OFFHAND) {
            togglePin(player, action, clicked);
            return;
        }
        if (requiresConfirm(action)) {
            openConfirm(player, action);
            return;
        }
        if (DevIndex.isRecentable(action) && !contentOnly(player)) {
            DevPrefs.recordRecent(player, action);
            if (pristineClick) {
                DevIndex.remember(action, clicked);
            }
        }
        dispatch(player, clicked, slot, click, action);
    }

    /** The action router. Reached only after the pin / confirm / recent pre-pass in {@link #handle}. */
    private void dispatch(Player player, ItemStack clicked, int slot, ClickType click, String action) {
        if (action.equals("close")) {
            de.aetherion.items.util.ManagerNav.openManager(player);
            return;
        }
        if (action.equals("root")) {
            open(player, Page.ROOT);
            return;
        }
        if (action.equals("back")) {
            goBack(player);
            return;
        }
        if (action.equals("search")) {
            promptSearch(player);
            return;
        }
        if (action.equals("recents:clear")) {
            DevPrefs.clearRecents(player);
            player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 0.7f, 1.2f);
            open(player, Page.ROOT);
            return;
        }
        if (action.equals("index:rebuild")) {
            long started = System.nanoTime();
            DevIndex.invalidate();
            int size = DevIndex.catalog(this, player).size();
            player.sendMessage("§d⌁ Search index rebuilt §8· §f" + size + " §7entries in §f"
                    + ((System.nanoTime() - started) / 1_000_000L) + " ms§7.");
            open(player, Page.STATUS);
            return;
        }
        if (action.equals("give-page")) {
            givePage(player);
            return;
        }
        if (action.startsWith("loadout:")) {
            giveLoadout(player, action.substring("loadout:".length()));
            return;
        }
        if (action.startsWith("confirm:")) {
            openConfirm(player, action.substring("confirm:".length()));
            return;
        }
        if (action.equals("confirm-no")) {
            Holder current = currentHolder(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
            open(player, current == null ? Page.CAT_DANGER : confirmReturn(current));
            return;
        }
        if (action.equals("confirm-yes")) {
            runConfirmed(player);
            return;
        }
        if (action.equals("npc-wand")) {
            giveNpcWand(player);
            return;
        }
        if (action.equals("flight-toggle")) {
            player.closeInventory();
            if (!player.performCommand("flight") && !player.performCommand("fly")) {
                player.sendMessage("§cFlight command unavailable.");
            }
            return;
        }
        if (action.equals("npc-editor") || action.startsWith("npc-editor:")) {
            runNpcEditor(player, action.equals("npc-editor") ? "" : action.substring("npc-editor:".length()));
            return;
        }
        if (action.equals("open:aethernpc")) {
            player.closeInventory();
            if (!player.hasPermission("aetherion.npc.editor") && !isFullDev(player)) {
                player.sendMessage("§cYou need the NPC editor permission.");
                return;
            }
            if (!player.performCommand("aethernpc")) {
                player.sendMessage("§cCould not open /aethernpc. Is AetherionQuests loaded?");
            }
            return;
        }
        if (action.startsWith("testbot:")) {
            handleTestbots(player, action, click);
            return;
        }
        if (action.startsWith(FarmIsleDevPages.PREFIX)) {
            Page current = player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                    ? holder.page() : Page.FARM_ISLE;
            Page reopen = farmIsle.handle(player, action, click, current);
            if (reopen != null) {
                open(player, reopen);
            }
            return;
        }
        if (action.startsWith(FishIsleDevPages.PREFIX)) {
            Page current = player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                    ? holder.page() : Page.FISH_ISLE;
            Page reopen = fishIsle.handle(player, action, click, current);
            if (reopen != null) {
                open(player, reopen);
            }
            return;
        }
        if (action.startsWith(MineIsleDevPages.PREFIX)) {
            Page current = player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                    ? holder.page() : Page.MINE_ISLE;
            Page reopen = mineIsle.handle(player, action, click, current);
            if (reopen != null) {
                open(player, reopen);
            }
            return;
        }
        if (action.startsWith("pageidx:")) {
            String[] parts = action.substring("pageidx:".length()).split(":");
            if (parts.length >= 2) {
                int index = 0;
                try {
                    index = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                }
                Page next;
                try {
                    next = Page.valueOf(parts[0]);
                } catch (IllegalArgumentException ignored) {
                    return;
                }
                if (!canOpenPage(player, next)) {
                    player.sendMessage("§cContent kit cannot open that page.");
                    return;
                }
                Holder current = currentHolder(player);
                String query = current != null && current.page() == next ? current.query() : null;
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.4f);
                open(player, new Holder(next, holderTarget(player), index, query, null, 0L));
            }
            return;
        }
        if (action.startsWith("page:")) {
            Page next;
            try {
                next = Page.valueOf(action.substring(5));
            } catch (IllegalArgumentException ignored) {
                return;
            }
            if (!canOpenPage(player, next)) {
                player.sendMessage("§cContent kit cannot open that page.");
                return;
            }
            if (next == Page.SHARDS && contentOnly(player)) {
                open(player, next, player.getUniqueId());
                return;
            }
            open(player, next);
            return;
        }
        if (action.startsWith("rank-player:")) {
            open(player, Page.RANKS, UUID.fromString(action.substring("rank-player:".length())));
            return;
        }
        if (action.startsWith("rank-set:")) {
            UUID target = holderTarget(player);
            RankBadgeService ranks = ranks();
            if (target == null || ranks == null) {
                player.sendMessage("§cPick a player first.");
                return;
            }
            String group = action.substring("rank-set:".length());
            if (DevRankBridge.isHomieExtra(group) && !ranks.isExtra(group)) {
                // This build's RankBadgeService does not know the group; setRank would force Adventurer.
                player.sendMessage("§c" + group + " §7needs the special-rank backend (not in this build).");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1f);
                open(player, Page.RANKS, target);
                return;
            }
            RankBadgeService.Rank extra = ranks.extraFor(target);
            if (ranks.isExtra(group)
                    && extra != null && extra.group().equalsIgnoreCase(group)) {
                if (!DevRankBridge.clearExtra(ranks, target, group)) {
                    player.sendMessage("§eThis build cannot remove ultras from the menu.");
                    open(player, Page.RANKS, target);
                    return;
                }
                player.sendMessage("§eUltra removed: §f" + nameOf(Bukkit.getOfflinePlayer(target))
                        + " §7← " + ranks.rankByGroup(group).display());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.9f);
                open(player, Page.RANKS, target);
                return;
            }
            if (!ranks.setRank(target, group)) {
                if ("admin".equalsIgnoreCase(group) && !DevRankBridge.isRobb(target)) {
                    player.sendMessage("§cAdmin is Robb's cosmetic rank. OP does not grant it.");
                } else {
                    player.sendMessage("§cCould not set that rank.");
                }
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1f);
                open(player, Page.RANKS, target);
                return;
            }
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(target);
            player.sendMessage("§aRank set: §f" + nameOf(targetPlayer) + " §7→ " + ranks.rankByGroup(group).display());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            open(player, Page.RANKS, target);
            return;
        }
        if (action.equals("rank-sync")) {
            UUID target = holderTarget(player);
            RankBadgeService ranks = ranks();
            if (target == null || ranks == null) {
                player.sendMessage("§cPick a player first.");
                return;
            }
            ranks.syncToLevel(target);
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(target);
            player.sendMessage("§aRank matched to account level: §f" + nameOf(targetPlayer)
                    + " §7→ " + ranks.rankByGroup(ranks.rankOf(target)).display());
            player.sendMessage("§7Ultra ranks (Citrus / Monkey / Beta / MVP++ / Admin) stay until you remove them.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.15f);
            open(player, Page.RANKS, target);
            return;
        }
        if (action.startsWith("shard-player:")) {
            UUID picked = UUID.fromString(action.substring("shard-player:".length()));
            if (contentOnly(player) && !player.getUniqueId().equals(picked)) {
                player.sendMessage("§cContent kit can only give shards to yourself.");
                return;
            }
            open(player, Page.SHARDS, picked);
            return;
        }
        if (action.startsWith("wipe-player:")) {
            open(player, Page.PLAYER_WIPE, UUID.fromString(action.substring("wipe-player:".length())));
            return;
        }
        if (action.equals("wipe-confirm")) {
            UUID target = holderTarget(player);
            if (target == null) {
                player.sendMessage("§cPick a player first.");
                return;
            }
            // Two-step: the first press only arms (8s window); the second, while armed, wipes.
            Holder current = currentHolder(player);
            if (current == null || current.armedUntil() < System.currentTimeMillis()) {
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 0.6f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                player.sendMessage("§4§l☠ ARMED §7— press §cCONFIRM §7again within §f8s §7to wipe §f"
                        + nameOf(Bukkit.getOfflinePlayer(target)) + "§7.");
                open(player, new Holder(Page.PLAYER_WIPE, target, 0, null, null,
                        System.currentTimeMillis() + WIPE_ARM_MILLIS));
                return;
            }
            player.closeInventory();
            AetherionItems plugin = AetherionItems.getInstance();
            if (plugin == null) {
                player.sendMessage("§cAetherionItems not loaded.");
                return;
            }
            PlayerWipeService.wipe(plugin, target, player);
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.55f, 0.7f);
            return;
        }
        if (action.equals("wipe-cancel")) {
            open(player, Page.PLAYER_WIPE);
            return;
        }
        if (action.startsWith("shard-add:")) {
            UUID target = holderTarget(player);
            ShardService shards = shards();
            if (target == null || shards == null) {
                player.sendMessage("§cPick a player first.");
                return;
            }
            if (contentOnly(player) && !player.getUniqueId().equals(target)) {
                player.sendMessage("§cContent kit can only give shards to yourself.");
                return;
            }
            long amount;
            try {
                amount = Long.parseLong(action.substring("shard-add:".length()));
            } catch (NumberFormatException exception) {
                player.sendMessage("§cInvalid amount.");
                return;
            }
            if (amount <= 0L) {
                player.sendMessage("§cInvalid amount.");
                return;
            }
            shards.add(target, amount);
            Player online = Bukkit.getPlayer(target);
            if (online != null && online.isOnline()) {
                online.sendMessage("§b+" + amount + " Aether Shards");
            }
            player.sendMessage("§aGave §b" + amount + " §ashards to §f" + nameOf(Bukkit.getOfflinePlayer(target))
                    + "§a. Balance: §b" + shards.get(target));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
            open(player, Page.SHARDS, target);
            return;
        }
        if (action.equals("dungeon:floor1") || action.equals("dungeon:floor1-boss")
                || action.equals("dungeon:floor2") || action.equals("dungeon:floor2-boss")
                || action.equals("dungeon:floor3") || action.equals("dungeon:floor3-boss")
                || action.equals("dungeon:test") || action.equals("dungeon:test-boss")
                || action.equals("dungeon:ice") || action.equals("dungeon:ice-boss")
                || action.equals("dungeon:endless") || action.equals("dungeon:endless-boss")) {
            player.closeInventory();
            boolean bossOnly = action.endsWith("boss");
            // endless → schematic paste test; ice/test → classic Floor 2 frost
            int floor = action.contains("endless") ? 6
                    : action.contains("ice") || action.contains("test") ? 2
                    : action.contains("floor3") ? 3
                    : action.contains("floor2") ? 2 : 1;
            if (!DevBridges.startDungeon(player, bossOnly, floor)) {
                player.sendMessage("§cAetherionDungeons is not loaded.");
            }
            return;
        }
        if (action.equals("farmportal:ensure")) {
            player.closeInventory();
            player.sendMessage(DevBridges.farmPortalEnsure(false));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.3f);
            return;
        }
        if (action.equals("forageisle:open")) {
            // Forage Island's DEV hub lives in AetherionForaging (/grove dev) — this tile only opens it.
            player.closeInventory();
            if (!player.performCommand("grove dev")) {
                player.sendMessage("§cAetherionForaging offline.");
            }
            return;
        }
        if (action.startsWith("isle-weather:")) {
            String kind = action.substring("isle-weather:".length());
            if (kind.equalsIgnoreCase("clear-override")) {
                if (!DevBridges.isleWeatherClear(player)) {
                    player.sendMessage("§cAetherionForaging offline.");
                    return;
                }
                player.sendMessage("§aIsle weather §7override cleared — ambient again.");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
                open(player, Page.ISLE_WEATHER);
                return;
            }
            if (!DevBridges.isleWeatherForce(player, kind, 75)) {
                player.sendMessage("§cCould not force §f" + kind + "§c. Stand on the forage isle?");
                return;
            }
            player.sendMessage("§aForced §f" + kind.toUpperCase() + " §7for ~75s (you only).");
            player.playSound(player.getLocation(), Sound.WEATHER_RAIN, 0.4f, 1.4f);
            open(player, Page.ISLE_WEATHER);
            return;
        }
        if (action.equals("farmportal:rebuild")) {
            if (!click.isShiftClick()) {
                player.sendMessage("§eSneak-click to rebuild (re-pastes the schematic).");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.7f);
                return;
            }
            player.closeInventory();
            player.sendMessage(DevBridges.farmPortalEnsure(true));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.1f);
            return;
        }
        if (action.equals("farmportal:tool")) {
            ItemStack tool = DevBridges.farmPortalTool();
            if (tool == null) {
                player.sendMessage("§cAetherionFarming is not loaded.");
                return;
            }
            give(player, tool);
            return;
        }
        if (action.equals("farmportal:set-exit")) {
            player.closeInventory();
            DevBridges.farmPortalSetIslandExit(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            return;
        }
        if (action.equals("farmportal:tp")) {
            player.closeInventory();
            DevBridges.farmPortalTeleport(player);
            return;
        }
        if (action.equals("farmportal:ambience")) {
            player.closeInventory();
            player.sendMessage(DevBridges.farmPortalRefreshAmbience());
            player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, 0.8f, 1.1f);
            return;
        }
        if (action.equals("skills:max")) {
            AetherionItems plugin = AetherionItems.getInstance();
            if (plugin == null || plugin.getSkills() == null) {
                player.sendMessage("§cSkills are not loaded.");
                return;
            }
            plugin.getSkills().setLevelAll(player, de.aetherion.items.skill.SkillProgression.MAX_LEVEL);
            plugin.getSkills().grantAllSlots(player);
            player.sendMessage("§aAll skills set to Lv. " + de.aetherion.items.skill.SkillProgression.MAX_LEVEL + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
            return;
        }
        if (action.equals("skills:wipe")) {
            AetherionItems plugin = AetherionItems.getInstance();
            if (plugin == null || plugin.getSkills() == null) {
                player.sendMessage("§cSkills are not loaded.");
                return;
            }
            plugin.getSkills().wipeProgress(player);
            player.sendMessage("§eAll skill levels wiped to 1. Loadout cleared.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 0.8f);
            return;
        }
        if (action.equals("quests:reset-all")) {
            player.closeInventory();
            if (!player.performCommand("aquest reset all")) {
                // Fallback if command map misses — call Quests directly.
                try {
                    org.bukkit.plugin.Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
                    if (quests == null) {
                        player.sendMessage("§cAetherionQuests is not loaded.");
                        return;
                    }
                    Object manager = quests.getClass().getMethod("getQuestManager").invoke(quests);
                    manager.getClass().getMethod("resetAllQuests", Player.class).invoke(manager, player);
                    player.sendMessage("§eAll quests reset. Harbour onboarding is fresh.");
                } catch (ReflectiveOperationException exception) {
                    player.sendMessage("§cQuest reset failed. Try §f/aquest reset all§c.");
                }
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.7f);
            return;
        }
        if (action.equals("quests:tutorial-done")) {
            player.closeInventory();
            try {
                org.bukkit.plugin.Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
                if (quests == null) {
                    player.sendMessage("§cAetherionQuests is not loaded.");
                    return;
                }
                Object manager = quests.getClass().getMethod("getQuestManager").invoke(quests);
                Object count = manager.getClass()
                        .getMethod("markTutorialDone", Player.class)
                        .invoke(manager, player);
                AetherionItems plugin = AetherionItems.getInstance();
                if (plugin != null && plugin.progress() != null) {
                    var prog = plugin.progress();
                    prog.unlock(player, de.aetherion.items.progress.ProgressionService.Flag.WORKBENCH);
                    prog.unlock(player, de.aetherion.items.progress.ProgressionService.Flag.SKILLS);
                    prog.unlock(player, de.aetherion.items.progress.ProgressionService.Flag.ANVIL);
                    prog.unlock(player, de.aetherion.items.progress.ProgressionService.Flag.PETS);
                }
                int n = count instanceof Integer i ? i : 0;
                player.sendMessage("§aTutorial marked done §7(§f" + n + "§7 quests completed). Manager flags unlocked.");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.1f);
            } catch (ReflectiveOperationException exception) {
                player.sendMessage("§cTutorial skip failed — update AetherionQuests jar.");
                player.sendMessage("§7" + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            return;
        }
        if (action.equals("give:animal")) {
            give(player, animalZones.createAnchor());
            return;
        }
        if (action.equals("give:mob")) {
            give(player, mobZones.createAnchor());
            return;
        }
        if (action.equals("give:borderlands")) {
            give(player, mobZones.createBorderlandsAnchor());
            return;
        }
        if (action.equals("give:eldervale-mobs")) {
            give(player, mobZones.createEldervaleAnchor());
            return;
        }
        if (action.equals("open:blueprint-forge")) {
            de.aetherion.items.blueprint.BlueprintForgeGUI.open(player, itemManager());
            return;
        }
        if (action.startsWith("give:pet-habitat:")) {
            PetHabitatKind kind = PetHabitatKind.fromId(action.substring("give:pet-habitat:".length()));
            if (kind == null || petHabitats == null) {
                player.sendMessage("§cUnknown pet habitat.");
                return;
            }
            give(player, petHabitats.createAnchor(kind));
            player.sendMessage(kind.coloredName() + " §7pet habitat stick. §eLeft-click §7cycles radius.");
            return;
        }
        if (action.equals("give:colosseum")) {
            ItemStack marker = DevBridges.spawnAnchor("colosseum");
            if (marker == null) {
                player.sendMessage("§cAetherionHub missing — cannot give Colosseum spawn.");
                return;
            }
            give(player, marker);
            player.sendMessage("§6Colosseum spawn anchor §7— place ~10–15 blocks off the pad center.");
            return;
        }
        if (action.equals("give:spirit-all")) {
            for (de.aetherion.items.world.BorderlandsRiteService.SpiritBoss boss
                    : de.aetherion.items.world.BorderlandsRiteService.T1) {
                give(player, de.aetherion.items.world.BorderlandsRiteService.createSpirit(boss));
            }
            player.sendMessage("§cAll Borderlands spirit vials.");
            return;
        }
        if (action.equals("give:crypt-spirit-all")) {
            for (de.aetherion.items.world.BorderlandsRiteService.SpiritBoss boss
                    : de.aetherion.items.world.BorderlandsRiteService.T2) {
                give(player, de.aetherion.items.world.BorderlandsRiteService.createCryptSpirit(boss));
            }
            player.sendMessage("§6All Crypt / Colosseum T2 spirit vials.");
            return;
        }
        if (action.startsWith("give:crypt-spirit:")) {
            String id = action.substring("give:crypt-spirit:".length());
            ItemStack vial = de.aetherion.items.world.BorderlandsRiteService.createCryptSpirit(id);
            if (vial == null) {
                player.sendMessage("§cUnknown Crypt spirit.");
                return;
            }
            give(player, vial);
            return;
        }
        if (action.startsWith("give:spirit:")) {
            String id = action.substring("give:spirit:".length());
            ItemStack vial = de.aetherion.items.world.BorderlandsRiteService.createSpirit(id);
            if (vial == null) {
                player.sendMessage("§cUnknown spirit.");
                return;
            }
            give(player, vial);
            return;
        }
        if (action.startsWith("give:area:")) {
            AreaType type = AreaType.fromId(action.substring("give:area:".length()));
            if (type == null || areas == null) {
                player.sendMessage("§cUnknown area.");
                return;
            }
            give(player, areas.createTool(type));
            return;
        }
        if (action.equals("give:worldmap")) {
            if (worldMaps == null) {
                player.sendMessage("§cWorld map is not loaded.");
                return;
            }
            give(player, worldMaps.createTool());
            return;
        }
        if (action.equals("give:crypt-holo")) {
            if (cryptHolograms == null) {
                player.sendMessage("§cCrypt hologram is not loaded.");
                return;
            }
            give(player, cryptHolograms.createTool());
            return;
        }
        if (action.startsWith("give:banner:")) {
            if (buildingBanners == null) {
                player.sendMessage("§cBuilding banners are not loaded.");
                return;
            }
            BuildingBannerKind kind = BuildingBannerKind.fromId(action.substring("give:banner:".length()));
            if (kind == null) {
                player.sendMessage("§cUnknown banner.");
                return;
            }
            give(player, buildingBanners.createTool(kind));
            return;
        }
        if (action.equals("give:npcremover")) {
            give(player, NpcRemoverListener.create());
            return;
        }
        if (action.equals("give:merchant-chest")) {
            ItemStack chest = DevBridges.merchantChest();
            if (chest == null) {
                player.sendMessage("§cAetherionQuests is not loaded.");
                return;
            }
            give(player, chest);
            return;
        }
        if (action.startsWith("give:explore-chest:")) {
            ItemStack chest = DevBridges.exploreChest(action.substring("give:explore-chest:".length()));
            if (chest == null) {
                player.sendMessage("§cAetherionQuests is not loaded.");
                return;
            }
            give(player, chest);
            return;
        }
        if (action.equals("give:homestead") || action.startsWith("give:homestead:")) {
            String spawnId = action.equals("give:homestead")
                    ? "harbour"
                    : action.substring("give:homestead:".length());
            ItemStack marker = DevBridges.spawnAnchor(spawnId);
            if (marker == null) {
                player.sendMessage("§cAetherionHub is not loaded.");
                return;
            }
            give(player, marker);
            return;
        }
        if (action.equals("give:homestead-all")) {
            List<DevBridges.NamedItem> markers = DevBridges.spawnMarkers();
            if (markers.isEmpty()) {
                player.sendMessage("§cAetherionHub is not loaded.");
                return;
            }
            int given = 0;
            for (DevBridges.NamedItem entry : markers) {
                ItemStack marker = DevBridges.spawnAnchor(entry.id());
                if (marker != null) {
                    give(player, marker);
                    given++;
                }
            }
            player.sendMessage("§aGave §f" + given + " §aspawn anchors.");
            return;
        }
        if (action.equals("unlock:spawns-all")) {
            int unlocked = DevBridges.unlockAllSpawns(player);
            if (unlocked < 0) {
                player.sendMessage("§cAetherionHub is not loaded.");
                return;
            }
            player.sendMessage(unlocked == 0
                    ? "§eYou already have every camp."
                    : "§aUnlocked §f" + unlocked + " §acamp" + (unlocked == 1 ? "" : "s") + "§a for testing.");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            return;
        }
        if (action.startsWith("set:")) {
            giveSet(player, action.substring(4));
            return;
        }
        if (action.startsWith("item:")) {
            ItemStack give = clean(clicked);
            if (click.isShiftClick() && !click.isRightClick() && give.getMaxStackSize() > 1) {
                give.setAmount(give.getMaxStackSize());
            }
            give(player, give);
            return;
        }
        if (action.startsWith("boss:")) {
            giveBoss(player, action.substring(5));
            return;
        }
        if (action.equals("test:goto")) {
            player.closeInventory();
            var arena = AetherionItems.getInstance().testArena();
            if (arena == null || !arena.enter(player)) {
                player.sendMessage("§cCould not open the test arena.");
            }
            return;
        }
        if (action.equals("test:leave")) {
            player.closeInventory();
            var arena = AetherionItems.getInstance().testArena();
            if (arena == null || !arena.leave(player)) {
                player.sendMessage("§cCould not leave the test arena.");
            }
            return;
        }
        if (action.equals("test:rebuild")) {
            var arena = AetherionItems.getInstance().testArena();
            if (arena != null) {
                arena.rebuildPlatform(player);
            }
            open(player, Page.TEST_ARENA);
            return;
        }
        if (action.equals("test:clear-bosses")) {
            var arena = AetherionItems.getInstance().testArena();
            if (arena != null) {
                arena.clearBosses(player);
            }
            return;
        }
        if (action.equals("test:clear-mobs")) {
            var arena = AetherionItems.getInstance().testArena();
            if (arena != null) {
                arena.clearMobs(player);
            }
            return;
        }
        if (action.equals("test:spawn-dummies")) {
            var arena = AetherionItems.getInstance().testArena();
            if (arena != null) {
                arena.spawnDummyMobs(player, 8);
            }
            return;
        }
        if (action.startsWith("test:spawn:")) {
            player.closeInventory();
            String id = action.substring("test:spawn:".length());
            var arena = AetherionItems.getInstance().testArena();
            if (arena == null || !arena.spawnBoss(player, id)) {
                player.sendMessage("§cSpawn failed.");
            }
            return;
        }
        if (action.startsWith("ambient:")) {
            String id = action.substring("ambient:".length());
            var items = AetherionItems.getInstance();
            ItemStack tool = items == null || items.ambientProps() == null
                    ? null
                    : items.ambientProps().tool(id);
            if (tool == null) {
                player.sendMessage("§cUnknown ambient prop.");
                return;
            }
            give(player, tool);
            return;
        }
        if (action.startsWith("testgear:")) {
            String id = action.substring("testgear:".length());
            java.util.List<ItemStack> gearPool = new java.util.ArrayList<>(TestGear.all());
            gearPool.addAll(TestGear.flagships());
            for (ItemStack gear : gearPool) {
                String gearId = gear.hasItemMeta()
                        ? gear.getItemMeta().getPersistentDataContainer().get(
                        de.aetherion.core.AetherKeys.namespaced("aetherion", "test_gear"),
                        org.bukkit.persistence.PersistentDataType.STRING)
                        : null;
                if (id.equals(gearId)) {
                    give(player, gear.clone());
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.3f);
                    return;
                }
            }
            player.sendMessage("§cUnknown test gear.");
            return;
        }
        if (action.startsWith("npc:")) {
            giveNpc(player, action.substring(4));
            return;
        }
        if (action.equals("farmtool:cane")) {
            ItemStack cane = DevBridges.canePatchTool();
            if (cane == null) {
                player.sendMessage("§cAetherionFarming offline — cane patch unavailable.");
                return;
            }
            give(player, cane);
            return;
        }
        if (action.startsWith("farmtool:district:")) {
            ItemStack marker = DevBridges.districtMarker(action.substring("farmtool:district:".length()));
            if (marker == null) {
                player.sendMessage("§cUnknown district marker.");
                return;
            }
            give(player, marker);
            return;
        }
        if (action.equals("farmtool:scarecrow")) {
            ItemStack scarecrow = DevBridges.scarecrowTool();
            if (scarecrow == null) {
                player.sendMessage("§cAetherionFarming offline — scarecrow unavailable.");
                return;
            }
            give(player, scarecrow);
            return;
        }
        if (action.equals("farmtool:haywagon")) {
            ItemStack wagon = DevBridges.hayWagonTool();
            if (wagon == null) {
                player.sendMessage("§cAetherionFarming offline — hay wagon unavailable.");
                return;
            }
            give(player, wagon);
            return;
        }
        if (action.startsWith("ambient:")) {
            String id = action.substring("ambient:".length());
            var items = AetherionItems.getInstance();
            ItemStack tool = items == null || items.ambientProps() == null
                    ? null
                    : items.ambientProps().tool(id);
            if (tool == null) {
                player.sendMessage("§cUnknown ambient prop.");
                return;
            }
            give(player, tool);
            return;
        }
        if (action.equals("farmtool:seed-isle")) {
            // Right-click forces a re-run after the first pass flagged the footprint.
            boolean force = click != null && click.isRightClick();
            player.sendMessage(DevBridges.farmIsleSeed(force));
            return;
        }
        if (action.startsWith("pet:")) {
            String petId = action.substring(4);
            boolean right = click != null && click.isRightClick();
            boolean ok = right
                    ? DevBridges.givePet(player, petId)
                    : DevBridges.spawnPet(player, petId);
            if (!ok) {
                player.sendMessage(right
                        ? "§cCould not add that pet."
                        : "§cCould not spawn that pet.");
            }
            return;
        }
        if (action.equals("pets:unlock-all")) {
            int added = DevBridges.unlockAllPets(player);
            if (added < 0) {
                player.sendMessage("§cAetherMobs is not loaded.");
                return;
            }
            player.sendMessage(
                    "§d✦ §fAetherlex sync done. §a+"
                            + added
                            + " §fnew pets."
            );
            player.sendMessage(
                    "§7Missing first-catch XP is paid out now"
                            + " §8(§b+90 §7per species§8)."
            );
            open(player, Page.PETS);
            return;
        }
        if (action.equals("noop")) {
            return;
        }
        if (action.equals("spawn:ore-troll")) {
            var items = AetherionItems.getInstance();
            if (items == null || items.getOreTrollListener() == null) {
                player.sendMessage("§cOre Troll not ready.");
                return;
            }
            items.getOreTrollListener().spawn(player.getLocation(), player);
            player.closeInventory();
            return;
        }
        if (action.equals("unlock:blueprint-vein-siphon") || action.equals("unlock:blueprint-all")) {
            var unlocks = AetherionItems.getInstance().blueprintUnlocks();
            if (unlocks == null) {
                player.sendMessage("§cBlueprint unlocks not ready.");
                return;
            }
            int unlocked = 0;
            for (de.aetherion.items.blueprint.BlueprintKind kind : de.aetherion.items.blueprint.BlueprintKind.values()) {
                if (unlocks.unlock(player, kind.id())) {
                    unlocked++;
                }
            }
            player.sendMessage(unlocked == 0
                    ? "§eAll skill blueprints already unlocked."
                    : "§aUnlocked §f" + unlocked + " §askill blueprint(s).");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.35f);
            return;
        }
        if (action.equals("give:blueprint-vein-siphon")) {
            give(player, customItem.createBlueprintVeinSiphon());
            return;
        }
        if (action.equals("unlock:recipes-all")) {
            var unlocks = AetherionItems.getInstance().recipeUnlocks();
            if (unlocks == null) {
                player.sendMessage("§cRecipe unlocks not ready.");
                return;
            }
            int added = unlocks.unlockAll(player);
            player.sendMessage(added == 0
                    ? "§eFull unlock already on (all recipes + gates ignored)."
                    : "§aFull unlock §8· §f" + added + " §anew recipes marked §7(+ resources, level, blueprints, spawns).");
            player.sendMessage("§7Recipe requirements are ignored while full unlock is active.");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.35f);
            return;
        }
        if (action.startsWith("sphere:")) {
            ItemStack sphere = DevBridges.catchSphere(action.substring(7));
            if (sphere == null) {
                player.sendMessage("§cAetherMobs is not loaded.");
                return;
            }
            give(player, sphere);
        }
    }

    private void drawContentRoot(Inventory inventory) {
        inventory.setItem(4, DevItems.glow(button(Material.JUNGLE_SAPLING, "§a§l✦ Content Kit ✦", "root",
                "§7Monkey / Homie tools.",
                "§7Flight · NPC · Resources · Props · self shards.",
                "§8Not full admin.")));
        inventory.setItem(20, button(Material.IRON_BLOCK, "§fResources", "page:RESOURCES",
                "§7Compressed / compacted mats."));
        inventory.setItem(21, button(Material.AMETHYST_SHARD, "§bAether Shards", "page:SHARDS",
                "§7Give yourself shards."));
        inventory.setItem(22, button(Material.ARMOR_STAND, "§dAmbient Props", "page:AMBIENT",
                "§7BlockDisplay scenery — place freely.",
                "§7Scarecrow · wagon · world props.",
                "§8No tick · safe to place many."));
        inventory.setItem(23, npcEditorButton());
        inventory.setItem(24, button(Material.FEATHER, "§aToggle /flight", "flight-toggle",
                "§7Same as EssentialsX fly.",
                "§8essentials.fly · aetherion.flight"));
        inventory.setItem(31, button(Material.WRITABLE_BOOK, "§b§lNPC / Quest Editor", "page:NPC_EDITOR",
                "§7Create · nearby · wand · list · help",
                "§7and §f/aethernpc§7."));
    }

    /** Cursor monkey-dev-menu NPC / Quest editor section, rebuilt on {@code /npc} subcommands. */
    private void drawNpcEditorSection(Inventory inventory) {
        inventory.setItem(4, button(Material.WRITABLE_BOOK, "§b§lNPC / Quest Editor", "root",
                "§7Dedicated staff section.",
                "§7Also §f/npc §7· §f/aethernpc",
                "§8Story NPCs stay in npcs.yml."));
        inventory.setItem(11, button(Material.NETHER_STAR, "§bOpen editor", "npc-editor",
                "§7Full /npc menu.",
                "§7Create, edit, list, wand."));
        inventory.setItem(13, button(Material.EMERALD_BLOCK, "§aCreate NPC", "npc-editor:create",
                "§7Name in chat, then place at your feet."));
        inventory.setItem(15, button(Material.COMPASS, "§eEdit nearby", "npc-editor:nearby",
                "§7Closest editor NPC within 8 blocks."));
        inventory.setItem(21, button(Material.BLAZE_ROD, "§6Get wand", "npc-editor:wand",
                "§7Right-click air — editor menu.",
                "§7Right-click an editor NPC — edit."));
        inventory.setItem(22, button(Material.WRITABLE_BOOK, "§dNPC & Quest Editor", "open:aethernpc",
                "§7Create a talking NPC in under a minute.",
                "§7Optional: give them a simple quest.",
                "§8Story NPCs stay untouched.",
                "§eOpens /aethernpc"));
        inventory.setItem(23, button(Material.BOOK, "§6List NPCs", "npc-editor:list",
                "§7Every editor NPC you created."));
        inventory.setItem(31, button(Material.KNOWLEDGE_BOOK, "§fHelp", "npc-editor:help",
                "§7Commands and permissions."));
        inventory.setItem(45, button(Material.ARROW, "§eBack", "back", "§7Return to DEV menu."));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private ItemStack npcEditorButton() {
        return button(Material.BLAZE_ROD, "§6§lNPC Editor", "npc-wand",
                "§7FancyNPC + quest creator.",
                "§7Opens §f/npc §7(same as §f/npc wand§7).",
                "§8Permission: §faetherion.npc.editor");
    }

    private void drawPage(Inventory inventory, Page page, UUID target, int index) {
        if (page == Page.RANKS) {
            drawRanks(inventory, target);
            return;
        }
        if (page == Page.SHARDS) {
            drawShards(inventory, target);
            return;
        }
        if (page == Page.PLAYER_WIPE) {
            drawPlayerWipe(inventory, target, 0L);
            return;
        }
        if (page == Page.WEAPONS) {
            drawWeaponsHub(inventory);
            return;
        }
        if (page == Page.DUNGEONS) {
            drawDungeons(inventory);
            return;
        }
        if (page == Page.PORTALS) {
            drawPortals(inventory);
            return;
        }
        if (page == Page.BOOSTER_LAB) {
            drawBoosterLab(inventory, null);
            return;
        }
        if (page == Page.NPCS) {
            drawNpcHub(inventory);
            return;
        }
        if (page == Page.NPC_EDITOR) {
            drawNpcEditorSection(inventory);
            return;
        }
        if (page == Page.TEST_ARENA) {
            drawTestArena(inventory, index);
            return;
        }
        if (page == Page.TESTBOTS) {
            drawTestbots(inventory, index);
            return;
        }
        if (page == Page.TESTBOTS_LIST) {
            drawTestbotList(inventory, index);
            return;
        }
        if (page == Page.TEST_GEAR) {
            drawTestGear(inventory);
            return;
        }
        if (page == Page.BLUEPRINTS) {
            drawBlueprints(inventory, index);
            return;
        }
        if (page == Page.COMBAT || page == Page.MINING || page == Page.SETS
                || page == Page.FARMING || page == Page.FORAGING || page == Page.FISHING
                || page == Page.CATCHER) {
            drawSetGrid(inventory, page, index);
            return;
        }
        List<ItemStack> contents = contents(page);
        // Inner 7×4 only — outer ring stays glass (1 pane thick).
        final int[] contentSlots = DevTheme.INNER;
        int perPage = contentSlots.length;
        int pages = Math.max(1, (contents.size() + perPage - 1) / perPage);
        index = Math.min(Math.max(0, index), pages - 1);
        int start = index * perPage;
        int end = Math.min(contents.size(), start + perPage);
        for (int i = start; i < end; i++) {
            inventory.setItem(contentSlots[i - start], contents.get(i));
        }
        if (contents.isEmpty()) {
            inventory.setItem(22, button(Material.GRAY_DYE, "§7Nothing on this shelf right now", "noop",
                    "§8The plugin that fills it may be offline —",
                    "§8check §9ADMIN › System Status§8."));
        }
        drawPager(inventory, page, index, pages, contents.size() - end);
        int gives = 0;
        for (int i = start; i < end; i++) {
            if (isBatchGive(DevItems.actionOf(contents.get(i)))) {
                gives++;
            }
        }
        if (gives > 1) {
            inventory.setItem(47, button(Material.HOPPER, "§a⇊ Give entire shelf", "give-page",
                    "§7Everything giveable on this page:",
                    "§f" + gives + " §7entries in one click.",
                    "§8Overflow drops at your feet."));
        }
    }

    /** Page arrows live in the top bar (0 / 8) so the bottom row stays pure navigation. */
    void drawPager(Inventory inventory, Page page, int index, int pages, int remaining) {
        if (index > 0) {
            inventory.setItem(DevTheme.PREV, button(Material.ARROW, "§e◀ Previous page",
                    "pageidx:" + page.name() + ":" + (index - 1),
                    "§7Page §f" + index + "§7 / §f" + pages));
        }
        if (index + 1 < pages) {
            inventory.setItem(DevTheme.NEXT, button(Material.ARROW, "§eNext page ▶",
                    "pageidx:" + page.name() + ":" + (index + 1),
                    "§7Page §f" + (index + 2) + "§7 / §f" + pages,
                    remaining > 0 ? "§8" + remaining + " more waiting." : "§8More waiting."));
        }
    }

    private void drawNpcHub(Inventory inventory) {
        inventory.setItem(4, button(Material.VILLAGER_SPAWN_EGG, "§bNPC Overview", "root",
                "§7Pick a shelf. Anchors place with right-click."));
        inventory.setItem(10, button(Material.IRON_CHESTPLATE, "§fStarter & Lessons", "page:NPCS_STARTER",
                "§7Tutorial loop + Vex / Ledger / Rook."));
        inventory.setItem(12, button(Material.WITHER_SKELETON_SKULL, "§cBoss Givers", "page:NPCS_BOSSES",
                "§7World boss quest NPCs."));
        inventory.setItem(14, button(Material.OAK_SAPLING, "§aWorld & Flavor", "page:NPCS_WORLD",
                "§7Skill-gated, pantry, pets, gossip.",
                "§eOre Ledger, Dock Scaler, Larder…"));
        inventory.setItem(16, button(Material.EMERALD, "§6Services & Tools", "page:NPCS_SERVICES",
                "§7Traders, bazaar, remover, chests."));
        inventory.setItem(45, button(Material.ARROW, "§eBack", "back"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    /**
     * Blueprints page: stones/forge on top, blueprints one row, tools one row.
     */
    private void drawBlueprints(Inventory inventory, int index) {
        inventory.setItem(4, button(Material.FILLED_MAP, "§bBlueprints", "page:BLUEPRINTS",
                "§7Stones · schematics · finished tools.",
                "§8Glass border kept clear."));

        inventory.setItem(10, itemButton(customItem.createBlueprintUpgradeStone2(), "blueprint_upgrade_stone_2"));
        inventory.setItem(11, itemButton(customItem.createBlueprintUpgradeStone3(), "blueprint_upgrade_stone_3"));
        inventory.setItem(12, itemButton(customItem.createBlueprintUpgradeStone4(), "blueprint_upgrade_stone_4"));
        inventory.setItem(14, button(Material.ANVIL, "§bOpen Blueprint Forge", "open:blueprint-forge",
                "§7Same GUI as Eldervale Forgehand."));
        inventory.setItem(16, button(Material.ZOMBIE_SPAWN_EGG, "§6Spawn Ore Troll", "spawn:ore-troll",
                "§725 HP · any pickaxe",
                "§750% random skill blueprint"));

        inventory.setItem(19, itemButton(customItem.createBlueprintVeinSiphon(), "blueprint_vein_siphon"));
        inventory.setItem(20, itemButton(customItem.createBlueprintCanopyCleaver(), "blueprint_canopy_cleaver"));
        inventory.setItem(21, itemButton(customItem.createBlueprintBountyHoe(), "blueprint_bounty_hoe"));
        inventory.setItem(22, itemButton(customItem.createBlueprintWildSight(), "blueprint_wild_sight"));
        inventory.setItem(23, itemButton(customItem.createBlueprintTideLatch(), "blueprint_tide_latch"));
        inventory.setItem(24, itemButton(customItem.createBlueprintResonanceScythe(), "blueprint_resonance_scythe"));

        inventory.setItem(28, itemButton(customItem.createVeinSiphon(), "vein_siphon"));
        inventory.setItem(29, itemButton(customItem.createCanopyCleaver(), "canopy_cleaver"));
        inventory.setItem(30, itemButton(customItem.createBountyHoe(), "bounty_hoe"));
        inventory.setItem(31, itemButton(customItem.createWildSight(), "wild_sight"));
        inventory.setItem(32, itemButton(customItem.createTideLatch(), "tide_latch"));
        inventory.setItem(33, itemButton(customItem.createResonanceScythe(false), "resonance_scythe"));

        inventory.setItem(40, button(Material.KNOWLEDGE_BOOK, "§aUnlock all blueprints", "unlock:blueprint-all",
                "§7DEV: mark every skill blueprint found."));

        inventory.setItem(45, button(Material.ARROW, "§eBack", "back"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private void drawWeaponsHub(Inventory inventory) {
        inventory.setItem(4, button(Material.NETHERITE_SWORD, "§cWeapons", "root",
                "§7Split so the junk drawer stops being a junk drawer."));
        inventory.setItem(10, button(Material.WOODEN_SWORD, "§fStarter", "page:WEAPONS_STARTER",
                "§7Early melee, knives, rods."));
        inventory.setItem(11, button(Material.BOW, "§eBows", "page:WEAPONS_BOWS",
                "§7Longbows and shortbows."));
        inventory.setItem(12, button(Material.DIAMOND_SWORD, "§bProgression", "page:WEAPONS_PROGRESSION",
                "§7Compressed swords and combat blades."));
        inventory.setItem(13, button(Material.WITHER_SKELETON_SKULL, "§5T1 Uniques", "page:WEAPONS_T1",
                "§7World boss toys. The first argument."));
        inventory.setItem(14, button(Material.END_CRYSTAL, "§dT2 Uniques", "page:WEAPONS_T2",
                "§7Off-hands and the staff that files tickets."));
        inventory.setItem(15, button(Material.HEART_OF_THE_SEA, "§3Dungeon", "page:WEAPONS_DUNGEON",
                "§7Cores and relic weapons."));
        inventory.setItem(16, button(Material.NETHERITE_SWORD, "§6Test Extras", "page:WEAPONS_GOD",
                "§7Void stick / leftovers."));
        inventory.setItem(22, button(Material.CHERRY_LEAVES, "§dSpecial Weapons", "page:WEAPONS_SPECIAL",
                "§7Boss-drop showpieces.",
                "§dAshen Katana"));
        inventory.setItem(45, button(Material.ARROW, "§eBack", "back"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private void drawSetGrid(Inventory inventory, Page page, int index) {
        List<ItemStack[]> columns = setColumns(page);
        // Inner columns only. 6 per page everywhere: the bottom row has exactly six weapon slots
        // (46–48 / 50–52), so a 7th column would silently lose its weapon.
        final int colsPerPage = 6;
        int pageIndex = Math.max(0, index);
        int start = pageIndex * colsPerPage;
        if (start >= columns.size() && pageIndex > 0) {
            pageIndex = (columns.size() - 1) / colsPerPage;
            start = pageIndex * colsPerPage;
        }
        int end = Math.min(columns.size(), start + colsPerPage);
        final int[] fifthSlots = {46, 47, 48, 50, 51, 52};
        for (int i = start; i < end; i++) {
            int col = i - start;
            ItemStack[] pieces = columns.get(i);
            int armorRows = Math.min(pieces.length, 4);
            for (int row = 0; row < armorRows; row++) {
                if (pieces[row] != null) {
                    inventory.setItem((row + 1) * 9 + (col + 1), pieces[row]);
                }
            }
            if (pieces.length > 4 && pieces[4] != null && col < fifthSlots.length) {
                inventory.setItem(fifthSlots[col], pieces[4]);
            }
        }
        int pages = Math.max(1, (columns.size() + colsPerPage - 1) / colsPerPage);
        drawPager(inventory, page, pageIndex, pages, columns.size() - end);
        List<String> lore = new ArrayList<>();
        lore.add("§8" + DevTheme.breadcrumb(page));
        lore.add("§7Each column is one set: helmet → boots,");
        lore.add("§7weapon or §6full-set§7 button on the bottom row.");
        lore.add("§7" + columns.size() + " sets §8· §7page §f" + (pageIndex + 1) + "§7/§f" + pages);
        lore.add("");
        lore.addAll(DevTheme.controls());
        inventory.setItem(DevTheme.HEADER, button(DevTheme.info(page).cat().icon,
                DevTheme.info(page).cat().accent + "§l" + DevTheme.pageName(page), "noop", lore.toArray(String[]::new)));
    }

    private List<ItemStack[]> setColumns(Page page) {
        List<ItemStack[]> columns = new ArrayList<>();
        switch (page) {
            case COMBAT -> {
                columns.add(gearColumn("§fCombat I", "combat1",
                        customItem.createCombatHelmet(), customItem.createCombatChestplate(),
                        customItem.createCombatLeggings(), customItem.createCombatBoots(),
                        customItem.createCombatSword()));
                columns.add(gearColumn("§fCombat II", "combat2",
                        customItem.createCombatHelmet2(), customItem.createCombatChestplate2(),
                        customItem.createCombatLeggings2(), customItem.createCombatBoots2(),
                        customItem.createCombatSword2()));
                columns.add(gearColumn("§6Combat III", "combat3",
                        customItem.createCombatHelmet3(), customItem.createCombatChestplate3(),
                        customItem.createCombatLeggings3(), customItem.createCombatBoots3(),
                        customItem.createCombatSword3()));
                columns.add(gearColumn("§bCombat IV", "combat4",
                        customItem.createCombatHelmet4(), customItem.createCombatChestplate4(),
                        customItem.createCombatLeggings4(), customItem.createCombatBoots4(),
                        customItem.createCombatSword4()));
                columns.add(gearColumn("§5Combat V", "combat5",
                        customItem.createCombatHelmet5(), customItem.createCombatChestplate5(),
                        customItem.createCombatLeggings5(), customItem.createCombatBoots5(),
                        customItem.createCombatSword5()));
            }
            case MINING -> {
                columns.add(gearColumn("§fMining I", "mining1",
                        customItem.createMiningHelmet(), customItem.createMiningChestplate(),
                        customItem.createMiningLeggings(), customItem.createMiningBoots(),
                        customItem.createMiningPickaxe()));
                columns.add(gearColumn("§fMining II", "mining2",
                        customItem.createMiningHelmet2(), customItem.createMiningChestplate2(),
                        customItem.createMiningLeggings2(), customItem.createMiningBoots2(),
                        customItem.createMiningPickaxe2()));
                columns.add(gearColumn("§fMining III", "mining3",
                        customItem.createMiningHelmet3(), customItem.createMiningChestplate3(),
                        customItem.createMiningLeggings3(), customItem.createMiningBoots3(),
                        customItem.createMiningPickaxe3()));
                columns.add(gearColumn("§bMining IV", "mining4",
                        customItem.createMiningHelmet4(), customItem.createMiningChestplate4(),
                        customItem.createMiningLeggings4(), customItem.createMiningBoots4(),
                        customItem.createMiningPickaxe4()));
                columns.add(gearColumn("§5Mining V", "mining5",
                        customItem.createMiningHelmet5(), customItem.createMiningChestplate5(),
                        customItem.createMiningLeggings5(), customItem.createMiningBoots5(),
                        customItem.createMiningPickaxe5()));
                columns.add(new ItemStack[]{
                        tagged(customItem.progression().createCompressedStonePickaxe().clone(), "item:compressed_stone_pickaxe"),
                        tagged(customItem.progression().createCompactedCobbleHammer().clone(), "item:compacted_cobble_hammer"),
                        tagged(customItem.progression().createCompactedIronPickaxe().clone(), "item:compacted_iron_pickaxe"),
                        tagged(customItem.progression().createCompactedDiamondPickaxe().clone(), "item:compacted_diamond_pickaxe"),
                        tagged(customItem.progression().createCompactedTimberAxe().clone(), "item:compacted_timber_axe")
                });
            }
            case FARMING -> {
                columns.add(gearColumn("§aFurrow I", "farming1",
                        customItem.farming().helmet(1), customItem.farming().chestplate(1),
                        customItem.farming().leggings(1), customItem.farming().boots(1),
                        customItem.farming().hoe(1)));
                columns.add(gearColumn("§bBarnstorm II", "farming2",
                        customItem.farming().helmet(2), customItem.farming().chestplate(2),
                        customItem.farming().leggings(2), customItem.farming().boots(2),
                        customItem.farming().hoe(2)));
                columns.add(gearColumn("§dHaymaker III", "farming3",
                        customItem.farming().helmet(3), customItem.farming().chestplate(3),
                        customItem.farming().leggings(3), customItem.farming().boots(3),
                        customItem.farming().hoe(3)));
                columns.add(gearColumn("§6Threshlord IV", "farming4",
                        customItem.farming().helmet(4), customItem.farming().chestplate(4),
                        customItem.farming().leggings(4), customItem.farming().boots(4),
                        customItem.farming().hoe(4)));
                columns.add(gearColumn("§5Verdant V", "farming5",
                        customItem.farming().helmet(5), customItem.farming().chestplate(5),
                        customItem.farming().leggings(5), customItem.farming().boots(5),
                        customItem.farming().hoe(5)));
            }
            case FORAGING -> {
                columns.add(gearColumn("§aKindling I", "foraging1",
                        customItem.foraging().helmet(1), customItem.foraging().chestplate(1),
                        customItem.foraging().leggings(1), customItem.foraging().boots(1),
                        customItem.foraging().axe(1)));
                columns.add(gearColumn("§2Windfall II", "foraging2",
                        customItem.foraging().helmet(2), customItem.foraging().chestplate(2),
                        customItem.foraging().leggings(2), customItem.foraging().boots(2),
                        customItem.foraging().axe(2)));
                columns.add(gearColumn("§aHeartwood III", "foraging3",
                        customItem.foraging().helmet(3), customItem.foraging().chestplate(3),
                        customItem.foraging().leggings(3), customItem.foraging().boots(3),
                        customItem.foraging().axe(3)));
                columns.add(gearColumn("§2Canopy IV", "foraging4",
                        customItem.foraging().helmet(4), customItem.foraging().chestplate(4),
                        customItem.foraging().leggings(4), customItem.foraging().boots(4),
                        customItem.foraging().axe(4)));
                columns.add(gearColumn("§5Worldroot V", "foraging5",
                        customItem.foraging().helmet(5), customItem.foraging().chestplate(5),
                        customItem.foraging().leggings(5), customItem.foraging().boots(5),
                        customItem.foraging().axe(5)));
            }
            case FISHING -> {
                columns.add(gearColumn("§bNibble I", "fishing1",
                        customItem.fishing().helmet(1), customItem.fishing().chestplate(1),
                        customItem.fishing().leggings(1), customItem.fishing().boots(1),
                        customItem.fishing().rod(1)));
                columns.add(gearColumn("§3Ripple II", "fishing2",
                        customItem.fishing().helmet(2), customItem.fishing().chestplate(2),
                        customItem.fishing().leggings(2), customItem.fishing().boots(2),
                        customItem.fishing().rod(2)));
                columns.add(gearColumn("§9Keelhaul III", "fishing3",
                        customItem.fishing().helmet(3), customItem.fishing().chestplate(3),
                        customItem.fishing().leggings(3), customItem.fishing().boots(3),
                        customItem.fishing().rod(3)));
                columns.add(gearColumn("§3Abyssal IV", "fishing4",
                        customItem.fishing().helmet(4), customItem.fishing().chestplate(4),
                        customItem.fishing().leggings(4), customItem.fishing().boots(4),
                        customItem.fishing().rod(4)));
                columns.add(gearColumn("§5Leviathan V", "fishing5",
                        customItem.fishing().helmet(5), customItem.fishing().chestplate(5),
                        customItem.fishing().leggings(5), customItem.fishing().boots(5),
                        customItem.fishing().rod(5)));
                columns.add(armorColumn("§3Tideglass", "diving",
                        customItem.fishing().divingHelmet(), customItem.fishing().divingChestplate(),
                        customItem.fishing().divingLeggings(), customItem.fishing().divingBoots()));
            }
            case CATCHER -> {
                columns.add(gearColumn("§eCatcher I", "catcher1",
                        customItem.catcher().helmet(1), customItem.catcher().chestplate(1),
                        customItem.catcher().leggings(1), customItem.catcher().boots(1),
                        customItem.catcher().gaff(1)));
                columns.add(gearColumn("§bSnare II", "catcher2",
                        customItem.catcher().helmet(2), customItem.catcher().chestplate(2),
                        customItem.catcher().leggings(2), customItem.catcher().boots(2),
                        customItem.catcher().gaff(2)));
                columns.add(gearColumn("§6Menagerie III", "catcher3",
                        customItem.catcher().helmet(3), customItem.catcher().chestplate(3),
                        customItem.catcher().leggings(3), customItem.catcher().boots(3),
                        customItem.catcher().gaff(3)));
            }
            case SETS -> {
                columns.add(armorColumn("§2Rotten", "rotten",
                        customItem.createRottenHelmet(), customItem.createRottenChestplate(),
                        customItem.createRottenLeggings(), customItem.createRottenBoots()));
                columns.add(armorColumn("§fBone", "bone",
                        customItem.createBoneHelmet(), customItem.createBoneChestplate(),
                        customItem.createBoneLeggings(), customItem.createBoneBoots()));
                // Webweave was only in the dead contents(SETS) list — surfaced here so it is reachable.
                columns.add(armorColumn("§fWebweave", "webweave",
                        customItem.createWebweaveHelmet(), customItem.createWebweaveChestplate(),
                        customItem.createWebweaveLeggings(), customItem.createWebweaveBoots()));
                columns.add(armorColumn("§5Ironhide", "ironhide",
                        customItem.createIronhideHelmet(), customItem.createIronhideChestplate(),
                        customItem.createIronhideLeggings(), customItem.createIronhideBoots()));
                columns.get(columns.size() - 1)[4] = tagged(customItem.createObsidianMaul(), "item:obsidian_maul");
                columns.add(armorColumn("§aHealer", "healer",
                        customItem.createHealerHelmet(), customItem.createHealerChestplate(),
                        customItem.createHealerLeggings(), customItem.createHealerBoots()));
                columns.get(columns.size() - 1)[4] = tagged(customItem.createMenderStaff(), "item:mender_staff");
                columns.add(armorColumn("§7Dungeon Vestige", "dungeon_vestige",
                        customItem.createDungeonVestigeHelmet(), customItem.createDungeonVestigeChestplate(),
                        customItem.createDungeonVestigeLeggings(), customItem.createDungeonVestigeBoots()));
                columns.add(armorColumn("§5Unidentified II", "dungeon_relic_t2",
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.HELMET),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.LEGGINGS),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.BOOTS)));
                columns.get(columns.size() - 1)[4] = tagged(
                        customItem.createDungeonWeaponRelic(de.aetherion.items.dungeon.DungeonGearTier.T2),
                        "item:dungeon_relic_t2_weapon");
                columns.add(armorColumn("§6Unidentified III", "dungeon_relic_t3",
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.HELMET),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.LEGGINGS),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.BOOTS)));
                columns.get(columns.size() - 1)[4] = tagged(
                        customItem.createDungeonWeaponRelic(de.aetherion.items.dungeon.DungeonGearTier.T3),
                        "item:dungeon_relic_t3_weapon");
                columns.add(armorColumn("§dAetherion", "aetherion",
                        customItem.createAetherionHelmet(), customItem.createAetherionChestplate(),
                        customItem.createAetherionLeggings(), customItem.createAetherionBoots()));
                columns.add(armorColumn("§5Worldhide", "worldhide",
                        customItem.createWorldhideHelmet(), customItem.createWorldhideChestplate(),
                        customItem.createWorldhideLeggings(), customItem.createWorldhideBoots()));
                columns.get(columns.size() - 1)[4] = tagged(customItem.createWorldbite(), "item:worldbite");
                columns.add(armorColumn("§6Hollow Sun", "hollow_sun",
                        customItem.createHollowSunHelmet(), customItem.createHollowSunChestplate(),
                        customItem.createHollowSunLeggings(), customItem.createHollowSunBoots()));
                columns.add(armorColumn("§eDawnbearer", "helios",
                        customItem.createHeliosCrown(), customItem.createHeliosHeartplate(),
                        customItem.createHeliosOrbitGreaves(), customItem.createHeliosDawnTreads()));
                columns.get(columns.size() - 1)[4] = tagged(customItem.createHeliosSolstice(), "item:helios_solstice");
                columns.add(new ItemStack[]{
                        setButton(Material.GOLDEN_CHESTPLATE, "§6God Kit I", "god1"),
                        setButton(Material.NETHERITE_CHESTPLATE, "§6God Kit II", "god2"),
                        null, null, null
                });
            }
            default -> {
            }
        }
        return columns;
    }

    private ItemStack[] gearColumn(
            String label,
            String setId,
            ItemStack helmet,
            ItemStack chest,
            ItemStack legs,
            ItemStack boots,
            ItemStack weapon
    ) {
        return new ItemStack[]{
                tagged(helmet.clone(), "item:" + itemIdOf(helmet)),
                tagged(chest.clone(), "item:" + itemIdOf(chest)),
                tagged(legs.clone(), "item:" + itemIdOf(legs)),
                tagged(boots.clone(), "item:" + itemIdOf(boots)),
                tagged(weapon.clone(), "item:" + itemIdOf(weapon))
        };
    }

    private ItemStack[] armorColumn(
            String label,
            String setId,
            ItemStack helmet,
            ItemStack chest,
            ItemStack legs,
            ItemStack boots
    ) {
        return new ItemStack[]{
                tagged(helmet.clone(), "item:" + itemIdOf(helmet)),
                tagged(chest.clone(), "item:" + itemIdOf(chest)),
                tagged(legs.clone(), "item:" + itemIdOf(legs)),
                tagged(boots.clone(), "item:" + itemIdOf(boots)),
                setButton(chest.getType(), label, setId)
        };
    }

    private String itemIdOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "unknown";
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        return id != null ? id : "unknown";
    }

    private List<ItemStack> contents(Page page) {
        List<ItemStack> items = new ArrayList<>();
        switch (page) {
            case COMBAT -> {
                items.add(setButton(Material.LEATHER_CHESTPLATE, "§fCombat I", "combat1"));
                items.add(setButton(Material.IRON_CHESTPLATE, "§fCombat II", "combat2"));
                items.add(setButton(Material.GOLDEN_CHESTPLATE, "§6Combat III", "combat3"));
                items.add(setButton(Material.DIAMOND_CHESTPLATE, "§bCombat IV", "combat4"));
                items.add(setButton(Material.NETHERITE_CHESTPLATE, "§5Combat V", "combat5"));
            }
            case MINING -> {
                items.add(setButton(Material.WOODEN_PICKAXE, "§fMining I", "mining1"));
                items.add(setButton(Material.STONE_PICKAXE, "§fMining II", "mining2"));
                items.add(setButton(Material.IRON_PICKAXE, "§fMining III", "mining3"));
                items.add(setButton(Material.DIAMOND_PICKAXE, "§bMining IV", "mining4"));
                items.add(setButton(Material.NETHERITE_PICKAXE, "§5Mining V", "mining5"));
            }
            case FARMING -> {
                items.add(setButton(Material.WOODEN_HOE, "§aFurrow I", "farming1"));
                items.add(setButton(Material.STONE_HOE, "§bBarnstorm II", "farming2"));
                items.add(setButton(Material.IRON_HOE, "§dHaymaker III", "farming3"));
                items.add(setButton(Material.DIAMOND_HOE, "§6Threshlord IV", "farming4"));
                items.add(setButton(Material.NETHERITE_HOE, "§5Verdant V", "farming5"));
            }
            case FORAGING -> {
                items.add(setButton(Material.WOODEN_AXE, "§aKindling I", "foraging1"));
                items.add(setButton(Material.STONE_AXE, "§2Windfall II", "foraging2"));
                items.add(setButton(Material.IRON_AXE, "§aHeartwood III", "foraging3"));
                items.add(setButton(Material.DIAMOND_AXE, "§2Canopy IV", "foraging4"));
                items.add(setButton(Material.NETHERITE_AXE, "§5Worldroot V", "foraging5"));
            }
            case FISHING -> {
                items.add(setButton(Material.FISHING_ROD, "§bNibble I", "fishing1"));
                items.add(setButton(Material.FISHING_ROD, "§3Ripple II", "fishing2"));
                items.add(setButton(Material.FISHING_ROD, "§9Keelhaul III", "fishing3"));
                items.add(setButton(Material.FISHING_ROD, "§3Abyssal IV", "fishing4"));
                items.add(setButton(Material.FISHING_ROD, "§5Leviathan V", "fishing5"));
            }
            case WEAPONS_STARTER -> {
                items.add(itemButton(customItem.createSimpleSword(), "simple_sword"));
                items.add(itemButton(customItem.createSplinterGlaive(), "splinter_glaive"));
                items.add(itemButton(customItem.createAshenCleaver(), "ashen_cleaver"));
                items.add(itemButton(customItem.createBoneKnife(), "bone_knife"));
                items.add(itemButton(customItem.createVenomDagger(), "venom_dagger"));
                items.add(itemButton(customItem.createWoodenRod(), "wooden_rod"));
                items.add(itemButton(customItem.createCopperRod(), "copper_rod"));
                items.add(itemButton(customItem.createFrostShard(), "frost_shard"));
                items.add(itemButton(customItem.createMenderStaff(), "mender_staff"));
                items.add(itemButton(customItem.createEmberRod(), "ember_rod"));
                items.add(itemButton(customItem.createObsidianMaul(), "obsidian_maul"));
            }
            case WEAPONS_BOWS -> {
                items.add(itemButton(customItem.createSimpleLongbow(), "simple_longbow"));
                items.add(itemButton(customItem.createIronLongbow(), "iron_longbow"));
                items.add(itemButton(customItem.createSimpleShortbow(), "simple_shortbow"));
                items.add(itemButton(customItem.createReinforcedShortbow(), "reinforced_shortbow"));
            }
            case WEAPONS_PROGRESSION -> {
                items.add(itemButton(customItem.progression().createCopperSword(), "copper_sword"));
                items.add(itemButton(customItem.progression().createCompressedGoldSword(), "compressed_gold_sword"));
                items.add(itemButton(customItem.progression().createCompactedMidasDagger(), "compacted_midas_dagger"));
                items.add(itemButton(customItem.progression().createCompactedDiamondSword(), "compacted_diamond_sword"));
                items.add(itemButton(customItem.progression().createCompactedEmeraldScythe(), "compacted_emerald_scythe"));
                items.add(itemButton(customItem.createCombatSword(), "combat_sword"));
                items.add(itemButton(customItem.createCombatSword2(), "combat_sword_2"));
                items.add(itemButton(customItem.createCombatSword3(), "combat_sword_3"));
                items.add(itemButton(customItem.createCombatSword4(), "combat_sword_4"));
                items.add(itemButton(customItem.createCombatSword5(), "combat_sword_5"));
            }
            case WEAPONS_T1 -> {
                items.add(itemButton(customItem.createSkuldugeryShortbow(), "shortbow"));
                items.add(itemButton(customItem.createHollowLongbow(), "hollow_longbow"));
                items.add(itemButton(customItem.createAetherblade(), "aetherblade"));
                items.add(itemButton(customItem.createBridgedAxe(), "bridged_axe"));
                items.add(itemButton(customItem.createWarpedBlade(), "warped_blade"));
                items.add(itemButton(customItem.createSquidsBoot(), "squids_boot"));
                items.add(itemButton(customItem.createAetherionVoidStick(), "void_stick"));
            }
            case WEAPONS_T2 -> {
                items.add(itemButton(customItem.createAshenKatana(), "ashen_katana"));
                items.add(itemButton(customItem.createWorldbite(), "worldbite"));
                items.add(itemButton(customItem.createSeraphineNeedle(), "seraphine_needle"));
                items.add(itemButton(customItem.createSeraphineVeil(), "seraphine_veil"));
                items.add(itemButton(customItem.createSeraphineBodice(), "seraphine_bodice"));
                items.add(itemButton(customItem.createSeraphineBellSkirt(), "seraphine_bell_skirt"));
                items.add(itemButton(customItem.createSeraphinePointeSlippers(), "seraphine_pointe_slippers"));
                items.add(itemButton(customItem.createGravwellCleaver(), "gravwell_cleaver"));
                items.add(itemButton(customItem.createAshenKatana(), "ashen_katana"));
                items.add(itemButton(customItem.createWorldbite(), "worldbite"));
                items.add(itemButton(customItem.createSeraphineNeedle(), "seraphine_needle"));
                items.add(itemButton(customItem.createSeraphineVeil(), "seraphine_veil"));
                items.add(itemButton(customItem.createSeraphineBodice(), "seraphine_bodice"));
                items.add(itemButton(customItem.createSeraphineBellSkirt(), "seraphine_bell_skirt"));
                items.add(itemButton(customItem.createSeraphinePointeSlippers(), "seraphine_pointe_slippers"));
                items.add(itemButton(customItem.createStaffOfTechnicalDifficulties(), "staff_of_technical_difficulties"));
                items.add(itemButton(customItem.createVoidVacuumCharm(), "void_vacuum_charm"));
                items.add(itemButton(customItem.createThermalCore(), "thermal_core"));
                items.add(itemButton(customItem.createPickaxeCoreOfTheBurrower(), "pickaxe_core_of_the_burrower"));
                items.add(itemButton(customItem.createInsolventLedger(), "insolvent_ledger"));
            }
            case WEAPONS_DUNGEON -> {
                items.add(itemButton(customItem.createDungeonCore(), "dungeon_core"));
                items.add(itemButton(customItem.createDungeonCore2(), "dungeon_core_2"));
                items.add(itemButton(customItem.createDungeonCore3(), "dungeon_core_3"));
                items.add(itemButton(customItem.createDungeonWeaponRelic(de.aetherion.items.dungeon.DungeonGearTier.T2), "dungeon_relic_t2_weapon"));
                items.add(itemButton(customItem.createDungeonWeaponRelic(de.aetherion.items.dungeon.DungeonGearTier.T3), "dungeon_relic_t3_weapon"));
                items.add(itemButton(customItem.createWeaponSchematic(), "weapon_schematic"));
                items.add(itemButton(customItem.createDungeonWeaponSchematic(1), "dungeon_weapon_schematic"));
            }
            case WEAPONS_SPECIAL -> {
                items.add(itemButton(customItem.createAshenKatana(), "ashen_katana"));
            }
            case WEAPONS_GOD -> {
                items.add(itemButton(customItem.createAetherionVoidStick(), "void_stick"));
                items.add(itemButton(customItem.createRottenCleaver(), "rotten_cleaver"));
                items.add(itemButton(customItem.createWebweaveFang(), "webweave_fang"));
            }
            case SETS -> {
                items.add(setButton(Material.LEATHER_CHESTPLATE, "§2Rotten Set", "rotten"));
                items.add(setButton(Material.BONE, "§fBone Set", "bone"));
                items.add(setButton(Material.COBWEB, "§fWebweave Set", "webweave"));
                items.add(setButton(Material.IRON_CHESTPLATE, "§5Ironhide Set", "ironhide"));
                items.add(setButton(Material.LEATHER_CHESTPLATE, "§aHealer Set", "healer"));
                items.add(setButton(Material.NETHERITE_CHESTPLATE, "§d✦✦✦ Aetherion Set", "aetherion"));
                items.add(setButton(Material.NETHERITE_CHESTPLATE, "§5✦✦✦ Worldhide Set", "worldhide"));
                items.add(setButton(Material.NETHERITE_CHESTPLATE, "§6✦✦✦ Hollow Sun Set", "hollow_sun"));
                items.add(setButton(Material.NETHERITE_CHESTPLATE, "§e✦✦✦ Dawnbearer Set", "helios"));
                items.add(setButton(Material.LEATHER_CHESTPLATE, "§7Dungeon Vestige", "dungeon_vestige"));
                items.add(itemButton(customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE), "dungeon_relic_t2_chestplate"));
                items.add(itemButton(customItem.createDungeonWeaponRelic(de.aetherion.items.dungeon.DungeonGearTier.T2), "dungeon_relic_t2_weapon"));
                items.add(itemButton(customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE), "dungeon_relic_t3_chestplate"));
                items.add(itemButton(customItem.createDungeonWeaponRelic(de.aetherion.items.dungeon.DungeonGearTier.T3), "dungeon_relic_t3_weapon"));
                items.add(itemButton(customItem.createWeaponSchematic(), "weapon_schematic"));
                items.add(itemButton(customItem.createDungeonWeaponSchematic(1), "dungeon_weapon_schematic"));
                items.add(itemButton(customItem.createRottenCleaver(), "rotten_cleaver"));
                items.add(itemButton(customItem.createWebweaveFang(), "webweave_fang"));
                items.add(itemButton(customItem.createAetherionVoidStick(), "void_stick"));
            }
            case BOOSTERS -> {
                items.add(itemButton(customItem.createCoalBooster(), "booster_coal"));
                items.add(itemButton(customItem.createIronBooster(), "booster_iron"));
                items.add(itemButton(customItem.createGoldBooster(), "booster_gold"));
                items.add(itemButton(customItem.createDiamondBooster(), "booster_diamond"));
                items.add(itemButton(customItem.createEmeraldBooster(), "booster_emerald"));
                items.add(itemButton(customItem.createRedstoneBooster(), "booster_redstone"));
                items.add(itemButton(customItem.createLapisBooster(), "booster_lapis"));
                items.add(itemButton(customItem.createGlowstoneBooster(), "booster_glowstone"));
                items.add(itemButton(customItem.createWheatBooster(), "booster_wheat"));
                items.add(itemButton(customItem.createOakBooster(), "booster_oak"));
                items.add(itemButton(customItem.createBirchBooster(), "booster_birch"));
                items.add(itemButton(customItem.createCarrotBooster(), "booster_carrot"));
            }
            case TOOLS -> {
                items.add(itemButton(customItem.create(), "beginner_pickaxe"));
                items.add(itemButton(customItem.createSimplePickaxe(), "simple_pickaxe"));
                items.add(itemButton(customItem.createSimpleAxe(), "simple_axe"));
                items.add(itemButton(customItem.createSimpleHoe(), "simple_hoe"));
                items.add(itemButton(customItem.createSimpleHelmet(), "simple_helmet"));
                items.add(itemButton(customItem.createSimpleChestplate(), "simple_chest"));
                items.add(itemButton(customItem.createSimpleLeggings(), "simple_legs"));
                items.add(itemButton(customItem.createSimpleBoots(), "simple_boots"));
                items.add(itemButton(customItem.createReinforcedPickaxe(), "reinforced_pickaxe"));
                items.add(itemButton(customItem.createMiningPickaxe(), "mining_pickaxe"));
                items.add(itemButton(customItem.createMiningPickaxe2(), "mining_pickaxe_2"));
                items.add(itemButton(customItem.createMiningPickaxe3(), "mining_pickaxe_3"));
                items.add(itemButton(customItem.createMiningPickaxe4(), "mining_pickaxe_4"));
                items.add(itemButton(customItem.createMiningPickaxe5(), "mining_pickaxe_5"));
                items.add(itemButton(customItem.progression().createCompressedStonePickaxe(), "compressed_stone_pickaxe"));
                items.add(itemButton(customItem.progression().createCompactedCobbleHammer(), "compacted_cobble_hammer"));
                items.add(itemButton(customItem.progression().createCompactedIronPickaxe(), "compacted_iron_pickaxe"));
                items.add(itemButton(customItem.progression().createCompactedDiamondPickaxe(), "compacted_diamond_pickaxe"));
                items.add(itemButton(customItem.progression().createVoided455(), "voided_455"));
                items.add(itemButton(customItem.progression().createCompactedTimberAxe(), "compacted_timber_axe"));
                items.add(itemButton(customItem.progression().createCompressedOakChestplate(), "compressed_oak_chestplate"));
                items.add(itemButton(customItem.progression().createCompressedCoalRing(), "compressed_coal_ring"));
                items.add(itemButton(customItem.progression().createLapisPendant(), "lapis_pendant"));
                items.add(itemButton(customItem.progression().createRedstoneInfusedBoots(), "redstone_infused_boots"));
                items.add(itemButton(customItem.progression().createEmeraldCrown(), "emerald_crown"));
                items.add(itemButton(customItem.progression().createCompactedDiamondChestplate(), "compacted_diamond_chestplate"));
                items.add(itemButton(customItem.createRecipeBook(), "recipe_book"));
                items.add(button(Material.KNOWLEDGE_BOOK, "§aUnlock all recipes", "unlock:recipes-all",
                        "§7Marks every recipe crafted",
                        "§7and every resource obtained.",
                        "§8DEV only."));
                items.add(itemButton(de.aetherion.items.shop.AetherBloodVial.create(), de.aetherion.items.shop.AetherBloodVial.ID));
                items.add(itemButton(storage.createStorage(), "storage"));
                items.add(itemButton(de.aetherion.items.storage.SackItems.create(de.aetherion.items.storage.SackType.RESOURCE), "resource_sack"));
                items.add(itemButton(de.aetherion.items.storage.SackItems.create(de.aetherion.items.storage.SackType.BOOSTER), "booster_sack"));
                items.add(button(Material.HAY_BLOCK, "§aAnimal Anchor", "give:animal", "§7Place in the world."));
                items.add(button(Material.ROTTEN_FLESH, "§cMob Anchor", "give:mob", "§7Hostiles, same radius."));
                items.add(button(Material.COARSE_DIRT, "§6Borderlands Marker", "give:borderlands",
                        "§7Combat waste + TAB name.",
                        "§7Left-click cycles radius."));
                items.add(button(Material.POTION, "§cBorderlands Spirits", "page:BORDERLANDS_SPIRITS",
                        "§7Ritual vials for the powder altar."));
                items.add(button(Material.BONE, "§cNPC Remover", "give:npcremover", "§7Right-click an Aetherion NPC."));
            }
            case SPAWN_MARKERS -> {
                items.add(button(Material.CHEST, "§aGive every spawn anchor", "give:homestead-all",
                        "§7One lodestone per camp.",
                        "§7Place them, then sneak-click",
                        "§7to keep the item."));
                items.add(button(Material.EXPERIENCE_BOTTLE, "§eUnlock every camp for me", "unlock:spawns-all",
                        "§7Tests the spawn menu without",
                        "§7running every boss quest."));
                List<DevBridges.NamedItem> markers = DevBridges.spawnMarkers();
                if (markers.isEmpty()) {
                    items.add(button(Material.BARRIER, "§cAetherionHub missing", "back",
                            "§7Load AetherionHub to give spawn anchors."));
                } else {
                    for (DevBridges.NamedItem entry : markers) {
                        items.add(tagged(named(entry.icon().clone(), entry.name()), "give:homestead:" + entry.id()));
                    }
                }
            }
            case BOSS_ANCHORS -> DevBridges.bossItems(true).forEach(entry -> items.add(tagged(entry.icon().clone(), "boss:" + entry.id())));
            case BOSS_CORES -> DevBridges.bossItems(false).forEach(entry -> items.add(tagged(entry.icon().clone(), "boss:" + entry.id())));
            case PETS -> {
                items.add(button(Material.BOOK, "§dFill Aetherlex", "pets:unlock-all",
                        "§7Adds one of every pet to your",
                        "§7collection if you don't have it.",
                        "§bAlso grants first-catch XP",
                        "§7(+90 Aetherion XP per species).",
                        "§8Aetherlex = fully caught."));
                items.add(itemButton(customItem.createDragonAscensionVial(), "dragon_ascension_vial"));
                DevBridges.pets().forEach(entry -> items.add(tagged(entry.icon().clone(), "pet:" + entry.id())));
            }
            case SPHERES -> {
                items.add(sphereButton("common", Material.SNOWBALL, "§fCommon Sphere"));
                items.add(sphereButton("rare", Material.ENDER_PEARL, "§9Rare Sphere"));
                items.add(sphereButton("epic", Material.HEART_OF_THE_SEA, "§5Epic Sphere"));
                items.add(sphereButton("legendary", Material.GOLD_NUGGET, "§6Legendary Sphere"));
                items.add(sphereButton("beta", Material.NETHER_STAR, "§dBeta Sphere"));
            }
            case MILLSTONE -> {
                items.add(tagged(named(
                        de.aetherion.items.farm.MillstoneCabinet.createAnchor().clone(),
                        "§eMillstone Anchor"
                ), "npc:millstone"));
                items.add(tagged(named(
                        de.aetherion.items.farm.MillstoneWindmill.createAnchor().clone(),
                        "§6Millstone 2.0"
                ), "npc:millstone_v2"));
                ItemStack caneTool = DevBridges.canePatchTool();
                items.add(tagged(named(
                        caneTool != null ? caneTool : new ItemStack(Material.SUGAR_CANE),
                        "§aCane Patch §8(DEV)"
                ), "farmtool:cane"));
                items.add(button(Material.SUGAR_CANE, "§aSeed Farm Isle", "farmtool:seed-isle",
                        "§7Plants cane banks on sand by water",
                        "§7and fills empty farmland with a",
                        "§7wheat · carrot · potato · beet mix.",
                        "§7Existing rows are never touched.",
                        "",
                        "§eLeft-click §7runs once.",
                        "§eRight-click §7forces a re-run."));
                for (String district : new String[]{"WHEAT_VALE", "ROOT_PATCH", "CANE_SHORE", "MILL_YARD"}) {
                    ItemStack marker = DevBridges.districtMarker(district);
                    items.add(tagged(
                            marker != null ? marker : button(Material.OAK_SIGN, "§e" + district, "noop"),
                            "farmtool:district:" + district
                    ));
                }
                var wheat = de.aetherion.items.economy.CompressedResource.WHEAT;
                var carrot = de.aetherion.items.economy.CompressedResource.CARROT;
                var potato = de.aetherion.items.economy.CompressedResource.POTATO;
                // Ready jar also shelved BEETROOT compressed/compacted — that resource exists only in
                // main-checkout economy WIP, not this lineage's CompressedResource.
                var caneRes = de.aetherion.items.economy.CompressedResource.SUGAR_CANE;
                items.add(itemButton(wheat.compressed(), wheat.compressedId()));
                items.add(itemButton(carrot.compressed(), carrot.compressedId()));
                items.add(itemButton(potato.compressed(), potato.compressedId()));
                items.add(itemButton(caneRes.compressed(), caneRes.compressedId()));
                items.add(itemButton(wheat.compacted(), wheat.compactedId()));
                items.add(itemButton(carrot.compacted(), carrot.compactedId()));
                items.add(itemButton(potato.compacted(), potato.compactedId()));
                items.add(itemButton(caneRes.compacted(), caneRes.compactedId()));
                items.add(itemButton(wheat.refined(), wheat.refinedId()));
                items.add(itemButton(carrot.refined(), carrot.refinedId()));
                items.add(itemButton(potato.refined(), potato.refinedId()));
                items.add(sphereButton("legendary", Material.GOLD_NUGGET, "§6Legendary Catch Sphere"));
                for (int tier = 1; tier <= 3; tier++) {
                    ItemStack treat = DevBridges.petExpTreat(tier);
                    if (treat != null) {
                        items.add(itemButton(treat, "pet_exp_treat_" + tier));
                    } else {
                        items.add(button(Material.APPLE, "§cPet EXP Treat " + tier, "back",
                                "§cAetherMobs missing."));
                    }
                }
                items.add(tagged(named(
                        new ItemStack(Material.WOODEN_HOE),
                        "§aField Warden"
                ), "npc:field_warden"));
                items.add(tagged(named(
                        new ItemStack(Material.CARROT),
                        "§aRoot Cellar §8(Mill Keeper)"
                ), "npc:root_cellar"));
                items.add(tagged(named(
                        new ItemStack(Material.WHEAT_SEEDS),
                        "§aSeed Stall §8(Market)"
                ), "npc:farm_market"));
            }
            case AMBIENT -> {
                try {
                    ItemStack scarecrow = DevBridges.scarecrowTool();
                    items.add(tagged(named(
                            scarecrow != null ? scarecrow : new ItemStack(Material.CARVED_PUMPKIN),
                            "§6Scarecrow §8(DEV)"
                    ), "farmtool:scarecrow"));
                    ItemStack wagon = DevBridges.hayWagonTool();
                    items.add(tagged(named(
                            wagon != null ? wagon : new ItemStack(Material.HAY_BLOCK),
                            "§6Hay Wagon §8(DEV)"
                    ), "farmtool:haywagon"));
                } catch (Throwable ignored) {
                    items.add(tagged(named(new ItemStack(Material.CARVED_PUMPKIN), "§6Scarecrow §8(DEV)"), "farmtool:scarecrow"));
                    items.add(tagged(named(new ItemStack(Material.HAY_BLOCK), "§6Hay Wagon §8(DEV)"), "farmtool:haywagon"));
                }
                var ambient = AetherionItems.getInstance() == null ? null : AetherionItems.getInstance().ambientProps();
                if (ambient != null) {
                    for (de.aetherion.items.dev.prop.AmbientProp prop : ambient.all()) {
                        items.add(tagged(prop.create().clone(), "ambient:" + prop.id()));
                    }
                } else {
                    items.add(button(Material.BARRIER, "§cAmbient offline", "noop", "§7Items prop registry missing."));
                }
            }
            case NPCS -> {
                // Hub is drawn separately.
            }
            case NPCS_STARTER -> DevBridges.npcs(DevBridges.NpcBucket.STARTER)
                    .forEach(entry -> items.add(tagged(named(entry.icon().clone(), entry.name()), "npc:" + entry.id())));
            case NPCS_BOSSES -> DevBridges.npcs(DevBridges.NpcBucket.BOSS)
                    .forEach(entry -> items.add(tagged(named(entry.icon().clone(), entry.name()), "npc:" + entry.id())));
            case NPCS_WORLD -> {
                ItemStack amethystSpawn = DevBridges.spawnAnchor("amethyst");
                if (amethystSpawn != null) {
                    items.add(tagged(named(amethystSpawn.clone(), "§dAmethyst Mines §8Spawn Anchor"),
                            "give:homestead:amethyst"));
                } else {
                    items.add(button(Material.AMETHYST_CLUSTER, "§dAmethyst Mines §8Spawn Anchor",
                            "give:homestead:amethyst",
                            "§7Stand in the Amethyst Area.",
                            "§eRight-click §7to save §f/amethyst§7.",
                            "§8Load AetherionHub if this fails."));
                }
                DevBridges.npcs(DevBridges.NpcBucket.WORLD)
                        .forEach(entry -> items.add(tagged(named(entry.icon().clone(), entry.name()), "npc:" + entry.id())));
            }
            case NPCS_SERVICES -> {
                items.add(button(Material.ENDER_CHEST, "§6Merchant Sample Chest", "give:merchant-chest",
                        "§7Place beside the merchant.",
                        "§7One random booster per player",
                        "§7during Open Up. Then dummy."));
                items.add(button(Material.CHEST, "§bRare Chest", "give:explore-chest:rare",
                        "§7Exploration crate. 12h per player.",
                        "§7Coins, compressed mats, T1 gear."));
                items.add(button(Material.TRAPPED_CHEST, "§5Epic Chest", "give:explore-chest:epic",
                        "§7Exploration crate. 12h per player.",
                        "§7Compacted mats, T2–T3 gear."));
                items.add(button(Material.ENDER_CHEST, "§6Legendary Chest", "give:explore-chest:legendary",
                        "§7Exploration crate. 12h per player.",
                        "§7T4 gear, Upgrade Stone II."));
                items.add(button(Material.PURPLE_SHULKER_BOX, "§dMythic Chest", "give:explore-chest:mythic",
                        "§7Exploration crate. 12h per player.",
                        "§7T5 gear, uniques, high coins."));
                items.add(button(Material.BONE, "§cNPC Remover", "give:npcremover",
                        "§7Right-click a quest NPC,",
                        "§7Trader, Bazaar or Auction House."));
                DevBridges.npcs(DevBridges.NpcBucket.SERVICE)
                        .forEach(entry -> items.add(tagged(named(entry.icon().clone(), entry.name()), "npc:" + entry.id())));
            }
            case RESOURCES -> {
                items.add(itemButton(de.aetherion.items.economy.QuarryItems.shard(), de.aetherion.items.economy.QuarryItems.SHARD_ID));
                items.add(itemButton(de.aetherion.items.economy.QuarryItems.core(), de.aetherion.items.economy.QuarryItems.CORE_ID));
                items.add(itemButton(de.aetherion.items.economy.QuarryItems.compressor(), de.aetherion.items.economy.QuarryItems.COMPRESSOR_ID));
                items.add(itemButton(de.aetherion.items.economy.QuarryItems.compactor(), de.aetherion.items.economy.QuarryItems.COMPACTOR_ID));
                for (de.aetherion.items.economy.CompressedResource resource
                        : de.aetherion.items.economy.CompressedResource.contentOrdered()) {
                    items.add(itemButton(resource.compressed(), resource.compressedId()));
                    items.add(itemButton(resource.compacted(), resource.compactedId()));
                    if (resource.hasQuarry()) {
                        items.add(itemButton(de.aetherion.items.economy.QuarryItems.quarry(resource), resource.quarryItemId()));
                    }
                }
                for (de.aetherion.items.economy.IsleHeartwood heart
                        : de.aetherion.items.economy.IsleHeartwood.contentOrdered()) {
                    items.add(itemButton(heart.create(), heart.itemId()));
                }
            }
            case AREAS -> {
                items.add(button(
                        Material.FILLED_MAP,
                        "§6World Map §8· Spawn",
                        "give:worldmap",
                        "§7Hang a large biome map of",
                        "§7~1000 blocks around spawn.",
                        "§eClick a block §7to place.",
                        "§eSneak + click §7removes it."
                ));
                items.add(button(
                        Material.AMETHYST_SHARD,
                        "§5Crypt Hologram",
                        "give:crypt-holo",
                        "§7Floating Crypt warning text.",
                        "§7Place at the cave entrance.",
                        "§eRight-click a block §7to place.",
                        "§eSneak + click §7removes nearby."
                ));
                for (BuildingBannerKind kind : BuildingBannerKind.values()) {
                    items.add(button(
                            kind.icon(),
                            kind.displayName(),
                            "give:banner:" + kind.id(),
                            "§7Image banner · §f1 high × 7 wide",
                            "§dPurple flame · map art",
                            "§7" + kind.title(),
                            "§eRight-click a wall §7to place.",
                            "§eSneak + click §7removes nearby."
                    ));
                }
                for (AreaType type : AreaType.values()) {
                    items.add(button(
                            type.icon(),
                            "§e" + type.display(),
                            "give:area:" + type.id(),
                            "§7Marks ~" + type.defaultRadius() + " blocks around the click",
                            "§7as §f" + type.display() + "§7.",
                            "§eSneak + click §7removes nearby."
                    ));
                }
            }
            case PET_HABITATS -> {
                items.add(button(Material.BOOK, "§aHow pet habitats work", "back",
                        "§7Paint disks override block auto-detect.",
                        "§7Farm / Village / Shore stay automatic.",
                        "§7Spawns use the whole biotope pool",
                        "§7+ wanderers — no single-species spam.",
                        "§eLeft-click stick §7cycles 30/50/80/120."));
                for (PetHabitatKind kind : PetHabitatKind.values()) {
                    items.add(button(
                            kind.icon(),
                            kind.coloredName() + " §8Stick",
                            "give:pet-habitat:" + kind.id(),
                            "§7Paint §f" + kind.display() + " §7for wild pets.",
                            "§7Default radius §f"
                                    + PetHabitatZoneService.DEFAULT_RADIUS + "m§7.",
                            "§eRight-click block §7to place.",
                            "§eSneak-click §7removes nearby."
                    ));
                }
            }
            case BORDERLANDS_SPIRITS -> {
                items.add(button(Material.CHEST, "§cGive every T1 spirit", "give:spirit-all",
                        "§7All T1 Borderlands ritual potions."));
                for (de.aetherion.items.world.BorderlandsRiteService.SpiritBoss boss
                        : de.aetherion.items.world.BorderlandsRiteService.T1) {
                    items.add(button(
                            Material.POTION,
                            "§cSpirit §8· §f" + boss.display(),
                            "give:spirit:" + boss.id(),
                            "§7Altar summon for §c" + boss.display() + "§7.",
                            "§eRight-click §7light-gray powder altar."
                    ));
                }
                items.add(button(Material.CHEST, "§6Give every T2 Crypt spirit", "give:crypt-spirit-all",
                        "§7Colosseum / Proctor vials."));
                for (de.aetherion.items.world.BorderlandsRiteService.SpiritBoss boss
                        : de.aetherion.items.world.BorderlandsRiteService.T2) {
                    items.add(button(
                            Material.POTION,
                            "§6Crypt Spirit §8· §f" + boss.display(),
                            "give:crypt-spirit:" + boss.id(),
                            "§7Show to the §6Proctor §7at the Colosseum.",
                            "§7Not for the Borderlands altar."
                    ));
                }
            }
            case CHARMS -> {
                for (de.aetherion.items.item.AccessoryItems.Charm charm : de.aetherion.items.item.AccessoryItems.Charm.values()) {
                    for (int tier = 1; tier <= 3; tier++) {
                        items.add(itemButton(customItem.accessories().charm(charm, tier), charm.itemId(tier)));
                    }
                }
                items.add(itemButton(customItem.accessories().shinyCharm(), de.aetherion.items.item.AccessoryItems.SHINY_ID));
                items.add(itemButton(customItem.accessories().forgeCharm(), de.aetherion.items.item.AccessoryItems.FORGE_ID));
                items.add(itemButton(customItem.accessories().estateCharm(), de.aetherion.items.item.AccessoryItems.ESTATE_ID));
            }
            default -> {
            }
        }
        return items;
    }

    private void giveSet(Player player, String id) {
        switch (id) {
            case "combat1" -> giveAll(player, customItem.createCombatHelmet(), customItem.createCombatChestplate(),
                    customItem.createCombatLeggings(), customItem.createCombatBoots(), customItem.createCombatSword());
            case "combat2" -> giveAll(player, customItem.createCombatHelmet2(), customItem.createCombatChestplate2(),
                    customItem.createCombatLeggings2(), customItem.createCombatBoots2(), customItem.createCombatSword2());
            case "combat3" -> giveAll(player, customItem.createCombatHelmet3(), customItem.createCombatChestplate3(),
                    customItem.createCombatLeggings3(), customItem.createCombatBoots3(), customItem.createCombatSword3());
            case "combat4" -> giveAll(player, customItem.createCombatHelmet4(), customItem.createCombatChestplate4(),
                    customItem.createCombatLeggings4(), customItem.createCombatBoots4(), customItem.createCombatSword4());
            case "combat5" -> giveAll(player, customItem.createCombatHelmet5(), customItem.createCombatChestplate5(),
                    customItem.createCombatLeggings5(), customItem.createCombatBoots5(), customItem.createCombatSword5());
            case "mining1" -> giveAll(player, customItem.createMiningHelmet(), customItem.createMiningChestplate(),
                    customItem.createMiningLeggings(), customItem.createMiningBoots(), customItem.createMiningPickaxe());
            case "mining2" -> giveAll(player, customItem.createMiningHelmet2(), customItem.createMiningChestplate2(),
                    customItem.createMiningLeggings2(), customItem.createMiningBoots2(), customItem.createMiningPickaxe2());
            case "mining3" -> giveAll(player, customItem.createMiningHelmet3(), customItem.createMiningChestplate3(),
                    customItem.createMiningLeggings3(), customItem.createMiningBoots3(), customItem.createMiningPickaxe3());
            case "mining4" -> giveAll(player, customItem.createMiningHelmet4(), customItem.createMiningChestplate4(),
                    customItem.createMiningLeggings4(), customItem.createMiningBoots4(), customItem.createMiningPickaxe4());
            case "mining5" -> giveAll(player, customItem.createMiningHelmet5(), customItem.createMiningChestplate5(),
                    customItem.createMiningLeggings5(), customItem.createMiningBoots5(), customItem.createMiningPickaxe5());
            case "rotten" -> giveAll(player, customItem.createRottenHelmet(), customItem.createRottenChestplate(),
                    customItem.createRottenLeggings(), customItem.createRottenBoots());
            case "bone" -> giveAll(player, customItem.createBoneHelmet(), customItem.createBoneChestplate(),
                    customItem.createBoneLeggings(), customItem.createBoneBoots());
            case "webweave" -> giveAll(player, customItem.createWebweaveHelmet(), customItem.createWebweaveChestplate(),
                    customItem.createWebweaveLeggings(), customItem.createWebweaveBoots());
            case "dungeon_vestige" -> giveAll(player,
                    customItem.createDungeonVestigeHelmet(), customItem.createDungeonVestigeChestplate(),
                    customItem.createDungeonVestigeLeggings(), customItem.createDungeonVestigeBoots());
            case "dungeon_relic_t2" -> giveAll(player,
                    customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.HELMET),
                    customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE),
                    customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.LEGGINGS),
                    customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.BOOTS),
                    customItem.createDungeonWeaponRelic(de.aetherion.items.dungeon.DungeonGearTier.T2));
            case "dungeon_relic_t3" -> giveAll(player,
                    customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.HELMET),
                    customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE),
                    customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.LEGGINGS),
                    customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.BOOTS),
                    customItem.createDungeonWeaponRelic(de.aetherion.items.dungeon.DungeonGearTier.T3));
            case "aetherion" -> giveAll(player, customItem.createAetherionHelmet(), customItem.createAetherionChestplate(),
                    customItem.createAetherionLeggings(), customItem.createAetherionBoots());
            case "worldhide" -> giveAll(player, customItem.createWorldhideHelmet(), customItem.createWorldhideChestplate(),
                    customItem.createWorldhideLeggings(), customItem.createWorldhideBoots());
            case "hollow_sun" -> giveAll(player, customItem.createHollowSunHelmet(), customItem.createHollowSunChestplate(),
                    customItem.createHollowSunLeggings(), customItem.createHollowSunBoots());
            case "helios" -> giveAll(player, customItem.createHeliosCrown(), customItem.createHeliosHeartplate(),
                    customItem.createHeliosOrbitGreaves(), customItem.createHeliosDawnTreads(),
                    customItem.createHeliosSolstice());
            case "catcher" -> giveAll(player, customItem.catcher().helmet(1), customItem.catcher().chestplate(1),
                    customItem.catcher().leggings(1), customItem.catcher().boots(1), customItem.catcher().gaff(1));
            case "catcher1" -> giveAll(player, customItem.catcher().helmet(1), customItem.catcher().chestplate(1),
                    customItem.catcher().leggings(1), customItem.catcher().boots(1), customItem.catcher().gaff(1));
            case "catcher2" -> giveAll(player, customItem.catcher().helmet(2), customItem.catcher().chestplate(2),
                    customItem.catcher().leggings(2), customItem.catcher().boots(2), customItem.catcher().gaff(2));
            case "catcher3" -> giveAll(player, customItem.catcher().helmet(3), customItem.catcher().chestplate(3),
                    customItem.catcher().leggings(3), customItem.catcher().boots(3), customItem.catcher().gaff(3));
            case "ironhide" -> giveAll(player, customItem.createIronhideHelmet(), customItem.createIronhideChestplate(),
                    customItem.createIronhideLeggings(), customItem.createIronhideBoots());
            case "healer" -> giveAll(player, customItem.createHealerHelmet(), customItem.createHealerChestplate(),
                    customItem.createHealerLeggings(), customItem.createHealerBoots(), customItem.createMenderStaff());
            case "farming1" -> giveAll(player, customItem.farming().helmet(1), customItem.farming().chestplate(1),
                    customItem.farming().leggings(1), customItem.farming().boots(1), customItem.farming().hoe(1));
            case "farming2" -> giveAll(player, customItem.farming().helmet(2), customItem.farming().chestplate(2),
                    customItem.farming().leggings(2), customItem.farming().boots(2), customItem.farming().hoe(2));
            case "farming3" -> giveAll(player, customItem.farming().helmet(3), customItem.farming().chestplate(3),
                    customItem.farming().leggings(3), customItem.farming().boots(3), customItem.farming().hoe(3));
            case "farming4" -> giveAll(player, customItem.farming().helmet(4), customItem.farming().chestplate(4),
                    customItem.farming().leggings(4), customItem.farming().boots(4), customItem.farming().hoe(4));
            case "farming5" -> giveAll(player, customItem.farming().helmet(5), customItem.farming().chestplate(5),
                    customItem.farming().leggings(5), customItem.farming().boots(5), customItem.farming().hoe(5));
            case "foraging1" -> giveAll(player, customItem.foraging().helmet(1), customItem.foraging().chestplate(1),
                    customItem.foraging().leggings(1), customItem.foraging().boots(1), customItem.foraging().axe(1));
            case "foraging2" -> giveAll(player, customItem.foraging().helmet(2), customItem.foraging().chestplate(2),
                    customItem.foraging().leggings(2), customItem.foraging().boots(2), customItem.foraging().axe(2));
            case "foraging3" -> giveAll(player, customItem.foraging().helmet(3), customItem.foraging().chestplate(3),
                    customItem.foraging().leggings(3), customItem.foraging().boots(3), customItem.foraging().axe(3));
            case "foraging4" -> giveAll(player, customItem.foraging().helmet(4), customItem.foraging().chestplate(4),
                    customItem.foraging().leggings(4), customItem.foraging().boots(4), customItem.foraging().axe(4));
            case "foraging5" -> giveAll(player, customItem.foraging().helmet(5), customItem.foraging().chestplate(5),
                    customItem.foraging().leggings(5), customItem.foraging().boots(5), customItem.foraging().axe(5));
            case "fishing1" -> giveAll(player, customItem.fishing().helmet(1), customItem.fishing().chestplate(1),
                    customItem.fishing().leggings(1), customItem.fishing().boots(1), customItem.fishing().rod(1));
            case "fishing2" -> giveAll(player, customItem.fishing().helmet(2), customItem.fishing().chestplate(2),
                    customItem.fishing().leggings(2), customItem.fishing().boots(2), customItem.fishing().rod(2));
            case "fishing3" -> giveAll(player, customItem.fishing().helmet(3), customItem.fishing().chestplate(3),
                    customItem.fishing().leggings(3), customItem.fishing().boots(3), customItem.fishing().rod(3));
            case "fishing4" -> giveAll(player, customItem.fishing().helmet(4), customItem.fishing().chestplate(4),
                    customItem.fishing().leggings(4), customItem.fishing().boots(4), customItem.fishing().rod(4));
            case "fishing5" -> giveAll(player, customItem.fishing().helmet(5), customItem.fishing().chestplate(5),
                    customItem.fishing().leggings(5), customItem.fishing().boots(5), customItem.fishing().rod(5));
            case "diving" -> giveAll(player, customItem.fishing().divingHelmet(), customItem.fishing().divingChestplate(),
                    customItem.fishing().divingLeggings(), customItem.fishing().divingBoots());
            default -> player.sendMessage("§cUnknown set.");
        }
    }

    private void giveItem(Player player, String id) {
        ItemStack item = resolveItem(id);
        if (item == null) {
            player.sendMessage("§cUnknown item.");
            return;
        }
        give(player, item);
    }

    /** Item id → fresh stack. Backs pinned {@code item:} tiles whose icon is not cached yet. */
    ItemStack resolveItem(String id) {
        if (id == null) {
            return null;
        }
        return switch (id) {
            case "simple_sword" -> customItem.createSimpleSword();
            case "dragon_ascension_vial" -> customItem.createDragonAscensionVial();
            case "splinter_glaive" -> customItem.createSplinterGlaive();
            case "ashen_cleaver" -> customItem.createAshenCleaver();
            case "bone_knife" -> customItem.createBoneKnife();
            case "venom_dagger" -> customItem.createVenomDagger();
            case "simple_longbow" -> customItem.createSimpleLongbow();
            case "iron_longbow" -> customItem.createIronLongbow();
            case "simple_shortbow" -> customItem.createSimpleShortbow();
            case "reinforced_shortbow" -> customItem.createReinforcedShortbow();
            case "wooden_rod" -> customItem.createWoodenRod();
            case "copper_rod" -> customItem.createCopperRod();
            case "frost_shard" -> customItem.createFrostShard();
            case "mender_staff" -> customItem.createMenderStaff();
            case "ember_rod" -> customItem.createEmberRod();
            case "obsidian_maul" -> customItem.createObsidianMaul();
            case "reinforced_pickaxe" -> customItem.createReinforcedPickaxe();
            case "hollow_longbow" -> customItem.createHollowLongbow();
            case "combat_sword" -> customItem.createCombatSword();
            case "combat_sword_2" -> customItem.createCombatSword2();
            case "combat_sword_3" -> customItem.createCombatSword3();
            case "combat_sword_4" -> customItem.createCombatSword4();
            case "combat_sword_5" -> customItem.createCombatSword5();
            case "shortbow" -> customItem.createSkuldugeryShortbow();
            case "aetherblade" -> customItem.createAetherblade();
            case "bridged_axe" -> customItem.createBridgedAxe();
            case "warped_blade" -> customItem.createWarpedBlade();
            case "gravwell_cleaver" -> customItem.createGravwellCleaver();
            case "ashen_katana" -> customItem.createAshenKatana();
            case "worldbite" -> customItem.createWorldbite();
            case "worldhide_helmet" -> customItem.createWorldhideHelmet();
            case "worldhide_chestplate" -> customItem.createWorldhideChestplate();
            case "worldhide_leggings" -> customItem.createWorldhideLeggings();
            case "worldhide_boots" -> customItem.createWorldhideBoots();
            case "seraphine_needle" -> customItem.createSeraphineNeedle();
            case "seraphine_veil" -> customItem.createSeraphineVeil();
            case "seraphine_bodice" -> customItem.createSeraphineBodice();
            case "seraphine_bell_skirt" -> customItem.createSeraphineBellSkirt();
            case "seraphine_pointe_slippers" -> customItem.createSeraphinePointeSlippers();
            case "staff_of_technical_difficulties" -> customItem.createStaffOfTechnicalDifficulties();
            case "void_vacuum_charm" -> customItem.createVoidVacuumCharm();
            case "thermal_core" -> customItem.createThermalCore();
            case "pickaxe_core_of_the_burrower" -> customItem.createPickaxeCoreOfTheBurrower();
            case "insolvent_ledger" -> customItem.createInsolventLedger();
            case "dungeon_core" -> customItem.createDungeonCore();
            case "dungeon_core_2" -> customItem.createDungeonCore2();
            case "dungeon_core_3" -> customItem.createDungeonCore3();
            case "dungeon_vestige_helmet" -> customItem.createDungeonVestigeHelmet();
            case "dungeon_vestige_chestplate" -> customItem.createDungeonVestigeChestplate();
            case "dungeon_vestige_leggings" -> customItem.createDungeonVestigeLeggings();
            case "dungeon_vestige_boots" -> customItem.createDungeonVestigeBoots();
            case "dungeon_tank_chestplate" -> customItem.createDungeonArmor(
                    de.aetherion.items.dungeon.DungeonCalling.TANK,
                    de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE);
            case "dungeon_assassin_chestplate" -> customItem.createDungeonArmor(
                    de.aetherion.items.dungeon.DungeonCalling.ASSASSIN,
                    de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE);
            case "dungeon_soldier_chestplate" -> customItem.createDungeonArmor(
                    de.aetherion.items.dungeon.DungeonCalling.SOLDIER,
                    de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE);
            case "dungeon_healer_chestplate" -> customItem.createDungeonArmor(
                    de.aetherion.items.dungeon.DungeonCalling.HEALER,
                    de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE);
            case "dungeon_shaman_chestplate" -> customItem.createDungeonArmor(
                    de.aetherion.items.dungeon.DungeonCalling.SHAMAN,
                    de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE);
            case "squids_boot" -> customItem.createSquidsBoot();
            case "void_stick" -> customItem.createAetherionVoidStick();
            case "god_sword", "god2_sword", "god_axe", "god2_axe" -> null;
            case "booster_coal" -> customItem.createCoalBooster();
            case "booster_iron" -> customItem.createIronBooster();
            case "booster_gold" -> customItem.createGoldBooster();
            case "booster_diamond" -> customItem.createDiamondBooster();
            case "booster_emerald" -> customItem.createEmeraldBooster();
            case "booster_redstone" -> customItem.createRedstoneBooster();
            case "booster_lapis" -> customItem.createLapisBooster();
            case "booster_glowstone" -> customItem.createGlowstoneBooster();
            case "booster_wheat" -> customItem.createWheatBooster();
            case "booster_carrot" -> customItem.createCarrotBooster();
            case "booster_oak" -> customItem.createOakBooster();
            case "booster_birch" -> customItem.createBirchBooster();
            case "beginner_pickaxe" -> customItem.create();
            case "simple_pickaxe" -> customItem.createSimplePickaxe();
            case "simple_axe" -> customItem.createSimpleAxe();
            case "simple_hoe" -> customItem.createSimpleHoe();
            case "vein_siphon" -> customItem.createVeinSiphon();
            case "blueprint_vein_siphon" -> customItem.createBlueprintVeinSiphon();
            case "canopy_cleaver" -> customItem.createCanopyCleaver();
            case "blueprint_canopy_cleaver" -> customItem.createBlueprintCanopyCleaver();
            case "bounty_hoe" -> customItem.createBountyHoe();
            case "blueprint_bounty_hoe" -> customItem.createBlueprintBountyHoe();
            case "wild_sight" -> customItem.createWildSight();
            case "blueprint_wild_sight" -> customItem.createBlueprintWildSight();
            case "tide_latch" -> customItem.createTideLatch();
            case "blueprint_tide_latch" -> customItem.createBlueprintTideLatch();
            case "resonance_scythe" -> customItem.createResonanceScythe(false);
            case "blueprint_resonance_scythe" -> customItem.createBlueprintResonanceScythe();
            case "blueprint_upgrade_stone_2" -> customItem.createBlueprintUpgradeStone2();
            case "blueprint_upgrade_stone_3" -> customItem.createBlueprintUpgradeStone3();
            case "blueprint_upgrade_stone_4" -> customItem.createBlueprintUpgradeStone4();
            case "weapon_schematic" -> customItem.createWeaponSchematic();
            case "dungeon_weapon_schematic" -> customItem.createDungeonWeaponSchematic();
            case "rotten_cleaver" -> customItem.createRottenCleaver();
            case "webweave_fang" -> customItem.createWebweaveFang();
            case "catcher_gaff" -> customItem.catcher().gaff(1);
            case "simple_helmet" -> customItem.createSimpleHelmet();
            case "simple_chest" -> customItem.createSimpleChestplate();
            case "simple_legs" -> customItem.createSimpleLeggings();
            case "simple_boots" -> customItem.createSimpleBoots();
            case "recipe_book" -> customItem.createRecipeBook();
            case "aetherion_blood_vial" -> de.aetherion.items.shop.AetherBloodVial.create();
            case "storage" -> storage.createStorage();
            case "resource_sack" -> de.aetherion.items.storage.SackItems.create(de.aetherion.items.storage.SackType.RESOURCE);
            case "booster_sack" -> de.aetherion.items.storage.SackItems.create(de.aetherion.items.storage.SackType.BOOSTER);
            default -> {
                ItemStack progression = customItem.progression().byId(id);
                if (progression != null) {
                    yield progression;
                }
                ItemStack farming = customItem.farming().byId(id);
                if (farming != null) {
                    yield farming;
                }
                ItemStack foraging = customItem.foraging().byId(id);
                if (foraging != null) {
                    yield foraging;
                }
                ItemStack fishing = customItem.fishing().byId(id);
                if (fishing != null) {
                    yield fishing;
                }
                ItemStack catcher = customItem.catcher().byId(id);
                if (catcher != null) {
                    yield catcher;
                }
                ItemStack charm = customItem.accessories().byId(id);
                if (charm != null) {
                    yield charm;
                }
                ItemStack dungeon = customItem.createDungeonFromId(id);
                if (dungeon != null) {
                    yield dungeon;
                }
                ItemStack found = de.aetherion.items.economy.CompressedResource.fromId(id);
                if (found != null) {
                    yield found;
                }
                ItemStack heart = de.aetherion.items.economy.IsleHeartwood.fromItemId(id);
                yield heart != null ? heart : de.aetherion.items.economy.QuarryItems.fromId(id);
            }
        };
    }

    private void giveBoss(Player player, String id) {
        for (boolean anchors : new boolean[]{true, false}) {
            for (DevBridges.NamedItem entry : DevBridges.bossItems(anchors)) {
                if (entry.id().equalsIgnoreCase(id)) {
                    give(player, entry.icon().clone());
                    return;
                }
            }
        }
        player.sendMessage("§cUnknown boss item.");
    }

    private void giveNpc(Player player, String id) {
        for (DevBridges.NamedItem entry : DevBridges.npcs()) {
            if (entry.id().equalsIgnoreCase(id)) {
                give(player, entry.icon().clone());
                return;
            }
        }
        player.sendMessage("§cUnknown NPC.");
    }

    private void giveAll(Player player, ItemStack... items) {
        for (ItemStack item : items) {
            give(player, item);
        }
    }

    private void give(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        giveQuiet(player, item);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
    }

    /** @return stacks that did not fit and were dropped at the player's feet. */
    private int giveQuiet(Player player, ItemStack item) {
        if (item == null) {
            return 0;
        }
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return overflow.size();
    }

    /** Shelf stack → the real item (DEV action tag and DEV decoration stripped). */
    static ItemStack clean(ItemStack shelf) {
        ItemStack give = shelf.clone();
        ItemMeta meta = give.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(ItemKeys.devAction());
            meta.getPersistentDataContainer().remove(DevItems.VIA);
            give.setItemMeta(meta);
        }
        return give;
    }

    private ItemStack setButton(Material material, String name, String setId) {
        return button(material, name, "set:" + setId, "§7Click to receive the full set.");
    }

    private ItemStack itemButton(ItemStack source, String id) {
        ItemStack clone = source.clone();
        return tagged(clone, "item:" + id);
    }

    private ItemStack sphereButton(String id, Material fallback, String name) {
        ItemStack sphere = DevBridges.catchSphere(id);
        if (sphere == null) {
            return button(fallback, name, "sphere:" + id, "§cAetherMobs missing.");
        }
        return tagged(named(sphere.clone(), name), "sphere:" + id);
    }

    private ItemStack button(Material material, String name, String action, String... lore) {
        return button(material, null, name, action, lore);
    }

    private ItemStack button(Material material, Integer customModelData, String name, String action, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            de.aetherion.items.util.GuiItems.hideVanilla(meta);
            meta.getPersistentDataContainer().set(ItemKeys.devAction(), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        DevIndex.remember(action, item);
        return item;
    }

    private ItemStack tagged(ItemStack item, String action) {
        return DevItems.tag(item, action);
    }

    private ItemStack named(ItemStack item, String name) {
        return DevItems.named(item, name);
    }

    private void drawRanks(Inventory inventory, UUID target) {
        RankBadgeService ranks = ranks();
        inventory.setItem(45, button(Material.ARROW, "§eBack", target == null ? "back" : "page:RANKS"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
        if (target == null) {
            drawPlayerPicker(inventory, "rank-player:", "§7Click to set their rank.");
            return;
        }
        OfflinePlayer selected = Bukkit.getOfflinePlayer(target);
        String current = ranks == null ? "default" : ranks.rankOf(target);
        RankBadgeService.Rank extra = ranks == null ? null : ranks.extraFor(target);
        inventory.setItem(4, playerHead(selected,
                "§e" + nameOf(selected),
                "§7Current: " + (ranks == null ? "§7?" : ranks.rankByGroup(current).display()),
                extra == null ? "§8Click a rank below. Adventurer sticks." : "§7Ultra: " + extra.display()));
        String[] progression = {
                "adventurer", "veteran", "champion", "legend", "mythwright", "aetherborn", "celestine",
                "sovereign", "ascendant", "empyrean", "eternal", "aetherion"
        };
        // Row 2–3: progression ranks (19–25, then 28–32)
        int slot = 19;
        for (String group : progression) {
            RankBadgeService.Rank rank = ranks == null ? null : ranks.rankByGroup(group);
            if (rank == null) {
                continue;
            }
            if (slot == 26) {
                slot = 28;
            }
            if (slot >= 33) {
                break;
            }
            boolean active = rank.group().equalsIgnoreCase(current);
            boolean first = group.equals("adventurer");
            inventory.setItem(slot++, button(
                    rankIcon(rank.group()),
                    (active ? "§a▶ " : "§f") + rank.display() + (first ? " §8(first)" : ""),
                    "rank-set:" + rank.group(),
                    first ? "§7First cosmetic rank." : (active ? "§aCurrently applied." : "§7Click to apply."),
                    "§7Stays until Match account level.",
                    "§8LuckPerms group: §7" + rank.group()
            ));
        }
        boolean adminOn = extra != null && extra.group().equals("admin");
        boolean monkeyOn = extra != null && extra.group().equals("monkey");
        boolean citrusOn = extra != null && extra.group().equals("citrus");
        boolean betaOn = extra != null && extra.group().equals("beta");
        boolean mvpOn = extra != null && extra.group().equals("mvpplusplus");
        inventory.setItem(37, button(
                rankIcon("admin"),
                (adminOn ? "§a▶ " : "") + "§cAdmin",
                "rank-set:admin",
                "§7Robb only. OP does not grant this.",
                "§7Stays on top of the level tag.",
                adminOn ? "§eClick again to remove." : "§7Click to grant."
        ));
        inventory.setItem(38, homieRankButton(ranks, "rank-set:monkey", monkeyOn, "§b§lMonkey §8· celestial",
                "§7Celestial dye #B2FFFF, then the level tag.",
                "§7Content Kit: flight, /npc, Resources,",
                "§7Ambient Props, self shards — not full admin."));
        inventory.setItem(39, homieRankButton(ranks, "rank-set:citrus", citrusOn, "§e§lCitrus",
                "§7Citrus dye, then the level tag."));
        inventory.setItem(40, homieRankButton(ranks, "rank-set:beta", betaOn, "§d§lBeta",
                "§7Rainbow dye, then the level tag.",
                "§7Not Monkey #B2FFFF. Cosmetic only."));
        inventory.setItem(41, button(
                rankIcon("mvpplusplus"),
                (mvpOn ? "§a▶ " : "") + "§6MVP§c++",
                "rank-set:mvpplusplus",
                "§7Gold/red homage, then the level tag.",
                mvpOn ? "§eClick again to remove." : "§7Click to grant."
        ));
        inventory.setItem(42, button(
                Material.EXPERIENCE_BOTTLE,
                "§eMatch account level",
                "rank-sync",
                "§7Clear the DEV progression override.",
                "§7Does not remove Citrus / Monkey / Beta /",
                "§7MVP++ / Admin. Click those to remove."
        ));
    }

    /** Ready-jar Homie ranks: live toggle when RankBadgeService knows the group, labelled stub otherwise. */
    private ItemStack homieRankButton(RankBadgeService ranks, String action, boolean on, String name, String... lore) {
        String group = action.substring("rank-set:".length());
        List<String> lines = new ArrayList<>(List.of(lore));
        if (ranks == null || !ranks.isExtra(group)) {
            lines.add("");
            lines.add("§8Needs the special-rank backend —");
            lines.add("§8not in this build. Click does nothing.");
            return button(Material.GRAY_DYE, "§8" + org.bukkit.ChatColor.stripColor(name), "noop",
                    lines.toArray(String[]::new));
        }
        lines.add(on ? "§eClick again to remove." : "§7Click to grant.");
        return button(rankIcon(group), (on ? "§a▶ " : "") + name, action, lines.toArray(String[]::new));
    }

    private void drawTestGear(Inventory inventory) {
        int flagshipSlot = 13;
        for (ItemStack flagship : TestGear.flagships()) {
            String id = flagship.hasItemMeta()
                    ? flagship.getItemMeta().getPersistentDataContainer().get(
                            de.aetherion.core.AetherKeys.namespaced("aetherion", "test_gear"),
                            org.bukkit.persistence.PersistentDataType.STRING)
                    : "terminus";
            inventory.setItem(flagshipSlot++, tagged(flagship.clone(), "testgear:" + id));
        }

        inventory.setItem(4, button(Material.NETHERITE_CHESTPLATE, "§d✦ Test Gear", "page:TEST_ARENA",
                "§7Sandbox prototypes only.",
                "§7Click to receive a piece."));
        int slot = 19;
        int index = 0;
        for (ItemStack gear : TestGear.all()) {
            String id = gear.hasItemMeta()
                    ? gear.getItemMeta().getPersistentDataContainer().get(
                    de.aetherion.core.AetherKeys.namespaced("aetherion", "test_gear"),
                    org.bukkit.persistence.PersistentDataType.STRING)
                    : ("gear_" + index);
            inventory.setItem(slot++, tagged(gear.clone(), "testgear:" + (id == null ? index : id)));
            index++;
            if (slot == 26) {
                slot = 28;
            }
            if (slot == 35) {
                slot = 37;
            }
            if (slot >= 44) {
                break;
            }
        }
        inventory.setItem(45, button(Material.ARROW, "§eBack", "page:TEST_ARENA"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private void drawTestArena(Inventory inventory, int index) {
        inventory.setItem(4, DevItems.glow(button(Material.STRUCTURE_BLOCK, "§d§l✦ Test Arena", "noop",
                "§7Void world §f" + de.aetherion.items.world.TestArenaService.WORLD_NAME,
                "§7Big flat pad. Spawn bosses here.",
                "§8Sandbox only — not the live game.",
                "",
                "§7Row 1 §8· §farena controls",
                "§7Rows 2–4 §8· §fclick a boss to spawn it here")));
        // Row 1 — arena controls (inner 10–16, never the ring).
        inventory.setItem(10, button(Material.ENDER_PEARL, "§a⚡ Go to arena", "test:goto",
                "§7Creates the world if needed,",
                "§7then teleports you to the pad."));
        inventory.setItem(11, button(Material.OAK_DOOR, "§eLeave arena", "test:leave",
                "§7Returns you to where you entered."));
        inventory.setItem(12, button(Material.BRICKS, "§bRebuild pad", "test:rebuild",
                "§7Re-lays the smooth-stone platform."));
        inventory.setItem(13, button(Material.ZOMBIE_HEAD, "§aSpawn test mobs", "test:spawn-dummies",
                "§7Spawns simple zombies / skeletons",
                "§7around you for ability testing.",
                "§8/summon also works in this world."));
        inventory.setItem(14, button(Material.WITHER_SKELETON_SKULL, "§cClear bosses", "test:clear-bosses",
                "§7Despawns BossEngine fights",
                "§7inside the test world."));
        inventory.setItem(15, button(Material.BARRIER, "§cClear all mobs", "test:clear-mobs",
                "§7Removes every non-player entity."));
        inventory.setItem(16, button(Material.NETHERITE_CHESTPLATE, "§d✦ Flagships & Test Gear", "page:TEST_GEAR",
                "§7Sandbox weapons / armor / charms.",
                "§7Built for the prototype bosses."));

        List<ItemStack> bosses = new ArrayList<>();
        // Sandbox prototypes first
        for (DevBridges.NamedItem entry : DevBridges.bossItems(false)) {
            String spawnId = entry.bossId() == null || entry.bossId().isBlank() ? entry.id() : entry.bossId();
            if (!spawnId.toLowerCase(java.util.Locale.ROOT).startsWith("test_")) {
                continue;
            }
            bosses.add(tagged(entry.icon().clone(), "test:spawn:" + spawnId));
        }
        for (DevBridges.NamedItem entry : DevBridges.bossItems(false)) {
            String spawnId = entry.bossId() == null || entry.bossId().isBlank() ? entry.id() : entry.bossId();
            if (spawnId.toLowerCase(java.util.Locale.ROOT).startsWith("test_")) {
                continue;
            }
            bosses.add(tagged(entry.icon().clone(), "test:spawn:" + spawnId));
        }
        // Rows 2–4 — spawnable bosses, sandbox prototypes first (inner 21 per page).
        final int[] bossSlots = java.util.Arrays.copyOfRange(DevTheme.INNER, 7, DevTheme.INNER.length);
        int perPage = bossSlots.length;
        int pages = Math.max(1, (bosses.size() + perPage - 1) / perPage);
        index = Math.min(Math.max(0, index), pages - 1);
        int start = index * perPage;
        int end = Math.min(bosses.size(), start + perPage);
        for (int i = start; i < end; i++) {
            inventory.setItem(bossSlots[i - start], bosses.get(i));
        }
        if (bosses.isEmpty()) {
            inventory.setItem(31, button(Material.GRAY_DYE, "§7BossEngine offline", "noop",
                    "§8No spawnable bosses registered.",
                    "§8Controls above still work."));
        }
        drawPager(inventory, Page.TEST_ARENA, index, pages, bosses.size() - end);
    }

    private void drawPortals(Inventory inventory) {
        inventory.setItem(4, button(Material.END_PORTAL_FRAME, "§dPortals", "root",
                DevBridges.farmPortalStatus(),
                "§7Shared farm island in §faether_farm_island§7.",
                "§7Gate: Farming skill level only."));
        inventory.setItem(19, button(Material.GRASS_BLOCK, "§aEnsure Farm Island", "farmportal:ensure",
                "§7Create void world + paste schematic",
                "§7if not already done.",
                "",
                "§eClick to ensure"));
        inventory.setItem(20, button(Material.OBSIDIAN, "§6Hub Portal Tool", "farmportal:tool",
                "§7Right-click a block at the farm",
                "§7to build a nether portal linked",
                "§7to the island.",
                "§eSneak-click §7clears hub portal."));
        inventory.setItem(21, button(Material.ENDER_PEARL, "§bTeleport to Island", "farmportal:tp",
                "§7Jump to the island exit",
                "§7(no level check)."));
        inventory.setItem(22, button(Material.COMPASS, "§eSet Island Exit Here", "farmportal:set-exit",
                "§7Stand where return should land",
                "§7(or just outside the island portal).",
                "§8Auto-detect usually works."));
        inventory.setItem(23, button(Material.TNT, "§cRebuild Island", "farmportal:rebuild",
                "§7Re-pastes the schematic.",
                "§eSneak-click §7to confirm."));
        inventory.setItem(24, button(Material.WHEAT, "§eRefresh Crops + Lights + Pets", "farmportal:ambience",
                "§7Full-grown crops, soft lights,",
                "§7reseed ambient animals + farm pets.",
                "§8Safe on an already-pasted island."));
        inventory.setItem(45, button(Material.ARROW, "§eBack", "page:FARM_ISLE"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private void drawIsleWeather(Inventory inventory, Player player) {
        inventory.setItem(4, button(Material.WHITE_BANNER, "§bIsle Weather", "root",
                DevBridges.isleWeatherStatus(player),
                "§7Temporary force · you only · ~75s",
                "§7Then ambient rolls again."));
        inventory.setItem(10, button(Material.SUNFLOWER, "§eClear", "isle-weather:CLEAR",
                "§7Sunny / no fog.",
                "§eClick §7to force ~75s"));
        inventory.setItem(11, button(Material.GRAY_DYE, "§7Fog", "isle-weather:FOG",
                "§7TAB/logic only (no Darkness — FPS).",
                "§eClick §7to force ~75s"));
        inventory.setItem(12, button(Material.WATER_BUCKET, "§bRain", "isle-weather:RAIN",
                "§7Vanilla rain.",
                "§eClick §7to force ~75s"));
        inventory.setItem(13, button(Material.POTION, "§3Drizzle", "isle-weather:DRIZZLE",
                "§7Light rain.",
                "§eClick §7to force ~75s"));
        inventory.setItem(14, button(Material.SNOWBALL, "§fSnow", "isle-weather:SNOW",
                "§7Vanilla downfall.",
                "§eClick §7to force ~75s"));
        inventory.setItem(15, button(Material.FEATHER, "§fWindy", "isle-weather:WINDY",
                "§7Clear sky · windy label.",
                "§eClick §7to force ~75s"));
        inventory.setItem(22, button(Material.BARRIER, "§cClear override", "isle-weather:clear-override",
                "§7Drop the force now.",
                "§7Ambient cycle resumes."));
        inventory.setItem(45, button(Material.ARROW, "§eBack", "root"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private static final int TESTBOT_PER_PAGE = 4;

    private static String[] testbotRoles() {
        de.aetherion.core.api.TestBotsAccess access = DevBridges.testBots();
        if (access != null) {
            java.util.List<String> ids = access.startableRoles();
            if (ids != null && !ids.isEmpty()) {
                return ids.toArray(String[]::new);
            }
        }
        return new String[] {"mine", "forage", "catch", "roam", "combat", "fish", "trade", "quest", "pad", "general"};
    }

    private void handleTestbots(Player player, String action, ClickType click) {
        String spec = action.substring("testbot:".length());
        int pageIndex = holderIndex(player);
        if (spec.equals("refresh")) {
            open(player, Page.TESTBOTS, null, pageIndex);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            return;
        }
        if (spec.equals("stopall")) {
            runTestbotIo(player, () -> DevBridges.testBotsStopAll(), Page.TESTBOTS);
            return;
        }
        if (spec.equals("report")) {
            player.closeInventory();
            player.performCommand("botreport");
            return;
        }
        if (spec.startsWith("start:")) {
            String role = spec.substring("start:".length());
            int count = Math.max(1, DevBridges.testBots() == null ? 1 : DevBridges.testBots().desired(role));
            runTestbotIo(player, () -> DevBridges.testBotsStart(role, count), Page.TESTBOTS);
            return;
        }
        if (spec.startsWith("stop:")) {
            String role = spec.substring("stop:".length());
            runTestbotIo(player, () -> DevBridges.testBotsStop(role), Page.TESTBOTS);
            return;
        }
        if (spec.startsWith("count:")) {
            String role = spec.substring("count:".length());
            int delta = click.isShiftClick() ? 5 : 1;
            if (click.isRightClick()) {
                delta = -delta;
            }
            int next = DevBridges.testBotsAdjust(role, delta);
            player.sendMessage("§7" + role + " desired count: §f" + next);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            open(player, Page.TESTBOTS, null, pageIndex);
            return;
        }
        if (spec.startsWith("list:")) {
            String role = spec.substring("list:".length());
            String[] roles = testbotRoles();
            int index = 0;
            for (int i = 0; i < roles.length; i++) {
                if (roles[i].equalsIgnoreCase(role)) {
                    index = i;
                    break;
                }
            }
            open(player, Page.TESTBOTS_LIST, null, index);
            return;
        }
        if (spec.startsWith("view:")) {
            String name = spec.substring("view:".length());
            TestBotView bot = DevBridges.testBot(name);
            if (bot == null) {
                player.sendMessage("§cBot §f" + name + " §cis not online.");
                return;
            }
            // profile / state / hp / food / target / inv come from newer Core builds only.
            String profile = DevRankBridge.botText(bot, "profile");
            String state = DevRankBridge.botText(bot, "state");
            double health = DevRankBridge.botNumber(bot, "health");
            double food = DevRankBridge.botNumber(bot, "food");
            String target = DevRankBridge.botText(bot, "target");
            String inventorySummary = DevRankBridge.botText(bot, "inventorySummary");
            player.sendMessage("§6" + bot.displayName() + " §8· §7" + bot.name() + " §8· §e" + bot.role()
                    + (profile.isBlank() ? "" : " §8· §b" + profile));
            player.sendMessage("§7" + bot.world() + String.format(" %.1f %.1f %.1f", bot.x(), bot.y(), bot.z()));
            player.sendMessage("§7activity §f" + bot.activity()
                    + (state.isBlank() ? "" : " §8· §7state §f" + state)
                    + (health < 0 ? "" : " §8· §7hp §f" + String.format("%.0f", health))
                    + (food < 0 ? "" : " §8· §7food §f" + String.format("%.0f", food)));
            player.sendMessage("§7held §f" + bot.heldItem() + " §8· §7deaths §f" + bot.deaths()
                    + (target.isBlank() ? "" : " §8· §7target §f" + target));
            if (!inventorySummary.isBlank()) {
                player.sendMessage("§7inv §f" + inventorySummary);
            }
            if (!bot.lastAction().isBlank()) {
                player.sendMessage("§7last §f" + bot.lastAction());
            }
            if (!bot.lastError().isBlank()) {
                player.sendMessage("§cerror §f" + bot.lastError());
            }
            if (!bot.recentActions().isEmpty()) {
                player.sendMessage("§8" + String.join(" §7|§8 ", bot.recentActions()));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        }
    }

    private void runTestbotIo(Player player, java.util.function.Supplier<String> work, Page returnTo) {
        int pageIndex = holderIndex(player);
        player.sendMessage("§7Contacting testbot runner…");
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            player.sendMessage(work.get());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String message = work.get();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(message);
                    open(player, returnTo, null, pageIndex);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
                }
            });
        });
    }

    private void drawTestbots(Inventory inventory, int pageIndex) {
        TestBotReport report = DevBridges.testBotsReport();
        if (report == null) {
            inventory.setItem(4, button(Material.BARRIER, "§cStressBots offline", "back",
                    "§7Install/enable AetherionStressBots",
                    "§7on this backend (mmo-r)."));
            inventory.setItem(45, button(Material.ARROW, "§eBack", "back"));
            inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
            return;
        }
        String[] roles = testbotRoles();
        int pages = Math.max(1, (roles.length + TESTBOT_PER_PAGE - 1) / TESTBOT_PER_PAGE);
        int page = Math.max(0, Math.min(pages - 1, pageIndex));
        inventory.setItem(4, button(Material.PLAYER_HEAD, "§bTestbots §8· §7p" + (page + 1) + "/" + pages,
                "testbot:refresh",
                "§7enabled: " + (report.enabled() ? "§ayes" : "§cno"),
                "§7runner: " + (report.runnerReachable() ? "§aup" : "§cdown"),
                "§8" + report.runnerDetail(),
                "§7TPS §f" + (report.tps() < 0 ? "n/a" : String.format("%.1f", report.tps()))
                        + " §8· §7online §f" + report.onlineTotal() + "§7/§f" + report.maxTotal(),
                "",
                "§eClick to refresh",
                "§8Wave 1–3. general = mixed player who switches."));

        int[] statusSlots = {10, 19, 28, 37};
        int[] countSlots = {11, 20, 29, 38};
        int[] startSlots = {12, 21, 30, 39};
        int[] stopSlots = {13, 22, 31, 40};
        java.util.Map<String, TestBotRoleView> byId = new HashMap<>();
        for (TestBotRoleView role : report.roles()) {
            byId.put(role.id(), role);
        }
        int from = page * TESTBOT_PER_PAGE;
        for (int i = 0; i < TESTBOT_PER_PAGE; i++) {
            int roleIndex = from + i;
            if (roleIndex >= roles.length) {
                break;
            }
            String id = roles[roleIndex];
            TestBotRoleView role = byId.get(id);
            int online = role == null ? 0 : role.online();
            int desired = role == null ? 0 : role.desired();
            int cap = role == null ? 20 : role.cap();
            String hint = role == null ? "offline" : role.activityHint();
            inventory.setItem(statusSlots[i], button(roleIcon(id), roleLabel(id) + " §8· §f" + online,
                    "testbot:list:" + id,
                    "§7online §f" + online + " §8/ §7desired §f" + desired + " §8/ §7cap §f" + cap,
                    "§7activity §f" + hint,
                    role != null && !role.lastError().isBlank() ? "§c" + role.lastError() : "§8no error",
                    "",
                    "§eClick §7for bot list (loc · hp · activity)"));
            inventory.setItem(countSlots[i], button(Material.PAPER, "§fCount §e" + desired,
                    "testbot:count:" + id,
                    "§7Left §a+1 §8· §7Right §c-1",
                    "§7Shift §a+5§7 / §c-5",
                    "§8Then Start to apply."));
            inventory.setItem(startSlots[i], button(Material.LIME_DYE, "§aStart " + id,
                    "testbot:start:" + id,
                    "§7Spawn §f" + Math.max(1, desired) + " §7" + id + " bots.",
                    "§8Needs runner --listen and testbots.enabled"));
            inventory.setItem(stopSlots[i], button(Material.RED_DYE, "§cStop " + id,
                    "testbot:stop:" + id,
                    "§7Quit this role and kick them."));
        }
        inventory.setItem(16, button(Material.BARRIER, "§cStop all", "testbot:stopall",
                "§7Every QA/stress bot this runner owns."));
        inventory.setItem(25, button(Material.WRITTEN_BOOK, "§e/botreport", "testbot:report",
                "§7Chat dump + book copy."));
        inventory.setItem(34, button(Material.CLOCK, "§eRefresh", "testbot:refresh"));
        if (page > 0) {
            inventory.setItem(DevTheme.PREV, button(Material.ARROW, "§e◀ Previous roles", "pageidx:TESTBOTS:" + (page - 1),
                    "§7Page §f" + page));
        }
        if (page + 1 < pages) {
            inventory.setItem(DevTheme.NEXT, button(Material.ARROW, "§eMore roles ▶", "pageidx:TESTBOTS:" + (page + 1),
                    "§7combat · fish · trade · quest · pad · general"));
        }
    }

    private void drawTestbotList(Inventory inventory, int index) {
        String[] roles = testbotRoles();
        String role = roles[Math.max(0, Math.min(roles.length - 1, index))];
        TestBotReport report = DevBridges.testBotsReport();
        inventory.setItem(4, button(roleIcon(role), "§b" + role + " bots", "page:TESTBOTS",
                "§7Nickname · loc · hp · activity · profile"));
        int shown = 0;
        if (report != null) {
            for (TestBotView bot : report.bots()) {
                if (!role.equalsIgnoreCase(bot.role())) {
                    continue;
                }
                if (shown >= DevTheme.INNER.length) {
                    break;
                }
                int slot = DevTheme.INNER[shown++];
                double health = DevRankBridge.botNumber(bot, "health");
                double food = DevRankBridge.botNumber(bot, "food");
                String profile = DevRankBridge.botText(bot, "profile");
                String target = DevRankBridge.botText(bot, "target");
                String inventorySummary = DevRankBridge.botText(bot, "inventorySummary");
                inventory.setItem(slot, button(Material.PLAYER_HEAD, "§e" + bot.displayName(),
                        "testbot:view:" + bot.name(),
                        "§8login §7" + bot.name(),
                        "§7" + bot.world() + String.format(" %.1f %.1f %.1f", bot.x(), bot.y(), bot.z()),
                        health < 0 ? "§8hp n/a on this Core" : "§7hp §f" + String.format("%.0f", health)
                                + (food < 0 ? "" : " §8· §7food §f" + String.format("%.0f", food)),
                        "§7activity §f" + bot.activity() + (profile.isBlank() ? "" : " §8· §b" + profile),
                        target.isBlank() ? "§7held §f" + bot.heldItem() : "§7target §f" + target,
                        inventorySummary.isBlank() ? "§7held §f" + bot.heldItem() : "§7inv §f" + inventorySummary,
                        "§7deaths §f" + bot.deaths(),
                        bot.lastAction().isBlank() ? "§8no recent action" : "§7last §f" + bot.lastAction(),
                        "",
                        "§eClick §7for a chat dump"));
            }
        }
        if (shown == 0) {
            inventory.setItem(22, button(Material.GRAY_DYE, "§7None online", "page:TESTBOTS",
                    "§7Start this role from the overview."));
        }
    }

    private static Material roleIcon(String role) {
        return switch (role) {
            case "mine" -> Material.IRON_PICKAXE;
            case "forage" -> Material.IRON_AXE;
            case "catch" -> Material.SNOWBALL;
            case "combat" -> Material.IRON_SWORD;
            case "fish" -> Material.FISHING_ROD;
            case "trade" -> Material.GOLD_INGOT;
            case "quest" -> Material.WRITABLE_BOOK;
            case "pad" -> Material.SLIME_BLOCK;
            case "general" -> Material.COMPASS;
            default -> Material.LEATHER_BOOTS;
        };
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "mine" -> "§bMine";
            case "forage" -> "§2Forage";
            case "catch" -> "§dCatch";
            case "roam" -> "§eRoam";
            case "combat" -> "§cCombat";
            case "fish" -> "§3Fish";
            case "trade" -> "§6Trade";
            case "quest" -> "§dQuest";
            case "pad" -> "§aPad";
            case "general" -> "§fGeneral";
            default -> "§f" + role;
        };
    }

    private int holderIndex(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
            return holder.index();
        }
        return 0;
    }

    private void drawDungeons(Inventory inventory) {
        inventory.setItem(4, button(Material.END_PORTAL_FRAME, "§5Dungeons", "root",
                "§7Opens a real instance.",
                "§7Party leader pulls the party.",
                "§8Max 4. Extra players scale mobs."));
        inventory.setItem(20, button(Material.DEEPSLATE_BRICKS, "§dFloor 1 · Full run", "dungeon:floor1",
                "§7Prison template. Safe lobby.",
                "§7Branching halls. Glass gates.",
                "§7Mobs / Sentinel = Floor 1 stats.",
                "§7Same as the Dungeon Keeper.",
                "",
                "§eClick to start"));
        inventory.setItem(21, button(Material.WITHER_SKELETON_SKULL, "§5Floor 1 · Boss only", "dungeon:floor1-boss",
                "§7Skip the trash. Land in the boss room.",
                "§7Sentinel spawns immediately.",
                "",
                "§eClick to start"));
        inventory.setItem(23, button(Material.PACKED_ICE, "§bFloor 2 · Full run", "dungeon:floor2",
                "§7Schematic Floor 2 (Endless XL).",
                "§7Clearance bar → Frostbound.",
                "",
                "§eClick to start"));
        inventory.setItem(24, button(Material.CARVED_PUMPKIN, "§3Floor 2 · Boss only", "dungeon:floor2-boss",
                "§7Same XL map as Floor 2.",
                "§8Full run preferred for now.",
                "",
                "§eClick to start"));
        inventory.setItem(30, button(Material.END_STONE_BRICKS, "§5Floor 3 · Full run", "dungeon:floor3",
                "§7End theme. Big dungeon. Fat arena.",
                "§5Aetherion. The set is not easy.",
                "",
                "§eClick to start"));
        inventory.setItem(31, button(Material.DRAGON_HEAD, "§5Floor 3 · Boss only", "dungeon:floor3-boss",
                "§7Skip the trash. Aetherion now.",
                "",
                "§eClick to start"));
        inventory.setItem(33, button(Material.BLUE_ICE, "§bFloor 2 · Frost (alias)", "dungeon:ice",
                "§7Same as Floor 2 full run.",
                "",
                "§eClick to start"));
        inventory.setItem(34, button(Material.CARVED_PUMPKIN, "§3Floor 2 · Boss (alias)", "dungeon:ice-boss",
                "§7Same as Floor 2 boss entry.",
                "",
                "§eClick to start"));
        inventory.setItem(39, button(Material.RESPAWN_ANCHOR, "§dEndless XL · Schem test", "dungeon:endless",
                "§7Same map as Floor 2 now.",
                "§8Dev alias (enter code 6).",
                "",
                "§eClick to start"));
        inventory.setItem(45, button(Material.ARROW, "§eBack", "back"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private static final long WIPE_ARM_MILLIS = 8_000L;

    private void drawPlayerWipe(Inventory inventory, UUID target, long armedUntil) {
        if (target == null) {
            inventory.setItem(4, DevItems.glow(button(Material.LAVA_BUCKET, "§4§l☠ Full Player Wipe", "noop",
                    "§7Pick an online player.",
                    "§7Wipes inventory, pads, skills, coins,",
                    "§7quests, codex, recipes, pets, hub unlocks.",
                    "§eOnline players are kicked after wipe.",
                    "§aNo server restart.",
                    "",
                    "§6Special ranks are never touched.",
                    "§8Pick → review → arm → confirm.")));
            drawPlayerPicker(inventory, "wipe-player:", "§cClick to review a wipe for this player.");
            return;
        }
        OfflinePlayer selected = Bukkit.getOfflinePlayer(target);
        boolean online = selected.isOnline();
        long left = armedUntil - System.currentTimeMillis();
        boolean armed = left > 0;
        inventory.setItem(4, playerHead(selected,
                "§c" + nameOf(selected),
                online ? "§aOnline §7— will be kicked after wipe." : "§7Offline §7— disk + RAM only.",
                "§7This cannot be undone.",
                "§6Special ranks in player-ranks.yml are kept."));
        inventory.setItem(20, button(Material.LIME_CONCRETE, "§a§l✔ KEEP §7— cancel", "wipe-cancel",
                "§7Back to the player list.",
                "§7Nothing is touched."));
        RankBadgeService ranks = ranks();
        RankBadgeService.Rank extra = ranks == null ? null : ranks.extraFor(target);
        inventory.setItem(22, button(Material.PAPER, "§fWhat gets zeroed", "noop",
                "§c✖ §7Inventory · ender chest · XP",
                "§c✖ §7Skills + loadout pads · storage",
                "§c✖ §7Coins · shards · XP boosts",
                "§c✖ §7Progress flags · codex · recipes · blueprints",
                "§c✖ §7Quests · hub teleports · pets · colosseum",
                "",
                extra == null ? "§a✔ §7No special rank on file." : "§a✔ §7Kept: " + extra.display()
                        + " §8(special ranks are exempt)",
                "§a✔ §7Level rank follows the XP reset."));
        if (armed) {
            inventory.setItem(24, DevItems.glow(button(Material.RED_CONCRETE,
                    "§4§l☠ CONFIRM WIPE §c— CLICK AGAIN", "wipe-confirm",
                    "§cArmed §7· expires in §f" + Math.max(1, (left + 999) / 1000) + "s",
                    "§7Zero everything for §f" + nameOf(selected) + "§7.",
                    online ? "§eThen kick from this server." : "§7Player is offline.")));
        } else {
            inventory.setItem(24, button(Material.RED_CONCRETE, "§c§lARM WIPE §8(step 1 of 2)", "wipe-confirm",
                    "§7Arms the wipe for §f8s§7.",
                    "§7Click §cCONFIRM §7again to fire.",
                    "§8Walk away and it disarms itself."));
        }
    }

    private void drawShards(Inventory inventory, UUID target) {
        ShardService shards = shards();
        inventory.setItem(45, button(Material.ARROW, "§eBack", target == null ? "back" : "page:SHARDS"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
        if (target == null) {
            drawPlayerPicker(inventory, "shard-player:", "§7Click to give them shards.");
            return;
        }
        OfflinePlayer selected = Bukkit.getOfflinePlayer(target);
        long balance = shards == null ? 0L : shards.get(target);
        inventory.setItem(4, playerHead(selected,
                "§b" + nameOf(selected),
                "§7Balance: §b" + String.format("%,d", balance),
                "§8Click an amount below."));
        long[] amounts = {1L, 10L, 50L, 100L, 500L, 1000L, 5000L};
        int slot = 19;
        for (long amount : amounts) {
            inventory.setItem(slot++, button(
                    Material.AMETHYST_SHARD,
                    "§b+" + amount,
                    "shard-add:" + amount,
                    "§7Give §b" + amount + " §7Aether Shards."
            ));
        }
    }

    private void drawPlayerPicker(Inventory inventory, String actionPrefix, String hint) {
        int shown = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (shown >= DevTheme.INNER.length) {
                break;
            }
            inventory.setItem(DevTheme.INNER[shown++], tagged(
                    playerHead(online, "§e" + online.getName(), hint),
                    actionPrefix + online.getUniqueId()
            ));
        }
        if (shown == 0) {
            inventory.setItem(22, button(Material.GRAY_DYE, "§7Nobody online", "noop"));
        }
    }

    private ItemStack playerHead(OfflinePlayer player, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.setDisplayName(name);
            if (lore.length > 0) {
                skull.setLore(List.of(lore));
            }
            item.setItemMeta(skull);
        }
        return item;
    }

    private Material rankIcon(String group) {
        return switch (group) {
            case "adventurer" -> Material.IRON_INGOT;
            case "veteran" -> Material.GOLD_INGOT;
            case "champion" -> Material.DIAMOND;
            case "legend" -> Material.NETHERITE_INGOT;
            case "mythwright" -> Material.AMETHYST_CLUSTER;
            case "aetherborn" -> Material.END_CRYSTAL;
            case "celestine" -> Material.PRISMARINE_CRYSTALS;
            case "sovereign" -> Material.GOLDEN_HELMET;
            case "ascendant" -> Material.HEART_OF_THE_SEA;
            case "empyrean" -> Material.BLAZE_POWDER;
            case "eternal" -> Material.ECHO_SHARD;
            case "aetherion" -> Material.DRAGON_EGG;
            case "mvpplusplus" -> Material.NETHER_STAR;
            case "admin" -> Material.BARRIER;
            case "monkey" -> Material.LIGHT_BLUE_DYE;
            case "citrus" -> Material.LIME_DYE;
            case "beta" -> Material.MAGENTA_DYE;
            default -> Material.GRAY_DYE;
        };
    }

    private UUID holderTarget(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
            return holder.target();
        }
        return null;
    }

    private RankBadgeService ranks() {
        AetherionItems plugin = AetherionItems.getInstance();
        return plugin == null ? null : plugin.ranks();
    }

    private ShardService shards() {
        AetherionItems plugin = AetherionItems.getInstance();
        return plugin == null ? null : plugin.getShards();
    }

    private String nameOf(OfflinePlayer player) {
        if (player == null) {
            return "unknown";
        }
        String name = player.getName();
        return name == null || name.isBlank() ? player.getUniqueId().toString() : name;
    }

    public void handleBoosterLab(Player player, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory) {
            ItemStack clicked = event.getCurrentItem();
            if (!isLabGear(clicked)) {
                if (clicked != null && !clicked.getType().isAir()) {
                    player.sendMessage("§cDrop Aetherion gear in the well. Boosters stay in the other page.");
                }
                return;
            }
            ItemStack well = labGear(top);
            top.setItem(BOOSTER_WELL_SLOT, clicked.clone());
            event.getClickedInventory().setItem(event.getSlot(), well);
            refreshBoosterLab(top);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BOOSTER_WELL_SLOT) {
            ItemStack cursor = player.getItemOnCursor();
            ItemStack well = labGear(top);
            if (cursor != null && !cursor.getType().isAir()) {
                if (!isLabGear(cursor)) {
                    player.sendMessage("§cAetherion gear only.");
                    return;
                }
                player.setItemOnCursor(well);
                top.setItem(BOOSTER_WELL_SLOT, cursor);
            } else {
                player.setItemOnCursor(well);
                top.setItem(BOOSTER_WELL_SLOT, labPlaceholder());
            }
            refreshBoosterLab(top);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String action = clicked.getItemMeta().getPersistentDataContainer().get(ItemKeys.devAction(), PersistentDataType.STRING);
        if (action != null && action.startsWith("lab-apply:")) {
            applyLabBooster(player, top, action.substring("lab-apply:".length()), event.isShiftClick() ? 5 : 1);
            return;
        }
        handle(player, clicked, slot);
    }

    public void returnLabItem(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return;
        }
        ItemStack gear = labGear(inventory);
        if (gear == null) {
            return;
        }
        inventory.setItem(BOOSTER_WELL_SLOT, null);
        player.getInventory().addItem(gear).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void applyLabBooster(Player player, Inventory inventory, String rawType, int times) {
        ItemManager items = itemManager();
        ItemStack gear = labGear(inventory);
        if (items == null || gear == null) {
            player.sendMessage("§cPut an Aetherion item in the well first.");
            return;
        }
        BoosterType type;
        try {
            type = BoosterType.valueOf(rawType);
        } catch (IllegalArgumentException exception) {
            return;
        }
        BoosterApplier.Status status = BoosterApplier.apply(items, gear, type, times);
        inventory.setItem(BOOSTER_WELL_SLOT, gear);
        refreshBoosterLab(inventory);
        switch (status) {
            case APPLIED -> {
                player.sendMessage("§aApplied §f" + type.name() + " §ax" + times + "§a.");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.55f, 1.35f);
                de.aetherion.items.util.QuestProgressHook.noteUsed(player, "APPLY_BOOSTER");
            }
            case FULL -> player.sendMessage("§cThat item is already at " + BoosterLimits.MAX_TOTAL + " boosters.");
            case NOT_ALLOWED -> player.sendMessage("§cThat booster does not fit this item.");
            case NO_RARITY, NOT_ITEM -> player.sendMessage("§cThat is not booster-ready Aetherion gear.");
        }
    }

    /** Booster buttons flank the well column (13 → 22 → 31 stays clear); never the glass ring. */
    private static final int[] LAB_SLOTS = {
            19, 20, 21, 23, 24, 25,
            28, 29, 30, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private void drawBoosterLab(Inventory inventory, ItemStack gear) {
        inventory.setItem(4, labInfo(gear));
        inventory.setItem(BOOSTER_WELL_SLOT, gear == null ? labPlaceholder() : gear);
        drawLabButtons(inventory, gear);
    }

    private void refreshBoosterLab(Inventory inventory) {
        ItemStack gear = labGear(inventory);
        inventory.setItem(4, labInfo(gear));
        if (gear == null) {
            inventory.setItem(BOOSTER_WELL_SLOT, labPlaceholder());
        }
        drawLabButtons(inventory, gear);
    }

    private void drawLabButtons(Inventory inventory, ItemStack gear) {
        BoosterType[] types = BoosterType.values();
        for (int i = 0; i < types.length && i < LAB_SLOTS.length; i++) {
            inventory.setItem(LAB_SLOTS[i], labBoosterButton(types[i], gear));
        }
    }

    private ItemStack labInfo(ItemStack gear) {
        ItemManager items = itemManager();
        if (gear == null || items == null) {
            return button(Material.ANVIL, "§dBooster Lab", "lab-info",
                    "§7Click an Aetherion item in your inventory.",
                    "§7Then click a booster below.",
                    "§8Uses the item's rarity. Shift-click = 5.");
        }
        Rarity rarity = items.getRarity(gear);
        ItemStats stats = items.getItemStats(gear);
        String name = gear.hasItemMeta() && gear.getItemMeta().hasDisplayName()
                ? gear.getItemMeta().getDisplayName()
                : gear.getType().name();
        return button(Material.ANVIL, "§dBooster Lab", "lab-info",
                "§7Item: §f" + name,
                "§7Rarity: §f" + (rarity == null ? "none" : rarity.name()),
                "§7Boosters: §f" + stats.getTotalBoosters() + "§7/" + BoosterLimits.MAX_TOTAL,
                "§8Click a booster to apply at this rarity.");
    }

    private ItemStack labBoosterButton(BoosterType type, ItemStack gear) {
        ItemManager items = itemManager();
        Rarity rarity = gear == null || items == null ? null : items.getRarity(gear);
        double value = 0;
        boolean percent = false;
        if (rarity != null) {
            if (type.isCore()) {
                value = BoosterStats.getCoreFlat(type, rarity);
            } else {
                value = BoosterStats.getSpecialStat(type, rarity);
                percent = type == BoosterType.GLOWSTONE
                        || type == BoosterType.WHEAT
                        || type == BoosterType.OAK
                        || type == BoosterType.BIRCH;
            }
        }
        String amount = rarity == null
                ? "§8Put an item in first."
                : "§7This rarity: §a+" + de.aetherion.items.item.ItemLore.formatStat(value) + (percent ? "%" : "");
        return button(labIcon(type), "§e" + type.name(), "lab-apply:" + type.name(),
                "§7" + BoosterStats.statLabel(type),
                amount,
                "§eClick §7+1   §eShift-click §7+5");
    }

    private Material labIcon(BoosterType type) {
        return switch (type) {
            case COAL -> Material.COAL;
            case IRON -> Material.IRON_INGOT;
            case GOLD -> Material.GOLD_INGOT;
            case DIAMOND -> Material.DIAMOND;
            case EMERALD -> Material.EMERALD;
            case REDSTONE -> Material.REDSTONE;
            case LAPIS -> Material.LAPIS_LAZULI;
            case GLOWSTONE -> Material.GLOWSTONE_DUST;
            case WHEAT -> Material.WHEAT;
            case CARROT -> Material.CARROT;
            case OAK -> Material.OAK_LOG;
            case BIRCH -> Material.BIRCH_LOG;
        };
    }

    private ItemStack labPlaceholder() {
        return button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Item well", "lab-well",
                "§7Click an Aetherion item",
                "§7in your inventory.");
    }

    private ItemStack labGear(Inventory inventory) {
        ItemStack item = inventory.getItem(BOOSTER_WELL_SLOT);
        return isLabGear(item) ? item : null;
    }

    private boolean isLabGear(ItemStack item) {
        ItemManager items = itemManager();
        if (items == null || item == null || item.getType().isAir()) {
            return false;
        }
        return items.isAetherionItem(item) && items.getBoosterType(item) == null && items.getRarity(item) != null;
    }

    private ItemManager itemManager() {
        AetherionItems plugin = AetherionItems.getInstance();
        return plugin == null ? null : plugin.getItemManager();
    }

    // ------------------------------------------------------------------ pins · confirms · search · batch

    /** Destructive actions. Wherever their buttons live, a click lands in the confirm modal first. */
    static boolean requiresConfirm(String action) {
        return switch (action) {
            case "skills:wipe", "quests:reset-all", "testbot:stopall", "farmportal:rebuild",
                 "farmisle:profile:reset", "farmisle:npcall:remove",
                 "fishisle:profile:reset", "fishisle:npcall:remove",
                 "mineisle:profile:reset", "mineisle:npcall:remove" -> true;
            default -> false;
        };
    }

    private void togglePin(Player player, String action, ItemStack shown) {
        if (contentOnly(player) || !DevIndex.isPinnable(action)) {
            player.sendMessage("§7That one can't be pinned.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.4f, 1.2f);
            return;
        }
        if (DevItems.viaOf(shown) == null) {
            DevIndex.remember(action, shown);
        }
        String name = DevIndex.plainName(shown);
        switch (DevPrefs.toggleFavorite(player, action)) {
            case PINNED -> {
                player.sendMessage("§e★ Pinned §f" + name + " §7— it's on your dashboard.");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.6f);
            }
            case UNPINNED -> {
                player.sendMessage("§7☆ Unpinned §f" + name + "§7.");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.7f);
            }
            case FULL -> {
                player.sendMessage("§c★ Dashboard full (" + DevPrefs.SLOTS + ") §7— press §eF §7on a pinned tile to free one.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1f);
            }
        }
        Holder current = currentHolder(player);
        if (current != null && current.page() == Page.ROOT) {
            open(player, Page.ROOT);
        }
    }

    private void openConfirm(Player player, String action) {
        if (!requiresConfirm(action)) {
            player.sendMessage("§cNothing to confirm for §f" + action + "§c.");
            return;
        }
        Holder current = currentHolder(player);
        // Search results would reopen without their query — land on DANGER instead.
        Page back = current == null || current.page() == Page.CONFIRM || current.page() == Page.SEARCH
                ? Page.CAT_DANGER : current.page();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
        open(player, new Holder(Page.CONFIRM, null, 0, null, back.name() + "|" + action, 0L));
    }

    static Page confirmReturn(Holder holder) {
        String payload = holder == null ? null : holder.payload();
        if (payload == null || payload.indexOf('|') < 0) {
            return Page.CAT_DANGER;
        }
        try {
            return Page.valueOf(payload.substring(0, payload.indexOf('|')));
        } catch (IllegalArgumentException ignored) {
            return Page.CAT_DANGER;
        }
    }

    static String confirmAction(Holder holder) {
        String payload = holder == null ? null : holder.payload();
        return payload == null || payload.indexOf('|') < 0 ? null : payload.substring(payload.indexOf('|') + 1);
    }

    private void runConfirmed(Player player) {
        Holder current = currentHolder(player);
        String action = current == null || current.page() != Page.CONFIRM ? null : confirmAction(current);
        if (action == null || !requiresConfirm(action)) {
            player.sendMessage("§cNothing to confirm.");
            return;
        }
        Page back = confirmReturn(current);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.3f);
        // Reopen the origin first so handlers that "reopen current" land there, not on the modal.
        open(player, back);
        // Shift-left satisfies the legacy sneak-to-confirm handlers (portal rebuild, isle resets).
        dispatch(player, DevItems.stub(Material.TNT, "§cconfirmed", action), -1, ClickType.SHIFT_LEFT, action);
    }

    private static final Map<UUID, Long> SEARCH_PROMPTS = new ConcurrentHashMap<>();
    private static final long SEARCH_PROMPT_MILLIS = 60_000L;

    void promptSearch(Player player) {
        if (contentOnly(player)) {
            player.sendMessage("§cSearch is DEV only.");
            return;
        }
        player.closeInventory();
        SEARCH_PROMPTS.put(player.getUniqueId(), System.currentTimeMillis() + SEARCH_PROMPT_MILLIS);
        player.sendMessage("");
        player.sendMessage("§d§l✎ DEV SEARCH §8» §7Type what you're after in chat.");
        player.sendMessage("§8   items · sets · pages · NPCs · tools — try §fkatana§8, §fdawnbearer§8, §fweather");
        player.sendMessage("§8   §7cancel §8to abort · open for 60s · also §f/devmenu <words>");
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    /** Chat hook (async-safe): true if this player's next message is a pending search query. */
    public boolean takeSearchPrompt(Player player) {
        Long until = SEARCH_PROMPTS.remove(player.getUniqueId());
        return until != null && until >= System.currentTimeMillis();
    }

    public void clearSearchPrompt(UUID playerId) {
        SEARCH_PROMPTS.remove(playerId);
    }

    public void openSearch(Player player, String query) {
        if (!canUse(player) || contentOnly(player)) {
            player.sendMessage("§cSearch is DEV only.");
            return;
        }
        String q = query == null ? "" : query.trim();
        if (q.isEmpty() || q.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7Search cancelled.");
            open(player, Page.ROOT);
            return;
        }
        if (q.length() > 40) {
            q = q.substring(0, 40);
        }
        open(player, new Holder(Page.SEARCH, null, 0, q, null, 0L));
    }

    /** Renders every indexable shelf off-screen and collects its tiles — search sees what pages show. */
    List<DevIndex.Entry> harvestIndex(Player player) {
        java.util.LinkedHashMap<String, DevIndex.Entry> found = new java.util.LinkedHashMap<>();
        for (Page page : Page.values()) {
            if (!indexable(page)) {
                continue;
            }
            for (int index = 0; index < 12; index++) {
                Holder spec = new Holder(page, null, index);
                Inventory scratch = Bukkit.createInventory(spec, 54, "index");
                try {
                    render(scratch, spec, player);
                } catch (RuntimeException | LinkageError error) {
                    break; // one shelf with an offline plugin must not take search down
                }
                String path = DevTheme.breadcrumb(page);
                for (int slot = 0; slot < scratch.getSize(); slot++) {
                    if (slot == DevTheme.HEADER) {
                        continue;
                    }
                    ItemStack item = scratch.getItem(slot);
                    String action = DevItems.actionOf(item);
                    if (action == null || DevIndex.isChrome(action) || found.containsKey(action)) {
                        continue;
                    }
                    String entryPath = path;
                    if (action.startsWith("page:")) {
                        try {
                            entryPath = DevTheme.breadcrumb(Page.valueOf(action.substring(5)));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    found.put(action, DevIndex.entry(action, item, entryPath));
                    // Isle pages build their own stacks — cache them so their pins survive restarts.
                    DevIndex.rememberIfAbsent(action, item);
                }
                String next = DevItems.actionOf(scratch.getItem(DevTheme.NEXT));
                if (next == null || !next.startsWith("pageidx:")) {
                    break;
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private static boolean indexable(Page page) {
        return switch (page) {
            case ROOT, SEARCH, CONFIRM, RANKS, SHARDS, PLAYER_WIPE, BOOSTER_LAB, TESTBOTS, TESTBOTS_LIST,
                 STATUS, CONTENT_KIT -> false;
            default -> true;
        };
    }

    static boolean isBatchGive(String action) {
        return action != null && (action.startsWith("item:") || action.startsWith("testgear:")
                || action.startsWith("ambient:") || action.startsWith("sphere:"));
    }

    private void givePage(Player player) {
        Holder current = currentHolder(player);
        if (current == null) {
            return;
        }
        List<ItemStack> shelf = new ArrayList<>();
        if (current.page() == Page.SEARCH) {
            for (DevIndex.Entry entry : DevIndex.search(this, player, current.query())) {
                if (isBatchGive(entry.action()) && shelf.size() < DevHubs.SEARCH_BATCH_LIMIT) {
                    shelf.add(entry.icon());
                }
            }
        } else {
            Inventory scratch = Bukkit.createInventory(current, 54, "batch");
            render(scratch, current, player);
            for (int slot = 9; slot < 54; slot++) {
                int col = slot % 9;
                boolean inner = slot < 45 && col > 0 && col < 8;
                boolean weaponRow = slot > 45 && slot < 53 && slot != 49;
                ItemStack item = scratch.getItem(slot);
                if ((inner || weaponRow) && isBatchGive(DevItems.actionOf(item))) {
                    shelf.add(item);
                }
            }
        }
        int given = 0;
        int dropped = 0;
        for (ItemStack shown : shelf) {
            ItemStack stack = batchStack(DevItems.actionOf(shown), shown);
            if (stack != null) {
                given++;
                dropped += giveQuiet(player, stack);
            }
        }
        reportBatch(player, "§a⇊ Shelf", given, dropped);
    }

    /** One shelf entry → the stack it gives, or null. No sounds (batch plays one). */
    private ItemStack batchStack(String action, ItemStack shown) {
        if (action == null) {
            return null;
        }
        if (action.startsWith("item:")) {
            return clean(shown);
        }
        if (action.startsWith("testgear:")) {
            return testGear(action.substring("testgear:".length()));
        }
        if (action.startsWith("ambient:")) {
            return ambientTool(action.substring("ambient:".length()));
        }
        if (action.startsWith("sphere:")) {
            return DevBridges.catchSphere(action.substring("sphere:".length()));
        }
        return null;
    }

    private void reportBatch(Player player, String label, int given, int dropped) {
        if (given == 0) {
            player.sendMessage("§7Nothing giveable there.");
            return;
        }
        player.sendMessage(label + " §8» §f" + given + " §7item" + (given == 1 ? "" : "s") + " handed over"
                + (dropped > 0 ? " §8· §e" + dropped + " dropped at your feet" : "") + "§7.");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.4f, 1.6f);
    }

    ItemStack testGear(String id) {
        List<ItemStack> pool = new ArrayList<>(TestGear.all());
        pool.addAll(TestGear.flagships());
        for (ItemStack gear : pool) {
            String gearId = gear.hasItemMeta()
                    ? gear.getItemMeta().getPersistentDataContainer().get(
                    de.aetherion.core.AetherKeys.namespaced("aetherion", "test_gear"), PersistentDataType.STRING)
                    : null;
            if (id.equals(gearId)) {
                return gear.clone();
            }
        }
        return null;
    }

    private ItemStack ambientTool(String id) {
        var items = AetherionItems.getInstance();
        return items == null || items.ambientProps() == null ? null : items.ambientProps().tool(id);
    }

    /** A named batch of real items. Built from the same factories the shelves use. */
    record Loadout(String id, Material icon, String name, String blurb, java.util.function.Supplier<List<ItemStack>> items) {
    }

    List<Loadout> loadouts() {
        List<Loadout> list = new ArrayList<>();
        list.add(new Loadout("combat-starter", Material.DIAMOND_SWORD, "§b⚔ Combat Lab Starter",
                "Combat V armor + sword, two T1 blades, a bow.",
                () -> List.of(customItem.createCombatHelmet5(), customItem.createCombatChestplate5(),
                        customItem.createCombatLeggings5(), customItem.createCombatBoots5(), customItem.createCombatSword5(),
                        customItem.createAetherblade(), customItem.createWarpedBlade(), customItem.createSkuldugeryShortbow())));
        list.add(new Loadout("endgame", Material.SUNFLOWER, "§e☀ Endgame Showcase",
                "Dawnbearer + Solstice, Ashen Katana, Worldbite, every flagship.",
                () -> {
                    List<ItemStack> items = new ArrayList<>(List.of(customItem.createHeliosCrown(),
                            customItem.createHeliosHeartplate(), customItem.createHeliosOrbitGreaves(),
                            customItem.createHeliosDawnTreads(), customItem.createHeliosSolstice(),
                            customItem.createAshenKatana(), customItem.createWorldbite()));
                    items.addAll(TestGear.flagships());
                    return items;
                }));
        list.add(new Loadout("hollow-sun", Material.NETHERITE_CHESTPLATE, "§6Hollow Sun Kit",
                "Hollow Sun set + Gravwell Cleaver + Seraphine Needle.",
                () -> List.of(customItem.createHollowSunHelmet(), customItem.createHollowSunChestplate(),
                        customItem.createHollowSunLeggings(), customItem.createHollowSunBoots(),
                        customItem.createGravwellCleaver(), customItem.createSeraphineNeedle())));
        list.add(new Loadout("seraphine", Material.PINK_DYE, "§dSeraphine Ensemble",
                "Veil · Bodice · Bell Skirt · Pointe Slippers · Needle.",
                () -> List.of(customItem.createSeraphineVeil(), customItem.createSeraphineBodice(),
                        customItem.createSeraphineBellSkirt(), customItem.createSeraphinePointeSlippers(),
                        customItem.createSeraphineNeedle())));
        list.add(new Loadout("worldhide", Material.NETHERITE_HELMET, "§5Worldhide + Worldbite",
                "The full Worldhide set with its blade.",
                () -> List.of(customItem.createWorldhideHelmet(), customItem.createWorldhideChestplate(),
                        customItem.createWorldhideLeggings(), customItem.createWorldhideBoots(), customItem.createWorldbite())));
        list.add(new Loadout("flagships", Material.NETHER_STAR, "§d✦ Flagship Rack",
                "Every flagship + every sandbox prototype.",
                () -> {
                    List<ItemStack> items = new ArrayList<>(TestGear.flagships());
                    items.addAll(TestGear.all());
                    return items;
                }));
        list.add(new Loadout("t1", Material.WITHER_SKELETON_SKULL, "§5Every T1 Unique",
                "The whole T1 shelf.", () -> shelf(Page.WEAPONS_T1)));
        list.add(new Loadout("t2", Material.END_CRYSTAL, "§dEvery T2 Unique",
                "The whole T2 shelf — Seraphine, Gravwell, cores, ledger.", () -> shelf(Page.WEAPONS_T2)));
        list.add(new Loadout("gatherer", Material.NETHERITE_PICKAXE, "§aGatherer T5",
                "Top tool of every skill: pick · hoe · axe · rod · gaff.",
                () -> List.of(customItem.createMiningPickaxe5(), customItem.farming().hoe(5),
                        customItem.foraging().axe(5), customItem.fishing().rod(5), customItem.catcher().gaff(3))));
        list.add(new Loadout("blueprint-tools", Material.FILLED_MAP, "§bBlueprint Tools",
                "All six finished blueprint tools.",
                () -> List.of(customItem.createVeinSiphon(), customItem.createCanopyCleaver(), customItem.createBountyHoe(),
                        customItem.createWildSight(), customItem.createTideLatch(), customItem.createResonanceScythe(false))));
        list.add(new Loadout("dungeon", Material.HEART_OF_THE_SEA, "§3Dungeon Relic Run",
                "Relic T2 + T3 sets and weapons, cores I – III.",
                () -> List.of(
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.HELMET),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.LEGGINGS),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T2, de.aetherion.items.dungeon.DungeonPiece.BOOTS),
                        customItem.createDungeonWeaponRelic(de.aetherion.items.dungeon.DungeonGearTier.T2),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.HELMET),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.CHESTPLATE),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.LEGGINGS),
                        customItem.createDungeonRelic(de.aetherion.items.dungeon.DungeonGearTier.T3, de.aetherion.items.dungeon.DungeonPiece.BOOTS),
                        customItem.createDungeonWeaponRelic(de.aetherion.items.dungeon.DungeonGearTier.T3),
                        customItem.createDungeonCore(), customItem.createDungeonCore2(), customItem.createDungeonCore3())));
        list.add(new Loadout("boosters", Material.EMERALD, "§2Booster Crate",
                "One of every booster type.", () -> shelf(Page.BOOSTERS)));
        list.add(new Loadout("charms", Material.MAGMA_CREAM, "§dCharm Case",
                "Every charm in every tier.", () -> shelf(Page.CHARMS)));
        list.add(new Loadout("qol", Material.ENDER_CHEST, "§eStorage & QoL",
                "Storage · recipe book · sacks · blood vial · dragon vial.",
                () -> List.of(storage.createStorage(), customItem.createRecipeBook(),
                        de.aetherion.items.storage.SackItems.create(de.aetherion.items.storage.SackType.RESOURCE),
                        de.aetherion.items.storage.SackItems.create(de.aetherion.items.storage.SackType.BOOSTER),
                        de.aetherion.items.shop.AetherBloodVial.create(), customItem.createDragonAscensionVial())));
        return list;
    }

    private List<ItemStack> shelf(Page page) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack shown : contents(page)) {
            if (isBatchGive(DevItems.actionOf(shown))) {
                out.add(clean(shown));
            }
        }
        return out;
    }

    private void giveLoadout(Player player, String id) {
        for (Loadout loadout : loadouts()) {
            if (!loadout.id().equals(id)) {
                continue;
            }
            List<ItemStack> items;
            try {
                items = loadout.items().get();
            } catch (RuntimeException error) {
                player.sendMessage("§cLoadout failed to build: §7" + error.getClass().getSimpleName());
                return;
            }
            int given = 0;
            int dropped = 0;
            for (ItemStack item : items) {
                if (item != null) {
                    given++;
                    dropped += giveQuiet(player, item.clone());
                }
            }
            reportBatch(player, loadout.name(), given, dropped);
            return;
        }
        player.sendMessage("§cUnknown loadout §f" + id + "§c.");
    }

    public static final class Holder implements InventoryHolder {
        private final Page page;
        private final UUID target;
        private final int index;
        private final String query;
        private final String payload;
        private final long armedUntil;

        Holder(Page page, UUID target) {
            this(page, target, 0);
        }

        Holder(Page page, UUID target, int index) {
            this(page, target, index, null, null, 0L);
        }

        Holder(Page page, UUID target, int index, String query, String payload, long armedUntil) {
            this.page = page;
            this.target = target;
            this.index = index;
            this.query = query;
            this.payload = payload;
            this.armedUntil = armedUntil;
        }

        public Page page() {
            return page;
        }

        public UUID target() {
            return target;
        }

        public int index() {
            return index;
        }

        /** SEARCH: the query shown. */
        public String query() {
            return query;
        }

        /** CONFIRM: {@code <RETURN_PAGE>|<action>}. */
        public String payload() {
            return payload;
        }

        /** PLAYER_WIPE: epoch millis until which CONFIRM fires instead of arming. */
        public long armedUntil() {
            return armedUntil;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

}
