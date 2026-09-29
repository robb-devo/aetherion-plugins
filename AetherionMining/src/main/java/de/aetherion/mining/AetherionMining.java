package de.aetherion.mining;

import de.aetherion.mining.veins.VeinsCommand;
import de.aetherion.mining.veins.VeinsListener;
import de.aetherion.mining.veins.VeinsNpcs;
import de.aetherion.mining.veins.VeinsWorld;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;

public class AetherionMining extends JavaPlugin {

    private static AetherionMining instance;
    private WorldGuardPlugin worldGuard;
    private VeinsWorld veins;
    private de.aetherion.core.api.MiningAccess miningAccess;
    private de.aetherion.mining.isle.MineIsle isle;

    @Override
    public void onEnable() {
        instance = this;
        worldGuard = WorldGuardPlugin.inst();
        saveDefaultConfig();
        // Additive: merge new default keys (Mining Eldervale, Amethyst Mine) into an existing
        // config.yml without touching values that are already set.
        getConfig().options().copyDefaults(true);
        saveConfig();

        veins = new VeinsWorld(this);
        VeinsNpcs npcs = new VeinsNpcs(this);
        veins.bind(npcs);

        // Mining Eldervale first: its break router must see the strike before MiningListener seals it.
        if (getConfig().getBoolean("mine-isle.enabled", true)) {
            try {
                isle = new de.aetherion.mining.isle.MineIsle(this);
                isle.start();
                PluginCommand mineisle = getCommand("mineisle");
                if (mineisle != null) {
                    de.aetherion.mining.isle.MineCommand isleCommand = new de.aetherion.mining.isle.MineCommand(isle);
                    mineisle.setExecutor(isleCommand);
                    mineisle.setTabCompleter(isleCommand);
                }
            } catch (LinkageError error) {
                isle = null;
                getLogger().warning("Mining Eldervale off: AetherionItems missing or too old (" + error.getClass().getSimpleName() + ").");
            }
        }

        getServer().getPluginManager().registerEvents(new MiningListener(), this);
        getServer().getPluginManager().registerEvents(new SharedWorldGuard(), this);
        getServer().getPluginManager().registerEvents(new VeinsListener(this, veins, npcs), this);

        VeinsCommand command = new VeinsCommand(this, veins, npcs);
        PluginCommand amethyst = getCommand("amethyst");
        if (amethyst != null) {
            amethyst.setExecutor(command);
            amethyst.setTabCompleter(command);
        }
        PluginCommand deepmines = getCommand("deepmines");
        if (deepmines != null) {
            deepmines.setExecutor(command);
            deepmines.setTabCompleter(command);
        }

        getServer().getScheduler().runTask(this, () -> {
            veins.ensureLoaded();
            npcs.loadEntrance();
            if (getConfig().getBoolean("eldervale.auto-paste-once", false)) {
                getConfig().set("eldervale.auto-paste-once", false);
                saveConfig();
                getLogger().warning("eldervale.auto-paste-once triggered — pasting Mining Eldervale Island…");
                de.aetherion.mining.island.EldervalePaste.paste(this, getServer().getConsoleSender());
            }
        });
        getServer().getScheduler().runTaskTimer(this, veins::tickReset, 20L * 60L, 20L * 60L * 5L);
        getLogger().info("Amethyst Area dig world ready. Players: /amethyst. Admin: /deepmines.");
        miningAccess = new de.aetherion.mining.api.MiningAccessImpl();
        de.aetherion.core.api.AetherServices.registerMining(miningAccess);
    }

    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        String name = getConfig().getString("veins.world", "aether_veins");
        if (worldName != null && worldName.equalsIgnoreCase(name)) {
            return new de.aetherion.mining.veins.VeinsChunkGenerator(
                    Math.max(16, getConfig().getInt("veins.radius", 250)),
                    getConfig().getInt("veins.hub-y", 220)
            );
        }
        return null;
    }

    @Override
    public void onDisable() {
        if (isle != null) {
            isle.shutdown();
            isle = null;
        }
        if (veins != null) {
            veins.saveData();
        }
        if (miningAccess != null) {
            de.aetherion.core.api.AetherServices.clearMining(miningAccess);
            miningAccess = null;
        }
        getLogger().info("AetherionMining beendet!");
        instance = null;
    }

    public static AetherionMining getInstance() {
        return instance;
    }

    public WorldGuardPlugin getWorldGuard() {
        return worldGuard;
    }

    public VeinsWorld getVeins() {
        return veins;
    }

    /** Mining Eldervale router, or {@code null} when disabled. */
    public de.aetherion.mining.isle.MineIsle getIsle() {
        return isle;
    }
}
