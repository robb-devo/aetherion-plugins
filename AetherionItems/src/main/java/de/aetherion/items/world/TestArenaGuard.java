package de.aetherion.items.world;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test Arena only: no natural wildlife spawn, keep mob-spawning gamerule off.
 * Does not touch pets, set minions, bosses, or any other world.
 */
public final class TestArenaGuard implements Listener, Runnable {

    public TestArenaGuard(JavaPlugin plugin, TestArenaService arena) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 100L, 100L);
    }

    public static boolean isArena(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName();
        return TestArenaService.WORLD_NAME.equals(name)
                || name.startsWith(TestArenaService.WORLD_NAME + "_");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isArena(event.getLocation().getWorld())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        // Explicitly allow COMMAND / CUSTOM / SPAWNER_EGG — /summon and menu dummies must work.
        if (reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL
                || reason == CreatureSpawnEvent.SpawnReason.DEFAULT
                || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                || reason == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                || reason == CreatureSpawnEvent.SpawnReason.PATROL
                || reason == CreatureSpawnEvent.SpawnReason.RAID
                || reason == CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION
                || reason == CreatureSpawnEvent.SpawnReason.BREEDING
                || reason == CreatureSpawnEvent.SpawnReason.EGG
                || reason == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER
                || reason == CreatureSpawnEvent.SpawnReason.TRAP
                || reason == CreatureSpawnEvent.SpawnReason.JOCKEY
                || reason == CreatureSpawnEvent.SpawnReason.MOUNT) {
            event.setCancelled(true);
        }
    }

    @Override
    public void run() {
        World world = Bukkit.getWorld(TestArenaService.WORLD_NAME);
        if (world == null) {
            return;
        }
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setSpawnFlags(false, false);
    }
}
