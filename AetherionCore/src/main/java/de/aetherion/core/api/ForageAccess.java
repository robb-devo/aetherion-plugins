package de.aetherion.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Foraging chop demo, isle weather, and DEV anchors.
 * Implemented by AetherionForaging.
 */
public interface ForageAccess {

    boolean isChopDemoRunning(Player player);

    void playChopDemo(Player player, Location nearForager);

    ItemStack isleGuideAnchor();

    ItemStack groveAnchor();

    ForageWeatherView weather(Player player);

    boolean forceWeather(Player player, String kind, int seconds);

    boolean clearWeather(Player player);

    boolean preferWetLoot(Player player);

    boolean preferFogLoot(Player player);
}
