package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Flush dirty economy/progress stores on quit so crash windows stay small.
 */
public final class PersistenceFlushListener implements Listener {

    private final AetherionItems plugin;

    public PersistenceFlushListener(AetherionItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getCoins() != null) {
            plugin.getCoins().saveIfDirty();
        }
        if (plugin.getShards() != null) {
            plugin.getShards().saveIfDirty();
        }
        if (plugin.progress() != null) {
            plugin.progress().saveIfDirty();
        }
        if (plugin.getCodex() != null) {
            plugin.getCodex().saveIfDirty();
        }
        if (plugin.recipeUnlocks() != null) {
            plugin.recipeUnlocks().saveIfDirty();
        }
    }
}
