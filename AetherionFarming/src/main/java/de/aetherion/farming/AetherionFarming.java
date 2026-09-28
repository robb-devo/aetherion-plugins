package de.aetherion.farming;

import de.aetherion.farming.dev.CanePatchTool;
import de.aetherion.farming.dev.FarmDistrictMarker;
import de.aetherion.farming.dev.HayWagonProp;
import de.aetherion.farming.dev.ScarecrowProp;
import de.aetherion.farming.island.FarmIsleSeeder;
import de.aetherion.farming.isle.FarmIsle;
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
    private FeaturedCropService featuredCrop;
    private FarmIsleAmbienceLoop isleAmbience;
    private CanePatchTool canePatchTool;
    private FarmDistrictMarker districtMarker;
    private ScarecrowProp scarecrow;
    private HayWagonProp hayWagon;
    private ScarecrowEvent scarecrowEvent;
    private FarmIsleSeeder seeder;
    private FarmIsle farmIsle;
    private NamespacedKey portalToolKey;
    private de.aetherion.core.api.FarmAccess farmAccess;

    @Override
    public void onEnable() {
        instance = this;
        worldGuard = WorldGuardPlugin.inst();
        saveDefaultConfig();
        // Merge new keys without wiping live overrides.
        getConfig().options().copyDefaults(true);
        saveConfig();
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

        canePatchTool = new CanePatchTool(this);
        districtMarker = new FarmDistrictMarker(this);
        scarecrow = new ScarecrowProp(this);
        hayWagon = new HayWagonProp(this);
        getServer().getPluginManager().registerEvents(canePatchTool, this);
        getServer().getPluginManager().registerEvents(districtMarker, this);
        getServer().getPluginManager().registerEvents(scarecrow, this);
        getServer().getPluginManager().registerEvents(hayWagon, this);

        seeder = new FarmIsleSeeder(this);
        getServer().getScheduler().runTaskLater(this, seeder::autoSeed, 120L);

        featuredCrop = new FeaturedCropService(this);
        featuredCrop.start();
        isleAmbience = new FarmIsleAmbienceLoop(this);
        isleAmbience.start();

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
            scarecrowEvent = new ScarecrowEvent(this);
            scarecrowEvent.start();
            getLogger().info("Bird scare crop event enabled (hub farm + Farm Isle zones).");
            // Eldervale loops need Items (skills, coins, stats) — same gate as the bird scare.
            farmIsle = new FarmIsle(this);
            farmIsle.start();
        } else {
            getLogger().warning("AetherionItems missing — bird scare event disabled.");
        }
        farmAccess = new de.aetherion.farming.api.FarmAccessImpl();
        de.aetherion.core.api.AetherServices.registerFarming(farmAccess);
    }

    @Override
    public void onDisable() {
        if (farmIsle != null) {
            farmIsle.shutdown();
            farmIsle = null;
        }
        if (birdScare != null) {
            birdScare.shutdown();
            birdScare = null;
        }
        if (scarecrowEvent != null) {
            scarecrowEvent.shutdown();
            scarecrowEvent = null;
        }
        if (seeder != null) {
            seeder.shutdown();
            seeder = null;
        }
        if (featuredCrop != null) {
            featuredCrop.shutdown();
            featuredCrop = null;
        }
        if (isleAmbience != null) {
            isleAmbience.shutdown();
            isleAmbience = null;
        }
        if (farmAccess != null) {
            de.aetherion.core.api.AetherServices.clearFarming(farmAccess);
            farmAccess = null;
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

    public FeaturedCropService featuredCrop() {
        return featuredCrop;
    }

    public CanePatchTool canePatchTool() {
        return canePatchTool;
    }

    public FarmDistrictMarker districtMarker() {
        return districtMarker;
    }

    public ScarecrowProp scarecrow() {
        return scarecrow;
    }

    public HayWagonProp hayWagon() {
        return hayWagon;
    }

    public FarmIsleSeeder seeder() {
        return seeder;
    }

    /** Eldervale loops (rhythm, prizes, mastery, orders, cast…). Null without AetherionItems. */
    public FarmIsle farmIsle() {
        return farmIsle;
    }

    public BirdScareEvent birdScare() {
        return birdScare;
    }

    public ScarecrowEvent scarecrowEvent() {
        return scarecrowEvent;
    }

    public NamespacedKey portalToolKey() {
        return portalToolKey;
    }
}
