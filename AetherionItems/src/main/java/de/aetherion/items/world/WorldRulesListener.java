package de.aetherion.items.world;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldRulesListener implements Listener, Runnable {

    private final JavaPlugin plugin;

    public WorldRulesListener(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTask(plugin, this);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 20L * 30, 20L * 60);
    }

    @Override
    public void run() {
        for (World world : plugin.getServer().getWorlds()) {
            if (!isOverworld(world)) {
                continue;
            }
            // Day/night runs — forage isle weather phases depend on it.
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            // Animals may still natural-spawn; NaturalSpawnGuard blocks vanilla hostiles.
            world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            // Persist across restarts — vanilla default is false; nothing else was setting this.
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            // World storm stays off; forage isle uses player-local weather instead.
            world.setStorm(false);
            world.setThundering(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreeperExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Creeper)) {
            return;
        }
        event.blockList().clear();
        event.setYield(0f);
    }

    private static boolean isOverworld(World world) {
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }
        String name = world.getName().toLowerCase();
        // Never rewrite Test Arena / shared farm island rules (mob spawning stays off).
        if (name.equals("aether_test") || name.startsWith("aether_test_")) {
            return false;
        }
        if (name.equals("aether_farm_island") || name.startsWith("aether_farm_")) {
            return false;
        }
        return !name.startsWith("aedun_") && !name.startsWith("ae_dun");
    }
}
