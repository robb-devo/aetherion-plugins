package de.aetherion.items.listener;

import de.aetherion.items.economy.Signatures;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * P1 Signatures: finds from before the id stamp (Crystal Finds, Prize Crops, Fish Trophies) pick up
 * their Items id the next time the owner joins or opens a container, so they work in T4/T5 recipes.
 * Only the player's own inventory and ender chest are touched; the plugin keys stay as they were.
 */
public final class SignatureListener implements Listener {

    private final JavaPlugin plugin;

    public SignatureListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> stamp(player), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            stamp(player);
        }
    }

    private static void stamp(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Signatures.stampLegacy(player.getInventory());
        Signatures.stampLegacy(player.getEnderChest());
    }
}
