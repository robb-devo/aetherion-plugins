package de.aetherion.aethermobs.pet.body;

/**
 * How a pet body moves. Every pet id maps to exactly one skeleton; the skeleton picks the
 * carriage (where the anchor rides) and the gait the animator plays.
 */
public enum Skeleton {

    /** Springy diagonal trot: canines, felines, deer, goats, pigs, horses. */
    TROT(Carriage.GROUND, 0.55f),
    /** Heavy lateral walk with a rolling sway: cows, bears, pandas, camels, sniffers. */
    AMBLE(Carriage.GROUND, 0.70f),
    /** Upright two-legged walk with arm swing. */
    STRIDE(Carriage.GROUND, 0.60f),
    /** Heavy brute biped: long arms, slow stomp, shoulder roll. */
    LUMBER(Carriage.GROUND, 0.90f),
    /** Crouch, leap, land: rabbits, frogs, squirrels, sacks. */
    HOP(Carriage.GROUND, 0.75f),
    /** Squash-and-stretch blob bounce. */
    BOUNCE(Carriage.GROUND, 0.80f),
    /** Many-legged skitter, legs ripple out of phase. */
    SCUTTLE(Carriage.GROUND, 0.35f),
    /** Low shelled crawl: turtles, armadillos. */
    CRAWL(Carriage.GROUND, 0.30f),
    /** Segmented side-wave slither with an inch-worm lift. */
    SLITHER(Carriage.GROUND, 0.40f),
    /** Side-to-side rocking waddle: mushroom folk. */
    WADDLE(Carriage.GROUND, 0.38f),
    /** Insect hover: blur wings, darting bob. */
    HOVER(Carriage.AIR, 0.0f),
    /** Butterfly / fairy flutter: big slow wings, loopy bob. */
    FLUTTER(Carriage.AIR, 0.0f),
    /** Flapping flyer: bats and birds, flap bursts then glide. */
    FLAP(Carriage.AIR, 0.0f),
    /** Wide-wing raptor soar: long glides, banking, rare strokes. */
    SOAR(Carriage.AIR, 0.0f),
    /** Spirit / wisp drift: no wings, orbiting motes, slow figure-eight. */
    DRIFT(Carriage.AIR, 0.0f),
    /** Menacing floater: ghasts, blazes, withers. Tentacles, rods, skulls. */
    HAUNT(Carriage.AIR, 0.0f),
    /** Fish swim: tail-fin beat drives a body S-curve. */
    SWIM(Carriage.WATER, 0.0f),
    /** Squid jet: mantle squeeze, tentacle pulse, glide. */
    JET(Carriage.WATER, 0.0f),
    /** Dolphin porpoise: pitch waves, fluke strokes. */
    PORPOISE(Carriage.WATER, 0.0f),
    /** Western dragon: four legs, two wings, neck, tail. Walks wild, flies when equipped. */
    DRAGON(Carriage.DRAGON, 0.65f),
    /** Eastern wyrm: long serpentine body that rides the air in waves. */
    WYRM(Carriage.AIR, 0.0f);

    /** Where the anchor lives relative to the terrain. */
    public enum Carriage {
        GROUND,
        AIR,
        WATER,
        DRAGON
    }

    private final Carriage carriage;
    /** Blocks travelled per full gait cycle at size 1 (ground skeletons). */
    private final float stride;

    Skeleton(Carriage carriage, float stride) {
        this.carriage = carriage;
        this.stride = stride;
    }

    public Carriage carriage() {
        return carriage;
    }

    public float stride() {
        return stride;
    }

    public boolean grounded() {
        return carriage == Carriage.GROUND || carriage == Carriage.DRAGON;
    }

    public boolean aquatic() {
        return carriage == Carriage.WATER;
    }

    public boolean airborne() {
        return carriage == Carriage.AIR;
    }
}
