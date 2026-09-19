package de.aetherion.farming;

import de.aetherion.farming.island.FarmIslandAmbience;
import de.aetherion.farming.island.FarmIslandService;
import de.aetherion.farming.portal.FarmPortalListener;
import de.aetherion.farming.portal.FarmPortalService;
import de.aetherion.farming.portal.FarmPortalToolListener;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;

public class AetherionFarming extends JavaPlugin {

    private static AetherionFarming instance;
    private WorldGuardPlugin worldGuard;
    private BirdScareEvent birdScare;
    private FarmIslandService island;
    private FarmPortalService portals;
    private FarmIslandAmbience ambience;
    private NamespacedKey portalToolKey;

    @Override
    public void onEnable() {
        instance = this;
        worldGuard = WorldGuardPlugin.inst();
        saveDefaultConfig();
        portalToolKey = new NamespacedKey(this, FarmPortalService.TOOL_KEY);

        FarmingListener farming = new FarmingListener();
        getServer().getPluginManager().registerEvents(farming, this);
        getServer().getScheduler().runTaskLater(this, farming::ripenLoadedChunks, 40L);

        island = new FarmIslandService(this);
        portals = new FarmPortalService(this, island);
        ambience = new FarmIslandAmbience(this, island);
        ambience.start();
        getServer().getPluginManager().registerEvents(new FarmPortalListener(portals), this);
        getServer().getPluginManager().registerEvents(new FarmPortalToolListener(portals), this);

        getServer().getScheduler().runTaskLater(this, () -> {
            if (!getConfig().getBoolean("farm-island.enabled", true)) {
                return;
            }
            if (getConfig().getBoolean("farm-island.auto-ensure", true)) {
                String status = island.ensureIsland(false);
                getLogger().info(status.replace('§', '&'));
            }
            if (island.isPasted()) {
                World world = island.ensureWorld();
                if (world != null) {
                    world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
                    if (!getConfig().getBoolean("farm-island.ambience-ready", false)) {
                        String msg = ambience.refreshNearExit();
                        getLogger().info(msg.replace('§', '&'));
                    }
                }
            }
        }, 80L);

        if (getServer().getPluginManager().getPlugin("AetherionItems") != null) {
            birdScare = new BirdScareEvent(this);
            birdScare.start();
            getLogger().info("Bird scare crop event enabled.");
        } else {
            getLogger().warning("AetherionItems missing — bird scare event disabled.");
        }
    }

    @Override
    public void onDisable() {
        if (birdScare != null) {
            birdScare.shutdown();
            birdScare = null;
        }
        getLogger().info("AetherionFarming beendet!");
    }

    public static AetherionFarming getInstance() {
        return instance;
    }

    public WorldGuardPlugin getWorldGuard() {
        return worldGuard;
    }

    public FarmIslandService island() {
        return island;
    }

    public FarmPortalService portals() {
        return portals;
    }

    public FarmIslandAmbience ambience() {
        return ambience;
    }

    public NamespacedKey portalToolKey() {
        return portalToolKey;
    }
}
