package de.aetherion.gnomon.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the Gnomon remembers about one player. All evidence decays; reads are recomputed from it.
 */
public final class Profile {

    public static final int SECTORS = 8;

    /** One sample of the player's route. */
    public record PathPoint(double x, double z, long tick) {
    }

    /** Current belief about one habit. */
    public static final class ReadState {
        double confidence;
        int sign;
        int sector = -1;
        boolean noticed;
        boolean exploitable;
        final java.util.Set<Integer> announcedSigns = new java.util.HashSet<>();
        boolean announcedForget;

        public double confidence() {
            return confidence;
        }

        public int sign() {
            return sign;
        }

        public int sector() {
            return sector;
        }

        public boolean noticed() {
            return noticed;
        }

        public boolean exploitable() {
            return exploitable;
        }
    }

    final UUID id;
    final String name;

    // ---- evidence (decaying)
    final Decay dodgeLeft = new Decay();
    final Decay dodgeRight = new Decay();
    final Decay early = new Decay();
    final Decay late = new Decay();
    final Decay onTime = new Decay();
    final Decay orbitCw = new Decay();
    final Decay orbitCcw = new Decay();
    final Decay timeClose = new Decay();
    final Decay timeMid = new Decay();
    final Decay timeFar = new Decay();
    final Decay greedHits = new Decay();
    final Decay cleanHits = new Decay();
    final Decay[] sectors = new Decay[SECTORS];
    final Decay exploitTries = new Decay();
    final Decay exploitLanded = new Decay();
    final Decay damageDealt = new Decay();
    final Decay hurtTaken = new Decay();

    // ---- live sample
    double x;
    double z;
    double y;
    boolean grounded = true;
    double hubAngle = Double.NaN;
    long lastSample = -1;
    double hpFraction = 1.0;
    boolean present = true;
    long lastFocused = 0;

    // ---- route memory (for the ledger and the Recital)
    final Deque<PathPoint> path = new ArrayDeque<>();
    long lastPathTick = -1000;
    double lastPathX = Double.NaN;
    double lastPathZ = Double.NaN;

    final Map<Read, ReadState> reads = new EnumMap<>(Read.class);

    Profile(UUID id, String name) {
        this.id = id;
        this.name = name;
        for (int i = 0; i < SECTORS; i++) {
            sectors[i] = new Decay();
        }
        for (Read r : Read.values()) {
            reads.put(r, new ReadState());
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public double x() {
        return x;
    }

    public double z() {
        return z;
    }

    public double y() {
        return y;
    }

    public boolean present() {
        return present;
    }

    public ReadState read(Read read) {
        return reads.get(read);
    }

    /** Strongest noticed read, or null. */
    public Read strongest() {
        Read best = null;
        double conf = 0;
        for (Map.Entry<Read, ReadState> e : reads.entrySet()) {
            if (e.getValue().noticed && e.getValue().confidence > conf) {
                best = e.getKey();
                conf = e.getValue().confidence;
            }
        }
        return best;
    }

    public double maxConfidence() {
        double best = 0;
        for (ReadState s : reads.values()) {
            best = Math.max(best, s.confidence);
        }
        return best;
    }

    /** Route points recorded between {@code fromTick} and {@code toTick}, oldest first. */
    public List<PathPoint> pathBetween(long fromTick, long toTick) {
        List<PathPoint> out = new ArrayList<>();
        for (PathPoint p : path) {
            if (p.tick() >= fromTick && p.tick() <= toTick) {
                out.add(p);
            }
        }
        return out;
    }

    public Deque<PathPoint> path() {
        return path;
    }
}
