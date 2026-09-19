package de.aetherion.aethermobs.pet;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.skill.PetSkill;
import de.aetherion.items.manager.CompactBonusSource;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class PetCompactBonus implements CompactBonusSource {

    private final AetherMobs plugin;

    public PetCompactBonus(
            AetherMobs plugin
    ) {
        this.plugin = plugin;
    }

    @Override
    public double extraCompactChance(
            Player player,
            Material drop
    ) {

        PetInstance pet =
                equipped(player);

        if (pet == null) {
            return 0.0;
        }

        PetSkill skill =
                PetSkill.fromPet(
                        pet
                );

        if (!skill.matchesCrop(drop)) {
            return 0.0;
        }

        return PetSkill.cropCompactChance(
                pet.getRarity(),
                pet.getLevel(),
                skill
        );
    }

    @Override
    public double extraCompactedUpgradeChance(
            Player player,
            Material drop
    ) {

        PetInstance pet =
                equipped(player);

        if (pet == null) {
            return 0.0;
        }

        PetSkill skill =
                PetSkill.fromPet(
                        pet
                );

        if (!skill.matchesCrop(drop)) {
            return 0.0;
        }

        return PetSkill.cropCompactedUpgrade(
                pet.getRarity(),
                pet.getLevel(),
                skill
        );
    }

    private PetInstance equipped(
            Player player
    ) {

        if (player == null
                || plugin == null) {

            return null;
        }

        PlayerPetCollection collection =
                plugin.getPetCollection(
                        player
                );

        if (collection == null) {
            return null;
        }

        return collection.getEquippedPet();
    }
}
