package de.aetherion.gnomon.ai;

/**
 * The Gnomon's mood. Changes how it paces, what it favours and what the iris shows.
 */
public enum Posture {

    /** Teaching. Long telegraphs, honest aim, it walks and looks. White iris. */
    OBSERVE,
    /** Neutral pressure. Shorter gaps. Amber iris. */
    PRESS,
    /** Zone control: sweeps, ink, walls. Takes your space. Violet iris. */
    DENY,
    /** Predatory: leads, feints, false openings - spends what it learned. Red iris. */
    PREY
}
