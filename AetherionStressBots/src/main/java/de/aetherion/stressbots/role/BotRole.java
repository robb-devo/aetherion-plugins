package de.aetherion.stressbots.role;

import java.util.Locale;

/**
 * All Mineflayer bot identities this plugin kits.
 * Wave 1 QA roles: {@link #MINE}, {@link #FORAGE}, {@link #CATCH}, {@link #ROAM}.
 * Wave 2 QA roles: {@link #COMBAT}, {@link #FISH}, {@link #TRADE}, {@link #QUEST}, {@link #PAD}.
 * Wave 3 roles play the rest of the network: {@link #FARM}, {@link #MENUS}, {@link #CASINO}, {@link #SOCIAL} on the
 * main world, {@link #PIT} on the hub, {@link #DUNGEON} on mmo-d, {@link #EXILE} on mmo-c, {@link #ISLAND} on mmo-i,
 * and {@link #GENERAL}, a player who tours all of it. {@link #MINING} stays as the Phase 1 {@code StressM*} prefix.
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
    GENERAL("general", 3),
    FARM("farm", 3),
    MENUS("menus", 3),
    CASINO("casino", 3),
    SOCIAL("social", 3),
    PIT("pit", 3),
    DUNGEON("dungeon", 3),
    EXILE("exile", 3),
    ISLAND("island", 3),
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

    /** Dev menu / {@code /stressbots start} roles (Wave 1 + later). */
    public boolean startable() {
        return wave >= 1;
    }

    public boolean qa() {
        return wave >= 1;
    }

    /**
     * Roles that travel or wander on purpose (other servers, dungeons, islands, the Pit): the plugin keeps them out
     * of the void but never leashes them to a pad, never re-anchors them when idle and never re-kits them on respawn.
     */
    public boolean free() {
        return wave == 3;
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
        if ("qacombat".equals(key) || "qa-combat".equals(key) || "fight".equals(key)
                || "pve".equals(key) || "sword".equals(key)) {
            return COMBAT;
        }
        if ("fishing".equals(key) || "angler".equals(key) || "rod".equals(key)) {
            return FISH;
        }
        if ("ah".equals(key) || "auction".equals(key) || "auction-house".equals(key)
                || "bazaar".equals(key) || "market".equals(key) || "trader".equals(key)) {
            return TRADE;
        }
        if ("quests".equals(key) || "npc".equals(key) || "npcs".equals(key) || "dialog".equals(key)) {
            return QUEST;
        }
        if ("jump".equals(key) || "jumppad".equals(key) || "jump-pad".equals(key)
                || "pads".equals(key) || "slime".equals(key) || "hop".equals(key)) {
            return PAD;
        }
        switch (key) {
            case "tour", "player", "mixed", "all" -> {
                return GENERAL;
            }
            case "farmer", "farming", "crops" -> {
                return FARM;
            }
            case "menu", "gui", "guis" -> {
                return MENUS;
            }
            case "gamble", "slots" -> {
                return CASINO;
            }
            case "party", "friends", "friend" -> {
                return SOCIAL;
            }
            case "pvp", "lobby" -> {
                return PIT;
            }
            case "dungeons", "mmo-d", "mmod" -> {
                return DUNGEON;
            }
            case "story", "mmo-c", "mmoc" -> {
                return EXILE;
            }
            case "islands", "isle", "insel", "mmo-i", "mmoi" -> {
                return ISLAND;
            }
            default -> {
            }
        }
        for (BotRole role : values()) {
            if (role.id.equals(key) || role.name().equalsIgnoreCase(key)) {
                return role;
            }
        }
        return null;
    }
}
