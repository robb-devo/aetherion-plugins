package net.aetherion.samuraiboss.arena.destruction;

import net.aetherion.samuraiboss.arena.Arena;
import net.aetherion.samuraiboss.arena.BlockSnapshot;
import net.aetherion.samuraiboss.arena.DecoManager;
import net.aetherion.samuraiboss.arena.Job;
import net.aetherion.samuraiboss.arena.RestoreJob;
import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.World;
import org.bukkit.entity.Display;

import java.util.List;

/**
 * Der magische Wiederaufbau als Ganzes:
 * <ol>
 *   <li>Deko-Teile (Glocke, Banner, Blütenwolken …) schweben in ihre alte Lage zurück</li>
 *   <li>alle zerstörten Blöcke fliegen – getragen von Blütenblättern – an ihren Platz
 *       ({@link FlyInJob})</li>
 *   <li>exakter Abgleich mit dem Snapshot vom Kampfbeginn ({@link RestoreJob}) – repariert auch
 *       alles, was nicht vom Boss zerstört wurde</li>
 *   <li>die echte Deko übernimmt wieder</li>
 * </ol>
 */
public final class RebuildJob implements Job {

    private final Arena arena;
    private final World world;
    private final FlyInJob fly;
    private final List<DecoManager.Piece> deco;
    private final List<Display> fade;
    private final BlockSnapshot snap;
    private final int restorePerTick;
    private final int flight;
    private final Runnable onDone;
    private RestoreJob restore;
    private int tick;
    private boolean done;

    public RebuildJob(Arena arena, FlyInJob fly, List<DecoManager.Piece> deco, List<Display> fade, BlockSnapshot snap,
                      int restorePerTick, int flight, Runnable onDone) {
        this.arena = arena;
        this.world = arena.world();
        this.fly = fly;
        this.deco = deco;
        this.fade = fade;
        this.snap = snap;
        this.restorePerTick = restorePerTick;
        this.flight = flight;
        this.onDone = onDone;
    }

    @Override
    public boolean tick() {
        if (done) return true;
        if (tick == 0) {
            for (DecoManager.Piece p : deco) {
                Display d = p.display();
                if (d == null || !d.isValid()) continue;
                int delay = Rand.range(0, 20);
                // einfache Verzögerung über die Interpolation: erst warten, dann zurück
                d.setInterpolationDelay(delay);
                d.setInterpolationDuration(flight + 10);
                d.setTransformation(p.transform());
            }
        }
        if (tick == 12) {
            for (Display d : fade) if (d.isValid()) d.remove();
            fade.clear();
        }
        tick++;
        if (!fly.tick()) return false;
        if (restore == null) {
            if (snap == null || world == null) {
                finish();
                return true;
            }
            restore = new RestoreJob(world, snap, restorePerTick, null);
        }
        if (!restore.tick()) return false;
        finish();
        return true;
    }

    private void finish() {
        if (done) return;
        done = true;
        for (Display d : fade) if (d.isValid()) d.remove();
        fade.clear();
        for (DecoManager.Piece p : deco) {
            Display d = p.display();
            if (d != null && d.isValid()) d.remove();
        }
        deco.clear();
        arena.deco.showAll();
        if (onDone != null) onDone.run();
    }

    @Override
    public void finishNow() {
        if (done) return;
        fly.finishNow();
        if (snap != null && world != null) {
            RestoreJob r = restore != null ? restore : new RestoreJob(world, snap, restorePerTick, null);
            r.finishNow();
        }
        finish();
    }

    @Override
    public String describe() {
        if (restore != null) return "Abgleich mit Snapshot";
        return fly.describe();
    }

    public float progress() {
        return fly.progress();
    }

    /** Hilfsfunktion: Display sofort unsichtbar (falls eine Unterbrechung nötig ist). */
    static void hide(Display d) {
        DisplayFactory.snap(d, DisplayFactory.HIDDEN);
    }
}
