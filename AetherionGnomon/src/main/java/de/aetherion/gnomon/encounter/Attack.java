package de.aetherion.gnomon.encounter;

import de.aetherion.gnomon.ai.Decision;

/**
 * One authored move. Lifecycle: {@link #start()} once, {@link #tick()} until it returns true, {@link #end()} always
 * (also on abort - must be idempotent and remove everything the move drew).
 */
public abstract class Attack {

    protected final Encounter e;
    protected final Decision d;
    protected int t;
    private boolean ended;

    protected Attack(Encounter e, Decision d) {
        this.e = e;
        this.d = d;
    }

    public Decision decision() {
        return d;
    }

    public String id() {
        return d.id();
    }

    /** Uses the heart (plumb bob): no stagger may interrupt it. */
    public boolean usesBob() {
        return false;
    }

    /** This move aims the lens (the eye must stay on the real hit). Walking and breathing do not. */
    public boolean ownsLens() {
        return true;
    }

    public abstract void start();

    /** @return true when finished */
    public abstract boolean tick();

    public final void end() {
        if (ended) {
            return;
        }
        ended = true;
        cleanup();
    }

    protected abstract void cleanup();
}
