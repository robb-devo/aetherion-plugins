package de.aetherion.aethermobs.pet;

import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.Rarity;

public class PetFactory {

    private static final double COMMON = 30.0;
    private static final double UNCOMMON = 56.0;
    private static final double RARE = 10.5;
    private static final double EPIC = 2.8;
    private static final double LEGENDARY = 0.28;
    private static final double MYTHIC = 0.004;

    /*
     * =========================================================
     * SURFACE PETS
     * =========================================================
     */

    public PetDefinition createWolf() {

        PetDefinition wolf =
                new PetDefinition(
                        "wolf",
                        "Wolf",
                        ItemCapability.ATTACK_SPREAD
                );

        wolf.setSpawnType(
                PetSpawnType.SURFACE
        );

        wolf.setHabitat(
                PetHabitat.FOREST
        );

        wolf.setSpawnWeight(
                12.0
        );

        addRarity(wolf, Rarity.COMMON, 2.0, 4.0, COMMON);
        addRarity(wolf, Rarity.UNCOMMON, 7.0, 12.0, UNCOMMON);
        addRarity(wolf, Rarity.RARE, 12.0, 19.0, RARE);
        addRarity(wolf, Rarity.EPIC, 19.0, 30.0, EPIC);
        addRarity(wolf, Rarity.LEGENDARY, 30.0, 46.0, LEGENDARY);

        return wolf;
    }

    public PetDefinition createPig() {

        PetDefinition pig =
                new PetDefinition(
                        "pig",
                        "Pig",
                        ItemCapability.HARVEST_SPREAD
                );

        pig.setSpawnType(
                PetSpawnType.SURFACE
        );

        pig.setHabitat(
                PetHabitat.FARM
        );

        pig.setSpawnWeight(
                14.0
        );

        addRarity(pig, Rarity.COMMON, 4.0, 8.0, COMMON);
        addRarity(pig, Rarity.UNCOMMON, 10.5, 17.5, UNCOMMON);
        addRarity(pig, Rarity.RARE, 17.5, 26.0, RARE);
        addRarity(pig, Rarity.EPIC, 26.0, 38.5, EPIC);
        addRarity(pig, Rarity.LEGENDARY, 38.5, 56.0, LEGENDARY);

        return pig;
    }

    public PetDefinition createCow() {

        PetDefinition cow =
                new PetDefinition(
                        "cow",
                        "Cow",
                        ItemCapability.HEALTH
                );

        cow.setSpawnType(
                PetSpawnType.SURFACE
        );

        cow.setHabitat(
                PetHabitat.FARM
        );

        cow.setSpawnWeight(
                14.0
        );

        addRarity(cow, Rarity.COMMON, 4.0, 8.0, COMMON);
        addRarity(cow, Rarity.UNCOMMON, 8.0, 14.0, UNCOMMON);
        addRarity(cow, Rarity.RARE, 14.0, 22.0, RARE);
        addRarity(cow, Rarity.EPIC, 22.0, 34.0, EPIC);
        addRarity(cow, Rarity.LEGENDARY, 34.0, 52.0, LEGENDARY);

        return cow;
    }

    /*
     * =========================================================
     * FARM PETS
     * =========================================================
     */

    public PetDefinition createFarmRabbit() {

        PetDefinition rabbit =
                new PetDefinition(
                        "farm_rabbit",
                        "Farm Rabbit",
                        ItemCapability.HARVEST_SPREAD
                );

        rabbit.setSpawnType(
                PetSpawnType.SURFACE
        );

        rabbit.setHabitat(
                PetHabitat.FARM
        );

        rabbit.setSpawnWeight(
                11.0
        );

        addRarity(rabbit, Rarity.COMMON, 5.4, 9.9, COMMON);
        addRarity(rabbit, Rarity.UNCOMMON, 9.9, 17.1, UNCOMMON);
        addRarity(rabbit, Rarity.RARE, 17.1, 27.0, RARE);
        addRarity(rabbit, Rarity.EPIC, 27.0, 41.4, EPIC);
        addRarity(rabbit, Rarity.LEGENDARY, 41.4, 63.0, LEGENDARY);

        return rabbit;
    }

    public PetDefinition createHorse() {

        PetDefinition horse =
                new PetDefinition(
                        "horse",
                        "Horse",
                        ItemCapability.SPEED
                );

        horse.setSpawnType(
                PetSpawnType.SURFACE
        );

        horse.setHabitat(
                PetHabitat.FARM
        );

        horse.setSpawnWeight(
                8.5
        );

        addRarity(horse, Rarity.COMMON, 3.6, 7.2, COMMON);
        addRarity(horse, Rarity.UNCOMMON, 7.2, 12.6, UNCOMMON);
        addRarity(horse, Rarity.RARE, 12.6, 20.7, RARE);
        addRarity(horse, Rarity.EPIC, 20.7, 32.4, EPIC);
        addRarity(horse, Rarity.LEGENDARY, 32.4, 49.5, LEGENDARY);

        return horse;
    }

    public PetDefinition createSackOfPotatoes() {

        PetDefinition sack =
                new PetDefinition(
                        "sack_of_potatoes",
                        "Sack of Potatoes",
                        ItemCapability.HEALTH
                );

        sack.setSpawnType(
                PetSpawnType.SURFACE
        );

        sack.setHabitat(
                PetHabitat.FARM
        );

        sack.setSpawnWeight(
                3.6
        );

        addRarity(sack, Rarity.COMMON, 6.0, 12.0, COMMON);
        addRarity(sack, Rarity.UNCOMMON, 11.0, 18.0, UNCOMMON);
        addRarity(sack, Rarity.RARE, 18.0, 28.0, RARE);
        addRarity(sack, Rarity.EPIC, 28.0, 42.0, EPIC);
        addRarity(sack, Rarity.LEGENDARY, 42.0, 63.0, LEGENDARY);
        addRarity(sack, Rarity.MYTHIC, 63.0, 91.0, MYTHIC);

        return sack;
    }

    /*
     * =========================================================
     * CAVE PETS
     * =========================================================
     */

    public PetDefinition createZombie() {

        PetDefinition zombie =
                new PetDefinition(
                        "zombie",
                        "Zombie",
                        ItemCapability.UNDEAD_DAMAGE
                );

        zombie.setSpawnType(
                PetSpawnType.CAVE
        );

        zombie.setSpawnWeight(
                11.0
        );

        addRarity(zombie, Rarity.COMMON, 4.5, 11.2, COMMON);
        addRarity(zombie, Rarity.UNCOMMON, 11.2, 20.2, UNCOMMON);
        addRarity(zombie, Rarity.RARE, 20.2, 31.5, RARE);
        addRarity(zombie, Rarity.EPIC, 31.5, 45.0, EPIC);
        addRarity(zombie, Rarity.LEGENDARY, 45.0, 67.5, LEGENDARY);

        return zombie;
    }

    public PetDefinition createSkeleton() {

        PetDefinition skeleton =
                new PetDefinition(
                        "skeleton",
                        "Skeleton",
                        ItemCapability.CRIT_DAMAGE
                );

        skeleton.setSpawnType(
                PetSpawnType.CAVE
        );

        skeleton.setSpawnWeight(
                5.0
        );

        addRarity(skeleton, Rarity.UNCOMMON, 2.7, 5.4, UNCOMMON);
        addRarity(skeleton, Rarity.RARE, 5.4, 8.1, RARE);
        addRarity(skeleton, Rarity.EPIC, 8.1, 11.7, EPIC);
        addRarity(skeleton, Rarity.LEGENDARY, 11.7, 16.2, LEGENDARY);
        addRarity(skeleton, Rarity.MYTHIC, 16.2, 22.5, MYTHIC);

        return skeleton;
    }

    public PetDefinition createBat() {

        PetDefinition bat =
                new PetDefinition(
                        "bat",
                        "Bat",
                        ItemCapability.MINING_POWER
                );

        bat.setSpawnType(
                PetSpawnType.CAVE
        );

        bat.setSpawnWeight(
                6.0
        );

        addRarity(bat, Rarity.UNCOMMON, 5.0, 10.0, UNCOMMON);
        addRarity(bat, Rarity.RARE, 14.0, 24.5, RARE);
        addRarity(bat, Rarity.EPIC, 24.5, 38.5, EPIC);
        addRarity(bat, Rarity.LEGENDARY, 38.5, 56.0, LEGENDARY);
        addRarity(bat, Rarity.MYTHIC, 56.0, 87.5, MYTHIC);

        return bat;
    }

    public PetDefinition createCaveSpider() {

        PetDefinition caveSpider =
                new PetDefinition(
                        "cave_spider",
                        "Cave Spider",
                        ItemCapability.CRIT_CHANCE
                );

        caveSpider.setSpawnType(
                PetSpawnType.CAVE
        );

        caveSpider.setSpawnWeight(
                9.0
        );

        addRarity(caveSpider, Rarity.COMMON, 1.0, 2.5, COMMON);
        addRarity(caveSpider, Rarity.UNCOMMON, 2.5, 4.0, UNCOMMON);
        addRarity(caveSpider, Rarity.RARE, 4.0, 6.0, RARE);
        addRarity(caveSpider, Rarity.EPIC, 6.0, 9.0, EPIC);
        addRarity(caveSpider, Rarity.LEGENDARY, 9.0, 13.0, LEGENDARY);

        return caveSpider;
    }

    public PetDefinition createCreeper() {

        PetDefinition creeper =
                new PetDefinition(
                        "creeper",
                        "Creeper",
                        ItemCapability.DAMAGE
                );

        creeper.setSpawnType(
                PetSpawnType.CAVE
        );

        creeper.setSpawnWeight(
                6.0
        );

        addRarity(creeper, Rarity.UNCOMMON, 6.0, 14.0, UNCOMMON);
        addRarity(creeper, Rarity.RARE, 14.0, 24.0, RARE);
        addRarity(creeper, Rarity.EPIC, 24.0, 38.0, EPIC);
        addRarity(creeper, Rarity.LEGENDARY, 38.0, 56.0, LEGENDARY);
        addRarity(creeper, Rarity.MYTHIC, 56.0, 88.0, MYTHIC);

        return creeper;
    }

    /*
     * =========================================================
     * AQUATIC PETS
     * =========================================================
     */

    public PetDefinition createSquid() {

        PetDefinition squid =
                new PetDefinition(
                        "squid",
                        "Squid",
                        ItemCapability.SPREAD
                );

        squid.setSpawnType(
                PetSpawnType.AQUATIC
        );

        squid.setSpawnWeight(
                12.0
        );

        addRarity(squid, Rarity.COMMON, 4.5, 9.0, COMMON);
        addRarity(squid, Rarity.UNCOMMON, 9.0, 15.8, UNCOMMON);
        addRarity(squid, Rarity.RARE, 15.8, 24.8, RARE);
        addRarity(squid, Rarity.EPIC, 24.8, 38.2, EPIC);
        addRarity(squid, Rarity.LEGENDARY, 38.2, 58.5, LEGENDARY);

        return squid;
    }

    public PetDefinition createGlowSquid() {

        PetDefinition glowSquid =
                new PetDefinition(
                        "glow_squid",
                        "Glow Squid",
                        ItemCapability.MINING_POWER
                );

        glowSquid.setSpawnType(
                PetSpawnType.AQUATIC
        );

        glowSquid.setSpawnWeight(
                5.0
        );

        addRarity(glowSquid, Rarity.UNCOMMON, 9.0, 18.0, UNCOMMON);
        addRarity(glowSquid, Rarity.RARE, 18.0, 31.5, RARE);
        addRarity(glowSquid, Rarity.EPIC, 31.5, 49.5, EPIC);
        addRarity(glowSquid, Rarity.LEGENDARY, 49.5, 72.0, LEGENDARY);
        addRarity(glowSquid, Rarity.MYTHIC, 72.0, 112.5, MYTHIC);

        return glowSquid;
    }

    public PetDefinition createAxolotl() {

        PetDefinition axolotl =
                new PetDefinition(
                        "axolotl",
                        "Axolotl",
                        ItemCapability.HEALTH
                );

        axolotl.setSpawnType(
                PetSpawnType.AQUATIC
        );

        axolotl.setSpawnWeight(
                3.5
        );

        addRarity(axolotl, Rarity.RARE, 18.0, 31.5, RARE);
        addRarity(axolotl, Rarity.EPIC, 31.5, 49.5, EPIC);
        addRarity(axolotl, Rarity.LEGENDARY, 49.5, 81.0, LEGENDARY);

        return axolotl;
    }

    public PetDefinition createGuardian() {

        PetDefinition guardian =
                new PetDefinition(
                        "guardian",
                        "Guardian",
                        ItemCapability.DAMAGE
                );

        guardian.setSpawnType(
                PetSpawnType.AQUATIC
        );

        guardian.setSpawnWeight(
                1.4
        );

        addRarity(guardian, Rarity.EPIC, 36.0, 58.5, EPIC);
        addRarity(guardian, Rarity.LEGENDARY, 58.5, 90.0, LEGENDARY);
        addRarity(guardian, Rarity.MYTHIC, 90.0, 135.0, MYTHIC);

        return guardian;
    }

    public PetDefinition createDolphin() {

        PetDefinition dolphin =
                new PetDefinition(
                        "dolphin",
                        "Dolphin",
                        ItemCapability.SPEED
                );

        dolphin.setSpawnType(
                PetSpawnType.AQUATIC
        );

        dolphin.setSpawnWeight(
                10.0
        );

        addRarity(dolphin, Rarity.COMMON, 4.5, 9.0, COMMON);
        addRarity(dolphin, Rarity.UNCOMMON, 9.0, 15.8, UNCOMMON);
        addRarity(dolphin, Rarity.RARE, 15.8, 24.8, RARE);
        addRarity(dolphin, Rarity.EPIC, 24.8, 38.2, EPIC);
        addRarity(dolphin, Rarity.LEGENDARY, 38.2, 58.5, LEGENDARY);

        return dolphin;
    }

    public PetDefinition createCod() {

        PetDefinition cod =
                new PetDefinition(
                        "cod",
                        "Cod",
                        ItemCapability.FISHING_SPEED
                );

        cod.setSpawnType(
                PetSpawnType.AQUATIC
        );

        cod.setWaterDepth(
                10,
                20
        );

        cod.setSpawnWeight(
                11.0
        );

        addRarity(cod, Rarity.COMMON, 4.5, 9.0, COMMON);
        addRarity(cod, Rarity.UNCOMMON, 9.0, 15.8, UNCOMMON);
        addRarity(cod, Rarity.RARE, 15.8, 24.8, RARE);
        addRarity(cod, Rarity.EPIC, 24.8, 38.2, EPIC);
        addRarity(cod, Rarity.LEGENDARY, 38.2, 58.5, LEGENDARY);

        return cod;
    }

    public PetDefinition createSalmon() {

        PetDefinition salmon =
                new PetDefinition(
                        "salmon",
                        "Salmon",
                        ItemCapability.SPEED
                );

        salmon.setSpawnType(
                PetSpawnType.AQUATIC
        );

        salmon.setWaterDepth(
                10,
                20
        );

        salmon.setSpawnWeight(
                8.0
        );

        addRarity(salmon, Rarity.UNCOMMON, 8.1, 15.3, UNCOMMON);
        addRarity(salmon, Rarity.RARE, 15.3, 26.1, RARE);
        addRarity(salmon, Rarity.EPIC, 26.1, 41.4, EPIC);
        addRarity(salmon, Rarity.LEGENDARY, 41.4, 63.0, LEGENDARY);

        return salmon;
    }

    public PetDefinition createPufferfish() {

        PetDefinition pufferfish =
                new PetDefinition(
                        "pufferfish",
                        "Pufferfish",
                        ItemCapability.DEFENSE
                );

        pufferfish.setSpawnType(
                PetSpawnType.AQUATIC
        );

        pufferfish.setWaterDepth(
                10,
                20
        );

        pufferfish.setSpawnWeight(
                4.2
        );

        addRarity(pufferfish, Rarity.RARE, 18.0, 31.5, RARE);
        addRarity(pufferfish, Rarity.EPIC, 31.5, 49.5, EPIC);
        addRarity(pufferfish, Rarity.LEGENDARY, 49.5, 72.0, LEGENDARY);
        addRarity(pufferfish, Rarity.MYTHIC, 72.0, 108.0, MYTHIC);

        return pufferfish;
    }

    public PetDefinition createTropicalFish() {

        PetDefinition tropical =
                new PetDefinition(
                        "tropical_fish",
                        "Tropical Fish",
                        ItemCapability.PET_CATCH_RATE
                );

        tropical.setSpawnType(
                PetSpawnType.AQUATIC
        );

        tropical.setWaterDepth(
                10,
                20
        );

        tropical.setSpawnWeight(
                6.0
        );

        addRarity(tropical, Rarity.UNCOMMON, 1.8, 3.6, UNCOMMON);
        addRarity(tropical, Rarity.RARE, 3.6, 5.9, RARE);
        addRarity(tropical, Rarity.EPIC, 5.9, 9.0, EPIC);
        addRarity(tropical, Rarity.LEGENDARY, 9.0, 13.5, LEGENDARY);

        return tropical;
    }

    /*
     * =========================================================
     * SPECIAL PETS
     * =========================================================
     */

    public PetDefinition createWither() {

        PetDefinition wither =
                new PetDefinition(
                        "wither",
                        "Wither",
                        ItemCapability.UNDEAD_RESIST
                );

        wither.setSpawnType(
                PetSpawnType.NETHER
        );

        wither.setSpawnWeight(
                1.6
        );

        addRarity(wither, Rarity.EPIC, 4.5, 7.2, EPIC);
        addRarity(wither, Rarity.LEGENDARY, 7.2, 10.8, LEGENDARY);
        addRarity(wither, Rarity.MYTHIC, 10.8, 16.2, MYTHIC);

        return wither;
    }

    public PetDefinition createAetherion() {

        PetDefinition aetherion =
                new PetDefinition(
                        "aetherion",
                        "Aetherion",
                        ItemCapability.DAMAGE
                );

        aetherion.setSpawnType(
                PetSpawnType.SURFACE
        );

        aetherion.setSpawnWeight(
                0.0
        );

        addRarity(aetherion, Rarity.MYTHIC, 225.0, 450.0, MYTHIC);

        return aetherion;
    }

    public PetDefinition createHacker() {

        PetDefinition hacker =
                new PetDefinition(
                        "hacker",
                        "Hacker",
                        ItemCapability.CRIT_CHANCE
                );

        hacker.setSpawnType(
                PetSpawnType.SURFACE
        );

        hacker.setShopExclusive(
                true
        );

        hacker.setRandomCoreStat(
                true
        );

        addRarity(hacker, Rarity.RARE, 18.0, 30.0, RARE);
        addRarity(hacker, Rarity.EPIC, 28.0, 44.0, EPIC);

        return hacker;
    }

    /*
     * =========================================================
     * SKY PETS
     * =========================================================
     */

    public PetDefinition createHawk() {

        PetDefinition hawk =
                new PetDefinition(
                        "hawk",
                        "Hawk",
                        ItemCapability.CRIT_CHANCE
                );

        hawk.setSpawnType(
                PetSpawnType.SKY
        );

        hawk.setHabitat(
                PetHabitat.MOUNTAIN
        );

        hawk.setSpawnWeight(
                2.2
        );

        addRarity(hawk, Rarity.RARE, 3.6, 6.3, RARE);
        addRarity(hawk, Rarity.EPIC, 6.3, 9.9, EPIC);

        return hawk;
    }

    public PetDefinition createBee() {

        PetDefinition bee =
                new PetDefinition(
                        "bee",
                        "Bee",
                        ItemCapability.FORTUNE
                );

        bee.setSpawnType(
                PetSpawnType.SURFACE
        );

        bee.setHabitat(
                PetHabitat.FLOWER
        );

        bee.setSpawnWeight(
                14.0
        );

        addRarity(bee, Rarity.UNCOMMON, 9.0, 18.0, UNCOMMON);
        addRarity(bee, Rarity.RARE, 18.0, 31.5, RARE);

        return bee;
    }

    public PetDefinition createPigeon() {

        PetDefinition pigeon =
                new PetDefinition(
                        "pigeon",
                        "Pigeon",
                        ItemCapability.SPREAD
                );

        pigeon.setSpawnType(
                PetSpawnType.SKY
        );

        pigeon.setHabitat(
                PetHabitat.VILLAGE
        );

        pigeon.setSpawnWeight(
                16.0
        );

        addRarity(pigeon, Rarity.COMMON, 3.6, 7.2, COMMON);
        addRarity(pigeon, Rarity.UNCOMMON, 7.2, 12.6, UNCOMMON);
        addRarity(pigeon, Rarity.RARE, 12.6, 20.2, RARE);
        addRarity(pigeon, Rarity.EPIC, 20.2, 31.5, EPIC);
        addRarity(pigeon, Rarity.LEGENDARY, 31.5, 49.5, LEGENDARY);
        addRarity(pigeon, Rarity.MYTHIC, 49.5, 72.0, MYTHIC);

        return pigeon;
    }

    /*
     * =========================================================
     * JUNGLE / SHORE
     * =========================================================
     */

    public PetDefinition createOcelot() {

        PetDefinition ocelot =
                new PetDefinition(
                        "ocelot",
                        "Ocelot",
                        ItemCapability.FISHING_CATCH
                );

        ocelot.setSpawnType(
                PetSpawnType.SURFACE
        );

        ocelot.setHabitat(
                PetHabitat.JUNGLE
        );

        ocelot.setSpawnWeight(
                11.0
        );

        addRarity(ocelot, Rarity.COMMON, 5.4, 10.8, COMMON);
        addRarity(ocelot, Rarity.UNCOMMON, 10.8, 18.9, UNCOMMON);
        addRarity(ocelot, Rarity.RARE, 18.9, 30.6, RARE);
        addRarity(ocelot, Rarity.EPIC, 30.6, 47.2, EPIC);
        addRarity(ocelot, Rarity.LEGENDARY, 47.2, 69.8, LEGENDARY);

        return ocelot;
    }

    public PetDefinition createParrot() {

        PetDefinition parrot =
                new PetDefinition(
                        "parrot",
                        "Parrot",
                        ItemCapability.SPREAD
                );

        parrot.setSpawnType(
                PetSpawnType.SKY
        );

        parrot.setHabitat(
                PetHabitat.JUNGLE
        );

        parrot.setSpawnWeight(
                9.0
        );

        addRarity(parrot, Rarity.UNCOMMON, 8.1, 15.3, UNCOMMON);
        addRarity(parrot, Rarity.RARE, 15.3, 26.1, RARE);
        addRarity(parrot, Rarity.EPIC, 26.1, 41.4, EPIC);
        addRarity(parrot, Rarity.LEGENDARY, 41.4, 63.0, LEGENDARY);

        return parrot;
    }

    public PetDefinition createTurtle() {

        PetDefinition turtle =
                new PetDefinition(
                        "turtle",
                        "Turtle",
                        ItemCapability.DEFENSE
                );

        turtle.setSpawnType(
                PetSpawnType.SURFACE
        );

        turtle.setHabitat(
                PetHabitat.SHORE
        );

        turtle.setSpawnWeight(
                8.0
        );

        addRarity(turtle, Rarity.UNCOMMON, 9.9, 18.0, UNCOMMON);
        addRarity(turtle, Rarity.RARE, 18.0, 30.6, RARE);
        addRarity(turtle, Rarity.EPIC, 30.6, 47.2, EPIC);
        addRarity(turtle, Rarity.LEGENDARY, 47.2, 72.0, LEGENDARY);

        return turtle;
    }

    public PetDefinition createPanda() {

        PetDefinition panda =
                new PetDefinition(
                        "panda",
                        "Panda",
                        ItemCapability.HEALTH
                );

        panda.setSpawnType(
                PetSpawnType.SURFACE
        );

        panda.setHabitat(
                PetHabitat.JUNGLE
        );

        panda.setSpawnWeight(
                2.4
        );

        addRarity(panda, Rarity.RARE, 22.5, 38.2, RARE);
        addRarity(panda, Rarity.EPIC, 38.2, 58.5, EPIC);
        addRarity(panda, Rarity.LEGENDARY, 58.5, 90.0, LEGENDARY);

        return panda;
    }

    /*
     * =========================================================
     * MOUNTAINS
     * =========================================================
     */

    public PetDefinition createGoat() {

        PetDefinition goat =
                new PetDefinition(
                        "goat",
                        "Goat",
                        ItemCapability.SPEED
                );

        goat.setSpawnType(
                PetSpawnType.SURFACE
        );

        goat.setHabitat(
                PetHabitat.MOUNTAIN
        );

        goat.setSpawnWeight(
                10.0
        );

        addRarity(goat, Rarity.COMMON, 4.5, 9.9, COMMON);
        addRarity(goat, Rarity.UNCOMMON, 9.9, 18.0, UNCOMMON);
        addRarity(goat, Rarity.RARE, 18.0, 29.2, RARE);
        addRarity(goat, Rarity.EPIC, 29.2, 45.0, EPIC);
        addRarity(goat, Rarity.LEGENDARY, 45.0, 67.5, LEGENDARY);

        return goat;
    }

    public PetDefinition createLlama() {

        PetDefinition llama =
                new PetDefinition(
                        "llama",
                        "Llama",
                        ItemCapability.SPREAD
                );

        llama.setSpawnType(
                PetSpawnType.SURFACE
        );

        llama.setHabitat(
                PetHabitat.MOUNTAIN
        );

        llama.setSpawnWeight(
                8.5
        );

        addRarity(llama, Rarity.COMMON, 5.4, 10.8, COMMON);
        addRarity(llama, Rarity.UNCOMMON, 10.8, 18.9, UNCOMMON);
        addRarity(llama, Rarity.RARE, 18.9, 30.6, RARE);
        addRarity(llama, Rarity.EPIC, 30.6, 47.2, EPIC);
        addRarity(llama, Rarity.LEGENDARY, 47.2, 69.8, LEGENDARY);

        return llama;
    }

    /*
     * =========================================================
     * SNOW
     * =========================================================
     */

    public PetDefinition createFox() {

        PetDefinition fox =
                new PetDefinition(
                        "fox",
                        "Fox",
                        ItemCapability.SPEED
                );

        fox.setSpawnType(
                PetSpawnType.SURFACE
        );

        fox.setHabitat(
                PetHabitat.FOREST
        );

        fox.setSpawnWeight(
                9.0
        );

        addRarity(fox, Rarity.UNCOMMON, 9.0, 17.1, UNCOMMON);
        addRarity(fox, Rarity.RARE, 17.1, 27.9, RARE);
        addRarity(fox, Rarity.EPIC, 27.9, 44.1, EPIC);
        addRarity(fox, Rarity.LEGENDARY, 44.1, 67.5, LEGENDARY);

        return fox;
    }

    public PetDefinition createRabbit() {

        PetDefinition rabbit =
                new PetDefinition(
                        "rabbit",
                        "Rabbit",
                        ItemCapability.FORTUNE
                );

        rabbit.setSpawnType(
                PetSpawnType.SURFACE
        );

        rabbit.setHabitat(
                PetHabitat.FOREST
        );

        rabbit.setSpawnWeight(
                11.0
        );

        addRarity(rabbit, Rarity.COMMON, 4.5, 9.0, COMMON);
        addRarity(rabbit, Rarity.UNCOMMON, 9.0, 16.2, UNCOMMON);
        addRarity(rabbit, Rarity.RARE, 16.2, 26.1, RARE);
        addRarity(rabbit, Rarity.EPIC, 26.1, 40.5, EPIC);
        addRarity(rabbit, Rarity.LEGENDARY, 40.5, 63.0, LEGENDARY);

        return rabbit;
    }

    public PetDefinition createPolarBear() {

        PetDefinition polarBear =
                new PetDefinition(
                        "polar_bear",
                        "Polar Bear",
                        ItemCapability.DEFENSE
                );

        polarBear.setSpawnType(
                PetSpawnType.SURFACE
        );

        polarBear.setHabitat(
                PetHabitat.SNOW
        );

        polarBear.setSpawnWeight(
                4.8
        );

        addRarity(polarBear, Rarity.RARE, 20.2, 33.8, RARE);
        addRarity(polarBear, Rarity.EPIC, 33.8, 54.0, EPIC);
        addRarity(polarBear, Rarity.LEGENDARY, 54.0, 83.2, LEGENDARY);

        return polarBear;
    }

    public PetDefinition createYeti() {

        PetDefinition yeti =
                new PetDefinition(
                        "yeti",
                        "Yeti",
                        ItemCapability.HEALTH
                );

        yeti.setSpawnType(
                PetSpawnType.SURFACE
        );

        yeti.setHabitat(
                PetHabitat.SNOW
        );

        yeti.setSpawnWeight(
                1.2
        );

        addRarity(yeti, Rarity.EPIC, 39.6, 60.8, EPIC);
        addRarity(yeti, Rarity.LEGENDARY, 60.8, 92.2, LEGENDARY);

        return yeti;
    }

    public PetDefinition createSnowflake() {

        PetDefinition flake =
                new PetDefinition(
                        "snowflake",
                        "Snowflake",
                        ItemCapability.CRIT_CHANCE
                );

        flake.setSpawnType(
                PetSpawnType.SURFACE
        );

        flake.setHabitat(
                PetHabitat.SNOW
        );

        flake.setSpawnWeight(
                2.4
        );

        addRarity(flake, Rarity.UNCOMMON, 10.8, 18.9, UNCOMMON);
        addRarity(flake, Rarity.RARE, 18.9, 31.5, RARE);
        addRarity(flake, Rarity.EPIC, 31.5, 48.6, EPIC);
        addRarity(flake, Rarity.LEGENDARY, 48.6, 74.2, LEGENDARY);

        return flake;
    }

    public PetDefinition createIceDragon() {

        PetDefinition dragon =
                new PetDefinition(
                        "ice_dragon",
                        "Ice Dragon",
                        ItemCapability.DAMAGE
                );

        dragon.setSpawnType(
                PetSpawnType.SURFACE
        );

        dragon.setHabitat(
                PetHabitat.SNOW
        );

        dragon.setSpawnWeight(
                0.00025
        );

        addRarity(dragon, Rarity.MYTHIC, 67.5, 108.0, MYTHIC);

        return dragon;
    }

    public PetDefinition createFireDragon() {

        PetDefinition dragon =
                new PetDefinition(
                        "fire_dragon",
                        "Fire Dragon",
                        ItemCapability.DAMAGE
                );

        dragon.setSpawnType(
                PetSpawnType.NETHER
        );

        dragon.setSpawnWeight(
                0.00025
        );

        addRarity(dragon, Rarity.MYTHIC, 67.5, 108.0, MYTHIC);

        return dragon;
    }

    public PetDefinition createWaterDragon() {

        PetDefinition dragon =
                new PetDefinition(
                        "water_dragon",
                        "Water Dragon",
                        ItemCapability.FISHING_SPEED
                );

        dragon.setSignatureStat(
                ItemCapability.FISHING_CATCH
        );

        dragon.setSpawnType(
                PetSpawnType.AQUATIC
        );

        dragon.setWaterDepth(
                10,
                40
        );

        dragon.setSpawnWeight(
                0.00025
        );

        addRarity(dragon, Rarity.MYTHIC, 67.5, 108.0, MYTHIC);

        return dragon;
    }

    /** Farming mythic — wild near crops + ultra-rare harvest event. */
    public PetDefinition createNatureDragon() {

        PetDefinition dragon =
                new PetDefinition(
                        "nature_dragon",
                        "Nature Dragon",
                        ItemCapability.HARVEST_SPREAD
                );

        dragon.setSignatureStat(
                ItemCapability.FORTUNE
        );

        dragon.setSpawnType(
                PetSpawnType.SURFACE
        );

        dragon.setHabitat(
                PetHabitat.FARM
        );

        // Wild near crops (hard catch). Event path still reserves the catcher.
        dragon.setSpawnWeight(
                0.00025
        );

        addRarity(dragon, Rarity.MYTHIC, 67.5, 108.0, MYTHIC);

        return dragon;
    }

    /** Cave mining mythic — prefers spawning near ores. */
    public PetDefinition createMiningDragon() {

        PetDefinition dragon =
                new PetDefinition(
                        "mining_dragon",
                        "Mining Dragon",
                        ItemCapability.MINING_POWER
                );

        dragon.setSignatureStat(
                ItemCapability.FORTUNE
        );

        dragon.setSpawnType(
                PetSpawnType.CAVE
        );

        dragon.setSpawnWeight(
                0.00025
        );

        addRarity(dragon, Rarity.MYTHIC, 67.5, 108.0, MYTHIC);

        return dragon;
    }

    /** Foraging mythic — wild near trees + ultra-rare chop/log event. */
    public PetDefinition createForestDragon() {

        PetDefinition dragon =
                new PetDefinition(
                        "forest_dragon",
                        "Forest Dragon",
                        ItemCapability.SPREAD
                );

        dragon.setSignatureStat(
                ItemCapability.FORTUNE
        );

        dragon.setSpawnType(
                PetSpawnType.SURFACE
        );

        dragon.setHabitat(
                PetHabitat.FOREST
        );

        // Wild near wood (hard catch). Event path still reserves the catcher.
        dragon.setSpawnWeight(
                0.00025
        );

        addRarity(dragon, Rarity.MYTHIC, 67.5, 108.0, MYTHIC);

        return dragon;
    }

    public PetDefinition createLightningDragon() {

        PetDefinition dragon =
                new PetDefinition(
                        "lightning_dragon",
                        "Lightning Dragon",
                        ItemCapability.CRIT_DAMAGE
                );

        dragon.setSpawnType(
                PetSpawnType.SKY
        );

        // Weight 0: only the dedicated ultra-rare sky roll may pick this.
        // Otherwise it fills every sky tick in biomes without owl/parrot/pigeon/hawk.
        dragon.setSpawnWeight(
                0.0
        );

        addRarity(dragon, Rarity.MYTHIC, 81.0, 126.0, MYTHIC);

        return dragon;
    }

    public PetDefinition createBlaze() {

        PetDefinition blaze =
                new PetDefinition(
                        "blaze",
                        "Blaze",
                        ItemCapability.CRIT_CHANCE
                );

        blaze.setSpawnType(
                PetSpawnType.NETHER
        );

        blaze.setSpawnWeight(
                5.0
        );

        addRarity(blaze, Rarity.UNCOMMON, 9.9, 18.0, UNCOMMON);
        addRarity(blaze, Rarity.RARE, 18.0, 30.6, RARE);
        addRarity(blaze, Rarity.EPIC, 30.6, 47.2, EPIC);
        addRarity(blaze, Rarity.LEGENDARY, 47.2, 72.0, LEGENDARY);

        return blaze;
    }

    public PetDefinition createSlimeMinion() {

        PetDefinition slime =
                new PetDefinition(
                        "slime_minion",
                        "Slime Minion",
                        ItemCapability.HEALTH
                );

        slime.setSpawnType(
                PetSpawnType.NETHER
        );

        slime.setSpawnWeight(
                8.5
        );

        addRarity(slime, Rarity.COMMON, 5.4, 10.8, COMMON);
        addRarity(slime, Rarity.UNCOMMON, 10.8, 18.9, UNCOMMON);
        addRarity(slime, Rarity.RARE, 18.9, 31.5, RARE);
        addRarity(slime, Rarity.EPIC, 31.5, 48.6, EPIC);

        return slime;
    }

    public PetDefinition createGhast() {

        PetDefinition ghast =
                new PetDefinition(
                        "ghast",
                        "Ghast",
                        ItemCapability.ATTACK_SPREAD
                );

        ghast.setSpawnType(
                PetSpawnType.NETHER
        );

        ghast.setSpawnWeight(
                2.4
        );

        addRarity(ghast, Rarity.RARE, 18.0, 31.5, RARE);
        addRarity(ghast, Rarity.EPIC, 31.5, 51.8, EPIC);
        addRarity(ghast, Rarity.LEGENDARY, 51.8, 78.8, LEGENDARY);

        return ghast;
    }

    /*
     * =========================================================
     * DESERT
     * =========================================================
     */

    public PetDefinition createCamel() {

        PetDefinition camel =
                new PetDefinition(
                        "camel",
                        "Camel",
                        ItemCapability.HEALTH
                );

        camel.setSpawnType(
                PetSpawnType.SURFACE
        );

        camel.setHabitat(
                PetHabitat.DESERT
        );

        camel.setSpawnWeight(
                9.0
        );

        addRarity(camel, Rarity.COMMON, 6.3, 12.6, COMMON);
        addRarity(camel, Rarity.UNCOMMON, 12.6, 21.6, UNCOMMON);
        addRarity(camel, Rarity.RARE, 21.6, 33.8, RARE);
        addRarity(camel, Rarity.EPIC, 33.8, 51.8, EPIC);
        addRarity(camel, Rarity.LEGENDARY, 51.8, 76.5, LEGENDARY);

        return camel;
    }

    public PetDefinition createArmadillo() {

        PetDefinition armadillo =
                new PetDefinition(
                        "armadillo",
                        "Armadillo",
                        ItemCapability.DEFENSE
                );

        armadillo.setSpawnType(
                PetSpawnType.SURFACE
        );

        armadillo.setHabitat(
                PetHabitat.DESERT
        );

        armadillo.setSpawnWeight(
                8.0
        );

        addRarity(armadillo, Rarity.UNCOMMON, 9.9, 18.0, UNCOMMON);
        addRarity(armadillo, Rarity.RARE, 18.0, 30.6, RARE);
        addRarity(armadillo, Rarity.EPIC, 30.6, 47.2, EPIC);
        addRarity(armadillo, Rarity.LEGENDARY, 47.2, 72.0, LEGENDARY);

        return armadillo;
    }

    /*
     * =========================================================
     * DUNGEON PETS
     * =========================================================
     *
     * Only spawn inside Aetherion dungeon instances.
     * Core values are percent auras, not flat stats.
     */

    public PetDefinition createDungeonZombie() {

        PetDefinition zombie =
                new PetDefinition(
                        "dungeon_zombie",
                        "Dungeon Zombie",
                        ItemCapability.DAMAGE
                );

        zombie.setDungeonAura(
                PetDefinition.DungeonAura.DAMAGE
        );

        zombie.setSpawnType(
                PetSpawnType.DUNGEON
        );

        zombie.setSpawnWeight(
                9.0
        );

        addRarity(zombie, Rarity.COMMON, 2.5, 3.5, COMMON);
        addRarity(zombie, Rarity.UNCOMMON, 3.5, 5.0, UNCOMMON);
        addRarity(zombie, Rarity.RARE, 5.0, 7.0, RARE);

        return zombie;
    }

    public PetDefinition createDungeonSkeleton() {

        PetDefinition skeleton =
                new PetDefinition(
                        "dungeon_skeleton",
                        "Dungeon Skeleton",
                        ItemCapability.DAMAGE
                );

        skeleton.setDungeonAura(
                PetDefinition.DungeonAura.BOW
        );

        skeleton.setSpawnType(
                PetSpawnType.DUNGEON
        );

        skeleton.setSpawnWeight(
                7.0
        );

        addRarity(skeleton, Rarity.COMMON, 3.4, 5.1, COMMON);
        addRarity(skeleton, Rarity.UNCOMMON, 5.1, 6.8, UNCOMMON);
        addRarity(skeleton, Rarity.RARE, 6.8, 9.4, RARE);

        return skeleton;
    }

    public PetDefinition createDungeonDragon() {

        PetDefinition dragon =
                new PetDefinition(
                        "dungeon_dragon",
                        "Dungeon Dragon",
                        ItemCapability.DAMAGE
                );

        dragon.setDungeonAura(
                PetDefinition.DungeonAura.ALL
        );

        dragon.setSpawnType(
                PetSpawnType.DUNGEON
        );

        dragon.setSpawnWeight(
                0.008
        );

        addRarity(dragon, Rarity.MYTHIC, 4.2, 6.8, MYTHIC);

        return dragon;
    }

    /*
     * =========================================================
     * ULTRA-RARE
     * =========================================================
     */

    public PetDefinition createAllay() {

        PetDefinition allay =
                new PetDefinition(
                        "allay",
                        "Allay",
                        ItemCapability.FORTUNE
                );

        allay.setSpawnType(
                PetSpawnType.SURFACE
        );

        allay.setHabitat(
                PetHabitat.DARK
        );

        allay.setSpawnWeight(
                8.0
        );

        addRarity(allay, Rarity.EPIC, 40.5, 63.0, EPIC);
        addRarity(allay, Rarity.LEGENDARY, 63.0, 99.0, LEGENDARY);
        addRarity(allay, Rarity.MYTHIC, 99.0, 153.0, MYTHIC);

        return allay;
    }

    /*
     * =========================================================
     * SWAMP
     * =========================================================
     */

    public PetDefinition createFrog() {

        PetDefinition frog =
                new PetDefinition(
                        "frog",
                        "Frog",
                        ItemCapability.HEALTH
                );

        frog.setSpawnType(
                PetSpawnType.SURFACE
        );

        frog.setHabitat(
                PetHabitat.SWAMP
        );

        frog.setSpawnWeight(
                11.0
        );

        addRarity(frog, Rarity.COMMON, 5.4, 10.8, COMMON);
        addRarity(frog, Rarity.UNCOMMON, 10.8, 18.9, UNCOMMON);
        addRarity(frog, Rarity.RARE, 18.9, 30.6, RARE);
        addRarity(frog, Rarity.EPIC, 30.6, 47.2, EPIC);
        addRarity(frog, Rarity.LEGENDARY, 47.2, 72.0, LEGENDARY);

        return frog;
    }

    public PetDefinition createWitch() {

        PetDefinition witch =
                new PetDefinition(
                        "witch",
                        "Witch",
                        ItemCapability.CRIT_CHANCE
                );

        witch.setSpawnType(
                PetSpawnType.SURFACE
        );

        witch.setHabitat(
                PetHabitat.SWAMP
        );

        witch.setSpawnWeight(
                3.2
        );

        addRarity(witch, Rarity.RARE, 18.9, 31.5, RARE);
        addRarity(witch, Rarity.EPIC, 31.5, 49.5, EPIC);
        addRarity(witch, Rarity.LEGENDARY, 49.5, 76.5, LEGENDARY);

        return witch;
    }

    public PetDefinition createMudling() {

        PetDefinition mudling =
                new PetDefinition(
                        "mudling",
                        "Mudling",
                        ItemCapability.DEFENSE
                );

        mudling.setSpawnType(
                PetSpawnType.SURFACE
        );

        mudling.setHabitat(
                PetHabitat.SWAMP
        );

        mudling.setSpawnWeight(
                6.5
        );

        addRarity(mudling, Rarity.UNCOMMON, 9.0, 16.2, UNCOMMON);
        addRarity(mudling, Rarity.RARE, 16.2, 27.0, RARE);
        addRarity(mudling, Rarity.EPIC, 27.0, 42.8, EPIC);
        addRarity(mudling, Rarity.LEGENDARY, 42.8, 65.2, LEGENDARY);

        return mudling;
    }

    /*
     * =========================================================
     * FOREST
     * =========================================================
     */

    public PetDefinition createDeer() {

        PetDefinition deer =
                new PetDefinition(
                        "deer",
                        "Deer",
                        ItemCapability.SPEED
                );

        deer.setSpawnType(
                PetSpawnType.SURFACE
        );

        deer.setHabitat(
                PetHabitat.FOREST
        );

        deer.setSpawnWeight(
                10.0
        );

        addRarity(deer, Rarity.COMMON, 4.5, 9.0, COMMON);
        addRarity(deer, Rarity.UNCOMMON, 9.0, 16.2, UNCOMMON);
        addRarity(deer, Rarity.RARE, 16.2, 26.1, RARE);
        addRarity(deer, Rarity.EPIC, 26.1, 40.5, EPIC);
        addRarity(deer, Rarity.LEGENDARY, 40.5, 63.0, LEGENDARY);

        return deer;
    }

    public PetDefinition createSquirrel() {

        PetDefinition squirrel =
                new PetDefinition(
                        "squirrel",
                        "Squirrel",
                        ItemCapability.FORTUNE
                );

        squirrel.setSpawnType(
                PetSpawnType.SURFACE
        );

        squirrel.setHabitat(
                PetHabitat.FOREST
        );

        squirrel.setSpawnWeight(
                11.0
        );

        addRarity(squirrel, Rarity.COMMON, 4.5, 9.0, COMMON);
        addRarity(squirrel, Rarity.UNCOMMON, 9.0, 16.2, UNCOMMON);
        addRarity(squirrel, Rarity.RARE, 16.2, 26.1, RARE);
        addRarity(squirrel, Rarity.EPIC, 26.1, 40.5, EPIC);
        addRarity(squirrel, Rarity.LEGENDARY, 40.5, 63.0, LEGENDARY);

        return squirrel;
    }

    public PetDefinition createBoar() {

        PetDefinition boar =
                new PetDefinition(
                        "boar",
                        "Boar",
                        ItemCapability.DAMAGE
                );

        boar.setSpawnType(
                PetSpawnType.SURFACE
        );

        boar.setHabitat(
                PetHabitat.FOREST
        );

        boar.setSpawnWeight(
                7.5
        );

        addRarity(boar, Rarity.UNCOMMON, 8.1, 15.3, UNCOMMON);
        addRarity(boar, Rarity.RARE, 15.3, 26.1, RARE);
        addRarity(boar, Rarity.EPIC, 26.1, 41.4, EPIC);
        addRarity(boar, Rarity.LEGENDARY, 41.4, 65.2, LEGENDARY);

        return boar;
    }

    public PetDefinition createOwl() {

        PetDefinition owl =
                new PetDefinition(
                        "owl",
                        "Owl",
                        ItemCapability.SPREAD
                );

        owl.setSpawnType(
                PetSpawnType.SKY
        );

        owl.setHabitat(
                PetHabitat.FOREST
        );

        owl.setSpawnWeight(
                8.0
        );

        addRarity(owl, Rarity.UNCOMMON, 8.1, 15.3, UNCOMMON);
        addRarity(owl, Rarity.RARE, 15.3, 26.1, RARE);
        addRarity(owl, Rarity.EPIC, 26.1, 41.4, EPIC);
        addRarity(owl, Rarity.LEGENDARY, 41.4, 63.0, LEGENDARY);

        return owl;
    }

    /*
     * =========================================================
     * FLOWER / MEADOW
     * =========================================================
     */

    public PetDefinition createSheep() {

        PetDefinition sheep =
                new PetDefinition(
                        "sheep",
                        "Sheep",
                        ItemCapability.DEFENSE
                );

        sheep.setSpawnType(
                PetSpawnType.SURFACE
        );

        sheep.setHabitat(
                PetHabitat.FLOWER
        );

        sheep.setSpawnWeight(
                10.0
        );

        addRarity(sheep, Rarity.COMMON, 5.4, 10.8, COMMON);
        addRarity(sheep, Rarity.UNCOMMON, 10.8, 18.9, UNCOMMON);
        addRarity(sheep, Rarity.RARE, 18.9, 30.6, RARE);
        addRarity(sheep, Rarity.EPIC, 30.6, 47.2, EPIC);
        addRarity(sheep, Rarity.LEGENDARY, 47.2, 72.0, LEGENDARY);

        return sheep;
    }

    public PetDefinition createButterfly() {

        PetDefinition butterfly =
                new PetDefinition(
                        "butterfly",
                        "Butterfly",
                        ItemCapability.PET_CATCH_RATE
                );

        butterfly.setSpawnType(
                PetSpawnType.SURFACE
        );

        butterfly.setHabitat(
                PetHabitat.FLOWER
        );

        butterfly.setSpawnWeight(
                11.0
        );

        addRarity(butterfly, Rarity.UNCOMMON, 7.2, 13.5, UNCOMMON);
        addRarity(butterfly, Rarity.RARE, 13.5, 23.4, RARE);
        addRarity(butterfly, Rarity.EPIC, 23.4, 36.9, EPIC);
        addRarity(butterfly, Rarity.LEGENDARY, 36.9, 58.5, LEGENDARY);

        return butterfly;
    }

    /*
     * =========================================================
     * MUSHROOM
     * =========================================================
     */

    public PetDefinition createMooshroom() {

        PetDefinition mooshroom =
                new PetDefinition(
                        "mooshroom",
                        "Mooshroom",
                        ItemCapability.HEALTH
                );

        mooshroom.setSpawnType(
                PetSpawnType.SURFACE
        );

        mooshroom.setHabitat(
                PetHabitat.MUSHROOM
        );

        mooshroom.setSpawnWeight(
                8.5
        );

        addRarity(mooshroom, Rarity.UNCOMMON, 9.9, 18.0, UNCOMMON);
        addRarity(mooshroom, Rarity.RARE, 18.0, 30.6, RARE);
        addRarity(mooshroom, Rarity.EPIC, 30.6, 47.2, EPIC);
        addRarity(mooshroom, Rarity.LEGENDARY, 47.2, 72.0, LEGENDARY);

        return mooshroom;
    }

    public PetDefinition createShroomling() {

        PetDefinition shroomling =
                new PetDefinition(
                        "shroomling",
                        "Shroomling",
                        ItemCapability.FORTUNE
                );

        shroomling.setSpawnType(
                PetSpawnType.SURFACE
        );

        shroomling.setHabitat(
                PetHabitat.MUSHROOM
        );

        shroomling.setSpawnWeight(
                4.0
        );

        addRarity(shroomling, Rarity.RARE, 18.0, 31.5, RARE);
        addRarity(shroomling, Rarity.EPIC, 31.5, 49.5, EPIC);
        addRarity(shroomling, Rarity.LEGENDARY, 49.5, 76.5, LEGENDARY);
        addRarity(shroomling, Rarity.MYTHIC, 76.5, 112.5, MYTHIC);

        return shroomling;
    }

    /*
     * =========================================================
     * VILLAGE
     * =========================================================
     */

    public PetDefinition createCat() {

        PetDefinition cat =
                new PetDefinition(
                        "cat",
                        "Cat",
                        ItemCapability.SPEED
                );

        cat.setSpawnType(
                PetSpawnType.SURFACE
        );

        cat.setHabitat(
                PetHabitat.VILLAGE
        );

        cat.setSpawnWeight(
                10.0
        );

        addRarity(cat, Rarity.COMMON, 4.5, 9.0, COMMON);
        addRarity(cat, Rarity.UNCOMMON, 9.0, 16.2, UNCOMMON);
        addRarity(cat, Rarity.RARE, 16.2, 26.1, RARE);
        addRarity(cat, Rarity.EPIC, 26.1, 40.5, EPIC);
        addRarity(cat, Rarity.LEGENDARY, 40.5, 63.0, LEGENDARY);

        return cat;
    }

    public PetDefinition createIronGolem() {

        PetDefinition golem =
                new PetDefinition(
                        "iron_golem",
                        "Iron Golem",
                        ItemCapability.DEFENSE
                );

        golem.setSpawnType(
                PetSpawnType.SURFACE
        );

        golem.setHabitat(
                PetHabitat.VILLAGE
        );

        golem.setSpawnWeight(
                2.2
        );

        addRarity(golem, Rarity.RARE, 21.6, 36.0, RARE);
        addRarity(golem, Rarity.EPIC, 36.0, 56.2, EPIC);
        addRarity(golem, Rarity.LEGENDARY, 56.2, 85.5, LEGENDARY);

        return golem;
    }

    /*
     * =========================================================
     * LUSH
     * =========================================================
     */

    public PetDefinition createSniffer() {

        PetDefinition sniffer =
                new PetDefinition(
                        "sniffer",
                        "Sniffer",
                        ItemCapability.MINING_POWER
                );

        sniffer.setSpawnType(
                PetSpawnType.SURFACE
        );

        sniffer.setHabitat(
                PetHabitat.LUSH
        );

        sniffer.setSpawnWeight(
                5.5
        );

        addRarity(sniffer, Rarity.UNCOMMON, 7.2, 13.5, UNCOMMON);
        addRarity(sniffer, Rarity.RARE, 13.5, 23.4, RARE);
        addRarity(sniffer, Rarity.EPIC, 23.4, 38.2, EPIC);
        addRarity(sniffer, Rarity.LEGENDARY, 38.2, 60.8, LEGENDARY);

        return sniffer;
    }

    public PetDefinition createMossSprite() {

        PetDefinition sprite =
                new PetDefinition(
                        "moss_sprite",
                        "Moss Sprite",
                        ItemCapability.HEALTH
                );

        sprite.setSpawnType(
                PetSpawnType.CAVE
        );

        sprite.setHabitat(
                PetHabitat.LUSH
        );

        sprite.setSpawnWeight(
                6.0
        );

        addRarity(sprite, Rarity.UNCOMMON, 9.0, 17.1, UNCOMMON);
        addRarity(sprite, Rarity.RARE, 17.1, 28.8, RARE);
        addRarity(sprite, Rarity.EPIC, 28.8, 45.0, EPIC);
        addRarity(sprite, Rarity.LEGENDARY, 45.0, 69.8, LEGENDARY);

        return sprite;
    }

    /*
     * =========================================================
     * HABITAT MYTHICS
     * =========================================================
     */

    public PetDefinition createSwampHag() {

        PetDefinition hag =
                new PetDefinition(
                        "swamp_hag",
                        "Swamp Hag",
                        ItemCapability.CRIT_DAMAGE
                );

        hag.setSpawnType(
                PetSpawnType.SURFACE
        );

        hag.setHabitat(
                PetHabitat.SWAMP
        );

        hag.setSpawnWeight(
                0.00025
        );

        addRarity(hag, Rarity.MYTHIC, 72.0, 112.5, MYTHIC);

        return hag;
    }

    public PetDefinition createForestSpirit() {

        PetDefinition spirit =
                new PetDefinition(
                        "forest_spirit",
                        "Forest Spirit",
                        ItemCapability.FORTUNE
                );

        spirit.setSpawnType(
                PetSpawnType.SURFACE
        );

        spirit.setHabitat(
                PetHabitat.FOREST
        );

        spirit.setSpawnWeight(
                0.00025
        );

        addRarity(spirit, Rarity.MYTHIC, 72.0, 112.5, MYTHIC);

        return spirit;
    }

    public PetDefinition createBloomFairy() {

        PetDefinition fairy =
                new PetDefinition(
                        "bloom_fairy",
                        "Bloom Fairy",
                        ItemCapability.PET_CATCH_RATE
                );

        fairy.setSpawnType(
                PetSpawnType.SURFACE
        );

        fairy.setHabitat(
                PetHabitat.FLOWER
        );

        fairy.setSpawnWeight(
                0.0002
        );

        addRarity(fairy, Rarity.MYTHIC, 67.5, 108.0, MYTHIC);

        return fairy;
    }

    public PetDefinition createSandWraith() {

        PetDefinition wraith =
                new PetDefinition(
                        "sand_wraith",
                        "Sand Wraith",
                        ItemCapability.SPEED
                );

        wraith.setSpawnType(
                PetSpawnType.SURFACE
        );

        wraith.setHabitat(
                PetHabitat.DESERT
        );

        wraith.setSpawnWeight(
                0.00025
        );

        addRarity(wraith, Rarity.MYTHIC, 69.8, 110.2, MYTHIC);

        return wraith;
    }

    public PetDefinition createMycelord() {

        PetDefinition lord =
                new PetDefinition(
                        "mycelord",
                        "Mycelord",
                        ItemCapability.HEALTH
                );

        lord.setSpawnType(
                PetSpawnType.SURFACE
        );

        lord.setHabitat(
                PetHabitat.MUSHROOM
        );

        lord.setSpawnWeight(
                0.00025
        );

        addRarity(lord, Rarity.MYTHIC, 76.5, 117.0, MYTHIC);

        return lord;
    }

    public PetDefinition createLushOracle() {

        PetDefinition oracle =
                new PetDefinition(
                        "lush_oracle",
                        "Lush Oracle",
                        ItemCapability.MINING_POWER
                );

        oracle.setSpawnType(
                PetSpawnType.CAVE
        );

        oracle.setHabitat(
                PetHabitat.LUSH
        );

        oracle.setSpawnWeight(
                0.0002
        );

        addRarity(oracle, Rarity.MYTHIC, 67.5, 108.0, MYTHIC);

        return oracle;
    }

    /*
     * =========================================================
     * RARITY HELPER
     * =========================================================
     */

    /**
     * Wave 3: non-percent cores are sized from {@link de.aetherion.aethermobs.model.PetBalance}
     * (12% stacking slice). {@code min}/{@code max} stay as dungeon-percent literals.
     */
    private void addRarity(
            PetDefinition pet,
            Rarity rarity,
            double min,
            double max,
            double weight
    ) {
        if (pet != null && pet.isPercentBonus()) {
            pet.addRarityConfig(
                    new PetRarityConfig(
                            rarity,
                            min,
                            max,
                            weight
                    )
            );
            return;
        }
        double[] band = de.aetherion.aethermobs.model.PetBalance.coreBand(
                pet.getId(),
                pet.getCoreStat(),
                pet.rollsRandomCoreStat(),
                rarity
        );
        pet.addRarityConfig(
                new PetRarityConfig(
                        rarity,
                        band[0],
                        band[1],
                        weight
                )
        );
    }
}