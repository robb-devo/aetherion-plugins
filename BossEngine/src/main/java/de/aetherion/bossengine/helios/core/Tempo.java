package de.aetherion.bossengine.helios.core;

/**
 * The fight's clock. The dying star's heartbeat IS the tempo: every tell counts in on it and every hit
 * lands on it, so the whole encounter reads like a piece of music and the finale can be one.
 *
 * <p>Beats are tracked as a fractional position so any BPM works (90 BPM is 13.33 ticks per beat);
 * a beat "happens" on the first tick at or after its exact time.
 */
public final class Tempo {

    private double bpm;
    private double targetBpm;
    private double bpmStep;
    private double position;
    private int beat;
    private boolean beatNow;
    private int beatsPerBar = 4;

    public Tempo(double bpm) {
        this.bpm = Math.max(20, bpm);
        this.targetBpm = this.bpm;
    }

    /** Advance one server tick. */
    public void tick() {
        if (bpm != targetBpm) {
            bpm = Math.abs(targetBpm - bpm) <= Math.abs(bpmStep) ? targetBpm : bpm + bpmStep;
        }
        position += bpm / 1200.0;
        int whole = (int) Math.floor(position);
        beatNow = whole != beat;
        beat = whole;
    }

    /** Glide to a new tempo over {@code ticks} (0 = cut). */
    public void bpm(double next, int ticks) {
        targetBpm = Math.max(20, next);
        if (ticks <= 0) {
            bpm = targetBpm;
            bpmStep = 0;
        } else {
            bpmStep = (targetBpm - bpm) / ticks;
        }
    }

    public double bpm() {
        return bpm;
    }

    /** Beat index since start. */
    public int beat() {
        return beat;
    }

    /** True on the tick a new beat starts. */
    public boolean onBeat() {
        return beatNow;
    }

    public boolean onDownbeat() {
        return beatNow && beat % beatsPerBar == 0;
    }

    public int beatInBar() {
        return Math.floorMod(beat, beatsPerBar);
    }

    /** Progress through the current beat, 0..1. */
    public float phase() {
        return (float) (position - Math.floor(position));
    }

    public double ticksPerBeat() {
        return 1200.0 / bpm;
    }

    /** Whole ticks from now until {@code beats} beats after the next beat boundary. */
    public int ticksUntil(int beats) {
        double left = (1.0 - (position - Math.floor(position))) + Math.max(0, beats);
        return (int) Math.max(1, Math.round(left * ticksPerBeat()));
    }

    /** Ticks for a musical length in beats at the current tempo. */
    public int ticks(double beats) {
        return (int) Math.max(1, Math.round(beats * ticksPerBeat()));
    }

    /** Ticks until the start of the next bar. */
    public int ticksToBar() {
        int into = beatInBar();
        return ticksUntil(beatsPerBar - into - 1);
    }
}
