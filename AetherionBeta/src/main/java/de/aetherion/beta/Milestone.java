package de.aetherion.beta;

public enum Milestone {
    MEET_GUIDE,
    QUESTS,
    GATHER,
    PET,
    BOSS,
    DUNGEON,
    ISLAND;

    public String id() {
        return name().toLowerCase();
    }
}
