package de.aetherion.core.api;

import org.bukkit.entity.Player;

/**
 * Read side of the player's chosen language ("/sprache de" / "/language en"). AetherionQuests owns
 * the preference and registers this; other plugins (Items tooltips, …) only read it. Core holds no
 * storage. Nothing registered = everybody is English.
 */
public interface LangAccess {

    /** True when this player chose German. */
    boolean german(Player player);

    /** Language code of the player ("en" / "de"). */
    default String code(Player player) {
        return german(player) ? "de" : "en";
    }
}
