package de.aetherion.core.exile;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.LangAccess;

import org.bukkit.entity.Player;

/** English canon, German from the player's /sprache choice (Quests owns the preference). */
public final class Say {

    private Say() {
    }

    public static boolean german(Player player) {
        LangAccess lang = AetherServices.lang();
        try {
            return lang != null && player != null && lang.german(player);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static String t(Player player, String en, String de) {
        return german(player) && de != null ? de : en;
    }
}
