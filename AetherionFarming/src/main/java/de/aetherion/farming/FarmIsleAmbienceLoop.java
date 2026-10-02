package de.aetherion.farming;

import de.aetherion.farming.island.FarmIsleZones;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Light Farm Isle presence: soft wind for players in the footprint, a few bees.
 * No weather packets, no heavy FX — Forage-isle “light” style.
 */
public final class FarmIsleAmbienceLoop implements Runnable {

    private final AetherionFarming plugin;
    private final org.bukkit.NamespacedKey beeKey;
    private BukkitTask task;

    public FarmIsleAmbienceLoop(AetherionFarming plugin) {
        this.plugin = plugin;
        this.beeKey = new org.bukkit.NamespacedKey(plugin, "farm_isle_bee");
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("farm-isle-ambience.enabled", true)) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 40L, 20L * 18L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!FarmIsleZones.inFarmIsleFootprint(plugin, player.getLocation())) {
                continue;
            }
            if (rng.nextDouble() < 0.55d) {
                player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.08f, 0.55f + rng.nextFloat() * 0.2f);
            }
            if (rng.nextDouble() < 0.25d) {
                player.playSound(player.getLocation(), Sound.BLOCK_GRASS_STEP, 0.12f, 0.7f);
            }
        }
        maybeTopUpBees();
    }

    private void maybeTopUpBees() {
        int want = Math.max(0, plugin.getConfig().getInt("farm-isle-ambience.bees", 6));
        if (want <= 0) {
            return;
        }
        World world = FarmIsleZones.resolveWorld(plugin);
        if (world == null) {
            return;
        }
        int have = 0;
        for (Entity entity : world.getEntitiesByClass(Bee.class)) {
            if (entity.getPersistentDataContainer().has(beeKey, PersistentDataType.BYTE)) {
                have++;
            }
        }
        if (have >= want) {
            return;
        }
        // Spawn near a survival player on the isle if any, else skip.
        Player anchor = null;
        for (Player player : world.getPlayers()) {
            if (FarmIsleZones.inFarmIsleFootprint(plugin, player.getLocation())) {
                anchor = player;
                break;
            }
        }
        if (anchor == null) {
            return;
        }
        Location at = anchor.getLocation().clone().add(
                ThreadLocalRandom.current().nextDouble() * 10 - 5,
                1.2,
                ThreadLocalRandom.current().nextDouble() * 10 - 5
        );
        Bee bee = (Bee) world.spawnEntity(at, EntityType.BEE);
        bee.setPersistent(true);
        bee.setRemoveWhenFarAway(true);
        bee.setAnger(0);
        bee.setHasNectar(false);
        bee.setSilent(true);
        bee.getPersistentDataContainer().set(beeKey, PersistentDataType.BYTE, (byte) 1);
    }
}
