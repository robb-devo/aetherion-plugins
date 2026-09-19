package de.aetherion.aethermobs;

import de.aetherion.aethermobs.command.PetTestCommand;
import de.aetherion.aethermobs.listener.FarmingDragonListener;
import de.aetherion.aethermobs.listener.FishingDragonListener;
import de.aetherion.aethermobs.listener.ForagingDragonListener;
import de.aetherion.aethermobs.listener.DragonAscensionListener;
import de.aetherion.aethermobs.listener.PetCatchListener;
import de.aetherion.aethermobs.listener.PetExperienceListener;
import de.aetherion.aethermobs.menu.PetMenu;
import de.aetherion.aethermobs.pet.ActivePetManager;
import de.aetherion.aethermobs.pet.BetaSphereManager;
import de.aetherion.aethermobs.pet.CatchSphereRecipes;
import de.aetherion.aethermobs.pet.CatchSphereRegistry;
import de.aetherion.aethermobs.pet.PetDataManager;
import de.aetherion.aethermobs.pet.PetDefinition;
import de.aetherion.aethermobs.pet.PetEntity;
import de.aetherion.aethermobs.pet.PetExpTreat;
import de.aetherion.aethermobs.pet.PetFactory;
import de.aetherion.aethermobs.pet.PetGenerator;
import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.aethermobs.pet.PetRegistry;
import de.aetherion.aethermobs.pet.PetSpawnManager;
import de.aetherion.aethermobs.pet.PlayerPetCollection;
import de.aetherion.aethermobs.pet.RarityGlow;
import de.aetherion.aethermobs.pet.skill.PetSkillManager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AetherMobs extends JavaPlugin implements Listener {

    private static AetherMobs instance;

    private PetRegistry petRegistry;
    private PetGenerator petGenerator;
    private PetSpawnManager petSpawnManager;

    private CatchSphereRegistry catchSphereRegistry;
    private BetaSphereManager betaSphereManager;

    private PetDataManager petDataManager;
    private PetMenu petMenu;
    private ActivePetManager activePetManager;
    private PetSkillManager petSkillManager;
    private Object petStatProvider;
    private PetExperienceListener petExperienceListener;

    private final Map<UUID, PlayerPetCollection> playerPetCollections =
            new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPetPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;

        /*
         * =====================================================
         * CORE SYSTEMS
         * =====================================================
         */

        PetFactory petFactory =
                new PetFactory();

        petRegistry =
                new PetRegistry();

        petGenerator =
                new PetGenerator();

        petSpawnManager =
                new PetSpawnManager(
                        this
                );

        try {
            RarityGlow.purgeAll();
        } catch (Throwable ignored) {
        }

        catchSphereRegistry =
                new CatchSphereRegistry();

        betaSphereManager =
                new BetaSphereManager(
                        catchSphereRegistry
                );

        petDataManager =
                new PetDataManager(
                        this
                );

        petMenu =
                new PetMenu(
                        this
                );

        activePetManager =
                new ActivePetManager(
                        this
                );

        petSkillManager =
                new PetSkillManager(
                        this
                );


        /*
         * =====================================================
         * PET STAT PROVIDER
         * =====================================================
         *
         * Connect equipped pet stats with
         * the AetherionItems stat system.
         */

        try {
            Class<?> statsClass = Class.forName("de.aetherion.items.manager.ActiveEquipmentStats");
            Class<?> providerInterface = Class.forName("de.aetherion.items.manager.StatProvider");
            petStatProvider = Class.forName("de.aetherion.aethermobs.pet.PetStatProvider")
                    .getConstructor(AetherMobs.class)
                    .newInstance(this);
            statsClass.getMethod("registerProvider", providerInterface).invoke(null, petStatProvider);
        } catch (Throwable exception) {
            getLogger().warning("Could not hook pet stats into AetherionItems: " + exception.getMessage());
        }

        try {
            Class<?> effects = Class.forName("de.aetherion.items.listener.ProgressionEffects");
            Class<?> source = Class.forName("de.aetherion.items.manager.CompactBonusSource");
            Object bonus = Class.forName("de.aetherion.aethermobs.pet.PetCompactBonus")
                    .getConstructor(AetherMobs.class)
                    .newInstance(this);
            effects.getMethod("registerCompactBonus", source).invoke(null, bonus);
        } catch (Throwable exception) {
            getLogger().warning("Could not hook pet crop compact into AetherionItems: " + exception.getMessage());
        }


        /*
         * =====================================================
         * SURFACE PETS
         * =====================================================
         *
         * These pets spawn on the surface.
         */

        petRegistry.register(
                petFactory.createWolf()
        );

        petRegistry.register(
                petFactory.createPig()
        );

        petRegistry.register(
                petFactory.createCow()
        );


        /*
         * =====================================================
         * FARM
         * =====================================================
         */

        petRegistry.register(
                petFactory.createFarmRabbit()
        );

        petRegistry.register(
                petFactory.createHorse()
        );

        petRegistry.register(
                petFactory.createSackOfPotatoes()
        );


        /*
         * =====================================================
         * CAVE PETS
         * =====================================================
         *
         * These pets spawn underground.
         */

        petRegistry.register(
                petFactory.createBat()
        );

        petRegistry.register(
                petFactory.createCaveSpider()
        );

        petRegistry.register(
                petFactory.createCreeper()
        );

        petRegistry.register(
                petFactory.createMiningDragon()
        );

        petRegistry.register(
                petFactory.createForestDragon()
        );

        petRegistry.register(
                petFactory.createZombie()
        );

        petRegistry.register(
                petFactory.createSkeleton()
        );


        /*
         * =====================================================
         * AQUATIC PETS
         * =====================================================
         *
         * These pets spawn in water.
         */

        petRegistry.register(
                petFactory.createSquid()
        );

        petRegistry.register(
                petFactory.createGlowSquid()
        );

        petRegistry.register(
                petFactory.createAxolotl()
        );

        petRegistry.register(
                petFactory.createGuardian()
        );

        petRegistry.register(
                petFactory.createDolphin()
        );

        petRegistry.register(
                petFactory.createCod()
        );

        petRegistry.register(
                petFactory.createSalmon()
        );

        petRegistry.register(
                petFactory.createPufferfish()
        );

        petRegistry.register(
                petFactory.createTropicalFish()
        );

        petRegistry.register(
                petFactory.createWaterDragon()
        );

        petRegistry.register(
                petFactory.createNatureDragon()
        );


        /*
         * =====================================================
         * NETHER PETS
         * =====================================================
         */

        petRegistry.register(
                petFactory.createWither()
        );

        petRegistry.register(
                petFactory.createFireDragon()
        );

        petRegistry.register(
                petFactory.createBlaze()
        );

        petRegistry.register(
                petFactory.createSlimeMinion()
        );

        petRegistry.register(
                petFactory.createGhast()
        );


        /*
         * =====================================================
         * MYTHIC / ULTRA-RARE PETS
         * =====================================================
         */

        petRegistry.register(
                petFactory.createAetherion()
        );

        petRegistry.register(
                petFactory.createHacker()
        );


        /*
         * =====================================================
         * SKY PETS
         * =====================================================
         *
         * Flying pets use PetSpawnType.SKY.
         */

        petRegistry.register(
                petFactory.createHawk()
        );

        petRegistry.register(
                petFactory.createBee()
        );

        petRegistry.register(
                petFactory.createPigeon()
        );

        petRegistry.register(
                petFactory.createLightningDragon()
        );


        /*
         * =====================================================
         * JUNGLE + SHORE
         * =====================================================
         */

        petRegistry.register(
                petFactory.createOcelot()
        );

        petRegistry.register(
                petFactory.createParrot()
        );

        petRegistry.register(
                petFactory.createTurtle()
        );

        petRegistry.register(
                petFactory.createPanda()
        );


        /*
         * =====================================================
         * MOUNTAINS
         * =====================================================
         */

        petRegistry.register(
                petFactory.createGoat()
        );

        petRegistry.register(
                petFactory.createLlama()
        );


        /*
         * =====================================================
         * SNOW
         * =====================================================
         */

        petRegistry.register(
                petFactory.createFox()
        );

        petRegistry.register(
                petFactory.createRabbit()
        );

        petRegistry.register(
                petFactory.createPolarBear()
        );

        petRegistry.register(
                petFactory.createYeti()
        );

        petRegistry.register(
                petFactory.createSnowflake()
        );

        petRegistry.register(
                petFactory.createIceDragon()
        );


        /*
         * =====================================================
         * DESERT
         * =====================================================
         */

        petRegistry.register(
                petFactory.createCamel()
        );

        petRegistry.register(
                petFactory.createArmadillo()
        );


        /*
         * =====================================================
         * DUNGEON PETS
         * =====================================================
         */

        petRegistry.register(
                petFactory.createDungeonZombie()
        );

        petRegistry.register(
                petFactory.createDungeonSkeleton()
        );

        petRegistry.register(
                petFactory.createDungeonDragon()
        );


        /*
         * =====================================================
         * ULTRA-RARE
         * =====================================================
         */

        petRegistry.register(
                petFactory.createAllay()
        );


        /*
         * =====================================================
         * SWAMP
         * =====================================================
         */

        petRegistry.register(
                petFactory.createFrog()
        );

        petRegistry.register(
                petFactory.createWitch()
        );

        petRegistry.register(
                petFactory.createMudling()
        );


        /*
         * =====================================================
         * FOREST
         * =====================================================
         */

        petRegistry.register(
                petFactory.createDeer()
        );

        petRegistry.register(
                petFactory.createSquirrel()
        );

        petRegistry.register(
                petFactory.createBoar()
        );

        petRegistry.register(
                petFactory.createOwl()
        );


        /*
         * =====================================================
         * FLOWER / MEADOW
         * =====================================================
         */

        petRegistry.register(
                petFactory.createSheep()
        );

        petRegistry.register(
                petFactory.createButterfly()
        );


        /*
         * =====================================================
         * MUSHROOM
         * =====================================================
         */

        petRegistry.register(
                petFactory.createMooshroom()
        );

        petRegistry.register(
                petFactory.createShroomling()
        );


        /*
         * =====================================================
         * VILLAGE
         * =====================================================
         */

        petRegistry.register(
                petFactory.createCat()
        );

        petRegistry.register(
                petFactory.createIronGolem()
        );


        /*
         * =====================================================
         * LUSH
         * =====================================================
         */

        petRegistry.register(
                petFactory.createSniffer()
        );

        petRegistry.register(
                petFactory.createMossSprite()
        );

        petRegistry.register(
                petFactory.createSwampHag()
        );

        petRegistry.register(
                petFactory.createForestSpirit()
        );

        petRegistry.register(
                petFactory.createBloomFairy()
        );

        petRegistry.register(
                petFactory.createSandWraith()
        );

        petRegistry.register(
                petFactory.createMycelord()
        );

        petRegistry.register(
                petFactory.createLushOracle()
        );


        /*
         * =====================================================
         * LOG REGISTERED PETS
         * =====================================================
         */

        for (PetDefinition definition :
                petRegistry.getAll()) {

            getLogger().info(
                    "Registered pet: "
                            + definition.getId()
            );
        }


        /*
         * =====================================================
         * COMMANDS
         * =====================================================
         */

        getCommand("pet").setExecutor(
                new PetTestCommand(
                        this
                )
        );

        getCommand("pets").setExecutor(
                (sender, command, label, args) -> {

                    if (!(sender instanceof Player player)) {

                        sender.sendMessage(
                                "Only players can use this command."
                        );

                        return true;
                    }

                    petMenu.open(
                            player
                    );

                    return true;
                }
        );

        getCommand("aetherlex").setExecutor(
                (sender, command, label, args) -> {

                    if (!(sender instanceof Player player)) {

                        sender.sendMessage(
                                "Only players can use this command."
                        );

                        return true;
                    }

                    petMenu.openAetherlex(
                            player
                    );

                    return true;
                }
        );

        de.aetherion.items.AetherionItems itemsPlugin =
                de.aetherion.items.AetherionItems.getInstance();

        if (itemsPlugin != null) {
            itemsPlugin.setPetMenuOpener(player -> petMenu.open(player));
        }
        getServer().getScheduler().runTask(this, this::hookItemManager);
        getServer().getScheduler().runTaskLater(this, this::hookItemManager, 20L);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new de.aetherion.aethermobs.placeholder.PetPlaceholderExpansion(this).register();
        }


        /*
         * =====================================================
         * REGISTER LISTENERS
         * =====================================================
         */

        getServer()
                .getPluginManager()
                .registerEvents(
                        this,
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        new PetCatchListener(
                                this
                        ),
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        new DragonAscensionListener(
                                this
                        ),
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        new FishingDragonListener(
                                this
                        ),
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        new FarmingDragonListener(
                                this
                        ),
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        new ForagingDragonListener(
                                this
                        ),
                        this
                );

        petExperienceListener =
                new PetExperienceListener(
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        petExperienceListener,
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        betaSphereManager,
                        this
                );

        new CatchSphereRecipes(betaSphereManager).register();

        getServer()
                .getPluginManager()
                .registerEvents(
                        new PetExpTreat(),
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        petMenu,
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        activePetManager,
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        petSkillManager,
                        this
                );

        petSkillManager.start();


        /*
         * =====================================================
         * START WILD PET SPAWNING
         * =====================================================
         */

        petSpawnManager.start();

        getServer().getScheduler().runTaskTimer(this, this::saveDirtyPets, 20L * 15, 20L * 15);


        /*
         * =====================================================
         * ENABLE MESSAGE
         * =====================================================
         */

        getLogger().info(
                "AetherMobs has been enabled!"
        );
    }


    @Override
    public void onDisable() {

        /*
         * =====================================================
         * REMOVE ACTIVE PETS
         * =====================================================
         */

        if (petSkillManager != null) {

            petSkillManager.stop();
        }

        try {
            if (petStatProvider != null) {
                Class<?> statsClass = Class.forName("de.aetherion.items.manager.ActiveEquipmentStats");
                Class<?> providerInterface = Class.forName("de.aetherion.items.manager.StatProvider");
                statsClass.getMethod("unregisterProvider", providerInterface).invoke(null, petStatProvider);
            }
        } catch (Throwable ignored) {
        }
        petStatProvider = null;

        try {
            RarityGlow.purgeAll();
        } catch (Throwable ignored) {
        }

        if (activePetManager != null) {

            activePetManager.removeAll();
        }


        /*
         * =====================================================
         * SAVE LOADED COLLECTIONS
         * =====================================================
         */

        if (petDataManager != null) {

            for (PlayerPetCollection collection :
                    playerPetCollections.values()) {

                petDataManager.save(
                        collection
                );
            }
        }

        dirtyPetPlayers.clear();
        playerPetCollections.clear();


        /*
         * =====================================================
         * STOP WILD PET SPAWNING
         * =====================================================
         */

        if (petSpawnManager != null) {

            petSpawnManager.stop();
        }


        /*
         * =====================================================
         * DISABLE MESSAGE
         * =====================================================
         */

        getLogger().info(
                "AetherMobs has been disabled!"
        );
        instance = null;
    }


    @EventHandler
    public void onPlayerJoin(
            PlayerJoinEvent event
    ) {

        Player player =
                event.getPlayer();

        UUID playerId =
                player.getUniqueId();

        PlayerPetCollection collection =
                petDataManager.load(
                        playerId
                );

        playerPetCollections.put(
                playerId,
                collection
        );

        /*
         * ActivePetManager handles restoring
         * the equipped pet after the collection
         * has been loaded.
         */
    }


    @EventHandler
    public void onPlayerQuit(
            PlayerQuitEvent event
    ) {

        Player player =
                event.getPlayer();

        UUID playerId =
                player.getUniqueId();


        /*
         * Remove active entity before
         * the player leaves the server.
         */

        if (activePetManager != null) {

            activePetManager.unequip(
                    player
            );
        }


        /*
         * Save player's collection.
         */

        PlayerPetCollection collection =
                playerPetCollections.remove(
                        playerId
                );

        if (collection != null) {

            dirtyPetPlayers.remove(playerId);
            petDataManager.save(
                    collection
            );
        }
    }


    /*
     * =====================================================
     * GETTERS
     * =====================================================
     */

    public PetRegistry getPetRegistry() {

        return petRegistry;
    }


    public PetGenerator getPetGenerator() {

        return petGenerator;
    }


    public PetSpawnManager getPetSpawnManager() {

        return petSpawnManager;
    }


    public CatchSphereRegistry getCatchSphereRegistry() {

        return catchSphereRegistry;
    }


    public BetaSphereManager getBetaSphereManager() {

        return betaSphereManager;
    }


    public PetDataManager getPetDataManager() {

        return petDataManager;
    }

    public void markPetsDirty(UUID playerId) {
        if (playerId != null) {
            dirtyPetPlayers.add(playerId);
        }
    }

    private void saveDirtyPets() {
        if (petDataManager == null || dirtyPetPlayers.isEmpty()) {
            return;
        }
        for (UUID playerId : List.copyOf(dirtyPetPlayers)) {
            dirtyPetPlayers.remove(playerId);
            PlayerPetCollection collection = playerPetCollections.get(playerId);
            if (collection != null) {
                petDataManager.save(collection);
            }
        }
    }


    public PetMenu getPetMenu() {

        return petMenu;
    }


    public ActivePetManager getActivePetManager() {

        return activePetManager;
    }


    public PetSkillManager getPetSkillManager() {

        return petSkillManager;
    }


    public void sharePetExperience(
            Player player,
            int experience
    ) {

        if (petExperienceListener != null) {

            petExperienceListener.shareExperience(
                    player,
                    experience
            );
        }
    }

    public void shareGatherExperience(
            Player player,
            int experience
    ) {

        if (petExperienceListener != null) {

            petExperienceListener.shareGatherExperience(
                    player,
                    experience
            );
        }
    }

    private void hookItemManager() {
        de.aetherion.items.AetherionItems itemsPlugin =
                de.aetherion.items.AetherionItems.getInstance();
        if (itemsPlugin == null || petMenu == null) {
            return;
        }
        itemsPlugin.setPetMenuOpener(player -> petMenu.open(player));
    }


    public PlayerPetCollection getPetCollection(
            UUID playerId
    ) {

        return playerPetCollections.computeIfAbsent(
                playerId,
                id -> petDataManager.load(
                        id
                )
        );
    }


    public PlayerPetCollection getPetCollection(
            Player player
    ) {

        return getPetCollection(
                player.getUniqueId()
        );
    }

    public double getBossDropMultiplier(
            Player player
    ) {

        if (player == null) {
            return 1.0;
        }

        PlayerPetCollection collection =
                getPetCollection(
                        player
                );

        if (collection == null) {
            return 1.0;
        }

        PetInstance equipped =
                collection.getEquippedPet();

        if (equipped == null) {
            return 1.0;
        }

        if (de.aetherion.aethermobs.pet.skill.PetSkill.fromPet(equipped)
                != de.aetherion.aethermobs.pet.skill.PetSkill.ALLAY_HOARD) {

            return 1.0;
        }

        return de.aetherion.aethermobs.pet.skill.PetSkill.bossDropMultiplier(
                equipped.getRarity(),
                equipped.getLevel()
        );
    }

    public void markPetSighted(
            Player player,
            String petId
    ) {

        if (player == null
                || petId == null
                || petId.isBlank()) {

            return;
        }

        PlayerPetCollection collection =
                getPetCollection(
                        player
                );

        if (collection == null) {
            return;
        }

        if (!collection.markSighted(petId)) {
            return;
        }

        if (petDataManager != null) {

            petDataManager.save(
                    collection
            );
        }
    }

    public static AetherMobs getInstance() {
        return instance;
    }

    /**
     * DEV helper: spawn a wild pet beside the player. Habitat rules are ignored.
     * Does not add it to the collection.
     */
    public boolean spawnDevPet(Player player, String petId) {
        PetDefinition definition = resolveDevDefinition(petId);
        if (player == null || definition == null || petGenerator == null) {
            return false;
        }
        PetInstance pet = petGenerator.generate(definition);
        Location spawnLocation = player.getLocation().clone().add(1.5, 0.5, 1.5);
        PetEntity entity = new PetEntity(pet);
        entity.spawn(spawnLocation, true);
        if (!entity.isSpawned()) {
            entity.spawn(player.getLocation().clone().add(0, 1.2, 0), true);
        }
        if (!entity.isSpawned()) {
            player.sendMessage("§cCould not spawn §f" + definition.getDisplayName() + "§c.");
            return false;
        }
        if (petSpawnManager != null) {
            petSpawnManager.registerTestPet(entity);
        }
        player.sendMessage("§d✦ §fSpawned §d" + definition.getDisplayName() + "§f.");
        return true;
    }

    /**
     * Spawn a real pet that follows a location like an equipped pet (bob, look, side hover).
     * Same visuals/nameplate as a player-equipped pet. Not catchable, not in any collection.
     */
    public PetEntity spawnCompanionPet(
            String petId,
            java.util.function.Supplier<Location> host
    ) {
        if (petId == null || host == null || petRegistry == null || petGenerator == null) {
            return null;
        }
        PetDefinition definition = petRegistry.get(petId);
        if (definition == null) {
            return null;
        }
        Location at = host.get();
        if (at == null || at.getWorld() == null) {
            return null;
        }

        PetInstance pet = petGenerator.generate(definition);
        PetEntity entity = new PetEntity(pet);
        entity.setCompanionHost(host);
        entity.spawn(at.clone().add(0.75, HOVER_OFFSET, 0.0), true);
        if (!entity.isSpawned()) {
            entity.spawn(at.clone().add(0.0, 1.2, 0.0), true);
        }
        if (!entity.isSpawned()) {
            return null;
        }
        try {
            entity.setCatchBeacon(false);
        } catch (Throwable ignored) {
        }
        return entity;
    }

    private static final double HOVER_OFFSET = 1.05;

    /**
     * DEV helper: add a generated pet to the player's collection. Does not spawn it.
     */
    public boolean giveDevPet(Player player, String petId) {
        return giveShopPet(player, petId, null);
    }

    /**
     * Shop / grant helper. Optional forced rarity (e.g. RARE / EPIC for Hacker).
     */
    public boolean giveShopPet(Player player, String petId, String rarityName) {
        PetDefinition definition = resolveDevDefinition(petId);
        if (player == null || definition == null || petGenerator == null) {
            return false;
        }
        de.aetherion.items.model.Rarity forced = null;
        if (rarityName != null && !rarityName.isBlank()) {
            try {
                forced = de.aetherion.items.model.Rarity.valueOf(
                        rarityName.trim().toUpperCase(java.util.Locale.ROOT)
                );
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            if (!definition.hasRarity(forced)) {
                return false;
            }
        }
        PlayerPetCollection collection = getPetCollection(player);
        PetInstance pet = petGenerator.generate(definition, null, forced);
        collection.addPet(pet);
        if (collection.claimFirstCatchXp(definition.getId())) {
            grantFirstCatchAetherionXp(player, 1);
        }
        if (petDataManager != null) {
            petDataManager.save(collection);
        }
        String rarityLabel = pet.getRarity() == null
                ? ""
                : " §7(" + pet.getRarity().name() + ")";
        player.sendMessage("§d✦ §fAdded §d" + definition.getDisplayName() + rarityLabel + " §fto your collection.");
        return true;
    }

    /**
     * DEV helper: ensure the player owns one of every registered pet
     * so Aetherlex shows fully caught. Also pays out any missing first-catch XP.
     */
    public int unlockAllPetsDev(Player player) {
        if (player == null || petRegistry == null || petGenerator == null) {
            return 0;
        }

        PlayerPetCollection collection = getPetCollection(player);
        if (collection == null) {
            return 0;
        }

        int added = 0;
        for (PetDefinition definition : petRegistry.getAll()) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            if (collection.hasCaught(definition.getId())) {
                collection.markSighted(definition.getId());
                continue;
            }
            PetInstance pet = petGenerator.generate(definition);
            if (pet == null) {
                continue;
            }
            collection.addPet(pet);
            added++;
        }

        int xpClaims = 0;
        for (PetDefinition definition : petRegistry.getAll()) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            if (!collection.hasCaught(definition.getId())) {
                continue;
            }
            if (collection.claimFirstCatchXp(definition.getId())) {
                xpClaims++;
            }
        }

        if (petDataManager != null) {
            petDataManager.save(collection);
        }

        if (xpClaims > 0) {
            grantFirstCatchAetherionXp(player, xpClaims);
        }

        return added;
    }

    /**
     * Same first-catch Aetherion XP as a real catch ({@code +90} per new species).
     */
    public void grantFirstCatchAetherionXp(Player player, int speciesCount) {
        if (player == null || speciesCount <= 0) {
            return;
        }
        try {
            var items = de.aetherion.items.AetherionItems.getInstance();
            if (items == null || items.getSkills() == null) {
                return;
            }
            long amount = 90L * (long) speciesCount;
            items.getSkills().addBonusXp(player, amount);
            if (speciesCount == 1) {
                player.sendMessage("§b+" + amount + " Aetherion XP §8· §7first catch");
            } else {
                player.sendMessage(
                        "§b+" + amount + " Aetherion XP §8· §7"
                                + speciesCount
                                + " Aetherlex entries"
                );
            }
        } catch (Throwable ignored) {
            // AetherionItems optional at compile/runtime edges
        }
    }

    private PetDefinition resolveDevDefinition(String petId) {
        if (petRegistry == null) {
            return null;
        }
        PetDefinition definition = petId == null || petId.isBlank()
                ? null
                : petRegistry.get(petId);
        if (definition != null) {
            return definition;
        }
        var all = petRegistry.getAll();
        if (all.isEmpty()) {
            return null;
        }
        return all.stream().skip((int) (Math.random() * all.size())).findFirst().orElse(null);
    }

    public ItemStack createDevCatchSphere(String id) {
        if (betaSphereManager == null) {
            return null;
        }
        return betaSphereManager.createCatchSphere(id);
    }
}