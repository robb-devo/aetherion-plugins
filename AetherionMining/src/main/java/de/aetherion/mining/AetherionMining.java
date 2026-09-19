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

    @Override
    public void onEnable() {
        instance = this;
        worldGuard = WorldGuardPlugin.inst();
        saveDefaultConfig();

        veins = new VeinsWorld(this);
        VeinsNpcs npcs = new VeinsNpcs(this);
        veins.bind(npcs);

        getServer().getPluginManager().registerEvents(new MiningListener(), this);
        getServer().getPluginManager().registerEvents(new SharedWorldGuard(), this);
        getServer().getPluginManager().registerEvents(new VeinsListener(this, veins, npcs), this);

        VeinsCommand command = new VeinsCommand(this, veins, npcs);
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
        getLogger().info("The Veins ready. /deepmines");
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
        if (veins != null) {
            veins.saveData();
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
}
