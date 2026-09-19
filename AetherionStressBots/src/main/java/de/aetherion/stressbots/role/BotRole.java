package de.aetherion.stressbots.role;

import java.util.Locale;

/**
 * All Mineflayer bot identities this plugin kits.
 * Wave 1 QA: {@link #MINE}, {@link #FORAGE}, {@link #CATCH}, {@link #ROAM}.
 * Wave 2 QA: {@link #COMBAT}, {@link #FISH}, {@link #TRADE}, {@link #QUEST}, {@link #PAD}.
 * {@link #MINING} stays as Phase 1 {@code StressM*} stress.
 */
public enum BotRole {
    MINE("mine", 1),
    FORAGE("forage", 1),
    CATCH("catch", 1),
    ROAM("roam", 1),
    COMBAT("combat", 2),
    FISH("fish", 2),
    TRADE("trade", 2),
    QUEST("quest", 2),
    PAD("pad", 2),
    MINING("mining", 0);

    private final String id;
    private final int wave;

    BotRole(String id, int wave) {
        this.id = id;
        this.wave = wave;
    }

    public String id() {
        return id;
    }

    public int wave() {
        return wave;
    }

    public boolean wave1() {
        return wave == 1;
    }

    public boolean wave2() {
        return wave == 2;
    }

    public boolean qa() {
        return wave == 1 || wave == 2;
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
        if ("fishing".equals(key) || "angler".equals(key) || "rod".equals(key)) {
            return FISH;
        }
        if ("ah".equals(key) || "bazaar".equals(key) || "market".equals(key) || "trader".equals(key)) {
            return TRADE;
        }
        if ("npc".equals(key) || "dialog".equals(key) || "quests".equals(key)) {
            return QUEST;
        }
        if ("jumppad".equals(key) || "jump-pad".equals(key) || "hop".equals(key) || "pads".equals(key)) {
            return PAD;
        }
        if ("qacombat".equals(key) || "qa-combat".equals(key) || "sword".equals(key)) {
            return COMBAT;
        }
        for (BotRole role : values()) {
            if (role.id.equals(key) || role.name().equalsIgnoreCase(key)) {
                return role;
            }
        }
        return null;
    }
}
