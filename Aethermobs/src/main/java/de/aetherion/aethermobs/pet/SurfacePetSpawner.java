package de.aetherion.aethermobs.pet;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.ThreadLocalRandom;

public final class SurfacePetSpawner {

    private static final int LOCATION_ATTEMPTS = 48;

    private SurfacePetSpawner() {
    }

    public static Location findLocation(
            World world,
            Location center
    ) {
        if (
                world == null
                        || center == null
        ) {
            return null;
        }

        Location exact =
                tryColumn(
                        world,
                        center.getBlockX(),
                        center.getBlockZ()
                );

        if (exact != null) {
            return exact;
        }

        ThreadLocalRandom random =
                ThreadLocalRandom.current();

        for (
                int attempt = 0;
                attempt < LOCATION_ATTEMPTS;
                attempt++
        ) {

            double angle =
                    random.nextDouble(
                            0.0,
                            Math.PI * 2.0
                    );

            double distance =
                    random.nextDouble(
                            1.0,
                            8.0
                    );

            int x =
                    center.getBlockX()
                            + (int) Math.round(
                            Math.cos(angle)
                                    * distance
                    );

            int z =
                    center.getBlockZ()
                            + (int) Math.round(
                            Math.sin(angle)
                                    * distance
                    );

            Location found =
                    tryColumn(
                            world,
                            x,
                            z
                    );

            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private static Location tryColumn(
            World world,
            int x,
            int z
    ) {

        int y =
                PetWalkSurface.groundY(
                        world,
                        x,
                        z
                );

        Block ground =
                world.getBlockAt(
                        x,
                        y,
                        z
                );

        if (!ground.getType().isSolid()) {
            return null;
        }

        if (PetWalkSurface.isWatery(ground)
                || PetWalkSurface.isCanopy(ground.getType())) {
            return null;
        }

        Block feet =
                world.getBlockAt(
                        x,
                        y + 1,
                        z
                );

        Block head =
                world.getBlockAt(
                        x,
                        y + 2,
                        z
                );

        if (!feet.isPassable()
                || !head.isPassable()
                || PetWalkSurface.isWatery(feet)
                || PetWalkSurface.isWatery(head)) {

            return null;
        }

        if (!PetWalkSurface.isDrySurfaceColumn(world, x, z)) {
            return null;
        }

        return new Location(
                world,
                x + 0.5,
                y + PetEntity.HOVER_HEIGHT,
                z + 0.5
        );
    }
}
