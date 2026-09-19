package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Flush dirty economy/progress stores on quit so crash windows stay small.
 * Close trade/AH/Bazaar/storage GUIs first so holders return items before YAML flush.
 */
public final class PersistenceFlushListener implements Listener {

    private final AetherionItems plugin;

    public PersistenceFlushListener(AetherionItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        flush(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        flush(event.getPlayer());
    }

    private void flush(Player player) {
        if (player != null) {
            if (plugin.getMarket() != null) {
                plugin.getMarket().closeOpenGuis(player);
            }
            player.closeInventory();
        }
        if (plugin.getStorageInventory() != null && player != null) {
            plugin.getStorageInventory().savePlayerStorage(player.getUniqueId());
        }
        if (plugin.getLoadoutListener() != null && player != null) {
            plugin.getLoadoutListener().flushWornLoadout(player);
        }
        if (plugin.getCoins() != null) {
            plugin.getCoins().saveIfDirty();
        }
        if (plugin.getShards() != null) {
            plugin.getShards().saveIfDirty();
        }
        if (plugin.progress() != null) {
            plugin.progress().saveIfDirty();
        }
        if (plugin.getSkills() != null) {
            plugin.getSkills().saveIfDirty();
        }
        if (plugin.getCodex() != null) {
            plugin.getCodex().saveIfDirty();
        }
        if (plugin.recipeUnlocks() != null) {
            plugin.recipeUnlocks().saveIfDirty();
        }
        if (plugin.blueprintUnlocks() != null) {
            plugin.blueprintUnlocks().save();
        }
        if (plugin.xpBoost() != null) {
            plugin.xpBoost().saveIfDirty();
        }
        if (plugin.getMarket() != null) {
            plugin.getMarket().save();
        }
    }
}
