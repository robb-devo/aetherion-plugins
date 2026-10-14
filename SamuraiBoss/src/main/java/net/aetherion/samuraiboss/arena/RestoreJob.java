package net.aetherion.samuraiboss.arena;

import org.bukkit.World;

/**
 * Stellt einen Bereich exakt aus einem Snapshot wieder her – verteilt über Ticks und nur dort,
 * wo die Welt wirklich abweicht ("Diff"). Läuft von unten nach oben pro Säule.
 */
public final class RestoreJob implements Job {

    private final World world;
    private final BlockSnapshot snap;
    private final int writesPerTick;
    private final Runnable onDone;
    private int cursor;
    private int written;
    private boolean done;

    public RestoreJob(World world, BlockSnapshot snap, int writesPerTick, Runnable onDone) {
        this.world = world;
        this.snap = snap;
        this.writesPerTick = Math.max(100, writesPerTick);
        this.onDone = onDone;
    }

    @Override
    public boolean tick() {
        if (done) return true;
        int checks = writesPerTick * 4;
        int writes = 0;
        while (cursor < snap.size() && checks-- > 0 && writes < writesPerTick) {
            if (snap.differs(world, cursor)) {
                snap.restore(world, cursor);
                writes++;
                written++;
            }
            cursor++;
        }
        if (cursor >= snap.size()) {
            done = true;
            if (onDone != null) onDone.run();
        }
        return done;
    }

    @Override
    public void finishNow() {
        while (!done) {
            int c = cursor;
            tick();
            if (c == cursor && !done) break;
        }
    }

    public int written() {
        return written;
    }

    public float progress() {
        return snap.size() == 0 ? 1f : cursor / (float) snap.size();
    }

    @Override
    public String describe() {
        return "Wiederherstellen " + Math.round(progress() * 100) + "% (" + written + " Blöcke)";
    }
}
