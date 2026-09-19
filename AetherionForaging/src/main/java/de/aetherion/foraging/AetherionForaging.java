package de.aetherion.foraging;

import de.aetherion.foraging.command.ForageCommand;
import de.aetherion.foraging.habitat.ForageHabitatService;
import de.aetherion.foraging.npc.IsleGuideFancyListener;
import de.aetherion.foraging.npc.IsleGuideNpc;
import de.aetherion.foraging.ritual.GroveRitualService;
import de.aetherion.foraging.weather.IsleWeatherService;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class AetherionForaging extends JavaPlugin {

    private static AetherionForaging instance;
    private ForagingListener listener;
    private ForageHabitatService habitats;
    private IsleWeatherService weather;
    private GroveRitualService grove;
    private IsleGuideNpc guide;

    public static AetherionForaging getInstance() {
        return instance;
    }

    ForagingListener listener() {
        return listener;
    }

    public ForageHabitatService habitats() {
        return habitats;
    }

    public IsleWeatherService weather() {
        return weather;
    }

    public GroveRitualService grove() {
        return grove;
    }

    public IsleGuideNpc guide() {
        return guide;
    }

    ForagingHud hud() {
        return listener == null ? null : listener.hud();
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        if (!getConfig().isSet("foraging.force-drop")) {
            getConfig().set("foraging.force-drop", "OAK_LOG");
        }
        if (!getConfig().isSet("fell.zone-size")) {
            getConfig().set("fell.zone-size", 4);
        }
        if (getConfig().getInt("fell.strike-ticks", 64) < 40) {
            getConfig().set("fell.strike-ticks", 64);
        }
        mergeExtraWoodTypes();
        ensureWeatherAndRitualDefaults();
        saveConfig();
        habitats = new ForageHabitatService(this);
        weather = new IsleWeatherService(this);
        grove = new GroveRitualService(this);
        guide = new IsleGuideNpc(this);
        IsleGuideFancyListener.register(this, guide);
        ForagingListener foragingListener = new ForagingListener(this);
        this.listener = foragingListener;
        Bukkit.getPluginManager().registerEvents(foragingListener, this);
        Bukkit.getScheduler().runTaskTimer(this, foragingListener::tick, 1L, 1L);

        ForageCommand forageCommand = new ForageCommand(this);
        var forageAdmin = getCommand("forageadmin");
        if (forageAdmin != null) {
            forageAdmin.setExecutor(forageCommand);
            forageAdmin.setTabCompleter(forageCommand);
        }

        if (getConfig().getBoolean("forage-isle.auto-scan-if-empty", true)
                && (getConfig().getConfigurationSection("habitats") == null
                || getConfig().getConfigurationSection("habitats").getKeys(false).isEmpty())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                getLogger().info("Habitats empty — running forage isle surface scan…");
                de.aetherion.foraging.island.ForageIsleScan.run(this, Bukkit.getConsoleSender());
            }, 100L);
        }
        getLogger().info("AetherionForaging enabled!");
    }

    private void mergeExtraWoodTypes() {
        var blocks = getConfig().getStringList("foraging.blocks");
        boolean changed = false;
        for (String wood : List.of("BAMBOO_BLOCK")) {
            if (!blocks.contains(wood)) {
                blocks.add(wood);
                changed = true;
            }
        }
        if (changed) {
            getConfig().set("foraging.blocks", blocks);
        }
        if (!getConfig().contains("forage-isle.area-radius")) {
            getConfig().set("forage-isle.area-radius", 120);
        }
        if (!getConfig().contains("hotspots.enabled")) {
            getConfig().set("hotspots.enabled", false);
        }
        if (!getConfig().contains("bait.enabled")) {
            getConfig().set("bait.enabled", false);
        }
    }

    private void ensureWeatherAndRitualDefaults() {
        // Always clamp sense/weather perf knobs — dense isle + TAB was tanking FPS/TPS.
        getConfig().set("habitat-sense.radius", 8);
        getConfig().set("habitat-sense.step", 4);
        getConfig().set("habitat-sense.y-range", 4);
        getConfig().set("habitat-sense.cache-ms", 4000);
        getConfig().set("isle-weather.tick-ticks", 100);
        getConfig().set("isle-weather.reroll-seconds", 120);
        getConfig().set("isle-weather.visuals", false);
        if (!getConfig().contains("isle-weather.enabled")) {
            getConfig().set("isle-weather.enabled", true);
        }
        if (!getConfig().contains("rituals.grove.x") || getConfig().isSet("rituals.rain_grove")) {
            getConfig().set("rituals.enabled", true);
            getConfig().set("rituals.grove.world", getConfig().getString("forage-isle.world", "world"));
            getConfig().set("rituals.grove.x", 560.5);
            getConfig().set("rituals.grove.y", 91.0);
            getConfig().set("rituals.grove.z", -200.5);
            getConfig().set("rituals.grove.reach", 4.5);
            getConfig().set("rituals.buff-seconds", 240);
            getConfig().set("rituals.weather-seconds", 300);
            getConfig().set("rituals.rain_grove", null);
        } else if (!getConfig().contains("rituals.enabled")) {
            getConfig().set("rituals.enabled", true);
        }
        if (!getConfig().contains("isle-guide.placed")) {
            getConfig().set("isle-guide.enabled", true);
            getConfig().set("isle-guide.placed", false);
        }
        if (!getConfig().contains("rituals.placed")) {
            getConfig().set("rituals.placed", false);
        }
        saveConfig();
    }

    boolean fellEnabled() {
        return getConfig().getBoolean("fell.enabled", true);
    }

    int fellStrikeTicks() {
        return Math.max(40, getConfig().getInt("fell.strike-ticks", 64));
    }

    int fellZoneSize() {
        return Math.max(3, Math.min(6, getConfig().getInt("fell.zone-size", 4)));
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.shutdown();
        }
        instance = null;
    }
}
