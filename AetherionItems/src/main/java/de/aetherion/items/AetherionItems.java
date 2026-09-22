package de.aetherion.items;

import de.aetherion.items.codex.BestiaryGUI;
import de.aetherion.items.codex.CodexListener;
import de.aetherion.items.codex.CodexService;
import de.aetherion.items.codex.CollectionGUI;
import de.aetherion.items.command.AetherionCommand;
import de.aetherion.items.command.GuideCommand;
import de.aetherion.items.command.RecipeCommand;

import de.aetherion.items.item.CustomItem;

import de.aetherion.items.listener.AetherbladeListener;
import de.aetherion.items.listener.AetherionSetListener;
import de.aetherion.items.listener.AnvilBoosterListener;
import de.aetherion.items.listener.BridgedAxeListener;
import de.aetherion.items.listener.CrossbowListener;
import de.aetherion.items.listener.CraftingListener;
import de.aetherion.items.listener.DamageListener;
import de.aetherion.items.listener.EmeraldSpreadListener;
import de.aetherion.items.listener.PvpGuardListener;
import de.aetherion.items.listener.HealthListener;
import de.aetherion.items.listener.ItemProtectionListener;
import de.aetherion.items.listener.LoadoutListener;
import de.aetherion.items.listener.LongbowListener;
import de.aetherion.items.listener.HarvestListener;
import de.aetherion.items.listener.PickupDropsListener;
import de.aetherion.items.listener.PersistenceFlushListener;
import de.aetherion.items.listener.RecipeBookListener;
import de.aetherion.items.listener.ShortbowListener;
import de.aetherion.items.listener.T2UniqueListener;
import de.aetherion.items.listener.SquidsBootListener;
import de.aetherion.items.listener.StorageListener;
import de.aetherion.items.listener.VoidStickListener;
import de.aetherion.items.listener.WandListener;
import de.aetherion.items.listener.WarpedBladeListener;

import de.aetherion.items.manager.ItemManager;

import de.aetherion.items.menu.AetherionManager;
import de.aetherion.items.menu.AetherionManagerListener;

import de.aetherion.items.recipe.BukkitRecipeService;
import de.aetherion.items.recipe.GUI.RecipeBookGUI;
import de.aetherion.items.recipe.RecipeDefinition;
import de.aetherion.items.recipe.RecipeManager;
import de.aetherion.items.recipe.RecipeRegistry;
import de.aetherion.items.storage.AetherionStorage;
import de.aetherion.items.storage.StorageInventory;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public class AetherionItems extends JavaPlugin {

    public static final String NAMESPACE = "aetherion";

    private static AetherionItems instance;

    private ItemManager itemManager;
    private RecipeManager recipeManager;
    private de.aetherion.items.recipe.RecipeUnlockService recipeUnlocks;
    private de.aetherion.items.blueprint.BlueprintUnlockService blueprintUnlocks;
    private RecipeBookGUI recipeBookGUI;
    private BukkitRecipeService recipeService;
    private HealthListener healthListener;
    private HarvestListener harvestListener;
    private de.aetherion.core.api.HarvestAccess harvestAccess;
    private de.aetherion.items.mining.OreTrollListener oreTrollListener;
    private de.aetherion.items.blueprint.SurveyorGui surveyorGui;
    private StorageInventory storageInventory;
    private StorageListener storageListener;
    private de.aetherion.items.storage.SackInventory sackInventory;
    private LoadoutListener loadoutListener;
    private AetherionManager manager;
    private de.aetherion.items.menu.dev.DevMenu devMenu;
    private de.aetherion.items.world.TestArenaService testArena;
    private CodexService codex;
    private de.aetherion.items.economy.ItemValueService itemValues;
    private de.aetherion.items.economy.CoinService coins;
    private de.aetherion.items.economy.ShardService shards;
    private de.aetherion.items.shop.ShardShopMenu shardShop;
    private de.aetherion.items.shop.XpBoosterService xpBoost;
    private de.aetherion.items.skill.AetherionXpBarSync xpBarSync;
    private de.aetherion.items.rank.RankBadgeService ranks;
    private de.aetherion.items.guide.DiscordGuideHook discordGuide;

    private de.aetherion.items.skill.SkillService skills;
    private de.aetherion.items.skill.SkillMenu skillMenu;
    private de.aetherion.items.economy.TraderService trader;
    private de.aetherion.items.economy.GearTraderService gearTrader;
    private de.aetherion.items.economy.FishShopService fishShop;
    private de.aetherion.items.economy.FenceService fence;
    private de.aetherion.items.economy.LiquidatorService liquidator;
    private de.aetherion.items.economy.MarketService market;
    private de.aetherion.items.casino.CasinoService casino;
    private de.aetherion.items.world.AreaService areas;
    private de.aetherion.items.world.MobZoneService mobZones;
    private de.aetherion.items.world.WorldMapService worldMaps;
    private de.aetherion.items.world.CryptHologramService cryptHolograms;
    private de.aetherion.items.world.BuildingBannerService buildingBanners;
    private de.aetherion.items.social.PartyService party;
    private CustomItem customItem;
    private de.aetherion.items.progress.ProgressionService progress;
    private de.aetherion.core.api.ItemFactoryAccess itemFactoryAccess;
    private de.aetherion.core.api.ProgressAccess progressAccess;
    private de.aetherion.items.blueprint.BlueprintForgeStation blueprintForgeStation;
    private de.aetherion.items.blueprint.BlueprintForgeRitual blueprintForgeRitual;
    private de.aetherion.items.farm.MillstoneRitual millstoneRitual;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        itemManager = new ItemManager(this);
        de.aetherion.items.combat.GearSetBonuses.register(itemManager);
        itemValues = new de.aetherion.items.economy.ItemValueService(this, itemManager);
        coins = new de.aetherion.items.economy.CoinService(this);
        de.aetherion.core.api.AetherServices.registerCoins(coins);
        shards = new de.aetherion.items.economy.ShardService(this);
        xpBoost = new de.aetherion.items.shop.XpBoosterService(this);
        party = new de.aetherion.items.social.PartyService();
        de.aetherion.core.api.AetherServices.registerParty(party);
        skills = new de.aetherion.items.skill.SkillService(this, coins);
        xpBarSync = new de.aetherion.items.skill.AetherionXpBarSync(this, skills);
        skillMenu = new de.aetherion.items.skill.SkillMenu(skills);
        progress = new de.aetherion.items.progress.ProgressionService(this);
        progressAccess = new de.aetherion.items.api.ProgressAccessImpl(this);
        de.aetherion.core.api.AetherServices.registerProgress(progressAccess);
        recipeUnlocks = new de.aetherion.items.recipe.RecipeUnlockService(this);
        blueprintUnlocks = new de.aetherion.items.blueprint.BlueprintUnlockService(this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.ProgressUnlockListener(progress), this);
        getServer().getPluginManager().registerEvents(skillMenu, this);
        de.aetherion.items.command.SkillCommand skillCommand = new de.aetherion.items.command.SkillCommand(skills, skillMenu);
        if (getCommand("skills") != null) {
            getCommand("skills").setExecutor(skillCommand);
            getCommand("skills").setTabCompleter(skillCommand);
        }
        if (getCommand("av") != null) {
            getCommand("av").setExecutor(new de.aetherion.items.command.StationCommand(
                    de.aetherion.items.command.StationCommand.Station.ANVIL));
        }
        if (getCommand("ct") != null) {
            getCommand("ct").setExecutor(new de.aetherion.items.command.StationCommand(
                    de.aetherion.items.command.StationCommand.Station.WORKBENCH));
        }
        shardShop = new de.aetherion.items.shop.ShardShopMenu(shards);
        getServer().getPluginManager().registerEvents(shardShop, this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.shop.AetherBloodVial(), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.world.NetherRealm(), this);
        new de.aetherion.items.world.WorldRulesListener(this);
        de.aetherion.items.command.ShardCommand shardCommand = new de.aetherion.items.command.ShardCommand(shards, shardShop);
        if (getCommand("shards") != null) {
            getCommand("shards").setExecutor(shardCommand);
            getCommand("shards").setTabCompleter(shardCommand);
        }
        if (getCommand("shardshop") != null) {
            getCommand("shardshop").setExecutor(shardCommand);
        }
        trader = new de.aetherion.items.economy.TraderService(this, itemValues, coins);
        market = new de.aetherion.items.economy.MarketService(this, itemValues, coins);
        casino = new de.aetherion.items.casino.CasinoService(this, coins);
        recipeManager = new RecipeManager();
        storageInventory = new StorageInventory(this);

        customItem = new CustomItem(itemManager);
        itemFactoryAccess = new de.aetherion.items.api.ItemFactoryAccessImpl(customItem);
        de.aetherion.core.api.AetherServices.registerItems(itemFactoryAccess);
        getServer().getPluginManager().registerEvents(new PersistenceFlushListener(this), this);
        gearTrader = new de.aetherion.items.economy.GearTraderService(this, itemValues, coins, customItem);
        fishShop = new de.aetherion.items.economy.FishShopService(coins, customItem);
        fence = new de.aetherion.items.economy.FenceService(this, itemValues, coins, customItem);
        liquidator = new de.aetherion.items.economy.LiquidatorService(this, itemValues, coins, shards);
        AetherionStorage aetherionStorage = new AetherionStorage(this);
        de.aetherion.items.world.AnimalZoneService animalZones =
                new de.aetherion.items.world.AnimalZoneService(this);
        mobZones = new de.aetherion.items.world.MobZoneService(this);
        de.aetherion.items.world.PetHabitatZoneService petHabitats =
                new de.aetherion.items.world.PetHabitatZoneService(this);
        areas = new de.aetherion.items.world.AreaService(this);
        de.aetherion.items.world.ColosseumArena.ensureArea(this, areas);
        worldMaps = new de.aetherion.items.world.WorldMapService(this);
        cryptHolograms = new de.aetherion.items.world.CryptHologramService(this);
        buildingBanners = new de.aetherion.items.world.BuildingBannerService(this);
        devMenu = new de.aetherion.items.menu.dev.DevMenu(
                customItem, aetherionStorage, animalZones, mobZones, petHabitats,
                areas, worldMaps, cryptHolograms, buildingBanners);
        testArena = new de.aetherion.items.world.TestArenaService(this);
        new de.aetherion.items.world.TestArenaGuard(this, testArena);
        new de.aetherion.items.world.ShabbyMineGuard(this);
        new de.aetherion.items.world.NaturalSpawnGuard(this);
        getServer().getScheduler().runTaskLater(this, () -> {
            if (testArena != null) {
                testArena.ensureWorld();
            }
        }, 60L);
        new de.aetherion.items.listener.HungerListener(this);

        new RecipeRegistry(recipeManager, customItem).registerAll();

        recipeService = new BukkitRecipeService(this, recipeManager);
        recipeService.registerAll();

        getServer().getPluginManager().registerEvents(
                new CraftingListener(itemManager, recipeManager, recipeService, storageInventory, recipeUnlocks),
                this
        );

        getCommand("aetheriontest").setExecutor(new AetherionCommand(customItem));
        getCommand("aetherionitems").setExecutor(new AetherionCommand(customItem));
        if (getCommand("devmenu") != null) {
            getCommand("devmenu").setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player) {
                    if (devMenu == null || !de.aetherion.items.menu.dev.DevMenu.canUse(player)) {
                        sender.sendMessage("§cDEV only.");
                        return true;
                    }
                    devMenu.open(player);
                }
                return true;
            });
        }

        registerCombatListeners();
        registerWorldFeatureListeners(animalZones, petHabitats);
        registerEconomyListeners();
        registerFarmStations();
        registerHarvestListeners();
        registerProgressionAndSocial();
        registerStorageAndLoadout(aetherionStorage);
        registerMenusAndPlayerCommands();
        registerIntegrations();

        getLogger().info("AetherionItems enabled!");
        getLogger().info("Registered Aetherion recipes: " + recipeManager.getRecipeCount());
    }

    /** Combat / unique gear listeners. Order matches the former inline onEnable block. */
    private void registerCombatListeners() {
        getServer().getPluginManager().registerEvents(new AnvilBoosterListener(itemManager), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.menu.BoosterSocketMenu(itemManager), this);
        PvpGuardListener pvpGuard = new PvpGuardListener(this);
        getServer().getPluginManager().registerEvents(pvpGuard, this);
        pvpGuard.applyToLoadedWorlds();
        getServer().getPluginManager().registerEvents(new DamageListener(itemManager), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.WildlifeCombatListener(), this);
        getServer().getPluginManager().registerEvents(de.aetherion.items.combat.DamageNumbers.create(), this);

        ShortbowListener shortbowListener = new ShortbowListener(itemManager);
        getServer().getPluginManager().registerEvents(shortbowListener, this);
        getServer().getScheduler().runTaskTimer(this, shortbowListener, 1L, 1L);

        LongbowListener longbowListener = new LongbowListener(itemManager);
        getServer().getPluginManager().registerEvents(longbowListener, this);
        getServer().getScheduler().runTaskTimer(this, longbowListener::tick, 1L, 1L);

        getServer().getPluginManager().registerEvents(new AetherbladeListener(this, itemManager), this);
        getServer().getPluginManager().registerEvents(new BridgedAxeListener(this, itemManager), this);
        getServer().getPluginManager().registerEvents(new WarpedBladeListener(itemManager), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.GravwellCleaverListener(this, itemManager), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.AshenKatanaListener(this, itemManager), this);
        T2UniqueListener t2Uniques = new T2UniqueListener(itemManager);
        getServer().getPluginManager().registerEvents(t2Uniques, this);
        getServer().getScheduler().runTaskTimer(this, t2Uniques, 10L, 10L);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.TestGearListener(this, itemManager), this);
        new SquidsBootListener(this, itemManager);
        getServer().getPluginManager().registerEvents(new AetherionSetListener(this, itemManager), this);
        new VoidStickListener(this, itemManager);
        new WandListener(this, itemManager);
        new de.aetherion.items.listener.HealerSetListener(this, itemManager);
        new de.aetherion.items.dungeon.DungeonGearListener(this, itemManager, customItem);
        getServer().getPluginManager().registerEvents(new CrossbowListener(itemManager), this);
    }

    private void registerWorldFeatureListeners(
            de.aetherion.items.world.AnimalZoneService animalZones,
            de.aetherion.items.world.PetHabitatZoneService petHabitats
    ) {
        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.world.AnimalAnchorListener(animalZones),
                this
        );
        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.world.MobAnchorListener(mobZones, areas),
                this
        );
        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.world.PetHabitatAnchorListener(petHabitats),
                this
        );
        new de.aetherion.items.world.BorderlandsRiteService(this, mobZones);
        new de.aetherion.items.world.ColosseumGateService(this);
        new de.aetherion.items.world.ColosseumEscortService(this);
        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.world.NpcRemoverListener(),
                this
        );
        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.world.AreaListener(areas),
                this
        );
        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.world.WorldMapListener(worldMaps),
                this
        );
        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.world.CryptHologramListener(cryptHolograms),
                this
        );
        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.world.BuildingBannerListener(buildingBanners),
                this
        );
        new de.aetherion.items.world.CryptDiscoverListener(this, mobZones);
        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.world.MarkerVisibilityListener(areas, animalZones, mobZones, petHabitats),
                this
        );
        new de.aetherion.items.item.GearTooltip(this, itemManager);
        de.aetherion.items.world.WildlifeLooks.register(this);
        de.aetherion.items.world.BorderlandsLightPass.schedule(this);
    }

    private void registerEconomyListeners() {
        getServer().getPluginManager().registerEvents(new PickupDropsListener(itemValues), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.AutoPickupListener(this), this);
        getServer().getPluginManager().registerEvents(trader, this);
        getServer().getPluginManager().registerEvents(gearTrader, this);
        getServer().getPluginManager().registerEvents(fishShop, this);
        getServer().getPluginManager().registerEvents(fence, this);
        getServer().getPluginManager().registerEvents(liquidator, this);
        getServer().getScheduler().runTaskLater(this, () -> {
            if (liquidator != null) {
                liquidator.migrateLegacyVillagers();
            }
        }, 120L);
        getServer().getPluginManager().registerEvents(market, this);
        getServer().getPluginManager().registerEvents(casino, this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.casino.CasinoCabinet(), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.casino.RouletteCabinet(), this);
    }

    private void registerFarmStations() {
        millstoneRitual = new de.aetherion.items.farm.MillstoneRitual(this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.farm.MillstoneCabinet(this, millstoneRitual), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.farm.MillstoneWindmill(this), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.farm.MillstoneListener(), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.farm.RootCellarListener(), this);
        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.menu.dev.DevMenuListener(devMenu),
                this
        );
    }

    private void registerHarvestListeners() {
        harvestListener = new HarvestListener(itemManager);
        harvestAccess = new de.aetherion.items.api.HarvestAccessImpl(harvestListener);
        de.aetherion.core.api.AetherServices.registerHarvest(harvestAccess);
        getServer().getPluginManager().registerEvents(harvestListener, this);
        oreTrollListener = new de.aetherion.items.mining.OreTrollListener(this, itemManager);
        getServer().getPluginManager().registerEvents(oreTrollListener, this);
        surveyorGui = new de.aetherion.items.blueprint.SurveyorGui(this);
        getServer().getPluginManager().registerEvents(surveyorGui, this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.VeinSiphonListener(this, itemManager), this);
        blueprintForgeStation = new de.aetherion.items.blueprint.BlueprintForgeStation(this);
        blueprintForgeRitual = new de.aetherion.items.blueprint.BlueprintForgeRitual(this, blueprintForgeStation);
        getServer().getPluginManager().registerEvents(blueprintForgeRitual, this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.blueprint.BlueprintForgeListener(), this);

        getServer().getPluginManager().registerEvents(new EmeraldSpreadListener(itemManager), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.CropHarvestListener(itemManager), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.FishingListener(this, itemManager), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.PocketForgeListener(this, itemManager), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.EstateLiquidatorListener(this, itemManager, coins), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.JoinWelcomeListener(this), this);
        new de.aetherion.items.listener.DivingGearListener(this, itemManager);
    }

    private void registerProgressionAndSocial() {
        healthListener = new HealthListener(this, itemManager);
        getServer().getPluginManager().registerEvents(healthListener, this);
        if (skills != null) {
            skills.setHealthListener(healthListener);
        }

        getServer().getPluginManager().registerEvents(
                new de.aetherion.items.listener.ProgressionEffects(this, itemManager, healthListener),
                this
        );

        getServer().getPluginManager().registerEvents(new ItemProtectionListener(itemManager), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.ChatFormatListener(this), this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.social.PlayerSocialListener(this), this);
        de.aetherion.items.command.PartyCommand partyCommand = new de.aetherion.items.command.PartyCommand(party);
        if (getCommand("party") != null) {
            getCommand("party").setExecutor(partyCommand);
            getCommand("party").setTabCompleter(partyCommand);
        }
    }

    private void registerStorageAndLoadout(AetherionStorage aetherionStorage) {
        storageListener = new StorageListener(aetherionStorage, storageInventory);
        getServer().getPluginManager().registerEvents(storageListener, this);

        sackInventory = new de.aetherion.items.storage.SackInventory(this);
        getServer().getPluginManager().registerEvents(new de.aetherion.items.listener.SackListener(this, sackInventory), this);

        loadoutListener = new LoadoutListener();
        getServer().getPluginManager().registerEvents(loadoutListener, this);
    }

    private void registerMenusAndPlayerCommands() {
        recipeBookGUI = new RecipeBookGUI(recipeManager);
        codex = new CodexService(this);
        manager = new AetherionManager(
                itemManager,
                recipeBookGUI,
                storageInventory,
                new BestiaryGUI(codex),
                new CollectionGUI(codex),
                new de.aetherion.items.codex.DungeonJournalGUI(codex)
        );
        getServer().getPluginManager().registerEvents(new CodexListener(codex), this);
        de.aetherion.items.codex.BossJournalListener.register(this, codex);
        de.aetherion.items.listener.CoreShardDropListener.register(this);
        getServer().getPluginManager().registerEvents(new AetherionManagerListener(manager), this);
        getServer().getScheduler().runTask(this, this::hookPetMenu);
        getServer().getScheduler().runTaskLater(this, this::hookPetMenu, 20L);

        getCommand("atrecipes").setExecutor(new RecipeCommand(recipeBookGUI));
        if (getCommand("guide") != null) {
            GuideCommand guide = new GuideCommand(this);
            getCommand("guide").setExecutor(guide);
            getCommand("guide").setTabCompleter(guide);
        }
        getCommand("aetherion").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) {
                manager.open(player);
            }
            return true;
        });
        if (getCommand("bazaar") != null) {
            getCommand("bazaar").setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player && market != null) {
                    if (progress != null && !progress.bazaar(player)) {
                        player.sendMessage(progress.hint(de.aetherion.items.progress.ProgressionService.Flag.TRADER));
                        return true;
                    }
                    market.openBazaar(player);
                }
                return true;
            });
        }
        if (getCommand("ah") != null) {
            getCommand("ah").setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player && market != null) {
                    if (progress != null && !progress.auction(player)) {
                        player.sendMessage(progress.hint(de.aetherion.items.progress.ProgressionService.Flag.TRADER));
                        return true;
                    }
                    market.openAuction(player);
                }
                return true;
            });
        }
        if (getCommand("trades") != null) {
            getCommand("trades").setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player && trader != null) {
                    if (de.aetherion.items.farming.Crops.isDungeonWorld(player.getWorld())) {
                        player.sendMessage("§cTrading is disabled inside dungeons.");
                        return true;
                    }
                    trader.open(player);
                }
                return true;
            });
        }
        if (getCommand("gamble") != null) {
            de.aetherion.items.command.GambleCommand gambleCommand =
                    new de.aetherion.items.command.GambleCommand(casino);
            getCommand("gamble").setExecutor(gambleCommand);
            getCommand("gamble").setTabCompleter(gambleCommand);
        }
        getServer().getPluginManager().registerEvents(
                new RecipeBookListener(recipeBookGUI, itemManager),
                this
        );
    }

    private void registerIntegrations() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new de.aetherion.items.placeholder.CoinPlaceholderExpansion(this, coins).register();
            getLogger().info("PlaceholderAPI coin placeholders registered.");
        }
        ranks = de.aetherion.items.rank.RankBadgeService.apply(this);

        discordGuide = new de.aetherion.items.guide.DiscordGuideHook(this);
        // DiscordSRV JDA may not be ready at enable — retry a few times
        getServer().getScheduler().runTaskLater(this, discordGuide::tryHook, 40L);
        getServer().getScheduler().runTaskLater(this, discordGuide::tryHook, 100L);
    }

    @Override
    public void onDisable() {
        de.aetherion.items.listener.AshenKatanaListener.shutdown();
        // Close open GUIs so AH/Bazaar/trade/sack holders return or persist items
        // before YAML flush. Server is stopping; this is not a gameplay change.
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            if (market != null) {
                market.closeOpenGuis(player);
            }
            player.closeInventory();
        }
        if (discordGuide != null) {
            discordGuide.unhook();
        }
        // Prevent StatProvider stacking across soft reloads (SPEED / pets / skills).
        de.aetherion.items.manager.ActiveEquipmentStats.clearProviders();
        if (progress != null) {
            progress.save();
        }
        if (recipeUnlocks != null) {
            recipeUnlocks.save();
        }
        if (blueprintUnlocks != null) {
            blueprintUnlocks.save();
        }
        if (blueprintForgeRitual != null) {
            blueprintForgeRitual.shutdown();
        }
        if (millstoneRitual != null) {
            millstoneRitual.shutdown();
        }
        if (codex != null) {
            codex.save();
        }
        if (skills != null) {
            skills.save();
        }
        if (storageInventory != null) {
            storageInventory.saveAll();
        }
        if (shards != null) {
            shards.save();
        }
        if (xpBoost != null) {
            xpBoost.save();
        }
        if (coins != null) {
            coins.save();
        }
        if (areas != null) {
            areas.save();
        }
        if (market != null) {
            market.save();
        }
        if (ranks != null) {
            ranks.flush();
        }
        if (recipeService != null) {
            recipeService.unregisterAll();
        }
        if (party != null) {
            de.aetherion.core.api.AetherServices.clearParty(party);
        }
        if (progressAccess != null) {
            de.aetherion.core.api.AetherServices.clearProgress(progressAccess);
            progressAccess = null;
        }
        if (coins != null) {
            de.aetherion.core.api.AetherServices.clearCoins(coins);
        }
        if (itemFactoryAccess != null) {
            de.aetherion.core.api.AetherServices.clearItems(itemFactoryAccess);
            itemFactoryAccess = null;
        }
        if (harvestAccess != null) {
            de.aetherion.core.api.AetherServices.clearHarvest(harvestAccess);
            harvestAccess = null;
        }

        getLogger().info("AetherionItems disabled!");
    }

    public static AetherionItems getInstance() {
        return instance;
    }

    public de.aetherion.items.blueprint.BlueprintForgeRitual getBlueprintForgeRitual() {
        return blueprintForgeRitual;
    }

    public de.aetherion.items.farm.MillstoneRitual getMillstoneRitual() {
        return millstoneRitual;
    }

    public de.aetherion.items.blueprint.BlueprintForgeStation getBlueprintForgeStation() {
        return blueprintForgeStation;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public BukkitRecipeService getRecipeService() {
        return recipeService;
    }

    public void registerRecipe(RecipeDefinition definition) {
        if (definition == null || recipeManager == null || recipeService == null) {
            return;
        }

        if (!recipeManager.hasRecipe(definition.getId())) {
            recipeManager.register(definition);
        }

        recipeService.register(definition);
    }

    public RecipeBookGUI getRecipeBookGUI() {
        return recipeBookGUI;
    }

    public StorageInventory getStorageInventory() {
        return storageInventory;
    }

    public de.aetherion.items.storage.SackInventory getSackInventory() {
        return sackInventory;
    }

    public StorageListener getStorageListener() {
        return storageListener;
    }

    public LoadoutListener getLoadoutListener() {
        return loadoutListener;
    }

    public AetherionManager getManager() {
        return manager;
    }

    public void setPetMenuOpener(Consumer<Player> opener) {
        if (manager != null) {
            manager.setPetMenuOpener(opener);
        }
    }

    private void hookPetMenu() {
        var mobs = getServer().getPluginManager().getPlugin("AetherMobs");
        if (mobs == null || !mobs.isEnabled()) {
            return;
        }
        try {
            Object menu = mobs.getClass().getMethod("getPetMenu").invoke(mobs);
            if (menu == null) {
                return;
            }
            java.lang.reflect.Method open = menu.getClass().getMethod("open", Player.class);
            setPetMenuOpener(player -> {
                try {
                    open.invoke(menu, player);
                } catch (Exception ignored) {
                    player.performCommand("pets");
                }
            });
        } catch (Exception ignored) {
        }
    }

    public HealthListener getHealthListener() {
        return healthListener;
    }

    public de.aetherion.items.menu.dev.DevMenu getDevMenu() {
        return devMenu;
    }

    public de.aetherion.items.world.TestArenaService testArena() {
        return testArena;
    }

    public HarvestListener getHarvestListener() {
        return harvestListener;
    }

    /** @deprecated use {@link #getHarvestListener()} or {@link de.aetherion.core.api.AetherServices#harvest()}. */
    @Deprecated
    public HarvestListener getMiningListener() {
        return harvestListener;
    }

    public de.aetherion.items.mining.OreTrollListener getOreTrollListener() {
        return oreTrollListener;
    }

    public de.aetherion.items.blueprint.SurveyorGui getSurveyorGui() {
        return surveyorGui;
    }

    public CodexService getCodex() {
        return codex;
    }

    public CustomItem getCustomItem() {
        return customItem;
    }

    public de.aetherion.items.progress.ProgressionService progress() {
        return progress;
    }

    public de.aetherion.items.recipe.RecipeUnlockService recipeUnlocks() {
        return recipeUnlocks;
    }

    public de.aetherion.items.blueprint.BlueprintUnlockService blueprintUnlocks() {
        return blueprintUnlocks;
    }

    public de.aetherion.items.economy.ItemValueService getItemValues() {
        return itemValues;
    }

    public de.aetherion.items.economy.CoinService getCoins() {
        return coins;
    }

    public de.aetherion.items.economy.ShardService getShards() {
        return shards;
    }

    public de.aetherion.items.shop.ShardShopMenu getShardShop() {
        return shardShop;
    }

    public de.aetherion.items.economy.FishShopService getFishShop() {
        return fishShop;
    }

    public de.aetherion.items.shop.XpBoosterService xpBoost() {
        return xpBoost;
    }

    public de.aetherion.items.skill.AetherionXpBarSync xpBarSync() {
        return xpBarSync;
    }

    public de.aetherion.items.social.PartyService party() {
        return party;
    }

    public de.aetherion.items.skill.SkillService getSkills() {
        return skills;
    }

    public de.aetherion.items.skill.SkillMenu getSkillMenu() {
        return skillMenu;
    }

    public de.aetherion.items.rank.RankBadgeService ranks() {
        return ranks;
    }

    public de.aetherion.items.economy.TraderService getTrader() {
        return trader;
    }

    public de.aetherion.items.economy.LiquidatorService getLiquidator() {
        return liquidator;
    }

    public de.aetherion.items.casino.CasinoService getCasino() {
        return casino;
    }

    public de.aetherion.items.economy.MarketService getMarket() {
        return market;
    }

    public de.aetherion.items.world.AreaService getAreas() {
        return areas;
    }

    public de.aetherion.items.world.MobZoneService getMobZones() {
        return mobZones;
    }

    public de.aetherion.items.world.WorldMapService getWorldMaps() {
        return worldMaps;
    }

    public de.aetherion.items.world.CryptHologramService getCryptHolograms() {
        return cryptHolograms;
    }

    public de.aetherion.items.world.BuildingBannerService getBuildingBanners() {
        return buildingBanners;
    }
}
