package de.aetherion.pit.listener;

import de.aetherion.pit.AetherionPit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.entity.Projectile;

public final class PitCombatListener implements Listener {

    private final AetherionPit plugin;

    public PitCombatListener(AetherionPit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(event);
        if (attacker == null) {
            return;
        }
        if (!plugin.safeZone().isPitWorld(victim.getWorld())) {
            return;
        }
        // No PvP inside the red line (safe spawn).
        if (plugin.safeZone().isSafe(victim) || plugin.safeZone().isSafe(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!plugin.safeZone().isPitWorld(victim.getWorld())) {
            return;
        }
        if (plugin.safeZone().isSafe(victim)) {
            return;
        }
        plugin.levels().onDeath(victim);
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim) && !plugin.safeZone().isSafe(killer)) {
            plugin.levels().rewardKill(killer);
        }
        // Keep inventory for casual pit — less punishing than hardcore.
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    private static Player attackerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource src = projectile.getShooter();
            if (src instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
