package de.aetherion.bossengine.helios.core;

import org.bukkit.Bukkit;

/**
 * Per-instance performance budget. Cosmetic displays ask {@link #allowSpawn()} and are simply skipped
 * when the tick is full; load-bearing ones (telegraphs, bodies) spawn with {@code force}. The quality
 * level scales every cosmetic count through {@link #density()} and drops a level on its own when the
 * server is struggling. Everything here feeds {@code /helios debug}.
 */
public final class DisplayBudget {

    public enum Quality {
        HIGH(1f), MEDIUM(0.65f), LOW(0.4f);

        final float density;

        Quality(float density) {
            this.density = density;
        }
    }

    private final int liveCap;
    private final int spawnsPerTick;
    private final double degradeMspt;
    private Quality quality;
    private final Quality configured;

    private int live;
    private int peakLive;
    private int spawnsThisTick;
    private int pushesThisTick;
    private int skippedThisTick;
    private int lastSpawns;
    private int lastPushes;
    private int lastSkipped;
    private double emaMicros;
    private long tickStart;
    private int calmTicks;

    public DisplayBudget(int liveCap, int spawnsPerTick, Quality quality, double degradeMspt) {
        this.liveCap = Math.max(50, liveCap);
        this.spawnsPerTick = Math.max(4, spawnsPerTick);
        this.quality = quality == null ? Quality.HIGH : quality;
        this.configured = this.quality;
        this.degradeMspt = degradeMspt;
    }

    /** Call at the top of the encounter tick. */
    public void beginTick() {
        lastSpawns = spawnsThisTick;
        lastPushes = pushesThisTick;
        lastSkipped = skippedThisTick;
        spawnsThisTick = 0;
        pushesThisTick = 0;
        skippedThisTick = 0;
        tickStart = System.nanoTime();
    }

    /** Call at the end of the encounter tick. */
    public void endTick() {
        double micros = (System.nanoTime() - tickStart) / 1000.0;
        emaMicros = emaMicros == 0 ? micros : emaMicros * 0.92 + micros * 0.08;
        autoDegrade();
    }

    private void autoDegrade() {
        if (degradeMspt <= 0) {
            return;
        }
        double mspt = Bukkit.getAverageTickTime();
        if (mspt > degradeMspt) {
            calmTicks = 0;
            if (quality == Quality.HIGH) {
                quality = Quality.MEDIUM;
            } else if (quality == Quality.MEDIUM && mspt > degradeMspt * 1.2) {
                quality = Quality.LOW;
            }
        } else if (quality != configured && ++calmTicks > 20 * 30) {
            quality = quality == Quality.LOW ? Quality.MEDIUM : configured;
            calmTicks = 0;
        }
    }

    /** May a cosmetic display spawn this tick? */
    public boolean allowSpawn() {
        if (spawnsThisTick >= spawnsPerTick || live >= liveCap) {
            skippedThisTick++;
            return false;
        }
        return true;
    }

    public void spawned() {
        spawnsThisTick++;
        live++;
        peakLive = Math.max(peakLive, live);
    }

    public void removed() {
        live = Math.max(0, live - 1);
    }

    public void resync(int actualLive) {
        live = actualLive;
    }

    public void pushed() {
        pushesThisTick++;
    }

    /** Multiplier for cosmetic counts (debris, segments, sparks). */
    public float density() {
        return quality.density;
    }

    /** Scales a cosmetic count by the current quality, never below {@code min}. */
    public int scaled(int count, int min) {
        return Math.max(min, Math.round(count * quality.density));
    }

    public Quality quality() {
        return quality;
    }

    public int live() {
        return live;
    }

    public int peakLive() {
        return peakLive;
    }

    public String report() {
        return String.format(java.util.Locale.ROOT,
                "displays %d (peak %d / cap %d) | spawns/t %d | pushes/t %d | skipped/t %d | %.0f µs/t | quality %s",
                live, peakLive, liveCap, lastSpawns, lastPushes, lastSkipped, emaMicros, quality);
    }

    public double micros() {
        return emaMicros;
    }

    public int lastPushes() {
        return lastPushes;
    }
}
