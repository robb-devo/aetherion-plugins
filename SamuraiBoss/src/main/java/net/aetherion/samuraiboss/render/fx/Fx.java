package net.aetherion.samuraiboss.render.fx;

/**
 * Ein laufender Effekt. Wird jeden Tick vom {@link FxManager} aufgerufen.
 */
public interface Fx {

    /**
     * Einen Tick weiter.
     *
     * @return false, wenn der Effekt fertig ist (der Manager ruft dann {@link #stop()}).
     */
    boolean tick();

    /** Sofort beenden und alle Displays freigeben. Muss mehrfach aufrufbar sein. */
    void stop();
}
