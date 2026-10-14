package de.aetherion.items.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Manager buttons on a multi-backend network.
 * <ul>
 *     <li>Islands live on their own backend (mmo-i): Island / Guild work wherever AetherionGuilds runs locally
 *     <i>or</i> Core routes {@code /island} and {@code /guild} there.</li>
 *     <li>Bazaar / Auction House are one economy on the main world: on backends that route commands
 *     (mmo-c, mmo-i) the button crosses to mmo-r and opens it there instead of a second, local market.</li>
 * </ul>
 * Everything degrades to the old local behaviour with an older AetherionCore.
 */
final class NetworkHooks {

    private NetworkHooks() {
    }

    static boolean islandsAvailable() {
        if (Bukkit.getPluginManager().isPluginEnabled("AetherionGuilds")) {
            return true;
        }
        try {
            return de.aetherion.core.network.IslandLink.remoteAvailable();
        } catch (LinkageError olderCore) {
            return false;
        }
    }

    /** True when the player was sent to the main world to run {@code command} there (caller stops). */
    static boolean marketOnMain(Player player, String command) {
        try {
            return de.aetherion.core.network.NetworkRouter.routeCommandToMain(player, command);
        } catch (LinkageError olderCore) {
            return false;
        }
    }
}
