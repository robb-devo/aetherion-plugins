package de.aetherion.fishing;

import org.bukkit.plugin.java.JavaPlugin;

public final class AetherionFishing extends JavaPlugin {

    private static AetherionFishing instance;
    private FishingController controller;
    private FishingEncounterListener encounters;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        controller = new FishingController(this);
        encounters = new FishingEncounterListener(this);
        getServer().getPluginManager().registerEvents(controller, this);
        getServer().getPluginManager().registerEvents(encounters, this);
        getServer().getScheduler().runTaskTimer(this, controller::tick, 1L, 1L);
        getLogger().info("AetherionFishing ready.");
    }

    @Override
    public void onDisable() {
        if (controller != null) {
            controller.shutdown();
        }
        if (encounters != null) {
            encounters.shutdown();
        }
        instance = null;
    }

    public static AetherionFishing getInstance() {
        return instance;
    }
}
