package de.aetherion.stressbots.role;

import java.util.Locale;

/**
 * All Mineflayer bot identities this plugin kits.
 * Wave 1 QA roles: {@link #MINE}, {@link #FORAGE}, {@link #CATCH}, {@link #ROAM}.
 * Wave 2 QA roles: {@link #COMBAT}, {@link #FISH}, {@link #TRADE}, {@link #QUEST}, {@link #PAD}.
 * {@link #MINING} stays as the Phase 1 {@code StressM*} prefix.
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

    /** Dev menu / {@code /stressbots start} roles (Wave 1 + later). */
    public boolean startable() {
        return wave >= 1;
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
        if ("qacombat".equals(key) || "fight".equals(key) || "pve".equals(key)) {
            return COMBAT;
        }
        if ("fishing".equals(key) || "rod".equals(key)) {
            return FISH;
        }
        if ("ah".equals(key) || "auction".equals(key) || "auction-house".equals(key)
                || "bazaar".equals(key) || "market".equals(key)) {
            return TRADE;
        }
        if ("quests".equals(key) || "npc".equals(key) || "npcs".equals(key)) {
            return QUEST;
        }
        if ("jump".equals(key) || "jumppad".equals(key) || "jump-pad".equals(key)
                || "pads".equals(key) || "slime".equals(key)) {
            return PAD;
        }
        for (BotRole role : values()) {
            if (role.id.equals(key) || role.name().equalsIgnoreCase(key)) {
                return role;
            }
        }
        return null;
    }
}
