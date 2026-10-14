package de.aetherion.gnomon.ai;

/**
 * A habit the Gnomon can learn about a player. Directional reads carry a sign; {@link #CAMPER} carries a sector.
 */
public enum Read {

    /** Dodges to one side of a threat. sign -1 = left, +1 = right. */
    SIDE(true),
    /** Circles the hub one way. sign +1 = clockwise (seen from above, north up), -1 = counter-clockwise. */
    ORBIT(true),
    /** Lives under the tripod, next to the heart. */
    HUGGER(false),
    /** Keeps its distance and measures. */
    KITER(false),
    /** Hits every opening until the last frame. */
    GREEDY(false),
    /** Dodge timing. sign -1 = early (flinches at the first sight), +1 = late (last moment). */
    TIMING(true),
    /** Keeps returning to one slice of the dial. */
    CAMPER(false);

    private final boolean signed;

    Read(boolean signed) {
        this.signed = signed;
    }

    public boolean signed() {
        return signed;
    }

    /** Reads where "doing the opposite" can embarrass the Gnomon. */
    public boolean baitable() {
        return this == SIDE || this == ORBIT || this == GREEDY;
    }
}
