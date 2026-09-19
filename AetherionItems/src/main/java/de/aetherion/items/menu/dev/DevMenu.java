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
import java.util.UUID;

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
        NPC_EDITOR,
        NPCS_STARTER,
        NPCS_BOSSES,
        NPCS_WORLD,
        NPCS_SERVICES,
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
        ISLE_WEATHER
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
    }

    public static final String PERM_FULL = "aetherion.dev";
    public static final String PERM_MENU = "aetherion.dev.menu";
    public static final String PERM_NPC_EDITOR = "aetherion.npc.editor";

    public static boolean canUse(Player player) {
        return player != null && (player.isOp()
                || player.hasPermission(PERM_FULL)
                || player.hasPermission(PERM_MENU));
    }

    /** Moderators / admins: every Dev-menu tile. Monkey is menu-only. */
    public static boolean hasFullAccess(Player player) {
        return player != null && (player.isOp() || player.hasPermission(PERM_FULL));
    }

    public static boolean canUseNpcEditor(Player player) {
        return player != null && (player.hasPermission(PERM_NPC_EDITOR) || hasFullAccess(player));
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
        if (!canUse(player)) {
            player.sendMessage("§cDEV only.");
            return;
        }
        Page shown = page;
        if (!hasFullAccess(player) && page != Page.ROOT) {
            shown = Page.ROOT;
        }
        Inventory inventory = Bukkit.createInventory(new Holder(shown, target, index), 54, TITLE);
        fill(inventory);
        if (shown == Page.ROOT) {
            drawRoot(inventory, player, Math.max(0, index));
        } else if (shown == Page.ISLE_WEATHER) {
            drawIsleWeather(inventory, player);
        } else {
            drawPage(inventory, shown, target, Math.max(0, index));
        }
        player.openInventory(inventory);
    }

    public void handle(Player player, ItemStack clicked, int slot) {
        handle(player, clicked, slot, ClickType.LEFT);
    }

    public void handle(Player player, ItemStack clicked, int slot, ClickType click) {
        if (!canUse(player) || clicked == null || !clicked.hasItemMeta()) {
            return;
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
        if (!allowAction(player, action)) {
            player.sendMessage("§cThis tool is not available for your rank.");
            return;
        }
        if (action.equals("npc-editor") || action.startsWith("npc-editor:")) {
            String sub = action.equals("npc-editor") ? "open" : action.substring("npc-editor:".length());
            runNpcEditor(player, sub);
            return;
        }
        if (action.equals("close")) {
            de.aetherion.items.util.ManagerNav.openManager(player);
            return;
        }
        if (action.equals("back")) {
            open(player, Page.ROOT);
            return;
        }
        if (action.startsWith("testbot:")) {
            handleTestbots(player, action, click);
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
                open(player, Page.valueOf(parts[0]), holderTarget(player), index);
            }
            return;
        }
        if (action.startsWith("page:")) {
            open(player, Page.valueOf(action.substring(5)));
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
            ranks.setRank(target, group);
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
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.15f);
            open(player, Page.RANKS, target);
            return;
        }
        if (action.startsWith("shard-player:")) {
            open(player, Page.SHARDS, UUID.fromString(action.substring("shard-player:".length())));
            return;
        }
        if (action.startsWith("shard-add:")) {
            UUID target = holderTarget(player);
            ShardService shards = shards();
            if (target == null || shards == null) {
                player.sendMessage("§cPick a player first.");
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
            ItemStack give = clicked.clone();
            ItemMeta meta = give.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().remove(ItemKeys.devAction());
                give.setItemMeta(meta);
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
        if (action.startsWith("testgear:")) {
            String id = action.substring("testgear:".length());
            for (ItemStack gear : TestGear.all()) {
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

    private void drawRoot(Inventory inventory, Player player, int index) {
        if (!hasFullAccess(player)) {
            drawLimitedRoot(inventory, player);
            return;
        }
        int page = Math.max(0, Math.min(1, index));
        inventory.setItem(4, button(Material.NETHER_STAR, "§6§lDEV Menu", "root",
                "§7Page §f" + (page + 1) + "§7 / §f2",
                "§bNPC / Quest Editor §7· dedicated section",
                "§8One glass ring · no edge clutter."));
        if (canUseNpcEditor(player)) {
            inventory.setItem(2, npcEditorSectionButton());
        }

        if (page == 0) {
            // Row 1 — sets
            inventory.setItem(10, button(Material.DIAMOND_CHESTPLATE, "§bCombat Sets", "page:COMBAT", "§7I – V + swords."));
            inventory.setItem(11, button(Material.IRON_PICKAXE, "§aMining Sets", "page:MINING", "§7I – V + pickaxes."));
            inventory.setItem(12, button(Material.GOLDEN_HOE, "§eFarming Sets", "page:FARMING", "§7Armor + levelable hoe."));
            inventory.setItem(13, button(Material.IRON_AXE, "§2Foraging Sets", "page:FORAGING", "§7Armor + axe."));
            inventory.setItem(14, button(Material.FISHING_ROD, "§bFishing Sets", "page:FISHING", "§7Armor + rod."));
            inventory.setItem(15, button(Material.IRON_HOE, "§dCatcher Sets", "page:CATCHER", "§7Armor + gaff."));
            inventory.setItem(16, button(Material.NETHERITE_HELMET, "§5Special Sets", "page:SETS", "§7Aetherion / dungeon / god."));

            // Row 2 — gear / crafting
            inventory.setItem(19, button(Material.NETHERITE_SWORD, "§cWeapons", "page:WEAPONS", "§7Starter → god."));
            inventory.setItem(20, button(Material.EMERALD, "§2Boosters", "page:BOOSTERS", "§7All booster types."));
            inventory.setItem(21, button(Material.ANVIL, "§dBooster Lab", "page:BOOSTER_LAB", "§7Apply boosters."));
            inventory.setItem(22, button(Material.CRAFTING_TABLE, "§eTools", "page:TOOLS", "§7Basics, storage, recipe book."));
            inventory.setItem(23, button(Material.FILLED_MAP, "§bBlueprints", "page:BLUEPRINTS",
                    "§7Blueprint + finished tool side by side.",
                    "§7Vein Siphon · Ore Troll spawn."));
            inventory.setItem(24, button(Material.PLAYER_HEAD, "§7Resources", "page:RESOURCES", "§7Compressed / compacted."));
            inventory.setItem(25, button(Material.MAGMA_CREAM, "§dCharms", "page:CHARMS", "§7Off-hand accessories."));

            // Row 3 — world placeables
            inventory.setItem(28, button(Material.LEAD, "§dPets", "page:PETS", "§7Spawn / collect."));
            inventory.setItem(29, button(Material.ENDER_EYE, "§dSpheres", "page:SPHERES", "§7Catch spheres."));
            inventory.setItem(30, button(Material.VILLAGER_SPAWN_EGG, "§bNPCs", "page:NPCS", "§7Starter / bosses / world."));
            inventory.setItem(31, button(Material.LODESTONE, "§6Spawn Anchors", "page:SPAWN_MARKERS", "§7Origin teleports."));
            inventory.setItem(32, button(Material.HAY_BLOCK, "§aAnimal Anchor", "give:animal", "§7Place animal zone."));
            inventory.setItem(33, button(Material.ROTTEN_FLESH, "§cMob Anchor", "give:mob", "§7Place combat zone."));
            inventory.setItem(34, button(Material.COARSE_DIRT, "§6Borderlands", "give:borderlands", "§7Waste combat zone."));

            // Row 4 — more world
            inventory.setItem(37, button(Material.MOSS_BLOCK, "§aPet Habitats", "page:PET_HABITATS", "§7Wild pet biotopes."));
            inventory.setItem(38, button(Material.SANDSTONE, "§6Colosseum Spawn", "give:colosseum", "§7Hub ring teleport."));
            inventory.setItem(39, button(Material.RECOVERY_COMPASS, "§8Boss Anchors", "page:BOSS_ANCHORS", "§7Place spawn points."));
            inventory.setItem(40, button(Material.NETHER_STAR, "§5Boss Cores", "page:BOSS_CORES", "§7Summon for tests."));
            inventory.setItem(41, button(Material.FILLED_MAP, "§eArea Tools", "page:AREAS", "§7Map · hologram · markers."));
            inventory.setItem(42, button(Material.END_PORTAL_FRAME, "§dPortals", "page:PORTALS", "§7Farm island portal."));
            inventory.setItem(43, button(Material.GRINDSTONE, "§6Millstone", "page:MILLSTONE",
                    "§7Farm Isle mill + pantry loop.",
                    "§7Anchor · crops · spheres · treats · NPC."));
            inventory.setItem(44, button(Material.WHITE_BANNER, "§bIsle Weather", "page:ISLE_WEATHER",
                    "§7Force fog / rain / snow for ~75s.",
                    "§7Test only — then ambient again."));
        } else {
            // Page 2 — admin / test
            inventory.setItem(10, button(Material.NAME_TAG, "§6Ranks", "page:RANKS", "§7Account ranks."));
            inventory.setItem(11, button(Material.AMETHYST_SHARD, "§bAether Shards", "page:SHARDS", "§7Give shards."));
            inventory.setItem(12, button(Material.DEEPSLATE_BRICKS, "§5Dungeons", "page:DUNGEONS", "§7Start / skip boss."));
            inventory.setItem(13, button(Material.EXPERIENCE_BOTTLE, "§aMax Skills", "skills:max", "§7All skills Lv. 100."));
            inventory.setItem(14, button(Material.TNT, "§cWipe Skills", "skills:wipe", "§7All skills → Lv. 1."));
            inventory.setItem(15, button(Material.WRITABLE_BOOK, "§eReset Quests", "quests:reset-all", "§7Wipe quest progress."));
            inventory.setItem(16, button(Material.BOOK, "§aTutorial Done", "quests:tutorial-done",
                    "§7Complete orientation quests.", "§7Unlock Manager flags for testing."));
            inventory.setItem(17, button(Material.STRUCTURE_BLOCK, "§d✦ Test Arena", "page:TEST_ARENA", "§7Void sandbox + bosses."));
            inventory.setItem(19, button(Material.DEEPSLATE_IRON_ORE, "§bEldervale Deep Marker", "give:eldervale-mobs",
                    "§7Optional · zone already in mob-zones.yml.",
                    "§7Only re-place if the deep pack is missing.",
                    "§8Y≤24 · Rotten Miner / Cave Scrapper / Dust Digger."));
            inventory.setItem(20, button(Material.POTION, "§cBorderlands Spirits", "page:BORDERLANDS_SPIRITS", "§7Ritual vials."));
            inventory.setItem(21, button(Material.PLAYER_HEAD, "§bTestbots", "page:TESTBOTS",
                    "§7QA bots · Wave 1 + combat/fish/trade/quest/pad.",
                    "§7Start/stop from here. §f/botreport"));
            if (canUseNpcEditor(player)) {
                inventory.setItem(22, npcEditorSectionButton());
            }
        }

        drawBorderNav(inventory, page, 2, "ROOT");
    }

    private void drawLimitedRoot(Inventory inventory, Player player) {
        inventory.setItem(4, button(Material.NETHER_STAR, "§6§lDEV Menu", "root",
                "§7Staff tools you can use."));
        if (canUseNpcEditor(player)) {
            inventory.setItem(22, npcEditorButton());
        }
        inventory.setItem(45, button(Material.ARROW, "§eClose", "close", "§7Leave DEV menu."));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private ItemStack npcEditorButton() {
        return button(Material.WRITABLE_BOOK, "§bNPC / Quest Editor", "npc-editor",
                "§7Create FancyNPCs with dialogue.",
                "§7Story NPCs stay untouched.",
                "§eOpens /npc");
    }

    private ItemStack npcEditorSectionButton() {
        return button(Material.WRITABLE_BOOK, "§b§lNPC / Quest Editor", "page:NPC_EDITOR",
                "§7Dedicated staff section.",
                "§7Create FancyNPCs with dialogue.",
                "§7Story NPCs stay untouched.",
                "§eClick · also /npc /aethernpc");
    }

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
        inventory.setItem(23, button(Material.BOOK, "§6List NPCs", "npc-editor:list",
                "§7Every editor NPC you created."));
        inventory.setItem(31, button(Material.KNOWLEDGE_BOOK, "§fHelp", "npc-editor:help",
                "§7Commands and permissions."));
        inventory.setItem(45, button(Material.ARROW, "§eBack", "back", "§7Return to DEV menu."));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private static boolean allowAction(Player player, String action) {
        if (hasFullAccess(player)) {
            return true;
        }
        return "close".equals(action)
                || "back".equals(action)
                || "root".equals(action)
                || "npc-editor".equals(action)
                || (action != null && action.startsWith("npc-editor:"));
    }

    private static void runNpcEditor(Player player, String action) {
        if (!canUseNpcEditor(player)) {
            player.sendMessage("§cYou need §faetherion.npc.editor §cto use the NPC editor.");
            return;
        }
        de.aetherion.core.api.QuestProgressAccess quests = de.aetherion.core.api.AetherServices.quests();
        if (quests == null || !quests.npcEditorAction(player, action)) {
            player.sendMessage("§cAetherionQuests is not loaded.");
        }
    }

    /** Bottom nav: far-left 45 · center Close 49 · far-right 53. */
    private void drawBorderNav(Inventory inventory, int page, int pageCount, String pageName) {
        if (page > 0) {
            inventory.setItem(45, button(Material.ARROW, "§ePrevious", "pageidx:" + pageName + ":" + (page - 1),
                    "§7Page §f" + page));
        } else {
            inventory.setItem(45, button(Material.ARROW, "§eClose", "close", "§7Leave DEV menu."));
        }
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
        if (page + 1 < pageCount) {
            inventory.setItem(53, button(Material.ARROW, "§eNext page", "pageidx:" + pageName + ":" + (page + 1),
                    "§7Page §f" + (page + 2)));
        }
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
            drawSetGrid(inventory, page);
            return;
        }
        List<ItemStack> contents = contents(page);
        int perPage = 45;
        int start = Math.max(0, index) * perPage;
        int end = Math.min(contents.size(), start + perPage);
        int slot = 0;
        for (int i = start; i < end; i++) {
            inventory.setItem(slot++, contents.get(i));
        }
        if (start > 0) {
            inventory.setItem(45, button(Material.ARROW, "§ePrevious page", "pageidx:" + page.name() + ":" + (index - 1),
                    "§7Page §f" + index));
        } else {
            inventory.setItem(45, button(Material.ARROW, "§eBack", backAction(page)));
        }
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
        if (end < contents.size()) {
            inventory.setItem(53, button(Material.ARROW, "§eNext page", "pageidx:" + page.name() + ":" + (index + 1),
                    "§7Page §f" + (index + 2),
                    "§7" + (contents.size() - end) + " more on the next page."));
        }
    }

    private String backAction(Page page) {
        return switch (page) {
            case WEAPONS_STARTER, WEAPONS_BOWS, WEAPONS_PROGRESSION, WEAPONS_T1, WEAPONS_T2, WEAPONS_DUNGEON, WEAPONS_GOD
                    -> "page:WEAPONS";
            case NPCS_STARTER, NPCS_BOSSES, NPCS_WORLD, NPCS_SERVICES
                    -> "page:NPCS";
            case TEST_GEAR -> "page:TEST_ARENA";
            case TESTBOTS_LIST -> "page:TESTBOTS";
            default -> "back";
        };
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
        inventory.setItem(45, button(Material.ARROW, "§eBack", "back"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private void drawSetGrid(Inventory inventory, Page page) {
        List<ItemStack[]> columns = setColumns(page);
        int maxCols = Math.min(columns.size(), 9);
        for (int col = 0; col < maxCols; col++) {
            ItemStack[] pieces = columns.get(col);
            for (int row = 0; row < pieces.length && row < 5; row++) {
                if (pieces[row] != null) {
                    inventory.setItem(row * 9 + col, pieces[row]);
                }
            }
        }
        inventory.setItem(45, button(Material.ARROW, "§eBack", "back"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
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
                items.add(itemButton(customItem.createGravwellCleaver(), "gravwell_cleaver"));
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
                var wheat = de.aetherion.items.economy.CompressedResource.WHEAT;
                var carrot = de.aetherion.items.economy.CompressedResource.CARROT;
                var potato = de.aetherion.items.economy.CompressedResource.POTATO;
                items.add(itemButton(wheat.compressed(), wheat.compressedId()));
                items.add(itemButton(carrot.compressed(), carrot.compressedId()));
                items.add(itemButton(potato.compressed(), potato.compressedId()));
                items.add(itemButton(wheat.compacted(), wheat.compactedId()));
                items.add(itemButton(carrot.compacted(), carrot.compactedId()));
                items.add(itemButton(potato.compacted(), potato.compactedId()));
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
                        new ItemStack(Material.CARROT),
                        "§aRoot Cellar §8(Mill Keeper)"
                ), "npc:root_cellar"));
            }
            case NPCS -> {
                // Hub is drawn separately.
            }
            case NPCS_STARTER -> DevBridges.npcs(DevBridges.NpcBucket.STARTER)
                    .forEach(entry -> items.add(tagged(named(entry.icon().clone(), entry.name()), "npc:" + entry.id())));
            case NPCS_BOSSES -> DevBridges.npcs(DevBridges.NpcBucket.BOSS)
                    .forEach(entry -> items.add(tagged(named(entry.icon().clone(), entry.name()), "npc:" + entry.id())));
            case NPCS_WORLD -> DevBridges.npcs(DevBridges.NpcBucket.WORLD)
                    .forEach(entry -> items.add(tagged(named(entry.icon().clone(), entry.name()), "npc:" + entry.id())));
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
        ItemStack item = switch (id) {
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
        if (item == null) {
            player.sendMessage("§cUnknown item.");
            return;
        }
        give(player, item);
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
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
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
        return item;
    }

    private ItemStack tagged(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ItemKeys.devAction(), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack named(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
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
        inventory.setItem(33, button(
                rankIcon("mvpplusplus"),
                (extra != null && extra.group().equals("mvpplusplus") ? "§a▶ " : "") + "§6MVP§c++",
                "rank-set:mvpplusplus",
                "§7Ultra rank. Stays on top of the",
                "§7Aetherion title."
        ));
        inventory.setItem(34, button(
                rankIcon("admin"),
                (extra != null && extra.group().equals("admin") ? "§a▶ " : "") + "§cAdmin",
                "rank-set:admin",
                "§7Ultra rank. Stays on top of the",
                "§7Aetherion title."
        ));
        inventory.setItem(40, button(
                Material.EXPERIENCE_BOTTLE,
                "§eMatch account level",
                "rank-sync",
                "§7Clear the DEV override.",
                "§7Rank follows XP again."
        ));
    }

    private void drawTestGear(Inventory inventory) {
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
        inventory.setItem(4, button(Material.STRUCTURE_BLOCK, "§d✦ Test Arena", "root",
                "§7Void world §f" + de.aetherion.items.world.TestArenaService.WORLD_NAME,
                "§7Big flat pad. Spawn bosses here.",
                "§8Sandbox only — not the live game."));
        inventory.setItem(9, button(Material.ENDER_PEARL, "§aGo to arena", "test:goto",
                "§7Creates the world if needed,",
                "§7then teleports you to the pad."));
        inventory.setItem(10, button(Material.OAK_DOOR, "§eLeave arena", "test:leave",
                "§7Returns you to where you entered."));
        inventory.setItem(11, button(Material.BRICKS, "§bRebuild pad", "test:rebuild",
                "§7Re-lays the smooth-stone platform."));
        inventory.setItem(12, button(Material.WITHER_SKELETON_SKULL, "§cClear bosses", "test:clear-bosses",
                "§7Despawns BossEngine fights",
                "§7inside the test world."));
        inventory.setItem(13, button(Material.BARRIER, "§cClear all mobs", "test:clear-mobs",
                "§7Removes every non-player entity."));
        inventory.setItem(14, button(Material.ZOMBIE_HEAD, "§aSpawn test mobs", "test:spawn-dummies",
                "§7Spawns simple zombies / skeletons",
                "§7around you for ability testing.",
                "§8/summon also works in this world."));
        inventory.setItem(15, button(Material.NETHERITE_CHESTPLATE, "§d✦ Test Gear", "page:TEST_GEAR",
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
        int perPage = 27;
        int start = Math.max(0, index) * perPage;
        int end = Math.min(bosses.size(), start + perPage);
        int slot = 18;
        for (int i = start; i < end; i++) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, bosses.get(i));
        }
        if (start > 0) {
            inventory.setItem(45, button(Material.ARROW, "§ePrevious", "pageidx:TEST_ARENA:" + (index - 1)));
        } else {
            inventory.setItem(45, button(Material.ARROW, "§eBack", "back"));
        }
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
        if (end < bosses.size()) {
            inventory.setItem(53, button(Material.ARROW, "§eNext", "pageidx:TEST_ARENA:" + (index + 1),
                    "§7" + (bosses.size() - end) + " more bosses."));
        }
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
        inventory.setItem(45, button(Material.ARROW, "§eBack", "root"));
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
        return new String[] {"mine", "forage", "catch", "roam", "combat", "fish", "trade", "quest", "pad"};
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
            player.sendMessage("§6" + bot.displayName() + " §8· §7" + bot.name() + " §8· §e" + bot.role());
            player.sendMessage("§7" + bot.world() + String.format(" %.1f %.1f %.1f", bot.x(), bot.y(), bot.z()));
            player.sendMessage("§7activity §f" + bot.activity() + " §8· §7held §f" + bot.heldItem()
                    + " §8· §7deaths §f" + bot.deaths());
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
                "§8Wave 1 + combat / fish / trade / quest / pad."));

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
                    "§eClick §7for bot list (name · loc · held)"));
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
            inventory.setItem(45, button(Material.ARROW, "§ePrevious roles", "pageidx:TESTBOTS:" + (page - 1),
                    "§7Page §f" + page));
        } else {
            inventory.setItem(45, button(Material.ARROW, "§eBack", "back"));
        }
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
        if (page + 1 < pages) {
            inventory.setItem(53, button(Material.ARROW, "§eMore roles", "pageidx:TESTBOTS:" + (page + 1),
                    "§7combat · fish · trade · quest · pad"));
        }
    }

    private void drawTestbotList(Inventory inventory, int index) {
        String[] roles = testbotRoles();
        String role = roles[Math.max(0, Math.min(roles.length - 1, index))];
        TestBotReport report = DevBridges.testBotsReport();
        inventory.setItem(4, button(roleIcon(role), "§b" + role + " bots", "page:TESTBOTS",
                "§7Nickname · world xyz · held · activity"));
        int slot = 9;
        if (report != null) {
            for (TestBotView bot : report.bots()) {
                if (!role.equalsIgnoreCase(bot.role())) {
                    continue;
                }
                if (slot >= 44) {
                    break;
                }
                inventory.setItem(slot++, button(Material.PLAYER_HEAD, "§e" + bot.displayName(),
                        "testbot:view:" + bot.name(),
                        "§8login §7" + bot.name(),
                        "§7" + bot.world() + String.format(" %.1f %.1f %.1f", bot.x(), bot.y(), bot.z()),
                        "§7activity §f" + bot.activity(),
                        "§7held §f" + bot.heldItem(),
                        "§7deaths §f" + bot.deaths(),
                        bot.lastAction().isBlank() ? "§8no recent action" : "§7last §f" + bot.lastAction(),
                        "",
                        "§eClick §7for a chat dump"));
            }
        }
        if (slot == 9) {
            inventory.setItem(22, button(Material.GRAY_DYE, "§7None online", "page:TESTBOTS",
                    "§7Start this role from the overview."));
        }
        inventory.setItem(45, button(Material.ARROW, "§eBack", "page:TESTBOTS"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
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
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, tagged(
                    playerHead(online, "§e" + online.getName(), hint),
                    actionPrefix + online.getUniqueId()
            ));
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
        if (!hasFullAccess(player)) {
            event.setCancelled(true);
            return;
        }
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

    private void drawBoosterLab(Inventory inventory, ItemStack gear) {
        inventory.setItem(4, labInfo(gear));
        inventory.setItem(BOOSTER_WELL_SLOT, gear == null ? labPlaceholder() : gear);
        int slot = 19;
        for (BoosterType type : BoosterType.values()) {
            if (slot == 22) {
                slot = 23;
            }
            if (slot >= 36) {
                break;
            }
            inventory.setItem(slot++, labBoosterButton(type, gear));
        }
        inventory.setItem(45, button(Material.ARROW, "§eBack", "back"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private void refreshBoosterLab(Inventory inventory) {
        ItemStack gear = labGear(inventory);
        inventory.setItem(4, labInfo(gear));
        if (gear == null) {
            inventory.setItem(BOOSTER_WELL_SLOT, labPlaceholder());
        }
        int slot = 19;
        for (BoosterType type : BoosterType.values()) {
            if (slot == 22) {
                slot = 23;
            }
            if (slot >= 36) {
                break;
            }
            inventory.setItem(slot++, labBoosterButton(type, gear));
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

    public static final class Holder implements InventoryHolder {
        private final Page page;
        private final UUID target;
        private final int index;

        Holder(Page page, UUID target) {
            this(page, target, 0);
        }

        Holder(Page page, UUID target, int index) {
            this.page = page;
            this.target = target;
            this.index = index;
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

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
