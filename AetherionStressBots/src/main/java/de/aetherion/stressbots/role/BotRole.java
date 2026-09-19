package de.aetherion.stressbots.role;

import java.util.Locale;

/**
 * All Mineflayer bot identities this plugin kits.
 * Wave 1 QA roles are {@link #MINE}, {@link #FORAGE}, {@link #CATCH}, {@link #ROAM}.
 * {@link #COMBAT} and {@link #MINING} stay as Phase 1 stress prefixes.
 */
public enum BotRole {
    MINE("mine", true),
    FORAGE("forage", true),
    CATCH("catch", true),
    ROAM("roam", true),
    COMBAT("combat", false),
    MINING("mining", false);

    private final String id;
    private final boolean wave1;

    BotRole(String id, boolean wave1) {
        this.id = id;
        this.wave1 = wave1;
    }

    public String id() {
        return id;
    }

    public boolean wave1() {
        return wave1;
    }

    public static BotRole fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT).trim();
        if ("miner".equals(key) || "ores".equals(key)) {
            return MINE;
        }
        if ("wood".equals(key) || "chop".equals(key) || "foraging".equals(key)) {
            return FORAGE;
        }
        if ("pet".equals(key) || "pets".equals(key) || "catcher".equals(key)) {
            return CATCH;
        }
        if ("walk".equals(key) || "hub".equals(key) || "capital".equals(key)) {
            return ROAM;
        }
        for (BotRole role : values()) {
            if (role.id.equals(key) || role.name().equalsIgnoreCase(key)) {
                return role;
            }
        }
        return null;
    }
}
