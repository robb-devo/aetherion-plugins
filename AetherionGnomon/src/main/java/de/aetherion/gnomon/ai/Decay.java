package de.aetherion.gnomon.ai;

/** Evidence that fades: value halves every {@code halfLife} ticks. Lazily decayed on read/write. */
final class Decay {

    private double value;
    private long at;

    double get(long now, double halfLife) {
        decay(now, halfLife);
        return value;
    }

    void add(double amount, long now, double halfLife) {
        decay(now, halfLife);
        value += amount;
    }

    void scale(double factor) {
        value *= factor;
    }

    void clear() {
        value = 0;
    }

    private void decay(long now, double halfLife) {
        if (now > at) {
            if (value != 0 && halfLife > 0) {
                value *= Math.pow(0.5, (now - at) / halfLife);
                if (Math.abs(value) < 1e-6) {
                    value = 0;
                }
            }
            at = now;
        }
    }
}
