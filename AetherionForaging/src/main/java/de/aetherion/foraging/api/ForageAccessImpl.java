package de.aetherion.foraging.api;

import de.aetherion.core.api.ForageAccess;
import de.aetherion.core.api.ForageWeatherView;
import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.ForagerChopDemo;
import de.aetherion.foraging.npc.IsleGuideNpc;
import de.aetherion.foraging.ritual.GroveRitualService;
import de.aetherion.foraging.weather.FishingWeatherHook;
import de.aetherion.foraging.weather.IsleWeatherService;
import de.aetherion.foraging.weather.WeatherKind;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class ForageAccessImpl implements ForageAccess {

    private final AetherionForaging plugin;

    public ForageAccessImpl(AetherionForaging plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isChopDemoRunning(Player player) {
        return ForagerChopDemo.isRunning(player);
    }

    @Override
    public void playChopDemo(Player player, Location nearForager) {
        ForagerChopDemo.play(player, nearForager);
    }

    @Override
    public ItemStack isleGuideAnchor() {
        return IsleGuideNpc.createAnchor();
    }

    @Override
    public ItemStack groveAnchor() {
        return GroveRitualService.createAnchor();
    }

    @Override
    public ForageWeatherView weather(Player player) {
        IsleWeatherService.WeatherState state = FishingWeatherHook.state(player);
        if (state == null) {
            return null;
        }
        return new ForageWeatherView(
                String.valueOf(state.kind()),
                String.valueOf(state.habitat()),
                String.valueOf(state.source()),
                String.valueOf(state.phase())
        );
    }

    @Override
    public boolean forceWeather(Player player, String kind, int seconds) {
        if (plugin == null || plugin.weather() == null || player == null || kind == null) {
            return false;
        }
        WeatherKind weatherKind;
        try {
            weatherKind = WeatherKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        plugin.weather().setRitualOverride(player, weatherKind, Math.max(10, seconds));
        return true;
    }

    @Override
    public boolean clearWeather(Player player) {
        if (plugin == null || plugin.weather() == null || player == null) {
            return false;
        }
        plugin.weather().clearRitualOverride(player);
        return true;
    }

    @Override
    public boolean preferWetLoot(Player player) {
        return FishingWeatherHook.preferWetLoot(player);
    }

    @Override
    public boolean preferFogLoot(Player player) {
        return FishingWeatherHook.preferFogLoot(player);
    }
}
