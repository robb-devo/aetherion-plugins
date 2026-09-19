package de.aetherion.items.model;

import de.aetherion.items.core.BoosterLimits;
import de.aetherion.items.item.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class BoosterStats {

    /*
     * =========================================================
     * CORE BOOSTER FLAT
     * =========================================================
     *
     * Added to Mining Power, Fortune, Damage and Defense.
     * Percent stacking exploded once items already had big stats.
     */

    public static double getCoreFlat(BoosterType boosterType, Rarity rarity) {
        return switch (boosterType) {
            case COAL -> switch (rarity) {
                case COMMON -> 1.0;
                case UNCOMMON -> 1.5;
                case RARE -> 2.0;
                case EPIC -> 2.5;
                case LEGENDARY -> 3.0;
                case MYTHIC, AETHERED -> 4.0;
            };
            case IRON -> switch (rarity) {
                case COMMON -> 1.5;
                case UNCOMMON -> 2.25;
                case RARE -> 3.0;
                case EPIC -> 4.0;
                case LEGENDARY -> 5.0;
                case MYTHIC, AETHERED -> 6.0;
            };
            case GOLD -> switch (rarity) {
                case COMMON -> 2.5;
                case UNCOMMON -> 3.5;
                case RARE -> 5.0;
                case EPIC -> 6.5;
                case LEGENDARY -> 8.0;
                case MYTHIC, AETHERED -> 10.0;
            };
            case DIAMOND -> switch (rarity) {
                case COMMON -> 3.5;
                case UNCOMMON -> 5.0;
                case RARE -> 7.0;
                case EPIC -> 9.0;
                case LEGENDARY -> 11.0;
                case MYTHIC, AETHERED -> 14.0;
            };
            case EMERALD, REDSTONE, LAPIS, GLOWSTONE, WHEAT, CARROT, OAK, BIRCH -> 0.0;
        };
    }

    public static double getPercentage(BoosterType boosterType, Rarity rarity) {
        return getCoreFlat(boosterType, rarity);
    }


    /*
     * =========================================================
     * SPECIAL BOOSTER WERTE
     * =========================================================
     *
     * Diese Werte sind KEINE Prozentwerte.
     *
     * Added directly onto the special stat. Not compounded.
     */

    public static double getSpecialStat(
            BoosterType boosterType,
            Rarity rarity
    ) {

        return switch (boosterType) {

            /*
             * =====================================================
             * EMERALD
             * =====================================================
             *
             * Mining / Foraging Spread
             */

            case EMERALD -> switch (rarity) {

                case COMMON -> 4.0;
                case UNCOMMON -> 6.0;
                case RARE -> 9.0;
                case EPIC -> 12.0;
                case LEGENDARY -> 16.0;
                case MYTHIC, AETHERED -> 20.0;
            };


            /*
             * =====================================================
             * REDSTONE
             * =====================================================
             *
             * Attack Spread
             */

            case REDSTONE -> switch (rarity) {

                case COMMON -> 4.0;
                case UNCOMMON -> 6.0;
                case RARE -> 9.0;
                case EPIC -> 12.0;
                case LEGENDARY -> 16.0;
                case MYTHIC, AETHERED -> 20.0;
            };


            /*
             * =====================================================
             * LAPIS
             * =====================================================
             *
             * Health
             */

            case LAPIS -> switch (rarity) {

                case COMMON -> 4.0;
                case UNCOMMON -> 6.0;
                case RARE -> 9.0;
                case EPIC -> 12.0;
                case LEGENDARY -> 16.0;
                case MYTHIC, AETHERED -> 20.0;
            };


            case GLOWSTONE -> switch (rarity) {

                case COMMON -> 1.0;
                case UNCOMMON -> 1.5;
                case RARE -> 2.0;
                case EPIC -> 2.5;
                case LEGENDARY -> 3.0;
                case MYTHIC, AETHERED -> 4.0;
            };


            case WHEAT -> switch (rarity) {

                case COMMON -> 1.0;
                case UNCOMMON -> 1.5;
                case RARE -> 2.0;
                case EPIC -> 3.0;
                case LEGENDARY -> 4.0;
                case MYTHIC, AETHERED -> 5.0;
            };


            case CARROT -> switch (rarity) {

                case COMMON -> 4.0;
                case UNCOMMON -> 6.0;
                case RARE -> 9.0;
                case EPIC -> 12.0;
                case LEGENDARY -> 16.0;
                case MYTHIC, AETHERED -> 20.0;
            };


            case OAK -> switch (rarity) {

                case COMMON -> 4.0;
                case UNCOMMON -> 6.0;
                case RARE -> 9.0;
                case EPIC -> 12.0;
                case LEGENDARY -> 16.0;
                case MYTHIC, AETHERED -> 20.0;
            };


            case BIRCH -> switch (rarity) {

                case COMMON -> 0.5;
                case UNCOMMON -> 0.75;
                case RARE -> 1.0;
                case EPIC -> 1.5;
                case LEGENDARY -> 2.0;
                case MYTHIC, AETHERED -> 2.5;
            };


            case COAL,
                 IRON,
                 GOLD,
                 DIAMOND -> 0.0;
        };
    }


    /*
     * =========================================================
     * BOOSTER ITEM LORE
     * =========================================================
     */

    public static List<String> createItemLore(BoosterType boosterType) {
        List<String> lore = new ArrayList<>();
        boolean core = boosterType.isCore();

        lore.add(core ? "§7✦ §fBooster" : "§7✦ §eSpecial Booster");
        lore.add("");
        lore.add("§7Stat: §f" + statLabel(boosterType));
        lore.add("§7Applies to: §f" + appliesTo(boosterType));
        lore.add("");
        lore.add(core
                ? "§7Adds a flat amount to compatible stats:"
                : "§7Adds a flat amount to the stat:");
        lore.add("");

        boolean percent = !core && loreAsPercent(boosterType);
        for (Rarity rarity : Rarity.values()) {
            double value = core
                    ? getCoreFlat(boosterType, rarity)
                    : getSpecialStat(boosterType, rarity);
            lore.add(rarity.getChatColor()
                    + rarity.name()
                    + ": §a+"
                    + ItemLore.formatStat(value)
                    + (percent ? "%" : ""));
        }

        lore.add("");
        lore.add("§8" + BoosterLimits.MAX_TOTAL + " boosters total per item.");
        return lore;
    }

    public static String statLabel(BoosterType boosterType) {
        return switch (boosterType) {
            case COAL, IRON, GOLD, DIAMOND -> "Mining Power, Fortune, Damage, Defense";
            case EMERALD -> "Spread";
            case REDSTONE -> "Attack Spread";
            case LAPIS -> "Health";
            case GLOWSTONE -> "Speed";
            case WHEAT -> "Pet Catch Rate";
            case CARROT -> "Harvest";
            case OAK -> "Crit Damage";
            case BIRCH -> "Crit Chance";
        };
    }

    private static boolean loreAsPercent(BoosterType boosterType) {
        return boosterType == BoosterType.GLOWSTONE
                || boosterType == BoosterType.WHEAT
                || boosterType == BoosterType.OAK
                || boosterType == BoosterType.BIRCH;
    }

    public static String appliesTo(BoosterType boosterType) {
        return switch (boosterType) {
            case COAL, IRON, GOLD, DIAMOND -> "Tools, weapons and armor with those stats";
            case EMERALD -> "Items with Spread";
            case REDSTONE -> "Weapons and combat armor";
            case LAPIS -> "Armor with Health";
            case GLOWSTONE -> "All boots";
            case WHEAT -> "Catcher armor";
            case CARROT -> "Farming tools and armor";
            case OAK, BIRCH -> "Weapons and combat chestplates";
        };
    }

    /*
     * =========================================================
     * PRIVATE CONSTRUCTOR
     * =========================================================
     */

    private BoosterStats() {
    }
}