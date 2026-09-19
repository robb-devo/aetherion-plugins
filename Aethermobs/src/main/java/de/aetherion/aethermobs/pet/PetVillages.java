package de.aetherion.aethermobs.pet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class PetVillages {

    private PetVillages() {
    }

    public static boolean nearby(
            Location location,
            int radius
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

        int originY =
                location.getBlockY();

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

                for (
                        int dy = -6;
                        dy <= 8;
                        dy += step
                ) {

                    Material type =
                            world.getBlockAt(
                                            originX + dx,
                                            originY + dy,
                                            originZ + dz
                                    )
                                    .getType();

                    if (isVillageBlock(type)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean isVillageBlock(
            Material type
    ) {

        if (type == null) {
            return false;
        }

        String name =
                type.name();

        if (type == Material.BELL
                || type == Material.LOOM
                || type == Material.COMPOSTER
                || type == Material.BARREL
                || type == Material.SMOKER
                || type == Material.BLAST_FURNACE
                || type == Material.CARTOGRAPHY_TABLE
                || type == Material.FLETCHING_TABLE
                || type == Material.SMITHING_TABLE
                || type == Material.GRINDSTONE
                || type == Material.STONECUTTER
                || type == Material.LECTERN
                || type == Material.BREWING_STAND
                || type == Material.CAULDRON
                || type == Material.WATER_CAULDRON
                || type == Material.LAVA_CAULDRON
                || type == Material.POWDER_SNOW_CAULDRON) {

            return true;
        }

        return name.endsWith("_BED")
                || name.equals("HAY_BLOCK");
    }
}
