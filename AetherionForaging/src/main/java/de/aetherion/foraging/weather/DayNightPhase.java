package de.aetherion.foraging.weather;

import org.bukkit.World;

import java.util.Locale;

public enum DayNightPhase {
    MORNING,
    DAY,
    EVENING,
    NIGHT;

    public static DayNightPhase ofWorld(World world) {
        if (world == null) {
            return DAY;
        }
        long t = world.getTime() % 24000L;
        if (t < 2000L) {
            return MORNING;
        }
        if (t < 10000L) {
            return DAY;
        }
        if (t < 13000L) {
            return EVENING;
        }
        return NIGHT;
    }

    public static DayNightPhase parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAY;
        }
        try {
            return DayNightPhase.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DAY;
        }
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
