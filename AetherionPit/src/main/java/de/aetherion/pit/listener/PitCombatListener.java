package de.aetherion.pit.listener;

import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.hub.HubHolograms;
import de.aetherion.pit.util.Msg;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.entity.Projectile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PitCombatListener implements Listener {

    private final AetherionPit plugin;
    /** Kills since the last death, per player. Announced at 3 and every 5. */
    private final Map<UUID, Integer> streaks = new ConcurrentHashMap<>();

    public PitCombatListener(AetherionPit plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        streaks.remove(event.getPlayer().getUniqueId());
    }

    private static void announce(World world, String en, String de) {
        for (Player player : world.getPlayers()) {
            Msg.send(player, HubHolograms.german(player) ? de : en);
        }
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
        boolean scored = killer != null && !killer.equals(victim) && !plugin.safeZone().isSafe(killer);
        int ended = streaks.getOrDefault(victim.getUniqueId(), 0);
        streaks.remove(victim.getUniqueId());
        if (scored) {
            plugin.levels().rewardKill(killer);
            int streak = streaks.merge(killer.getUniqueId(), 1, Integer::sum);
            if (streak == 3 || (streak >= 5 && streak % 5 == 0)) {
                announce(victim.getWorld(), "&c" + killer.getName() + " &7is on a &c" + streak + " kill streak&7!",
                        "&c" + killer.getName() + " &7hat eine &c" + streak + "er-Killserie&7!");
            }
        }
        if (ended >= 3) {
            String by = scored ? killer.getName() : null;
            announce(victim.getWorld(),
                    by == null ? "&7" + victim.getName() + "'s &c" + ended + " kill streak &7is over."
                            : "&c" + by + " &7ended " + victim.getName() + "'s &c" + ended + " kill streak&7.",
                    by == null ? "&7Die &c" + ended + "er-Killserie &7von " + victim.getName() + " ist vorbei."
                            : "&c" + by + " &7beendet die &c" + ended + "er-Killserie &7von " + victim.getName() + ".");
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
