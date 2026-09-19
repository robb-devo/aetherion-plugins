package de.aetherion.foraging.weather;

import java.util.Locale;

/**
 * Forage-isle weather kinds — ambient habitat tables and ritual overrides.
 * Fishing loot can read {@link IsleWeatherService#current(org.bukkit.entity.Player)} later.
 */
public enum WeatherKind {
    CLEAR,
    FOG,
    RAIN,
    DRIZZLE,
    SNOW,
    WINDY;

    public static WeatherKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CLEAR;
        }
        try {
            return WeatherKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CLEAR;
        }
    }

    public boolean isWet() {
        return this == RAIN || this == DRIZZLE || this == SNOW;
    }
}
