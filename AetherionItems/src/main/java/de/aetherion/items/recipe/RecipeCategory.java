package de.aetherion.items.recipe;

/*
 * =========================================================
 * RECIPE CATEGORY
 * =========================================================
 *
 * Kategorien für das Aetherion Recipe Book.
 *
 * Neue Kategorien können später einfach ergänzt werden.
 * =========================================================
 */

public enum RecipeCategory {

    /*
     * =========================================================
     * MINING
     * =========================================================
     */

    MINING(
            "Mining",
            "⛏"
    ),


    /*
     * =========================================================
     * RESOURCES
     * =========================================================
     */

    RESOURCES(
            "Resources",
            "▣"
    ),


    /*
     * =========================================================
     * COMBAT
     * =========================================================
     */

    COMBAT(
            "Combat",
            "⚔"
    ),


    /*
     * =========================================================
     * ARMOR
     * =========================================================
     */

    ARMOR(
            "Armor",
            "🛡"
    ),


    FARMING(
            "Farming",
            "🌾"
    ),


    FORAGING(
            "Foraging",
            "🪓"
    ),


    FISHING(
            "Fishing",
            "🎣"
    ),


    BOOSTERS(
            "Boosters",
            "✦"
    ),


    CHARMS(
            "Charms",
            "★"
    ),


    /*
     * =========================================================
     * AETHER MOBS
     * =========================================================
     *
     * Platzhalter für das zukünftige Pet / Follower-System.
     *
     * Die Kategorie existiert bereits im Recipe Book,
     * kann aber über eine UnlockRequirement gesperrt werden.
     *
     * =========================================================
     */

    AETHER_MOBS(
            "Aethermobs",
            "🐾"
    );


    /*
     * =========================================================
     * DATA
     * =========================================================
     */

    private final String displayName;

    private final String icon;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    RecipeCategory(
            String displayName,
            String icon
    ) {

        this.displayName = displayName;
        this.icon = icon;
    }


    /*
     * =========================================================
     * DISPLAY NAME
     * =========================================================
     */

    public String getDisplayName() {

        return displayName;
    }


    /*
     * =========================================================
     * ICON
     * =========================================================
     */

    public String getIcon() {

        return icon;
    }
}