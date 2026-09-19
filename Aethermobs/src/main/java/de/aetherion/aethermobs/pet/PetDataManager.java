package de.aetherion.aethermobs.pet;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.model.PetStats;
import de.aetherion.aethermobs.model.PetVariant;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.Rarity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PetDataManager {

    private final AetherMobs plugin;
    private final File dataFolder;

    public PetDataManager(
            AetherMobs plugin
    ) {
        this.plugin = plugin;

        this.dataFolder =
                new File(
                        plugin.getDataFolder(),
                        "pets"
                );

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public void save(
            PlayerPetCollection collection
    ) {

        UUID playerId =
                collection.getOwner();

        File file =
                getPlayerFile(playerId);

        YamlConfiguration config =
                new YamlConfiguration();

        config.set(
                "player",
                playerId.toString()
        );

        config.set(
                "seen",
                new ArrayList<>(
                        collection.getSighted()
                )
        );

        config.set(
                "catch-xp",
                new ArrayList<>(
                        collection.getCatchXpClaimed()
                )
        );

        config.set(
                "pets",
                null
        );

        List<PetInstance> pets =
                collection.getPets();

        for (int i = 0; i < pets.size(); i++) {

            PetInstance pet =
                    pets.get(i);

            String path =
                    "pets." + i;

            config.set(
                    path + ".unique-id",
                    pet.getUniqueId()
                            .toString()
            );

            config.set(
                    path + ".definition",
                    pet.getDefinition()
                            .getId()
            );

            config.set(
                    path + ".rarity",
                    pet.getRarity()
                            .name()
            );

            config.set(
                    path + ".variant",
                    pet.getVariant()
                            .name()
            );

            config.set(
                    path + ".level",
                    pet.getLevel()
            );

            config.set(
                    path + ".experience",
                    pet.getExperience()
            );

            config.set(
                    path + ".equipped",
                    collection.isEquipped(pet)
            );

            PetStats stats =
                    pet.getStats();

            config.set(
                    path + ".stats.core-stat",
                    stats.getCoreStat()
                            .name()
            );

            config.set(
                    path + ".stats.core-value",
                    stats.getCoreValue()
            );

            for (
                    var entry :
                    stats.getBonusStats()
                            .entrySet()
            ) {

                config.set(
                        path
                                + ".stats.bonus."
                                + entry.getKey()
                                .name(),
                        entry.getValue()
                );
            }
        }

        try {

            config.save(file);

        } catch (IOException exception) {

            plugin.getLogger().severe(
                    "Could not save pets for player "
                            + playerId
            );

            exception.printStackTrace();
        }
    }

    public PlayerPetCollection load(
            UUID playerId
    ) {

        File file =
                getPlayerFile(playerId);

        PlayerPetCollection collection =
                new PlayerPetCollection(
                        playerId
                );

        if (!file.exists()) {
            return collection;
        }

        YamlConfiguration config =
                YamlConfiguration.loadConfiguration(
                        file
                );

        loadSighted(
                config,
                collection
        );

        loadCatchXp(
                config,
                collection
        );

        ConfigurationSection petsSection =
                config.getConfigurationSection(
                        "pets"
                );

        if (petsSection == null) {
            return collection;
        }

        for (String key :
                petsSection.getKeys(false)) {

            String path =
                    "pets." + key;

            try {

                PetInstance pet =
                        loadPet(
                                config,
                                path
                        );

                if (pet == null) {
                    continue;
                }

                collection.addPet(pet);

                boolean equipped =
                        config.getBoolean(
                                path + ".equipped",
                                false
                        );

                if (equipped) {

                    collection.equipPet(
                            pet
                    );
                }

            } catch (Exception exception) {

                plugin.getLogger().warning(
                        "Could not load pet "
                                + key
                                + " for player "
                                + playerId
                );

                exception.printStackTrace();
            }
        }

        return collection;
    }

    private void loadSighted(
            YamlConfiguration config,
            PlayerPetCollection collection
    ) {

        List<String> seen =
                config.getStringList(
                        "seen"
                );

        for (String id : seen) {

            collection.markSighted(
                    id
            );
        }
    }

    private void loadCatchXp(
            YamlConfiguration config,
            PlayerPetCollection collection
    ) {

        List<String> claimed =
                config.getStringList(
                        "catch-xp"
                );

        for (String id : claimed) {

            collection.claimFirstCatchXp(
                    id
            );
        }
    }

    private PetInstance loadPet(
            YamlConfiguration config,
            String path
    ) {

        String uniqueIdString =
                config.getString(
                        path + ".unique-id"
                );

        String definitionId =
                config.getString(
                        path + ".definition"
                );

        String rarityName =
                config.getString(
                        path + ".rarity"
                );

        String variantName =
                config.getString(
                        path + ".variant"
                );

        if (uniqueIdString == null
                || definitionId == null
                || rarityName == null
                || variantName == null) {

            return null;
        }

        UUID uniqueId =
                UUID.fromString(
                        uniqueIdString
                );

        PetDefinition definition =
                plugin.getPetRegistry()
                        .get(definitionId);

        if (definition == null) {

            plugin.getLogger().warning(
                    "Unknown pet definition: "
                            + definitionId
            );

            return null;
        }

        Rarity rarity =
                Rarity.valueOf(
                        rarityName
                );

        PetVariant variant =
                PetVariant.valueOf(
                        variantName
                );

        int level =
                config.getInt(
                        path + ".level",
                        1
                );

        long experience =
                config.getLong(
                        path + ".experience",
                        0L
                );

        String coreStatName =
                config.getString(
                        path + ".stats.core-stat"
                );

        if (coreStatName == null) {
            return null;
        }

        ItemCapability coreStat =
                ItemCapability.valueOf(
                        coreStatName
                );

        double coreValue =
                config.getDouble(
                        path
                                + ".stats.core-value",
                        0.0
                );

        PetStats stats =
                new PetStats(
                        coreStat
                );

        stats.setCoreValue(
                coreValue
        );

        ConfigurationSection bonusSection =
                config.getConfigurationSection(
                        path + ".stats.bonus"
                );

        if (bonusSection != null) {

            for (String key :
                    bonusSection.getKeys(false)) {

                ItemCapability capability =
                        ItemCapability.valueOf(
                                key
                        );

                double value =
                        bonusSection.getDouble(
                                key
                        );

                stats.setBonusStat(
                        capability,
                        value
                );
            }
        }

        return new PetInstance(
                uniqueId,
                definition,
                rarity,
                variant,
                level,
                experience,
                stats
        );
    }

    private File getPlayerFile(
            UUID playerId
    ) {

        return new File(
                dataFolder,
                playerId + ".yml"
        );
    }
}