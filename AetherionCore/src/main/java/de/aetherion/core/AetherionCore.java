package de.aetherion.core;

import de.aetherion.core.command.WipeCommand;
import de.aetherion.core.shutdown.ShutdownCountdown;
import de.aetherion.core.wipe.BetaWipe;
import de.aetherion.core.wipe.NetworkWipeWatch;
import de.aetherion.core.world.VoidChunkGenerator;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared library plugin. Loads first. Does not tick the game.
 */
public final class AetherionCore extends JavaPlugin {

    private static AetherionCore instance;
    private BetaWipe betaWipe;
    private NetworkWipeWatch networkWipeWatch;

    public static AetherionCore get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();
        betaWipe = new BetaWipe(this);
        if (betaWipe.isPending()) {
            betaWipe.run();
            betaWipe.clearPending();
        }
    }

    @Override
    public void onEnable() {
        if (betaWipe == null) {
            betaWipe = new BetaWipe(this);
        }
        getLogger().info("Wipe paths: " + betaWipe.layout().describe());
        networkWipeWatch = new NetworkWipeWatch(this, betaWipe);
        WipeCommand wipeCommand = new WipeCommand(this, betaWipe, networkWipeWatch);
        PluginCommand wipe = getCommand("wipe");
        if (wipe != null) {
            wipe.setExecutor(wipeCommand);
            wipe.setTabCompleter(wipeCommand);
        }
        networkWipeWatch.start();
        Bukkit.getPluginManager().registerEvents(new ShutdownCountdown(this), this);
        getLogger().info("Shared keys and hit flags ready. Game plugins keep the loop.");
        getLogger().info("ShutdownCountdown armed (10→8→6→4→2). Bypass: stop now|force.");
    }

    @Override
    public void onDisable() {
        if (networkWipeWatch != null) {
            networkWipeWatch.stop();
        }
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§8AetherionCore §7" + getPluginMeta().getVersion() + " §8· §7keys only, no tick.");
        return true;
    }

    /**
     * Island / dungeon void worlds (bukkit.yml / Multiverse {@code AetherionCore:void}). Available at STARTUP so
     * Multiverse can load {@code aether_islands} / {@code aether_guilds} as real void before Guilds enables.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidChunkGenerator();
    }
}
