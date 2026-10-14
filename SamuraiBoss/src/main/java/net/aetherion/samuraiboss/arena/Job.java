package net.aetherion.samuraiboss.arena;

/**
 * Eine Aufgabe, die über mehrere Ticks verteilt läuft (Bauen, Abreißen, Wiederherstellen),
 * damit der Server nie hängt.
 */
public interface Job {

    /** @return true, wenn fertig. */
    boolean tick();

    /** Sofort fertigstellen (z. B. beim Plugin-Deaktivieren). */
    void finishNow();

    /** Kurzbeschreibung für /samuraiboss info. */
    String describe();
}
