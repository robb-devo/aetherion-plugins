package de.aetherion.bossengine.integration.worldguard;

import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.event.Listener;

public final class WorldGuardHook {

    private WorldGuardHook() {
    }

    public static void register(BossEngine plugin) {
        Listener listener = new WorldGuardCompatListener(plugin.getKeys());
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getLogger().info("WorldGuard hook enabled – bosses and minions may spawn in protected regions.");
    }
}
