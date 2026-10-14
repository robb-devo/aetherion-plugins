package de.aetherion.gnomon.encounter;

/**
 * Something that outlives the move that made it (ink pools, walls, footstep shadows). Ticked by the encounter;
 * {@link #end()} must restore anything it changed and be idempotent.
 */
public interface Hazard {

    /** @return true when finished */
    boolean tick();

    void end();
}
