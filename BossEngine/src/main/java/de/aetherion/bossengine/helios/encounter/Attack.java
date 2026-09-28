package de.aetherion.bossengine.helios.encounter;

import de.aetherion.bossengine.helios.core.HeliosStage;

/**
 * One move of a boss. Owns a display group that is cleared when it ends, so an attack can never leak
 * geometry. Attacks belong to a hazard {@link Family}: the scheduler never runs two of the same family
 * at once (one kind of ground danger at a time), except where a script deliberately stacks them
 * (the Requiem).
 *
 * <p>Every attack follows the same grammar: <b>aim</b> (hairline, rising tone) → <b>commit</b> (amber
 * fill, count-in on the beat) → <b>hit</b> (white flash, impact layer) → <b>hush</b> (fade, one quiet
 * beat).
 */
public abstract class Attack {

    public enum Family {
        /** The boss's own body performs it (only one at a time). */
        BODY,
        /** Floor hazards: markers, impacts, fissures. */
        GROUND,
        /** Sweeping lines and cages. */
        SWEEP,
        /** Things falling or flying in from above. */
        SKY,
        /** The arena itself moves or breaks. */
        ARENA
    }

    protected final ActScript act;
    protected final HeliosEncounter enc;
    protected final HeliosStage stage;
    protected final HeliosStage.Group g;
    protected int t;
    private boolean ended;

    protected Attack(ActScript act) {
        this.act = act;
        this.enc = act.encounter();
        this.stage = enc.stage();
        this.g = stage.group();
    }

    public abstract String id();

    public abstract Family family();

    /** Called once before the first tick. */
    public void start() {
    }

    /** @return true when finished */
    protected abstract boolean tick();

    /** Called once when the attack finishes or is cut (death, phase change, abort). */
    protected void cleanup() {
    }

    public final boolean step() {
        if (ended) {
            return true;
        }
        boolean done = tick();
        t++;
        return done;
    }

    public final void end() {
        if (ended) {
            return;
        }
        ended = true;
        try {
            cleanup();
        } finally {
            g.clear();
        }
    }

    public boolean usesBody() {
        return family() == Family.BODY;
    }

    /** How long the scheduler should wait after this attack before the next body move (ticks). */
    public int recovery() {
        return enc.tempo().ticks(2);
    }

    public int age() {
        return t;
    }
}
