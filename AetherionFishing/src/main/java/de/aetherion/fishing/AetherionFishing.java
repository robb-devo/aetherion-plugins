package de.aetherion.fishing;

import de.aetherion.core.api.AetherServices;
import de.aetherion.fishing.isle.FishAccessImpl;
import de.aetherion.fishing.isle.FishIsle;

import org.bukkit.plugin.java.JavaPlugin;

public final class AetherionFishing extends JavaPlugin {

    private static AetherionFishing instance;
    private FishingController controller;
    private FishingEncounterListener encounters;
    private FishIsle isle;
    private FishAccessImpl access;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        // New keys (Fishing Eldervale) reach live configs; existing values are never touched.
        getConfig().options().copyDefaults(true);
        saveConfig();
        controller = new FishingController(this);
        encounters = new FishingEncounterListener(this);
        getServer().getPluginManager().registerEvents(controller, this);
        getServer().getPluginManager().registerEvents(encounters, this);
        getServer().getScheduler().runTaskTimer(this, controller::tick, 1L, 1L);
        startIsle();
        getLogger().info("AetherionFishing ready. Minigame/lure + Fishing Eldervale here; loot/rod XP stays in AetherionItems.");
    }

    private void startIsle() {
        if (getServer().getPluginManager().getPlugin("AetherionItems") == null) {
            getLogger().warning("Fishing Eldervale needs AetherionItems — isle loops off, harbour fishing unchanged.");
            return;
        }
        try {
            isle = new FishIsle(this);
            isle.start();
            controller.hooks(isle);
            access = new FishAccessImpl(isle);
            AetherServices.registerFishing(access);
        } catch (RuntimeException | LinkageError error) {
            getLogger().severe("Fishing Eldervale failed to start (harbour fishing unaffected): " + error);
            if (isle != null) {
                controller.hooks(null);
                isle.shutdown();
                isle = null;
            }
        }
    }

    @Override
    public void onDisable() {
        if (controller != null) {
            controller.hooks(null);
        }
        if (access != null) {
            AetherServices.clearFishing(access);
            access = null;
        }
        if (isle != null) {
            isle.shutdown();
            isle = null;
        }
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

    public FishingController controller() {
        return controller;
    }

    public FishIsle isle() {
        return isle;
    }
}
