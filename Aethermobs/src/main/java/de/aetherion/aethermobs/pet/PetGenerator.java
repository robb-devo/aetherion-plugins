package de.aetherion.aethermobs.pet;

import de.aetherion.aethermobs.model.PetStats;
import de.aetherion.aethermobs.model.PetVariant;
import de.aetherion.items.item.AccessoryItems;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.Rarity;

import org.bukkit.Location;

import java.util.Random;

public class PetGenerator {

    private final Random random;

    public PetGenerator() {

        this.random =
                new Random();
    }

    public PetInstance generate(
            PetDefinition definition
    ) {
        return generate(definition, null);
    }

    public PetInstance generate(
            PetDefinition definition,
            Location at
    ) {
        return generate(definition, at, null);
    }

    public PetInstance generate(
            PetDefinition definition,
            Location at,
            Rarity forcedRarity
    ) {

        Rarity rarity =
                forcedRarity != null
                        && definition.hasRarity(forcedRarity)
                        ? forcedRarity
                        : rollRarity(
                        definition
                );

        PetVariant variant =
                rollVariant(at);

        PetInstance instance =
                new PetInstance(
                        definition,
                        rarity,
                        variant
                );

        if (definition.rollsRandomCoreStat()) {
            ItemCapability[] capabilities =
                    ItemCapability.petBonusStats();
            ItemCapability core =
                    capabilities[
                            random.nextInt(
                                    capabilities.length
                            )
                            ];
            instance.setStats(
                    new PetStats(
                            core
                    )
            );
        }

        rollCoreStat(
                instance
        );

        if (!definition.isPercentBonus()) {
            rollSignatureStat(
                    instance
            );
            rollBonusStats(
                    instance
            );
        }

        /*
         * =====================================================
         * SHINY BONUS
         * =====================================================
         *
         * Shiny is an independent special variant.
         *
         * It does NOT change the normal rarity.
         *
         * Shiny stats are simply doubled.
         */

        if (instance.getVariant()
                == PetVariant.SHINY) {

            applyShinyMultiplier(
                    instance
            );
        }

        return instance;
    }

    private Rarity rollRarity(
            PetDefinition definition
    ) {

        double totalWeight =
                0.0;

        for (
                PetRarityConfig config :
                definition.getRarityConfigs().values()
        ) {

            totalWeight +=
                    config.getRarityWeight();
        }

        double roll =
                random.nextDouble()
                        * totalWeight;

        double currentWeight =
                0.0;

        for (
                PetRarityConfig config :
                definition.getRarityConfigs().values()
        ) {

            currentWeight +=
                    config.getRarityWeight();

            if (roll <= currentWeight) {

                return config.getRarity();
            }
        }

        return definition
                .getRarityConfigs()
                .values()
                .iterator()
                .next()
                .getRarity();
    }

    private PetVariant rollVariant(Location at) {

        double roll =
                random.nextDouble();

        /*
         * 1 / 4096 normally.
         * Shiny Charm in off-hand nearby raises this.
         */

        if (roll < AccessoryItems.shinyChanceAt(at)) {

            return PetVariant.SHINY;
        }

        return PetVariant.NORMAL;
    }

    private void applyShinyMultiplier(
            PetInstance instance
    ) {

        PetStats stats =
                instance.getStats();

        /*
         * =====================================================
         * CORE STAT
         * =====================================================
         */

        stats.setCoreValue(
                stats.getCoreValue()
                        * de.aetherion.aethermobs.model.PetBalance.SHINY_MULTIPLIER
        );

        /*
         * =====================================================
         * BONUS STATS
         * =====================================================
         */

        for (
                ItemCapability capability :
                stats.getBonusStats()
                        .keySet()
        ) {

            double value =
                    stats.getBonusStat(
                            capability
                    );

            stats.setBonusStat(
                    capability,
                    value * de.aetherion.aethermobs.model.PetBalance.SHINY_MULTIPLIER
            );
        }
    }

    private void rollCoreStat(
            PetInstance instance
    ) {

        PetRarityConfig config =
                instance.getDefinition()
                        .getRarityConfig(
                                instance.getRarity()
                        );

        double value =
                randomDouble(
                        config.getCoreMin(),
                        config.getCoreMax()
                );

        instance.getStats()
                .setCoreValue(
                        value
                );
    }

    private void rollSignatureStat(
            PetInstance instance
    ) {

        ItemCapability signature =
                instance.getDefinition()
                        .getSignatureStat();

        if (signature == null
                || signature == instance.getStats().getCoreStat()) {
            return;
        }

        PetRarityConfig config =
                instance.getDefinition()
                        .getRarityConfig(
                                instance.getRarity()
                        );

        if (config == null) {
            return;
        }

        instance.getStats()
                .setBonusStat(
                        signature,
                        randomDouble(
                                config.getCoreMin(),
                                config.getCoreMax()
                        )
                );
    }

    private void rollBonusStats(
            PetInstance instance
    ) {

        int amount =
                getBonusStatAmount(
                        instance.getRarity()
                );

        PetStats stats =
                instance.getStats();

        if (instance.getDefinition().getSignatureStat() != null
                && stats.getBonusStats().containsKey(
                        instance.getDefinition().getSignatureStat()
                )) {
            amount = Math.max(0, amount - 1);
        }

        for (
                int i = 0;
                i < amount;
                i++
        ) {

            ItemCapability capability =
                    rollBonusCapability(
                            instance
                    );

            double value =
                    rollBonusValue(
                            instance.getRarity()
                    );

            stats.setBonusStat(
                    capability,
                    value
            );
        }
    }

    private int getBonusStatAmount(
            Rarity rarity
    ) {

        return switch (rarity) {

            case COMMON ->
                    1;

            case UNCOMMON ->
                    1;

            case RARE ->
                    2;

            case EPIC ->
                    2;

            case LEGENDARY ->
                    3;

            case MYTHIC ->
                    3;

            case AETHERED ->
                    4;
        };
    }

    private ItemCapability rollBonusCapability(
            PetInstance instance
    ) {

        ItemCapability[] capabilities =
                ItemCapability.petBonusStats();

        ItemCapability selected;

        do {

            selected =
                    capabilities[
                            random.nextInt(
                                    capabilities.length
                            )
                            ];

        } while (
                selected
                        == instance.getStats()
                        .getCoreStat()

                        || instance.getStats()
                        .getBonusStats()
                        .containsKey(
                                selected
                        )
        );

        return selected;
    }

    private double rollBonusValue(
            Rarity rarity
    ) {

        double min;
        double max;

        switch (rarity) {

            case COMMON -> {

                min = 1.5;
                max = 3.0;
            }

            case UNCOMMON -> {

                min = 2.5;
                max = 4.5;
            }

            case RARE -> {

                min = 4.0;
                max = 7.0;
            }

            case EPIC -> {

                min = 6.0;
                max = 10.0;
            }

            case LEGENDARY -> {

                min = 8.0;
                max = 13.0;
            }

            case MYTHIC -> {

                min = 10.0;
                max = 16.0;
            }

            case AETHERED -> {

                min = 12.0;
                max = 18.0;
            }

            default -> {

                min = 4.0;
                max = 8.0;
            }
        }

        return randomDouble(
                min,
                max
        );
    }

    private double randomDouble(
            double min,
            double max
    ) {

        return min
                + (
                random.nextDouble()
                        * (max - min)
        );
    }
}