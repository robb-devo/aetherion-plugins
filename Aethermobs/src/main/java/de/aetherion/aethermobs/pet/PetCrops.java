package de.aetherion.aethermobs.pet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class PetCrops {

    private PetCrops() {
    }

    public static boolean nearby(
            Location location,
            int radius
    ) {

        return nearby(
                location,
                radius,
                null
        );
    }

    public static boolean nearbyPotato(
            Location location,
            int radius
    ) {

        return nearby(
                location,
                radius,
                Material.POTATOES
        );
    }

    private static boolean nearby(
            Location location,
            int radius,
            Material only
    ) {

        if (location == null
                || location.getWorld() == null
                || radius <= 0) {

            return false;
        }

        World world =
                location.getWorld();

        int originX =
                location.getBlockX();

        int originZ =
                location.getBlockZ();

        int step =
                radius > 12
                        ? 2
                        : 1;

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
                        originX + dx;

                int z =
                        originZ + dz;

                int groundY =
                        PetWalkSurface.groundY(
                                world,
                                x,
                                z
                        );

                for (
                        int dy = -1;
                        dy <= 3;
                        dy++
                ) {

                    Material type =
                            world.getBlockAt(
                                    x,
                                    groundY + dy,
                                    z
                            )
                                    .getType();

                    if (only != null) {

                        if (type == only) {
                            return true;
                        }

                    } else if (isCropRelated(type)) {

                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean isCropRelated(
            Material type
    ) {

        if (type == null) {
            return false;
        }

        return switch (type) {

            case WHEAT,
                    CARROTS,
                    POTATOES,
                    BEETROOTS,
                    FARMLAND,
                    HAY_BLOCK,
                    PUMPKIN,
                    MELON,
                    PUMPKIN_STEM,
                    MELON_STEM,
                    ATTACHED_PUMPKIN_STEM,
                    ATTACHED_MELON_STEM,
                    SWEET_BERRY_BUSH,
                    TORCHFLOWER_CROP,
                    PITCHER_CROP ->
                    true;

            default ->
                    false;
        };
    }

    public static Location findNearbyCropColumn(
            Location origin,
            int radius
    ) {

        return findNearbyColumn(
                origin,
                radius,
                null
        );
    }

    public static Location findNearbyPotatoColumn(
            Location origin,
            int radius
    ) {

        return findNearbyColumn(
                origin,
                radius,
                Material.POTATOES
        );
    }

    private static Location findNearbyColumn(
            Location origin,
            int radius,
            Material only
    ) {

        if (origin == null
                || origin.getWorld() == null) {

            return null;
        }

        World world =
                origin.getWorld();

        int originX =
                origin.getBlockX();

        int originZ =
                origin.getBlockZ();

        java.util.concurrent.ThreadLocalRandom random =
                java.util.concurrent.ThreadLocalRandom.current();

        for (
                int attempt = 0;
                attempt < 48;
                attempt++
        ) {

            int dx =
                    random.nextInt(
                            -radius,
                            radius + 1
                    );

            int dz =
                    random.nextInt(
                            -radius,
                            radius + 1
                    );

            int x =
                    originX + dx;

            int z =
                    originZ + dz;

            int groundY =
                    PetWalkSurface.groundY(
                            world,
                            x,
                            z
                    );

            Block ground =
                    world.getBlockAt(
                            x,
                            groundY,
                            z
                    );

            Block crop =
                    world.getBlockAt(
                            x,
                            groundY + 1,
                            z
                    );

            if (only != null) {

                if (crop.getType() != only
                        && ground.getType() != only) {

                    continue;
                }

            } else if (!isCropRelated(ground.getType())
                    && !isCropRelated(crop.getType())) {

                continue;
            }

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

            if (!feet.isPassable()
                    || !head.isPassable()
                    || PetWalkSurface.isWatery(feet)
                    || PetWalkSurface.isWatery(head)) {

                continue;
            }

            if (PetWalkSurface.isCanopy(ground.getType())) {
                continue;
            }

            return new Location(
                    world,
                    x + 0.5,
                    groundY + PetEntity.HOVER_HEIGHT,
                    z + 0.5
            );
        }

        return null;
    }
}
