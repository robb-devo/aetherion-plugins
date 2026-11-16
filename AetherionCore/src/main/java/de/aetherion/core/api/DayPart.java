package de.aetherion.core.api;

import org.bukkit.World;

/**
 * The parts of an Aetherion day, read from a world's clock. The outer edges match the Hub's Origin phases (dawn
 * and dusk are the minutes of the bell tolls); the day between them is cut into morning, midday and afternoon,
 * the night into evening and deep night, so routines have something to hang on.
 */
public enum DayPart {

    DAWN, MORNING, MIDDAY, AFTERNOON, DUSK, EVENING, NIGHT;

    /** Start of each part in world ticks (0 = sunrise), in the order of a day that begins at {@link #MORNING}. */
    private static final long MORNING_AT = 450L;
    private static final long MIDDAY_AT = 5000L;
    private static final long AFTERNOON_AT = 7500L;
    private static final long DUSK_AT = 11800L;
    private static final long EVENING_AT = 13200L;
    private static final long NIGHT_AT = 16500L;
    private static final long DAWN_AT = 22800L;

    public static DayPart of(World world) {
        return world == null ? MIDDAY : of(world.getTime());
    }

    public static DayPart of(long worldTime) {
        long time = Math.floorMod(worldTime, 24000L);
        if (time >= DAWN_AT || time < MORNING_AT) {
            return DAWN;
        }
        if (time < MIDDAY_AT) {
            return MORNING;
        }
        if (time < AFTERNOON_AT) {
            return MIDDAY;
        }
        if (time < DUSK_AT) {
            return AFTERNOON;
        }
        if (time < EVENING_AT) {
            return DUSK;
        }
        if (time < NIGHT_AT) {
            return EVENING;
        }
        return NIGHT;
    }

    /** Morning to afternoon: shops are open, carts are on the road. */
    public boolean working() {
        return this == MORNING || this == MIDDAY || this == AFTERNOON;
    }

    /** Evening and night: lamps are lit, the watch walks. */
    public boolean dark() {
        return this == EVENING || this == NIGHT;
    }

    /** How far this part has run, 0 (just begun) to 1 (about to end). */
    public double progress(long worldTime) {
        long time = Math.floorMod(worldTime, 24000L);
        long start = start();
        long end = next().start();
        long length = Math.floorMod(end - start, 24000L);
        long done = Math.floorMod(time - start, 24000L);
        return length <= 0L ? 0.0d : Math.min(1.0d, done / (double) length);
    }

    public DayPart next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** First world tick of this part. */
    public long start() {
        return switch (this) {
            case DAWN -> DAWN_AT;
            case MORNING -> MORNING_AT;
            case MIDDAY -> MIDDAY_AT;
            case AFTERNOON -> AFTERNOON_AT;
            case DUSK -> DUSK_AT;
            case EVENING -> EVENING_AT;
            case NIGHT -> NIGHT_AT;
        };
    }
}
