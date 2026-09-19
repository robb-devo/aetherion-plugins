package de.aetherion.items.model;

public enum BoosterType {

    /*
     * =========================================================
     * CORE BOOSTERS
     * =========================================================
     */

    COAL,
    IRON,
    GOLD,
    DIAMOND,


    /*
     * =========================================================
     * SPECIAL BOOSTERS
     * =========================================================
     *
     * Emerald:
     * -> Mining / Foraging Spread
     *
     * Redstone:
     * -> Attack Spread / zusätzliche Ziele
     *
     * Lapis:
     * -> Health
     */

    EMERALD,
    REDSTONE,
    LAPIS,
    GLOWSTONE,
    WHEAT,
    CARROT,
    OAK,
    BIRCH;


    public boolean isCore() {
        return this == COAL
                || this == IRON
                || this == GOLD
                || this == DIAMOND;
    }

    public boolean isSpecial() {
        return !isCore();
    }

    public ItemCapability getSpecialCapability() {
        return switch (this) {
            case EMERALD -> ItemCapability.SPREAD;
            case REDSTONE -> ItemCapability.ATTACK_SPREAD;
            case LAPIS -> ItemCapability.HEALTH;
            case GLOWSTONE -> ItemCapability.SPEED;
            case WHEAT -> ItemCapability.PET_CATCH_RATE;
            case CARROT -> ItemCapability.HARVEST_SPREAD;
            case OAK -> ItemCapability.CRIT_DAMAGE;
            case BIRCH -> ItemCapability.CRIT_CHANCE;
            default -> null;
        };
    }

    public String displayName() {
        return switch (this) {
            case COAL -> "Coal";
            case IRON -> "Iron";
            case GOLD -> "Gold";
            case DIAMOND -> "Diamond";
            case EMERALD -> "Emerald";
            case REDSTONE -> "Redstone";
            case LAPIS -> "Lapis";
            case GLOWSTONE -> "Glowstone";
            case WHEAT -> "Wheat";
            case CARROT -> "Carrot";
            case OAK -> "Oak";
            case BIRCH -> "Birch";
        };
    }

    public String loreColor() {
        return switch (this) {
            case COAL -> "§8";
            case IRON -> "§7";
            case GOLD -> "§6";
            case DIAMOND -> "§b";
            case EMERALD -> "§a";
            case REDSTONE -> "§c";
            case LAPIS -> "§9";
            case GLOWSTONE -> "§e";
            case WHEAT -> "§6";
            case CARROT -> "§6";
            case OAK -> "§6";
            case BIRCH -> "§f";
        };
    }

    public String emblem() {
        return "●";
    }
}