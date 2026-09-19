package de.aetherion.aethermobs.placeholder;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.aethermobs.pet.PlayerPetCollection;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.entity.Player;

import java.util.Locale;

public final class PetPlaceholderExpansion extends PlaceholderExpansion {

    private final AetherMobs plugin;

    public PetPlaceholderExpansion(AetherMobs plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "aethermobs";
    }

    @Override
    public String getAuthor() {
        return "Aetherion";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) {
            return "";
        }
        PetInstance pet = equipped(player);
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "pet_name", "pet" -> pet == null || pet.getDefinition() == null
                    ? "§7None"
                    : pet.getDefinition().getDisplayName();
            case "pet_level" -> pet == null ? "-" : Integer.toString(pet.getLevel());
            case "pet_xp", "pet_exp" -> pet == null ? "0" : Long.toString(pet.getExperience());
            case "pet_xp_need", "pet_exp_need" -> pet == null ? "0" : Long.toString(pet.getRequiredExperience(pet.getLevel()));
            case "pet_bar", "pet_xpbar" -> bar(pet);
            default -> null;
        };
    }

    private PetInstance equipped(Player player) {
        PlayerPetCollection collection = plugin.getPetCollection(player);
        return collection == null ? null : collection.getEquippedPet();
    }

    private static String bar(PetInstance pet) {
        if (pet == null) {
            return "§8▌▌▌▌▌▌▌▌";
        }
        if (pet.getLevel() >= pet.getMaxLevel()) {
            return "§6MAX";
        }
        int filled = (int) Math.round(Math.max(0.0d, Math.min(1.0d, pet.getExperienceProgress())) * 8.0d);
        return "§d" + "▌".repeat(filled) + "§8" + "▌".repeat(8 - filled);
    }
}
