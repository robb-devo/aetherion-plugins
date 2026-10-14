package de.aetherion.gnomon.ai;

import java.util.EnumMap;
import java.util.Map;

/**
 * What the mind knows about one authored move: its goal, what habits it answers and when it fits. Pure data, tunable
 * from config ({@code moves.<id>.*}). The move's body lives in the attack package.
 */
public record MoveSpec(
        String id,
        Family family,
        double base,
        int cooldownTicks,
        int minPhase,
        Map<Read, Double> reads,
        Map<Posture, Double> postures,
        double minDistance,
        double maxDistance,
        Read requires,
        boolean teaching,
        double closeCrowd,
        double farCrowd
) {

    /** The goal of a move. */
    public enum Family {
        PUNISH, ZONE, BAIT, RESET, EXECUTE
    }

    public double readWeight(Read read) {
        Double w = reads.get(read);
        return w == null ? 0 : w;
    }

    public double postureBias(Posture posture) {
        Double w = postures.get(posture);
        return w == null ? 0 : w;
    }

    /** Fluent builder for the default table. */
    public static Builder of(String id, Family family) {
        return new Builder(id, family);
    }

    public static final class Builder {
        private final String id;
        private final Family family;
        private double base = 0.5;
        private int cooldown = 0;
        private int minPhase = 1;
        private final Map<Read, Double> reads = new EnumMap<>(Read.class);
        private final Map<Posture, Double> postures = new EnumMap<>(Posture.class);
        private double minDist = 0;
        private double maxDist = 999;
        private Read requires;
        private boolean teaching = true;
        private double closeCrowd;
        private double farCrowd;

        Builder(String id, Family family) {
            this.id = id;
            this.family = family;
        }

        public Builder base(double v) {
            base = v;
            return this;
        }

        public Builder cooldown(int ticks) {
            cooldown = ticks;
            return this;
        }

        public Builder phase(int p) {
            minPhase = p;
            return this;
        }

        public Builder read(Read r, double w) {
            reads.put(r, w);
            return this;
        }

        public Builder posture(Posture p, double w) {
            postures.put(p, w);
            return this;
        }

        public Builder range(double min, double max) {
            minDist = min;
            maxDist = max;
            return this;
        }

        public Builder requires(Read r) {
            requires = r;
            return this;
        }

        public Builder noTeaching() {
            teaching = false;
            return this;
        }

        public Builder crowd(double close, double far) {
            closeCrowd = close;
            farCrowd = far;
            return this;
        }

        public MoveSpec build() {
            return new MoveSpec(id, family, base, cooldown, minPhase, Map.copyOf(reads), Map.copyOf(postures),
                    minDist, maxDist, requires, teaching, closeCrowd, farCrowd);
        }
    }
}
