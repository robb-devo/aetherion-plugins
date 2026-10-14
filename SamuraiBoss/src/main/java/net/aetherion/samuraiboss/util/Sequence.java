package net.aetherion.samuraiboss.util;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.IntConsumer;

/**
 * Eine Zeitachse für Choreografien (Fähigkeiten, Zwischensequenzen):
 * "bei Tick 12 das, von Tick 20 bis 40 jeden Tick jenes".
 *
 * <pre>
 * Sequence s = new Sequence()
 *     .at(0, () -> boss.play(anim.iaiReady))
 *     .during(0, 25, t -> telegraph.progress(t / 25f))
 *     .at(25, this::strike)
 *     .end(60);
 * </pre>
 */
public final class Sequence {

    private record Span(int from, int to, IntConsumer action) {
    }

    private final TreeMap<Integer, List<Runnable>> at = new TreeMap<>();
    private final List<Span> spans = new ArrayList<>();
    private int t;
    private int end = -1;
    private boolean stopped;
    private Runnable onEnd;

    public Sequence at(int tick, Runnable r) {
        at.computeIfAbsent(tick, k -> new ArrayList<>()).add(r);
        end = Math.max(end, tick);
        return this;
    }

    /** Jeden Tick von from bis to (inklusive); die Aktion bekommt den Tick relativ zu from. */
    public Sequence during(int from, int to, IntConsumer action) {
        spans.add(new Span(from, to, action));
        end = Math.max(end, to);
        return this;
    }

    /** Alle n Ticks im Bereich. */
    public Sequence every(int from, int to, int n, IntConsumer action) {
        return during(from, to, rel -> {
            if (rel % n == 0) action.accept(rel);
        });
    }

    public Sequence end(int tick) {
        end = Math.max(end, tick);
        return this;
    }

    public Sequence onEnd(Runnable r) {
        this.onEnd = r;
        return this;
    }

    /** Fügt während des Ablaufs dynamisch Ereignisse hinzu (relativ zu jetzt). */
    public void later(int delay, Runnable r) {
        at(t + Math.max(0, delay), r);
    }

    public int time() {
        return t;
    }

    public int length() {
        return end;
    }

    public boolean isDone() {
        return stopped || t > end;
    }

    public void stop() {
        stopped = true;
    }

    /** Einen Tick ausführen. @return false, wenn die Sequenz zu Ende ist. */
    public boolean tick() {
        if (stopped) return false;
        List<Runnable> now = at.remove(t);
        if (now != null) {
            for (Runnable r : now) {
                r.run();
                if (stopped) return false;
            }
        }
        for (int i = 0; i < spans.size(); i++) {
            Span s = spans.get(i);
            if (t >= s.from && t <= s.to) {
                s.action.accept(t - s.from);
                if (stopped) return false;
            }
        }
        t++;
        if (t > end) {
            if (onEnd != null) {
                Runnable r = onEnd;
                onEnd = null;
                r.run();
            }
            return false;
        }
        return true;
    }
}
