package de.aetherion.core.api;

/**
 * Player-local forage-isle weather snapshot. Fields are enum/name strings.
 */
public record ForageWeatherView(String kind, String habitat, String source, String phase) {
}
