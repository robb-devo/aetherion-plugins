package de.aetherion.gnomon.ai;

/**
 * Every number the mind uses. Built from config.yml ({@code ai.*}); {@link #defaults()} matches the shipped file.
 */
public record BrainConfig(
        double halfLifeSeconds,
        double halfLifeVerdictSeconds,
        double notice,
        double exploit,
        double forget,
        double teachSeconds,
        double minDodges,
        double minOrbitSeconds,
        double minRangeSeconds,
        double minOpeningHits,
        double minSectorSeconds,
        double outplayedForgetting,
        double temperature,
        double repeatPenalty,
        double focusMinSeconds,
        double focusMaxSeconds,
        double postureMinSeconds,
        int breathEvery,
        double closeRadius,
        double farRadius,
        int pathSampleTicks,
        double pathMinStep,
        long seed
) {

    public static BrainConfig defaults() {
        return new BrainConfig(
                40, 70,
                0.35, 0.55, 0.25,
                35,
                4, 6, 6, 4, 20,
                0.45,
                0.12, 0.45,
                5, 14,
                8,
                4,
                5.5, 13.0,
                8, 1.6,
                0L
        );
    }

    public double halfLifeTicks(int phase) {
        return 20.0 * (phase >= 2 ? halfLifeVerdictSeconds : halfLifeSeconds);
    }
}
