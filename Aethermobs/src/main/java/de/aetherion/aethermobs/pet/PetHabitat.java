package de.aetherion.aethermobs.pet;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public enum PetHabitat {

    ANY(
            "Overworld surface"
    ),

    JUNGLE(
            "Near jungle wood and bamboo"
    ),

    SHORE(
            "Near sand and water"
    ),

    MOUNTAIN(
            "Near andesite, tuff and dripstone"
    ),

    SNOW(
            "Near snow and ice pockets"
    ),

    DESERT(
            "Near sandstone, terracotta and cactus"
    ),

    DARK(
            "Near spruce groves"
    ),

    FARM(
            "Near crops and farmland"
    ),

    SWAMP(
            "Near mud, mangrove and lily pads"
    ),

    FOREST(
            "Near oak, birch and dark oak woods"
    ),

    FLOWER(
            "Near flowers and meadows"
    ),

    MUSHROOM(
            "Near mycelium and mushrooms"
    ),

    VILLAGE(
            "Near bells, beds and workstations"
    ),

    LUSH(
            "Near moss, azalea and dripleaf"
    ),

    /** Painted mining island — cave / sky / aquatic pool. */
    ELDERVALE(
            "Eldervale mining island"
    );

    private final String displayName;

    PetHabitat(
            String displayName
    ) {

        this.displayName =
                displayName;
    }

    public String getDisplayName() {

        return displayName;
    }

    public boolean matches(
            Location location
    ) {

        return presentAt(location);
    }

    private static volatile HabitatCache habitatCache;

    private record HabitatCache(
            String world,
            int blockX,
            int blockZ,
            PetHabitat dominant,
            long atMs
    ) {
    }

    /**
     * Hard local signature — biomes ignored (Origin map is almost all plains).
     * Scans early-exit at threshold; keep this cheap — spawn ticks call it a lot.
     */
    public boolean presentAt(
            Location location
    ) {

        if (this == ANY) {
            return true;
        }

        if (location == null
                || location.getWorld() == null) {

            return false;
        }

        PetHabitat painted =
                PetHabitatZones.habitatAt(location);

        if (painted == this) {
            return true;
        }

        return switch (this) {

            case FARM ->
                    PetCrops.nearby(location, 12);

            case VILLAGE ->
                    PetVillages.nearby(location, 14);

            case SHORE ->
                    isShoreConfirmed(location);

            case DESERT ->
                    isDesertConfirmed(location);

            case SNOW ->
                    countNearby(location, 14, PetHabitat::isSnowBlock, 6) >= 6;

            case DARK ->
                    countNearby(location, 14, PetHabitat::isSpruceBlock, 9) >= 9;

            case MUSHROOM ->
                    // Custom maps mix overworld mushrooms + nether fungus groves.
                    countNearby(location, 16, PetHabitat::isMushroomBlock, 4) >= 4;

            case SWAMP ->
                    countNearby(location, 14, PetHabitat::isSwampBlock, 7) >= 7;

            case FOREST ->
                    countNearby(location, 14, PetHabitat::isForestWoodBlock, 6) >= 6;

            case FLOWER ->
                    // Soft enough for sparse meadows / Origin flower patches.
                    countFlowerSignals(location, 18, 2) >= 2;

            case LUSH ->
                    // Moss alone is too common on Origin — need real lush signature.
                    countNearby(location, 14, PetHabitat::isStrongLushBlock, 6) >= 6;

            case MOUNTAIN ->
                    countNearby(location, 14, PetHabitat::isMountainBlock, 12) >= 12;

            case JUNGLE ->
                    countNearby(location, 14, PetHabitat::isJungleBlock, 5) >= 5;

            case ELDERVALE ->
                    // Painted disk only — no block auto-detect.
                    false;

            default ->
                    false;
        };
    }

    /**
     * Same as {@link #presentAt} — soft reach removed; habitats stay hard.
     */
    public boolean presentNear(
            Location location,
            int radius
    ) {

        return presentAt(location);
    }

    /**
     * Strongest hard habitat underfoot. Painted zones win; then rare/unique
     * patches over common forest/mountain fill.
     */
    public static PetHabitat dominantAt(
            Location location
    ) {

        PetHabitat painted =
                PetHabitatZones.habitatAt(location);

        if (painted != null) {
            return painted;
        }

        if (cacheFresh(location)) {
            return habitatCache.dominant();
        }

        return dominantAtUncached(location);
    }

    /**
     * Hard habitat underfoot, or a nearby hard patch (few samples, cached ~0.8s).
     * Painted disks always win at origin.
     */
    public static PetHabitat nearestDominant(
            Location origin,
            int radius
    ) {

        if (origin == null
                || origin.getWorld() == null) {

            return null;
        }

        PetHabitat painted =
                PetHabitatZones.habitatAt(origin);

        if (painted != null) {
            writeCache(origin, painted);
            return painted;
        }

        PetHabitat cached =
                cacheFresh(origin)
                        ? habitatCache.dominant()
                        : null;

        if (cacheFresh(origin)) {
            return cached;
        }

        PetHabitat here =
                dominantAtUncached(origin);

        if (here != null) {
            writeCache(origin, here);
            return here;
        }

        if (radius <= 0) {
            writeCache(origin, null);
            return null;
        }

        World world =
                origin.getWorld();

        int ox =
                origin.getBlockX();

        int oz =
                origin.getBlockZ();

        int[][] offsets = {
                {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius},
                {radius / 2, radius / 2}, {radius / 2, -radius / 2},
                {-radius / 2, radius / 2}, {-radius / 2, -radius / 2},
                {radius / 2, 0}, {-radius / 2, 0}, {0, radius / 2}, {0, -radius / 2}
        };

        for (int[] offset : offsets) {

            int x =
                    ox + offset[0];

            int z =
                    oz + offset[1];

            int groundY =
                    PetWalkSurface.groundY(
                            world,
                            x,
                            z
                    );

            Location sample =
                    new Location(
                            world,
                            x + 0.5,
                            groundY + 1.0,
                            z + 0.5
                    );

            PetHabitat samplePaint =
                    PetHabitatZones.habitatAt(sample);

            if (samplePaint != null) {
                writeCache(origin, samplePaint);
                return samplePaint;
            }

            PetHabitat found =
                    dominantAtUncached(sample);

            if (found != null) {
                writeCache(origin, found);
                return found;
            }
        }

        writeCache(origin, null);
        return null;
    }

    private static PetHabitat dominantAtUncached(
            Location location
    ) {

        if (location == null
                || location.getWorld() == null) {

            return null;
        }

        PetHabitat[] priority = {
                SNOW,
                MUSHROOM,
                DESERT,
                FLOWER,
                FARM,
                VILLAGE,
                SWAMP,
                JUNGLE,
                LUSH,
                DARK,
                SHORE,
                MOUNTAIN,
                FOREST
        };

        for (PetHabitat habitat : priority) {
            if (habitat.presentAt(location)) {
                return habitat;
            }
        }

        return null;
    }

    private static boolean cacheFresh(
            Location location
    ) {

        HabitatCache hit =
                habitatCache;

        if (hit == null
                || location == null
                || location.getWorld() == null) {

            return false;
        }

        if (System.currentTimeMillis() - hit.atMs() > 800L) {
            return false;
        }

        if (!hit.world().equals(location.getWorld().getName())) {
            return false;
        }

        return Math.abs(hit.blockX() - location.getBlockX()) <= 3
                && Math.abs(hit.blockZ() - location.getBlockZ()) <= 3;
    }

    private static void writeCache(
            Location location,
            PetHabitat dominant
    ) {

        if (location == null
                || location.getWorld() == null) {

            return;
        }

        habitatCache =
                new HabitatCache(
                        location.getWorld().getName(),
                        location.getBlockX(),
                        location.getBlockZ(),
                        dominant,
                        System.currentTimeMillis()
                );
    }

    /**
     * Random habitat anchor near the player — only hard {@link #presentAt} columns.
     */
    public Location findNearbyColumn(
            Location origin,
            int searchRadius
    ) {

        if (this == ANY
                || origin == null
                || origin.getWorld() == null
                || searchRadius <= 0) {

            return null;
        }

        World world =
                origin.getWorld();

        int ox =
                origin.getBlockX();

        int oz =
                origin.getBlockZ();

        ThreadLocalRandom random =
                ThreadLocalRandom.current();

        for (
                int attempt = 0;
                attempt < 32;
                attempt++
        ) {

            int dx =
                    random.nextInt(
                            -searchRadius,
                            searchRadius + 1
                    );

            int dz =
                    random.nextInt(
                            -searchRadius,
                            searchRadius + 1
                    );

            int x =
                    ox + dx;

            int z =
                    oz + dz;

            int groundY;
            if (this == MUSHROOM || this == LUSH) {
                // Covered groves: heightmap hits the roof — walk a local floor near the player.
                groundY = PetWalkSurface.groveFloorY(world, x, z, origin.getBlockY());
            } else {
                groundY = PetWalkSurface.groundY(world, x, z);
            }

            Location candidate =
                    new Location(
                            world,
                            x + 0.5,
                            groundY + PetEntity.HOVER_HEIGHT,
                            z + 0.5
                    );

            if (!presentAt(candidate)) {
                continue;
            }

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

            if (!ground.getType().isSolid()
                    || PetWalkSurface.isCanopy(ground.getType())
                    || PetWalkSurface.isWatery(ground)) {

                continue;
            }

            if (!feet.isPassable()
                    || !head.isPassable()
                    || PetWalkSurface.isWatery(feet)
                    || PetWalkSurface.isWatery(head)) {

                continue;
            }

            // Open-sky dry check rejects mushroom caves under a solid roof.
            if (this != MUSHROOM
                    && this != LUSH
                    && !PetWalkSurface.isDrySurfaceColumn(world, x, z)) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    /**
     * Legacy exclusive snow/desert — prefer {@link #dominantAt}.
     */
    public boolean isExclusive() {

        return this == SNOW
                || this == DESERT;
    }

    /**
     * Dominant exclusive habitat at a location, priority order.
     */
    public static PetHabitat exclusiveAt(
            Location location
    ) {

        PetHabitat dominant =
                dominantAt(location);

        if (dominant == SNOW
                || dominant == DESERT) {

            return dominant;
        }

        return null;
    }

    /**
     * Shore like ocelot: sand plus water in the local column ring.
     */
    public static boolean isShoreConfirmed(
            Location location
    ) {

        return countNearby(location, 10, PetHabitat::isShoreBlock, 3) >= 3
                && countNearby(location, 10, name -> name.equals("WATER")
                || name.equals("BUBBLE_COLUMN"), 2) >= 2;
    }

    /**
     * Desert needs real arid signature — plain sandstone walls on custom
     * maps (forage ruins, temples) must not flip the whole biotope to camel.
     */
    public static boolean isDesertConfirmed(
            Location location
    ) {

        int strong =
                countNearby(
                        location,
                        14,
                        PetHabitat::isStrongDesertBlock,
                        4
                );
        if (strong >= 3) {
            return true;
        }

        int sandstone =
                countNearby(
                        location,
                        14,
                        PetHabitat::isSandstoneBlock,
                        16
                );
        int sand =
                countNearby(
                        location,
                        14,
                        PetHabitat::isLooseDesertSand,
                        8
                );
        // Dense sandstone only counts with actual sand dunes underfoot.
        return sandstone >= 14 && sand >= 6;
    }

    public boolean matches(
            Biome biome
    ) {

        if (this == ANY) {
            return true;
        }

        if (biome == null) {
            return false;
        }

        String id =
                biomeKey(
                        biome
                );

        return switch (this) {

            case JUNGLE ->
                    id.contains("jungle");

            case SHORE ->
                    id.contains("beach")
                            || id.contains("shore")
                            || id.equals("river");

            case MOUNTAIN ->
                    id.contains("peak")
                            || id.contains("slope")
                            || id.equals("meadow")
                            || id.equals("grove")
                            || id.equals("cherry_grove")
                            || id.contains("windswept_hills")
                            || id.contains("windswept_gravelly")
                            || id.contains("windswept_forest");

            case SNOW ->
                    id.contains("snow")
                            || id.contains("frozen")
                            || id.equals("ice_spikes")
                            || id.equals("grove");

            case DESERT ->
                    id.contains("desert")
                            || id.contains("badlands")
                            || id.contains("savanna");

            case DARK ->
                    id.contains("dark_forest")
                            || id.equals("pale_garden");

            case FARM ->
                    id.contains("plains")
                            || id.contains("meadow")
                            || id.contains("sunflower")
                            || id.contains("flower_forest")
                            || id.contains("birch")
                            || id.contains("cherry")
                            || id.equals("river")
                            || id.contains("savanna")
                            || (id.contains("forest")
                            && !id.contains("dark")
                            && !id.contains("pale"))
                            || (id.contains("taiga")
                            && !id.contains("snow"));

            case SWAMP ->
                    id.contains("swamp")
                            || id.contains("mangrove");

            case FOREST ->
                    (id.contains("forest")
                            && !id.contains("dark")
                            && !id.contains("pale")
                            && !id.contains("jungle")
                            && !id.contains("flower"))
                            || id.contains("birch")
                            || (id.contains("taiga")
                            && !id.contains("snow"));

            case FLOWER ->
                    id.contains("flower")
                            || id.contains("meadow")
                            || id.contains("sunflower")
                            || id.contains("cherry");

            case MUSHROOM ->
                    id.contains("mushroom");

            case VILLAGE ->
                    id.contains("plains")
                            || id.contains("savanna")
                            || id.contains("desert")
                            || id.contains("taiga")
                            || id.contains("snow")
                            || id.contains("meadow");

            case LUSH ->
                    id.contains("lush")
                            || id.contains("dripstone")
                            || id.equals("cherry_grove");

            case ELDERVALE ->
                    false;

            default ->
                    true;
        };
    }

    private static boolean isDesertBlock(String name) {
        return isDesertSignatureBlock(name)
                || name.equals("RED_SAND")
                || name.equals("DEAD_BUSH");
    }

    private static boolean isJungleBlock(String name) {
        return name.contains("JUNGLE")
                || name.contains("BAMBOO")
                || name.equals("COCOA")
                || name.equals("VINE")
                || name.equals("MELON")
                || name.equals("MELON_STEM")
                || name.equals("ATTACHED_MELON_STEM")
                || name.equals("MOSSY_COBBLESTONE")
                || name.equals("MOSSY_STONE_BRICKS");
    }

    private static boolean isMountainBlock(String name) {
        return name.contains("ANDESITE")
                || name.contains("DIORITE")
                || name.contains("GRANITE")
                || name.contains("CALCITE")
                || name.contains("TUFF")
                || name.contains("DRIPSTONE")
                || name.equals("END_STONE")
                || name.equals("END_STONE_BRICKS")
                || name.contains("POINTED_DRIPSTONE");
    }

    private static boolean isSpruceBlock(String name) {
        return name.contains("SPRUCE")
                || name.contains("DARK_OAK_LOG")
                || name.contains("DARK_OAK_WOOD")
                || name.contains("DARK_OAK_LEAVES")
                || name.contains("PALE_OAK")
                || name.contains("PALE_MOSS");
    }

    private static boolean isSwampBlock(String name) {
        return name.contains("MANGROVE")
                || name.equals("MUD")
                || name.equals("PACKED_MUD")
                || name.equals("MUDDY_MANGROVE_ROOTS")
                || name.equals("LILY_PAD")
                || name.equals("CLAY")
                || name.contains("FROGLIGHT")
                || name.equals("SLIME_BLOCK")
                || name.equals("SUGAR_CANE")
                || name.contains("MANGROVE_ROOTS");
    }

    private static boolean isForestBlock(String name) {
        return isForestWoodBlock(name)
                || name.equals("PODZOL")
                || name.equals("COARSE_DIRT");
    }

    /** Real woods only — coarse dirt is everywhere on Origin and is not forest. */
    private static boolean isForestWoodBlock(String name) {
        if (name.contains("JUNGLE")
                || name.contains("MANGROVE")
                || name.contains("CHERRY")
                || name.contains("AZALEA")
                || name.contains("SPRUCE")
                || name.contains("CRIMSON")
                || name.contains("WARPED")
                || name.contains("PALE_OAK")) {
            return false;
        }

        return name.contains("OAK_LOG")
                || name.contains("OAK_WOOD")
                || name.contains("BIRCH_LOG")
                || name.contains("BIRCH_WOOD")
                || name.contains("DARK_OAK_LOG")
                || name.contains("DARK_OAK_WOOD")
                || name.contains("OAK_LEAVES")
                || name.contains("BIRCH_LEAVES")
                || name.contains("DARK_OAK_LEAVES")
                || name.contains("ACACIA_LOG")
                || name.contains("ACACIA_WOOD")
                || name.contains("ACACIA_LEAVES");
    }

    private static boolean isFlowerBlock(String name) {
        return name.contains("TULIP")
                || name.contains("ORCHID")
                || name.contains("ALLIUM")
                || name.contains("BLUET")
                || name.contains("DANDELION")
                || name.contains("POPPY")
                || name.contains("CORNFLOWER")
                || name.contains("LILY_OF")
                || name.contains("LILAC")
                || name.contains("ROSE_BUSH")
                || name.contains("PEONY")
                || name.contains("SUNFLOWER")
                || name.contains("OXEYE")
                || name.contains("PINK_PETALS")
                || name.contains("WILDFLOWERS")
                || name.contains("TORCHFLOWER")
                || name.contains("PITCHER")
                || name.contains("CHERRY_LEAVES")
                || name.contains("CHERRY_LOG")
                || name.contains("CHERRY_WOOD")
                || name.contains("CHERRY_SAPLING")
                || name.equals("FLOWERING_AZALEA")
                || name.equals("FLOWERING_AZALEA_LEAVES")
                || name.equals("SPORE_BLOSSOM")
                || name.contains("EYEBLOSSOM")
                || name.equals("PINK_PETALS")
                || name.contains("LEAF_LITTER");
    }

    /**
     * Overworld mushrooms + Nether fungus groves (custom maps often mix both).
     */
    private static boolean isMushroomBlock(String name) {
        if (name.contains("MUSHROOM")
                || name.equals("MYCELIUM")
                || name.equals("SHROOMLIGHT")) {
            return true;
        }
        // Nether fungus biome kit — counts as mushroom habitat on custom isles.
        return name.contains("CRIMSON")
                || name.contains("WARPED")
                || name.contains("NYLIUM")
                || name.contains("NETHER_WART")
                || name.equals("WEEPING_VINES")
                || name.equals("WEEPING_VINES_PLANT")
                || name.equals("TWISTING_VINES")
                || name.equals("TWISTING_VINES_PLANT")
                || name.equals("NETHER_SPROUTS")
                || name.contains("FUNGUS")
                || name.contains("HYPHAE")
                || name.equals("CRIMSON_ROOTS")
                || name.equals("WARPED_ROOTS")
                || name.equals("CRIMSON_STEM")
                || name.equals("WARPED_STEM")
                || name.equals("STRIPPED_CRIMSON_STEM")
                || name.equals("STRIPPED_WARPED_STEM")
                || name.equals("CRIMSON_HYPHAE")
                || name.equals("WARPED_HYPHAE");
    }

    private static boolean isLushBlock(String name) {
        return isStrongLushBlock(name)
                || name.contains("MOSS")
                || name.equals("ROOTED_DIRT")
                || name.equals("HANGING_ROOTS")
                || name.equals("MOSSY_COBBLESTONE")
                || name.equals("MOSSY_STONE_BRICKS");
    }

    /** Azalea / dripleaf / vines — not moss carpet decoration. */
    private static boolean isStrongLushBlock(String name) {
        return name.contains("AZALEA")
                || name.contains("DRIPLEAF")
                || name.equals("SPORE_BLOSSOM")
                || name.contains("CAVE_VINES")
                || name.equals("GLOW_BERRIES")
                || name.contains("BIG_DRIPLEAF")
                || name.contains("SMALL_DRIPLEAF")
                || name.equals("MOSS_BLOCK")
                || name.equals("MOSS_CARPET")
                || name.equals("FLOWERING_AZALEA")
                || name.equals("FLOWERING_AZALEA_LEAVES");
    }

    private static boolean isDesertSignatureBlock(String name) {
        return isStrongDesertBlock(name)
                || isSandstoneBlock(name)
                || isLooseDesertSand(name);
    }

    /** Cactus / terracotta / red sand — not decorative sandstone walls. */
    private static boolean isStrongDesertBlock(String name) {
        return name.equals("CACTUS")
                || name.equals("DEAD_BUSH")
                || name.equals("RED_SAND")
                || name.equals("SUSPICIOUS_SAND")
                || name.contains("TERRACOTTA");
    }

    private static boolean isSandstoneBlock(String name) {
        return name.contains("SANDSTONE");
    }

    private static boolean isLooseDesertSand(String name) {
        return name.equals("SAND")
                || name.equals("RED_SAND")
                || name.equals("SUSPICIOUS_SAND");
    }

    private static boolean isShoreBlock(String name) {
        return name.equals("SAND")
                || name.equals("RED_SAND")
                || name.equals("SUSPICIOUS_SAND")
                || name.equals("GRAVEL")
                || name.contains("CORAL")
                || name.equals("SEA_PICKLE")
                || name.equals("PRISMARINE")
                || name.contains("PRISMARINE");
    }

    private static boolean isSnowBlock(String name) {
        return name.contains("SNOW")
                || name.contains("ICE")
                || name.equals("POWDER_SNOW")
                || name.equals("PACKED_ICE")
                || name.equals("BLUE_ICE")
                || name.equals("FROSTED_ICE");
    }

    /**
     * Surface-focused flower scan — Origin patches are sparse and get
     * missed by coarse 3D stepping around the player Y.
     */
    private static int countFlowerSignals(
            Location location,
            int radius,
            int stopAt
    ) {

        if (location == null
                || location.getWorld() == null
                || radius <= 0) {

            return 0;
        }

        World world =
                location.getWorld();

        int ox =
                location.getBlockX();

        int oz =
                location.getBlockZ();

        int step = 2;

        int hits = 0;

        for (
                int dx = -radius;
                dx <= radius;
                dx += step
        ) {

            for (
                    int dz = -radius;
                    dz <= radius;
                    dz += step
            ) {

                int x =
                        ox + dx;

                int z =
                        oz + dz;

                int groundY =
                        PetWalkSurface.groundY(
                                world,
                                x,
                                z
                        );

                for (
                        int dy = 0;
                        dy <= 2;
                        dy++
                ) {

                    String name =
                            world.getBlockAt(
                                            x,
                                            groundY + dy,
                                            z
                                    )
                                    .getType()
                                    .name();

                    if (isFlowerBlock(name)) {
                        hits++;
                        break;
                    }
                }

                if (stopAt > 0
                        && hits >= stopAt) {
                    return hits;
                }
            }
        }

        return hits;
    }

    private static int countNearby(
            Location location,
            int radius,
            Predicate<String> match
    ) {

        return countNearby(
                location,
                radius,
                match,
                0
        );
    }

    private static int countNearby(
            Location location,
            int radius,
            Predicate<String> match,
            int stopAt
    ) {

        if (location == null
                || location.getWorld() == null
                || radius <= 0
                || match == null) {

            return 0;
        }

        World world =
                location.getWorld();

        int ox =
                location.getBlockX();

        int oy =
                location.getBlockY();

        int oz =
                location.getBlockZ();

        int step = 2;

        int hits = 0;

        for (
                int dx = -radius;
                dx <= radius;
                dx += step
        ) {

            for (
                    int dz = -radius;
                    dz <= radius;
                    dz += step
            ) {

                // Narrow vertical band — full -18..6 melted the tick.
                for (
                        int dy = -6;
                        dy <= 4;
                        dy += step
                ) {

                    String name =
                            world.getBlockAt(
                                            ox + dx,
                                            oy + dy,
                                            oz + dz
                                    )
                                    .getType()
                                    .name();

                    if (match.test(name)) {
                        hits++;
                        if (stopAt > 0
                                && hits >= stopAt) {
                            return hits;
                        }
                    }
                }
            }
        }

        return hits;
    }

    private static boolean nearby(
            Location location,
            int radius,
            Predicate<String> match
    ) {

        return countNearby(
                location,
                radius,
                match
        )
                > 0;
    }

    private static String biomeKey(
            Biome biome
    ) {

        try {

            return biome.getKey()
                    .getKey()
                    .toLowerCase();

        } catch (Throwable ignored) {

            return biome.name()
                    .toLowerCase();
        }
    }
}
