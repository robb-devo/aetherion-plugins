package de.aetherion.aethermobs.pet;

import org.bukkit.Color;
import org.bukkit.Sound;

/**
 * Player-facing identity of a pet biotope: place name, colour, one ambient sound.
 * {@link PetHabitat#getDisplayName()} stays the Aetherlex "where to look" hint.
 */
public final class HabitatPresentation {

    private HabitatPresentation() {
    }

    public static String placeName(PetHabitat habitat) {
        if (habitat == null) {
            return "Wilds";
        }
        return switch (habitat) {
            case JUNGLE -> "Jungle";
            case SHORE -> "Shoreline";
            case MOUNTAIN -> "Highlands";
            case SNOW -> "Snowfields";
            case DESERT -> "Dunes";
            case DARK -> "Dark Grove";
            case FARM -> "Farmland";
            case SWAMP -> "Swamp";
            case FOREST -> "Woodland";
            case FLOWER -> "Meadow";
            case MUSHROOM -> "Mushroom Fields";
            case VILLAGE -> "Village";
            case LUSH -> "Lush Hollow";
            case ELDERVALE -> "Eldervale";
            case ANY -> "Wilds";
        };
    }

    /** Legacy colour code — readable on titles (no dark grey). */
    public static String color(PetHabitat habitat) {
        if (habitat == null) {
            return "§f";
        }
        return switch (habitat) {
            case JUNGLE, LUSH -> "§a";
            case SHORE -> "§3";
            case MOUNTAIN -> "§7";
            case SNOW, ELDERVALE -> "§b";
            case DESERT -> "§e";
            case DARK -> "§9";
            case FARM -> "§6";
            case SWAMP, FOREST -> "§2";
            case FLOWER -> "§d";
            case MUSHROOM -> "§c";
            case VILLAGE, ANY -> "§f";
        };
    }

    /** One line of place-writing for the discover beat. */
    public static String tagline(PetHabitat habitat) {
        if (habitat == null) {
            return "Open country.";
        }
        return switch (habitat) {
            case JUNGLE -> "Vines and bamboo. Things watch from the canopy.";
            case SHORE -> "Where sand meets water. Waders and swimmers gather.";
            case MOUNTAIN -> "Stone, tuff and thin air. Climbers live up here.";
            case SNOW -> "Snow and ice. Only the stubborn stay warm.";
            case DESERT -> "Sandstone and cactus. The heat keeps secrets.";
            case DARK -> "Spruce shadow. Quiet pets prefer it dim.";
            case FARM -> "Crops and furrows. The tame and the greedy both visit.";
            case SWAMP -> "Mud, mangrove and lily pads. Everything croaks.";
            case FOREST -> "Oak and birch. The oldest paths wind through here.";
            case FLOWER -> "Petals everywhere. Wings follow the bloom.";
            case MUSHROOM -> "Caps and mycelium. Spores drift, pets follow.";
            case VILLAGE -> "Bells and workstations. Some pets like company.";
            case LUSH -> "Moss, azalea and dripleaf. The hollow breathes.";
            case ELDERVALE -> "The mining island. Cave, sky and water pets share it.";
            case ANY -> "Open country.";
        };
    }

    public static String coloredName(PetHabitat habitat) {
        return color(habitat) + placeName(habitat);
    }

    /** Dust tint for the one-shot discover ring. */
    public static Color dust(PetHabitat habitat) {
        if (habitat == null) {
            return Color.WHITE;
        }
        return switch (habitat) {
            case JUNGLE -> Color.fromRGB(96, 200, 72);
            case LUSH -> Color.fromRGB(140, 214, 96);
            case SHORE -> Color.fromRGB(96, 196, 204);
            case MOUNTAIN -> Color.fromRGB(176, 176, 184);
            case SNOW -> Color.fromRGB(214, 236, 255);
            case ELDERVALE -> Color.fromRGB(120, 210, 230);
            case DESERT -> Color.fromRGB(232, 206, 128);
            case DARK -> Color.fromRGB(96, 120, 176);
            case FARM -> Color.fromRGB(224, 176, 72);
            case SWAMP -> Color.fromRGB(96, 128, 72);
            case FOREST -> Color.fromRGB(72, 160, 80);
            case FLOWER -> Color.fromRGB(240, 150, 210);
            case MUSHROOM -> Color.fromRGB(210, 80, 80);
            case VILLAGE, ANY -> Color.fromRGB(236, 228, 210);
        };
    }

    /** A single quiet sound that says "you are somewhere" — layered under the discover chime. */
    public static Sound ambience(PetHabitat habitat) {
        if (habitat == null) {
            return Sound.BLOCK_GRASS_STEP;
        }
        return switch (habitat) {
            case JUNGLE, LUSH -> Sound.BLOCK_AZALEA_LEAVES_STEP;
            case SHORE -> Sound.AMBIENT_UNDERWATER_EXIT;
            case MOUNTAIN -> Sound.BLOCK_TUFF_STEP;
            case SNOW -> Sound.BLOCK_POWDER_SNOW_STEP;
            case ELDERVALE -> Sound.BLOCK_AMETHYST_CLUSTER_STEP;
            case DESERT -> Sound.BLOCK_SAND_STEP;
            case DARK -> Sound.BLOCK_CHERRY_WOOD_STEP;
            case FARM -> Sound.ITEM_CROP_PLANT;
            case SWAMP -> Sound.BLOCK_MUD_STEP;
            case FOREST -> Sound.BLOCK_WOOD_STEP;
            case FLOWER -> Sound.BLOCK_PINK_PETALS_STEP;
            case MUSHROOM -> Sound.BLOCK_FUNGUS_STEP;
            case VILLAGE -> Sound.BLOCK_BELL_RESONATE;
            case ANY -> Sound.BLOCK_GRASS_STEP;
        };
    }

    /** Biotopes a player can discover (everything but the catch-all). */
    public static int discoverableCount() {
        return PetHabitat.values().length - 1;
    }
}
