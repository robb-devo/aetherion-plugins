package de.aetherion.bossengine.listener;

import de.aetherion.bossengine.integration.worldguard.WorldGuardSpawnGuard;
import de.aetherion.bossengine.util.BossKeys;

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * DeluxeHub, MythicMobs and WorldGuardExtraFlags often cancel vanilla
 * CreatureSpawnEvent even for CUSTOM spawns. We uncancel tagged bosses
 * after those plugins have run (softdepend → we register later).
 */
public class BossSpawnProtectListener implements Listener {

    private final BossKeys keys;

    public BossSpawnProtectListener(BossKeys keys) {
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPreSpawn(PreCreatureSpawnEvent event) {
        if (WorldGuardSpawnGuard.isBypassing()) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (WorldGuardSpawnGuard.isBypassing()
                || keys.isBoss(event.getEntity())
                || keys.isMinion(event.getEntity())) {
            event.setCancelled(false);
        }
    }
}
