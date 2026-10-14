package net.aetherion.samuraiboss.arena.dojo;

/**
 * Bauteil-Kategorien des Dojos. Sie bestimmen, WANN ein Block bei der Zerstörung fällt.
 */
public enum El {
    // --- geschützt: wird nie zerstört (Boden, Fundament, Wasser, heiliger Stamm) ---
    FOUNDATION(true),
    GROUND(true),
    PATH(true),
    POND(true),
    HALL_PLATFORM(true),
    HALL_FLOOR(true),
    TREE_TRUNK(true),

    // --- Stufe 1 (Übergang zu Phase 2): Papierwände, Laternenköpfe, Bannerstangen ---
    HALL_SHOJI(false),
    LANTERN_TOP(false),
    NOBORI(false),

    // --- Stufe 2 (Übergang zu Phase 3): Torii-Balken, Mauerdächer, Glockenturm, Blüten ---
    TORII_TOP(false),
    WALL_CAP(false),
    BELL_TOWER(false),
    TREE_LEAVES(false),
    BRIDGE(false),

    // --- Stufe 3 (Ultimate): alles andere über Kopfhöhe ---
    WALL(false),
    GATE(false),
    HALL_PILLAR(false),
    HALL_WALL(false),
    HALL_ROOF(false),
    HALL_INTERIOR(false),
    TORII(false),
    LANTERN(false),
    GARDEN(false),
    FENCE(false);

    public final boolean protectedEl;

    El(boolean protectedEl) {
        this.protectedEl = protectedEl;
    }

    private static final El[] VALUES = values();

    public static El of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : GROUND;
    }
}
