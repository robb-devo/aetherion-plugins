package de.aetherion.guilds.model;

import java.util.Locale;

public enum GuildRank {
    LEADER("§6Leader", 5),
    VICE_PRESIDENT("§5Vice President", 4),
    MAYOR("§bMayor", 3),
    SOLDIER("§cSoldier", 2),
    FOOTMAN("§7Footman", 1);

    private final String display;
    private final int weight;

    GuildRank(String display, int weight) {
        this.display = display;
        this.weight = weight;
    }

    public String display() {
        return display;
    }

    public int weight() {
        return weight;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean canInvite() {
        return weight >= MAYOR.weight;
    }

    public boolean canKick(GuildRank target) {
        return target != null && weight > target.weight && weight >= MAYOR.weight;
    }

    public boolean canSetRank(GuildRank target, GuildRank next) {
        if (this == LEADER && next != LEADER) {
            return true;
        }
        return weight > target.weight && weight > next.weight && weight >= VICE_PRESIDENT.weight;
    }

    public boolean canBuild() {
        return weight > FOOTMAN.weight;
    }

    public boolean canPlaceQuarry() {
        return weight >= SOLDIER.weight;
    }

    public boolean canCollectQuarry() {
        return weight >= FOOTMAN.weight;
    }

    public boolean canUpgradeQuarry() {
        return weight >= MAYOR.weight;
    }

    public boolean canUpgradeIsland() {
        return weight >= MAYOR.weight;
    }

    public boolean canPickupQuarry() {
        return weight >= SOLDIER.weight;
    }

    public boolean canBankDeposit() {
        return weight >= FOOTMAN.weight;
    }

    public boolean canBankWithdraw() {
        return weight >= SOLDIER.weight;
    }

    public static GuildRank parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return FOOTMAN;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (key) {
            case "LEADER" -> LEADER;
            case "VICE_PRESIDENT", "VICEPRESIDENT", "VICE", "VP", "OFFICER" -> VICE_PRESIDENT;
            case "MAYOR" -> MAYOR;
            case "SOLDIER", "MEMBER" -> SOLDIER;
            case "FOOTMAN", "GUEST" -> FOOTMAN;
            default -> FOOTMAN;
        };
    }
}
