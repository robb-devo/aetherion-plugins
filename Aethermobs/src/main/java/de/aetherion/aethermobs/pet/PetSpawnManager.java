package de.aetherion.aethermobs.pet;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.core.AetherKeys;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PetSpawnManager {

    /*
     * =========================================================
     * WILD PET SPAWN SETTINGS
     * =========================================================
     */

    /** Global hard cap — each pet is ItemDisplay + TextDisplay. */
    private static final int MAX_PETS = 120;

    /** Hard local density — stops clumps even when nearby radius still has room. */
    private static final int MAX_PETS_PER_CHUNK = 4;

    private static final int SURFACE_NEARBY_CAP = 14;
    private static final int CAVE_NEARBY_CAP = 8;
    private static final int AQUATIC_NEARBY_CAP = 12;
    private static final int SKY_NEARBY_CAP = 6;
    /** Nether + Borderlands — keep combat packs readable, not a carpet. */
    private static final int NETHER_NEARBY_CAP = 4;
    private static final int DUNGEON_NEARBY_CAP = 2;

    /**
     * Shared farm island is all crops + clustered players — surface fills
     * instantly with the normal surface cap. Keep it sparse.
     */
    private static final int FARM_ISLAND_NEARBY_CAP = 5;
    private static final int FARM_ISLAND_WORLD_CAP = 12;
    private static final double FARM_ISLAND_SPAWN_CHANCE = 0.18;

    /** Borderlands is compact waste — throttle so packs spread instead of clump. */
    private static final double BORDERLANDS_SPAWN_CHANCE = 0.42;

    /** Density check radius — keep ≥ surface max spawn so far pets still count. */
    private static final double LOCAL_CAP_RADIUS = 72.0;

    private static final long INITIAL_DELAY = 40L;
    /** ~1.2s — slightly livelier fill without stacking many per tick. */
    private static final long SPAWN_INTERVAL = 24L;
    private static final int SPAWNS_PER_TICK = 1;

    private static final int SPAWN_ATTEMPTS = 28;
    private static final int LOCATION_ATTEMPTS = 20;

    /** How far habitat pets may seek a hard matching column. */
    private static final int HABITAT_SEEK_RADIUS = 36;

    /** No player within this range → despawn. Must stay above max spawn range. */
    private static final double DESPAWN_DISTANCE = 96.0;

    private static final double DUNGEON_SPAWN_CHANCE = 0.06;


    /*
     * =========================================================
     * AETHERION
     * =========================================================
     *
     * Rolled only when a surface spawn is attempted.
     */

    private static final double AETHERION_SPAWN_CHANCE = 0.00012;

    /** Ultra-rare sky mythic — never fill empty sky pools as a normal weight. */
    private static final double LIGHTNING_DRAGON_SPAWN_CHANCE = 0.00008;


    private final AetherMobs plugin;

    private final List<PetEntity> activePets;

    private BukkitRunnable spawnTask;


    public PetSpawnManager(
            AetherMobs plugin
    ) {

        this.plugin = plugin;

        this.activePets =
                new ArrayList<>();
    }


    /*
     * =========================================================
     * START
     * =========================================================
     */

    public void start() {

        if (spawnTask != null) {
            return;
        }


        spawnTask =
                new BukkitRunnable() {

                    @Override
                    public void run() {

                        cleanupPets();
                        enforceFarmIslandWorldCap();

                        if (
                                activePets.size()
                                        >= MAX_PETS
                        ) {

                            return;
                        }

                        if (
                                Bukkit.getOnlinePlayers()
                                        .isEmpty()
                        ) {

                            return;
                        }

                        Player player =
                                getRandomPlayer();

                        if (player == null) {
                            return;
                        }

                        for (
                                int spawn = 0;
                                spawn < SPAWNS_PER_TICK;
                                spawn++
                        ) {

                            if (
                                    activePets.size()
                                            >= MAX_PETS
                            ) {

                                break;
                            }

                            spawnPetNearPlayer(
                                    player
                            );
                        }
                    }
                };


        spawnTask.runTaskTimer(
                plugin,
                INITIAL_DELAY,
                SPAWN_INTERVAL
        );
    }


    /**
     * Drop wild pets that somehow entered a no-wild world from the tracker.
     * Never scans world entities — pet nameplates also carry PET_ENTITY and must not be deleted.
     */
    public void purgeTestArenaWildPets() {
        ActivePetManager active = plugin.getActivePetManager();
        activePets.removeIf(pet -> {
            if (pet == null || !pet.isSpawned() || pet.getEntity() == null) {
                return true;
            }
            if (!DungeonWorlds.blocksWildPets(pet.getEntity().getWorld())) {
                return false;
            }
            if (active != null) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (active.getActivePet(online) == pet) {
                        return false;
                    }
                }
            }
            if (pet.getOwner() != null) {
                return false;
            }
            pet.remove();
            return true;
        });
    }

    /*
     * =========================================================
     * STOP
     * =========================================================
     */

    public void stop() {

        if (spawnTask != null) {

            spawnTask.cancel();
            spawnTask = null;
        }


        for (PetEntity pet :
                activePets) {

            if (pet != null) {
                pet.remove();
            }
        }


        activePets.clear();
    }


    /*
     * =========================================================
     * SPAWN PET
     * =========================================================
     */

    private void spawnPetNearPlayer(
            Player player
    ) {

        if (player == null) {
            return;
        }


        World world =
                player.getWorld();

        if (DungeonWorlds.blocksWildPets(world)) {
            return;
        }

        if (isFarmIsland(world)) {
            if (countPetsInWorld(world) >= FARM_ISLAND_WORLD_CAP) {
                return;
            }
            if (ThreadLocalRandom.current().nextDouble()
                    > FARM_ISLAND_SPAWN_CHANCE) {
                return;
            }
        }

        if (PetBorderlands.contains(player.getLocation())
                && ThreadLocalRandom.current().nextDouble()
                > BORDERLANDS_SPAWN_CHANCE) {
            return;
        }

        if (DungeonWorlds.isDungeon(world)) {
            if (ThreadLocalRandom.current().nextDouble() > DUNGEON_SPAWN_CHANCE) {
                return;
            }
        }


        PetSpawnType spawnType =
                chooseSpawnType(
                        player
                );

        if (spawnType == null) {
            return;
        }


        PetDefinition definition =
                getRandomPet(
                        player,
                        spawnType
                );

        if (definition == null) {
            return;
        }


        Location location =
                findSpawnLocation(
                        player,
                        definition
                );


        /*
         * Stay inside the chosen habitat.
         * Do not fill the sky just because water or caves failed.
         */

        if (location == null) {

            for (
                    int attempt = 0;
                    attempt < 8;
                    attempt++
            ) {

                PetDefinition alternative =
                        getRandomPet(
                                player,
                                spawnType
                        );

                if (alternative == null) {
                    break;
                }

                if (
                        alternative.getId()
                                .equalsIgnoreCase(
                                        definition.getId()
                                )
                ) {
                    continue;
                }

                // Never backfill a failed common pick with an ultra-rare.
                if (definition.getSpawnWeight() > 0.05
                        && alternative.getSpawnWeight() <= 0.05) {
                    continue;
                }

                Location alternativeLocation =
                        findSpawnLocation(
                                player,
                                alternative
                        );

                if (alternativeLocation != null) {

                    definition =
                            alternative;

                    location =
                            alternativeLocation;

                    break;
                }
            }
        }


        if (location == null) {
            return;
        }


        /*
         * =====================================================
         * GENERATE PET INSTANCE
         * =====================================================
         */

        PetInstance instance =
                plugin.getPetGenerator()
                        .generate(
                                definition,
                                location
                        );

        if (instance == null) {
            return;
        }


        /*
         * =====================================================
         * CREATE ENTITY
         * =====================================================
         */

        PetEntity pet =
                new PetEntity(
                        instance
                );


        pet.spawn(
                location
        );


        /*
         * =====================================================
         * REGISTER ACTIVE PET
         * =====================================================
         */

        if (pet.isSpawned()) {

            activePets.add(
                    pet
            );
        }
    }

    public boolean spawnFishedPet(Player player, String petId) {
        if (player == null || DungeonWorlds.blocksWildPets(player.getWorld())) {
            return false;
        }
        if (player == null || petId == null || petId.isBlank()) {
            return false;
        }
        PetDefinition definition = plugin.getPetRegistry().get(petId);
        if (definition == null) {
            return false;
        }
        Location location = player.getLocation().clone().add(2.2d, 2.4d, 0.35d);
        PetInstance instance = plugin.getPetGenerator().generate(definition, location);
        if (instance == null) {
            return false;
        }
        PetEntity pet = new PetEntity(instance);
        pet.reserveCatcher(player.getUniqueId());
        pet.setGuaranteedCatch(true);
        pet.spawn(location, true);
        if (!pet.isSpawned()) {
            return false;
        }
        registerTestPet(pet);
        return true;
    }

    /*
     * =========================================================
     * REGISTER TEST PET
     * =========================================================
     *
     * Used by test commands to register manually spawned
     * pets in the same active registry as normal wild pets.
     *
     */

    public void registerTestPet(
            PetEntity pet
    ) {

        if (pet == null) {
            return;
        }

        if (!pet.isSpawned()) {
            return;
        }

        if (activePets.contains(pet)) {
            return;
        }


        activePets.add(
                pet
        );
    }


    /*
     * =========================================================
     * FIND SPAWN LOCATION
     * =========================================================
     */

    private Location findSpawnLocation(
            Player player,
            PetDefinition definition
    ) {

        if (
                player == null
                        || definition == null
        ) {

            return null;
        }


        Location playerLocation =
                player.getLocation();


        World world =
                playerLocation.getWorld();

        if (world == null) {
            return null;
        }


        PetSpawnType spawnType =
                getSpawnType(
                        definition
                );


        if (spawnType == PetSpawnType.SURFACE
                && definition.getHabitat()
                == PetHabitat.FARM) {

            Location farm =
                    "sack_of_potatoes".equals(
                            definition.getId()
                    )
                            ? PetCrops.findNearbyPotatoColumn(
                            playerLocation,
                            28
                    )
                            : PetCrops.findNearbyCropColumn(
                            playerLocation,
                            28
                    );

            if (farm != null
                    && isSpawnLocationClear(
                    farm,
                    spawnType
            )
                    && !PetSpawnZones.isBlocked(farm)) {

                return farm;
            }
        }


        /*
         * Habitat pets: only seek a hard matching column. Soft "near
         * enough" reach is gone — the pet has to land in its biotope.
         */
        PetHabitat habitat =
                definition.getHabitat();

        if (usesSurfaceHabitatRules(spawnType)
                && habitat != null
                && habitat != PetHabitat.ANY
                && habitat != PetHabitat.FARM
                && !isEldervaleAt(playerLocation)) {

            Location anchored =
                    habitat.findNearbyColumn(
                            playerLocation,
                            HABITAT_SEEK_RADIUS
                    );

            if (anchored != null) {

                Location habitatSpawn =
                        spawnType == PetSpawnType.SKY
                                ? findSkyLocation(anchored)
                                : anchored;

                if (habitatSpawn != null
                        && habitat.presentAt(habitatSpawn)
                        && isSpawnLocationClear(
                        habitatSpawn,
                        spawnType
                )
                        && !PetSpawnZones.isBlocked(habitatSpawn)) {

                    if (spawnType != PetSpawnType.SURFACE
                            || definition.getHabitat() == PetHabitat.MUSHROOM
                            || definition.getHabitat() == PetHabitat.LUSH
                            || PetWalkSurface.isDrySurfaceColumn(
                            habitatSpawn.getWorld(),
                            habitatSpawn.getBlockX(),
                            habitatSpawn.getBlockZ()
                    )) {

                        return habitatSpawn;
                    }
                }
            }

            // No hard column this pass — still try the ring below.
        }


        /*
         * =====================================================
         * TRY RANDOM POSITIONS
         * =====================================================
         */

        for (
                int attempt = 0;
                attempt < SPAWN_ATTEMPTS;
                attempt++
        ) {

            double angle =
                    ThreadLocalRandom.current()
                            .nextDouble(
                                    0.0,
                                    Math.PI * 2.0
                            );


            double distance =
                    ThreadLocalRandom.current()
                            .nextDouble(
                                    minSpawnDistance(
                                            definition,
                                            spawnType
                                    ),
                                    maxSpawnDistance(
                                            definition,
                                            spawnType
                                    )
                                            + 1.0
                            );


            double x =
                    playerLocation.getX()
                            + Math.cos(angle)
                            * distance;


            double z =
                    playerLocation.getZ()
                            + Math.sin(angle)
                            * distance;


            Location location =
                    new Location(
                            world,
                            x,
                            playerLocation.getY(),
                            z
                    );


            Location spawnLocation;


            switch (spawnType) {

                case CAVE:

                    spawnLocation =
                            findCaveLocation(
                                    location,
                                    definition
                            );

                    break;


                case AQUATIC:

                    spawnLocation =
                            findAquaticLocation(
                                    location,
                                    definition
                            );
                    if (spawnLocation == null) {
                        spawnLocation =
                                findNearbyAquaticLocation(
                                        location,
                                        definition,
                                        10
                                );
                    }

                    break;


                case SKY:

                    spawnLocation =
                            findSkyLocation(
                                    location
                            );

                    break;


                case NETHER:

                    // Real Nether: cave-style pockets. Borderlands (overworld):
                    // stand on the waste surface like other wild pets.
                    if (world.getEnvironment()
                            == World.Environment.NETHER) {

                        spawnLocation =
                                findNetherLocation(
                                        location
                                );
                    } else {

                        spawnLocation =
                                SurfacePetSpawner.findLocation(
                                        world,
                                        location
                                );
                    }

                    break;


                case DUNGEON:

                    spawnLocation =
                            findDungeonLocation(
                                    location
                            );

                    break;


                case SURFACE:
                default:

                    spawnLocation =
                            SurfacePetSpawner.findLocation(
                                    world,
                                    location
                            );

                    break;
            }


            if (spawnLocation == null) {
                continue;
            }


            // Hard habitat: column must match this pet. Avoid dominantAt here —
            // that scans every habitat and was melting TPS in the attempt loop.
            if (isEldervaleAt(spawnLocation)) {
                if (!allowedOnEldervale(definition)) {
                    continue;
                }
            } else if (usesSurfaceHabitatRules(spawnType)) {

                PetHabitat petHabitat =
                        definition.getHabitat();

                if (petHabitat != null
                        && petHabitat != PetHabitat.ANY) {

                    if (!petHabitat.presentAt(spawnLocation)) {
                        continue;
                    }
                } else if (PetHabitat.exclusiveAt(spawnLocation) != null) {
                    continue;
                }
            }


            if (
                    definition.getHabitat()
                            == PetHabitat.SHORE
                            && !PetHabitat.isShoreConfirmed(
                            spawnLocation
                    )
            ) {

                continue;
            }


            if (
                    definition.getHabitat()
                            == PetHabitat.DESERT
                            && !PetHabitat.isDesertConfirmed(
                            spawnLocation
                    )
            ) {

                continue;
            }


            if (
                    "sack_of_potatoes".equals(
                            definition.getId()
                    )
                            && !PetCrops.nearbyPotato(
                            spawnLocation,
                            10
                    )
            ) {

                continue;
            }


            if (
                    definition.getHabitat()
                            == PetHabitat.FARM
                            && !PetCrops.nearby(
                            spawnLocation,
                            10
                    )
            ) {

                continue;
            }


            if (
                    definition.getHabitat()
                            == PetHabitat.VILLAGE
                            && !PetVillages.nearby(
                            spawnLocation,
                            14
                    )
            ) {

                continue;
            }


            if (
                    getSpawnType(definition)
                            == PetSpawnType.SURFACE
                            && definition.getHabitat() != PetHabitat.MUSHROOM
                            && definition.getHabitat() != PetHabitat.LUSH
                            && spawnLocation.getWorld() != null
                            && !PetWalkSurface.isDrySurfaceColumn(
                            spawnLocation.getWorld(),
                            spawnLocation.getBlockX(),
                            spawnLocation.getBlockZ()
                    )
            ) {

                continue;
            }


            if (
                    !isSpawnLocationClear(
                            spawnLocation,
                            spawnType
                    )
            ) {

                continue;
            }

            if (PetSpawnZones.isBlocked(spawnLocation)) {
                continue;
            }

            // Borderlands = Nether-pet combat zone only.
            boolean inBorderlands =
                    PetBorderlands.contains(spawnLocation);

            PetSpawnType petType =
                    getSpawnType(definition);

            if (inBorderlands
                    && petType != PetSpawnType.NETHER) {
                continue;
            }

            if (petType == PetSpawnType.NETHER
                    && spawnLocation.getWorld() != null
                    && spawnLocation.getWorld().getEnvironment()
                    != World.Environment.NETHER
                    && !inBorderlands) {
                continue;
            }


            return spawnLocation;
        }


        return null;
    }


    /*
     * =========================================================
     * SURFACE LOCATION
     * =========================================================
     *
     * Surface pets must:
     *
     * - stand above solid ground
     * - not spawn inside water
     * - not spawn inside lava
     * - not spawn inside leaves
     * - have two blocks of free space
     */

    private Location findSurfaceLocation(
            Location location
    ) {

        World world =
                location.getWorld();

        if (world == null) {
            return null;
        }


        int x =
                location.getBlockX();

        int z =
                location.getBlockZ();


        int groundY =
                PetWalkSurface.groundY(
                        world,
                        x,
                        z
                );


        int feetY =
                groundY + 1;

        Block ground =
                world.getBlockAt(
                        x,
                        groundY,
                        z
                );

        Block feet =
                world.getBlockAt(
                        x,
                        groundY + 1,
                        z
                );

        Block head =
                world.getBlockAt(
                        x,
                        groundY + 2,
                        z
                );


        /*
         * Ground must actually be solid.
         */

        if (!ground.getType().isSolid()) {
            return null;
        }

        if (PetWalkSurface.isCanopy(ground.getType())) {
            return null;
        }


        /*
         * Never spawn surface pets in liquids.
         */

        if (isLiquid(
                ground
        )) {

            return null;
        }


        if (isLiquid(
                feet
        )) {

            return null;
        }


        if (isLiquid(
                head
        )) {

            return null;
        }


        /*
         * Feet and head need to be passable.
         *
         * isAir() was too strict here because blocks such
         * as grass and flowers are not air, but are still
         * perfectly valid free space for an ItemDisplay.
         */

        if (!feet.isPassable()) {
            return null;
        }


        if (!head.isPassable()) {
            return null;
        }


        /*
         * Reject single-column anomalies: window/hole in a
         * building, cliff edge, shoreline, etc.
         */

        if (!isTerrainStable(world, x, z, groundY, 2, 2)) {
            return null;
        }


        return new Location(
                world,
                x + 0.5,
                feetY,
                z + 0.5
        );
    }


    /*
     * =========================================================
     * DUNGEON LOCATION
     * =========================================================
     *
     * Stay on the dungeon floor under a ceiling.
     * Never spawn above the instance.
     */

    private Location findDungeonLocation(
            Location location
    ) {

        World world =
                location.getWorld();

        if (world == null) {
            return null;
        }

        int x =
                location.getBlockX();

        int z =
                location.getBlockZ();

        int originY =
                location.getBlockY();

        for (
                int y = originY + 2;
                y >= originY - 2;
                y--
        ) {

            Block floor =
                    world.getBlockAt(
                            x,
                            y - 1,
                            z
                    );

            Block feet =
                    world.getBlockAt(
                            x,
                            y,
                            z
                    );

            Block head =
                    world.getBlockAt(
                            x,
                            y + 1,
                            z
                    );

            if (!floor.getType().isSolid()
                    || floor.isLiquid()) {
                continue;
            }

            if (!isOpen(feet)
                    || !isOpen(head)) {
                continue;
            }

            boolean ceiling =
                    false;

            for (
                    int up = y + 2;
                    up <= y + 8;
                    up++
            ) {

                if (world.getBlockAt(x, up, z)
                        .getType()
                        .isSolid()) {
                    ceiling = true;
                    break;
                }
            }

            if (!ceiling) {
                continue;
            }

            return new Location(
                    world,
                    x + 0.5,
                    y + 1.0,
                    z + 0.5
            );
        }

        return null;
    }


    private boolean isOpen(
            Block block
    ) {

        if (block == null) {
            return false;
        }

        Material type =
                block.getType();

        return type.isAir()
                || type == Material.LIGHT
                || type == Material.CAVE_AIR
                || type == Material.VOID_AIR;
    }


    /*
     * =========================================================
     * CAVE LOCATION
     * =========================================================
     *
     * Cave pets must:
     *
     * - be underground
     * - have at least two free blocks
     * - be below the surface
     * - have a solid floor
     *
     */

    private Location findCaveLocation(
            Location location
    ) {
        return findCaveLocation(location, null);
    }

    private Location findCaveLocation(
            Location location,
            PetDefinition definition
    ) {

        World world =
                location.getWorld();

        if (world == null) {
            return null;
        }


        int x =
                location.getBlockX();

        int z =
                location.getBlockZ();


        int surfaceY =
                world.getHighestBlockYAt(
                        x,
                        z,
                        HeightMap.MOTION_BLOCKING_NO_LEAVES
                );


        int minY =
                world.getMinHeight() + 2;


        int maxY =
                Math.min(
                        surfaceY - 3,
                        world.getMaxHeight() - 3
                );


        if (maxY <= minY) {
            return null;
        }


        /*
         * Reject columns where the local surface height is a
         * single-column anomaly (cave entrance, mineshaft hole,
         * ravine). Otherwise "3 blocks below the surface" can
         * still land right at ground level of the surrounding
         * terrain.
         */

        if (!isTerrainStable(world, x, z, surfaceY, 2, 2)) {
            return null;
        }

        boolean preferOre = definition != null
                && ("mining_dragon".equalsIgnoreCase(definition.getId())
                || "poison_dragon".equalsIgnoreCase(definition.getId()));
        Location soft = null;

        /*
         * Try several random underground heights.
         */

        for (
                int attempt = 0;
                attempt < LOCATION_ATTEMPTS;
                attempt++
        ) {

            int y =
                    ThreadLocalRandom.current()
                            .nextInt(
                                    minY,
                                    maxY + 1
                            );


            /*
             * Must actually be underground.
             */

            if (y + 2 >= surfaceY) {
                continue;
            }


            Block floor =
                    world.getBlockAt(
                            x,
                            y - 1,
                            z
                    );


            Block feet =
                    world.getBlockAt(
                            x,
                            y,
                            z
                    );


            Block head =
                    world.getBlockAt(
                            x,
                            y + 1,
                            z
                    );


            /*
             * Need a real cave floor.
             */

            if (!floor.getType().isSolid()) {
                continue;
            }


            if (isLiquid(
                    floor
            )) {

                continue;
            }


            /*
             * Feet and head must be empty.
             */

            if (!feet.getType().isAir()) {
                continue;
            }


            if (!head.getType().isAir()) {
                continue;
            }


            /*
             * Do not allow cave pets to spawn in water.
             */

            if (isLiquid(
                    feet
            )) {

                continue;
            }


            if (isLiquid(
                    head
            )) {

                continue;
            }


            /*
             * Center the pet inside the free block.
             *
             * The block at Y=y is the pet's feet space.
             * Using y + 0.5 prevents the ItemDisplay from
             * sitting directly on a block boundary and
             * reduces clipping into the cave floor.
             */

            Location candidate = new Location(
                    world,
                    x + 0.5,
                    y + 1.0,
                    z + 0.5
            );
            if (preferOre) {
                if (hasNearbyOre(world, x, y, z, 5)) {
                    return candidate;
                }
                if (soft == null) {
                    soft = candidate;
                }
                continue;
            }
            return candidate;
        }

        return soft;
    }

    private static boolean hasNearbyOre(World world, int cx, int cy, int cz, int radius) {
        if (world == null) {
            return false;
        }
        int r = Math.max(1, radius);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (isOreBlock(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isOreBlock(Material type) {
        if (type == null || type.isAir()) {
            return false;
        }
        String name = type.name();
        return name.endsWith("_ORE")
                || name.equals("ANCIENT_DEBRIS")
                || name.equals("RAW_IRON_BLOCK")
                || name.equals("RAW_GOLD_BLOCK")
                || name.equals("RAW_COPPER_BLOCK")
                || name.equals("AMETHYST_BLOCK")
                || name.equals("BUDDING_AMETHYST");
    }


    private Location findNetherLocation(
            Location location
    ) {

        World world =
                location.getWorld();

        if (world == null) {
            return null;
        }

        int x =
                location.getBlockX();
        int z =
                location.getBlockZ();
        int startY =
                location.getBlockY();
        int minY =
                Math.max(
                        world.getMinHeight() + 2,
                        startY - 20
                );
        int maxY =
                Math.min(
                        world.getMaxHeight() - 4,
                        startY + 12
                );

        if (maxY <= minY) {
            return null;
        }

        for (
                int attempt = 0;
                attempt < LOCATION_ATTEMPTS;
                attempt++
        ) {

            int y =
                    ThreadLocalRandom.current()
                            .nextInt(
                                    minY,
                                    maxY + 1
                            );

            Block floor =
                    world.getBlockAt(x, y - 1, z);
            Block feet =
                    world.getBlockAt(x, y, z);
            Block head =
                    world.getBlockAt(x, y + 1, z);

            if (!floor.getType().isSolid()
                    || isLiquid(floor)) {
                continue;
            }

            if (!feet.getType().isAir()
                    || !head.getType().isAir()) {
                continue;
            }

            if (isLiquid(feet)
                    || isLiquid(head)) {
                continue;
            }

            return new Location(
                    world,
                    x + 0.5,
                    y + 1.0,
                    z + 0.5
            );
        }

        return null;
    }


    /*
     * =========================================================
     * AQUATIC LOCATION
     * =========================================================
     *
     * Aquatic pets spawn in water.
     *
     * Surface aquatic pets sit in the top water block.
     * Deep-sea pets spawn 10–20 blocks below that surface
     * inside a tall enough column.
     */

    private Location findAquaticLocation(
            Location location,
            PetDefinition definition
    ) {

        World world =
                location.getWorld();

        if (world == null) {
            return null;
        }


        int x =
                location.getBlockX();

        int z =
                location.getBlockZ();


        int highestY =
                Math.min(
                        world.getHighestBlockYAt(x, z),
                        world.getMaxHeight() - 2
                );


        int minY =
                world.getMinHeight();


        int surfaceY =
                -1;

        for (
                int y = highestY;
                y >= minY;
                y--
        ) {

            Block water =
                    world.getBlockAt(
                            x,
                            y,
                            z
                    );


            if (
                    !PetWalkSurface.isFullWaterBlock(water)
            ) {

                continue;
            }


            Block above =
                    world.getBlockAt(
                            x,
                            y + 1,
                            z
                    );


            if (
                    PetWalkSurface.isFullWaterBlock(above)
            ) {

                continue;
            }

            // Slab / waterlogged headspace — not a swim column.
            if (PetWalkSurface.isPartialWaterObstacle(above)
                    || PetWalkSurface.isPartialWaterObstacle(water)) {
                continue;
            }

            surfaceY =
                    y;

            break;
        }


        if (surfaceY < 0) {
            return null;
        }


        int minDepth =
                definition == null
                        ? 0
                        : definition.getMinWaterDepth();

        int maxDepth =
                definition == null
                        ? 0
                        : definition.getMaxWaterDepth();


        if (minDepth <= 0) {

            return new Location(
                    world,
                    x + 0.5,
                    surfaceY + 0.5,
                    z + 0.5
            );
        }


        int columnDepth =
                0;

        for (
                int y = surfaceY;
                y >= minY;
                y--
        ) {

            if (
                    !PetWalkSurface.isFullWaterBlock(
                            world.getBlockAt(
                                    x,
                                    y,
                                    z
                            )
                    )
            ) {

                break;
            }

            columnDepth++;
        }


        if (columnDepth < minDepth) {
            // Shallow ponds / 1-deep streams: still allow a surface swim spot
            // instead of skipping the column entirely.
            if (columnDepth >= 1) {
                return new Location(
                        world,
                        x + 0.5,
                        surfaceY + 0.5,
                        z + 0.5
                );
            }
            return null;
        }


        int deepest =
                Math.min(
                        maxDepth <= 0
                                ? columnDepth - 1
                                : maxDepth,
                        columnDepth - 1
                );

        // 1–2 deep columns: park at the surface water block.
        if (columnDepth <= 2 || deepest < 1) {
            return new Location(
                    world,
                    x + 0.5,
                    surfaceY + 0.5,
                    z + 0.5
            );
        }

        int spawnDepth =
                deepest < minDepth
                        ? Math.max(1, Math.min(deepest, columnDepth - 1))
                        : ThreadLocalRandom.current()
                        .nextInt(
                                Math.min(minDepth, deepest),
                                deepest + 1
                        );


        int spawnY =
                surfaceY
                        - spawnDepth;


        if (
                !PetWalkSurface.isFullWaterBlock(
                        world.getBlockAt(
                                x,
                                spawnY,
                                z
                        )
                )
        ) {

            return new Location(
                    world,
                    x + 0.5,
                    surfaceY + 0.5,
                    z + 0.5
            );
        }


        return new Location(
                world,
                x + 0.5,
                spawnY + 0.5,
                z + 0.5
        );
    }

    /**
     * When a random ring sample misses water, scan a small disk for a
     * usable water column (including 1-deep streams / ponds).
     */
    private Location findNearbyAquaticLocation(
            Location origin,
            PetDefinition definition,
            int radius
    ) {
        if (origin == null || origin.getWorld() == null || radius <= 0) {
            return null;
        }
        World world = origin.getWorld();
        int ox = origin.getBlockX();
        int oz = origin.getBlockZ();
        List<int[]> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                cells.add(new int[]{ox + dx, oz + dz});
            }
        }
        Collections.shuffle(cells, ThreadLocalRandom.current());
        int limit = Math.min(cells.size(), 48);
        for (int i = 0; i < limit; i++) {
            int[] cell = cells.get(i);
            Location candidate =
                    findAquaticLocation(
                            new Location(world, cell[0] + 0.5, origin.getY(), cell[1] + 0.5),
                            definition
                    );
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }


    /*
     * =========================================================
     * SKY LOCATION
     * =========================================================
     *
     * Sky pets hover in open air above the terrain.
     */

    private Location findSkyLocation(
            Location location
    ) {

        World world =
                location.getWorld();

        if (world == null) {
            return null;
        }

        int x =
                location.getBlockX();

        int z =
                location.getBlockZ();

        int groundY =
                world.getHighestBlockYAt(
                        x,
                        z,
                        HeightMap.MOTION_BLOCKING_NO_LEAVES
                );

        int minAirY =
                groundY + 11;

        int maxAirY =
                Math.min(
                        groundY + 16,
                        world.getMaxHeight() - 3
                );

        if (minAirY >= maxAirY) {
            return null;
        }

        int y =
                ThreadLocalRandom.current()
                        .nextInt(
                                minAirY,
                                maxAirY + 1
                        );

        Block feet =
                world.getBlockAt(
                        x,
                        y,
                        z
                );

        Block head =
                world.getBlockAt(
                        x,
                        y + 1,
                        z
                );

        if (!feet.isPassable()
                || !head.isPassable()
                || isLiquid(feet)
                || isLiquid(head)) {

            return null;
        }

        for (int checkY = groundY + 1;
             checkY <= y;
             checkY++) {

            Block open =
                    world.getBlockAt(
                            x,
                            checkY,
                            z
                    );

            if (!open.isPassable()
                    || isLiquid(open)) {

                return null;
            }
        }

        return new Location(
                world,
                x + 0.5,
                y,
                z + 0.5
        );
    }


    /*
     * =========================================================
     * SPAWN TYPE
     * =========================================================
     *
     * The definition is the primary source.
     *
     * The ID fallback exists so the system remains correct
     * even if an older PetFactory definition still defaults
     * to SURFACE.
     */

    private PetSpawnType getSpawnType(
            PetDefinition definition
    ) {

        if (definition == null) {
            return PetSpawnType.SURFACE;
        }

        // Factory config is the single source of truth — never override
        // SURFACE→SKY by pet id (that hid bees in the sky pool).
        return definition.getSpawnType();
    }


    /*
     * =========================================================
     * AVAILABLE PETS
     * =========================================================
     */

    private List<PetDefinition> getAvailablePets(
            World world
    ) {

        return getAvailablePets(
                world,
                null
        );
    }


    private List<PetDefinition> getAvailablePets(
            World world,
            PetSpawnType spawnType
    ) {

        Collection<PetDefinition> definitions =
                plugin.getPetRegistry()
                        .getAll();


        List<PetDefinition> available =
                new ArrayList<>();


        for (PetDefinition definition :
                definitions) {

            if (definition == null) {
                continue;
            }


            String id =
                    definition.getId();


            if (
                    id.equalsIgnoreCase(
                            "aetherion"
                    )
                            || id.equalsIgnoreCase(
                            "lightning_dragon"
                    )
            ) {

                continue;
            }


            if (
                    spawnType != null
                            && getSpawnType(definition)
                            != spawnType
            ) {

                continue;
            }

            PetSpawnType type =
                    getSpawnType(definition);

            if (world.getEnvironment() == World.Environment.NETHER) {
                if (type != PetSpawnType.NETHER) {
                    continue;
                }
            } else if (type == PetSpawnType.NETHER) {
                // Overworld: only when the spawn roll is explicitly NETHER
                // (Borderlands stand-in for Nether pets).
                if (spawnType != PetSpawnType.NETHER) {
                    continue;
                }
            }


            if (
                    definition.getSpawnWeight()
                            <= 0.0
            ) {

                continue;
            }


            available.add(
                    definition
            );
        }


        return available;
    }


    /*
     * =========================================================
     * RANDOM PET
     * =========================================================
     */

    private PetDefinition getRandomPet(
            Player player,
            PetSpawnType spawnType
    ) {

        World world =
                player.getWorld();

        if (
                spawnType == PetSpawnType.SURFACE
                        && world.getEnvironment()
                        == World.Environment.NORMAL
        ) {

            PetDefinition aetherion =
                    plugin.getPetRegistry()
                            .get(
                                    "aetherion"
                            );

            if (
                    aetherion != null
                            && ThreadLocalRandom.current()
                            .nextDouble()
                            < AETHERION_SPAWN_CHANCE
            ) {

                return aetherion;
            }
        }

        if (spawnType == PetSpawnType.SKY) {

            PetDefinition lightning =
                    plugin.getPetRegistry()
                            .get(
                                    "lightning_dragon"
                            );

            if (
                    lightning != null
                            && ThreadLocalRandom.current()
                            .nextDouble()
                            < LIGHTNING_DRAGON_SPAWN_CHANCE
            ) {

                return lightning;
            }
        }


        List<PetDefinition> available =
                getAvailablePets(
                        world,
                        spawnType
                );


        if (available.isEmpty()) {
            return null;
        }

        org.bukkit.Location playerLocation =
                player.getLocation();

        PetHabitat dominant =
                usesSurfaceHabitatRules(spawnType)
                        ? PetHabitat.nearestDominant(
                        playerLocation,
                        HABITAT_SEEK_RADIUS
                )
                        : null;

        double totalWeight =
                0.0;

        for (PetDefinition definition :
                available) {

            totalWeight +=
                    habitatWeight(
                            definition,
                            playerLocation,
                            spawnType,
                            dominant
                    );
        }

        if (totalWeight <= 0.0) {
            return null;
        }

        double roll =
                ThreadLocalRandom.current()
                        .nextDouble()
                        * totalWeight;

        double current =
                0.0;

        for (PetDefinition definition :
                available) {

            current +=
                    habitatWeight(
                            definition,
                            playerLocation,
                            spawnType,
                            dominant
                    );

            if (roll <= current) {
                return definition;
            }
        }

        return available.get(
                available.size()
                        - 1
        );
    }

    /**
     * Snow/desert exclusivity and surface habitat boosts apply to
     * normal overworld surface (+ sky). Cave/aquatic/nether/dungeon
     * keep their own spawn-type rules.
     */
    private static boolean usesSurfaceHabitatRules(
            PetSpawnType spawnType
    ) {

        return spawnType == PetSpawnType.SURFACE
                || spawnType == PetSpawnType.SKY;
    }

    /** Painted Eldervale disk from AetherionItems pet-habitats.yml. */
    private static boolean isEldervaleAt(Location location) {
        return PetHabitatZones.habitatAt(location) == PetHabitat.ELDERVALE;
    }

    /**
     * Eldervale pool: cave + sky + aquatic, plus mining-flavoured surface pets.
     */
    private static boolean allowedOnEldervale(PetDefinition definition) {
        if (definition == null) {
            return false;
        }
        PetSpawnType type = definition.getSpawnType();
        if (type == PetSpawnType.CAVE
                || type == PetSpawnType.SKY
                || type == PetSpawnType.AQUATIC) {
            return true;
        }
        if (type == PetSpawnType.SURFACE) {
            return definition.getCoreStat() == ItemCapability.MINING_POWER
                    || definition.getHabitat() == PetHabitat.ELDERVALE;
        }
        return false;
    }

    private double habitatWeight(
            PetDefinition definition,
            Location location,
            PetSpawnType spawnType,
            PetHabitat dominant
    ) {

        double weight =
                definition.getSpawnWeight();

        if (weight <= 0.0) {
            return 0.0;
        }

        PetHabitat habitat =
                definition.getHabitat();

        boolean surfaceRules =
                usesSurfaceHabitatRules(spawnType);

        if (isEldervaleAt(location) || dominant == PetHabitat.ELDERVALE) {
            if (!allowedOnEldervale(definition)) {
                return 0.0;
            }
            double mul = spawnType == PetSpawnType.SURFACE ? 2.5 : 3.5;
            return applyDiversity(weight * mul, definition, location);
        }

        // Underground / water / dimension pets: plain spawn weights,
        // but optional habitats still need a hard local match.
        if (!surfaceRules) {

            if (habitat == PetHabitat.ANY) {
                return applyDiversity(weight, definition, location);
            }

            if (!habitat.presentAt(location)
                    && (dominant == null || habitat != dominant)) {
                return 0.0;
            }

            if (weight <= 0.05) {
                return applyDiversity(weight, definition, location);
            }

            return applyDiversity(weight * 4.0, definition, location);
        }

        if ("sack_of_potatoes".equals(definition.getId())) {

            if (location == null
                    || !PetCrops.nearbyPotato(
                    location,
                    16
            )) {

                return 0.0;
            }

            return applyDiversity(weight * 6.0, definition, location);
        }

        // Soft biotope mix — never hard-lock to one species.
        if (dominant != null) {

            boolean exclusive =
                    dominant == PetHabitat.SNOW
                            || dominant == PetHabitat.DESERT;

            int poolSize =
                    countSurfacePool(dominant);

            double anyMul =
                    poolSize < 3
                            ? 0.75
                            : (exclusive ? 0.28 : 0.55);

            double foreignMul =
                    exclusive
                            ? 0.10
                            : (poolSize < 3 ? 0.32 : 0.22);

            if (habitat == PetHabitat.ANY) {
                return applyDiversity(weight * anyMul, definition, location);
            }

            if (habitat == dominant) {
                if (weight <= 0.05) {
                    return applyDiversity(weight, definition, location);
                }
                return applyDiversity(weight * 5.0, definition, location);
            }

            // Only mix in other biotopes that actually match underfoot
            // (overlapping paint/blocks) — never teleport desert pets into lush.
            if (habitat.presentAt(location)) {
                return applyDiversity(weight * foreignMul, definition, location);
            }

            return 0.0;
        }

        // Open land / paths: keep the world alive with wanderers.
        if (habitat == PetHabitat.ANY) {
            return applyDiversity(weight * 1.15, definition, location);
        }

        if (habitat == PetHabitat.FARM
                || habitat == PetHabitat.FOREST
                || habitat == PetHabitat.SHORE
                || habitat == PetHabitat.VILLAGE) {

            return applyDiversity(weight * 0.75, definition, location);
        }

        // Other biotopes still try — seek places them into their patch.
        if (weight <= 0.05) {
            return applyDiversity(weight * 0.35, definition, location);
        }

        return applyDiversity(weight * 0.45, definition, location);
    }

    private int countSurfacePool(PetHabitat habitat) {
        if (habitat == null || habitat == PetHabitat.ANY) {
            return 0;
        }
        int count = 0;
        for (PetDefinition definition : plugin.getPetRegistry().getAll()) {
            if (definition == null
                    || definition.getSpawnWeight() <= 0.0) {
                continue;
            }
            if (definition.getHabitat() != habitat) {
                continue;
            }
            PetSpawnType type = getSpawnType(definition);
            if (type == PetSpawnType.SURFACE || type == PetSpawnType.SKY) {
                count++;
            }
        }
        return count;
    }

    /**
     * Cut repeat species so a zone never fills with one pet id.
     */
    private double applyDiversity(
            double weight,
            PetDefinition definition,
            Location location
    ) {
        if (weight <= 0.0 || definition == null || location == null) {
            return weight;
        }
        int same = countNearbySamePet(definition.getId(), location);
        if (same <= 0) {
            return weight;
        }
        if (same == 1) {
            return weight * 0.70;
        }
        if (same == 2) {
            return weight * 0.40;
        }
        if (same == 3) {
            return weight * 0.18;
        }
        return weight * 0.06;
    }

    private int countNearbySamePet(String petId, Location location) {
        if (petId == null || location == null || location.getWorld() == null) {
            return 0;
        }
        double radiusSquared = LOCAL_CAP_RADIUS * LOCAL_CAP_RADIUS;
        int count = 0;
        for (PetEntity pet : activePets) {
            if (pet == null || !pet.isSpawned() || pet.getEntity() == null) {
                continue;
            }
            if (!pet.getEntity().getWorld().equals(location.getWorld())) {
                continue;
            }
            PetInstance instance = pet.getPetInstance();
            if (instance == null
                    || instance.getDefinition() == null
                    || !petId.equalsIgnoreCase(instance.getDefinition().getId())) {
                continue;
            }
            if (pet.getEntity().getLocation().distanceSquared(location) <= radiusSquared) {
                count++;
            }
        }
        return count;
    }


    private boolean isShoreSpot(
            Location location
    ) {

        World world =
                location.getWorld();

        if (world == null) {
            return false;
        }

        int originX =
                location.getBlockX();

        int originZ =
                location.getBlockZ();

        int originY =
                world.getHighestBlockYAt(
                        originX,
                        originZ,
                        HeightMap.MOTION_BLOCKING
                );

        boolean sand =
                false;

        boolean water =
                false;

        for (int dx = -6; dx <= 6; dx++) {

            for (int dz = -6; dz <= 6; dz++) {

                for (int dy = -3; dy <= 2; dy++) {

                    Material type =
                            world.getBlockAt(
                                    originX + dx,
                                    originY + dy,
                                    originZ + dz
                            )
                                    .getType();

                    if (type == Material.SAND
                            || type == Material.RED_SAND
                            || type == Material.SUSPICIOUS_SAND) {

                        sand = true;
                    }

                    if (type == Material.WATER
                            || type == Material.BUBBLE_COLUMN) {

                        water = true;
                    }

                    if (sand && water) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    /*
     * =========================================================
     * SPAWN DISTANCE CHECK
     * =========================================================
     */

    private boolean isSpawnLocationClear(
            Location location,
            PetSpawnType spawnType
    ) {

        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (countPetsInChunk(location) >= MAX_PETS_PER_CHUNK) {
            return false;
        }

        double minimumDistance =
                minPetDistance(
                        spawnType
                );

        if (isFarmIsland(location.getWorld())) {
            minimumDistance = Math.max(minimumDistance, 18.0);
        }

        if (spawnType == PetSpawnType.NETHER
                && PetBorderlands.contains(location)) {
            // Spread Borderlands packs a bit wider than real Nether pockets.
            minimumDistance = Math.max(minimumDistance, 18.0);
        }

        double minimumDistanceSquared =
                minimumDistance
                        * minimumDistance;


        for (PetEntity pet :
                activePets) {

            if (
                    pet == null
                            || !pet.isSpawned()
            ) {

                continue;
            }


            if (
                    !pet.getEntity()
                            .getWorld()
                            .equals(
                                    location.getWorld()
                            )
            ) {

                continue;
            }


            if (
                    pet.getEntity()
                            .getLocation()
                            .distanceSquared(
                                    location
                            )
                            < minimumDistanceSquared
            ) {

                return false;
            }
        }


        return true;
    }

    private int countPetsInChunk(Location location) {
        if (location == null || location.getWorld() == null) {
            return 0;
        }

        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        int count = 0;

        for (PetEntity pet : activePets) {
            if (pet == null || !pet.isSpawned() || pet.getEntity() == null) {
                continue;
            }
            Location petLoc = pet.getEntity().getLocation();
            if (petLoc.getWorld() == null || !world.equals(petLoc.getWorld())) {
                continue;
            }
            if ((petLoc.getBlockX() >> 4) == chunkX
                    && (petLoc.getBlockZ() >> 4) == chunkZ) {
                count++;
            }
        }
        return count;
    }


    private PetSpawnType chooseSpawnType(
            Player player
    ) {

        World world =
                player.getWorld();

        if (world == null) {
            return null;
        }

        if (DungeonWorlds.isDungeon(world)) {
            if (countNearbyPets(player, PetSpawnType.DUNGEON) >= DUNGEON_NEARBY_CAP) {
                return null;
            }
            return PetSpawnType.DUNGEON;
        }

        boolean nether =
                world.getEnvironment()
                        == World.Environment.NETHER;

        boolean borderlands =
                !nether
                        && PetBorderlands.contains(
                        player.getLocation()
                );

        // Borderlands = overworld Nether pet zone (no real Nether on this map).
        if (nether || borderlands) {
            if (countNearbyPets(player, PetSpawnType.NETHER) >= NETHER_NEARBY_CAP) {
                return null;
            }
            return PetSpawnType.NETHER;
        }

        boolean underground =
                isUnderground(
                        player
                );

        boolean watery =
                isInOrNearWater(
                        player
                );

        Location here =
                player.getLocation();

        PetHabitat dominantHere =
                PetHabitat.nearestDominant(here, HABITAT_SEEK_RADIUS);

        // Covered mushroom / lush groves are still floor biotopes, not mines.
        boolean groveFloor =
                dominantHere == PetHabitat.MUSHROOM
                        || dominantHere == PetHabitat.LUSH
                        || PetHabitat.MUSHROOM.presentAt(here)
                        || PetHabitat.LUSH.presentAt(here);

        if (underground && groveFloor) {
            underground = false;
        }

        double surfaceWeight = 48.0;
        double caveWeight = 18.0;
        double aquaticWeight = 24.0;
        double skyWeight = 5.0;

        // Habitat bias is surface/sky only — underground uses its own mix below.
        if (!underground) {

            PetHabitat dominant = dominantHere;

            if (dominant == PetHabitat.SNOW
                    || dominant == PetHabitat.DESERT) {
                surfaceWeight = 78.0;
                skyWeight = 2.0;
                caveWeight = 6.0;
                aquaticWeight = watery ? 18.0 : 2.0;
            } else if (dominant == PetHabitat.FLOWER) {
                // Bees/butterflies are surface flower pets — sky stays low.
                surfaceWeight = 70.0;
                skyWeight = 4.0;
            } else if (dominant == PetHabitat.FOREST) {
                // Owl = forest sky; surface pack is deer/fox/wolf/…
                surfaceWeight = 52.0;
                skyWeight = 32.0;
            } else if (dominant == PetHabitat.DARK) {
                // Allay is surface dark — no sky pets here.
                surfaceWeight = 72.0;
                skyWeight = 3.0;
            } else if (dominant == PetHabitat.JUNGLE) {
                // Parrot lives in jungle sky.
                surfaceWeight = 50.0;
                skyWeight = 34.0;
            } else if (dominant == PetHabitat.VILLAGE) {
                // Pigeon lives over village.
                surfaceWeight = 48.0;
                skyWeight = 36.0;
            } else if (dominant == PetHabitat.MOUNTAIN) {
                // Hawk soars mountains.
                surfaceWeight = 48.0;
                skyWeight = 36.0;
            } else if (dominant == PetHabitat.SHORE
                    || dominant == PetHabitat.SWAMP) {
                surfaceWeight = 48.0;
                skyWeight = 6.0;
                aquaticWeight = 38.0;
            } else if (dominant == PetHabitat.ELDERVALE) {
                // Mining island — cave / sky / aquatic; few surface pets.
                surfaceWeight = 12.0;
                caveWeight = 42.0;
                skyWeight = 28.0;
                aquaticWeight = watery ? 30.0 : 18.0;
            } else if (dominant == PetHabitat.LUSH
                    || dominant == PetHabitat.MUSHROOM) {
                surfaceWeight = 78.0;
                caveWeight = 8.0;
                skyWeight = 2.0;
            } else if (dominant == PetHabitat.FARM) {
                // Crop fields + farm island fill very easily — keep surface
                // dominant but leave a little room for sky / wander mix.
                surfaceWeight = 58.0;
                skyWeight = 6.0;
                caveWeight = 4.0;
                aquaticWeight = watery ? 14.0 : 2.0;
            }
        }

        if (underground) {

            surfaceWeight = 8.0;
            caveWeight = 68.0;
            aquaticWeight = watery ? 16.0 : 4.0;
            skyWeight = 4.0;

        } else if (watery) {

            surfaceWeight = 16.0;
            caveWeight = 6.0;
            aquaticWeight = 68.0;
            skyWeight = 10.0;
        }

        int surfaceCap =
                isFarmIsland(world)
                        ? FARM_ISLAND_NEARBY_CAP
                        : SURFACE_NEARBY_CAP;

        if (
                countNearbyPets(
                        player,
                        PetSpawnType.SURFACE
                )
                        >= surfaceCap
        ) {
            surfaceWeight = 0.0;
        }

        if (
                countNearbyPets(
                        player,
                        PetSpawnType.CAVE
                )
                        >= CAVE_NEARBY_CAP
        ) {
            caveWeight = 0.0;
        }

        if (
                countNearbyPets(
                        player,
                        PetSpawnType.AQUATIC
                )
                        >= AQUATIC_NEARBY_CAP
        ) {
            aquaticWeight = 0.0;
        }

        if (
                countNearbyPets(
                        player,
                        PetSpawnType.SKY
                )
                        >= SKY_NEARBY_CAP
        ) {
            skyWeight = 0.0;
        }

        double total =
                surfaceWeight
                        + caveWeight
                        + aquaticWeight
                        + skyWeight;

        if (total <= 0.0) {
            return null;
        }

        double roll =
                ThreadLocalRandom.current()
                        .nextDouble()
                        * total;

        if (roll < surfaceWeight) {
            return PetSpawnType.SURFACE;
        }

        roll -=
                surfaceWeight;

        if (roll < caveWeight) {
            return PetSpawnType.CAVE;
        }

        roll -=
                caveWeight;

        if (roll < aquaticWeight) {
            return PetSpawnType.AQUATIC;
        }

        return PetSpawnType.SKY;
    }


    private int countNearbyPets(
            Player player,
            PetSpawnType spawnType
    ) {

        double radiusSquared =
                LOCAL_CAP_RADIUS
                        * LOCAL_CAP_RADIUS;

        int count = 0;

        for (PetEntity pet :
                activePets) {

            if (
                    pet == null
                            || !pet.isSpawned()
            ) {
                continue;
            }

            if (
                    !pet.getEntity()
                            .getWorld()
                            .equals(
                                    player.getWorld()
                            )
            ) {
                continue;
            }

            if (
                    getSpawnType(
                            pet.getPetInstance()
                                    .getDefinition()
                    )
                            != spawnType
            ) {
                continue;
            }

            if (
                    pet.getEntity()
                            .getLocation()
                            .distanceSquared(
                                    player.getLocation()
                            )
                            <= radiusSquared
            ) {

                count++;
            }
        }

        return count;
    }


    private boolean isUnderground(
            Player player
    ) {

        Location location =
                player.getLocation();

        World world =
                location.getWorld();

        if (world == null) {
            return false;
        }

        int surfaceY =
                world.getHighestBlockYAt(
                        location.getBlockX(),
                        location.getBlockZ(),
                        HeightMap.MOTION_BLOCKING_NO_LEAVES
                );

        return location.getBlockY()
                + 5
                < surfaceY;
    }


    private boolean isInOrNearWater(
            Player player
    ) {

        if (player.isInWater()) {
            return true;
        }

        Location location =
                player.getLocation();

        World world =
                location.getWorld();

        if (world == null) {
            return false;
        }

        int x =
                location.getBlockX();

        int y =
                location.getBlockY();

        int z =
                location.getBlockZ();

        // Include one-below (shore beside a 1-deep stream) and one-above.
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    Block block =
                            world.getBlockAt(
                                    x + dx,
                                    y + dy,
                                    z + dz
                            );
                    if (block.getType() == Material.WATER) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    private double minSpawnDistance(
            PetDefinition definition,
            PetSpawnType spawnType
    ) {

        boolean habitatPet =
                isHabitatPet(
                        definition
                );

        return switch (spawnType) {

            case SKY ->
                    habitatPet ? 20.0 : 28.0;

            case NETHER ->
                    12.0;

            case DUNGEON ->
                    5.0;

            case CAVE ->
                    14.0;

            case AQUATIC ->
                    16.0;

            // Keep a clear "explore ahead" ring — no pop-in underfoot.
            default ->
                    habitatPet ? 16.0 : 20.0;
        };
    }


    private double maxSpawnDistance(
            PetDefinition definition,
            PetSpawnType spawnType
    ) {

        boolean habitatPet =
                isHabitatPet(
                        definition
                );

        return switch (spawnType) {

            case SKY ->
                    habitatPet ? 64.0 : 72.0;

            case NETHER ->
                    44.0;

            case DUNGEON ->
                    18.0;

            case CAVE ->
                    52.0;

            case AQUATIC ->
                    56.0;

            default ->
                    habitatPet ? 56.0 : 72.0;
        };
    }

    private boolean isHabitatPet(
            PetDefinition definition
    ) {

        return definition != null
                && definition.getHabitat()
                != PetHabitat.ANY;
    }


    private double minPetDistance(
            PetSpawnType spawnType
    ) {

        return switch (spawnType) {

            case SKY ->
                    34.0;

            case NETHER ->
                    16.0;

            case DUNGEON ->
                    10.0;

            case CAVE ->
                    11.0;

            case AQUATIC ->
                    12.0;

            default ->
                    14.0;
        };
    }


    /*
     * =========================================================
     * LIQUID CHECK
     * =========================================================
     */

    private boolean isLiquid(
            Block block
    ) {

        if (block == null) {
            return false;
        }


        return block.isLiquid();
    }


    /*
     * =========================================================
     * TERRAIN STABILITY CHECK
     * =========================================================
     *
     * Prevents pets from spawning on single-column anomalies:
     * windows/holes in buildings, cave entrances, cliff edges,
     * shorelines, etc.
     *
     * Samples a ring of neighboring columns around (x, z) and
     * rejects the position if any neighbor's height differs
     * from centerHeight by more than maxDeviation blocks.
     */

    private boolean isTerrainStable(
            World world,
            int x,
            int z,
            int centerHeight,
            int radius,
            int maxDeviation
    ) {

        int[] offsets = { -radius, 0, radius };

        for (int dx : offsets) {

            for (int dz : offsets) {

                if (dx == 0 && dz == 0) {
                    continue;
                }

                int neighborHeight =
                        PetWalkSurface.groundY(
                                world,
                                x + dx,
                                z + dz
                        );

                if (
                        Math.abs(
                                neighborHeight - centerHeight
                        )
                                > maxDeviation
                ) {

                    return false;
                }
            }
        }


        return true;
    }


    /*
     * =========================================================
     * RANDOM PLAYER
     * =========================================================
     */

    private Player getRandomPlayer() {

        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && !DungeonWorlds.blocksWildPets(player.getWorld())) {
                players.add(player);
            }
        }

        if (players.isEmpty()) {
            return null;
        }

        return players.get(
                ThreadLocalRandom.current()
                        .nextInt(
                                players.size()
                        )
        );
    }


    /*
     * =========================================================
     * CLEANUP
     * =========================================================
     */

    private void cleanupPets() {

        activePets.removeIf(
                pet -> {

                    if (
                            pet == null
                                    || !pet.isSpawned()
                    ) {

                        return true;
                    }

                    if (DungeonWorlds.blocksWildPets(pet.getEntity().getWorld())) {
                        ActivePetManager active = plugin.getActivePetManager();
                        if (active != null) {
                            for (Player online : Bukkit.getOnlinePlayers()) {
                                if (active.getActivePet(online) == pet) {
                                    return false;
                                }
                            }
                        }
                        if (pet.getOwner() != null) {
                            return false;
                        }
                        pet.remove();
                        return true;
                    }

                    if (DungeonWorlds.isDungeon(pet.getEntity().getWorld())
                            && getSpawnType(pet.getPetInstance().getDefinition())
                            != PetSpawnType.DUNGEON) {
                        pet.remove();
                        return true;
                    }

                    // Combat Borderlands: only Nether wild pets stay.
                    if (pet.getOwner() == null
                            && PetBorderlands.contains(pet.getEntity().getLocation())
                            && getSpawnType(pet.getPetInstance().getDefinition())
                            != PetSpawnType.NETHER) {
                        ActivePetManager active = plugin.getActivePetManager();
                        if (active != null) {
                            boolean equipped = false;
                            for (Player online : Bukkit.getOnlinePlayers()) {
                                if (active.getActivePet(online) == pet) {
                                    equipped = true;
                                    break;
                                }
                            }
                            if (equipped) {
                                return false;
                            }
                        }
                        pet.remove();
                        return true;
                    }


                    Location petLocation =
                            pet.getEntity()
                                    .getLocation();


                    if (
                            hasNearbyPlayer(
                                    petLocation,
                                    DESPAWN_DISTANCE
                            )
                    ) {

                        return false;
                    }


                    pet.remove();

                    return true;
                }
        );
    }

    /**
     * When farm island already has too many wild pets (from before a reload
     * or from clustered players), drop the farthest ones immediately.
     */
    private void enforceFarmIslandWorldCap() {
        World farm = Bukkit.getWorld("aether_farm_island");
        if (farm == null) {
            for (World world : Bukkit.getWorlds()) {
                if (isFarmIsland(world)) {
                    farm = world;
                    break;
                }
            }
        }
        if (farm == null) {
            return;
        }

        int count = countPetsInWorld(farm);
        if (count <= FARM_ISLAND_WORLD_CAP) {
            return;
        }

        int remove = count - FARM_ISLAND_WORLD_CAP;
        ActivePetManager active = plugin.getActivePetManager();

        while (remove > 0) {
            PetEntity farthest = null;
            double farthestDist = -1.0;

            for (PetEntity pet : activePets) {
                if (pet == null || !pet.isSpawned() || pet.getEntity() == null) {
                    continue;
                }
                if (!farm.equals(pet.getEntity().getWorld())) {
                    continue;
                }
                if (pet.getOwner() != null) {
                    continue;
                }
                if (active != null) {
                    boolean equipped = false;
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (active.getActivePet(online) == pet) {
                            equipped = true;
                            break;
                        }
                    }
                    if (equipped) {
                        continue;
                    }
                }

                double nearest = nearestPlayerDistanceSquared(pet.getEntity().getLocation());
                if (nearest > farthestDist) {
                    farthestDist = nearest;
                    farthest = pet;
                }
            }

            if (farthest == null) {
                break;
            }
            farthest.remove();
            activePets.remove(farthest);
            remove--;
        }
    }

    private static double nearestPlayerDistanceSquared(Location location) {
        if (location == null || location.getWorld() == null) {
            return Double.MAX_VALUE;
        }
        double best = Double.MAX_VALUE;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(location.getWorld())) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(location);
            if (dist < best) {
                best = dist;
            }
        }
        return best;
    }

    private int countPetsInWorld(World world) {
        if (world == null) {
            return 0;
        }
        int count = 0;
        for (PetEntity pet : activePets) {
            if (pet == null || !pet.isSpawned() || pet.getEntity() == null) {
                continue;
            }
            if (world.equals(pet.getEntity().getWorld())) {
                count++;
            }
        }
        return count;
    }

    private static boolean isFarmIsland(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName();
        return name.equals("aether_farm_island")
                || name.startsWith("aether_farm_");
    }


    /*
     * =========================================================
     * NEARBY PLAYER
     * =========================================================
     */

    private boolean hasNearbyPlayer(
            Location location,
            double radius
    ) {

        double radiusSquared =
                radius * radius;


        for (Player player :
                Bukkit.getOnlinePlayers()) {

            if (
                    !player.getWorld()
                            .equals(
                                    location.getWorld()
                            )
            ) {

                continue;
            }


            if (
                    player.getLocation()
                            .distanceSquared(
                                    location
                            )
                            <= radiusSquared
            ) {

                return true;
            }
        }


        return false;
    }


    /*
     * =========================================================
     * ACTIVE PETS
     * =========================================================
     */

    public List<PetEntity> getActivePets() {

        return List.copyOf(
                activePets
        );
    }


    public int getActivePetCount() {

        return activePets.size();
    }
}