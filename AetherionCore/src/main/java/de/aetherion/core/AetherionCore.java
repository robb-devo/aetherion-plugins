package de.aetherion.core;

import de.aetherion.core.command.RestartCommand;
import de.aetherion.core.command.WipeCommand;
import de.aetherion.core.restart.RestartCountdown;
import de.aetherion.core.wipe.BetaWipe;
import de.aetherion.core.wipe.NetworkWipeWatch;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared library plugin. Loads first. Does not tick the game.
 */
public final class AetherionCore extends JavaPlugin {

    private static AetherionCore instance;
    private BetaWipe betaWipe;
    private NetworkWipeWatch networkWipeWatch;
    private RestartCountdown restartCountdown;

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
        restartCountdown = new RestartCountdown(this);
        RestartCommand restartCommand = new RestartCommand(this, restartCountdown);
        PluginCommand restart = getCommand("aetherrestart");
        if (restart != null) {
            restart.setExecutor(restartCommand);
            restart.setTabCompleter(restartCommand);
        }
        getLogger().info("Shared keys and hit flags ready. Game plugins keep the loop.");
    }

    public RestartCountdown restartCountdown() {
        return restartCountdown;
    }

    @Override
    public void onDisable() {
        if (restartCountdown != null) {
            restartCountdown.cancel();
        }
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
}
