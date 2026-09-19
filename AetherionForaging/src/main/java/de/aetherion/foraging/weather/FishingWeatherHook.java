package de.aetherion.foraging.weather;

import de.aetherion.foraging.AetherionForaging;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Stable entry for fishing (and other plugins) to read forage-isle weather
 * without depending on habitat internals.
 *
 * <pre>
 * WeatherKind kind = FishingWeatherHook.kind(player);
 * if (kind != null && kind.isWet()) { … }
 * </pre>
 */
public final class FishingWeatherHook {

    private FishingWeatherHook() {
    }

    /** Null if foraging plugin is offline or player is off the isle (treated as clear). */
    public static WeatherKind kind(Player player) {
        IsleWeatherService.WeatherState state = state(player);
        return state == null ? null : state.kind();
    }

    public static IsleWeatherService.WeatherState state(Player player) {
        AetherionForaging plugin = AetherionForaging.getInstance();
        if (plugin == null || plugin.weather() == null || player == null) {
            return null;
        }
        return plugin.weather().current(player);
    }

    public static boolean isOnIsleWithWeather(Player player) {
        IsleWeatherService.WeatherState state = state(player);
        return state != null && !"none".equals(state.habitat());
    }

    /** Convenience for future loot tables. */
    public static boolean preferWetLoot(Player player) {
        WeatherKind kind = kind(player);
        return kind != null && kind.isWet();
    }

    public static boolean preferFogLoot(Player player) {
        return kind(player) == WeatherKind.FOG;
    }

    /** Soft lookup when Items is calling without a compile dep on this class name stability. */
    public static Object reflectKind(Player player) {
        if (Bukkit.getPluginManager().getPlugin("AetherionForaging") == null) {
            return null;
        }
        return kind(player);
    }
}
