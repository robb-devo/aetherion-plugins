package de.aetherion.items.listener;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Global soft PvP off — players cannot damage each other.
 * World flag + event cancel so projectiles / sweep / custom damage stay blocked.
 */
public final class PvpGuardListener implements Listener {

    private final Plugin plugin;

    public PvpGuardListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public void applyToLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            world.setPVP(false);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().setPVP(false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Entity source = event.getDamager();
        if (source instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }

        if (source instanceof Player) {
            event.setCancelled(true);
        }
    }
}
