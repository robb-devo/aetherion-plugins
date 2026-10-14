package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.DisplayPool;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verwaltet alle laufenden Effekte eines Kampfes. Fehler in einem Effekt beenden nur diesen
 * Effekt – nie den ganzen Kampf.
 */
public final class FxManager {

    private final DisplayPool pool;
    private final Logger log;
    private final List<Fx> active = new ArrayList<>();
    private final List<Fx> pending = new ArrayList<>();
    private boolean ticking;
    /** 0..1 – reduziert die Anzahl an Partikel-/Display-Elementen bei Bedarf (config). */
    private final float density;

    public FxManager(DisplayPool pool, Logger log, float density) {
        this.pool = pool;
        this.log = log;
        this.density = Math.max(0.1f, Math.min(1f, density));
    }

    public DisplayPool pool() {
        return pool;
    }

    public float density() {
        return density;
    }

    /** Skaliert eine gewünschte Anzahl von Elementen mit der Dichte-Einstellung. */
    public int count(int wanted) {
        return Math.max(1, Math.round(wanted * density));
    }

    public <T extends Fx> T add(T fx) {
        if (ticking) pending.add(fx);
        else active.add(fx);
        return fx;
    }

    public void tick() {
        ticking = true;
        try {
            for (int i = 0; i < active.size(); i++) {
                Fx f = active.get(i);
                boolean alive;
                try {
                    alive = f.tick();
                } catch (RuntimeException ex) {
                    log.log(Level.WARNING, "Effekt " + f.getClass().getSimpleName() + " abgebrochen", ex);
                    alive = false;
                }
                if (!alive) {
                    safeStop(f);
                    active.remove(i);
                    i--;
                }
            }
        } finally {
            ticking = false;
        }
        if (!pending.isEmpty()) {
            active.addAll(pending);
            pending.clear();
        }
    }

    private void safeStop(Fx f) {
        try {
            f.stop();
        } catch (RuntimeException ex) {
            log.log(Level.WARNING, "Fehler beim Beenden von " + f.getClass().getSimpleName(), ex);
        }
    }

    /** Beendet einen bestimmten Effekt sofort. */
    public void kill(Fx f) {
        if (active.remove(f) || pending.remove(f)) safeStop(f);
    }

    /** Beendet alle Effekte eines Typs. */
    public void killAll(Class<? extends Fx> type) {
        for (Fx f : new ArrayList<>(active)) if (type.isInstance(f)) kill(f);
        for (Fx f : new ArrayList<>(pending)) if (type.isInstance(f)) kill(f);
    }

    public <T extends Fx> List<T> find(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Fx f : active) if (type.isInstance(f)) out.add(type.cast(f));
        for (Fx f : pending) if (type.isInstance(f)) out.add(type.cast(f));
        return out;
    }

    public int size() {
        return active.size() + pending.size();
    }

    /** Alle Effekte beenden (Kampfende). */
    public void clear() {
        for (Fx f : new ArrayList<>(active)) safeStop(f);
        for (Fx f : new ArrayList<>(pending)) safeStop(f);
        active.clear();
        pending.clear();
    }
}
